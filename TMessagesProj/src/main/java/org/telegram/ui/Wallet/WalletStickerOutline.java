package org.telegram.ui.Wallet;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.telegram.messenger.DispatchQueue;

/** The white die-cut outline of a wallet card sticker, built off the main thread. */
final class WalletStickerOutline {
    static final int SIZE = 288, PADDING = 10, RADIUS = 7;

    private static DispatchQueue queue;

    static DispatchQueue queue() {
        if (queue == null) queue = new DispatchQueue("walletStickerOutline");
        return queue;
    }

    /** Squared distance → outline alpha: a disk of RADIUS with a one pixel antialiased rim. */
    private static final int CAP = RADIUS + 1;
    private static final int[] RIM = new int[2 * CAP * CAP + 1];
    static {
        for (int d2 = 0; d2 < RIM.length; d2++) {
            RIM[d2] = (int) (255 * Math.max(0, Math.min(1, RADIUS + .5 - Math.sqrt(d2))));
        }
    }

    /** Puts {@code src} (non-premultiplied ARGB, SIZE×SIZE) over its white outline into {@code out}. */
    static void compose(int[] src, int[] out, int[] alpha, int[] line) {
        final int n = SIZE;
        // rows: distance to the nearest opaque pixel, capped at CAP
        for (int y = 0; y < n; y++) {
            final int row = y * n;
            int d = CAP;
            for (int x = 0; x < n; x++) {
                d = (src[row + x] >>> 24) >= 128 ? 0 : Math.min(d + 1, CAP);
                alpha[row + x] = d;
            }
            d = CAP;
            for (int x = n - 1; x >= 0; x--) {
                d = alpha[row + x] == 0 ? 0 : Math.min(d + 1, CAP);
                if (d < alpha[row + x]) alpha[row + x] = d;
            }
        }
        // columns: min over |dy| ≤ CAP of dx² + dy², i.e. a true circle instead of a square
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) line[y] = alpha[y * n + x];
            for (int y = 0; y < n; y++) {
                int best = RIM.length - 1;
                final int from = Math.max(0, y - CAP), to = Math.min(n - 1, y + CAP);
                for (int yy = from; yy <= to; yy++) {
                    final int dx = line[yy];
                    if (dx >= CAP) continue;
                    final int dy = yy - y, d2 = dx * dx + dy * dy;
                    if (d2 < best) best = d2;
                }
                alpha[y * n + x] = RIM[best];
            }
        }
        for (int i = 0; i < n * n; i++) out[i] = over(src[i], Math.max(alpha[i], src[i] >>> 24));
    }

    static int lineBufferSize() {
        return SIZE;
    }

    /** Non-premultiplied {@code color} drawn over white with {@code alpha}. */
    private static int over(int color, int alpha) {
        final int sa = color >>> 24;
        if (sa == 255) return color;
        if (sa == 0) return alpha << 24 | 0xFFFFFF;
        final int da = alpha * (255 - sa) / 255, ra = sa + da;
        final int r = ((color >> 16 & 0xFF) * sa + 255 * da) / ra;
        final int g = ((color >> 8 & 0xFF) * sa + 255 * da) / ra;
        final int b = ((color & 0xFF) * sa + 255 * da) / ra;
        return ra << 24 | r << 16 | g << 8 | b;
    }

    /** Whether enough of the sticker has been drawn to cast a shadow (lottie may hand out an empty first frame). */
    static boolean covered(int[] pixels) {
        final int n = SIZE;
        int covered = 0;
        for (int i = 0; i < n * n; i += 7) if ((pixels[i] >>> 24) > 0) covered++;
        return covered > n * n / 7 / 20;
    }

    static Bitmap shadow(int[] pixels) {
        final Bitmap art = Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888);
        final Bitmap small = Bitmap.createScaledBitmap(art, 128, 128, true);
        final Bitmap alpha = small.extractAlpha();
        final Bitmap shadow = Bitmap.createBitmap(176, 176, Bitmap.Config.ALPHA_8);
        final Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
        blur.setMaskFilter(new BlurMaskFilter(5, BlurMaskFilter.Blur.NORMAL));
        new Canvas(shadow).drawBitmap(alpha, 24, 24, blur);
        art.recycle();
        small.recycle();
        alpha.recycle();
        return shadow;
    }
}
