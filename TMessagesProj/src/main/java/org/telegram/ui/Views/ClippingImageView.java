/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

public class ClippingImageView extends View {
    private int clipBottom;
    private int clipLeft;
    private int clipRight;
    private int clipTop;
    private Rect drawRect;
    private Paint paint;
    private Bitmap bmp;
    private onDrawListener drawListener;

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

    public void onDraw(Canvas canvas) {
        if (bmp != null) {
            if (drawListener != null && getScaleY() != 1) {
                drawListener.onDraw();
            }
            canvas.save();
            canvas.clipRect(clipLeft / getScaleY(), clipTop / getScaleY(), getWidth() - clipRight / getScaleY(), getHeight() - clipBottom / getScaleY());
            drawRect.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(this.bmp, null, drawRect, this.paint);
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
        invalidate();
    }

    public void setOnDrawListener(onDrawListener listener) {
        drawListener = listener;
    }
}
