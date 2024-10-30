package org.telegram.ui.Stories;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Interpolator;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inspector.WindowInspector;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Stories.recorder.StoryRecorder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class StoryWaveEffectView extends TextureView implements TextureView.SurfaceTextureListener  {

    public static StoryWaveEffectView launch(Context context, float cx, float cy, float r) {
        if (SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_AVERAGE || !LiteMode.isEnabled(LiteMode.FLAGS_CHAT)) {
            return null;
        }
        return new StoryWaveEffectView(context, cx, cy, r);
    }

    private final static String VERTEX_SHADER =
        "attribute vec4 vPosition;\n" +
        "attribute vec2 aTexCoord;\n" +
        "varying vec2 vTexCoord;\n" +
        "void main() {\n" +
        "  gl_Position = vPosition;\n" +
        "  vTexCoord = aTexCoord;\n" +
        "}\n";
    private final static String FRAGMENT_SHADER =
        "precision lowp float;\n" +
        "varying vec2 vTexCoord;\n" +
        "uniform sampler2D sTexture;\n" +
        "uniform vec2 iResolution;\n" +
        "uniform vec2 c;\n" +
        "uniform float r;\n" +
        "uniform float t;\n" +
        "void main() {\n" +
        "   vec2 U = vTexCoord * iResolution.xy;" +
        "   float maxd = .35 * max(\n" +
        "       max(length(c - vec2(0., 0.)), length(c - vec2(iResolution.x, 0.))),\n" +
        "       max(length(c - vec2(0., iResolution.y)), length(c - iResolution))\n" +
        "   );" +
        "   float len = 250.;\n" +
        "   float amplitude = len / 2. * (1. - t);" +
        "   float R = mix(r - len, maxd + len, t);\n" +
        "   float d = (length(U - c) - R) / len;\n" +
        "   if (d > -1. && d < 1. && length(U - c) > r) {\n" +
        "       vec2 dir = normalize(c - U);\n" +
        "       vec2 uv = vTexCoord + dir * d * pow(1. - abs(d), 1.5) * amplitude / iResolution.xy;\n" +
        "       if (length(uv * iResolution - c) > r) {\n" +
        "           gl_FragColor = texture2D(sTexture, uv);\n" +
        "       } else {\n" +
        "           gl_FragColor = vec4(0.);\n" +
        "       }\n" +
        "       gl_FragColor.a *= min(1., (1. - abs(d)) * 2.);\n" +
        "   } else {\n" +
        "       gl_FragColor = vec4(0.);\n" +
        "   }\n" +
        "}\n";

    private Bitmap screenshot;

    private final WindowManager.LayoutParams layoutParams;
    private final WindowManager windowManager;
    private RenderingThread renderThread;

    private final float[] vertices = {
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f
    };
    private final FloatBuffer vertexBuffer;

    private final float[] uv = {
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    };
    private final FloatBuffer uvBuffer;

    private float cx, cy, r;

    public StoryWaveEffectView(Context context, float cx, float cy, float r) {
        super(context);

        this.cx = cx;
        this.cy = cy;
        this.r = r;

        setSurfaceTextureListener(this);

        vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices);
        vertexBuffer.position(0);

        uvBuffer = ByteBuffer.allocateDirect(uv.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(uv);
        uvBuffer.position(0);

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.LAST_APPLICATION_WINDOW,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        layoutParams.flags |=
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION |
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;

        layoutParams.gravity = Gravity.FILL;
        layoutParams.x = 0;
        layoutParams.y = 0;

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public StoryWaveEffectView prepare() {
        screenshot = makeScreenshot();
        return this;
    }

    public StoryWaveEffectView start() {
        AndroidUtilities.setPreferredMaxRefreshRate(windowManager, this, layoutParams);
        windowManager.addView(this, layoutParams);
        return this;
    }

    private boolean madeTouchable;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!madeTouchable) {
            layoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            windowManager.updateViewLayout(this, layoutParams);
            madeTouchable = true;

            animate().alpha(0).setDuration(180).withEndAction(() -> {
                if (renderThread != null) {
                    renderThread.finish();
                }
            }).start();
        }
        return super.onTouchEvent(event);
    }

    private Bitmap makeScreenshot() {
        List<View> views = AndroidUtilities.allGlobalViews();
        if (views == null) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(AndroidUtilities.displaySize.x, AndroidUtilities.statusBarHeight + AndroidUtilities.displaySize.y + AndroidUtilities.navigationBarHeight, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);
        Paint navBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        navBarPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        canvas.drawRect(0, bitmap.getHeight() - AndroidUtilities.navigationBarHeight, bitmap.getWidth(), bitmap.getHeight(), navBarPaint);
        for (int i = 0; i < views.size(); ++i) {
            View view = views.get(i);
            if (view != null && !(view instanceof StoryWaveEffectView) && !(view instanceof StoryRecorder.WindowView)) {
                canvas.save();
                canvas.translate(view.getX(), view.getY());
                view.draw(canvas);
                canvas.restore();
            }
        }
        return bitmap;
    }

    private EGLContext createContext(EGL10 egl, EGLDisplay eglDisplay, EGLConfig eglConfig) {
        int[] attrib_list = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
        return egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (renderThread == null) {
            renderThread = new RenderingThread(surface, screenshot);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        if (renderThread != null) {
            renderThread.finish();
            renderThread = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

    }


    private class RenderingThread extends Thread {

        private volatile boolean running;

        private SurfaceTexture surfaceTexture;
        private EGL10 egl;
        private EGLDisplay eglDisplay;
        private EGLContext eglContext;
        private EGLSurface eglSurface;

        private boolean inited;
        private int program;
        private int sTexture;
        private int vPosition, aTexCoord;
        private int iC, iTime, iR, iResolution;

        private Bitmap bitmap;
        private int[] textureId = new int[1];

        public RenderingThread(SurfaceTexture surfaceTexture, Bitmap bitmap) {
            super("StoryWaveEffectView.RenderingThread");
            this.surfaceTexture = surfaceTexture;
            this.bitmap = bitmap;
            start();
        }

        @Override
        public void run() {
            running = true;
            eglSetup(surfaceTexture);
            final long maxdt = (long) (1000L / Math.max(30, AndroidUtilities.screenRefreshRate));
            start = System.currentTimeMillis();
            while (running) {
                long start = System.currentTimeMillis();
                drawFrame();
                long dt = System.currentTimeMillis() - start;
                if (dt < maxdt - 1) {
                    try {
                        Thread.sleep(maxdt - 1 - dt);
                    } catch (Exception ignore) {}
                }
            }
            cleanup();
        }

        private void eglSetup(SurfaceTexture surface) {
            egl = (EGL10) EGLContext.getEGL();
            eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

            int[] version = new int[2];
            egl.eglInitialize(eglDisplay, version);

            EGLConfig eglConfig = chooseEglConfig();
            eglContext = createContext(egl, eglDisplay, eglConfig);

            eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null);

            if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                throw new RuntimeException("GL Error: " + egl.eglGetError());
            }

            if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw new RuntimeException("GL Make current error: " + egl.eglGetError());
            }

            int[] compiled = new int[1];
            int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
            GLES20.glShaderSource(vertexShader, VERTEX_SHADER);
            GLES20.glCompileShader(vertexShader);
            GLES20.glGetShaderiv(vertexShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                final String log = GLES20.glGetShaderInfoLog(vertexShader);
                GLES20.glDeleteShader(vertexShader);
                throw new RuntimeException("Shader Compile Error: " + log);
            }

            int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
            GLES20.glShaderSource(fragmentShader, FRAGMENT_SHADER);
            GLES20.glCompileShader(fragmentShader);
            GLES20.glGetShaderiv(fragmentShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                final String log = GLES20.glGetShaderInfoLog(fragmentShader);
                GLES20.glDeleteShader(fragmentShader);
                throw new RuntimeException("Shader Compile Error: " + log);
            }

            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);

            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                final String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                throw new RuntimeException("Program Link Error: " + log);
            }

            vPosition = GLES20.glGetAttribLocation(program, "vPosition");
            aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord");
            iC = GLES20.glGetUniformLocation(program, "c");
            iR = GLES20.glGetUniformLocation(program, "r");
            iTime = GLES20.glGetUniformLocation(program, "t");
            iResolution = GLES20.glGetUniformLocation(program, "iResolution");
            sTexture = GLES20.glGetUniformLocation(program, "sTexture");

            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);

            if (bitmap != null) {
                GLES20.glGenTextures(1, textureId, 0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
                bitmap.recycle();
                bitmap = null;
            }

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glDisable(GLES20.GL_DITHER);
            GLES20.glDisable(GLES20.GL_STENCIL_TEST);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);

            inited = true;
        }

        private EGLConfig chooseEglConfig() {
            int[] configsCount = new int[]{0};
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = {
                    EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };

            egl.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount);
            return configs[0];
        }

        final int[] array = new int[1];

        private long start;
        private final long duration = 800;
        private final CubicBezierInterpolator interpolator = CubicBezierInterpolator.EASE_OUT;

        private void drawFrame() {
            if (!inited) {
                return;
            }

            egl.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_WIDTH, array);
            int drawnWidth = array[0];
            egl.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_HEIGHT, array);
            int drawnHeight = array[0];

            GLES20.glViewport(0, 0, drawnWidth, drawnHeight);

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            GLES20.glUseProgram(program);

            GLES20.glUniform2f(iResolution, drawnWidth, drawnHeight);
            GLES20.glUniform2f(iC, cx, cy);
            GLES20.glUniform1f(iR, r);
            float tValue = (System.currentTimeMillis() - start) / (float) duration;
            float tInterpolated = Math.min(1, interpolator.getInterpolation(Math.min(1, tValue)));
            GLES20.glUniform1f(iTime, tInterpolated);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
            GLES20.glUniform1i(sTexture, 0);

            GLES20.glEnableVertexAttribArray(vPosition);
            GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);
            GLES20.glEnableVertexAttribArray(aTexCoord);
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 8, uvBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(vPosition);

            egl.eglSwapBuffers(eglDisplay, eglSurface);

            if (tValue >= 1 && running) {
                running = false;
            }
        };

        public void finish() {
            running = false;
        }

        private void cleanup() {
            running = false;

            egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            egl.eglDestroySurface(eglDisplay, eglSurface);
            egl.eglDestroyContext(eglDisplay, eglContext);
            egl.eglTerminate(eglDisplay);

            GLES20.glDeleteProgram(program);

            AndroidUtilities.runOnUIThread(() -> {
                WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
                windowManager.removeView(StoryWaveEffectView.this);
            });
        }
    }
}
