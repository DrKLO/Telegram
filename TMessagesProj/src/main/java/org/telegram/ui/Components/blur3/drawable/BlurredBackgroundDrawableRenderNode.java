package org.telegram.ui.Components.blur3.drawable;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.math.MathUtils;

import org.telegram.messenger.LiteMode;
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
    private final Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        renderNodeInvalidated = true;
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
                boundProps.liquidIndex
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
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S || liquidGlassEffect != null) && backgroundColor != 0) {
            c.drawPaint(paintFill);
        }
        if (hasStroke) {
            c.drawRenderNode(renderNodeStroke);
        }
        renderNode.endRecording();
    }


    private int lastBackgroundColor;

    @Override
    public void updateColors() {
        super.updateColors();

        if (lastBackgroundColor != backgroundColor) {
            lastBackgroundColor = backgroundColor;
            if (liquidGlassEffect == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (Color.alpha(backgroundColor) != 0) {
                    renderNodeFill.setRenderEffect(RenderEffect.createColorFilterEffect(
                            new BlendModeColorFilter(backgroundColor, BlendMode.SRC_OVER)));
                } else {
                    renderNodeFill.setRenderEffect(null);
                }
            }
        }

        paintShadow.setShadowLayer(dpf2(1), 0f, dpf2(1 / 3f), shadowColor);
        paintFill.setColor(Theme.multAlpha(backgroundColor, boundProps.fillAlpha));
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

        if (renderNodeInvalidated || !renderNode.hasDisplayList()) {
            updateDisplayList();
        }

        int color = Theme.multAlpha(shadowColor, renderNode.getAlpha());
        if (Color.alpha(color) != 0) {
            paintShadow.setShadowLayer(dpf2(1), 0f, dpf2(1 / 3f), color);

            if (inAppKeyboardOptimization) {
                canvas.drawRoundRect(
                    boundProps.boundsWithPadding.left,
                    boundProps.boundsWithPadding.top,
                    boundProps.boundsWithPadding.right,
                    MathUtils.clamp(
                        boundProps.boundsWithPadding.top + boundProps.radii[0] * 2,
                        boundProps.boundsWithPadding.top,
                        boundProps.boundsWithPadding.bottom
                    ),
                    boundProps.radii[0],
                    boundProps.radii[0],
                    paintShadow
                );
            } else if (boundProps.radiiAreSame) {
                canvas.drawRoundRect(
                    boundProps.boundsWithPadding.left,
                    boundProps.boundsWithPadding.top,
                    boundProps.boundsWithPadding.right,
                    boundProps.boundsWithPadding.bottom,
                    boundProps.radii[0],
                    boundProps.radii[0],
                    paintShadow
                );
            } else {
                canvas.drawPath(boundProps.path, paintShadow);
            }
        }

        canvas.save();
        canvas.translate(boundProps.boundsWithPadding.left, boundProps.boundsWithPadding.top);
        canvas.drawRenderNode(renderNode);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        super.setAlpha(alpha);
        renderNode.setAlpha(alpha / 255f);
        renderNodeInvalidated = true;
    }
}
