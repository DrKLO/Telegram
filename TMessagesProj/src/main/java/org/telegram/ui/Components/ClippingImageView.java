/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
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
import androidx.annotation.Keep;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;

public class ClippingImageView extends View {

    private int clipBottom;
    private int clipLeft;
    private int clipRight;
    private int clipTop;
    private int orientation;
    private int imageY;
    private int imageX;
    private RectF drawRect;
    private Paint paint;
    private ImageReceiver.BitmapHolder bmp;
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
        paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setFilterBitmap(true);
        matrix = new Matrix();
        drawRect = new RectF();
        bitmapRect = new RectF();
        roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        roundRect = new RectF();
        shaderMatrix = new Matrix();
    }

    public void setAnimationValues(float[][] values) {
        animationValues = values;
    }

    @Keep
    public float getAnimationProgress() {
        return animationProgress;
    }

    @Keep
    public void setAnimationProgress(float progress) {
        animationProgress = progress;

        setScaleX(animationValues[0][0] + (animationValues[1][0] - animationValues[0][0]) * animationProgress);
        setScaleY(animationValues[0][1] + (animationValues[1][1] - animationValues[0][1]) * animationProgress);
        setTranslationX(animationValues[0][2] + (animationValues[1][2] - animationValues[0][2]) * animationProgress);
        setTranslationY(animationValues[0][3] + (animationValues[1][3] - animationValues[0][3]) * animationProgress);
        setClipHorizontal((int) (animationValues[0][4] + (animationValues[1][4] - animationValues[0][4]) * animationProgress));
        setClipTop((int) (animationValues[0][5] + (animationValues[1][5] - animationValues[0][5]) * animationProgress));
        setClipBottom((int) (animationValues[0][6] + (animationValues[1][6] - animationValues[0][6]) * animationProgress));
        setRadius((int) (animationValues[0][7] + (animationValues[1][7] - animationValues[0][7]) * animationProgress));
        if (animationValues[0].length > 8) {
            setImageY((int) (animationValues[0][8] + (animationValues[1][8] - animationValues[0][8]) * animationProgress));
            setImageX((int) (animationValues[0][9] + (animationValues[1][9] - animationValues[0][9]) * animationProgress));
        }

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
        if (bmp != null && !bmp.isRecycled()) {
            float scaleY = getScaleY();
            canvas.save();

            if (needRadius) {
                shaderMatrix.reset();
                roundRect.set(imageX / scaleY, imageY / scaleY, getWidth() - imageX / scaleY, getHeight() - imageY / scaleY);
                bitmapRect.set(0, 0, bmp.getWidth(), bmp.getHeight());
                AndroidUtilities.setRectToRect(shaderMatrix, bitmapRect, roundRect, orientation, false);
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
                    canvas.drawBitmap(bmp.bitmap, matrix, paint);
                } catch (Exception e) {
                    FileLog.e(e);
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

    public void setImageY(int value) {
        imageY = value;
    }

    public void setImageX(int value) {
        imageX = value;
    }

    public void setOrientation(int angle) {
        orientation = angle;
    }

    public void setImageBitmap(ImageReceiver.BitmapHolder bitmap) {
        if (bmp != null) {
            bmp.release();
            bitmapShader = null;
        }
        bmp = bitmap;
        if (bitmap != null && bitmap.bitmap != null) {
            bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            if (needRadius) {
                bitmapShader = new BitmapShader(bmp.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                roundPaint.setShader(bitmapShader);
            }
        }
        invalidate();
    }

    public Bitmap getBitmap() {
        return bmp != null ? bmp.bitmap : null;
    }

    public int getOrientation() {
        return orientation;
    }

    public void setNeedRadius(boolean value) {
        needRadius = value;
    }

    public void setRadius(int value) {
        radius = value;
    }
}
