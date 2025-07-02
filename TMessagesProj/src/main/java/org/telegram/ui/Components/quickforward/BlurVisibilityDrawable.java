package org.telegram.ui.Components.quickforward;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import org.telegram.messenger.Utilities;

public class BlurVisibilityDrawable extends Drawable {

    public interface DrawRunnable {
        void draw(@NonNull Canvas canvas, int alpha);
    }

    public BlurVisibilityDrawable (DrawRunnable runnable) {
        this.drawRunnable = runnable;
    }

    private final Paint emptyPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final DrawRunnable drawRunnable;

    private Bitmap bitmap;
    private Canvas canvas;

    private int width;
    private int height;
    private int blurRadius;
    private float bitmapScale;

    private int left, top;
    private int alpha = 255;

    public boolean hasBitmap () {
        return bitmap != null;
    }

    public void render (int width, int height, int blurRadius, float bitmapScale) {
        final int bitmapWidth = (int) ((width + blurRadius * 2) / bitmapScale);
        final int bitmapHeight = (int) ((height + blurRadius * 2) / bitmapScale);

        if (bitmap == null || bitmap.getWidth() != bitmapWidth || bitmap.getHeight() != bitmapHeight) {
            if (bitmap != null) {
                bitmap.recycle();
            }

            bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
        } else {
            bitmap.eraseColor(0);
        }

        this.bitmapScale = bitmapScale;
        this.blurRadius = blurRadius;
        this.width = width;
        this.height = height;

        canvas.save();
        canvas.translate(blurRadius / bitmapScale, blurRadius / bitmapScale);
        canvas.scale(1 / bitmapScale, 1 / bitmapScale);
        drawRunnable.draw(canvas, 255);
        Utilities.stackBlurBitmap(bitmap, (int) (blurRadius / bitmapScale));
        canvas.restore();
    }

    public void recycle () {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (alpha == 255) {
            drawNormal(canvas, 255);
            return;
        } else if (alpha == 0) {
            return;
        }

        double alphaTarget = alpha / 255.0;
        double a1Component = alphaTarget;
        double a2Component = 1 - a1Component;
        double k = a1Component / (a2Component * 6);
        double D = (k + 1) * (k + 1) - 4 * (-k) * (-alphaTarget);
        double sqrtD = Math.sqrt(D);

        double a2_1 = (-(k + 1) + sqrtD) / (-2 * k);
        double a1_1 = a2_1 * k;

        int a1i = MathUtils.clamp((int) (a1_1 * 255), 0, 255);
        int a2i = MathUtils.clamp((int) (a2_1 * 255), 0, 255);

        drawBlur(canvas, a2i);
        drawNormal(canvas, a1i);
    }

    private void drawNormal (Canvas canvas, int alpha) {
        if (alpha > 0) {
            canvas.save();
            canvas.translate(left, top);
            drawRunnable.draw(canvas, alpha);
            canvas.restore();
        }
    }

    private void drawBlur (Canvas canvas, int alpha) {
        if (alpha > 0 && bitmap != null) {
            emptyPaint.setAlpha(alpha);
            canvas.save();
            canvas.translate(left - blurRadius, top - blurRadius);
            canvas.scale(bitmapScale, bitmapScale);
            canvas.drawBitmap(bitmap, 0, 0, emptyPaint);
            canvas.restore();
        }
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(this.left = left, this.top = top, right, bottom);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }
}
