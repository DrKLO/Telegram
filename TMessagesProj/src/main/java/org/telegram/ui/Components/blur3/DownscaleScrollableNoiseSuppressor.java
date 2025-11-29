package org.telegram.ui.Components.blur3;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;

@RequiresApi(api = Build.VERSION_CODES.S)
public class DownscaleScrollableNoiseSuppressor {
    private final RenderNode renderNodeOriginal = new RenderNode(null);
    private final @Nullable DownscaledRenderNode renderNodesForGlass;
    private final DownscaledRenderNode renderNodesForFrostedGlass = new DownscaledRenderNode(1);

    private static Canvas recordingCanvas;
    private boolean inRecording;
    private boolean invalidateRestoredAfterRecording;

    public DownscaleScrollableNoiseSuppressor() {
        final ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(2f);

        renderNodeOriginal.setClipToBounds(true);
        renderNodeOriginal.setUseCompositingLayer(true, null);

        final int frostedDownscale;
        if (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS)) {
            final float glassRadius = downscaleRadius(dpf2(1.66f), 2);

            renderNodesForGlass = new DownscaledRenderNode(1);
            renderNodesForGlass.setScale(2, 2);
            renderNodesForGlass.renderNodeDownsampled[0].setUseCompositingLayer(true, null);
            renderNodesForGlass.setPrimaryEffect(RenderEffect.createBlurEffect(glassRadius, glassRadius, Shader.TileMode.CLAMP));
            renderNodesForGlass.setSecondaryEffect(0, RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(colorMatrix)));

