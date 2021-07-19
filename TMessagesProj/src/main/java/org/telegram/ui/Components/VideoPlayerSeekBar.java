/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;

public class VideoPlayerSeekBar {

    public interface SeekBarDelegate {
        void onSeekBarDrag(float progress);
        default void onSeekBarContinuousDrag(float progress) {
        }
    }

    private static Paint paint;
    private static Paint strokePaint;
    private static int thumbWidth;
    private int thumbX = 0;
    private int draggingThumbX = 0;
    private int thumbDX = 0;
    private boolean pressed = false;
    private int width;
    private int height;
    private SeekBarDelegate delegate;
    private int backgroundColor;
    private int cacheColor;
    private int circleColor;
    private int progressColor;
    private int backgroundSelectedColor;
    private RectF rect = new RectF();
    private boolean selected;
    private float animateFromBufferedProgress;
    private boolean animateResetBuffering;
    private float bufferedAnimationValue = 1f;
    private float bufferedProgress;
    private float currentRadius;
    private long lastUpdateTime;
    private View parentView;

    private int lineHeight = AndroidUtilities.dp(4);
    private int smallLineHeight = AndroidUtilities.dp(2);

    private float transitionProgress;
    private int horizontalPadding;
    private int smallLineColor;

    private int fromThumbX = 0;
    private float animateThumbProgress = 1f;

