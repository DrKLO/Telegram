package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BatteryDrawable extends Drawable {

    private Paint paintReference;
    private Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint connectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float scale = 1f, translateY = 0;
    private float fillValue = 1f;

    private RectF rectTmp = new RectF();

    public BatteryDrawable() {
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    public BatteryDrawable(float value) {
        this();
        setFillValue(value, false);
    }

    public BatteryDrawable(float value, int color) {
        this();
        setFillValue(value, false);
        setColor(color);
    }

    public BatteryDrawable(float value, int color, int fillColor) {
        this();
        setFillValue(value, false);
        setColor(color, fillColor);
    }

    public BatteryDrawable(float value, int color, int fillColor, float scale) {
        this();
        setFillValue(value, false);
        setColor(color, fillColor);
        setScale(scale);
    }

    public void setScale(float scale) {
        this.scale = scale;
        invalidateSelf();
    }

    public void setColor(int color) {
        setColor(color, color);
    }

    public void setColor(int color, int fillColor) {
        strokePaint.setColor(color);
        connectorPaint.setColor(color);
        fillPaint.setColor(fillColor);
    }

    private ValueAnimator fillValueAnimator;

    public void setFillValue(float newValue, boolean animated) {
        final float value = Math.max(Math.min(newValue, 1), 0);

        if (fillValueAnimator != null) {
            fillValueAnimator.cancel();
            fillValueAnimator = null;
        }

        if (!animated) {
            fillValue = value;
            invalidateSelf();
        } else {
            fillValueAnimator = ValueAnimator.ofFloat(fillValue, value);
            fillValueAnimator.addUpdateListener(anm -> {
                fillValue = (float) anm.getAnimatedValue();
                invalidateSelf();
            });
            fillValueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    fillValue = value;
                    invalidateSelf();
                }
            });
            fillValueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            fillValueAnimator.setDuration(200);
            fillValueAnimator.start();
        }
    }

    public void colorFromPaint(Paint paintReference) {
        this.paintReference = paintReference;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (getBounds() == null) {
            return;
        }

        final int x = getBounds().left, y = getBounds().top + (int) translateY;
        final int w = getBounds().width(), h = getBounds().height();
        final int cx = getBounds().centerX(), cy = getBounds().centerY() + (int) translateY;

        if (paintReference != null) {
            setColor(paintReference.getColor());
        }

        if (scale != 1) {
            canvas.save();
            canvas.scale(scale, scale, cx, cy);
        }

        strokePaint.setStrokeWidth(dpf2(1.1f));

        rectTmp.set(
                x + (w - dpf2(16.33f)) / 2f - dpf2(1.33f),
                y + (h - dpf2(10.33f)) / 2f,
                x + (w + dpf2(16.33f)) / 2f - dpf2(1.33f),
                y + (h + dpf2(10.33f)) / 2f
        );
        canvas.drawRoundRect(rectTmp, dpf2(2.33f), dpf2(2.33f), strokePaint);

        rectTmp.set(
                x + (w - dpf2(13f)) / 2f - dpf2(1.66f),
                y + (h - dpf2(7.33f)) / 2f,
                x + (w - dpf2(13f)) / 2f - dpf2(1.66f) + Math.max(dpf2(1.1f), fillValue * dpf2(13)),
                y + (h + dpf2(7.33f)) / 2f
        );
        canvas.drawRoundRect(rectTmp, dpf2(0.83f), dpf2(0.83f), fillPaint);

        rectTmp.set(
                x + (w + dpf2(17.5f) - dpf2(4.66f)) / 2f,
                cy - dpf2(2.65f),
                x + (w + dpf2(17.5f) + dpf2(4.66f)) / 2f,
                cy + dpf2(2.65f)
        );
        canvas.drawArc(rectTmp, -90, 180, false, connectorPaint);

        if (scale != 1) {
            canvas.restore();
        }
    }

    public void setTranslationY(float translateY) {
        this.translateY = translateY;
    }

    @Override
    public void setAlpha(int alpha) {
        strokePaint.setAlpha(alpha);
        connectorPaint.setAlpha(alpha);
        fillPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        strokePaint.setColorFilter(colorFilter);
        connectorPaint.setColorFilter(colorFilter);
        fillPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return dp(24 * scale);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(24 * scale);
    }
}