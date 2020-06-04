package org.telegram.ui.Components.Paint;

import android.content.Context;
import android.graphics.*;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.Size;

import java.util.concurrent.CountDownLatch;

public class RenderView extends TextureView {

    public interface RenderViewDelegate {
        void onBeganDrawing();
        void onFinishedDrawing(boolean moved);
        void onFirstDraw();
        boolean shouldDraw();
    }

    private RenderViewDelegate delegate;
    private UndoStore undoStore;
    private DispatchQueue queue;

    private Painting painting;
    private CanvasInternal internal;
    private Input input;
    private Bitmap bitmap;
    private boolean transformedBitmap;

    private boolean firstDrawSent;

    private float weight;
    private int color;
    private Brush brush;

    private boolean shuttingDown;

    public RenderView(Context context, Painting paint, Bitmap b) {
        super(context);
        setOpaque(false);

        bitmap = b;
        painting = paint;
        painting.setRenderView(this);

        setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (surface == null || internal != null) {
                    return;
                }

                internal = new CanvasInternal(surface);
                internal.setBufferSize(width, height);
                updateTransform();

                internal.requestRender();

                if (painting.isPaused()) {
                    painting.onResume();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                if (internal == null) {
                    return;
                }

                internal.setBufferSize(width, height);
                updateTransform();
                internal.requestRender();
                internal.postRunnable(() -> {
                    if (internal != null) {
                        internal.requestRender();
                    }
                });
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (internal == null) {
                    return true;
                }
                if (!shuttingDown) {
                    painting.onPause(() -> {
                        internal.shutdown();
                        internal = null;
                    });
                }

                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        input = new Input(this);
        painting.setDelegate(new Painting.PaintingDelegate() {
            @Override
            public void contentChanged() {
                if (internal != null) {
                    internal.scheduleRedraw();
                }
            }

            @Override
            public void strokeCommited() {

            }

            @Override
            public UndoStore requestUndoStore() {
                return undoStore;
            }

            @Override
            public DispatchQueue requestDispatchQueue() {
                return queue;
            }
        });
    }

    public void redraw() {
        if (internal == null) {
            return;
        }
        internal.requestRender();
    }

    public boolean onTouch(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            return false;
        }
        if (internal == null || !internal.initialized || !internal.ready) {
            return true;
        }
        input.process(event, getScaleX());
        return true;
    }

    public void setUndoStore(UndoStore store) {
        undoStore = store;
    }

    public void setQueue(DispatchQueue dispatchQueue) {
        queue = dispatchQueue;
    }

    public void setDelegate(RenderViewDelegate renderViewDelegate) {
        delegate = renderViewDelegate;
    }

    public Painting getPainting() {
        return painting;
    }

    private float brushWeightForSize(float size) {
        float paintingWidth = painting.getSize().width;
        return 8.0f / 2048.0f * paintingWidth + (90.0f / 2048.0f * paintingWidth) * size;
    }

    public int getCurrentColor() {
        return color;
    }

    public void setColor(int value) {
        color = value;
    }

    public float getCurrentWeight() {
        return weight;
    }

    public void setBrushSize(float size) {
        weight = brushWeightForSize(size);
    }

    public Brush getCurrentBrush() {
        return brush;
    }

    public void setBrush(Brush value) {
        painting.setBrush(brush = value);
    }

    private void updateTransform() {
        Matrix matrix = new Matrix();

        float scale = painting != null ? getWidth() / painting.getSize().width : 1.0f;
        if (scale <= 0) {
            scale = 1.0f;
        }

        Size paintingSize = getPainting().getSize();

        matrix.preTranslate(getWidth() / 2.0f, getHeight() / 2.0f);
        matrix.preScale(scale, -scale);
        matrix.preTranslate(-paintingSize.width / 2.0f, -paintingSize.height / 2.0f);

        input.setMatrix(matrix);

        float[] proj = GLMatrix.LoadOrtho(0.0f, internal.bufferWidth, 0.0f, internal.bufferHeight, -1.0f, 1.0f);
        float[] effectiveProjection = GLMatrix.LoadGraphicsMatrix(matrix);
        float[] finalProjection = GLMatrix.MultiplyMat4f(proj, effectiveProjection);
        painting.setRenderProjection(finalProjection);
    }

    public boolean shouldDraw() {
        return delegate == null || delegate.shouldDraw();
    }

    public void onBeganDrawing() {
        if (delegate != null) {
            delegate.onBeganDrawing();
        }
    }

    public void onFinishedDrawing(boolean moved) {
        if (delegate != null) {
            delegate.onFinishedDrawing(moved);
        }
    }

