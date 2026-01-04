package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.google.zxing.common.detector.MathUtils;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.BatchParticlesDrawHelper;

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

    private static final BatchParticlesDrawHelper.BatchParticlesBuffer batchBuffer;
    static {
        short max = 0;
        for (float[] patternLocation : patternLocations) {
            max = (short) Math.max(max, patternLocation.length / 4);
        }
        batchBuffer = new BatchParticlesDrawHelper.BatchParticlesBuffer(max);
    }

    public static void drawPatternBatch(Canvas canvas, int type, Paint paint, Bitmap bitmap, float w, float h, float alpha, float scale) {
        if (alpha <= 0.0f) return;

        batchBuffer.fillParticleTextureCords(0, 0, bitmap.getWidth(), bitmap.getHeight());
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

            batchBuffer.setParticleVertexCords(i / 4, (dp(cx) - dp(sz) / 2.0f), (dp(cy) - dp(sz) / 2.0f), (dp(cx) + dp(sz) / 2.0f), (dp(cy) + dp(sz) / 2.0f));
            batchBuffer.setParticleColor(i / 4, ColorUtils.setAlphaComponent(Color.WHITE, (int) (0xFF * alpha * thisAlpha)));
        }

        BatchParticlesDrawHelper.draw(canvas, batchBuffer, patternLocations[type].length / 4, paint);
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

    public static void drawProfileAnimatedPattern(
        Canvas canvas,
        Drawable pattern,
        int w, float maxExpandY, float diff,
        View avatarContainer,
        float _alpha
    ) {
        AndroidUtilities.rectTmp.set(
            avatarContainer.getX(), avatarContainer.getY(),
            avatarContainer.getX() + avatarContainer.getWidth() * avatarContainer.getScaleX(),
            avatarContainer.getY() + avatarContainer.getHeight() * avatarContainer.getScaleY()
        );
        drawProfileAnimatedPattern(
            canvas,
            pattern,
            w,
            maxExpandY,
            diff,
            AndroidUtilities.rectTmp,
            _alpha
        );
    }

    public static void drawProfileAnimatedPattern(
        Canvas canvas,
        Drawable pattern,
        int w, float maxExpandY, float diff,
        RectF avatarContainer,
        float _alpha
    ) {
        if (diff <= 0.0f) return;
        float collapseDiff = diff;
        collapseDiff = collapseDiff >= 0.85f ? 1f : (collapseDiff / 0.85f);
        collapseDiff = Utilities.clamp01((collapseDiff - 0.2f) / 0.8f);

        final float realX = avatarContainer.left;
        final float realY = avatarContainer.top;
        final float realW = avatarContainer.width();
        final float realH = avatarContainer.height();

        final float realCX = realX + realW / 2.0f;
        final float realCY = realY + realH / 2.0f;

        final float sz = dpf2(96);
        final float ax = Math.min(realX, (w - sz) / 2f);
        final float ay = Math.max(realY, (maxExpandY - sz) / 2f);
        final float aw = Math.max(realW, sz);
        final float ah = Math.max(realH, sz);

        final float acx = ax + aw / 2.0f;
        final float acy = ay + ah / 2.0f;

        final float padding24 = dpf2(24);
        final float padding16 = dpf2(16);
        final float padding12 = dpf2(12);
        final float padding8 = dpf2(8);
        final float padding4 = dpf2(4);
        final float padding48 = padding24 * 2;
        //final float padding72 = padding48 + padding24;
        final float padding96 = padding48 * 2;
        final float r48Cos120 = (padding48 + aw / 2f) * (float) Math.cos(Math.toRadians(120));
        //final float r96Cos120 = (padding96 + aw / 2f) * (float) Math.cos(Math.toRadians(120));
        final float r16Cos160 = (padding16 + ah / 2f) * (float) Math.cos(Math.toRadians(160));
        //final float r56Cos150 = (padding48 + padding8 + ah / 2f) * (float) Math.cos(Math.toRadians(150));

        final float[] items = new float[] {
            // first row
            acx, ay - padding24, 20, // top
            acx, ay + ah + padding24, 20, // bottom
            ax - padding16, acy - ah / 4f - padding8, 23, // top left
            ax + aw + padding16, acy - ah / 4f - padding8, 18, // top right
            ax - padding16, acy + ah / 4f + padding8, 24, // bottom left
            ax + aw + padding16 - padding4, acy + ah / 4f + padding8, 24, // bottom right
            // second row
            ax - padding48, acy, 19, // left
            ax + aw + padding48, acy, 19, // right
            acx + r48Cos120, ay - padding48 + padding12, 17, // top left
            acx - r48Cos120, ay - padding48 + padding12, 17, // top right
            acx + r48Cos120, ay + ah + padding48 - padding12, 20, // bottom left
            acx - r48Cos120, ay + ah + padding48 - padding12, 20, // bottom right
            // third row
            //acx, ay - padding72, 19, // top
            //acx, ay + ah + padding72, 19, // bottom
            ax - padding48 - padding8, acy + r16Cos160, 20, // top left
            ax + aw + padding48 + padding8, acy + r16Cos160, 19, // top right
            ax - padding48 - padding8, acy - r16Cos160, 21, // bottom left
            ax + aw + padding48 + padding8, acy - r16Cos160, 18, // bottom right
            // 4th row
            ax - padding96, acy, 19, // left
            ax + aw + padding96, acy, 19, // right
            /*acx + r96Cos120, ay - padding96, 17, // top left
            acx - r96Cos120, ay - padding96, 17, // top right
            acx + r96Cos120, ay + ah + padding96, 18, // bottom left
            acx - r96Cos120, ay + ah + padding96, 18, // bottom right
            // 5th row
            acx, ay - padding96, 19, // top
            acx, ay + ah + padding96, 18, // bottom
            ax - padding72 - padding8, acy + r56Cos150, 19, // top left
            ax - padding72 - padding8, acy - r56Cos150, 20, // bottom left
            ax + aw + padding72 + padding8, acy - r56Cos150, 17, // bottom right
            ax + aw + padding72 + padding8, acy + r56Cos150, 18, // top right*/
        };

        //final float delayFraction = 0.15f;
        //final float maxDelayFraction = 3.80f * delayFraction;
        //final float intervalFraction = 1f - maxDelayFraction;

        //int row = 0;
        //int column = 0;
        //float delayValue = 0f;

        final float[] timings = new float[] {
            // first row
            0.02f, 0.42f,
            0.00f, 0.32f,
            0.00f, 0.40f,
            0.00f, 0.40f,
            0.00f, 0.40f,
            0.00f, 0.40f,
            // second row
            0.14f, 0.60f,
            0.16f, 0.64f,
            0.14f, 0.70f,
            0.14f, 0.90f,
            0.20f, 0.75f,
            0.20f, 0.85f,
            // third row
            //0.00f, 0.00f,
            //0.00f, 0.00f,
            0.09f, 0.45f,
            0.09f, 0.45f,
            0.09f, 0.45f,
            0.11f, 0.45f,
            // 4th row
            0.14f, 0.75f,
            0.20f, 0.80f,
            //1.00f, 1.00f,
            //1.00f, 1.00f,
            //1.00f, 1.00f,
            //1.00f, 1.00f,
        };

        for (int i = 0, t = 0; i < items.length; i += 3, t += 2) {
            float x = items[i];
            float y = items[i + 1];
            float r = dpf2(items[i + 2]) * 0.5f;

            final float start = timings[t];
            final float end = timings[t + 1];

            // final float delay = delayValue * delayFraction;
            // final float collapse = diff >= 1f - delay ? 1f : Utilities.clamp01((diff - maxDelayFraction + delay) / intervalFraction);
            float collapse = 1f - collapseDiff < start ? 1f : 1f - Utilities.clamp01((1f - collapseDiff - start) / (end - start));
            if (i == 18 || i == 19 || i == 6 || i == 7) {
                collapse = CubicBezierInterpolator.EASE_IN.getInterpolation(collapse);
            }
            y -= dp(12) * (1f - diff);
            if (collapse < 1f) {
                x = AndroidUtilities.lerp(realCX, x, CubicBezierInterpolator.EASE_IN.getInterpolation(collapse));
                y = AndroidUtilities.lerp(realCY, y, collapse);
                r = AndroidUtilities.lerp(dpf2(8), r, collapse);
            }

            final float bottomAlpha = y > ay + ah + dp(8) ? 1f - Utilities.clamp01((y - ay - ah - dp(8)) / dp(56)) : 1f;

            final float dist = 1f - Utilities.clamp01((MathUtils.distance(acx, acy, x, y) / (aw * 2)));
            float alpha = _alpha * dist * 0.5f * bottomAlpha;
            if (collapse < 1f) {
                alpha = AndroidUtilities.lerp(0f, alpha, collapse);
            }

            pattern.setBounds(
                (int) (x - r),
                (int) (y - r),
                (int) (x + r),
                (int) (y + r)
            );
            pattern.setAlpha((int) (0xFF * alpha));
            pattern.draw(canvas);

            /*column++;
            if (column == 6) {
                column = 0;
                row++;
                if (row <= 3) {
                    delayValue = row + (row * 0.25f);
                } else {
                    delayValue += 0.05f;
                }
            }*/
        }
    }
}
