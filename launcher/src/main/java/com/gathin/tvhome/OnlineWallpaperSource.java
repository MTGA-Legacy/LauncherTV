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

/** API 19 compatible online wallpaper sources, with a China-friendly Bing first path. */
final class OnlineWallpaperSource {
    static final int DAILY = 0;
    static final int LANDSCAPES = 1;
    static final int PEOPLE = 2;
    static final int ANIMALS = 3;
    static final int ARCHITECTURE = 4;
    static final int ASTRONOMY = 5;

    private static final String BING_ENDPOINT = "https://cn.bing.com/HPImageArchive.aspx"
            + "?format=js&idx=0&n=8&mkt=zh-CN&uhd=1&uhdwidth=1920&uhdheight=1080";
    private static final String[] COMMONS_CATEGORIES = {
            "Category:Commons featured widescreen desktop backgrounds",
            "Category:Featured pictures of landscapes",
            "Category:Featured pictures of people",
            "Category:Featured pictures of animals",
            "Category:Featured pictures of architecture",
            "Category:Featured pictures of astronomy"
    };
    private static final int MAX_RESULTS = 20;
    private static final int MAX_BYTES = 12 * 1024 * 1024;

    static final class Item {
        final String title;
        final String imageUrl;
        final String pageUrl;
        final String artist;
        final String license;
        final String provider;
        final String preset;
        final String date;
        final String copyright;

        Item(String title, String imageUrl, String pageUrl, String artist, String license,
             String provider, String preset, String date, String copyright) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.pageUrl = pageUrl;
            this.artist = artist;
            this.license = license;
            this.provider = provider;
            this.preset = preset;
            this.date = date;
            this.copyright = copyright;
        }

        String credit() {
            if (copyright != null && !copyright.isEmpty()) return copyright;
            StringBuilder value = new StringBuilder();
            if (artist != null && !artist.isEmpty()) value.append(artist);
            if (license != null && !license.isEmpty()) {
                if (value.length() > 0) value.append(" · ");
                value.append(license);
            }
            return value.toString();
        }

        String sourceLabel() {
            return "bing".equals(provider) ? "Bing" : "Wikimedia Commons";
        }
    }

    private OnlineWallpaperSource() { }

    /** Daily uses Bing China first, then Commons; themed presets use Commons directly. */
    static List<Item> fetch(int preset) throws Exception {
        if (preset == DAILY) {
            try {
                List<Item> bing = fetchBing();
                if (!bing.isEmpty()) return bing;
            } catch (Exception ignored) {
                // Commons is the explicit fallback when Bing is unavailable.
            }
        }
        return fetchCommons(preset);
    }

    private static List<Item> fetchBing() throws Exception {
        JSONObject root = new JSONObject(read(new URL(BING_ENDPOINT)));
        JSONArray images = root.optJSONArray("images");
        List<Item> result = new ArrayList<>();
        if (images == null) return result;
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null) continue;
            String imageUrl = absoluteBingUrl(image.optString("url", ""));
            if (imageUrl.isEmpty()) {
                String urlbase = image.optString("urlbase", "");
                imageUrl = urlbase.isEmpty() ? "" : absoluteBingUrl(urlbase + "_1920x1080.jpg");
            }
            if (imageUrl.isEmpty()) continue;
            result.add(new Item(image.optString("title", "Bing"), imageUrl,
                    image.optString("copyrightlink", "https://cn.bing.com/"), "", "",
                    "bing", "daily", image.optString("startdate", ""),
                    image.optString("copyright", "")));
        }
        return result;
    }

    private static List<Item> fetchCommons(int preset) throws Exception {
        String category = COMMONS_CATEGORIES[Math.max(0, Math.min(preset, COMMONS_CATEGORIES.length - 1))];
        String query = "https://commons.wikimedia.org/w/api.php?action=query"
                + "&generator=categorymembers&gcmtitle=" + URLEncoder.encode(category, "UTF-8")
                + "&gcmtype=file&gcmlimit=" + MAX_RESULTS
                + "&prop=imageinfo&iiprop=url|extmetadata&iiurlwidth=1920"
                + "&format=json&formatversion=2";
        JSONObject root = new JSONObject(read(new URL(query)));
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
            String title = cleanTitle(page.optString("title", "Wallpaper"));
            result.add(new Item(title, imageUrl,
                    "https://commons.wikimedia.org/wiki/" + encodeTitle(page.optString("title", "")),
                    stripMarkup(artist), stripMarkup(license), "commons", presetKey(preset), "", ""));
        }
        return result;
    }

    static String presetKey(int preset) {
        switch (preset) {
            case LANDSCAPES: return "landscapes";
            case PEOPLE: return "people";
            case ANIMALS: return "animals";
            case ARCHITECTURE: return "architecture";
            case ASTRONOMY: return "astronomy";
            default: return "daily";
        }
    }

    static void download(Context context, Item item) throws Exception {
        File target = new File(context.getFilesDir(), Config.ONLINE_WALLPAPER_FILE);
        File temp = new File(context.getFilesDir(), Config.ONLINE_WALLPAPER_FILE + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(item.imageUrl).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(25_000);
        connection.setRequestProperty("User-Agent", "LauncherTV/3.6 (wallpaper; contact repository maintainer)");
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

    private static String read(URL url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("User-Agent", "LauncherTV/3.6 (wallpaper; contact repository maintainer)");
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

    private static String absoluteBingUrl(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.startsWith("http://") || value.startsWith("https://")
                ? value : "https://cn.bing.com" + (value.startsWith("/") ? value : "/" + value);
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
