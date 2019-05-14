/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;

public class MenuDrawable extends Drawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean reverseAngle;
    private long lastFrameTime;
    private boolean animationInProgress;
    private float finalRotation;
    private float currentRotation;
    private int currentAnimationTime;
    private boolean rotateToBack = true;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator();

    public MenuDrawable() {
        super();
        paint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    public void setRotateToBack(boolean value) {
        rotateToBack = value;
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

        canvas.save();
        canvas.translate(getIntrinsicWidth() / 2, getIntrinsicHeight() / 2);
        float endYDiff;
        float endXDiff;
        float startYDiff;
        float startXDiff;
        int color1 = Theme.getColor(Theme.key_actionBarDefaultIcon);
        if (rotateToBack) {
            canvas.rotate(currentRotation * (reverseAngle ? -180 : 180));
            paint.setColor(color1);
            canvas.drawLine(-AndroidUtilities.dp(9), 0, AndroidUtilities.dp(9) - AndroidUtilities.dp(3.0f) * currentRotation, 0, paint);
            endYDiff = AndroidUtilities.dp(5) * (1 - Math.abs(currentRotation)) - AndroidUtilities.dp(0.5f) * Math.abs(currentRotation);
            endXDiff = AndroidUtilities.dp(9) - AndroidUtilities.dp(2.5f) * Math.abs(currentRotation);
            startYDiff = AndroidUtilities.dp(5) + AndroidUtilities.dp(2.0f) * Math.abs(currentRotation);
            startXDiff = -AndroidUtilities.dp(9) + AndroidUtilities.dp(7.5f) * Math.abs(currentRotation);
        } else {
            canvas.rotate(currentRotation * (reverseAngle ? -225 : 135));
            int color2 = Theme.getColor(Theme.key_actionBarActionModeDefaultIcon);
            paint.setColor(AndroidUtilities.getOffsetColor(color1, color2, currentRotation, 1.0f));
            canvas.drawLine(-AndroidUtilities.dp(9) + AndroidUtilities.dp(1) * currentRotation, 0, AndroidUtilities.dp(9) - AndroidUtilities.dp(1) * currentRotation, 0, paint);
            endYDiff = AndroidUtilities.dp(5) * (1 - Math.abs(currentRotation)) - AndroidUtilities.dp(0.5f) * Math.abs(currentRotation);
            endXDiff = AndroidUtilities.dp(9) - AndroidUtilities.dp(9) * Math.abs(currentRotation);
            startYDiff = AndroidUtilities.dp(5) + AndroidUtilities.dp(3.0f) * Math.abs(currentRotation);
            startXDiff = -AndroidUtilities.dp(9) + AndroidUtilities.dp(9) * Math.abs(currentRotation);
        }
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
