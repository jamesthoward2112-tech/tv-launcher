package uk.co.howardtv;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Where the TV goes when it wakes. TCL's system brings home to the front on every wake,
 * over the HDMI picture. A device that woke the TV over HDMI-CEC (PS5, Apple TV) announces
 * itself with <Active Source>, which the TV ignores while home is in front, so this reads
 * it from the system log (READ_LOGS, granted by install.sh) and opens that port. Woken
 * by the remote, or with no device speaking up in time, the TV returns to its last input.
 */
final class WakeRouter {
    private static final String TAG = "WakeRouter";
    /** Set by Android on the home intent it sends when the screen wakes. */
    private static final String START_REASON = "android.intent.extra.EXTRA_START_REASON";
    /** TCL's record of the hardware input on screen, and of the one before it. */
    private static final String CURRENT_INPUT = "tcl_sys_tv_input_current_device_id";
    private static final String LAST_INPUT = "tcl_sys_tv_input_last_device_id";
    /** How long a CEC device gets to announce itself; the Apple TV takes about 2 s. */
    private static final long CEC_WAIT = 8000;
    /** How long to look for TCL's wake reason before taking the remote as the cause. */
    private static final long REASON_WAIT = 1500;

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile Process logcat;
    private volatile boolean cancelled;
    /** From TCL's wake reason: null until it is read. */
    private volatile Boolean cecWake;

    private WakeRouter(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Whether the home intent was sent because the TV woke up. */
    static boolean isWake(Intent intent) {
        String reason = intent == null ? null : intent.getStringExtra(START_REASON);
        return reason != null && reason.startsWith("WAKE_REASON");
    }

    /** Finds the input to show and hands it to open on the main thread. */
    static WakeRouter start(Context ctx, Consumer<Inputs> open) {
        WakeRouter r = new WakeRouter(ctx);
        Thread t = new Thread(() -> {
            Inputs target = r.find();
            if (target != null) r.ui.post(() -> { if (!r.cancelled) open.accept(target); });
        }, TAG);
        t.setDaemon(true);
        t.start();
        return r;
    }

    /** Stays on home: the viewer pressed a key, or the TV went back to sleep. */
    void cancel() {
        cancelled = true;
        Process p = logcat;
        if (p != null) p.destroy();
    }

    private Inputs find() {
        List<Inputs> inputs = Inputs.list(ctx);
        int port = cecPort();
        if (cancelled) return null;
        if (port > 0) {
            Inputs in = byLabel(inputs, "HDMI " + port);
            if (in != null) { Log.i(TAG, "woken by the device on HDMI " + port); return in; }
        }
        for (String key : new String[] {CURRENT_INPUT, LAST_INPUT}) {
            String hw = Settings.Secure.getString(ctx.getContentResolver(), key);
            for (Inputs in : inputs) {
                if (hw != null && in.id.endsWith("/HW" + hw)) { Log.i(TAG, "back to " + in.label); return in; }
            }
        }
        return byLabel(inputs, "HDMI 1");
    }

    private static Inputs byLabel(List<Inputs> inputs, String label) {
        for (Inputs in : inputs) if (in.label.equals(label)) return in;
        return null;
    }

    /**
     * The port of the device that woke the TV over CEC, read from the log since just before
     * the wake: TclPowerManagerServiceEx logs "type = CEC" for a CEC wake, then the device's
     * <Active Source> carries its physical address, whose first digit is the port. 0 when
     * the remote woke the TV, the log is unreadable, or no device spoke up in time.
     */
    private int cecPort() {
        long t0 = SystemClock.elapsedRealtime();
        double since = System.currentTimeMillis() / 1000.0 - 4;
        try {
            logcat = new ProcessBuilder("logcat", "-v", "brief", "-T", String.format(Locale.US, "%.3f", since),
                    "TclPowerManagerServiceEx:V", "HdmiControlService:V", "*:S").redirectErrorStream(true).start();
        } catch (IOException e) {
            return 0;
        }
        // The log only ends when the process does, so a timer ends it.
        Thread timer = new Thread(() -> {
            try {
                Thread.sleep(REASON_WAIT);
                if (!Boolean.TRUE.equals(cecWake)) { logcat.destroy(); return; }
                Thread.sleep(CEC_WAIT - REASON_WAIT);
            } catch (InterruptedException ignored) {
            }
            logcat.destroy();
        });
        timer.setDaemon(true);
        timer.start();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(logcat.getInputStream()))) {
            for (String line; (line = in.readLine()) != null; ) {
                int at = line.indexOf("type = ");
                if (at >= 0 && line.contains("TclPowerManagerServiceEx")) {
                    cecWake = line.startsWith("CEC", at + 7);
                    if (!cecWake) return 0;
                    continue;
                }
                at = line.indexOf("handleCecCommand:<Active Source> ");
                if (at < 0) continue;
                // "8F:82:10:00": source 8 to all, physical address 1.0.0.0
                String[] b = line.substring(at + 33).trim().split(":");
                if (b.length < 4 || b[0].startsWith("0")) continue;  // the TV's own
                int port = Integer.parseInt(b[2], 16) >> 4;
                if (port > 0) {
                    Log.i(TAG, "active source on port " + port + " after " + (SystemClock.elapsedRealtime() - t0) + " ms");
                    return port;
                }
            }
        } catch (IOException | RuntimeException ignored) {
        } finally {
            timer.interrupt();
            logcat.destroy();
        }
        return 0;
    }
}
