package com.ambient.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the remote's gear key open the launcher's quick settings panel, in any app (over
 * HDMI too). The firmware handles that key before accessibility key filtering can see it
 * and opens the full Settings app; with the stock launcher disabled, Google TV's own panel
 * is gone. So this watches for Settings' main screen appearing when the panel did not open
 * it, backs out of it, and opens the panel instead. Settings shows for a moment first;
 * the system addresses it by name, so there is no way to be chosen instead of it.
 */
public class GearService extends AccessibilityService {
    private static final String SETTINGS = "com.android.tv.settings";
    private static final String MAIN_SETTINGS = "com.android.tv.settings.MainSettings";

    /** Set by the launcher and the panel when they open Settings themselves. */
    static volatile long launcherOpenedSettingsAt;

    private final Handler handler = new Handler(Looper.getMainLooper());

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
            handler.postDelayed(() -> startActivity(new Intent(this, QuickSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), 650);
        }
    }

    @Override
    public void onInterrupt() {}
}
