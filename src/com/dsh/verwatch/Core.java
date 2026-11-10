package com.dsh.verwatch;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** 一次检查：刷新本机版本 → 拉 GitHub → 比对 → 通知。 */
public class Core {

    public static String runCheck(Context c, boolean notify) {
        List<Store.Entry> list = Store.load(c);
        PackageManager pm = c.getPackageManager();
        int newer = 0;
        int errors = 0;
        int skipped = 0;
        int okCount = 0;
        StringBuilder hits = new StringBuilder();
        long now = System.currentTimeMillis();

        for (Store.Entry e : list) {
            try {
                PackageInfo pi = pm.getPackageInfo(e.pkg, 0);
                if (pi.versionName != null) e.installed = pi.versionName;
            } catch (Exception ignored) {
            }
            if (!GitHub.validRepo(e.repo)) {
                e.error = "未填 GitHub 仓库";
                skipped++;
                continue;
            }
            try {
                String[] r = GitHub.fetchLatest(e.repo, e.etag, Store.token(c));
                if (r[0] != null) {
                    if (e.latestTag.length() > 0 && !e.latestTag.equals(r[0])) e.apkFile = "";
                    e.latestTag = r[0];
                    e.latestDate = Store.shortDate(r[1]);
                    e.etag = r[2] == null ? "" : r[2];
                }
                e.error = "";
                e.checkedAt = now;
                okCount++;
                if (e.latestTag.length() > 0) {
                    int cmp = Store.compare(e.installed, e.latestTag);
                    if (cmp < 0) {
                        newer++;
                        hits.append("\n• ").append(e.label).append("  ").append(e.installed)
                                .append(" → ").append(e.latestTag);
                    }
                }
            } catch (Exception ex) {
                String m = ex.getMessage();
                e.error = (m == null || m.length() == 0) ? "请求失败" : m;
                e.checkedAt = now;
                errors++;
            }
        }

        Store.save(c, list);
        Store.setLastCheck(c, new SimpleDateFormat("MM-dd HH:mm").format(new Date(now)));

        String title;
        StringBuilder text = new StringBuilder();
        if (list.isEmpty()) {
            title = "版本监视：还没选应用";
            text.append("打开应用，添加要监控的 App 并填上 GitHub 仓库。");
        } else if (newer > 0) {
            title = "有 " + newer + " 个应用可更新";
            text.append("发现新版本：").append(hits);
        } else {
            title = "全部已是最新";
            text.append("已检查 ").append(list.size()).append(" 个应用，没有正式版更新。");
        }
        if (errors > 0) text.append("\n失败的 ").append(errors).append(" 个：" ).append(firstError(list));
        if (skipped > 0) text.append("\n待填仓库的 ").append(skipped).append(" 个。");

        boolean should;
        if (list.isEmpty()) {
            should = !Store.quiet(c);
        } else if (newer > 0) {
            should = true;
        } else if (okCount == 0) {
            should = true;
        } else {
            should = !Store.quiet(c);
        }
        if (notify && should) {
            Notifier.show(c, title, text.toString());
        }
        return title + " · " + text.toString().replace("\n", " ");
    }

    private static String firstError(List<Store.Entry> list) {
        for (Store.Entry e : list) {
            if (e.error != null && e.error.length() > 0 && !e.error.equals("未填 GitHub 仓库")) {
                return e.label + "（" + e.error + "）";
            }
        }
        return "网络或仓库问题";
    }
}
