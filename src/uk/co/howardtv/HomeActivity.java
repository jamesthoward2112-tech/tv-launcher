package uk.co.howardtv;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONObject;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The home screen. Focus is a small model (area + index) driven straight from D-pad keys,
 * not Android's focus search, so every move is predictable: Up/Down change exactly one row,
 * Up from the dock reaches the top bar, and the ends of a row stop instead of wrapping.
 */
public class HomeActivity extends Activity {
    /** The TV app, left out of the grid when the Live TV tile stands in for it. */
    private static final String TV_APP = "com.tcl.tv";
    private static final int COLUMNS = 4, DOCK_SIZE = 8;
    private static final float TILE_SCALE = 1.08f, PILL_SCALE = 1.06f;

    private enum Area { TOP, DOCK, GRID, MENU }

    /** Which dropdown MENU is: the Inputs list or the launcher settings. */
    private enum Menu { INPUTS, SETTINGS }

    private static final long WEATHER_EVERY = 30 * 60_000L;

    private Area area = Area.DOCK;
    private int topIdx, dockIdx, gridIdx, menuIdx;
    private boolean resumed, started, centerDown, longPressed;
    /** All apps in user order: the first DOCK_SIZE are the dock, the rest the grid. */
    private final List<App> order = new ArrayList<>();
    private boolean moving;  // rearranging: the focused app follows the arrows
    private ObjectAnimator wiggle;
    private TextView hint;
    private Menu menuKind = Menu.INPUTS;
    private List<LauncherSettings.Option> menuOptions = new ArrayList<>();

    private FrameLayout content;
    private GlassHighlight glass;
    private GlowView glow;
    private TextView title, greeting, clock, date, wifiName;
    private View[] pills;
    private LinearLayout menu;
    private RecyclerView dockView, gridView;
    private TileAdapter dockAdapter, gridAdapter;
    private final List<Inputs> inputs = new ArrayList<>();
    private View focusedBanner, focusedName, focusedPill;
    private ValueAnimator scroller;
    private float scroll;
    private int gridTop, pitch;

    private SharedPreferences prefs;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LauncherSettings settings;
    private Backdrop backdrop;
    private TextView weather;
    private LinearLayout agendaView;
    /** The calendar feeds in config.json, and their events once loaded. */
    private final List<String> icsUrls = new ArrayList<>();
    private Agenda agenda;
    private View dim, credit, band;
    /** Gillingham, Kent; the optional on-device config can override this. */
    private double lat = 51.3867, lon = 0.5471;
    private ConnectivityManager connectivity;
    /** Picking the input to wake to; null once it opened or the viewer took over. */
    private WakeRouter wakeRouter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        configurePhonePreview();
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        gridTop = getResources().getDimensionPixelSize(R.dimen.grid_top);
        pitch = getResources().getDimensionPixelSize(R.dimen.grid_pitch);
        content = findViewById(R.id.content);
        glass = findViewById(R.id.glass);
        glow = findViewById(R.id.glow);
        title = findViewById(R.id.title);
        greeting = findViewById(R.id.greeting);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        wifiName = findViewById(R.id.wifi_name);
        menu = findViewById(R.id.menu);
        weather = findViewById(R.id.weather);
        agendaView = findViewById(R.id.agenda);
        dim = findViewById(R.id.dim);
        credit = findViewById(R.id.credit);
        band = findViewById(R.id.band);
        try (InputStream in = getAssets().open("HowardTV-logo.png")) {
            ((ImageView) findViewById(R.id.howardtv_logo)).setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception e) {
            android.util.Log.e("HowardTV", "Logo unavailable", e);
        }
        hint = findViewById(R.id.hint);
        hint.setTypeface(font(400));
        weather.setTypeface(Fonts.bebas(getAssets()));
        ((TextView) credit).setTypeface(font(300));

        // The Unsplash key and location come from config.json, pushed to the TV over adb.
        settings = new LauncherSettings(this);
        JSONObject config = Net.config(this);
        if (config.has("lat") && config.has("lon")) {
            lat = config.optDouble("lat");
            lon = config.optDouble("lon");
        }
        backdrop = new Backdrop(this, findViewById(R.id.bg0), findViewById(R.id.bg1), (TextView) credit, settings, net);
        backdrop.setKey(config.optString("unsplashKey"));
        JSONArray feeds = config.optJSONArray("icsUrls");
        for (int i = 0; feeds != null && i < feeds.length(); i++) icsUrls.add(feeds.optString(i));
        styleBand();
        pills = new View[] {findViewById(R.id.pill_inputs), findViewById(R.id.pill_wifi), findViewById(R.id.pill_settings)};