            frostedDownscale = 4;
        } else {
            renderNodesForGlass = null;
            int performance = SharedConfig.getDevicePerformanceClass();
            if (performance == SharedConfig.PERFORMANCE_CLASS_HIGH) {
                frostedDownscale = 4;
            } else if (performance == SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
                frostedDownscale = 6;
            } else {
                frostedDownscale = 8;
            }
        }


        final float frostedRadius = downscaleRadius(dpf2(30 - 1.66f), frostedDownscale);
        renderNodesForFrostedGlass.setScale(frostedDownscale, frostedDownscale);
        renderNodesForFrostedGlass.renderNodeDownsampled[0].setUseCompositingLayer(true, null);
        renderNodesForFrostedGlass.setPrimaryEffect(RenderEffect.createBlurEffect(frostedRadius, frostedRadius, Shader.TileMode.CLAMP));
        renderNodesForFrostedGlass.setSecondaryEffect(0, RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(colorMatrix)));
    }

    public void onScrolled(float dx, float dy) {
        if (renderNodesForGlass != null) {
            renderNodesForGlass.onScrolled(dx, dy);
        }
        renderNodesForFrostedGlass.onScrolled(dx, dy);
    }

    public Canvas beginRecording(int width, int height) {
        if (inRecording()) {
            throw new IllegalStateException();
        }

        inRecording = true;
        invalidateRestoredAfterRecording = !renderNodeOriginal.hasDisplayList()
            || renderNodeOriginal.getWidth() != width
            || renderNodeOriginal.getHeight() != height;

        renderNodeOriginal.setPosition(0, 0, width, height);
        recordingCanvas = renderNodeOriginal.beginRecording(width, height);
        return recordingCanvas;
    }

    public void endRecording() {
        if (!inRecording()) {
            throw new IllegalStateException();
        }

        renderNodeOriginal.endRecording();
        if (invalidateRestoredAfterRecording) {
            invalidateRestoredAfterRecording = false;
            invalidateInternalRenderNodes();
        }

        recordingCanvas = null;
        inRecording = false;
    }

    public static boolean isRecordingCanvas(Canvas canvas) {
        return canvas != null && canvas == recordingCanvas;
    }

    public boolean inRecording() {
        return inRecording;
    }


    public static final int DRAW_ORIGINAL = -1;
    public static final int DRAW_GLASS = -2;
    public static final int DRAW_FROSTED_GLASS = -3;
    public static final int DRAW_FROSTED_GLASS_NO_SATURATION = -4;

    public void draw(Canvas canvas, int index) {
        if (inRecording() || !canvas.isHardwareAccelerated()) {
            throw new IllegalStateException();
        }

        if (index == DRAW_ORIGINAL) {
            canvas.drawRenderNode(renderNodeOriginal);
        } else if (index == DRAW_GLASS) {
            if (LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) && renderNodesForGlass != null) {
                canvas.drawRenderNode(renderNodesForGlass.renderNodeRestored[1]);
            } else {
                canvas.drawRenderNode(renderNodesForFrostedGlass.renderNodeRestored[1]);
            }
        } else if (index == DRAW_FROSTED_GLASS_NO_SATURATION) {
            canvas.drawRenderNode(renderNodesForFrostedGlass.renderNodeRestored[0]);
        } else if (index == DRAW_FROSTED_GLASS) {
            canvas.drawRenderNode(renderNodesForFrostedGlass.renderNodeRestored[1]);
        }
    }

    private void invalidateInternalRenderNodes() {
        if (renderNodesForGlass != null) {
            renderNodesForGlass.invalidateRenderNodes(renderNodeOriginal);
            renderNodesForFrostedGlass.invalidateRenderNodes(renderNodesForGlass.renderNodeRestored[0]);
        } else {
            renderNodesForFrostedGlass.invalidateRenderNodes(renderNodeOriginal);
        }
    }


    public static class DownscaledRenderNode {
        private final RenderNode renderNodeOriginalWithOffset = new RenderNode(null);
        private final RenderNode[] renderNodeDownsampled;
        private final RenderNode[] renderNodeRestored;

        private int scaleX, scaleY;
        private float scrollX, scrollY;

        public DownscaledRenderNode(int subeffects) {
            renderNodeDownsampled = new RenderNode[1 + subeffects];
            renderNodeRestored = new RenderNode[1 + subeffects];

            for (int a = 0; a < (subeffects + 1); a++) {
                renderNodeDownsampled[a] = new RenderNode(null);
                renderNodeDownsampled[a].setClipToBounds(true);
                renderNodeRestored[a] = new RenderNode(null);
                renderNodeRestored[a].setClipToBounds(true);
            }

            scaleX = scaleY = 1;
        }

        public void setPrimaryEffect(RenderEffect renderEffect) {
            renderNodeDownsampled[0].setRenderEffect(renderEffect);
        }

        public void setSecondaryEffect(int index, RenderEffect renderEffect) {
            renderNodeDownsampled[1 + index].setRenderEffect(renderEffect);
        }

        public void invalidateRenderNodes(RenderNode renderNode) {
            final int originalWidth = renderNode.getWidth();
            final int originalHeight = renderNode.getHeight();
            final int downsampledWidth = Math.round((float) originalWidth / scaleX);
            final int downsampledHeight = Math.round((float) originalHeight / scaleY);

            final float scaleX = (float) downsampledWidth / originalWidth;
            final float scaleY = (float) downsampledHeight / originalHeight;

            final float scaleRevX = (float) originalWidth / downsampledWidth;
            final float scaleRevY = (float) originalHeight / downsampledHeight;

            Canvas canvas;

            renderNodeOriginalWithOffset.setPosition(0, 0, originalWidth, originalHeight);
            canvas = renderNodeOriginalWithOffset.beginRecording(originalWidth, originalHeight);
            canvas.drawRenderNode(renderNode);
            renderNodeOriginalWithOffset.endRecording();

            renderNodeDownsampled[0].setPosition(0, 0, downsampledWidth, downsampledHeight);
            canvas = renderNodeDownsampled[0].beginRecording(downsampledWidth, downsampledHeight);
            canvas.scale(scaleX, scaleY);
            canvas.drawRenderNode(renderNodeOriginalWithOffset);
            renderNodeDownsampled[0].endRecording();

            for (int a = 0; a < renderNodeDownsampled.length; a++) {
                renderNodeDownsampled[a].setPosition(0, 0, downsampledWidth, downsampledHeight);
                canvas = renderNodeDownsampled[a].beginRecording(downsampledWidth, downsampledHeight);
                if (a > 0) {
                    canvas.drawRenderNode(renderNodeDownsampled[0]);
                } else {
                    canvas.scale(scaleX, scaleY);
                    canvas.drawRenderNode(renderNodeOriginalWithOffset);
                }
                renderNodeDownsampled[a].endRecording();

                renderNodeRestored[a].setPosition(0, 0, originalWidth, originalHeight);
                canvas = renderNodeRestored[a].beginRecording(originalWidth, originalHeight);
                canvas.scale(scaleRevX, scaleRevY);
                canvas.drawRenderNode(renderNodeDownsampled[a]);
                renderNodeRestored[a].endRecording();
            }
        }

        public void setScale(int scaleX, int scaleY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }

        public void onScrolled(float dx, float dy) {
            scrollX = scaleX >= 2 ? ((scrollX + dx) % scaleX) : 0;
            scrollY = scaleY >= 2 ? ((scrollY + dy) % scaleY) : 0;

            renderNodeOriginalWithOffset.setTranslationX(scrollX);
            renderNodeOriginalWithOffset.setTranslationY(scrollY);

            for (RenderNode renderNode : renderNodeRestored) {
                renderNode.setTranslationX(-scrollX);
                renderNode.setTranslationY(-scrollY);
            }
        }
    }


    // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/hwui/jni/RenderEffect.cpp;drc=61197364367c9e404c7da6900658f1b16c42d0da;l=39
    // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/libs/hwui/utils/Blur.cpp;drc=61197364367c9e404c7da6900658f1b16c42d0da;l=29

    // This constant approximates the scaling done in the software path's
    // "high quality" mode, in SkBlurMask::Blur() (1 / sqrt(3)).
    public static final float BLUR_SIGMA_SCALE = 0.57735f;

    public static float convertRadiusToSigma(float radius) {
        return radius > 0 ? BLUR_SIGMA_SCALE * radius + 0.5f : 0.0f;
    }

    public static float convertSigmaToRadius(float sigma) {
        return sigma > .5f ? (sigma - 0.5f) / BLUR_SIGMA_SCALE : 0.0f;
    }

    public static float downscaleRadius(float radius, float scale) {
        return convertSigmaToRadius(convertRadiusToSigma(radius) / scale);
    }


    public static final float MAX_RADIUS_FOR_FAST_BLUR = 2.595f; // convertSigmaToRadius(2) - 0.031f;
}
