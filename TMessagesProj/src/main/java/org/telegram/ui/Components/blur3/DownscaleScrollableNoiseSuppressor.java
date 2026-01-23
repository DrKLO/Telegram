package org.telegram.ui.Components.blur3;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.S)
public class DownscaleScrollableNoiseSuppressor {
    public final boolean isLiquidGlassEnabled;
    private final RenderEffect saturationUpEffect;

    public DownscaleScrollableNoiseSuppressor() {
        isLiquidGlassEnabled = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
        resultRenderNodes[0] = new RenderNode(null);
        resultRenderNodes[1] = new RenderNode(null);
        final ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(2f);

        saturationUpEffect = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(colorMatrix));
    }

    public static final int DRAW_GLASS = -2;
    public static final int DRAW_FROSTED_GLASS = -3;
    public static final int DRAW_FROSTED_GLASS_NO_SATURATION = -4;

    public void draw(Canvas canvas, int index) {
        if (!canvas.isHardwareAccelerated()) {
            throw new IllegalStateException();
        }

        if (index == DRAW_GLASS) {
            canvas.drawRenderNode(resultRenderNodes[isLiquidGlassEnabled ? 0 : 1]);
        } else if (index == DRAW_FROSTED_GLASS_NO_SATURATION) {
            canvas.drawRenderNode(resultRenderNodes[0]);
        } else if (index == DRAW_FROSTED_GLASS) {
            canvas.drawRenderNode(resultRenderNodes[1]);
        }
    }

    public static class DownscaledRenderNode {
        private final RenderNode renderNodeOriginalWithOffset = new RenderNode(null);
        private final RenderNode[] renderNodeDownsampled;
        private final RenderNode[] renderNodeRestored;

        private int scaleX, scaleY;
        private float scrollX, scrollY;
        private float left, top;

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

        public void setPrimaryEffectBlur(float radius) {
            final float downsampledRadiusX = downscaleRadius(radius, scaleX);
            final float downsampledRadiusY = downscaleRadius(radius, scaleY);
            setPrimaryEffect(RenderEffect.createBlurEffect(
                downsampledRadiusX,
                downsampledRadiusY,
                Shader.TileMode.CLAMP
            ));
        }

        public void setPrimaryEffectBlur(float radius, RenderEffect secondEffect) {
            final float downsampledRadiusX = downscaleRadius(radius, scaleX);
            final float downsampledRadiusY = downscaleRadius(radius, scaleY);

            setPrimaryEffect(RenderEffect.createChainEffect(RenderEffect.createBlurEffect(
                downsampledRadiusX,
                downsampledRadiusY,
                Shader.TileMode.CLAMP
            ), secondEffect));
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
        return Math.max(1, convertSigmaToRadius(convertRadiusToSigma(radius) / scale));
    }


    public static final float MAX_RADIUS_FOR_FAST_BLUR = 2.595f; // convertSigmaToRadius(2) - 0.031f;


    public void onScrolled(float dx, float dy) {
        for (int a = 0; a < rectRenderNodesCount; a++) {
            final SourcePart sourcePart = rectRenderNodes.get(a);
            sourcePart.renderNodesForBlur.onScrolled(dx, dy);
            if (sourcePart.renderNodesForGlass != null) {
                sourcePart.renderNodesForGlass.onScrolled(dx, dy);
            }
        }
    }



    /**
     * Glass Mode: [0] - weak blur, saturation up.      [1] - strong blur, saturation up
     * Blur Mode:  [0] - strong blur, no color matrix.  [1] - strong blur, saturation up
     * */
    private final RenderNode[] resultRenderNodes = new RenderNode[2];

    public void invalidateResultRenderNodes(int width, int height) {
        for (int a = 0; a < 2; a++) {
            RenderNode renderNode = resultRenderNodes[a];
            renderNode.setPosition(0, 0, width, height);
            Canvas canvas = renderNode.beginRecording(width, height);

            for (int b = 0; b < rectRenderNodesCount; b++) {
                final SourcePart sourcePart = rectRenderNodes.get(b);
                canvas.save();
                canvas.translate(sourcePart.position.left, sourcePart.position.top);

                if (isLiquidGlassEnabled && sourcePart.renderNodesForGlass != null) {
                    if (a == 0) {
                        canvas.drawRenderNode(sourcePart.renderNodesForGlass.renderNodeRestored[0]);
                    } else {
                        canvas.drawRenderNode(sourcePart.renderNodesForBlur.renderNodeRestored[0]);
                    }
                } else {
                    canvas.drawRenderNode(sourcePart.renderNodesForBlur.renderNodeRestored[Math.min(a, sourcePart.renderNodesForBlur.renderNodeRestored.length)]);
                }

                canvas.restore();
            }

            renderNode.endRecording();
        }
    }









    private class SourcePart {
        final RenderNode renderNode = new RenderNode(null);
        final DownscaledRenderNode renderNodesForBlur;
        final @Nullable DownscaledRenderNode renderNodesForGlass;
        final RectF position = new RectF();

        private SourcePart() {
            renderNode.setClipToBounds(true);
            if (isLiquidGlassEnabled) {
                renderNodesForGlass = new DownscaledRenderNode(0);
                renderNodesForGlass.renderNodeDownsampled[0].setUseCompositingLayer(true, null);
                renderNodesForGlass.setScale(4, 4);
                renderNodesForGlass.setPrimaryEffectBlur(dpf2(1.66f), saturationUpEffect);
                renderNodesForBlur = new DownscaledRenderNode(0);
                renderNodesForBlur.setScale(16, 16);
                renderNodesForBlur.setPrimaryEffectBlur(dpf2(30 - 1.66f));
            } else {
                renderNodesForBlur = new DownscaledRenderNode(1);
                renderNodesForBlur.setScale(16, 16);
                renderNodesForBlur.setPrimaryEffectBlur(dpf2(30));
                renderNodesForBlur.setSecondaryEffect(0, saturationUpEffect);
                renderNodesForGlass = null;
            }
            renderNodesForBlur.renderNodeDownsampled[0].setUseCompositingLayer(true, null);
        }


        private void setPosition(RectF position) {
            this.position.left = position.left - position.left % 16;
            this.position.top = position.top - position.top % 16;
            this.position.right = position.right + (16 - position.right % 16);;
            this.position.bottom = position.bottom + (16 - position.bottom % 16);
        }


        public void invalidate() {
            if (renderNodesForGlass != null) {
                renderNodesForGlass.invalidateRenderNodes(renderNode);
                renderNodesForBlur.invalidateRenderNodes(renderNodesForGlass.renderNodeRestored[0]);
            } else {
                renderNodesForBlur.invalidateRenderNodes(renderNode);
            }
        }
    }

    private final ArrayList<SourcePart> rectRenderNodes = new ArrayList<>();
    private int rectRenderNodesCount;

    public int getRenderNodesCount() {
        return rectRenderNodesCount;
    }

    public void setupRenderNodes(List<RectF> positions, int count) {
        rectRenderNodesCount = count;

        while (rectRenderNodesCount > rectRenderNodes.size()) {
            rectRenderNodes.add(new SourcePart());
        }

        for (int a = 0; a < rectRenderNodesCount; a++) {
            rectRenderNodes.get(a).setPosition(positions.get(a));
        }
    }

    private int recordingIndex;
    private RectF recordingPos;

    public RectF getPosition(int index) {
        final SourcePart sourcePart = rectRenderNodes.get(index);
        return sourcePart.position;
    }

    public RecordingCanvas beginRecordingRect(int index) {
        if (recordingPos != null) {
            throw new IllegalStateException();
        }


        final SourcePart sourcePart = rectRenderNodes.get(index);
        RectF rectF = sourcePart.position;

        recordingPos = rectF;
        recordingIndex = index;

        final int width = (int) Math.ceil(rectF.width());
        final int height = (int) Math.ceil(rectF.height());

        sourcePart.renderNode.setPosition(0, 0, width, height);
        return sourcePart.renderNode.beginRecording(width, height);
    }

    public void endRecordingRect() {
        if (recordingPos == null) {
            throw new IllegalStateException();
        }

        final SourcePart sourcePart = rectRenderNodes.get(recordingIndex);
        sourcePart.renderNode.endRecording();
        sourcePart.invalidate();

        recordingPos = null;
    }

    public void drawDebugPositions(Canvas canvas) {
        for (int b = 0; b < rectRenderNodesCount; b++) {
            final SourcePart sourcePart = rectRenderNodes.get(b);
            canvas.drawRect(sourcePart.position, Theme.DEBUG_GREEN_STROKE);
        }
    }
}
