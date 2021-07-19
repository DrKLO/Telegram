package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ColoredImageSpan extends ReplacementSpan {

    int drawableColor;
    Drawable drawable;

    public ColoredImageSpan(@NonNull Drawable drawable) {
        this.drawable = drawable;
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence charSequence, int i, int i1, @Nullable Paint.FontMetricsInt fontMetricsInt) {
        return drawable.getIntrinsicWidth();
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        if (drawableColor != paint.getColor()) {
            drawableColor = paint.getColor();
            drawable.setColorFilter(new PorterDuffColorFilter(drawableColor, PorterDuff.Mode.MULTIPLY));
        }
        int lineHeight = bottom - top;
        int drawableHeight = drawable.getIntrinsicHeight();
        int padding = (lineHeight - drawableHeight) / 2;

        canvas.save();
        canvas.translate(x, top + padding);
        drawable.draw(canvas);
        canvas.restore();
    }
}
