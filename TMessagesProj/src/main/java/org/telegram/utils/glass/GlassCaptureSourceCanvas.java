package org.telegram.utils.glass;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.utils.RenderNodeEffects;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.utils.glass.positions.GlassPositionsArray;
import org.telegram.utils.glass.fade.GlassFadeBlurredDrawable;
import org.telegram.utils.glass.source.GlassSource;

import java.util.ArrayList;

@RequiresApi(api = Build.VERSION_CODES.S)
public class GlassCaptureSourceCanvas {

    public enum SourceIndex {
        BLURRED,
        GLASS,
        FROSTED_GLASS,
        /** The frosted blur without the saturation of the glass, through {@link #setFilteredColorFilter}; captured once requested. */
        FROSTED_FILTERED;

        private boolean isFrosted() {
            return this == FROSTED_GLASS || this == FROSTED_FILTERED;
        }
    }

    public interface Source extends GlassSource,
            GlassFadeBlurredDrawable.GlassFadeDownsampledSource {
    }

    private static final int GLASS_DOWNSAMPLE = 5;
    private static final ColorFilter IDENTITY_FILTER = new ColorMatrixColorFilter(new ColorMatrix());
    private static final int FROSTED_GLASS_DOWNSAMPLE = 10;

    private final ArrayList<Position> positions = new ArrayList<>();

    private int positionsCount;

    private IBlur3Capture capture;

    private View rootView;

    private final RenderNode debugRenderNode = new RenderNode("debug");
    private final Source blurredSource = new SourceImpl(SourceIndex.BLURRED);
    private final Source glassSource = new SourceImpl(SourceIndex.GLASS);
    private final Source frostedGlassSource = new SourceImpl(SourceIndex.FROSTED_GLASS);
    private final Source frostedFilteredSource = new SourceImpl(SourceIndex.FROSTED_FILTERED);

    private boolean frostedFilteredRequested;
    private @Nullable ColorFilter frostedFilteredColorFilter;


    public void setRootView(View rootView) {
        this.rootView = rootView;
    }

    public void setCapture(IBlur3Capture capture) {
        this.capture = capture;
    }

    public void setPositions(GlassPositionsArray positionsArray) {
        positionsCount = positionsArray.size();

        while (positionsCount > positions.size()) {
            positions.add(new Position(positions.size()));
        }

        for (int a = 0; a < positionsCount; a++) {
            final Position position = positions.get(a);
            position.set(positionsArray.get(a));
            position.setDownsamples(
                    glassDownsample, frostedGlassDownsample, normalizationDownsample);
            position.setFilteredColorFilter(frostedFilteredColorFilter);
            position.setOffsets(
                    glassDownsampleOffsetX,
                    glassDownsampleOffsetY,
                    frostedGlassDownsampleOffsetX,
                    frostedGlassDownsampleOffsetY);
        }
    }

    private boolean allowDownsample;
    private int glassDownsample = 1;
    private int frostedGlassDownsample = 1;
    private int normalizationDownsample = 1;

    public void setAllowDownsample(boolean allowDownsample) {
        if (this.allowDownsample == allowDownsample) {
            return;
        }
        this.allowDownsample = allowDownsample;
        glassDownsample = allowDownsample ? GLASS_DOWNSAMPLE : 1;
        frostedGlassDownsample = allowDownsample ? FROSTED_GLASS_DOWNSAMPLE : 1;
        normalizationDownsample = Math.max(glassDownsample, frostedGlassDownsample);
        for (int a = 0; a < positionsCount; a++) {
            positions.get(a).setDownsamples(
                    glassDownsample, frostedGlassDownsample, normalizationDownsample);
        }
        updateOffsets();
    }

    @NonNull
    public Source getSource(@NonNull SourceIndex index) {
        switch (index) {
            case BLURRED:
                return blurredSource;
            case GLASS:
                return glassSource;
            case FROSTED_GLASS:
                return frostedGlassSource;
            case FROSTED_FILTERED:
                frostedFilteredRequested = true;
                return frostedFilteredSource;
            default:
                throw new IllegalArgumentException("Unknown source index: " + index);
        }
    }

    /** The color filter of {@link SourceIndex#FROSTED_FILTERED}, e.g. darkening what is under a button; null for none. */
    public void setFilteredColorFilter(@Nullable ColorFilter colorFilter) {
        if (frostedFilteredColorFilter == colorFilter) {
            return;
        }
        frostedFilteredColorFilter = colorFilter;
        for (int a = 0; a < positionsCount; a++) {
            positions.get(a).setFilteredColorFilter(colorFilter);
        }
    }

