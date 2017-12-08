/*
 * This is the source code of Telegram for Android v. 3.x.x.
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
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class SeekBarView extends FrameLayout {

    private Paint innerPaint1;
    private Paint outerPaint1;
    private int thumbWidth;
    private int thumbHeight;
    private int thumbX;
    private int thumbDX;
    private float progressToSet;
    private boolean pressed;
    private SeekBarViewDelegate delegate;
    private boolean reportChanges;

    public interface SeekBarViewDelegate {
        void onSeekBarDrag(float progress);
    }

    public SeekBarView(Context context) {
        super(context);
        setWillNotDraw(false);
        innerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint1.setColor(Theme.getColor(Theme.key_player_progressBackground));

        outerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPaint1.setColor(Theme.getColor(Theme.key_player_progress));

        thumbWidth = AndroidUtilities.dp(24);
        thumbHeight = AndroidUtilities.dp(24);
    }

    public void setColors(int inner, int outer) {
        innerPaint1.setColor(inner);
        outerPaint1.setColor(outer);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return onTouch(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return onTouch(event);
    }

    public void setReportChanges(boolean value) {
        reportChanges = value;
    }

    public void setDelegate(SeekBarViewDelegate seekBarViewDelegate) {
        delegate = seekBarViewDelegate;
    }

    boolean onTouch(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            int additionWidth = (getMeasuredHeight() - thumbWidth) / 2;
            if (thumbX - additionWidth <= ev.getX() && ev.getX() <= thumbX + thumbWidth + additionWidth && ev.getY() >= 0 && ev.getY() <= getMeasuredHeight()) {
                pressed = true;
                thumbDX = (int) (ev.getX() - thumbX);
                invalidate();
                return true;
            }
        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            if (pressed) {
                if (ev.getAction() == MotionEvent.ACTION_UP) {
                    delegate.onSeekBarDrag((float) thumbX / (float) (getMeasuredWidth() - thumbWidth));
                }
                pressed = false;
                invalidate();
                return true;
            }
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressed) {
                thumbX = (int) (ev.getX() - thumbDX);
                if (thumbX < 0) {
                    thumbX = 0;
                } else if (thumbX > getMeasuredWidth() - thumbWidth) {
                    thumbX = getMeasuredWidth() - thumbWidth;
                }
                if (reportChanges) {
                    delegate.onSeekBarDrag((float) thumbX / (float) (getMeasuredWidth() - thumbWidth));
                }
                invalidate();
                return true;
            }
        }
        return false;
    }

    public void setProgress(float progress) {
        if (getMeasuredWidth() == 0) {
            progressToSet = progress;
            return;
        }
        progressToSet = -1;
        int newThumbX = (int) Math.ceil((getMeasuredWidth() - thumbWidth) * progress);
        if (thumbX != newThumbX) {
            thumbX = newThumbX;
            if (thumbX < 0) {
                thumbX = 0;
            } else if (thumbX > getMeasuredWidth() - thumbWidth) {
                thumbX = getMeasuredWidth() - thumbWidth;
            }
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (progressToSet >= 0 && getMeasuredWidth() > 0) {
            setProgress(progressToSet);
            progressToSet = -1;
        }
    }

    public boolean isDragging() {
        return pressed;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int y = (getMeasuredHeight() - thumbHeight) / 2;
        canvas.drawRect(thumbWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), getMeasuredWidth() - thumbWidth / 2, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), innerPaint1);
        canvas.drawRect(thumbWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), thumbWidth / 2 + thumbX, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), outerPaint1);
        canvas.drawCircle(thumbX + thumbWidth / 2, y + thumbHeight / 2, AndroidUtilities.dp(pressed ? 8 : 6), outerPaint1);
    }
}
