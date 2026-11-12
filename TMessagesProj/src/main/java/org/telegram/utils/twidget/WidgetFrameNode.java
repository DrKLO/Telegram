package org.telegram.utils.twidget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorSpace;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;

import org.telegram.messenger.FileLog;

import androidx.annotation.RequiresApi;

/**
 * A widget's recorded drawing, one frame of its document or of a compositor layer: recorded once on the
 * widget's worker, then only drawn, by the UI thread, at its position in the widget.
 *
 * <p>API 29+ records a {@link RenderNode}: the UI thread draws the display list as it is, and a layer's
 * opacity is a node property. Before, a {@link Picture}: drawPicture replays its commands into the UI
 * thread's canvas on every draw, and opacity needs a saveLayer while it is below 1. A Picture is never
 * recorded again once drawn, so the worker and the UI thread never share one being recorded.
 */
public abstract class WidgetFrameNode {

    final int left, top, right, bottom;

    WidgetFrameNode(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /** A node for the area (left, top) – (right, bottom) of the widget, in px. */
    static WidgetFrameNode create(String name, int left, int top, int right, int bottom) {
        return Build.VERSION.SDK_INT >= 29
                ? new Node(name, left, top, right, bottom)
                : new PictureNode(left, top, right, bottom);
    }

    /** Worker: a canvas whose origin is the node's (left, top). */
    abstract Canvas beginRecording();

    abstract void endRecording();

    /** False once the system dropped a RenderNode's display list: it must be recorded again. */
    public abstract boolean recorded();

    /** Frees the recording early, for a frame never published. */
    abstract void discard();

    /**
     * Whether `canvas` can draw this node now. Any canvas can: a software one (a picture of the chat made into a
     * bitmap, a story's message) gets a RenderNode as a bitmap rendered from it.
     */
    public abstract boolean canDraw(Canvas canvas);

    /** UI: draws the node at its position in the widget's canvas, with `alpha` in [0, 1]. */
    public abstract void draw(Canvas canvas, float alpha);

    /**
     * UI: whether the node is drawn from a compositing layer, a texture HWUI keeps and composites, rather than
     * by replaying its display list (API 29+; a Picture has none).
     */
    public void setLayered(boolean layered) {
    }

    @RequiresApi(29)
    private static final class Node extends WidgetFrameNode {
        private final RenderNode node;

        Node(String name, int left, int top, int right, int bottom) {
            super(left, top, right, bottom);
            node = new RenderNode(name);
            node.setPosition(left, top, right, bottom);
        }

        @Override
        Canvas beginRecording() {
            return node.beginRecording(right - left, bottom - top);
        }

        @Override
        void endRecording() {
            node.endRecording();
        }

        @Override
        public boolean recorded() {
            return node.hasDisplayList();
        }

        @Override
        void discard() {
            node.discardDisplayList();
        }

        /** UI: the node rendered into a bitmap for software canvases, made on the first such draw. */
        private Bitmap software;
        private Paint softwarePaint;

        @Override
        public boolean canDraw(Canvas canvas) {
            return true;
        }

        @Override
        public void setLayered(boolean layered) {
            node.setUseCompositingLayer(layered, null);
        }

        @Override
        public void draw(Canvas canvas, float alpha) {
            if (!node.hasDisplayList()) return;
            if (!canvas.isHardwareAccelerated()) {
                drawSoftware(canvas, alpha);
                return;
            }
            // A UI-thread property of a node the worker no longer touches.
            node.setAlpha(alpha);
            canvas.drawRenderNode(node);
        }

        /** A software canvas cannot draw a RenderNode: it draws the node rendered once into a bitmap. */
        private void drawSoftware(Canvas canvas, float alpha) {
            if (alpha <= 0) return;
            if (software == null) {
                // A recorded node is never recorded again, so its bitmap stays right.
                software = render(node, left, top, right - left, bottom - top);
                if (software == null) return;
            }
            if (softwarePaint == null) softwarePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            softwarePaint.setAlpha(Math.round(Math.min(1, alpha) * 255));
            canvas.drawBitmap(software, left, top, softwarePaint);
        }

        /** UI: `node` (at left, top) rendered by an offscreen HardwareRenderer into a software bitmap, or null. */
        private static Bitmap render(RenderNode node, int left, int top, int width, int height) {
            if (width <= 0 || height <= 0) return null;
            ImageReader reader = null;
            HardwareRenderer renderer = null;
            try {
                reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1,
                        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
                renderer = new HardwareRenderer();
                renderer.setOpaque(false);
                renderer.setSurface(reader.getSurface());
                RenderNode root = new RenderNode("widget-sandbox-software");
                root.setPosition(0, 0, width, height);
                RecordingCanvas recording = root.beginRecording(width, height);
                recording.translate(-left, -top);
                node.setAlpha(1);
                recording.drawRenderNode(node);
                root.endRecording();
                renderer.setContentRoot(root);
                renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
                Image image = reader.acquireNextImage();
                if (image == null) return null;
                try {
                    HardwareBuffer buffer = image.getHardwareBuffer();
                    if (buffer == null) return null;
                    try {
                        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB));
                        return hardware == null ? null : hardware.copy(Bitmap.Config.ARGB_8888, false);
                    } finally {
                        buffer.close();
                    }
                } finally {
                    image.close();
                }
            } catch (Throwable e) {
                FileLog.e(e);
                return null;
            } finally {
                if (renderer != null) renderer.destroy();
                if (reader != null) reader.close();
            }
        }
    }

    private static final class PictureNode extends WidgetFrameNode {
        private final Picture picture = new Picture();
        private boolean recording;

        PictureNode(int left, int top, int right, int bottom) {
            super(left, top, right, bottom);
        }

        @Override
        Canvas beginRecording() {
            recording = true;
            return picture.beginRecording(Math.max(1, right - left), Math.max(1, bottom - top));
        }

        @Override
        void endRecording() {
            picture.endRecording();
            recording = false;
        }

        @Override
        public boolean recorded() {
            return true;
        }

        @Override
        void discard() {
            if (recording) endRecording();
        }

        @Override
        public boolean canDraw(Canvas canvas) {
            return true;
        }

        @Override
        public void draw(Canvas canvas, float alpha) {
            if (recording || alpha <= 0) return;
            int count;
            if (alpha < 1) {
                count = canvas.saveLayerAlpha(left, top, right, bottom, Math.round(alpha * 255));
            } else {
                count = canvas.save();
            }
            canvas.translate(left, top);
            canvas.drawPicture(picture);
            canvas.restoreToCount(count);
        }
    }
}
