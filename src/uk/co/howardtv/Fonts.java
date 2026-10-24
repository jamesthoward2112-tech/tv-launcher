package uk.co.howardtv;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.util.SparseArray;

/** Outfit (SIL OFL, assets/fonts) is a variable font; one Typeface per weight in use. */
final class Fonts {
    private static final SparseArray<Typeface> cache = new SparseArray<>();

    private Fonts() {}

    static Typeface outfit(AssetManager assets, int weight) {
        Typeface t = cache.get(weight);
        if (t == null) {
            t = new Typeface.Builder(assets, "fonts/Outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight).build();
            cache.put(weight, t);
        }
        return t;
    }
}
