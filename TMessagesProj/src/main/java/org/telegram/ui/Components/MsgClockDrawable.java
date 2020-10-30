package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;

public class MsgClockDrawable extends Drawable {

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    int alpha = 255;
    int colorAlpha = 255;

    long startTime;

    public MsgClockDrawable() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(AndroidUtilities.dp(1f));
        startTime = System.currentTimeMillis();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int r = Math.min(bounds.width(), bounds.height());
        canvas.drawCircle(bounds.centerX(), bounds.centerY(), (r >> 1) - AndroidUtilities.dp(0.5f), paint);

        long currentTime = System.currentTimeMillis();
        float rotateTime = 1500;
        float rotateHourTime = rotateTime * 3;

        canvas.save();
        canvas.rotate(360 * ((currentTime - startTime) % rotateTime) / rotateTime, bounds.centerX(), bounds.centerY());
        canvas.drawLine(bounds.centerX(), bounds.centerY(), bounds.centerX(), bounds.centerY() - AndroidUtilities.dp(3), paint);
        canvas.restore();

        canvas.save();
        canvas.rotate(360 * ((currentTime - startTime) % rotateHourTime) / rotateHourTime, bounds.centerX(), bounds.centerY());
        canvas.drawLine(bounds.centerX(), bounds.centerY(), bounds.centerX() + AndroidUtilities.dp(2.3f), bounds.centerY(), paint);
        canvas.restore();
    }

    public void setColor(int color) {
        colorAlpha = Color.alpha(color);
        paint.setColor(color);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(12);
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(12);
    }

    @Override
    public void setAlpha(int i) {
        if (alpha != i) {
            alpha = i;
            paint.setAlpha((int) (alpha * (colorAlpha / 255f)));
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

}