    private int glassDownsampleOffsetX;
    private int glassDownsampleOffsetY;
    private int frostedGlassDownsampleOffsetX;
    private int frostedGlassDownsampleOffsetY;
    private int scrollOffsetX;
    private int scrollOffsetY;

    public void setOffset(int dx, int dy) {
        scrollOffsetX = dx;
        scrollOffsetY = dy;
        updateOffsets();
    }

    private void updateOffsets() {
        glassDownsampleOffsetX = -centeredRemainder(scrollOffsetX, glassDownsample);
        glassDownsampleOffsetY = -centeredRemainder(scrollOffsetY, glassDownsample);
        frostedGlassDownsampleOffsetX = -centeredRemainder(
                scrollOffsetX, frostedGlassDownsample);
        frostedGlassDownsampleOffsetY = -centeredRemainder(
                scrollOffsetY, frostedGlassDownsample);
        for (int a = 0; a < positionsCount; a++) {
            positions.get(a).setOffsets(
                    glassDownsampleOffsetX,
                    glassDownsampleOffsetY,
                    frostedGlassDownsampleOffsetX,
                    frostedGlassDownsampleOffsetY);
        }
    }

    public void invalidate() {
        for (int a = 0; a < positionsCount; a++) {
            final Position position = positions.get(a);
            position.capture(capture, frostedFilteredRequested, rootView.getWidth(), rootView.getHeight());
        }

        debugRenderNode.setPosition(0, 0, rootView.getWidth(), rootView.getHeight());
        final RecordingCanvas recordingCanvas = debugRenderNode.beginRecording();
        for (int a = 0; a < positionsCount; a++) {
            final Position position = positions.get(a);
            recordingCanvas.save();
            recordingCanvas.clipRect(position.originalPosition);
            recordingCanvas.translate(
                    position.normalizedPositionGlassWithOffset.left,
                    position.normalizedPositionGlassWithOffset.top);
            recordingCanvas.scale(position.glassDownsample, position.glassDownsample);
            recordingCanvas.drawRenderNode(position.renderNode);
            recordingCanvas.restore();
            recordingCanvas.drawRect(position.originalPosition, Theme.DEBUG_GREEN_STROKE);
            recordingCanvas.drawRect(
                    position.normalizedPositionGlassWithOffset, Theme.DEBUG_RED_STROKE);
        }
        debugRenderNode.endRecording();
    }




    private void drawInline(
            @NonNull Canvas canvas,
            @NonNull SourceIndex index,
            float left,
            float top,
            float right,
            float bottom
    ) {
        for (int a = 0; a < positionsCount; a++) {
            final Position position = positions.get(a);
            if (position.originalPosition.intersects(left, top, right, bottom)) {
                final RectF normalizedPositionWithOffset =
                        position.getNormalizedPositionWithOffset(index);
                final int downsample = position.getDownsample(index);
                final RenderNode renderNode = position.getRenderNode(index);

                canvas.save();
                canvas.clipRect(position.originalPosition);
                canvas.translate(
                        normalizedPositionWithOffset.left,
                        normalizedPositionWithOffset.top);
                canvas.scale(downsample, downsample);
                canvas.drawRenderNode(renderNode);
                canvas.restore();
            }
        }
    }

    private void drawDownsampled(
            @NonNull SourceIndex index,
            @NonNull Canvas canvas,
            @NonNull RectF boundsRelativeRoot,
            int targetDownsample,
            float targetOriginX,
            float targetOriginY
    ) {
        for (int a = 0; a < positionsCount; a++) {
            final Position position = positions.get(a);
            if (!RectF.intersects(position.originalPosition, boundsRelativeRoot)) {
                continue;
            }

            final RectF normalizedPositionWithOffset =
                    position.getNormalizedPositionWithOffset(index);
            final int sourceDownsample = position.getDownsample(index);

            canvas.save();
            // a node goes far beyond its position by the padding of the blur, not over the glass next to it;
            // what draws it is on the grid with a padding of its own, up to two pixels of the target
            final float outset = targetDownsample * 2;
            canvas.clipRect(
                    (position.originalPosition.left - outset - targetOriginX) / targetDownsample,
                    (position.originalPosition.top - outset - targetOriginY) / targetDownsample,
                    (position.originalPosition.right + outset - targetOriginX) / targetDownsample,
                    (position.originalPosition.bottom + outset - targetOriginY) / targetDownsample);
            canvas.translate(
                    (normalizedPositionWithOffset.left - targetOriginX) / targetDownsample,
                    (normalizedPositionWithOffset.top - targetOriginY) / targetDownsample
            );

            if (sourceDownsample != targetDownsample) {
                final float scale = sourceDownsample / (float) targetDownsample;
                canvas.scale(scale, scale);
            }

            canvas.drawRenderNode(position.getRenderNode(index));
            canvas.restore();
        }
    }

