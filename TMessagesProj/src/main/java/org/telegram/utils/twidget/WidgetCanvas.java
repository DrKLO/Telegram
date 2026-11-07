package org.telegram.utils.twidget;

import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
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
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.nio.charset.StandardCharsets;

/**
 * Worker-owned bitmap of one {@code <canvas>}. Runs the drawing operations TwidgetCore's shared
 * interpreter already resolved (canvas_ops.h), as the bridge writes them (encodeCanvasOp): geometry
 * arrives in canvas pixels, so this only fills, strokes, clips and blits. Java port of upstream
 * CanvasBitmaps.kt.
 *
 * <p>The working bitmap is mutated only here. Display lists get an immutable copy that is never
 * recycled or changed: RenderThread may still read it.
 */
final class WidgetCanvas {
    interface Resources {
        Paint font(int id);

        Bitmap image(int id);

        /** The working bitmap of another canvas, worker-owned. */
        Bitmap canvasBitmap(int id);
    }

    /** Before API 29, the Porter-Duff modes of Composite (canvas_ops.h order); null: source-over. */
    private static final PorterDuff.Mode[] PORTER_DUFF_MODES = {
        null, PorterDuff.Mode.SRC_IN, PorterDuff.Mode.SRC_OUT, PorterDuff.Mode.SRC_ATOP,
        PorterDuff.Mode.DST_OVER, PorterDuff.Mode.DST_IN, PorterDuff.Mode.DST_OUT, PorterDuff.Mode.DST_ATOP,
        PorterDuff.Mode.ADD, PorterDuff.Mode.SRC, PorterDuff.Mode.XOR, PorterDuff.Mode.MULTIPLY,
        PorterDuff.Mode.SCREEN, PorterDuff.Mode.OVERLAY, PorterDuff.Mode.DARKEN, PorterDuff.Mode.LIGHTEN,
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint layerPaint = new Paint();
    private final Paint clear = new Paint();
    private final Path path = new Path();
    private final Path clipPath = new Path();
    private final Matrix user = new Matrix();
    private final Matrix inverse = new Matrix();
    private final float[] values = new float[9];
    private final float[] args = new float[6];
    private final float[] imageRect = new float[8];
    private final RectF destination = new RectF();
    private final Rect source = new Rect();
    private final int id;
    private Bitmap bitmap, snapshot;
    private Canvas canvas;
    private int version, snapshotVersion = -1;
    // The batch being read and the op read from it, as the bridge writes them (encodeCanvasOp).
    private int[] words;
    private int at;
    private int kind, composite, paintKind, color, shadowColor;
    private float alpha, shadowX, shadowY, shadowBlur;
    private int[] stopColors;
    private float[] stopOffsets;
    private int lineCap, lineJoin;
    private float lineWidth, miterLimit, dashOffset;
    private float[] dashes;
    private int font, canvasSource, imageSource;
    private String text;
    private float textX, textY, textScale, textDx;
    private boolean strokeText, smoothing;

    WidgetCanvas(int id) {
        this.id = id;
        clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    long allocationBytes() {
        return bitmap == null ? 0 : (long) bitmap.getWidth() * bitmap.getHeight() * 8;
    }

    Bitmap bitmap() {
        return bitmap;
    }

    /** Immutable pixels for a display list, copied once per change. */
    // The recorder thread takes snapshots while the host thread runs batches and releases the bitmap.
    synchronized Bitmap snapshot() {
        if (bitmap == null) return null;
        if (snapshotVersion != version || snapshot == null) {
            // The previous copy may be referenced by a published frame: replace, never recycle.
            snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            snapshotVersion = version;
        }
        return snapshot;
    }

    /** A w × h bitmap, (re)created when the size changes, runs the batch. */
    synchronized void run(int width, int height, int[] ops, Resources resources) {
        if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
            // The old working bitmap was never published (display lists get copies): safe to drop.
            if (bitmap != null) bitmap.recycle();
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
        }
        words = ops;
        at = 0;
        while (at < ops.length) op(resources);
        words = null;
        version++;
    }

    /** Straight RGBA of a region; transparent outside the bitmap. */
    byte[] read(int x, int y, int width, int height) {
        byte[] out = new byte[Math.max(width, 0) * Math.max(height, 0) * 4];
        if (bitmap == null) return out;
        int x0 = Math.max(0, x), y0 = Math.max(0, y);
        int x1 = Math.min(bitmap.getWidth(), x + width), y1 = Math.min(bitmap.getHeight(), y + height);
        if (x1 <= x0 || y1 <= y0) return out;
        int[] pixels = new int[(x1 - x0) * (y1 - y0)];
        // getPixels answers unpremultiplied colours, which is what getImageData wants.
        bitmap.getPixels(pixels, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0);
        for (int row = y0; row < y1; row++) {
            for (int column = x0; column < x1; column++) {
                int color = pixels[(row - y0) * (x1 - x0) + (column - x0)];
                int offset = ((row - y) * width + (column - x)) * 4;
                out[offset] = (byte) Color.red(color);
                out[offset + 1] = (byte) Color.green(color);
                out[offset + 2] = (byte) Color.blue(color);
                out[offset + 3] = (byte) Color.alpha(color);
            }
        }
        return out;
    }

    synchronized void release() {
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
        canvas = null;
        snapshot = null;
    }

    private float nextFloat() {
        return Float.intBitsToFloat(words[at++]);
    }

    /** A geometry into `into`: op codes, then their points. */
    private void nextPath(Path into, boolean winding) {
        into.rewind();
        int ops = words[at++], count = words[at++];
        int[] w = words;
        int op = at, point = at + ops;
        for (int end = point; op < end; op++) {
            switch (w[op]) {
                case 0:
                    into.moveTo(Float.intBitsToFloat(w[point]), Float.intBitsToFloat(w[point + 1]));
                    point += 2;
                    break;
                case 1:
                    into.lineTo(Float.intBitsToFloat(w[point]), Float.intBitsToFloat(w[point + 1]));
                    point += 2;
                    break;
                case 2:
                    into.quadTo(
                            Float.intBitsToFloat(w[point]), Float.intBitsToFloat(w[point + 1]),
                            Float.intBitsToFloat(w[point + 2]), Float.intBitsToFloat(w[point + 3]));
                    point += 4;
                    break;
                case 3:
                    into.cubicTo(
                            Float.intBitsToFloat(w[point]), Float.intBitsToFloat(w[point + 1]),
                            Float.intBitsToFloat(w[point + 2]), Float.intBitsToFloat(w[point + 3]),
                            Float.intBitsToFloat(w[point + 4]), Float.intBitsToFloat(w[point + 5]));
                    point += 6;
                    break;
                case 4:
                    into.close();
                    break;
            }
        }
        at += ops + count;
        if (winding) into.setFillType(Path.FillType.WINDING);
    }

    private static void fillRule(Path into, int evenodd) {
        into.setFillType(evenodd != 0 ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
    }

    private byte[] nextBytes() {
        int size = words[at++];
        byte[] out = new byte[size];
        for (int i = 0; i < size; i++) out[i] = (byte) (words[at + (i >> 2)] >>> ((i & 3) * 8));
        at += (size + 3) >> 2;
        return out;
    }

    private void op(Resources resources) {
        kind = words[at++];
        if (kind == 0) { // Reset
            bitmap.eraseColor(Color.TRANSPARENT);
            return;
        }
        if (kind == 6) { // PutImage: raw pixels, outside drawing state
            putImage();
            return;
        }
        int clips = words[at++];
        if (clips > 0) {
            canvas.save();
            for (int i = 0; i < clips; i++) {
                nextPath(clipPath, false);
                fillRule(clipPath, words[at++]);
                canvas.clipPath(clipPath);
            }
        }
        if (kind == 1 || kind == 2 || kind == 3) nextPath(path, kind == 3);
        if (kind == 3) { // Clear
            canvas.drawPath(path, clear);
        } else {
            readOp();
            draw(resources);
        }
        if (clips > 0) canvas.restore();
    }

    /** What follows an op's clips and path. */
    private void readOp() {
        int[] w = words;
        if (kind == 1 || kind == 2) fillRule(path, w[at]);
        at++;
        values[0] = nextFloat();
        values[3] = nextFloat();
        values[1] = nextFloat();
        values[4] = nextFloat();
        values[2] = nextFloat();
        values[5] = nextFloat();
        alpha = Math.max(0, Math.min(1, nextFloat()));
        composite = w[at++];
        boolean shadow = w[at++] != 0;
        shadowColor = 0;
        if (shadow) {
            shadowColor = w[at++];
            shadowX = nextFloat();
            shadowY = nextFloat();
            shadowBlur = nextFloat();
        }
        if (kind == 1 || kind == 2 || kind == 4) {
            paintKind = w[at++];
            stopColors = null;
            stopOffsets = null;
            color = 0;
            if (paintKind == 0) {
                color = w[at++];
            } else {
                for (int i = 0; i < 6; i++) args[i] = nextFloat();
                readStops();
            }
        }
        strokeText = false;
        if (kind == 4) readText();
        if (kind == 2 || strokeText) readLine();
        if (kind == 5) {
            canvasSource = w[at++];
            imageSource = w[at++];
            for (int i = 0; i < 8; i++) imageRect[i] = nextFloat();
            smoothing = w[at++] != 0;
        }
    }

    private void readStops() {
        int count = words[at++];
        if (count == 0) return;
        int[] colors = new int[Math.max(2, count)];
        float[] offsets = new float[count == 1 ? 2 : count];
        for (int i = 0; i < count; i++) {
            float offset = nextFloat();
            colors[i] = words[at++];
            offsets[i] = Math.max(0, Math.min(1, offset));
        }
        if (count == 1) {
            colors[1] = colors[0];
            offsets[0] = 0;
            offsets[1] = 1;
        }
        stopColors = colors;
        stopOffsets = offsets;
    }

    private void readLine() {
        lineWidth = nextFloat();
        lineCap = words[at++];
        lineJoin = words[at++];
        miterLimit = nextFloat();
        int count = words[at++];
        dashes = null;
        if (count > 0) {
            dashes = new float[count];
            for (int i = 0; i < count; i++) dashes[i] = nextFloat();
        }
        dashOffset = nextFloat();
    }

    private void readText() {
        font = words[at++];
        text = new String(nextBytes(), StandardCharsets.UTF_8);
        textX = nextFloat();
        textY = nextFloat();
        textScale = nextFloat();
        textDx = nextFloat();
        strokeText = words[at++] != 0;
    }

    private void draw(Resources resources) {
        if (kind == 1 && paintKind == 0 && alpha == 1 && composite == 0 && shadowColor == 0) {
            // A solid fill: the path already is in canvas px and its paint has nothing in user space.
            solid.setColor(color);
            canvas.drawPath(path, solid);
            return;
        }
        if (shadowColor != 0) {
            shadow(resources);
        } else if (alpha < 1 || composite != 0) {
            // A layer applies alpha and the mode to the operation as a whole, as the canvas does.
            layerPaint.reset();
            layerPaint.setAlpha(Math.round(alpha * 255));
            composite(layerPaint, composite);
            canvas.saveLayer(null, layerPaint);
            content(canvas, resources);
            canvas.restore();
        } else {
            content(canvas, resources);
        }
    }

    private void shadow(Resources resources) {
        // The shadow is cast by the whole operation in canvas px, under it: draw the content once
        // into a scratch bitmap, then its blurred alpha at the offset, then the content.
        Bitmap scratch =
                Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        content(new Canvas(scratch), resources);
        Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG);
        float sigma = shadowBlur / 2f;
        if (sigma > .5f)
            blur.setMaskFilter(
                    new BlurMaskFilter((sigma - .5f) / .57735f, BlurMaskFilter.Blur.NORMAL));
        int[] offset = new int[2];
        Bitmap mask = scratch.extractAlpha(blur, offset);
        layerPaint.reset();
        layerPaint.setAlpha(Math.round(alpha * 255));
        composite(layerPaint, composite);
        canvas.saveLayer(null, layerPaint);
        Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tint.setColor(shadowColor);
        canvas.drawBitmap(mask, offset[0] + shadowX, offset[1] + shadowY, tint);
        canvas.drawBitmap(scratch, 0, 0, null);
        canvas.restore();
        // Both are local to this call and never published.
        mask.recycle();
        scratch.recycle();
    }

    /** One operation's content at full alpha and source-over; restores `target`'s state. */
    private void content(Canvas target, Resources resources) {
        values[6] = 0;
        values[7] = 0;
        values[8] = 1;
        user.setValues(values);
        paint.reset();
        paint.setFlags(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        target.save();
        switch (kind) {
            case 1: // Fill
            case 2: // Stroke
                // The path is in canvas px; paints and line widths are in user space.
                if (!user.invert(inverse)) break;
                path.transform(inverse);
                target.concat(user);
                applyStyle(paint);
                if (kind == 2) {
                    paint.setStyle(Paint.Style.STROKE);
                    lineStyle(paint);
                }
                target.drawPath(path, paint);
                break;
            case 4: // Text
                Paint fontPaint = resources.font(font);
                if (fontPaint == null) break;
                Paint textPaint = new Paint(fontPaint);
                target.concat(user);
                target.translate(textX, textY);
                target.scale(textScale, 1);
                textPaint.setShader(null);
                applyStyle(textPaint);
                if (strokeText) {
                    textPaint.setStyle(Paint.Style.STROKE);
                    lineStyle(textPaint);
                }
                target.drawText(text, textDx, 0, textPaint);
                break;
            case 5: // Image
                Bitmap image;
                boolean self = false;
                if (canvasSource != 0) {
                    image = canvasSource == id ? bitmap : resources.canvasBitmap(canvasSource);
                    self = canvasSource == id;
                } else image = resources.image(imageSource);
                if (image == null) break;
                // Drawing a canvas onto itself reads what it held before the draw.
                Bitmap copy = self ? image.copy(Bitmap.Config.ARGB_8888, false) : null;
                if (copy != null) image = copy;
                float[] r = imageRect;
                target.concat(user);
                paint.setFilterBitmap(smoothing);
                destination.set(r[4], r[5], r[4] + r[6], r[5] + r[7]);
                if (r[2] < 0) target.drawBitmap(image, null, destination, paint);
                else {
                    source.set((int) r[0], (int) r[1], (int) (r[0] + r[2]), (int) (r[1] + r[3]));
                    target.drawBitmap(image, source, destination, paint);
                }
                if (copy != null) copy.recycle();
                break;
        }
        target.restore();
    }

    private void lineStyle(Paint target) {
        target.setStrokeWidth(lineWidth);
        target.setStrokeCap(lineCap == 1 ? Paint.Cap.ROUND : lineCap == 2 ? Paint.Cap.SQUARE : Paint.Cap.BUTT);
        target.setStrokeJoin(lineJoin == 1 ? Paint.Join.ROUND : lineJoin == 2 ? Paint.Join.BEVEL : Paint.Join.MITER);
        target.setStrokeMiter(miterLimit);
        target.setPathEffect(null);
        if (dashes == null) return;
        // DashPathEffect wants an even count; the canvas repeats an odd list twice.
        int count = dashes.length;
        float[] intervals = new float[count % 2 == 1 ? count * 2 : count];
        float sum = 0;
        for (int i = 0; i < intervals.length; i++) {
            intervals[i] = Math.max(0, dashes[i % count]);
            sum += intervals[i];
        }
        if (sum > 0) target.setPathEffect(new DashPathEffect(intervals, dashOffset));
    }

    /** The op's fill or stroke style, in user space. */
    private void applyStyle(Paint target) {
        if (paintKind == 0 || stopColors == null) {
            target.setShader(null);
            target.setColor(color);
            return;
        }
        int[] colors = stopColors;
        float[] offsets = stopOffsets;
        target.setColor(Color.BLACK);
        if (paintKind == 1) {
            target.setShader(
                    new LinearGradient(
                            args[0], args[1], args[2], args[3], colors, offsets,
                            Shader.TileMode.CLAMP));
        } else if (paintKind == 2) {
            // Android's radial gradient starts at radius 0 around the outer circle's centre: an
            // inner circle squeezes the stops outwards.
            float inner = args[2], outer = Math.max(args[5], .01f);
            if (inner > 0 && inner < outer) {
                float[] squeezed = new float[offsets.length];
                for (int i = 0; i < offsets.length; i++)
                    squeezed[i] = inner / outer + offsets[i] * (1 - inner / outer);
                offsets = squeezed;
            }
            target.setShader(
                    new RadialGradient(
                            args[3], args[4], outer, colors, offsets, Shader.TileMode.CLAMP));
        } else {
            SweepGradient sweep = new SweepGradient(args[1], args[2], colors, offsets);
            Matrix rotation = new Matrix();
            rotation.setRotate((float) Math.toDegrees(args[0]), args[1], args[2]);
            sweep.setLocalMatrix(rotation);
            target.setShader(sweep);
        }
    }

    private void putImage() {
        int dx = (int) nextFloat(), dy = (int) nextFloat();
        int width = words[at++], height = words[at++];
        int size = words[at++], first = at;
        at += (size + 3) >> 2;
        if (width <= 0 || height <= 0 || size != width * height * 4) return;
        int x0 = Math.max(0, dx), y0 = Math.max(0, dy);
        int x1 = Math.min(bitmap.getWidth(), dx + width);
        int y1 = Math.min(bitmap.getHeight(), dy + height);
        if (x1 <= x0 || y1 <= y0) return;
        int[] pixels = new int[(x1 - x0) * (y1 - y0)];
        for (int row = y0; row < y1; row++) {
            for (int column = x0; column < x1; column++) {
                // A word holds R, G, B, A from its lowest byte up.
                int rgba = words[first + (row - dy) * width + (column - dx)];
                pixels[(row - y0) * (x1 - x0) + (column - x0)] =
                        (rgba & 0xff00ff00) | ((rgba & 0xff) << 16) | ((rgba >>> 16) & 0xff);
            }
        }
        // setPixels takes unpremultiplied colours: putImageData's straight alpha.
        bitmap.setPixels(pixels, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0);
    }

    /**
     * globalCompositeOperation, in the order of Composite in canvas_ops.h. Before API 29 the separable and
     * non-separable blend modes beyond Porter-Duff (color-dodge … luminosity) draw as source-over.
     */
    private static void composite(Paint target, int index) {
        if (Build.VERSION.SDK_INT >= 29) {
            Blend.apply(target, index);
            return;
        }
        PorterDuff.Mode mode = index > 0 && index < PORTER_DUFF_MODES.length ? PORTER_DUFF_MODES[index] : null;
        target.setXfermode(mode == null ? null : new PorterDuffXfermode(mode));
    }

    @RequiresApi(29)
    private static final class Blend {
        private static final BlendMode[] MODES = {
            BlendMode.SRC_OVER, BlendMode.SRC_IN, BlendMode.SRC_OUT, BlendMode.SRC_ATOP,
            BlendMode.DST_OVER, BlendMode.DST_IN, BlendMode.DST_OUT, BlendMode.DST_ATOP,
            BlendMode.PLUS, BlendMode.SRC, BlendMode.XOR, BlendMode.MULTIPLY, BlendMode.SCREEN,
            BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN, BlendMode.COLOR_DODGE,
            BlendMode.COLOR_BURN, BlendMode.HARD_LIGHT, BlendMode.SOFT_LIGHT, BlendMode.DIFFERENCE,
            BlendMode.EXCLUSION, BlendMode.HUE, BlendMode.SATURATION, BlendMode.COLOR,
            BlendMode.LUMINOSITY,
        };

        static void apply(Paint target, int index) {
            target.setBlendMode(index > 0 && index < MODES.length ? MODES[index] : null);
        }
    }
}
