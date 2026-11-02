package org.telegram.ui.QrShare;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

/**
 * The premium star of R.drawable.star_premium as a plain shape in one colour (set by the colour filter, like a tint).
 * The resource has a gradient fill: before Android 7 the framework cannot draw that, and the build's PNG fallback of it
 * comes out empty, so the star of the fee chip was missing there.
 * The path is rasterized once per size into a white bitmap, drawn like any tinted icon: a path on the GPU is a draw of
 * its own kind (a stencil-then-cover or atlas pass, programs compiled for it on the screen's first open).
 */
final class QrStarDrawable extends Drawable {

    // the outline of star_premium.xml, viewport 52 × 52
    private static final String PATH = "M25.0339 38.8535L16.4947 44.2223C15.6075 44.7802 14.4361 44.5131 13.8782 43.6258C13.6038 43.1893 13.5188 42.6601 13.6426 42.1598L15.0264 36.5704C15.4811 34.7334 16.7041 33.1809 18.3835 32.3088L26.359 28.1671C26.7894 27.9436 26.9572 27.4137 26.7337 26.9833C26.5595 26.6481 26.1903 26.4617 25.8172 26.5208L16.9312 27.9261C14.8088 28.2617 12.6463 27.6475 11.0173 26.2465L7.5978 23.3054C6.8032 22.622 6.71306 21.4239 7.39646 20.6293C7.7225 20.2503 8.18486 20.0153 8.68322 19.9753L18.8056 19.1622C19.5221 19.1046 20.1443 18.6473 20.4134 17.9809L24.2843 8.39097C24.6766 7.41912 25.7823 6.94929 26.7542 7.34157C27.232 7.53443 27.6108 7.91321 27.8037 8.39097L31.6745 17.9809C31.9435 18.6473 32.5659 19.1046 33.2822 19.1622L43.4582 19.9795C44.5029 20.0634 45.2817 20.9784 45.1978 22.023C45.1582 22.5159 44.9279 22.9737 44.5559 23.2992L36.7229 30.1527C36.1947 30.6149 35.9647 31.3308 36.125 32.0141L38.5201 42.2261C38.7594 43.2465 38.1263 44.2677 37.106 44.507C36.6119 44.6228 36.0921 44.536 35.6626 44.2659L27.054 38.8535C26.4366 38.4654 25.6513 38.4654 25.0339 38.8535Z";
    private static final float VIEWPORT = 52f;

    private static Path shape;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;

    private static Path shape() {
        if (shape == null) {
            shape = PathParser.createPathFromPathData(PATH);
        }
        return shape;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        if (bitmap == null || bitmap.getWidth() != b.width() || bitmap.getHeight() != b.height()) {
            bitmap = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);
            final Path path = new Path();
            final Matrix matrix = new Matrix();
            matrix.setScale(b.width() / VIEWPORT, b.height() / VIEWPORT);
            shape().transform(matrix, path);
            final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(0xFFFFFFFF);
            new Canvas(bitmap).drawPath(path, fill);
        }
        canvas.drawBitmap(bitmap, b.left, b.top, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return org.telegram.messenger.AndroidUtilities.dp(18);
    }

    @Override
    public int getIntrinsicHeight() {
        return org.telegram.messenger.AndroidUtilities.dp(18);
    }
}
