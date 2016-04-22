/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.AnimationCompat.ViewProxy;

public class ClippingImageView extends View {

    private int clipBottom;
    private int clipLeft;
    private int clipRight;
    private int clipTop;
    private int orientation;
    private RectF drawRect;
    private Paint paint;
    private Bitmap bmp;
    private Matrix matrix;

    private boolean needRadius;
    private int radius;
    private BitmapShader bitmapShader;
    private Paint roundPaint;
    private RectF roundRect;
    private RectF bitmapRect;
    private Matrix shaderMatrix;

    private float animationProgress;
    private float animationValues[][];

    public ClippingImageView(Context context) {
        super(context);
        paint = new Paint();
        paint.setFilterBitmap(true);
        matrix = new Matrix();
        drawRect = new RectF();
        bitmapRect = new RectF();
        roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        roundRect = new RectF();
        shaderMatrix = new Matrix();
    }

    public void setAnimationValues(float[][] values) {
        animationValues = values;
    }

    public float getAnimationProgress() {
        return animationProgress;
    }

    public void setAnimationProgress(float progress) {
        animationProgress = progress;

        ViewProxy.setScaleX(this, animationValues[0][0] + (animationValues[1][0] - animationValues[0][0]) * animationProgress);
        ViewProxy.setScaleY(this, animationValues[0][1] + (animationValues[1][1] - animationValues[0][1]) * animationProgress);
        ViewProxy.setTranslationX(this, animationValues[0][2] + (animationValues[1][2] - animationValues[0][2]) * animationProgress);
        ViewProxy.setTranslationY(this, animationValues[0][3] + (animationValues[1][3] - animationValues[0][3]) * animationProgress);
        setClipHorizontal((int) (animationValues[0][4] + (animationValues[1][4] - animationValues[0][4]) * animationProgress));
        setClipTop((int) (animationValues[0][5] + (animationValues[1][5] - animationValues[0][5]) * animationProgress));
        setClipBottom((int) (animationValues[0][6] + (animationValues[1][6] - animationValues[0][6]) * animationProgress));
        setRadius((int) (animationValues[0][7] + (animationValues[1][7] - animationValues[0][7]) * animationProgress));

        invalidate();
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
        if (getVisibility() != VISIBLE) {
            return;
        }
        if (bmp != null) {
            float scaleY = ViewProxy.getScaleY(this);
            canvas.save();

            if (needRadius) {
                shaderMatrix.reset();
                roundRect.set(0, 0, getWidth(), getHeight());

                int bitmapW;
                int bitmapH;
                if (orientation % 360 == 90 || orientation % 360 == 270) {
                    bitmapW = bmp.getHeight();
                    bitmapH = bmp.getWidth();
                } else {
                    bitmapW = bmp.getWidth();
                    bitmapH = bmp.getHeight();
                }
                float scaleW = getWidth() != 0 ? bitmapW / getWidth() : 1.0f;
                float scaleH = getHeight() != 0 ? bitmapH / getHeight() : 1.0f;
                float scale = Math.min(scaleW, scaleH);
                if (Math.abs(scaleW - scaleH) > 0.00001f) {
                    int w = (int) Math.floor(getWidth() * scale);
                    int h = (int) Math.floor(getHeight() * scale);
                    bitmapRect.set((bitmapW - w) / 2, (bitmapH - h) / 2, w, h);
                    shaderMatrix.setRectToRect(bitmapRect, roundRect, Matrix.ScaleToFit.START);
                } else {
                    bitmapRect.set(0, 0, bmp.getWidth(), bmp.getHeight());
                    shaderMatrix.setRectToRect(bitmapRect, roundRect, Matrix.ScaleToFit.FILL);
                }
                bitmapShader.setLocalMatrix(shaderMatrix);
                canvas.clipRect(clipLeft / scaleY, clipTop / scaleY, getWidth() - clipRight / scaleY, getHeight() - clipBottom / scaleY);
                canvas.drawRoundRect(roundRect, radius, radius, roundPaint);
            } else {
                if (orientation == 90 || orientation == 270) {
                    drawRect.set(-getHeight() / 2, -getWidth() / 2, getHeight() / 2, getWidth() / 2);
                    matrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL);
                    matrix.postRotate(orientation, 0, 0);
                    matrix.postTranslate(getWidth() / 2, getHeight() / 2);
                } else if (orientation == 180) {
                    drawRect.set(-getWidth() / 2, -getHeight() / 2, getWidth() / 2, getHeight() / 2);
                    matrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL);
                    matrix.postRotate(orientation, 0, 0);
                    matrix.postTranslate(getWidth() / 2, getHeight() / 2);
                } else {
                    drawRect.set(0, 0, getWidth(), getHeight());
                    matrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL);
                }

                canvas.clipRect(clipLeft / scaleY, clipTop / scaleY, getWidth() - clipRight / scaleY, getHeight() - clipBottom / scaleY);
                try {
                    canvas.drawBitmap(bmp, matrix, paint);
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

    public void setOrientation(int angle) {
        orientation = angle;
    }

    public void setImageBitmap(Bitmap bitmap) {
        bmp = bitmap;
        if (bitmap != null) {
            bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            if (needRadius) {
                bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                roundPaint.setShader(bitmapShader);
            }
        }
        invalidate();
    }

    public void setNeedRadius(boolean value) {
        needRadius = value;
    }

    public void setRadius(int value) {
        radius = value;
    }
}
