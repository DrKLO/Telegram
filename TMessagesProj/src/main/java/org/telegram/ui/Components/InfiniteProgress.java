package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;

public class InfiniteProgress {

    private long lastUpdateTime;
    private float radOffset;
    private float currentCircleLength;
    private boolean risingCircleLength;
    private float currentProgressTime;
    private RectF cicleRect = new RectF();

    private int progressColor;

    private Paint progressPaint;
    private static final float rotationTime = 2000;
    private static final float risingTime = 500;
    private int radius;

    public InfiniteProgress(int rad) {
        radius = rad;

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setAlpha(float alpha) {
        progressPaint.setAlpha((int) (alpha * Color.alpha(progressColor)));
    }

    public void setColor(int color) {
        progressColor = color;
        progressPaint.setColor(progressColor);
    }

    private void updateAnimation() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        lastUpdateTime = newTime;

        radOffset += 360 * dt / rotationTime;
        int count = (int) (radOffset / 360);
        radOffset -= count * 360;

        currentProgressTime += dt;
        if (currentProgressTime >= risingTime) {
            currentProgressTime = risingTime;
        }
        if (risingCircleLength) {
            currentCircleLength = 4 + 266 * AndroidUtilities.accelerateInterpolator.getInterpolation(currentProgressTime / risingTime);
        } else {
            currentCircleLength = 4 - 270 * (1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(currentProgressTime / risingTime));
        }
        if (currentProgressTime == risingTime) {
            if (risingCircleLength) {
                radOffset += 270;
                currentCircleLength = -266;
            }
            risingCircleLength = !risingCircleLength;
            currentProgressTime = 0;
        }
    }

    public void draw(Canvas canvas, float cx, float cy, float scale) {
        cicleRect.set(cx - radius * scale, cy - radius * scale, cx + radius * scale, cy + radius * scale);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(2) * scale);
        canvas.drawArc(cicleRect, radOffset, currentCircleLength, false, progressPaint);
        updateAnimation();
    }
}
