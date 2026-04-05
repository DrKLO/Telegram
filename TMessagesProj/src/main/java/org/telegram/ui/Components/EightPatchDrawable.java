package org.telegram.ui.Components;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.graphics.NinePatch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class EightPatchDrawable extends Drawable {

    private static final int DEFAULT_DENSITY = DisplayMetrics.DENSITY_DEFAULT;

    private final Rect srcTL = new Rect();
    private final Rect srcTC = new Rect();
    private final Rect srcTR = new Rect();
    private final Rect srcML = new Rect();
    private final Rect srcMR = new Rect();
    private final Rect srcBL = new Rect();
    private final Rect srcBC = new Rect();
    private final Rect srcBR = new Rect();

    private final Rect dstTL = new Rect();
    private final Rect dstTC = new Rect();
    private final Rect dstTR = new Rect();
    private final Rect dstML = new Rect();
    private final Rect dstMR = new Rect();
    private final Rect dstBL = new Rect();
    private final Rect dstBC = new Rect();
    private final Rect dstBR = new Rect();

    private EightPatchState state;
    private boolean mutated;

    private PorterDuffColorFilter tintFilter;
    private ColorFilter explicitColorFilter;

    private boolean geometryValid;

    public EightPatchDrawable(@NonNull Bitmap bitmap,
                                    @NonNull byte[] chunk,
                                    @Nullable Rect padding,
                                    @Nullable String srcName) {
        this(null, bitmap, chunk, padding, srcName);
    }

    public EightPatchDrawable(@Nullable Resources res,
                                    @NonNull Bitmap bitmap,
                                    @NonNull byte[] chunk,
                                    @Nullable Rect padding,
                                    @Nullable String srcName) {
        state = new EightPatchState(res, bitmap, chunk, padding, srcName);
        updateLocalState();
        rebuildSourcePatches();
        rebuildDestPatches(getBounds());
    }

    private EightPatchDrawable(@NonNull EightPatchState state) {
        this.state = new EightPatchState(state);
        updateLocalState();
        rebuildSourcePatches();
        rebuildDestPatches(getBounds());
    }

    private void updateLocalState() {
        tintFilter = createTintFilter(state.tint, state.tintMode);
        geometryValid = false;
    }

    private void rebuildSourcePatches() {
        geometryValid = false;

        final Bitmap bitmap = state.bitmap;
        final ChunkInfo chunk = state.chunkInfo;
        if (bitmap == null || bitmap.isRecycled() || chunk == null) {
            clearDst();
            return;
        }

        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();

        final int x0 = 0;
        final int x1 = chunk.xDivs[0];
        final int x2 = chunk.xDivs[1];
        final int x3 = w;

        final int y0 = 0;
        final int y1 = chunk.yDivs[0];
        final int y2 = chunk.yDivs[1];
        final int y3 = h;

        srcTL.set(x0, y0, x1, y1);
        srcTC.set(x1, y0, x2, y1);
        srcTR.set(x2, y0, x3, y1);

        srcML.set(x0, y1, x1, y2);
        srcMR.set(x2, y1, x3, y2);

        srcBL.set(x0, y2, x1, y3);
        srcBC.set(x1, y2, x2, y3);
        srcBR.set(x2, y2, x3, y3);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        rebuildDestPatches(bounds);
    }

    private void rebuildDestPatches(@NonNull Rect bounds) {
        geometryValid = false;

        final Bitmap bitmap = state.bitmap;
        final ChunkInfo chunk = state.chunkInfo;
        if (bitmap == null || bitmap.isRecycled() || chunk == null || bounds.isEmpty()) {
            clearDst();
            return;
        }

        final float scale = getDensityScale();

        final int srcW = bitmap.getWidth();
        final int srcH = bitmap.getHeight();

        final int sx0 = 0;
        final int sx1 = chunk.xDivs[0];
        final int sx2 = chunk.xDivs[1];
        final int sx3 = srcW;

        final int sy0 = 0;
        final int sy1 = chunk.yDivs[0];
        final int sy2 = chunk.yDivs[1];
        final int sy3 = srcH;

        final int segW0 = Math.round((sx1 - sx0) * scale);
        final int segW1 = Math.round((sx2 - sx1) * scale);
        final int segW2 = Math.round((sx3 - sx2) * scale);

        final int segH0 = Math.round((sy1 - sy0) * scale);
        final int segH1 = Math.round((sy2 - sy1) * scale);
        final int segH2 = Math.round((sy3 - sy2) * scale);

        final Axis xAxis = computeAxis(bounds.left, bounds.width(), segW0, segW1, segW2);
        final Axis yAxis = computeAxis(bounds.top, bounds.height(), segH0, segH1, segH2);

        final int x0 = xAxis.p0;
        final int x1 = xAxis.p1;
        final int x2 = xAxis.p2;
        final int x3 = xAxis.p3;

        final int y0 = yAxis.p0;
        final int y1 = yAxis.p1;
        final int y2 = yAxis.p2;
        final int y3 = yAxis.p3;

        dstTL.set(x0, y0, x1, y1);
        dstTC.set(x1, y0, x2, y1);
        dstTR.set(x2, y0, x3, y1);

        dstML.set(x0, y1, x1, y2);
        dstMR.set(x2, y1, x3, y2);

        dstBL.set(x0, y2, x1, y3);
        dstBC.set(x1, y2, x2, y3);
        dstBR.set(x2, y2, x3, y3);

        geometryValid = true;
    }

    private static Axis computeAxis(int dstStart, int dstSize, int seg0, int seg1, int seg2) {
        final int total = seg0 + seg1 + seg2;
        final int fixed = seg0 + seg2;

        final int out0;
        final int out1;
        final int out2;

        if (dstSize <= 0 || total <= 0) {
            out0 = 0;
            out1 = 0;
            out2 = 0;
        } else if (dstSize >= fixed) {
            out0 = seg0;
            out1 = dstSize - fixed;
            out2 = seg2;
        } else {
            final float k = (float) dstSize / (float) total;
            final int s0 = Math.round(seg0 * k);
            final int s1 = Math.round(seg1 * k);
            int s2 = dstSize - s0 - s1;
            if (s2 < 0) {
                s2 = 0;
            }
            out0 = s0;
            out1 = s1;
            out2 = s2;
        }

        final Axis axis = new Axis();
        axis.p0 = dstStart;
        axis.p1 = dstStart + out0;
        axis.p2 = axis.p1 + out1;
        axis.p3 = dstStart + dstSize;
        return axis;
    }

    private void clearDst() {
        dstTL.setEmpty();
        dstTC.setEmpty();
        dstTR.setEmpty();
        dstML.setEmpty();
        dstMR.setEmpty();
        dstBL.setEmpty();
        dstBC.setEmpty();
        dstBR.setEmpty();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!geometryValid) {
            return;
        }

        final Bitmap bitmap = state.bitmap;
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }

        final Paint paint = state.paint;
        final int oldAlpha = paint.getAlpha();
        final ColorFilter oldColorFilter = paint.getColorFilter();

        final int combinedAlpha = oldAlpha * state.alpha / 255;
        if (combinedAlpha != oldAlpha) {
            paint.setAlpha(combinedAlpha);
        }

        final ColorFilter newColorFilter = explicitColorFilter != null ? explicitColorFilter : tintFilter;
        if (oldColorFilter != newColorFilter) {
            paint.setColorFilter(newColorFilter);
        }

        drawPatch(canvas, bitmap, srcTL, dstTL, paint);
        drawPatch(canvas, bitmap, srcTC, dstTC, paint);
        drawPatch(canvas, bitmap, srcTR, dstTR, paint);
        drawPatch(canvas, bitmap, srcML, dstML, paint);
        drawPatch(canvas, bitmap, srcMR, dstMR, paint);
        drawPatch(canvas, bitmap, srcBL, dstBL, paint);
        drawPatch(canvas, bitmap, srcBC, dstBC, paint);
        drawPatch(canvas, bitmap, srcBR, dstBR, paint);

        if (oldColorFilter != newColorFilter) {
            paint.setColorFilter(oldColorFilter);
        }
        if (combinedAlpha != oldAlpha) {
            paint.setAlpha(oldAlpha);
        }
    }

    private static void drawPatch(@NonNull Canvas canvas,
                                  @NonNull Bitmap bitmap,
                                  @NonNull Rect src,
                                  @NonNull Rect dst,
                                  @NonNull Paint paint) {
        if (src.isEmpty() || dst.isEmpty()) {
            return;
        }
        canvas.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        alpha = clamp255(alpha);
        if (state.alpha != alpha) {
            state.alpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return state.alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        if (explicitColorFilter != colorFilter) {
            explicitColorFilter = colorFilter;
            invalidateSelf();
        }
    }

    @Nullable
    @Override
    public ColorFilter getColorFilter() {
        return explicitColorFilter;
    }

    @Override
    public void setTintList(@Nullable ColorStateList tint) {
        if (state.tint != tint) {
            state.tint = tint;
            tintFilter = createTintFilter(tint, state.tintMode);
            invalidateSelf();
        }
    }

    @Override
    public void setTintMode(@NonNull PorterDuff.Mode tintMode) {
        if (state.tintMode != tintMode) {
            state.tintMode = tintMode;
            tintFilter = createTintFilter(state.tint, tintMode);
            invalidateSelf();
        }
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        if (state.tint != null && state.tint.isStateful()) {
            final PorterDuffColorFilter newFilter = createTintFilter(state.tint, state.tintMode);
            if (!sameTintFilter(tintFilter, newFilter)) {
                tintFilter = newFilter;
                invalidateSelf();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isStateful() {
        return (state.tint != null && state.tint.isStateful()) || super.isStateful();
    }

    @Override
    public int getOpacity() {
        final Bitmap bitmap = state.bitmap;
        if (bitmap == null) {
            return PixelFormat.TRANSPARENT;
        }
        if (state.alpha < 255 || bitmap.hasAlpha() || state.paint.getAlpha() < 255) {
            return PixelFormat.TRANSLUCENT;
        }
        return PixelFormat.OPAQUE;
    }

    @Override
    public boolean getPadding(@NonNull Rect padding) {
        if (state.padding != null) {
            padding.set(state.padding);
            return (padding.left | padding.top | padding.right | padding.bottom) != 0;
        }
        padding.setEmpty();
        return false;
    }

    @Override
    public int getIntrinsicWidth() {
        final Bitmap bitmap = state.bitmap;
        return bitmap != null ? scaleFromSourceDensity(bitmap.getWidth()) : -1;
    }

    @Override
    public int getIntrinsicHeight() {
        final Bitmap bitmap = state.bitmap;
        return bitmap != null ? scaleFromSourceDensity(bitmap.getHeight()) : -1;
    }

    @Override
    public int getMinimumWidth() {
        return getIntrinsicWidth();
    }

    @Override
    public int getMinimumHeight() {
        return getIntrinsicHeight();
    }

    public Paint getPaint() {
        return state.paint;
    }

    public void setFilterBitmap(boolean filter) {
        if (state.paint.isFilterBitmap() != filter) {
            state.paint.setFilterBitmap(filter);
            invalidateSelf();
        }
    }

    public boolean isFilterBitmap() {
        return state.paint.isFilterBitmap();
    }

    public void setDither(boolean dither) {
        if (state.paint.isDither() != dither) {
            state.paint.setDither(dither);
            invalidateSelf();
        }
    }

    public void setTargetDensity(@NonNull Canvas canvas) {
        setTargetDensity(canvas.getDensity());
    }

    public void setTargetDensity(@NonNull DisplayMetrics metrics) {
        setTargetDensity(metrics.densityDpi);
    }

    public void setTargetDensity(int density) {
        if (density == 0) {
            density = DEFAULT_DENSITY;
        }
        if (state.targetDensity != density) {
            state.targetDensity = density;
            rebuildDestPatches(getBounds());
            invalidateSelf();
        }
    }

    public int getTargetDensity() {
        return state.targetDensity;
    }

    @Nullable
    @Override
    public ConstantState getConstantState() {
        state.changingConfigurations = getChangingConfigurations();
        return state;
    }

    @NonNull
    @Override
    public Drawable mutate() {
        if (!mutated && super.mutate() == this) {
            state = new EightPatchState(state);
            mutated = true;
        }
        return this;
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | state.changingConfigurations;
    }

    private float getDensityScale() {
        final int sourceDensity = state.sourceDensity != 0 ? state.sourceDensity : DEFAULT_DENSITY;
        final int targetDensity = state.targetDensity != 0 ? state.targetDensity : DEFAULT_DENSITY;
        return (float) targetDensity / (float) sourceDensity;
    }

    private int scaleFromSourceDensity(int value) {
        return Math.round(value * getDensityScale());
    }

    private PorterDuffColorFilter createTintFilter(@Nullable ColorStateList tint,
                                                   @Nullable PorterDuff.Mode tintMode) {
        if (tint == null || tintMode == null) {
            return null;
        }
        final int color = tint.getColorForState(getState(), tint.getDefaultColor());
        return new PorterDuffColorFilter(color, tintMode);
    }

    private static boolean sameTintFilter(@Nullable PorterDuffColorFilter a,
                                          @Nullable PorterDuffColorFilter b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class Axis {
        int p0;
        int p1;
        int p2;
        int p3;
    }

    private static final class ChunkInfo {
        final int[] xDivs;
        final int[] yDivs;
        final Rect padding;

        ChunkInfo(int[] xDivs, int[] yDivs, Rect padding) {
            this.xDivs = xDivs;
            this.yDivs = yDivs;
            this.padding = padding;
        }

        @Nullable
        static ChunkInfo parse(@NonNull byte[] chunk, @Nullable Rect fallbackPadding, int bitmapW, int bitmapH) {
            if (!NinePatch.isNinePatchChunk(chunk) || chunk.length < 32) {
                return null;
            }

            try {
                final ByteBuffer buffer = ByteBuffer.wrap(chunk).order(ByteOrder.nativeOrder());

                final byte wasSerialized = buffer.get();
                if (wasSerialized == 0) {
                    return null;
                }

                final int xDivCount = buffer.get() & 0xFF;
                final int yDivCount = buffer.get() & 0xFF;
                final int colorCount = buffer.get() & 0xFF;

                if (xDivCount != 2 || yDivCount != 2) {
                    throw new IllegalArgumentException("EightPatchDrawable supports only 3x3 nine-patch");
                }

                buffer.getInt();
                buffer.getInt();

                final int paddingLeft = buffer.getInt();
                final int paddingRight = buffer.getInt();
                final int paddingTop = buffer.getInt();
                final int paddingBottom = buffer.getInt();

                buffer.getInt();

                final int[] xDivs = new int[2];
                final int[] yDivs = new int[2];

                xDivs[0] = clamp(buffer.getInt(), 0, bitmapW);
                xDivs[1] = clamp(buffer.getInt(), xDivs[0], bitmapW);

                yDivs[0] = clamp(buffer.getInt(), 0, bitmapH);
                yDivs[1] = clamp(buffer.getInt(), yDivs[0], bitmapH);

                for (int i = 0; i < colorCount && buffer.remaining() >= 4; i++) {
                    buffer.getInt();
                }

                final Rect parsedPadding = new Rect(
                        paddingLeft,
                        paddingTop,
                        paddingRight,
                        paddingBottom
                );

                return new ChunkInfo(
                        xDivs,
                        yDivs,
                        fallbackPadding != null ? new Rect(fallbackPadding) : parsedPadding
                );
            } catch (Throwable ignore) {
                return null;
            }
        }
    }

    static final class EightPatchState extends ConstantState {
        final Bitmap bitmap;
        final byte[] chunk;
        final String srcName;
        final Rect padding;
        final ChunkInfo chunkInfo;
        final Paint paint;

        int sourceDensity;
        int targetDensity;

        int alpha = 255;
        int changingConfigurations;

        ColorStateList tint;
        PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;

        EightPatchState(@Nullable Resources res,
                        @NonNull Bitmap bitmap,
                        @NonNull byte[] chunk,
                        @Nullable Rect padding,
                        @Nullable String srcName) {
            this.bitmap = bitmap;
            this.chunk = chunk.clone();
            this.srcName = srcName;
            this.padding = padding != null ? new Rect(padding) : null;
            this.chunkInfo = ChunkInfo.parse(this.chunk, this.padding, bitmap.getWidth(), bitmap.getHeight());

            this.paint = new Paint();
            this.paint.setFilterBitmap(true);
            this.paint.setDither(true);

            this.sourceDensity = bitmap.getDensity() != 0 ? bitmap.getDensity() : DEFAULT_DENSITY;
            this.targetDensity = res != null ? res.getDisplayMetrics().densityDpi : sourceDensity;
        }

        EightPatchState(@NonNull EightPatchState other) {
            this.bitmap = other.bitmap;
            this.chunk = other.chunk.clone();
            this.srcName = other.srcName;
            this.padding = other.padding != null ? new Rect(other.padding) : null;
            this.chunkInfo = ChunkInfo.parse(this.chunk, this.padding, bitmap.getWidth(), bitmap.getHeight());

            this.paint = new Paint(other.paint);

            this.sourceDensity = other.sourceDensity;
            this.targetDensity = other.targetDensity;
            this.alpha = other.alpha;
            this.changingConfigurations = other.changingConfigurations;
            this.tint = other.tint;
            this.tintMode = other.tintMode;
        }

        @NonNull
        @Override
        public Drawable newDrawable() {
            return new EightPatchDrawable(this);
        }

        @NonNull
        @Override
        public Drawable newDrawable(@Nullable Resources res) {
            final EightPatchDrawable d = new EightPatchDrawable(this);
            if (res != null) {
                d.setTargetDensity(res.getDisplayMetrics());
            }
            return d;
        }

        @Override
        public int getChangingConfigurations() {
            return changingConfigurations;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}