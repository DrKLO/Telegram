package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CrossfadeDrawable extends Drawable {

    private final Drawable topDrawable;
    private final Drawable bottomDrawable;

    private float progress;

    public CrossfadeDrawable(Drawable topDrawable, Drawable bottomDrawable) {
        this.topDrawable = topDrawable;
        this.bottomDrawable = bottomDrawable;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        topDrawable.setBounds(bounds);
        bottomDrawable.setBounds(bounds);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (progress < 1f) {
            topDrawable.setAlpha((int) (255f * (1f - progress)));
            topDrawable.draw(canvas);
        }
        if (progress > 0f) {
            bottomDrawable.setAlpha((int) (255f * progress));
            bottomDrawable.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return topDrawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return topDrawable.getIntrinsicHeight();
    }

    public float getProgress() {
        return progress;
    }

    public void setProgress(float progress) {
        this.progress = progress;
    }
}
