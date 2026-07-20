package org.telegram.messenger.utils;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.os.Build;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.S)
public class RenderNodeEffects {
    private RenderNodeEffects() {}

    private static RenderEffect saturationUpX2Effect;
    private static RenderEffect saturationUpX3Effect;
    private static RenderEffect saturationUpX4Effect;

    public static RenderEffect getSaturationX2RenderEffect() {
        if (saturationUpX2Effect == null) {
            final ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(2f);
            saturationUpX2Effect = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(colorMatrix));
        }

        return saturationUpX2Effect;
    }

    public static RenderEffect getSaturationX3RenderEffect() {
        if (saturationUpX3Effect == null) {
            final ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(3f);
            saturationUpX3Effect = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(colorMatrix));
        }

        return saturationUpX3Effect;
    }

    public static RenderEffect getSaturationX4RenderEffect() {
        if (saturationUpX4Effect == null) {
            final ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(4f);
            saturationUpX4Effect = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(colorMatrix));
        }

        return saturationUpX4Effect;
    }

    public static RenderEffect createSaturationXRenderEffect(float x) {
        final ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(x);
        return RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(colorMatrix));
    }
}
