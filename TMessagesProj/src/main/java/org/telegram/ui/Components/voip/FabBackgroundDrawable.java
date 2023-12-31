/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package org.telegram.ui.Components.voip;

import android.graphics.*;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;

import androidx.annotation.Keep;

public class FabBackgroundDrawable extends Drawable {

    private Paint bgPaint, shadowPaint;
    private Bitmap shadowBitmap;

    public FabBackgroundDrawable() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint = new Paint();
        shadowPaint.setColor(0x4C000000);
    }

    @Override
    public void draw(Canvas canvas) {
        if (shadowBitmap == null)
            onBoundsChange(getBounds());
        int size = Math.min(getBounds().width(), getBounds().height());
        if (shadowBitmap != null)
            canvas.drawBitmap(shadowBitmap, getBounds().centerX() - shadowBitmap.getWidth() / 2, getBounds().centerY() - shadowBitmap.getHeight() / 2, shadowPaint);
        canvas.drawCircle(size / 2, size / 2, size / 2 - AndroidUtilities.dp(4), bgPaint);
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        int size = Math.min(bounds.width(), bounds.height());
        if (size <= 0) {
            shadowBitmap = null;
            return;
        }
        shadowBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(shadowBitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawCircle(size / 2, size / 2, size / 2 - AndroidUtilities.dp(4), p);
    }

    @Keep
    public void setColor(int color) {
        bgPaint.setColor(color);
        invalidateSelf();
    }

    @Override
    public boolean getPadding(Rect padding) {
        int pad = AndroidUtilities.dp(4);
        padding.set(pad, pad, pad, pad);
        return true;
    }
}
