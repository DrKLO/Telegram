package org.telegram.ui.QrShare;

/**
 * The values of the glass, the light band and the QR intro, as tuned by the designer ("Dark out" rim, Glass Lab).
 * Four element groups for the rim (Big: close, bottom buttons, tab island; Small: chip, badge; Sheet: layers and
 * islands; Swatch) plus global values.
 */
final class RimTune {

    private RimTune() {
    }

    // the "Dark out" rim: white crescents added (Plus) and a dark ring multiplied over the real pixels under the rim,
    // so the tint follows the backdrop by itself. Multiply is drawn as black at alpha a (identical maths, layer-safe)

    static final int BIG = 0, SMALL = 1, SHEET = 2, SWATCH = 3;
    static final int NG = 4;

    // per-group parameters
    static final int T_TOP = 0, A_TOP = 1, T_BOT = 2, A_BOT = 3, D_W = 4, D_A = 5, D_EXP = 6, D_TINT = 7, INSET = 8, W = 9, W_TINT = 10, D_TOP = 11, D_BOT = 12;
    /**
     * Top crescent dp, top white %, bottom crescent dp, bottom white %, dark line dp, dark % (sides), dark falloff
     * exponent, dark tint, outer inset dp, stroke dp, white tint, dark at top %, dark at bottom %.
     */
    static final float[][] g = {
        {1.1f, 27, 1.05f, 23, 1.15f, 33, 3.8f, 1, -1, 1, 1, 2, 3},  // Big
        {0.83f, 27, 0.79f, 23, 0.86f, 33, 3.8f, 1, -1, 0.6f, 1, 2, 3}, // Small
        {1.1f, 27, 1.05f, 23, 1.15f, 18, 3.8f, 1, -1, 1.2f, 1, 2, 3},  // Sheet: subtler dark sides (18%)
        {0.83f, 13, 0.79f, 11, 0.86f, 16, 3.8f, 1, -1, 0.6f, 1, 1, 1.5f}, // Swatch: Small lines, about half the strength
    };

    // global parameters
    static final int TILT_DEG = 0, TILT_BAL = 1, BLUR = 2, FILL = 3, V_TOP = 4, V_BOTTOM = 5, V_SIDE = 6, V_WHITE_HALF = 7, V_DARK_HALF = 8,
        SH_FILL = 9, SH_BRIGHT = 10, SH_CONTRAST = 11, SH_BLUR = 12, SHINE = 13, SHINE_WHITE = 14, SHINE_TILT = 15, SHINE_SENS = 16, PALE_SHARE = 17, PALE_OPACITY = 18, SHINE_CURVE = 19, SHINE_CYL = 20, GLINT = 21, METAL = 22,
        SH_W = 23, SH_ANG = 24, SH_DUR = 25, SH_PER = 26, SH_RAND = 27, SH_SOFT = 28, CORE_W = 29, CORE_WHITE = 30, EDGE_DIM = 31, SH_EXP = 32, SH2_OFF = 33, SH2_STR = 34, OUTLINE = 35, OUT_W = 36, OUT_STR = 37, OUT_OFF = 38, GLINT_SURF = 39, IRI_HUE = 40, IRI_STR = 41, PUSH = 42, PUSH_SCALE = 43, PUSH_TILT = 44, BG_W = 45, BG_STR = 46, BG_NOISE = 47, PUSH_IN = 48, PUSH_OUT = 49, PUSH_W = 50, INTRO_MS = 51, INTRO_EASE = 52, INTRO_HALF = 53;
    static final float[] v = {20, 10, 17.5f, 6, 85, 55, 26, 50, 40, 5, 7, 9, 33, 46, 24, 0, 30, 45, 67, 15, 70, 139, 100, 61, 193, 4, 2, 100, 55, 0.3f, 100, 30, 1.5f, 2.5f, 0, 1, 1.3f, 200, 0.7f, 207, 0, 0, 3.5f, 0, 1.5f, 2.5f, 34, 27, 360, 780, 1.1f, 920, 1, 10};

    /** The values never change; shaders built from them are built once. */
    static final int version = 1;
}
