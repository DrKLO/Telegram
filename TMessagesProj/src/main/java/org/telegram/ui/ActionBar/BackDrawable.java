/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.ActionBar;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;

public class BackDrawable extends Drawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean reverseAngle = false;
    private long lastFrameTime;
    private boolean animationInProgress;
    private float finalRotation;
    private float currentRotation;
    private int currentAnimationTime;
    private boolean alwaysClose;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator();

    public BackDrawable(boolean close) {
        super();
        paint.setColor(0xffffffff);
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        alwaysClose = close;
    }

    public void setRotation(float rotation, boolean animated) {
        lastFrameTime = 0;
        if (currentRotation == 1) {
            reverseAngle = true;
        } else if (currentRotation == 0) {
            reverseAngle = false;
        }
        lastFrameTime = 0;
        if (animated) {
            if (currentRotation < rotation) {
                currentAnimationTime = (int) (currentRotation * 300);
            } else {
                currentAnimationTime = (int) ((1.0f - currentRotation) * 300);
            }
            lastFrameTime = System.currentTimeMillis();
            finalRotation = rotation;
        } else {
            finalRotation = currentRotation = rotation;
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        if (currentRotation != finalRotation) {
            if (lastFrameTime != 0) {
                long dt = System.currentTimeMillis() - lastFrameTime;

                currentAnimationTime += dt;
                if (currentAnimationTime >= 300) {
                    currentRotation = finalRotation;
                } else {
                    if (currentRotation < finalRotation) {
                        currentRotation = interpolator.getInterpolation(currentAnimationTime / 300.0f) * finalRotation;
                    } else {
                        currentRotation = 1.0f - interpolator.getInterpolation(currentAnimationTime / 300.0f);
                    }
                }
            }
            lastFrameTime = System.currentTimeMillis();
            invalidateSelf();
        }

        int rD = (int) ((117 - 255) * currentRotation);
        int c = Color.rgb(255 + rD, 255 + rD, 255 + rD);
        paint.setColor(c);

        canvas.save();
        canvas.translate(getIntrinsicWidth() / 2, getIntrinsicHeight() / 2);
        float rotation = currentRotation;
        if (!alwaysClose) {
            canvas.rotate(currentRotation * (reverseAngle ? -225 : 135));
        } else {
            canvas.rotate(135 + currentRotation * (reverseAngle ? -180 : 180));
            rotation = 1.0f;
        }
        canvas.drawLine(-AndroidUtilities.dp(7) - AndroidUtilities.dp(1) * rotation, 0, AndroidUtilities.dp(8), 0, paint);
        float startYDiff = -AndroidUtilities.dp(0.5f);
        float endYDiff = AndroidUtilities.dp(7) + AndroidUtilities.dp(1) * rotation;
        float startXDiff = -AndroidUtilities.dp(7.0f) + AndroidUtilities.dp(7.0f) * rotation;
        float endXDiff = AndroidUtilities.dp(0.5f) - AndroidUtilities.dp(0.5f) * rotation;
        canvas.drawLine(startXDiff, -startYDiff, endXDiff, -endYDiff, paint);
        canvas.drawLine(startXDiff, startYDiff, endXDiff, endYDiff, paint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24);
    }
}
