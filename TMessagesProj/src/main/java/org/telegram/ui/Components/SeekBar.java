/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import org.telegram.messenger.AndroidUtilities;

public class SeekBar {

    public interface SeekBarDelegate {
        void onSeekBarDrag(float progress);
        default void onSeekBarContinuousDrag(float progress) {

        }
    }

    private static Paint paint;
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
    private int lineHeight = AndroidUtilities.dp(2);
    private boolean selected;
    private float bufferedProgress;

    public SeekBar(Context context) {
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            thumbWidth = AndroidUtilities.dp(24);
        }
    }

    public void setDelegate(SeekBarDelegate seekBarDelegate) {
        delegate = seekBarDelegate;
    }

    public boolean onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            int additionWidth = (height - thumbWidth) / 2;
            if (thumbX - additionWidth <= x && x <= thumbX + thumbWidth + additionWidth && y >= 0 && y <= height) {
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

    public void setColors(int background, int cache, int progress, int circle, int selected) {
        backgroundColor = background;
        cacheColor = cache;
        circleColor = circle;
        progressColor = progress;
        backgroundSelectedColor = selected;
    }

    public void setProgress(float progress) {
        thumbX = (int) Math.ceil((width - thumbWidth) * progress);
        if (thumbX < 0) {
            thumbX = 0;
        } else if (thumbX > width - thumbWidth) {
            thumbX = width - thumbWidth;
        }
    }

    public void setBufferedProgress(float value) {
        bufferedProgress = value;
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

    public void setLineHeight(int value) {
        lineHeight = value;
    }

    public void draw(Canvas canvas) {
        rect.set(thumbWidth / 2, height / 2 - lineHeight / 2, width - thumbWidth / 2, height / 2 + lineHeight / 2);
        paint.setColor(selected ? backgroundSelectedColor : backgroundColor);
        canvas.drawRoundRect(rect, thumbWidth / 2, thumbWidth / 2, paint);
        if (bufferedProgress > 0) {
            paint.setColor(selected ? backgroundSelectedColor : cacheColor);
            rect.set(thumbWidth / 2, height / 2 - lineHeight / 2, thumbWidth / 2 + bufferedProgress * (width - thumbWidth), height / 2 + lineHeight / 2);
            canvas.drawRoundRect(rect, thumbWidth / 2, thumbWidth / 2, paint);
        }
        rect.set(thumbWidth / 2, height / 2 - lineHeight / 2, thumbWidth / 2 + thumbX, height / 2 + lineHeight / 2);
        paint.setColor(progressColor);
        canvas.drawRoundRect(rect, thumbWidth / 2, thumbWidth / 2, paint);
        paint.setColor(circleColor);
        canvas.drawCircle((pressed ? draggingThumbX : thumbX) + thumbWidth / 2, height / 2, AndroidUtilities.dp(pressed ? 8 : 6), paint);
    }
}
