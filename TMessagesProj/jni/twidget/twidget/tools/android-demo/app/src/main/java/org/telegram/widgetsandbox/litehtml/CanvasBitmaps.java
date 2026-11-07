package org.telegram.widgetsandbox.litehtml;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.Base64;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONObject;

/** Bitmaps for <canvas> elements, run from TwidgetCore's canvas op batches (src/canvas_ops.h, to_json). */
final class CanvasBitmaps {

    interface Source {
        Paint font(int id);

        Bitmap image(int id);
    }

    private static final class Target {
        final Bitmap bitmap;
        final Canvas canvas;

        Target(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.canvas = new Canvas(bitmap);
        }
    }

    private static final float[] IDENTITY = {1, 0, 0, 1, 0, 0};
    private static final float[] TEXT_PLACEMENT = {0, 0, 1, 0};

    private final Source source;
    private final SparseArray<Target> targets = new SparseArray<>();
    private final Path path = new Path();
    private final Matrix user = new Matrix();
    private final Matrix inverse = new Matrix();
    private final float[] values = new float[9];

    CanvasBitmaps(Source source) {
        this.source = source;
    }

    Bitmap bitmap(int id) {
        Target target = targets.get(id);
        return target == null ? null : target.bitmap;
    }

    /** jni.cpp → canvasRun: a w × h bitmap, created on the first call, runs the ops. */
    void run(int id, int w, int h, String json) {
        if (w <= 0 || h <= 0) {
            release(id);
            return;
        }
        Target target = targets.get(id);
        if (target == null || target.bitmap.getWidth() != w || target.bitmap.getHeight() != h) {
            if (target != null) {
                target.bitmap.recycle();
            }
            target = new Target(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
            targets.put(id, target);
        }
        JSONArray ops;
        try {
            ops = new JSONArray(json);
        } catch (Exception e) {
            return;
        }
        for (int i = 0; i < ops.length(); i++) {
            JSONObject op = ops.optJSONObject(i);
            if (op != null) {
                op(target, id, op);
            }
        }
    }

    /** jni.cpp → canvasRead: straight RGBA, transparent outside the bitmap. */
    byte[] read(int id, int x, int y, int w, int h) {
        byte[] out = new byte[Math.max(w, 0) * Math.max(h, 0) * 4];
        Bitmap bitmap = bitmap(id);
        if (bitmap == null) {
            return out;
        }
        int x0 = Math.max(0, x), y0 = Math.max(0, y);
        int x1 = Math.min(bitmap.getWidth(), x + w), y1 = Math.min(bitmap.getHeight(), y + h);
        if (x1 <= x0 || y1 <= y0) {
            return out;
        }
        int[] pixels = new int[(x1 - x0) * (y1 - y0)];
        // getPixels answers unpremultiplied colours, which is what getImageData wants.
        bitmap.getPixels(pixels, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0);
        for (int row = y0; row < y1; row++) {
            for (int col = x0; col < x1; col++) {
                int c = pixels[(row - y0) * (x1 - x0) + (col - x0)];
                int o = ((row - y) * w + (col - x)) * 4;
                out[o] = (byte) Color.red(c);
                out[o + 1] = (byte) Color.green(c);
                out[o + 2] = (byte) Color.blue(c);
                out[o + 3] = (byte) Color.alpha(c);
            }
        }
        return out;
    }

    void release(int id) {
        Target target = targets.get(id);
        if (target != null) {
            target.bitmap.recycle();
            targets.remove(id);
        }
    }

    void releaseAll() {
        for (int i = 0; i < targets.size(); i++) {
            targets.valueAt(i).bitmap.recycle();
        }
        targets.clear();
    }

    private void op(Target target, int id, JSONObject op) {
        Canvas canvas = target.canvas;
        int kind = op.optInt("k");
        if (kind == 0) { // Reset
            target.bitmap.eraseColor(Color.TRANSPARENT);
            return;
        }
        if (kind == 6) { // PutImage: raw pixels, outside drawing
            putImage(target, op);
            return;
        }
        canvas.save();
        JSONArray clips = op.optJSONArray("cl");
        if (clips != null) {
            for (int i = 0; i < clips.length(); i++) {
                JSONObject clip = clips.optJSONObject(i);
                if (clip != null) {
                    canvas.clipPath(Paths.readPath(clip, new Path()));
                }
            }
        }
        if (kind == 3) { // Clear
            Paint clear = new Paint();
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawPath(Paths.readPath(op, path), clear);
            canvas.restore();
            return;
        }
        float alpha = Math.max(0f, Math.min(1f, (float) op.optDouble("a", 1.0)));
        int composite = op.optInt("cm");
        JSONArray shadow = op.optJSONArray("sh");
        if (shadow != null && op.has("shc")) {
            // The shadow is cast by the whole operation in canvas px, under it: draw the content once into a
            // scratch bitmap, then its blurred alpha at the offset, then the content.
            Bitmap scratch = Bitmap.createBitmap(target.bitmap.getWidth(), target.bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            content(new Canvas(scratch), id, op, new Paint(Paint.ANTI_ALIAS_FLAG));
            Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
            float sigma = (float) shadow.optDouble(2) / 2f;
            if (sigma > 0.5f) {
                blur.setMaskFilter(new BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL));
            }
            int[] offset = new int[2];
            Bitmap mask = scratch.extractAlpha(blur, offset);
            Paint layer = new Paint(Paint.ANTI_ALIAS_FLAG);
            layer.setAlpha((int) (alpha * 255));
            Paths.composite(layer, composite);
            canvas.saveLayer(null, layer);
            Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tint.setColor(Paths.argb(op, "shc"));
            canvas.drawBitmap(mask, offset[0] + (float) shadow.optDouble(0), offset[1] + (float) shadow.optDouble(1), tint);
            canvas.drawBitmap(scratch, 0f, 0f, null);
            canvas.restore();
            mask.recycle();
            scratch.recycle();
        } else if (alpha < 1f || composite != 0) {
            // A layer applies alpha and the mode to the operation as a whole, as the canvas does.
            Paint layer = new Paint();
            layer.setAlpha((int) (alpha * 255));
            Paths.composite(layer, composite);
            canvas.saveLayer(null, layer);
            content(canvas, id, op, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            canvas.restore();
        } else {
            content(canvas, id, op, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
        }
        canvas.restore();
    }

    private static void putImage(Target target, JSONObject op) {
        int w = op.optInt("w"), h = op.optInt("h");
        byte[] bytes;
        try {
            bytes = Base64.decode(op.optString("px"), Base64.DEFAULT);
        } catch (Exception e) {
            return;
        }
        if (w <= 0 || h <= 0 || bytes.length != w * h * 4) {
            return;
        }
        int dx = (int) op.optDouble("x"), dy = (int) op.optDouble("y");
        int x0 = Math.max(0, dx), y0 = Math.max(0, dy);
        int x1 = Math.min(target.bitmap.getWidth(), dx + w), y1 = Math.min(target.bitmap.getHeight(), dy + h);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        int[] pixels = new int[(x1 - x0) * (y1 - y0)];
        for (int row = y0; row < y1; row++) {
            for (int col = x0; col < x1; col++) {
                int o = ((row - dy) * w + (col - dx)) * 4;
                pixels[(row - y0) * (x1 - x0) + (col - x0)] = Color.argb(bytes[o + 3] & 0xff, bytes[o] & 0xff, bytes[o + 1] & 0xff, bytes[o + 2] & 0xff);
            }
        }
        // setPixels takes unpremultiplied colours: putImageData's straight alpha.
        target.bitmap.setPixels(pixels, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0);
    }

    /** One operation's content, at full alpha and source-over, on `canvas` whose state it restores. */
    private void content(Canvas canvas, int id, JSONObject op, Paint paint) {
        JSONArray matrixJson = op.optJSONArray("m");
        float[] m = matrixJson == null ? IDENTITY : Paths.floats(matrixJson, 6);
        values[0] = m[0];
        values[1] = m[2];
        values[2] = m[4];
        values[3] = m[1];
        values[4] = m[3];
        values[5] = m[5];
        values[6] = 0f;
        values[7] = 0f;
        values[8] = 1f;
        user.setValues(values);
        canvas.save();
        int kind = op.optInt("k");
        if (kind == 1 || kind == 2) { // Fill, Stroke
            // The path is in canvas px; paints and line widths are in user space, so draw it back there.
            if (user.invert(inverse)) {
                Path shape = Paths.readPath(op, path);
                shape.transform(inverse);
                canvas.concat(user);
                style(paint, op);
                if (kind == 2) {
                    paint.setStyle(Paint.Style.STROKE);
                    Paths.lineStyle(paint, op, (float) op.optDouble("lw", 1.0), 10f);
                }
                canvas.drawPath(shape, paint);
            }
        } else if (kind == 4) { // Text
            Paint font = source.font(op.optInt("f"));
            JSONArray placement = op.optJSONArray("tp");
            float[] tp = placement == null ? TEXT_PLACEMENT : Paths.floats(placement, 4);
            if (font != null) {
                Paint text = new Paint(font);
                canvas.concat(user);
                canvas.translate(tp[0], tp[1]);
                canvas.scale(tp[2], 1f);
                style(text, op);
                if (op.optInt("st") != 0) {
                    text.setStyle(Paint.Style.STROKE);
                    Paths.lineStyle(text, op, (float) op.optDouble("lw", 1.0), 10f);
                }
                canvas.drawText(op.optString("t"), tp[3], 0f, text);
            }
        } else if (kind == 5) { // Image
            Bitmap image = op.has("cv") ? bitmap(op.optInt("cv")) : source.image(op.optInt("i"));
            if (image != null) {
                // Drawing a canvas onto itself reads what it held before the draw.
                Bitmap copy = op.has("cv") && op.optInt("cv") == id ? image.copy(Bitmap.Config.ARGB_8888, false) : null;
                if (copy != null) {
                    image = copy;
                }
                float[] r = Paths.floats(op.optJSONArray("ir"), 8);
                canvas.concat(user);
                paint.setFilterBitmap(op.optInt("ns") == 0);
                Rect src = r[2] < 0 ? null : new Rect((int) r[0], (int) r[1], (int) (r[0] + r[2]), (int) (r[1] + r[3]));
                canvas.drawBitmap(image, src, new RectF(r[4], r[5], r[4] + r[6], r[5] + r[7]), paint);
                if (copy != null) {
                    copy.recycle();
                }
            }
        }
        canvas.restore();
    }

    /** The op's fill or stroke style, in user space. */
    private static void style(Paint paint, JSONObject op) {
        int kind = op.optInt("pk");
        JSONArray stopsJson = op.optJSONArray("s");
        Paths.Stops stops = stopsJson == null ? null : new Paths.Stops(stopsJson);
        if (kind == 0 || stops == null || stops.isEmpty()) {
            paint.setShader(null);
            paint.setColor(Paths.argb(op, "c"));
            return;
        }
        float[] a = Paths.floats(op.optJSONArray("pa"), 6);
        paint.setColor(Color.BLACK);
        if (kind == 1) {
            paint.setShader(new LinearGradient(a[0], a[1], a[2], a[3], stops.shaderColors(), stops.shaderOffsets(), Shader.TileMode.CLAMP));
        } else if (kind == 2) {
            // Android's radial gradient starts at radius 0 around the outer circle's centre: an inner
            // circle squeezes the stops outwards, as on Direct2D.
            float r0 = a[2], r1 = Math.max(a[5], 0.01f);
            float[] offsets = stops.shaderOffsets();
            if (r0 > 0 && r0 < r1) {
                float[] squeezed = new float[offsets.length];
                for (int i = 0; i < offsets.length; i++) {
                    squeezed[i] = r0 / r1 + offsets[i] * (1 - r0 / r1);
                }
                offsets = squeezed;
            }
            paint.setShader(new RadialGradient(a[3], a[4], r1, stops.shaderColors(), offsets, Shader.TileMode.CLAMP));
        } else {
            SweepGradient sweep = new SweepGradient(a[1], a[2], stops.shaderColors(), stops.shaderOffsets());
            Matrix rotation = new Matrix();
            rotation.setRotate((float) Math.toDegrees(a[0]), a[1], a[2]);
            sweep.setLocalMatrix(rotation);
            paint.setShader(sweep);
        }
    }
}
