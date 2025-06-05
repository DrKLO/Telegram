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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import androidx.annotation.Keep;
import androidx.core.graphics.ColorUtils;

import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

public class RadioButton extends View {

    private static Paint paint;
    private static Paint eraser;
    private static Paint checkedPaint;

    private int checkedColor;
    private int color;

    private float progress;
    private ObjectAnimator checkAnimator;

    private boolean attachedToWindow;
    private boolean isChecked;
    private int size = AndroidUtilities.dp(16);

    public RadioButton(Context context) {
        super(context);
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setStyle(Paint.Style.STROKE);
            checkedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraser.setColor(0);
            eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

//        try {
//            bitmap = Bitmap.createBitmap(AndroidUtilities.dp(size), AndroidUtilities.dp(size), Bitmap.Config.ARGB_4444);
//            bitmapCanvas = new Canvas(bitmap);
//        } catch (Throwable e) {
//            FileLog.e(e);
//        }
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    public void setSize(int value) {
        if (size == value) {
            return;
        }
        size = value;
    }

    private int iconColor;
    private Drawable icon;
    public void setIcon(Drawable drawable) {
        iconColor = 0;
        icon = drawable;
        invalidate();
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color1, int color2) {
        color = color1;
        checkedColor = color2;
        invalidate();
    }

    public void setBackgroundColor(int color1) {
        color = color1;
        invalidate();
    }

    public void setCheckedColor(int color2) {
        checkedColor = color2;
        invalidate();
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
    }

    private void animateToCheckedState(boolean newCheckedState) {
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(200);
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
        float circleProgress;
        if (progress <= 0.5f) {
            paint.setColor(color);
            checkedPaint.setColor(color);
            circleProgress = progress / 0.5f;
        } else {
            circleProgress = 2.0f - progress / 0.5f;
            int r1 = Color.red(color);
            int rD = (int) ((Color.red(checkedColor) - r1) * (1.0f - circleProgress));
            int g1 = Color.green(color);
            int gD = (int) ((Color.green(checkedColor) - g1) * (1.0f - circleProgress));
            int b1 = Color.blue(color);
            int bD = (int) ((Color.blue(checkedColor) - b1) * (1.0f - circleProgress));
            int c = Color.rgb(r1 + rD, g1 + gD, b1 + bD);
            paint.setColor(c);
            checkedPaint.setColor(c);
        }
        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
        float rad = size / 2 - (1 + circleProgress) * AndroidUtilities.density;
        canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, rad, paint);
        if (icon == null) {
            if (progress <= 0.5f) {
                canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, (rad - AndroidUtilities.dp(1)), checkedPaint);
                canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, (rad - AndroidUtilities.dp(1)) * (1.0f - circleProgress), eraser);
            } else {
                canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, size / 4 + (rad - AndroidUtilities.dp(1) - size / 4) * circleProgress, checkedPaint);
            }
        }
        canvas.restore();
        if (icon != null) {
            final int finalIconColor = ColorUtils.blendARGB(color, checkedColor, Utilities.clamp(progress, 1, 0));
            if (iconColor != finalIconColor) {
                icon.setColorFilter(new PorterDuffColorFilter(iconColor = finalIconColor, PorterDuff.Mode.SRC_IN));
            }
            icon.setBounds(
                    (int) (getWidth() / 2f  - icon.getIntrinsicWidth() / 2f),
                    (int) (getHeight() / 2f - icon.getIntrinsicHeight() / 2f),
                    (int) (getWidth() / 2f  + icon.getIntrinsicWidth() / 2f),
                    (int) (getHeight() / 2f + icon.getIntrinsicHeight() / 2f)
            );
            icon.draw(canvas);
        }
    }
}