        greeting.setTypeface(Fonts.bebas(getAssets()));
        clock.setTypeface(Fonts.bebas(getAssets()));
        date.setTypeface(Fonts.bebas(getAssets()));
        title.setTypeface(Fonts.lemon(getAssets()));
        for (View p : pills) ((TextView) ((ViewGroup) p).getChildAt(1)).setTypeface(Fonts.bebas(getAssets()));

        dockAdapter = new TileAdapter(false, (h, pos) -> bindFocus(h, area == Area.DOCK && pos == dockIdx));
        gridAdapter = new TileAdapter(true, (h, pos) -> bindFocus(h, area == Area.GRID && pos == gridIdx));
        dockView = findViewById(R.id.dock);
        gridView = findViewById(R.id.grid);
        setUpList(dockView, dockAdapter, new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        setUpList(gridView, gridAdapter, new GridLayoutManager(this, COLUMNS));

        String saved = prefs.getString("area", Area.DOCK.name());
        area = Area.valueOf(saved.equals(Area.MENU.name()) ? Area.TOP.name() : saved);
        topIdx = prefs.getInt("top", 0);

        IntentFilter pkgs = new IntentFilter();
        pkgs.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgs.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgs.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkgs.addDataScheme("package");
        registerReceiver(packageReceiver, pkgs);
        connectivity = getSystemService(ConnectivityManager.class);
        loadApps();
        // Never auto-switch HDMI inputs or change the stock Shield launcher.
    }

    /** Leaves home for the input the TV should wake to, unless a key is pressed first. */
    private void routeWake() {
        if (wakeRouter != null) wakeRouter.cancel();
        wakeRouter = WakeRouter.start(this, in -> {
            wakeRouter = null;
            startSafely(in.intent);
        });
    }

    private void stopWakeRoute() {
        if (wakeRouter != null) wakeRouter.cancel();
        wakeRouter = null;
    }

    private Typeface font(int weight) {
        return Fonts.outfit(getAssets(), weight);
    }

