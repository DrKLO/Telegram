package org.telegram.widgetsandbox.litehtml;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

import org.json.JSONArray;
import org.json.JSONObject;

/** Executes TwidgetCore display lists (src/display_list.h, to_json) on an android.graphics.Canvas. */
final class DisplayListPainter {

    /** Fonts, images and canvas bitmaps the painter draws with. */
    interface Resources {
        Paint font(int id);

        Bitmap image(int id);

        Bitmap canvas(int id);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint clear = new Paint();
    private final Path path = new Path();
    private final Path clipPath = new Path();
    private final RectF rectF = new RectF();
    private final RectF clipRectF = new RectF();
    private final float[] radii = new float[8];
    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];

    DisplayListPainter() {
        stroke.setStyle(Paint.Style.STROKE);
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    /**
     * Paints `list` on `canvas`, already scaled to CSS px. `density` is device px per CSS px: shadows are
     * blurred into bitmaps at that resolution. With `damage` (LitehtmlNative.damage, CSS px), only those
     * areas are cleared and painted again on a canvas that keeps the last frame; null paints everything.
     */
    void paint(Canvas canvas, JSONArray list, Resources res, float density, JSONArray damage) {
        int base = canvas.save();
        if (damage != null) {
            if (damage.length() == 0) {
                canvas.restoreToCount(base);
                return;
            }
            // Whole CSS px, one more around, for the antialiased edges.
            clipPath.rewind();
            for (int i = 0; i < damage.length(); i++) {
                JSONArray d = damage.optJSONArray(i);
                if (d == null) {
                    continue;
                }
                float x = (float) Math.floor(d.optDouble(0)) - 1, y = (float) Math.floor(d.optDouble(1)) - 1;
                rectF.set(x, y, (float) Math.ceil(d.optDouble(0) + d.optDouble(2)) + 1, (float) Math.ceil(d.optDouble(1) + d.optDouble(3)) + 1);
                clipPath.addRect(rectF, Path.Direction.CW);
            }
            canvas.clipPath(clipPath);
            canvas.drawPaint(clear);
        }
        paintAll(canvas, list, 0, list.length(), res, density);
        canvas.restoreToCount(base);
    }

    private void paintAll(Canvas canvas, JSONArray list, int from, int to, Resources res, float density) {
        // Clips and groups nest in paint order and unwind together, as on every platform: each is one save.
        int depth = 0;
        for (int i = from; i < to; i++) {
            JSONObject c = list.optJSONObject(i);
            if (c == null) {
                continue;
            }
            switch (c.optInt("k")) {
                case 15: { // FilterPush: what it wraps goes through the filters on a bitmap of its own
                    int end = filterEnd(list, i, to);
                    filtered(canvas, c, list, i + 1, end, res, density);
                    i = end;
                    break;
                }
                // 16 FilterPop closes in filtered(); 17 Backdrop needs the pixels under it, which a Canvas does not give.
                case 0: { // FillRect
                    canvas.save();
                    clipRect(canvas, c);
                    solid(fill, Paths.argb(c, "c"));
                    canvas.drawPath(rounded(c, "r", path), fill);
                    canvas.restore();
                    break;
                }
                case 1: { // StrokeRect
                    stroke.setColor(Paths.argb(c, "c"));
                    stroke.setStrokeWidth((float) c.optDouble("w", 1.0));
                    stroke.setPathEffect(null);
                    canvas.drawPath(rounded(c, "r", path), stroke);
                    break;
                }
                case 2: { // FillEllipse
                    solid(fill, Paths.argb(c, "c"));
                    canvas.drawOval(Paths.readRect(c, "r", rectF), fill);
                    break;
                }
                case 3: { // StrokeEllipse
                    float w = (float) c.optDouble("w", 1.0);
                    Paths.readRect(c, "r", rectF).inset(w / 2, w / 2);
                    stroke.setColor(Paths.argb(c, "c"));
                    stroke.setStrokeWidth(w);
                    stroke.setPathEffect(null);
                    canvas.drawOval(rectF, stroke);
                    break;
                }
                case 4: { // Text
                    Paint font = res.font(c.optInt("f"));
                    if (font == null) {
                        break;
                    }
                    font.setColor(Paths.argb(c, "c"));
                    font.setShader(null);
                    canvas.drawText(c.optString("t"), (float) c.optDouble("x"), (float) c.optDouble("y"), font);
                    break;
                }
                case 5: { // Image
                    Bitmap bitmap = c.has("cv") ? res.canvas(c.optInt("cv")) : res.image(c.optInt("i"));
                    if (bitmap == null) {
                        break;
                    }
                    canvas.save();
                    clipRect(canvas, c);
                    // The layer's border radius bounds the image like the clip does.
                    if (c.has("q")) {
                        canvas.clipPath(rounded(c, c.has("l") ? "l" : "r", clipPath));
                    }
                    canvas.drawBitmap(bitmap, null, Paths.readRect(c, "r", rectF), bitmapPaint);
                    canvas.restore();
                    break;
                }
                case 6:
                case 7:
                case 8:
                    gradient(canvas, c);
                    break;
                case 9: { // ClipPush
                    canvas.save();
                    depth++;
                    JSONObject p = c.optJSONObject("p");
                    canvas.clipPath(p != null ? Paths.readPath(p, clipPath) : rounded(c, "r", clipPath));
                    break;
                }
                case 10: // ClipPop
                case 13: // GroupPop
                    if (depth > 0) {
                        canvas.restore();
                        depth--;
                    }
                    break;
                case 11: { // Path
                    JSONObject p = c.optJSONObject("p");
                    if (p != null) {
                        svgPath(canvas, p);
                    }
                    break;
                }
                case 12: { // GroupPush
                    float opacity = (float) c.optDouble("o", 1.0);
                    boolean hidden = c.optInt("h") != 0;
                    // An opacity group is a layer composited once, so overlapping children do not darken.
                    if (opacity < 1f && !hidden) {
                        canvas.saveLayerAlpha(null, (int) (Math.max(0f, Math.min(1f, opacity)) * 255));
                    } else {
                        canvas.save();
                    }
                    depth++;
                    float[] m = Paths.floats(c.optJSONArray("m"), 6);
                    values[0] = m[0];
                    values[1] = m[2];
                    values[2] = m[4];
                    values[3] = m[1];
                    values[4] = m[3];
                    values[5] = m[5];
                    values[6] = 0f;
                    values[7] = 0f;
                    values[8] = 1f;
                    matrix.setValues(values);
                    canvas.concat(matrix);
                    if (hidden) {
                        canvas.clipRect(0f, 0f, 0f, 0f);
                    }
                    break;
                }
                case 14:
                    shadow(canvas, c, density);
                    break;
            }
        }
        while (depth-- > 0) {
            canvas.restore();
        }
    }

    /** The index of the FilterPop closing the FilterPush at `at`, or `to`. */
    private static int filterEnd(JSONArray list, int at, int to) {
        int depth = 0;
        for (int j = at; j < to; j++) {
            JSONObject c = list.optJSONObject(j);
            int k = c == null ? -1 : c.optInt("k");
            if (k == 15) {
                depth++;
            } else if (k == 16 && --depth == 0) {
                return j;
            }
        }
        return to;
    }

    private void filtered(Canvas canvas, JSONObject c, JSONArray list, int from, int to, Resources res, float density) {
        JSONArray fl = c.optJSONArray("fl");
        @SuppressWarnings("deprecation") Matrix m = canvas.getMatrix();
        m.getValues(values);
        // How far the filters spread past the box, in CSS px: blurs three deviations, a shadow that and its offset.
        float left = 0f, top = 0f, right = 0f, bottom = 0f;
        for (int k = 0; fl != null && k < fl.length(); k++) {
            JSONArray f = fl.optJSONArray(k);
            if (f == null) {
                continue;
            }
            float amount = Math.max((float) f.optDouble(1), 0f);
            if (f.optInt(0) == 0) {
                left += 3 * amount; top += 3 * amount; right += 3 * amount; bottom += 3 * amount;
            } else if (f.optInt(0) == 9) {
                float x = (float) f.optDouble(2), y = (float) f.optDouble(3);
                left = Math.max(left, 1.5f * amount - x); right = Math.max(right, 1.5f * amount + x);
                top = Math.max(top, 1.5f * amount - y); bottom = Math.max(bottom, 1.5f * amount + y);
            }
        }
        Paths.readRect(c, "r", rectF);
        rectF.set(rectF.left - left, rectF.top - top, rectF.right + right, rectF.bottom + bottom);
        RectF device = new RectF();
        m.mapRect(device, rectF);
        float x0 = (float) Math.floor(device.left), y0 = (float) Math.floor(device.top);
        int w = (int) Math.ceil(device.right) - (int) x0, h = (int) Math.ceil(device.bottom) - (int) y0;
        // Under a rotation or past a sensible size the filters do not apply: what they wrap draws as it is.
        if (fl == null || fl.length() == 0 || values[1] != 0f || values[3] != 0f || w <= 0 || h <= 0 || (long) w * h > 4096L * 4096L) {
            paintAll(canvas, list, from, to, res, density);
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas layer = new Canvas(bitmap);
        layer.translate(-x0, -y0);
        layer.concat(m);
        paintAll(layer, list, from, to, res, density);
        LitehtmlNative.filterPixels(bitmap, fl.toString(), values[0]);
        // Back where it was drawn, in user space: the inverse of the device rect it covers.
        Matrix inverse = new Matrix();
        m.invert(inverse);
        RectF back = new RectF(x0, y0, x0 + w, y0 + h);
        inverse.mapRect(back);
        canvas.drawBitmap(bitmap, null, back, bitmapPaint);
        bitmap.recycle();
    }

    private static void solid(Paint paint, int color) {
        paint.setShader(null);
        paint.setColor(color);
    }

    private void clipRect(Canvas canvas, JSONObject c) {
        if (c.has("l")) {
            canvas.clipRect(Paths.readRect(c, "l", clipRectF));
        }
    }

    private Path rounded(JSONObject c, String key, Path into) {
        Paths.readRect(c, key, rectF);
        into.rewind();
        JSONArray q = c.optJSONArray("q");
        if (q == null) {
            into.addRect(rectF, Path.Direction.CW);
        } else {
            for (int i = 0; i < 8; i++) {
                radii[i] = Paths.f(q, i);
            }
            into.addRoundRect(rectF, radii, Path.Direction.CW);
        }
        return into;
    }

    private void gradient(Canvas canvas, JSONObject c) {
        float[] a = Paths.floats(c.optJSONArray("a"), 4);
        JSONArray s = c.optJSONArray("s");
        if (s == null) {
            return;
        }
        Paths.Stops stops = new Paths.Stops(s);
        if (stops.isEmpty()) {
            return;
        }
        canvas.save();
        clipRect(canvas, c);
        canvas.clipPath(rounded(c, "r", clipPath));
        int kind = c.optInt("k");
        if (kind == 6) {
            fill.setShader(new LinearGradient(a[0], a[1], a[2], a[3], stops.shaderColors(), stops.shaderOffsets(), Shader.TileMode.CLAMP));
        } else if (kind == 7) {
            float rx = Math.max(a[2], 0.01f), ry = Math.max(a[3], 0.01f);
            fill.setShader(new RadialGradient(a[0], a[1], rx, stops.shaderColors(), stops.shaderOffsets(), Shader.TileMode.CLAMP));
            canvas.translate(a[0], a[1]);
            canvas.scale(1f, ry / rx);
            canvas.translate(-a[0], -a[1]);
        } else {
            // CSS: degrees clockwise from the top; SweepGradient starts at +x going clockwise.
            fill.setShader(new SweepGradient(a[0], a[1], stops.shaderColors(), stops.shaderOffsets()));
            canvas.rotate(a[2] - 90f, a[0], a[1]);
        }
        Paths.readRect(c, "r", rectF);
        canvas.drawRect(rectF.left - 4000f, rectF.top - 4000f, rectF.right + 4000f, rectF.bottom + 4000f, fill);
        fill.setShader(null);
        canvas.restore();
    }

    private void svgPath(Canvas canvas, JSONObject p) {
        Path shape = Paths.readPath(p, path);
        JSONArray s = p.optJSONArray("s");
        Paths.Stops stops = s == null ? null : new Paths.Stops(s);
        if (p.has("g") && stops != null && !stops.isEmpty()) {
            float[] a = Paths.floats(p.optJSONArray("ga"), 7);
            // Android's radial gradient has no focus: it is centred, as the focus usually is.
            fill.setShader(p.optInt("g") == 1
                ? new LinearGradient(a[0], a[1], a[2], a[3], stops.shaderColors(), stops.shaderOffsets(), Shader.TileMode.CLAMP)
                : new RadialGradient(a[0], a[1], Math.max(a[4], 0.01f), stops.shaderColors(), stops.shaderOffsets(), Shader.TileMode.CLAMP));
            canvas.drawPath(shape, fill);
            fill.setShader(null);
        } else if (p.has("f") && (Paths.argb(p, "f") >>> 24) != 0) {
            solid(fill, Paths.argb(p, "f"));
            canvas.drawPath(shape, fill);
        }
        float width = (float) p.optDouble("sw");
        if (p.has("sc") && (Paths.argb(p, "sc") >>> 24) != 0 && width > 0) {
            stroke.setColor(Paths.argb(p, "sc"));
            Paths.lineStyle(stroke, p, width, 4f);
            canvas.drawPath(shape, stroke);
            stroke.setPathEffect(null);
        }
    }

    /**
     * CSS box-shadow, blurred in a software bitmap: hardware canvases before API 28 ignore blur on shapes. The
     * blur is sigma = blur / 2 in CSS px, drawn outside the border box, or inside the padding box when inset.
     */
    @SuppressWarnings("deprecation")
    private void shadow(Canvas canvas, JSONObject c, float density) {
        float[] a = Paths.floats(c.optJSONArray("a"), 4);
        float dx = a[0], dy = a[1], blur = Math.max(a[2], 0f), spread = a[3];
        int color = Paths.argb(c, "c");
        RectF box = new RectF(Paths.readRect(c, "r", rectF));
        if (box.isEmpty() || (color >>> 24) == 0) {
            return;
        }
        boolean inset = c.optInt("n") != 0;
        // Three sigma of blur beyond the spread shape: past that the Gaussian is invisible.
        float reach = blur * 1.5f + Math.abs(spread) + 2;
        float grow = reach + (inset ? Math.abs(dx) + Math.abs(dy) : 0f);
        // The ink: where the blurred shape can show, in CSS px, before the offset.
        RectF ink = new RectF(box);
        ink.inset(-grow, -grow);
        float scale = Math.max(density, 1f);
        int w = (int) Math.ceil(ink.width() * scale), h = (int) Math.ceil(ink.height() * scale);
        if (w <= 0 || h <= 0 || (long) w * h > (16L << 20)) {
            return;
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas offscreen = new Canvas(bitmap);
        offscreen.scale(scale, scale);
        offscreen.translate(-ink.left, -ink.top);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        // Skia's blur radius r has sigma = 0.57735 r + 0.5; this inverts it for sigma = blur / 2, in device px.
        float sigma = blur / 2f * scale;
        if (sigma > 0.5f) {
            paint.setMaskFilter(new BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL));
        }
        Path shape = new Path();
        if (inset) {
            shape.addRect(ink, Path.Direction.CW);
            rectF.set(box.left + spread, box.top + spread, box.right - spread, box.bottom - spread);
            if (!rectF.isEmpty()) {
                shape.addPath(roundedAt(c, rectF, -spread));
            }
            shape.setFillType(Path.FillType.EVEN_ODD);
        } else {
            rectF.set(box.left - spread, box.top - spread, box.right + spread, box.bottom + spread);
            shape.addPath(roundedAt(c, rectF, c.has("q") ? spread : 0f));
        }
        offscreen.drawPath(shape, paint);

        canvas.save();
        Path border = rounded(c, "r", new Path());
        if (inset) {
            canvas.clipPath(border);
        } else if (android.os.Build.VERSION.SDK_INT >= 26) {
            canvas.clipOutPath(border);
        } else {
            canvas.clipPath(border, android.graphics.Region.Op.DIFFERENCE);
        }
        rectF.set(ink.left + dx, ink.top + dy, ink.right + dx, ink.bottom + dy);
        canvas.drawBitmap(bitmap, null, rectF, bitmapPaint);
        canvas.restore();
        bitmap.recycle();
    }

    /** The radii of `c` grown by `grow`, on `rect`. */
    private static Path roundedAt(JSONObject c, RectF rect, float grow) {
        Path out = new Path();
        JSONArray q = c.optJSONArray("q");
        if (q == null) {
            out.addRect(rect, Path.Direction.CW);
        } else {
            float[] r = new float[8];
            for (int i = 0; i < 8; i++) {
                r[i] = Math.max(Paths.f(q, i) + grow, 0f);
            }
            out.addRoundRect(rect, r, Path.Direction.CW);
        }
        return out;
    }
}
