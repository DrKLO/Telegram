package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import androidx.core.graphics.ColorUtils;

public class RoundVideoProgressShadow {

    RadialGradient radialGradient = new RadialGradient(0, 0, 100, new int[]{Color.TRANSPARENT, Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, 40)}, new float[]{0, 0.7f, 1f}, Shader.TileMode.CLAMP);
    Paint shaderPaint = new Paint();
    Matrix matrix = new Matrix();

    int lastSizesHash;

    public RoundVideoProgressShadow() {
        shaderPaint.setShader(radialGradient);
    }

    public void draw(Canvas canvas, float cx, float cy, float radius, float alpha) {
        int sizesHash = (int) cx + ((int) cy << 12) + ((int) radius << 24);
        if (sizesHash != lastSizesHash) {
            matrix.reset();
            float s = radius / 100f;
            matrix.setTranslate(cx, cy);
            matrix.preScale(s, s);
            radialGradient.setLocalMatrix(matrix);
        }
        shaderPaint.setAlpha((int) (255 * alpha));
        canvas.drawCircle(cx, cy, radius, shaderPaint);
    }
}
