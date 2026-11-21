package org.telegram.utils.twidget;

import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.json.JSONArray;

import java.util.ArrayList;

/**
 * Executes TwidgetCore display lists (display_list.h, in the bridge's binary form: WidgetDisplayList) on an
 * android.graphics.Canvas already scaled to CSS px. Java port of upstream DisplayListPainter.kt and Paths.kt. One
 * instance per widget worker: its scratch objects are not shared between threads.
 */
final class WidgetSandboxPainter {
    interface Resources {
        Paint font(int id);

        /** The font at the device's size (CSS size × density), for the document's text. */
        Paint deviceFont(int id);

        Bitmap image(int id);

        /** Immutable pixels of a canvas element, safe to record into a published display list. */
        Bitmap canvas(int id);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path clipPath = new Path();
    private final Path shadowPath = new Path();
    private final RectF rect = new RectF();
    private final RectF clipRect = new RectF();
    private final RectF box = new RectF();
    private final float[] radii = new float[8];
    private final float[] numbers = new float[8];
    private final Matrix matrix = new Matrix();
    private final java.util.HashMap<Float, BlurMaskFilter> blurFilters = new java.util.HashMap<>();
    private final java.util.IdentityHashMap<Paint, Paint> textPaints = new java.util.IdentityHashMap<>();
    private final float[] values = new float[9];
    // The command being painted: its rect, radii and clip (WidgetDisplayList HAS_*).
    private final RectF cmdRect = new RectF();
    private final RectF cmdClip = new RectF();
    private final float[] cmdRadii = new float[8];
    private boolean hasRadii, hasClip;
    private float[] points = new float[64];

    WidgetSandboxPainter() {
        stroke.setStyle(Paint.Style.STROKE);
    }

    /**
     * Paints `list`. `density` is device px per CSS px, used for blur radii. Clips and groups nest
     * in paint order and unwind together: each is one save.
     */
    void paint(Canvas canvas, WidgetDisplayList list, Resources resources, float density) {
        // A backdrop replaces what lies under its box with the filtered copy (backdrop-filter), so the list goes into
        // a layer of its own that the backdrop can clear its box in: drawn straight onto the bubble, the copy only
        // went over the sharp original, which showed through wherever the copy is not opaque.
        boolean layered = Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated() && list.hasBackdrop;
        int saved = layered ? canvas.saveLayer(null, null) : -1;
        paint(canvas, list, list.count, resources, density, true);
        if (layered) canvas.restoreToCount(saved);
    }

