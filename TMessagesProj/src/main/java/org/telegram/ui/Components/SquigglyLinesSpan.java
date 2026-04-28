package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.LineBackgroundSpan;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.HintView2;

public class SquigglyLinesSpan extends CharacterStyle {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public SquigglyLinesSpan() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void updateDrawState(TextPaint tp) {}

    public void draw(Canvas canvas, float y, float left, float right) {
        final float strokeWidth = dp(1.33f);
        final float wavelength = dp(10);
        final float amplitude = dp(2);

        paint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        paint.setStrokeWidth(strokeWidth);

        float x = left;
        path.rewind();
        path.moveTo(x, y);
        while (x < right) {
            path.quadTo(
                x + wavelength / 4f, y - amplitude,
                x + wavelength / 2f, y
            );
            path.quadTo(
                x + 3f * wavelength / 4f, y + amplitude,
                x + wavelength, y
            );
            x += wavelength;
        }
        if (x > right) {
            canvas.save();
            canvas.clipRect(left - strokeWidth / 2f, y - amplitude - strokeWidth / 2f, right + strokeWidth / 2f, y + amplitude + strokeWidth / 2f);
            canvas.drawPath(path, paint);
            canvas.restore();
        } else {
            canvas.drawPath(path, paint);
        }
    }

    public static void drawOnText(Canvas canvas, Layout layout) {
        if (layout == null) return;
        final CharSequence _text = layout.getText();
        if (_text == null) return;
        if (!(_text instanceof Spanned)) return;
        final Spanned text = (Spanned) _text;

        final SquigglyLinesSpan[] spans = text.getSpans(0, text.length(), SquigglyLinesSpan.class);
        if (spans == null || spans.length == 0) return;

        for (int i = 0; i < spans.length; ++i) {
            final SquigglyLinesSpan span = spans[i];

            final int start = text.getSpanStart(span);
            final int end   = text.getSpanEnd(span);

            final int lineStart = layout.getLineForOffset(start);
            final int lineEnd = layout.getLineForOffset(end);

            for (int line = lineStart; line <= lineEnd; ++line) {
                span.draw(
                    canvas,
                    layout.getLineBottom(line) - dp(1),
                    layout.getPrimaryHorizontal(line == lineStart ? start : layout.getLineStart(line)),
                    layout.getPrimaryHorizontal(line == lineEnd ? end : layout.getLineEnd(line) - 1)
                );
            }
        }
    }
}
