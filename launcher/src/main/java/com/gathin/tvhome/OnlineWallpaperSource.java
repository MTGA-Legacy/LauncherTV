package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Small API 19 compatible client for Wikimedia Commons' featured widescreen backgrounds. */
final class OnlineWallpaperSource {
    private static final String CATEGORY = "Category:Commons featured widescreen desktop backgrounds";
    private static final int MAX_RESULTS = 20;
    private static final int MAX_BYTES = 12 * 1024 * 1024;

    static final class Item {
        final String title;
        final String imageUrl;
        final String pageUrl;
        final String artist;
        final String license;

        Item(String title, String imageUrl, String pageUrl, String artist, String license) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.pageUrl = pageUrl;
            this.artist = artist;
            this.license = license;
        }

        String credit() {
            StringBuilder value = new StringBuilder();
            if (artist != null && !artist.isEmpty()) value.append(artist);
            if (license != null && !license.isEmpty()) {
                if (value.length() > 0) value.append(" · ");
                value.append(license);
            }
            return value.toString();
        }
    }

    private OnlineWallpaperSource() { }

    static List<Item> fetch() throws Exception {
        String query = "https://commons.wikimedia.org/w/api.php?action=query"
                + "&generator=categorymembers&gcmtitle=" + URLEncoder.encode(CATEGORY, "UTF-8")
                + "&gcmtype=file&gcmlimit=" + MAX_RESULTS
                + "&prop=imageinfo&iiprop=url|extmetadata&iiurlwidth=1920"
                + "&format=json&formatversion=2";
        JSONObject root = new JSONObject(read(new URL(query), false));
        JSONObject queryObject = root.optJSONObject("query");
        JSONArray pages = queryObject == null ? null : queryObject.optJSONArray("pages");
        List<Item> result = new ArrayList<>();
        if (pages == null) return result;
        for (int i = 0; i < pages.length(); i++) {
            JSONObject page = pages.optJSONObject(i);
            JSONArray info = page == null ? null : page.optJSONArray("imageinfo");
            JSONObject image = info == null ? null : info.optJSONObject(0);
            if (page == null || image == null) continue;
            String imageUrl = image.optString("thumburl", image.optString("url", ""));
            if (imageUrl.isEmpty()) continue;
            JSONObject metadata = image.optJSONObject("extmetadata");
            String artist = metadata == null ? "" : metadata.optString("Artist", "");
            String license = metadata == null ? "" : metadata.optString("LicenseShortName", "");
            result.add(new Item(cleanTitle(page.optString("title", "Wallpaper")), imageUrl,
                    "https://commons.wikimedia.org/wiki/" + encodeTitle(page.optString("title", "")),
                    stripMarkup(artist), stripMarkup(license)));
        }
        return result;
    }

    static void download(Context context, Item item) throws Exception {
        File target = new File(context.getFilesDir(), Config.ONLINE_WALLPAPER_FILE);
        File temp = new File(context.getFilesDir(), Config.ONLINE_WALLPAPER_FILE + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(item.imageUrl).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("User-Agent", "LauncherTV/3.6 (Wikimedia Commons wallpaper)");
        connection.connect();
        if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300)
            throw new java.io.IOException("HTTP " + connection.getResponseCode());
        int length = connection.getContentLength();
        if (length > MAX_BYTES) throw new java.io.IOException("Image too large");
        int total = 0;
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) throw new java.io.IOException("Image too large");
                output.write(buffer, 0, count);
            }
        } finally {
            connection.disconnect();
        }
        if (total == 0 || !temp.renameTo(target)) {
            temp.delete();
            throw new java.io.IOException("Could not save wallpaper");
        }
    }

    private static String read(URL url, boolean image) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("User-Agent", "LauncherTV/3.6 (Wikimedia Commons wallpaper)");
        connection.connect();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
            return text.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String cleanTitle(String title) {
        return title.startsWith("File:") ? title.substring(5) : title;
    }

    private static String encodeTitle(String title) throws Exception {
        return URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20");
    }

    private static String stripMarkup(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", "").trim();
    }
}
