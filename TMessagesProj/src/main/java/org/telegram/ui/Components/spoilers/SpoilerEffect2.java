package org.telegram.ui.Components.spoilers;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLES31;
import android.os.Build;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class SpoilerEffect2 {

    public final int MAX_FPS;
    private final double MIN_DELTA;
    private final double MAX_DELTA;

    public static boolean supports() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private static SpoilerEffect2 instance;
    public static SpoilerEffect2 getInstance(View view) {
        if (view == null || !supports()) {
            return null;
        }
        if (instance == null) {
            final int sz = getSize();
            ViewGroup rootView = getRootView(view);
            if (rootView == null) {
                return null;
            }
            instance = new SpoilerEffect2(makeTextureViewContainer(rootView), sz, sz);
        }
        instance.attach(view);
        return instance;
    }

    private static ViewGroup getRootView(View view) {
        Activity activity = AndroidUtilities.findActivity(view.getContext());
        if (activity == null) {
            return null;
        }
        View rootView = activity.findViewById(android.R.id.content).getRootView();
        if (!(rootView instanceof ViewGroup)) {
            return null;
        }
        return (ViewGroup) rootView;
    }

    public static void pause(boolean pause) {
        if (instance != null && instance.thread != null) {
            instance.thread.pause(pause);
        }
    }

    private static int getSize() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return Math.min(1280, (int) ((AndroidUtilities.displaySize.x + AndroidUtilities.displaySize.y) / 2f * 1.0f));
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return Math.min(900, (int) ((AndroidUtilities.displaySize.x + AndroidUtilities.displaySize.y) / 2f * .8f));
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return Math.min(720, (int) ((AndroidUtilities.displaySize.x + AndroidUtilities.displaySize.y) / 2f * .7f));
        }
    }


    private static FrameLayout makeTextureViewContainer(ViewGroup rootView) {
        FrameLayout container = new FrameLayout(rootView.getContext()) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                return false;
            }
        };
        rootView.addView(container);
        return container;
    }

    private final ViewGroup textureViewContainer;
    private final TextureView textureView;
    private SpoilerThread thread;
    private int width, height;
    public boolean destroyed;

    private final ArrayList<View> holders = new ArrayList<View>();
    private final HashMap<View, Integer> holdersToIndex = new HashMap<>();
    private int holdersIndex = 0;

    public void attach(View view) {
        if (destroyed) {
            return;
        }
        if (!holders.contains(view)) {
            holders.add(view);
            holdersToIndex.put(view, holdersIndex++);
        }
    }

    public void reassignAttach(View view, int index) {
        holdersToIndex.put(view, index);
    }

    public int getAttachIndex(View view) {
        Integer index = holdersToIndex.get(view);
        if (index == null) {
            index = 0;
        }
        return index;
    }

    public void detach(View view) {
        holders.remove(view);
        holdersToIndex.remove(view);
        if (!destroyed) {
            AndroidUtilities.cancelRunOnUIThread(checkDestroy);
            AndroidUtilities.runOnUIThread(checkDestroy, 30);
        }
    }

    public void invalidate() {
        for (int i = 0; i < holders.size(); ++i) {
            holders.get(i).invalidate();
        }
    }

    private final Runnable checkDestroy = () -> {
        if (holders.isEmpty()) {
            destroy();
        }
    };

    public void draw(Canvas canvas, View view) {
        draw(canvas, view, view.getWidth(), view.getHeight(), 1f);
    }

    public void draw(Canvas canvas, View view, int w, int h) {
        draw(canvas, view, w, h, 1f);
    }

    public void draw(Canvas canvas, View view, int w, int h, float alpha) {
        draw(canvas, view, w, h, alpha, false);
    }

    public void draw(Canvas canvas, View view, int w, int h, float alpha, boolean toBitmap) {
        if (canvas == null || view == null) {
            return;
        }
        canvas.save();
        int ow = width, oh = height;
        Integer index = holdersToIndex.get(view);
        if (index == null) {
            index = 0;
        }
        if (w > ow || h > oh) {
            final float scale = Math.max(w / (float) ow, h / (float) oh);
            canvas.scale(scale, scale);
        }
        if ((index % 4) == 1) {
            canvas.rotate(180, ow / 2f, oh / 2f);
        }
        if ((index % 4) == 2) {
            canvas.scale(-1, 1, ow / 2f, oh / 2f);
        }
        if ((index % 4) == 3) {
            canvas.scale(1, -1, ow / 2f, oh / 2f);
        }
        if (toBitmap) {
            Bitmap bitmap = textureView.getBitmap();
            if (bitmap != null) {
                Paint paint = new Paint(Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.WHITE);
                canvas.drawBitmap(bitmap, 0, 0, paint);
                bitmap.recycle();
            }
        } else {
            textureView.setAlpha(alpha);
            textureView.draw(canvas);
        }
        canvas.restore();
    }

    private void destroy() {
        destroyed = true;
        instance = null;
        if (thread != null) {
            thread.halt();
            thread = null;
        }
        textureViewContainer.removeView(textureView);
        if (textureViewContainer.getParent() instanceof ViewGroup) {
            ViewGroup rootView = (ViewGroup) textureViewContainer.getParent();
            rootView.removeView(textureViewContainer);
        }
    }

    private SpoilerEffect2(ViewGroup container, int width, int height) {
        MAX_FPS = (int) AndroidUtilities.screenRefreshRate;
        MIN_DELTA = 1.0 / MAX_FPS;
        MAX_DELTA = MIN_DELTA * 4;

        this.width = width;
        this.height = height;

        textureViewContainer = container;
        textureView = new TextureView(textureViewContainer.getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(SpoilerEffect2.this.width, SpoilerEffect2.this.height);
            }
        };
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                if (thread == null) {
                    thread = new SpoilerThread(surface, width, height, SpoilerEffect2.this::invalidate);
                    thread.start();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                if (thread != null) {
                    thread.updateSize(width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                if (thread != null) {
                    thread.halt();
                    thread = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
        textureView.setOpaque(false);
        textureViewContainer.addView(textureView);
    }

    private void resize(int w, int h) {
        if (this.width == w && this.height == h) {
            return;
        }
        this.width = w;
        this.height = h;
        textureView.requestLayout();
    }

    private class SpoilerThread extends Thread {
        private volatile boolean running = true;
        private volatile boolean paused = false;

        private final Runnable invalidate;
        private final SurfaceTexture surfaceTexture;
        private final Object resizeLock = new Object();
        private boolean resize;
        private int width, height;
        private int particlesCount;
        private float radius = AndroidUtilities.dpf2(1.2f);

        public SpoilerThread(SurfaceTexture surfaceTexture, int width, int height, Runnable invalidate) {
            this.invalidate = invalidate;
            this.surfaceTexture = surfaceTexture;
            this.width = width;
            this.height = height;
            this.particlesCount = particlesCount();
        }

        private int particlesCount() {
            return (int) Utilities.clamp(width * height / (500f * 500f) * 1000, 10000, 500);
        }

        public void updateSize(int width, int height) {
            synchronized (resizeLock) {
                resize = true;
                this.width = width;
                this.height = height;
            }
        }

        public void halt() {
            running = false;
        }

        public void pause(boolean paused) {
            this.paused = paused;
        }

        @Override
        public void run() {
            init();
            long lastTime = System.nanoTime();
            while (running) {
                final long now = System.nanoTime();
                double Δt = (now - lastTime) / 1_000_000_000.;
                lastTime = now;

                if (Δt < MIN_DELTA) {
                    double wait = MIN_DELTA - Δt;
                    try {
                        long milli = (long) (wait * 1000L);
                        int nano = (int) ((wait - milli / 1000.) * 1_000_000_000);
                        sleep(milli, nano);
                    } catch (Exception ignore) {}
                    Δt = MIN_DELTA;
                } else if (Δt > MAX_DELTA) {
                    Δt = MAX_DELTA;
                }

                while (paused) {
                    try {
                        sleep(1000);
                    } catch (Exception ignore) {}
                }

                checkResize();
                drawFrame((float) Δt);

                AndroidUtilities.cancelRunOnUIThread(this.invalidate);
                AndroidUtilities.runOnUIThread(this.invalidate);
            }
            die();
        }

        private EGL10 egl;
        private EGLDisplay eglDisplay;
        private EGLConfig eglConfig;
        private EGLSurface eglSurface;
        private EGLContext eglContext;

        private int drawProgram;
        private int resetHandle;
        private int timeHandle;
        private int deltaTimeHandle;
        private int sizeHandle;
        private int radiusHandle;
        private int seedHandle;

        private boolean reset = true;

        private int currentBuffer = 0;
        private int[] particlesData;

        private void init() {
            egl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();

            eglDisplay = egl.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == egl.EGL_NO_DISPLAY) {
                running = false;
                return;
            }
            int[] version = new int[2];
            if (!egl.eglInitialize(eglDisplay, version)) {
                running = false;
                return;
            }

            int[] configAttributes = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_NONE
            };
            EGLConfig[] eglConfigs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl.eglChooseConfig(eglDisplay, configAttributes, eglConfigs, 1, numConfigs)) {
                running = false;
                return;
            }
            eglConfig = eglConfigs[0];

            int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            };
            eglContext = egl.eglCreateContext(eglDisplay, eglConfig, egl.EGL_NO_CONTEXT, contextAttributes);
            if (eglContext == null) {
                running = false;
                return;
            }

            eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
            if (eglSurface == null) {
                running = false;
                return;
            }

            if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                running = false;
                return;
            }

            genParticlesData();

            // draw program (vertex and fragment shaders)
            int vertexShader = GLES31.glCreateShader(GLES31.GL_VERTEX_SHADER);
            int fragmentShader = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
            if (vertexShader == 0 || fragmentShader == 0) {
                running = false;
                return;
            }
            GLES31.glShaderSource(vertexShader, RLottieDrawable.readRes(null, R.raw.spoiler_vertex) + "\n// " + Math.random());
            GLES31.glCompileShader(vertexShader);
            int[] status = new int[1];
            GLES31.glGetShaderiv(vertexShader, GLES31.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                FileLog.e("SpoilerEffect2, compile vertex shader error: " + GLES31.glGetShaderInfoLog(vertexShader));
                GLES31.glDeleteShader(vertexShader);
                running = false;
                return;
            }
            GLES31.glShaderSource(fragmentShader, RLottieDrawable.readRes(null, R.raw.spoiler_fragment) + "\n// " + Math.random());
            GLES31.glCompileShader(fragmentShader);
            GLES31.glGetShaderiv(fragmentShader, GLES31.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                FileLog.e("SpoilerEffect2, compile fragment shader error: " + GLES31.glGetShaderInfoLog(fragmentShader));
                GLES31.glDeleteShader(fragmentShader);
                running = false;
                return;
            }
            drawProgram = GLES31.glCreateProgram();
            if (drawProgram == 0) {
                running = false;
                return;
            }
            GLES31.glAttachShader(drawProgram, vertexShader);
            GLES31.glAttachShader(drawProgram, fragmentShader);
            String[] feedbackVaryings = {"outPosition", "outVelocity", "outTime", "outDuration"};
            GLES31.glTransformFeedbackVaryings(drawProgram, feedbackVaryings, GLES31.GL_INTERLEAVED_ATTRIBS);

            GLES31.glLinkProgram(drawProgram);
            GLES31.glGetProgramiv(drawProgram, GLES31.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                FileLog.e("SpoilerEffect2, link draw program error: " + GLES31.glGetProgramInfoLog(drawProgram));
                running = false;
                return;
            }

            resetHandle = GLES31.glGetUniformLocation(drawProgram, "reset");
            timeHandle = GLES31.glGetUniformLocation(drawProgram, "time");
            deltaTimeHandle = GLES31.glGetUniformLocation(drawProgram, "deltaTime");
            sizeHandle = GLES31.glGetUniformLocation(drawProgram, "size");
            radiusHandle = GLES31.glGetUniformLocation(drawProgram, "r");
            seedHandle = GLES31.glGetUniformLocation(drawProgram, "seed");

            GLES31.glViewport(0, 0, width, height);
            GLES31.glEnable(GLES31.GL_BLEND);
            GLES31.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES31.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            GLES31.glUseProgram(drawProgram);
            GLES31.glUniform2f(sizeHandle, width, height);
            GLES31.glUniform1f(resetHandle, reset ? 1 : 0);
            GLES31.glUniform1f(radiusHandle, radius);
            GLES31.glUniform1f(seedHandle, Utilities.fastRandom.nextInt(256) / 256f);

            GLES31.glUniform1f(GLES31.glGetUniformLocation(drawProgram, "noiseScale"), 6);
            GLES31.glUniform1f(GLES31.glGetUniformLocation(drawProgram, "noiseSpeed"), 0.6f);
            GLES31.glUniform1f(GLES31.glGetUniformLocation(drawProgram, "noiseMovement"), 4f);
            GLES31.glUniform1f(GLES31.glGetUniformLocation(drawProgram, "longevity"), 1.4f);
            GLES31.glUniform1f(GLES31.glGetUniformLocation(drawProgram, "dampingMult"), .9999f);
            GLES31.glUniform1f(GLES31.glGetUniformLocation(drawProgram, "maxVelocity"), 6.f);
            GLES31.glUniform1f(GLES31.glGetUniformLocation(drawProgram, "velocityMult"), 1.0f);
            GLES31.glUniform1f(GLES31.glGetUniformLocation(drawProgram, "forceMult"), 0.6f);
        }

        private float t;
        private final float timeScale = .65f;

        private void drawFrame(float Δt) {
            if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                running = false;
                return;
            }

            t += Δt * timeScale;
            if (t > 1000.f) {
                t = 0;
            }

            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, particlesData[currentBuffer]);
            GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 24, 0); // Position (vec2)
            GLES31.glEnableVertexAttribArray(0);
            GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, 24, 8); // Velocity (vec2)
            GLES31.glEnableVertexAttribArray(1);
            GLES31.glVertexAttribPointer(2, 1, GLES31.GL_FLOAT, false, 24, 16); // Time (float)
            GLES31.glEnableVertexAttribArray(2);
            GLES31.glVertexAttribPointer(3, 1, GLES31.GL_FLOAT, false, 24, 20); // Duration (float)
            GLES31.glEnableVertexAttribArray(3);
            GLES31.glBindBufferBase(GLES31.GL_TRANSFORM_FEEDBACK_BUFFER, 0, particlesData[1 - currentBuffer]);
            GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 24, 0); // Position (vec2)
            GLES31.glEnableVertexAttribArray(0);
            GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, 24, 8); // Velocity (vec2)
            GLES31.glEnableVertexAttribArray(1);
            GLES31.glVertexAttribPointer(2, 1, GLES31.GL_FLOAT, false, 24, 16); // Time (float)
            GLES31.glEnableVertexAttribArray(2);
            GLES31.glVertexAttribPointer(3, 1, GLES31.GL_FLOAT, false, 24, 20); // Duration (float)
            GLES31.glEnableVertexAttribArray(3);
            GLES31.glUniform1f(timeHandle, t);
            GLES31.glUniform1f(deltaTimeHandle, Δt * timeScale);
            GLES31.glBeginTransformFeedback(GLES31.GL_POINTS);
            GLES31.glDrawArrays(GLES31.GL_POINTS, 0, particlesCount);
            GLES31.glEndTransformFeedback();

            if (reset) {
                reset = false;
                GLES31.glUniform1f(resetHandle, 0f);
            }
            currentBuffer = 1 - currentBuffer;

            egl.eglSwapBuffers(eglDisplay, eglSurface);

            checkGlErrors();
        }

        private void die() {
            if (particlesData != null) {
                try { GLES31.glDeleteBuffers(2, particlesData, 0); } catch (Exception e) { FileLog.e(e); };
                particlesData = null;
            }
            if (drawProgram != 0) {
                try { GLES31.glDeleteProgram(drawProgram); } catch (Exception e) { FileLog.e(e); };
                drawProgram = 0;
            }
            if (egl != null) {
                try { egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT); } catch (Exception e) { FileLog.e(e); };
                try { egl.eglDestroySurface(eglDisplay, eglSurface); } catch (Exception e) { FileLog.e(e); };
                try { egl.eglDestroyContext(eglDisplay, eglContext); } catch (Exception e) { FileLog.e(e); };
            }
            try { surfaceTexture.release(); } catch (Exception e) { FileLog.e(e); };

            checkGlErrors();
        }

        private void checkResize() {
            synchronized (resizeLock) {
                if (resize) {
                    GLES31.glUniform2f(sizeHandle, width, height);
                    GLES31.glViewport(0, 0, width, height);
                    int newParticlesCount = particlesCount();
                    if (newParticlesCount > this.particlesCount) {
                        reset = true;
                        genParticlesData();
                    }
                    this.particlesCount = newParticlesCount;
                    resize = false;
                }
            }
        }

        private void genParticlesData() {
            if (particlesData != null) {
                GLES31.glDeleteBuffers(2, particlesData, 0);
            }

            particlesData = new int[2];
            GLES31.glGenBuffers(2, particlesData, 0);

            for (int i = 0; i < 2; ++i) {
                GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, particlesData[i]);
                GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, this.particlesCount * 6 * 4, null, GLES31.GL_DYNAMIC_DRAW);
            }

            checkGlErrors();
        }

        private void checkGlErrors() {
            int err;
            while ((err = GLES31.glGetError()) != GLES31.GL_NO_ERROR) {
                FileLog.e("spoiler gles error " + err);
            }
        }
    }
}
