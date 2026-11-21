package org.telegram.utils.glass.source;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.RenderNodeWithHash;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.utils.glass.GlassCaptureSourceCanvas;
import org.telegram.utils.glass.fade.GlassFadeBlurredDrawable;

import me.vkryl.core.reference.ReferenceList;

@RequiresApi(api = Build.VERSION_CODES.Q)
public final class GlassSourceRenderNode implements GlassSource, BlurredBackgroundDrawableViewFactory.DrawableOwner, GlassFadeBlurredDrawable.GlassFadeDownsampledSource {
    private final GlassSource fallbackSource;
    private final RenderNode renderNode;
    private RenderNodeWithHash renderNodeWithHash;

    private DownscaleScrollableNoiseSuppressor scrollableNoiseSuppressor;
    private int scrollableNoiseSuppressorIndex;
    public GlassSource underSource;

    private GlassSource glassCaptureSourceCanvas;

    private boolean noClip;

    public GlassSourceRenderNode(GlassSource fallbackSource) {
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

    public void invalidateRenderer() {
        renderNodeWithHash.invalidate();
    }

    public void setSize(int width, int height) {
        renderNode.setPosition(0, 0, width, height);
    }

    public void setScrollableNoiseSuppressor(DownscaleScrollableNoiseSuppressor scrollableNoiseSuppressor, int index) {
        this.scrollableNoiseSuppressor = scrollableNoiseSuppressor;
        this.scrollableNoiseSuppressorIndex = index;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void setGlassCaptureSourceCanvas(GlassCaptureSourceCanvas glassCaptureSourceCanvas, GlassCaptureSourceCanvas.SourceIndex sourceIndex) {
        this.glassCaptureSourceCanvas = glassCaptureSourceCanvas.getSource(sourceIndex);
    }

    public void setUnderSource(GlassSource underSource) {
        this.underSource = underSource;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void setBlur(float radius) {
        renderNode.setRenderEffect(radius > 0 ? RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP) : null);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public void setBlur(float radius, RenderEffect effect) {
        renderNode.setRenderEffect(RenderEffect.createChainEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP), effect));
    }



    public void noClip() {
        this.noClip = true;
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
    public void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom) {
        if (!canvas.isHardwareAccelerated()) {
            if (fallbackSource != null) {
                fallbackSource.draw(canvas, left, top, right, bottom);
            }
            return;
        }

        if (inRecording) {
            throw new IllegalStateException();
        }

        if (underSource != null) {
            underSource.draw(canvas, left, top, right, bottom);
        }
        canvas.save();
        if (!noClip) {
            canvas.clipRect(left, top, right, bottom);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && glassCaptureSourceCanvas != null) {
            glassCaptureSourceCanvas.draw(canvas, left, top, right, bottom);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableNoiseSuppressor != null) {
            scrollableNoiseSuppressor.drawInline(canvas, scrollableNoiseSuppressorIndex);
        } else {
            canvas.drawRenderNode(renderNode);
        }

        canvas.restore();
    }

    /* Downsampled, for the lens */

    @Nullable
    private GlassFadeBlurredDrawable.GlassFadeDownsampledSource getDownsampledCapture() {
        return glassCaptureSourceCanvas instanceof GlassFadeBlurredDrawable.GlassFadeDownsampledSource
            ? (GlassFadeBlurredDrawable.GlassFadeDownsampledSource) glassCaptureSourceCanvas : null;
    }

    @Override
    public boolean isDownsampledSourceSupported() {
        return getDownsample() > 1;
    }

    @Override
    public int getDownsample() {
        final GlassFadeBlurredDrawable.GlassFadeDownsampledSource capture = getDownsampledCapture();
        if (capture != null) {
            return capture.getDownsample();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableNoiseSuppressor != null) {
            return scrollableNoiseSuppressor.getDownsample(scrollableNoiseSuppressorIndex);
        }
        return 1;
    }

    @Override
    public int getDownsampleOffsetX() {
        final GlassFadeBlurredDrawable.GlassFadeDownsampledSource capture = getDownsampledCapture();
        return capture != null ? capture.getDownsampleOffsetX() : 0;
    }

    @Override
    public int getDownsampleOffsetY() {
        final GlassFadeBlurredDrawable.GlassFadeDownsampledSource capture = getDownsampledCapture();
        return capture != null ? capture.getDownsampleOffsetY() : 0;
    }

    @Override
    public void drawDownsampled(@NonNull Canvas canvas, @NonNull RectF boundsRelativeRoot, int targetDownsample, float targetOriginX, float targetOriginY) {
        final GlassFadeBlurredDrawable.GlassFadeDownsampledSource capture = getDownsampledCapture();
        if (underSource != null || capture == null) {
            canvas.save();
            canvas.scale(1f / targetDownsample, 1f / targetDownsample);
            canvas.translate(-targetOriginX, -targetOriginY);
            if (underSource != null) {
                // the node goes beyond the bounds by its padding
                final int outset = targetDownsample * 2;
                underSource.draw(canvas, boundsRelativeRoot.left - outset, boundsRelativeRoot.top - outset, boundsRelativeRoot.right + outset, boundsRelativeRoot.bottom + outset);
            }
            if (capture == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableNoiseSuppressor != null) {
                scrollableNoiseSuppressor.drawInline(canvas, scrollableNoiseSuppressorIndex);
            }
            canvas.restore();
        }
        if (capture != null) {
            capture.drawDownsampled(canvas, boundsRelativeRoot, targetDownsample, targetOriginX, targetOriginY);
        }
    }

    public GlassSource getFallbackSource() {
        return fallbackSource;
    }

    private final ReferenceList<BlurredBackgroundDrawable> drawables = new ReferenceList<>();

    /** Re-records the drawables of this source, e.g. when what is behind them changed without a scroll (wallpaper, theme). */
    public void invalidateDisplayListForDrawables() {
        for (BlurredBackgroundDrawable d : drawables) {
            d.invalidateDisplayList();
        }
    }

    /** A glass drawable over this source: it draws the node itself, so it can show the liquid glass effect. */
    @Override
    public BlurredBackgroundDrawable createDrawable() {
        final BlurredBackgroundDrawable d = new BlurredBackgroundDrawable(this);
        attachDrawable(d);
        return d;
    }

    @Override
    public void attachDrawable(BlurredBackgroundDrawable drawable) {
        drawables.add(drawable);
    }
}
