package uk.co.howardtv;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * HowardTV's approved local cinema wallpaper. Never replaces it with online photos.
 */
final class Backdrop {
    private static final long LIST_TTL = 6 * 3600_000L;

    private final ImageView[] layers;
    private final TextView credit;
    private final LauncherSettings settings;
    private final SharedPreferences prefs;
    private final File dir;
    private final ExecutorService net;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private String key = "";
    private int front;
    private boolean started, busy, shown;

    Backdrop(HomeActivity act, ImageView a, ImageView b, TextView credit, LauncherSettings settings, ExecutorService net) {
        this.layers = new ImageView[] {a, b};
        this.credit = credit;
        this.settings = settings;
        this.net = net;
        prefs = act.getSharedPreferences("backdrop", 0);
        dir = new File(act.getCacheDir(), "photos");
        dir.mkdirs();
        // Behind the content frame (z 0), the incoming layer above the outgoing one.
        a.setTranslationZ(-2);
        b.setTranslationZ(-1);
        try (InputStream wallpaper = act.getAssets().open("cinema_wallpaper.jpg")) {
            a.setImageBitmap(BitmapFactory.decodeStream(wallpaper));
            a.setAlpha(1f);
            shown = true;
        } catch (Exception e) {
            android.util.Log.e("HowardTV", "Cinema wallpaper unavailable", e);
        }
        credit.setText("");
    }

    void setKey(String unsplashKey) {
        key = unsplashKey == null ? "" : unsplashKey;
    }

    void start() {
        started = true;
        apply(false);
    }

    void stop() {
        started = false;
        ui.removeCallbacks(tick);
    }

    /** Re-reads the settings; next is true when the photo should change now (a new theme). */
    void apply(boolean next) {
        ui.removeCallbacks(tick);
        // Settings may toggle the cinema art off, but it must never download replacements.
        layers[0].animate().alpha(settings.photos() ? 1f : 0f).setDuration(180).start();
        layers[1].setAlpha(0f);
        credit.setText("");
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!started || !settings.photos()) return;
            next();
            ui.postDelayed(this, Long.parseLong(settings.get(LauncherSettings.PHOTO_MINUTES)) * 60_000L);
        }
    };

    /** The last photo, straight away, so home never opens on black while the next loads. */
    private void showLast() {
        File f = new File(prefs.getString("file", ""));
        String who = prefs.getString("credit", "");
        if (!f.isFile()) return;
        net.execute(() -> {
            Bitmap bmp = decode(f);
            if (bmp != null) ui.post(() -> { if (!shown) show(bmp, who, false); });
        });
    }

    private void next() {
        if (busy) return;
        busy = true;
        final String slot = slotKey();
        net.execute(() -> {
            try {
                JSONArray list = photoList(slot);
                if (list.length() == 0) return;
                String last = prefs.getString("url", "");
                JSONObject p = list.getJSONObject(random.nextInt(list.length()));
                if (list.length() > 1 && p.getString("url").equals(last))
                    p = list.getJSONObject(random.nextInt(list.length()));
                File f = new File(dir, Integer.toHexString(p.getString("url").hashCode()) + ".jpg");
                if (!f.isFile()) Net.download(p.getString("url"), f);
                f.setLastModified(System.currentTimeMillis());
                prune();
                Bitmap bmp = decode(f);
                if (bmp == null) return;
                String who = p.optString("credit");
                prefs.edit().putLong("at", System.currentTimeMillis()).putString("file", f.getPath())
                        .putString("url", p.getString("url")).putString("credit", who).apply();
                ui.post(() -> { if (settings.photos()) show(bmp, who, true); });
            } catch (Exception e) {
                android.util.Log.w("Backdrop", "photo failed: " + e);
            } finally {
                busy = false;
            }
        });
    }

    private void show(Bitmap bmp, String who, boolean fade) {
        ImageView in = layers[1 - front], out = layers[front];
        in.setImageBitmap(bmp);
        in.setTranslationZ(-1);
        out.setTranslationZ(-2);
        in.setAlpha(fade ? 0f : 1f);
        in.animate().alpha(1f).setDuration(fade ? 1500 : 0).withEndAction(() -> out.setImageDrawable(null)).start();
        front = 1 - front;
        credit.setText(who);
        shown = true;
    }

    // ---- photo lists, cached per theme slot like the screensaver does

    private String slotKey() {
        String theme = settings.get(LauncherSettings.THEME);
        if (key.isEmpty()) return "bing";
        if (!theme.equals("time")) return theme;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY), k = LauncherSettings.SLOT_HOURS.length - 1;
        for (int i = 0; i < LauncherSettings.SLOT_HOURS.length; i++) if (h >= LauncherSettings.SLOT_HOURS[i]) k = i;
        return "time" + k;
    }

    private JSONArray photoList(String slot) throws Exception {
        File cache = new File(dir, "list-" + slot + ".json");
        if (cache.isFile() && System.currentTimeMillis() - cache.lastModified() < LIST_TTL)
            return new JSONArray(read(cache));
        JSONArray list = new JSONArray();
        try {
            if (slot.equals("bing")) {
                JSONArray imgs = new JSONObject(Net.get(
                        "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US")).getJSONArray("images");
                for (int i = 0; i < imgs.length(); i++) {
                    JSONObject im = imgs.getJSONObject(i);
                    list.put(new JSONObject().put("url", "https://www.bing.com" + im.getString("urlbase") + "_1920x1080.jpg")
                            .put("credit", im.optString("copyright").replaceAll(".*\\((©[^)]*)\\).*", "$1")));
                }
            } else {
                String[] qs = queries(slot);
                String q = qs[random.nextInt(qs.length)];
                JSONArray ps = new JSONArray(Net.get("https://api.unsplash.com/photos/random?count=30"
                        + "&orientation=landscape&content_filter=high&query=" + URLEncoder.encode(q, "UTF-8")
                        + "&client_id=" + key));
                for (int i = 0; i < ps.length(); i++) {
                    JSONObject p = ps.getJSONObject(i);
                    list.put(new JSONObject()
                            .put("url", p.getJSONObject("urls").getString("raw") + "&w=1920&h=1080&fit=crop&q=80")
                            .put("credit", "Photo: " + p.getJSONObject("user").getString("name") + " / Unsplash"));
                }
            }
        } catch (Exception e) {
            if (cache.isFile()) return new JSONArray(read(cache));  // stale beats nothing
            throw e;
        }
        if (list.length() > 0) write(cache, list.toString());
        return list;
    }

    private static String[] queries(String slot) {
        if (slot.startsWith("time")) return LauncherSettings.SLOT_QUERIES[Integer.parseInt(slot.substring(4))];
        for (String[] t : LauncherSettings.THEMES)
            if (t[0].equals(slot)) return Arrays.copyOfRange(t, 2, t.length);
        return LauncherSettings.SLOT_QUERIES[1];
    }

    // ---- files

    /** Decoded at screen size in RGB_565: 4 MB a photo instead of 8. */
    private static Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= 1920) o.inSampleSize *= 2;
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** Keeps the 30 newest photos. */
    private void prune() {
        File[] fs = dir.listFiles((d, n) -> n.endsWith(".jpg"));
        if (fs == null || fs.length <= 30) return;
        Arrays.sort(fs, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
        for (int i = 30; i < fs.length; i++) fs[i].delete();
    }

    private static String read(File f) throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void write(File f, String s) throws java.io.IOException {
        java.nio.file.Files.write(f.toPath(), s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
