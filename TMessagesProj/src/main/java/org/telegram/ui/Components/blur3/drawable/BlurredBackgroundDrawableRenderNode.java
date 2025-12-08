package org.telegram.ui.Components.blur3.drawable;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.LiquidGlassEffect;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

@RequiresApi(api = Build.VERSION_CODES.Q)
public class BlurredBackgroundDrawableRenderNode extends BlurredBackgroundDrawable {
    private final BlurredBackgroundSourceRenderNode source;
    private final Outline outline = new Outline();
    private final Rect outlineRect = new Rect();

    private final RenderNode renderNode;
    private final RenderNode renderNodeFill;
    private final RenderNode renderNodeStroke;

    private final Paint paintShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeTop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStrokeBottom = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean renderNodeInvalidated;

    public BlurredBackgroundDrawableRenderNode(BlurredBackgroundSourceRenderNode source) {
        this.renderNode = new RenderNode("BlurredBackgroundDrawableRenderNode");
        this.renderNodeFill = new RenderNode("BlurredBackgroundDrawableRenderNode.Fill");
        this.renderNodeStroke = new RenderNode("BlurredBackgroundDrawableRenderNode.Stroke");
        this.renderNode.setClipToOutline(true);
        this.renderNode.setClipToBounds(true);

        this.source = source;

        this.paintShadow.setColor(0);
        this.paintStrokeTop.setStyle(Paint.Style.STROKE);
        this.paintStrokeBottom.setStyle(Paint.Style.STROKE);
    }

    private LiquidGlassEffect liquidGlassEffect;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public void setLiquidGlassEffectAllowed() {
        liquidGlassEffect = new LiquidGlassEffect(renderNodeFill);
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
            renderNodeStroke.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
            renderNode.setPosition(0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height());
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

        c = renderNodeFill.beginRecording();
        c.save();
        c.translate(-(boundProps.boundsWithPadding.left + offsetX), -(boundProps.boundsWithPadding.top + offsetY));
        if (liquidGlassEffect != null && Build.VERSION.SDK_INT >= 33) {
            liquidGlassEffect.update(
                0, 0, boundProps.boundsWithPadding.width(), boundProps.boundsWithPadding.height(),
                boundProps.shaderRadii[0], boundProps.shaderRadii[2], boundProps.shaderRadii[4], boundProps.shaderRadii[6],
                boundProps.liquidThickness <= 0 ? dp(11) : boundProps.liquidThickness,
                boundProps.liquidIntensity,
                boundProps.liquidIndex,
                backgroundColor
            );
        }
        source.draw(c,
            boundProps.boundsWithPadding.left + offsetX,
            boundProps.boundsWithPadding.top + offsetY,
            boundProps.boundsWithPadding.right + offsetX,
            boundProps.boundsWithPadding.bottom + offsetY
        );
        c.restore();
        renderNodeFill.endRecording();

        final boolean hasStroke = strokeColorTop != 0 || strokeColorBottom != 0;
        if (hasStroke) {
            c = renderNodeStroke.beginRecording();
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
            renderNodeStroke.endRecording();
        }

        c = renderNode.beginRecording();
        c.drawRenderNode(renderNodeFill);
        if (liquidGlassEffect == null && Color.alpha(backgroundColor) != 0) {
            c.drawColor(backgroundColor);
        }
        if (hasStroke) {
            c.drawRenderNode(renderNodeStroke);
        }
        renderNode.endRecording();
    }

    @Override
    public void updateColors() {
        super.updateColors();

        paintShadow.setShadowLayer(dpf2(1), 0f, dpf2(1 / 3f), shadowColor);
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

        int color = Theme.multAlpha(shadowColor, renderNode.getAlpha());
        if (Color.alpha(color) != 0) {
            paintShadow.setShadowLayer(dpf2(1), 0f, dpf2(1 / 3f), color);
            boundProps.drawShadows(canvas, paintShadow, inAppKeyboardOptimization);
        }

        canvas.save();
        canvas.translate(boundProps.boundsWithPadding.left, boundProps.boundsWithPadding.top);
        canvas.drawRenderNode(renderNode);
        canvas.restore();
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
