package org.telegram.messenger;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

public class ColorUtilities {

    public final static float[] hslTmp = new float[3];
    public final static float[] hslTmp2 = new float[3];
    public final static float[] yuvTmp = new float[3];
    public final static float[] hsvTmp = new float[3];
    public final static float[] hsvTmp2 = new float[3];

    /**
     * RGB -> YUV.
     * Y in the range [0..1].
     * U in the range [-0.5..0.5].
     * V in the range [-0.5..0.5].
     */
    public static void colorToYuv(int color, float[] yuv) {
        float r = Color.red(color) / 255f;
        float g = Color.green(color) / 255f;
        float b = Color.blue(color) / 255f;

        float y, u, v;
        y = (float) (0.299 * r + 0.587 * g + 0.114 * b);
        u = (float) (-0.14713 * r - 0.28886 * g + 0.436 * b);
        v = (float) (0.615 * r - 0.51499 * g - 0.10001 * b);

        yuv[0] = y;
        yuv[1] = u;
        yuv[2] = v;
    }

    /**
     * YUV -> RGB.
     * Y in the range [0..1].
     * U in the range [-0.5..0.5].
     * V in the range [-0.5..0.5].
     */
    public static int YuvToColor(float[] yuv) {
        int r, g, b;

        float y = yuv[0];
        float u = yuv[1];
        float v = yuv[2];

        r = (int) ((y + 0.000 * u + 1.140 * v) * 255);
        g = (int) ((y - 0.396 * u - 0.581 * v) * 255);
        b = (int) ((y + 2.029 * u + 0.000 * v) * 255);

        r = Math.min(Math.max(r, 0), 255);
        g = Math.min(Math.max(g, 0), 255);
        b = Math.min(Math.max(b, 0), 255);

        return Color.rgb(r, g, b);
    }

    public static int replaceHue(int to, int from) {
        ColorUtils.colorToHSL(to, hslTmp);
        ColorUtils.colorToHSL(from, hslTmp2);
        hslTmp[0] = hslTmp2[0];
        if (Color.red(from) == Color.blue(from) && Color.blue(from) == Color.green(from)) {
            hslTmp[1] = 0;
        }
        return ColorUtils.HSLToColor(hslTmp);
    }

    public static int clampLightness(int color, float v, float v1) {
        ColorUtils.colorToHSL(color,hslTmp);
        if(hslTmp[2] >= v && hslTmp[2] <= v1) return color;
        if(hslTmp[2] < v) hslTmp[2] = v;
        if(hslTmp[2] > v1) hslTmp[2] = v1;
        return ColorUtils.HSLToColor(hslTmp);
    }
}