    private final class SourceImpl implements Source {
        @NonNull private final SourceIndex index;

        private SourceImpl(@NonNull SourceIndex index) {
            this.index = index;
        }

        @Override
        public void draw(
                @NonNull Canvas canvas,
                float left,
                float top,
                float right,
                float bottom
        ) {
            drawInline(canvas, index, left, top, right, bottom);
        }

        @Override
        public boolean isDownsampledSourceSupported() {
            return true;
        }

        @Override
        public int getDownsample() {
            return index.isFrosted()
                    ? frostedGlassDownsample : glassDownsample;
        }

        @Override
        public int getDownsampleOffsetX() {
            return index.isFrosted()
                    ? frostedGlassDownsampleOffsetX : glassDownsampleOffsetX;
        }

        @Override
        public int getDownsampleOffsetY() {
            return index.isFrosted()
                    ? frostedGlassDownsampleOffsetY : glassDownsampleOffsetY;
        }

        @Override
        public void drawDownsampled(
                @NonNull Canvas canvas,
                @NonNull RectF boundsRelativeRoot,
                int targetDownsample,
                float targetOriginX,
                float targetOriginY
        ) {
            GlassCaptureSourceCanvas.this.drawDownsampled(
                    index,
                    canvas,
                    boundsRelativeRoot,
                    targetDownsample,
                    targetOriginX,
                    targetOriginY);
        }
    }



    public View createDebugView(Context context) {
        return new GlassCaptureDebugView(context, debugRenderNode);
    }

    private static class Position {
        public final RectF originalPosition = new RectF();

        public final Rect normalizedPosition = new Rect();

        public final RectF normalizedPositionGlassWithOffset = new RectF();
        /** Within the root: nothing is captured past it, and the heavy blur would pull that transparency in. */
        public final RectF normalizedPositionFrostedGlassWithOffset = new RectF();

        public final RenderNode renderNode;
        public final RenderNode renderNodeGlass;
        public final RenderNode renderNodeGlassFrosted;
        /**
         * The capture through the color filter, and its frosted blur over it: as the frosted glass, the blur is of a node
         * with an effect of its own. A blur of the capture node itself was lost (the filter of the same node was not).
         */
        public final RenderNode renderNodeFiltered;
        public final RenderNode renderNodeFrostedFiltered;
        private @Nullable ColorFilter filteredColorFilter;

        public int glassWidth;
        public int glassHeight;
        public int frostedGlassWidth;
        public int frostedGlassHeight;

        public int glassDownsample;
        public int frostedGlassDownsample;
        public int normalizationDownsample = 1;

        public Position(int index) {
            renderNode = new RenderNode("cap-" + index);
            renderNode.setUseCompositingLayer(true, null);
            renderNodeGlass = new RenderNode("glass-" + index);
            renderNodeGlass.setRenderEffect(RenderNodeEffects.getSaturationX3RenderEffect());
            renderNodeGlassFrosted = new RenderNode("glass-frosted-" + index);
            renderNodeFiltered = new RenderNode("filtered-" + index);
            renderNodeFiltered.setRenderEffect(RenderEffect.createColorFilterEffect(IDENTITY_FILTER));
            renderNodeFrostedFiltered = new RenderNode("frosted-filtered-" + index);
        }

        @NonNull
        public RectF getNormalizedPositionWithOffset(@NonNull SourceIndex index) {
            return index.isFrosted()
                    ? normalizedPositionFrostedGlassWithOffset
                    : normalizedPositionGlassWithOffset;
        }

        public int getDownsample(@NonNull SourceIndex index) {
            return index.isFrosted()
                    ? frostedGlassDownsample : glassDownsample;
        }

        @NonNull
        public RenderNode getRenderNode(@NonNull SourceIndex index) {
            switch (index) {
                case BLURRED:
                    return renderNode;
                case GLASS:
                    return renderNodeGlass;
                case FROSTED_GLASS:
                    return renderNodeGlassFrosted;
                case FROSTED_FILTERED:
                    return renderNodeFrostedFiltered;
                default:
                    throw new IllegalArgumentException("Unknown source index: " + index);
            }
        }

