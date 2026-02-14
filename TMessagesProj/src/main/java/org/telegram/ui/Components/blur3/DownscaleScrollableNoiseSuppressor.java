package org.telegram.ui.Components.blur3;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.utils.RenderNodeEffects;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;

import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.S)
public class DownscaleScrollableNoiseSuppressor {
    public final boolean isLiquidGlassEnabled;
    public final boolean allowNoiseSuppress = false;
    private final boolean simpleMode;
    private final int k;

    public DownscaleScrollableNoiseSuppressor() {
        this(true);
    }

    public DownscaleScrollableNoiseSuppressor(boolean simple) {
        isLiquidGlassEnabled = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
        simpleMode = simple;
        k = isLiquidGlassEnabled ? 1 : 8; // 1

        resultRenderNodes = new RenderNode[isLiquidGlassEnabled || !simpleMode ? 2 : 1];
        for (int a = 0; a < resultRenderNodes.length; a++) {
            resultRenderNodes[a] = new RenderNode(null);
        }
    }

    public static final int DRAW_GLASS = -2;
    public static final int DRAW_FROSTED_GLASS = -3;
    public static final int DRAW_FROSTED_GLASS_NO_SATURATION = -4;

    public void draw(Canvas canvas, int index) {
        if (!canvas.isHardwareAccelerated()) {
            throw new IllegalStateException();
        }

        if (!isLiquidGlassEnabled && simpleMode) {
            canvas.drawRenderNode(resultRenderNodes[0]);
            return;
        }

        if (index == DRAW_GLASS) {
            canvas.drawRenderNode(resultRenderNodes[isLiquidGlassEnabled ? 0 : 1]);
        } else if (index == DRAW_FROSTED_GLASS_NO_SATURATION) {
            canvas.drawRenderNode(resultRenderNodes[0]);
        } else if (index == DRAW_FROSTED_GLASS) {
            canvas.drawRenderNode(resultRenderNodes[1]);
        }
    }

    public void drawInline(Canvas canvas, int index) {
        final int a;
        if (!isLiquidGlassEnabled && simpleMode) {
            a = 0;
        } else if (index == DRAW_GLASS) {
            a = isLiquidGlassEnabled ? 0 : 1;
        } else if (index == DRAW_FROSTED_GLASS_NO_SATURATION) {
            a = 0;
        } else if (index == DRAW_FROSTED_GLASS) {
            a = 1;
        } else {
            return;
        }

        for (int b = 0; b < rectRenderNodesCount; b++) {
            final SourcePart sourcePart = rectRenderNodes.get(b);
            if (canvas.quickReject(sourcePart.position.left, sourcePart.position.top, sourcePart.position.right, sourcePart.position.bottom)) {
                // Log.i("WTF_DEBUG", "quickrejected");
                continue;
            }

            canvas.save();
            canvas.translate(sourcePart.position.left, sourcePart.position.top);
            final RenderNode rn = getRenderNode(a, b);
            canvas.drawRenderNode(rn);
            canvas.restore();
        }
    }

    public class DownscaledRenderNode {
        private final RenderNode renderNodeOriginalWithOffset = new RenderNode(null);
        private final RenderNode[] renderNodeDownsampled;
        private final RenderNode[] renderNodeRestored;
        private final boolean simpleMode;

        private int scaleX, scaleY;
        private float scrollX, scrollY;

        public DownscaledRenderNode(String name, int subeffects) {
            this(name, subeffects, false);
        }

