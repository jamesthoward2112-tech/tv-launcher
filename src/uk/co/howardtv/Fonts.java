package uk.co.howardtv;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.util.SparseArray;

/** Bundled typefaces. Bebas Neue is OFL; Lemon Milk is supplied for personal use. */
final class Fonts {
    private static final SparseArray<Typeface> cache = new SparseArray<>();
    private static Typeface bebas;
    private static Typeface lemon;

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

    static Typeface bebas(AssetManager assets) {
        if (bebas == null) bebas = Typeface.createFromAsset(assets, "fonts/BebasNeue-Regular.ttf");
        return bebas;
    }

    static Typeface lemon(AssetManager assets) {
        if (lemon == null) lemon = Typeface.createFromAsset(assets, "fonts/LEMONMILK-Regular.otf");
        return lemon;
    }
}
