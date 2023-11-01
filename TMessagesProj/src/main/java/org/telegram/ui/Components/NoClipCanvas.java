package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.DrawFilter;
import android.graphics.Matrix;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.RenderNode;
import android.graphics.fonts.Font;
import android.graphics.text.MeasuredText;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class NoClipCanvas extends Canvas {

    public Canvas canvas;

    @Override
    public boolean clipRect(float left, float top, float right, float bottom) {
        // nop
        return false;
    }

    @Override
    public int save() {
        return canvas.save();
    }

    @Override
    public void translate(float dx, float dy) {
        canvas.translate(dx, dy);
    }

    @Override
    public void scale(float sx, float sy) {
        canvas.scale(sx, sy);
    }

    @Override
    public void rotate(float degrees) {
        canvas.rotate(degrees);
    }

    @Override
    public void restore() {
        canvas.restore();
    }

    @Override
    public void restoreToCount(int saveCount) {
        canvas.restoreToCount(saveCount);
    }

    @Override
    public int getSaveCount() {
        return canvas.getSaveCount();
    }

    @Override
    public void drawPath(@NonNull Path path, @NonNull Paint paint) {
        canvas.drawPath(path, paint);
    }

    @Override
    public void drawText(@NonNull String text, float x, float y, @NonNull Paint paint) {
        canvas.drawText(text, x, y, paint);
    }

    @Override
    public void drawText(@NonNull String text, int start, int end, float x, float y, @NonNull Paint paint) {
        canvas.drawText(text, start, end, x, y, paint);
    }

    @Override
    public void drawText(@NonNull char[] text, int index, int count, float x, float y, @NonNull Paint paint) {
        canvas.drawText(text, index, count, x, y, paint);
    }

    @Override
    public void drawTextOnPath(@NonNull char[] text, int index, int count, @NonNull Path path, float hOffset, float vOffset, @NonNull Paint paint) {
        canvas.drawTextOnPath(text, index, count, path, hOffset, vOffset, paint);
    }

    @Override
    public void drawText(@NonNull CharSequence text, int start, int end, float x, float y, @NonNull Paint paint) {
        canvas.drawText(text, start, end, x, y, paint);
    }

    @Override
    public void drawRect(@NonNull Rect r, @NonNull Paint paint) {
        canvas.drawRect(r, paint);
    }

    @Override
    public void drawRect(@NonNull RectF rect, @NonNull Paint paint) {
        canvas.drawRect(rect, paint);
    }

    @Override
    public void drawCircle(float cx, float cy, float radius, @NonNull Paint paint) {
        canvas.drawCircle(cx, cy, radius, paint);
    }

    @Override
    public void drawRect(float left, float top, float right, float bottom, @NonNull Paint paint) {
        canvas.drawRect(left, top, right, bottom, paint);
    }

    @Override
    public void drawRoundRect(@NonNull RectF rect, float rx, float ry, @NonNull Paint paint) {
        canvas.drawRoundRect(rect, rx, ry, paint);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void drawRoundRect(float left, float top, float right, float bottom, float rx, float ry, @NonNull Paint paint) {
        canvas.drawRoundRect(left, top, right, bottom, rx, ry, paint);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void drawTextRun(@NonNull char[] text, int index, int count, int contextIndex, int contextCount, float x, float y, boolean isRtl, @NonNull Paint paint) {
        canvas.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void drawTextRun(@NonNull MeasuredText text, int start, int end, int contextStart, int contextEnd, float x, float y, boolean isRtl, @NonNull Paint paint) {
        canvas.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void drawTextRun(@NonNull CharSequence text, int start, int end, int contextStart, int contextEnd, float x, float y, boolean isRtl, @NonNull Paint paint) {
        canvas.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint);
    }

    @Override
    public void drawTextOnPath(@NonNull String text, @NonNull Path path, float hOffset, float vOffset, @NonNull Paint paint) {
        canvas.drawTextOnPath(text, path, hOffset, vOffset, paint);
    }

    @Override
    public void drawBitmap(@NonNull Bitmap bitmap, @NonNull Matrix matrix, @Nullable Paint paint) {
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    @Override
    public void drawBitmap(@NonNull Bitmap bitmap, @Nullable Rect src, @NonNull Rect dst, @Nullable Paint paint) {
        canvas.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public void drawBitmap(@NonNull int[] colors, int offset, int stride, float x, float y, int width, int height, boolean hasAlpha, @Nullable Paint paint) {
        canvas.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint);
    }

    @Override
    public void drawBitmap(@NonNull int[] colors, int offset, int stride, int x, int y, int width, int height, boolean hasAlpha, @Nullable Paint paint) {
        canvas.drawBitmap(colors, offset, stride, x, y, width, height, hasAlpha, paint);
    }

    @Override
    public void drawArc(@NonNull RectF oval, float startAngle, float sweepAngle, boolean useCenter, @NonNull Paint paint) {
        canvas.drawArc(oval, startAngle, sweepAngle, useCenter, paint);
    }

    @Override
    public void drawLine(float startX, float startY, float stopX, float stopY, @NonNull Paint paint) {
        canvas.drawLine(startX, startY, stopX, stopY, paint);
    }

    @Override
    public void drawVertices(@NonNull VertexMode mode, int vertexCount, @NonNull float[] verts, int vertOffset, @Nullable float[] texs, int texOffset, @Nullable int[] colors, int colorOffset, @Nullable short[] indices, int indexOffset, int indexCount, @NonNull Paint paint) {
        canvas.drawVertices(mode, vertexCount, verts, vertOffset, texs, texOffset, colors, colorOffset, indices, indexOffset, indexCount, paint);
    }

    @Override
    public void drawBitmap(@NonNull Bitmap bitmap, @Nullable Rect src, @NonNull RectF dst, @Nullable Paint paint) {
        canvas.drawBitmap(bitmap, src, dst, paint);
    }

    @Override
    public void drawBitmap(@NonNull Bitmap bitmap, float left, float top, @Nullable Paint paint) {
        canvas.drawBitmap(bitmap, left, top, paint);
    }

    @Override
    public void drawBitmapMesh(@NonNull Bitmap bitmap, int meshWidth, int meshHeight, @NonNull float[] verts, int vertOffset, @Nullable int[] colors, int colorOffset, @Nullable Paint paint) {
        canvas.drawBitmapMesh(bitmap, meshWidth, meshHeight, verts, vertOffset, colors, colorOffset, paint);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public void drawGlyphs(@NonNull int[] glyphIds, int glyphIdOffset, @NonNull float[] positions, int positionOffset, int glyphCount, @NonNull Font font, @NonNull Paint paint) {
        canvas.drawGlyphs(glyphIds, glyphIdOffset, positions, positionOffset, glyphCount, font, paint);
    }

    @Override
    public void setBitmap(@Nullable Bitmap bitmap) {
        canvas.setBitmap(bitmap);
    }

    @Override
    public int getMaximumBitmapWidth() {
        return canvas.getMaximumBitmapWidth();
    }

    @Override
    public void setMatrix(@Nullable Matrix matrix) {
        canvas.setMatrix(matrix);
    }

    @Override
    public int getMaximumBitmapHeight() {
        return canvas.getMaximumBitmapHeight();
    }

    @Override
    public boolean getClipBounds(@NonNull Rect bounds) {
        return canvas.getClipBounds(bounds);
    }

    @Override
    public void getMatrix(@NonNull Matrix ctm) {
        canvas.getMatrix(ctm);
    }

    @Override
    public int getWidth() {
        return canvas.getWidth();
    }

    @Override
    public int getHeight() {
        return canvas.getHeight();
    }

    @Override
    public boolean quickReject(float left, float top, float right, float bottom, @NonNull EdgeType type) {
        return canvas.quickReject(left, top, right, bottom, type);
    }
}
