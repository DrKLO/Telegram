package org.telegram.ui;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.Paint.FragmentShader;
import org.telegram.ui.Components.Paint.Shader;
import org.webrtc.EglBase;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Semaphore;

public class GradientTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    private final GradientDrawer drawer = new GradientDrawer(getContext());
    private final RenderThread renderThread = new RenderThread(drawer, false);

    public GradientTextureView(@NonNull Context context) {
        super(context);
        setSurfaceTextureListener(this);
        renderThread.start();
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        renderThread.setSurfaceTexture(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) { }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) { }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        renderThread.setSurfaceTexture(null);
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        setSurfaceTextureListener(null);
        super.onDetachedFromWindow();
    }


    private static class GradientDrawer implements RenderThread.Drawer {

        private final String fragmentShaderSource;
        private FragmentShader shader;

        GradientDrawer(Context context) {
            fragmentShaderSource = AndroidUtilities.readTextFromAsset(context, "shaders/gradient_background.frag");
        }

        @Override
        public void init() {
            shader = new FragmentShader(fragmentShaderSource);
        }

        @Override
        public void draw() {
            shader.draw();
        }

        @Override
        public void release() {
            if (shader != null) {
                shader.cleanResources();
            }
        }
    }

    // TODO agolokoz: use HandlerThread
    private static class RenderThread extends Thread {

        private final Object surfaceTextureLock = new Object();
        private final Semaphore redrawSemaphore = new Semaphore(1);

        @NonNull
        private final Drawer drawer;
        private final boolean isAlwaysInvalidate;

        @Nullable
        private SurfaceTexture surfaceTexture;

        public RenderThread(@NonNull Drawer drawer, boolean isAlwaysInvalidate) {
            super("GradientTextureView.RenderThread");
            this.drawer = drawer;
            this.isAlwaysInvalidate = isAlwaysInvalidate;
        }

        @Override
        public void run() {
            while (true) {
                SurfaceTexture texture;

                // wait for texture
                synchronized (surfaceTextureLock) {
                    texture = surfaceTexture;
                    while (!isInterrupted() && texture == null) {
                        try {
                            surfaceTextureLock.wait();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        texture = surfaceTexture;
                    }
                    if (isInterrupted()) {
                        break;
                    }
                }

                // prepare
                EglBase eglBase = EglBase.create();
                WindowEglSurface windowSurface = new WindowEglSurface(eglBase, surfaceTexture);
                windowSurface.makeCurrent();

                // draw
                drawer.init();
                drawLoop(windowSurface);

                // release
                windowSurface.releaseEglSurface();
                eglBase.release();
            }
        }

        void setSurfaceTexture(@Nullable SurfaceTexture surfaceTexture) {
            synchronized (surfaceTextureLock) {
                this.surfaceTexture = surfaceTexture;
                surfaceTextureLock.notify();
            }
        }

        void invalidate() {
            redrawSemaphore.release();
        }

        private void drawLoop(WindowEglSurface surface) {
            while (true) {
                synchronized (surfaceTextureLock) {
                    SurfaceTexture texture = surfaceTexture;
                    if (texture == null) {
                        return;
                    }
                }

                if (!isAlwaysInvalidate) {
                    try {
                        redrawSemaphore.acquire();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                drawer.draw();

                surface.swapBuffers();
            }
        }

        interface Drawer {

            void init();
            void draw();
            void release();
        }
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

