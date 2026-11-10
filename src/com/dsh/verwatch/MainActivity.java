package com.dsh.verwatch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    static final int C_BG = 0xFFF6F6F7;
    static final int C_TEXT = 0xFF141414;
    static final int C_SUB = 0xFF8A8A8E;
    static final int C_GREEN = 0xFF1F8A46;
    static final int C_ORANGE = 0xFFD2691E;
    static final int C_RED = 0xFFC0392B;
    static final int C_PRIMARY = 0xFF3B6FF6;

    private final ArrayList<Store.Entry> entries = new ArrayList<Store.Entry>();
    private LinearLayout cards;
    private TextView subtitle;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Button shizukuBtn;
    private LinearLayout dlPanel;
    private TextView dlSpeed;
    private TextView dlTime;
    private TextView dlPct;
    private TextView dlSize;
    private ProgressBar dlBar;
    private TextView dlName;
    private final Runnable hidePanel = new Runnable() {
        @Override
        public void run() {
            if (dlPanel != null) dlPanel.setVisibility(View.GONE);
        }
    };
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    static class AppItem {
        String pkg = "";
        String label = "";
        String ver = "";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        Notifier.ensureChannel(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(14), dp(18), dp(14), 0);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        View accent = new View(this);
        accent.setBackground(round(C_PRIMARY, 4));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(4), dp(26));
        alp.rightMargin = dp(10);
        titleRow.addView(accent, alp);
        TextView title = new TextView(this);
        title.setText("版本监视");
        title.setTextSize(24);
        title.setTextColor(C_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(title);

        titleRow.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        shizukuBtn = actionButton("Shizuku", 0xFFEEF1F5, 0xFF6B7280);
        shizukuBtn.setTextSize(11.5f);
        shizukuBtn.setMinHeight(dp(32));
        shizukuBtn.setMinimumHeight(dp(32));
        shizukuBtn.setPadding(dp(10), 0, dp(10), 0);
        shizukuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ShizukuHelper.alive()) {
                    Toast.makeText(MainActivity.this,
                            "先打开 Shizuku 并启动服务（无线调试 / ADB），再回来点这里",
                            Toast.LENGTH_LONG).show();
                } else if (!ShizukuHelper.granted()) {
                    ShizukuHelper.request();
                    Toast.makeText(MainActivity.this, "在 Shizuku 弹窗里点「允许」", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            "已连接：以后装更新会用 Shizuku 静默安装，不再弹安装器",
                            Toast.LENGTH_LONG).show();
                }
                updateShizukuBtn();
            }
        });
        titleRow.addView(shizukuBtn);
        root.addView(titleRow);

        ShizukuHelper.init(this);
        ShizukuHelper.setOnStateChanged(new Runnable() {
            @Override
            public void run() {
                updateShizukuBtn();
            }
        });
        updateShizukuBtn();

        subtitle = new TextView(this);
        subtitle.setTextSize(11.5f);
        subtitle.setTextColor(C_SUB);
        subtitle.setPadding(dp(14), dp(5), 0, dp(12));
        root.addView(subtitle);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        Button check = actionButton("立即检查", C_PRIMARY, 0xFFFFFFFF);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runCheckAsync();
            }
        });
        bar.addView(check, weighted(dp(6)));

        Button add = actionButton("＋ 添加", 0xFFE8EEFC, C_PRIMARY);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPicker();
            }
        });
        bar.addView(add, weighted(dp(6)));

        Button more = actionButton("设置", 0xFFE8EEFC, C_PRIMARY);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });
        bar.addView(more, weighted(0));
        root.addView(bar);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        sv.setClipToPadding(false);
        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setPadding(0, dp(12), 0, dp(28));
        sv.addView(cards, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv);

        dlPanel = new LinearLayout(this);
        dlPanel.setOrientation(LinearLayout.VERTICAL);
        dlPanel.setBackground(round(0xFFFFFFFF, 18));
        dlPanel.setElevation(dp(6));
        int q = dp(14);
        dlPanel.setPadding(q, dp(10), q, dp(10));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.bottomMargin = dp(10);
        plp.topMargin = dp(4);
        dlPanel.setLayoutParams(plp);
        dlPanel.setVisibility(View.GONE);

        dlName = new TextView(this);
        dlName.setTextSize(11.5f);
        dlName.setTextColor(C_SUB);
        dlName.setSingleLine(true);
        dlName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        dlPanel.addView(dlName);

        LinearLayout dlRow1 = new LinearLayout(this);
        dlRow1.setOrientation(LinearLayout.HORIZONTAL);
        dlSpeed = new TextView(this);
        dlSpeed.setTextSize(12);
        dlSpeed.setTextColor(C_SUB);
        dlRow1.addView(dlSpeed, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dlTime = new TextView(this);
        dlTime.setTextSize(12);
        dlTime.setTextColor(C_SUB);
        dlRow1.addView(dlTime);
        dlPanel.addView(dlRow1);

        dlBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        dlBar.setMax(100);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        blp2.topMargin = dp(8);
        blp2.bottomMargin = dp(6);
        dlBar.setLayoutParams(blp2);
        dlPanel.addView(dlBar);

        LinearLayout dlRow2 = new LinearLayout(this);
        dlRow2.setOrientation(LinearLayout.HORIZONTAL);
        dlPct = new TextView(this);
        dlPct.setTextSize(12.5f);
        dlPct.setTextColor(C_TEXT);
        dlPct.setTypeface(Typeface.DEFAULT_BOLD);
        dlRow2.addView(dlPct, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dlSize = new TextView(this);
        dlSize.setTextSize(12);
        dlSize.setTextColor(C_SUB);
        dlRow2.addView(dlSize);
        dlPanel.addView(dlRow2);

        root.addView(dlPanel);

        UpdateFlow.listener = new UpdateFlow.Listener() {
            @Override
            public void onDownloadStart(final String name, final long total) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        ui.removeCallbacks(hidePanel);
                        dlPanel.setVisibility(View.VISIBLE);
                        dlName.setText(name);
                        dlBar.setProgress(0);
                        dlPct.setText("0%");
                        dlSpeed.setText("0 B/s");
                        dlSize.setText(total > 0 ? "0 / " + fmtSize(total) : name);
                        dlTime.setText("已用 00:00 · 剩余 --");
                    }
                });
            }

            @Override
            public void onDownloadProgress(final long got, final long total,
                                           final long elapsed, final long eta, final long speed) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        dlPanel.setVisibility(View.VISIBLE);
                        int pct = total > 0 ? (int) (got * 100 / total) : 0;
                        dlBar.setProgress(pct);
                        dlPct.setText(pct + "%");
                        dlSpeed.setText(fmtSize(speed) + "/s");
                        dlSize.setText(fmtSize(got) + " / " + (total > 0 ? fmtSize(total) : "?"));
                        dlTime.setText("已用 " + fmtClock(elapsed)
                                + " · 剩余 " + (eta > 0 ? fmtClock(eta) : "--"));
                    }
                });
            }

            @Override
            public void onDownloadEnd(final boolean ok, final String message) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            dlPct.setText("100%");
                            dlSpeed.setText("下载完成");
                            dlTime.setText("等待安装…");
                        } else {
                            dlSpeed.setText("下载失败");
                        }
                        ui.removeCallbacks(hidePanel);
                        ui.postDelayed(hidePanel, 1500);
                    }
                });
            }
        };

        setContentView(root);

        Scheduler.schedule(this);
        cleanCache();
        askNotificationPermission();
        boolean seeded = seedIfNeeded();
        refresh();
        if (seeded) runCheckAsync();
    }

    private final Runnable softRefresh = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        ui.removeCallbacks(softRefresh);
        ui.postDelayed(softRefresh, 1500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(softRefresh);
    }

    /** 清掉 3 天前的缓存安装包。 */
    private void cleanCache() {
        try {
            File dir = new File(getFilesDir(), "updates");
            File[] fs = dir.listFiles();
            if (fs == null) return;
            long now = System.currentTimeMillis();
            for (int i = 0; i < fs.length; i++) {
                if (now - fs[i].lastModified() > 3L * 24L * 3600L * 1000L) fs[i].delete();
            }
        } catch (Exception ignored) {
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 9);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 9 && res.length > 0 && res[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "没有通知权限，每天的检查结果只能进 App 看", Toast.LENGTH_LONG).show();
        }
    }


    /** 首次启动预置三个试用对象，省得手动挑。 */
    private boolean seedIfNeeded() {
        if (getSharedPreferences("verwatch", MODE_PRIVATE).getBoolean("seeded", false)) return false;
        getSharedPreferences("verwatch", MODE_PRIVATE).edit().putBoolean("seeded", true).apply();
        String[][] defs = {
                {"com.github.metacubex.clash.meta", "MetaCubeX/ClashMetaForAndroid"},
                {"li.songe.gkd", "gkd-kit/gkd"},
                {"com.topjohnwu.magisk", "topjohnwu/Magisk"}
        };
        ArrayList<Store.Entry> add = new ArrayList<Store.Entry>();
        PackageManager pm = getPackageManager();
        for (int i = 0; i < defs.length; i++) {
            try {
                android.content.pm.PackageInfo pi = pm.getPackageInfo(defs[i][0], 0);
                Store.Entry e = new Store.Entry();
                e.pkg = defs[i][0];
                e.repo = defs[i][1];
                e.installed = pi.versionName == null ? "" : pi.versionName;
                e.label = String.valueOf(pm.getApplicationLabel(pi.applicationInfo));
                add.add(e);
            } catch (Exception ignored) {
            }
        }
        if (add.isEmpty()) return false;
        entries.clear();
        entries.addAll(Store.load(this));
        entries.addAll(add);
        Store.save(this, entries);
        return true;
    }


    /** 重新读本机版本：装完更新回来能立刻变，卸载了也能发现。 */
    private void syncInstalled() {
        PackageManager pm = getPackageManager();
        boolean changed = false;
        HashSet<String> visible = null;
        int visibleCount = 0;
        try {
            visible = new HashSet<String>();
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            for (int k = 0; k < list.size(); k++) {
                if (list.get(k).activityInfo != null) visible.add(list.get(k).activityInfo.packageName);
            }
            List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (int k = 0; k < apps.size(); k++) {
                if (apps.get(k).packageName != null) visible.add(apps.get(k).packageName);
            }
            visibleCount = visible.size();
        } catch (Throwable t) {
            visible = null;
        }
        for (int i = 0; i < entries.size(); i++) {
            Store.Entry e = entries.get(i);
            if (e.pkg.length() == 0) continue;
            String v = versionOf(pm, e.pkg);
            boolean known = v != null && v.length() > 0 && !"?".equals(v);
            if (known) {
                if (!v.equals(e.installed)) {
                    e.installed = v;
                    dropApk(e);
                    changed = true;
                }
            } else if (visible != null && visibleCount >= 20
                    && e.installed.length() > 0 && !visible.contains(e.pkg)) {
                // 系统里已经找不到这个包了
                e.installed = "";
                dropApk(e);
                changed = true;
            }
        }
        if (changed) Store.save(this, entries);
    }

    private void dropApk(Store.Entry e) {
        if (e.apkFile.length() > 0) {
            try {
                new File(new File(getFilesDir(), "updates"), e.apkFile).delete();
            } catch (Exception ignored) {
            }
        }
        e.apkFile = "";
    }

    public void refreshAfterInstall() {
        refresh();
    }

    // ---------- UI ----------

    private LinearLayout.LayoutParams weighted(int rightMargin) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.rightMargin = rightMargin;
        return l;
    }

    private Button actionButton(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13.5f);
        b.setTextColor(fg);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(12));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), g, null));
        b.setMinHeight(dp(42));
        b.setMinimumHeight(dp(42));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setStateListAnimator(null);
        return b;
    }

    private Button ghostButton(String text) {
        Button b = actionButton(text, 0x00000000, C_PRIMARY);
        b.setTextSize(12.5f);
        b.setMinHeight(dp(34));
        b.setMinimumHeight(dp(34));
        b.setPadding(dp(6), 0, dp(6), 0);
        return b;
    }

    private TextView pill(String text, int bg, int fg) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setTextColor(fg);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(20));
        tv.setBackground(g);
        tv.setPadding(dp(8), dp(3), dp(8), dp(3));
        return tv;
    }

    private String chipText(Store.Entry e) {
        if (e.repo.length() == 0) return "未配置";
        if (e.pkg.length() == 0 && e.error.length() == 0) return "未安装";
        if (e.installed.length() == 0 && e.error.length() == 0) return "未安装";
        if (e.error.length() > 0) return "失败";
        if (e.latestTag.length() == 0) return "待检查";
        int cmp = Store.compare(e.installed, e.latestTag);
        if (cmp < 0) return "有更新";
        if (cmp == 0) return "最新";
        if (cmp == 1) return "本机较新";
        return "无法比较";
    }

    private int[] chipColors(Store.Entry e) {
        if (e.repo.length() == 0) return new int[]{0xFFEEF1F5, 0xFF6B7280};
        if (e.pkg.length() == 0) return new int[]{0xFFE8EEFC, 0xFF3B6FF6};
        if (e.latestTag.length() == 0) return new int[]{0xFFEEF1F5, 0xFF6B7280};
        if (e.installed.length() == 0) return new int[]{0xFFE8EEFC, 0xFF3B6FF6};
        if (e.error.length() > 0) return new int[]{0xFFFDE8E6, 0xFFC0392B};
        int cmp = Store.compare(e.installed, e.latestTag);
        if (cmp < 0) return new int[]{0xFFFFEAD6, 0xFFC2620A};
        if (cmp == 0) return new int[]{0xFFE7F6EC, 0xFF1F8A46};
        if (cmp == 1) return new int[]{0xFFE8EEFC, 0xFF3B6FF6};
        return new int[]{0xFFEEF1F5, 0xFF6B7280};
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l.rightMargin = dp(6);
        return l;
    }


    /** 0 未配置 / 1 有更新 / 2 其他 / 3 已是最新。 */
    private int rank(Store.Entry e) {
        if (e.repo.length() == 0) return 0;
        if (e.pkg.length() == 0) return 1;
        if (e.error.length() > 0 || e.latestTag.length() == 0) return 3;
        int cmp = Store.compare(e.installed, e.latestTag);
        if (cmp < 0) return 2;
        if (cmp == 0) return 4;
        return 3;
    }

    /** 未配置 → 有更新 → 其他 → 已是最新（最新发布在前）。 */
    private boolean sortEntries() {
        final ArrayList<Store.Entry> before = new ArrayList<Store.Entry>(entries);
        final Collator col = Collator.getInstance(Locale.CHINA);
        Collections.sort(entries, new Comparator<Store.Entry>() {
            @Override
            public int compare(Store.Entry a, Store.Entry b) {
                int ra = rank(a);
                int rb = rank(b);
                if (ra != rb) return ra - rb;
                boolean ea = a.latestDate.length() == 0;
                boolean eb = b.latestDate.length() == 0;
                if (ea != eb) return ea ? 1 : -1;
                int d = b.latestDate.compareTo(a.latestDate);
                if (d != 0) return d;
                return col.compare(a.label, b.label);
            }
        });
        for (int i = 0; i < entries.size(); i++) {
            if (before.get(i) != entries.get(i)) return true;
        }
        return false;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }


    private static String fmtSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double k = bytes / 1024.0;
        if (k < 1024) return String.format(java.util.Locale.US, "%.1f KB", k);
        double m = k / 1024.0;
        if (m < 1024) return String.format(java.util.Locale.US, "%.1f MB", m);
        return String.format(java.util.Locale.US, "%.2f GB", m / 1024.0);
    }

    private static String fmtClock(long ms) {
        if (ms < 0) return "--";
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(java.util.Locale.US, "%02d:%02d", m, sec);
    }

    private void updateShizukuBtn() {
        if (shizukuBtn == null) return;
        int bg;
        int fg;
        if (!ShizukuHelper.alive()) {
            bg = 0xFFEEF1F5;
            fg = 0xFF6B7280;
        } else if (!ShizukuHelper.granted()) {
            bg = 0xFFFFEAD6;
            fg = 0xFFC2620A;
        } else {
            bg = 0xFFE7F6EC;
            fg = 0xFF1F8A46;
        }
        shizukuBtn.setText(ShizukuHelper.stateText());
        shizukuBtn.setTextColor(fg);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(12));
        shizukuBtn.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), g, null));
    }

    private void refresh() {
        entries.clear();
        entries.addAll(Store.load(this));
        syncInstalled();
        if (sortEntries()) Store.save(this, entries);
        cards.removeAllViews();
        if (entries.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("还没有监控任何应用。\n点「＋ 添加」选 App，然后在卡片里填它的 GitHub 仓库（形如 gkd-kit/gkd）。");
            hint.setTextSize(13);
            hint.setTextColor(C_SUB);
            hint.setPadding(dp(4), dp(20), dp(4), dp(20));
            cards.addView(hint);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                cards.addView(card(entries.get(i)));
            }
        }
        updateShizukuBtn();
        String lc = Store.lastCheck(this);
        subtitle.setText("下次 " + Scheduler.nextTenText() + "  ·  上次 " + (lc.length() == 0 ? "未检查" : lc));
    }

    private View card(final Store.Entry e) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(0xFFFFFFFF, 18));
        box.setElevation(dp(2));
        int p = dp(14);
        box.setPadding(p, dp(13), p, dp(13));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = dp(10);
        box.setLayoutParams(blp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(e.label.length() == 0 ? e.pkg : e.label);
        name.setTextSize(16);
        name.setTextColor(C_TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int[] cc = chipColors(e);
        head.addView(pill(chipText(e), cc[0], cc[1]));
        box.addView(head);

        TextView meta = new TextView(this);
        meta.setText(e.pkg + (e.installed.length() == 0 ? "" : "  ·  本机 " + e.installed));
        meta.setTextSize(11);
        meta.setTextColor(C_SUB);
        meta.setPadding(0, dp(4), 0, dp(4));
        box.addView(meta);

        final TextView state = new TextView(this);
        state.setTextSize(12.5f);
        state.setPadding(0, dp(6), 0, 0);

        final EditText repo = new EditText(this);
        repo.setHint("GitHub 仓库，如 gkd-kit/gkd");
        repo.setTextSize(13);
        repo.setSingleLine(true);
        repo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        repo.setText(e.repo);
        repo.setFocusable(false);
        repo.setFocusableInTouchMode(false);
        repo.setClickable(false);
        repo.setCursorVisible(false);
        repo.setTextColor(0xFF2B3138);

        final Button edit = ghostButton("编辑");
        final Button save = ghostButton("保存");
        save.setEnabled(false);
        save.setAlpha(0.4f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(repo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(edit);
        row.addView(save);
        box.addView(row);
        box.addView(state);

        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (repo.isFocusable()) {
                    repo.setText(e.repo);
                    setReadOnly(repo);
                    save.setEnabled(false);
                    save.setAlpha(0.4f);
                    edit.setText("编辑");
                } else {
                    repo.setFocusableInTouchMode(true);
                    repo.setFocusable(true);
                    repo.setClickable(true);
                    repo.setCursorVisible(true);
                    repo.requestFocus();
                    repo.setSelection(repo.getText().length());
                    save.setEnabled(true);
                    save.setAlpha(1f);
                    edit.setText("取消");
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(repo, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String v2 = repo.getText().toString().trim();
                if (v2.length() > 0 && !GitHub.validRepo(v2)) {
                    Toast.makeText(MainActivity.this, "格式应为 owner/repo", Toast.LENGTH_SHORT).show();
                    return;
                }
                e.repo = v2;
                e.error = "";
                e.latestTag = "";
                e.latestDate = "";
                e.etag = "";
                e.apkFile = "";
                Store.save(MainActivity.this, entries);
                setReadOnly(repo);
                save.setEnabled(false);
                save.setAlpha(0.4f);
                edit.setText("编辑");
                refresh();
                Toast.makeText(MainActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });

        if (e.latestTag.length() > 0
                && (e.pkg.length() == 0 || e.installed.length() == 0
                || Store.compare(e.installed, e.latestTag) < 0)) {
            boolean ready = UpdateFlow.apkReady(this, e);
            Button upd = actionButton(ready ? "安装 " + e.latestTag : "下载并安装 " + e.latestTag,
                    C_PRIMARY, 0xFFFFFFFF);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
            ulp.topMargin = dp(10);
            upd.setLayoutParams(ulp);
            upd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (UpdateFlow.apkReady(MainActivity.this, e)) {
                        UpdateFlow.installExisting(MainActivity.this, e);
                    } else {
                        UpdateFlow.start(MainActivity.this, e);
                    }
                }
            });
            box.addView(upd);
        }

        bindState(state, e);

        box.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmRemove(e);
                return true;
            }
        });
        return box;
    }

    private void setReadOnly(EditText et) {
        et.setFocusable(false);
        et.setFocusableInTouchMode(false);
        et.setClickable(false);
        et.setCursorVisible(false);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
    }

    private void bindState(TextView tv, Store.Entry e) {
        if (e.repo.length() == 0) {
            tv.setText("填好仓库后点「立即检查」");
            tv.setTextColor(C_SUB);
            return;
        }
        if (!GitHub.validRepo(e.repo)) {
            tv.setText("仓库格式应为 owner/repo");
            tv.setTextColor(C_RED);
            return;
        }
        if (e.error.length() > 0) {
            tv.setText("⚠ " + e.error);
            tv.setTextColor(C_RED);
            return;
        }
        if (e.pkg.length() == 0) {
            boolean ready = UpdateFlow.apkReady(this, e);
            if (e.latestTag.length() == 0) {
                tv.setText("手机上没装，点「立即检查」取最新版后可下载安装");
                tv.setTextColor(C_SUB);
            } else {
                tv.setText("未安装 · 最新 " + e.latestTag + (ready ? " · 已下载" : ""));
                tv.setTextColor(C_PRIMARY);
            }
            return;
        }
        if (e.latestTag.length() == 0) {
            tv.setText("还没检查过");
            tv.setTextColor(C_SUB);
            return;
        }
        String date = e.latestDate.length() == 0 ? "" : "（" + e.latestDate + "）";
        if (e.installed.length() == 0) {
            boolean ready = UpdateFlow.apkReady(this, e);
            tv.setText("读不到本机版本（没装，或被系统限制读应用列表）· 最新 "
                    + e.latestTag + date + (ready ? " · 已下载" : ""));
            tv.setTextColor(C_PRIMARY);
            return;
        }
        int cmp = Store.compare(e.installed, e.latestTag);
        if (cmp < 0) {
            boolean ready = UpdateFlow.apkReady(this, e);
            tv.setText("有新版本 " + e.latestTag + date + (ready ? " · 已下载" : ""));
            tv.setTextColor(C_ORANGE);
        } else if (cmp == 0) {
            tv.setText("已是最新 " + e.latestTag + date);
            tv.setTextColor(C_GREEN);
        } else if (cmp == 1) {
            tv.setText("本机比 Release 新（" + e.latestTag + "）");
            tv.setTextColor(C_SUB);
        } else {
            tv.setText("无法比较：本机 " + e.installed + " / 最新 " + e.latestTag);
            tv.setTextColor(C_SUB);
        }
    }


    /** 列表里没有的应用，可以直接输包名。 */
    private void showManualAdd() {
        final EditText input = new EditText(this);
        input.setHint("包名，如 md.obsidian");
        input.setSingleLine(true);
        input.setTextSize(14);
        new AlertDialog.Builder(this)
                .setTitle("手动添加")
                .setMessage("在 设置 → 应用 → 应用信息 里能看到包名")
                .setView(input)
                .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        addByPkg(input.getText().toString().trim());
                    }
                })
                .setNeutralButton("批量导入", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        showImportDialog();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }


    private void showImportDialog() {
        final EditText input = new EditText(this);
        input.setTextSize(13);
        input.setSingleLine(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(9);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("每行一条，仓库放最后：\n\ncom.topjohnwu.magisk topjohnwu/Magisk\nGKD gkd-kit/gkd\nShizuku RikkaApps/Shizuku\n\n前半段可以写包名，也可以写应用名（忽略大小写和空格）");
        new AlertDialog.Builder(this)
                .setTitle("批量导入")
                .setView(input)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        doImport(input.getText().toString());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doImport(String text) {
        int added = 0;
        int updated = 0;
        int dupRepo = 0;
        ArrayList<String> fails = new ArrayList<String>();
        String[] lines = text.split("\n");
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li].trim();
            if (line.length() == 0 || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            int repoIdx = -1;
            for (int i = parts.length - 1; i >= 0; i--) {
                if (GitHub.validRepo(parts[i])) {
                    repoIdx = i;
                    break;
                }
            }
            if (repoIdx <= 0) {
                fails.add(line);
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < repoIdx; i++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(parts[i]);
            }
            String key = sb.toString().trim();
            String pkg = resolvePkg(key);
            if (pkg == null && looksLikePackage(key)) pkg = key;
            if (pkg == null) pkg = "";
            if (key.length() == 0) {
                fails.add(line);
                continue;
            }
            Store.Entry target = null;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).pkg.equals(pkg)) target = entries.get(i);
            }
            String repo = GitHub.cleanRepo(parts[repoIdx]);
            if (target == null) {
                target = new Store.Entry();
                target.pkg = pkg;
                target.label = key;
                if (pkg.length() > 0) {
                    try {
                        PackageManager pm = getPackageManager();
                        android.content.pm.PackageInfo pi = pm.getPackageInfo(pkg, 0);
                        target.installed = pi.versionName == null ? "" : pi.versionName;
                        target.label = String.valueOf(pm.getApplicationLabel(pi.applicationInfo));
                    } catch (Exception ex) {
                        // 手机上没装：保留输入的名字，等下载后绑定包名
                    }
                }
                entries.add(target);
                added++;
            } else {
                if (target.repo.equals(repo)) dupRepo++;
                else updated++;
            }
            target.repo = repo;
            target.error = "";
            target.latestTag = "";
            target.latestDate = "";
            target.etag = "";
        }
        Store.save(this, entries);
        refresh();
        String msg = "新增 " + added + "，更新 " + updated;
        if (dupRepo > 0) msg += "，重复 " + dupRepo;
        if (!fails.isEmpty()) msg += "，失败 " + fails.size();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        if (!fails.isEmpty()) showFails(fails);
    }

    private void showFails(ArrayList<String> fails) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(fails.size(), 12);
        for (int i = 0; i < n; i++) sb.append("· ").append(fails.get(i)).append("\n");
        if (fails.size() > n) sb.append("…还有 ").append(fails.size() - n).append(" 行\n");
        new AlertDialog.Builder(this)
                .setTitle("这些没导入")
                .setMessage(sb.toString() + "\n没匹配到已安装应用，或缺少 owner/repo")
                .setPositiveButton("知道了", null)
                .show();
    }

    private boolean looksLikePackage(String s) {
        if (s == null || s.length() < 5 || s.indexOf(' ') >= 0 || s.indexOf('.') <= 0) return false;
        return s.matches("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+");
    }

    private String normName(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || Character.isLetter(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String resolvePkg(String key) {
        String k = key.trim();
        if (k.length() == 0) return null;
        boolean looksPkg = k.indexOf(' ') < 0 && k.indexOf('.') > 0;
        if (looksPkg) {
            try {
                if (getPackageManager().getPackageInfo(k, 0) != null) return k;
            } catch (Exception ignored) {
            }
        }
        String nk = normName(k);
        if (nk.length() == 0) return null;
        ArrayList<AppItem> apps = listApps();
        String best = null;
        int bestScore = 0;
        int bestDiff = 1 << 30;
        for (int i = 0; i < apps.size(); i++) {
            AppItem it = apps.get(i);
            String nl = normName(it.label);
            if (nl.length() == 0) continue;
            int score = 0;
            if (nl.equals(nk)) score = 100;
            else if (nl.startsWith(nk)) score = 80;
            else if (nl.contains(nk)) score = 60;
            else if (nk.contains(nl) && nl.length() >= 3) score = 40;
            if (score == 0) continue;
            int diff = Math.abs(nl.length() - nk.length());
            if (score > bestScore || (score == bestScore && diff < bestDiff)) {
                bestScore = score;
                bestDiff = diff;
                best = it.pkg;
            }
        }
        return best;
    }

    private String exportText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Store.Entry e = entries.get(i);
            if (e.repo.length() == 0) continue;
            sb.append(e.pkg.length() > 0 ? e.pkg : e.label).append(' ').append(e.repo).append('\n');
        }
        return sb.toString();
    }

    private void exportConfig() {
        String t = exportText();
        if (t.length() == 0) {
            Toast.makeText(this, "还没有可导出的（先给应用填上仓库）", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("verwatch", t));
        } catch (Exception ignored) {
        }
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        new AlertDialog.Builder(this)
                .setTitle("已复制到剪贴板（" + (t.split("\n").length) + " 条）")
                .setView(tv)
                .setPositiveButton("好", null)
                .show();
    }

    private void addByPkg(String pkg) {
        if (pkg.length() == 0 || pkg.indexOf(' ') >= 0) {
            Toast.makeText(this, "包名不对", Toast.LENGTH_SHORT).show();
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).pkg.equals(pkg)) {
                Toast.makeText(this, "已经在列表里了", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Store.Entry e = new Store.Entry();
        e.pkg = pkg;
        try {
            PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo pi = pm.getPackageInfo(pkg, 0);
            e.installed = pi.versionName == null ? "" : pi.versionName;
            e.label = String.valueOf(pm.getApplicationLabel(pi.applicationInfo));
        } catch (Exception ex) {
            e.label = pkg;
            e.error = "系统读不到这个包（可能没安装或不可见）";
        }
        entries.add(e);
        Store.save(this, entries);
        refresh();
        Toast.makeText(this, "已添加 " + pkg, Toast.LENGTH_SHORT).show();
    }

    private void confirmRemove(final Store.Entry e) {
        new AlertDialog.Builder(this)
                .setTitle("移除监控")
                .setMessage(e.label + "？")
                .setPositiveButton("移除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        entries.remove(e);
                        Store.save(MainActivity.this, entries);
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------- 检查 ----------

    private void runCheckAsync() {
        if (entries.isEmpty()) {
            Toast.makeText(this, "先添加要监控的应用", Toast.LENGTH_SHORT).show();
            return;
        }
        subtitle.setText("正在检查…");
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final String msg;
                try {
                    msg = Core.runCheck(MainActivity.this, true);
                } catch (Throwable t) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "检查失败：" + t, Toast.LENGTH_LONG).show();
                            refresh();
                        }
                    });
                    return;
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // ---------- 设置 ----------

    private void showSettings() {
        final String[] items = {
                "关闭电池优化（推荐）",
                "授予精确闹钟权限",
                "发送一条测试通知",
                "重新安排每天 10:00",
                "GitHub Token（限流时用）",
                "批量导入（文本）",
                "导出配置（复制）",
                Store.quiet(this) ? "通知：仅在有更新时" : "通知：每次都发",
                "诊断（读不到应用版本时用）",
                "关于自启动"
        };
        new AlertDialog.Builder(this)
                .setTitle("设置 · " + GitHub.rateText())
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        switch (which) {
                            case 0:
                                openBattery();
                                break;
                            case 1:
                                openExactAlarm();
                                break;
                            case 2:
                                Notifier.show(MainActivity.this, "测试通知",
                                        "通知正常。每天 10:00 会汇总推送检查结果。");
                                Toast.makeText(MainActivity.this, Notifier.canNotify(MainActivity.this)
                                        ? "已发送" : "没有通知权限", Toast.LENGTH_SHORT).show();
                                break;
                            case 3:
                                Scheduler.schedule(MainActivity.this);
                                refresh();
                                Toast.makeText(MainActivity.this,
                                        "已排到 " + Scheduler.nextTenText(), Toast.LENGTH_LONG).show();
                                break;
                            case 4:
                                showTokenDialog();
                                break;
                            case 5:
                                showImportDialog();
                                break;
                            case 6:
                                exportConfig();
                                break;
                            case 8:
                                showDiagnostics();
                                break;
                            case 7:
                                Store.setQuiet(MainActivity.this, !Store.quiet(MainActivity.this));
                                Toast.makeText(MainActivity.this,
                                        Store.quiet(MainActivity.this) ? "以后没更新就不打扰" : "每次检查都会通知",
                                        Toast.LENGTH_LONG).show();
                                break;
                            default:
                                showAutostart();
                                break;
                        }
                    }
                })
                .show();
    }


    private void showTokenDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(13);
        input.setText(Store.token(this));
        input.setHint("ghp_xxx，可留空");
        new AlertDialog.Builder(this)
                .setTitle("GitHub Token")
                .setMessage("不带 Token：60 次/小时（每个应用一次，检查 20 个应用一天要 20 次）。\n"
                        + "带只读 Token：5000 次/小时，基本不会碰到限流。\n\n"
                        + "生成：GitHub 网页 → Settings → Developer settings → Personal access tokens → "
                        + "Generate new token，不需要勾选任何权限。\n\n"
                        + "当前：" + GitHub.rateText())
                .setView(input)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Store.setToken(MainActivity.this, input.getText().toString().trim());
                        Toast.makeText(MainActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Store.setToken(MainActivity.this, "");
                        Toast.makeText(MainActivity.this, "已清空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e2) {
                Toast.makeText(this, "请手动到 设置 → 应用 → 版本监视 → 省电策略 设为无限制", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) {
            Toast.makeText(this, "当前系统不需要单独授权", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "请到 设置 → 应用 → 版本监视 → 闹钟和提醒 里打开", Toast.LENGTH_LONG).show();
        }
    }


    private void showDiagnostics() {
        StringBuilder sb = new StringBuilder();
        PackageManager pm = getPackageManager();
        sb.append("系统：Android ").append(Build.VERSION.RELEASE)
                .append(" / SDK ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("机型：").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        try {
            sb.append("自身版本：").append(versionOf(pm, getPackageName())).append("\n");
        } catch (Exception ignored) {
        }
        boolean qa;
        try {
            qa = checkSelfPermission("android.permission.QUERY_ALL_PACKAGES") == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            qa = false;
        }
        sb.append("QUERY_ALL_PACKAGES：").append(qa ? "已授予" : "未授予").append("\n");
        int launchers = -1;
        int packed = -1;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            launchers = pm.queryIntentActivities(i, 0).size();
        } catch (Exception ignored) {
        }
        try {
            packed = pm.getInstalledPackages(0).size();
        } catch (Exception ignored) {
        }
        sb.append("可见启动器应用：").append(launchers).append(" 个\n");
        sb.append("可见已安装包：").append(packed).append(" 个\n\n");
        for (int i = 0; i < entries.size() && i < 20; i++) {
            Store.Entry e = entries.get(i);
            if (e.pkg.length() == 0) continue;
            sb.append(e.pkg).append(" → ").append(versionOf(pm, e.pkg)).append("\n");
        }
        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextSize(12);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        new AlertDialog.Builder(this)
                .setTitle("诊断")
                .setView(tv)
                .setPositiveButton("好", null)
                .show();
    }

    private void showAutostart() {
        new AlertDialog.Builder(this)
                .setTitle("自启动")
                .setMessage("小米/红米：设置 → 应用设置 → 应用管理 → 版本监视 → 自启动 打开；\n"
                        + "再把「省电策略」设为「无限制」。\n\n"
                        + "不做这两步，手机深度休眠时 10:00 的检查可能被推迟。")
                .setPositiveButton("知道了", null)
                .show();
    }

    // ---------- 选择应用 ----------

    private String versionOf(PackageManager pm, String pkg) {
        try {
            return pm.getPackageInfo(pkg, 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private ArrayList<AppItem> listApps() {
        ArrayList<AppItem> out = new ArrayList<AppItem>();
        PackageManager pm = getPackageManager();
        HashSet<String> seen = new HashSet<String>();

        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        try {
            List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
            for (int k = 0; k < list.size(); k++) {
                ResolveInfo ri = list.get(k);
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg == null || pkg.equals(getPackageName()) || seen.contains(pkg)) continue;
                seen.add(pkg);
                AppItem it = new AppItem();
                it.pkg = pkg;
                try {
                    it.label = String.valueOf(ri.loadLabel(pm));
                } catch (Exception ex) {
                    it.label = pkg;
                }
                it.ver = versionOf(pm, pkg);
                out.add(it);
            }
        } catch (Exception ignored) {
        }

        // 没有启动器图标的已安装应用（个别 App 会这样）
        try {
            List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (int k = 0; k < apps.size(); k++) {
                android.content.pm.ApplicationInfo ai = apps.get(k);
                if (ai == null || ai.packageName == null) continue;
                if (seen.contains(ai.packageName) || ai.packageName.equals(getPackageName())) continue;
                if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                seen.add(ai.packageName);
                AppItem it = new AppItem();
                it.pkg = ai.packageName;
                try {
                    it.label = String.valueOf(pm.getApplicationLabel(ai));
                } catch (Exception ex) {
                    it.label = ai.packageName;
                }
                it.ver = versionOf(pm, ai.packageName);
                out.add(it);
            }
        } catch (Exception ignored) {
        }

        final Collator col = Collator.getInstance(Locale.CHINA);
        Collections.sort(out, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem a, AppItem b) {
                return col.compare(a.label, b.label);
            }
        });
        return out;
    }

    private void showPicker() {
        final ArrayList<AppItem> all = listApps();
        if (all.isEmpty()) {
            Toast.makeText(this, "读不到已安装应用列表", Toast.LENGTH_LONG).show();
            return;
        }
        final ArrayList<AppItem> filtered = new ArrayList<AppItem>(all);
        final HashSet<String> chosen = new HashSet<String>();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);

        EditText search = new EditText(this);
        search.setHint("搜索应用名或包名");
        search.setSingleLine(true);
        search.setTextSize(14);
        box.addView(search);

        final ListView lv = new ListView(this);
        lv.setChoiceMode(ListView.CHOICE_MODE_NONE);
        lv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(380)));

        final BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return filtered.size();
            }

            @Override
            public Object getItem(int i) {
                return filtered.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View cv, ViewGroup parent) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(4), dp(8), dp(4), dp(8));
                row.setGravity(Gravity.CENTER_VERTICAL);
                AppItem it = filtered.get(i);
                CheckBox cb = new CheckBox(MainActivity.this);
                cb.setChecked(chosen.contains(it.pkg));
                cb.setClickable(false);
                cb.setFocusable(false);
                row.addView(cb);
                TextView tv = new TextView(MainActivity.this);
                tv.setText(it.label + "\n" + it.pkg + "  ·  " + it.ver);
                tv.setTextSize(13);
                tv.setTextColor(C_TEXT);
                row.addView(tv, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                return row;
            }
        };
        lv.setAdapter(adapter);
        box.addView(lv);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(box)
                .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        addChosen(all, chosen);
                    }
                })
                .setNeutralButton("手动添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        showManualAdd();
                    }
                })
                .setNegativeButton("取消", null);
        final AlertDialog dlg = builder.create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                refreshTitle(dlg, chosen);
            }
        });
        dlg.show();

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                AppItem it = filtered.get(pos);
                if (!chosen.remove(it.pkg)) chosen.add(it.pkg);
                adapter.notifyDataSetChanged();
                refreshTitle(dlg, chosen);
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase();
                filtered.clear();
                for (int i = 0; i < all.size(); i++) {
                    AppItem it = all.get(i);
                    if (q.length() == 0 || it.label.toLowerCase().contains(q) || it.pkg.contains(q)) {
                        filtered.add(it);
                    }
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void refreshTitle(AlertDialog dlg, HashSet<String> chosen) {
        try {
            dlg.setTitle("选择应用（已选 " + chosen.size() + " 个）");
        } catch (Exception ignored) {
        }
    }

    private void addChosen(List<AppItem> all, HashSet<String> chosen) {
        if (chosen.isEmpty()) {
            Toast.makeText(this, "还没勾选：点一下应用行，左边的框会打勾", Toast.LENGTH_LONG).show();
            return;
        }
        int n = 0;
        int dup = 0;
        for (int i = 0; i < all.size(); i++) {
            AppItem it = all.get(i);
            if (!chosen.contains(it.pkg)) continue;
            boolean exists = false;
            for (int j = 0; j < entries.size(); j++) {
                if (entries.get(j).pkg.equals(it.pkg)) exists = true;
            }
            if (exists) {
                dup++;
                continue;
            }
            Store.Entry e = new Store.Entry();
            e.pkg = it.pkg;
            e.label = it.label;
            e.installed = it.ver;
            entries.add(e);
            n++;
        }
        Store.save(this, entries);
        refresh();
        if (n > 0) {
            Toast.makeText(this, "已添加 " + n + " 个，点卡片上的「编辑」填仓库", Toast.LENGTH_LONG).show();
        } else if (dup > 0) {
            Toast.makeText(this, "这些已经在列表里了", Toast.LENGTH_LONG).show();
        }
    }

}
