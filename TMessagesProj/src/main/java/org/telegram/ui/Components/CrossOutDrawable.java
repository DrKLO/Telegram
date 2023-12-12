package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
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
    int colorKey;
    float progress;
    boolean cross;

    private float xOffset;
    private float lenOffsetTop;
    private float lenOffsetBottom;

    public CrossOutDrawable(Context context, int iconRes, int colorKey) {
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
        if (this.cross != cross) {
            this.cross = cross;
            if (!animated) {
                progress = cross ? 1f : 0f;
            } else {
                progress = cross ? 0f : 1f;
            }
            invalidateSelf();
        }
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
        int newColor = colorKey < 0 ? Color.WHITE : Theme.getColor(colorKey);
        if (color != newColor) {
            color = newColor;
            paint.setColor(newColor);
            iconDrawable.setColorFilter(new PorterDuffColorFilter(newColor, PorterDuff.Mode.MULTIPLY));
        }
        if (progress == 0) {
            iconDrawable.draw(canvas);
            return;
        }
        rectF.set(iconDrawable.getBounds());
        canvas.saveLayerAlpha(rectF, 255, Canvas.ALL_SAVE_FLAG);
        iconDrawable.draw(canvas);

        float startX = rectF.left + AndroidUtilities.dpf2(4.5f) + xOffset + lenOffsetTop;
        float startY = rectF.top + AndroidUtilities.dpf2(4.5f) - AndroidUtilities.dp(1) + lenOffsetTop;
        float stopX = rectF.right - AndroidUtilities.dp(3) + xOffset - lenOffsetBottom;
        float stopY = rectF.bottom - AndroidUtilities.dp(1) - AndroidUtilities.dp(3) - lenOffsetBottom;
        if (cross) {
            stopX = startX + (stopX - startX) * progress;
            stopY = startY + (stopY - startY) * progress;
        } else {
            startX = startX + (stopX - startX) * (1f - progress);
            startY = startY + (stopY - startY) * (1f - progress);
        }
        canvas.drawLine(startX, startY - paint.getStrokeWidth(), stopX, stopY - paint.getStrokeWidth(), xRefPaint);
        float offsetY = (xRefPaint.getStrokeWidth() - paint.getStrokeWidth()) / 2f + 1;
        canvas.drawLine(startX, startY - offsetY, stopX, stopY - offsetY, xRefPaint);
        canvas.drawLine(startX, startY, stopX, stopY, paint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int i) {
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        iconDrawable.setBounds(left, top, right, bottom);
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
        return PixelFormat.TRANSPARENT;
    }

    public void setColorKey(int colorKey) {
        this.colorKey = colorKey;
    }

    public void setOffsets(float xOffset, float lenOffsetTop, float lenOffsetBottom) {
        this.xOffset = xOffset;
        this.lenOffsetTop = lenOffsetTop;
        this.lenOffsetBottom = lenOffsetBottom;
        invalidateSelf();
    }

    public void setStrokeWidth(float w) {
        paint.setStrokeWidth(w);
        xRefPaint.setStrokeWidth(w * 1.47f);
    }

    public float getProgress() {
        return progress;
    }
}
