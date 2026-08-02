package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ActivityWindowEmptyBackgroundDrawable extends Drawable {
    private ColorFilter mColorFilter;
    private int mAlpha = 255;

    @Override
    public void draw(@NonNull Canvas canvas) {

    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mColorFilter = colorFilter;
    }

    @Nullable
    @Override
    public ColorFilter getColorFilter() {
        return mColorFilter;
    }

    @Override
    public int getAlpha() {
        return mAlpha;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
