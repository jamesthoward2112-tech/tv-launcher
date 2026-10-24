package uk.co.howardtv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A launchable app: its TV banner (or icon), name, and a glow colour taken from the banner.
 * TV inputs are tiles too, under the made-up package "input:" plus the input's id.
 */
final class App {
    final String pkg;
    final String label;
    final Drawable banner;  // null when the app has no TV banner; then icon is shown
    final Drawable icon;
    final int glow;
    final Intent launch;

    private App(String pkg, String label, Drawable banner, Drawable icon, int glow, Intent launch) {
        this.pkg = pkg;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
        this.glow = glow;
        this.launch = launch;
    }

    /** Every app with a TV (leanback) or phone launcher entry, except this one, by name. */
    static List<App> loadAll(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, ActivityInfo> found = new LinkedHashMap<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                ActivityInfo a = r.activityInfo;
                if (a.packageName.equals(ctx.getPackageName())) continue;
                if (!found.containsKey(a.packageName)) found.put(a.packageName, a);  // TV entry wins
            }
        }
        List<App> apps = new ArrayList<>();
        for (ActivityInfo a : found.values()) {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(a.packageName);
            if (launch == null) launch = pm.getLaunchIntentForPackage(a.packageName);
            if (launch == null) continue;
            Drawable banner = a.loadBanner(pm);
            if (banner == null) banner = a.applicationInfo.loadBanner(pm);
            Drawable icon = a.loadIcon(pm);
            String label = String.valueOf(a.applicationInfo.loadLabel(pm));
            apps.add(new App(a.packageName, label, banner, icon, sampleGlow(banner != null ? banner : icon),
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));
        }
        Collator c = Collator.getInstance();
        apps.sort((x, y) -> c.compare(x.label, y.label));
        return apps;
    }

    static final String INPUT = "input:";
    static final String ALL_APPS = "howardtv:all-applications";
    static final String MISSING = "missing:";

    static App placeholder(Context ctx, String id, String label, int tint) {
        return branded(ctx, MISSING + id, label, tint);
    }

    static App allApplications(Context ctx) {
        return new App(ALL_APPS, "All Applications", BrandTile.create(ctx, "allapps"),
                null, 0xFF80FF20, null);
    }

    /** Use one consistent logo-only card for favourites, retaining the installed launch intent. */
    static App favourite(Context ctx, App source, String id, String label, int tint) {
        return new App(source.pkg, label, BrandTile.create(ctx, id),
                source.icon, tint, source.launch);
    }

    /** Brand-only tiles show before a streaming app has been installed on the Shield. */
    private static App branded(Context ctx, String id, String label, int tint) {
        final int w = 380, h = 220;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xE6151B23);
        canvas.drawRoundRect(new RectF(0, 0, w, h), 25, 25, p);
        p.setColor(tint);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        canvas.drawRoundRect(new RectF(5, 5, w - 5, h - 5), 20, 20, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        p.setTextSize(label.length() > 12 ? 40 : label.length() > 8 ? 48 : 62);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFF6F7FA);
        String display = id.equals(ALL_APPS) ? "ALL APPS" : label;
        canvas.drawText(display, w / 2f, h / 2f - (p.ascent() + p.descent()) / 2f, p);
        BitmapDrawable banner = new BitmapDrawable(ctx.getResources(), bmp);
        return new App(id, label, banner, banner, tint, null);
    }

    /** HDMI 1 to 3 and Live TV, those the TV has, as tiles, each with its own colour. */
    static List<App> loadInputs(Context ctx) {
        List<App> tiles = new ArrayList<>();
        int hdmi = 0;
        for (Inputs in : Inputs.list(ctx)) {
            boolean live = in.label.equals("Live TV");
            if (!live && !(in.label.startsWith("HDMI ") && hdmi < 3)) continue;
            int tint = live ? 0xFFFF6B5A : new int[] {0xFF5AA0FF, 0xFFA57CFF, 0xFF3FD1C0}[hdmi++];
            tiles.add(new App(INPUT + in.id, in.label,
                    new InputBanner(ctx, live ? R.drawable.ic_live_tv : R.drawable.ic_hdmi, in.label, tint),
                    null, tint, in.intent));
        }
        return tiles;
    }

    /**
     * The banner's most vivid colour, brightened for use as a glow on black: pixels are
     * weighted by saturation and brightness so a logo's colour beats a grey background.
     */
    static int sampleGlow(Drawable d) {
        final int w = 32, h = 18;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(b));
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        b.recycle();
        float[] hsv = new float[3];
        double r = 0, g = 0, bl = 0, total = 0;
        for (int p : px) {
            if (Color.alpha(p) < 128) continue;
            Color.colorToHSV(p, hsv);
            double weight = hsv[1] * hsv[1] * hsv[2] + 0.002;
            r += Color.red(p) * weight;
            g += Color.green(p) * weight;
            bl += Color.blue(p) * weight;
            total += weight;
        }
        if (total == 0) return 0xFFB0B4C0;
        Color.colorToHSV(Color.rgb((int) (r / total), (int) (g / total), (int) (bl / total)), hsv);
        if (hsv[1] < 0.15f) return 0xFFB0B4C0;  // a grey banner gets a cool white glow
        hsv[1] = Math.min(1f, hsv[1] * 1.15f);
        hsv[2] = Math.max(hsv[2], 0.9f);
        return Color.HSVToColor(hsv);
    }
}
