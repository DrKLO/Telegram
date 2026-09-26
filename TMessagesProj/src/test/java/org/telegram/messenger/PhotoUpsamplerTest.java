package org.telegram.messenger;

/** Display-path SR must beat bilinear on a step edge (RAISR-class, not bicubic mush). */
public final class PhotoUpsamplerTest {
    private static int failures;

    public static void main(String[] args) {
        int sw = 8;
        int sh = 8;
        int[] src = new int[sw * sh];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int v = x < sw / 2 ? 0 : 255;
                src[y * sw + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
            }
        }
        int dw = 16;
        int dh = 16;
        int[] bilinear = PhotoUpsampler.bilinearRgba8888(src, sw, sh, dw, dh);
        int[] ours = PhotoUpsampler.upsampleRgba8888(src, sw, sh, dw, dh);
        int eB = edgeEnergy(bilinear, dw, dh);
        int eO = edgeEnergy(ours, dw, dh);
        if (eO <= eB) {
            failures++;
            System.err.println("FAIL display SR should beat bilinear edge energy: ours=" + eO + " bilinear=" + eB);
        }
        if (ours.length != dw * dh) {
            failures++;
            System.err.println("FAIL size");
        }
        if (failures != 0) {
            System.err.println(failures + " failed");
            System.exit(1);
        }
        System.out.println("ok energy ours=" + eO + " bilinear=" + eB);
    }

    private static int edgeEnergy(int[] img, int w, int h) {
        int s = 0;
        int x = w / 2;
        for (int y = 1; y < h - 1; y++) {
            s += Math.abs(PhotoUpsampler.luma(img[y * w + x]) - PhotoUpsampler.luma(img[y * w + x - 1]));
        }
        return s;
    }
}