        private final RectF paddedPosition = new RectF();

        private float glassRadius;
        private float frostedGlassRadius;

        private int glassOffsetX, glassOffsetY;
        private int frostedGlassOffsetX, frostedGlassOffsetY;

        public void setDownsamples(
                int glassDownsample,
                int frostedGlassDownsample,
                int normalizationDownsample
        ) {
            if (this.glassDownsample != glassDownsample) {
                this.glassDownsample = glassDownsample;
                glassRadius = blurRadiusForDownsample(dpf2(7), glassDownsample);
                renderNode.setRenderEffect(RenderEffect.createBlurEffect(
                        glassRadius, glassRadius, Shader.TileMode.CLAMP));
            }
            if (this.frostedGlassDownsample != frostedGlassDownsample) {
                this.frostedGlassDownsample = frostedGlassDownsample;
                frostedGlassRadius = blurRadiusForDownsample(
                        dpf2(36), frostedGlassDownsample);
                renderNodeGlassFrosted.setRenderEffect(RenderEffect.createBlurEffect(
                        frostedGlassRadius, frostedGlassRadius, Shader.TileMode.CLAMP));
                renderNodeFrostedFiltered.setRenderEffect(RenderEffect.createBlurEffect(
                        frostedGlassRadius, frostedGlassRadius, Shader.TileMode.CLAMP));
            }
            this.normalizationDownsample = normalizationDownsample;
        }

        public void setFilteredColorFilter(@Nullable ColorFilter colorFilter) {
            if (filteredColorFilter != colorFilter) {
                filteredColorFilter = colorFilter;
                // the node keeps an effect without a filter too, the blur over it stays of the same structure
                renderNodeFiltered.setRenderEffect(RenderEffect.createColorFilterEffect(
                        colorFilter != null ? colorFilter : IDENTITY_FILTER));
            }
        }

        public void setOffsets(
                int glassOffsetX,
                int glassOffsetY,
                int frostedGlassOffsetX,
                int frostedGlassOffsetY
        ) {
            this.glassOffsetX = glassOffsetX;
            this.glassOffsetY = glassOffsetY;
            this.frostedGlassOffsetX = frostedGlassOffsetX;
            this.frostedGlassOffsetY = frostedGlassOffsetY;
        }

        public void set(RectF position) {
            originalPosition.set(position);
        }

        public void capture(IBlur3Capture capture, boolean frostedFiltered, int rootWidth, int rootHeight) {
            final int padding = blurPadding(glassRadius, glassDownsample)
                    + blurPadding(frostedGlassRadius, frostedGlassDownsample)
                    + normalizationDownsample / 2 + 1;
            paddedPosition.set(originalPosition);
            paddedPosition.inset(-padding, -padding);
            normalizePosition(
                    paddedPosition, normalizedPosition, normalizationDownsample);

            normalizedPositionGlassWithOffset.set(normalizedPosition);
            normalizedPositionGlassWithOffset.offset(glassOffsetX, glassOffsetY);
            normalizedPositionFrostedGlassWithOffset.set(normalizedPosition);
            normalizedPositionFrostedGlassWithOffset.offset(
                    frostedGlassOffsetX, frostedGlassOffsetY);
            clipToRoot(normalizedPositionFrostedGlassWithOffset, frostedGlassDownsample, rootWidth, rootHeight);

            glassWidth = normalizedPosition.width() / glassDownsample;
            glassHeight = normalizedPosition.height() / glassDownsample;
            frostedGlassWidth = Math.max(1, Math.round(normalizedPositionFrostedGlassWithOffset.width() / frostedGlassDownsample));
            frostedGlassHeight = Math.max(1, Math.round(normalizedPositionFrostedGlassWithOffset.height() / frostedGlassDownsample));

            renderNode.setPosition(0, 0, glassWidth, glassHeight);
            RecordingCanvas recordingCanvas = renderNode.beginRecording();
            recordingCanvas.save();
            recordingCanvas.scale(
                    1f / glassDownsample, 1f / glassDownsample, 0f, 0f);
            recordingCanvas.translate(
                    -normalizedPositionGlassWithOffset.left,
                    -normalizedPositionGlassWithOffset.top);
            capture.capture(recordingCanvas, normalizedPositionGlassWithOffset);
            recordingCanvas.restore();
            renderNode.endRecording();

            renderNodeGlass.setPosition(0, 0, glassWidth, glassHeight);
            recordingCanvas = renderNodeGlass.beginRecording();
            recordingCanvas.drawRenderNode(renderNode);
            renderNodeGlass.endRecording();

            renderNodeGlassFrosted.setPosition(
                    0, 0, frostedGlassWidth, frostedGlassHeight);
            recordingCanvas = renderNodeGlassFrosted.beginRecording();
            recordingCanvas.translate(
                    (normalizedPositionGlassWithOffset.left
                            - normalizedPositionFrostedGlassWithOffset.left)
                            / frostedGlassDownsample,
                    (normalizedPositionGlassWithOffset.top
                            - normalizedPositionFrostedGlassWithOffset.top)
                            / frostedGlassDownsample);
            if (glassDownsample != frostedGlassDownsample) {
                final float scale = glassDownsample / (float) frostedGlassDownsample;
                recordingCanvas.scale(scale, scale);
            }
            recordingCanvas.drawRenderNode(renderNodeGlass);
            renderNodeGlassFrosted.endRecording();

            if (frostedFiltered) {
                renderNodeFiltered.setPosition(0, 0, glassWidth, glassHeight);
                recordingCanvas = renderNodeFiltered.beginRecording();
                recordingCanvas.drawRenderNode(renderNode);
                renderNodeFiltered.endRecording();

                renderNodeFrostedFiltered.setPosition(
                        0, 0, frostedGlassWidth, frostedGlassHeight);
                recordingCanvas = renderNodeFrostedFiltered.beginRecording();
                recordingCanvas.translate(
                        (normalizedPositionGlassWithOffset.left
                                - normalizedPositionFrostedGlassWithOffset.left)
                                / frostedGlassDownsample,
                        (normalizedPositionGlassWithOffset.top
                                - normalizedPositionFrostedGlassWithOffset.top)
                                / frostedGlassDownsample);
                if (glassDownsample != frostedGlassDownsample) {
                    final float scale = glassDownsample / (float) frostedGlassDownsample;
                    recordingCanvas.scale(scale, scale);
                }
                recordingCanvas.drawRenderNode(renderNodeFiltered);
                renderNodeFrostedFiltered.endRecording();
            }
        }

