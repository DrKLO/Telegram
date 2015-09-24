/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;

public class RadioButton extends View {

    private static Paint paint;
    private static Paint checkedPaint;

    private int checkedColor = 0xffd7e8f7;
    private int color = 0xffd7e8f7;

    private float progress;
    private ObjectAnimatorProxy checkAnimator;
    private boolean isCheckAnimation = true;

    private boolean attachedToWindow;
    private boolean isChecked;
    private int size = AndroidUtilities.dp(16);

    private final static float progressBounceDiff = 0.2f;

    public RadioButton(Context context) {
        super(context);
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setStyle(Paint.Style.STROKE);
            checkedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
    }

    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    public void setSize(int value) {
        size = value;
    }

    public void setColor(int color1, int color2) {
        color = color1;
        checkedColor = color2;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
    }

    private void animateToCheckedState(boolean newCheckedState) {
        isCheckAnimation = newCheckedState;
        checkAnimator = ObjectAnimatorProxy.ofFloatProxy(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(300);
        checkAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checked == isChecked) {
            return;
        }
        isChecked = checked;

        if (attachedToWindow && animated) {
            animateToCheckedState(checked);
        } else {
            cancelCheckAnimator();
            setProgress(checked ? 1.0f : 0.0f);
        }
    }

    public boolean isChecked() {
        return isChecked;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }
        paint.setColor(isChecked ? checkedColor : color);
        checkedPaint.setColor(checkedColor);
        canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, size / 2 - AndroidUtilities.dp(1), paint);
        if (isChecked) {
            canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, size / 4, checkedPaint);
        }
    }
}
