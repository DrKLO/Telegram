package org.telegram.utils.twidget;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Build;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.Trace;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Java port of upstream LitehtmlBackend.kt. Fonts, image decoding, canvas bitmaps and frame
 * recording are confined to the native renderer's worker: in-process, its threads; with the isolated
 * engine (WidgetRemoteEngine), the binder threads its calls arrive on, one at a time per engine thread,
 * where fonts and images come defined by the engine (defineFont, defineImage). UI never reads these
 * mutable objects: it receives published frames (WidgetFrameNode) and immutable layer descriptions only.
 */
final class WidgetSandboxBackend implements WidgetSandboxPainter.Resources, WidgetCanvas.Resources {
    private static final long MEMORY_BUDGET = 32L * 1024 * 1024;
    // What the engine's own limits (2000 nodes, 4000 canvas commands, 64 layers) keep far below; here they hold for
    // an engine whose widget broke out (WidgetGate): it cannot flood this process with fonts, ops or frames.
    private static final int MAX_FONTS = 4096, MAX_LAYERS = 256, MAX_DISPLAY_OPS = 200_000, MAX_CANVAS_WORDS = 1 << 21,
            MAX_FRAMES_PER_SECOND = 240;
    private static final long MAX_LIST_BYTES = 8L * 1024 * 1024;
    private final long[] recordTimes = new long[MAX_FRAMES_PER_SECOND];
    private int nextRecord;
    private final WidgetSandboxSession owner;
    // Written on the host thread, read by the recorder thread too (record, selectableText): concurrent.
    private final java.util.concurrent.ConcurrentHashMap<Integer, Paint> fonts = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Integer, Paint> deviceFonts = new java.util.concurrent.ConcurrentHashMap<>();
    // The selectable text of the current layout, null when there is none (worker).
    private WidgetText text;
    private final java.util.concurrent.ConcurrentHashMap<Integer, Bitmap> images = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Integer, WidgetCanvas> canvases = new java.util.concurrent.ConcurrentHashMap<>();
    // Layer content lists by layer index, sent just before the frame that names them.
    private final HashMap<Integer, WidgetDisplayList> pendingLayers = new HashMap<>();
    // Parsing the pending layers' contents, for the next record's work (WidgetSandboxSession#onRecorded).
    private long layerParseNanos;
    // Layer contents of the last frame by element id, reused while their version holds.
    private HashMap<Long, LayerContent> layerContents = new HashMap<>();
    private final WidgetSandboxPainter painter = new WidgetSandboxPainter();
    private int nextFont;
    private long imageBytes;

    WidgetSandboxBackend(WidgetSandboxSession owner) {
        this.owner = owner;
    }

    WidgetSandboxSession session() {
        return owner;
    }

    // JNI callbacks, looked up by name. Keep rules accompany this file.
    /**
     * A font at `size` CSS px. Android rounds glyph advances to whole pixels at small sizes, which at CSS px
     * puts text up to a pixel per word off its browser width; so widths and metrics come from the font at
     * the device's size, divided back, and the document's text is drawn at that size too
     * ({@link #deviceFont}). The CSS px paint is for canvas elements, whose pixels are their own.
     * `encodedFeatures`: OpenType features from font-variant-numeric and font-feature-settings as
     * `tag=value` pairs (`tnum=1,zero=1`), empty for the font's defaults.
     */
    float[] createFont(byte[] encodedFamilies, float size, int weight, boolean italic, byte[] encodedFeatures) {
        assertWorker();
        return defineFont(++nextFont, encodedFamilies, size, weight, italic, encodedFeatures);
    }

    /**
     * A font the engine measures with, answered by its metrics (createFont): here createFont's, from the isolated
     * engine its own id. Any thread.
     */
    float[] defineFont(int id, byte[] encodedFamilies, float size, int weight, boolean italic, byte[] encodedFeatures) {
        if (fonts.size() >= MAX_FONTS && !fonts.containsKey(id)) throw new IllegalArgumentException("Too many fonts");
        Paint[] paints = paints(text(encodedFamilies), size, weight, italic, text(encodedFeatures), owner.density);
        fonts.put(id, paints[0]);
        deviceFonts.put(id, paints[1]);
        return metrics(id, paints[1], owner.density, new Rect());
    }

    /** The font at `size` CSS px and at the device's size (createFont): {css, device}. */
    static Paint[] paints(String families, float size, int weight, boolean italic, String encodedFeatures, float density) {
        if (!(size > 0 && size <= 10_000)) size = 16;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(size);
        paint.setTypeface(typeface(families, weight, italic));
        String features = featureSettings(encodedFeatures);
        if (features != null) paint.setFontFeatureSettings(features);
        Paint device = new Paint(paint);
        device.setTextSize(size * density);
        return new Paint[] {paint, device};
    }

