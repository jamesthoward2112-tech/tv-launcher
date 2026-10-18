package com.ambient.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Blocking HTTP helpers for the background threads, plus the TV-side config.json. */
final class Net {
    private Net() {}

    static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Downloads url to f via a .part file, so a dropped connection leaves nothing half-written. */
    static void download(String url, File f) throws IOException {
        File tmp = new File(f.getPath() + ".part");
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        }
        if (!tmp.renameTo(f)) throw new IOException("rename failed");
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "TvLauncher/0.1");
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode() + " " + url);
        return c;
    }

    /**
     * config.json in the app's external files dir: {"unsplashKey", "lat", "lon"}. It is put
     * there over adb, so the key never ships in the APK. Missing or broken means {}.
     */
    static JSONObject config(android.content.Context ctx) {
        File f = new File(ctx.getExternalFilesDir(null), "config.json");
        try (InputStream in = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