    public VideoPlayerSeekBar(View parent) {
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.BLACK);
            strokePaint.setStrokeWidth(1);
        }
        parentView = parent;
        thumbWidth = AndroidUtilities.dp(24);
        currentRadius = AndroidUtilities.dp(6);
    }

    public void setDelegate(SeekBarDelegate seekBarDelegate) {
        delegate = seekBarDelegate;
    }

    public boolean onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (transitionProgress > 0f) {
                return false;
            }
            int additionWidth = (height - thumbWidth) / 2;
            if (x >= -additionWidth && x <= width + additionWidth && y >= 0 && y <= height) {
                if (!(thumbX - additionWidth <= x && x <= thumbX + thumbWidth + additionWidth)) {
                    thumbX = (int) x - thumbWidth / 2;
                    if (thumbX < 0) {
                        thumbX = 0;
                    } else if (thumbX > width - thumbWidth) {
                        thumbX = thumbWidth - width;
                    }
                }
                pressed = true;
                draggingThumbX = thumbX;
                thumbDX = (int) (x - thumbX);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pressed) {
                thumbX = draggingThumbX;
                if (action == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.onSeekBarDrag((float) thumbX / (float) (width - thumbWidth));
                }
                pressed = false;
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (pressed) {
                draggingThumbX = (int) (x - thumbDX);
                if (draggingThumbX < 0) {
                    draggingThumbX = 0;
                } else if (draggingThumbX > width - thumbWidth) {
                    draggingThumbX = width - thumbWidth;
                }
                if (delegate != null) {
                    delegate.onSeekBarContinuousDrag((float) draggingThumbX / (float) (width - thumbWidth));
                }
                return true;
            }
        }
        return false;
    }

    public void setColors(int background, int cache, int progress, int circle, int selected, int smallLineColor) {
        backgroundColor = background;
        cacheColor = cache;
        circleColor = circle;
        progressColor = progress;
        backgroundSelectedColor = selected;
        this.smallLineColor = smallLineColor;
    }

    public void setProgress(float progress, boolean animated) {
        int newThumb = (int) Math.ceil((width - thumbWidth) * progress);

        if (animated) {
            if (Math.abs(newThumb - thumbX) > AndroidUtilities.dp(10)) {
                float progressInterpolated = CubicBezierInterpolator.DEFAULT.getInterpolation(animateThumbProgress);
                fromThumbX = (int) (thumbX * progressInterpolated + fromThumbX * (1f - progressInterpolated));
                animateThumbProgress = 0;
            } else if (animateThumbProgress == 1f) {
                animateThumbProgress = 0;
                fromThumbX = thumbX;
            }
        }
        thumbX = newThumb;

        if (thumbX < 0) {
            thumbX = 0;
        } else if (thumbX > width - thumbWidth) {
            thumbX = width - thumbWidth;
        }
    }

    public void setProgress(float progress) {
        setProgress(progress, false);
    }

    public void setBufferedProgress(float value) {
        if (value != bufferedProgress) {
            animateFromBufferedProgress = bufferedProgress;
            animateResetBuffering = value < bufferedProgress;
            bufferedProgress = value;
            bufferedAnimationValue = 0;
        }
    }

    public float getProgress() {
        return (float) thumbX / (float) (width - thumbWidth);
    }

    public int getThumbX() {
        return (pressed ? draggingThumbX : thumbX) + thumbWidth / 2;
    }

    public boolean isDragging() {
        return pressed;
    }

    public void setSelected(boolean value) {
        selected = value;
    }

    public void setSize(int w, int h) {
        width = w;
        height = h;
    }

    public int getWidth() {
        return width - thumbWidth;
    }


    public float getTransitionProgress() {
        return transitionProgress;
    }

    public void setTransitionProgress(float transitionProgress) {
        if (this.transitionProgress != transitionProgress) {
            this.transitionProgress = transitionProgress;
            parentView.invalidate();
        }
    }

    public int getHorizontalPadding() {
        return horizontalPadding;
    }

    public void setHorizontalPadding(int horizontalPadding) {
        this.horizontalPadding = horizontalPadding;
    }

    public void draw(Canvas canvas, View view) {
        final float radius = AndroidUtilities.lerp(thumbWidth / 2f, smallLineHeight / 2f, transitionProgress);
        rect.left = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f, 0, transitionProgress);
        rect.top = AndroidUtilities.lerp((height - lineHeight) / 2f, height - AndroidUtilities.dp(3) - smallLineHeight, transitionProgress);
        rect.bottom = AndroidUtilities.lerp((height + lineHeight) / 2f, height - AndroidUtilities.dp(3), transitionProgress);

        float currentThumbX = thumbX;
        if (animateThumbProgress != 1f) {
            animateThumbProgress += 16 / 220f;
            if (animateThumbProgress >= 1f) {
                animateThumbProgress = 1f;
            } else {
                view.invalidate();
                float progressInterpolated = CubicBezierInterpolator.DEFAULT.getInterpolation(animateThumbProgress);
                currentThumbX = fromThumbX * (1f - progressInterpolated) + thumbX * progressInterpolated;
            }
        }
        // background
        rect.right = horizontalPadding + AndroidUtilities.lerp(width - thumbWidth / 2f, parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
        setPaintColor(selected ? backgroundSelectedColor : backgroundColor, 1f - transitionProgress);
        canvas.drawRoundRect(rect, radius, radius, paint);

        if (bufferedAnimationValue != 1f) {
            bufferedAnimationValue += 16 / 100f;
            if (bufferedAnimationValue > 1) {
                bufferedAnimationValue = 1f;
            } else {
                parentView.invalidate();
            }
        }

        // buffered
        if (animateResetBuffering) {
            if (animateFromBufferedProgress > 0) {
                rect.right = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + animateFromBufferedProgress * (width - thumbWidth), parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
                setPaintColor(selected ? backgroundSelectedColor : cacheColor, (1f - transitionProgress) * (1f - bufferedAnimationValue));
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
            if (bufferedProgress > 0) {
                rect.right = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + bufferedProgress * (width - thumbWidth), parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
                setPaintColor(selected ? backgroundSelectedColor : cacheColor, 1f - transitionProgress);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
        } else {
            float currentBufferedProgress = animateFromBufferedProgress * (1f - bufferedAnimationValue) + bufferedProgress * bufferedAnimationValue;
            if (currentBufferedProgress > 0) {
                rect.right = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + currentBufferedProgress * (width - thumbWidth), parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
                setPaintColor(selected ? backgroundSelectedColor : cacheColor, 1f - transitionProgress);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
        }

        // progress
        rect.right = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + (pressed ? draggingThumbX : currentThumbX), (parentView.getWidth() - horizontalPadding * 2f) * getProgress(), transitionProgress);
        if (transitionProgress > 0f && rect.width() > 0) {
            // progress stroke
            strokePaint.setAlpha((int) (transitionProgress * 255 * 0.2f));
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }
        setPaintColor(ColorUtils.blendARGB(progressColor, smallLineColor, transitionProgress), 1f);
        canvas.drawRoundRect(rect, radius, radius, paint);

        // circle
        setPaintColor(ColorUtils.blendARGB(circleColor, getProgress() == 0 ? Color.TRANSPARENT : smallLineColor, transitionProgress), 1f - transitionProgress);
        int newRad = AndroidUtilities.dp(pressed ? 8 : 6);
        if (currentRadius != newRad) {
            long newUpdateTime = SystemClock.elapsedRealtime();
            long dt = newUpdateTime - lastUpdateTime;
            lastUpdateTime = newUpdateTime;
            if (dt > 18) {
                dt = 16;
            }
            if (currentRadius < newRad) {
                currentRadius += AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius > newRad) {
                    currentRadius = newRad;
                }
            } else {
                currentRadius -= AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius < newRad) {
                    currentRadius = newRad;
                }
            }
            if (parentView != null) {
                parentView.invalidate();
            }
        }
        final float circleRadius = AndroidUtilities.lerp(currentRadius, 0, transitionProgress);
        canvas.drawCircle(rect.right, rect.centerY(), circleRadius, paint);
    }

    private void setPaintColor(int color, float alpha) {
        if (alpha < 1f) {
            color = ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * alpha));
        }
        paint.setColor(color);
    }
}