    public void shutdown() {
        shuttingDown = true;

        if (internal != null) {
            performInContext(() -> {
                painting.cleanResources(transformedBitmap);
                internal.shutdown();
                internal = null;
            });
        }

        setVisibility(View.GONE);
    }

    private class CanvasInternal extends DispatchQueue {
        private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private static final int EGL_OPENGL_ES2_BIT = 4;
        private SurfaceTexture surfaceTexture;
        private EGL10 egl10;
        private EGLDisplay eglDisplay;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private boolean initialized;
        private boolean ready;

        private int bufferWidth;
        private int bufferHeight;

        private long lastRenderCallTime;
        private Runnable scheduledRunnable;

        public CanvasInternal(SurfaceTexture surface) {
            super("CanvasInternal");
            surfaceTexture = surface;
        }

        @Override
        public void run() {
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }

            initialized = initGL();
            super.run();
        }

        private boolean initGL() {
            egl10 = (EGL10) EGLContext.getEGL();

            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] version = new int[2];
            if (!egl10.eglInitialize(eglDisplay, version)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = new int[]{
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };
            EGLConfig eglConfig;
            if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            } else if (configsCount[0] > 0) {
                eglConfig = configs[0];
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglConfig not initialized");
                }
                finish();
                return false;
            }

            int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
            eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
            if (eglContext == null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            if (surfaceTexture instanceof SurfaceTexture) {
                eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
            } else {
                finish();
                return false;
            }

            if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glDisable(GLES20.GL_DITHER);
            GLES20.glDisable(GLES20.GL_STENCIL_TEST);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);

            painting.setupShaders();
            checkBitmap();
            painting.setBitmap(bitmap);

            Utils.HasGLError();

            return true;
        }

        private Bitmap createBitmap(Bitmap bitmap, float scale) {
            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        private void checkBitmap() {
            Size paintingSize = painting.getSize();
            if (bitmap.getWidth() != paintingSize.width || bitmap.getHeight() != paintingSize.height) {
                Bitmap b = Bitmap.createBitmap((int) paintingSize.width, (int) paintingSize.height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(b);
                canvas.drawBitmap(bitmap, null, new RectF(0, 0, paintingSize.width, paintingSize.height), null);
                bitmap = b;
                transformedBitmap = true;
            }
        }

        private boolean setCurrentContext() {
            if (!initialized) {
                return false;
            }

            if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
                if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    return false;
                }
            }
            return true;
        }

        private Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                if (!initialized || shuttingDown) {
                    return;
                }

                setCurrentContext();

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                GLES20.glViewport(0, 0, bufferWidth, bufferHeight);

                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                painting.render();

                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

                egl10.eglSwapBuffers(eglDisplay, eglSurface);
                if (!firstDrawSent) {
                    firstDrawSent = true;
                    AndroidUtilities.runOnUIThread(() -> delegate.onFirstDraw());
                }

                if (!ready) {
                    queue.postRunnable(() -> ready = true, 200);
                }
            }
        };

        public void setBufferSize(int width, int height) {
            bufferWidth = width;
            bufferHeight = height;
        }

        public void requestRender() {
            postRunnable(() -> drawRunnable.run());
        }

        public void scheduleRedraw() {
            if (scheduledRunnable != null) {
                cancelRunnable(scheduledRunnable);
                scheduledRunnable = null;
            }

            scheduledRunnable = () -> {
                scheduledRunnable = null;
                drawRunnable.run();
            };

            postRunnable(scheduledRunnable, 1);
        }

        public void finish() {
            if (eglSurface != null) {
                egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                egl10.eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = null;
            }
            if (eglContext != null) {
                egl10.eglDestroyContext(eglDisplay, eglContext);
                eglContext = null;
            }
            if (eglDisplay != null) {
                egl10.eglTerminate(eglDisplay);
                eglDisplay = null;
            }
        }

        public void shutdown() {
            postRunnable(() -> {
                finish();
                Looper looper = Looper.myLooper();
                if (looper != null) {
                    looper.quit();
                }
            });
        }

        public Bitmap getTexture() {
            if (!initialized) {
                return null;
            }
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final Bitmap[] object = new Bitmap[1];
            try {
                postRunnable(() -> {
                    Painting.PaintingData data = painting.getPaintingData(new RectF(0, 0, painting.getSize().width, painting.getSize().height), false);
                    if (data != null) {
                        object[0] = data.bitmap;
                    }
                    countDownLatch.countDown();
                });
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
            return object[0];
        }
    }

    public Bitmap getResultBitmap() {
        return internal != null ? internal.getTexture() : null;
    }

    public void performInContext(final Runnable action) {
        if (internal == null) {
            return;
        }

        internal.postRunnable(() -> {
            if (internal == null || !internal.initialized) {
                return;
            }

            internal.setCurrentContext();
            action.run();
        });
    }
}
