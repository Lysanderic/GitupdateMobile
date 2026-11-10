package com.dsh.verwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/** 选 APK → 下载 → Shizuku 静默装 / 系统安装器。 */
public class UpdateFlow {

    public interface Listener {
        void onDownloadStart(String name, long total);

        void onDownloadProgress(long got, long total, long elapsedMs, long etaMs, long speedBps);

        void onDownloadEnd(boolean ok, String message);
    }

    public static volatile Listener listener;

    private static final java.util.concurrent.ExecutorService DL =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static volatile int pending = 0;

    private static void reportProgress(long got, long total, long elapsed, long eta, long speed) {
        Listener l = listener;
        if (l != null) l.onDownloadProgress(got, total, elapsed, eta, speed);
    }

    public static boolean apkReady(Activity act, Store.Entry e) {
        if (e.apkFile.length() == 0) return false;
        File f = new File(new File(act.getFilesDir(), "updates"), e.apkFile);
        return f.isFile() && f.length() > 1024;
    }

    public static void installExisting(Activity act, Store.Entry e) {
        if (!apkReady(act, e)) {
            toast(act, "安装包没了，需要重新下载");
            return;
        }
        install(act, e, e.apkFile);
    }

    public static void start(final Activity act, final Store.Entry e) {
        if (!GitHub.validRepo(e.repo) || e.latestTag.length() == 0) {
            toast(act, "先检查出最新版本再说");
            return;
        }
        toast(act, "正在查 Release 里的安装包…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<String[]> assets;
                try {
                    assets = GitHub.fetchAssets(e.repo, e.latestTag, Store.token(act));
                } catch (final Exception ex) {
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(act, "查不到安装包：" + ex.getMessage());
                            openReleasePage(act, e);
                        }
                    });
                    return;
                }
                if (assets.isEmpty()) {
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toast(act, "这个 Release 没传 APK，给你打开网页");
                            openReleasePage(act, e);
                        }
                    });
                    return;
                }
                Collections.sort(assets, new Comparator<String[]>() {
                    @Override
                    public int compare(String[] a, String[] b) {
                        return score(b[0], e.latestTag) - score(a[0], e.latestTag);
                    }
                });
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showChooser(act, e, assets);
                    }
                });
            }
        }).start();
    }

    private static int score(String name, String tag) {
        String n = name.toLowerCase();
        int s = 10;
        if (n.contains("arm64")) s += 100;
        if (n.contains("universal")) s += 30;
        if (n.contains("armeabi") || n.contains("v7a")) s += 20;
        if (n.contains("x86")) s -= 200;
        if (n.contains("debug")) s -= 60;
        String t = tag.toLowerCase();
        if (t.startsWith("v")) t = t.substring(1);
        if (t.length() > 0 && n.contains(t)) s += 50;
        return s;
    }

    private static void showChooser(final Activity act, final Store.Entry e,
                                    final ArrayList<String[]> assets) {
        String[] items = new String[assets.size() + 1];
        for (int i = 0; i < assets.size(); i++) {
            long size = 0;
            try {
                size = Long.parseLong(assets.get(i)[2]);
            } catch (Exception ignored) {
            }
            items[i] = assets.get(i)[0] + (size > 0 ? "   " + (size / 1048576) + " MB" : "");
        }
        items[assets.size()] = "打开 Release 网页";
        new AlertDialog.Builder(act)
                .setTitle("装哪个包（" + e.latestTag + "）")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which >= assets.size()) {
                            openReleasePage(act, e);
                            return;
                        }
                        download(act, e, assets.get(which));
                    }
                })
                .show();
    }

    private static void download(final Activity act, final Store.Entry e, final String[] asset) {
        final String name = safeName(asset[0]);
        final int ahead = pending;
        pending++;
        if (ahead > 0) {
            toast(act, "已加入下载队列，前面还有 " + ahead + " 个");
        }
        DL.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadNow(act, e, asset, name);
                } finally {
                    pending--;
                }
            }
        });
    }

    private static void downloadNow(final Activity act, final Store.Entry e,
                                    final String[] asset, final String name) {
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        new Thread(new Runnable() {
            @Override
            public void run() {
              try {
                File dir = new File(act.getFilesDir(), "updates");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, name);
                HttpURLConnection c = null;
                InputStream in = null;
                FileOutputStream fos = null;
                String err = null;
                long total = 0;
                long start = System.currentTimeMillis();
                long got = 0;
                Listener l = listener;
                if (l != null) l.onDownloadStart(name, 0);
                try {
                    c = (HttpURLConnection) new URL(asset[1]).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("User-Agent", "VerWatch/1.0 (Android)");
                    c.setInstanceFollowRedirects(true);
                    int code = c.getResponseCode();
                    if (code != 200) throw new Exception("HTTP " + code);
                    total = c.getContentLength();
                    Listener l2 = listener;
                    if (l2 != null) l2.onDownloadStart(name, total);
                    in = c.getInputStream();
                    fos = new FileOutputStream(out);
                    byte[] buf = new byte[65536];
                    int n;
                    long lastT = System.currentTimeMillis();
                    long lastB = 0;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        got += n;
                        long nowT = System.currentTimeMillis();
                        if (nowT - lastT >= 300) {
                            long dt = nowT - lastT;
                            long db = got - lastB;
                            long speed = dt > 0 ? (long) (db * 1000.0 / dt) : 0;
                            long eta = speed > 0 && total > 0 ? (long) ((total - got) * 1000.0 / speed) : -1;
                            reportProgress(got, total, nowT - start, eta, speed);
                            lastT = nowT;
                            lastB = got;
                        }
                    }
                    fos.flush();
                } catch (Exception ex) {
                    err = String.valueOf(ex.getMessage());
                    try {
                        if (out.exists()) out.delete();
                    } catch (Exception ignored) {
                    }
                } finally {
                    try {
                        if (fos != null) fos.close();
                    } catch (Exception ignored) {
                    }
                    try {
                        if (in != null) in.close();
                    } catch (Exception ignored) {
                    }
                    if (c != null) c.disconnect();
                }

                final String error = err;
                final long fTotal = total;
                final long fGot = got;
                if (error == null && !(fTotal > 0 && fGot < fTotal)) {
                    bindApk(act, e, name);
                }
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Listener l3 = listener;
                        if (l3 != null) {
                            if (error != null) l3.onDownloadEnd(false, error);
                            else l3.onDownloadEnd(true, null);
                        }
                        if (error != null) {
                            toast(act, "下载失败：" + error);
                            openReleasePage(act, e);
                            return;
                        }
                        if (fTotal > 0 && fGot < fTotal) {
                            try {
                                new File(new File(act.getFilesDir(), "updates"), name).delete();
                            } catch (Exception ignored) {
                            }
                            toast(act, "下载不完整，重新试一次");
                            return;
                        }
                        install(act, e, name);
                    }
                });
              } finally {
                latch.countDown();
              }
            }
        }).start();
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
    }

    /** 记录已下载的包；条目还没绑定包名时，从 APK 里读出来绑上。 */
    private static void bindApk(Activity act, Store.Entry e, String name) {
        String pkg = null;
        String ver = null;
        String label = null;
        try {
            File f = new File(new File(act.getFilesDir(), "updates"), name);
            android.content.pm.PackageInfo pi = act.getPackageManager()
                    .getPackageArchiveInfo(f.getAbsolutePath(), 0);
            if (pi != null) {
                pkg = pi.packageName;
                ver = pi.versionName;
                try {
                    label = String.valueOf(act.getPackageManager().getApplicationLabel(pi.applicationInfo));
                } catch (Exception ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            java.util.List<Store.Entry> list = Store.load(act);
            int idx = -1;
            for (int i = 0; i < list.size(); i++) {
                Store.Entry x = list.get(i);
                if (e.pkg.length() > 0 && x.pkg.equals(e.pkg)) {
                    idx = i;
                    break;
                }
                if (e.pkg.length() == 0 && x.repo.equals(e.repo) && x.label.equals(e.label)) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) return;
            Store.Entry self = list.get(idx);
            self.apkFile = name;
            if (pkg != null && pkg.length() > 0) {
                self.pkg = pkg;
                if (ver != null) self.installed = ver;
                if (label != null && label.length() > 0) self.label = label;
                for (int i = list.size() - 1; i >= 0; i--) {
                    if (i != idx && list.get(i).pkg.equals(pkg)) list.remove(i);
                }
            }
            Store.save(act, list);
            e.apkFile = name;
            if (pkg != null) e.pkg = pkg;
        } catch (Throwable ignored) {
        }
    }

    private static void install(final Activity act, final Store.Entry e, final String name) {
        if (ShizukuHelper.usable()) {
            installWithShizuku(act, e, name);
            return;
        }
        launchSystemInstaller(act, name);
    }

    /** 后台复制到 /sdcard/Download，再交给 Shizuku 用 /data/local/tmp 中转安装。 */
    private static void installWithShizuku(final Activity act, final Store.Entry e, final String name) {
        final File src = new File(new File(act.getFilesDir(), "updates"), name);
        new Thread(new Runnable() {
            @Override
            public void run() {
                File stageDir = new File("/sdcard/Download/.verwatch_tmp");
                File dst = null;
                try {
                    if (!stageDir.exists()) stageDir.mkdirs();
                    dst = new File(stageDir, name);
                    copy(src, dst);
                } catch (Throwable t) {
                    dst = null;
                }
                final File target = dst;
                if (target == null || !target.isFile() || target.length() < 1024) {
                    act.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            launchSystemInstaller(act, name);
                        }
                    });
                    return;
                }
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(act, "Shizuku 安装中，请稍等…", Toast.LENGTH_LONG).show();
                        ShizukuHelper.installApk(act, target, new ShizukuHelper.Callback() {
                            @Override
                            public void onResult(final boolean ok, final String message) {
                                act.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            target.delete();
                                            File d = target.getParentFile();
                                            if (d != null) d.delete();
                                        } catch (Exception ignored) {
                                        }
                                        if (!ok) {
                                            showShizukuFail(act, name, message);
                                            return;
                                        }
                                        verifyInstalled(act, e, name, message);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        }).start();
    }

    /** pm 说成功不算数，回查一次包是否真的在。 */
    private static void verifyInstalled(final Activity act, final Store.Entry e, final String name,
                                        final String output) {
        final String pkg = e.pkg;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean found = false;
                if (pkg != null && pkg.length() > 0) {
                    for (int i = 0; i < 10; i++) {
                        try {
                            act.getPackageManager().getPackageInfo(pkg, 0);
                            found = true;
                            break;
                        } catch (Throwable ignored) {
                        }
                        try {
                            Thread.sleep(300);
                        } catch (Exception ignored) {
                        }
                    }
                }
                final boolean ok = found;
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            Toast.makeText(act, "已通过 Shizuku 安装完成", Toast.LENGTH_LONG).show();
                            if (act instanceof MainActivity) ((MainActivity) act).refreshAfterInstall();
                        } else {
                            showShizukuFail(act, name,
                                    "安装命令返回成功，但系统里查不到这个应用（可能被「USB 安装」确认拦截）。\n"
                                            + output);
                        }
                    }
                });
            }
        }).start();
    }

    private static void showShizukuFail(final Activity act, final String name, String msg) {
        String detail = msg == null ? "无输出" : msg.trim();
        if (detail.length() > 700) detail = detail.substring(0, 700) + "…";
        new AlertDialog.Builder(act)
                .setTitle("Shizuku 安装失败")
                .setMessage("输出：\n" + detail)
                .setPositiveButton("用系统安装器", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        launchSystemInstaller(act, name);
                    }
                })
                .setNegativeButton("算了", null)
                .show();
    }

    private static void copy(File src, File dst) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(src);
        java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
        out.close();
        in.close();
    }

    private static void launchSystemInstaller(Activity act, String name) {
        Uri uri = Uri.parse("content://" + act.getPackageName() + ".apk/" + name);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            act.startActivity(i);
        } catch (Exception ex) {
            toast(act, "打不开安装器：" + ex.getMessage() + "\n到设置里允许「安装未知应用」");
        }
    }

    private static void openReleasePage(Activity act, Store.Entry e) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/" + GitHub.cleanRepo(e.repo) + "/releases/tag/" + e.latestTag));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Exception ex) {
            toast(act, "打不开浏览器");
        }
    }

    private static String safeName(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String s = sb.toString();
        if (!s.toLowerCase().endsWith(".apk")) s = s + ".apk";
        return s;
    }

    private static void toast(Activity act, String msg) {
        Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
    }
}
