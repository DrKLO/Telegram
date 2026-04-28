package org.telegram.messenger.utils;

import android.graphics.Bitmap;
import android.graphics.NinePatch;
import android.graphics.Canvas;
import android.graphics.DrawFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Region.Op;
import android.graphics.text.MeasuredText;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Canvas} decorator that records every drawing/state call into an ordered list of
 * {@link Command} objects and can replay the first <em>N</em> of them onto any other Canvas.
 *
 * <p>Use {@link #logCommands()} to dump the full recorded command list to logcat via
 * {@code Log.i(TAG, ...)}.
 *
 * <p>Deep-copy policy
 * <ul>
 *   <li>{@link Paint}   – copied via {@code new Paint(src)}</li>
 *   <li>{@link Matrix}  – copied via {@code new Matrix(src)}</li>
 *   <li>{@link Bitmap}  – copied via {@code src.copy(src.getConfig(), false)}</li>
 *   <li>{@link Path}    – copied via {@code new Path(src)}</li>
 *   <li>{@link Rect}, {@link RectF} – copied via copy-constructors</li>
 *   <li>{@link float[]}, {@link int[]} – copied via {@code Arrays.copyOf}</li>
 *   <li>Immutable / opaque objects ({@code Shader}, {@code DrawFilter}, {@code String},
 *       primitives, enums) – stored by reference (no copy needed).</li>
 * </ul>
 *
 * <p>Replay closes every unclosed {@code save / saveLayer / saveLayerAlpha} automatically.
 */
@SuppressWarnings({"deprecation", "unused"})
public class DebugRecordingCanvas extends Canvas {

    private static final String TAG = "DebugRecordingCanvas";

    /** Coordinates exceeding this absolute value are flagged as suspicious. */
    private static final float COORD_WARN_THRESHOLD = 20_000f;

    // ──────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static String paintInfo(@Nullable Paint p) {
        if (p == null) return "paint=null";
        return "paint(alpha=" + p.getAlpha() + ")";
    }

    private static String bitmapInfo(@Nullable Bitmap bm) {
        if (bm == null) return "bitmap=null";
        return "bitmap(" + bm.getWidth() + "x" + bm.getHeight() + ")";
    }

    private static String matrixInfo(@Nullable Matrix m) {
        if (m == null) return "matrix=null";
        float[] v = new float[9];
        m.getValues(v);
        return String.format("matrix([%.2f,%.2f,%.2f][%.2f,%.2f,%.2f][%.2f,%.2f,%.2f])",
                v[0],v[1],v[2], v[3],v[4],v[5], v[6],v[7],v[8]);
    }

    private static String rectInfo(@Nullable RectF r) {
        if (r == null) return "rect=null";
        return "rect(" + r.left + "," + r.top + "," + r.right + "," + r.bottom + ")";
    }

    /** Returns a warning prefix if any coordinate exceeds the threshold, empty string otherwise. */
    private static String coordWarn(float... coords) {
        for (float c : coords) {
            if (Math.abs(c) > COORD_WARN_THRESHOLD) {
                return "⚠ COORD_OUT_OF_RANGE ";
            }
        }
        return "";
    }

    /** Returns a warning if paint alpha is <= 0. */
    private static String paintWarn(@Nullable Paint p) {
        if (p != null && p.getAlpha() <= 0) return "⚠ PAINT_ALPHA_ZERO ";
        return "";
    }

    /**
     * Validates a command at record time and logs a warning immediately if something
     * looks wrong. The warning is also a breakpoint-friendly site: put a breakpoint
     * on the {@code Log.w} line inside this method to catch bad draws as they happen.
     */
    private void validate(@NonNull Command cmd) {
        String desc = cmd.toString();
        if (desc.contains("⚠")) {
            Log.w(TAG, "recorded [" + mCommands.size() + "] " + desc);
        }
    }

    /**
     * Records a command: validates it first (logging any warning immediately),
     * then appends it to the list.
     */
    private void record(@NonNull Command cmd) {
        validate(cmd);
        mCommands.add(cmd);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Command sealed hierarchy
    // ──────────────────────────────────────────────────────────────────────────

    /** Base class for all recorded commands. */
    public abstract static class Command {
        /** Replay this command onto {@code target}. */
        public abstract void replay(@NonNull Canvas target);
    }

    // ── State commands ────────────────────────────────────────────────────────

    public static final class SaveCmd extends Command {
        @Override public void replay(@NonNull Canvas c) { c.save(); }
        @Override public String toString() { return "save()"; }
    }

    public static final class SaveLayerCmd extends Command {
        final RectF bounds;
        final Paint paint;
        final int saveFlags;

        SaveLayerCmd(@Nullable RectF bounds, @Nullable Paint paint) {
            this.bounds = bounds != null ? new RectF(bounds) : null;
            this.paint  = paint  != null ? new Paint(paint)  : null;
            this.saveFlags = 0;
        }

        @Override public void replay(@NonNull Canvas c) {
            c.saveLayer(bounds, paint);
        }

        @Override public String toString() {
            return paintWarn(paint)
                    + "saveLayer(" + rectInfo(bounds) + " " + paintInfo(paint) + ")";
        }
    }

    public static final class SaveLayerAlphaCmd extends Command {
        final RectF bounds;
        final int   alpha;

        SaveLayerAlphaCmd(@Nullable RectF bounds, int alpha) {
            this.bounds = bounds != null ? new RectF(bounds) : null;
            this.alpha  = alpha;
        }

        @Override public void replay(@NonNull Canvas c) {
            c.saveLayerAlpha(bounds, alpha);
        }

        @Override public String toString() {
            String warn = (alpha <= 0 || alpha >= 255) ? "⚠ ALPHA_SUSPICIOUS(" + alpha + ") " : "";
            return warn + "saveLayerAlpha(alpha=" + alpha + " " + rectInfo(bounds) + ")";
        }
    }

    public static final class RestoreCmd extends Command {
        @Override public void replay(@NonNull Canvas c) { c.restore(); }
        @Override public String toString() { return "restore()"; }
    }

    public static final class RestoreToCountCmd extends Command {
        final int count;
        RestoreToCountCmd(int count) { this.count = count; }
        @Override public void replay(@NonNull Canvas c) { c.restoreToCount(count); }
        @Override public String toString() { return "restoreToCount(" + count + ")"; }
    }

    // ── Transform commands ────────────────────────────────────────────────────

    public static final class TranslateCmd extends Command {
        final float dx, dy;
        TranslateCmd(float dx, float dy) { this.dx = dx; this.dy = dy; }
        @Override public void replay(@NonNull Canvas c) { c.translate(dx, dy); }
        @Override public String toString() {
            return coordWarn(dx, dy) + "translate(" + dx + "," + dy + ")";
        }
    }

    public static final class ScaleCmd extends Command {
        final float sx, sy;
        ScaleCmd(float sx, float sy) { this.sx = sx; this.sy = sy; }
        @Override public void replay(@NonNull Canvas c) { c.scale(sx, sy); }
        @Override public String toString() { return "scale(" + sx + "," + sy + ")"; }
    }

    public static final class RotateCmd extends Command {
        final float deg;
        RotateCmd(float deg) { this.deg = deg; }
        @Override public void replay(@NonNull Canvas c) { c.rotate(deg); }
        @Override public String toString() { return "rotate(" + deg + ")"; }
    }

    public static final class SkewCmd extends Command {
        final float sx, sy;
        SkewCmd(float sx, float sy) { this.sx = sx; this.sy = sy; }
        @Override public void replay(@NonNull Canvas c) { c.skew(sx, sy); }
        @Override public String toString() { return "skew(" + sx + "," + sy + ")"; }
    }

    public static final class ConcatCmd extends Command {
        final Matrix matrix;
        ConcatCmd(@Nullable Matrix m) { this.matrix = m != null ? new Matrix(m) : null; }
        @Override public void replay(@NonNull Canvas c) { c.concat(matrix); }
        @Override public String toString() { return "concat(" + matrixInfo(matrix) + ")"; }
    }

    public static final class SetMatrixCmd extends Command {
        final Matrix matrix;
        SetMatrixCmd(@Nullable Matrix m) { this.matrix = m != null ? new Matrix(m) : null; }
        @Override public void replay(@NonNull Canvas c) { c.setMatrix(matrix); }
        @Override public String toString() { return "setMatrix(" + matrixInfo(matrix) + ")"; }
    }

    // ── Clip commands ─────────────────────────────────────────────────────────

    public static final class ClipRectCmd extends Command {
        final RectF     rect;
        final Region.Op op;
        final boolean   hasOp;

        ClipRectCmd(RectF r, Region.Op op) {
            this.rect = new RectF(r); this.op = op; this.hasOp = op != null;
        }
        ClipRectCmd(float l, float t, float r, float b, Region.Op op) {
            this(new RectF(l, t, r, b), op);
        }
        ClipRectCmd(float l, float t, float r, float b) {
            this.rect = new RectF(l, t, r, b); this.op = null; this.hasOp = false;
        }

        @Override public void replay(@NonNull Canvas c) {
            if (hasOp) c.clipRect(rect, op); else c.clipRect(rect);
        }

        @Override public String toString() {
            String opStr = hasOp ? " op=" + op : "";
            return coordWarn(rect.left, rect.top, rect.right, rect.bottom)
                    + "clipRect(" + rectInfo(rect) + opStr + ")";
        }
    }

    public static final class ClipPathCmd extends Command {
        final Path      path;
        final Region.Op op;
        final boolean   hasOp;

        ClipPathCmd(Path p) { this.path = new Path(p); this.op = null; this.hasOp = false; }
        ClipPathCmd(Path p, Region.Op op) { this.path = new Path(p); this.op = op; this.hasOp = true; }

        @Override public void replay(@NonNull Canvas c) {
            if (hasOp) c.clipPath(path, op); else c.clipPath(path);
        }

        @Override public String toString() {
            String opStr = hasOp ? " op=" + op : "";
            return "clipPath(" + opStr + ")";
        }
    }

    // ── Filter ────────────────────────────────────────────────────────────────

    public static final class SetDrawFilterCmd extends Command {
        final DrawFilter filter;
        SetDrawFilterCmd(DrawFilter f) { this.filter = f; }
        @Override public void replay(@NonNull Canvas c) { c.setDrawFilter(filter); }
        @Override public String toString() { return "setDrawFilter(" + filter + ")"; }
    }

    // ── Draw commands ─────────────────────────────────────────────────────────

    public static final class DrawColorCmd extends Command {
        final int color;
        final PorterDuff.Mode mode;
        final boolean hasMode;

        DrawColorCmd(int color) { this.color = color; this.mode = null; this.hasMode = false; }
        DrawColorCmd(int color, PorterDuff.Mode mode) { this.color = color; this.mode = mode; this.hasMode = true; }

        @Override public void replay(@NonNull Canvas c) {
            if (hasMode) c.drawColor(color, mode); else c.drawColor(color);
        }

        @Override public String toString() {
            int a = (color >>> 24);
            String warn = a <= 0 ? "⚠ PAINT_ALPHA_ZERO " : "";
            String modeStr = hasMode ? " mode=" + mode : "";
            return warn + "drawColor(0x" + Integer.toHexString(color) + modeStr + ")";
        }
    }

    public static final class DrawPaintCmd extends Command {
        final Paint paint;
        DrawPaintCmd(Paint p) { this.paint = new Paint(p); }
        @Override public void replay(@NonNull Canvas c) { c.drawPaint(paint); }
        @Override public String toString() {
            return paintWarn(paint) + "drawPaint(" + paintInfo(paint) + ")";
        }
    }

    public static final class DrawArcCmd extends Command {
        final RectF  oval;
        final float  startAngle, sweepAngle;
        final boolean useCenter;
        final Paint  paint;

        DrawArcCmd(RectF oval, float startAngle, float sweepAngle, boolean useCenter, Paint p) {
            this.oval = new RectF(oval); this.startAngle = startAngle; this.sweepAngle = sweepAngle;
            this.useCenter = useCenter; this.paint = new Paint(p);
        }

        @Override public void replay(@NonNull Canvas c) {
            c.drawArc(oval, startAngle, sweepAngle, useCenter, paint);
        }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(oval.left, oval.top, oval.right, oval.bottom)
                    + "drawArc(" + rectInfo(oval)
                    + " start=" + startAngle + " sweep=" + sweepAngle
                    + " center=" + useCenter + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawBitmapCmd extends Command {
        final Bitmap bitmap;
        final float left, top;
        final Paint paint;

        DrawBitmapCmd(Bitmap bm, float left, float top, @Nullable Paint p) {
            this.bitmap = bm.copy(bm.getConfig() != null ? bm.getConfig() : Bitmap.Config.ARGB_8888, false);
            this.left = left; this.top = top; this.paint = p != null ? new Paint(p) : null;
        }

        @Override public void replay(@NonNull Canvas c) { c.drawBitmap(bitmap, left, top, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(left, top)
                    + "drawBitmap(" + bitmapInfo(bitmap)
                    + " x=" + left + " y=" + top + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawBitmapSrcDstFCmd extends Command {
        final Bitmap bitmap;
        final Rect   src;
        final RectF  dst;
        final Paint  paint;

        DrawBitmapSrcDstFCmd(Bitmap bm, @Nullable Rect src, RectF dst, @Nullable Paint p) {
            this.bitmap = bm.copy(bm.getConfig() != null ? bm.getConfig() : Bitmap.Config.ARGB_8888, false);
            this.src = src != null ? new Rect(src) : null;
            this.dst = new RectF(dst); this.paint = p != null ? new Paint(p) : null;
        }

        @Override public void replay(@NonNull Canvas c) { c.drawBitmap(bitmap, src, dst, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(dst.left, dst.top, dst.right, dst.bottom)
                    + "drawBitmap(" + bitmapInfo(bitmap)
                    + " src=" + src + " dst=" + rectInfo(dst) + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawBitmapSrcDstCmd extends Command {
        final Bitmap bitmap;
        final Rect   src;
        final Rect   dst;
        final Paint  paint;

        DrawBitmapSrcDstCmd(Bitmap bm, @Nullable Rect src, Rect dst, @Nullable Paint p) {
            this.bitmap = bm.copy(bm.getConfig() != null ? bm.getConfig() : Bitmap.Config.ARGB_8888, false);
            this.src = src != null ? new Rect(src) : null;
            this.dst = new Rect(dst); this.paint = p != null ? new Paint(p) : null;
        }

        @Override public void replay(@NonNull Canvas c) { c.drawBitmap(bitmap, src, dst, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(dst.left, dst.top, dst.right, dst.bottom)
                    + "drawBitmap(" + bitmapInfo(bitmap)
                    + " src=" + src + " dst=" + dst + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawBitmapMatrixCmd extends Command {
        final Bitmap bitmap;
        final Matrix matrix;
        final Paint  paint;

        DrawBitmapMatrixCmd(Bitmap bm, Matrix m, @Nullable Paint p) {
            this.bitmap = bm.copy(bm.getConfig() != null ? bm.getConfig() : Bitmap.Config.ARGB_8888, false);
            this.matrix = new Matrix(m); this.paint = p != null ? new Paint(p) : null;
        }

        @Override public void replay(@NonNull Canvas c) { c.drawBitmap(bitmap, matrix, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + "drawBitmap(" + bitmapInfo(bitmap)
                    + " " + matrixInfo(matrix) + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawBitmapMeshCmd extends Command {
        final Bitmap  bitmap;
        final int     meshWidth, meshHeight;
        final float[] verts;
        final int     vertOffset;
        final int[]   colors;
        final int     colorOffset;
        final Paint   paint;

        DrawBitmapMeshCmd(Bitmap bm, int mw, int mh, float[] verts, int vo,
                          @Nullable int[] colors, int co, @Nullable Paint p) {
            this.bitmap = bm.copy(bm.getConfig() != null ? bm.getConfig() : Bitmap.Config.ARGB_8888, false);
            this.meshWidth = mw; this.meshHeight = mh;
            this.verts = java.util.Arrays.copyOf(verts, verts.length); this.vertOffset = vo;
            this.colors = colors != null ? java.util.Arrays.copyOf(colors, colors.length) : null;
            this.colorOffset = co;
            this.paint = p != null ? new Paint(p) : null;
        }

        @Override public void replay(@NonNull Canvas c) {
            c.drawBitmapMesh(bitmap, meshWidth, meshHeight, verts, vertOffset, colors, colorOffset, paint);
        }

        @Override public String toString() {
            return paintWarn(paint)
                    + "drawBitmapMesh(" + bitmapInfo(bitmap)
                    + " mesh=" + meshWidth + "x" + meshHeight + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawCircleCmd extends Command {
        final float cx, cy, radius;
        final Paint paint;

        DrawCircleCmd(float cx, float cy, float r, Paint p) {
            this.cx = cx; this.cy = cy; this.radius = r; this.paint = new Paint(p);
        }

        @Override public void replay(@NonNull Canvas c) { c.drawCircle(cx, cy, radius, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(cx, cy)
                    + "drawCircle(cx=" + cx + " cy=" + cy + " r=" + radius
                    + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawLineCmd extends Command {
        final float startX, startY, stopX, stopY;
        final Paint paint;

        DrawLineCmd(float x0, float y0, float x1, float y1, Paint p) {
            startX = x0; startY = y0; stopX = x1; stopY = y1; paint = new Paint(p);
        }

        @Override public void replay(@NonNull Canvas c) { c.drawLine(startX, startY, stopX, stopY, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(startX, startY, stopX, stopY)
                    + "drawLine(" + startX + "," + startY + " → " + stopX + "," + stopY
                    + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawLinesCmd extends Command {
        final float[] pts;
        final int     offset, count;
        final Paint   paint;

        DrawLinesCmd(float[] pts, int offset, int count, Paint p) {
            this.pts = java.util.Arrays.copyOf(pts, pts.length);
            this.offset = offset; this.count = count; this.paint = new Paint(p);
        }
        DrawLinesCmd(float[] pts, Paint p) { this(pts, 0, pts.length, p); }

        @Override public void replay(@NonNull Canvas c) { c.drawLines(pts, offset, count, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + "drawLines(count=" + count + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawOvalCmd extends Command {
        final RectF oval;
        final Paint paint;

        DrawOvalCmd(RectF oval, Paint p) { this.oval = new RectF(oval); this.paint = new Paint(p); }

        @Override public void replay(@NonNull Canvas c) { c.drawOval(oval, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(oval.left, oval.top, oval.right, oval.bottom)
                    + "drawOval(" + rectInfo(oval) + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawPathCmd extends Command {
        final Path  path;
        final Paint paint;

        DrawPathCmd(Path path, Paint p) { this.path = new Path(path); this.paint = new Paint(p); }

        @Override public void replay(@NonNull Canvas c) { c.drawPath(path, paint); }

        @Override public String toString() {
            return paintWarn(paint) + "drawPath(" + paintInfo(paint) + ")";
        }
    }

    public static final class DrawPictureCmd extends Command {
        final Picture picture;
        final RectF   dst;
        final boolean hasDst;

        DrawPictureCmd(Picture pic) { this.picture = pic; this.dst = null; this.hasDst = false; }
        DrawPictureCmd(Picture pic, RectF dst) { this.picture = pic; this.dst = new RectF(dst); this.hasDst = true; }
        DrawPictureCmd(Picture pic, Rect dst)  { this.picture = pic; this.dst = new RectF(dst); this.hasDst = true; }

        @Override public void replay(@NonNull Canvas c) {
            if (hasDst) c.drawPicture(picture, dst); else c.drawPicture(picture);
        }

        @Override public String toString() {
            String dstStr = hasDst ? " dst=" + rectInfo(dst) : "";
            return "drawPicture(size=" + picture.getWidth() + "x" + picture.getHeight() + dstStr + ")";
        }
    }

    public static final class DrawPointCmd extends Command {
        final float x, y;
        final Paint paint;

        DrawPointCmd(float x, float y, Paint p) { this.x = x; this.y = y; this.paint = new Paint(p); }

        @Override public void replay(@NonNull Canvas c) { c.drawPoint(x, y, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(x, y)
                    + "drawPoint(" + x + "," + y + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawPointsCmd extends Command {
        final float[] pts;
        final int     offset, count;
        final Paint   paint;

        DrawPointsCmd(float[] pts, int offset, int count, Paint p) {
            this.pts = java.util.Arrays.copyOf(pts, pts.length);
            this.offset = offset; this.count = count; this.paint = new Paint(p);
        }
        DrawPointsCmd(float[] pts, Paint p) { this(pts, 0, pts.length, p); }

        @Override public void replay(@NonNull Canvas c) { c.drawPoints(pts, offset, count, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + "drawPoints(count=" + count + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawRectCmd extends Command {
        final RectF rect;
        final Paint paint;

        DrawRectCmd(RectF r, Paint p)                          { this.rect = new RectF(r); this.paint = new Paint(p); }
        DrawRectCmd(float l, float t, float r, float b, Paint p) { this(new RectF(l, t, r, b), p); }
        DrawRectCmd(Rect r, Paint p)                           { this(new RectF(r), p); }

        @Override public void replay(@NonNull Canvas c) { c.drawRect(rect, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(rect.left, rect.top, rect.right, rect.bottom)
                    + "drawRect(" + rectInfo(rect) + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawRoundRectCmd extends Command {
        final RectF rect;
        final float rx, ry;
        final Paint paint;

        DrawRoundRectCmd(RectF r, float rx, float ry, Paint p) {
            this.rect = new RectF(r); this.rx = rx; this.ry = ry; this.paint = new Paint(p);
        }

        @Override public void replay(@NonNull Canvas c) { c.drawRoundRect(rect, rx, ry, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(rect.left, rect.top, rect.right, rect.bottom)
                    + "drawRoundRect(" + rectInfo(rect) + " rx=" + rx + " ry=" + ry
                    + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawTextCmd extends Command {
        final CharSequence text;
        final int   start, end;
        final float x, y;
        final Paint paint;
        final boolean isCharArray, isSpanned;
        final int charArrayOffset, charArrayCount;

        DrawTextCmd(char[] text, int index, int count, float x, float y, Paint p) {
            this.text = new String(text, index, count);
            this.start = 0; this.end = count;
            this.x = x; this.y = y; this.paint = new Paint(p);
            this.charArrayOffset = index; this.charArrayCount = count;
            this.isCharArray = true; this.isSpanned = false;
        }
        DrawTextCmd(CharSequence text, int start, int end, float x, float y, Paint p) {
            this.text = text.toString().substring(start, end);
            this.start = 0; this.end = end - start;
            this.x = x; this.y = y; this.paint = new Paint(p);
            this.charArrayOffset = 0; this.charArrayCount = 0;
            this.isCharArray = false; this.isSpanned = true;
        }
        DrawTextCmd(String text, float x, float y, Paint p) {
            this.text = text; this.start = 0; this.end = text.length();
            this.x = x; this.y = y; this.paint = new Paint(p);
            this.charArrayOffset = 0; this.charArrayCount = 0;
            this.isCharArray = false; this.isSpanned = false;
        }
        DrawTextCmd(String text, int start, int end, float x, float y, Paint p) {
            this.text = text.substring(start, end); this.start = 0; this.end = end - start;
            this.x = x; this.y = y; this.paint = new Paint(p);
            this.charArrayOffset = 0; this.charArrayCount = 0;
            this.isCharArray = false; this.isSpanned = false;
        }

        @Override public void replay(@NonNull Canvas c) {
            c.drawText(text.toString(), 0, text.length(), x, y, paint);
        }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(x, y)
                    + "drawText(\"" + text + "\" x=" + x + " y=" + y + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawTextOnPathCmd extends Command {
        final String text;
        final Path   path;
        final float  hOffset, vOffset;
        final Paint  paint;

        DrawTextOnPathCmd(char[] text, int index, int count, Path path, float h, float v, Paint p) {
            this.text = new String(text, index, count);
            this.path = new Path(path); this.hOffset = h; this.vOffset = v; this.paint = new Paint(p);
        }
        DrawTextOnPathCmd(String text, Path path, float h, float v, Paint p) {
            this.text = text;
            this.path = new Path(path); this.hOffset = h; this.vOffset = v; this.paint = new Paint(p);
        }

        @Override public void replay(@NonNull Canvas c) { c.drawTextOnPath(text, path, hOffset, vOffset, paint); }

        @Override public String toString() {
            return paintWarn(paint)
                    + "drawTextOnPath(\"" + text + "\" h=" + hOffset + " v=" + vOffset
                    + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawTextRunCmd extends Command {
        final char[]  chars;
        final String  str;
        final int     index, count, contextIndex, contextCount, start, end;
        final float   x, y;
        final boolean isRtl, isCharArray;
        final Paint   paint;

        DrawTextRunCmd(char[] chars, int index, int count, int ctxIndex, int ctxCount,
                       float x, float y, boolean rtl, Paint p) {
            this.chars = java.util.Arrays.copyOf(chars, chars.length);
            this.str = null; this.index = index; this.count = count;
            this.contextIndex = ctxIndex; this.contextCount = ctxCount;
            this.start = 0; this.end = 0;
            this.x = x; this.y = y; this.isRtl = rtl; this.isCharArray = true; this.paint = new Paint(p);
        }
        DrawTextRunCmd(CharSequence str, int start, int end, int ctxStart, int ctxEnd,
                       float x, float y, boolean rtl, Paint p) {
            this.chars = null; this.str = str.toString();
            this.index = 0; this.count = 0;
            this.contextIndex = ctxStart; this.contextCount = ctxEnd;
            this.start = start; this.end = end;
            this.x = x; this.y = y; this.isRtl = rtl; this.isCharArray = false; this.paint = new Paint(p);
        }

        @Override public void replay(@NonNull Canvas c) {
            if (isCharArray) {
                c.drawTextRun(chars, index, count, contextIndex, contextCount, x, y, isRtl, paint);
            } else {
                c.drawTextRun(str, start, end, contextIndex, contextCount, x, y, isRtl, paint);
            }
        }

        @Override public String toString() {
            String text = isCharArray ? new String(chars, index, count) : str;
            return paintWarn(paint)
                    + coordWarn(x, y)
                    + "drawTextRun(\"" + text + "\" x=" + x + " y=" + y
                    + " rtl=" + isRtl + " " + paintInfo(paint) + ")";
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static final class DrawTextRunMeasuredCmd extends Command {
        final MeasuredText text;
        final int   start, end, contextStart, contextEnd;
        final float x, y;
        final boolean isRtl;
        final Paint paint;

        DrawTextRunMeasuredCmd(MeasuredText mt, int s, int e, int cs, int ce,
                               float x, float y, boolean rtl, Paint p) {
            this.text = mt; this.start = s; this.end = e;
            this.contextStart = cs; this.contextEnd = ce;
            this.x = x; this.y = y; this.isRtl = rtl; this.paint = new Paint(p);
        }

        @Override public void replay(@NonNull Canvas c) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
            }
        }

        @Override public String toString() {
            return paintWarn(paint)
                    + coordWarn(x, y)
                    + "drawTextRun(MeasuredText start=" + start + " end=" + end
                    + " x=" + x + " y=" + y + " rtl=" + isRtl + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawVerticesCmd extends Command {
        final Canvas.VertexMode mode;
        final int               vertexCount;
        final float[]           verts;
        final int               vertOffset;
        final float[]           texs;
        final int               texOffset;
        final int[]             colors;
        final int               colorOffset;
        final short[]           indices;
        final int               indexOffset, indexCount;
        final Paint             paint;

        DrawVerticesCmd(Canvas.VertexMode mode, int vertexCount,
                        float[] verts, int vertOffset,
                        @Nullable float[] texs, int texOffset,
                        @Nullable int[] colors, int colorOffset,
                        @Nullable short[] indices, int indexOffset, int indexCount,
                        Paint p) {
            this.mode = mode; this.vertexCount = vertexCount;
            this.verts = java.util.Arrays.copyOf(verts, verts.length); this.vertOffset = vertOffset;
            this.texs  = texs   != null ? java.util.Arrays.copyOf(texs,   texs.length)   : null;
            this.texOffset = texOffset;
            this.colors = colors != null ? java.util.Arrays.copyOf(colors, colors.length) : null;
            this.colorOffset = colorOffset;
            this.indices = indices != null ? java.util.Arrays.copyOf(indices, indices.length) : null;
            this.indexOffset = indexOffset; this.indexCount = indexCount;
            this.paint = new Paint(p);
        }

        @Override public void replay(@NonNull Canvas c) {
            c.drawVertices(mode, vertexCount, verts, vertOffset, texs, texOffset,
                    colors, colorOffset, indices, indexOffset, indexCount, paint);
        }

        @Override public String toString() {
            return paintWarn(paint)
                    + "drawVertices(mode=" + mode + " vertexCount=" + vertexCount
                    + " " + paintInfo(paint) + ")";
        }
    }

    public static final class DrawPatchCmd extends Command {
        final NinePatch patch;
        final Rect      dst;
        final RectF     dstF;
        final Paint     paint;

        private static NinePatch copyPatch(@NonNull NinePatch patch) {
            // NinePatch is backed by an immutable bitmap+chunk — share by reference.
            return patch;
        }

        DrawPatchCmd(@NonNull NinePatch patch, @NonNull Rect dst, @Nullable Paint p) {
            this.patch = copyPatch(patch);
            this.dst   = new Rect(dst); this.dstF = null;
            this.paint = p != null ? new Paint(p) : null;
        }

        DrawPatchCmd(@NonNull NinePatch patch, @NonNull RectF dst, @Nullable Paint p) {
            this.patch = copyPatch(patch);
            this.dst   = null; this.dstF = new RectF(dst);
            this.paint = p != null ? new Paint(p) : null;
        }

        @Override public void replay(@NonNull Canvas c) {
            if (dstF != null) c.drawPatch(patch, dstF, paint);
            else              c.drawPatch(patch, dst,  paint);
        }

        @Override public String toString() {
            String dstStr = dstF != null ? rectInfo(dstF) : String.valueOf(dst);
            return paintWarn(paint)
                    + "drawPatch(dst=" + dstStr + " " + paintInfo(paint) + ")";
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Fields
    // ──────────────────────────────────────────────────────────────────────────

    private final List<Command> mCommands = new ArrayList<>();

    // ──────────────────────────────────────────────────────────────────────────
    //  Constructors
    // ──────────────────────────────────────────────────────────────────────────

    public DebugRecordingCanvas(@NonNull Bitmap bitmap) { super(bitmap); }
    public DebugRecordingCanvas() { super(); }

    // ──────────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────────

    @NonNull
    public List<Command> getCommands() {
        return java.util.Collections.unmodifiableList(mCommands);
    }

    public void clearCommands() { mCommands.clear(); }

    public int getCommandCount() { return mCommands.size(); }

    /**
     * Dumps all recorded commands to logcat at INFO level.
     * Commands with warnings (suspicious coordinates, zero alpha, etc.)
     * are additionally emitted at WARN level so they stand out in the log.
     */
    public void logCommands() {
        Log.i(TAG, "=== DebugRecordingCanvas: " + mCommands.size() + " command(s) ===");
        for (int i = 0; i < mCommands.size(); i++) {
            String line = "[" + i + "] " + mCommands.get(i);
            if (line.contains("⚠")) {
                Log.w(TAG, line);
            } else {
                Log.i(TAG, line);
            }
        }
        Log.i(TAG, "=== end ===");
    }

    /**
     * Replays the first {@code n} recorded commands onto {@code target}.
     * Any unclosed save/saveLayer/saveLayerAlpha are closed automatically.
     */
    public void replayCommands(@NonNull Canvas target, int n) {
        int limit = Math.min(n, mCommands.size());
        int saveDepthBefore = target.getSaveCount();
        for (int i = 0; i < limit; i++) {
            mCommands.get(i).replay(target);
        }
        int openLayers = target.getSaveCount() - saveDepthBefore;
        if (openLayers > 0) {
            target.restoreToCount(saveDepthBefore);
        }
    }

    public void replayAll(@NonNull Canvas target) {
        replayCommands(target, mCommands.size());
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Canvas overrides – State
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public int save() {
        record(new SaveCmd());
        return super.save();
    }

    @Override
    public int saveLayer(@Nullable RectF bounds, @Nullable Paint paint) {
        // not recorded — delegates internally to another saveLayer overload, would duplicate
        return super.saveLayer(bounds, paint);
    }

    @Override
    public int saveLayer(@Nullable RectF bounds, @Nullable Paint paint, int saveFlags) {
        // not recorded — see above
        return super.saveLayer(bounds, paint, saveFlags);
    }

    @Override
    public int saveLayer(float left, float top, float right, float bottom,
                         @Nullable Paint paint, int saveFlags) {
        record(new SaveLayerCmd(new RectF(left, top, right, bottom), paint));
        return super.saveLayer(left, top, right, bottom, paint, saveFlags);
    }

    @Override
    public int saveLayer(float left, float top, float right, float bottom, @Nullable Paint paint) {
        // not recorded — see above
        return super.saveLayer(left, top, right, bottom, paint);
    }

    @Override
    public int saveLayerAlpha(@Nullable RectF bounds, int alpha) {
        // not recorded — see above
        return super.saveLayerAlpha(bounds, alpha);
    }

    @Override
    public int saveLayerAlpha(@Nullable RectF bounds, int alpha, int saveFlags) {
        // not recorded — see above
        return super.saveLayerAlpha(bounds, alpha, saveFlags);
    }

    @Override
    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha) {
        // not recorded — see above
        return super.saveLayerAlpha(left, top, right, bottom, alpha);
    }

    @Override
    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha, int saveFlags) {
        record(new SaveLayerAlphaCmd(new RectF(left, top, right, bottom), alpha));
        return super.saveLayerAlpha(left, top, right, bottom, alpha, saveFlags);
    }

    @Override
    public void restore() {
        record(new RestoreCmd());
        super.restore();
    }

    @Override
    public void restoreToCount(int saveCount) {
        record(new RestoreToCountCmd(saveCount));
        super.restoreToCount(saveCount);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Canvas overrides – Transforms
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void translate(float dx, float dy) {
        record(new TranslateCmd(dx, dy));
        super.translate(dx, dy);
    }

    @Override
    public void scale(float sx, float sy) {
        record(new ScaleCmd(sx, sy));
        super.scale(sx, sy);
    }

    @Override
    public void rotate(float degrees) {
        record(new RotateCmd(degrees));
        super.rotate(degrees);
    }

    @Override
    public void skew(float sx, float sy) {
        record(new SkewCmd(sx, sy));
        super.skew(sx, sy);
    }

    @Override
    public void concat(@Nullable Matrix matrix) {
        record(new ConcatCmd(matrix));
        super.concat(matrix);
    }

    @Override
    public void setMatrix(@Nullable Matrix matrix) {
        record(new SetMatrixCmd(matrix));
        super.setMatrix(matrix);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Canvas overrides – Clip
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean clipRect(@NonNull RectF rect) {
        record(new ClipRectCmd(rect, null));
        return super.clipRect(rect);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean clipRect(@NonNull RectF rect, @NonNull Region.Op op) {
        record(new ClipRectCmd(rect, op));
        return super.clipRect(rect, op);
    }

    @Override
    public boolean clipRect(@NonNull Rect rect) {
        record(new ClipRectCmd(new RectF(rect), null));
        return super.clipRect(rect);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean clipRect(@NonNull Rect rect, @NonNull Region.Op op) {
        record(new ClipRectCmd(new RectF(rect), op));
        return super.clipRect(rect, op);
    }

    @Override
    public boolean clipRect(float left, float top, float right, float bottom) {
        record(new ClipRectCmd(left, top, right, bottom));
        return super.clipRect(left, top, right, bottom);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean clipRect(float left, float top, float right, float bottom, @NonNull Region.Op op) {
        record(new ClipRectCmd(left, top, right, bottom, op));
        return super.clipRect(left, top, right, bottom, op);
    }

    @Override
    public boolean clipRect(int left, int top, int right, int bottom) {
        record(new ClipRectCmd(left, top, right, bottom));
        return super.clipRect(left, top, right, bottom);
    }

    @Override
    public boolean clipPath(@NonNull Path path) {
        record(new ClipPathCmd(path));
        return super.clipPath(path);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean clipPath(@NonNull Path path, @NonNull Region.Op op) {
        record(new ClipPathCmd(path, op));
        return super.clipPath(path, op);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Canvas overrides – Filter
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void setDrawFilter(@Nullable DrawFilter filter) {
        record(new SetDrawFilterCmd(filter));
        super.setDrawFilter(filter);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Canvas overrides – Draw
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void drawColor(int color) {
        record(new DrawColorCmd(color));
        super.drawColor(color);
    }

    @Override
    public void drawColor(int color, @NonNull PorterDuff.Mode mode) {
        record(new DrawColorCmd(color, mode));
        super.drawColor(color, mode);
    }

    @Override
    public void drawPaint(@NonNull Paint paint) {
        record(new DrawPaintCmd(paint));
        super.drawPaint(paint);
    }

    @Override
    public void drawArc(@NonNull RectF oval, float startAngle, float sweepAngle,
                        boolean useCenter, @NonNull Paint paint) {
        record(new DrawArcCmd(oval, startAngle, sweepAngle, useCenter, paint));
        super.drawArc(oval, startAngle, sweepAngle, useCenter, paint);
    }

    @Override
    public void drawArc(float left, float top, float right, float bottom,
                        float startAngle, float sweepAngle, boolean useCenter, @NonNull Paint paint) {
        record(new DrawArcCmd(new RectF(left, top, right, bottom), startAngle, sweepAngle, useCenter, paint));
        super.drawArc(left, top, right, bottom, startAngle, sweepAngle, useCenter, paint);
    }

    @Override
    public void drawBitmap(@NonNull Bitmap bitmap, float left, float top, @Nullable Paint paint) {
        record(new DrawBitmapCmd(bitmap, left, top, paint));
        super.drawBitmap(bitmap, left, top, paint);
    }

    @Override
    public void drawBitmap(@NonNull Bitmap bitmap, @Nullable Rect src, @NonNull RectF dst, @Nullable Paint paint) {
        record(new DrawBitmapSrcDstFCmd(bitmap, src, dst, paint));
        super.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public void drawBitmap(@NonNull Bitmap bitmap, @Nullable Rect src, @NonNull Rect dst, @Nullable Paint paint) {
        record(new DrawBitmapSrcDstCmd(bitmap, src, dst, paint));
        super.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public void drawBitmap(@NonNull Bitmap bitmap, @NonNull Matrix matrix, @Nullable Paint paint) {
        record(new DrawBitmapMatrixCmd(bitmap, matrix, paint));
        super.drawBitmap(bitmap, matrix, paint);
    }

    @Override
    public void drawBitmap(@NonNull int[] colors, int offset, int stride,
                           float x, float y, int width, int height,
                           boolean hasAlpha, @Nullable Paint paint) {
        final int[] colorsCopy = java.util.Arrays.copyOf(colors, colors.length);
        final float rx = x, ry = y;
        final int rw = width, rh = height, ro = offset, rs = stride;
        final boolean rha = hasAlpha;
        final Paint rp = paint != null ? new Paint(paint) : null;
        record(new Command() {
            @Override public void replay(@NonNull Canvas c) {
                c.drawBitmap(colorsCopy, ro, rs, rx, ry, rw, rh, rha, rp);
            }
            @Override public String toString() {
                return paintWarn(rp)
                        + coordWarn(rx, ry)
                        + "drawBitmap(int[] " + rw + "x" + rh + " x=" + rx + " y=" + ry
                        + " " + paintInfo(rp) + ")";
            }
        });
        super.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint);
    }

    @Override
    public void drawBitmap(@NonNull int[] colors, int offset, int stride,
                           int x, int y, int width, int height,
                           boolean hasAlpha, @Nullable Paint paint) {
        final int[] colorsCopy = java.util.Arrays.copyOf(colors, colors.length);
        final int rx = x, ry = y, rw = width, rh = height, ro = offset, rs = stride;
        final boolean rha = hasAlpha;
        final Paint rp = paint != null ? new Paint(paint) : null;
        record(new Command() {
            @Override public void replay(@NonNull Canvas c) {
                c.drawBitmap(colorsCopy, ro, rs, rx, ry, rw, rh, rha, rp);
            }
            @Override public String toString() {
                return paintWarn(rp)
                        + coordWarn(rx, ry)
                        + "drawBitmap(int[] " + rw + "x" + rh + " x=" + rx + " y=" + ry
                        + " " + paintInfo(rp) + ")";
            }
        });
        super.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint);
    }

    @Override
    public void drawBitmapMesh(@NonNull Bitmap bitmap, int meshWidth, int meshHeight,
                               @NonNull float[] verts, int vertOffset,
                               @Nullable int[] colors, int colorOffset,
                               @Nullable Paint paint) {
        record(new DrawBitmapMeshCmd(bitmap, meshWidth, meshHeight, verts, vertOffset, colors, colorOffset, paint));
        super.drawBitmapMesh(bitmap, meshWidth, meshHeight, verts, vertOffset, colors, colorOffset, paint);
    }

    @Override
    public void drawCircle(float cx, float cy, float radius, @NonNull Paint paint) {
        record(new DrawCircleCmd(cx, cy, radius, paint));
        super.drawCircle(cx, cy, radius, paint);
    }

    @Override
    public void drawLine(float startX, float startY, float stopX, float stopY, @NonNull Paint paint) {
        record(new DrawLineCmd(startX, startY, stopX, stopY, paint));
        super.drawLine(startX, startY, stopX, stopY, paint);
    }

    @Override
    public void drawLines(@NonNull float[] pts, int offset, int count, @NonNull Paint paint) {
        record(new DrawLinesCmd(pts, offset, count, paint));
        super.drawLines(pts, offset, count, paint);
    }

    @Override
    public void drawLines(@NonNull float[] pts, @NonNull Paint paint) {
        record(new DrawLinesCmd(pts, paint));
        super.drawLines(pts, paint);
    }

    @Override
    public void drawOval(@NonNull RectF oval, @NonNull Paint paint) {
        record(new DrawOvalCmd(oval, paint));
        super.drawOval(oval, paint);
    }

    @Override
    public void drawOval(float left, float top, float right, float bottom, @NonNull Paint paint) {
        record(new DrawOvalCmd(new RectF(left, top, right, bottom), paint));
        super.drawOval(left, top, right, bottom, paint);
    }

    @Override
    public void drawPath(@NonNull Path path, @NonNull Paint paint) {
        record(new DrawPathCmd(path, paint));
        super.drawPath(path, paint);
    }

    @Override
    public void drawPicture(@NonNull Picture picture) {
        record(new DrawPictureCmd(picture));
        super.drawPicture(picture);
    }

    @Override
    public void drawPicture(@NonNull Picture picture, @NonNull RectF dst) {
        record(new DrawPictureCmd(picture, dst));
        super.drawPicture(picture, dst);
    }

    @Override
    public void drawPicture(@NonNull Picture picture, @NonNull Rect dst) {
        record(new DrawPictureCmd(picture, dst));
        super.drawPicture(picture, dst);
    }

    @Override
    public void drawPoint(float x, float y, @NonNull Paint paint) {
        record(new DrawPointCmd(x, y, paint));
        super.drawPoint(x, y, paint);
    }

    @Override
    public void drawPoints(@NonNull float[] pts, int offset, int count, @NonNull Paint paint) {
        record(new DrawPointsCmd(pts, offset, count, paint));
        super.drawPoints(pts, offset, count, paint);
    }

    @Override
    public void drawPoints(@NonNull float[] pts, @NonNull Paint paint) {
        record(new DrawPointsCmd(pts, paint));
        super.drawPoints(pts, paint);
    }

    @Override
    public void drawRect(@NonNull RectF rect, @NonNull Paint paint) {
        record(new DrawRectCmd(rect, paint));
        super.drawRect(rect, paint);
    }

    @Override
    public void drawRect(@NonNull Rect r, @NonNull Paint paint) {
        record(new DrawRectCmd(r, paint));
        super.drawRect(r, paint);
    }

    @Override
    public void drawRect(float left, float top, float right, float bottom, @NonNull Paint paint) {
        record(new DrawRectCmd(left, top, right, bottom, paint));
        super.drawRect(left, top, right, bottom, paint);
    }

    @Override
    public void drawRoundRect(@NonNull RectF rect, float rx, float ry, @NonNull Paint paint) {
        record(new DrawRoundRectCmd(rect, rx, ry, paint));
        super.drawRoundRect(rect, rx, ry, paint);
    }

    @Override
    public void drawRoundRect(float left, float top, float right, float bottom,
                              float rx, float ry, @NonNull Paint paint) {
        record(new DrawRoundRectCmd(new RectF(left, top, right, bottom), rx, ry, paint));
        super.drawRoundRect(left, top, right, bottom, rx, ry, paint);
    }

    @Override
    public void drawText(@NonNull char[] text, int index, int count,
                         float x, float y, @NonNull Paint paint) {
        record(new DrawTextCmd(text, index, count, x, y, paint));
        super.drawText(text, index, count, x, y, paint);
    }

    @Override
    public void drawText(@NonNull CharSequence text, int start, int end,
                         float x, float y, @NonNull Paint paint) {
        record(new DrawTextCmd(text, start, end, x, y, paint));
        super.drawText(text, start, end, x, y, paint);
    }

    @Override
    public void drawText(@NonNull String text, float x, float y, @NonNull Paint paint) {
        record(new DrawTextCmd(text, x, y, paint));
        super.drawText(text, x, y, paint);
    }

    @Override
    public void drawText(@NonNull String text, int start, int end,
                         float x, float y, @NonNull Paint paint) {
        record(new DrawTextCmd(text, start, end, x, y, paint));
        super.drawText(text, start, end, x, y, paint);
    }

    @Override
    public void drawTextOnPath(@NonNull char[] text, int index, int count,
                               @NonNull Path path, float hOffset, float vOffset,
                               @NonNull Paint paint) {
        record(new DrawTextOnPathCmd(text, index, count, path, hOffset, vOffset, paint));
        super.drawTextOnPath(text, index, count, path, hOffset, vOffset, paint);
    }

    @Override
    public void drawTextOnPath(@NonNull String text, @NonNull Path path,
                               float hOffset, float vOffset, @NonNull Paint paint) {
        record(new DrawTextOnPathCmd(text, path, hOffset, vOffset, paint));
        super.drawTextOnPath(text, path, hOffset, vOffset, paint);
    }

    @Override
    public void drawTextRun(@NonNull char[] text, int index, int count,
                            int contextIndex, int contextCount,
                            float x, float y, boolean isRtl, @NonNull Paint paint) {
        record(new DrawTextRunCmd(text, index, count, contextIndex, contextCount, x, y, isRtl, paint));
        super.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint);
    }

    @Override
    public void drawTextRun(@NonNull CharSequence text, int start, int end,
                            int contextStart, int contextEnd,
                            float x, float y, boolean isRtl, @NonNull Paint paint) {
        record(new DrawTextRunCmd(text, start, end, contextStart, contextEnd, x, y, isRtl, paint));
        super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void drawTextRun(@NonNull MeasuredText text, int start, int end,
                            int contextStart, int contextEnd,
                            float x, float y, boolean isRtl, @NonNull Paint paint) {
        record(new DrawTextRunMeasuredCmd(text, start, end, contextStart, contextEnd, x, y, isRtl, paint));
        super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
    }

    @Override
    public void drawVertices(@NonNull VertexMode mode, int vertexCount,
                             @NonNull float[] verts, int vertOffset,
                             @Nullable float[] texs, int texOffset,
                             @Nullable int[] colors, int colorOffset,
                             @Nullable short[] indices, int indexOffset, int indexCount,
                             @NonNull Paint paint) {
        record(new DrawVerticesCmd(mode, vertexCount, verts, vertOffset,
                texs, texOffset, colors, colorOffset, indices, indexOffset, indexCount, paint));
        super.drawVertices(mode, vertexCount, verts, vertOffset,
                texs, texOffset, colors, colorOffset, indices, indexOffset, indexCount, paint);
    }

    @Override
    public void drawPatch(@NonNull NinePatch patch, @NonNull Rect dst, @Nullable Paint paint) {
        record(new DrawPatchCmd(patch, dst, paint));
        super.drawPatch(patch, dst, paint);
    }

    @Override
    public void drawPatch(@NonNull NinePatch patch, @NonNull RectF dst, @Nullable Paint paint) {
        record(new DrawPatchCmd(patch, dst, paint));
        super.drawPatch(patch, dst, paint);
    }
}