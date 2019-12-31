package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

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
    public void draw(Canvas canvas) {
        if (progress < 1.0f) {
            topDrawable.setAlpha((int) (255f * (1.0f - progress)));
            topDrawable.draw(canvas);
        }
        if (progress > 0.0f) {
            bottomDrawable.setAlpha((int) (255.0f * progress));
            bottomDrawable.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

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

    public void setProgress(float value) {
        progress = value;
    }
}