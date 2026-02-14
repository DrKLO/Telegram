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

    public static RenderEffect getSaturationX2RenderEffect() {
        if (saturationUpX2Effect == null) {
            final ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(2f);
            saturationUpX2Effect = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(colorMatrix));
        }

        return saturationUpX2Effect;
    }
}
