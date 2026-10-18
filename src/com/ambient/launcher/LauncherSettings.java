package com.ambient.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** The launcher's own settings, changed from the Settings dropdown and kept in prefs. */
final class LauncherSettings {
    static final class Option {
        final String key, label, def;
        final String[] values, names;

        Option(String key, String label, String def, String[] values, String[] names) {
            this.key = key;
            this.label = label;
            this.def = def;
            this.values = values;
            this.names = names;
        }
    }

    // Same Unsplash themes as the ambient screensaver.
    static final String[][] THEMES = {
        {"time", "Time of day"},
        {"mountains", "Mountains", "mountain landscape", "alpine peaks", "mountain lake", "snowy mountains"},
        {"coast", "Ocean & coast", "ocean coastline", "beach aerial", "sea cliffs", "ocean waves"},
        {"forest", "Forests", "forest landscape", "misty forest", "autumn forest", "forest aerial"},
        {"city", "Cities", "city skyline", "city aerial", "city street", "blue hour city"},
        {"stars", "Night sky", "night sky stars landscape", "milky way", "aurora borealis", "starry night mountains"},
        {"architecture", "Architecture", "modern architecture", "historic architecture", "bridge architecture", "cathedral interior"},
        {"wildlife", "Wildlife", "wildlife", "animals in the wild", "birds in nature", "safari animals"},
    };
    // "Time of day": each slot runs from its hour until the next one's.
    static final int[] SLOT_HOURS = {5, 11, 17, 20};
    static final String[][] SLOT_QUERIES = {
        {"sunrise landscape", "misty morning mountains", "morning lake reflection"},
        {"landscape nature", "mountain lake", "coastline aerial", "forest valley"},
        {"sunset landscape", "golden hour mountains", "sunset coast"},
        {"city skyline night", "night sky stars landscape", "blue hour city"},
    };

    static final Option BACKGROUND = new Option("background", "Background", "photo",
            new String[] {"photo", "black"}, new String[] {"Photos", "Black"});
    static final Option THEME;
    static final Option PHOTO_MINUTES = new Option("photoMinutes", "Change photo every", "15",
            new String[] {"5", "15", "60", "1440"}, new String[] {"5 minutes", "15 minutes", "1 hour", "Day"});
    static final Option WEATHER = new Option("weather", "Weather", "on",
            new String[] {"on", "off"}, new String[] {"On", "Off"});
    static final Option UNITS = new Option("units", "Temperature", "C",
            new String[] {"C", "F"}, new String[] {"°C", "°F"});

    static {
        String[] v = new String[THEMES.length], n = new String[THEMES.length];
        for (int i = 0; i < THEMES.length; i++) { v[i] = THEMES[i][0]; n[i] = THEMES[i][1]; }
        THEME = new Option("theme", "Photo theme", "time", v, n);
    }

    private final SharedPreferences prefs;

    LauncherSettings(Context ctx) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    String get(Option o) {
        return prefs.getString(o.key, o.def);
    }

    /** Steps o's value by dir, wrapping; returns the new value. */
    String step(Option o, int dir) {
        int i = 0;
        for (int j = 0; j < o.values.length; j++) if (o.values[j].equals(get(o))) i = j;
        String v = o.values[(i + dir + o.values.length) % o.values.length];
        prefs.edit().putString(o.key, v).apply();
        return v;
    }

    String name(Option o) {
        String v = get(o);
        for (int i = 0; i < o.values.length; i++) if (o.values[i].equals(v)) return o.names[i];
        return v;
    }

    /** The rows the dropdown shows now; the theme and interval only matter with photos. */
    List<Option> visible() {
        List<Option> l = new ArrayList<>();
        l.add(BACKGROUND);
        if (photos()) { l.add(THEME); l.add(PHOTO_MINUTES); }
        l.add(WEATHER);
        if (get(WEATHER).equals("on")) l.add(UNITS);
        return l;
    }

    boolean photos() {
        return get(BACKGROUND).equals("photo");
    }
}
