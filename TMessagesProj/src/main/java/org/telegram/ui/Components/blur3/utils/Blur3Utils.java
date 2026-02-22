package org.telegram.ui.Components.blur3.utils;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.chat.ViewPositionWatcher;

public class Blur3Utils {
    private Blur3Utils() {

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
