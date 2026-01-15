package org.telegram.ui.Components.blur3.capture;

import android.graphics.Canvas;
import android.graphics.RectF;

import androidx.annotation.Nullable;

public interface IBlur3Capture {
    void capture(Canvas canvas, RectF position);

    default long captureCalculateHash(RectF position) {
        return -1;
    }
}
