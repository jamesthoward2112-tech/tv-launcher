package com.ambient.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** A thin rounded track with a white fill: the volume level, 0..1. */
public class VolumeBar extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level;

    public VolumeBar(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        track.setColor(0x33FFFFFF);
        fill.setColor(0xFFF2F2F4);
    }

    /** Dark track and fill, for the filled (focused) row. */
    void setDark(boolean dark) {
        track.setColor(dark ? 0x33000000 : 0x33FFFFFF);
        fill.setColor(dark ? 0xFF1F2023 : 0xFFF2F2F4);
        invalidate();
    }

    void setLevel(float l) {
        level = Math.max(0f, Math.min(1f, l));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = 4 * getResources().getDisplayMetrics().density, top = (getHeight() - h) / 2;
        c.drawRoundRect(0, top, getWidth(), top + h, h / 2, h / 2, track);
        if (level > 0) c.drawRoundRect(0, top, Math.max(h, getWidth() * level), top + h, h / 2, h / 2, fill);
    }
}