        public DownscaledRenderNode(String name, int subeffects, boolean simpleModeNotAllowed) {
            renderNodeDownsampled = new RenderNode[1 + subeffects];
            for (int a = 0; a < (subeffects + 1); a++) {
                renderNodeDownsampled[a] = new RenderNode(name + "_down_" + subeffects);
            }
            if (subeffects > 0 || simpleModeNotAllowed) {
                renderNodeRestored = new RenderNode[1 + subeffects];
                for (int a = 0; a < (subeffects + 1); a++) {
                    renderNodeRestored[a] = new RenderNode(null);
                }
            } else {
                renderNodeRestored = renderNodeDownsampled;
            }
            simpleMode = renderNodeRestored == renderNodeDownsampled;
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

        long lastHash;

        public void invalidateRenderNodes(RenderNode renderNode) {
            final int originalWidth = renderNode.getWidth();
            final int originalHeight = renderNode.getHeight();
            final int downsampledWidth = Math.round((float) originalWidth * k / scaleX);
            final int downsampledHeight = Math.round((float) originalHeight * k / scaleY);

            final float scaleX = (float) downsampledWidth / originalWidth;
            final float scaleY = (float) downsampledHeight / originalHeight;

            final float scaleRevX = (float) originalWidth * k / downsampledWidth;
            final float scaleRevY = (float) originalHeight * k / downsampledHeight;

            Canvas canvas;

            long hash = 0;
            hash = MediaDataController.calcHash(hash, renderNode.getUniqueId());
            hash = MediaDataController.calcHash(hash, downsampledWidth);
            hash = MediaDataController.calcHash(hash, downsampledHeight);
            hash = MediaDataController.calcHash(hash, originalWidth);
            hash = MediaDataController.calcHash(hash, originalHeight);

            boolean ignoreHashCheck = !renderNodeOriginalWithOffset.hasDisplayList()
                || !renderNodeDownsampled[0].hasDisplayList();

            for (int a = 0; a < renderNodeDownsampled.length; a++) {
                ignoreHashCheck |= !renderNodeDownsampled[a].hasDisplayList();
                if (!simpleMode) {
                    ignoreHashCheck |= !renderNodeRestored[a].hasDisplayList();
                }
            }

            if (lastHash == hash && !ignoreHashCheck) {
                return;
            }
            lastHash = hash;

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

                if (simpleMode) {
                    renderNodeDownsampled[a].setScaleX(scaleRevX);
                    renderNodeDownsampled[a].setScaleY(scaleRevY);
                    renderNodeDownsampled[a].setPivotX(0);
                    renderNodeDownsampled[a].setPivotY(0);
                } else {
                    renderNodeRestored[a].setPosition(0, 0, originalWidth, originalHeight);
                    canvas = renderNodeRestored[a].beginRecording(originalWidth, originalHeight);
                    canvas.scale(scaleRevX, scaleRevY);
                    canvas.drawRenderNode(renderNodeDownsampled[a]);
                    renderNodeRestored[a].endRecording();
                }
            }
        }

        public void setScale(int scaleX, int scaleY) {
            this.scaleX = scaleX;
            this.scaleY = scaleY;
        }