    /** createFont's answer: the id, then ascent, descent, height, x-height and the width of "0", CSS px. */
    static float[] metrics(int id, Paint device, float density, Rect bounds) {
        Paint.FontMetrics metrics = device.getFontMetrics();
        device.getTextBounds("x", 0, 1, bounds);
        return new float[] {
            id,
            -metrics.ascent / density,
            metrics.descent / density,
            (metrics.descent - metrics.ascent + metrics.leading) / density,
            bounds.height() / density,
            device.measureText("0") / density
        };
    }

    /**
     * `tnum=1,zero=1` as Paint#setFontFeatureSettings takes it, in CSS syntax: `"tnum" 1, "zero" 1`.
     * Null for none; entries that are not a four-letter tag and a number are left out.
     */
    static String featureSettings(String features) {
        if (features.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (String entry : features.split(",")) {
            int eq = entry.indexOf('=');
            if (eq != 4) continue;
            String tag = entry.substring(0, 4), value = entry.substring(5);
            if (!tag.matches("[\\x20-\\x7e]{4}") || tag.indexOf('"') >= 0 || !value.matches("\\d{1,5}")) continue;
            if (out.length() > 0) out.append(", ");
            out.append('"').append(tag).append("\" ").append(value);
        }
        return out.length() == 0 ? null : out.toString();
    }

    void deleteFont(int id) {
        fonts.remove(id);
        deviceFonts.remove(id);
    }

    float textWidth(int id, byte[] value) {
        Paint paint = deviceFonts.get(id);
        return paint == null ? 0 : paint.measureText(text(value)) / owner.density;
    }

    float[] loadImage(int id, byte[] encodedUrl) {
        Bitmap image = decodeImage(text(encodedUrl), imageBytes);
        if (image == null) return null;
        putImage(id, image);
        return new float[] {image.getWidth(), image.getHeight()};
    }

    /**
     * An image the isolated engine decoded (WidgetEngineBackend#loadImage). It comes from untrusted code: held to the
     * limits loadImage keeps here.
     */
    void defineImage(int id, Bitmap image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                || (long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS
                || imageBytes + image.getAllocationByteCount() > MAX_IMAGE_BYTES) return;
        putImage(id, image);
    }

    private void putImage(int id, Bitmap image) {
        // Do not recycle bitmaps: previously published frames can still reference them.
        Bitmap old = images.put(id, image);
        imageBytes += image.getAllocationByteCount();
        if (old != null) imageBytes -= old.getAllocationByteCount();
    }

    static final long MAX_IMAGE_PIXELS = 4 * 1024 * 1024, MAX_IMAGE_BYTES = 32L * 1024 * 1024;

    /** A data: URL's image within the limits, `imageBytes` already taken; null when it is none. */
    static Bitmap decodeImage(String url, long imageBytes) {
        int comma = url.indexOf(',');
        if (!url.startsWith("data:") || comma < 0 || url.length() > 2 * 1024 * 1024) return null;
        String meta = url.substring(5, comma);
        if (meta.contains("svg")) return null; // Same limitation as the upstream backend.
        byte[] bytes;
        try {
            String payload = url.substring(comma + 1);
            bytes =
                    meta.contains(";base64")
                            ? Base64.decode(payload, Base64.DEFAULT)
                            : java.net.URLDecoder.decode(payload, "UTF-8")
                                    .getBytes(StandardCharsets.ISO_8859_1);
        } catch (Exception error) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0
                || options.outHeight <= 0
                || (long) options.outWidth * options.outHeight > MAX_IMAGE_PIXELS) return null;
        long decodedBytes = (long) options.outWidth * options.outHeight * 4;
        if (imageBytes + decodedBytes > MAX_IMAGE_BYTES) return null;
        options.inJustDecodeBounds = false;
        Bitmap image = WidgetPng.decodeBroken(bytes);
        if (image == null) image = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return image;
    }

    void canvasRun(int id, int width, int height, int[] ops) throws Exception {
        assertWorker();
        if (width <= 0 || height <= 0) {
            canvasRelease(id);
            return;
        }
        checkCanvasSize(id, width, height);
        if (ops.length > MAX_CANVAS_WORDS) throw new IllegalArgumentException("Canvas batch");
        WidgetCanvas surface = canvases.get(id);
        if (surface == null) {
            surface = new WidgetCanvas(id);
            canvases.put(id, surface);
        }
        surface.run(width, height, ops, this);
    }

