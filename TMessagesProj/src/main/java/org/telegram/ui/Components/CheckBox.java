/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;

public class CheckBox extends View {

    private static Drawable checkDrawable;
    private static Paint paint;
    private static Paint eraser;
    private static Paint eraser2;
    private static Paint checkPaint;

    private Bitmap drawBitmap;
    private Bitmap checkBitmap;
    private Canvas bitmapCanvas;
    private Canvas checkCanvas;

    private float progress;
    private ObjectAnimatorProxy checkAnimator;
    private boolean isCheckAnimation = true;

    private boolean attachedToWindow;
    private boolean isChecked = false;

    private final static float progressBounceDiff = 0.2f;

    public CheckBox(Context context) {
        super(context);
        if (checkDrawable == null) {
            checkDrawable = context.getResources().getDrawable(R.drawable.round_check2);
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xff5ec245);
            eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraser.setColor(0);
            eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            eraser2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraser2.setColor(0);
            eraser2.setStyle(Paint.Style.STROKE);
            eraser2.setStrokeWidth(AndroidUtilities.dp(28));
            eraser2.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE && drawBitmap == null) {
            drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(22), AndroidUtilities.dp(22), Bitmap.Config.ARGB_4444);
            bitmapCanvas = new Canvas(drawBitmap);
            checkBitmap = Bitmap.createBitmap(AndroidUtilities.dp(22), AndroidUtilities.dp(22), Bitmap.Config.ARGB_4444);
            checkCanvas = new Canvas(checkBitmap);
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

    public boolean isChecked() {
        return isChecked;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }
        if (progress != 0) {
            drawBitmap.eraseColor(0);
            float rad = getMeasuredWidth() / 2;

            float roundProgress = progress >= 0.5f ? 1.0f : progress / 0.5f;
            float checkProgress = progress < 0.5f ? 0.0f : (progress - 0.5f) / 0.5f;

            float roundProgressCheckState = isCheckAnimation ? progress : (1.0f - progress);
            if (roundProgressCheckState < progressBounceDiff) {
                rad -= AndroidUtilities.dp(2) * roundProgressCheckState / progressBounceDiff;
            } else if (roundProgressCheckState < progressBounceDiff * 2) {
                rad -= AndroidUtilities.dp(2) - AndroidUtilities.dp(2) * (roundProgressCheckState - progressBounceDiff) / progressBounceDiff;
            }
            bitmapCanvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, rad, paint);
            bitmapCanvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, rad * (1 - roundProgress), eraser);
            canvas.drawBitmap(drawBitmap, 0, 0, null);

            checkBitmap.eraseColor(0);
            int w = checkDrawable.getIntrinsicWidth();
            int h = checkDrawable.getIntrinsicHeight();
            int x = (getMeasuredWidth() - w) / 2;
            int y = (getMeasuredHeight() - h) / 2;

            checkDrawable.setBounds(x, y, x + w, y + h);
            checkDrawable.draw(checkCanvas);
            checkCanvas.drawCircle(getMeasuredWidth() / 2 - AndroidUtilities.dp(2.5f), getMeasuredHeight() / 2 + AndroidUtilities.dp(4), ((getMeasuredWidth() + AndroidUtilities.dp(6)) / 2) * (1 - checkProgress), eraser2);

            canvas.drawBitmap(checkBitmap, 0, 0, null);
        }
    }
}