        public void onScrolled(float dx, float dy) {
            scrollX = scaleX >= 2 ? ((scrollX + dx) % scaleX) : 0;
            scrollY = scaleY >= 2 ? ((scrollY + dy) % scaleY) : 0;

            if (allowNoiseSuppress) {
                renderNodeOriginalWithOffset.setTranslationX(scrollX);
                renderNodeOriginalWithOffset.setTranslationY(scrollY);

                for (RenderNode renderNode : renderNodeRestored) {
                    renderNode.setTranslationX(-scrollX);
                    renderNode.setTranslationY(-scrollY);
                }
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
    private final RenderNode[] resultRenderNodes;

    long lastHash;

    private boolean invalidateResultRenderNodes(int width, int height) {
        boolean ignoreHashCheck = false;
        long hash = 0;

        hash = MediaDataController.calcHash(hash, width);
        hash = MediaDataController.calcHash(hash, height);

        for (int a = 0; a < resultRenderNodes.length; a++) {
            RenderNode renderNode = resultRenderNodes[a];
            hash = MediaDataController.calcHash(hash, renderNode.getUniqueId());
            for (int b = 0; b < rectRenderNodesCount; b++) {
                final SourcePart sourcePart = rectRenderNodes.get(b);
                final RenderNode rn = getRenderNode(a, b);

                hash = MediaDataController.calcHash(hash, sourcePart.position.left);
                hash = MediaDataController.calcHash(hash, sourcePart.position.top);
                hash = MediaDataController.calcHash(hash, sourcePart.position.right);
                hash = MediaDataController.calcHash(hash, sourcePart.position.bottom);
                hash = MediaDataController.calcHash(hash, rn.getUniqueId());
            }
            if (!renderNode.hasDisplayList()) {
                ignoreHashCheck = true;
            }
        }

        if (hash == lastHash && !ignoreHashCheck) {
            return false;
        }

        lastHash = hash;

        for (int a = 0; a < resultRenderNodes.length; a++) {
            RenderNode renderNode = resultRenderNodes[a];
            renderNode.setPosition(0, 0, width, height);
            Canvas canvas = renderNode.beginRecording(width, height);

            for (int b = 0; b < rectRenderNodesCount; b++) {
                final SourcePart sourcePart = rectRenderNodes.get(b);
                canvas.save();
                canvas.translate(sourcePart.position.left, sourcePart.position.top);

                final RenderNode rn = getRenderNode(a, b);
                canvas.drawRenderNode(rn);
                canvas.restore();
            }

            renderNode.endRecording();
        }

        return true;
    }

    private RenderNode getRenderNode(int a, int b) {
        final SourcePart sourcePart = rectRenderNodes.get(b);
        if (isLiquidGlassEnabled && sourcePart.renderNodesForGlass != null) {
            if (a == 0) {
                return (sourcePart.renderNodesForGlass.renderNodeRestored[0]);
            } else {
                return (sourcePart.renderNodesForBlur.renderNodeRestored[0]);
            }
        } else {
            return (sourcePart.renderNodesForBlur.renderNodeRestored[Math.min(a, sourcePart.renderNodesForBlur.renderNodeRestored.length - 1)]);
        }
    }

    private final RectF tmpRectF = new RectF();

    public boolean invalidateResultRenderNodes(IBlur3Capture capture, int width, int height) {
        int updatedCount = 0;
        for (int a = 0; a < rectRenderNodesCount; a++) {
            final SourcePart sourcePart = rectRenderNodes.get(a);
            final Rect position = sourcePart.position;
            tmpRectF.set(position);

            final long hash = capture.captureCalculateHash(tmpRectF);
            if (hash != -1 && sourcePart.lastHash == hash && sourcePart.renderNode.hasDisplayList()) {
                continue;
            }

            sourcePart.lastHash = hash;

            Canvas c = beginRecordingRect(a);
            c.save();
            c.translate(-position.left, -position.top);

            capture.capture(c, tmpRectF);
            c.restore();
            endRecordingRect();

            updatedCount++;
        }

        if (updatedCount > 0) {
            return invalidateResultRenderNodes(width, height);
        }
        return false;
    }

    private class SourcePart {
        final RenderNode renderNode = new RenderNode(null);
        final DownscaledRenderNode renderNodesForBlur;
        final @Nullable DownscaledRenderNode renderNodesForGlass;
        final Rect position = new Rect();
        long lastHash;

        private SourcePart() {
            if (isLiquidGlassEnabled) {
                renderNodesForGlass = new DownscaledRenderNode("glass", 0, true);
                renderNodesForGlass.setScale(4, 4);
                renderNodesForGlass.setPrimaryEffectBlur(dpf2(1.66f), RenderNodeEffects.getSaturationX2RenderEffect());
                renderNodesForBlur = new DownscaledRenderNode("blur", 0);
                renderNodesForBlur.setScale(8, 8);
                renderNodesForBlur.setPrimaryEffectBlur(dpf2(40 - 1.66f));
            } else if (simpleMode) {
                renderNodesForBlur = new DownscaledRenderNode("blur", 0);
                renderNodesForBlur.setScale(8, 8);
                renderNodesForBlur.setPrimaryEffectBlur(dpf2(40), RenderNodeEffects.getSaturationX2RenderEffect());
                renderNodesForGlass = null;
            } else {
                renderNodesForBlur = new DownscaledRenderNode("blur", 1);
                renderNodesForBlur.setScale(8, 8);
                renderNodesForBlur.setPrimaryEffectBlur(dpf2(40));
                renderNodesForBlur.setSecondaryEffect(0, RenderNodeEffects.getSaturationX2RenderEffect());
                renderNodesForGlass = null;
            }
        }


        private void setPosition(RectF position) {
            this.position.left = roundDown(position.left, 16);
            this.position.top = roundDown(position.top, 16);
            this.position.right = roundUp(position.right, 16);
            this.position.bottom = roundUp(position.bottom, 16);
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

    private static int roundDown(float value, int N) {
        return Math.round(value - value % N);
    }

    public static int roundUp(float value, int N) {
        return Math.round(value + (N - value % N));
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
    private Rect recordingPos;

    private RecordingCanvas beginRecordingRect(int index) {
        if (recordingPos != null) {
            throw new IllegalStateException();
        }


        final SourcePart sourcePart = rectRenderNodes.get(index);
        Rect rectF = sourcePart.position;

        recordingPos = rectF;
        recordingIndex = index;

        final int width = rectF.width() / k;
        final int height = rectF.height() / k;

        sourcePart.renderNode.setPosition(0, 0, width, height);
        RecordingCanvas c = sourcePart.renderNode.beginRecording(width, height);
        c.scale(1f / k, 1f / k);
        return c;
    }

    private void endRecordingRect() {
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