    /** The commands before `end`; `backdrops` false while a backdrop replays what lies under it. */
    private void paint(Canvas canvas, WidgetDisplayList list, int end, Resources resources, float density, boolean backdrops) {
        State s = new State(canvas);
        WidgetDisplayList.Cursor c = list.cursor();
        for (int i = 0; i < end; i++) {
            canvas = s.canvas;
            int head = c.i();
            int kind = head & 0xff, present = head >>> 8;
            if ((present & WidgetDisplayList.HAS_RECT) != 0) c.rect(cmdRect);
            else cmdRect.setEmpty();
            hasRadii = (present & WidgetDisplayList.HAS_RADII) != 0;
            if (hasRadii) c.floats(cmdRadii, 8);
            hasClip = (present & WidgetDisplayList.HAS_CLIP) != 0;
            if (hasClip) c.rect(cmdClip);
            switch (kind) {
                case 0: { // FillRect
                    int color = c.i();
                    if (hasClip) {
                        canvas.save();
                        canvas.clipRect(cmdClip);
                    }
                    solid(fill, color);
                    drawRounded(canvas, fill);
                    if (hasClip) canvas.restore();
                    break;
                }
                case 1: { // StrokeRect
                    stroke.setColor(c.i());
                    plainStroke(c.f());
                    drawRounded(canvas, stroke);
                    break;
                }
                case 2: // FillEllipse
                    solid(fill, c.i());
                    canvas.drawOval(cmdRect, fill);
                    break;
                case 3: { // StrokeEllipse
                    int color = c.i();
                    float width = c.f();
                    rect.set(cmdRect);
                    rect.inset(width / 2, width / 2);
                    stroke.setColor(color);
                    plainStroke(width);
                    canvas.drawOval(rect, stroke);
                    break;
                }
                case 4: { // Text
                    int color = c.i(), fontId = c.i();
                    float x = c.f(), y = c.f();
                    String text = list.string(c.i());
                    // At the device's size, as it was measured (WidgetSandboxBackend#createFont).
                    Paint font = textPaint(resources.deviceFont(fontId));
                    if (font == null) break;
                    font.setColor(color);
                    font.setShader(null);
                    canvas.save();
                    canvas.scale(1 / density, 1 / density);
                    canvas.drawText(text, x * density, y * density, font);
                    canvas.restore();
                    break;
                }
                case 5: { // Image
                    boolean isCanvas = c.i() != 0;
                    int id = c.i();
                    Bitmap image = isCanvas ? resources.canvas(id) : resources.image(id);
                    if (image == null) break;
                    canvas.save();
                    if (hasClip) canvas.clipRect(cmdClip);
                    // The layer's border radius bounds the image like the clip does.
                    if (hasRadii) canvas.clipPath(rounded(hasClip ? cmdClip : cmdRect, clipPath));
                    canvas.drawBitmap(image, null, cmdRect, bitmapPaint);
                    canvas.restore();
                    break;
                }
                case 6:
                case 7:
                case 8:
                    gradient(canvas, c, kind);
                    break;
                case 9: { // ClipPush
                    boolean hasPath = c.i() != 0;
                    canvas.save();
                    s.push();
                    canvas.clipPath(hasPath ? readPath(c, clipPath) : rounded(cmdRect, clipPath));
                    break;
                }
                case 10: // ClipPop
                case 13: // GroupPop
                    if (s.depth > 0) s.pop();
                    break;
                case 11: // Path
                    if (c.i() != 0) svgPath(canvas, c);
                    break;
                case 12: { // GroupPush
                    c.floats(numbers, 6);
                    float opacity = c.f();
                    boolean hidden = c.i() != 0;
                    values[0] = numbers[0];
                    values[1] = numbers[2];
                    values[2] = numbers[4];
                    values[3] = numbers[1];
                    values[4] = numbers[3];
                    values[5] = numbers[5];
                    values[6] = 0;
                    values[7] = 0;
                    values[8] = 1;
                    matrix.setValues(values);
                    // An opacity group is composited once, so overlapping children do not darken. Its layer is
                    // bounded by the group's rect (its box and the ink of what it draws, TwidgetCore's
                    // paint_group.bounds) through its matrix: unbounded, a RenderNode allocates an offscreen
                    // buffer the size of the whole clip for every such group, every frame.
                    if (opacity < 1 && !hidden) {
                        int alpha = Math.round(Math.max(0, Math.min(1, opacity)) * 255);
                        if (!cmdRect.isEmpty()) {
                            matrix.mapRect(rect, cmdRect);
                            rect.inset(-1, -1);
                            canvas.saveLayerAlpha(rect, alpha);
                        } else {
                            canvas.saveLayerAlpha(null, alpha);
                        }
                    } else canvas.save();
                    s.push();
                    canvas.concat(matrix);
                    s.current.preConcat(matrix);
                    if (hidden) canvas.clipRect(0, 0, 0, 0);
                    break;
                }
                case 14:
                    shadow(canvas, c, density);
                    break;
                case 15: // FilterPush
                    pushFilter(s, readFilters(c), density);
                    break;
                case 16: // FilterPop
                    if (!s.filters.isEmpty()) popFilter(s, density);
                    break;
                case 17: { // Backdrop
                    Filters functions = readFilters(c);
                    if (backdrops && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated())
                        Effects.backdrop(this, s, list, i, functions, new RectF(cmdRect),
                                hasRadii ? cmdRadii.clone() : null, resources, density);
                    break;
                }
                default:
                    // Unknown kinds have no known length: the rest of the list cannot be read.
                    throw WidgetDisplayList.invalid();
            }
        }
        while (!s.filters.isEmpty()) popFilter(s, density);
        while (s.depth > 0) s.pop();
    }

    /** Filter functions (display_list.h Filter), as the list carries them. */
    static final class Filters {
        final int[] kind, color;
        final float[] amount, x, y;

        Filters(int n) {
            kind = new int[n];
            color = new int[n];
            amount = new float[n];
            x = new float[n];
            y = new float[n];
        }

        int size() {
            return kind.length;
        }
    }

