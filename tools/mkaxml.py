#!/usr/bin/env python3
"""Generate a binary AndroidManifest.xml (AXML) and an empty resources.arsc."""
import struct

ATTR_ID = {
    "name": 0x01010003,
    "label": 0x01010001,
    "icon": 0x01010002,
    "permission": 0x01010006,
    "exported": 0x01010010,
    "versionCode": 0x0101021B,
    "versionName": 0x0101021C,
    "minSdkVersion": 0x0101020C,
    "targetSdkVersion": 0x01010270,
    "allowBackup": 0x01010280,
    "supportsRtl": 0x010103AF,
    "usesCleartextTraffic": 0x010104EC,
    "requestLegacyExternalStorage": 0x01010603,
}
TYPE_REF, TYPE_STRING, TYPE_INT_DEC, TYPE_INT_BOOL = 0x01, 0x03, 0x10, 0x12
ANDROID_NS = "http://schemas.android.com/apk/res/android"


class El:
    def __init__(self, tag, attrs=None, children=None):
        self.tag = tag
        self.attrs = attrs or []   # list of (ns_android: bool, name, (type, value))
        self.children = children or []


def collect_attr_names(node, out):
    for a in node.attrs:
        if a[0] and a[1] not in out:
            out.append(a[1])
    for c in node.children:
        collect_attr_names(c, out)


def collect_strings(node, pool):
    for a in node.attrs:
        if not a[0]:
            pool.append(a[1])
        if a[2][0] == TYPE_STRING:
            pool.append(a[2][1])
    pool.append(node.tag)
    for c in node.children:
        collect_strings(c, pool)


def pool_chunk(strings):
    index = {}
    for s in strings:
        if s not in index:
            index[s] = len(strings) - 1
    # build unique ordered list
    uniq = []
    for s in strings:
        if s not in [u for u in uniq]:
            uniq.append(s)
    del index
    offsets = b""
    data = b""
    for s in uniq:
        offsets += struct.pack("<I", len(data))
        data += struct.pack("<H", len(s)) + s.encode("utf-16-le") + b"\x00\x00"
    while len(data) % 4:
        data += b"\x00"
    header_size = 28
    strings_start = header_size + 4 * len(uniq)
    size = strings_start + len(data)
    out = struct.pack("<HHI", 0x0001, header_size, size)
    out += struct.pack("<IIIII", len(uniq), 0, 0, strings_start, 0)
    out += offsets + data
    return out, {s: i for i, s in enumerate(uniq)}


def resmap_chunk(attr_names):
    body = b"".join(struct.pack("<I", ATTR_ID.get(n, 0)) for n in attr_names)
    return struct.pack("<HHI", 0x0180, 8, 8 + len(body)) + body


def node_header(ctype, size, line=1):
    return struct.pack("<HHIII", ctype, 16, size, line, 0xFFFFFFFF)


def ns_chunk(ctype, prefix_idx, uri_idx):
    return node_header(ctype, 24) + struct.pack("<II", prefix_idx, uri_idx)


def start_el(node, sidx, attr_names, attr_ids, ns_android_idx):
    # attrs sorted: android-namespaced first is NOT required; keep document order
    count = len(node.attrs)
    body = b""
    for is_android, name, (vtype, value) in node.attrs:
        ns = ns_android_idx if is_android else 0xFFFFFFFF
        name_idx = sidx[name]
        raw = 0xFFFFFFFF
        if vtype == TYPE_STRING:
            data = sidx[value]
            raw = data
        elif vtype == TYPE_INT_DEC:
            data = value & 0xFFFFFFFF
        elif vtype == TYPE_INT_BOOL:
            data = 1 if value else 0
        elif vtype == TYPE_REF:
            data = value & 0xFFFFFFFF
        body += struct.pack("<III", ns, name_idx, raw)
        body += struct.pack("<HBBI", 8, 0, vtype, data)
    attr_ext = struct.pack("<II6H", 0xFFFFFFFF, sidx[node.tag], 20, 20, count, 0, 0, 0)
    size = 16 + 20 + len(body)
    return node_header(0x0102, size) + attr_ext + body


