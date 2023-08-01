package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

public class GradientTools {

    public boolean isDiagonal;
    public boolean isRotate;
    public Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    int color1;
    int color2;
    int color3;
    int color4;

    private final static int INTERNAL_WIDTH = 60;
    private final static int INTERNAL_HEIGHT = 80;

    RectF bounds = new RectF();
    Shader shader;
    Matrix matrix = new Matrix();

    Bitmap gradientBitmap = null;

    int[] colors = new int[4];

    public void setColors(int color1, int color2) {
       setColors(color1, color2, 0, 0);
    }

    public void setColors(int color1, int color2, int color3) {
        setColors(color1, color2, color3, 0);
    }

    public void setColors(int color1, int color2, int color3, int color4) {
        if (shader != null && this.color1 == color1 && this.color2 == color2 && this.color3 == color3 && this.color4 == color4) {
            return;
        }
        colors[0] = this.color1 = color1;
        colors[1] = this.color2 = color2;
        colors[2] = this.color3 = color3;
        colors[3] = this.color4 = color4;
        if (color2 == 0) {
            paint.setShader(shader = null);
            paint.setColor(color1);
        } else if (color3 == 0) {
            if (isDiagonal && isRotate) {
                paint.setShader(shader = new LinearGradient(0, 0, INTERNAL_HEIGHT, INTERNAL_HEIGHT, new int[]{color1, color2}, null, Shader.TileMode.CLAMP));
            } else {
                paint.setShader(shader = new LinearGradient(isDiagonal ? INTERNAL_HEIGHT : 0, 0, 0, INTERNAL_HEIGHT, new int[]{color1, color2}, null, Shader.TileMode.CLAMP));
            }
        } else {
            if (gradientBitmap == null) {
                gradientBitmap = Bitmap.createBitmap(INTERNAL_WIDTH, INTERNAL_HEIGHT, Bitmap.Config.ARGB_8888);
            }
            Utilities.generateGradient(gradientBitmap, true, 0, 0, gradientBitmap.getWidth(), gradientBitmap.getHeight(), gradientBitmap.getRowBytes(), colors);
            paint.setShader(shader = new BitmapShader(gradientBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        }
        updateBounds();
    }

    public void setBounds(RectF bounds) {
        if (this.bounds.top == bounds.top && this.bounds.bottom == bounds.bottom && this.bounds.left == bounds.left && this.bounds.right == bounds.right) {
            return;
        }
        this.bounds.set(bounds);
        updateBounds();
    }

    protected void updateBounds() {
        if (shader == null) {
            return;
        }
        float sx = bounds.width() / (float) INTERNAL_WIDTH;
        float sy = bounds.height() / (float) INTERNAL_HEIGHT;

        matrix.reset();
        matrix.postTranslate(bounds.left, bounds.top);
        matrix.preScale(sx, sy);

        shader.setLocalMatrix(matrix);
    }

    public void setBounds(float left, float top, float right, float bottom) {
        AndroidUtilities.rectTmp.set(left, top, right, bottom);
        setBounds(AndroidUtilities.rectTmp);
    }

    public int getAverageColor() {
        int color = color1;
        if (color2 != 0) {
            color = ColorUtils.blendARGB(color, color2, 0.5f);
        }
        if (color3 != 0) {
            color = ColorUtils.blendARGB(color, color3, 0.5f);
        }
        if (color4 != 0) {
            color = ColorUtils.blendARGB(color, color4, 0.5f);
        }
        return color;
    }
}
