/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import org.telegram.messenger.AndroidUtilities;

public class SeekBar {

    public interface SeekBarDelegate {
        void onSeekBarDrag(float progress);
    }

    private static Paint innerPaint;
    private static Paint outerPaint;
    private static int thumbWidth;
    private int thumbX = 0;
    private int thumbDX = 0;
    private boolean pressed = false;
    private int width;
    private int height;
    private SeekBarDelegate delegate;
    private int innerColor;
    private int outerColor;
    private int selectedColor;
    private boolean selected;

    public SeekBar(Context context) {
        if (innerPaint == null) {
            innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
                thumbDX = (int) (x - thumbX);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pressed) {
                if (action == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.onSeekBarDrag((float) thumbX / (float) (width - thumbWidth));
                }
                pressed = false;
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (pressed) {
                thumbX = (int) (x - thumbDX);
                if (thumbX < 0) {
                    thumbX = 0;
                } else if (thumbX > width - thumbWidth) {
                    thumbX = width - thumbWidth;
                }
                return true;
            }
        }
        return false;
    }

    public void setColors(int inner, int outer, int selected) {
        innerColor = inner;
        outerColor = outer;
        selectedColor = selected;
    }

    public void setProgress(float progress) {
        thumbX = (int) Math.ceil((width - thumbWidth) * progress);
        if (thumbX < 0) {
            thumbX = 0;
        } else if (thumbX > width - thumbWidth) {
            thumbX = width - thumbWidth;
        }
    }

    public float getProgress() {
        return (float) thumbX / (float) (width - thumbWidth);
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

    public void draw(Canvas canvas) {
        innerPaint.setColor(selected ? selectedColor : innerColor);
        outerPaint.setColor(outerColor);

        canvas.drawRect(thumbWidth / 2, height / 2 - AndroidUtilities.dp(1), width - thumbWidth / 2, height / 2 + AndroidUtilities.dp(1), innerPaint);
        canvas.drawRect(thumbWidth / 2, height / 2 - AndroidUtilities.dp(1), thumbWidth / 2 + thumbX, height / 2 + AndroidUtilities.dp(1), outerPaint);
        canvas.drawCircle(thumbX + thumbWidth / 2, height / 2, AndroidUtilities.dp(pressed ? 8 : 6), outerPaint);
    }
}
