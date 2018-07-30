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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.annotation.Keep;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class Switch2 extends View {

    private RectF rectF;

    private static Bitmap drawBitmap;

    private float progress;
    private ObjectAnimator checkAnimator;

    private boolean attachedToWindow;
    private boolean isChecked;
    private Paint paint;
    private Paint paint2;
    private Paint shadowPaint;

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

        shadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
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

        int color1 = Theme.getColor(Theme.key_switch2Track);
        int color2 = Theme.getColor(Theme.key_switch2TrackChecked);
        int r1 = Color.red(color1);
        int r2 = Color.red(color2);
        int g1 = Color.green(color1);
        int g2 = Color.green(color2);
        int b1 = Color.blue(color1);
        int b2 = Color.blue(color2);
        int a1 = Color.alpha(color1);
        int a2 = Color.alpha(color2);

        int red = (int) (r1 + (r2 - r1) * progress);
        int green = (int) (g1 + (g2 - g1) * progress);
        int blue = (int) (b1 + (b2 - b1) * progress);
        int alpha = (int) (a1 + (a2 - a1) * progress);
        paint.setColor(((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff));

        rectF.set(x, y, x + width, y + AndroidUtilities.dp(14));
        canvas.drawRoundRect(rectF, AndroidUtilities.dp(7), AndroidUtilities.dp(7), paint);

        color1 = Theme.getColor(Theme.key_switch2Thumb);
        color2 = Theme.getColor(Theme.key_switch2ThumbChecked);
        r1 = Color.red(color1);
        r2 = Color.red(color2);
        g1 = Color.green(color1);
        g2 = Color.green(color2);
        b1 = Color.blue(color1);
        b2 = Color.blue(color2);
        a1 = Color.alpha(color1);
        a2 = Color.alpha(color2);

        red = (int) (r1 + (r2 - r1) * progress);
        green = (int) (g1 + (g2 - g1) * progress);
        blue = (int) (b1 + (b2 - b1) * progress);
        alpha = (int) (a1 + (a2 - a1) * progress);
        paint.setColor(((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff));

        shadowPaint.setAlpha(alpha);
        canvas.drawBitmap(drawBitmap, tx - AndroidUtilities.dp(12), ty - AndroidUtilities.dp(11), shadowPaint);
        canvas.drawCircle(tx, ty, AndroidUtilities.dp(10), paint);

        paint2.setColor(Theme.getColor(Theme.key_switch2Check));
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
