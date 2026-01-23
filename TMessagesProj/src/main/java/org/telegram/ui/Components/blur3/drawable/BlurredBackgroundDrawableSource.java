package org.telegram.ui.Components.blur3.drawable;

import android.graphics.Canvas;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;

public class BlurredBackgroundDrawableSource extends BlurredBackgroundDrawable {
    private final BlurredBackgroundSource source;

    public BlurredBackgroundDrawableSource(BlurredBackgroundSource source) {
        this.source = source;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        drawSource(canvas, source);
    }

    @Override
    public BlurredBackgroundSource getSource() {
        return source;
    }
}
