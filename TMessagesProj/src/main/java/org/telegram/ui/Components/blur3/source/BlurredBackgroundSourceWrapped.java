package org.telegram.ui.Components.blur3.source;

import android.graphics.Canvas;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableSource;

public class BlurredBackgroundSourceWrapped implements BlurredBackgroundSource {
    @Override
    public BlurredBackgroundDrawable createDrawable() {
        return new BlurredBackgroundDrawableSource(this);
    }


    private BlurredBackgroundSource sourceInternal;

    public BlurredBackgroundSource getSource() {
        return sourceInternal;
    }

    public void setSource(BlurredBackgroundSource sourceInternal) {
        this.sourceInternal = sourceInternal;
    }

    @Override
    public void draw(Canvas canvas, float left, float top, float right, float bottom) {
        if (sourceInternal != null) {
            sourceInternal.draw(canvas, left, top, right, bottom);
        }
    }
}
