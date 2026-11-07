package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import org.telegram.messenger.AndroidUtilities;

/**
 * Torn-paper edge generator.
 *
 * <p>The edge is a 1D height field y(x): one unsmoothed random sample every
 * {@code stepDp}, which reads as paper fibre. Neighbouring samples are uncorrelated on
 * purpose — a smoothed field reads as a wave, not as a tear.
 *
 * <p>The integer hash matches torn-edge-tuner.html byte for byte, so parameters picked
 * in the tuner reproduce exactly here. Sample index k equals xDp / stepDp, which makes
 * the shape density independent.
 *
 * <p>y(x) depends only on the absolute x, never on the requested width, so a bitmap
 * generated once for the widest bubble crops exactly to any narrower one.
 */
public final class TornEdge {

    private TornEdge() {}

    // ---------------------------------------------------------------- params

    public static final class Params {
        public int seed = 1337;

        public float stepDp = 2.5f;        // distance between samples
        public float jitterDp = 1.9f;      // fibre amplitude; the tear reaches exactly ±this

        public float maxDeviationDp() {
            return jitterDp;
        }

        /** Vertical room a torn edge needs on each side of its baseline. */
        public int paddingPx() {
            return (int) Math.ceil(px(maxDeviationDp()));
        }
    }

    /**
     * dp -> px in exact float. Deliberately not AndroidUtilities.dp(), which ceils to
     * whole pixels: rounding step or jitter before the field is evaluated would shift
     * the contour away from what the tuner shows.
     */
    private static float px(float dp) {
        return dp * AndroidUtilities.density;
    }

    // ------------------------------------------------------------ noise core

