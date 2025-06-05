package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import org.telegram.messenger.AndroidUtilities;

public class CircularProgressDrawable extends Drawable {

    public float size = AndroidUtilities.dp(18);
    public float thickness = AndroidUtilities.dp(2.25f);

    public CircularProgressDrawable() {
        this(0xffffffff);
    }
    public CircularProgressDrawable(int color) {
        setColor(color);
    }
    public CircularProgressDrawable(float size, float thickness, int color) {
        this.size = size;
        this.thickness = thickness;
        setColor(color);
    }

    private long start = -1;
    public static final FastOutSlowInInterpolator interpolator = new FastOutSlowInInterpolator();
    private float[] segment = new float[2];
    private void updateSegment() {
        final long now = SystemClock.elapsedRealtime();
        final long t = (now - start) % 5400;
        getSegments(t, segment);
    }

    public static void getSegments(float t, float[] segments) {
        segments[0] = Math.max(0, 1520 * t / 5400f - 20);
        segments[1] = 1520 * t / 5400f;
        for (int i = 0; i < 4; ++i) {
            segments[1] += interpolator.getInterpolation((t - i * 1350) / 667f) * 250;
            segments[0] += interpolator.getInterpolation((t - (667 + i * 1350)) / 667f) * 250;
        }
    }

    private final Paint paint = new Paint(); {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    private float angleOffset;
    private final RectF bounds = new RectF();

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (start < 0) {
            start = SystemClock.elapsedRealtime();
        }
        updateSegment();
        canvas.drawArc(
            bounds,
            angleOffset + segment[0],
            segment[1] - segment[0],
            false,
            paint
        );
        invalidateSelf();
    }

    public void reset() {
        start = -1;
    }

    public void setAngleOffset(float angleOffset) {
        this.angleOffset = angleOffset;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        int width = right - left, height = bottom - top;
        bounds.set(
            left + (width - thickness / 2f - size) / 2f,
            top + (height - thickness / 2f - size) / 2f,
            left + (width + thickness / 2f + size) / 2f,
            top + (height + thickness / 2f + size) / 2f
        );
        super.setBounds(left, top, right, bottom);
        paint.setStrokeWidth(thickness);
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) (size + thickness);
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) (size + thickness);
    }
}
