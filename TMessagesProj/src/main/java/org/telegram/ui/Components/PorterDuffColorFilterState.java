package org.telegram.ui.Components;

import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

public class PorterDuffColorFilterState {
    private ColorFilter colorFilter;
    private int lastColor;
    private PorterDuff.Mode lastMode;

    public ColorFilter get(int color, PorterDuff.Mode mode) {
        if (colorFilter == null || lastColor != color || lastMode != mode) {
            colorFilter = new PorterDuffColorFilter(color, mode);
            lastColor = color;
            lastMode = mode;
        }
        return colorFilter;
    }
}
