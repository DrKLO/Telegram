package org.telegram.utils.glass.source;

import android.graphics.Canvas;

import androidx.annotation.NonNull;

public interface GlassSource {
    void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom);

    default long getGenerationId() {
        return 0;
    }
}
