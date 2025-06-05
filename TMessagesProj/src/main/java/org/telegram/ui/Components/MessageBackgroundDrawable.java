package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

public class MessageBackgroundDrawable extends Drawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint customPaint = null;
    private long lastAnimationTime;
    private float currentAnimationProgress;
    private boolean isSelected;
    private boolean animationInProgress;
    private float finalRadius;
    private float touchX = -1;
    private float touchY = -1;
    private float touchOverrideX = -1;
    private float touchOverrideY = -1;
    private long lastTouchTime;
    private View parentView;

    public MessageBackgroundDrawable(View parent) {
        parentView = parent;
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    public void setCustomPaint(Paint paint) {
        this.customPaint = paint;
    }

    public void setSelected(boolean selected, boolean animated) {
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
            lastAnimationTime = SystemClock.elapsedRealtime();
        } else {
            currentAnimationProgress = selected ? 1.0f : 0.0f;
        }
        calcRadius();
        invalidate();
    }

    private void invalidate() {
        if (parentView != null) {
            parentView.invalidate();
            if (parentView.getParent() != null) {
                ((ViewGroup) parentView.getParent()).invalidate();
            }
        }
    }

    private void calcRadius() {
        Rect bounds = getBounds();
        float x1 = bounds.centerX();
        float y1 = bounds.centerY();
        finalRadius = (float) Math.ceil(Math.sqrt((bounds.left - x1) * (bounds.left - x1) + (bounds.top - y1) * (bounds.top - y1)));
    }

    public void setTouchCoords(float x, float y) {
        touchX = x;
        touchY = y;
        lastTouchTime = SystemClock.elapsedRealtime();
    }

    public void setTouchCoordsOverride(float x, float y) {
        touchOverrideX = x;
        touchOverrideY = y;
    }

    public float getTouchX() {
        return touchX;
    }

    public float getTouchY() {
        return touchY;
    }

    public long getLastTouchTime() {
        return lastTouchTime;
    }

    public boolean isAnimationInProgress() {
        return animationInProgress;
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
        if (currentAnimationProgress == 1.0f) {
            canvas.drawRect(getBounds(), customPaint != null ? customPaint : paint);
        } else if (currentAnimationProgress != 0.0f) {
            float interpolatedProgress;
            if (isSelected) {
                interpolatedProgress = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(currentAnimationProgress);
            } else {
                interpolatedProgress = 1.0f - CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(1.0f - currentAnimationProgress);
            }
            Rect bounds = getBounds();
            float centerX = bounds.centerX();
            float centerY = bounds.centerY();
            float x1;
            float y1;
            if (touchOverrideX >= 0 && touchOverrideY >= 0) {
                x1 = touchOverrideX;
                y1 = touchOverrideY;
            } else if (touchX >= 0 && touchY >= 0) {
                x1 = touchX;
                y1 = touchY;
            } else {
                x1 = centerX;
                y1 = centerY;
            }
            x1 = centerX + (1.0f - interpolatedProgress) * (x1 - centerX);
            y1 = centerY + (1.0f - interpolatedProgress) * (y1 - centerY);
            canvas.drawCircle(x1, y1, finalRadius * interpolatedProgress, customPaint != null ? customPaint : paint);
        }
        if (animationInProgress) {
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastAnimationTime;
            if (dt > 20) {
                dt = 17;
            }
            lastAnimationTime = newTime;

            boolean finished = false;
            if (isSelected) {
                currentAnimationProgress += dt / 240.0f;
                if (currentAnimationProgress >= 1.0f) {
                    currentAnimationProgress = 1.0f;
                    finished = true;
                }
            } else {
                currentAnimationProgress -= dt / 240.0f;
                if (currentAnimationProgress <= 0.0f) {
                    currentAnimationProgress = 0.0f;
                    finished = true;
                }
            }
            if (finished) {
                touchX = -1;
                touchY = -1;
                touchOverrideX = -1;
                touchOverrideY = -1;
                animationInProgress = false;
            }
            invalidate();
        }
    }
}
