package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Choreographer;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.webrtc.EglBase;

@SuppressLint("ViewConstructor")
public class GLTextureView extends TextureView implements TextureView.SurfaceTextureListener,
        Choreographer.FrameCallback {

    @Nullable
    private Drawer drawer;

    @Nullable
    private RenderThread renderThread;

    // TODO agolokoz: optimize to use false
    private boolean isAlwaysInvalidate = true;
    private boolean isDestroyThreadOnSurfaceDestroyed = false;
    private int width;
    private int height;

    public GLTextureView(@NonNull Context context) {
        super(context);
        setSurfaceTextureListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (drawer != null) {
            renderThread = new RenderThread(drawer);
            renderThread.start();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        this.width = width;
        this.height = height;
        RenderThread thread = renderThread;
        if (thread != null) {
            thread.dispatchSetSurfaceTexture(surface);
        }
        if (isAlwaysInvalidate) {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        if (this.width == width && this.height == height) {
            return;
        }
        this.width = width;
        this.height = height;
        RenderThread thread = renderThread;
        if (thread != null) {
            thread.dispatchSetSurfaceTexture(surface);
        }
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) { }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        Choreographer.getInstance().removeFrameCallback(this);
        if (isDestroyThreadOnSurfaceDestroyed) {
            destroyThread();
        }
        return true;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        RenderThread thread = renderThread;
        if (thread != null) {
            thread.dispatchInvalidate();
        }
        if (isAlwaysInvalidate) {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    public void onDestroy() {
        destroyThread();
    }

    public void setDrawer(@Nullable Drawer drawer) {
        this.drawer = drawer;
    }

    public void setAlwaysInvalidate(boolean alwaysInvalidate) {
        isAlwaysInvalidate = alwaysInvalidate;
        if (isAlwaysInvalidate) {
            Choreographer.getInstance().postFrameCallback(this);
        } else {
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }

    private void destroyThread() {
        if (renderThread != null) {
            renderThread.dispatchRelease();
            renderThread.interrupt();
            renderThread = null;
        }
    }

    private static class RenderThread extends HandlerThread implements Handler.Callback {

        private static final int MSG_WHAT_SET_SURFACE = 1;
        private static final int MSG_WHAT_INVALIDATE = 2;
        private static final int MSG_WHAT_RELEASE = 3;

        @NonNull
        private final Handler handler = new Handler(Looper.myLooper(), this);
        @Nullable
        private Drawer drawer;

        private EglBase eglBase;
        private WindowEglSurface windowSurface;

        public RenderThread(@NonNull Drawer drawer) {
            super("GradientTextureView.RenderThread");
            this.drawer = drawer;
        }

        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_WHAT_SET_SURFACE:
                    if (msg.obj instanceof SurfaceTexture) {
                        prepare((SurfaceTexture) msg.obj);
                    }
                    break;
                case MSG_WHAT_INVALIDATE:
                    draw();
                    break;
                case MSG_WHAT_RELEASE:
                    release();
                    break;
                default:
                    return false;
            }
            return true;
        }

        void dispatchSetSurfaceTexture(@Nullable SurfaceTexture surfaceTexture) {
            Message msg = handler.obtainMessage(MSG_WHAT_SET_SURFACE, surfaceTexture);
            handler.dispatchMessage(msg);
        }

        void dispatchInvalidate() {
            Message msg = handler.obtainMessage(MSG_WHAT_INVALIDATE);
            handler.dispatchMessage(msg);
        }

        void dispatchRelease() {
            Message msg = handler.obtainMessage(MSG_WHAT_RELEASE);
            handler.dispatchMessage(msg);
        }

        private void prepare(@Nullable SurfaceTexture surfaceTexture) {
            boolean isRecreate = eglBase != null || windowSurface != null;
            if (isRecreate) {
                release();
            }
            eglBase = EglBase.create();
            windowSurface = new WindowEglSurface(eglBase, surfaceTexture);
            windowSurface.makeCurrent();
            if (drawer != null) {
                drawer.init(eglBase.surfaceWidth(), eglBase.surfaceHeight());
            }
        }

        private void draw() {
            if (windowSurface == null || drawer == null) {
                return;
            }
            drawer.draw();
            windowSurface.swapBuffers();
        }

        private void release() {
            if (drawer != null) {
                drawer.release();
                drawer = null;
            }
            if (windowSurface != null) {
                windowSurface.releaseEglSurface();
                windowSurface = null;
            }
            if (eglBase != null) {
                eglBase.release();
                eglBase = null;
            }
        }
    }

    public interface Drawer {

        void init(int width, int height);
        void setSize(int width, int height);
        void draw();
        void release();
    }

    static class WindowEglSurface {

        private final EglBase eglBase;

        WindowEglSurface(EglBase eglBase, SurfaceTexture surfaceTexture) {
            this.eglBase = eglBase;
            if (eglBase.hasSurface()) {
                throw new IllegalStateException("surface already created");
            }
            eglBase.createSurface(surfaceTexture);
        }

        void releaseEglSurface() {
            eglBase.releaseSurface();
        }

        void makeCurrent() {
            eglBase.makeCurrent();
        }

        void swapBuffers() {
            eglBase.swapBuffers();
        }
    }
}

