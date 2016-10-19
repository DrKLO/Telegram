/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class CheckBoxSquare extends View {

    private static Paint eraser;
    private static Paint checkPaint;
    private static Paint backgroundPaint;
    private static RectF rectF;

    private Bitmap drawBitmap;
    private Canvas drawCanvas;

    private float progress;
    private ObjectAnimator checkAnimator;

    private boolean attachedToWindow;
    private boolean isChecked;
    private boolean isDisabled;

    private int color = 0xff43a0df;

    private final static float progressBounceDiff = 0.2f;

    public CheckBoxSquare(Context context) {
        super(context);
        if (checkPaint == null) {
            checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            checkPaint.setColor(0xffffffff);
            checkPaint.setStyle(Paint.Style.STROKE);
            checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
            eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraser.setColor(0);
            eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            rectF = new RectF();
        }

        drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(18), AndroidUtilities.dp(18), Bitmap.Config.ARGB_4444);
        drawCanvas = new Canvas(drawBitmap);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE && drawBitmap == null) {

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

    public void setColor(int value) {
        color = value;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
    }

    private void animateToCheckedState(boolean newCheckedState) {
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
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

    public void setDisabled(boolean disabled) {
        isDisabled = disabled;
        invalidate();
    }

    public boolean isChecked() {
        return isChecked;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }

        float checkProgress;
        float bounceProgress;
        if (progress <= 0.5f) {
            bounceProgress = checkProgress = progress / 0.5f;
            int rD = (int) ((Color.red(color) - 0x73) * checkProgress);
            int gD = (int) ((Color.green(color) - 0x73) * checkProgress);
            int bD = (int) ((Color.blue(color) - 0x73) * checkProgress);
            int c = Color.rgb(0x73 + rD, 0x73 + gD, 0x73 + bD);
            backgroundPaint.setColor(c);
        } else {
            bounceProgress = 2.0f - progress / 0.5f;
            checkProgress = 1.0f;
            backgroundPaint.setColor(color);
        }
        if (isDisabled) {
            backgroundPaint.setColor(0xffb0b0b0);
        }
        float bounce = AndroidUtilities.dp(1) * bounceProgress;
        rectF.set(bounce, bounce, AndroidUtilities.dp(18) - bounce, AndroidUtilities.dp(18) - bounce);

        drawBitmap.eraseColor(0);
        drawCanvas.drawRoundRect(rectF, AndroidUtilities.dp(2), AndroidUtilities.dp(2), backgroundPaint);

        if (checkProgress != 1) {
            float rad = Math.min(AndroidUtilities.dp(7), AndroidUtilities.dp(7) * checkProgress + bounce);
            rectF.set(AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(16) - rad, AndroidUtilities.dp(16) - rad);
            drawCanvas.drawRect(rectF, eraser);
        }

        if (progress > 0.5f) {
            int endX = (int) (AndroidUtilities.dp(7.5f) - AndroidUtilities.dp(5) * (1.0f - bounceProgress));
            int endY = (int) (AndroidUtilities.dpf2(13.5f) - AndroidUtilities.dp(5) * (1.0f - bounceProgress));
            drawCanvas.drawLine(AndroidUtilities.dp(7.5f), (int) AndroidUtilities.dpf2(13.5f), endX, endY, checkPaint);
            endX = (int) (AndroidUtilities.dpf2(6.5f) + AndroidUtilities.dp(9) * (1.0f - bounceProgress));
            endY = (int) (AndroidUtilities.dpf2(13.5f) - AndroidUtilities.dp(9) * (1.0f - bounceProgress));
            drawCanvas.drawLine((int) AndroidUtilities.dpf2(6.5f), (int) AndroidUtilities.dpf2(13.5f), endX, endY, checkPaint);
        }
        canvas.drawBitmap(drawBitmap, 0, 0, null);
    }
}
