package uk.co.howardtv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/**
 * Makes the remote's gear key open the launcher's quick settings panel, in any app (over
 * HDMI too); with the stock launcher disabled, Google TV's own panel is gone.
 *
 * The TCL remote's gear sends KEYCODE_NOTIFICATION, which the system would hand to the
 * stock launcher's panel and otherwise passes on, so it is caught here as a key. Remotes
 * that send KEYCODE_SETTINGS instead get the full Settings app from the firmware before
 * any app sees the key; for those, this watches for Settings' main screen appearing when
 * the panel did not open it, backs out of it, and opens the panel (Settings flashes first).
 *
 * It also keeps the TV remote from driving the Apple TV on HDMI 1. On an HDMI input the TV
 * app forwards navigation and media keys to the source over HDMI-CEC; CEC itself stays on so
 * the Apple TV can still switch the TV on and off. The keys are dropped only while the bare
 * picture has focus, so the TV app's own menus (separate dialog windows) still work.
 */
public class GearService extends AccessibilityService {
    private static final String SETTINGS = "com.android.tv.settings";
    private static final String MAIN_SETTINGS = "com.android.tv.settings.MainSettings";

    /** Set by the launcher and the panel when they open Settings themselves. */
    static volatile long launcherOpenedSettingsAt;

    private static final String TV_APP = "com.tcl.tv";
    /** TCL's record of the hardware input on screen (the n in a ".../HW<n>" input id). */
    private static final String CURRENT_INPUT = "tcl_sys_tv_input_current_device_id";

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_NOTIFICATION) return forwardedToHdmi1(e.getKeyCode());
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) openPanel();
        return true;  // both halves, so the app in front does not see it
    }

    /** Home, volume, power, input, channel and the like are left alone. */
    private static boolean forwardedByCec(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_MEDIA_PLAY: case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_NEXT: case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return true;
            default:
                return key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9;
        }
    }

    /** Whether the key would reach the Apple TV: HDMI 1's picture has focus in the TV app. */
    private boolean forwardedToHdmi1(int key) {
        if (!forwardedByCec(key)) return false;
        AccessibilityWindowInfo tv = null;
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || !TV_APP.contentEquals(root.getPackageName())) continue;
            if (tv != null) return false;  // a TV app dialog over the picture
            tv = w;
        }
        if (tv == null || !tv.isFocused()) return false;
        String current = Settings.Secure.getString(getContentResolver(), CURRENT_INPUT);
        return current != null && current.equals(hdmi1());
    }

    /** HDMI 1's hardware number: the lowest among the hardware HDMI inputs, as in Inputs. */
    private String hdmi1() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return null;
        int lowest = Integer.MAX_VALUE;
        for (TvInputInfo info : tim.getTvInputList()) {
            String id = info.getId();
            int at = id.lastIndexOf("/HW");
            if (info.getType() != TvInputInfo.TYPE_HDMI || at < 0) continue;
            try {
                lowest = Math.min(lowest, Integer.parseInt(id.substring(at + 3)));
            } catch (NumberFormatException ignored) {
            }
        }
        return lowest == Integer.MAX_VALUE ? null : String.valueOf(lowest);
    }

    private void openPanel() {
        // A second press reaches the open panel's onNewIntent and closes it.
        startActivity(new Intent(this, QuickSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        String cls = e.getClassName() == null ? "" : e.getClassName().toString();
        if (pkg.equals(SETTINGS) && cls.equals(MAIN_SETTINGS)
                && SystemClock.elapsedRealtime() - launcherOpenedSettingsAt > 3000) {
            // Back only once Settings has the focus (Back sent earlier reaches the app below,
            // and the TV app's Back goes home), then the panel once Settings is gone.
            handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 350);
            handler.postDelayed(this::openPanel, 650);
        }
    }

    @Override
    public void onInterrupt() {}
}
