/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class SendingFileDrawable extends Drawable {

    private float radOffset = 0;
    private float currentProgress = 0;
    private float animationProgressStart = 0;
    private long currentProgressTime = 0;
    private float animatedProgressValue = 0;
    private RectF cicleRect = new RectF();
    private boolean isChat = false;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastUpdateTime = 0;
    private boolean started = false;
    private static DecelerateInterpolator decelerateInterpolator = null;

    public SendingFileDrawable() {
        super();
        paint.setColor(Theme.ACTION_BAR_SUBTITLE_COLOR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        decelerateInterpolator = new DecelerateInterpolator();
    }

    public void setIsChat(boolean value) {
        isChat = value;
    }

    public void setProgress(float value, boolean animated) {
        if (!animated) {
            animatedProgressValue = value;
            animationProgressStart = value;
        } else {
            animationProgressStart = animatedProgressValue;
        }
        currentProgress = value;
        currentProgressTime = 0;

        invalidateSelf();
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;

        if (animatedProgressValue != 1) {
            radOffset += 360 * dt / 1000.0f;
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
            invalidateSelf();
        }
    }

    public void start() {
        lastUpdateTime = System.currentTimeMillis();
        started = true;
        invalidateSelf();
    }

    public void stop() {
        started = false;
    }

    @Override
    public void draw(Canvas canvas) {
        cicleRect.set(AndroidUtilities.dp(1), AndroidUtilities.dp(isChat ? 3 : 4), AndroidUtilities.dp(10), AndroidUtilities.dp(isChat ? 11 : 12));
        canvas.drawArc(cicleRect, -90 + radOffset, Math.max(60, 360 * animatedProgressValue), false, paint);

        if (started) {
            update();
        }
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(14);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(14);
    }
}
