package org.telegram.ui.Components.blur3.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.NinePatchDrawable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import androidx.core.math.MathUtils;

public class NinePatchBuilder {
    public static final int TRANSPARENT_COLOR = 0x00000000;
    public static final int NO_COLOR = 0x00000001;

    private NinePatchBuilder() {}

    public static NinePatchDrawable createNinePatch(
        Bitmap[] bitmapRef,
        int fillColor,
        float[] radii,              // 8 values: TLx,TLy, TRx,TRy, BRx,BRy, BLx,BLy
        float shadowRadiusPx,
        int shadowColor,
        float shadowDxPx,
        float shadowDyPx,
        int centralColorHint
    ) {
        if (radii == null || radii.length != 8) {
            throw new IllegalArgumentException("radii must have 8 values: TLx,TLy, TRx,TRy, BRx,BRy, BLx,BLy");
        }

        // Clamp negative radii to 0 (defensive)
        final float tlRx = Math.max(0f, radii[0]);
        final float tlRy = Math.max(0f, radii[1]);
        final float trRx = Math.max(0f, radii[2]);
        final float trRy = Math.max(0f, radii[3]);
        final float brRx = Math.max(0f, radii[4]);
        final float brRy = Math.max(0f, radii[5]);
        final float blRx = Math.max(0f, radii[6]);
        final float blRy = Math.max(0f, radii[7]);

        // 1) Blur padding (safe coefficient)
        final int blurPad = (int) Math.ceil(2.0f * shadowRadiusPx);

        // 2) Shadow insets (asymmetric with dx/dy)
        final int padLeft   = blurPad + (int) Math.ceil(Math.max(0f, -shadowDxPx));
        final int padRight  = blurPad + (int) Math.ceil(Math.max(0f,  shadowDxPx));
        final int padTop    = blurPad + (int) Math.ceil(Math.max(0f, -shadowDyPx));
        final int padBottom = blurPad + (int) Math.ceil(Math.max(0f,  shadowDyPx));

        // 3) Minimal content size for elliptical corners.
        // Horizontal requirement uses Rx; vertical requirement uses Ry.
        final float reqW = Math.max(tlRx + trRx, blRx + brRx);
        final float reqH = Math.max(tlRy + blRy, trRy + brRy);

        final int contentW = (int) Math.ceil(reqW + 2f); // +2px for AA/stability
        final int contentH = (int) Math.ceil(reqH + 2f);

        final int bitmapW = contentW + padLeft + padRight;
        final int bitmapH = contentH + padTop + padBottom;

        final boolean hasBitmapRef = bitmapRef != null && bitmapRef.length == 1;

        Bitmap bitmap = null;
        if (hasBitmapRef && bitmapRef[0] != null) {
            final Bitmap b = bitmapRef[0];
            if (!b.isRecycled() && b.isMutable() && b.getWidth() == bitmapW && b.getHeight() == bitmapH && b.getConfig() == Bitmap.Config.ARGB_8888) {
                b.eraseColor(0);
                bitmap = b;
            }
        }
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888);
        }
        if (hasBitmapRef) {
            bitmapRef[0] = bitmap;
        }

        final Canvas canvas = new Canvas(bitmap);

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fillColor);

        if (shadowRadiusPx > 0f) {
            paint.setShadowLayer(shadowRadiusPx, shadowDxPx, shadowDyPx, shadowColor);
        }

        final RectF rect = new RectF(
                padLeft,
                padTop,
                padLeft + contentW,
                padTop + contentH
        );

        final Path path = new Path();
        // radii are already per-corner (x,y), supports elliptical corners
        path.addRoundRect(rect, new float[]{
                tlRx, tlRy,
                trRx, trRy,
                brRx, brRy,
                blRx, blRy
        }, Path.Direction.CW);

        canvas.drawPath(path, paint);

        // Optional second pass without shadow to keep fill perfectly crisp
        if (shadowRadiusPx > 0f) {
            paint.clearShadowLayer();
            canvas.drawPath(path, paint);
        }

        // 4) Stretch area: central region that must not include any corner curvature.
        // X uses Rx; Y uses Ry.
        final float leftMaxRx  = Math.max(tlRx, blRx);
        final float rightMaxRx = Math.max(trRx, brRx);
        final float topMaxRy   = Math.max(tlRy, trRy);
        final float botMaxRy   = Math.max(blRy, brRy);

        int v3 = padLeft + (int) Math.ceil(leftMaxRx);
        final int x1 = MathUtils.clamp(v3, 1, bitmapW - 2);
        int v2 = bitmapW - padRight - (int) Math.ceil(rightMaxRx);
        final int x2 = MathUtils.clamp(v2, x1 + 1, bitmapW - 1);

        int v1 = padTop + (int) Math.ceil(topMaxRy);
        final int y1 = MathUtils.clamp(v1, 1, bitmapH - 2);
        int v = bitmapH - padBottom - (int) Math.ceil(botMaxRy);
        final int y2 = MathUtils.clamp(v, y1 + 1, bitmapH - 1);

        final byte[] chunk = createNinePatchChunk(
            x1, x2,
            y1, y2,
            padLeft, padTop, padRight, padBottom,
            centralColorHint
        ).array();

        final Rect padding = new Rect(padLeft, padTop, padRight, padBottom);
        return new NinePatchDrawable(bitmap, chunk, padding, null);
    }

    /**
     * One stretch segment on X (x1..x2) and on Y (y1..y2).
     * Padding defines content insets (typically equal to shadow pads).
     */
    public static ByteBuffer createNinePatchChunk(
            int x1, int x2,
            int y1, int y2,
            int padLeft, int padTop,
            int padRight, int padBottom,
            int centralColorHint
    ) {
        final byte xDivsCount = 2;
        final byte yDivsCount = 2;
        final byte colorsCount = 9; // (2+1)*(2+1)

        final int size = 4 + 8 + 16 + 4
                + (xDivsCount + yDivsCount + colorsCount) * 4;

        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.nativeOrder());

        buffer.put((byte) 1);     // wasSerialized != 0
        buffer.put(xDivsCount);   // numXDivs
        buffer.put(yDivsCount);   // numYDivs
        buffer.put(colorsCount);  // numColors

        // skip 8 bytes
        buffer.putInt(0);
        buffer.putInt(0);

        // padding (ORDER IS IMPORTANT!)
        buffer.putInt(padLeft);
        buffer.putInt(padRight);
        buffer.putInt(padTop);
        buffer.putInt(padBottom);

        // skip 4 bytes
        buffer.putInt(0);

        // xDivs, yDivs
        buffer.putInt(x1);
        buffer.putInt(x2);
        buffer.putInt(y1);
        buffer.putInt(y2);

        // color hint
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(centralColorHint);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);
        buffer.putInt(0x00000001);

        return buffer;
    }
}
