package com.ambient.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the remote's gear key open the launcher's quick settings panel, in any app (over
 * HDMI too); with the stock launcher disabled, Google TV's own panel is gone.
 *
 * The TCL remote's gear sends KEYCODE_NOTIFICATION, which the system would hand to the
 * stock launcher's panel and otherwise passes on, so it is caught here as a key. Remotes
 * that send KEYCODE_SETTINGS instead get the full Settings app from the firmware before
 * any app sees the key; for those, this watches for Settings' main screen appearing when
 * the panel did not open it, backs out of it, and opens the panel (Settings flashes first).
 */
public class GearService extends AccessibilityService {
    private static final String SETTINGS = "com.android.tv.settings";
    private static final String MAIN_SETTINGS = "com.android.tv.settings.MainSettings";

    /** Set by the launcher and the panel when they open Settings themselves. */
    static volatile long launcherOpenedSettingsAt;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_NOTIFICATION) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) openPanel();
        return true;  // both halves, so the app in front does not see it
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
