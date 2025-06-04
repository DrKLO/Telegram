package org.telegram.messenger.pip.source;

import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.RenderNode;
import android.os.Build;
import android.view.View;

import org.telegram.messenger.Utilities;

class PipSourceSnapshot {
    private final Picture picture;
    private final RenderNode node;

    public PipSourceSnapshot(int width, int height, Utilities.Callback<Canvas> onDraw) {
        picture = new Picture();
        onDraw.run(picture.beginRecording(width, height));
        picture.endRecording();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            node = new RenderNode("pip-node-" + View.generateViewId());
            node.setPosition(0, 0, width, height);
            node.beginRecording().drawPicture(picture);
            node.endRecording();
        } else {
            node = null;
        }
    }

    public void draw(Canvas canvas, float alpha) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (node != null) {
                node.setAlpha(alpha);
                canvas.drawRenderNode(node);
            }
        } else if (picture != null) {
            if (alpha > 0.001f) {
                final boolean needAlpha = alpha < 0.999f;
                if (needAlpha) {
                    canvas.saveLayerAlpha(0, 0, picture.getWidth(), picture.getHeight(), (int) (alpha * 255), Canvas.ALL_SAVE_FLAG);
                }
                canvas.drawPicture(picture);
                if (needAlpha) {
                    canvas.restore();
                }
            }
        }
    }

    public void release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            node.discardDisplayList();
        }
    }
}
