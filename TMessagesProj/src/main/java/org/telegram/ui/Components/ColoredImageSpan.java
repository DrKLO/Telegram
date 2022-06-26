package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

public class ColoredImageSpan extends ReplacementSpan {

    int drawableColor;
    Drawable drawable;

    boolean usePaintColor = true;
    String colorKey;
    private int topOffset = 0;

    private int size;

    public ColoredImageSpan(int imageRes) {
        this(ContextCompat.getDrawable(ApplicationLoader.applicationContext, imageRes));
    }

    public ColoredImageSpan(Drawable drawable) {
        this.drawable = drawable;
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    public void setSize(int size) {
        this.size = size;
        drawable.setBounds(0, 0, size, size);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence charSequence, int i, int i1, @Nullable Paint.FontMetricsInt fontMetricsInt) {
        return size != 0 ? size : drawable.getIntrinsicWidth();
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        int color;
        if (usePaintColor) {
            color = paint.getColor();
        } else {
            color = Theme.getColor(colorKey);
        }
        if (drawableColor != color) {
            drawableColor = color;
            drawable.setColorFilter(new PorterDuffColorFilter(drawableColor, PorterDuff.Mode.MULTIPLY));
        }
        int lineHeight = bottom - top;
        int drawableHeight = size != 0 ? size : drawable.getIntrinsicHeight();
        int padding = (lineHeight - drawableHeight) / 2;

        canvas.save();
        canvas.translate(x, top + padding + AndroidUtilities.dp(topOffset));
        drawable.draw(canvas);
        canvas.restore();
    }

    public void setColorKey(String colorKey) {
        this.colorKey = colorKey;
        usePaintColor = false;
    }

    public void setTopOffset(int topOffset) {
        this.topOffset = topOffset;
    }
}
