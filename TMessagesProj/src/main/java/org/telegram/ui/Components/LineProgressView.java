/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;

public class LineProgressView extends View {

    private long lastUpdateTime = 0;
    private float currentProgress = 0;
    private float animationProgressStart = 0;
    private long currentProgressTime = 0;
    private float animatedProgressValue = 0;
    private float animatedAlphaValue = 1.0f;

    private int backColor;
    private int progressColor;

    private static DecelerateInterpolator decelerateInterpolator = null;
    private static Paint progressPaint = null;

    public LineProgressView(Context context) {
        super(context);

        if (decelerateInterpolator == null) {
            decelerateInterpolator = new DecelerateInterpolator();
            progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
        }
    }

    private void updateAnimation() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;

        if (animatedProgressValue != 1 && animatedProgressValue != currentProgress) {
            float progressDiff = currentProgress - animationProgressStart;
            if (progressDiff > 0) {
                currentProgressTime += dt;
                if (currentProgressTime >= 300) {
                    animatedProgressValue = currentProgress;
                    animationProgressStart = currentProgress;
                    currentProgressTime = 0;
                } else {
                    animatedProgressValue = animationProgressStart + progressDiff * decelerateInterpolator.getInterpolation(currentProgressTime / 300.0f);
                }
            }
            invalidate();
        }
        if (animatedProgressValue >= 1 && animatedProgressValue == 1 && animatedAlphaValue != 0) {
            animatedAlphaValue -= dt / 200.0f;
            if (animatedAlphaValue <= 0) {
                animatedAlphaValue = 0.0f;
            }
            invalidate();
        }
    }

    public void setProgressColor(int color) {
        progressColor = color;
    }

    public void setBackColor(int color) {
        backColor = color;
    }

    public void setProgress(float value, boolean animated) {
        if (!animated) {
            animatedProgressValue = value;
            animationProgressStart = value;
        } else {
            animationProgressStart = animatedProgressValue;
        }
        if (value != 1) {
            animatedAlphaValue = 1;
        }
        currentProgress = value;
        currentProgressTime = 0;

        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public void onDraw(Canvas canvas) {
        if (backColor != 0 && animatedProgressValue != 1) {
            progressPaint.setColor(backColor);
            progressPaint.setAlpha((int) (255 * animatedAlphaValue));
            int start = (int) (getWidth() * animatedProgressValue);
            canvas.drawRect(start, 0, getWidth(), getHeight(), progressPaint);
        }

        progressPaint.setColor(progressColor);
        progressPaint.setAlpha((int)(255 * animatedAlphaValue));
        canvas.drawRect(0, 0, getWidth() * animatedProgressValue, getHeight(), progressPaint);
        updateAnimation();
    }
}
