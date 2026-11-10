package com.dsh.verwatch;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 配置存储 + 版本号处理。 */
public class Store {

    public static class Entry {
        public String pkg = "";
        public String label = "";
        public String repo = "";
        public String installed = "";
        public String latestTag = "";
        public String latestDate = "";
        public String error = "";
        public String etag = "";
        public String apkFile = "";
        public long checkedAt = 0;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("pkg", pkg);
                o.put("label", label);
                o.put("repo", repo);
                o.put("installed", installed);
                o.put("latestTag", latestTag);
                o.put("latestDate", latestDate);
                o.put("error", error);
                o.put("checkedAt", checkedAt);
                o.put("etag", etag);
                o.put("apkFile", apkFile);
            } catch (Exception ignored) {
            }
            return o;
        }

        public static Entry from(JSONObject o) {
            Entry e = new Entry();
            e.pkg = o.optString("pkg");
            e.label = o.optString("label");
            e.repo = o.optString("repo");
            e.installed = o.optString("installed");
            e.latestTag = o.optString("latestTag");
            e.latestDate = o.optString("latestDate");
            e.error = o.optString("error");
            e.checkedAt = o.optLong("checkedAt");
            e.etag = o.optString("etag");
            e.apkFile = o.optString("apkFile");
            return e;
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("verwatch", Context.MODE_PRIVATE);
    }

    public static List<Entry> load(Context c) {
        ArrayList<Entry> out = new ArrayList<Entry>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("apps", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(Entry.from(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context c, List<Entry> list) {
        JSONArray arr = new JSONArray();
        for (Entry e : list) {
            arr.put(e.toJson());
        }
        sp(c).edit().putString("apps", arr.toString()).commit();
    }

    public static String token(Context c) {
        return sp(c).getString("token", "");
    }

    public static void setToken(Context c, String v) {
        sp(c).edit().putString("token", v).apply();
    }

    public static boolean quiet(Context c) {
        return sp(c).getBoolean("quiet", true);
    }

    public static void setQuiet(Context c, boolean v) {
        sp(c).edit().putBoolean("quiet", v).apply();
    }

    public static String lastCheck(Context c) {
        return sp(c).getString("lastCheck", "");
    }

    public static void setLastCheck(Context c, String v) {
        sp(c).edit().putString("lastCheck", v).apply();
    }

    /** 去掉 v 前缀、空格、括号后缀等噪声，保留数字与点。 */
    public static String norm(String v) {
        if (v == null) return "";
        String s = v.trim().toLowerCase();
        if (s.startsWith("v")) s = s.substring(1);
        int cut = s.length();
        char[] seps = {' ', '(', '+', ',', '&', '-'};
        for (int i = 0; i < seps.length; i++) {
            int p = s.indexOf(seps[i]);
            if (p > 0 && p < cut) cut = p;
        }
        s = s.substring(0, cut);
        // 只留开头的数字和点：13.6.0.r1086.2650830c → 13.6.0；0.9.114 Simulacrum → 0.9.114
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if ((ch >= '0' && ch <= '9') || ch == '.') i++;
            else break;
        }
        s = s.substring(0, i);
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static int[] nums(String v) {
        String s = norm(v);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
            else sb.append('.');
        }
        String[] parts = sb.toString().split("\\.+");
        ArrayList<Integer> tmp = new ArrayList<Integer>();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() > 0 && tmp.size() < 6) {
                try {
                    tmp.add(Integer.parseInt(parts[i]));
                } catch (Exception e) {
                    tmp.add(0);
                }
            }
        }
        int[] r = new int[tmp.size()];
        for (int i = 0; i < r.length; i++) r[i] = tmp.get(i);
        return r;
    }

    /** -1 本机更旧，0 相同，1 本机更新，2 无法判断。 */
    public static int compare(String installed, String release) {
        int[] a = nums(installed);
        int[] b = nums(release);
        if (a.length == 0 || b.length == 0) return 2;
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }

    public static String shortDate(String iso) {
        if (iso == null || iso.length() < 10) return "";
        return iso.substring(0, 10);
    }
}
