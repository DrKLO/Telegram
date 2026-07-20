package org.telegram.ui.Components.blur3.source;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

import androidx.annotation.Nullable;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableSource;

public class BlurredBackgroundSourceBitmap implements BlurredBackgroundSource {
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix bitmapMatrix = new Matrix();
    private @Nullable BitmapShader bitmapShader;
    private @Nullable Bitmap bitmap;

    public BlurredBackgroundSourceBitmap() {
        bitmapPaint.setFilterBitmap(true);
    }

    public void setBitmap(Bitmap bitmap) {
        if (this.bitmap == bitmap) {
            return;
        }

        this.bitmap = bitmap;

        bitmapPaint.setShader(null);
        bitmapShader = null;

        if (bitmap != null) {
            bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            bitmapPaint.setShader(bitmapShader);
            updateMatrix();
        }
    }

    public void setMatrix(Matrix matrix) {
        bitmapMatrix.set(matrix);
    }

    public Matrix getMatrix() {
        return bitmapMatrix;
    }

    public @Nullable Bitmap getBitmap() {
        return bitmap;
    }


    private final Matrix matrixForDraw = new Matrix();

    @Override
    public void draw(Canvas canvas, float left, float top, float right, float bottom) {
        if (bitmap == null || bitmap.isRecycled() || bitmapShader == null) {
            return;
        }

        matrixForDraw.set(bitmapMatrix);
        matrixForDraw.postTranslate(left, top);
        bitmapShader.setLocalMatrix(bitmapMatrix);

        canvas.drawRect(left, top, right, bottom, bitmapPaint);
    }

    @Override
    public BlurredBackgroundDrawable createDrawable() {
        return new BlurredBackgroundDrawableSource(this);
    }



    private Bitmap bitmapInternal;

    public Canvas beginRecording(int width, int height) {
        return beginRecording(width, height, 1);
    }

    public Canvas beginRecording(int width, int height, float scale) {
        final int bitmapWidth = Math.round(width / scale);
        final int bitmapHeight = Math.round(width / scale);
        if (bitmapInternal == null || bitmapInternal.isRecycled() || bitmapInternal.getWidth() != bitmapHeight || bitmapInternal.getHeight() != bitmapHeight) {
            bitmapInternal = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        } else {
            bitmapInternal.eraseColor(0);
        }

        final Canvas canvas = new Canvas(bitmapInternal);
        canvas.scale((float) width / bitmapWidth, (float) height / bitmapHeight);
        return canvas;
    }

    public void endRecording() {
        setBitmap(bitmapInternal);
        bitmapInternal = null;
    }

    public boolean inRecording() {
        return bitmapInternal != null;
    }



    /* Utils For Wallpaper */

    protected int parentWidth, parentHeight, actionBarHeight;

    public final void setParentSize(int width, int height, int actionBarHeight) {
        if (this.parentWidth != width || this.parentHeight != height || this.actionBarHeight != actionBarHeight) {
            this.parentWidth = width;
            this.parentHeight = height;
            this.actionBarHeight = actionBarHeight;
            updateMatrix();
        }
    }

    private void updateMatrix() {
        if (bitmap == null) {
            bitmapMatrix.reset();
            return;
        }

        buildCenterCropMatrix(bitmapMatrix,
            bitmap.getWidth(), bitmap.getHeight(),
            parentWidth, parentHeight, actionBarHeight);
    }

    private static void buildCenterCropMatrix(
            Matrix m,
            int bitmapWidth, int bitmapHeight,
            int parentWidth, int parentHeight,
            int parentPaddingTop
    ) {
        m.reset();

        final int parentInternalWidth = parentWidth;
        final int parentInternalHeight = parentHeight - parentPaddingTop;
        if (bitmapWidth <= 0 || bitmapHeight <= 0 || parentInternalWidth <= 0 || parentInternalHeight <= 0) {
            return;
        }

        float scale = Math.max(
                (float) parentInternalWidth  / (float) bitmapWidth,
                (float) parentInternalHeight / (float) bitmapHeight
        );

        float scaledW = bitmapWidth  * scale;
        float scaledH = bitmapHeight * scale;

        float dx = (parentInternalWidth  - scaledW) * 0.5f;
        float dy = (parentInternalHeight - scaledH) * 0.5f;

        m.setScale(scale, scale);
        m.postTranslate(dx, dy + parentPaddingTop);
    }
}
