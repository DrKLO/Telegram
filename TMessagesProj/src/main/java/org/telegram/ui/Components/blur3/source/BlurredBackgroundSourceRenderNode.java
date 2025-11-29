package org.telegram.ui.Components.blur3.source;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableRenderNode;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class BlurredBackgroundSourceRenderNode implements BlurredBackgroundSource {
    private final BlurredBackgroundSource fallbackSource;
    private final RenderNode renderNode;

    public BlurredBackgroundSourceRenderNode(BlurredBackgroundSource fallbackSource) {
        this.fallbackSource = fallbackSource;

        renderNode = new RenderNode("BlurredBackgroundSourceRenderNode");
        renderNode.setClipToBounds(true);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void setBlur(float radius) {
        renderNode.setRenderEffect(radius > 0 ? RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) : null);
    }

    private boolean inRecording;

    public interface Recorder {
        void draw(RecordingCanvas c);
    }

    public void doRecording(int width, int height, Recorder recorder) {
        RecordingCanvas canvas = beginRecording(width, height);
        recorder.draw(canvas);
        endRecording();
    }

    private RecordingCanvas recordingCanvas;


    public boolean needUpdateDisplayList(int width, int height) {
        return !renderNode.hasDisplayList() || renderNode.getWidth() != width || renderNode.getHeight() != height;
    }

    public RecordingCanvas beginRecording(int width, int height) {
        if (inRecording) {
            throw new IllegalStateException();
        }

        inRecording = true;

        renderNode.setPosition(0, 0, width, height);
        recordingCanvas = renderNode.beginRecording(width, height);
        return recordingCanvas;
    }

    public void endRecording() {
        if (!inRecording) {
            throw new IllegalStateException();
        }

        renderNode.endRecording();
        inRecording = false;
        recordingCanvas = null;
    }

    public boolean isRecordingCanvas(Canvas canvas) {
        return canvas != null && canvas == recordingCanvas;
    }

    public boolean inRecording() {
        return inRecording;
    }

    @Override
    public void draw(Canvas canvas, float left, float top, float right, float bottom) {
        if (!canvas.isHardwareAccelerated()) {
            fallbackSource.draw(canvas, left, top, right, bottom);
            return;
        }

        if (inRecording) {
            throw new IllegalStateException();
        }

        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.drawRenderNode(renderNode);
        canvas.restore();
    }

    public BlurredBackgroundSource getFallbackSource() {
        return fallbackSource;
    }

    @Override
    public BlurredBackgroundDrawable createDrawable() {
        return new BlurredBackgroundDrawableRenderNode(this);
    }
}
