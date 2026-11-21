package org.telegram.ui.Components.blur3;

import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

import java.util.Arrays;

@RequiresApi(api = 33)
public class LiquidGlassEffect {

    private final RenderNode node;
    // the arrow costs the shader a lot: a shape without it keeps the plain one
    private final RuntimeShader plainShader;
    private RuntimeShader arrowShader;
    private RuntimeShader shader;
    private RenderEffect effect;

    /** The effects by the phase of the node on the grid of the downsample, they keep the uniforms they were made with. */
    private RenderEffect[] effectPool;
    private float[] effectPoolCenterX;
    private float[] effectPoolCenterY;

    public LiquidGlassEffect(RenderNode node) {
        this.node = node;
        final String code = AndroidUtilities.readRes(R.raw.liquid_glass_shader);
        shader = plainShader = new RuntimeShader(code);
        node.setRenderEffect(effect = RenderEffect.createRuntimeShaderEffect(shader, "img"));
    }

    private int downsample;
    private float width, height;
    private float radiusLeftTop;
    private float radiusRightTop;
    private float radiusRightBottom;
    private float radiusLeftBottom;
    private float thickness;
    private float intensity;
    private float index;
    private int foregroundColor;

    private float arrowHeight, arrowTipRadius, arrowFilletRadius, arrowStripe;
    private float arrowAxisX, arrowAxisY, arrowDirX, arrowDirY;
    private boolean arrowChanged;

    /**
     * An arrow on a side of the shape (a hint), see {@link org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable#setArrow}.
     * In the pixels of the screen: the point of the edge on the arrow's axis from the shape's center, the direction out of
     * it, the half width of the stripe of the edge the arrow changes.
     */
    public void setArrow(float axisX, float axisY, float dirX, float dirY, float height, float tipRadius, float filletRadius, float stripe) {
        if (arrowAxisX != axisX || arrowAxisY != axisY || arrowDirX != dirX || arrowDirY != dirY
                || arrowHeight != height || arrowTipRadius != tipRadius || arrowFilletRadius != filletRadius || arrowStripe != stripe) {
            arrowAxisX = axisX;
            arrowAxisY = axisY;
            arrowDirX = dirX;
            arrowDirY = dirY;
            arrowHeight = height;
            arrowTipRadius = tipRadius;
            arrowFilletRadius = filletRadius;
            arrowStripe = stripe;
            arrowChanged = true;
        }
    }

