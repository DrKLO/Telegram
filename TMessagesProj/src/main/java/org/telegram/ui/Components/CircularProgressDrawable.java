package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AndroidUtilities;

public class CircularProgressDrawable extends Drawable {

    public CircularProgressDrawable() {
        this(0xffffffff);
    }
    public CircularProgressDrawable(int color) {
        setColor(color);
    }

    private long start = -1;
    private final FastOutSlowInInterpolator interpolator = new FastOutSlowInInterpolator();
    private float segmentFrom, segmentTo;
    private void updateSegment() {
        final float t = (SystemClock.elapsedRealtime() - start) % 5400f / 667f;
        segmentFrom =
            t * 187.748148f + 250 * (
                interpolator.getInterpolation(t - 1f) +
                interpolator.getInterpolation(t - 3.024f) +
                interpolator.getInterpolation(t - 5.048f) +
                interpolator.getInterpolation(t - 7.072f)
            ) - 20;
        segmentTo =
            t * 187.748148f + 250 * (
                interpolator.getInterpolation(t) +
                interpolator.getInterpolation(t - 2.024f) +
                interpolator.getInterpolation(t - 4.048f) +
                interpolator.getInterpolation(t - 6.072f)
            );
    }

    private final Paint paint = new Paint(); {
        paint.setStyle(Paint.Style.STROKE);
    }

    private final RectF bounds = new RectF();
    @Override
    public void draw(@NonNull Canvas canvas) {
        if (start < 0) {
            start = SystemClock.elapsedRealtime();
        }
        updateSegment();
        canvas.drawArc(
            bounds,
            segmentFrom,
            segmentTo - segmentFrom,
            false,
            paint
        );
        invalidateSelf();
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        final float radius = AndroidUtilities.dp(9);
        final float thickness = AndroidUtilities.dp(2.25f);
        int width = right - left, height = bottom - top;
        bounds.set(
            left + (width - thickness / 2f) / 2f - radius,
            top + (height - thickness / 2f) / 2f - radius,
            left + (width + thickness / 2f) / 2f + radius,
            top + (height + thickness / 2f) / 2f + radius
        );
        super.setBounds(left, top, right, bottom);
        paint.setStrokeWidth(thickness);
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    @Override
    public void setAlpha(int i) {}
    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
