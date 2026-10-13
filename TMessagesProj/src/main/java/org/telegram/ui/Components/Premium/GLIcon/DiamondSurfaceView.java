package org.telegram.ui.Components.Premium.GLIcon;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.Choreographer;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.Premium.StarParticlesView;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Transparent, independently composited surface for the Wallet diamond. */
public class DiamondSurfaceView extends GLSurfaceView implements Choreographer.FrameCallback {
    public final GLIconRenderer mRenderer;
    private final GestureDetector gestures;
    private StarParticlesView particles;
    private ValueAnimator animation;
    private boolean attached, paused = true, idle, touching;
    private volatile boolean failed;
    private boolean ready;
    private Runnable readyListener;
    private volatile float compositionAlpha = 1f;
    private volatile boolean positionReady, revealScheduled;
    private volatile int placementGeneration;
    private long nextFrame;
    private volatile int surfaceGeneration;
    private static final long FRAME_NANOS = 1_000_000_000L / 60;
    private final ArrayList<Runnable> pendingRedraws = new ArrayList<>();
    private float renderScale = 1f;
    private float bufferScale = 1f;

    /** Reserve the compositor buffer before the view is shown; never resize it on touch. */
    public void reserveRenderScale(float scale) {
        bufferScale = Math.max(1f, scale);
        mRenderer.diamondResolutionScale = renderScale / bufferScale;
        updateBufferSize();
    }

    /** Change only GL's offscreen resolution, independently of SurfaceControl geometry. */
    public void setRenderScale(float scale) {
        scale = Math.max(1f, Math.min(bufferScale, scale));
        if (renderScale == scale) return;
        renderScale = scale;
        mRenderer.diamondResolutionScale = renderScale / bufferScale;
    }

