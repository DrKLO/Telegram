package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class CrossOutDrawable extends Drawable {

    Drawable iconDrawable;
    RectF rectF = new RectF();
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    int color;
    String colorKey;
    float progress;
    boolean cross;

    public CrossOutDrawable(Context context, int iconRes, String colorKey) {
        iconDrawable = ContextCompat.getDrawable(context, iconRes);
        this.colorKey = colorKey;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dpf2(1.7f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        xRefPaint.setStyle(Paint.Style.STROKE);
        xRefPaint.setStrokeWidth(AndroidUtilities.dpf2(2.5f));
    }

    public void setCrossOut(boolean cross, boolean animated) {
        this.cross = cross;
        if (!animated) {
            progress = cross ? 1f : 0f;
        } else {
            progress = cross ? 0f : 1f;
        }
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (cross && progress != 1f) {
            progress += 16f / 150f;
            invalidateSelf();
            if (progress > 1f) {
                progress = 1f;
            }
        } else if (!cross && progress != 0f) {
            progress -= 16f / 150f;
            invalidateSelf();
            if (progress < 0) {
                progress = 0;
            }
        }
        if (progress == 0) {
            iconDrawable.draw(canvas);
            return;
        }
        int newColor = Theme.getColor(colorKey);
        if (color != newColor) {
            color = newColor;
            paint.setColor(newColor);
        }
        rectF.set(iconDrawable.getBounds());
        canvas.saveLayerAlpha(rectF, 255, Canvas.ALL_SAVE_FLAG);
        iconDrawable.draw(canvas);

        float startX = rectF.left + AndroidUtilities.dpf2(4.5f);
        float startY = rectF.top + AndroidUtilities.dpf2(4.5f) - AndroidUtilities.dp(1);
        float stopX = rectF.right - AndroidUtilities.dp(3);
        float stopY = rectF.bottom - AndroidUtilities.dp(1) - AndroidUtilities.dp(3);
        if (cross) {
            stopX = startX + (stopX - startX) * progress;
            stopY = startY + (stopY - startY) * progress;
        } else {
            startX = startX + (stopX - startX) * (1f - progress);
            startY = startY + (stopY - startY) * (1f - progress);
        }
        canvas.drawLine(startX, startY - paint.getStrokeWidth(), stopX, stopY - paint.getStrokeWidth(), xRefPaint);
        canvas.drawLine(startX, startY, stopX, stopY, paint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int i) {
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        iconDrawable.setBounds(left, top, right, bottom);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        iconDrawable.setColorFilter(colorFilter);
    }

    @Override
    public int getIntrinsicHeight() {
        return iconDrawable.getIntrinsicHeight();
    }

    @Override
    public int getIntrinsicWidth() {
        return iconDrawable.getIntrinsicWidth();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void setColorKey(String colorKey) {
        this.colorKey = colorKey;
    }
}
