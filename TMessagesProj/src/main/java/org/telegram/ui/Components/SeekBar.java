/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
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

    private static Paint innerPaint1;
    private static Paint outerPaint1;
    private static Paint innerPaint2;
    private static Paint outerPaint2;
    private static int thumbWidth;
    private static int thumbHeight;
    public int type;
    public int thumbX = 0;
    public int thumbDX = 0;
    private boolean pressed = false;
    public int width;
    public int height;
    private SeekBarDelegate delegate;

    public SeekBar(Context context) {
        if (innerPaint1 == null) {
            innerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerPaint1.setColor(0xffc3e3ab);

            outerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
            outerPaint1.setColor(0xff87bf78);

            innerPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            innerPaint2.setColor(0xffe4eaf0);

            outerPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            outerPaint2.setColor(0xff4195e5);

            thumbWidth = AndroidUtilities.dp(24);
            thumbHeight = AndroidUtilities.dp(24);
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
                thumbDX = (int)(x - thumbX);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pressed) {
                if (action == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.onSeekBarDrag((float)thumbX / (float)(width - thumbWidth));
                }
                pressed = false;
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (pressed) {
                thumbX = (int)(x - thumbDX);
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

    public void setProgress(float progress) {
        thumbX = (int)Math.ceil((width - thumbWidth) * progress);
        if (thumbX < 0) {
            thumbX = 0;
        } else if (thumbX > width - thumbWidth) {
            thumbX = width - thumbWidth;
        }
    }

    public boolean isDragging() {
        return pressed;
    }

    public void draw(Canvas canvas) {
        Paint inner = null;
        Paint outer = null;
        if (type == 0) {
            inner = innerPaint1;
            outer = outerPaint1;
        } else if (type == 1) {
            inner = innerPaint2;
            outer = outerPaint2;
        }
        int y = (height - thumbHeight) / 2;
        canvas.drawRect(thumbWidth / 2, height / 2 - AndroidUtilities.dp(1), width - thumbWidth / 2, height / 2 + AndroidUtilities.dp(1), inner);
        canvas.drawRect(thumbWidth / 2, height / 2 - AndroidUtilities.dp(1), thumbWidth / 2 + thumbX, height / 2 + AndroidUtilities.dp(1), outer);
        canvas.drawCircle(thumbX + thumbWidth / 2, y + thumbHeight / 2, AndroidUtilities.dp(pressed ? 8 : 6), outer);
    }
}
