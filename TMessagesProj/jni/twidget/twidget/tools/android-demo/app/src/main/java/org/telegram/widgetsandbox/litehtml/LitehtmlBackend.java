package org.telegram.widgetsandbox.litehtml;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Base64;
import android.util.SparseArray;

import org.json.JSONArray;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * What TwidgetCore asks Android for (src/backend.h): fonts with metrics, text widths, decoded `data:` images and
 * canvas bitmaps (android.graphics has them, the NDK does not), and the painter of the display lists it records.
 * Coordinates are CSS px; the view scales its canvas by the density before painting. jni.cpp looks the callbacks
 * up by name and signature: keep them (proguard-rules.pro does).
 */
public final class LitehtmlBackend {

    private final SparseArray<Paint> fonts = new SparseArray<>();
    private int nextFont;
    private final SparseArray<Bitmap> images = new SparseArray<>();
    private final CanvasBitmaps canvases = new CanvasBitmaps(new CanvasBitmaps.Source() {
        @Override
        public Paint font(int id) {
            return fonts.get(id);
        }

        @Override
        public Bitmap image(int id) {
            return images.get(id);
        }
    });
    private final DisplayListPainter painter = new DisplayListPainter();
    private final DisplayListPainter.Resources resources = new DisplayListPainter.Resources() {
        @Override
        public Paint font(int id) {
            return fonts.get(id);
        }

        @Override
        public Bitmap image(int id) {
            return images.get(id);
        }

        @Override
        public Bitmap canvas(int id) {
            return canvases.bitmap(id);
        }
    };
    private final Rect bounds = new Rect();

    // ── callbacks from C++ ──

    /**
     * [id, ascent, descent, height, xHeight, zeroWidth]; id 0 when no font could be made. `features` is
     * in font-feature-settings syntax, empty for the font's defaults.
     */
    public float[] createFont(String families, float sizePx, int weight, boolean italic, String features) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(sizePx);
        paint.setTypeface(typeface(families, weight, italic));
        if (!features.isEmpty()) {
            paint.setFontFeatureSettings(features);
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        int id = ++nextFont;
        fonts.put(id, paint);
        paint.getTextBounds("x", 0, 1, bounds);
        // FontMetrics.ascent is negative: above the baseline.
        return new float[]{id, -fm.ascent, fm.descent, fm.descent - fm.ascent + fm.leading, bounds.height(), paint.measureText("0")};
    }

    public void deleteFont(int id) {
        fonts.remove(id);
    }

    public float textWidth(int id, String text) {
        Paint paint = fonts.get(id);
        return paint == null ? 0f : paint.measureText(text);
    }

    /** Decodes a `data:` image (base64 or percent-encoded; no SVG) and keeps it under `id`. [w, h] in px, or null. */
    public float[] loadImage(int id, String url) {
        int comma = url.indexOf(',');
        if (!url.startsWith("data:") || comma < 0) {
            return null;
        }
        String meta = url.substring(5, comma);
        if (meta.contains("svg")) {
            return null;
        }
        String payload = url.substring(comma + 1);
        byte[] bytes;
        try {
            bytes = meta.contains(";base64") ? Base64.decode(payload, Base64.DEFAULT) : URLDecoder.decode(payload, "UTF-8").getBytes(StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) {
            return null;
        }
        Bitmap old = images.get(id);
        if (old != null) {
            old.recycle();
        }
        images.put(id, bitmap);
        return new float[]{bitmap.getWidth(), bitmap.getHeight()};
    }

    /** Runs a canvas op batch (JSON, src/canvas_ops.h) on canvas `id`'s w × h bitmap. */
    public void canvasRun(int id, int w, int h, String ops) {
        canvases.run(id, w, h, ops);
    }

    /** Straight RGBA of a canvas region, w × h × 4 bytes. */
    public byte[] canvasRead(int id, int x, int y, int w, int h) {
        return canvases.read(id, x, y, w, h);
    }

    public void canvasRelease(int id) {
        canvases.release(id);
    }

    // ── painting ──

    /**
     * Executes a display list (the JSON of LitehtmlNative.displayList or layerContent) on `canvas`, already
     * scaled to CSS px; with `damage` (LitehtmlNative.damage), only the areas that changed, on a canvas that
     * keeps the last frame.
     */
    public void paint(Canvas canvas, JSONArray list, float density, JSONArray damage) {
        painter.paint(canvas, list, resources, density, damage);
    }

    public void release() {
        for (int i = 0; i < images.size(); i++) {
            images.valueAt(i).recycle();
        }
        images.clear();
        canvases.releaseAll();
        fonts.clear();
    }

    private static Typeface typeface(String families, int weight, boolean italic) {
        Typeface base = null;
        for (String raw : families.split(",")) {
            String family = raw.trim().replace("\"", "").replace("'", "");
            if (family.isEmpty()) {
                continue;
            }
            switch (family.toLowerCase(Locale.US)) {
                case "-apple-system":
                case "blinkmacsystemfont":
                case "system-ui":
                case "ui-sans-serif":
                case "sans-serif":
                case "segoe ui":
                case "roboto":
                case "inter":
                case "helvetica neue":
                case "helvetica":
                case "arial":
                    base = Typeface.SANS_SERIF;
                    break;
                case "serif":
                case "ui-serif":
                case "georgia":
                case "times new roman":
                case "times":
                    base = Typeface.SERIF;
                    break;
                case "monospace":
                case "ui-monospace":
                case "menlo":
                case "consolas":
                case "courier new":
                case "sf mono":
                    base = Typeface.MONOSPACE;
                    break;
                default: {
                    // Unknown names come back as the default typeface.
                    Typeface named = Typeface.create(family, Typeface.NORMAL);
                    base = named == Typeface.DEFAULT ? null : named;
                }
            }
            if (base != null) {
                break;
            }
        }
        if (base == null) {
            base = Typeface.SANS_SERIF;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            return Typeface.create(base, Math.max(1, Math.min(1000, weight)), italic);
        }
        return Typeface.create(base, (weight >= 600 ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0));
    }
}
