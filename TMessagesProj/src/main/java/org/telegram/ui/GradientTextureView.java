package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Choreographer;
import android.view.TextureView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.Paint.FragmentShader;
import org.webrtc.EglBase;

public class GradientTextureView extends TextureView implements
        TextureView.SurfaceTextureListener,
        Choreographer.FrameCallback {

    private final boolean isAlwaysInvalidate = true;
    private final GradientDrawer drawer = new GradientDrawer(getContext());

    @Nullable
    private RenderThread renderThread;

    public GradientTextureView(@NonNull Context context) {
        super(context);
        drawer.setColor(0, Color.RED);
        drawer.setColor(1, Color.GREEN);
        drawer.setColor(2, Color.BLUE);
        drawer.setColor(3, Color.YELLOW);

        drawer.setPosition(0, 0.0f, 0.0f);
        drawer.setPosition(1, 1.0f, 0.0f);
        drawer.setPosition(2, 1.0f, 1.0f);
        drawer.setPosition(3, 0.0f, 1.0f);

        setSurfaceTextureListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        renderThread = new RenderThread(drawer);
        renderThread.start();
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (isAlwaysInvalidate) {
            Choreographer.getInstance().postFrameCallback(this);
        }
        RenderThread thread = renderThread;
        if (thread != null) {
            thread.dispatchSetSurfaceTexture(surface);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        RenderThread thread = renderThread;
        if (thread != null) {
            thread.dispatchRelease();
            thread.dispatchSetSurfaceTexture(surface);
        }
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) { }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        Choreographer.getInstance().removeFrameCallback(this);
        if (renderThread != null) {
            renderThread.dispatchRelease();
            renderThread.interrupt();
            renderThread = null;
        }
        return true;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (renderThread != null) {
            renderThread.dispatchInvalidate();
        }
        if (isAlwaysInvalidate) {
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    private static class GradientDrawer implements RenderThread.Drawer {

        private static final int COLOR_SIZE = 3;
        private static final int POINT_SIZE = 2;

        private final String fragmentShaderSource;
        private FragmentShader shader;

        private final float[] colors = new float[COLOR_SIZE * 4];
        private final float[] points = new float[POINT_SIZE * 4];
        private float width;
        private float height;

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
                shader = null;
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

    private static class RenderThread extends HandlerThread implements Handler.Callback {

        private static final int MSG_WHAT_SET_SURFACE = 1;
        private static final int MSG_WHAT_INVALIDATE = 2;
        private static final int MSG_WHAT_RELEASE = 3;

        @NonNull
        private final Handler handler = new Handler(Looper.myLooper(), this);
        @NonNull
        private final Drawer drawer;

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
            eglBase = EglBase.create();
            windowSurface = new WindowEglSurface(eglBase, surfaceTexture);
            windowSurface.makeCurrent();
            drawer.init(eglBase.surfaceWidth(), eglBase.surfaceHeight());
        }

        private void draw() {
            if (windowSurface == null) {
                return;
            }
            drawer.draw();
            windowSurface.swapBuffers();
        }

        private void release() {
            drawer.release();
            windowSurface.releaseEglSurface();
            windowSurface = null;
            eglBase.release();
            eglBase = null;
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

