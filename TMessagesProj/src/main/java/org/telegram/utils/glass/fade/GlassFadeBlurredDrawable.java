package org.telegram.utils.glass.fade;

import android.graphics.Canvas;
import android.graphics.BlendMode;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.intellij.lang.annotations.Language;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.utils.GradientProtectionDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.GlassDrawable;
import org.telegram.utils.glass.GlassSettings;
import org.telegram.utils.glass.source.GlassSource;
import org.telegram.utils.glass.source.GlassSourceEmpty;

import java.util.Arrays;


public class GlassFadeBlurredDrawable extends GlassDrawable {
    private static final int LEGACY_DOWNSAMPLE = 5;
    private static final int DOWNSAMPLED_NODE_PADDING = 1;

    private enum SourceType {
        DEFAULT,
        OVERLAY_ONLY
    }

    public interface GlassFadeDownsampledSource {
        boolean isDownsampledSourceSupported();
        int getDownsample();
        int getDownsampleOffsetX();
        int getDownsampleOffsetY();
        void drawDownsampled(@NonNull Canvas canvas, @NonNull RectF boundsRelativeRoot,
                             int targetDownsample, float targetOriginX, float targetOriginY);
    }

    private final GlassSource source;
    @Nullable private final GlassFadeDownsampledSource downsampledSource;
    private boolean inefficientRenderPathsAllowed;
    private final Impl blurredImpl;
    private final Impl overlayOnlyImpl;
    private Impl impl;

    private boolean shaderDirty = true;
    private boolean overlayOnlyDirty = true;

    private GlassFadeDirection direction = GlassFadeDirection.TOP;

    @NonNull private GlassFadeRangeParams maskRange = GlassFadeRangeParams.FILL;
    @NonNull private GlassFadeRangeParams overlayRange = GlassFadeRangeParams.FILL;
    @NonNull private GlassFadeRangeParams overlayOnlyRange = GlassFadeRangeParams.FILL;

    public GlassFadeBlurredDrawable(@NonNull GlassSource source) {
        this.source = source;
        downsampledSource = source instanceof GlassFadeDownsampledSource
                ? (GlassFadeDownsampledSource) source : null;

        final SourceType sourceType = resolveSourceType(source);
        if (sourceType == SourceType.OVERLAY_ONLY) {
            final Impl overlayImpl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? new ImplOverlayOnly() : new ImplLegacy(false);
            blurredImpl = overlayImpl;
            overlayOnlyImpl = overlayImpl;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            blurredImpl = new ImplSdk33();
            overlayOnlyImpl = new ImplOverlayOnly();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurredImpl = new ImplSdk31();
            overlayOnlyImpl = new ImplOverlayOnly();
        } else {
            blurredImpl = new ImplLegacy(true);
            overlayOnlyImpl = new ImplLegacy(false);
        }
        onGlassSettingsChanged(getGlassSettings());
    }

    private static SourceType resolveSourceType(@NonNull GlassSource source) {
        if (source instanceof GlassSourceEmpty) {
            return SourceType.OVERLAY_ONLY;
        }
        return SourceType.DEFAULT;
    }

    public void setInefficientRenderPathsAllowed(boolean allowed) {
        if (inefficientRenderPathsAllowed != allowed) {
            inefficientRenderPathsAllowed = allowed;
            shaderDirty = true;
            invalidateSelf();
        }
    }

    public void setDirection(@NonNull GlassFadeDirection direction) {
        if (this.direction != direction) {
            this.direction = direction;
            shaderDirty = true;
            overlayOnlyDirty = true;
            invalidateSelf();
        }
    }

    public void setMaskRange(@NonNull GlassFadeRangeParams range) {
        maskRange = range;
        shaderDirty = true;
        invalidateSelf();
    }

    public void setOverlayRange(@NonNull GlassFadeRangeParams range) {
        overlayRange = range;
        shaderDirty = true;
        invalidateSelf();
    }

    public void setOverlayOnlyRange(@NonNull GlassFadeRangeParams range) {
        overlayOnlyRange = range;
        overlayOnlyDirty = true;
        invalidateSelf();
    }

    private GlassSourceColor overlaySourceColor;
    private int overlayColor;
    private int overlayAlpha = 255;
    private int overlayOnlyColor;
    private int overlayOnlyAlpha = 255;

