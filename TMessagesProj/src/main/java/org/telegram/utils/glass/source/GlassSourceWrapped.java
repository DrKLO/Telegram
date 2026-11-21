package org.telegram.utils.glass.source;

import android.graphics.Canvas;

import androidx.annotation.NonNull;


public final class GlassSourceWrapped implements GlassSource {
    private GlassSource sourceInternal;

    public GlassSource getSource() {
        return sourceInternal;
    }

    public void setSource(GlassSource sourceInternal) {
        this.sourceInternal = sourceInternal;
    }

    @Override
    public void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom) {
        if (sourceInternal != null) {
            sourceInternal.draw(canvas, left, top, right, bottom);
        }
    }
}
