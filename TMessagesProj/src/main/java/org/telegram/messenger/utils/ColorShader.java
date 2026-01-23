package org.telegram.messenger.utils;

import android.graphics.LinearGradient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ColorShader extends LinearGradient {
    public ColorShader(int color) {
        super(0, 0, 1, 0, new int[] {color, color}, null, TileMode.CLAMP);
    }
}
