package uk.co.howardtv;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * A quick settings panel modelled on Google TV's (which lives inside the stock launcher
 * and goes away with it): a floating card with the date and time, a volume row and
 * shortcut tiles, focus shown by filling the tile. It runs in its own see-through task,
 * so whatever was playing, HDMI included, stays on screen behind it. Over a TV source,
 * TCL's Picture and Sound pages open as overlays on the live picture, so their changes
 * show as they are made.
 */
public class QuickSettingsActivity extends Activity {
    private static final int VOLUME = -1;
    private static final float FOCUS_SCALE = 1.04f;

    private interface Action { void run(); }

    private static final class Tile {
        final int icon;
        final String label;
        final Action action;

        Tile(int icon, String label, Action action) {
            this.icon = icon;
            this.label = label;
            this.action = action;
        }
    }

    private final List<Tile> tiles = new ArrayList<>();
    private final List<View> tileViews = new ArrayList<>();
    private final List<Inputs> inputs = new ArrayList<>();
    private boolean inputsShown;
    private int sel = VOLUME;  // VOLUME, a tile index, or an input row while inputsShown
    private boolean centerDown;

    private AudioManager audio;
    private VolumeBar volumeBar;
    private TextView volumeValue;
    private ImageView volumeIcon;
    private LinearLayout tileBox;
    private View volumeRow, focused;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.quick_settings);
        audio = getSystemService(AudioManager.class);
        volumeIcon = findViewById(R.id.qs_volume_icon);
        volumeRow = findViewById(R.id.qs_volume);
        volumeBar = findViewById(R.id.qs_volume_bar);
        volumeValue = findViewById(R.id.qs_volume_value);
        tileBox = findViewById(R.id.qs_tiles);
        TextView time = findViewById(R.id.qs_time), date = findViewById(R.id.qs_date);
        time.setTypeface(Fonts.outfit(getAssets(), 400));
        date.setTypeface(Fonts.outfit(getAssets(), 400));
        volumeValue.setTypeface(Fonts.outfit(getAssets(), 400));
        Calendar now = Calendar.getInstance();
        time.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm", now));
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), now));

        tiles.add(new Tile(R.drawable.ic_settings, "All settings", () -> {
            GearService.launcherOpenedSettingsAt = SystemClock.elapsedRealtime();
            open(new Intent(Settings.ACTION_SETTINGS).setClassName("com.android.tv.settings",
                    "com.android.tv.settings.MainSettings"));
        }));
        tiles.add(new Tile(R.drawable.ic_screensaver, "Screensaver", () -> open(new Intent(Intent.ACTION_MAIN)
                .setClassName("com.android.systemui", "com.android.systemui.Somnambulator"))));
        tiles.add(new Tile(R.drawable.ic_input, "Inputs", this::showInputs));
        tiles.add(new Tile(R.drawable.ic_picture, "Picture", () -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS))));
        tiles.add(new Tile(R.drawable.ic_volume, "Sound", () -> {
            Intent tcl = new Intent("com.android.tv.action.VIEW_SOUND_SETTINGS");
            open(tcl.resolveActivity(getPackageManager()) != null ? tcl : new Intent(Settings.ACTION_SOUND_SETTINGS));
        }));
        tiles.add(new Tile(R.drawable.ic_wifi, "Wi-Fi", () -> open(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        tiles.add(new Tile(R.drawable.ic_bluetooth, "Bluetooth", () -> open(new Intent().setClassName(
                "com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"))));
        tiles.add(new Tile(R.drawable.ic_accessibility, "Accessibility",
                () -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        showTiles();
        updateVolume();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();  // the gear again closes the panel
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();  // it is a transient panel; leaving it dismisses it
    }

    // ---- building

    private void showTiles() {
        inputsShown = false;
        tileBox.removeAllViews();
        tileViews.clear();
        LayoutInflater inf = LayoutInflater.from(this);
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.topMargin = dp(8);
                tileBox.addView(row, lp);
            }
            View v = inf.inflate(R.layout.qs_tile, row, false);
            ((ImageView) v.findViewById(R.id.qs_icon)).setImageResource(tiles.get(i).icon);
            TextView label = v.findViewById(R.id.qs_label);
            label.setText(tiles.get(i).label);
            label.setTypeface(Fonts.outfit(getAssets(), 400));
            if (i % 2 == 1) ((LinearLayout.LayoutParams) v.getLayoutParams()).leftMargin = dp(8);
            row.addView(v);
            tileViews.add(v);
        }
        focus();
    }

    private void showInputs() {
        inputsShown = true;
        inputs.clear();
        inputs.addAll(Inputs.list(this));
        tileBox.removeAllViews();
        tileViews.clear();
        LayoutInflater inf = LayoutInflater.from(this);
        for (Inputs in : inputs) {
            TextView row = (TextView) inf.inflate(R.layout.menu_row, tileBox, false);
            row.setText(in.label);
            row.setTypeface(Fonts.outfit(getAssets(), 400));
            row.setBackgroundResource(R.drawable.qs_tile);
            row.setTextColor(0xFFE3E5E8);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
            lp.height = dp(50);
            lp.bottomMargin = dp(8);
            tileBox.addView(row);
            tileViews.add(row);
        }
        sel = inputs.isEmpty() ? VOLUME : 0;
        focus();
    }

    // ---- keys

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) centerDown = true;
            if (e.getAction() == KeyEvent.ACTION_UP && centerDown) { centerDown = false; select(); }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int n = tileViews.size(), cols = inputsShown ? 1 : 2;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (sel == VOLUME) return true;
                sel = sel < cols ? VOLUME : sel - cols;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (sel == VOLUME) { if (n > 0) sel = 0; }
                else if (sel + cols < n) sel += cols;
                else return true;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int dx = k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                if (sel == VOLUME) { stepVolume(dx); return true; }  // Left/Right on the row set the volume
                if (cols == 2 && (sel % 2 + dx == 0 || sel % 2 + dx == 1) && sel + dx < n) sel += dx;
                else return true;
                break;
            }
            default:
                return super.dispatchKeyEvent(e);
        }
        focus();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (inputsShown) { sel = 2; showTiles(); }  // back to the Inputs tile
        else finish();
    }

    private void select() {
        if (sel == VOLUME) return;
        if (inputsShown) { if (sel < inputs.size()) open(inputs.get(sel).intent); }
        else tiles.get(sel).action.run();
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
            // Not on this TV; the panel just closes.
        }
        finish();
    }

    // ---- focus and volume

    /** Fills the selected tile and lifts it slightly; the previous one goes back to slate. */
    private void focus() {
        View v = sel == VOLUME ? volumeRow : sel < tileViews.size() ? tileViews.get(sel) : null;
        if (v == focused) return;
        if (focused != null) paint(focused, false);
        if (v != null) paint(v, true);
        focused = v;
    }

    private void paint(View v, boolean on) {
        v.setBackgroundResource(on ? R.drawable.qs_tile_focused : R.drawable.qs_tile);
        int ink = on ? 0xFF1F2023 : 0xFFE3E5E8;
        if (v == volumeRow) {
            volumeIcon.setColorFilter(ink);
            volumeValue.setTextColor(ink);
            volumeBar.setDark(on);
        } else if (v instanceof TextView) {
            ((TextView) v).setTextColor(ink);
        } else {
            ((ImageView) v.findViewById(R.id.qs_icon)).setColorFilter(ink);
            ((TextView) v.findViewById(R.id.qs_label)).setTextColor(ink);
        }
        float s = on ? FOCUS_SCALE : 1f;
        v.animate().scaleX(s).scaleY(s).setDuration(300).setInterpolator(Spring.INSTANCE).start();
    }

    private void stepVolume(int dir) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                dir > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
        updateVolume();
    }

    private void updateVolume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        volumeBar.setLevel(max == 0 ? 0 : v / (float) max);
        volumeValue.setText(String.valueOf(v));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
