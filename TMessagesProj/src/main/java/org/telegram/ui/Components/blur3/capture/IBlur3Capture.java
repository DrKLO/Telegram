package org.telegram.ui.Components.blur3.capture;

import android.graphics.Canvas;
import android.graphics.RectF;

public interface IBlur3Capture {
    void capture(Canvas canvas, RectF position);

    default void captureCalculateHash(IBlur3Hash builder, RectF position) {
        builder.unsupported();
    }
}
