package org.telegram.ui.Components.blur3.drawable;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.BlendMode;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.LiquidGlassEffect;
import org.telegram.ui.Components.blur3.render.SurfaceEdgeLighting;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class BlurredBackgroundDrawableRenderNode extends BlurredBackgroundDrawable {
    private final BlurredBackgroundSource source;
    private final Outline outline = new Outline();
    private final Rect outlineRect = new Rect();

    private final RenderNode renderNode;
    private final RenderNode renderNodeFill;
    private final RenderNode renderNodeInnerShadow;

    private final Paint paintShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintInnerShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintInnerShadowMask = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path innerShadowPath = new Path();
    private final float[] innerShadowRadii = new float[8];
    private RenderEffect innerShadowBlurEffect;
    private float innerShadowBlurRadius = Float.NaN;

    private boolean renderNodeInvalidated;

    public BlurredBackgroundDrawableRenderNode(BlurredBackgroundSource source) {
        this.renderNode = new RenderNode("BlurredNode");
        this.renderNodeFill = new RenderNode("BlurredFill");
        this.renderNodeInnerShadow = new RenderNode("LiquidInnerShadow");
        this.renderNode.setClipToOutline(true);
        this.renderNode.setClipToBounds(true);
        this.renderNodeInnerShadow.setUseCompositingLayer(true, null);

        this.source = source;

        this.paintShadow.setColor(0);
        this.paintStrokeTop.setStyle(Paint.Style.STROKE);
        this.paintStrokeBottom.setStyle(Paint.Style.STROKE);
        this.paintInnerShadowMask.setBlendMode(BlendMode.CLEAR);
    }

    @Override
    public BlurredBackgroundDrawable setClipToOutline(boolean clipToOutline) {
        renderNode.setClipToOutline(clipToOutline);
        return super.setClipToOutline(clipToOutline);
    }

    private LiquidGlassEffect liquidGlassEffect;
    private SurfaceEdgeLighting surfaceEdgeLighting;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void setLiquidGlassEffectAllowed() {
        liquidGlassEffect = new LiquidGlassEffect(renderNodeFill);
        surfaceEdgeLighting = new SurfaceEdgeLighting();
    }


    @Override
    public BlurredBackgroundSource getSource() {
        return source;
    }

    @Override
    protected void onBoundPropsChanged() {
        super.onBoundPropsChanged();

        paintStrokeTop.setStrokeWidth(boundProps.strokeWidthTop);
        paintStrokeBottom.setStrokeWidth(boundProps.strokeWidthBottom);

        outlineRect.set(0, 0,
            boundProps.boundsWithPadding.width(),
            boundProps.boundsWithPadding.height()
        );
        getOutline(outline, outlineRect, boundProps.radii);
        outline.setAlpha(1);

        if (!boundProps.boundsWithPadding.isEmpty()) {
            renderNodeFill.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNode.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNodeInnerShadow.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNode.setOutline(outline);

            renderNodeInvalidated = true;
        }
    }

    @Override
    protected void onSourceOffsetChange(float sourceOffsetX, float sourceOffsetY) {
        super.onSourceOffsetChange(sourceOffsetX, sourceOffsetY);
        renderNodeInvalidated = true;
    }

    public boolean hasDisplayList() {
        return renderNode.hasDisplayList();
    }

    private void updateDisplayList() {
        final float offsetX = sourceOffsetX;
        final float offsetY = sourceOffsetY;

        Canvas c;

        final float sL = boundProps.boundsWithPadding.left + offsetX;
        final float sT = boundProps.boundsWithPadding.top + offsetY;
        final float sR = boundProps.boundsWithPadding.right + offsetX;
        final float sB = boundProps.boundsWithPadding.bottom + offsetY;

        c = renderNodeFill.beginRecording();
        c.save();
        c.translate(-sL, -sT);
        if (liquidGlassEffect != null && Build.VERSION.SDK_INT >= 33) {
            final float refractionHeight = Math.max(
                boundProps.liquidThickness <= 0 ? dp(12) : boundProps.liquidThickness,
                1f
            );
            final float refractionAmount = !Float.isNaN(boundProps.opticalDisplacement)
                ? boundProps.opticalDisplacement
                : dp(24) * boundProps.liquidIntensity;

            liquidGlassEffect.update(
                boundProps.boundsWithPadding.width(),
                boundProps.boundsWithPadding.height(),
                boundProps.shaderRadii[0], boundProps.shaderRadii[2], boundProps.shaderRadii[4], boundProps.shaderRadii[6],
                refractionHeight,
                refractionAmount,
                boundProps.depthShadingEnabled,
                boundProps.spectralSeparationEnabled,
                boundProps.opticalBlurRadius,
                boundProps.backdropSaturation
            );
        }
        source.draw(c, sL, sT, sR, sB);
        c.restore();
        renderNodeFill.endRecording();


        c = renderNode.beginRecording();
        if (Color.alpha(backgroundColor) == 255) {
            c.drawColor(backgroundColor);
        } else {
            c.drawRenderNode(renderNodeFill);
            if (Color.alpha(backgroundColor) != 0) {
                c.drawColor(backgroundColor);
            }
            if (Color.alpha(boundProps.surfaceTintColor) != 0) {
                c.drawColor(boundProps.surfaceTintColor);
            }
        }
        drawInnerShadow(c);
        if (strokeColorTop != 0) {
            drawStroke(c, 0, 0, boundProps.boundsWithPadding.width(),
                    boundProps.boundsWithPadding.height(), boundProps.radii,
                    boundProps.strokeWidthTop, true, paintStrokeTop);
        }
        if (strokeColorBottom != 0) {
            drawStroke(c, 0, 0, boundProps.boundsWithPadding.width(),
                    boundProps.boundsWithPadding.height(), boundProps.radii,
                    boundProps.strokeWidthBottom, false, paintStrokeBottom);
        }
        if (surfaceEdgeLighting != null && Build.VERSION.SDK_INT >= 33) {
            surfaceEdgeLighting.render(
                c,
                boundProps.boundsWithPadding.width(),
                boundProps.boundsWithPadding.height(),
                boundProps.shaderRadii[0],
                boundProps.shaderRadii[2],
                boundProps.shaderRadii[4],
                boundProps.shaderRadii[6],
                boundProps.edgeLightingStrength
            );
        }
        renderNode.endRecording();
    }

    private void drawInnerShadow(Canvas canvas) {
        final float radius = boundProps.insetShadowRadius;
        final float alpha = boundProps.insetShadowAlpha;
        if (radius <= 0f || alpha <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        final float width = boundProps.boundsWithPadding.width();
        final float height = boundProps.boundsWithPadding.height();
        System.arraycopy(
            boundProps.shaderRadii,
            0,
            innerShadowRadii,
            0,
            innerShadowRadii.length
        );

        innerShadowPath.rewind();
        innerShadowPath.addRoundRect(
            0f,
            0f,
            width,
            height,
            innerShadowRadii,
            Path.Direction.CW
        );
        innerShadowPath.close();

        paintInnerShadow.setColor(Theme.multAlpha(Color.BLACK, 0.15f * alpha));
        if (innerShadowBlurEffect == null || Math.abs(innerShadowBlurRadius - radius) > 0.1f) {
            innerShadowBlurRadius = radius;
            innerShadowBlurEffect =
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL);
            renderNodeInnerShadow.setRenderEffect(innerShadowBlurEffect);
        }

        final Canvas shadowCanvas = renderNodeInnerShadow.beginRecording();
        shadowCanvas.save();
        shadowCanvas.clipPath(innerShadowPath);
        shadowCanvas.drawPath(innerShadowPath, paintInnerShadow);
        shadowCanvas.translate(0f, radius);
        shadowCanvas.drawPath(innerShadowPath, paintInnerShadowMask);
        shadowCanvas.restore();
        renderNodeInnerShadow.endRecording();

        canvas.save();
        canvas.clipPath(innerShadowPath);
        canvas.drawRenderNode(renderNodeInnerShadow);
        canvas.restore();
    }

    @Override
    public void updateColors() {
        super.updateColors();

        paintShadow.setShadowLayer(shadowLayerRadius, shadowLayerDx, shadowLayerDy, shadowColor);
        paintStrokeTop.setColor(strokeColorTop);
        paintStrokeBottom.setColor(strokeColorBottom);

        renderNodeInvalidated = true;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (boundProps.boundsWithPadding.isEmpty()) {
            return;
        }

        if (!canvas.isHardwareAccelerated()) {
            drawSource(canvas, source);
            return;
        }

        if (!renderNode.hasDisplayList()) {
            source.dispatchOnDrawablesRelativePositionChange();
            updateDisplayList();
        } else if (renderNodeInvalidated) {
            updateDisplayList();
        }
        renderNodeInvalidated = false;

        int color = Theme.multAlpha(shadowColor, renderNode.getAlpha() * shadowAlpha);
        if (Color.alpha(color) != 0) {
            paintShadow.setShadowLayer(shadowLayerRadius, shadowLayerDx, shadowLayerDy, color);
            boundProps.drawShadows(canvas, paintShadow, inAppKeyboardOptimization);
        }

        canvas.save();
        canvas.translate(boundProps.boundsWithPadding.left, boundProps.boundsWithPadding.top);
        canvas.drawRenderNode(renderNode);
        canvas.restore();
    }

    public void invalidateDisplayList() {
        renderNodeInvalidated = true;
    }

    @Override
    public void setAlpha(int alpha) {
        final int oldAlpha = getAlpha();

        super.setAlpha(alpha);
        renderNode.setAlpha(alpha / 255f);
        renderNodeInvalidated = true;

        if (oldAlpha == 0 && alpha > 0) {
            source.dispatchOnDrawablesRelativePositionChange();
        }
    }

    @Override
    protected void onSourceRelativePositionChanged(RectF position) {
        super.onSourceRelativePositionChanged(position);
        source.dispatchOnDrawablesRelativePositionChange();
    }
}
