package uk.co.howardtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The banner for an input tile in place of an app's: a dark card washed with the input's
 * colour, its icon on the left and its name beside it, drawn at whatever size the tile is.
 */
final class InputBanner extends Drawable {
    private final Drawable icon;
    private final String label;
    private final int tint;
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    InputBanner(Context ctx, int iconRes, String label, int tint) {
        this.icon = ctx.getDrawable(iconRes).mutate();
        this.label = label;
        this.tint = tint;
        density = ctx.getResources().getDisplayMetrics().density;
        text.setColor(0xFFF2F2F5);
        text.setTypeface(Fonts.outfit(ctx.getAssets(), 500));
    }

    @Override
    protected void onBoundsChange(Rect b) {
        // The colour sits in the top left corner and fades to the tile's usual grey.
        fill.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                (tint & 0x00FFFFFF) | 0x8C000000, 0xFF1E1E21, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        c.drawColor(0xFF1E1E21);
        c.drawRect(b, fill);
        int size = Math.round(b.height() * 0.36f);
        text.setTextSize(b.height() * 0.2f);
        float gap = 10 * density, width = size + gap + text.measureText(label);
        int left = Math.round(b.centerX() - width / 2), top = b.centerY() - size / 2;
        icon.setBounds(left, top, left + size, top + size);
        icon.draw(c);
        Paint.FontMetrics m = text.getFontMetrics();
        c.drawText(label, left + size + gap, b.centerY() - (m.ascent + m.descent) / 2, text);
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(ColorFilter cf) {}

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