    public void setOverlayColorSource(GlassSourceColor sourceColor) {
        overlaySourceColor = sourceColor;
        shaderDirty = true;
        updateColors();
        invalidateSelf();
    }

    public void setOverlayAlpha(int overlayAlpha) {
        this.overlayAlpha = overlayAlpha;
        if (overlaySourceColor != null) {
            overlayColor = Theme.multAlpha(overlaySourceColor.getColor(), overlayAlpha / 255f);
        }
        shaderDirty = true;
        invalidateSelf();
    }

    public void setOverlayOnlyAlpha(int overlayOnlyAlpha) {
        this.overlayOnlyAlpha = overlayOnlyAlpha;
        if (overlaySourceColor != null) {
            overlayOnlyColor = Theme.multAlpha(
                    overlaySourceColor.getColor(), overlayOnlyAlpha / 255f);
        }
        overlayOnlyDirty = true;
        invalidateSelf();
    }



    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty() || getAlpha() <= 0) {
            return;
        }

        canvas.save();
        canvas.clipRect(bounds);
        canvas.translate(bounds.left, bounds.top);
        if (canvas.isHardwareAccelerated() || impl.supportsSoftwareCanvas()) {
            impl.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    protected void onAlphaChanged(int oldAlpha, int alpha) {
        impl.setAlpha(alpha / 255f);
        invalidateSelf();
    }

    @Override
    protected void onGlassSettingsChanged(GlassSettings glassSettings) {
        final Impl newImpl = glassSettings.isBlurEnabled ? blurredImpl : overlayOnlyImpl;
        if (impl != newImpl) {
            if (impl != null) {
                impl.discard();
            }
            impl = newImpl;
            impl.setAlpha(getAlpha() / 255f);
            if (impl == overlayOnlyImpl) {
                overlayOnlyDirty = true;
            } else {
                shaderDirty = true;
            }
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        shaderDirty = true;
        overlayOnlyDirty = true;
    }

    @Override
    public void updateColors() {
        if (overlaySourceColor != null) {
            overlayColor = Theme.multAlpha(overlaySourceColor.getColor(), overlayAlpha / 255f);
            overlayOnlyColor = Theme.multAlpha(
                    overlaySourceColor.getColor(), overlayOnlyAlpha / 255f);
        }
        shaderDirty = true;
        overlayOnlyDirty = true;
    }

    private final RectF boundsRelativeRoot = new RectF();

    @Override
    public void buildDisplayList() {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            impl.discard();
            return;
        }

        boundsRelativeRoot.set(bounds);
        boundsRelativeRoot.offset(getSourceOffsetX(), getSourceOffsetY());
        impl.buildDisplayList(bounds, boundsRelativeRoot);
    }

    private LinearGradient createGradient(@Nullable GlassFadeRangeParams range, float axisLength,
                                          float marginEnd, int color, int downsample) {
        final int marginStart = range == null ? 0 : range.marginStart;
        final int requestedHeight = range == null ? -1 : range.height;
        final GlassFadeRangeParams.Gravity gravity = range == null
                ? GlassFadeRangeParams.Gravity.START : range.gravity;

        final float available = Math.max(0f, axisLength - marginStart - marginEnd);
        final float length = requestedHeight == -1
                ? available : Math.min(requestedHeight, available);
        final float remaining = available - length;
        final float offset = gravity == GlassFadeRangeParams.Gravity.END ? remaining
                : gravity == GlassFadeRangeParams.Gravity.CENTER ? remaining * 0.5f : 0f;

        final float scale = 1f / downsample;
        final boolean reverse = direction == GlassFadeDirection.TOP || direction == GlassFadeDirection.LEFT;
        final float start = ((reverse ? axisLength - marginStart : marginStart)
                + (reverse ? -offset : offset)) * scale;
        final float end = start + (reverse ? -1f : 1f) * Math.max(length * scale, 0.001f);

        final int[] colors = new int[8];
        GradientProtectionDrawable.fillColors(GradientProtectionDrawable.DEFAULT_INTERPOLATOR, color, colors);

        if (direction == GlassFadeDirection.TOP || direction == GlassFadeDirection.BOTTOM) {
            return new LinearGradient(0f, end, 0f, start,
                    colors, null, Shader.TileMode.CLAMP);
        }
        return new LinearGradient(end, 0f, start, 0f,
                colors, null, Shader.TileMode.CLAMP);
    }

    public static float alignDownsampleOrigin(float value, int phase, int downsample) {
        return (float) Math.floor((value - phase) / downsample) * downsample + phase;
    }

    private boolean canDrawDownsampled() {
        final boolean supported = downsampledSource != null
                && downsampledSource.isDownsampledSourceSupported()
                && downsampledSource.getDownsample() > 0;
        if (!supported && !inefficientRenderPathsAllowed && BuildConfig.DEBUG_PRIVATE_VERSION) {
            throw new IllegalStateException(
                    "GlassSource fallback is disabled because it uses an inefficient render path");
        }
        return supported;
    }

    @Override
    public boolean hasDisplayList() {
        return impl.hasDisplayList();
    }

    @Override
    public boolean shouldHaveDisplayList() {
        return impl.shouldHaveDisplayList();
    }

    @Override
    public boolean isInvalidated() {
        return impl == overlayOnlyImpl ? overlayOnlyDirty : shaderDirty;
    }



    private abstract static class Impl {
        abstract void setAlpha(float alpha);
        abstract void draw(Canvas canvas);
        abstract void buildDisplayList(Rect bounds, RectF boundsRelativeRoot);
        abstract void discard();
        abstract boolean hasDisplayList();

        boolean shouldHaveDisplayList() {
            return true;
        }

        boolean supportsSoftwareCanvas() {
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private final class ImplOverlayOnly extends Impl {
        private final RenderNode overlayNode = new RenderNode("glass-fade-overlay-only");
        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        void setAlpha(float alpha) {
            overlayNode.setAlpha(alpha);
        }

        @Override
        void draw(Canvas canvas) {
            canvas.drawRenderNode(overlayNode);
        }

        @Override
        void buildDisplayList(Rect bounds, RectF boundsRelativeRoot) {
            if (!overlayOnlyDirty && overlayNode.hasDisplayList()) {
                return;
            }
            final int width = bounds.width();
            final int height = bounds.height();
            final float axisLength = direction == GlassFadeDirection.TOP
                    || direction == GlassFadeDirection.BOTTOM ? height : width;

            gradientPaint.setShader(createGradient(overlayOnlyRange, axisLength,
                    overlayOnlyRange.marginEnd, overlayOnlyColor, 1));
            overlayNode.setPosition(0, 0, width, height);
            final RecordingCanvas canvas = overlayNode.beginRecording();
            canvas.drawRect(0f, 0f, width, height, gradientPaint);
            overlayNode.endRecording();
            gradientPaint.setShader(null);
            overlayOnlyDirty = false;
        }

        @Override
        void discard() {
            overlayNode.discardDisplayList();
        }

        @Override
        boolean hasDisplayList() {
            return overlayNode.hasDisplayList();
        }
    }

    private final class ImplLegacy extends Impl {
        private final boolean drawContent;
        private final Paint layerPaint = new Paint();
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private int shaderWidth = -1;
        private int shaderHeight = -1;

        ImplLegacy(boolean drawContent) {
            this.drawContent = drawContent;
            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        @Override
        void setAlpha(float alpha) {
            layerPaint.setAlpha(Math.round(alpha * 255f));
        }

        @Override
        void draw(Canvas canvas) {
            final Rect bounds = getBounds();
            final int width = bounds.width();
            final int height = bounds.height();
            final int layerWidth = (int) Math.ceil(width / (float) LEGACY_DOWNSAMPLE);
            final int layerHeight = (int) Math.ceil(height / (float) LEGACY_DOWNSAMPLE);

            final boolean gradientDirty = drawContent ? shaderDirty : overlayOnlyDirty;
            if (gradientDirty || shaderWidth != width || shaderHeight != height) {
                final float axisLength = direction == GlassFadeDirection.TOP
                        || direction == GlassFadeDirection.BOTTOM ? height : width;
                if (drawContent) {
                    maskPaint.setShader(createGradient(maskRange, axisLength,
                            maskRange.marginEnd, Color.WHITE, LEGACY_DOWNSAMPLE));
                    overlayPaint.setShader(createGradient(overlayRange, axisLength,
                            overlayRange.marginEnd, overlayColor, LEGACY_DOWNSAMPLE));
                } else {
                    overlayPaint.setShader(createGradient(overlayOnlyRange, axisLength,
                            overlayOnlyRange.marginEnd, overlayOnlyColor, LEGACY_DOWNSAMPLE));
                }
                shaderWidth = width;
                shaderHeight = height;
                if (drawContent) {
                    shaderDirty = false;
                } else {
                    overlayOnlyDirty = false;
                }
            }

            canvas.save();
            canvas.scale(LEGACY_DOWNSAMPLE, LEGACY_DOWNSAMPLE);
            final int layer = canvas.saveLayer(
                    0f, 0f, layerWidth, layerHeight, layerPaint);

            if (drawContent) {
                canvas.drawColor(0xFF000000 | (overlayColor & 0x00FFFFFF));
                canvas.save();
                canvas.scale(1f / LEGACY_DOWNSAMPLE, 1f / LEGACY_DOWNSAMPLE);
                final float left = bounds.left + getSourceOffsetX();
                final float top = bounds.top + getSourceOffsetY();
                final float right = bounds.right + getSourceOffsetX();
                final float bottom = bounds.bottom + getSourceOffsetY();
                canvas.translate(-left, -top);
                source.draw(canvas, left, top, right, bottom);
                canvas.restore();
                canvas.drawRect(0f, 0f, layerWidth, layerHeight, maskPaint);
            }

            canvas.drawRect(0f, 0f, layerWidth, layerHeight, overlayPaint);
            canvas.restoreToCount(layer);
            canvas.restore();
        }

        @Override
        void buildDisplayList(Rect bounds, RectF boundsRelativeRoot) {
        }

        @Override
        void discard() {
            shaderWidth = -1;
        }

        @Override
        boolean hasDisplayList() {
            return false;
        }

        @Override
        boolean shouldHaveDisplayList() {
            return false;
        }

        @Override
        boolean supportsSoftwareCanvas() {
            return true;
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private final class ImplSdk31 extends Impl {
        private final RenderNode contentNode = new RenderNode("glass-fade-content");
        private final RenderNode overlayNode = new RenderNode("glass-fade-overlay");
        private final Paint maskPaint = new Paint();
        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix maskMatrix = new Matrix();
        private Shader maskShader;
        private int shaderWidth = -1, shaderHeight = -1, shaderDownsample;

        ImplSdk31() {
            maskPaint.setBlendMode(BlendMode.DST_IN);
        }

        @Override
        void setAlpha(float alpha) {
            contentNode.setAlpha(alpha);
            overlayNode.setAlpha(alpha);
        }

        @Override
        void draw(Canvas canvas) {
            canvas.drawRenderNode(contentNode);
            canvas.drawRenderNode(overlayNode);
        }

        @Override
        void buildDisplayList(Rect bounds, RectF boundsRelativeRoot) {
            final boolean useDownsampledSource = canDrawDownsampled();
            final int downsample = useDownsampledSource ? downsampledSource.getDownsample() : 1;
            final int nodePadding = useDownsampledSource ? DOWNSAMPLED_NODE_PADDING : 0;
            final float originX = useDownsampledSource
                    ? alignDownsampleOrigin(boundsRelativeRoot.left,
                    downsampledSource.getDownsampleOffsetX(), downsample)
                      - nodePadding * downsample
                    : boundsRelativeRoot.left;
            final float originY = useDownsampledSource
                    ? alignDownsampleOrigin(boundsRelativeRoot.top,
                    downsampledSource.getDownsampleOffsetY(), downsample)
                      - nodePadding * downsample
                    : boundsRelativeRoot.top;
            final float nodeOffsetX = originX - boundsRelativeRoot.left;
            final float nodeOffsetY = originY - boundsRelativeRoot.top;
            final int width = (int) Math.ceil(
                    (boundsRelativeRoot.right - originX) / downsample) + nodePadding;
            final int height = (int) Math.ceil(
                    (boundsRelativeRoot.bottom - originY) / downsample) + nodePadding;
            final int overlayWidth = (int) Math.ceil(bounds.width() / (float) downsample);
            final int overlayHeight = (int) Math.ceil(bounds.height() / (float) downsample);
            if (shaderDirty || shaderWidth != bounds.width() || shaderHeight != bounds.height()
                    || shaderDownsample != downsample) {
                final float axisLength = direction == GlassFadeDirection.TOP || direction == GlassFadeDirection.BOTTOM
                        ? bounds.height() : bounds.width();
                maskShader = createGradient(maskRange, axisLength,
                        maskRange.marginEnd,
                        Color.WHITE, downsample);
                maskPaint.setShader(maskShader);
                final Shader overlay = createGradient(overlayRange, axisLength,
                        overlayRange.marginEnd, overlayColor, downsample);
                overlayNode.setPosition(0, 0, overlayWidth, overlayHeight);
                overlayNode.setPivotX(0f);
                overlayNode.setPivotY(0f);
                overlayNode.setScaleX(downsample);
                overlayNode.setScaleY(downsample);
                gradientPaint.setShader(overlay);
                final RecordingCanvas recordingCanvas = overlayNode.beginRecording();
                recordingCanvas.drawRect(0f, 0f, overlayWidth, overlayHeight, gradientPaint);
                overlayNode.endRecording();
                gradientPaint.setShader(null);
                shaderWidth = bounds.width();
                shaderHeight = bounds.height();
                shaderDownsample = downsample;
            }

            maskMatrix.setTranslate(-nodeOffsetX / downsample, -nodeOffsetY / downsample);
            maskShader.setLocalMatrix(maskMatrix);

            contentNode.setPosition(0, 0, width, height);
            contentNode.setPivotX(0f);
            contentNode.setPivotY(0f);
            contentNode.setScaleX(downsample);
            contentNode.setScaleY(downsample);
            contentNode.setTranslationX(nodeOffsetX);
            contentNode.setTranslationY(nodeOffsetY);

            final RecordingCanvas canvas = contentNode.beginRecording();
            canvas.saveLayer(0f, 0f, width, height, null);
            canvas.drawColor(0xFF000000 | (overlayColor & 0x00FFFFFF));
            if (useDownsampledSource) {
                downsampledSource.drawDownsampled(
                        canvas, boundsRelativeRoot, downsample, originX, originY);
            } else {
                canvas.save();
                canvas.translate(-boundsRelativeRoot.left, -boundsRelativeRoot.top);
                source.draw(canvas, boundsRelativeRoot.left, boundsRelativeRoot.top,
                        boundsRelativeRoot.right, boundsRelativeRoot.bottom);
                canvas.restore();
            }
            canvas.drawRect(0f, 0f, width, height, maskPaint);
            canvas.restore();
            contentNode.endRecording();

            shaderDirty = false;
        }

        @Override
        void discard() {
            contentNode.discardDisplayList();
            overlayNode.discardDisplayList();
            shaderWidth = -1;
        }

        @Override
        boolean hasDisplayList() {
            return contentNode.hasDisplayList() && overlayNode.hasDisplayList();
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private final class ImplSdk33 extends Impl {
        @Language("AGSL")
        private static final String AGSL_SOURCE =
                "uniform shader content;\n" +
                        "uniform shader alphaMask;\n" +
                        "uniform shader overlay;\n" +
                        "uniform float4 background_color_premultiplied;\n" +
                        "uniform float2 shader_offset;\n" +
                        "half4 main(float2 coord) {\n" +
                        "    half4 source = content.eval(coord);\n" +
                        "    half4 background = half4(background_color_premultiplied);\n" +
                        "    half4 base = source + background * (1.0 - source.a);\n" +
                        "    float2 shaderCoord = coord + shader_offset;\n" +
                        "    base *= alphaMask.eval(shaderCoord).a;\n" +
                        "    half4 top = overlay.eval(shaderCoord);\n" +
                        "    return top + base * (1.0 - top.a);\n" +
                        "}";

        private final RenderNode contentNode = new RenderNode("glass-fade-content");
        private final RuntimeShader runtimeShader = new RuntimeShader(AGSL_SOURCE);
        private int shaderWidth = -1, shaderHeight = -1, shaderDownsample;
        private RenderEffect[] effectPool;
        private float[] effectPoolOffsetX;
        private float[] effectPoolOffsetY;

        @Override
        void setAlpha(float alpha) {
            contentNode.setAlpha(alpha);
        }

        @Override
        void draw(Canvas canvas) {
            canvas.drawRenderNode(contentNode);
        }

        @Override
        void buildDisplayList(Rect bounds, RectF boundsRelativeRoot) {
            final boolean useDownsampledSource = canDrawDownsampled();
            final int downsample = useDownsampledSource ? downsampledSource.getDownsample() : 1;
            final int nodePadding = useDownsampledSource ? DOWNSAMPLED_NODE_PADDING : 0;
            final float originX = useDownsampledSource
                    ? alignDownsampleOrigin(boundsRelativeRoot.left,
                    downsampledSource.getDownsampleOffsetX(), downsample)
                      - nodePadding * downsample
                    : boundsRelativeRoot.left;
            final float originY = useDownsampledSource
                    ? alignDownsampleOrigin(boundsRelativeRoot.top,
                    downsampledSource.getDownsampleOffsetY(), downsample)
                      - nodePadding * downsample
                    : boundsRelativeRoot.top;
            final float nodeOffsetX = originX - boundsRelativeRoot.left;
            final float nodeOffsetY = originY - boundsRelativeRoot.top;
            final int width = (int) Math.ceil(
                    (boundsRelativeRoot.right - originX) / downsample) + nodePadding;
            final int height = (int) Math.ceil(
                    (boundsRelativeRoot.bottom - originY) / downsample) + nodePadding;
            final boolean shaderChanged = shaderDirty
                    || shaderWidth != bounds.width()
                    || shaderHeight != bounds.height()
                    || shaderDownsample != downsample;
            if (shaderChanged) {
                final float axisLength = direction == GlassFadeDirection.TOP || direction == GlassFadeDirection.BOTTOM
                        ? bounds.height() : bounds.width();
                runtimeShader.setInputShader("alphaMask", createGradient(maskRange, axisLength,
                        maskRange.marginEnd,
                        Color.WHITE, downsample));
                runtimeShader.setInputShader("overlay", createGradient(overlayRange, axisLength,
                        overlayRange.marginEnd, overlayColor, downsample));
                runtimeShader.setFloatUniform("background_color_premultiplied",
                        Color.red(overlayColor) / 255f,
                        Color.green(overlayColor) / 255f,
                        Color.blue(overlayColor) / 255f,
                        1f);
                shaderWidth = bounds.width();
                shaderHeight = bounds.height();
                shaderDownsample = downsample;
                final int poolSize = downsample * downsample;
                if (effectPool == null || effectPool.length != poolSize) {
                    effectPool = new RenderEffect[poolSize];
                    effectPoolOffsetX = new float[poolSize];
                    effectPoolOffsetY = new float[poolSize];
                } else {
                    Arrays.fill(effectPool, null);
                }
            }

            final int phaseX = useDownsampledSource
                    ? Math.floorMod(downsampledSource.getDownsampleOffsetX(), downsample) : 0;
            final int phaseY = useDownsampledSource
                    ? Math.floorMod(downsampledSource.getDownsampleOffsetY(), downsample) : 0;
            final int effectIndex = phaseY * downsample + phaseX;
            final float shaderOffsetX = nodeOffsetX / downsample;
            final float shaderOffsetY = nodeOffsetY / downsample;
            RenderEffect effect = effectPool[effectIndex];
            if (effect == null
                    || effectPoolOffsetX[effectIndex] != shaderOffsetX
                    || effectPoolOffsetY[effectIndex] != shaderOffsetY) {
                runtimeShader.setFloatUniform("shader_offset", shaderOffsetX, shaderOffsetY);
                effect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "content");
                effectPool[effectIndex] = effect;
                effectPoolOffsetX[effectIndex] = shaderOffsetX;
                effectPoolOffsetY[effectIndex] = shaderOffsetY;
            }

            contentNode.setPosition(0, 0, width, height);
            contentNode.setPivotX(0f);
            contentNode.setPivotY(0f);
            contentNode.setScaleX(downsample);
            contentNode.setScaleY(downsample);
            contentNode.setTranslationX(nodeOffsetX);
            contentNode.setTranslationY(nodeOffsetY);

            final RecordingCanvas canvas = contentNode.beginRecording();
            if (useDownsampledSource) {
                downsampledSource.drawDownsampled(
                        canvas, boundsRelativeRoot, downsample, originX, originY);
            } else {
                canvas.save();
                canvas.translate(-boundsRelativeRoot.left, -boundsRelativeRoot.top);
                source.draw(canvas, boundsRelativeRoot.left, boundsRelativeRoot.top,
                        boundsRelativeRoot.right, boundsRelativeRoot.bottom);
                canvas.restore();
            }
            contentNode.endRecording();

            contentNode.setRenderEffect(effect);
            shaderDirty = false;
        }

        @Override
        void discard() {
            contentNode.discardDisplayList();
            shaderWidth = -1;
        }

        @Override
        boolean hasDisplayList() {
            return contentNode.hasDisplayList();
        }
    }
}
