package org.telegram.ui.Components.spoilers;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Process;
import android.view.Choreographer;

import androidx.annotation.Size;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Arrays;

public class SpoilerEffectBitmapFactory {

    private static SpoilerEffectBitmapFactory factory;

    public static SpoilerEffectBitmapFactory getInstance() {
        if (factory == null) {
            factory = new SpoilerEffectBitmapFactory();
        }
        return factory;
    }

    private final DispatchQueue dispatchQueue = new DispatchQueue("SpoilerEffectBitmapFactory", true, 3 * Process.THREAD_PRIORITY_LESS_FAVORABLE);
    private final PointsBuffer[] buffers = new PointsBuffer[SpoilerEffect.ALPHAS.length];
    private final Buffer[] bitmapBuffers = new Buffer[2];
    private int currentBitmapBuffer = 0;

    private Bitmap backgroundBitmap;
    private Canvas backgroundCanvas;
    private Paint shaderPaint;
    private long lastUpdateTime;
    private ArrayList<SpoilerEffect> shaderSpoilerEffects;
    private boolean isRunning;
    final int size;

    private SpoilerEffectBitmapFactory() {
        int maxSize = SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? AndroidUtilities.dp(150) : AndroidUtilities.dp(100);
        int size = (int) Math.min(Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f, maxSize);
        if (size < AndroidUtilities.dp(80)) {
            size = AndroidUtilities.dp(80);
        }
        this.size = size;
        for (int a = 0; a < buffers.length; a++) {
            buffers[a] = new PointsBuffer();
        }
    }

    Paint getPaint() {
        if (bitmapBuffers[0] == null) {
            bitmapBuffers[0] = new Buffer(size);
            shaderPaint = new Paint();
            shaderSpoilerEffects = new ArrayList<>(10 * 10);
            int step = (int) (size / 10f);
            int particleCount = (int) (60 * (size / (float) AndroidUtilities.dp(200)));
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 10; j++) {
                    SpoilerEffect shaderSpoilerEffect = new SpoilerEffect();
                    shaderSpoilerEffect.setSize(size);
                    shaderSpoilerEffect.setBounds(step * i, step * j - AndroidUtilities.dp(5), step * i + step +  AndroidUtilities.dp(3), step * j + step + AndroidUtilities.dp(5));
                    shaderSpoilerEffect.setMaxParticlesCount(Math.min(SpoilerEffect.MAX_PARTICLES_PER_ENTITY * 5, particleCount));
                    shaderSpoilerEffect.setColor(Color.WHITE);
                    shaderSpoilerEffects.add(shaderSpoilerEffect);
                }
            }
            doDraw(new Canvas(bitmapBuffers[0].bitmap), new Rect(0, 0, size, size));
            shaderPaint.setShader(bitmapBuffers[0].shader);
            lastUpdateTime = System.currentTimeMillis();
        } else if (isDrawnWithClipRegion && !LiteMode.isEnabled(LiteMode.FLAG_CHAT_SPOILER)) {
            // restore full-drawn texture

            currentBitmapBuffer = 0;
            doDraw(new Canvas(bitmapBuffers[0].bitmap), new Rect(0, 0, size, size));
            shaderPaint.setShader(bitmapBuffers[0].shader);
            lastUpdateTime = System.currentTimeMillis();
            isDrawnWithClipRegion = false;
        }


        return shaderPaint;
    }

    private void doDraw(Canvas canvas, Rect clipRegion) {
        for (PointsBuffer buffer : buffers) {
            buffer.reset();
        }
        for (int k = 0; k < 100; k++) {
            SpoilerEffect spoilerEffect = shaderSpoilerEffects.get(k);
            if (Rect.intersects(spoilerEffect.getBounds(), clipRegion)) {
                spoilerEffect.addPoints(buffers, clipRegion);
            }
        }
        shaderSpoilerEffects.get(0).drawPoints(canvas, buffers);
    }

    private boolean invalidated;
    private final Rect clipRegion = new Rect();

    public void checkUpdate(Rect region) {
        applyClip(region);
        if (!invalidated && !clipRegion.isEmpty()) {
            invalidated = true;
            Choreographer.getInstance().postFrameCallback(postFrameCallback);
        }
    }

    private void applyClip(Rect clipRect) {
        int left   = ((clipRect.left % size) + size) % size;
        int top    = ((clipRect.top  % size) + size) % size;
        int width  = Math.min(clipRect.width(),  size);
        int height = Math.min(clipRect.height(), size);

        int right  = left + width;
        int bottom = top  + height;

        clipRegion.union(left, top, Math.min(right, size), Math.min(bottom, size));

        if (right > size)  clipRegion.union(0, top,  right  - size, Math.min(bottom, size));
        if (bottom > size) clipRegion.union(left, 0, Math.min(right, size), bottom - size);
        if (right > size && bottom > size) clipRegion.union(0, 0, right - size, bottom - size);
    }

    private final Choreographer.FrameCallback postFrameCallback = frameTimeNanos -> {
        checkUpdateImpl();
        clipRegion.set(0, 0, 0, 0);
        invalidated = false;
    };

    private final Rect clipRegionDump = new Rect();
    private boolean isDrawnWithClipRegion;

    private void checkUpdateImpl() {
        long time = System.currentTimeMillis();
        if (time - lastUpdateTime > 32 && !isRunning && !clipRegion.isEmpty()) {
            lastUpdateTime = time;
            isRunning = true;
            clipRegionDump.set(clipRegion);

            final int nextBitmapBuffer = (currentBitmapBuffer + 1) % 2;
            dispatchQueue.postRunnable(() -> {
                if (bitmapBuffers[nextBitmapBuffer] == null) {
                    bitmapBuffers[nextBitmapBuffer] = new Buffer(size);
                }
                if (backgroundBitmap == null) {
                    backgroundBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
                    backgroundCanvas = new Canvas(backgroundBitmap);
                } else {
                    backgroundBitmap.eraseColor(Color.TRANSPARENT);
                }
                doDraw(backgroundCanvas, clipRegionDump);
                Utilities.copyBitmaps(backgroundBitmap, bitmapBuffers[nextBitmapBuffer].bitmap);
                AndroidUtilities.runOnUIThread(() -> {
                    currentBitmapBuffer = nextBitmapBuffer;
                    shaderPaint.setShader(bitmapBuffers[currentBitmapBuffer].shader);
                    isRunning = false;
                    isDrawnWithClipRegion = true;
                });
            });
        }
    }

    private static class Buffer {
        private final Bitmap bitmap;
        private final BitmapShader shader;
        public Buffer(int size) {
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            shader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        }
    }

    public static class PointsBuffer {
        private float[] buffer;
        private int length;

        public PointsBuffer(int initialCapacity) {
            buffer = new float[Math.max(initialCapacity, 2)];
            length = 0;
        }

        public PointsBuffer() {
            this(64);
        }

        public void reset() {
            length = 0;
        }

        public void addPoints(@Size(multiple = 2) float[] pts, int offset, int count) {
            ensureCapacity(length + count);
            System.arraycopy(pts, offset, buffer, length, count);
            length += count;
        }

        public void draw(Canvas canvas, Paint paint) {
            if (length > 0) {
                canvas.drawPoints(buffer, 0, length, paint);
            }
        }

        private void ensureCapacity(int required) {
            if (required <= buffer.length) return;
            int newSize = Math.max(required, buffer.length * 2);
            buffer = Arrays.copyOf(buffer, newSize);
        }
    }
}
