package com.dsh.verwatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** 读取 GitHub 最新正式 Release。 */
public class GitHub {

    public static volatile int remaining = -1;
    public static volatile long resetAt = 0;

    private static void applyAuth(HttpURLConnection c, String token) {
        c.setRequestProperty("User-Agent", "VerWatch/1.0 (Android)");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        if (token != null && token.trim().length() > 0) {
            c.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
    }

    private static void readQuota(HttpURLConnection c) {
        try {
            String r = c.getHeaderField("X-RateLimit-Remaining");
            String t = c.getHeaderField("X-RateLimit-Reset");
            if (r != null) remaining = Integer.parseInt(r.trim());
            if (t != null) resetAt = Long.parseLong(t.trim()) * 1000L;
        } catch (Exception ignored) {
        }
    }

    public static String rateText() {
        if (remaining < 0) return "额度未知";
        String s = "剩余额度 " + remaining + "/" + (remaining > 100 ? 5000 : 60);
        if (resetAt > System.currentTimeMillis()) {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm");
            s += "，" + f.format(new java.util.Date(resetAt)) + " 重置";
        }
        return s;
    }


    public static String cleanRepo(String repo) {
        if (repo == null) return "";
        String s = repo.trim();
        if (s.startsWith("https://github.com/")) s = s.substring("https://github.com/".length());
        if (s.startsWith("http://github.com/")) s = s.substring("http://github.com/".length());
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static boolean validRepo(String repo) {
        String s = cleanRepo(repo);
        int i = s.indexOf('/');
        return i > 0 && i < s.length() - 1 && s.indexOf(' ') < 0;
    }

    /** 返回 {tag, publishedAt}。 */
    public static String[] fetchLatest(String repo, String etag, String token) throws IOException {
        String path = "https://api.github.com/repos/" + cleanRepo(repo) + "/releases?per_page=30";
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(path).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            applyAuth(c, token);
            if (etag != null && etag.length() > 0) c.setRequestProperty("If-None-Match", etag);
            int code = c.getResponseCode();
            readQuota(c);
            if (code == 304) {
                return new String[]{null, null, etag};
            }
            if (code == 403 || code == 429) {
                throw new IOException("限流：" + rateText() + "，加 Token 可提到 5000 次/小时");
            }
            if (code == 451) {
                throw new IOException("仓库被下架（HTTP 451，通常因 DMCA）");
            }
            if (code == 404) {
                throw new IOException("仓库不存在或已改名");
            }
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            JSONArray arr = new JSONArray(read(c.getInputStream()));
            String bestTag = null;
            String bestDate = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optBoolean("draft") || o.optBoolean("prerelease")) continue;
                String tag = o.optString("tag_name", "");
                if (tag.length() == 0) continue;
                String date = o.optString("published_at", "");
                if (bestTag == null) {
                    bestTag = tag;
                    bestDate = date;
                    continue;
                }
                int cmp = Store.compare(tag, bestTag);
                if (cmp == 1) {
                    bestTag = tag;
                    bestDate = date;
                }
            }
            if (bestTag == null) {
                throw new IOException("没有正式 Release（可能只打了 tag）");
            }
            return new String[]{bestTag, bestDate, c.getHeaderField("ETag")};
        } catch (org.json.JSONException je) {
            throw new IOException("GitHub 返回内容无法解析");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    /** 取某个 Release 的 APK 资产：{name, url, size}。 */
    public static java.util.ArrayList<String[]> fetchAssets(String repo, String tag, String token) throws IOException {
        String path = "https://api.github.com/repos/" + cleanRepo(repo) + "/releases/tags/" + tag;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(path).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            applyAuth(c, token);
            int code = c.getResponseCode();
            readQuota(c);
            if (code == 403 || code == 429) throw new IOException("限流：" + rateText());
            if (code == 451) throw new IOException("仓库被下架（HTTP 451，通常因 DMCA）");
            if (code == 404) throw new IOException("找不到这个 Release（仓库改名？）");
            if (code != 200) throw new IOException("HTTP " + code);
            JSONObject rel = new JSONObject(read(c.getInputStream()));
            JSONArray assets = rel.optJSONArray("assets");
            java.util.ArrayList<String[]> out = new java.util.ArrayList<String[]>();
            if (assets == null) return out;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                String name = a.optString("name", "");
                String url = a.optString("browser_download_url", "");
                if (!name.toLowerCase().endsWith(".apk") || url.length() == 0) continue;
                out.add(new String[]{name, url, String.valueOf(a.optLong("size", 0))});
            }
            return out;
        } catch (org.json.JSONException je) {
            throw new IOException("Release 内容无法解析");
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