    private static Filters readFilters(WidgetDisplayList.Cursor c) {
        int n = c.count(WidgetDisplayList.MAX_FILTERS, 5);
        Filters filters = new Filters(n);
        for (int k = 0; k < n; k++) {
            filters.kind[k] = c.i();
            filters.amount[k] = c.f();
            filters.x[k] = c.f();
            filters.y[k] = c.f();
            filters.color[k] = c.i();
        }
        return filters;
    }

    /** The saves of one paint: their depth, the transform from the list's origin, the open filters. */
    private static final class State {
        Canvas canvas;
        int depth;
        final Matrix current = new Matrix();
        final ArrayList<Matrix> saved = new ArrayList<>();
        final ArrayList<FilterFrame> filters = new ArrayList<>();

        State(Canvas canvas) {
            this.canvas = canvas;
        }

        /** After a save. */
        void push() {
            saved.add(new Matrix(current));
            depth++;
        }

        void pop() {
            canvas.restore();
            current.set(saved.remove(saved.size() - 1));
            depth--;
        }
    }

    /** An open FilterPush: its content recorded apart (a RenderNode, API 31+) or into a colour-filtered layer. */
    private static final class FilterFrame {
        Canvas outer;
        int depth;
        Object node;
        int left, top;
        // The CPU pass: the content's pixels, where they sit on the device, and the filters to run.
        Bitmap bitmap;
        final Matrix device = new Matrix();
        float scale;
        Filters functions;
    }

    /**
     * filter: on API 31+ on a hardware canvas the content is recorded into a RenderNode at the device's
     * resolution and drawn through a RenderEffect; otherwise it is drawn into a bitmap of its own, which
     * twidget's CPU pass filters (WidgetClientNative#filterPixels). Under a rotation or skew, or past
     * 4096² px, the content draws unfiltered.
     */
    private void pushFilter(State s, Filters functions, float density) {
        FilterFrame frame = new FilterFrame();
        frame.outer = s.canvas;
        frame.depth = s.depth;
        s.filters.add(frame);
        if (functions.size() == 0) return;
        box.set(cmdRect);
        if (box.isEmpty()) return;
        // The rect is the content's ink: blurs and shadows paint past it.
        reach(functions, box);
        if (Build.VERSION.SDK_INT >= 31 && s.canvas.isHardwareAccelerated()) {
            Canvas inner = Effects.beginFilter(frame, box, functions, density);
            if (inner != null) {
                s.canvas = inner;
                s.depth = 0;
            }
            return;
        }
        s.current.getValues(values);
        if (values[Matrix.MSKEW_X] != 0 || values[Matrix.MSKEW_Y] != 0) return;
        frame.device.set(s.current);
        frame.device.postScale(density, density);
        frame.device.mapRect(rect, box);
        int x0 = (int) Math.floor(rect.left), y0 = (int) Math.floor(rect.top);
        int w = (int) Math.ceil(rect.right) - x0, h = (int) Math.ceil(rect.bottom) - y0;
        if (w <= 0 || h <= 0 || (long) w * h > 4096L * 4096L) return;
        frame.bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        frame.left = x0;
        frame.top = y0;
        frame.scale = density * Math.abs(values[Matrix.MSCALE_X]);
        frame.functions = functions;
        Canvas inner = new Canvas(frame.bitmap);
        inner.translate(-x0, -y0);
        inner.concat(frame.device);
        s.canvas = inner;
        s.depth = 0;
    }

    private void popFilter(State s, float density) {
        FilterFrame frame = s.filters.remove(s.filters.size() - 1);
        while (s.depth > (frame.node != null || frame.bitmap != null ? 0 : frame.depth)) s.pop();
        if (frame.node != null) {
            Effects.endFilter(frame, density);
            s.canvas = frame.outer;
        } else if (frame.bitmap != null) {
            Filters f = frame.functions;
            WidgetClientNative.filterPixels(frame.bitmap, f.kind, f.amount, f.x, f.y, f.color, frame.scale);
            s.canvas = frame.outer;
            // Back from device px to the content's coordinates. The bitmap is not touched again: a
            // recorded display list may hold it.
            Matrix back = new Matrix();
            if (frame.device.invert(back)) {
                s.canvas.save();
                s.canvas.concat(back);
                s.canvas.drawBitmap(frame.bitmap, frame.left, frame.top, bitmapPaint);
                s.canvas.restore();
            }
        }
        s.depth = frame.depth;
    }

