package org.telegram.ui.Components.Paint;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.Size;

import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class RenderView extends TextureView {

    public interface RenderViewDelegate {
        void onBeganDrawing();
        void onFinishedDrawing(boolean moved);
        void onFirstDraw();
        boolean shouldDraw();
        default void invalidateInputView() {}
        void resetBrush();
    }

    private RenderViewDelegate delegate;
    private UndoStore undoStore;
    private DispatchQueue queue;

    private Painting painting;
    private CanvasInternal internal;
    private Input input;
    private ShapeInput shapeInput;
    private Bitmap bitmap;
    private Bitmap blurBitmap;
    private boolean transformedBitmap;

    private boolean firstDrawSent;

    private float weight;
    private int color;
    private Brush brush;

    private boolean shuttingDown;

    public RenderView(Context context, Painting paint, Bitmap bitmap, Bitmap blurBitmap, BlurringShader.BlurManager blurManager) {
        super(context);
        setOpaque(false);

        this.bitmap = bitmap;
        this.blurBitmap = blurBitmap;
        painting = paint;
        painting.setRenderView(this);

        setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (surface == null || internal != null) {
                    return;
                }
                internal = new CanvasInternal(surface, blurManager);
                internal.setBufferSize(width, height);
                updateTransform();

                post(() -> {
                    if (internal != null) {
                        internal.requestRender();
                    }
                });

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
        shapeInput = new ShapeInput(this, () -> {
            if (delegate != null) {
                delegate.invalidateInputView();
            }
        });
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
        if (brush instanceof Brush.Shape) {
            shapeInput.process(event, getScaleX());
        } else {
            input.process(event, getScaleX());
        }
        return true;
    }

    public void onDrawForInput(Canvas canvas) {
        if (brush instanceof Brush.Shape) {
            shapeInput.dispatchDraw(canvas);
        }
    }

    public void onFillShapesToggle(Canvas canvas) {

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

    public float brushWeightForSize(float size) {
        float paintingWidth = painting.getSize().width;
        return 8.0f / 2048.0f * paintingWidth + (90.0f / 2048.0f * paintingWidth) * size;
    }

    public int getCurrentColor() {
        return color;
    }

    public void setColor(int value) {
        color = value;
        if (brush instanceof Brush.Shape) {
            shapeInput.onColorChange();
        }
    }

    public float getCurrentWeight() {
        return weight;
    }

    public void setBrushSize(float size) {
        weight = brushWeightForSize(size);
        if (brush instanceof Brush.Shape) {
            shapeInput.onWeightChange();
        }
    }

    public Brush getCurrentBrush() {
        return brush;
    }

    public UndoStore getUndoStore() {
        return undoStore;
    }

    public void setBrush(Brush value) {
        if (brush instanceof Brush.Shape) {
            shapeInput.stop();
        }
        brush = value;
        updateTransform();
        painting.setBrush(brush);
        if (brush instanceof Brush.Shape) {
            shapeInput.start(((Brush.Shape) brush).getShapeShaderType());
        }
    }

    public void resetBrush() {
        if (delegate != null) {
            delegate.resetBrush();
        }
        input.ignoreOnce();
    }

    public void clearShape() {
        if (shapeInput != null) {
            shapeInput.clear();
        }
    }

    private void updateTransform() {
        if (internal == null) {
            return;
        }
        Matrix matrix = new Matrix();

        float scale = painting != null ? getWidth() / painting.getSize().width : 1.0f;
        if (scale <= 0) {
            scale = 1.0f;
        }

        Size paintingSize = getPainting().getSize();

        matrix.preTranslate(getWidth() / 2.0f, getHeight() / 2.0f);
        matrix.preScale(scale, -scale);
        matrix.preTranslate(-paintingSize.width / 2.0f, -paintingSize.height / 2.0f);

        if (brush instanceof Brush.Shape) {
            shapeInput.setMatrix(matrix);
        } else {
            input.setMatrix(matrix);
        }

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

    public void clearAll() {
        input.clear(() -> painting.setBrush(brush));
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
        private volatile boolean ready;

        private int bufferWidth;
        private int bufferHeight;

        private long lastRenderCallTime;
        private Runnable scheduledRunnable;

        private final BlurringShader.BlurManager blurManager;

        public CanvasInternal(SurfaceTexture surface, BlurringShader.BlurManager blurManager) {
            super("CanvasInternal");
            this.blurManager = blurManager;
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
            EGLContext parentContext = blurManager != null ? blurManager.getParentContext() : EGL10.EGL_NO_CONTEXT;
            eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, parentContext, attrib_list);
            if (eglContext == null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            if (blurManager != null) {
                blurManager.acquiredContext(eglContext);
                blurManager.attach(safeRequestRender);
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
            painting.setBitmap(bitmap, blurBitmap);

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
            if (blurBitmap != null && (blurBitmap.getWidth() != paintingSize.width || blurBitmap.getHeight() != paintingSize.height)) {
                Bitmap b = Bitmap.createBitmap((int) paintingSize.width, (int) paintingSize.height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(b);
                canvas.drawBitmap(blurBitmap, null, new RectF(0, 0, paintingSize.width, paintingSize.height), null);
                blurBitmap = b;
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
                    ready = true;
                }
            }
        };

        public void setBufferSize(int width, int height) {
            bufferWidth = width;
            bufferHeight = height;
        }

        public void requestRender() {
            postRunnable(drawRunnable);
        }

        public Runnable safeRequestRender = () -> {
            if (scheduledRunnable != null) {
                cancelRunnable(scheduledRunnable);
                scheduledRunnable = null;
            }
            cancelRunnable(drawRunnable);
            postRunnable(drawRunnable);
        };

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
                if (blurManager != null) {
                    blurManager.destroyedContext(eglContext);
                }
                egl10.eglDestroyContext(eglDisplay, eglContext);
                eglContext = null;
            }
            if (eglDisplay != null) {
                egl10.eglTerminate(eglDisplay);
                eglDisplay = null;
            }
            if (blurManager != null) {
                blurManager.detach(safeRequestRender);
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
            return getTexture(false, false);
        }

        public Bitmap getTexture(final boolean onlyBlur, final boolean includeBlur) {
            if (!initialized) {
                return null;
            }
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final Bitmap[] object = new Bitmap[1];
            try {
                postRunnable(() -> {
                    Painting.PaintingData data = painting.getPaintingData(new RectF(0, 0, painting.getSize().width, painting.getSize().height), false, onlyBlur, includeBlur);
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

    public Bitmap getResultBitmap(boolean blurTex, boolean includeBlur) {
        if (brush instanceof Brush.Shape) {
            shapeInput.stop();
        }
        return internal != null ? internal.getTexture(blurTex, includeBlur) : null;
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

    protected void selectBrush(Brush brush) {}
}
