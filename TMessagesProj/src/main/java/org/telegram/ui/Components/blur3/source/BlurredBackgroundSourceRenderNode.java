package org.telegram.ui.Components.blur3.source;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.RenderNodeWithHash;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawableRenderNode;

import java.util.List;

import me.vkryl.core.reference.ReferenceList;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class BlurredBackgroundSourceRenderNode implements BlurredBackgroundSource {
    private final BlurredBackgroundSource fallbackSource;
    private final RenderNode renderNode;
    private RenderNodeWithHash renderNodeWithHash;

    private DownscaleScrollableNoiseSuppressor scrollableNoiseSuppressor;
    private int scrollableNoiseSuppressorIndex;
    public BlurredBackgroundSource underSource;

    public BlurredBackgroundSourceRenderNode(BlurredBackgroundSource fallbackSource) {
        this.fallbackSource = fallbackSource;

        renderNode = new RenderNode(null);
    }

    public void setupRenderer(RenderNodeWithHash.Renderer renderer) {
        if (renderNodeWithHash == null) {
            renderNodeWithHash = new RenderNodeWithHash(renderNode, renderer);
        }
    }

    public void updateDisplayListIfNeeded() {
        renderNodeWithHash.updateDisplayListIfNeeded();
    }

    public void setSize(int width, int height) {
        renderNode.setPosition(0, 0, width, height);
    }

    public void setScrollableNoiseSuppressor(DownscaleScrollableNoiseSuppressor scrollableNoiseSuppressor, int index) {
        this.scrollableNoiseSuppressor = scrollableNoiseSuppressor;
        this.scrollableNoiseSuppressorIndex = index;
    }

    public void setUnderSource(BlurredBackgroundSource underSource) {
        this.underSource = underSource;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void setBlur(float radius) {
        renderNode.setRenderEffect(radius > 0 ? RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) : null);
    }

    private boolean inRecording;
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
            if (fallbackSource != null) {
                fallbackSource.draw(canvas, left, top, right, bottom);
            }
            return;
        }

        if (inRecording) {
            throw new IllegalStateException();
        }

        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        if (underSource != null) {
            underSource.draw(canvas, left, top, right, bottom);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableNoiseSuppressor != null) {
            scrollableNoiseSuppressor.drawInline(canvas, scrollableNoiseSuppressorIndex);
        } else {
            canvas.drawRenderNode(renderNode);
        }

        canvas.restore();
    }

    public BlurredBackgroundSource getFallbackSource() {
        return fallbackSource;
    }

    public int getVisiblePositions(List<RectF> positions, int index, int expand) {
        int count = 0;

        for (BlurredBackgroundDrawableRenderNode d : drawables) {
            if (d.hasDisplayList() && d.getAlpha() > 0 && !d.getPaddedBounds().isEmpty()) {
                final RectF rectf;
                if (index < positions.size()) {
                    rectf = positions.get(index);
                } else {
                    rectf = new RectF();
                    positions.add(rectf);
                }
                d.getPositionRelativeSource(rectf);
                rectf.inset(-expand, -expand);

                index++;
                count++;
            }
        }

        return count;
    }

    private final ReferenceList<BlurredBackgroundDrawableRenderNode> drawables = new ReferenceList<>();

    private Runnable onDrawablesRelativePositionChangeListener;
    public void setOnDrawablesRelativePositionChangeListener(Runnable callback) {
        onDrawablesRelativePositionChangeListener = callback;
    }

    public void dispatchOnDrawablesRelativePositionChange() {
        if (onDrawablesRelativePositionChangeListener != null) {
            onDrawablesRelativePositionChangeListener.run();
        }
    }

    public void invalidateDisplayListForDrawables() {
        for (BlurredBackgroundDrawableRenderNode d : drawables) {
            d.invalidateDisplayList();
        }
    }

    @Override
    public BlurredBackgroundDrawable createDrawable() {
        BlurredBackgroundDrawableRenderNode d = new BlurredBackgroundDrawableRenderNode(this);
        drawables.add(d);
        return d;
    }
}