    /** Grows `box` (CSS px) by how far `functions` paint past it: blurs 3 deviations, a shadow that and its offset. */
    private static void reach(Filters functions, RectF box) {
        float left = 0, top = 0, right = 0, bottom = 0;
        for (int k = 0; k < functions.size(); k++) {
            float amount = Math.max(0, functions.amount[k]);
            if (functions.kind[k] == 0) {
                left += 3 * amount;
                top += 3 * amount;
                right += 3 * amount;
                bottom += 3 * amount;
            } else if (functions.kind[k] == 9) {
                float x = functions.x[k], y = functions.y[k];
                left = Math.max(left, 1.5f * amount - x);
                right = Math.max(right, 1.5f * amount + x);
                top = Math.max(top, 1.5f * amount - y);
                bottom = Math.max(bottom, 1.5f * amount + y);
            }
        }
        box.set(box.left - left, box.top - top, box.right + right, box.bottom + bottom);
    }

    /** CSS brightness(): the colour channels scaled, alpha kept. */
    private static ColorMatrixColorFilter brightnessFilter(float amount) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setScale(amount, amount, amount, 1);
        return new ColorMatrixColorFilter(matrix);
    }

    /** Skia: sigma = 0.57735 r + 0.5 in device px; the radius for a sigma (0: no blur). */
    private static float blurRadius(float sigma) {
        return sigma > .5f ? (sigma - .5f) / .57735f : 0;
    }

    @RequiresApi(31)
    private static final class Effects {

        /** The effect of filter functions [kind, amount, x, y, ARGB] (display_list.h Filter); null: none. */
        static RenderEffect effect(Filters functions, float density, Shader.TileMode edges) {
            RenderEffect effect = null;
            for (int k = 0; k < functions.size(); k++) {
                float amount = functions.amount[k];
                switch (functions.kind[k]) {
                    case 0: { // blur(): the length is the standard deviation
                        float radius = blurRadius(amount * density);
                        if (radius <= 0) break;
                        effect = effect == null
                                ? RenderEffect.createBlurEffect(radius, radius, edges)
                                : RenderEffect.createBlurEffect(radius, radius, effect, edges);
                        break;
                    }
                    case 1: // brightness()
                        effect = effect == null
                                ? RenderEffect.createColorFilterEffect(brightnessFilter(amount))
                                : RenderEffect.createColorFilterEffect(brightnessFilter(amount), effect);
                        break;
                    case 9: { // drop-shadow(): the content over its alpha in the colour, offset, blurred (sigma = blur / 2)
                        RenderEffect source = effect != null ? effect : RenderEffect.createOffsetEffect(0, 0);
                        RenderEffect shadow = RenderEffect.createColorFilterEffect(
                                new PorterDuffColorFilter(functions.color[k], PorterDuff.Mode.SRC_IN), source);
                        shadow = RenderEffect.createOffsetEffect(
                                functions.x[k] * density, functions.y[k] * density, shadow);
                        float radius = blurRadius(amount / 2 * density);
                        if (radius > 0) shadow = RenderEffect.createBlurEffect(radius, radius, shadow, Shader.TileMode.DECAL);
                        effect = RenderEffect.createBlendModeEffect(shadow, source, BlendMode.SRC_OVER);
                        break;
                    }
                    default: // out of profile 1: the validator drops them
                        break;
                }
            }
            return effect;
        }

        /** A node over `box` (CSS px) at the device's resolution, and its canvas in CSS px; null: no effect. */
        static Canvas beginFilter(FilterFrame frame, RectF box, Filters functions, float density) {
            RenderEffect effect = effect(functions, density, Shader.TileMode.DECAL);
            if (effect == null) return null;
            RenderNode node = new RenderNode("widget-filter");
            frame.left = (int) Math.floor(box.left * density) - 1;
            frame.top = (int) Math.floor(box.top * density) - 1;
            node.setPosition(frame.left, frame.top, (int) Math.ceil(box.right * density) + 1, (int) Math.ceil(box.bottom * density) + 1);
            node.setRenderEffect(effect);
            frame.node = node;
            Canvas canvas = node.beginRecording();
            canvas.translate(-frame.left, -frame.top);
            canvas.scale(density, density);
            return canvas;
        }

        static void endFilter(FilterFrame frame, float density) {
            RenderNode node = (RenderNode) frame.node;
            node.endRecording();
            frame.outer.save();
            frame.outer.scale(1 / density, 1 / density);
            frame.outer.drawRenderNode(node);
            frame.outer.restore();
        }

