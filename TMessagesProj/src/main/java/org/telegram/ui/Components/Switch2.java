/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.Keep;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

public class Switch2 extends View {

    private RectF rectF;

    private static Bitmap drawBitmap;

    private float progress;
    private ObjectAnimator checkAnimator;

    private boolean attachedToWindow;
    private boolean isChecked;
    private boolean isDisabled;
    private Paint paint;
    private Paint paint2;

    public Switch2(Context context) {
        super(context);
        rectF = new RectF();
        if (drawBitmap == null || drawBitmap.getWidth() != AndroidUtilities.dp(24)) {
            drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(24), AndroidUtilities.dp(24), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(drawBitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShadowLayer(AndroidUtilities.dp(2), 0, 0, 0x7f000000);
            canvas.drawCircle(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(9), paint);
            try {
                canvas.setBitmap(null);
            } catch (Exception ignore) {

            }
        }

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setStrokeCap(Paint.Cap.ROUND);
        paint2.setStrokeWidth(AndroidUtilities.dp(2));
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

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
        }
    }

    private void animateToCheckedState(boolean newCheckedState) {
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(250);
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

        int width = AndroidUtilities.dp(36);
        int thumb = AndroidUtilities.dp(20);
        int x = (getMeasuredWidth() - width) / 2;
        int y = (getMeasuredHeight() - AndroidUtilities.dp(14)) / 2;
        int tx = (int) ((width - AndroidUtilities.dp(14)) * progress) + x + AndroidUtilities.dp(7);
        int ty = getMeasuredHeight() / 2;


        int red = (int) (0xff + (0xa0 - 0xff) * progress);
        int green = (int) (0xb0 + (0xd6 - 0xb0) * progress);
        int blue = (int) (0xad + (0xfa - 0xad) * progress);
        paint.setColor(0xff000000 | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff));

        rectF.set(x, y, x + width, y + AndroidUtilities.dp(14));
        canvas.drawRoundRect(rectF, AndroidUtilities.dp(7), AndroidUtilities.dp(7), paint);

        red = (int) (0xdb + (0x44 - 0xdb) * progress);
        green = (int) (0x58 + (0xa8 - 0x58) * progress);
        blue = (int) (0x5c + (0xea - 0x5c) * progress);
        paint.setColor(0xff000000 | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff));

        canvas.drawBitmap(drawBitmap, tx - AndroidUtilities.dp(12), ty - AndroidUtilities.dp(11), null);
        canvas.drawCircle(tx, ty, AndroidUtilities.dp(10), paint);

        paint2.setColor(0xffffffff);
        tx -= AndroidUtilities.dp(10.8f) - AndroidUtilities.dp(1.3f) * progress;
        ty -= AndroidUtilities.dp(8.5f) - AndroidUtilities.dp(0.5f) * progress;
        int startX2 = (int) AndroidUtilities.dpf2(4.6f) + tx;
        int startY2 = (int) (AndroidUtilities.dpf2(9.5f) + ty);
        int endX2 = startX2 + AndroidUtilities.dp(2);
        int endY2 = startY2 + AndroidUtilities.dp(2);

        int startX = (int) AndroidUtilities.dpf2(7.5f) + tx;
        int startY = (int) AndroidUtilities.dpf2(5.4f) + ty;
        int endX = startX + AndroidUtilities.dp(7);
        int endY = startY + AndroidUtilities.dp(7);

        startX = (int) (startX + (startX2 - startX) * progress);
        startY = (int) (startY + (startY2 - startY) * progress);
        endX = (int) (endX + (endX2 - endX) * progress);
        endY = (int) (endY + (endY2 - endY) * progress);
        canvas.drawLine(startX, startY, endX, endY, paint2);

        startX = (int) AndroidUtilities.dpf2(7.5f) + tx;
        startY = (int) AndroidUtilities.dpf2(12.5f) + ty;
        endX = startX + AndroidUtilities.dp(7);
        endY = startY - AndroidUtilities.dp(7);
        canvas.drawLine(startX, startY, endX, endY, paint2);
    }
}
