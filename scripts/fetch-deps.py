#!/usr/bin/env python3
"""下载构建依赖：android.jar、d8(r8.jar)、Shizuku SDK。"""
import os
import urllib.request
import zipfile

SDK = "sdk"
LIBS = "libs"

FILES = [
    ("https://dl.google.com/android/repository/platform-35_r01.zip", None),
    ("https://raw.githubusercontent.com/Sable/android-platforms/master/android-30/android.jar",
     os.path.join(SDK, "android-30.jar")),
    ("https://maven.google.com/com/android/tools/r8/8.2.42/r8-8.2.42.jar",
     os.path.join(SDK, "r8.jar")),
]

SHIZUKU = ["api", "provider", "aidl", "shared"]


def get(url, dst):
    if os.path.exists(dst) and os.path.getsize(dst) > 1024:
        print("skip", dst)
        return
    print("get ", url)
    os.makedirs(os.path.dirname(dst) or ".", exist_ok=True)
    urllib.request.urlretrieve(url, dst)


def main():
    os.makedirs(SDK, exist_ok=True)
    os.makedirs(LIBS, exist_ok=True)

    plat = os.path.join(SDK, "platform-35.zip")
    get(FILES[0][0], plat)
    if not os.path.exists(os.path.join(SDK, "android.jar")):
        with zipfile.ZipFile(plat) as z:
            with z.open("android-35/android.jar") as src, open(os.path.join(SDK, "android.jar"), "wb") as out:
                out.write(src.read())

    for url, dst in FILES[1:]:
        get(url, dst)

    for name in SHIZUKU:
        aar = os.path.join(LIBS, "shizuku-%s.aar" % name)
        get("https://repo1.maven.org/maven2/dev/rikka/shizuku/%s/13.1.5/%s-13.1.5.aar" % (name, name), aar)
        jar = os.path.join(LIBS, "shizuku-%s.jar" % name)
        if not os.path.exists(jar):
            with zipfile.ZipFile(aar) as z, open(jar, "wb") as out:
                out.write(z.read("classes.jar"))
    print("done")


if __name__ == "__main__":
    main()