        /**
         * backdrop-filter: what the commands before `end` paint under the box, replayed into a node
         * through the effect, inside the box's rounded rect. The edges clamp, as browsers' do.
         */
        static void backdrop(WidgetSandboxPainter painter, State s, WidgetDisplayList list, int end, Filters functions,
                             RectF box, float[] corners, Resources resources, float density) {
            RenderEffect effect = effect(functions, density, Shader.TileMode.CLAMP);
            Matrix inverse = new Matrix();
            if (effect == null || !s.current.invert(inverse)) return;
            if (box.isEmpty()) return;
            RenderNode node = new RenderNode("widget-backdrop");
            int left = (int) Math.floor(box.left * density), top = (int) Math.floor(box.top * density);
            node.setPosition(left, top, (int) Math.ceil(box.right * density), (int) Math.ceil(box.bottom * density));
            Canvas canvas = node.beginRecording();
            canvas.translate(-left, -top);
            canvas.scale(density, density);
            // The replay starts from the list's origin; this node sits in the current transform.
            canvas.concat(inverse);
            painter.paint(canvas, list, end, resources, density, false);
            node.endRecording();
            node.setRenderEffect(effect);
            Canvas into = s.canvas;
            into.save();
            into.clipPath(roundedPath(box, corners, painter.clipPath));
            // The filtered copy replaces the box's backdrop: clear it in the layer (the list's own, or an opacity
            // group's, which is a backdrop root) before drawing the copy.
            into.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            into.scale(1 / density, 1 / density);
            into.drawRenderNode(node);
            into.restore();
        }
    }

    private void plainStroke(float width) {
        stroke.setStrokeWidth(width);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        stroke.setStrokeJoin(Paint.Join.MITER);
        stroke.setPathEffect(null);
    }

    private static void solid(Paint paint, int color) {
        paint.setShader(null);
        paint.setColor(color);
    }

    /** `source` rounded by the command's radii. */
    private Path rounded(RectF source, Path into) {
        return roundedPath(source, hasRadii ? cmdRadii : null, into);
    }

    private static Path roundedPath(RectF source, float[] corners, Path into) {
        into.rewind();
        if (corners == null) into.addRect(source, Path.Direction.CW);
        else into.addRoundRect(source, corners, Path.Direction.CW);
        return into;
    }

    private void gradient(Canvas canvas, WidgetDisplayList.Cursor c, int kind) {
        c.floats(numbers, 4);
        float ax = numbers[0], ay = numbers[1], bx = numbers[2], by = numbers[3];
        int n = c.count(WidgetDisplayList.MAX_STOPS, 2);
        if (n == 0) return;
        int[] colors = new int[Math.max(2, n)];
        float[] offsets = n == 1 ? new float[] {0, 1} : new float[n];
        readStops(c, n, colors, offsets);
        canvas.save();
        if (hasClip) canvas.clipRect(cmdClip);
        canvas.clipPath(rounded(cmdRect, clipPath));
        // Paint alpha multiplies the shader: a previous translucent fill must not fade the gradient.
        fill.setColor(0xff000000);
        if (kind == 6 && colors.length > 16) {
            // Many stops (a repeating gradient unrolled) go to the GPU as a 256-sample texture, which
            // loses a 1px stripe on a long box: one band of two stops each instead.
            linearBands(canvas, ax, ay, bx, by, colors, offsets);
            canvas.restore();
            return;
        }
        if (kind == 6) {
            fill.setShader(
                    new LinearGradient(ax, ay, bx, by, colors, offsets, Shader.TileMode.CLAMP));
        } else if (kind == 7) {
            float rx = Math.max(bx, .01f), ry = Math.max(by, .01f);
            fill.setShader(new RadialGradient(ax, ay, rx, colors, offsets, Shader.TileMode.CLAMP));
            canvas.translate(ax, ay);
            canvas.scale(1, ry / rx);
            canvas.translate(-ax, -ay);
        } else {
            // CSS: degrees clockwise from the top; SweepGradient starts at +x going clockwise.
            fill.setShader(new SweepGradient(ax, ay, colors, offsets));
            canvas.rotate(bx - 90, ax, ay);
        }
        canvas.drawRect(
                cmdRect.left - 4000, cmdRect.top - 4000, cmdRect.right + 4000, cmdRect.bottom + 4000, fill);
        fill.setShader(null);
        canvas.restore();
    }