        /** Shrinks the rect into the root by whole downsampled pixels, so it stays on the downsample grid. */
        private static void clipToRoot(RectF rect, int downsample, int rootWidth, int rootHeight) {
            if (rect.left < 0) {
                rect.left += (float) Math.ceil(-rect.left / downsample) * downsample;
            }
            if (rect.top < 0) {
                rect.top += (float) Math.ceil(-rect.top / downsample) * downsample;
            }
            if (rootWidth > 0 && rect.right > rootWidth) {
                rect.right -= (float) Math.ceil((rect.right - rootWidth) / downsample) * downsample;
            }
            if (rootHeight > 0 && rect.bottom > rootHeight) {
                rect.bottom -= (float) Math.ceil((rect.bottom - rootHeight) / downsample) * downsample;
            }
        }
    }

    private static int blurPadding(float effectRadius, int downsample) {
        if (effectRadius <= 0f) {
            return 0;
        }

        float sigma = 0.57735f * effectRadius + 0.5f;
        int kernelRadius = (int) Math.ceil(3f * sigma);
        return kernelRadius * downsample;
    }

    private static void normalizePosition(RectF pos, Rect out, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }

        out.left   = (int) Math.floor(pos.left   / n) * n;
        out.top    = (int) Math.floor(pos.top    / n) * n;
        out.right  = (int) Math.ceil(pos.right  / n) * n;
        out.bottom = (int) Math.ceil(pos.bottom / n) * n;
    }

    private static int centeredRemainder(int value, int downsample) {
        int remainder = value % downsample;
        if (remainder > downsample / 2) {
            remainder -= downsample;
        } else if (remainder < -downsample / 2) {
            remainder += downsample;
        }
        return remainder;
    }

    private static float blurRadiusForDownsample(float radius, int downsample) {
        if (radius <= 0f) {
            return 0f;
        }

        float sigma = (0.57735f * radius + 0.5f) / downsample;
        return Math.max(0f, (sigma - 0.5f) / 0.57735f);
    }

    private final static class GlassCaptureDebugView extends View {
        private final RenderNode renderNode;

        public GlassCaptureDebugView(Context context, RenderNode renderNode) {
            super(context);
            this.renderNode = renderNode;
        }

        @Override
        protected void onDraw(@NonNull Canvas recordingCanvas) {
            super.onDraw(recordingCanvas);
            if (recordingCanvas.isHardwareAccelerated()) {
                recordingCanvas.drawRenderNode(renderNode);
            }
        }
    }
}
