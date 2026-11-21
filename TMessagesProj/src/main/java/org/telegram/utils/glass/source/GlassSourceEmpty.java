package org.telegram.utils.glass.source;

import android.graphics.Canvas;

import androidx.annotation.NonNull;

public final class GlassSourceEmpty implements GlassSource {
    public static final GlassSourceEmpty INSTANCE = new GlassSourceEmpty();

    private GlassSourceEmpty() {

    }

    @Override
    public void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom) {

    }

    @Override
    public long getGenerationId() {
        return -1;
    }
}
