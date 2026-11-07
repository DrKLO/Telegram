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
    private final RuntimeShader shader;
    private RenderEffect effect;

    /** The effects by the phase of the node on the grid of the downsample, they keep the uniforms they were made with. */
    private RenderEffect[] effectPool;
    private float[] effectPoolCenterX;
    private float[] effectPoolCenterY;

    public LiquidGlassEffect(RenderNode node) {
        this.node = node;
        final String code = AndroidUtilities.readRes(R.raw.liquid_glass_shader);
        shader = new RuntimeShader(code);
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

    /**
     * Sizes are of the shape on the screen. The node may be downsampled: {@code downsample} pixels of the screen in a
     * pixel of it, its origin {@code nodeOffset} away from the shape's. The shader works in the pixels of the node.
     */
    public void update(
        float width, float height,
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
            this.foregroundColor != foregroundColor
        ) {
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

        final float centerX = (width / 2 - nodeOffsetX) / downsample;
        final float centerY = (height / 2 - nodeOffsetY) / downsample;
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