    byte[] canvasRead(int id, int x, int y, int width, int height) {
        assertWorker();
        if (width <= 0 || height <= 0 || (long) width * height > 4 * 1024 * 1024) return null;
        WidgetCanvas surface = canvases.get(id);
        return surface == null ? new byte[width * height * 4] : surface.read(x, y, width, height);
    }

    void canvasRelease(int id) {
        assertWorker();
        WidgetCanvas surface = canvases.remove(id);
        if (surface != null) surface.release();
    }

    private void checkCanvasSize(int id, int width, int height) {
        if ((long) width * height > 4 * 1024 * 1024)
            throw new IllegalArgumentException("Canvas dimensions");
        long bytes = (long) width * height * 8 + imageBytes;
        for (java.util.Map.Entry<Integer, WidgetCanvas> entry : canvases.entrySet())
            if (entry.getKey() != id) bytes += entry.getValue().allocationBytes();
        if (bytes > MEMORY_BUDGET) throw new IllegalArgumentException("Canvas memory budget");
    }

    @Override
    public Paint font(int id) {
        return fonts.get(id);
    }

    @Override
    public Paint deviceFont(int id) {
        return deviceFonts.get(id);
    }

    @Override
    public Bitmap image(int id) {
        return images.get(id);
    }

    /** For the display list: an immutable copy. */
    @Override
    public Bitmap canvas(int id) {
        WidgetCanvas surface = canvases.get(id);
        return surface == null ? null : surface.snapshot();
    }

    /** For another canvas's drawImage, on the worker: the working bitmap. */
    @Override
    public Bitmap canvasBitmap(int id) {
        WidgetCanvas surface = canvases.get(id);
        return surface == null ? null : surface.bitmap();
    }

    byte[] formatIntl(byte[] request) {
        return WidgetSandboxIntl.format(text(request)).getBytes(StandardCharsets.UTF_8);
    }

    void signal(int kind, byte[] message) {
        owner.onWorkerSignal(kind, text(message));
    }

    /** Worker: the text the widget made selectable changed (Renderer::selectable_text_json); frames carry it. */
    void selectableText(byte[] encodedRuns) {
        assertWorker();
        try {
            text = WidgetText.parse(text(encodedRuns), deviceFonts::get, owner.density);
        } catch (Exception e) {
            text = null;
        }
    }

    /**
     * A compositor layer's content (CSS px, document coordinates). It is recorded in record(),
     * which knows the layer's bounds; layers precede the frame that names them.
     */
    void recordLayer(int index, int version, byte[] encodedList) throws Exception {
        assertWorker();
        long started = System.nanoTime();
        if (index < 0 || index >= MAX_LAYERS || encodedList.length > MAX_LIST_BYTES) throw new IllegalArgumentException("Layer");
        pendingLayers.put(index, WidgetDisplayList.parse(encodedList, MAX_DISPLAY_OPS));
        layerParseNanos += System.nanoTime() - started;
    }

