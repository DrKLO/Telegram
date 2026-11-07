package org.telegram.ui.Components.blur3.drawable;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import androidx.annotation.ColorInt;
import androidx.annotation.Px;

/**
 * How the edge of a glass shape catches the light. Inside: two crescents, each the shape minus its own copy shifted along
 * the light (the top one lit, the bottom one opposite). Outside: a ring whose strength may change around the shape
 * (stronger at the sides, the "Dark out" rim of the QR screen) or stay even (an outline).
 * <p>
 * Every layer is a white mask baked once into a nine-patch ({@link GlassRimRenderer}) and tinted when drawn: colors,
 * alpha and themes cost nothing, only the geometry is baked.
 */
public final class GlassRim {
    /** The light from straight above: the top edge lit, the bottom one opposite. */
    public static final float LIGHT_TOP = 0;
    /** The light from the top-left: the top-left corner lit, the bottom-right one opposite. */
    public static final float LIGHT_TOP_LEFT = 45;

    /** Degrees, 0 from straight above, positive towards the left: the crescents' shift is (sin, cos) of it. */
    public final float lightAngle;

    public final @Px float topWidth;
    public final @ColorInt int topColor;
    public final @Px float bottomWidth;
    public final @ColorInt int bottomColor;

    public final @Px float ringWidth;
    public final @ColorInt int ringColor;
    /** The ring's strength along the light (at the lit and the opposite edge) and across it, multiplied with its color. */
    public final float ringTop, ringBottom, ringSides;
    /** How fast the ring goes from the sides to the lit edges: |cos θ|^exp. */
    public final float ringExp;

    public GlassRim(float lightAngle,
                    float topWidth, int topColor, float bottomWidth, int bottomColor,
                    float ringWidth, int ringColor, float ringTop, float ringBottom, float ringSides, float ringExp) {
        this.lightAngle = lightAngle;
        this.topWidth = topWidth;
        this.topColor = topColor;
        this.bottomWidth = bottomWidth;
        this.bottomColor = bottomColor;
        this.ringWidth = ringWidth;
        this.ringColor = ringColor;
        this.ringTop = ringTop;
        this.ringBottom = ringBottom;
        this.ringSides = ringSides;
        this.ringExp = ringExp;
    }

    /** The crescents of the glass's stroke colors and an even outline. */
    public static GlassRim of(float lightAngle, float topWidth, int topColor, float bottomWidth, int bottomColor, float outlineWidth, int outlineColor) {
        return new GlassRim(lightAngle, topWidth, topColor, bottomWidth, bottomColor, outlineWidth, outlineColor, 1, 1, 1, 0);
    }

    public GlassRim withLightAngle(float lightAngle) {
        if (this.lightAngle == lightAngle) {
            return this;
        }
        return new GlassRim(lightAngle, topWidth, topColor, bottomWidth, bottomColor, ringWidth, ringColor, ringTop, ringBottom, ringSides, ringExp);
    }

    boolean hasTop() {
        return topWidth > 0.05f && (topColor >>> 24) != 0;
    }

    boolean hasBottom() {
        return bottomWidth > 0.05f && (bottomColor >>> 24) != 0;
    }

    boolean hasRing() {
        return ringWidth > 0.05f && (ringColor >>> 24) != 0 && (ringTop > 0 || ringBottom > 0 || ringSides > 0);
    }

    boolean isEvenRing() {
        return ringTop == ringSides && ringBottom == ringSides;
    }

    /* The rims of the QR screen (the "Dark out" rim of the Glass Lab, the values of its RimTune) */

    /** Close, bottom buttons, tab island. */
    public static GlassRim qrBig() {
        return qr(1.1f, 27, 1.05f, 23, 1.15f, 33, 2, 3);
    }

    /** Chips, badges. */
    public static GlassRim qrSmall() {
        return qr(0.83f, 27, 0.79f, 23, 0.86f, 33, 2, 3);
    }

    /** Sheets and islands: subtler dark sides. */
    public static GlassRim qrSheet() {
        return qr(1.1f, 27, 1.05f, 23, 1.15f, 18, 2, 3);
    }

    /** Swatches: the small lines at about half the strength. */
    public static GlassRim qrSwatch() {
        return qr(0.83f, 13, 0.79f, 11, 0.86f, 16, 1, 1.5f);
    }

    private static GlassRim qr(float topDp, int topWhite, float bottomDp, int bottomWhite, float ringDp, int ringSides, float ringTop, float ringBottom) {
        return new GlassRim(LIGHT_TOP,
            dpf2(topDp), white(topWhite), dpf2(bottomDp), white(bottomWhite),
            dpf2(ringDp), 0xFF000000, ringTop / 100f, ringBottom / 100f, ringSides / 100f, 3.8f);
    }

    private static int white(int percent) {
        return (Math.round(255 * percent / 100f) << 24) | 0xFFFFFF;
    }
}
