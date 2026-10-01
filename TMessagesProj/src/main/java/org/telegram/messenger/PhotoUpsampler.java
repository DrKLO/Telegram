package org.telegram.messenger;

/**
 * RAISR-class display upsample: cheap bilinear, then edge-adaptive luma sharpen.
 * Pure JVM so PhotoUpsamplerTest can run without the Android SDK.
 */
public final class PhotoUpsampler {
    private PhotoUpsampler() {}

    public static int[] upsampleRgba8888(int[] src, int sw, int sh, int dw, int dh) {
        int[] base = bilinearRgba8888(src, sw, sh, dw, dh);
        return sharpenEdges(base, dw, dh);
    }

    /** RAISR-lite: unsharp luma on coherent edges, blend with bilinear in flats. */
    static int[] sharpenEdges(int[] src, int w, int h) {
        int[] dst = src.clone();
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                int l = luma(src[i]);
                int gx = luma(src[i + 1]) - luma(src[i - 1]);
                int gy = luma(src[i + w]) - luma(src[i - w]);
                int mag = Math.abs(gx) + Math.abs(gy);
                if (mag < 24) {
                    continue;
                }
                int lap = 5 * l
                        - luma(src[i - 1]) - luma(src[i + 1])
                        - luma(src[i - w]) - luma(src[i + w]);
                float t = mag > 160 ? 1f : mag / 160f;
                int nl = clamp(Math.round(l + t * lap * 0.35f), 0, 255);
                int argb = src[i];
                int r = (argb >> 16) & 255;
                int g = (argb >> 8) & 255;
                int b = argb & 255;
                int yv = l == 0 ? 1 : l;
                r = clamp(r * nl / yv, 0, 255);
                g = clamp(g * nl / yv, 0, 255);
                b = clamp(b * nl / yv, 0, 255);
                dst[i] = (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
        }
        return dst;
    }

    static int[] bilinearRgba8888(int[] src, int sw, int sh, int dw, int dh) {
        int[] dst = new int[dw * dh];
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) {
            return dst;
        }
        for (int y = 0; y < dh; y++) {
            float fy = (y + 0.5f) * sh / dh - 0.5f;
            int y0 = clamp((int) Math.floor(fy), 0, sh - 1);
            int y1 = clamp(y0 + 1, 0, sh - 1);
            float ty = fy - (float) Math.floor(fy);
            if (fy < 0) {
                y0 = y1 = 0;
                ty = 0;
            }
            for (int x = 0; x < dw; x++) {
                float fx = (x + 0.5f) * sw / dw - 0.5f;
                int x0 = clamp((int) Math.floor(fx), 0, sw - 1);
                int x1 = clamp(x0 + 1, 0, sw - 1);
                float tx = fx - (float) Math.floor(fx);
                if (fx < 0) {
                    x0 = x1 = 0;
                    tx = 0;
                }
                int p00 = src[y0 * sw + x0];
                int p10 = src[y0 * sw + x1];
                int p01 = src[y1 * sw + x0];
                int p11 = src[y1 * sw + x1];
                dst[y * dw + x] = lerpArgb(p00, p10, p01, p11, tx, ty);
            }
        }
        return dst;
    }

    static int luma(int argb) {
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        return (77 * r + 150 * g + 29 * b) >> 8;
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static int lerpArgb(int p00, int p10, int p01, int p11, float tx, float ty) {
        return pack(
                lerp(lerp(ch(p00, 24), ch(p10, 24), tx), lerp(ch(p01, 24), ch(p11, 24), tx), ty),
                lerp(lerp(ch(p00, 16), ch(p10, 16), tx), lerp(ch(p01, 16), ch(p11, 16), tx), ty),
                lerp(lerp(ch(p00, 8), ch(p10, 8), tx), lerp(ch(p01, 8), ch(p11, 8), tx), ty),
                lerp(lerp(ch(p00, 0), ch(p10, 0), tx), lerp(ch(p01, 0), ch(p11, 0), tx), ty));
    }

    private static int ch(int p, int shift) {
        return (p >> shift) & 255;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int pack(int a, int r, int g, int b) {
        return (clamp(a, 0, 255) << 24) | (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(b, 0, 255);
    }
}