    /**
     * `n` stops (offset, ARGB) into `colors` and `offsets`, which hold at least two: Android gradients need two
     * colours, so one stop is drawn as a solid gradient from 0 to 1.
     */
    private static void readStops(WidgetDisplayList.Cursor c, int n, int[] colors, float[] offsets) {
        for (int k = 0; k < n; k++) {
            float offset = Math.max(0, Math.min(1, c.f()));
            colors[k] = c.i();
            if (n > 1) offsets[k] = offset;
        }
        if (n == 1) colors[1] = colors[0];
    }

    /** A linear gradient as bands across its line, a two-stop gradient (or a solid colour) each. */
    private void linearBands(Canvas canvas, float ax, float ay, float bx, float by, int[] colors, float[] offsets) {
        float length = (float) Math.hypot(bx - ax, by - ay);
        if (length <= 0) return;
        canvas.translate(ax, ay);
        canvas.rotate((float) Math.toDegrees(Math.atan2(by - ay, bx - ax)));
        final float reach = 8000;
        int last = offsets.length - 1;
        solid(fill, colors[0]);
        canvas.drawRect(-reach, -reach, offsets[0] * length, reach, fill);
        for (int i = 0; i < last; i++) {
            float from = offsets[i] * length, to = offsets[i + 1] * length;
            if (to <= from) continue;
            if (colors[i] == colors[i + 1]) {
                solid(fill, colors[i]);
            } else {
                fill.setColor(0xff000000);
                fill.setShader(new LinearGradient(from, 0, to, 0, colors[i], colors[i + 1], Shader.TileMode.CLAMP));
            }
            canvas.drawRect(from, -reach, to, reach, fill);
        }
        solid(fill, colors[last]);
        canvas.drawRect(offsets[last] * length, -reach, reach, reach, fill);
        fill.setShader(null);
    }

    private void svgPath(Canvas canvas, WidgetDisplayList.Cursor c) {
        Path geometry = readPath(c, path);
        int flags = c.i();
        Shader fillShader = null;
        int fillColor = 0;
        if ((flags & 1) != 0) {
            int kind = c.i();
            if (kind == 0) fillColor = c.i();
            else fillShader = pathGradient(c, kind);
        }
        boolean hasStroke = (flags & 2) != 0;
        int strokeColor = 0, cap = 0, join = 0;
        float width = 0, miter = 4, dashOffset = 0;
        Shader strokeShader = null;
        float[] dashes = null;
        if (hasStroke) {
            strokeColor = c.i();
            int kind = c.i();
            if (kind != 0) strokeShader = pathGradient(c, kind);
            width = c.f();
            cap = c.i();
            join = c.i();
            miter = c.f();
            int n = c.count(WidgetDisplayList.MAX_DASHES, 1);
            if (n > 0) {
                dashes = new float[n];
                c.floats(dashes, n);
            }
            dashOffset = c.f();
        }
        if (fillShader != null) {
            fill.setColor(0xff000000);
            fill.setShader(fillShader);
            canvas.drawPath(geometry, fill);
            fill.setShader(null);
        } else if ((flags & 1) != 0 && (fillColor >>> 24) != 0) {
            solid(fill, fillColor);
            canvas.drawPath(geometry, fill);
        }
        if (width <= 0) strokeShader = null;
        if (hasStroke && ((strokeColor >>> 24) != 0 || strokeShader != null) && width > 0) {
            stroke.setColor(strokeShader != null ? 0xff000000 : strokeColor);
            stroke.setShader(strokeShader);
            lineStyle(stroke, width, cap, join, miter, dashes, dashOffset);
            canvas.drawPath(geometry, stroke);
            stroke.setPathEffect(null);
            stroke.setShader(null);
        }
    }

    /** An SVG paint server: kind 1 linear (x1, y1 → x2, y2), 2 radial; null when it has fewer than two stops. */
    private Shader pathGradient(WidgetDisplayList.Cursor c, int kind) {
        c.floats(numbers, 7);
        float x1 = numbers[0], y1 = numbers[1], x2 = numbers[2], y2 = numbers[3], r = numbers[4];
        int n = c.count(WidgetDisplayList.MAX_STOPS, 2);
        int[] colors = new int[Math.max(2, n)];
        float[] offsets = n == 1 ? new float[] {0, 1} : new float[Math.max(n, 2)];
        readStops(c, n, colors, offsets);
        if (n < 2) return null;
        // Android's radial gradient has no focus: it is centred, as the focus usually is.
        return kind == 1
                ? new LinearGradient(x1, y1, x2, y2, colors, offsets, Shader.TileMode.CLAMP)
                : new RadialGradient(x1, y1, Math.max(r, .01f), colors, offsets, Shader.TileMode.CLAMP);
    }

