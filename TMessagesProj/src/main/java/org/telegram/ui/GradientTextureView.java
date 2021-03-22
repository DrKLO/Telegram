package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.view.TextureView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.Paint.FragmentShader;
import org.webrtc.EglBase;

import java.util.concurrent.Semaphore;

public class GradientTextureView extends TextureView implements TextureView.SurfaceTextureListener {

    private final GradientDrawer drawer = new GradientDrawer(getContext());
    private final RenderThread renderThread = new RenderThread(drawer, false);

    public GradientTextureView(@NonNull Context context) {
        super(context);
        drawer.setColor(0, Color.RED);
        drawer.setColor(1, Color.GREEN);
        drawer.setColor(2, Color.BLUE);
        drawer.setColor(3, Color.YELLOW);

        drawer.setPosition(0, 0.0f, 0.0f);
        drawer.setPosition(1, 0.8f, 0.3f);
        drawer.setPosition(2, 0.8f, 0.8f);
        drawer.setPosition(3, 0.2f, 0.8f);

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

        private static final int COLOR_SIZE = 3;
        private static final int POINT_SIZE = 2;

        private final String fragmentShaderSource;
        private FragmentShader shader;

        private float width;
        private float height;
        private float[] colors = new float[COLOR_SIZE * 4];
        private float[] points = new float[POINT_SIZE * 4];

        private int locResolution = -1;
        private int locColor1 = -1;
        private int locColor2 = -1;
        private int locColor3 = -1;
        private int locColor4 = -1;
        private int locPoint1 = -1;
        private int locPoint2 = -1;
        private int locPoint3 = -1;
        private int locPoint4 = -1;

        GradientDrawer(Context context) {
            fragmentShaderSource = AndroidUtilities.readTextFromAsset(context, "shaders/gradient_background.frag", true);
        }

        @Override
        public void init(int width, int height) {
            this.width = width;
            this.height = height;

            shader = new FragmentShader(fragmentShaderSource);
            int program = shader.getProgram();
            locResolution = GLES20.glGetUniformLocation(program, "u_resolution");

            locColor1 = GLES20.glGetUniformLocation(program, "u_color1");
            locColor2 = GLES20.glGetUniformLocation(program, "u_color2");
            locColor3 = GLES20.glGetUniformLocation(program, "u_color3");
            locColor4 = GLES20.glGetUniformLocation(program, "u_color4");

            locPoint1 = GLES20.glGetUniformLocation(program, "u_point1");
            locPoint2 = GLES20.glGetUniformLocation(program, "u_point2");
            locPoint3 = GLES20.glGetUniformLocation(program, "u_point3");
            locPoint4 = GLES20.glGetUniformLocation(program, "u_point4");
        }

        @Override
        public void draw() {
            if (shader == null) {
                return;
            }

            GLES20.glUseProgram(shader.getProgram());
            GLES20.glUniform2f(locResolution, width, height);

            GLES20.glUniform3fv(locColor1, 1, colors, 0);
            GLES20.glUniform3fv(locColor2, 1, colors, COLOR_SIZE);
            GLES20.glUniform3fv(locColor3, 1, colors, COLOR_SIZE * 2);
            GLES20.glUniform3fv(locColor4, 1, colors, COLOR_SIZE * 3);

            GLES20.glUniform2fv(locPoint1, 1, points, 0);
            GLES20.glUniform2fv(locPoint2, 1, points, POINT_SIZE);
            GLES20.glUniform2fv(locPoint3, 1, points, POINT_SIZE * 2);
            GLES20.glUniform2fv(locPoint4, 1, points, POINT_SIZE * 3);

            shader.draw();
        }

        @Override
        public void release() {
            if (shader != null) {
                shader.cleanResources();
            }
        }

        void setColor(int idx, @ColorInt int color) {
            colors[idx * 3] = Color.red(color) / 255f;
            colors[idx * 3 + 1] = Color.green(color) / 255f;
            colors[idx * 3 + 2] = Color.blue(color) / 255f;
        }

        void setPosition(int idx, float x, float y) {
            points[idx * 2] = x;
            points[idx * 2 + 1] = y;
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
                drawer.init(eglBase.surfaceWidth(), eglBase.surfaceHeight());
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

            void init(int width, int height);
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

