package org.telegram.utils.glass.source;

import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.annotation.NonNull;

public final class GlassSourceColor implements GlassSource {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color;

    public void setColor(int color) {
        if (this.color != color) {
            this.color = color;
            paint.setColor(color);
        }
    }

    public int getColor() {
        return color;
    }

    @Override
    public void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom) {
        canvas.drawRect(left, top, right, bottom, paint);
    }

    @Override
    public long getGenerationId() {
        return color;
    }
}
