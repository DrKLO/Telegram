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
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

public class SeekBar extends View {
    Drawable thumbDrawable1;
    Drawable thumbDrawablePressed1;
    Drawable thumbDrawable2;
    Drawable thumbDrawablePressed2;
    static Paint innerPaint1 = new Paint();
    static Paint outerPaint1 = new Paint();
    static Paint innerPaint2 = new Paint();
    static Paint outerPaint2 = new Paint();
    public int type;
    public int thumbX = 0;
    public int thumbDX = 0;
    private boolean pressed = false;
    private boolean dragging = false;
    private int thumbWidth;
    private int thumbHeight;

    public SeekBar(Context context) {
        super(context);
        init();
    }

    public SeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (thumbDrawable1 == null) {
            thumbDrawable1 = getResources().getDrawable(R.drawable.player1);
            thumbDrawablePressed1 = getResources().getDrawable(R.drawable.player1_pressed);
            thumbDrawable2 = getResources().getDrawable(R.drawable.player2);
            thumbDrawablePressed2 = getResources().getDrawable(R.drawable.player2_pressed);
            innerPaint1.setColor(0xffb4e396);
            outerPaint1.setColor(0xff6ac453);
            innerPaint2.setColor(0xffd9e2eb);
            outerPaint2.setColor(0xff86c5f8);
            thumbWidth = thumbDrawable1.getIntrinsicWidth();
            thumbHeight = thumbDrawable1.getIntrinsicHeight();
        }

        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                float x = motionEvent.getX();
                float y = motionEvent.getY();
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    int additionWidth = (getMeasuredHeight() - thumbWidth) / 2;
                    if (thumbX - additionWidth <= x && x <= thumbX + thumbWidth + additionWidth && y >= 0 && y <= getMeasuredHeight()) {
                        pressed = true;
                        thumbDX = (int)(x - thumbX);
                        invalidate();
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (pressed) {
                        pressed = false;
                        invalidate();
                        return true;
                    }
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                    if (pressed) {
                        thumbX = (int)(x - thumbDX);
                        if (thumbX < 0) {
                            thumbX = 0;
                        } else if (thumbX > getMeasuredWidth() - thumbWidth) {
                            thumbX = getMeasuredWidth() - thumbWidth;
                        }
                        invalidate();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
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
        int height = getMeasuredHeight();
        int width = getMeasuredWidth();
        int y = (height - thumbHeight) / 2;
        canvas.drawRect(thumbWidth / 2, height / 2 - Utilities.dp(1), width - thumbWidth / 2, height / 2 + Utilities.dp(1), inner);
        canvas.drawRect(thumbWidth / 2, height / 2 - Utilities.dp(1), thumbWidth / 2 + thumbX, height / 2 + Utilities.dp(1), outer);
        thumb.setBounds(thumbX, y, thumbX + thumbWidth, y + thumbHeight);
        thumb.draw(canvas);
    }
}
