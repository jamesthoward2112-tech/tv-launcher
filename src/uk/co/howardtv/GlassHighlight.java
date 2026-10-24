package uk.co.howardtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * The single translucent "glass" focus highlight. It covers the whole content frame and
 * draws one rounded rect, which springs from its current place to the next target, so
 * tiles need no focus borders of their own.
 */
public class GlassHighlight extends View {
    private final RectF rect = new RectF(), from = new RectF(), to = new RectF();
    private float radius, fromRadius, toRadius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    // Unit-height gradient, scaled onto the rect each frame rather than rebuilt.
    private final LinearGradient sheenShader =
            new LinearGradient(0, 0, 0, 1, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
    private final Matrix sheenMatrix = new Matrix();
    private final ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
    private boolean shown;

    public GlassHighlight(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        density = getResources().getDisplayMetrics().density;
        fill.setColor(0x1AFFFFFF);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.5f * density);
        edge.setColor(0xB3FFFFFF);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(1.5f * density);
        sheen.setShader(sheenShader);
        anim.setDuration(480);
        anim.setInterpolator(Spring.INSTANCE);
        anim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            rect.set(lerp(from.left, to.left, f), lerp(from.top, to.top, f),
                    lerp(from.right, to.right, f), lerp(from.bottom, to.bottom, f));
            radius = lerp(fromRadius, toRadius, f);
            invalidate();
        });
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    /** Moves to target (in this view's coordinates); jumps there if animate is false. */
    void moveTo(RectF target, float cornerRadius, boolean animate) {
        anim.cancel();
        if (!animate || !shown) {
            rect.set(target);
            radius = cornerRadius;
            shown = true;
            invalidate();
            return;
        }
        from.set(rect);
        fromRadius = radius;
        to.set(target);
        toRadius = cornerRadius;
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!shown || rect.isEmpty()) return;
        c.drawRoundRect(rect, radius, radius, fill);
        // Light catching the top edge of the glass.
        sheenMatrix.setScale(1f, rect.height() * 0.55f);
        sheenMatrix.postTranslate(0, rect.top);
        sheenShader.setLocalMatrix(sheenMatrix);
        c.drawRoundRect(rect, radius, radius, sheen);
        float inset = edge.getStrokeWidth() / 2;
        c.drawRoundRect(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset,
                radius, radius, edge);
        // A faint light just outside the edge, so the glass reads on white banners too.
        // Three thin strokes stand in for a blur.
        int[] alpha = {0x40, 0x1C, 0x0A};
        for (int i = 0; i < alpha.length; i++) {
            float o = (1.5f + 1.5f * i) * density;
            halo.setColor(alpha[i] << 24 | 0xFFFFFF);
            c.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, halo);
        }
    }
}
