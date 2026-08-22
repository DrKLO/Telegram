package org.telegram.ui.iv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spanned;
import android.text.style.ReplacementSpan;

import org.telegram.messenger.FileLog;

/**
 * Inline LaTeX span for the rich editor. It renders the equation as a baseline-aligned bitmap (the same way
 * {@code TL_iv.textMath} is drawn in messages) and carries the original {@code source} so the run can be
 * serialized back into a {@link org.telegram.tgnet.tl.TL_iv.textMath} node when the message is sent.
 */
public class MathSpan extends ReplacementSpan {

    public final String source;

    private final Bitmap bitmap;
    private final int width;
    private final int height;
    private final int depth;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private MathSpan(String source, Bitmap bitmap, int w, int h, int color, int depth) {
        this.source = source;
        this.bitmap = bitmap;
        this.width = w;
        this.height = h;
        this.depth = depth;
        paint.setColor(color);
    }

    /** Builds a span for {@code source} rendered at {@code textSizePx}, or null if it can't be rendered. */
    public static MathSpan create(String source, int color, float textSizePx) {
        if (source == null || source.isEmpty()) return null;
        final Latex r = Latex.render(source, textSizePx, true);
        if (r == null) return null;
        return new MathSpan(source, r.bitmap, r.width, r.height, color, r.depth);
    }

    @Override
    public int getSize(Paint p, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            // baseline-aligned: depth sits below the baseline, the rest rises above it
            fm.top = fm.ascent = -(height - depth);
            fm.bottom = fm.descent = depth;
        }
        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint p) {
        if (bitmap == null) return;
        // ALPHA_8 bitmap is tinted by the paint colour; follow the surrounding text colour.
        paint.setColor(p.getColor());
        canvas.drawBitmap(bitmap, x, y - (height - depth), paint);
    }

    /** Source of the first math span covering [from, to), or null if none. */
    public static String sourceAt(CharSequence cs, int from, int to) {
        if (!(cs instanceof Spanned)) return null;
        final MathSpan[] spans = ((Spanned) cs).getSpans(from, to, MathSpan.class);
        return spans.length > 0 ? spans[0].source : null;
    }
}
