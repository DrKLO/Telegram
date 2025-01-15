package org.telegram.ui.Components.Premium.GLIcon;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.EmuDetector;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Premium.StarParticlesView;

import java.util.ArrayList;
import java.util.Collections;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;


public class GLIconTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    public boolean touched;
    public GLIconRenderer mRenderer;

    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    private SurfaceTexture mSurface;
    private EGLDisplay mEglDisplay;
    private EGLSurface mEglSurface;
    private EGLContext mEglContext;
    private EGL10 mEgl;
    private EGLConfig eglConfig;
    private GL10 mGl;

    private int targetFrameDurationMillis;

    private int surfaceHeight;
    private int surfaceWidth;

    public boolean isRunning = false;
    private boolean paused = true;
    private boolean rendererChanged = false;
    private boolean dialogIsVisible = false;

    private RenderThread thread;

    private int targetFps;

    private long idleDelay = 2000;

    private final int animationsCount;
    int animationPointer;
    ArrayList<Integer> animationIndexes = new ArrayList<>();
    boolean attached;
    StarParticlesView starParticlesView;
    int type;

    public GLIconTextureView(Context context, int style) {
        this(context, style, Icon3D.TYPE_STAR);
    }

    public GLIconTextureView(Context context, int style, int type) {
        super(context);

        this.type = type;
        animationsCount = type == Icon3D.TYPE_COIN || type == Icon3D.TYPE_DEAL ? 1 : 5;
        setOpaque(false);
        setRenderer(new GLIconRenderer(context, style, type));
        initialize(context);

        gestureDetector = new GestureDetector(context, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent motionEvent) {
                if (backAnimation != null) {
                    backAnimation.removeAllListeners();
                    backAnimation.cancel();
                    backAnimation = null;
                }
                if (animatorSet != null) {
                    animatorSet.removeAllListeners();
                    animatorSet.cancel();
                    animatorSet = null;
                }
                AndroidUtilities.cancelRunOnUIThread(idleAnimation);
                touched = true;
                return true;
            }

            @Override
            public void onShowPress(MotionEvent motionEvent) {

            }

            @Override
            public boolean onSingleTapUp(MotionEvent motionEvent) {
                float rad = getMeasuredWidth() / 2f;
                float toAngleX = (40 + Utilities.random.nextInt(30)) * (rad - motionEvent.getX()) / rad;
                float toAngleY = (40 + Utilities.random.nextInt(30)) * (rad - motionEvent.getY()) / rad;
                AndroidUtilities.runOnUIThread(() -> {
                    if (backAnimation != null) {
                        backAnimation.removeAllListeners();
                        backAnimation.cancel();
                        backAnimation = null;
                    }
                    if (animatorSet != null) {
                        animatorSet.removeAllListeners();
                        animatorSet.cancel();
                        animatorSet = null;
                    }
                    if (Math.abs(mRenderer.angleX) > 10) {
                        startBackAnimation();
                        return;
                    }
                    AndroidUtilities.cancelRunOnUIThread(idleAnimation);
                    animatorSet = new AnimatorSet();
                    int inTime = 220;

                    ValueAnimator v1 = ValueAnimator.ofFloat(mRenderer.angleX, toAngleX);
                    v1.addUpdateListener(xUpdater);
                    v1.setDuration(inTime);
                    v1.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);

                    ValueAnimator v2 = ValueAnimator.ofFloat(toAngleX, 0);
                    v2.addUpdateListener(xUpdater);
                    v2.setStartDelay(inTime);
                    v2.setDuration(600);
                    v2.setInterpolator(AndroidUtilities.overshootInterpolator);

                    ValueAnimator v3 = ValueAnimator.ofFloat(mRenderer.angleY, toAngleY);
                    v3.addUpdateListener(yUpdater);
                    v3.setDuration(inTime);
                    v3.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);

                    ValueAnimator v4 = ValueAnimator.ofFloat(toAngleY, 0);
                    v4.addUpdateListener(yUpdater);
                    v4.setStartDelay(inTime);
                    v4.setDuration(600);
                    v4.setInterpolator(AndroidUtilities.overshootInterpolator);

                    animatorSet.playTogether(v1, v2, v3, v4);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mRenderer.angleX = 0;
                            animatorSet = null;
                            scheduleIdleAnimation(idleDelay);
                        }
                    });
                    animatorSet.start();
                }, 16);

                return true;
            }

            @Override
            public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
                mRenderer.angleX += v * 0.5f;
                mRenderer.angleY += v1 * 0.05f;
                return true;
            }

            @Override
            public void onLongPress(MotionEvent motionEvent) {
                GLIconTextureView.this.onLongPress();
            }

            @Override
            public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
                return false;
            }

        });
        gestureDetector.setIsLongpressEnabled(true);
        for (int i = 0; i < animationsCount; i++) {
            animationIndexes.add(i);
        }
        Collections.shuffle(animationIndexes);
    }

    public void onLongPress() {

    }


    public synchronized void setRenderer(GLIconRenderer renderer) {
        mRenderer = renderer;
        rendererChanged = true;
    }


    private void initialize(Context context) {
        targetFps = (int) AndroidUtilities.screenRefreshRate;
        setSurfaceTextureListener(this);
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        startThread(surface, width, height);
    }

    public void startThread(SurfaceTexture surface, int width, int height) {
        thread = new RenderThread();
        mSurface = surface;
        setDimensions(width, height);
        targetFrameDurationMillis = Math.max(0, (int) ((1 / (float) targetFps) * 1000) - 1);
        thread.start();
    }


    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        setDimensions(width, height);
        if (mRenderer != null) {
            mRenderer.onSurfaceChanged(mGl, width, height);
        }
    }

    public synchronized void setPaused(boolean isPaused) {
        paused = isPaused;
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        ready = false;
        stopThread();
        return false;
    }

    public void stopThread() {
        if (thread != null) {
            isRunning = false;
//            try {
//                thread.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            thread = null;
        }

    }

    private boolean shouldSleep() {
        return isPaused() || mRenderer == null;
    }

    public void setBackgroundBitmap(Bitmap gradientTextureBitmap) {
        mRenderer.setBackground(gradientTextureBitmap);
    }

    private volatile boolean ready;
    private volatile Runnable readyListener;
    public void whenReady(Runnable whenReady) {
        if (ready) whenReady.run();
        else readyListener = whenReady;
    }


    private class RenderThread extends Thread {
        @Override
        public void run() {
            isRunning = true;

            try {
                initGL();
            } catch (Exception e) {
                FileLog.e(e);
                isRunning = false;
                return;
            }
            checkGlError();

            long lastFrameTime = System.currentTimeMillis();

            while (isRunning) {
                while (mRenderer == null) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                if (rendererChanged) {
                    initializeRenderer(mRenderer);
                    rendererChanged = false;
                }

                try {
                    if (!shouldSleep()) {
                        final long now = System.currentTimeMillis();
                        float dt = (now - lastFrameTime) / 1000f;
                        lastFrameTime = now;
                        drawSingleFrame(dt);
                        if (!ready) {
                            ready = true;
                            AndroidUtilities.runOnUIThread(readyListener);
                            readyListener = null;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    break;
                }

                try {
                    if (shouldSleep())
                        Thread.sleep(100);
                    else {
                        long thisFrameTime = System.currentTimeMillis();
                        long timDiff = thisFrameTime - lastFrameTime;
                        while (timDiff < targetFrameDurationMillis) {
                            thisFrameTime = System.currentTimeMillis();
                            timDiff = thisFrameTime - lastFrameTime;
                        }
                    }
                } catch (InterruptedException ignore) {
                }
            }
        }

    }

    private synchronized void initializeRenderer(GLIconRenderer renderer) {
        if (renderer != null && isRunning) {
            renderer.onSurfaceCreated(mGl, eglConfig);
            renderer.onSurfaceChanged(mGl, surfaceWidth, surfaceHeight);
        }
    }

    private synchronized void drawSingleFrame(float dt) {
        checkCurrent();
        if (mRenderer != null) {
            mRenderer.setDeltaTime(dt);
            mRenderer.onDrawFrame(mGl);
        }
        checkGlError();
        mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
    }

    public void setDimensions(int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
    }

    private void checkCurrent() {
        if (!mEglContext.equals(mEgl.eglGetCurrentContext())
                || !mEglSurface.equals(mEgl
                .eglGetCurrentSurface(EGL10.EGL_DRAW))) {
            checkEglError();
            if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface,
                    mEglSurface, mEglContext)) {
                throw new RuntimeException(
                        "eglMakeCurrent failed "
                                + GLUtils.getEGLErrorString(mEgl
                                .eglGetError()));
            }
            checkEglError();
        }
    }

    private void checkEglError() {
        final int error = mEgl.eglGetError();
        if (error != EGL10.EGL_SUCCESS) {
            FileLog.e("cannot swap buffers!");
        }
    }


    private void checkGlError() {
        final int error = mGl.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            FileLog.e("GL error = 0x" + Integer.toHexString(error));
        }
    }

    private void initGL() {

        mEgl = (EGL10) EGLContext.getEGL();
        mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed "
                    + GLUtils.getEGLErrorString(mEgl.eglGetError()));
        }
        int[] version = new int[2];
        if (!mEgl.eglInitialize(mEglDisplay, version)) {
            throw new RuntimeException("eglInitialize failed "
                    + GLUtils.getEGLErrorString(mEgl.eglGetError()));
        }
        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec;
        if (EmuDetector.with(getContext()).detect()) {
            configSpec = new int[] {
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 16,  //was 0
                    EGL10.EGL_NONE
            };
        } else {
            configSpec = new int[] {
                    EGL10.EGL_RENDERABLE_TYPE,
                    EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 16,  //was 0
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_SAMPLE_BUFFERS, 1,
                    EGL10.EGL_NONE
            };
        }
        eglConfig = null;
        if (!mEgl.eglChooseConfig(mEglDisplay, configSpec, configs, 1,
                configsCount)) {
            throw new IllegalArgumentException(
                    "eglChooseConfig failed "
                            + GLUtils.getEGLErrorString(mEgl
                            .eglGetError()));
        } else if (configsCount[0] > 0) {
            eglConfig = configs[0];
        }
        if (eglConfig == null) {
            throw new RuntimeException("eglConfig not initialized");
        }
        int[] attrib_list = {
                EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE
        };
        mEglContext = mEgl.eglCreateContext(mEglDisplay,
                eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
        checkEglError();
        mEglSurface = mEgl.eglCreateWindowSurface(
                mEglDisplay, eglConfig, mSurface, null);
        checkEglError();
        if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) {
            int error = mEgl.eglGetError();
            if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                FileLog.e("eglCreateWindowSurface returned EGL10.EGL_BAD_NATIVE_WINDOW");
                return;
            }
            throw new RuntimeException(
                    "eglCreateWindowSurface failed "
                            + GLUtils.getEGLErrorString(error));
        }
        if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface,
                mEglSurface, mEglContext)) {
            throw new RuntimeException("eglMakeCurrent failed "
                    + GLUtils.getEGLErrorString(mEgl.eglGetError()));
        }
        checkEglError();
        mGl = (GL10) mEglContext.getGL();
        checkEglError();
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }


    GestureDetector gestureDetector;
    ValueAnimator backAnimation;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
            touched = false;
            startBackAnimation();
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return gestureDetector.onTouchEvent(event);
    }

    public void startBackAnimation() {
        cancelAnimatons();
        float fromX = mRenderer.angleX;
        float fromY = mRenderer.angleY;
        float fromX2 = mRenderer.angleX2;
        float sum = fromX + fromY;
        backAnimation = ValueAnimator.ofFloat(1f, 0f);
        backAnimation.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            mRenderer.angleX = v * fromX;
            mRenderer.angleX2 = v * fromX2;
            mRenderer.angleY = v * fromY;

        });
        backAnimation.setDuration(600);
        backAnimation.setInterpolator(new OvershootInterpolator());
        backAnimation.start();
        if (starParticlesView != null) {
            starParticlesView.flingParticles(Math.abs(sum));
        }
        scheduleIdleAnimation(idleDelay);
    }

    public void cancelAnimatons() {
        if (backAnimation != null) {
            backAnimation.removeAllListeners();
            backAnimation.cancel();
            backAnimation = null;
        }
        if (animatorSet != null) {
            animatorSet.removeAllListeners();
            animatorSet.cancel();
            animatorSet = null;
        }
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        rendererChanged = true;
        scheduleIdleAnimation(idleDelay);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnimatons();
        if (mRenderer != null) {
            mRenderer.angleX = 0;
            mRenderer.angleY = 0;
            mRenderer.angleX2 = 0;
        }
        attached = false;
    }

    AnimatorSet animatorSet = new AnimatorSet();

    Runnable idleAnimation = new Runnable() {
        @Override
        public void run() {
            if ((animatorSet != null && animatorSet.isRunning()) || (backAnimation != null && backAnimation.isRunning())) {
                scheduleIdleAnimation(idleDelay);
            } else {
                startIdleAnimation();
            }
        }
    };

    ValueAnimator.AnimatorUpdateListener xUpdater2 = valueAnimator -> {
        mRenderer.angleX2 = (float) valueAnimator.getAnimatedValue();
    };

    ValueAnimator.AnimatorUpdateListener xUpdater = valueAnimator -> {
        mRenderer.angleX = (float) valueAnimator.getAnimatedValue();
    };

    ValueAnimator.AnimatorUpdateListener yUpdater = valueAnimator -> {
        mRenderer.angleY = (float) valueAnimator.getAnimatedValue();
    };

    public void scheduleIdleAnimation(long time) {
        AndroidUtilities.cancelRunOnUIThread(idleAnimation);
        if (dialogIsVisible) {
            return;
        }
        AndroidUtilities.runOnUIThread(idleAnimation, time);
    }

    public void cancelIdleAnimation() {
        AndroidUtilities.cancelRunOnUIThread(idleAnimation);
    }


    protected void startIdleAnimation() {
        if (!attached) {
            return;
        }

        int i = animationIndexes.get(animationPointer);
        animationPointer++;
        if (animationPointer >= animationIndexes.size()) {
            Collections.shuffle(animationIndexes);
            animationPointer = 0;
        }

        if (i == 0) {
            pullAnimation();
        } else if (i == 1) {
            slowFlipAnimation();
        } else if (i == 2) {
            sleepAnimation();
        } else {
            flipAnimation();
        }
    }

    private void slowFlipAnimation() {
        animatorSet = new AnimatorSet();
        ValueAnimator v1 = ValueAnimator.ofFloat(mRenderer.angleX, 360);
        v1.addUpdateListener(xUpdater);
        v1.setDuration(8000);
        v1.setInterpolator(CubicBezierInterpolator.DEFAULT);

        animatorSet.playTogether(v1);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mRenderer.angleX = 0;
                animatorSet = null;
                scheduleIdleAnimation(idleDelay);
            }
        });
        animatorSet.start();
    }

    private void pullAnimation() {
        int i = Math.abs(Utilities.random.nextInt() % 4);
        animatorSet = new AnimatorSet();
        if (i == 0 && type != Icon3D.TYPE_COIN && type != Icon3D.TYPE_DEAL) {
            int a = 48;

            ValueAnimator v1 = ValueAnimator.ofFloat(mRenderer.angleY, a);
            v1.addUpdateListener(yUpdater);
            v1.setDuration(2300);
            v1.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);


            ValueAnimator v2 = ValueAnimator.ofFloat(a, 0);
            v2.addUpdateListener(yUpdater);
            v2.setDuration(500);
            v2.setStartDelay(2300);
            v2.setInterpolator(AndroidUtilities.overshootInterpolator);
            animatorSet.playTogether(v1, v2);
        } else {
            int dg = 485;
            if (type == Icon3D.TYPE_COIN || type == Icon3D.TYPE_DEAL) {
                dg = 360;
            }
            int a = dg;
            if (i == 2) {
                a = -dg;
            }
            ValueAnimator v1 = ValueAnimator.ofFloat(mRenderer.angleY, a);
            v1.addUpdateListener(xUpdater);
            v1.setDuration(3000);
            v1.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);


            ValueAnimator v2 = ValueAnimator.ofFloat(a, 0);
            v2.addUpdateListener(xUpdater);
            v2.setDuration(1000);
            v2.setStartDelay(3000);
            v2.setInterpolator(AndroidUtilities.overshootInterpolator);
            animatorSet.playTogether(v1, v2);
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mRenderer.angleX = 0;
                animatorSet = null;
                scheduleIdleAnimation(idleDelay);
            }
        });
        animatorSet.start();
    }

    private void flipAnimation() {
        animatorSet = new AnimatorSet();
        ValueAnimator v1 = ValueAnimator.ofFloat(mRenderer.angleX, 180);
        v1.addUpdateListener(xUpdater);
        v1.setDuration(600);
        v1.setInterpolator(CubicBezierInterpolator.DEFAULT);

        ValueAnimator v2 = ValueAnimator.ofFloat(180, 360);
        v2.addUpdateListener(xUpdater);
        v2.setDuration(600);
        v2.setStartDelay(2000);
        v2.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animatorSet.playTogether(v1, v2);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mRenderer.angleX = 0;
                animatorSet = null;
                scheduleIdleAnimation(idleDelay);
            }
        });
        animatorSet.start();
    }

    private void sleepAnimation() {
        animatorSet = new AnimatorSet();
        ValueAnimator v1 = ValueAnimator.ofFloat(mRenderer.angleX, 184);
        v1.addUpdateListener(xUpdater);
        v1.setDuration(600);
        v1.setInterpolator(CubicBezierInterpolator.EASE_OUT);

        ValueAnimator v2 = ValueAnimator.ofFloat(mRenderer.angleY, 50);
        v2.addUpdateListener(yUpdater);
        v2.setDuration(600);
        v2.setInterpolator(CubicBezierInterpolator.EASE_OUT);


        ValueAnimator v3 = ValueAnimator.ofFloat(180, 0);
        v3.addUpdateListener(xUpdater);
        v3.setDuration(800);
        v3.setStartDelay(10000);
        v3.setInterpolator(AndroidUtilities.overshootInterpolator);

        ValueAnimator v4 = ValueAnimator.ofFloat(60, 0);
        v4.addUpdateListener(yUpdater);
        v4.setDuration(800);
        v4.setStartDelay(10000);
        v4.setInterpolator(AndroidUtilities.overshootInterpolator);

        ValueAnimator v5 = ValueAnimator.ofFloat(0, 2, -3, 2, -1, 2, -3, 2, -1, 0);
        v5.addUpdateListener(xUpdater2);
        v5.setDuration(10000);
        v5.setInterpolator(new LinearInterpolator());


        animatorSet.playTogether(v1, v2, v3, v4, v5);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mRenderer.angleX = 0;
                animatorSet = null;
                scheduleIdleAnimation(idleDelay);
            }
        });
        animatorSet.start();
    }

    public void setStarParticlesView(StarParticlesView starParticlesView) {
        this.starParticlesView = starParticlesView;
    }

    public void startEnterAnimation(int angle, long delay) {
        if (mRenderer != null) {
            mRenderer.angleX = -180;
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    startBackAnimation();
                }
            }, delay);

        }
    }

    public void setDialogVisible(boolean isVisible) {
        dialogIsVisible = isVisible;
        if (isVisible) {
            AndroidUtilities.cancelRunOnUIThread(idleAnimation);
            startBackAnimation();
        } else {
            scheduleIdleAnimation(idleDelay);
        }
    }
}