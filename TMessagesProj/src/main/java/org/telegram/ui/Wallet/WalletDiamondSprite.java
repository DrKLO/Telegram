package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

/**
 * Flat stand-in for the card's 3D diamond: draws the wallet_card_diamond vector recolored by a
 * luma-preserving hue rotation and chroma scale around the gray axis (identity for the stock blue tint), plus
 * a soft drop shadow and a tight centered outline shadow, so it can sit above stickers.
 */
final class WalletDiamondSprite extends Drawable {

    private static final float MAX_TINT_SATURATION = .55f;
    /** Chroma kept for tints 60 degrees or more away from the stock blue. */
    private static final float FOREIGN_TINT_CHROMA = .7f;
    private static final float[] LUMA = {.2126f, .7152f, .0722f};
    /** Shadow mask resolution; blur hides it, the diamond itself stays vector. */
    private static final int SHADOW_SIZE = 84;
    /** WalletGiftPalette.DIAMOND_TINT after the saturation cap: the artwork's own colors. */
    private static float[] referenceTint;

    private static Bitmap softShadow, outlineShadow;
    private static final int[] softOffset = new int[2], outlineOffset = new int[2];

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final int intrinsicWidth, intrinsicHeight;
    private final Drawable diamond;
    private float tintR = -1, tintG, tintB;
    private int alpha = 255;
    private float shadowAlpha = 1f;

    /** Shadow strength relative to the sprite's own alpha. */
    void setShadowAlpha(float shadowAlpha) {
        this.shadowAlpha = shadowAlpha;
    }

    WalletDiamondSprite(int intrinsicWidth, int intrinsicHeight) {
        this.intrinsicWidth = intrinsicWidth;
        this.intrinsicHeight = intrinsicHeight;
        diamond = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.wallet_card_diamond).mutate();
        loadShadows(diamond);
    }

    private static void loadShadows(Drawable diamond) {
        if (softShadow != null) return;
        Bitmap argb = Bitmap.createBitmap(SHADOW_SIZE, SHADOW_SIZE, Bitmap.Config.ARGB_8888);
        diamond.setBounds(0, 0, SHADOW_SIZE, SHADOW_SIZE);
        diamond.draw(new Canvas(argb));
        // Blur radii are in design dp of the 28dp icon, converted to mask pixels.
        float pxPerDp = SHADOW_SIZE / 28f;
        Paint blur = new Paint();
        blur.setMaskFilter(new BlurMaskFilter(5f * pxPerDp, BlurMaskFilter.Blur.NORMAL));
        softShadow = argb.extractAlpha(blur, softOffset);
        blur.setMaskFilter(new BlurMaskFilter(1.5f * pxPerDp, BlurMaskFilter.Blur.NORMAL));
        outlineShadow = argb.extractAlpha(blur, outlineOffset);
        argb.recycle();
    }

    /** Limit only the chroma; retain hue and peak brightness. Neutral tints stay neutral. */
    private static float[] capSaturation(float r, float g, float b) {
        float peak = Math.max(r, Math.max(g, b));
        float chroma = peak - Math.min(r, Math.min(g, b));
        if (chroma > peak * MAX_TINT_SATURATION) {
            float scale = peak * MAX_TINT_SATURATION / chroma;
            r = peak + (r - peak) * scale;
            g = peak + (g - peak) * scale;
            b = peak + (b - peak) * scale;
        }
        return new float[]{r, g, b};
    }

    /** Tint of the artwork's blue layer, as WalletGiftPalette.material writes it at 30..35. */
    void setTint(float r, float g, float b) {
        float[] tint = capSaturation(r, g, b);
        r = tint[0];
        g = tint[1];
        b = tint[2];
        if (r == tintR && g == tintG && b == tintB) return;
        tintR = r;
        tintG = g;
        tintB = b;
        if (referenceTint == null) {
            referenceTint = capSaturation(0f, .47809f, 1f);
        }
        // Chroma plane coordinates: u = (1,-1,0)/sqrt2, v = (1,1,-2)/sqrt6, u x v = gray axis.
        final float[] ref = referenceTint;
        final double ru = (ref[0] - ref[1]) / Math.sqrt(2), rv = (ref[0] + ref[1] - 2 * ref[2]) / Math.sqrt(6);
        final double tu = (r - g) / Math.sqrt(2), tv = (r + g - 2 * b) / Math.sqrt(6);
        double angle = Math.atan2(tv, tu) - Math.atan2(rv, ru);
        angle = Math.IEEEremainder(angle, 2 * Math.PI);
        final double s = Math.hypot(tu, tv) / Math.hypot(ru, rv)
                * (1 + (FOREIGN_TINT_CHROMA - 1) * Math.min(1, Math.abs(angle) / (Math.PI / 3)));
        // M = P + s * (R - P): P projects onto gray, R rotates about it (Rodrigues).
        final double cos = Math.cos(angle), sin = Math.sin(angle) / Math.sqrt(3);
        final double p = 1 / 3.0, diag = p + s * (cos - cos * p), off = p - s * cos * p;
        final float[] m = new float[20];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                double value = row == column ? diag : off;
                if (column == (row + 1) % 3) value -= s * sin;
                else if (column == (row + 2) % 3) value += s * sin;
                m[row * 5 + column] = (float) value;
            }
        }
        // Keep each pixel's luma: greens and yellows read far brighter than blue at equal mean.
        for (int column = 0; column < 3; column++) {
            float luma = LUMA[0] * m[column] + LUMA[1] * m[5 + column] + LUMA[2] * m[10 + column];
            for (int row = 0; row < 3; row++) {
                m[row * 5 + column] += LUMA[column] - luma;
            }
        }
        m[18] = 1f;
        diamond.setColorFilter(new ColorMatrixColorFilter(m));
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        if (alpha <= 0 || tintR < 0) return;
        final android.graphics.Rect b = getBounds();
        if (shadowAlpha > 0f) {
            final float scale = b.width() / (float) SHADOW_SIZE;
            canvas.save();
            canvas.translate(b.left, b.top);
            canvas.scale(scale, b.height() / (float) SHADOW_SIZE);
            shadowPaint.setColor(Color.BLACK);
            shadowPaint.setAlpha((int) (.35f * alpha * shadowAlpha));
            canvas.drawBitmap(softShadow, softOffset[0], softOffset[1] + dp(1) / scale, shadowPaint);
            shadowPaint.setAlpha((int) (.55f * alpha * shadowAlpha));
            canvas.drawBitmap(outlineShadow, outlineOffset[0], outlineOffset[1], shadowPaint);
            canvas.restore();
        }
        diamond.setBounds(b);
        diamond.setAlpha(alpha);
        diamond.draw(canvas);
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
