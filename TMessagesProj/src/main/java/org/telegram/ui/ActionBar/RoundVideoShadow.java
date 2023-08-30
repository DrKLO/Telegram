package org.telegram.ui.ActionBar;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class RoundVideoShadow extends Drawable {

    Paint eraserPaint;
    Paint paint;

    public RoundVideoShadow() {
//        eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//        eraserPaint.setColor(0);
//        eraserPaint.setStyle(Paint.Style.FILL);
//        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0x5f000000);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float cx = getBounds().centerX();
        float cy = getBounds().centerY();
//        for (int a = 0; a < 2; a++) {
//            canvas.drawCircle(cx, cy, AndroidUtilities.roundMessageSize / 2f - AndroidUtilities.dp(1), a == 0 ? paint : eraserPaint);
//        }
        float r = (getBounds().width() - AndroidUtilities.dp(8)) / 2f;
        canvas.drawCircle(cx, cy - AndroidUtilities.dp(1), r, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
       // eraserPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }
}
