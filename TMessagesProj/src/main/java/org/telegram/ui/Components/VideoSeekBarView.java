/*
 * This is the source code of Telegram for Android v. 3.x.x.
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
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class VideoSeekBarView extends View {

    private Paint paint = new Paint();
    private Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int thumbWidth = AndroidUtilities.dp(12);
    private int thumbHeight = AndroidUtilities.dp(12);
    private int thumbDX = 0;
    private float progress = 0;
    private boolean pressed = false;
    private SeekBarDelegate delegate;

    public interface SeekBarDelegate {
        void onSeekBarDrag(float progress);
    }

    public VideoSeekBarView(Context context) {
        super(context);
        paint.setColor(0xff5c5c5c);
        paint2.setColor(0xffffffff);
    }

    public void setDelegate(SeekBarDelegate seekBarDelegate) {
        delegate = seekBarDelegate;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }
        float x = event.getX();
        float y = event.getY();
        float thumbX = (int)((getMeasuredWidth() - thumbWidth) * progress);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int additionWidth = (getMeasuredHeight() - thumbWidth) / 2;
            if (thumbX - additionWidth <= x && x <= thumbX + thumbWidth + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                pressed = true;
                thumbDX = (int)(x - thumbX);
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressed) {
                if (event.getAction() == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.onSeekBarDrag(thumbX / (float)(getMeasuredWidth() - thumbWidth));
                }
                pressed = false;
                invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressed) {
                thumbX = (int)(x - thumbDX);
                if (thumbX < 0) {
                    thumbX = 0;
                } else if (thumbX > getMeasuredWidth() - thumbWidth) {
                    thumbX = getMeasuredWidth() - thumbWidth;
                }
                progress = thumbX / (getMeasuredWidth() - thumbWidth);
                invalidate();
                return true;
            }
        }
        return false;
    }

    public void setProgress(float progress) {
        if (progress < 0) {
            progress = 0;
        } else if (progress > 1) {
            progress = 1;
        }
        this.progress = progress;
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int y = (getMeasuredHeight() - thumbHeight) / 2;
        int thumbX = (int)((getMeasuredWidth() - thumbWidth) * progress);
        canvas.drawRect(thumbWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), getMeasuredWidth() - thumbWidth / 2, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), paint);
        canvas.drawCircle(thumbX + thumbWidth / 2, y + thumbHeight / 2, thumbWidth / 2, paint2);
    }
}
