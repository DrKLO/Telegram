package org.telegram.ui.Components.blur3.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import org.jspecify.annotations.Nullable;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.Components.chat.ViewPositionWatcher;

public class Blur3Utils {
    private Blur3Utils() {

    }

    private static final Matrix matrixTmp = new Matrix();

    public static boolean checkBitmapSourceMatrixScale(@Nullable BlurredBackgroundSourceBitmap source, @Nullable View view) {
        if (source == null || view == null || view.getWidth() == 0 || view.getHeight() == 0) {
            return false;
        }
        final Bitmap bitmap = source.getBitmap();
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return false;
        }

        matrixTmp.reset();
        matrixTmp.setScale(
            (float) view.getWidth() / bitmap.getWidth(),
            (float) view.getHeight() / bitmap.getHeight());
        source.setMatrix(matrixTmp);

        return true;
    }

    private static final RectF captureTmpRectF = new RectF();
    private static final RectF captureTmpChildPos = new RectF();

    public static void captureRelativeParent(IBlur3Capture capture, Canvas canvas, RectF position, View view, ViewGroup parent) {
        captureRelativeParent(capture, canvas, position, view, parent, 255);
    }

    public static void captureRelativeParent(IBlur3Capture capture, Canvas canvas, RectF position, View view, ViewGroup parent, int alpha) {
        if (alpha <= 0) {
            return;
        }

        if (ViewPositionWatcher.computeRectInParent(view, parent, captureTmpChildPos)) {
            final float oX = captureTmpChildPos.left;
            final float oY = captureTmpChildPos.top;
            captureTmpRectF.set(position);
            captureTmpRectF.offset(-oX, -oY);

            final boolean needSaveTranslation = oX != 0 || oY != 0;
            final boolean needSaveAlpha = alpha != 255;

            if (needSaveTranslation) {
                canvas.save();
                canvas.translate(oX, oY);
            }
            if (needSaveAlpha) {
                canvas.saveLayerAlpha(captureTmpRectF, alpha);
            }
            capture.capture(canvas, captureTmpRectF);
            if (needSaveAlpha) {
                canvas.restore();
            }
            if (needSaveTranslation) {
                canvas.restore();
            }
        }
    }
}
