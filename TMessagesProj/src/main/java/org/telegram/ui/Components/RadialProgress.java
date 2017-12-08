/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class RadialProgress {

    private long lastUpdateTime = 0;
    private float radOffset = 0;
    private float currentProgress = 0;
    private float animationProgressStart = 0;
    private long currentProgressTime = 0;
    private float animatedProgressValue = 0;
    private RectF progressRect = new RectF();
    private RectF cicleRect = new RectF();
    private View parent;
    private float animatedAlphaValue = 1.0f;

    private boolean drawCheckDrawable;
    private boolean previousCheckDrawable;

    private boolean currentWithRound;
    private boolean previousWithRound;
    private Drawable currentDrawable;
    private Drawable previousDrawable;
    private boolean hideCurrentDrawable;
    private int progressColor = 0xffffffff;
    private Paint progressPaint;

    private CheckDrawable checkDrawable;
    private Drawable checkBackgroundDrawable;

    private int diff = AndroidUtilities.dp(4);

    private static DecelerateInterpolator decelerateInterpolator;
    private boolean alphaForPrevious = true;

    private float overrideAlpha = 1.0f;

    private class CheckDrawable extends Drawable {

        private Paint paint;
        private float progress;

        public CheckDrawable() {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xffffffff);
        }

        public void resetProgress(boolean animated) {
            progress = animated ? 0.0f : 1.0f;
        }

        public boolean updateAnimation(long dt) {
            if (progress < 1.0f) {
                progress += dt / 700.0f;
                if (progress > 1.0f) {
                    progress = 1.0f;
                }
                return true;
            }
            return false;
        }

        @Override
        public void draw(Canvas canvas) {
            int x = getBounds().centerX() - AndroidUtilities.dp(12);
            int y = getBounds().centerY() - AndroidUtilities.dp(6);
            float p = progress != 1.0f ? decelerateInterpolator.getInterpolation(progress) : 1.0f;
            int endX = (int) (AndroidUtilities.dp(7.0f) - AndroidUtilities.dp(6) * p);
            int endY = (int) (AndroidUtilities.dpf2(13.0f) - AndroidUtilities.dp(6) * p);
            canvas.drawLine(x + AndroidUtilities.dp(7.0f), y + (int) AndroidUtilities.dpf2(13.0f), x + endX, y + endY, paint);
            endX = (int) (AndroidUtilities.dpf2(7.0f) + AndroidUtilities.dp(13) * p);
            endY = (int) (AndroidUtilities.dpf2(13.0f) - AndroidUtilities.dp(13) * p);
            canvas.drawLine(x + (int) AndroidUtilities.dpf2(7.0f), y + (int) AndroidUtilities.dpf2(13.0f), x + endX, y + endY, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(48);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(48);
        }
    }

    public RadialProgress(View parentView) {
        if (decelerateInterpolator == null) {
            decelerateInterpolator = new DecelerateInterpolator();
        }
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(3));
        parent = parentView;
    }

    public void setStrikeWidth(int width) {
        progressPaint.setStrokeWidth(width);
    }

    public void setProgressRect(int left, int top, int right, int bottom) {
        progressRect.set(left, top, right, bottom);
    }

    public RectF getProgressRect() {
        return progressRect;
    }

    public void setAlphaForPrevious(boolean value) {
        alphaForPrevious = value;
    }

    private void updateAnimation(boolean progress) {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (checkBackgroundDrawable != null && (currentDrawable == checkBackgroundDrawable || previousDrawable == checkBackgroundDrawable)) {
            if (checkDrawable.updateAnimation(dt)) {
                invalidateParent();
            }
        }

        if (progress) {
            if (animatedProgressValue != 1) {
                radOffset += 360 * dt / 3000.0f;
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
                invalidateParent();
            }
            if (animatedProgressValue >= 1 && previousDrawable != null) {
                animatedAlphaValue -= dt / 200.0f;
                if (animatedAlphaValue <= 0) {
                    animatedAlphaValue = 0.0f;
                    previousDrawable = null;
                }
                invalidateParent();
            }
        } else {
            if (previousDrawable != null) {
                animatedAlphaValue -= dt / 200.0f;
                if (animatedAlphaValue <= 0) {
                    animatedAlphaValue = 0.0f;
                    previousDrawable = null;
                }
                invalidateParent();
            }
        }
    }

    public void setDiff(int value) {
        diff = value;
    }

    public void setProgressColor(int color) {
        progressColor = color;
    }

    public void setHideCurrentDrawable(boolean value) {
        hideCurrentDrawable = value;
    }

    public void setProgress(float value, boolean animated) {
        if (value != 1 && animatedAlphaValue != 0 && previousDrawable != null) {
            animatedAlphaValue = 0.0f;
            previousDrawable = null;
        }
        if (!animated) {
            animatedProgressValue = value;
            animationProgressStart = value;
        } else {
            if (animatedProgressValue > value) {
                animatedProgressValue = value;
            }
            animationProgressStart = animatedProgressValue;
        }
        currentProgress = value;
        currentProgressTime = 0;

        invalidateParent();
    }

    private void invalidateParent() {
        int offset = AndroidUtilities.dp(2);
        parent.invalidate((int) progressRect.left - offset, (int) progressRect.top - offset, (int) progressRect.right + offset * 2, (int) progressRect.bottom + offset * 2);
    }

    public void setCheckBackground(boolean withRound, boolean animated) {
        if (checkDrawable == null) {
            checkDrawable = new CheckDrawable();
            checkBackgroundDrawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(48), checkDrawable, 0);
        }
        Theme.setCombinedDrawableColor(checkBackgroundDrawable, Theme.getColor(Theme.key_chat_mediaLoaderPhoto), false);
        Theme.setCombinedDrawableColor(checkBackgroundDrawable, Theme.getColor(Theme.key_chat_mediaLoaderPhotoIcon), true);
        if (currentDrawable != checkBackgroundDrawable) {
            setBackground(checkBackgroundDrawable, withRound, animated);
            checkDrawable.resetProgress(animated);
        }
    }

    public void setBackground(Drawable drawable, boolean withRound, boolean animated) {
        lastUpdateTime = System.currentTimeMillis();
        if (animated && currentDrawable != drawable) {
            previousDrawable = currentDrawable;
            previousWithRound = currentWithRound;
            animatedAlphaValue = 1.0f;
            setProgress(1, animated);
        } else {
            previousDrawable = null;
            previousWithRound = false;
        }
        currentWithRound = withRound;
        currentDrawable = drawable;
        if (!animated) {
            parent.invalidate();
        } else {
            invalidateParent();
        }
    }

    public boolean swapBackground(Drawable drawable) {
        if (currentDrawable != drawable) {
            currentDrawable = drawable;
            return true;
        }
        return false;
    }

    public float getAlpha() {
        return previousDrawable != null || currentDrawable != null ? animatedAlphaValue : 0.0f;
    }

    public void setOverrideAlpha(float alpha) {
        overrideAlpha = alpha;
    }

    public void draw(Canvas canvas) {
        if (previousDrawable != null) {
            if (alphaForPrevious) {
                previousDrawable.setAlpha((int) (255 * animatedAlphaValue * overrideAlpha));
            } else {
                previousDrawable.setAlpha((int) (255 * overrideAlpha));
            }
            previousDrawable.setBounds((int) progressRect.left, (int) progressRect.top, (int) progressRect.right, (int) progressRect.bottom);
            previousDrawable.draw(canvas);
        }

        if (!hideCurrentDrawable && currentDrawable != null) {
            if (previousDrawable != null) {
                currentDrawable.setAlpha((int) (255 * (1.0f - animatedAlphaValue) * overrideAlpha));
            } else {
                currentDrawable.setAlpha((int) (255 * overrideAlpha));
            }
            currentDrawable.setBounds((int) progressRect.left, (int) progressRect.top, (int) progressRect.right, (int) progressRect.bottom);
            currentDrawable.draw(canvas);
        }

        if (currentWithRound || previousWithRound) {
            progressPaint.setColor(progressColor);
            if (previousWithRound) {
                progressPaint.setAlpha((int) (255 * animatedAlphaValue * overrideAlpha));
            } else {
                progressPaint.setAlpha((int) (255 * overrideAlpha));
            }
            cicleRect.set(progressRect.left + diff, progressRect.top + diff, progressRect.right - diff, progressRect.bottom - diff);
            canvas.drawArc(cicleRect, -90 + radOffset, Math.max(4, 360 * animatedProgressValue), false, progressPaint);
            updateAnimation(true);
        } else {
            updateAnimation(false);
        }
    }
}