    public void clearArrow() {
        setArrow(0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Sizes are of the shape on the screen, its rect {@code bodyX, bodyY} from the shape's origin (the arrow is out of it).
     * The node may be downsampled: {@code downsample} pixels of the screen in a pixel of it, its origin {@code nodeOffset}
     * away from the shape's. The shader works in the pixels of the node.
     */
    public void update(
        float width, float height,
        float bodyX, float bodyY,
        float radiusLeftTop, float radiusRightTop, float radiusRightBottom, float radiusLeftBottom,

        float thickness,
        float intensity,
        float index,
        int foregroundColor,

        float nodeOffsetX, float nodeOffsetY,
        int downsample
    ) {
        if (radiusLeftTop + radiusLeftBottom > height) {
            float a = radiusLeftTop / (radiusLeftTop + radiusLeftBottom);
            radiusLeftTop = height * a;
            radiusLeftBottom = height * (1.0f - a);
        }
        if (radiusRightTop + radiusRightBottom > height) {
            float a = radiusRightTop / (radiusRightTop + radiusRightBottom);
            radiusRightTop = height * a;
            radiusRightBottom = height * (1.0f - a);
        }

        if (
            this.downsample != downsample ||
            Math.abs(this.width - width) > 0.1f ||
            Math.abs(this.height - height) > 0.1f ||
            Math.abs(this.radiusLeftTop - radiusLeftTop) > 0.1f ||
            Math.abs(this.radiusRightTop - radiusRightTop) > 0.1f ||
            Math.abs(this.radiusRightBottom - radiusRightBottom) > 0.1f ||
            Math.abs(this.radiusLeftBottom - radiusLeftBottom) > 0.1f ||
            Math.abs(this.thickness - thickness) > 0.1f ||
            Math.abs(this.intensity - intensity) > 0.1f ||
            Math.abs(this.index - index) > 0.1f ||
            this.foregroundColor != foregroundColor ||
            arrowChanged
        ) {
            arrowChanged = false;
            if (arrowHeight > 0) {
                if (arrowShader == null) {
                    arrowShader = new RuntimeShader(AndroidUtilities.readRes(R.raw.liquid_glass_arrow_shader));
                }
                shader = arrowShader;
            } else {
                shader = plainShader;
            }
            this.foregroundColor = foregroundColor;

            final float a = Color.alpha(foregroundColor) / 255f;
            final float r = Color.red(foregroundColor) / 255f * a;
            final float g = Color.green(foregroundColor) / 255f * a;
            final float b = Color.blue(foregroundColor) / 255f * a;
            final float scale = 1f / downsample;

            this.downsample = downsample;
            this.width = width;
            this.height = height;
            this.radiusRightBottom = radiusRightBottom;
            this.radiusRightTop = radiusRightTop;
            this.radiusLeftBottom = radiusLeftBottom;
            this.radiusLeftTop = radiusLeftTop;
            this.thickness = thickness;

            shader.setFloatUniform("size", width / 2 * scale, height / 2 * scale);
            shader.setFloatUniform("radius", radiusRightBottom * scale, radiusRightTop * scale, radiusLeftBottom * scale, radiusLeftTop * scale);
            shader.setFloatUniform("thickness", thickness * scale);
            shader.setFloatUniform("refract_intensity", this.intensity = intensity);
            shader.setFloatUniform("refract_index", this.index = index);
            shader.setFloatUniform("foreground_color_premultiplied", r, g, b, a);
            if (shader == arrowShader) {
                shader.setFloatUniform("arrow", arrowHeight * scale, arrowTipRadius * scale, arrowFilletRadius * scale, arrowStripe * scale);
                shader.setFloatUniform("arrow_axis", arrowAxisX * scale, arrowAxisY * scale, arrowDirX, arrowDirY);
            }

            // and the last one for a node off the grid of the screen, e.g. a view on its way
            final int poolSize = downsample * downsample + 1;
            if (effectPool == null || effectPool.length != poolSize) {
                effectPool = new RenderEffect[poolSize];
                effectPoolCenterX = new float[poolSize];
                effectPoolCenterY = new float[poolSize];
            } else {
                Arrays.fill(effectPool, null);
            }
        }

        final float centerX = (bodyX + width / 2 - nodeOffsetX) / downsample;
        final float centerY = (bodyY + height / 2 - nodeOffsetY) / downsample;
        final boolean onGrid = nodeOffsetX == (int) nodeOffsetX && nodeOffsetY == (int) nodeOffsetY;
        final int effectIndex = onGrid
            ? Math.floorMod((int) -nodeOffsetY, downsample) * downsample + Math.floorMod((int) -nodeOffsetX, downsample)
            : effectPool.length - 1;
        RenderEffect phaseEffect = effectPool[effectIndex];
        if (phaseEffect == null || effectPoolCenterX[effectIndex] != centerX || effectPoolCenterY[effectIndex] != centerY) {
            shader.setFloatUniform("center", centerX, centerY);
            phaseEffect = RenderEffect.createRuntimeShaderEffect(shader, "img");
            effectPool[effectIndex] = phaseEffect;
            effectPoolCenterX[effectIndex] = centerX;
            effectPoolCenterY[effectIndex] = centerY;
        }
        if (effect != phaseEffect) {
            node.setRenderEffect(effect = phaseEffect);
        }
    }

}
