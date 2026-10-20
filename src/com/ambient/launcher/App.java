package com.ambient.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
