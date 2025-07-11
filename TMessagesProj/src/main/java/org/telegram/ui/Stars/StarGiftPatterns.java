package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class StarGiftPatterns {

    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_ACTION = 1;
    public static final int TYPE_GIFT = 2;
    public static final int TYPE_LINK_PREVIEW = 3;

    private static final float[][] patternLocations = new float[][] {
        {
            83.33f, 24, 27.33f, .22f,
            68.66f, 75.33f, 25.33f, .21f,
            0, 86, 25.33f, .12f,
            -68.66f, 75.33f, 25.33f, .21f,
            -82.66f, 13.66f, 27.33f, .22f,
            -80, -33.33f, 20, .24f,
            -46.5f, -63.16f, 27, .21f,
            1, -82.66f, 20, .15f,
            46.5f, -63.16f, 27, .21f,
            80, -33.33f, 19.33f, .24f,

            115.66f, -63, 20, .15f,
            134, -10.66f, 20, .18f,
            118.66f, 55.66f, 20, .15f,
            124.33f, 98.33f, 20, .11f,

            -128, 98.33f, 20, .11f,
            -108, 55.66f, 20, .15f,
            -123.33f, -10.66f, 20, .18f,
            -116, -63.33f, 20, .15f
        },
        {
            27.33f, -57.66f, 20, .12f,
            59, -32, 19.33f, .22f,
            77, 4.33f, 22.66f, .2f,
            100, 40.33f, 18, .12f,
            58.66f, 59, 20, .18f,
            73.33f, 100.33f, 22.66f, .15f,
            75, 155, 22, .11f,

            -27.33f, -57.33f, 20, .12f,
            -59, -32.33f, 19.33f, .2f,
            -77, 4.66f, 23.33f, .2f,
            -98.66f, 41, 18.66f, .12f,
            -58, 59.33f, 19.33f, .18f,
            -73.33f, 100, 22, .15f,
            -75.66f, 155, 22, .11f
        },
        {
            -0.83f, -52.16f, 12.33f, .2f,
            26.66f, -40.33f, 16, .2f,
            44.16f, -20.5f, 12.33f, .2f,
            53, 7.33f, 16, .2f,
            31, 23.66f, 14.66f, .2f,
            0, 32, 13.33f, .2f,
            -29, 23.66f, 14, .2f,
            -53, 7.33f, 16, .2f,
            -44.5f, -20.16f, 12.33f, .2f,
            -27.33f, -40.33f, 16, .2f,
            43.66f, 50, 14.66f, .2f,
            -41.66f, 48, 14.66f, .2f
        },
        {
            -0.16f, -103.5f, 20.33f, .15f,
            39.66f, -77.33f, 26.66f, .15f,
            70.66f, -46.33f, 21.33f, .15f,
            84.5f, -3.83f, 29.66f, .15f,
            65.33f, 56.33f, 24.66f, .15f,
            0, 67.66f, 24.66f, .15f,
            -65.66f, 56.66f, 24.66f, .15f,
            -85, -4, 29.33f, .15f,
            -70.66f, -46.33f, 21.33f, .15f,
            -40.33f, -77.66f, 26.66f, .15f,

            62.66f, -109.66f, 21.33f, .11f,
            103.166f, -67.5f, 20.33f, .11f,
            110.33f, 37.66f, 20.66f, .11f,
            94.166f, 91.16f, 20.33f, .11f,
            38.83f, 91.16f, 20.33f, .11f,
            0, 112.5f, 20.33f, .11f,
            -38.83f, 91.16f, 20.33f, .11f,
            -94.166f, 91.16f, 20.33f, .11f,
            -110.33f, 37.66f, 20.66f, .11f,
            -103.166f, -67.5f, 20.33f, .11f,
            -62.66f, -109.66f, 21.33f, .11f
        }
    };

    public static void drawPattern(Canvas canvas, Drawable pattern, float w, float h, float alpha, float scale) {
        drawPattern(canvas, TYPE_DEFAULT, pattern, w, h, alpha, scale);
    }

    public static void drawPattern(Canvas canvas, int type, Drawable pattern, float w, float h, float alpha, float scale) {
        if (alpha <= 0.0f) return;
        for (int i = 0; i < patternLocations[type].length; i += 4) {
            final float x = patternLocations[type][i];
            final float y = patternLocations[type][i + 1];
            final float size = patternLocations[type][i + 2];
            final float thisAlpha = patternLocations[type][i + 3];

            float cx = x, cy = y, sz = size;
            if (w < h && type == TYPE_DEFAULT) {
                cx = y;
                cy = x;
            }
            cx *= scale;
            cy *= scale;
            sz *= scale;
            pattern.setBounds((int) (dp(cx) - dp(sz) / 2.0f), (int) (dp(cy) - dp(sz) / 2.0f), (int) (dp(cx) + dp(sz) / 2.0f), (int) (dp(cy) + dp(sz) / 2.0f));

            pattern.setAlpha((int) (0xFF * alpha * thisAlpha));
            pattern.draw(canvas);
        }
    }

    private static final float[] profileRight = new float[] {
        -35.66f, -5, 24, .2388f,
        -14.33f, -29.33f, 20.66f, .32f,
        -15, -73.66f, 19.33f, .32f,
        -2, -99.66f, 18, .1476f,
        -64.33f, -24.66f, 23.33f, .3235f,
        -40.66f, -53.33f, 24, .3654f,
        -50.33f, -85.66f, 20, .172f,
        -96, -1.33f, 19.33f, .3343f,
        -136.66f, -13, 18.66f, .2569f,
        -104.66f, -33.66f, 20.66f, .2216f,
        -82, -62.33f, 22.66f, .2562f,
        -131.66f, -60, 18, .1316f,
        -105.66f, -88.33f, 18, .1487f
    };
    private static final float[] profileLeft = new float[] {
        0, -107.33f, 16, .1505f,
        14.33f, -84, 18, .1988f,
        0, -50.66f, 18.66f, .3225f,
        13, -15, 18.66f, .37f,
        43.33f, 1, 18.66f, .3186f
    };

    public static void drawProfilePattern(Canvas canvas, Drawable pattern, float w, float h, float alpha, float full) {
        if (alpha <= 0.0f) return;

        final float b = h;
        final float l = 0, r = w;

        if (full > 0) {
            for (int i = 0; i < profileLeft.length; i += 4) {
                final float x = profileLeft[i];
                final float y = profileLeft[i + 1];
                final float size = profileLeft[i + 2];
                final float thisAlpha = profileLeft[i + 3];

                pattern.setBounds(
                    (int) (l + dpf2(x) - dpf2(size) / 2.0f),
                    (int) (b + dpf2(y) - dpf2(size) / 2.0f),
                    (int) (l + dpf2(x) + dpf2(size) / 2.0f),
                    (int) (b + dpf2(y) + dpf2(size) / 2.0f)
                );
                pattern.setAlpha((int) (0xFF * alpha * thisAlpha * full));
                pattern.draw(canvas);
            }

            final float sl = 77.5f, sr = 173.33f;
            final float space = w / AndroidUtilities.density - sl - sr;
            int count = Math.max(0, Math.round(space / 27.25f));
            if (count % 2 == 0) {
                count++;
            }
            for (int i = 0; i < count; ++i) {
                final float x = sl + space * ((float) i / (count - 1));
                final float y = i % 2 == 0 ? 0 : -12.5f;
                final float size = 17;
                final float thisAlpha = .21f;

                pattern.setBounds(
                    (int) (l + dpf2(x) - dpf2(size) / 2.0f),
                    (int) (b + dpf2(y) - dpf2(size) / 2.0f),
                    (int) (l + dpf2(x) + dpf2(size) / 2.0f),
                    (int) (b + dpf2(y) + dpf2(size) / 2.0f)
                );
                pattern.setAlpha((int) (0xFF * alpha * thisAlpha * full));
                pattern.draw(canvas);
            }
        }

        for (int i = 0; i < profileRight.length; i += 4) {
            final float x = profileRight[i];
            final float y = profileRight[i + 1];
            final float size = profileRight[i + 2];
            final float thisAlpha = profileRight[i + 3];

            pattern.setBounds(
                (int) (r + dpf2(x) - dpf2(size) / 2.0f),
                (int) (b + dpf2(y) - dpf2(size) / 2.0f),
                (int) (r + dpf2(x) + dpf2(size) / 2.0f),
                (int) (b + dpf2(y) + dpf2(size) / 2.0f)
            );
            pattern.setAlpha((int) (0xFF * alpha * thisAlpha));
            pattern.draw(canvas);
        }
    }

    final static float[][] orbitConfigs = {
            {AndroidUtilities.dp(25), AndroidUtilities.dp(9), 0.3f},
            {AndroidUtilities.dp(35), AndroidUtilities.dp(7), 0.15f},
            {AndroidUtilities.dp(35), AndroidUtilities.dp(8), 0.28f},
            {AndroidUtilities.dp(45), AndroidUtilities.dp(7), 0.15f}
    };

    final static float[][] progressThresholds = {
            {0.86f, 0.69f},
            {0.86f, 0.61f},
            {0.84f, 0.43f},
            {0.84f, 0.38f}
    };

    public static void calcOrbits() {
        int width = AndroidUtilities.displaySize.x;
        int addition = width / 100;
        int start = AndroidUtilities.dp(21) + addition / 2;

        orbitConfigs[0][0] = start;
        orbitConfigs[1][0] = start * 1.6f;
        orbitConfigs[2][0] = start * 1.35f;
        orbitConfigs[3][0] = start * 2.1f;
    }

    private static float stableY = 0;

    public static void drawProfilePattern(Canvas canvas, Drawable pattern, float w, float h,
                                          float alpha, View avatarContainer, float progress) {

        if (alpha <= 0.0f || progress <= 0.0f) return;

        final float ax = avatarContainer.getX();
        final float ay = avatarContainer.getY();
        final float aw = avatarContainer.getWidth() * avatarContainer.getScaleX();
        final float ah = avatarContainer.getHeight() * avatarContainer.getScaleY();

        final float acx = ax + aw / 2.0f;
        final float acy = ay + ah / 2.0f;
        final float centerX = w / 2.0f;
        float centerY = progress < 1f ? stableY : acy;

        if (progress == 1f) {
            stableY = centerY;
        }

        for (int i = 3; i >= 0; i--) {
            float radiusDp = orbitConfigs[i][0];
            float sizeDp = orbitConfigs[i][1];
            float orbitAlpha = orbitConfigs[i][2];
            float startProgress = progressThresholds[i][0];
            float endProgress = progressThresholds[i][1];

            float factor;
            if (progress >= startProgress) {
                factor = 1.0f;
            } else if (progress <= endProgress) {
                factor = 0.0f;
            } else {
                factor = (progress - endProgress) / (startProgress - endProgress);
            }

            factor = CubicBezierInterpolator.EASE_OUT.getInterpolation(factor);

            centerY = AndroidUtilities.lerp(stableY, acy, 1f - factor);

            float currentRadius = radiusDp * factor;
            float alphaFactor = factor;
            float scaleFactor = factor;
            if (i == 0) {
                alphaFactor = Math.min(factor * 1.4f, 1f);
            } else if (i == 1) {
                alphaFactor = Math.min(factor * 3f, 1f);
            } else if (i == 2) {
                alphaFactor = Math.min(factor * 3f, 1f);
            } else {
                alphaFactor = Math.min(factor * 4f, 1f);
            }
            scaleFactor = alphaFactor;
            sizeDp = sizeDp * scaleFactor;
            float currentAlpha = orbitAlpha * alphaFactor * alpha;

            if (currentAlpha <= 0 || currentRadius <= 0) continue;

            switch (i) {
                case 0:
                    drawHexagonOrbit(canvas, pattern, centerX, centerY,
                            currentRadius, factor, sizeDp,
                            currentAlpha, true, 6, 1.12f, 1f);
                    break;
                case 1:
                    drawRectangleOrbit(canvas, pattern, centerX, centerY,
                            currentRadius, factor, sizeDp,
                            currentAlpha, 1.0f);
                    break;
                case 2:
                    drawPointsOrbit(canvas, pattern, centerX, centerY,
                            currentRadius, sizeDp,
                            currentAlpha, 1f);
                    break;
                case 3:
                    drawHexagonOrbit(canvas, pattern, centerX, centerY,
                            currentRadius, 1f, sizeDp,
                            currentAlpha, false, 6, 0.7f, 0.7f);
                    break;
            }
        }
    }

    private static void drawHexagonOrbit(Canvas canvas, Drawable pattern,
                                         float centerX, float centerY,
                                         float radius, float factor, float sizeDp,
                                         float totalAlpha,
                                         boolean pointyTop, int points,
                                         float flattening, float flatteningOther) {
        final float radiusPx = dpf2(radius);
        final float sizePx = dpf2(sizeDp);
        final double startAngle = pointyTop ? Math.PI / 2 : 0;
        final double angleStep = 2 * Math.PI / points;

        for (int i = 0; i < points; i++) {
            double angle = startAngle + i * angleStep;
            float x, y;

            if (pointyTop) {
                float addR = 0f;
                if (i == 4 || i == 2) {
                    addR = AndroidUtilities.dp(sizeDp) * (1f - factor);
                }
                x = centerX + (float) ((radiusPx - addR) * Math.cos(angle) * flattening);
                y = centerY - (float) (radiusPx * Math.sin(angle) * flatteningOther);
            } else {
                float fOther = flatteningOther;
                if (i == 0 || i == points / 2) {
                    fOther = 1f;
                }
                x = centerX + (float) (radiusPx * Math.cos(angle) * fOther);
                y = centerY - (float) (radiusPx * Math.sin(angle) * flattening);
            }

            drawPatternAt(canvas, pattern, x, y, sizePx, totalAlpha);
        }
    }

    private static final float[][] corners = new float[4][2];
    private static void drawRectangleOrbit(Canvas canvas, Drawable pattern,
                                           float centerX, float centerY,
                                           float radius, float factor, float sizeDp,
                                           float totalAlpha, float flattening) {
        final float sizePx = dpf2(sizeDp);
        final float width = dpf2(radius);
        final float height = dpf2(radius * 0.5f);

        float addMult = AndroidUtilities.lerp(1f, 1.2f, 1f - factor);

        corners[0][0] = width * flattening;
        corners[0][1] = -height;
        corners[1][0] = -width * flattening;
        corners[1][1] = -height;
        corners[2][0] = -width * flattening;
        corners[2][1] = height;
        corners[3][0] = width * flattening * addMult;
        corners[3][1] = height * addMult;

        for (float[] corner : corners) {
            float x = centerX + corner[0];
            float y = centerY + corner[1];
            drawPatternAt(canvas, pattern, x, y, sizePx, totalAlpha);
        }
    }

    private static final float[][] pointsOrbitCorners = new float[2][2];

    private static void drawPointsOrbit(Canvas canvas, Drawable pattern,
                                           float centerX, float centerY,
                                           float radius, float sizeDp,
                                           float totalAlpha, float flattening) {
        final float sizePx = dpf2(sizeDp);
        final float width = dpf2(radius);

        pointsOrbitCorners[0][0] = width * flattening;
        pointsOrbitCorners[0][1] = 0;
        pointsOrbitCorners[1][0] = -width * flattening;
        pointsOrbitCorners[1][1] = 0;

        for (float[] corner : pointsOrbitCorners) {
            float x = centerX + corner[0];
            float y = centerY + corner[1];
            drawPatternAt(canvas, pattern, x, y, sizePx, totalAlpha);
        }
    }

    private static void drawPatternAt(Canvas canvas, Drawable pattern,
                                      float x, float y, float sizePx,
                                      float totalAlpha) {
        int alphaInt = (int) (255 * totalAlpha);
        if (alphaInt <= 0) return;
        final float halfSize = sizePx / 2;
        pattern.setBounds(
            (int) (x - halfSize),
            (int) (y - halfSize),
            (int) (x + halfSize),
            (int) (y + halfSize)
        );
        pattern.setAlpha(alphaInt);
        pattern.draw(canvas);
    }

    private static float dpf2(float dp) {
        return dp * AndroidUtilities.density;
    }
}
