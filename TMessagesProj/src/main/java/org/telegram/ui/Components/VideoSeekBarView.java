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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public class VideoSeekBarView extends View {

    private static Drawable thumbDrawable1;
    private static Paint innerPaint1 = new Paint();
    private static int thumbWidth;
    private static int thumbHeight;
    private int thumbDX = 0;
    private float progress = 0;
    private boolean pressed = false;
    public SeekBarDelegate delegate;

    public interface SeekBarDelegate {
        void onSeekBarDrag(float progress);
    }

    private void init(Context context) {
        if (thumbDrawable1 == null) {
            thumbDrawable1 = context.getResources().getDrawable(R.drawable.videolapse);
            innerPaint1.setColor(0x99999999);
            thumbWidth = thumbDrawable1.getIntrinsicWidth();
            thumbHeight = thumbDrawable1.getIntrinsicHeight();
        }
    }

    public VideoSeekBarView(Context context) {
        super(context);
        init(context);
    }

    public VideoSeekBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VideoSeekBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
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
        canvas.drawRect(thumbWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), getMeasuredWidth() - thumbWidth / 2, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), innerPaint1);
        thumbDrawable1.setBounds(thumbX, y, thumbX + thumbWidth, y + thumbHeight);
        thumbDrawable1.draw(canvas);
    }
}
