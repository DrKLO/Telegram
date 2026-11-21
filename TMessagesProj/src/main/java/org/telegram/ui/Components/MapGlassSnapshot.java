package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

/**
 * A copy of a map's GL surface for the glass over the map: the glass captures views, not surfaces, so the surface is
 * copied (at a quarter of its size, the glass is blurred anyway) and the copy is drawn into the glass capture.
 * It is copied on every frame for a while after the map is touched or moves, and now and then while it is still
 * (the tiles load later).
 */
public class MapGlassSnapshot {
    private static final int SCALE = 4;
    private static final long ACTIVE_MS = 1500;
    private static final long IDLE_INTERVAL_MS = 400;

    private final Runnable onUpdated;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    @Nullable
    private SurfaceView surface;
    @Nullable
    private Bitmap shown, spare;
    private boolean running, copying, released;
    private boolean scheduledFrame, scheduledIdle;
    private long activeUntil;

    private final Choreographer.FrameCallback frameCallback = frameTimeNanos -> {
        scheduledFrame = false;
        tick();
    };

    private final Runnable idleCallback = () -> {
        scheduledIdle = false;
        tick();
    };

    /** @param onUpdated called on a new copy: the glass should capture the map again */
    public MapGlassSnapshot(Runnable onUpdated) {
        this.onUpdated = onUpdated;
    }

    /** The surface of the map, once the map has made it. */
    public void setSurface(@Nullable SurfaceView surface) {
        if (this.surface == surface) {
            return;
        }
        this.surface = surface;
        poke();
    }

    /** Copies only while the map is on screen under the glass: the screen is resumed and the map is visible. */
    public void setRunning(boolean running) {
        if (this.running == running) {
            return;
        }
        this.running = running;
        if (running) {
            poke();
        } else {
            unschedule();
        }
    }

    /** The map has changed or is about to (a touch, a camera move): copies on every frame for a while. */
    public void poke() {
        activeUntil = SystemClock.uptimeMillis() + ACTIVE_MS;
        if (scheduledIdle) {
            handler.removeCallbacks(idleCallback);
            scheduledIdle = false;
        }
        schedule();
    }

    public boolean hasSurface() {
        return surface != null && surface.isAttachedToWindow();
    }

    /** Draws the last copy over {@code rect}, where the surface is, with the alpha of the map. */
    public void draw(Canvas canvas, RectF rect, float alpha) {
        final Bitmap bitmap = shown;
        if (bitmap == null || bitmap.isRecycled() || alpha <= 0) {
            return;
        }
        paint.setAlpha((int) (255 * Math.min(1f, alpha)));
        canvas.drawBitmap(bitmap, null, rect, paint);
    }

    public void release() {
        released = true;
        running = false;
        unschedule();
        surface = null;
        // the bitmaps may still be in a recorded capture: left to the GC
        shown = null;
        spare = null;
    }

    private void unschedule() {
        if (scheduledFrame) {
            Choreographer.getInstance().removeFrameCallback(frameCallback);
            scheduledFrame = false;
        }
        if (scheduledIdle) {
            handler.removeCallbacks(idleCallback);
            scheduledIdle = false;
        }
    }

    private void schedule() {
        if (!running || released || surface == null || scheduledFrame || scheduledIdle) {
            return;
        }
        if (SystemClock.uptimeMillis() < activeUntil) {
            scheduledFrame = true;
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } else {
            scheduledIdle = true;
            handler.postDelayed(idleCallback, IDLE_INTERVAL_MS);
        }
    }

    private void tick() {
        if (!running || released) {
            return;
        }
        if (!copying) {
            copy();
        }
        schedule();
    }

    private void copy() {
        final SurfaceView view = surface;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || view == null || !view.isAttachedToWindow() || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }
        final Surface s = view.getHolder().getSurface();
        if (s == null || !s.isValid()) {
            return;
        }
        final int w = Math.max(1, view.getWidth() / SCALE), h = Math.max(1, view.getHeight() / SCALE);
        if (spare == null || spare.getWidth() != w || spare.getHeight() != h) {
            spare = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        }
        final Bitmap target = spare;
        copying = true;
        try {
            PixelCopy.request(view, target, result -> {
                copying = false;
                if (released || result != PixelCopy.SUCCESS) {
                    return;
                }
                // the shown copy is in the capture recorded last: it is copied into only after the next capture
                spare = shown;
                shown = target;
                onUpdated.run();
            }, handler);
        } catch (Exception e) {
            copying = false;
            FileLog.e(e);
        }
    }
}