    /**
     * CSS box-shadow: blur sigma = blur / 2 in CSS px, drawn outside the border box, or inside the
     * padding box when inset. Hardware canvases blur shapes since API 28, so it records directly;
     * before, a blurred shadow is left out (a hardware canvas would draw it sharp).
     */
    private void shadow(Canvas canvas, WidgetDisplayList.Cursor c, float density) {
        int color = c.i();
        c.floats(numbers, 4);
        boolean inset = c.i() != 0;
        float dx = numbers[0], dy = numbers[1], blur = Math.max(numbers[2], 0), spread = numbers[3];
        box.set(cmdRect);
        if (box.isEmpty() || (color >>> 24) == 0) return;
        if (Build.VERSION.SDK_INT < 28 && blur / 2 * density > .5f) return;
        shadowPaint.setColor(color);
        // Skia: sigma = 0.57735 r + 0.5 in device px; this inverts it for sigma = blur / 2.
        float sigma = blur / 2 * density;
        shadowPaint.setMaskFilter(sigma > .5f ? blurFilter((sigma - .5f) / .57735f / density) : null);
        float radius = hasRadii ? uniformRadius(cmdRadii) : 0;
        if (!inset && radius >= 0) {
            // A rect or a round rect of one radius: Skia blurs it analytically on the GPU, where a path's blur is a
            // mask rastered again on every frame.
            rect.set(box.left - spread + dx, box.top - spread + dy, box.right + spread + dx, box.bottom + spread + dy);
            // As the path below: listed radii grow by the spread, absent ones stay square.
            float grown = hasRadii ? Math.max(radius + spread, 0) : 0;
            canvas.save();
            if (radius == 0) {
                if (Build.VERSION.SDK_INT >= 26) canvas.clipOutRect(box);
                else canvas.clipRect(box, Region.Op.DIFFERENCE);
            } else {
                Path border = rounded(cmdRect, clipPath);
                if (Build.VERSION.SDK_INT >= 26) canvas.clipOutPath(border);
                else canvas.clipPath(border, Region.Op.DIFFERENCE);
            }
            if (grown > 0) canvas.drawRoundRect(rect, grown, grown, shadowPaint);
            else canvas.drawRect(rect, shadowPaint);
            canvas.restore();
            shadowPaint.setMaskFilter(null);
            return;
        }
        shadowPath.rewind();
        if (inset) {
            float reach = blur * 1.5f + Math.abs(spread) + Math.abs(dx) + Math.abs(dy) + 2;
            rect.set(box.left - reach, box.top - reach, box.right + reach, box.bottom + reach);
            shadowPath.addRect(rect, Path.Direction.CW);
            rect.set(box.left + spread, box.top + spread, box.right - spread, box.bottom - spread);
            if (!rect.isEmpty()) addRounded(shadowPath, rect, -spread);
            shadowPath.setFillType(Path.FillType.EVEN_ODD);
        } else {
            rect.set(box.left - spread, box.top - spread, box.right + spread, box.bottom + spread);
            addRounded(shadowPath, rect, hasRadii ? spread : 0);
        }
        shadowPath.offset(dx, dy);
        canvas.save();
        Path border = rounded(cmdRect, clipPath);
        if (inset) canvas.clipPath(border);
        else if (Build.VERSION.SDK_INT >= 26) canvas.clipOutPath(border);
        else canvas.clipPath(border, Region.Op.DIFFERENCE);
        canvas.drawPath(shadowPath, shadowPaint);
        canvas.restore();
        shadowPaint.setMaskFilter(null);
    }

    /** Draws the command's rect, rounded by its radii: a rect or a round rect where it can, a path otherwise. */
    private void drawRounded(Canvas canvas, Paint paint) {
        float radius = hasRadii ? uniformRadius(cmdRadii) : 0;
        if (radius == 0) canvas.drawRect(cmdRect, paint);
        else if (radius > 0) canvas.drawRoundRect(cmdRect, radius, radius, paint);
        else canvas.drawPath(rounded(cmdRect, path), paint);
    }

