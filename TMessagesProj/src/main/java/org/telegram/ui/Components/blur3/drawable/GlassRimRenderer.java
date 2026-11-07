package org.telegram.ui.Components.blur3.drawable;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.SweepGradient;
import android.os.Build;
import android.util.LruCache;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.blur3.utils.NinePatchBuilder;

/**
 * Draws a {@link GlassRim} around a rounded rect. Each layer (the top crescent, the bottom one, the ring) is a white mask
 * baked once per geometry into a nine-patch: the corners as they are, the straight parts stretched (along a straight edge
 * a crescent of a shifted copy keeps its thickness, so the stretch is exact for any light angle). The masks are shared by
 * all glass through a cache; a renderer remembers the ones it draws, so a frame with the same geometry looks nothing up.
 * The color is applied when drawn (a source-in filter), so colors, themes and alpha never bake anything.
 * <p>
 * A side too short to slice (a circle, a shape still growing out of a button) is baked at its exact size.
 */
public final class GlassRimRenderer {
    private static final int TOP = 0, BOTTOM = 1, RING = 2;
    private static final int LAYERS = 3;

    private static final int CACHE_BYTES = 4 * 1024 * 1024;
    private static final LruCache<Long, Entry> cache = new LruCache<Long, Entry>(CACHE_BYTES) {
        @Override
        protected int sizeOf(Long key, Entry value) {
            return value.bytes;
        }
    };

    private static final class Entry {
        final NinePatch patch;
        final int pad;
        final int bytes;

        Entry(NinePatch patch, int pad, int bytes) {
            this.patch = patch;
            this.pad = pad;
            this.bytes = bytes;
        }
    }

    private final long[] keys = new long[LAYERS];
    private final Entry[] entries = new Entry[LAYERS];
    private final int[] filterColors = new int[LAYERS];
    private final PorterDuffColorFilter[] filters = new PorterDuffColorFilter[LAYERS];
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float[] radii = new float[8];
    private final Rect dst = new Rect();