    void record(
            byte[] encodedList,
            byte[] encodedComposition,
            double nativeClock,
            int width,
            int height,
            float density,
            float layoutMs,
            int passes,
            int builds)
            throws Exception {
        assertWorker();
        if (encodedList.length > MAX_LIST_BYTES) {
            pendingLayers.clear();
            throw new IllegalArgumentException("Display list");
        }
        // More frames than any refresh rate: dropped, the next one has the same content.
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - recordTimes[nextRecord] < 1000) {
            pendingLayers.clear();
            layerParseNanos = 0;
            return;
        }
        recordTimes[nextRecord] = now;
        nextRecord = (nextRecord + 1) % recordTimes.length;
        owner.lastList = encodedList;
        owner.lastComposition = encodedComposition;
        Trace.beginSection("WidgetSandbox:Record");
        long started = System.nanoTime();
        double clockOffset = nativeClock - started / 1e9;
        WidgetFrameNode node = WidgetFrameNode.create("widget-sandbox-content", 0, 0, width, height);
        try {
            WidgetDisplayList list = WidgetDisplayList.parse(encodedList, MAX_DISPLAY_OPS);
            JSONObject composition = new JSONObject(text(encodedComposition));
            long parsed = System.nanoTime();
            Canvas canvas = node.beginRecording();
            try {
                canvas.scale(density, density);
                painter.paint(canvas, list, this, density);
            } finally {
                node.endRecording();
            }
            WidgetSandboxSession.Layer[] layers = layers(composition, density);
            owner.onRecorded((parsed - started + layerParseNanos) / 1e6f, (System.nanoTime() - parsed) / 1e6f);
            owner.onWorkerFrame(
                    node,
                    layers,
                    clockOffset,
                    width,
                    height,
                    layoutMs,
                    (System.nanoTime() - started) / 1e6f,
                    passes,
                    builds,
                    list.count,
                    text);
        } catch (Exception error) {
            node.discard();
            throw error;
        } finally {
            pendingLayers.clear();
            layerParseNanos = 0;
            Trace.endSection();
        }
    }

    private WidgetSandboxSession.Layer[] layers(JSONObject composition, float density) {
        JSONArray items = composition.optJSONArray("l");
        int count = items == null ? 0 : items.length();
        HashMap<Long, LayerContent> contents = new HashMap<>();
        WidgetSandboxSession.Layer[] layers = new WidgetSandboxSession.Layer[count];
        int used = 0;
        for (int i = 0; i < count; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            long id = item.optLong("id");
            long version = item.optLong("cv");
            JSONArray box = item.optJSONArray("b");
            if (box == null) continue;
            int left = (int) Math.floor(box.optDouble(0) * density);
            int top = (int) Math.floor(box.optDouble(1) * density);
            int right = (int) Math.ceil((box.optDouble(0) + box.optDouble(2)) * density);
            int bottom = (int) Math.ceil((box.optDouble(1) + box.optDouble(3)) * density);
            LayerContent content = layerContents.get(id);
            WidgetDisplayList list = pendingLayers.get(i);
            if (list != null) {
                WidgetFrameNode node = WidgetFrameNode.create("widget-sandbox-layer", left, top, right, bottom);
                Canvas canvas = node.beginRecording();
                try {
                    canvas.translate(-left, -top);
                    canvas.scale(density, density);
                    painter.paint(canvas, list, this, density);
                } finally {
                    node.endRecording();
                }
                content = new LayerContent(node, (int) version);
            } else if (content == null || content.version != (int) version) {
                continue; // Not sent and not known: never expected, drop the layer.
            }
            contents.put(id, content);
            JSONArray clip = item.optJSONArray("l");
            float[] clipBox = null, clipRadii = null;
            if (clip != null) {
                clipBox = new float[4];
                WidgetSandboxPainter.floats(clip, clipBox, 4);
                JSONArray corners = item.optJSONArray("q");
                if (corners != null) {
                    clipRadii = new float[8];
                    WidgetSandboxPainter.floats(corners, clipRadii, 8);
                }
            }
            JSONArray keys = item.optJSONArray("k");
            float[] track = new float[keys == null ? 0 : keys.length() / 8 * 8];
            WidgetSandboxPainter.floats(keys, track, track.length);
            layers[used++] =
                    new WidgetSandboxSession.Layer(
                            content.node,
                            clipBox,
                            clipRadii,
                            item.optDouble("ts"),
                            item.optDouble("td"),
                            item.optInt("tr") != 0,
                            track);
        }
        // Old contents keep their nodes: published frames may still draw them.
        layerContents = contents;
        if (used == layers.length) return layers;
        WidgetSandboxSession.Layer[] trimmed = new WidgetSandboxSession.Layer[used];
        System.arraycopy(layers, 0, trimmed, 0, used);
        return trimmed;
    }

    private static final class LayerContent {
        final WidgetFrameNode node;
        final int version;

        LayerContent(WidgetFrameNode node, int version) {
            this.node = node;
            this.version = version;
        }
    }

    private static void assertWorker() {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Native layout and recording must run off UI");
    }

    static String text(byte[] value) {
        return value == null ? "" : new String(value, StandardCharsets.UTF_8);
    }

    static Typeface typeface(String families, int weight, boolean italic) {
        Typeface base = Typeface.SANS_SERIF;
        for (String raw : families.split(",")) {
            String name =
                    raw.trim()
                            .replace("\"", "")
                            .replace("'", "")
                            .toLowerCase(java.util.Locale.ROOT);
            if (name.isEmpty()) continue;
            switch (name) {
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
                default:
                    base = Typeface.create(name, Typeface.NORMAL);
                    if (base == Typeface.DEFAULT) continue;
            }
            break;
        }
        if (Build.VERSION.SDK_INT >= 28)
            return Typeface.create(base, Math.max(1, Math.min(1000, weight)), italic);
        // Before API 28 a family has its regular and bold faces only.
        return Typeface.create(base, (weight >= 600 ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0));
    }
}
