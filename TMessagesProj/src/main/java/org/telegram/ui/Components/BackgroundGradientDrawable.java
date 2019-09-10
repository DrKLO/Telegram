package org.telegram.ui.Components;

import android.graphics.drawable.GradientDrawable;

import androidx.annotation.Nullable;

public class BackgroundGradientDrawable extends GradientDrawable {

    private int[] colors;

    public BackgroundGradientDrawable(Orientation orientation, int[] colors) {
        super(orientation, colors);
        this.colors = colors;
    }

    @Nullable
    public int[] getColorsList() {
        return colors;
    }
}
