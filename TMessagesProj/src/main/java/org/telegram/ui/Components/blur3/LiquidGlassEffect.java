package org.telegram.ui.Components.blur3;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

@RequiresApi(api = 33)
public class LiquidGlassEffect {

    private final RenderNode node;
    private final RuntimeShader shader;

    private float width;
    private float height;
    private float radiusLeftTop;
    private float radiusRightTop;
    private float radiusRightBottom;
    private float radiusLeftBottom;
    private float refractionHeight;
    private float refractionAmount;
    private float depthEffect;
    private float chromaticAberration;
    private float blurRadius;
    private float saturation;

    public LiquidGlassEffect(RenderNode node) {
        this.node = node;
        shader = new RuntimeShader(AndroidUtilities.readRes(R.raw.liquid_glass_shader));
    }

    public void update(
        float width,
        float height,
        float radiusLeftTop,
        float radiusRightTop,
        float radiusRightBottom,
        float radiusLeftBottom,
        float refractionHeight,
        float refractionAmount,
        boolean depthEffect,
        boolean chromaticAberration,
        float blurRadius,
        float saturation
    ) {
        final float maxRadius = Math.min(width, height) * 0.5f;
        radiusLeftTop = Math.min(radiusLeftTop, maxRadius);
        radiusRightTop = Math.min(radiusRightTop, maxRadius);
        radiusRightBottom = Math.min(radiusRightBottom, maxRadius);
        radiusLeftBottom = Math.min(radiusLeftBottom, maxRadius);
        refractionHeight = Math.max(refractionHeight, 0.001f);

        final float depth = depthEffect ? 1f : 0f;
        final float dispersion = chromaticAberration ? 1f : 0f;

        if (
            Math.abs(this.width - width) <= 0.1f &&
            Math.abs(this.height - height) <= 0.1f &&
            Math.abs(this.radiusLeftTop - radiusLeftTop) <= 0.1f &&
            Math.abs(this.radiusRightTop - radiusRightTop) <= 0.1f &&
            Math.abs(this.radiusRightBottom - radiusRightBottom) <= 0.1f &&
            Math.abs(this.radiusLeftBottom - radiusLeftBottom) <= 0.1f &&
            Math.abs(this.refractionHeight - refractionHeight) <= 0.1f &&
            Math.abs(this.refractionAmount - refractionAmount) <= 0.1f &&
            Math.abs(this.depthEffect - depth) <= 0.001f &&
            Math.abs(this.chromaticAberration - dispersion) <= 0.001f &&
            Math.abs(this.blurRadius - blurRadius) <= 0.1f &&
            Math.abs(this.saturation - saturation) <= 0.001f
        ) {
            return;
        }

        this.width = width;
        this.height = height;
        this.radiusLeftTop = radiusLeftTop;
        this.radiusRightTop = radiusRightTop;
        this.radiusRightBottom = radiusRightBottom;
        this.radiusLeftBottom = radiusLeftBottom;
        this.refractionHeight = refractionHeight;
        this.refractionAmount = refractionAmount;
        this.depthEffect = depth;
        this.chromaticAberration = dispersion;
        this.blurRadius = blurRadius;
        this.saturation = saturation;

        shader.setFloatUniform("size", width, height);
        shader.setFloatUniform("offset", 0f, 0f);
        shader.setFloatUniform(
            "cornerRadii",
            radiusLeftTop,
            radiusRightTop,
            radiusRightBottom,
            radiusLeftBottom
        );
        shader.setFloatUniform("refractionHeight", refractionHeight);
        shader.setFloatUniform("refractionAmount", -refractionAmount);
        shader.setFloatUniform("depthEffect", depth);
        shader.setFloatUniform("chromaticAberration", dispersion);

        RenderEffect input = null;
        if (Math.abs(saturation - 1f) > 0.001f) {
            final float invSat = 1f - saturation;
            final float r = 0.213f * invSat;
            final float g = 0.715f * invSat;
            final float b = 0.072f * invSat;
            final ColorMatrix matrix = new ColorMatrix(new float[] {
                r + saturation, g, b, 0f, 0f,
                r, g + saturation, b, 0f, 0f,
                r, g, b + saturation, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            });
            input = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(matrix));
        }
        if (blurRadius > 0f) {
            input = input == null
                ? RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                : RenderEffect.createBlurEffect(blurRadius, blurRadius, input, Shader.TileMode.CLAMP);
        }

        final RenderEffect lens = RenderEffect.createRuntimeShaderEffect(shader, "content");
        node.setRenderEffect(input == null ? lens : RenderEffect.createChainEffect(lens, input));
    }
}