    private void updateBufferSize() {
        if (bufferScale == 1f) {
            getHolder().setSizeFromLayout();
        } else if (getWidth() > 0 && getHeight() > 0) {
            getHolder().setFixedSize(Math.round(getWidth() * bufferScale), Math.round(getHeight() * bufferScale));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (bufferScale > 1f) updateBufferSize();
    }

    @Override
    public void surfaceRedrawNeededAsync(SurfaceHolder holder, Runnable finishDrawing) {
        final ArrayList<Runnable> batch;
        synchronized (pendingRedraws) {
            // Give each request its own identity, even if callbacks are reused.
            pendingRedraws.add(() -> finishDrawing.run());
            batch = new ArrayList<>(pendingRedraws);
        }
        // Older GLSurfaceView implementations overwrite a pending completion when
        // another redraw arrives. The replacement must also finish earlier requests,
        // or WindowManager can keep this and subsequent dialogs hidden indefinitely.
        super.surfaceRedrawNeededAsync(holder, () -> finishRedraws(batch));
    }

    private void finishRedraws(ArrayList<Runnable> batch) {
        final ArrayList<Runnable> callbacks = new ArrayList<>();
        synchronized (pendingRedraws) {
            for (Runnable callback : batch) {
                if (pendingRedraws.remove(callback)) callbacks.add(callback);
            }
        }
        // GL completion and surface teardown can race. Remove before invoking so
        // each report completes exactly once, without calling out under our lock.
        for (Runnable callback : callbacks) callback.run();
    }

    private void finishPendingRedraws() {
        final ArrayList<Runnable> batch;
        synchronized (pendingRedraws) {
            batch = new ArrayList<>(pendingRedraws);
        }
        finishRedraws(batch);
    }

    public DiamondSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        // The RGBA surface must sit above the window to blend with its UI background.
        setZOrderOnTop(true);
        mRenderer = new GLIconRenderer(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_DIAMOND);
        setRenderer(new Renderer() {
            private long previous;
            private boolean announced;
            private int generation;

            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                generation = ++surfaceGeneration;
                announced = false;
                previous = 0;
                failed = false;
                // The previous context is gone; its object names must not be deleted
                // in this new context. GLSurfaceView owns context/surface teardown.
                mRenderer.model = null;
                try {
                    mRenderer.onSurfaceCreated(gl, config);
                } catch (RuntimeException e) {
                    fail(e);
                }
            }

            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) {
                announced = false;
                generation = surfaceGeneration;
                if (!failed) mRenderer.onSurfaceChanged(gl, width, height);
            }

            @Override
            public void onDrawFrame(GL10 gl) {
                if (failed) {
                    GLES20.glClearColor(0, 0, 0, 0);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    return;
                }
                if (!positionReady) {
                    // Submit a transparent first buffer. Android can latch it before
                    // the opening sheet's SurfaceControl position transaction.
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                    GLES20.glClearColor(0, 0, 0, 0);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    if (!revealScheduled) {
                        revealScheduled = true;
                        final int placement = placementGeneration;
                        post(() -> revealAfterPositionCommit(placement));
                    }
                    return;
                }
                try {
                    long now = System.nanoTime();
                    mRenderer.setDeltaTime(previous == 0 ? 1f / 60 : Math.min(.1f, (now - previous) / 1e9f));
                    previous = now;
                    mRenderer.model.diamondAlpha = compositionAlpha;
                    mRenderer.onDrawFrame(gl);
                    int error = GLES20.glGetError();
                    if (error != GLES20.GL_NO_ERROR) throw new IllegalStateException("Diamond GL error: " + error);
                    if (!announced) {
                        announced = true;
                        final int renderedGeneration = generation;
                        post(() -> {
                            if (!attached || failed || renderedGeneration != surfaceGeneration) return;
                            ready = true;
                            Runnable listener = readyListener;
                            readyListener = null;
                            if (listener != null) listener.run();
                        });
                    }
                } catch (RuntimeException e) {
                    fail(e);
                }
            }

            private void fail(RuntimeException error) {
                FileLog.e(error);
                failed = true;
                post(() -> {
                    ready = false;
                    readyListener = null;
                    stopFrames();
                    onRenderError();
                });
            }
        });
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                touching = true;
                cancelAnimation();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent first, MotionEvent current, float dx, float dy) {
                // GestureDetector reports the previous position minus the current
                // one. Keep this in the same inverted physical direction as the
                // Wallet card and amount-field diamond controls.
                mRenderer.angleX -= dx * .5f;
                mRenderer.angleY -= dy * .05f;
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (Math.abs(mRenderer.angleX) > 10) return true;
                float radius = Math.max(1, getWidth() / 2f);
                float x = (40 + (float) Math.random() * 30f) * (radius - e.getX()) / radius;
                float y = (40 + (float) Math.random() * 30f) * (radius - e.getY()) / radius;
                cancelAnimation();
                final float fromX = mRenderer.angleX, fromY = mRenderer.angleY;
                animation = ValueAnimator.ofFloat(0, 1);
                animation.setDuration(220);
                animation.setInterpolator(new PathInterpolator(.23f, 1, .32f, 1));
                animation.addUpdateListener(a -> {
                    float v = (float) a.getAnimatedValue();
                    mRenderer.angleX = fromX + (x - fromX) * v;
                    mRenderer.angleY = fromY + (y - fromY) * v;
                });
                animation.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator a) { returnToRest(); }
                });
                animation.start();
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            touching = false;
            returnToRest();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        return gestures.onTouchEvent(event);
    }

    private void returnToRest() {
        cancelAnimation();
        final float x = mRenderer.angleX, y = mRenderer.angleY;
        animation = ValueAnimator.ofFloat(1, 0);
        animation.setDuration(600);
        animation.setInterpolator(new OvershootInterpolator());
        animation.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            mRenderer.angleX = x * v;
            mRenderer.angleY = y * v;
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { animation = null; startIdle(); }
        });
        animation.start();
        if (particles != null) particles.flingParticles(Math.abs(x + y));
    }

    private void startIdle() {
        if (!attached || paused || !idle || touching || animation != null) return;
        animation = ValueAnimator.ofFloat(mRenderer.angleX, mRenderer.angleX + 360);
        // Swift demo: 2π / 18 × 1.70775 radians per second.
        animation.setDuration(Math.round(18000 / 1.70775));
        animation.setRepeatCount(ValueAnimator.INFINITE);
        animation.setInterpolator(new LinearInterpolator());
        animation.addUpdateListener(a -> mRenderer.angleX = (float) a.getAnimatedValue());
        animation.start();
    }

    private void cancelAnimation() {
        if (animation == null) return;
        animation.removeAllListeners();
        animation.cancel();
        animation = null;
    }

    public void setIdleAnimationEnabled(boolean enabled) {
        idle = enabled;
        if (enabled) startIdle(); else cancelAnimation();
    }

    public void setStarParticlesView(StarParticlesView view) { particles = view; }

    public void whenReady(Runnable listener) {
        readyListener = listener;
        if (ready && listener != null) {
            readyListener = null;
            listener.run();
        }
    }

    public void setPaused(boolean value) {
        paused = value;
        stopFrames();
        if (value) cancelAnimation();
        else if (attached && !failed) {
            startIdle();
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!attached || paused || failed) return;
        // The independently composited surface does not inherit its parents' alpha,
        // including on Android 14+. Only its own alpha is handled natively there.
        float alpha = Build.VERSION.SDK_INT < 34 ? getAlpha() : 1f;
        for (ViewParent p = getParent(); p instanceof View; p = p.getParent()) alpha *= ((View) p).getAlpha();
        compositionAlpha = alpha;
        if (nextFrame == 0 || frameTimeNanos >= nextFrame) {
            if (nextFrame == 0 || frameTimeNanos - nextFrame > FRAME_NANOS) nextFrame = frameTimeNanos;
            nextFrame += FRAME_NANOS;
            requestRender();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void stopFrames() {
        Choreographer.getInstance().removeFrameCallback(this);
        nextFrame = 0;
    }

    public void stopThread() {
        setPaused(true);
        onPause();
    }

    protected void onRenderError() { }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        ++placementGeneration;
        positionReady = revealScheduled = false;
        ready = false;
        super.surfaceCreated(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        ++placementGeneration;
        positionReady = revealScheduled = false;
        ready = false;
        super.surfaceDestroyed(holder);
        finishPendingRedraws();
    }

    private void revealAfterPositionCommit(int placement) {
        if (!attached || placement != placementGeneration) return;
        Runnable reveal = () -> postOnAnimation(() -> {
            if (!attached || failed || placement != placementGeneration) return;
            positionReady = true;
            requestRender();
        });
        if (Build.VERSION.SDK_INT >= 29 && isHardwareAccelerated()) {
            // Wait for a window frame containing the surface's actual position,
            // after the transparent buffer has satisfied the initial redraw.
            getViewTreeObserver().registerFrameCommitCallback(reveal);
            getRootView().invalidate();
        } else {
            // Pre-Q has no frame-commit callback; cross two UI traversal frames.
            postOnAnimation(reveal);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        setPaused(paused);
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        ready = false;
        ++surfaceGeneration;
        stopFrames();
        cancelAnimation();
        super.onDetachedFromWindow();
        finishPendingRedraws();
    }
}
