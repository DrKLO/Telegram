package org.telegram.ui.Components.Premium.GLIcon;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Trace;
import android.view.View;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;

import java.nio.ByteBuffer;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** One small offscreen renderer shared by visible transfer cards, composited by their canvas. */
public final class CoolDiamondIcon {
    private static CoolDiamondIcon instance;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Handler worker;
    private final WeakHashMap<View, State> viewers = new WeakHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final float IDLE_SPEED = (float) (2 * Math.PI / 18 * 1.70775);
    private Bitmap frame;
    private float idleYaw;
    private float idlePitch;
    private volatile float tiltYaw, tiltPitch;
    private volatile Request[] requests = new Request[0];
    private static final class State {
        long seen;
        boolean custom, enlarged;
        long spinUpdated;
        float spinOffset, spinSpeed;
        int generation;
        float yaw, pitch;
        Bitmap frame;
    }
    private static final class Request {
        final State state;
        final float yaw, pitch;
        final int size;
        final int generation;
        final boolean enlarged;
        Request(State state, int size) {
            this.state = state; this.yaw = state.yaw; this.pitch = state.pitch; this.size = size;
            this.generation = state.generation;
            this.enlarged = state.enlarged;
        }
    }
    public float getIdleYaw() { return idleYaw; }
    public float getRestingYaw(View parent, boolean sending) {
        State state = getState(parent);
        updateSpin(state, sending);
        return idleYaw + state.spinOffset;
    }

    private State getState(View parent) {
        State state = viewers.get(parent);
        if (state == null) { state = new State(); viewers.put(parent, state); }
        return state;
    }

    private void updateSpin(State state, boolean sending) {
        long now = SystemClock.uptimeMillis();
        if (state.spinUpdated != 0) {
            float dt = (now - state.spinUpdated) / 1000f;
            float target = sending ? IDLE_SPEED * 3 : 0;
            float decay = (float) Math.exp(-dt / .2f);
            // Integrate the easing speed, preserving the angle on both state transitions.
            state.spinOffset += target * dt + (state.spinSpeed - target) * .2f * (1 - decay);
            state.spinOffset %= (float) (Math.PI * 2);
            state.spinSpeed = target + (state.spinSpeed - target) * decay;
        }
        state.spinUpdated = now;
    }
    public float getIdlePitch() { return idlePitch; }
    public void setTilt(float yaw, float pitch) { tiltYaw = yaw; tiltPitch = pitch; }
    public boolean isReady() { return !failed && frame != null; }

    private volatile boolean pending;
    private boolean failed;
    private volatile int renderGeneration;
    private final AtomicReference<RenderedFrame> completed = new AtomicReference<>();
    private final Runnable publishTask = () -> {
        RenderedFrame result = completed.getAndSet(null);
        if (result == null || !pending || result.generation != renderGeneration) return;
        idleYaw = result.yaw;
        idlePitch = result.pitch;
        for (int i = 0; i < result.batch.length; i++) {
            Request request = result.batch[i];
            if (request.state.custom && request.state.generation == request.generation) {
                request.state.frame = result.images[i];
            }
        }
        publish(result.idle);
    };

    private static final class RenderedFrame {
        final int generation;
        final float yaw, pitch;
        final Bitmap idle;
        final Request[] batch;
        final Bitmap[] images;
        RenderedFrame(int generation, float yaw, float pitch, Bitmap idle, Request[] batch, Bitmap[] images) {
            this.generation = generation;
            this.yaw = yaw;
            this.pitch = pitch;
            this.idle = idle;
            this.batch = batch;
            this.images = images;
        }
    }
    private final int size;
    // Bound synchronous readback/upload work independently of screen density.
    // The canvas still supplies the full spring scale, including its overshoot.
    private final int interactionSize;
    private long lastIdleRender;
    private float idleDt;
    private Bitmap renderedIdle;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface = EGL14.EGL_NO_SURFACE;
    private DiamondRenderer renderer, customRenderer, interactionRenderer;
    private ByteBuffer pixels;
    private float elapsed;
    private long previous;

