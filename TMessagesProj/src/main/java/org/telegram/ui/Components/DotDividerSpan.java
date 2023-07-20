package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class DotDividerSpan extends ReplacementSpan {

    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    int color;
    int topPadding;
    private int size = 3;

    @Override
    public int getSize(@NonNull Paint paint, CharSequence charSequence, int i, int i1, @Nullable Paint.FontMetricsInt fontMetricsInt) {
        return AndroidUtilities.dp(size);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        if (color != paint.getColor()) {
            p.setColor(paint.getColor());
        }
        float offset = AndroidUtilities.dpf2(size) / 2f;
        float radius = AndroidUtilities.dpf2(3) / 2f;
        canvas.drawCircle(x + offset, (bottom - top) / 2 + topPadding, radius, p);
    }

    public void setTopPadding(int topPadding) {
        this.topPadding = topPadding;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
