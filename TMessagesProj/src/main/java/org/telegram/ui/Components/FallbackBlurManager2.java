package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.View;

import androidx.annotation.NonNull;
import org.telegram.messenger.Utilities;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Records a view’s frame each VSYNC, blurs it off-thread, lets callers paint it. */
public final class FallbackBlurManager2 {

    public FallbackBlurManager2(@NonNull View root, float sigma, float downScale) {
        this.root = root;
        this.sigma = sigma;
        this.scale = Math.max(0.1f, Math.min(downScale, 1f));
    }

    public void start() {
        running.set(false);
    }

    public void stop()  {
        running.set(false);
        worker.shutdownNow();
        recycle();
    }

    public void update() {
        if (Boolean.TRUE.equals(CAPTURING.get())) return;
        ensureBuffers();
        if (capture == null || running.get()) return;

        CAPTURING.set(Boolean.TRUE);
        root.draw(captureCanvas);
        CAPTURING.set(Boolean.FALSE);

        final int back = 1 - front;
        running.set(true);
        worker.submit(() -> {
            long before = System.currentTimeMillis();
            downScale(capture, small[back]);
            Utilities.blurBitmapIIR(small[back], sigma);
            android.util.Log.d("wwttff", " blur time " + (System.currentTimeMillis() - before));
            front = back;
            running.set(false);
            root.postInvalidateOnAnimation();
        });
    }

    public void draw(@NonNull View requester, @NonNull Canvas canvas) {
        if (small[front] == null) return;

        //requester.getLocationInWindow(tmpXY);
        //int rqX = tmpXY[0], rqY = tmpXY[1];
        //root.getLocationInWindow(tmpXY);
        //int rtX = tmpXY[0], rtY = tmpXY[1];

        //int x = rqX - rtX;
        //int y = rqY - rtY;

        canvas.save();
        //canvas.clipRect(0, 0, requester.getWidth(), requester.getHeight());
        //canvas.translate(-x, -y);
        canvas.drawBitmap(small[front], drawMatrix, null);
        canvas.restore();
    }

    private final View root;
    private final float sigma;
    private final float scale;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Bitmap capture;
    private Bitmap[] small = new Bitmap[2];
    private int front = 0;

    private final Matrix drawMatrix = new Matrix();
    private final int[]  tmpXY = new int[2];
    private Canvas captureCanvas;

    private void ensureBuffers() {
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0) return;

        if (capture != null && capture.getWidth() == w && capture.getHeight() == h) return;

        recycle();
        capture = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        captureCanvas = new Canvas(capture);

        int sw = Math.max(1, Math.round(w * scale));
        int sh = Math.max(1, Math.round(h * scale));
        small[0] = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        small[1] = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);

        drawMatrix.reset();
        drawMatrix.setScale(1f / scale, 1f / scale);
    }

    private void recycle() {
        if (capture != null) { capture.recycle(); capture = null; }
        for (int i = 0; i < small.length; ++i) {
            if (small[i] != null) { small[i].recycle(); small[i] = null; }
        }
        captureCanvas = null;
    }

    private static void downScale(Bitmap src, Bitmap dst) {
        Canvas c = new Canvas(dst);
        c.scale((float) dst.getWidth() / src.getWidth(), (float) dst.getHeight() / src.getHeight());
        c.drawBitmap(src, 0, 0, null);
    }

    static final ThreadLocal<Boolean> CAPTURING = ThreadLocal.withInitial(() -> Boolean.FALSE);
}
