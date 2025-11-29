package org.telegram.ui.Components.blur3.source;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

public interface BlurredBackgroundSource {
    BlurredBackgroundDrawable createDrawable();

    void draw(Canvas canvas, float left, float top, float right, float bottom);

    default void draw(Canvas canvas, Rect rect) {
        draw(canvas, rect.left, rect.top, rect.right, rect.bottom);
    }

    default void draw(Canvas canvas, RectF rect) {
        draw(canvas, rect.left, rect.top, rect.right, rect.bottom);
    }
}
