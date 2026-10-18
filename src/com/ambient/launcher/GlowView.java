package com.ambient.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * An oval of soft colour behind the dock title: a radial gradient stretched to the view's
 * shape. A shader, not a blur, so it costs one draw call.
 */
public class GlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = Color.TRANSPARENT;
    private ValueAnimator anim;

    public GlowView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    /** Fades from the current colour to c. */
    void setColor(int c) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofObject(new ArgbEvaluator(), color, c);
        anim.setDuration(450);
        anim.addUpdateListener(a -> {
            color = (int) a.getAnimatedValue();
            updateShader();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateShader();
    }

    private void updateShader() {
        float r = getHeight() / 2f;
        if (r <= 0) return;
        int a = Color.alpha(color);
        int rgb = color & 0xFFFFFF;
        paint.setShader(new RadialGradient(0, 0, r,
                new int[] {(a * 42 / 100) << 24 | rgb, (a * 16 / 100) << 24 | rgb, rgb},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (h <= 0) return;
        c.save();
        c.translate(w / 2, h / 2);
        c.scale(w / h, 1f);  // circle of radius h/2, stretched to the full width
        c.drawCircle(0, 0, h / 2, paint);
        c.restore();
    }
}
