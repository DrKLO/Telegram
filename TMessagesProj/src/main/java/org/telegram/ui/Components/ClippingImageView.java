/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import org.telegram.messenger.FileLog;
import org.telegram.ui.AnimationCompat.ViewProxy;

public class ClippingImageView extends View {
    private int clipBottom;
    private int clipLeft;
    private int clipRight;
    private int clipTop;
    private Rect drawRect;
    private Paint paint;
    private Bitmap bmp;
    private onDrawListener drawListener;

    private boolean needRadius;
    private int radius;
    private BitmapShader bitmapShader;
    private Paint roundPaint;
    private RectF roundRect;
    private RectF bitmapRect;
    private Matrix shaderMatrix;

    public static interface onDrawListener {
        public abstract void onDraw();
    }

    public ClippingImageView(Context context) {
        super(context);
        paint = new Paint();
        paint.setFilterBitmap(true);
        drawRect = new Rect();
    }

    public int getClipBottom() {
        return clipBottom;
    }

    public int getClipHorizontal() {
        return clipRight;
    }

    public int getClipLeft() {
        return clipLeft;
    }

    public int getClipRight() {
        return clipRight;
    }

    public int getClipTop() {
        return clipTop;
    }

    public int getRadius() {
        return radius;
    }

    public void onDraw(Canvas canvas) {
        if (getVisibility() == GONE || getVisibility() == INVISIBLE) {
            return;
        }
        if (bmp != null) {
            float scaleY = ViewProxy.getScaleY(this);
            if (drawListener != null && scaleY != 1) {
                drawListener.onDraw();
            }
            canvas.save();
            if (needRadius) {
                roundRect.set(0, 0, getWidth(), getHeight());
                shaderMatrix.reset();
                shaderMatrix.setRectToRect(bitmapRect, roundRect, Matrix.ScaleToFit.FILL);
                bitmapShader.setLocalMatrix(shaderMatrix);
                canvas.drawRoundRect(roundRect, radius, radius, roundPaint);
            } else {
                canvas.clipRect(clipLeft / scaleY, clipTop / scaleY, getWidth() - clipRight / scaleY, getHeight() - clipBottom / scaleY);
                drawRect.set(0, 0, getWidth(), getHeight());
                try {
                    canvas.drawBitmap(bmp, null, drawRect, paint);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            canvas.restore();
        }
    }

    public void setClipBottom(int value) {
        clipBottom = value;
        invalidate();
    }

    public void setClipHorizontal(int value) {
        clipRight = value;
        clipLeft = value;
        invalidate();
    }

    public void setClipLeft(int value) {
        clipLeft = value;
        invalidate();
    }

    public void setClipRight(int value) {
        clipRight = value;
        invalidate();
    }

    public void setClipTop(int value) {
        clipTop = value;
        invalidate();
    }

    public void setClipVertical(int value) {
        clipBottom = value;
        clipTop = value;
        invalidate();
    }

    public void setImageBitmap(Bitmap bitmap) {
        bmp = bitmap;
        if (bitmap != null && needRadius) {
            roundRect = new RectF();
            shaderMatrix = new Matrix();
            bitmapRect = new RectF();
            bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            roundPaint.setShader(bitmapShader);
        }
        invalidate();
    }

    public void setOnDrawListener(onDrawListener listener) {
        drawListener = listener;
    }

    public void setNeedRadius(boolean value) {
        needRadius = value;
    }

    public void setRadius(int value) {
        radius = value;
    }
}