    /** The radius all eight of `corners` share (both axes of every corner), or -1 when they differ. */
    private static float uniformRadius(float[] corners) {
        float first = corners[0];
        for (int i = 1; i < 8; i++) if (corners[i] != first) return -1;
        return first;
    }

    /**
     * The painter's own copy of a document font: recording runs on its own thread while the host thread measures
     * text with the backend's paint, and drawing sets the colour.
     */
    private Paint textPaint(Paint font) {
        if (font == null) return null;
        Paint copy = textPaints.get(font);
        if (copy == null) {
            if (textPaints.size() >= 64) textPaints.clear();
            copy = new Paint(font);
            textPaints.put(font, copy);
        }
        return copy;
    }

    /** A blur mask filter of `radius`, shared: a frame of many shadows reuses a few. */
    private BlurMaskFilter blurFilter(float radius) {
        BlurMaskFilter filter = blurFilters.get(radius);
        if (filter == null) {
            if (blurFilters.size() >= 32) blurFilters.clear();
            filter = new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL);
            blurFilters.put(radius, filter);
        }
        return filter;
    }

    /** The command's radii grown by `grow`, on `area`. */
    private void addRounded(Path into, RectF area, float grow) {
        if (!hasRadii) into.addRect(area, Path.Direction.CW);
        else {
            for (int i = 0; i < 8; i++) radii[i] = Math.max(cmdRadii[i] + grow, 0);
            into.addRoundRect(area, radii, Path.Direction.CW);
        }
    }

    /** A path (op count, point count, op bytes, points, evenodd) into `into`. */
    private Path readPath(WidgetDisplayList.Cursor c, Path into) {
        into.rewind();
        int ops = c.count(WidgetDisplayList.MAX_PATH_OPS, 0);
        int count = c.count(WidgetDisplayList.MAX_PATH_OPS * 6, 1);
        int start = c.ops(ops);
        if (points.length < count) points = new float[Math.max(count, points.length * 2)];
        c.floats(points, count);
        int i = 0;
        for (int n = 0; n < ops; n++) {
            int op = c.op(start, n);
            int need = op == 3 ? 6 : op == 2 ? 4 : op == 4 ? 0 : 2;
            if (i + need > count) break;
            switch (op) {
                case 0:
                    into.moveTo(points[i], points[i + 1]);
                    break;
                case 1:
                    into.lineTo(points[i], points[i + 1]);
                    break;
                case 2:
                    into.quadTo(points[i], points[i + 1], points[i + 2], points[i + 3]);
                    break;
                case 3:
                    into.cubicTo(points[i], points[i + 1], points[i + 2], points[i + 3], points[i + 4], points[i + 5]);
                    break;
                case 4:
                    into.close();
                    break;
            }
            i += need;
        }
        into.setFillType(c.i() != 0 ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
        return into;
    }

    /** The first `count` numbers of `array` into `out`, or `fallback` where absent (the composition's JSON). */
    static void floats(JSONArray array, float[] out, int count, float... fallback) {
        for (int i = 0; i < count; i++) {
            float value = i < fallback.length ? fallback[i] : 0;
            out[i] = array != null && i < array.length() ? (float) array.optDouble(i, value) : value;
        }
    }

    static void lineStyle(Paint paint, float width, int cap, int join, float miter, float[] dashes, float dashOffset) {
        paint.setStrokeWidth(width);
        paint.setStrokeCap(cap == 1 ? Paint.Cap.ROUND : cap == 2 ? Paint.Cap.SQUARE : Paint.Cap.BUTT);
        paint.setStrokeJoin(
                join == 1 ? Paint.Join.ROUND : join == 2 ? Paint.Join.BEVEL : Paint.Join.MITER);
        paint.setStrokeMiter(miter);
        paint.setPathEffect(null);
        if (dashes == null || dashes.length == 0) return;
        // DashPathEffect wants an even count; the canvas and SVG repeat an odd list twice.
        int count = dashes.length;
        float[] intervals = new float[count % 2 == 1 ? count * 2 : count];
        float sum = 0;
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = Math.max(0, dashes[i % count]);
            sum += intervals[i];
        }
        if (sum > 0) paint.setPathEffect(new DashPathEffect(intervals, dashOffset));
    }
}
