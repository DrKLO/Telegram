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

    Paint paint;

    public RoundVideoShadow() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0x5f000000);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float cx = getBounds().centerX();
        float cy = getBounds().centerY();
        float r = (getBounds().width() - AndroidUtilities.dp(8)) / 2f;
        canvas.drawCircle(cx, cy - AndroidUtilities.dp(1), r, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }
}