    /** Draws the rim of the rounded rect (left, top, right, bottom; radii as in Path#addRoundRect), faded by alpha. */
    public void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom,
                     @NonNull float[] shapeRadii, @NonNull GlassRim rim, float alpha) {
        final float w = right - left, h = bottom - top;
        if (w < 1 || h < 1 || alpha <= 0) {
            return;
        }
        fitRadii(shapeRadii, w, h, radii);
        if (rim.hasRing()) {
            drawLayer(canvas, RING, left, top, right, bottom, rim, rim.ringColor, alpha);
        }
        if (rim.hasTop()) {
            drawLayer(canvas, TOP, left, top, right, bottom, rim, rim.topColor, alpha);
        }
        if (rim.hasBottom()) {
            drawLayer(canvas, BOTTOM, left, top, right, bottom, rim, rim.bottomColor, alpha);
        }
    }

    private void drawLayer(Canvas canvas, int layer, float left, float top, float right, float bottom, GlassRim rim, int color, float alpha) {
        final int w = Math.round(right - left), h = Math.round(bottom - top);
        final Geometry g = geometry(layer, rim, radii, w, h);
        final long key = g.key();
        Entry entry = entries[layer];
        if (entry == null || keys[layer] != key) {
            entry = cache.get(key);
            if (entry == null) {
                entry = bake(layer, rim, g);
                cache.put(key, entry);
            }
            keys[layer] = key;
            entries[layer] = entry;
        }
        final int opaque = color | 0xFF000000;
        if (filters[layer] == null || filterColors[layer] != opaque) {
            filterColors[layer] = opaque;
            filters[layer] = new PorterDuffColorFilter(opaque, PorterDuff.Mode.SRC_IN);
        }
        paint.setColorFilter(filters[layer]);
        paint.setAlpha(Math.round(Color.alpha(color) * Math.min(1f, alpha)));
        dst.set(Math.round(left) - entry.pad, Math.round(top) - entry.pad, Math.round(right) + entry.pad, Math.round(bottom) + entry.pad);
        entry.patch.draw(canvas, dst, paint);
    }

    /** Radii scaled down together where a side is shorter than its two corners, as a path's round rect does. */
    private static void fitRadii(float[] in, float w, float h, float[] out) {
        float scale = 1f;
        scale = fit(scale, w, in[0] + in[2]);
        scale = fit(scale, w, in[6] + in[4]);
        scale = fit(scale, h, in[1] + in[7]);
        scale = fit(scale, h, in[3] + in[5]);
        for (int i = 0; i < 8; i++) {
            out[i] = Math.max(0f, in[i]) * scale;
        }
    }

    private static float fit(float scale, float side, float sum) {
        return sum > side && sum > 0 ? Math.min(scale, side / sum) : scale;
    }

    /* Geometry of a layer, what its mask depends on */

    private static final Geometry tmpGeometry = new Geometry();

    private static final class Geometry {
        int layer;
        final int[] radii = new int[8]; // quarter pixels
        float offsetX, offsetY;         // the crescent's shift
        int cornerL, cornerT, cornerR, cornerB;
        int width, height;              // the baked content: the corners + 2 stretched px, or the exact side
        boolean sliceX, sliceY;
        int ringWidth;                  // sixteenth pixels
        int angle;                      // half degrees
        int ringTop, ringBottom, ringSides, ringExp; // thousandths

        long key() {
            long k = 0xcbf29ce484222325L;
            k = mix(k, layer);
            for (int r : radii) {
                k = mix(k, r);
            }
            k = mix(k, Float.floatToIntBits(offsetX));
            k = mix(k, Float.floatToIntBits(offsetY));
            k = mix(k, sliceX ? -1 : width);
            k = mix(k, sliceY ? -1 : height);
            k = mix(k, ringWidth);
            k = mix(k, angle);
            k = mix(k, ringTop);
            k = mix(k, ringBottom);
            k = mix(k, ringSides);
            k = mix(k, ringExp);
            return k;
        }

        private static long mix(long k, int v) {
            k ^= v;
            return k * 0x100000001b3L;
        }
    }

    private static Geometry geometry(int layer, GlassRim rim, float[] radii, int w, int h) {
        final Geometry g = tmpGeometry;
        g.layer = layer;
        for (int i = 0; i < 8; i++) {
            g.radii[i] = Math.round(radii[i] * 4);
        }
        final double a = Math.toRadians(rim.lightAngle);
        final float sin = (float) Math.sin(a), cos = (float) Math.cos(a);
        if (layer == RING) {
            g.offsetX = g.offsetY = 0;
            g.ringWidth = Math.round(rim.ringWidth * 16);
            g.angle = rim.isEvenRing() ? 0 : Math.round(rim.lightAngle * 2);
            g.ringTop = Math.round(rim.ringTop * 1000);
            g.ringBottom = Math.round(rim.ringBottom * 1000);
            g.ringSides = Math.round(rim.ringSides * 1000);
            g.ringExp = rim.isEvenRing() ? 0 : Math.round(rim.ringExp * 1000);
        } else {
            final float t = layer == TOP ? rim.topWidth : -rim.bottomWidth;
            // quantized: the same rim at slightly different subpixel widths is one mask
            g.offsetX = Math.round(sin * t * 8) / 8f;
            g.offsetY = Math.round(cos * t * 8) / 8f;
            g.ringWidth = g.angle = g.ringTop = g.ringBottom = g.ringSides = g.ringExp = 0;
        }
        final float ox = Math.abs(g.offsetX), oy = Math.abs(g.offsetY);
        g.cornerL = (int) Math.ceil(Math.max(radii[0], radii[6]) + ox) + 1;
        g.cornerR = (int) Math.ceil(Math.max(radii[2], radii[4]) + ox) + 1;
        g.cornerT = (int) Math.ceil(Math.max(radii[1], radii[3]) + oy) + 1;
        g.cornerB = (int) Math.ceil(Math.max(radii[5], radii[7]) + oy) + 1;
        g.sliceX = w >= g.cornerL + g.cornerR + 2;
        g.sliceY = h >= g.cornerT + g.cornerB + 2;
        g.width = g.sliceX ? g.cornerL + g.cornerR + 2 : w;
        g.height = g.sliceY ? g.cornerT + g.cornerB + 2 : h;
        return g;
    }

    /* Baking */

    private static Entry bake(int layer, GlassRim rim, Geometry g) {
        final int pad = layer == RING ? (int) Math.ceil(rim.ringWidth) + 2 : 1;
        final int bw = g.width + 2 * pad, bh = g.height + 2 * pad;
        final Bitmap bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(bitmap);
        final RectF e = new RectF(pad, pad, pad + g.width, pad + g.height);
        final float[] radii = new float[8];
        for (int i = 0; i < 8; i++) {
            radii[i] = g.radii[i] / 4f;
        }

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        if (layer == RING) {
            final float rw = rim.ringWidth;
            final RectF ring = new RectF(e);
            ring.inset(-rw / 2f, -rw / 2f);
            final float[] ringRadii = new float[8];
            for (int i = 0; i < 8; i++) {
                ringRadii[i] = radii[i] > 0 ? radii[i] + rw / 2f : 0;
            }
            final Path path = new Path();
            path.addRoundRect(ring, ringRadii, Path.Direction.CW);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(rw);
            if (!rim.isEvenRing()) {
                p.setShader(ringSweep(rim, e));
            } else {
                p.setAlpha(Math.round(255 * Math.min(1f, rim.ringSides)));
            }
            c.drawPath(path, p);
        } else {
            // the crescent: the shape with its copy shifted along the light clipped out
            final Path shape = new Path();
            shape.addRoundRect(e, radii, Path.Direction.CW);
            final Path shifted = new Path();
            shifted.addRoundRect(e.left + g.offsetX, e.top + g.offsetY, e.right + g.offsetX, e.bottom + g.offsetY, radii, Path.Direction.CW);
            c.save();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.clipOutPath(shifted);
            } else {
                c.clipPath(shifted, Region.Op.DIFFERENCE);
            }
            c.drawPath(shape, p);
            c.restore();
        }
        bitmap.prepareToDraw();

        final int x1 = g.sliceX ? pad + g.cornerL : pad + g.width / 2;
        final int y1 = g.sliceY ? pad + g.cornerT : pad + g.height / 2;
        final byte[] chunk = NinePatchBuilder.createNinePatchChunk(
            x1, x1 + (g.sliceX ? 2 : 1), y1, y1 + (g.sliceY ? 2 : 1),
            0, 0, 0, 0, NinePatchBuilder.NO_COLOR).array();
        return new Entry(new NinePatch(bitmap, chunk, null), pad, bitmap.getByteCount());
    }

    /** The ring's strength around the shape, a(θ) = edge + (sides − edge)·|cos θ|^exp, the light's edge up. */
    private static SweepGradient ringSweep(GlassRim rim, RectF e) {
        final int n = 73;
        final int[] colors = new int[n];
        final float[] positions = new float[n];
        for (int i = 0; i < n; i++) {
            final double th = Math.toRadians(i * 5);
            final float side = (float) Math.pow(Math.abs(Math.cos(th)), rim.ringExp);
            final float edge = Math.sin(th) < 0 ? rim.ringTop : rim.ringBottom; // + = down, away from the light
            final float a = Math.max(0f, Math.min(1f, edge + (rim.ringSides - edge) * side));
            colors[i] = (Math.round(255 * a) << 24) | 0xFFFFFF;
            positions[i] = i / (float) (n - 1);
        }
        final SweepGradient sweep = new SweepGradient(0, 0, colors, positions);
        final Matrix m = new Matrix();
        // up to the light: the light from the top-left turns the axis to the left
        m.setRotate(-rim.lightAngle);
        m.postScale(e.width() / Math.max(1f, e.height()), 1f);
        m.postTranslate(e.centerX(), e.centerY());
        sweep.setLocalMatrix(m);
        return sweep;
    }
}