    private static int hash(int x, int seed) {
        int h = x * 374761393 + seed * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static double rand01(int x, int seed) {
        return (hash(x, seed) >>> 8) / 16777216.0;
    }

    // -------------------------------------------------------------- profile

    /**
     * Deviation from the baseline, in px, for every sample along the edge.
     * Positive points down, i.e. into the shape for a top edge.
     * Computed in double so the result is identical to the JS tuner.
     */
    public static float[] profile(Params p, int widthPx, int seed) {
        final float step = Math.max(1f, px(p.stepDp));
        final float jitter = px(p.jitterDp);
        final int n = (int) Math.ceil(widthPx / step) + 1;
        final float[] out = new float[n];
        for (int k = 0; k < n; k++) {
            out[k] = (float) ((rand01(k, seed ^ 0x9E37) - 0.5) * 2.0 * jitter);
        }
        return out;
    }

    // ----------------------------------------------------------------- paths

    /**
     * Closed path of a horizontal slab. A null profile means a straight edge.
     * Baselines are in the coordinate space of the target canvas.
     */
    public static Path buildSlabPath(Params p, float width,
                                     float topBaseline, float bottomBaseline,
                                     float[] topProfile, float[] bottomProfile) {
        final float step = Math.max(1f, px(p.stepDp));
        final Path path = new Path();

        if (topProfile != null) {
            for (int k = 0; k < topProfile.length; k++) {
                float x = Math.min(k * step, width);
                float y = topBaseline + topProfile[k];
                if (k == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
        } else {
            path.moveTo(0, topBaseline);
            path.lineTo(width, topBaseline);
        }

        if (bottomProfile != null) {
            for (int k = bottomProfile.length - 1; k >= 0; k--) {
                path.lineTo(Math.min(k * step, width), bottomBaseline + bottomProfile[k]);
            }
        } else {
            path.lineTo(width, bottomBaseline);
            path.lineTo(0, bottomBaseline);
        }

        path.close();
        return path;
    }

    // --------------------------------------------------------------- drawing

    /**
     * Draws the slab straight into an existing canvas. Prefer this over a bitmap when
     * the geometry is stable, but cache the Paths — a few hundred lineTo segments
     * re-tessellated every frame is exactly the kind of load that shows up as
     * PathStencilCoverOp in a systrace.
     *
     * <p>When the canvas is backed by an ALPHA_8 bitmap only the alpha of {@code color}
     * survives; the RGB is discarded.
     */
    public static void draw(Canvas canvas, Params p, float width,
                            float topBaseline, float bottomBaseline,
                            int color, float cornerRadius,
                            float[] topProfile, float[] bottomProfile) {
        Path body = buildSlabPath(p, width, topBaseline, bottomBaseline, topProfile, bottomProfile);

        if (cornerRadius > 0) {
            Path clip = new Path();
            RectF bounds = new RectF();
            body.computeBounds(bounds, true);
            clip.addRoundRect(new RectF(0, bounds.top, width, bounds.bottom),
                    cornerRadius, cornerRadius, Path.Direction.CW);
            body.op(clip, Path.Op.INTERSECT); // API 19+, antialiases better than clipPath
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        canvas.drawPath(body, paint);
    }

    // --------------------------------------------------------------- bitmaps

    /**
     * One tear line as an ALPHA_8 coverage mask, packed into the smallest bitmap that
     * can hold both of its fragments. Height is derived from the params — the caller
     * never supplies it, and no colour is stored.
     *
     * <p>The bitmap holds both shapes a tear can take, one per half:
     *
     * <pre>
     *   row 0        ╱╲╱‾╲╱╲   ← tear, material below it  (top-edge shape)
     *                ████████
     *   row band-1   ████████
     *   row band     ████████
     *                ████████
     *   row 2band-1  ╲╱╲_╱╲╱   ← same tear, material above (bottom-edge shape)
     * </pre>
     *
     * Both halves use the same profile, so the two shapes interlock exactly: whatever
     * one removes, the other supplies. The rows in the middle are opaque whatever the
     * params, so a fragment can be extended inwards by simply taking more rows.
     *
     * <p>At 1.9 dp jitter and ×3 density this is 26 px tall — about 28 KB for a
     * 1080 px wide mask, against 112 KB for the same thing in ARGB_8888.
     */
    public static Bitmap createTearBitmap(Params p, int width, int seed) {
        final int pad = p.paddingPx();
        final int height = bitmapHeight(p);
        final float[] prof = profile(p, width, seed);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(bitmap);
        draw(canvas, p, width, pad, height - pad, Color.BLACK, 0, prof, prof);
        return bitmap;
    }

    /** Height of one fragment band, i.e. the row where the two fragments are split. */
    public static int fragmentHeight(Params p) {
        return p.paddingPx() * 2 + solidGuardPx();
    }

    /** Total bitmap height for the given params. */
    public static int bitmapHeight(Params p) {
        return fragmentHeight(p) * 2;
    }

    private static int solidGuardPx() {
        return Math.max(1, (int) Math.ceil(px(0.5f)));
    }

    // -------------------------------------------------------------- stamping

    /**
     * Stamps the bottom half of the mask — material above the tear — into the band
     * starting at {@code baselineY} and running down one fragment height.
     * The tear itself lands {@code paddingPx + 1} px below {@code baselineY}.
     *
     * <p>{@code width} may be smaller than the bitmap: y(x) ignores the requested width,
     * so one bitmap generated at the widest bubble crops exactly to any narrower one.
     */
    public static void drawTopEdge(Canvas canvas, Bitmap tear, Params p, int width,
                                   float left, float baselineY, Paint paint) {
        final int band = fragmentHeight(p);
        stamp(canvas, tear, width, left, baselineY, band, band, paint);
    }

    /**
     * Stamps the top half of the mask — material below the tear — into the band ending
     * at {@code baselineY}. The tear lands {@code paddingPx + 1} px above it.
     */
    public static void drawBottomEdge(Canvas canvas, Bitmap tear, Params p, int width,
                                      float left, float baselineY, Paint paint) {
        final int band = fragmentHeight(p);
        stamp(canvas, tear, width, left, baselineY - band, 0, band, paint);
    }

    private static void stamp(Canvas canvas, Bitmap tear, int width, float left, float top,
                              int srcTop, int srcHeight, Paint paint) {
        final int w = Math.min(width, tear.getWidth());
        final Rect src = new Rect(0, srcTop, w, srcTop + srcHeight);
        final Rect dst = new Rect((int) left, (int) top, (int) left + w, (int) top + srcHeight);
        canvas.drawBitmap(tear, src, dst, paint);
    }
}