/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import androidx.annotation.Keep;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class GroupCreateCheckBox extends View {

    private Paint backgroundPaint;
    private Paint backgroundInnerPaint;
    private Paint checkPaint;
    private static Paint eraser;
    private static Paint eraser2;

    private Bitmap drawBitmap;
    private Canvas bitmapCanvas;

    private float progress;
    private ObjectAnimator checkAnimator;
    private boolean isCheckAnimation = true;

    private boolean attachedToWindow;
    private boolean isChecked;

    private int innerRadDiff;
    private float checkScale = 1.0f;

    private String backgroundKey = Theme.key_checkboxCheck;
    private String checkKey = Theme.key_checkboxCheck;
    private String innerKey = Theme.key_checkbox;


    private final static float progressBounceDiff = 0.2f;

    public GroupCreateCheckBox(Context context) {
        super(context);
        if (eraser == null) {
            eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraser.setColor(0);
            eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            eraser2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraser2.setColor(0);
            eraser2.setStyle(Paint.Style.STROKE);
            eraser2.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkPaint.setStyle(Paint.Style.STROKE);
        innerRadDiff = AndroidUtilities.dp(2);
        checkPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        eraser2.setStrokeWidth(AndroidUtilities.dp(28));

        drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(24), AndroidUtilities.dp(24), Bitmap.Config.ARGB_4444);
        bitmapCanvas = new Canvas(drawBitmap);
        updateColors();
    }

    public void setColorKeysOverrides(String check, String inner, String back) {
        checkKey = check;
        innerKey = inner;
        backgroundKey = back;
        updateColors();
    }

    public void updateColors() {
        backgroundInnerPaint.setColor(Theme.getColor(innerKey));
        backgroundPaint.setColor(Theme.getColor(backgroundKey));
        checkPaint.setColor(Theme.getColor(checkKey));
        invalidate();
    }

    @Keep
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

    public void setCheckScale(float value) {
        checkScale = value;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
    }

    private void animateToCheckedState(boolean newCheckedState) {
        isCheckAnimation = newCheckedState;
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(300);
        checkAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
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

    public void setInnerRadDiff(int value) {
        innerRadDiff = value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }
        if (progress != 0) {
            int cx = getMeasuredWidth() / 2;
            int cy = getMeasuredHeight() / 2;
            eraser2.setStrokeWidth(AndroidUtilities.dp(24 + 6));

            drawBitmap.eraseColor(0);

            float roundProgress = progress >= 0.5f ? 1.0f : progress / 0.5f;
            float checkProgress = progress < 0.5f ? 0.0f : (progress - 0.5f) / 0.5f;

            float roundProgressCheckState = isCheckAnimation ? progress : (1.0f - progress);
            float radDiff;
            if (roundProgressCheckState < progressBounceDiff) {
                radDiff = AndroidUtilities.dp(2) * roundProgressCheckState / progressBounceDiff;
            } else if (roundProgressCheckState < progressBounceDiff * 2) {
                radDiff = AndroidUtilities.dp(2) - AndroidUtilities.dp(2) * (roundProgressCheckState - progressBounceDiff) / progressBounceDiff;
            } else {
                radDiff = 0;
            }

            if (checkProgress != 0) {
                canvas.drawCircle(cx, cy, (cx - AndroidUtilities.dp(2)) + AndroidUtilities.dp(2) * checkProgress - radDiff, backgroundPaint);
            }

            float innerRad = cx - innerRadDiff - radDiff;
            bitmapCanvas.drawCircle(cx, cy, innerRad, backgroundInnerPaint);
            bitmapCanvas.drawCircle(cx, cy, innerRad * (1 - roundProgress), eraser);
            canvas.drawBitmap(drawBitmap, 0, 0, null);

            float checkSide = AndroidUtilities.dp(10) * checkProgress * checkScale;
            float smallCheckSide = AndroidUtilities.dp(5) * checkProgress * checkScale;
            int x = cx - AndroidUtilities.dp(1);
            int y = cy + AndroidUtilities.dp(4);
            float side = (float) Math.sqrt(smallCheckSide * smallCheckSide / 2.0f);
            canvas.drawLine(x, y, x - side, y - side, checkPaint);
            side = (float) Math.sqrt(checkSide * checkSide / 2.0f);
            x -= AndroidUtilities.dp(1.2f);
            canvas.drawLine(x, y, x + side, y - side, checkPaint);
        }
    }
}
