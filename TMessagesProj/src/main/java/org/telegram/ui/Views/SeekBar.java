/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

public class SeekBar {

    public abstract interface SeekBarDelegate {
        public void onSeekBarDrag(float progress);
    }

    private static Drawable thumbDrawable1;
    private static Drawable thumbDrawablePressed1;
    private static Drawable thumbDrawable2;
    private static Drawable thumbDrawablePressed2;
    private static Paint innerPaint1 = new Paint();
    private static Paint outerPaint1 = new Paint();
    private static Paint innerPaint2 = new Paint();
    private static Paint outerPaint2 = new Paint();
    private static int thumbWidth;
    private static int thumbHeight;
    public int type;
    public int thumbX = 0;
    public int thumbDX = 0;
    private boolean pressed = false;
    public int width;
    public int height;
    public SeekBarDelegate delegate;

    public SeekBar(Context context) {
        if (thumbDrawable1 == null) {
            thumbDrawable1 = context.getResources().getDrawable(R.drawable.player1);
            thumbDrawablePressed1 = context.getResources().getDrawable(R.drawable.player1_pressed);
            thumbDrawable2 = context.getResources().getDrawable(R.drawable.player2);
            thumbDrawablePressed2 = context.getResources().getDrawable(R.drawable.player2_pressed);
            innerPaint1.setColor(0xffb4e396);
            outerPaint1.setColor(0xff6ac453);
            innerPaint2.setColor(0xffd9e2eb);
            outerPaint2.setColor(0xff86c5f8);
            thumbWidth = thumbDrawable1.getIntrinsicWidth();
            thumbHeight = thumbDrawable1.getIntrinsicHeight();
        }
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
        Drawable thumb = null;
        Paint inner = null;
        Paint outer = null;
        if (type == 0) {
            if (!pressed) {
                thumb = thumbDrawable1;
            } else {
                thumb = thumbDrawablePressed1;
            }
            inner = innerPaint1;
            outer = outerPaint1;
        } else if (type == 1) {
            if (!pressed) {
                thumb = thumbDrawable2;
            } else {
                thumb = thumbDrawablePressed2;
            }
            inner = innerPaint2;
            outer = outerPaint2;
        }
        int y = (height - thumbHeight) / 2;
        canvas.drawRect(thumbWidth / 2, height / 2 - Utilities.dp(1), width - thumbWidth / 2, height / 2 + Utilities.dp(1), inner);
        canvas.drawRect(thumbWidth / 2, height / 2 - Utilities.dp(1), thumbWidth / 2 + thumbX, height / 2 + Utilities.dp(1), outer);
        thumb.setBounds(thumbX, y, thumbX + thumbWidth, y + thumbHeight);
        thumb.draw(canvas);
    }
}