def end_el(tag_idx):
    return node_header(0x0103, 24) + struct.pack("<II", 0xFFFFFFFF, tag_idx)


def build(root):
    attr_names = []
    collect_attr_names(root, attr_names)
    all_strings = list(attr_names) + ["android", ANDROID_NS]
    collect_strings(root, all_strings)
    pool, sidx = pool_chunk(all_strings)
    ns_android_idx = sidx[ANDROID_NS]
    prefix_idx = sidx["android"]

    body = b""
    body += pool
    body += resmap_chunk(attr_names)
    body += ns_chunk(0x0100, prefix_idx, ns_android_idx)

    def emit(node):
        out = start_el(node, sidx, attr_names, None, ns_android_idx)
        for c in node.children:
            out += emit(c)
        out += end_el(sidx[node.tag])
        return out

    body += emit(root)
    body += ns_chunk(0x0101, prefix_idx, ns_android_idx)
    return struct.pack("<HHI", 0x0003, 8, 8 + len(body)) + body


def empty_arsc():
    return struct.pack("<HHII", 0x0002, 12, 12, 0)


def A(name, vtype, value):
    return (True, name, (vtype, value))


def manifest_tree(pkg, version_code, version_name, label, activity, receivers):
    def perm(p):
        return El("uses-permission", [A("name", TYPE_STRING, p)])

    perms = [
        "android.permission.INTERNET",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.WAKE_LOCK",
    ]
    queries = El("queries", [], [
        El("intent", [], [
            El("action", [A("name", TYPE_STRING, "android.intent.action.MAIN")]),
            El("category", [A("name", TYPE_STRING, "android.intent.category.LAUNCHER")]),
        ])
    ])
    app_children = [
        El("activity", [
            A("name", TYPE_STRING, activity),
            A("exported", TYPE_INT_BOOL, True),
            A("label", TYPE_STRING, label),
        ], [
            El("intent-filter", [], [
                El("action", [A("name", TYPE_STRING, "android.intent.action.MAIN")]),
                El("category", [A("name", TYPE_STRING, "android.intent.category.LAUNCHER")]),
            ])
        ])
    ]
    for r in receivers:
        app_children.append(El("receiver", [
            A("name", TYPE_STRING, r),
            A("exported", TYPE_INT_BOOL, False),
        ], [
            El("intent-filter", [], [
                El("action", [A("name", TYPE_STRING, "android.intent.action.BOOT_COMPLETED")]),
                El("action", [A("name", TYPE_STRING, "android.intent.action.TIME_SET")]),
                El("action", [A("name", TYPE_STRING, "android.intent.action.TIMEZONE_CHANGED")]),
                El("action", [A("name", TYPE_STRING, "android.intent.action.MY_PACKAGE_REPLACED")]),
            ])
        ]))
    application = El("application", [
        A("label", TYPE_STRING, label),
        A("allowBackup", TYPE_INT_BOOL, False),
        A("supportsRtl", TYPE_INT_BOOL, True),
    ], app_children)
    return El("manifest", [
        (False, "package", (TYPE_STRING, pkg)),
        A("versionCode", TYPE_INT_DEC, version_code),
        A("versionName", TYPE_STRING, version_name),
    ], [
        El("uses-sdk", [
            A("minSdkVersion", TYPE_INT_DEC, 26),
            A("targetSdkVersion", TYPE_INT_DEC, 35),
        ])
    ] + [perm(p) for p in perms] + [queries, application])


if __name__ == "__main__":
    import sys
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    tree = manifest_tree(
        "com.dsh.verwatch", 1, "1.0", "版本监视",
        ".MainActivity", [".CheckReceiver", ".BootReceiver"])
    blob = build(tree)
    open(out + "/AndroidManifest.xml", "wb").write(blob)
    open(out + "/resources.arsc", "wb").write(empty_arsc())
    print("manifest bytes", len(blob))
