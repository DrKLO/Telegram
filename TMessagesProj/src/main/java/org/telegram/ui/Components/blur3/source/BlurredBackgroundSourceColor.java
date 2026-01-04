package org.telegram.ui.Components.blur3.source;

import android.graphics.Canvas;
import android.graphics.Paint;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableSource;

public class BlurredBackgroundSourceColor implements BlurredBackgroundSource {
    @Override
    public BlurredBackgroundDrawable createDrawable() {
        return new BlurredBackgroundDrawableSource(this);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void setColor(int color) {
        paint.setColor(color);
    }

    public int getColor() {
        return paint.getColor();
    }

    @Override
    public void draw(Canvas canvas, float left, float top, float right, float bottom) {
        canvas.drawRect(left, top, right, bottom, paint);
    }
}
