package uk.co.howardtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Minimal Apple-TV-inspired favourite cards: identical charcoal backgrounds and
 * centred white symbols. Labels belong to the glowing focus title, not the tiles.
 * Shapes are drawn locally, so third-party TV banners cannot crop or change them.
 */
final class BrandTile {
    private static final int W = 384, H = 280;
    private static final int CARD = 0xFF202832;
    private static final int EDGE = 0xFF3A4554;

    private BrandTile() {}

    static Drawable create(Context ctx, String id) {
        Bitmap image = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(CARD);
        canvas.drawRoundRect(new RectF(1, 1, W - 1, H - 1), 28, 28, p);
        p.setColor(EDGE);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        canvas.drawRoundRect(new RectF(3, 3, W - 3, H - 3), 26, 26, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);

        switch (id) {
            case "netflix":
                // A simple, readable N, retaining Netflix's recognisable silhouette.
                polygon(canvas, p, 133, 67, 158, 67, 158, 213, 133, 213);
                polygon(canvas, p, 226, 67, 251, 67, 251, 213, 226, 213);
                polygon(canvas, p, 133, 67, 158, 67, 251, 213, 226, 213);
                break;
            case "disney":
                p.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                p.setTextSize(91);
                p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Disney+", W / 2f, 169, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(7);
                canvas.drawArc(new RectF(105, 67, 289, 144), 205, 135, false, p);
                break;
            case "prime":
                // Amazon's smile arrow, without a second in-tile text label.
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(15);
                p.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawArc(new RectF(86, 70, 291, 211), 16, 136, false, p);
                p.setStyle(Paint.Style.FILL);
                polygon(canvas, p, 282, 174, 305, 154, 302, 201);
                break;
            case "purpleglobe":
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(10);
                canvas.drawRoundRect(new RectF(105, 81, 279, 189), 10, 10, p);
                canvas.drawLine(192, 190, 192, 208, p);
                canvas.drawLine(158, 211, 226, 211, p);
                canvas.drawLine(179, 79, 160, 61, p);
                canvas.drawLine(204, 79, 223, 61, p);
                p.setStyle(Paint.Style.FILL);
                p.setTypeface(Fonts.lemon(ctx.getAssets()));
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(41);
                canvas.drawText("IPTV", 192, 152, p);
                break;
            case "kodi":
                diamond(canvas, p, 190, 79, 27);
                diamond(canvas, p, 148, 137, 27);
                diamond(canvas, p, 235, 137, 27);
                diamond(canvas, p, 190, 196, 27);
                break;
            case "youtube":
                canvas.drawRoundRect(new RectF(97, 83, 287, 195), 33, 33, p);
                p.setColor(CARD);
                polygon(canvas, p, 170, 110, 170, 168, 224, 139);
                break;
            case "spotify":
                canvas.drawCircle(192, 140, 75, p);
                p.setColor(CARD);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(11);
                canvas.drawArc(new RectF(143, 114, 255, 171), 207, 127, false, p);
                p.setStrokeWidth(9);
                canvas.drawArc(new RectF(148, 135, 248, 187), 208, 123, false, p);
                p.setStrokeWidth(7);
                canvas.drawArc(new RectF(155, 156, 240, 197), 210, 116, false, p);
                break;
            case "allapps":
                for (int x = 0; x < 2; x++)
                    for (int y = 0; y < 2; y++)
                        canvas.drawRoundRect(new RectF(129 + x * 72, 76 + y * 72,
                                182 + x * 72, 129 + y * 72), 11, 11, p);
                break;
            default:
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(95);
                canvas.drawText("?", 192, 168, p);
        }
        return new BitmapDrawable(ctx.getResources(), image);
    }

    private static void diamond(Canvas c, Paint p, float x, float y, float r) {
        polygon(c, p, x, y - r, x + r, y, x, y + r, x - r, y);
    }

    private static void polygon(Canvas c, Paint p, float... xy) {
        Path path = new Path();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        path.close();
        c.drawPath(path, p);
    }
}
