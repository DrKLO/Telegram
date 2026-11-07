package org.telegram.widgetsandbox.litehtml;

import android.graphics.BlendMode;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Readers for the JSON forms of TwidgetCore's display lists and canvas batches (src/encode.cpp). */
final class Paths {

    private Paths() {
    }

    /** Builds `{"o": op codes, "v": points, "e": evenodd}` (0 move, 1 line, 2 quad, 3 cubic, 4 close) into `into`. */
    static Path readPath(JSONObject json, Path into) {
        into.rewind();
        JSONArray ops = json.optJSONArray("o");
        if (ops == null) {
            return into;
        }
        JSONArray v = json.optJSONArray("v");
        if (v == null) {
            v = new JSONArray();
        }
        int i = 0;
        for (int n = 0; n < ops.length(); n++) {
            int op = ops.optInt(n);
            int need = op == 3 ? 6 : op == 2 ? 4 : op == 4 ? 0 : 2;
            if (i + need > v.length()) {
                break;
            }
            switch (op) {
                case 0:
                    into.moveTo(f(v, i), f(v, i + 1));
                    break;
                case 1:
                    into.lineTo(f(v, i), f(v, i + 1));
                    break;
                case 2:
                    into.quadTo(f(v, i), f(v, i + 1), f(v, i + 2), f(v, i + 3));
                    break;
                case 3:
                    into.cubicTo(f(v, i), f(v, i + 1), f(v, i + 2), f(v, i + 3), f(v, i + 4), f(v, i + 5));
                    break;
                case 4:
                    into.close();
                    break;
            }
            i += need;
        }
        into.setFillType(json.optInt("e") != 0 ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
        return into;
    }

    /** A rectangle, `[x, y, w, h]`, into `out`; empty when absent. */
    static RectF readRect(JSONObject json, String key, RectF out) {
        JSONArray r = json.optJSONArray(key);
        if (r == null) {
            out.setEmpty();
            return out;
        }
        float x = f(r, 0), y = f(r, 1);
        out.set(x, y, x + f(r, 2), y + f(r, 3));
        return out;
    }

    static float[] floats(JSONArray array, int count) {
        float[] out = new float[count];
        for (int i = 0; array != null && i < count; i++) {
            out[i] = f(array, i);
        }
        return out;
    }

    static float[] floats(JSONArray array) {
        return floats(array, array == null ? 0 : array.length());
    }

    static float f(JSONArray array, int index) {
        return (float) array.optDouble(index);
    }

    /** ARGB as the JSON writes it (an unsigned 32-bit number). */
    static int argb(JSONObject json, String key) {
        return (int) json.optLong(key);
    }

    /** Stops as offset, ARGB pairs. */
    static final class Stops {
        final int[] colors;
        final float[] offsets;

        Stops(JSONArray s) {
            int n = s.length() / 2;
            colors = new int[n];
            offsets = new float[n];
            for (int i = 0; i < n; i++) {
                colors[i] = (int) s.optLong(i * 2 + 1);
                offsets[i] = Math.max(0f, Math.min(1f, (float) s.optDouble(i * 2)));
            }
        }

        boolean isEmpty() {
            return colors.length == 0;
        }

        /** Android gradients need two colours at least. */
        int[] shaderColors() {
            return colors.length == 1 ? new int[]{colors[0], colors[0]} : colors;
        }

        float[] shaderOffsets() {
            return offsets.length == 1 ? new float[]{0f, 1f} : offsets;
        }
    }

    static void lineStyle(Paint paint, JSONObject json, float width, float defaultMiter) {
        paint.setStrokeWidth(width);
        int cap = json.optInt("lc");
        paint.setStrokeCap(cap == 1 ? Paint.Cap.ROUND : cap == 2 ? Paint.Cap.SQUARE : Paint.Cap.BUTT);
        int join = json.optInt("lj");
        paint.setStrokeJoin(join == 1 ? Paint.Join.ROUND : join == 2 ? Paint.Join.BEVEL : Paint.Join.MITER);
        paint.setStrokeMiter((float) json.optDouble("ml", defaultMiter));
        float[] dashes = floats(json.optJSONArray("d"));
        boolean any = false;
        for (float d : dashes) {
            any |= d > 0;
        }
        if (any) {
            // DashPathEffect wants an even count; the canvas and SVG repeat an odd list twice.
            float[] even = dashes;
            if (dashes.length % 2 == 1) {
                even = new float[dashes.length * 2];
                System.arraycopy(dashes, 0, even, 0, dashes.length);
                System.arraycopy(dashes, 0, even, dashes.length, dashes.length);
            }
            paint.setPathEffect(new DashPathEffect(even, (float) json.optDouble("do")));
        } else {
            paint.setPathEffect(null);
        }
    }

    /** globalCompositeOperation, in the order of Composite in canvas_ops.h. */
    static void composite(Paint paint, int index) {
        if (Build.VERSION.SDK_INT >= 29) {
            BlendMode[] modes = {
                BlendMode.SRC_OVER, BlendMode.SRC_IN, BlendMode.SRC_OUT, BlendMode.SRC_ATOP, BlendMode.DST_OVER, BlendMode.DST_IN,
                BlendMode.DST_OUT, BlendMode.DST_ATOP, BlendMode.PLUS, BlendMode.SRC, BlendMode.XOR, BlendMode.MULTIPLY, BlendMode.SCREEN,
                BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN, BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN, BlendMode.HARD_LIGHT,
                BlendMode.SOFT_LIGHT, BlendMode.DIFFERENCE, BlendMode.EXCLUSION, BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR,
                BlendMode.LUMINOSITY,
            };
            paint.setBlendMode(index == 0 || index >= modes.length ? null : modes[index]);
            return;
        }
        // Before API 29 only the Porter-Duff modes and a few separable blends exist; the rest draw as source-over.
        PorterDuff.Mode[] modes = {
            null, PorterDuff.Mode.SRC_IN, PorterDuff.Mode.SRC_OUT, PorterDuff.Mode.SRC_ATOP, PorterDuff.Mode.DST_OVER, PorterDuff.Mode.DST_IN,
            PorterDuff.Mode.DST_OUT, PorterDuff.Mode.DST_ATOP, PorterDuff.Mode.ADD, PorterDuff.Mode.SRC, PorterDuff.Mode.XOR,
            PorterDuff.Mode.MULTIPLY, PorterDuff.Mode.SCREEN, PorterDuff.Mode.OVERLAY, PorterDuff.Mode.DARKEN, PorterDuff.Mode.LIGHTEN,
        };
        PorterDuff.Mode mode = index < modes.length ? modes[index] : null;
        paint.setXfermode(mode == null ? null : new PorterDuffXfermode(mode));
    }
}
