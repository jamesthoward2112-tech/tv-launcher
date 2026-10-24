package uk.co.howardtv;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import java.util.ArrayList;
import java.util.List;

/**
 * The TV's physical inputs from the TV input framework: HDMI ports, AV and other
 * passthrough inputs, then Live TV (the hardware tuner). Switching hands a standard
 * TvContract URI to whichever TV app handles it.
 */
final class Inputs {
    final String label;
    final Intent intent;
    final String id;

    private Inputs(String id, String label, Intent intent) {
        this.id = id;
        this.label = label;
        this.intent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private Inputs renamed(String newLabel) {
        return new Inputs(id, newLabel, intent);
    }

    static List<Inputs> list(Context ctx) {
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        List<Inputs> hdmi = new ArrayList<>(), other = new ArrayList<>(), tuner = new ArrayList<>();
        if (tim == null) return hdmi;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isHidden(ctx)) continue;
            // Hardware-backed inputs carry "/HW<n>" in their id; app-provided ones do not.
            boolean hardware = info.getId().contains("/HW");
            if (info.isPassthroughInput()) {
                Inputs in = new Inputs(info.getId(), name(ctx, info), new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(info.getId())));
                if (info.getType() == TvInputInfo.TYPE_HDMI) hdmi.add(in); else other.add(in);
            } else if (hardware && info.getType() == TvInputInfo.TYPE_TUNER && tuner.isEmpty()) {
                tuner.add(new Inputs(info.getId(), "Live TV", new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelsUriForInput(info.getId()))));
            }
        }
        // Port order (the ids number the ports), named by position: HDMI 1, 2, 3, ignoring
        // any custom names set in the TV's input settings.
        hdmi.sort((a, b) -> a.id.length() != b.id.length() ? a.id.length() - b.id.length() : a.id.compareTo(b.id));
        for (int i = 0; i < hdmi.size(); i++) hdmi.set(i, hdmi.get(i).renamed("HDMI " + (i + 1)));
        hdmi.addAll(other);
        hdmi.addAll(tuner);
        return hdmi;
    }

    private static String name(Context ctx, TvInputInfo info) {
        CharSequence label = info.loadLabel(ctx);
        if (label != null && label.length() > 0) return label.toString();
        switch (info.getType()) {
            case TvInputInfo.TYPE_HDMI: return "HDMI";
            case TvInputInfo.TYPE_COMPOSITE: return "AV";
            case TvInputInfo.TYPE_COMPONENT: return "Component";
            default: return "Input";
        }
    }
}
