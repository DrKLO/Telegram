package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;

public class BatchParticlesDrawHelper {

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    public static boolean isAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static void draw(Canvas canvas, BatchParticlesBuffer buffer, int vertexCount, Paint paint) {
        draw(canvas, buffer, vertexCount, 0, paint);
    }

    public static void draw(Canvas canvas, BatchParticlesBuffer buffer, int vertexCount, int vertexOffset, Paint paint) {
        canvas.drawVertices(Canvas.VertexMode.TRIANGLES,
            vertexCount * 8,
            buffer.batchCordVertex, vertexOffset * 8,
            buffer.batchCordTexture, vertexOffset * 8,
            buffer.batchColors, vertexOffset * 4,
            buffer.batchIdx, vertexOffset * 6, vertexCount * 6,
            paint);
    }

    public static Paint createBatchParticlesPaint(Bitmap bitmap) {
        final BitmapShader bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bitmapShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        }

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(bitmapShader);
        paint.setFilterBitmap(true);

        return paint;
    }



    public static class BatchParticlesBuffer {
        public final int vertexCount;

        protected final float[] batchCordVertex;
        protected final float[] batchCordTexture;
        protected final short[] batchIdx;
        protected final int [] batchColors;

        public BatchParticlesBuffer(int N) {
            vertexCount = N;

            batchCordVertex = new float[N * 8];
            batchCordTexture = new float[N * 8];
            batchIdx = new short[N * 6];
            batchColors = new int[N * 4];

            for (short a = 0; a < N; a++) {
                int idxPos = a * 6;
                int value = a * 4;

                batchIdx[idxPos] =     (short) value;
                batchIdx[idxPos + 1] = (short) (value + 1);
                batchIdx[idxPos + 2] = (short) (value + 2);
                batchIdx[idxPos + 3] = (short) (value + 2);
                batchIdx[idxPos + 4] = (short) (value + 3);
                batchIdx[idxPos + 5] = (short) value;
            }
        }

        public void setParticleColor(int index, int color) {
            batchColors[index * 4] = color;
            batchColors[index * 4 + 1] = color;
            batchColors[index * 4 + 2] = color;
            batchColors[index * 4 + 3] = color;
        }
        
        public void setParticleVertexCords(int index, float left, float top, float right, float bottom) {
            bufferVertexSet(batchCordVertex, index, left, top, right, bottom);
        }

        public void setParticleTextureCords(int index, float left, float top, float right, float bottom) {
            bufferVertexSet(batchCordTexture, index, left, top, right, bottom);
        }

        public void fillParticleTextureCords(float left, float top, float right, float bottom) {
            for (int a = 0; a < vertexCount; a++) {
                setParticleTextureCords(a, left, top, right, bottom);
            }
        }

        private static void bufferVertexSet(float[] array, int index, float left, float top, float right, float bottom) {
            array[index * 8] = left;
            array[index * 8 + 1] = top;
            array[index * 8 + 2] = right;
            array[index * 8 + 3] = top;
            array[index * 8 + 4] = right;
            array[index * 8 + 5] = bottom;
            array[index * 8 + 6] = left;
            array[index * 8 + 7] = bottom;
        }
    }
}
