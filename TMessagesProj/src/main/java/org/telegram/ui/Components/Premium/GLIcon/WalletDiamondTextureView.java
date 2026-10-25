package org.telegram.ui.Components.Premium.GLIcon;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.Rect;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.Trace;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Each card has GPU-backed buffers, sharing a GL context and idle texture without CPU readback. */
public class WalletDiamondTextureView extends TextureView implements TextureView.SurfaceTextureListener, Choreographer.FrameCallback {
    /** One per appearance: each renders a single shared idle texture. */
    private static final Engine[] engines = new Engine[3];
    private final Engine engine;
    private final Rect visibleRect = new Rect();
    private final Runnable updatePose;
    private final int bufferSize;
    private Target target;
    private boolean attached, paused = true, ready;
    private float yaw, pitch;
    private boolean custom, enlarged;

    public WalletDiamondTextureView(Context context, Runnable updatePose) {
        this(context, updatePose, GLIconRenderer.DIAMOND_COOL);
    }

    public WalletDiamondTextureView(Context context, Runnable updatePose, int appearance) {
        super(context);
        this.updatePose = updatePose;
        if (engines[appearance] == null) engines[appearance] = new Engine(context.getApplicationContext(), appearance);
        engine = engines[appearance];
        bufferSize = engine.expandedSize;
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    public boolean isReady() { return ready; }

    public void setPose(float yaw, float pitch, boolean custom, boolean enlarged) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.custom = custom;
        this.enlarged = enlarged;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        Choreographer.getInstance().removeFrameCallback(this);
        if (attached && !paused) Choreographer.getInstance().postFrameCallback(this);
        else if (target != null) {
            target.pose = null;
            engine.requestFrame();
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) { return false; }

    @Override public void doFrame(long time) {
        if (!attached || paused) return;
        updatePose.run();
        if (target != null) {
            // TextureView inherits alpha, transforms and clipping from the view tree.
            float alpha = 1f;
            boolean visible = isShown() && getWindowVisibility() == VISIBLE && getGlobalVisibleRect(visibleRect);
            target.pose = visible ? new Pose(yaw, pitch, alpha, custom, enlarged) : null;
            engine.requestFrame();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        ready = false;
        texture.setDefaultBufferSize(bufferSize, bufferSize);
        target = new Target(this, new Surface(texture), bufferSize);
        engine.add(target);
        setPaused(paused);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        // Layout and spring transforms must not resize the GPU buffers on every frame.
        texture.setDefaultBufferSize(bufferSize, bufferSize);
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        ready = false;
        Target previous = target;
        target = null;
        if (previous != null) engine.remove(previous);
        // Abandon the consumer immediately so a pending swap cannot block UI teardown.
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // Recreating TextureView's hardware layer after resume resets the buffer to
        // the view size without a size callback. Restore it after layer creation.
        texture.setDefaultBufferSize(bufferSize, bufferSize);
        if (!ready && target != null && target.hasContent) {
            ready = true;
            if (getParent() instanceof View) ((View) getParent()).invalidate();
        }
    }

    protected void onRenderError() { }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        setPaused(paused);
    }

    @Override protected void onDetachedFromWindow() {
        attached = ready = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    private static final class Pose {
        final float yaw, pitch, alpha;
        final boolean custom, enlarged;
        Pose(float yaw, float pitch, float alpha, boolean custom, boolean enlarged) {
            this.yaw = yaw; this.pitch = pitch; this.alpha = alpha;
            this.custom = custom; this.enlarged = enlarged;
        }
    }

    private static final class Target {
        final WalletDiamondTextureView view;
        final Surface surface;
        volatile boolean valid = true, hasContent;
        volatile Pose pose;
        int width, height;
        EGLSurface egl = EGL14.EGL_NO_SURFACE;
        boolean cleared, custom;
        int idleVersion = -1;
        float alpha = -1;
        Target(WalletDiamondTextureView view, Surface surface, int size) {
            this.view = view; this.surface = surface; width = height = size;
        }
    }

    private static final class Engine {
        final Context context;
        final Handler worker;
        final int size, expandedSize, appearance;
        final ArrayList<Target> views = new ArrayList<>(); // main thread only
        volatile Target[] targets = new Target[0];
        final AtomicBoolean scheduled = new AtomicBoolean();
        final int[] surfaceSize = new int[2];
        EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        EGLContext contextGL = EGL14.EGL_NO_CONTEXT;
        EGLSurface pbuffer = EGL14.EGL_NO_SURFACE;
        EGLConfig config;
        DiamondRenderer idle, interaction, sending;
        long lastFrame, lastIdle;
        int idleVersion;

        Engine(Context context, int appearance) {
            this.context = context;
            this.appearance = appearance;
            size = Math.max(1, Math.min(192, Math.round(42 * context.getResources().getDisplayMetrics().density)));
            expandedSize = 378;
            HandlerThread thread = new HandlerThread("WalletDiamondTextures");
            thread.start();
            worker = new Handler(thread.getLooper());
        }

        void add(Target target) {
            views.add(target);
            targets = views.toArray(new Target[0]);
            requestFrame();
        }

        void remove(Target target) {
            target.valid = false;
            views.remove(target);
            targets = views.toArray(new Target[0]);
            worker.post(() -> {
                try {
                    if (display != EGL14.EGL_NO_DISPLAY) {
                        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, contextGL);
                        if (target.egl != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, target.egl);
                        target.egl = EGL14.EGL_NO_SURFACE;
                        if (targets.length == 0) release();
                    }
                } finally {
                    target.surface.release();
                }
            });
        }

        void requestFrame() {
            if (scheduled.compareAndSet(false, true)) worker.post(this::render);
        }

        DiamondRenderer renderer() {
            DiamondRenderer result = new DiamondRenderer(context, 2);
            result.appearance = appearance;
            return result;
        }

        void initialize() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw new IllegalStateException("Wallet EGL initialize failed");
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            int[] attributes = {EGL14.EGL_RENDERABLE_TYPE, 0x40,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE};
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) throw new IllegalStateException("Wallet EGL config failed");
            config = configs[0];
            contextGL = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            pbuffer = EGL14.eglCreatePbufferSurface(display, config, new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (!EGL14.eglMakeCurrent(display, pbuffer, pbuffer, contextGL)) throw new IllegalStateException("Wallet EGL context failed");
            idle = renderer();
            interaction = renderer();
            lastFrame = lastIdle = 0;
            idleVersion = 0;
        }

        void render() {
            Target[] batch = targets;
            boolean tracing = false;
            try {
                if (batch.length == 0) return;
                if (idle == null) initialize();
                long now = SystemClock.uptimeMillis();
                boolean active = false, redraw = false;
                Pose resting = null;
                for (Target t : batch) {
                    Pose p = t.pose;
                    if (t.valid && p != null) {
                        active |= p.custom;
                        if (!p.custom) resting = p;
                    }
                    redraw |= t.valid && !t.hasContent && p != null;
                }
                if (!redraw && now - lastFrame < (active ? 16 : 33)) return;
                Trace.beginSection(active ? "WalletTextures.dragBatch" : "WalletTextures.idleBatch");
                tracing = true;
                float dt = lastFrame == 0 ? 0 : Math.min(.1f, (now - lastFrame) / 1000f);
                lastFrame = now;
                EGL14.eglMakeCurrent(display, pbuffer, pbuffer, contextGL);
                if (resting != null && (lastIdle == 0 || now - lastIdle >= 33)) {
                    idle.drawToTexture(size, resting.yaw, resting.pitch, lastIdle == 0 ? 0 : (now - lastIdle) / 1000f);
                    lastIdle = now;
                    ++idleVersion;
                }
                boolean advancedInteraction = false, advancedSending = false;
                for (Target t : batch) {
                    if (!t.valid) continue;
                    Pose p = t.pose;
                    if (p == null && t.cleared) continue;
                    if (t.egl == EGL14.EGL_NO_SURFACE) {
                        t.egl = EGL14.eglCreateWindowSurface(display, config, t.surface, new int[]{EGL14.EGL_NONE}, 0);
                        if (t.egl == EGL14.EGL_NO_SURFACE) {
                            if (!t.valid) continue;
                            throw new IllegalStateException("Wallet EGL window failed");
                        }
                    }
                    if (!EGL14.eglMakeCurrent(display, t.egl, t.egl, contextGL)) {
                        if (!t.valid) continue;
                        throw new IllegalStateException("Wallet EGL makeCurrent failed");
                    }
                    EGL14.eglSwapInterval(display, 0);
                    // Buffer resizing takes effect asynchronously; use the actual
                    // drawable dimensions rather than the requested buffer size.
                    if (!EGL14.eglQuerySurface(display, t.egl, EGL14.EGL_WIDTH, surfaceSize, 0)
                            || !EGL14.eglQuerySurface(display, t.egl, EGL14.EGL_HEIGHT, surfaceSize, 1)) {
                        if (!t.valid) continue;
                        throw new IllegalStateException("Wallet EGL surface size failed");
                    }
                    boolean resized = t.width != surfaceSize[0] || t.height != surfaceSize[1];
                    t.width = surfaceSize[0];
                    t.height = surfaceSize[1];
                    if (p == null) {
                        if (!t.cleared) {
                            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
                            GLES30.glClearColor(0, 0, 0, 0);
                            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                            swap(t);
                            t.cleared = true;
                        }
                        continue;
                    }
                    if (!resized && !p.custom && !t.custom && !t.cleared && t.idleVersion == idleVersion && t.alpha == p.alpha) continue;
                    DiamondRenderer image = idle;
                    if (p.custom) {
                        if (p.enlarged) {
                            image = interaction;
                            image.drawToTexture(expandedSize, p.yaw, p.pitch, advancedInteraction ? 0 : dt);
                            advancedInteraction = true;
                        } else {
                            if (sending == null) sending = renderer();
                            image = sending;
                            image.drawToTexture(size, p.yaw, p.pitch, advancedSending ? 0 : dt);
                            advancedSending = true;
                        }
                    }
                    image.drawTexture(t.width, t.height, p.alpha);
                    swap(t);
                    t.cleared = false;
                    t.idleVersion = idleVersion;
                    t.alpha = p.alpha;
                    t.custom = p.custom;
                    t.hasContent = true;
                }
            } catch (RuntimeException error) {
                FileLog.e(error);
                for (Target t : batch) {
                    t.view.post(() -> {
                        if (t.view.target == t) {
                            t.view.ready = false;
                            t.view.onRenderError();
                        }
                    });
                }
            } finally {
                scheduled.set(false);
                if (tracing) Trace.endSection();
            }
        }

        void swap(Target target) {
            if (!EGL14.eglSwapBuffers(display, target.egl) && target.valid) throw new IllegalStateException("Wallet EGL swap failed");
        }

        void release() {
            if (idle != null) idle.destroy();
            if (interaction != null) interaction.destroy();
            if (sending != null) sending.destroy();
            idle = interaction = sending = null;
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(display, pbuffer);
            EGL14.eglDestroyContext(display, contextGL);
            EGL14.eglTerminate(display);
            EGL14.eglReleaseThread();
            display = EGL14.EGL_NO_DISPLAY;
            contextGL = EGL14.EGL_NO_CONTEXT;
            pbuffer = EGL14.EGL_NO_SURFACE;
        }
    }
}
