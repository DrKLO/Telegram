package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES20;
import android.opengl.GLES31;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class ThanosEffect extends TextureView {

    private static Boolean nothanos = null;
    public static boolean supports() {
        if (nothanos == null) {
            nothanos = MessagesController.getGlobalMainSettings().getBoolean("nothanos", false);
        }
        return (nothanos == null || !nothanos) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private DrawingThread drawThread;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (drawThread != null) {
                drawThread.requestDraw();
                if (drawThread.running) {
                    Choreographer.getInstance().postFrameCallback(this);
                }
            }
        }
    };

    private final ArrayList<ToSet> toSet = new ArrayList<>();
    private static class ToSet {
        public final View view;
        public final ArrayList<View> views;
        public final Runnable startCallback, doneCallback;

        public final Bitmap bitmap;
        public final Matrix matrix;
        public ToSet(View view, Runnable callback) {
            this.view = view;
            this.views = null;
            this.startCallback = null;
            this.doneCallback = callback;
            this.bitmap = null;
            this.matrix = null;
        }
        public ToSet(ArrayList<View> views, Runnable callback) {
            this.view = null;
            this.views = views;
            this.startCallback = null;
            this.doneCallback = callback;
            this.bitmap = null;
            this.matrix = null;
        }
        public ToSet(Matrix matrix, Bitmap bitmap, Runnable startCallback, Runnable doneCallback) {
            this.view = null;
            this.views = null;
            this.startCallback = startCallback;
            this.doneCallback = doneCallback;
            this.matrix = matrix;
            this.bitmap = bitmap;
        }
    }

    private Runnable whenDone;
    public ThanosEffect(@NonNull Context context, Runnable whenDoneCallback) {
        super(context);
        this.whenDone = whenDoneCallback;
        setOpaque(false);
        setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                if (drawThread != null) {
                    drawThread.kill();
                    drawThread = null;
                }
                drawThread = new DrawingThread(surface, ThanosEffect.this::invalidate, ThanosEffect.this::destroy, width, height);
                if (!toSet.isEmpty()) {
                    for (int i = 0; i < toSet.size(); ++i) {
                        ToSet toSetObj = toSet.get(i);
                        if (toSetObj.bitmap != null) {
                            drawThread.animate(toSetObj.matrix, toSetObj.bitmap, toSetObj.startCallback, toSetObj.doneCallback);
                        } else if (toSetObj.views != null) {
                            drawThread.animateGroup(toSetObj.views, toSetObj.doneCallback);
                        } else {
                            drawThread.animate(toSetObj.view, toSetObj.doneCallback);
                        }
                    }
                    toSet.clear();
                    Choreographer.getInstance().postFrameCallback(frameCallback);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                if (drawThread != null) {
                    drawThread.resize(width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                if (drawThread != null) {
                    drawThread.kill();
                    drawThread = null;
                }
                if (whenDone != null) {
                    Runnable runnable = whenDone;
                    whenDone = null;
                    runnable.run();
                }
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
    }

    private void destroy() {
        if (whenDone != null) {
            Runnable runnable = whenDone;
            whenDone = null;
            runnable.run();
        }
    }

    public void scroll(int dx, int dy) {
        if (drawThread != null && drawThread.running) {
//            post(() -> drawThread.scroll(dx, dy));
        }
    }

    public void animateGroup(ArrayList<View> views, Runnable whenDone) {
        if (drawThread != null) {
            drawThread.animateGroup(views, whenDone);
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } else {
            toSet.add(new ToSet(views, whenDone));
        }
    }

    public void animate(View view, Runnable whenDone) {
        if (drawThread != null) {
            drawThread.animate(view, whenDone);
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } else {
            toSet.add(new ToSet(view, whenDone));
        }
    }

    public void animate(Matrix matrix, Bitmap bitmap, Runnable whenStarted, Runnable whenDone) {
        if (drawThread != null) {
            drawThread.animate(matrix, bitmap, whenStarted, whenDone);
            Choreographer.getInstance().postFrameCallback(frameCallback);
        } else {
            toSet.add(new ToSet(matrix, bitmap, whenStarted, whenDone));
        }
    }

    private static class DrawingThread extends DispatchQueue {

        private boolean alive = true;
        private final SurfaceTexture surfaceTexture;
        private final Runnable invalidate;
        private Runnable destroy;
        private int width, height;

        public DrawingThread(SurfaceTexture surfaceTexture, Runnable invalidate, Runnable destroy, int width, int height) {
            super("ThanosEffect.DrawingThread", false);

            this.surfaceTexture = surfaceTexture;
            this.invalidate = invalidate;
            this.destroy = destroy;
            this.width = width;
            this.height = height;

            start();
        }

        public final static int DO_DRAW = 0;
        public final static int DO_RESIZE = 1;
        public final static int DO_KILL = 2;
        public final static int DO_ADD_ANIMATION = 3;
        public final static int DO_SCROLL = 4;

        @Override
        public void handleMessage(Message inputMessage) {
            switch (inputMessage.what) {
                case DO_DRAW: {
                    draw();
                    return;
                }
                case DO_RESIZE: {
                    resizeInternal(inputMessage.arg1, inputMessage.arg2);
                    draw();
                    return;
                }
                case DO_KILL: {
                    killInternal();
                    return;
                }
                case DO_ADD_ANIMATION: {
                    addAnimationInternal((Animation) inputMessage.obj);
                    return;
                }
                case DO_SCROLL: {
                    for (int i = 0; i < pendingAnimations.size(); ++i) {
                        Animation anim = pendingAnimations.get(i);
                        anim.offsetLeft += inputMessage.arg1;
                        anim.offsetTop += inputMessage.arg2;
                    }
                    return;
                }
            }
        }

        @Override
        public void run() {
            try {
                init();
            } catch (Exception e) {
                FileLog.e(e);
                for (int i = 0; i < toAddAnimations.size(); ++i) {
                    Animation animation = toAddAnimations.get(i);
                    if (animation.startCallback != null) {
                        AndroidUtilities.runOnUIThread(animation.startCallback);
                    }
                    animation.done(false);
                }
                toAddAnimations.clear();
                AndroidUtilities.runOnUIThread(() -> {
                    MessagesController.getGlobalMainSettings().edit().putBoolean("nothanos", nothanos = true).apply();
                });
                killInternal();
                return;
            }
            if (!toAddAnimations.isEmpty()) {
                for (int i = 0; i < toAddAnimations.size(); ++i) {
                    addAnimationInternal(toAddAnimations.get(i));
                }
                toAddAnimations.clear();
            }
            super.run();
        }

        public void requestDraw() {
            Handler handler = getHandler();
            if (handler != null) {
                handler.sendMessage(handler.obtainMessage(DO_DRAW));
            }
        }

        public void resize(int width, int height) {
            Handler handler = getHandler();
            if (handler != null) {
                handler.sendMessage(handler.obtainMessage(DO_RESIZE, width, height));
            }
        }

        public void scroll(int dx, int dy) {
            Handler handler = getHandler();
            if (handler != null) {
                handler.sendMessage(handler.obtainMessage(DO_SCROLL, dx, dy));
            }
        }

        private void resizeInternal(int width, int height) {
            if (!alive) return;
            this.width = width;
            this.height = height;
            GLES31.glViewport(0, 0, width, height);
            GLES31.glUniform2f(sizeHandle, width, height);
        }

        public void kill() {
            Handler handler = getHandler();
            if (handler != null) {
                handler.sendMessage(handler.obtainMessage(DO_KILL));
            }
        }

        private void killInternal() {
            if (!alive) return;
            alive = false;
            for (int i = 0; i < pendingAnimations.size(); ++i) {
                Animation animation = pendingAnimations.get(i);
                animation.done(false);
            }
            if (surfaceTexture != null) {
                surfaceTexture.release();
            }
            Looper looper = Looper.myLooper();
            if (looper != null) {
                looper.quit();
            }
            if (destroy != null) {
                AndroidUtilities.runOnUIThread(destroy);
                destroy = null;
            }
        }

        private EGL10 egl;
        private EGLDisplay eglDisplay;
        private EGLConfig eglConfig;
        private EGLSurface eglSurface;
        private EGLContext eglContext;

        private int drawProgram;

        private int matrixHandle;
        private int resetHandle;
        private int timeHandle;
        private int deltaTimeHandle;
        private int particlesCountHandle;
        private int sizeHandle;
        private int gridSizeHandle;
        private int rectSizeHandle;
        private int seedHandle;
        private int rectPosHandle;
        private int textureHandle;
        private int densityHandle;
        private int longevityHandle;
        private int offsetHandle;

        public volatile boolean running;
        private final ArrayList<Animation> pendingAnimations = new ArrayList<>();

        private void init() {
            egl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();

            eglDisplay = egl.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == egl.EGL_NO_DISPLAY) {
                killInternal();
                return;
            }
            int[] version = new int[2];
            if (!egl.eglInitialize(eglDisplay, version)) {
                killInternal();
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
                kill();
                return;
            }
            eglConfig = eglConfigs[0];

            int[] contextAttributes = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };
            eglContext = egl.eglCreateContext(eglDisplay, eglConfig, egl.EGL_NO_CONTEXT, contextAttributes);
            if (eglContext == null) {
                killInternal();
                return;
            }

            eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
            if (eglSurface == null) {
                killInternal();
                return;
            }

            if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                killInternal();
                return;
            }

            int vertexShader = GLES31.glCreateShader(GLES31.GL_VERTEX_SHADER);
            int fragmentShader = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER);
            if (vertexShader == 0 || fragmentShader == 0) {
                killInternal();
                return;
            }
            GLES31.glShaderSource(vertexShader, RLottieDrawable.readRes(null, R.raw.thanos_vertex) + "\n// " + Math.random());
            GLES31.glCompileShader(vertexShader);
            int[] status = new int[1];
            GLES31.glGetShaderiv(vertexShader, GLES31.GL_COMPILE_STATUS, status, 0);
            if (status[0] != GLES31.GL_TRUE) {
                FileLog.e("ThanosEffect, compile vertex shader error: " + GLES31.glGetShaderInfoLog(vertexShader));
                GLES31.glDeleteShader(vertexShader);
                killInternal();
                return;
            }
            GLES31.glShaderSource(fragmentShader, RLottieDrawable.readRes(null, R.raw.thanos_fragment) + "\n// " + Math.random());
            GLES31.glCompileShader(fragmentShader);
            GLES31.glGetShaderiv(fragmentShader, GLES31.GL_COMPILE_STATUS, status, 0);
            if (status[0] != GLES31.GL_TRUE) {
                FileLog.e("ThanosEffect, compile fragment shader error: " + GLES31.glGetShaderInfoLog(fragmentShader));
                GLES31.glDeleteShader(fragmentShader);
                killInternal();
                return;
            }
            drawProgram = GLES31.glCreateProgram();
            if (drawProgram == 0) {
                killInternal();
                return;
            }
            GLES31.glAttachShader(drawProgram, vertexShader);
            GLES31.glAttachShader(drawProgram, fragmentShader);

            String[] feedbackVaryings = { "outUV", "outPosition", "outVelocity", "outTime" };
            GLES31.glTransformFeedbackVaryings(drawProgram, feedbackVaryings, GLES31.GL_INTERLEAVED_ATTRIBS);
            GLES31.glLinkProgram(drawProgram);
            GLES31.glGetProgramiv(drawProgram, GLES31.GL_LINK_STATUS, status, 0);
            if (status[0] != GLES31.GL_TRUE) {
                FileLog.e("ThanosEffect, link program error: " + GLES31.glGetProgramInfoLog(drawProgram));
                killInternal();
                return;
            }

            matrixHandle = GLES31.glGetUniformLocation(drawProgram, "matrix");
            rectSizeHandle = GLES31.glGetUniformLocation(drawProgram, "rectSize");
            rectPosHandle = GLES31.glGetUniformLocation(drawProgram, "rectPos");
            resetHandle = GLES31.glGetUniformLocation(drawProgram, "reset");
            timeHandle = GLES31.glGetUniformLocation(drawProgram, "time");
            deltaTimeHandle = GLES31.glGetUniformLocation(drawProgram, "deltaTime");
            particlesCountHandle = GLES31.glGetUniformLocation(drawProgram, "particlesCount");
            sizeHandle = GLES31.glGetUniformLocation(drawProgram, "size");
            gridSizeHandle = GLES31.glGetUniformLocation(drawProgram, "gridSize");
            textureHandle = GLES31.glGetUniformLocation(drawProgram, "tex");
            seedHandle = GLES31.glGetUniformLocation(drawProgram, "seed");
            densityHandle = GLES31.glGetUniformLocation(drawProgram, "dp");
            longevityHandle = GLES31.glGetUniformLocation(drawProgram, "longevity");
            offsetHandle = GLES31.glGetUniformLocation(drawProgram, "offset");

            GLES31.glViewport(0, 0, width, height);
            GLES31.glEnable(GLES31.GL_BLEND);
            GLES31.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES31.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

            GLES31.glUseProgram(drawProgram);

            GLES31.glUniform2f(sizeHandle, width, height);
        }

        private final ArrayList<Animation> toRunStartCallback = new ArrayList<>();

        private float animationHeightPart(Animation animation) {
            int totalHeight = 0;
            for (int i = 0; i < pendingAnimations.size(); ++i) {
                totalHeight += pendingAnimations.get(i).viewHeight;
            }
            return (float) animation.viewHeight / totalHeight;
        }

        private boolean drawnAnimations = false;
        private void draw() {
            if (!alive) return;

            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

            for (int i = 0; i < pendingAnimations.size(); ++i) {
                Animation animation = pendingAnimations.get(i);
                if (animation.firstDraw) {
                    animation.calcParticlesGrid(animationHeightPart(animation));
                    if (animation.startCallback != null) {
                        toRunStartCallback.add(animation);
                    }
                }
                drawnAnimations = true;
                animation.draw();
                if (animation.isDead()) {
                    animation.done(true);
                    pendingAnimations.remove(i);
                    running = !pendingAnimations.isEmpty();
                    i--;
                }
            }

            checkGlErrors();

            try {
                egl.eglSwapBuffers(eglDisplay, eglSurface);
            } catch (Exception e) {
                for (int i = 0; i < toRunStartCallback.size(); ++i) {
                    AndroidUtilities.runOnUIThread(toRunStartCallback.get(i).startCallback);
                }
                toRunStartCallback.clear();
                for (int i = 0; i < pendingAnimations.size(); ++i) {
                    pendingAnimations.get(i).done(false);
                }
                pendingAnimations.clear();
                AndroidUtilities.runOnUIThread(() -> {
                    MessagesController.getGlobalMainSettings().edit().putBoolean("nothanos", nothanos = true).apply();
                });
                killInternal();
                return;
            }

            for (int i = 0; i < toRunStartCallback.size(); ++i) {
                AndroidUtilities.runOnUIThread(toRunStartCallback.get(i).startCallback);
            }
            toRunStartCallback.clear();

            if (pendingAnimations.isEmpty() && drawnAnimations) {
                killInternal();
            }
        };

        public void layoutAnimations() {

        }

        private final ArrayList<Animation> toAddAnimations = new ArrayList<>();
        public void animateGroup(ArrayList<View> views, Runnable whenDone) {
            if (!alive) return;
            Animation animation = new Animation(views, whenDone);
            Handler handler = getHandler();
            running = true;
            if (handler == null) {
                toAddAnimations.add(animation);
            } else {
                handler.sendMessage(handler.obtainMessage(DO_ADD_ANIMATION, animation));
            }
        }
        public void animate(View view, Runnable whenDone) {
            if (!alive) return;
            Animation animation = new Animation(view, whenDone);
            Handler handler = getHandler();
            running = true;
            if (handler == null) {
                toAddAnimations.add(animation);
            } else {
                handler.sendMessage(handler.obtainMessage(DO_ADD_ANIMATION, animation));
            }
        }
        public void animate(Matrix matrix, Bitmap bitmap, Runnable whenStart, Runnable whenDone) {
            if (!alive) return;
            Animation animation = new Animation(matrix, bitmap, whenStart, whenDone);
            Handler handler = getHandler();
            running = true;
            if (handler == null) {
                toAddAnimations.add(animation);
            } else {
                handler.sendMessage(handler.obtainMessage(DO_ADD_ANIMATION, animation));
            }
        }

        private void addAnimationInternal(Animation animation) {
            GLES31.glGenTextures(1, animation.texture, 0);
            GLES20.glBindTexture(GL10.GL_TEXTURE_2D, animation.texture[0]);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, animation.bitmap, 0);
            GLES20.glBindTexture(GL10.GL_TEXTURE_2D, 0);

            animation.bitmap.recycle();
            animation.bitmap = null;

            pendingAnimations.add(animation);
            running = true;

            animation.ready = true;
        }

        private class Animation {

            public ArrayList<View> views = new ArrayList<>();
            private long lastDrawTime = -1;
            public float time = 0;
            public boolean firstDraw = true;
            public Runnable startCallback, doneCallback;
            public volatile boolean ready;

            public float offsetLeft = 0, offsetTop = 0;
            public float left = 0;
            public float top = 0;
            public final float density = AndroidUtilities.density;
            public float longevity = 1.5f;
            public float timeScale = 1.12f;
            public boolean invalidateMatrix = true;
            public boolean customMatrix = false;
            public final float[] glMatrixValues = new float[9];
            public final float[] matrixValues = new float[9];
            public final Matrix matrix = new Matrix();

            public int particlesCount;
            public int viewWidth, viewHeight;
            public int gridWidth, gridHeight;
            public float gridSize;

            public final float seed = (float) (Math.random() * 2.);

            public int currentBuffer;
            public final int[] texture = new int[1];
            public final int[] buffer = new int[2];

            private Bitmap bitmap;

            public Animation(Matrix matrix, Bitmap bitmap, Runnable whenStarted, Runnable whenDone) {
                float[] v = new float[] { 0, 0, 0, 1, 1, 0, 1, 1 };
                matrix.mapPoints(v);
                left = v[0];
                top = v[1];
                viewWidth = (int) MathUtils.distance(v[2], v[3], v[6], v[7]);
                viewHeight = (int) MathUtils.distance(v[4], v[5], v[6], v[7]);
                customMatrix = true;
                this.matrix.set(matrix);
                retrieveMatrixValues();
                startCallback = whenStarted;
                doneCallback = whenDone;
//                longevity = 1.5f * Utilities.clamp(viewWidth / (float) AndroidUtilities.displaySize.x, .6f, 0.2f);
                this.bitmap = bitmap;
            }

            public Animation(ArrayList<View> views, Runnable whenDone) {
                this.views.addAll(views);
                int mleft = Integer.MAX_VALUE, mright = Integer.MIN_VALUE;
                int mtop = Integer.MAX_VALUE, mbottom = Integer.MIN_VALUE;
                for (int i = 0; i < views.size(); ++i) {
                    View view = views.get(i);
                    mleft = Math.min(mleft, (int) view.getX());
                    mright = Math.max(mright, (int) view.getX() + view.getWidth());
                    mtop = Math.min(mtop, (int) view.getY());
                    mbottom = Math.max(mbottom, (int) view.getY() + view.getHeight());
                }
                top = mtop;
                left = mleft;
                viewWidth = mright - mleft;
                viewHeight = mbottom - mtop;
                doneCallback = whenDone;
                startCallback = () -> {
                    for (int j = 0; j < views.size(); ++j) {
                        views.get(j).setVisibility(View.GONE);
                        if (views.get(j) instanceof ChatMessageCell) {
                            ((ChatMessageCell) views.get(j)).setCheckBoxVisible(false, false);
                            ((ChatMessageCell) views.get(j)).setChecked(false, false, false);
                        }
                    }
                };
//                longevity = 1.6f * .6f;

                for (int i = 0; i < views.size(); ++i) {
                    if (views.get(i) instanceof ChatMessageCell) {
                        ((ChatMessageCell) views.get(i)).drawingToBitmap = true;
                    }
                }

                bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                if (views.size() <= 0) return;
                if (!(views.get(0).getParent() instanceof RecyclerListView)) return;
                RecyclerListView chatListView = (RecyclerListView) views.get(0).getParent();
                if (!(chatListView.getParent() instanceof ChatActivity.ChatActivityFragmentView)) return;
                ChatActivity.ChatActivityFragmentView contentView = (ChatActivity.ChatActivityFragmentView) chatListView.getParent();
                ChatActivity chatActivity = contentView.getChatActivity();
                final ArrayList<MessageObject.GroupedMessages> drawingGroups = new ArrayList<>(10);
                ArrayList<ChatMessageCell> drawTimeAfter = new ArrayList<>();
                ArrayList<ChatMessageCell> drawNamesAfter = new ArrayList<>();
                ArrayList<ChatMessageCell> drawCaptionAfter = new ArrayList<>();
                canvas.save();
                for (int k = 0; k < 3; k++) {
                    drawingGroups.clear();
                    if (k == 2 && !chatListView.isFastScrollAnimationRunning()) {
                        continue;
                    }
                    for (int i = 0; i < views.size(); i++) {
                        View child = views.get(i);
                        if (child instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            if (child.getY() > chatListView.getHeight() || child.getY() + child.getHeight() < 0 || cell.getVisibility() == View.INVISIBLE || cell.getVisibility() == View.GONE) {
                                continue;
                            }

                            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                            MessageObject.GroupedMessagePosition position = group == null || group.positions == null ? null : group.positions.get(cell.getMessageObject());
                            if (k == 0) {
                                if (position != null || cell.getTransitionParams().animateBackgroundBoundsInner) {
                                    if (position == null || (position.last || position.minX == 0 && position.minY == 0)) {
                                        if (position == null || position.last) {
                                            drawTimeAfter.add(cell);
                                        }
                                        if ((position == null || (position.minX == 0 && position.minY == 0)) && cell.hasNameLayout()) {
                                            drawNamesAfter.add(cell);
                                        }
                                    }
                                    if (position != null || cell.getTransitionParams().transformGroupToSingleMessage || cell.getTransitionParams().animateBackgroundBoundsInner) {
                                        if (position == null || (position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                            drawCaptionAfter.add(cell);
                                        }
                                    }
                                }
                            }

                            if (group == null || (k == 0 && group.messages.size() == 1) || (k == 1 && !group.transitionParams.drawBackgroundForDeletedItems)) {
                                continue;
                            }
                            if ((k == 0 && cell.getMessageObject().deleted) || (k == 1 && !cell.getMessageObject().deleted)) {
                                continue;
                            }
                            if ((k == 2 && !cell.willRemovedAfterAnimation()) || (k != 2 && cell.willRemovedAfterAnimation())) {
                                continue;
                            }

                            if (!drawingGroups.contains(group)) {
                                group.transitionParams.left = 0;
                                group.transitionParams.top = 0;
                                group.transitionParams.right = 0;
                                group.transitionParams.bottom = 0;

                                group.transitionParams.pinnedBotton = false;
                                group.transitionParams.pinnedTop = false;
                                group.transitionParams.cell = cell;
                                drawingGroups.add(group);
                            }

                            group.transitionParams.pinnedTop = cell.isPinnedTop();
                            group.transitionParams.pinnedBotton = cell.isPinnedBottom();

                            int left = (cell.getLeft() + cell.getBackgroundDrawableLeft());
                            int right = (cell.getLeft() + cell.getBackgroundDrawableRight());
                            int top = (cell.getTop() + cell.getBackgroundDrawableTop());
                            int bottom = (cell.getTop() + cell.getBackgroundDrawableBottom());

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                                top -= AndroidUtilities.dp(10);
                            }

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                                bottom += AndroidUtilities.dp(10);
                            }

                            if (cell.willRemovedAfterAnimation()) {
                                group.transitionParams.cell = cell;
                            }

                            if (group.transitionParams.top == 0 || top < group.transitionParams.top) {
                                group.transitionParams.top = top;
                            }
                            if (group.transitionParams.bottom == 0 || bottom > group.transitionParams.bottom) {
                                group.transitionParams.bottom = bottom;
                            }
                            if (group.transitionParams.left == 0 || left < group.transitionParams.left) {
                                group.transitionParams.left = left;
                            }
                            if (group.transitionParams.right == 0 || right > group.transitionParams.right) {
                                group.transitionParams.right = right;
                            }
                        }
                    }

                    for (int i = 0; i < drawingGroups.size(); i++) {
                        MessageObject.GroupedMessages group = drawingGroups.get(i);
                        float x = group.transitionParams.cell.getNonAnimationTranslationX(true);
                        float l = (group.transitionParams.left + x + group.transitionParams.offsetLeft);
                        float t = (group.transitionParams.top + group.transitionParams.offsetTop);
                        float r = (group.transitionParams.right + x + group.transitionParams.offsetRight);
                        float b = (group.transitionParams.bottom + group.transitionParams.offsetBottom);

                        if (!group.transitionParams.backgroundChangeBounds) {
                            t += group.transitionParams.cell.getTranslationY();
                            b += group.transitionParams.cell.getTranslationY();
                        }

                        if (t < chatActivity.chatListViewPaddingTop - chatActivity.chatListViewPaddingVisibleOffset - AndroidUtilities.dp(20)) {
                            t = chatActivity.chatListViewPaddingTop - chatActivity.chatListViewPaddingVisibleOffset - AndroidUtilities.dp(20);
                        }

                        if (b > chatListView.getMeasuredHeight() + AndroidUtilities.dp(20)) {
                            b = chatListView.getMeasuredHeight() + AndroidUtilities.dp(20);
                        }

                        t -= top;
                        b -= top;

                        boolean useScale = group.transitionParams.cell.getScaleX() != 1f || group.transitionParams.cell.getScaleY() != 1f;
                        if (useScale) {
                            canvas.save();
                            canvas.scale(group.transitionParams.cell.getScaleX(), group.transitionParams.cell.getScaleY(), l + (r - l) / 2, t + (b - t) / 2);
                        }
                        boolean selected = false;
                        group.transitionParams.cell.drawBackground(canvas, (int) l, (int) t, (int) r, (int) b, group.transitionParams.pinnedTop, group.transitionParams.pinnedBotton, selected, contentView.getKeyboardHeight());
                        group.transitionParams.cell = null;
                        group.transitionParams.drawCaptionLayout = group.hasCaption;
                        if (useScale) {
                            canvas.restore();
                            for (int ii = 0; ii < views.size(); ii++) {
                                View child = views.get(ii);
                                if (child instanceof ChatMessageCell && ((ChatMessageCell) child).getCurrentMessagesGroup() == group) {
                                    ChatMessageCell cell = ((ChatMessageCell) child);
                                    int left = cell.getLeft();
                                    int top = cell.getTop();
                                    child.setPivotX(l - left + (r - l) / 2);
                                    child.setPivotY(t - top + (b - t) / 2);
                                }
                            }
                        }
                    }
                }
                for (int i = 0; i < views.size(); ++i) {
                    View view = views.get(i);
                    canvas.save();
                    canvas.translate(view.getX() - mleft, view.getY() - mtop);
                    view.draw(canvas);
                    if (view instanceof ChatMessageCell) {
                        ((ChatMessageCell) view).drawOutboundsContent(canvas);
                    } else if (view instanceof ChatActionCell) {
                        ((ChatActionCell) view).drawOutboundsContent(canvas);
                    }
                    canvas.restore();
                }
                float listTop = chatListView.getY() + chatActivity.chatListViewPaddingTop - chatActivity.chatListViewPaddingVisibleOffset - AndroidUtilities.dp(4);
                int size = drawTimeAfter.size();
                if (size > 0) {
                    for (int a = 0; a < size; a++) {
                        ChatMessageCell view = drawTimeAfter.get(a);
                        drawChildElement(chatListView, chatActivity, canvas, listTop, view, 0, view.getX() - mleft, view.getY() - mtop);
                    }
                    drawTimeAfter.clear();
                }
                size = drawNamesAfter.size();
                if (size > 0) {
                    for (int a = 0; a < size; a++) {
                        ChatMessageCell view = drawNamesAfter.get(a);
                        drawChildElement(chatListView, chatActivity, canvas, listTop, view, 1,  view.getX() - mleft, view.getY() - mtop);
                    }
                    drawNamesAfter.clear();
                }
                size = drawCaptionAfter.size();
                if (size > 0) {
                    for (int a = 0; a < size; a++) {
                        ChatMessageCell cell = drawCaptionAfter.get(a);
                        if (cell.getCurrentPosition() == null && !cell.getTransitionParams().animateBackgroundBoundsInner) {
                            continue;
                        }
                        drawChildElement(chatListView, chatActivity, canvas, listTop, cell, 2,  cell.getX() - mleft, cell.getY() - mtop);
                    }
                    drawCaptionAfter.clear();
                }
                canvas.restore();

                for (int i = 0; i < views.size(); ++i) {
                    if (views.get(i) instanceof ChatMessageCell) {
                        ((ChatMessageCell) views.get(i)).drawingToBitmap = false;
                    }
                }
            }

            private void drawChildElement(View chatListView, ChatActivity chatActivity, Canvas canvas, float listTop, ChatMessageCell cell, int type, float x, float y) {
                canvas.save();
                float alpha = cell.shouldDrawAlphaLayer() ? cell.getAlpha() : 1f;
//                canvas.clipRect(chatListView.getLeft() - x, listTop - y, chatListView.getRight() - x, chatListView.getY() + chatListView.getMeasuredHeight() - (chatActivity == null ? 0 : chatActivity.blurredViewBottomOffset) - y);
                canvas.translate(x, y);
                cell.setInvalidatesParent(true);
                if (type == 0) {
                    cell.drawTime(canvas, alpha, true);
                } else if (type == 1) {
                    cell.drawNamesLayout(canvas, alpha);
                } else {
                    cell.drawCaptionLayout(canvas, cell.getCurrentPosition() != null && (cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_LEFT) == 0, alpha);
                }
                cell.setInvalidatesParent(false);
                canvas.restore();
            }

            public void calcParticlesGrid(float part) {
                final int maxParticlesCount;
                switch (SharedConfig.getDevicePerformanceClass()) {
                    case SharedConfig.PERFORMANCE_CLASS_HIGH:
                        maxParticlesCount = 120_000;
                        break;
                    case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                        maxParticlesCount = 60_000;
                        break;
                    case SharedConfig.PERFORMANCE_CLASS_LOW:
                    default:
                        maxParticlesCount = 30_000;
                        break;
                }
                float p = Math.max(AndroidUtilities.dpf2(.4f), 1);
                particlesCount = Utilities.clamp((int) (viewWidth * viewHeight / (p * p)), (int) (maxParticlesCount * part), 10);

                final float aspectRatio = (float) viewWidth / viewHeight;
                gridHeight = (int) Math.round(Math.sqrt(particlesCount / aspectRatio));
                gridWidth = (int) Math.round((float) particlesCount / gridHeight);
                while (gridWidth * gridHeight < particlesCount) {
                    if ((float) gridWidth / gridHeight < aspectRatio) {
                        gridWidth++;
                    } else {
                        gridHeight++;
                    }
                }
                particlesCount = gridWidth * gridHeight;
                gridSize = Math.max((float) viewWidth / gridWidth, (float) viewHeight / gridHeight);

                GLES31.glGenBuffers(2, buffer, 0);
                for (int i = 0; i < 2; ++i) {
                    GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, buffer[i]);
                    GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, particlesCount * 28, null, GLES31.GL_DYNAMIC_DRAW);
                }
            }

            public Animation(View view, Runnable whenDone) {
                this.views.add(view);
                viewWidth = view.getWidth();
                viewHeight = view.getHeight();
                top = view.getY();
                left = 0;
                if (view instanceof BaseCell) {
                    viewWidth = Math.max(1, ((BaseCell) view).getBoundsRight() - ((BaseCell) view).getBoundsLeft());
                    left += ((BaseCell) view).getBoundsLeft();
                }
                doneCallback = whenDone;
                startCallback = () -> {
                    for (int j = 0; j < views.size(); ++j) {
                        views.get(j).setVisibility(View.GONE);
                        if (views.get(j) instanceof ChatMessageCell) {
                            ((ChatMessageCell) views.get(j)).setCheckBoxVisible(false, false);
                            ((ChatMessageCell) views.get(j)).setChecked(false, false, false);
                        }
                    }
                };
//                longevity = 1.5f * Utilities.clamp(viewWidth / (float) AndroidUtilities.displaySize.x, .6f, 0.2f);

                bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                canvas.save();
                canvas.translate(-left, 0);
                if (view instanceof ChatMessageCell) {
                    ((ChatMessageCell) view).drawingToBitmap = true;
                }
                if (view instanceof ChatActionCell && ((ChatActionCell) view).hasGradientService()) {
                    ((ChatActionCell) view).drawBackground(canvas, true);
                } else if (view instanceof ChatMessageCell && ((ChatMessageCell) view).drawBackgroundInParent()) {
                    ((ChatMessageCell) view).drawBackgroundInternal(canvas, true);
                }
                view.draw(canvas);
                if (view instanceof ChatMessageCell) {
                    ImageReceiver avatarImage = ((ChatMessageCell) view).getAvatarImage();
                    if (avatarImage != null && avatarImage.getVisible()) {
                        canvas.save();
                        canvas.translate(0, -view.getY());
                        avatarImage.draw(canvas);
                        canvas.restore();
                    }
                    ((ChatMessageCell) view).drawingToBitmap = false;
                }
                if (view instanceof ChatMessageCell) {
                    ((ChatMessageCell) view).drawOutboundsContent(canvas);
                } else if (view instanceof ChatActionCell) {
                    ((ChatActionCell) view).drawOutboundsContent(canvas);
                }
                canvas.restore();

                left += view.getX();
            }

            private void retrieveMatrixValues() {
                matrix.getValues(matrixValues);
                glMatrixValues[0] = matrixValues[0];
                glMatrixValues[1] = matrixValues[3];
                glMatrixValues[2] = matrixValues[6];
                glMatrixValues[3] = matrixValues[1];
                glMatrixValues[4] = matrixValues[4];
                glMatrixValues[5] = matrixValues[7];
                glMatrixValues[6] = matrixValues[2];
                glMatrixValues[7] = matrixValues[5];
                glMatrixValues[8] = matrixValues[8];
                invalidateMatrix = false;
            }

            public void draw() {
                final long now = System.nanoTime();
                final double Δt = lastDrawTime < 0 ? 0 : (now - lastDrawTime) / 1_000_000_000.;
                lastDrawTime = now;

                if (invalidateMatrix && !customMatrix) {
                    matrix.reset();
                    matrix.postScale(viewWidth, viewHeight);
                    matrix.postTranslate(left, top);
                    retrieveMatrixValues();
                }

                time += Δt * timeScale;

                GLES31.glUniformMatrix3fv(matrixHandle, 1, false, glMatrixValues, 0);
                GLES31.glUniform1f(resetHandle, firstDraw ? 1f : 0f);
                GLES31.glUniform1f(timeHandle, time);
                GLES31.glUniform1f(deltaTimeHandle, (float) Δt * timeScale);
                GLES31.glUniform1f(particlesCountHandle, particlesCount);
                GLES31.glUniform3f(gridSizeHandle, gridWidth, gridHeight, gridSize);
                GLES31.glUniform2f(offsetHandle, offsetLeft, offsetTop);

                GLES31.glUniform2f(rectSizeHandle, viewWidth, viewHeight);
                GLES31.glUniform1f(seedHandle, seed);
                GLES31.glUniform2f(rectPosHandle, 0, 0);
                GLES31.glUniform1f(densityHandle, density);
                GLES31.glUniform1f(longevityHandle, longevity);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
                GLES31.glUniform1i(textureHandle, 0);

                GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, buffer[currentBuffer]);
                GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 28, 0); // Initial UV (vec2)
                GLES31.glEnableVertexAttribArray(0);
                GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, 28, 8); // Position (vec2)
                GLES31.glEnableVertexAttribArray(1);
                GLES31.glVertexAttribPointer(2, 2, GLES31.GL_FLOAT, false, 28, 16); // Velocity (vec2)
                GLES31.glEnableVertexAttribArray(2);
                GLES31.glVertexAttribPointer(3, 1, GLES31.GL_FLOAT, false, 28, 24); // Time (float)
                GLES31.glEnableVertexAttribArray(3);
                GLES31.glBindBufferBase(GLES31.GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffer[1 - currentBuffer]);
                GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 28, 0); // Initial UV (vec2)
                GLES31.glEnableVertexAttribArray(0);
                GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, 28, 8); // Position (vec2)
                GLES31.glEnableVertexAttribArray(1);
                GLES31.glVertexAttribPointer(2, 2, GLES31.GL_FLOAT, false, 28, 16); // Velocity (vec2)
                GLES31.glEnableVertexAttribArray(2);
                GLES31.glVertexAttribPointer(3, 1, GLES31.GL_FLOAT, false, 28, 24); // Time (float)
                GLES31.glEnableVertexAttribArray(3);

                GLES31.glBeginTransformFeedback(GLES31.GL_POINTS);
                GLES31.glDrawArrays(GLES31.GL_POINTS, 0, particlesCount);
                GLES31.glEndTransformFeedback();

                GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, 0);
                GLES31.glBindBuffer(GLES31.GL_TRANSFORM_FEEDBACK_BUFFER, 0);

                firstDraw = false;
                currentBuffer = 1 - currentBuffer;
            }

            public boolean isDead() {
                return time > longevity + .9f;
            }

            public void done(boolean success) {
                try { GLES31.glDeleteBuffers(2, buffer, 0); } catch (Exception e) { FileLog.e(e); };
                if (drawProgram != 0) {
                    try { GLES31.glDeleteProgram(drawProgram); } catch (Exception e) { FileLog.e(e); };
                    drawProgram = 0;
                }
                try { GLES31.glDeleteTextures(1, texture, 0); } catch (Exception e) { FileLog.e(e); };

                if (doneCallback != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (doneCallback != null) {
                            doneCallback.run();
                        }
                    });
                }
            }
        }

        private void checkGlErrors() {
            int err;
            while ((err = GLES31.glGetError()) != GLES31.GL_NO_ERROR) {
                FileLog.e("thanos gles error " + err);
            }
        }
    }
}