    /** Fit the unchanged 960x540 TV home onto a shorter phone display in landscape. */
    private void configurePhonePreview() {
        UiModeManager mode = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (mode != null && mode.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return;

        View root = findViewById(R.id.root);
        View canvas = findViewById(R.id.preview_canvas);
        final int virtualWidth = dp(960), virtualHeight = dp(540);
        ViewGroup.LayoutParams params = canvas.getLayoutParams();
        params.width = virtualWidth;
        params.height = virtualHeight;
        canvas.setLayoutParams(params);
        canvas.setPivotX(0f);
        canvas.setPivotY(0f);
        root.post(() -> {
            if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
            float scale = Math.min(root.getWidth() / (float) virtualWidth,
                    root.getHeight() / (float) virtualHeight) * 0.97f;
            canvas.setScaleX(scale);
            canvas.setScaleY(scale);
            canvas.setTranslationX((root.getWidth() - virtualWidth * scale) / 2f);
            canvas.setTranslationY((root.getHeight() - virtualHeight * scale) / 2f);
        });
    }

    private void setUpList(RecyclerView rv, TileAdapter adapter, RecyclerView.LayoutManager lm) {
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);
        rv.setItemAnimator(null);
        rv.setHasFixedSize(true);
        // Keys are handled in dispatchKeyEvent; the lists must never take focus or scroll.
        rv.setFocusable(false);
        rv.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        loader.shutdown();
        net.shutdown();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- apps

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) {
            loadApps();
        }
    };

    private void loadApps() {
        loader.execute(() -> {
            List<App> all = App.loadAll(this);
            runOnUiThread(() -> showApps(all));
        });
    }

    private void showApps(List<App> all) {
        // Seven favourite launchers, plus a fixed All Applications shortcut at the end.
        // Favourites use matching monochrome cards, while the other installed apps
        // keep their own artwork in All Applications.
        String saved = prefs.getString("order", null);
        List<App> rest = new ArrayList<>(all);
        List<App> defaults = new ArrayList<>();
        defaults.add(takeDefault(rest, "netflix", "Netflix", 0xFFE50914, "netflix"));
        defaults.add(takeDefault(rest, "disney", "Disney+", 0xFF2566FF, "disney"));
        defaults.add(takeDefault(rest, "prime", "Prime Video", 0xFF00A8E1, "prime", "amazonvideo", "avod"));
        defaults.add(takeDefault(rest, "purpleglobe", "IPTV", 0xFFA25AFF, "purpleglobe", "purple globe"));
        defaults.add(takeDefault(rest, "kodi", "Kodi", 0xFF17B6E8, "kodi", "xbmc"));
        defaults.add(takeDefault(rest, "youtube", "YouTube", 0xFFFF2424, "youtube"));
        defaults.add(takeDefault(rest, "spotify", "Spotify", 0xFF1ED760, "spotify"));
        defaults.addAll(rest);
        List<App> ordered = new ArrayList<>();
        if (saved != null) for (String s : saved.split(",")) {
            if (s.isEmpty() || s.equals(App.ALL_APPS)) continue;
            int i = indexOf(defaults, s);
            if (i >= 0) ordered.add(defaults.remove(i));
        }
        ordered.addAll(defaults);
        order.clear();
        int favorites = Math.min(DOCK_SIZE - 1, ordered.size());
        order.addAll(ordered.subList(0, favorites));
        order.add(App.allApplications(this));
        order.addAll(ordered.subList(favorites, ordered.size()));
        // Keep focus on the same app across reloads (an install, or the first load).
        String pkg = focusedPackage();
        if (pkg == null) pkg = prefs.getString("pkg", null);
        List<App> dock = split();

        List<App> grid = gridAdapter.apps;
        dockIdx = Math.max(0, indexOf(dock, pkg));
        gridIdx = Math.max(0, indexOf(grid, pkg));
        if (area == Area.GRID && (grid.isEmpty() || indexOf(grid, pkg) < 0)) area = Area.DOCK;
        if (area == Area.DOCK && indexOf(dock, pkg) < 0 && indexOf(grid, pkg) >= 0) area = Area.GRID;
        if (dock.isEmpty() && area == Area.DOCK) area = grid.isEmpty() ? Area.TOP : Area.GRID;
        afterLayout(() -> applyFocus(false));
    }

    private App takeDefault(List<App> rest, String id, String label, int tint, String... aliases) {
        for (int i = 0; i < rest.size(); i++) {
            App app = rest.get(i);
            String p = app.pkg.toLowerCase(Locale.ROOT), l = app.label.toLowerCase(Locale.ROOT);
            for (String alias : aliases) {
                if (p.contains(alias) || l.contains(alias))
                    return App.favourite(this, rest.remove(i), id, label, tint);
            }
        }
        return App.favourite(this, App.placeholder(this, id, label, tint), id, label, tint);
    }

    /** Fills the dock and grid from order; returns the dock list. */
    private List<App> split() {
        int d = Math.min(DOCK_SIZE, order.size());
        dockAdapter.set(order.subList(0, d));
        gridAdapter.set(order.subList(d, order.size()));
        int rows = (gridAdapter.getItemCount() + COLUMNS - 1) / COLUMNS;
        gridView.getLayoutParams().height = rows * pitch;
        content.getLayoutParams().height = gridTop + rows * pitch + dp(60);
        gridView.requestLayout();
        return dockAdapter.apps;
    }

    private static int indexOf(List<App> list, String pkg) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).pkg.equals(pkg)) return i;
        return -1;
    }

    private String focusedPackage() {
        if (area == Area.DOCK && dockIdx < dockAdapter.apps.size()) return dockAdapter.apps.get(dockIdx).pkg;
        if (area == Area.GRID && gridIdx < gridAdapter.apps.size()) return gridAdapter.apps.get(gridIdx).pkg;
        return null;
    }

    private void afterLayout(Runnable r) {
        content.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                content.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                r.run();
            }
        });
        content.requestLayout();
    }

    private void launch(App a, View from) {
        if (a.launch == null) {
            Toast.makeText(this, "Hold OK, then press Down to swap this shortcut with an installed app", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ActivityOptions o = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight());
            startActivity(a.launch, o.toBundle());
        } catch (Exception e) {
            Toast.makeText(this, "Can't open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        stopWakeRoute();
        int k = e.getKeyCode();
        boolean center = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (center) {
            // Act on release, so the key-up does not land in the app being opened. Holding
            // OK on an app (the key repeats) starts rearranging instead.
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) { centerDown = true; longPressed = false; }
                else if (centerDown && !longPressed && !moving && (area == Area.DOCK || area == Area.GRID)) {
                    longPressed = true;
                    setMoving(true);
                }
            } else if (e.getAction() == KeyEvent.ACTION_UP && centerDown) {
                centerDown = false;
                if (!longPressed) select();
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && (area == Area.DOCK || area == Area.GRID)) setMoving(!moving);
            return true;
        }
        int dx = 0, dy = 0;
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) dx = -1;
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 1;
        else if (k == KeyEvent.KEYCODE_DPAD_UP) dy = -1;
        else if (k == KeyEvent.KEYCODE_DPAD_DOWN) dy = 1;
        else return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) move(dx, dy);
        return true;  // arrows never reach Android's own focus search
    }

    private void move(int dx, int dy) {
        if (moving) { moveApp(dx, dy); return; }
        int docks = dockAdapter.getItemCount(), tiles = gridAdapter.getItemCount();
        switch (area) {
            case TOP:
                if (dx != 0) topIdx = clamp(topIdx + dx, 0, pills.length - 1);  // ends stop here
                else if (dy > 0 && docks > 0) area = Area.DOCK;
                else if (dy > 0 && tiles > 0) area = Area.GRID;
                else return;
                break;
            case DOCK:
                if (dx != 0) dockIdx = clamp(dockIdx + dx, 0, docks - 1);
                else if (dy < 0) area = Area.TOP;
                else if (tiles > 0) { area = Area.GRID; gridIdx = Math.min(dockIdx, tiles - 1); }
                else return;
                break;
            case GRID: {
                int col = gridIdx % COLUMNS, row = gridIdx / COLUMNS;
                if (dx < 0 && col > 0) gridIdx--;
                else if (dx > 0 && col < COLUMNS - 1 && gridIdx + 1 < tiles) gridIdx++;
                else if (dy < 0 && row > 0) gridIdx -= COLUMNS;
                else if (dy < 0) {
                    if (docks > 0) { area = Area.DOCK; dockIdx = Math.min(col, docks - 1); } else area = Area.TOP;
                } else if (dy > 0 && (row + 1) * COLUMNS < tiles) gridIdx = Math.min(gridIdx + COLUMNS, tiles - 1);
                else return;
                break;
            }
            case MENU:
                if (dy != 0) menuIdx = clamp(menuIdx + dy, 0, menu.getChildCount() - 1);
                else if (menuKind == Menu.SETTINGS) {
                    // Left/Right change the value; on "TV settings" they do nothing.
                    if (menuIdx < menuOptions.size()) changeSetting(menuOptions.get(menuIdx), dx);
                    return;
                } else { closeMenu(); return; }
                break;
        }
        applyFocus(true);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void select() {
        if (moving) { setMoving(false); return; }
        switch (area) {
            case DOCK:
                if (dockIdx < dockAdapter.getItemCount()) {
                    App app = dockAdapter.apps.get(dockIdx);
                    if (app.pkg.equals(App.ALL_APPS)) {
                        if (gridAdapter.getItemCount() > 0) {
                            area = Area.GRID;
                            gridIdx = 0;
                            applyFocus(true);
                        } else Toast.makeText(this, "No other apps installed", Toast.LENGTH_SHORT).show();
                    } else launch(app, focusedBanner);
                }
                break;
            case GRID:
                if (gridIdx < gridAdapter.getItemCount()) launch(gridAdapter.apps.get(gridIdx), focusedBanner);
                break;
            case TOP:
                if (topIdx == 0) openMenu(Menu.INPUTS);
                else if (topIdx == 1) startSafely(new Intent(Settings.ACTION_WIFI_SETTINGS));
                else openMenu(Menu.SETTINGS);
                break;
            case MENU:
                if (menuKind == Menu.INPUTS && menuIdx < inputs.size()) {
                    Intent i = inputs.get(menuIdx).intent;
                    closeMenu();
                    startSafely(i);
                } else if (menuKind == Menu.SETTINGS) {
                    if (menuIdx < menuOptions.size()) changeSetting(menuOptions.get(menuIdx), 1);
                    else if (menuIdx == menuOptions.size()) {  // "Reset app order"
                        prefs.edit().remove("order").apply();
                        closeMenu();
                        loadApps();
                    } else {  // "TV settings": tell GearService this one is wanted
                        closeMenu();
                        GearService.launcherOpenedSettingsAt = android.os.SystemClock.elapsedRealtime();
                        startSafely(new Intent(Settings.ACTION_SETTINGS).setClassName("com.android.tv.settings",
                                "com.android.tv.settings.MainSettings"));
                    }
                }
                break;
        }
    }

    // ---------------------------------------------------------------- rearranging

    private void setMoving(boolean on) {
        if (moving == on) return;
        if (on && position() == DOCK_SIZE - 1) return;  // All Applications never moves.
        moving = on;
        if (on) {
            App a = order.get(position());
            hint.setText("Moving " + a.label + "   \u25C0 \u25B2 \u25BC \u25B6 to move  \u00B7  OK when done");
            hint.animate().alpha(1f).setDuration(200).start();
        } else {
            hint.animate().alpha(0f).setDuration(200).start();
            StringBuilder b = new StringBuilder();
            for (App a : order) b.append(a.pkg).append(',');
            prefs.edit().putString("order", b.toString()).apply();
        }
        glass.animate().alpha(on ? 0f : 1f).setDuration(200).start();
        applyFocus(true);
    }

    /** The focused app's index in order. */
    private int position() {
        return area == Area.DOCK ? dockIdx : dockAdapter.getItemCount() + gridIdx;
    }

    /**
     * One step of the lifted app. Within the dock or the grid it slides past its neighbour;
     * across the boundary it swaps places with the app there, so the dock stays full.
     */
    private void moveApp(int dx, int dy) {
        int n = order.size(), d = dockAdapter.getItemCount(), p = position(), q;
        boolean swap = false;
        if (p < d) {
            if (dx != 0) q = clamp(p + dx, 0, d - 1);
            else if (dy > 0 && n > d) { q = Math.min(d + p, n - 1); swap = true; }
            else return;
        } else {
            int g = p - d, col = g % COLUMNS;
            if (dx != 0) q = clamp(p + dx, d, n - 1);
            else if (dy < 0 && g < COLUMNS) { q = Math.min(col, d - 1); swap = true; }
            else if (dy < 0) q = p - COLUMNS;
            else if (dy > 0 && (g / COLUMNS + 1) * COLUMNS < n - d) q = Math.min(p + COLUMNS, n - 1);
            else return;
        }
        if (q == p) return;
        if (q == DOCK_SIZE - 1 || p == DOCK_SIZE - 1) return;
        if (swap) {
            App a = order.get(p);
            order.set(p, order.get(q));
            order.set(q, a);
        } else order.add(q, order.remove(p));
        if (q < d) { area = Area.DOCK; dockIdx = q; } else { area = Area.GRID; gridIdx = q - d; }
        split();
        afterLayout(() -> applyFocus(true));
    }

    /** SystemUI's Somnambulator is the public way to start the screensaver now. */
    private void startDream() {
        startSafely(new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui",
                "com.android.systemui.Somnambulator"));
    }

    private void startSafely(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // Home never closes: Back returns to the dock, and from the dock starts the screensaver.
        if (moving) setMoving(false);
        else if (area == Area.MENU) closeMenu();
        else if (area != Area.DOCK && dockAdapter.getItemCount() > 0) { area = Area.DOCK; applyFocus(true); }
        // No action on Home: a safe Back key must not replace the chosen system Home.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // No wake-to-HDMI routing on the Shield.
        // Home pressed while already home: back to the dock. Coming back from an app keeps
        // the last focused tile.
        if (resumed) {
            if (area == Area.MENU) closeMenu();
            if (dockAdapter.getItemCount() > 0) { area = Area.DOCK; applyFocus(true); }
        }
    }

    // ---------------------------------------------------------------- focus drawing

    private void bindFocus(TileAdapter.Holder h, boolean focused) {
        h.banner.animate().cancel();
        h.name.animate().cancel();
        h.banner.setScaleX(focused ? TILE_SCALE : 1f);
        h.banner.setScaleY(focused ? TILE_SCALE : 1f);
        h.name.setAlpha(focused ? 1f : 0f);
        h.name.setTranslationY(focused ? dp(4) : 0);
        if (focused) { focusedBanner = h.banner; focusedName = h.name; }
    }

    /** Points every focus cue (glass, scale, name, title, scroll) at the current model state. */
    private void applyFocus(boolean animate) {
        View banner = null, name = null, pill = null, row = null;
        if (area == Area.DOCK) banner = tile(dockView, dockIdx);
        else if (area == Area.GRID) banner = tile(gridView, gridIdx);
        else if (area == Area.TOP) pill = pills[topIdx];
        else row = menu.getChildAt(menuIdx);
        if (banner != null) name = ((ViewGroup) banner.getParent()).findViewById(R.id.name);

        if (wiggle != null && (wiggle.getTarget() != banner || !moving)) {
            wiggle.cancel();
            ((View) wiggle.getTarget()).setRotation(0);
            wiggle = null;
        }
        if (moving && banner != null && wiggle == null) {
            // The lifted app rocks gently while it is being moved.
            wiggle = ObjectAnimator.ofFloat(banner, View.ROTATION, -1.4f, 1.4f);
            wiggle.setDuration(150);
            wiggle.setRepeatMode(ValueAnimator.REVERSE);
            wiggle.setRepeatCount(ValueAnimator.INFINITE);
            wiggle.start();
        }
        if (focusedBanner != banner) {
            if (focusedBanner != null) scaleTo(focusedBanner, 1f);
            if (focusedName != null) focusedName.animate().alpha(0f).translationY(0).setDuration(150).start();
            if (banner != null) scaleTo(banner, TILE_SCALE);
            if (name != null && area == Area.GRID)
                name.animate().alpha(1f).translationY(dp(4)).setDuration(220).start();
            focusedBanner = banner;
            focusedName = name;
        }
        if (focusedPill != pill) {
            if (focusedPill != null) scaleTo(focusedPill, 1f);
            if (pill != null) scaleTo(pill, PILL_SCALE);
            focusedPill = pill;
        }

        if (area == Area.DOCK) showTitle(dockAdapter.apps.get(dockIdx));
        // An input tile already carries its name, so the title and its glow step aside.
        boolean input = titleText == null;
        title.animate().alpha(input ? 0f : area == Area.DOCK ? 1f : 0.35f).setDuration(250).start();
        glow.animate().alpha(input ? 0f : area == Area.DOCK ? 1f : 0.3f).setDuration(250).start();

        // The dock and top bar sit at the top; a grid row scrolls up to 230dp from the top,
        // which keeps the dock in view above the first row.
        scrollTo(area == Area.GRID ? gridTop + (gridIdx / COLUMNS) * pitch - dp(230) : 0, animate);
        // Over a photo, the grid gets a darker backdrop so its tiles and names stay clear.
        dim.animate().alpha(area == Area.GRID ? 0.6f : 0f).setDuration(400).start();
        credit.animate().alpha(area == Area.GRID ? 0f : 1f).setDuration(400).start();
        if (area == Area.MENU && menuKind == Menu.SETTINGS) drawSettingValues();

        View v = banner != null ? banner : pill != null ? pill : row;
        if (v != null) {
            float s = banner != null ? TILE_SCALE : pill != null ? PILL_SCALE : 1f;
            RectF rect = rectInContent(v, s);
            float radius = banner != null ? dp(10) * s : pill != null ? rect.height() / 2 : dp(10);
            glass.moveTo(rect, radius, animate);
        }
        save();
    }

    private View tile(RecyclerView rv, int pos) {
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        View item = lm == null ? null : lm.findViewByPosition(pos);
        return item == null ? null : item.findViewById(R.id.banner);
    }

    private void scaleTo(View v, float s) {
        v.animate().scaleX(s).scaleY(s).setDuration(420).setInterpolator(Spring.INSTANCE).start();
    }

    private String titleText = "";

    /** The dock app named in the title; null while an input tile is focused there. */
    private void showTitle(App a) {
        if (a.pkg.startsWith(App.INPUT)) { titleText = null; return; }
        if (a.label.equals(titleText)) return;
        titleText = a.label;
        title.setText(a.label);
        title.setAlpha(0.4f);
        glow.setColor(a.glow);
        // An oval a little wider and taller than the name, centred on it.
        title.post(() -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) glow.getLayoutParams();
            lp.width = title.getWidth() + dp(260);
            lp.height = title.getHeight() + dp(110);
            lp.leftMargin = (int) title.getX() - dp(130);
            lp.topMargin = (int) title.getY() - dp(55);
            glow.setLayoutParams(lp);
        });
    }

    /** v's bounds in content coordinates, scaled about its centre by s. */
    private RectF rectInContent(View v, float s) {
        float x = 0, y = 0;
        for (View cur = v; cur != content && cur != null; cur = (View) cur.getParent()) {
            x += cur.getLeft() + (cur == v ? 0 : cur.getTranslationX());
            y += cur.getTop() + (cur == v ? 0 : cur.getTranslationY());
        }
        float cx = x + v.getWidth() / 2f, cy = y + v.getHeight() / 2f;
        float w = v.getWidth() * s / 2f, h = v.getHeight() * s / 2f;
        return new RectF(cx - w, cy - h, cx + w, cy + h);
    }

    private void scrollTo(float target, boolean animate) {
        if (scroller != null) scroller.cancel();
        if (!animate) { scroll = target; content.setTranslationY(-scroll); return; }
        if (target == scroll) return;
        scroller = ValueAnimator.ofFloat(scroll, target);
        scroller.setDuration(520);
        scroller.setInterpolator(Spring.INSTANCE);
        scroller.addUpdateListener(a -> {
            scroll = (float) a.getAnimatedValue();
            content.setTranslationY(-scroll);
        });
        scroller.start();
    }

    private void save() {
        SharedPreferences.Editor e = prefs.edit().putString("area", area.name()).putInt("top", topIdx);
        String pkg = focusedPackage();
        if (pkg != null) e.putString("pkg", pkg);
        e.apply();
    }

    // ---------------------------------------------------------------- dropdowns

    /** The glass dropdown under a pill: the TV inputs, or the launcher settings. */
    private void openMenu(Menu kind) {
        menuKind = kind;
        menuIdx = 0;
        fillMenu();
        View pill = pills[kind == Menu.INPUTS ? 0 : 2];
        RectF r = rectInContent(pill, 1f);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menu.getLayoutParams();
        lp.width = dp(kind == Menu.INPUTS ? 210 : 330);
        // Inputs hangs from its pill's left edge; Settings, at the screen edge, from its right.
        lp.leftMargin = kind == Menu.INPUTS ? (int) r.left : (int) r.right - lp.width;
        lp.topMargin = (int) r.bottom + dp(10);
        menu.setVisibility(View.VISIBLE);
        menu.setAlpha(0f);
        menu.setPivotX(kind == Menu.INPUTS ? dp(30) : lp.width - dp(30));
        menu.setPivotY(0);
        menu.setScaleX(0.94f);
        menu.setScaleY(0.94f);
        menu.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
        area = Area.MENU;
        afterLayout(() -> applyFocus(true));
    }

    private void fillMenu() {
        menu.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        if (menuKind == Menu.INPUTS) {
            inputs.clear();
            inputs.addAll(Inputs.list(this));
            for (Inputs in : inputs) addMenuRow(inf, in.label);
            if (inputs.isEmpty()) addMenuRow(inf, "No inputs found");
            return;
        }
        menuOptions = settings.visible();
        for (LauncherSettings.Option o : menuOptions) addSettingsRow(inf, o.label);
        addSettingsRow(inf, "Reset app order");
        addSettingsRow(inf, "TV settings");
        drawSettingValues();
    }

    private void addMenuRow(LayoutInflater inf, String label) {
        TextView row = (TextView) inf.inflate(R.layout.menu_row, menu, false);
        row.setText(label);
        row.setTypeface(font(400));
        menu.addView(row);
    }

    private void addSettingsRow(LayoutInflater inf, String label) {
        View row = inf.inflate(R.layout.settings_row, menu, false);
        TextView l = row.findViewById(R.id.label), v = row.findViewById(R.id.value);
        l.setText(label);
        l.setTypeface(font(400));
        v.setTypeface(font(400));
        menu.addView(row);
    }

    /** Values on the right; the focused one shows arrows, since Left/Right change it. */
    private void drawSettingValues() {
        for (int i = 0; i < menu.getChildCount(); i++) {
            TextView v = menu.getChildAt(i).findViewById(R.id.value);
            if (v == null) continue;
            if (i < menuOptions.size()) {
                String name = settings.name(menuOptions.get(i));
                v.setText(i == menuIdx ? "\u2039  " + name + "  \u203A" : name);
            } else v.setText("\u203A");
        }
    }

    private void changeSetting(LauncherSettings.Option o, int dir) {
        settings.step(o, dir);
        if (o == LauncherSettings.BACKGROUND) { backdrop.apply(false); styleBand(); }
        else if (o == LauncherSettings.THEME) backdrop.apply(true);
        else if (o == LauncherSettings.PHOTO_MINUTES) backdrop.apply(false);
        else { prefs.edit().remove("weatherAt").apply(); refreshWeather(); }  // weather or units
        // Rows come and go with Background and Weather; stay on the same option.
        String key = o.key;
        fillMenu();
        for (int i = 0; i < menuOptions.size(); i++) if (menuOptions.get(i).key.equals(key)) menuIdx = i;
        afterLayout(() -> applyFocus(true));
    }

    /** Opaque over black; see-through over a photo, as frosted as we can get without blur. */
    private void styleBand() {
        GradientDrawable d = (GradientDrawable) band.getBackground().mutate();
        d.setColor(settings.photos() ? 0xB3161618 : 0xFF141416);
    }

    private void closeMenu() {
        menu.animate().alpha(0f).setDuration(150).withEndAction(() -> menu.setVisibility(View.GONE)).start();
        area = Area.TOP;
        topIdx = menuKind == Menu.INPUTS ? 0 : 2;
        applyFocus(true);
    }

    // ---------------------------------------------------------------- weather

    /** Every 30 minutes while home is on screen; the last reading shows at once. */
    private final Runnable weatherTick = this::refreshWeather;

    private void refreshWeather() {
        ui.removeCallbacks(weatherTick);
        if (!started) return;
        boolean on = settings.get(LauncherSettings.WEATHER).equals("on") && !Double.isNaN(lat);
        weather.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) return;
        weather.setText(prefs.getString("weatherText", ""));
        long due = prefs.getLong("weatherAt", 0) + WEATHER_EVERY - System.currentTimeMillis();
        if (due > 0) { ui.postDelayed(weatherTick, due); return; }
        String units = settings.get(LauncherSettings.UNITS);
        net.execute(() -> {
            try {
                String text = Weather.fetch(lat, lon, units);
                prefs.edit().putString("weatherText", text).putLong("weatherAt", System.currentTimeMillis()).apply();
                ui.post(() -> weather.setText(text));
            } catch (Exception e) {
                android.util.Log.w("Home", "weather failed: " + e);
            }
            ui.post(() -> { if (started) ui.postDelayed(weatherTick, WEATHER_EVERY); });
        });
    }

    // ---------------------------------------------------------------- clock and Wi-Fi

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) {
            updateClock();
        }
    };

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon"
                : h < 22 ? "Good evening" : "Good night");
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), now));
        showAgenda();
    }

    // ---------------------------------------------------------------- calendar

    private static final long CALENDAR_EVERY = 15 * 60_000L;
    private static final int AGENDA_ROWS = 3;

    /** Every 15 minutes while home is on screen; the last copy of the feeds shows at once. */
    private final Runnable calendarTick = this::refreshCalendar;

    private void refreshCalendar() {
        ui.removeCallbacks(calendarTick);
        if (!started || icsUrls.isEmpty()) return;
        File cache = new File(getFilesDir(), "calendar.ics");
        long due = cache.lastModified() + CALENDAR_EVERY - System.currentTimeMillis();
        net.execute(() -> {
            String text = null;
            if (due <= 0) {
                StringBuilder all = new StringBuilder();
                for (String url : icsUrls) {
                    try {
                        String t = Net.get(url.replaceFirst("^webcal:", "https:"));
                        if (t.contains("BEGIN:VCALENDAR")) all.append(t).append('\n');
                    } catch (Exception e) {
                        android.util.Log.w("Home", "calendar failed: " + e);
                    }
                }
                if (all.length() > 0) {
                    text = all.toString();
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(cache)) {
                        out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (Exception ignored) {
                    }
                }
            }
            if (text == null && agenda == null && cache.exists()) {
                try {
                    text = new String(java.nio.file.Files.readAllBytes(cache.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                }
            }
            Agenda a = text != null ? new Agenda(text) : null;
            ui.post(() -> {
                if (a != null) agenda = a;
                showAgenda();
                if (started) ui.postDelayed(calendarTick, due > 0 ? due : CALENDAR_EVERY);
            });
        });
    }

    /**
     * Today's events still to come, or else the next day within a week that has any: a label,
     * then up to three rows of time and title. Redrawn each minute with the clock.
     */
    private void showAgenda() {
        agendaView.setVisibility(agenda == null ? View.GONE : View.VISIBLE);
        if (agenda == null) return;
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        String label = "Today";
        List<Agenda.Item> list = new ArrayList<>();
        for (Agenda.Item o : agenda.on(today, zone)) if (o.allDay || o.end.isAfter(now)) list.add(o);
        for (int i = 1; list.isEmpty() && i <= 7; i++) {
            LocalDate day = today.plusDays(i);
            list = agenda.on(day, zone);
            label = i == 1 ? "Tomorrow" : day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault());
        }
        agendaView.removeAllViews();
        agendaView.addView(agendaText(list.isEmpty() ? "Nothing scheduled this week" : label, 13, 0xFF9A9AA2, 400));
        DateTimeFormatter time = DateTimeFormatter.ofPattern(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm a");
        for (int i = 0; i < Math.min(AGENDA_ROWS, list.size()); i++) {
            Agenda.Item o = list.get(i);
            boolean live = !o.allDay && !o.start.isAfter(now) && label.equals("Today");
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(5), 0, 0);
            // Times in a fixed-width column, so the titles line up after them.
            TextView when = agendaText(o.allDay ? "All day" : live ? "Now" : time.format(o.start), 15,
                    live ? 0xFFFFFFFF : 0xFFC8C8CE, live ? 500 : 300);
            when.setLayoutParams(new LinearLayout.LayoutParams(dp(82), ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView what = agendaText(o.summary, 15, 0xFFF0F0F3, live ? 500 : 300);
            what.setMaxWidth(dp(260));
            row.addView(when);
            row.addView(what);
            agendaView.addView(row);
        }
        if (list.size() > AGENDA_ROWS)
            agendaView.addView(agendaText("+" + (list.size() - AGENDA_ROWS) + " more", 13, 0xFF9A9AA2, 300));
    }

    private TextView agendaText(String text, int sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(font(weight));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setShadowLayer(dp(4), 0, 0, 0x99000000);
        return t;
    }

    /** Network changes arrive as events; nothing polls. */
    private final ConnectivityManager.NetworkCallback network =
            new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
            String label;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                label = getString(R.string.wifi);
                if (caps.getTransportInfo() instanceof WifiInfo) {
                    String ssid = ((WifiInfo) caps.getTransportInfo()).getSSID();
                    if (ssid != null && !ssid.equals(WifiManager.UNKNOWN_SSID))
                        label = ssid.replaceAll("^\"|\"$", "");
                }
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) label = "Ethernet";
            else label = getString(R.string.wifi);
            String l = label;
            runOnUiThread(() -> wifiName.setText(l));
        }

        @Override
        public void onLost(Network n) {
            runOnUiThread(() -> wifiName.setText("Not connected"));
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        connectivity.registerDefaultNetworkCallback(network);
        started = true;
        backdrop.start();
        refreshWeather();
        refreshCalendar();
    }

    @Override
    protected void onStop() {
        stopWakeRoute();
        connectivity.unregisterNetworkCallback(network);
        started = false;
        backdrop.stop();
        ui.removeCallbacks(weatherTick);
        ui.removeCallbacks(calendarTick);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, f);
        updateClock();
    }

    @Override
    protected void onPause() {
        resumed = false;
        unregisterReceiver(timeReceiver);
        if (area == Area.MENU) {
            menu.setVisibility(View.GONE);
            area = Area.TOP;
            applyFocus(false);
        }
        super.onPause();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
