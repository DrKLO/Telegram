package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

public class MessageBackgroundDrawable extends Drawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastAnimationTime;
    private float currentAnimationProgress;
    private boolean isSelected;
    private boolean animationInProgress;
    private int finalRadius;
    private float touchX = -1;
    private float touchY = -1;

    public final static float ANIMATION_DURATION = 200.0f;

    public MessageBackgroundDrawable(int color) {
        paint.setColor(color);
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    public void setSelected(boolean selected, boolean animated) {
        animated = false;
        if (isSelected == selected) {
            if (animationInProgress != animated && !animated) {
                currentAnimationProgress = selected ? 1.0f : 0.0f;
                animationInProgress = false;
            }
            return;
        }
        isSelected = selected;
        animationInProgress = animated;
        if (animated) {
            lastAnimationTime = SystemClock.uptimeMillis();
        } else {
            currentAnimationProgress = selected ? 1.0f : 0.0f;
        }
        calcRadius();
        invalidateSelf();
    }

    private void calcRadius() {
        Rect bounds = getBounds();
        float x1;
        float y1;
        if (touchX >= 0 && touchY >= 0) {
            x1 = touchX;
            y1 = touchY;
        } else {
            x1 = bounds.centerX();
            y1 = bounds.centerY();
        }
        finalRadius = 0;
        for (int a = 0; a < 4; a++) {
            float x2;
            float y2;
            switch (a) {
                case 0:
                    x2 = bounds.left;
                    y2 = bounds.top;
                    break;
                case 1:
                    x2 = bounds.left;
                    y2 = bounds.bottom;
                    break;
                case 2:
                    x2 = bounds.right;
                    y2 = bounds.top;
                    break;
                case 3:
                default:
                    x2 = bounds.right;
                    y2 = bounds.bottom;
                    break;
            }
            finalRadius = Math.max(finalRadius, (int) Math.ceil(Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))));
        }
    }

    public void setTouchCoords(float x, float y) {
        touchX = x;
        touchY = y;
        calcRadius();
        invalidateSelf();
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        calcRadius();
    }

    @Override
    public void setBounds(Rect bounds) {
        super.setBounds(bounds);
        calcRadius();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void draw(Canvas canvas) {
        if (animationInProgress) {
            long newTime = SystemClock.uptimeMillis();
            long dt = newTime - lastAnimationTime;
            lastAnimationTime = newTime;

            if (isSelected) {
                currentAnimationProgress += dt / ANIMATION_DURATION;
                if (currentAnimationProgress >= 1.0f) {
                    touchX = -1;
                    touchY = -1;
                    currentAnimationProgress = 1.0f;
                    animationInProgress = false;
                }
                invalidateSelf();
            } else {
                currentAnimationProgress -= dt / ANIMATION_DURATION;
                if (currentAnimationProgress <= 0.0f) {
                    touchX = -1;
                    touchY = -1;
                    currentAnimationProgress = 0.0f;
                    animationInProgress = false;
                }
                invalidateSelf();
            }
        }
        if (currentAnimationProgress == 1.0f) {
            canvas.drawRect(getBounds(), paint);
        } else if (currentAnimationProgress != 0.0f) {
            float x1;
            float y1;
            if (touchX >= 0 && touchY >= 0) {
                x1 = touchX;
                y1 = touchY;
            } else {
                Rect bounds = getBounds();
                x1 = bounds.centerX();
                y1 = bounds.centerY();
            }
            canvas.drawCircle(x1, y1, finalRadius * CubicBezierInterpolator.EASE_OUT.getInterpolation(currentAnimationProgress), paint);
        }
    }
}