    public static CoolDiamondIcon get(Context context) {
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) return null;
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null || manager.getDeviceConfigurationInfo().reqGlEsVersion < 0x30000) return null;
        if (instance == null) instance = new CoolDiamondIcon(context.getApplicationContext());
        return instance;
    }

    private CoolDiamondIcon(Context context) {
        this.context = context;
        size = Math.max(1, Math.min(192, Math.round(42 * context.getResources().getDisplayMetrics().density)));
        interactionSize = Math.min(SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH
                ? 256 : 192, Math.round(size * 3.5f));
        HandlerThread thread = new HandlerThread("CoolDiamondIcon");
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    /** Called on the UI thread only, after the caller's clip rejection check. */
    public boolean draw(Canvas canvas, Rect bounds, View parent) {
        return draw(canvas, bounds, parent, false);
    }

    public boolean draw(Canvas canvas, Rect bounds, View parent, boolean sending) {
        return draw(canvas, bounds, parent, false, 0, 0, sending);
    }

    public boolean draw(Canvas canvas, Rect bounds, View parent, boolean custom, float yaw, float pitch) {
        return draw(canvas, bounds, parent, custom, yaw, pitch, false);
    }

    private boolean draw(Canvas canvas, Rect bounds, View parent, boolean custom, float yaw, float pitch, boolean sending) {
        if (failed) return false;
        State state = getState(parent);
        boolean enlarged = custom;
        if (!custom) {
            updateSpin(state, sending);
            yaw = idleYaw + state.spinOffset;
            pitch = idlePitch;
            custom = sending || state.spinOffset != 0 || state.spinSpeed != 0;
        }
        if (state.custom != custom || state.enlarged != enlarged) {
            state.generation++;
            // Keep the last pose while a differently sized frame is being rendered.
            if (state.custom != custom) state.frame = null;
        }
        state.custom = custom;
        state.enlarged = enlarged;
        state.yaw = yaw;
        state.pitch = pitch;
        if (!custom) state.frame = null;
        if (parent.isAttachedToWindow() && parent.isShown() && parent.getWindowVisibility() == View.VISIBLE) {
            state.seen = SystemClock.uptimeMillis();
            updateRequests();
            if (!pending) {
                final int generation = ++renderGeneration;
                pending = true;
                worker.post(() -> render(generation));
            }
        }
        Bitmap image = custom && state.frame != null ? state.frame : frame;
        if (image == null) return false;
        canvas.save();
        // GL readback starts at the bottom row; retain its premultiplied RGBA bytes.
        canvas.translate(bounds.left, bounds.bottom);
        canvas.scale(bounds.width() / (float) image.getWidth(), -bounds.height() / (float) image.getHeight());
        canvas.drawBitmap(image, 0, 0, paint);
        canvas.restore();
        return true;
    }

    public void detach(View parent) {
        viewers.remove(parent);
        updateRequests();
    }

    private void updateRequests() {
        ArrayList<Request> next = new ArrayList<>();
        for (State state : viewers.values()) {
            if (state.custom) next.add(new Request(state, state.enlarged ? interactionSize : size));
        }
        requests = next.toArray(new Request[0]);
    }

    private Bitmap renderImage(DiamondRenderer renderer, int side, float yaw, float pitch, float dt) {
        renderer.drawToTexture(side, yaw, pitch, dt);
        pixels.clear();
        GLES30.glReadPixels(0, 0, side, side, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels);
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) throw new IllegalStateException("Cool diamond GL error: " + error);
        Bitmap next = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        pixels.rewind();
        next.copyPixelsFromBuffer(pixels);
        return next;
    }

    private void render(int generation) {
        if (!pending || generation != renderGeneration) return;
        final long renderStarted = SystemClock.uptimeMillis();
        Trace.beginSection("CoolDiamond.render");
        try {
            if (renderer == null) initialize();
            long now = SystemClock.uptimeMillis();
            float dt = previous == 0 ? 0 : Math.min(.1f, (now - previous) / 1000f);
            previous = now;
            elapsed += dt;
            float yaw = elapsed * IDLE_SPEED + tiltYaw;
            float pitch = tiltPitch;
            // Other cards retain their idle cadence while the dragged card
            // updates at display speed; don't render the shared pose twice as often.
            idleDt += dt;
            if (lastIdleRender == 0 || now - lastIdleRender >= 33) {
                renderedIdle = renderImage(renderer, size, yaw, pitch, idleDt);
                idleDt = 0;
                lastIdleRender = now;
            }
            Request[] batch = requests;
            Bitmap[] images = new Bitmap[batch.length];
            boolean renderedInteraction = false;
            boolean advancedCustom = false, advancedInteraction = false;
            for (int i = 0; i < batch.length; i++) {
                Request request = batch[i];
                // Keep each renderer's targets at a fixed size, including when a
                // sending card and a dragged card are visible together.
                DiamondRenderer target;
                float step;
                if (request.enlarged) {
                    target = interactionRenderer;
                    step = advancedInteraction ? 0 : dt;
                    advancedInteraction = renderedInteraction = true;
                } else {
                    if (customRenderer == null) {
                        customRenderer = new DiamondRenderer(context, 2);
                        customRenderer.appearance = GLIconRenderer.DIAMOND_COOL;
                    }
                    target = customRenderer;
                    step = advancedCustom ? 0 : dt;
                    advancedCustom = true;
                }
                images[i] = renderImage(target, request.size, request.yaw, request.pitch, step);
            }
            if (pending && generation == renderGeneration) {
                // Keep at most one unpublished batch. UI work must not add a
                // full vsync delay to the GL loop or queue stale drag poses.
                completed.set(new RenderedFrame(generation, yaw, pitch, renderedIdle, batch, images));
                main.removeCallbacks(publishTask);
                main.post(publishTask);
                long interval = renderedInteraction ? 16 : 33;
                worker.postDelayed(() -> render(generation), Math.max(0, renderStarted + interval - SystemClock.uptimeMillis()));
            }
        } catch (RuntimeException error) {
            FileLog.e(error);
            release();
            main.post(() -> {
                failed = true;
                pending = false;
                for (View parent : viewers.keySet()) parent.invalidate();
                viewers.clear();
                frame = null;
            });
        } finally {
            Trace.endSection();
        }
    }

    private void publish(Bitmap next) {
        // Do not recycle a bitmap which RenderThread may still be using.
        if (next != null) frame = next;
        long now = SystemClock.uptimeMillis();
        viewers.entrySet().removeIf(entry -> !entry.getKey().isAttachedToWindow()
                || !entry.getKey().isShown() || entry.getKey().getWindowVisibility() != View.VISIBLE
                || now - entry.getValue().seen > 250);
        updateRequests();
        if (viewers.isEmpty()) {
            pending = false;
            final int generation = ++renderGeneration;
            worker.post(() -> {
                if (!pending && generation == renderGeneration) release();
            });
            completed.set(null);
        } else {
            for (View parent : viewers.keySet()) parent.invalidate();
        }
    }

    private void initialize() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw new IllegalStateException("EGL initialize failed");
        int[] attributes = {EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE};
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            throw new IllegalStateException("No GLES3 pbuffer config");
        }
        eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
        int bufferSize = Math.max(size, interactionSize);
        surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE
                || !EGL14.eglMakeCurrent(display, surface, surface, eglContext)) {
            throw new IllegalStateException("Cool diamond EGL surface failed");
        }
        renderer = new DiamondRenderer(context, 2);
        renderer.appearance = GLIconRenderer.DIAMOND_COOL;
        pixels = ByteBuffer.allocateDirect(bufferSize * bufferSize * 4);
        // Compile shaders and allocate the enlarged targets before publishing
        // readiness, not on the first touch.
        interactionRenderer = new DiamondRenderer(context, 2);
        interactionRenderer.appearance = GLIconRenderer.DIAMOND_COOL;
        interactionRenderer.drawToTexture(interactionSize, 0, 0, 0);
        GLES30.glFinish();
        previous = 0;
        lastIdleRender = 0;
        idleDt = 0;
    }

    private void release() {
        if (renderer != null) renderer.destroy();
        renderer = null;
        if (interactionRenderer != null) interactionRenderer.destroy();
        interactionRenderer = null;
        if (customRenderer != null) customRenderer.destroy();
        customRenderer = null;
        pixels = null;
        renderedIdle = null;
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext);
            EGL14.eglTerminate(display);
            EGL14.eglReleaseThread();
        }
        display = EGL14.EGL_NO_DISPLAY;
        surface = EGL14.EGL_NO_SURFACE;
        eglContext = EGL14.EGL_NO_CONTEXT;
    }
}
