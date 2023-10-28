/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.zxing.common.detector.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.video.MP4Builder;
import org.telegram.messenger.video.MediaCodecVideoConvertor;
import org.telegram.messenger.video.Mp4Movie;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.InstantCameraView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;

@SuppressLint("NewApi")
public class CameraView extends FrameLayout implements TextureView.SurfaceTextureListener, CameraController.ICameraView {

    public boolean WRITE_TO_FILE_IN_BACKGROUND = false;

    public boolean isStory;
    private Size[] previewSize = new Size[2];
    private Size[] pictureSize = new Size[2];
    CameraInfo[] info = new CameraInfo[2];
    private boolean mirror;
    private boolean lazy;
    private TextureView textureView;
    private ImageView blurredStubView;
    private CameraSession[] cameraSession = new CameraSession[2];
    private boolean inited;
    private CameraViewDelegate delegate;
    private int clipTop;
    private int clipBottom;
    private boolean isFrontface;
    private Matrix txform = new Matrix();
    private Matrix matrix = new Matrix();
    private int focusAreaSize;
    private Drawable thumbDrawable;

    private boolean useMaxPreview;

    private long lastDrawTime;
    private float focusProgress = 1.0f;
    private float innerAlpha;
    private float outerAlpha;
    private boolean initialFrontface;
    private int cx;
    private int cy;
    private Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean optimizeForBarcode;
    File recordFile;

    private DecelerateInterpolator interpolator = new DecelerateInterpolator();
    private volatile int surfaceWidth;
    private volatile int surfaceHeight;

    private File cameraFile;

    boolean firstFrameRendered;
    boolean firstFrame2Rendered;
    private final Object layoutLock = new Object();

    private float[][] mMVPMatrix = new float[2][16];
    private float[][] mSTMatrix = new float[2][16];
    private float[][] moldSTMatrix = new float[2][16];

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private float[][] cameraMatrix = new float[2][16];
    private volatile float lastCrossfadeValue = 0;

    private final static int audioSampleRate = 44100;

    public void setRecordFile(File generateVideoPath) {
        recordFile = generateVideoPath;
    }

    Runnable onRecordingFinishRunnable;

    private CameraSession cameraSessionRecording;

    public boolean startRecording(File path, Runnable onFinished) {
        cameraSessionRecording = cameraSession[0];
        cameraThread.startRecording(path);
        onRecordingFinishRunnable = onFinished;
        return true;
    }

    public void stopRecording() {
        cameraThread.stopRecording();
    }

    ValueAnimator flipAnimator;
    boolean flipHalfReached;
    boolean flipping = false;

    private int fpsLimit = -1;
    long nextFrameTimeNs;

    public void startSwitchingAnimation() {
        if (flipAnimator != null) {
            flipAnimator.cancel();
        }
        blurredStubView.animate().setListener(null).cancel();
        if (firstFrameRendered) {
            Bitmap bitmap = textureView.getBitmap(100, 100);
            if (bitmap != null) {
                Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
                Drawable drawable = new BitmapDrawable(bitmap);
                blurredStubView.setBackground(drawable);
            }
        }
        blurredStubView.setAlpha(1f);
        blurredStubView.setVisibility(View.VISIBLE);

        flipHalfReached = false;
        flipping = true;
        flipAnimator = ValueAnimator.ofFloat(0, 1f);
        flipAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float v = (float) valueAnimator.getAnimatedValue();

                float rotation;
                boolean halfReached = false;
                if (v < 0.5f) {
                    rotation = v;
                } else {
                    halfReached = true;
                    rotation = v - 1f;
                }
                rotation *= 180;
                textureView.setRotationY(rotation);
                blurredStubView.setRotationY(rotation);
                if (halfReached && !flipHalfReached) {
//                    blurredStubView.setAlpha(1f);
                    flipHalfReached = true;
                }
            }
        });
        flipAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                flipAnimator = null;
                textureView.setTranslationY(0);
                textureView.setRotationX(0);
                textureView.setRotationY(0);
                textureView.setScaleX(1f);
                textureView.setScaleY(1f);

                blurredStubView.setRotationY(0);

                if (!flipHalfReached) {
//                    blurredStubView.setAlpha(1f);
                    flipHalfReached = true;
                }
                invalidate();
            }
        });
        flipAnimator.setDuration(500);
        flipAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        flipAnimator.start();
        invalidate();
    }

    protected boolean dual;
    private boolean dualCameraAppeared;
    private Matrix dualMatrix = new Matrix();
    private long toggleDualUntil;
    private boolean closingDualCamera;
    private boolean initFirstCameraAfterSecond;
    public boolean toggledDualAsSave;

    public boolean isDual() {
        return dual;
    }

    private void enableDualInternal() {
        if (cameraSession[1] != null) {
            if (closingDualCamera) {
                return;
            }
            closingDualCamera = true;
            CameraController.getInstance().close(cameraSession[1], null, null, () -> {
                closingDualCamera = false;
                enableDualInternal();
            });
            if (cameraSessionRecording == cameraSession[1]) {
                cameraSessionRecording = null;
            }
            cameraSession[1] = null;
            addToDualWait(400L);
            return;
        }
        if (!isFrontface && "samsung".equalsIgnoreCase(Build.MANUFACTURER) && !toggledDualAsSave && cameraSession[0] != null) {
            final Handler handler = cameraThread.getHandler();
            if (handler != null) {
                cameraThread.sendMessage(handler.obtainMessage(cameraThread.BLUR_CAMERA1), 0);
            }
            CameraController.getInstance().close(cameraSession[0], null, null, () -> {
//                inited = false;
//                synchronized (layoutLock) {
//                    firstFrameRendered = false;
//                }
                initFirstCameraAfterSecond = true;
                updateCameraInfoSize(1);
                if (handler != null) {
                    cameraThread.sendMessage(handler.obtainMessage(cameraThread.DO_DUAL_START, info[1].cameraId, 0, dualMatrix), 0);
                }
                addToDualWait(1200L);
            });
            cameraSession[0] = null;
            return;
        }
        updateCameraInfoSize(1);
        final Handler handler = cameraThread.getHandler();
        if (handler != null) {
            cameraThread.sendMessage(handler.obtainMessage(cameraThread.DO_DUAL_START, info[1].cameraId, 0, dualMatrix), 0);
        }
        addToDualWait(800L);
    }

    public void toggleDual() {
        toggleDual(false);
    }

    public void toggleDual(boolean force) {
        if (!force && (flipping || closingDualCamera || (System.currentTimeMillis() < toggleDualUntil || dual != dualCameraAppeared) && !dual)) {
            return;
        }
        addToDualWait(200L);
        dual = !dual;
        if (dual) {
            if (cameraSession[0] != null) {
                cameraSession[0].setCurrentFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }
            enableDualInternal();
        } else {
            if (cameraSession[1] == null || !cameraSession[1].isInitied()) {
                dual = !dual;
                return;
            }
            if (cameraSession[1] != null) {
                closingDualCamera = true;
                if (cameraSessionRecording == cameraSession[1]) {
                    cameraSessionRecording = null;
                }
                CameraController.getInstance().close(cameraSession[1], null, null, () -> {
                    closingDualCamera = false;
                    dualCameraAppeared = false;
                    addToDualWait(400L);
                    final Handler handler = cameraThread.getHandler();
                    if (handler != null) {
                        cameraThread.sendMessage(handler.obtainMessage(cameraThread.DO_DUAL_END), 0);
                    }
                });
                cameraSession[1] = null;
                previewSize[1] = null;
                pictureSize[1] = null;
                info[1] = null;
            } else {
                dualCameraAppeared = false;
            }
            if (!closingDualCamera) {
                final Handler handler = cameraThread.getHandler();
                if (handler != null) {
                    cameraThread.sendMessage(handler.obtainMessage(cameraThread.DO_DUAL_END), 0);
                }
            }
        }
        toggledDualAsSave = false;
    }

    private void addToDualWait(long add) {
        final long now = System.currentTimeMillis();
        if (toggleDualUntil < now) {
            toggleDualUntil = now + add;
        } else {
            toggleDualUntil += add;
        }
    }

    public Matrix getDualPosition() {
        return dualMatrix;
    }

    public void updateDualPosition() {
        if (cameraThread == null) {
            return;
        }
        final Handler handler = cameraThread.getHandler();
        if (handler != null) {
            cameraThread.sendMessage(handler.obtainMessage(cameraThread.DO_DUAL_MOVE, dualMatrix), 0);
        }
    }

    public interface CameraViewDelegate {
        void onCameraInit();
    }

    public CameraView(Context context, boolean frontface) {
        this(context, frontface, false);
    }

    public CameraView(Context context, boolean frontface, boolean lazy) {
        super(context, null);
        initialFrontface = isFrontface = frontface;
        textureView = new TextureView(context);
        if (!(this.lazy = lazy)) {
            initTexture();
        }

        setWillNotDraw(!lazy);

        blurredStubView = new ImageView(context);
        addView(blurredStubView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        blurredStubView.setVisibility(View.GONE);
        focusAreaSize = AndroidUtilities.dp(96);
        outerPaint.setColor(0xffffffff);
        outerPaint.setStyle(Paint.Style.STROKE);
        outerPaint.setStrokeWidth(AndroidUtilities.dp(2));
        innerPaint.setColor(0x7fffffff);
    }

    private boolean textureInited = false;
    public void initTexture() {
        if (textureInited) {
            return;
        }

        textureView.setSurfaceTextureListener(this);
        addView(textureView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        textureInited = true;
    }

    public void setOptimizeForBarcode(boolean value) {
        optimizeForBarcode = value;
        if (cameraSession[0] != null) {
            cameraSession[0].setOptimizeForBarcode(true);
        }
    }

    Rect bounds = new Rect();

    @Override
    protected void onDraw(Canvas canvas) {
        if (thumbDrawable != null) {
            bounds.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            int W = thumbDrawable.getIntrinsicWidth(), H = thumbDrawable.getIntrinsicHeight();
            float scale = 1f / Math.min(W / (float) Math.max(1, bounds.width()), H / (float) Math.max(1, bounds.height()));
            thumbDrawable.setBounds(
                (int) (bounds.centerX() - W * scale / 2f),
                (int) (bounds.centerY() - H * scale / 2f),
                (int) (bounds.centerX() + W * scale / 2f),
                (int) (bounds.centerY() + H * scale / 2f)
            );
            thumbDrawable.draw(canvas);
        }
        super.onDraw(canvas);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == thumbDrawable || super.verifyDrawable(who);
    }

    public void setThumbDrawable(Drawable drawable) {
        if (thumbDrawable != null) {
            thumbDrawable.setCallback(null);
        }
        thumbDrawable = drawable;
        if (thumbDrawable != null) {
            thumbDrawable.setCallback(this);
        }
        if (!firstFrameRendered) {
            blurredStubView.animate().setListener(null).cancel();
            blurredStubView.setBackground(thumbDrawable);
            blurredStubView.setAlpha(1f);
            blurredStubView.setVisibility(View.VISIBLE);
        }
    }

    private int measurementsCount = 0;
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        measurementsCount = 0;
    }

    private int lastWidth = -1, lastHeight = -1;
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec),
            height = MeasureSpec.getSize(heightMeasureSpec);
        if (previewSize[0] != null && cameraSession[0] != null) {
            int frameWidth, frameHeight;
            if ((lastWidth != width || lastHeight != height) && measurementsCount > 1) {
                cameraSession[0].updateRotation();
            }
            measurementsCount++;
            if (cameraSession[0].getWorldAngle() == 90 || cameraSession[0].getWorldAngle() == 270) {
                frameWidth = previewSize[0].getWidth();
                frameHeight = previewSize[0].getHeight();
            } else {
                frameWidth = previewSize[0].getHeight();
                frameHeight = previewSize[0].getWidth();
            }
            float s = Math.max(MeasureSpec.getSize(widthMeasureSpec) / (float) frameWidth , MeasureSpec.getSize(heightMeasureSpec) / (float) frameHeight);
            blurredStubView.getLayoutParams().width = textureView.getLayoutParams().width = (int) (s * frameWidth);
            blurredStubView.getLayoutParams().height = textureView.getLayoutParams().height = (int) (s * frameHeight);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkPreviewMatrix();
        lastWidth = width;
        lastHeight = height;

        pixelW = getMeasuredWidth();
        pixelH = getMeasuredHeight();
        if (pixelDualW <= 0) {
            pixelDualW = getMeasuredWidth();
            pixelDualH = getMeasuredHeight();
        }
    }

    public float getTextureHeight(float width, float height) {
        if (previewSize[0] == null || cameraSession[0] == null) {
            return height;
        }

        int frameWidth, frameHeight;
        if (cameraSession[0].getWorldAngle() == 90 || cameraSession[0].getWorldAngle() == 270) {
            frameWidth = previewSize[0].getWidth();
            frameHeight = previewSize[0].getHeight();
        } else {
            frameWidth = previewSize[0].getHeight();
            frameHeight = previewSize[0].getWidth();
        }
        float s = Math.max(width / (float) frameWidth , height / (float) frameHeight);
        return (int) (s * frameHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        checkPreviewMatrix();
    }

    public void setMirror(boolean value) {
        mirror = value;
    }

    public boolean isFrontface() {
        return isFrontface;
    }

    public TextureView getTextureView() {
        return textureView;
    }

    public void setUseMaxPreview(boolean value) {
        useMaxPreview = value;
    }

    public boolean hasFrontFaceCamera() {
        ArrayList<CameraInfo> cameraInfos = CameraController.getInstance().getCameras();
        for (int a = 0; a < cameraInfos.size(); a++) {
            if (cameraInfos.get(a).frontCamera != 0) {
                return true;
            }
        }
        return false;
    }

    private Integer shape;
    public void dualToggleShape() {
        if (flipping || !dual) {
            return;
        }
        Handler handler = cameraThread.getHandler();
        if (shape == null) {
            shape = MessagesController.getGlobalMainSettings().getInt("dualshape", 0);
        }
        shape++;
        MessagesController.getGlobalMainSettings().edit().putInt("dualshape", shape).apply();
        if (handler != null) {
            handler.sendMessage(handler.obtainMessage(cameraThread.DO_DUAL_TOGGLE_SHAPE));
        }
    }

    public int getDualShape() {
        if (shape == null) {
            shape = MessagesController.getGlobalMainSettings().getInt("dualshape", 0);
        }
        return shape;
    }

    private long lastDualSwitchTime;

    public void switchCamera() {
        if (flipping || System.currentTimeMillis() < toggleDualUntil && !dualCameraAppeared) {
            return;
        }
        if (dual) {
            if (!dualCameraAppeared || System.currentTimeMillis() - lastDualSwitchTime < 420) {
                return;
            }
            lastDualSwitchTime = System.currentTimeMillis();
            CameraInfo info0 = info[0];
            info[0] = info[1];
            info[1] = info0;

            Size previewSize0 = previewSize[0];
            previewSize[0] = previewSize[1];
            previewSize[1] = previewSize0;

            Size pictureSize0 = pictureSize[0];
            pictureSize[0] = pictureSize[1];
            pictureSize[1] = pictureSize0;

            CameraSession cameraSession0 = cameraSession[0];
            cameraSession[0] = cameraSession[1];
            cameraSession[1] = cameraSession0;

            isFrontface = !isFrontface;

            Handler handler = cameraThread.getHandler();
            if (handler != null) {
                handler.sendMessage(handler.obtainMessage(cameraThread.DO_DUAL_FLIP));
            }
            return;
        }
        startSwitchingAnimation();
        if (cameraSession[0] != null) {
            if (cameraSessionRecording == cameraSession[0]) {
                cameraSessionRecording = null;
            }
            CameraController.getInstance().close(cameraSession[0], null, null, () -> {
                inited = false;
                synchronized (layoutLock) {
                    firstFrameRendered = false;
                }
                updateCameraInfoSize(0);
                cameraThread.reinitForNewCamera();
            });
            cameraSession[0] = null;
        }
        isFrontface = !isFrontface;
    }

    public void resetCamera() {
        if (cameraSession[0] != null) {
            if (cameraSessionRecording == cameraSession[0]) {
                cameraSessionRecording = null;
            }
            final Handler handler = cameraThread.getHandler();
            if (handler != null) {
                cameraThread.sendMessage(handler.obtainMessage(cameraThread.BLUR_CAMERA1), 0);
            }
            CameraController.getInstance().close(cameraSession[0], null, null, () -> {
                inited = false;
                synchronized (layoutLock) {
                    firstFrameRendered = false;
                }
                updateCameraInfoSize(0);
                cameraThread.reinitForNewCamera();
            });
            cameraSession[0] = null;
        }
    }

    public Size getPreviewSize() {
        return previewSize[0];
    }

    protected CameraGLThread cameraThread;
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        updateCameraInfoSize(0);
        if (dual) {
            updateCameraInfoSize(1);
        }

        surfaceHeight = height;
        surfaceWidth = width;

        if (cameraThread == null && surface != null) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("CameraView " + "start create thread");
            }
            cameraThread = new CameraGLThread(surface);
            checkPreviewMatrix();
        }
    }

    private void updateCameraInfoSize(int i) {
        ArrayList<CameraInfo> cameraInfos = CameraController.getInstance().getCameras();
        if (cameraInfos == null) {
            return;
        }
        for (int a = 0; a < cameraInfos.size(); a++) {
            CameraInfo cameraInfo = cameraInfos.get(a);
            boolean cameraInfoIsFrontface = cameraInfo.frontCamera != 0;
            boolean shouldBeFrontface = isFrontface;
            if (i == 1) {
                shouldBeFrontface = !shouldBeFrontface;
            }
            if (cameraInfoIsFrontface == shouldBeFrontface) {
                info[i] = cameraInfo;
                break;
            }
        }
        if (info[i] == null) {
            return;
        }
        float size4to3 = 4.0f / 3.0f;
        float size16to9 = 16.0f / 9.0f;
        float screenSize = (float) Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
        org.telegram.messenger.camera.Size aspectRatio;
        int wantedWidth;
        int wantedHeight;

        int photoMaxWidth;
        int photoMaxHeight;
        if (initialFrontface) {
            aspectRatio = new Size(16, 9);
            photoMaxWidth = wantedWidth = 1280;
            photoMaxHeight = wantedHeight = 720;
        } else {
            if (Math.abs(screenSize - size4to3) < 0.1f) {
                aspectRatio = new Size(4, 3);
                wantedWidth = 1280;
                wantedHeight = 960;

                if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
                    photoMaxWidth = 1280;
                    photoMaxHeight = 960;
                } else {
                    photoMaxWidth = 1920;
                    photoMaxHeight = 1440;
                }
            } else {
                aspectRatio = new Size(16, 9);
                wantedWidth = 1280;
                wantedHeight = 720;

                if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
                    photoMaxWidth = 1280;
                    photoMaxHeight = 960;
                } else {
                    photoMaxWidth = isStory ? 1280 : 1920;
                    photoMaxHeight = isStory ? 720 : 1080;
                }
            }
        }

        previewSize[i] = CameraController.chooseOptimalSize(info[i].getPreviewSizes(), wantedWidth, wantedHeight, aspectRatio, isStory);
        pictureSize[i] = CameraController.chooseOptimalSize(info[i].getPictureSizes(), photoMaxWidth, photoMaxHeight, aspectRatio, false);

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("camera preview " + previewSize[0]);
        }
        requestLayout();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int surfaceW, int surfaceH) {
        surfaceHeight = surfaceH;
        surfaceWidth = surfaceW;
        checkPreviewMatrix();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        if (cameraThread != null) {
            cameraThread.shutdown(0);
            cameraThread.postRunnable(() -> this.cameraThread = null);
        }
        if (cameraSession[0] != null) {
            CameraController.getInstance().close(cameraSession[0], null, null);
        }
        if (cameraSession[1] != null) {
            CameraController.getInstance().close(cameraSession[1], null, null);
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (!inited && cameraSession[0] != null && cameraSession[0].isInitied()) {
            if (delegate != null) {
                delegate.onCameraInit();
            }
            inited = true;
            if (lazy) {
                textureView.setAlpha(0);
                showTexture(true, true);
            }
        }
    }

    private ValueAnimator textureViewAnimator;
    public void showTexture(boolean show, boolean animated) {
        if (textureView == null) {
            return;
        }

        if (textureViewAnimator != null) {
            textureViewAnimator.cancel();
            textureViewAnimator = null;
        }
        if (animated) {
            textureViewAnimator = ValueAnimator.ofFloat(textureView.getAlpha(), show ? 1 : 0);
            textureViewAnimator.addUpdateListener(anm -> {
                final float t = (float) anm.getAnimatedValue();
                textureView.setAlpha(t);
            });
            textureViewAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    textureView.setAlpha(show ? 1 : 0);
                    textureViewAnimator = null;
                }
            });
            textureViewAnimator.start();
        } else {
            textureView.setAlpha(show ? 1 : 0);
        }
    }

    public void setClipTop(int value) {
        clipTop = value;
    }

    public void setClipBottom(int value) {
        clipBottom = value;
    }

    private final Runnable updateRotationMatrix = () -> {
        final CameraGLThread cameraThread = this.cameraThread;
        if (cameraThread != null) {
            for (int i = 0; i < 2; ++i) {
                if (cameraThread.currentSession[i] != null) {
                    int rotationAngle = cameraThread.currentSession[i].getWorldAngle();
                    android.opengl.Matrix.setIdentityM(mMVPMatrix[i], 0);
                    if (rotationAngle != 0) {
                        android.opengl.Matrix.rotateM(mMVPMatrix[i], 0, rotationAngle, 0, 0, 1);
                    }
                }
            }
        }
    };

    private void checkPreviewMatrix() {
        if (previewSize[0] == null || textureView == null) {
            return;
        }

        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();

        Matrix matrix = new Matrix();
        if (cameraSession[0] != null) {
            matrix.postRotate(cameraSession[0].getDisplayOrientation());
        }
        matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
        matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
        matrix.invert(this.matrix);

        if (cameraThread != null) {
            if (!cameraThread.isReady()) {
                updateRotationMatrix.run();
            } else {
                cameraThread.postRunnable(updateRotationMatrix);
            }
        }
    }

    private Rect calculateTapArea(float x, float y, float coefficient) {
        int areaSize = Float.valueOf(focusAreaSize * coefficient).intValue();

        int left = clamp((int) x - areaSize / 2, 0, getWidth() - areaSize);
        int top = clamp((int) y - areaSize / 2, 0, getHeight() - areaSize);

        RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
        matrix.mapRect(rectF);

        return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
    }

    private int clamp(int x, int min, int max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    public void focusToPoint(int x, int y, boolean visible) {
        focusToPoint(0, x, y, x, y, visible);
    }

    public void focusToPoint(int i, int x, int y, int vx, int vy, boolean visible) {
        Rect focusRect = calculateTapArea(x, y, 1f);
        Rect meteringRect = calculateTapArea(x, y, 1.5f);

        if (cameraSession[i] != null) {
            cameraSession[i].focusToRect(focusRect, meteringRect);
        }
        if (visible) {
            focusProgress = 0.0f;
            innerAlpha = 1.0f;
            outerAlpha = 1.0f;
            cx = vx;
            cy = vy;
            lastDrawTime = System.currentTimeMillis();
            invalidate();
        }
    }

    public void focusToPoint(int x, int y) {
        focusToPoint(x, y, true);
    }

    public void setZoom(float value) {
        if (cameraSession[0] != null) {
            cameraSession[0].setZoom(value);
        }
    }

    public void setDelegate(CameraViewDelegate cameraViewDelegate) {
        delegate = cameraViewDelegate;
    }

    public boolean isInited() {
        return inited;
    }

    public CameraSession getCameraSession() {
        return getCameraSession(0);
    }

    public CameraSession getCameraSession(int i) {
        return cameraSession[i];
    }

    public CameraSession getCameraSessionRecording() {
        return cameraSessionRecording;
    }

    public void destroy(boolean async, final Runnable beforeDestroyRunnable) {
        for (int i = 0; i < 2; ++i) {
            if (cameraSession[i] != null) {
                cameraSession[i].destroy();
                CameraController.getInstance().close(cameraSession[i], !async ? new CountDownLatch(1) : null, beforeDestroyRunnable);
            }
        }
    }

    public Matrix getMatrix() {
        return txform;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (focusProgress != 1.0f || innerAlpha != 0.0f || outerAlpha != 0.0f) {
            int baseRad = AndroidUtilities.dp(30);
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastDrawTime;
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            lastDrawTime = newTime;
            outerPaint.setAlpha((int) (interpolator.getInterpolation(outerAlpha) * 255));
            innerPaint.setAlpha((int) (interpolator.getInterpolation(innerAlpha) * 127));
            float interpolated = interpolator.getInterpolation(focusProgress);
            canvas.drawCircle(cx, cy, baseRad + baseRad * (1.0f - interpolated), outerPaint);
            canvas.drawCircle(cx, cy, baseRad * interpolated, innerPaint);

            if (focusProgress < 1) {
                focusProgress += dt / 200.0f;
                if (focusProgress > 1) {
                    focusProgress = 1;
                }
                invalidate();
            } else if (innerAlpha != 0) {
                innerAlpha -= dt / 150.0f;
                if (innerAlpha < 0) {
                    innerAlpha = 0;
                }
                invalidate();
            } else if (outerAlpha != 0) {
                outerAlpha -= dt / 150.0f;
                if (outerAlpha < 0) {
                    outerAlpha = 0;
                }
                invalidate();
            }
        }
        return result;
    }

    private float takePictureProgress = 1f;

    public void startTakePictureAnimation(boolean haptic) {
        takePictureProgress = 0;
        invalidate();
        if (haptic) {
            runHaptic();
        }
    }

    public void runHaptic() {
        long[] vibrationWaveFormDurationPattern = {0, 1};
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            final Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            VibrationEffect vibrationEffect = VibrationEffect.createWaveform(vibrationWaveFormDurationPattern, -1);
            vibrator.cancel();
            vibrator.vibrate(vibrationEffect);
        } else {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }


    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (flipAnimator != null) {
            canvas.drawColor(Color.BLACK);
        }
        super.dispatchDraw(canvas);
        if (takePictureProgress != 1f) {
            takePictureProgress += 16 / 150f;
            if (takePictureProgress > 1f) {
                takePictureProgress = 1f;
            } else {
                invalidate();
            }
            canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) ((1f - takePictureProgress) * 150)));
        }
    }

    private int videoWidth;
    private int videoHeight;

    public int getVideoWidth() {
        return videoWidth;
    }
    public int getVideoHeight() {
        return videoHeight;
    }

    private int[] position = new int[2];
    private int[][] cameraTexture = new int[2][1];
    private int[] oldCameraTexture = new int[1];
    private VideoRecorder videoEncoder;

    private volatile float pixelW, pixelH;
    private volatile float pixelDualW, pixelDualH;
    private volatile float lastShapeTo;
    private volatile float shapeValue;

    public class CameraGLThread extends DispatchQueue {

        private final static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private final static int EGL_OPENGL_ES2_BIT = 4;
        private SurfaceTexture surfaceTexture;
        private EGL10 egl10;
        private EGLDisplay eglDisplay;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private EGLConfig eglConfig;
        private boolean initied;

        private CameraSession currentSession[] = new CameraSession[2];

        private final SurfaceTexture[] cameraSurface = new SurfaceTexture[2];

        private final int DO_RENDER_MESSAGE = 0;
        private final int DO_SHUTDOWN_MESSAGE = 1;
        private final int DO_REINIT_MESSAGE = 2;
        private final int DO_SETSESSION_MESSAGE = 3;
        private final int DO_START_RECORDING = 4;
        private final int DO_STOP_RECORDING = 5;

        private final int DO_DUAL_START = 6;
        private final int DO_DUAL_MOVE = 7;
        private final int DO_DUAL_FLIP = 8;
        private final int DO_DUAL_TOGGLE_SHAPE = 9;
        private final int DO_DUAL_END = 10;
        private final int BLUR_CAMERA1 = 11;

        private int drawProgram;
        private int vertexMatrixHandle;
        private int textureMatrixHandle;
        private int cameraMatrixHandle;
        private int oppositeCameraMatrixHandle;
        private int positionHandle;
        private int textureHandle;
        private int roundRadiusHandle;
        private int pixelHandle;
        private int dualHandle;
        private int scaleHandle;
        private int blurHandle;
        private int alphaHandle;
        private int crossfadeHandle;
        private int shapeFromHandle;
        private int shapeToHandle;
        private int shapeHandle;

        private boolean initDual, initDualReverse;
        private Matrix initDualMatrix;
        private boolean recording;
        private boolean needRecord;

        private int cameraId[] = new int[] { -1, -1 };

        private final float[] verticesData = {
            -1.0f, -1.0f, 0,
            1.0f, -1.0f, 0,
            -1.0f, 1.0f, 0,
            1.0f, 1.0f, 0
        };

        //private InstantCameraView.VideoRecorder videoEncoder;

        public CameraGLThread(SurfaceTexture surface) {
            super("CameraGLThread");
            surfaceTexture = surface;
            initDual = dual;
            initDualReverse = !isFrontface;
            initDualMatrix = dualMatrix;
        }

        private boolean initGL() {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("CameraView " + "start init gl");
            }
            egl10 = (EGL10) EGLContext.getEGL();

            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                eglDisplay = null;
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
                    EGL10.EGL_ALPHA_SIZE, 0,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };
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
            if (eglContext == null || eglContext == EGL10.EGL_NO_CONTEXT) {
                eglContext = null;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            if (surfaceTexture != null) {
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
            GL gl = eglContext.getGL();

            android.opengl.Matrix.setIdentityM(mSTMatrix[0], 0);

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, RLottieDrawable.readRes(null, R.raw.camera_vert));
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, RLottieDrawable.readRes(null, R.raw.camera_frag));
            if (vertexShader != 0 && fragmentShader != 0) {
                drawProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(drawProgram, vertexShader);
                GLES20.glAttachShader(drawProgram, fragmentShader);
                GLES20.glLinkProgram(drawProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(drawProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("failed link shader");
                    }
                    GLES20.glDeleteProgram(drawProgram);
                    drawProgram = 0;
                } else {
                    positionHandle = GLES20.glGetAttribLocation(drawProgram, "aPosition");
                    textureHandle = GLES20.glGetAttribLocation(drawProgram, "aTextureCoord");
                    vertexMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uMVPMatrix");
                    textureMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uSTMatrix");
                    cameraMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "cameraMatrix");
                    oppositeCameraMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "oppositeCameraMatrix");

                    roundRadiusHandle = GLES20.glGetUniformLocation(drawProgram, "roundRadius");
                    pixelHandle = GLES20.glGetUniformLocation(drawProgram, "pixelWH");
                    dualHandle = GLES20.glGetUniformLocation(drawProgram, "dual");
                    scaleHandle = GLES20.glGetUniformLocation(drawProgram, "scale");
                    blurHandle = GLES20.glGetUniformLocation(drawProgram, "blur");
                    alphaHandle = GLES20.glGetUniformLocation(drawProgram, "alpha");
                    crossfadeHandle = GLES20.glGetUniformLocation(drawProgram, "crossfade");
                    shapeFromHandle = GLES20.glGetUniformLocation(drawProgram, "shapeFrom");
                    shapeToHandle = GLES20.glGetUniformLocation(drawProgram, "shapeTo");
                    shapeHandle = GLES20.glGetUniformLocation(drawProgram, "shapeT");
                }
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("failed creating shader");
                }
                finish();
                return false;
            }

            GLES20.glGenTextures(1, cameraTexture[0], 0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[0][0]);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            android.opengl.Matrix.setIdentityM(mMVPMatrix[0], 0);

            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("gl initied");
            }

            float tX = 1.0f / 2.0f;
            float tY = 1.0f / 2.0f;
            float[] texData = {
                    0.5f - tX, 0.5f - tY,
                    0.5f + tX, 0.5f - tY,
                    0.5f - tX, 0.5f + tY,
                    0.5f + tX, 0.5f + tY
            };

            vertexBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertexBuffer.put(verticesData).position(0);

            textureBuffer = ByteBuffer.allocateDirect(texData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            textureBuffer.put(texData).position(0);

            cameraSurface[0] = new SurfaceTexture(cameraTexture[0][0]);
            cameraSurface[0].setOnFrameAvailableListener(this::updTex);

            if (initDual) {
                GLES20.glGenTextures(1, cameraTexture[1], 0);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[1][0]);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

                cameraSurface[1] = new SurfaceTexture(cameraTexture[1][0]);
                cameraSurface[1].setOnFrameAvailableListener(this::updTex);

            }

            if (initDual) {
                if (initDualReverse) {
                    createCamera(cameraSurface[1], 1);
                    createCamera(cameraSurface[0], 0);
                } else {
                    createCamera(cameraSurface[0], 0);
                    createCamera(cameraSurface[1], 1);
                }
            } else {
                createCamera(cameraSurface[0], 0);
            }

            Matrix simpleMatrix = new Matrix();
            simpleMatrix.reset();
            getValues(simpleMatrix, cameraMatrix[0]);
            if (initDualMatrix != null) {
                getValues(initDualMatrix, cameraMatrix[1]);
            } else {
                getValues(simpleMatrix, cameraMatrix[1]);
            }

            lastShapeTo = shapeTo;

            return true;
        }

        private void updTex(SurfaceTexture surfaceTexture) {
            if (surfaceTexture == cameraSurface[0]) {
                if (!ignoreCamera1Upd && System.currentTimeMillis() > camera1AppearedUntil) {
                    camera1Appeared = true;
                }
                requestRender(true, false);
            } else if (surfaceTexture == cameraSurface[1]) {
                if (!dualAppeared) {
                    synchronized (layoutLock) {
                        dualCameraAppeared = true;
                        addToDualWait(1200L);
                    }
                }
                dualAppeared = true;
                requestRender(false, true);
            }
        }

        public void reinitForNewCamera() {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_REINIT_MESSAGE, info[0].cameraId), 0);
            }
        }

        public void finish() {
            if (cameraSurface != null) {
                for (int i = 0; i < cameraSurface.length; ++i) {
                    if (cameraSurface[i] != null) {
                        cameraSurface[i].setOnFrameAvailableListener(null);
                        cameraSurface[i].release();
                        cameraSurface[i] = null;
                    }
                }
            }
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

        public void setCurrentSession(CameraSession session, int i) {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_SETSESSION_MESSAGE, i, 0, session), 0);
            }
        }

        private boolean crossfading;
        private final AnimatedFloat crossfade = new AnimatedFloat(() -> this.requestRender(false, false), 560, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat camera1Appear = new AnimatedFloat(1f, () -> this.requestRender(false, false), 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat dualAppear = new AnimatedFloat(() -> this.requestRender(false, false), 340, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat shape = new AnimatedFloat(() -> this.requestRender(false, false), 340, CubicBezierInterpolator.EASE_OUT_QUINT);
        private boolean dualAppeared, camera1Appeared, ignoreCamera1Upd;
        private long camera1AppearedUntil;
        private float shapeTo = MessagesController.getGlobalMainSettings().getInt("dualshape", 0);

        final int array[] = new int[1];

        private void onDraw(int cameraId1, int cameraId2, boolean updateTexImage1, boolean updateTexImage2) {
            if (!initied) {
                return;
            }

            if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
                if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                    }
                    return;
                }
            }

            final boolean waitingForCamera1;
            final boolean dual;
            synchronized (layoutLock) {
                dual = CameraView.this.dual;
                waitingForCamera1 = !camera1Appeared;
            }

            if ((updateTexImage1 || updateTexImage2) && !waitingForCamera1) {
                updateTexImage1 = updateTexImage2 = true;
            }

            if (updateTexImage1) {
                try {
                    if (cameraSurface[0] != null && cameraId1 >= 0) {
                        cameraSurface[0].updateTexImage();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            if (updateTexImage2) {
                try {
                    if (cameraSurface[1] != null && cameraId2 >= 0) {
                        cameraSurface[1].updateTexImage();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            final boolean shouldRenderFrame;
            synchronized (layoutLock) {
                if (fpsLimit <= 0) {
                    shouldRenderFrame = true;
                } else {
                    final long currentTimeNs = System.nanoTime();
                    if (currentTimeNs < nextFrameTimeNs) {
                        shouldRenderFrame = false;
                    } else {
                        nextFrameTimeNs += (long) (TimeUnit.SECONDS.toNanos(1) / fpsLimit);;
                        // The time for the next frame should always be in the future.
                        nextFrameTimeNs = Math.max(nextFrameTimeNs, currentTimeNs);
                        shouldRenderFrame = true;
                    }
                }
            }

            if (currentSession[0] == null || currentSession[0].cameraInfo.cameraId != cameraId1) {
                return;
            }

            if (recording && videoEncoder != null && (updateTexImage1 || updateTexImage2)) {
                videoEncoder.frameAvailable(cameraSurface[0], cameraId1, System.nanoTime());
            }

            if (!shouldRenderFrame) {
                return;
            }

            egl10.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_WIDTH, array);
            int drawnWidth = array[0];
            egl10.eglQuerySurface(eglDisplay, eglSurface, EGL10.EGL_HEIGHT, array);
            int drawnHeight = array[0];

            GLES20.glViewport(0, 0, drawnWidth, drawnHeight);
            if (dual) {
                GLES20.glClearColor(0.f, 0.f, 0.f, 1.f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            }
            shapeValue = shape.set(shapeTo);
            final float crossfade = lastCrossfadeValue = this.crossfade.set(0f);
            final float dualScale = dualAppear.set(dualAppeared ? 1f : 0f);
            final float camera1Blur = 1f - camera1Appear.set(camera1Appeared);
            if (crossfade <= 0) {
                crossfading = false;
            }
            for (int a = -1; a < 2; ++a) {
                if (a == -1 && !crossfading) {
                    continue;
                }
                final int i = a < 0 ? 1 : a;
                if (cameraSurface[i] == null) {
                    continue;
                }
                if (i != 0 && (currentSession[i] == null || !currentSession[i].isInitied()) || i == 0 && cameraId1 < 0 && !dual || i == 1 && cameraId2 < 0) {
                    continue;
                }

                if (i == 0 && updateTexImage1 || i == 1 && updateTexImage2) {
                    cameraSurface[i].getTransformMatrix(mSTMatrix[i]);
                }

                GLES20.glUseProgram(drawProgram);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[i][0]);

                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);
                GLES20.glEnableVertexAttribArray(positionHandle);

                GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                GLES20.glEnableVertexAttribArray(textureHandle);

                GLES20.glUniformMatrix4fv(cameraMatrixHandle, 1, false, cameraMatrix[i], 0);
                GLES20.glUniformMatrix4fv(oppositeCameraMatrixHandle, 1, false, cameraMatrix[1 - i], 0);

                GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, mSTMatrix[i], 0);
                GLES20.glUniformMatrix4fv(vertexMatrixHandle, 1, false, mMVPMatrix[i], 0);
                if (i == 0) {
                    GLES20.glUniform2f(pixelHandle, pixelW, pixelH);
                    GLES20.glUniform1f(dualHandle, dual ? 1 : 0);
                } else {
                    GLES20.glUniform2f(pixelHandle, pixelDualW, pixelDualH);
                    GLES20.glUniform1f(dualHandle, 1f);
                }
                GLES20.glUniform1f(blurHandle, i == 0 ? camera1Blur : 0f);
                if (i == 1) {
                    GLES20.glUniform1f(alphaHandle, 1);
                    if (a < 0) {
                        GLES20.glUniform1f(roundRadiusHandle, 0);
                        GLES20.glUniform1f(scaleHandle, 1);
                        GLES20.glUniform1f(shapeFromHandle, 2);
                        GLES20.glUniform1f(shapeToHandle, 2);
                        GLES20.glUniform1f(shapeHandle, 0);
                        GLES20.glUniform1f(crossfadeHandle, 1);
                    } else if (!crossfading) {
                        GLES20.glUniform1f(roundRadiusHandle, AndroidUtilities.dp(16));
                        GLES20.glUniform1f(scaleHandle, dualScale);
                        GLES20.glUniform1f(shapeFromHandle, (float) Math.floor(shapeValue));
                        GLES20.glUniform1f(shapeToHandle, (float) Math.ceil(shapeValue));
                        GLES20.glUniform1f(shapeHandle, shapeValue - (float) Math.floor(shapeValue));
                        GLES20.glUniform1f(crossfadeHandle, 0);
                    } else {
                        GLES20.glUniform1f(roundRadiusHandle, AndroidUtilities.dp(16));
                        GLES20.glUniform1f(scaleHandle, 1f - crossfade);
                        GLES20.glUniform1f(shapeFromHandle, (float) Math.floor(shapeValue));
                        GLES20.glUniform1f(shapeToHandle, (float) Math.ceil(shapeValue));
                        GLES20.glUniform1f(shapeHandle, shapeValue - (float) Math.floor(shapeValue));
                        GLES20.glUniform1f(shapeHandle, crossfade);
                        GLES20.glUniform1f(crossfadeHandle, 0);
                    }
                } else {
                    GLES20.glUniform1f(alphaHandle, 1f);
                    if (crossfading) {
                        GLES20.glUniform1f(roundRadiusHandle, AndroidUtilities.lerp(AndroidUtilities.dp(12), AndroidUtilities.dp(16), crossfade));
                        GLES20.glUniform1f(scaleHandle, 1f);
                        GLES20.glUniform1f(shapeFromHandle, shapeTo);
                        GLES20.glUniform1f(shapeToHandle, 2);
                        GLES20.glUniform1f(shapeHandle, Utilities.clamp((1f - crossfade), 1, 0));
                        GLES20.glUniform1f(crossfadeHandle, crossfade);
                    } else {
                        GLES20.glUniform1f(roundRadiusHandle, 0);
                        GLES20.glUniform1f(scaleHandle, 1f);
                        GLES20.glUniform1f(shapeFromHandle, 2f);
                        GLES20.glUniform1f(shapeToHandle, 2f);
                        GLES20.glUniform1f(shapeHandle, 0f);
                        GLES20.glUniform1f(crossfadeHandle, 0f);
                    }
                }

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

                GLES20.glDisableVertexAttribArray(positionHandle);
                GLES20.glDisableVertexAttribArray(textureHandle);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
                GLES20.glUseProgram(0);
            }

            egl10.eglSwapBuffers(eglDisplay, eglSurface);

            synchronized (layoutLock) {
                if (!firstFrameRendered && !waitingForCamera1) {
                    firstFrameRendered = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        onFirstFrameRendered(0);
                    });
                }
                if (!firstFrame2Rendered && dualAppeared) {
                    firstFrame2Rendered = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        onFirstFrameRendered(1);
                    });
                }
            }
        }

        @Override
        public void run() {
            initied = initGL();
            super.run();
        }

        @Override
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;

            switch (what) {
                case DO_RENDER_MESSAGE:
                    onDraw(inputMessage.arg1, inputMessage.arg2, inputMessage.obj == updateTexBoth || inputMessage.obj == updateTex1, inputMessage.obj == updateTexBoth || inputMessage.obj == updateTex2);
                    break;
                case DO_SHUTDOWN_MESSAGE:
                    finish();
                    if (recording) {
                        videoEncoder.stopRecording(inputMessage.arg1);
                    }
                    Looper looper = Looper.myLooper();
                    if (looper != null) {
                        looper.quit();
                    }
                    break;
                case DO_DUAL_START:
                case DO_REINIT_MESSAGE: {
                    final int i = what == DO_REINIT_MESSAGE ? 0 : 1;

                    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("CameraView " + "eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                        }
                        return;
                    }

                    if (cameraSurface[i] != null) {
                        cameraSurface[i].getTransformMatrix(moldSTMatrix[i]);
                        cameraSurface[i].setOnFrameAvailableListener(null);
                        cameraSurface[i].release();
                        cameraSurface[i] = null;
                    }

                    if (cameraTexture[i][0] == 0) {
                        GLES20.glGenTextures(1, cameraTexture[i], 0);
                    }

                    cameraId[i] = inputMessage.arg1;

                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[i][0]);
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

                    if (i == 1) {
                        applyDualMatrix((Matrix) inputMessage.obj);
                    }

                    cameraSurface[i] = new SurfaceTexture(cameraTexture[i][0]);
                    cameraSurface[i].setOnFrameAvailableListener(this::updTex);
                    if (ignoreCamera1Upd) {
                        camera1Appeared = false;
                        camera1AppearedUntil = System.currentTimeMillis() + 60L;
                        ignoreCamera1Upd = false;
                    }
                    createCamera(cameraSurface[i], i);

                    if (i == 1) {
                        dualAppeared = false;
                        synchronized (layoutLock) {
                            dualCameraAppeared = false;
                            firstFrame2Rendered = false;
                        }
                        dualAppear.set(0f, true);
                    }
                    break;
                }
                case DO_SETSESSION_MESSAGE: {
                    final int i = inputMessage.arg1;
                    CameraSession newSession = (CameraSession) inputMessage.obj;
                    if (newSession == null) {
                        return;
                    }
                    if (currentSession[i] != newSession) {
                        currentSession[i] = newSession;
                        cameraId[i] = newSession.cameraInfo.cameraId;
                    }
//                    currentSession[i].updateRotation();
                    int rotationAngle = currentSession[i].getWorldAngle();
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("CameraView " + "set gl renderer session " + i + " angle=" + rotationAngle);
                    }
                    android.opengl.Matrix.setIdentityM(mMVPMatrix[i], 0);
                    if (rotationAngle != 0) {
                        android.opengl.Matrix.rotateM(mMVPMatrix[i], 0, rotationAngle, 0, 0, 1);
                    }
                    break;
                }
                case DO_START_RECORDING: {
                    if (!initied) {
                        return;
                    }
                    recordFile = (File) inputMessage.obj;
                    videoEncoder = new VideoRecorder();
                    recording = true;
                    videoEncoder.startRecording(recordFile,  EGL14.eglGetCurrentContext());
                    break;
                }
                case DO_STOP_RECORDING: {
                    if (videoEncoder != null) {
                        videoEncoder.stopRecording(0);
                        videoEncoder = null;
                    }
                    recording = false;
                    break;
                }
                case DO_DUAL_END: {
                    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("CameraView " + "eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                        }
                        return;
                    }
                    if (cameraSurface[1] != null) {
                        cameraSurface[1].getTransformMatrix(moldSTMatrix[1]);
                        cameraSurface[1].setOnFrameAvailableListener(null);
                        cameraSurface[1].release();
                        cameraSurface[1] = null;
                    }
                    if (cameraTexture[1][0] != 0) {
                        GLES20.glDeleteTextures(1, cameraTexture[1], 0);
                        cameraTexture[1][0] = 0;
                    }
                    currentSession[1] = null;
                    cameraId[1] = -1;
                    requestRender(false, false);
                    break;
                }
                case DO_DUAL_MOVE: {
                    applyDualMatrix((Matrix) inputMessage.obj);
                    requestRender(false, false);
                    break;
                }
                case DO_DUAL_TOGGLE_SHAPE: {
                    shapeTo++;
                    lastShapeTo = shapeTo;
                    requestRender(false, false);
                    break;
                }
                case DO_DUAL_FLIP: {
                    int cameraId0 = cameraId[0];
                    cameraId[0] = cameraId[1];
                    cameraId[1] = cameraId0;

                    CameraSession cameraSession0 = currentSession[0];
                    currentSession[0] = currentSession[1];
                    currentSession[1] = cameraSession0;

                    int[] cameraTexture0 = cameraTexture[0];
                    cameraTexture[0] = cameraTexture[1];
                    cameraTexture[1] = cameraTexture0;

                    SurfaceTexture cameraSurface0 = cameraSurface[0];
                    cameraSurface[0] = cameraSurface[1];
                    cameraSurface[1] = cameraSurface0;

                    float[] mMVPMatrix0 = mMVPMatrix[0];
                    mMVPMatrix[0] = mMVPMatrix[1];
                    mMVPMatrix[1] = mMVPMatrix0;

                    float[] mSTMatrix0 = mSTMatrix[0];
                    mSTMatrix[0] = mSTMatrix[1];
                    mSTMatrix[1] = mSTMatrix0;

                    float[] moldSTMatrix0 = moldSTMatrix[0];
                    moldSTMatrix[0] = moldSTMatrix[1];
                    moldSTMatrix[1] = moldSTMatrix0;

                    crossfading = true;
                    lastCrossfadeValue = 1f;
                    crossfade.set(1f, true);

                    requestRender(true, true);
                    break;
                }
                case BLUR_CAMERA1: {
                    camera1Appeared = false;
                    ignoreCamera1Upd = true;
                    camera1AppearedUntil = System.currentTimeMillis() + 60L;
                    requestRender(false, false);
                    break;
                }
            }
        }

//        private final float[] tempVertices = new float[6];
        private void applyDualMatrix(Matrix matrix) {
//            tempVertices[0] = tempVertices[1] = 0;
//            tempVertices[2] = pixelW;
//            tempVertices[3] = 0;
//            tempVertices[4] = 0;
//            tempVertices[5] = pixelH;
//            matrix.mapPoints(tempVertices);
//            pixelDualW = MathUtils.distance(tempVertices[0], tempVertices[1], tempVertices[2], tempVertices[3]);
//            pixelDualH = MathUtils.distance(tempVertices[0], tempVertices[1], tempVertices[4], tempVertices[5]);
            getValues(matrix, cameraMatrix[1]);
        }

        private float[] m3x3;
        private void getValues(Matrix matrix3x3, float[] m4x4) {
            if (m3x3 == null) {
                m3x3 = new float[9];
            }
            matrix3x3.getValues(m3x3);

            m4x4[0] = m3x3[0];
            m4x4[1] = m3x3[3];
            m4x4[2] = 0;
            m4x4[3] = m3x3[6];

            m4x4[4] = m3x3[1];
            m4x4[5] = m3x3[4];
            m4x4[6] = 0;
            m4x4[7] = m3x3[7];

            m4x4[8] = 0;
            m4x4[9] = 0;
            m4x4[10] = 1;
            m4x4[11] = 0;

            m4x4[12] = m3x3[2];
            m4x4[13] = m3x3[5];
            m4x4[14] = 0;
            m4x4[15] = m3x3[8];
        }


        public void shutdown(int send) {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_SHUTDOWN_MESSAGE, send, 0), 0);
            }
        }

        private long pausedTime;
        public void pause(long duration) {
            pausedTime = System.currentTimeMillis() + duration;
        }

        private final Object updateTex1 = new Object();
        private final Object updateTex2 = new Object();
        private final Object updateTexBoth = new Object();
        public void requestRender(boolean updateTexImage1, boolean updateTexImage2) {
            if (pausedTime > 0 && System.currentTimeMillis() < pausedTime) {
                return;
            }
            if (!updateTexImage1 && !updateTexImage2 && recording) {
                // todo: currently video timestamps are messed up in that case
                return;
            }
            Handler handler = getHandler();
            if (handler != null) {
                if ((updateTexImage1 || updateTexImage2) && handler.hasMessages(DO_RENDER_MESSAGE, updateTexBoth)) {
                    return;
                }
                if (!updateTexImage1 && handler.hasMessages(DO_RENDER_MESSAGE, updateTex1)) {
                    updateTexImage1 = true;
                }
                if (!updateTexImage2 && handler.hasMessages(DO_RENDER_MESSAGE, updateTex2)) {
                    updateTexImage2 = true;
                }
                handler.removeMessages(DO_RENDER_MESSAGE);
                sendMessage(handler.obtainMessage(DO_RENDER_MESSAGE, cameraId[0], cameraId[1], updateTexImage1 && updateTexImage2 ? updateTexBoth : (updateTexImage1 ? updateTex1 : updateTex2)), 0);
            }
        }

        public boolean startRecording(File path) {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_START_RECORDING, path), 0);
                return false;
            }
            return true;
        }

        public void stopRecording() {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_STOP_RECORDING), 0);
            }
        }
    }

    private void onFirstFrameRendered(int i) {
        if (i == 0) {
            flipping = false;
            if (blurredStubView.getVisibility() == View.VISIBLE) {
                blurredStubView.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        blurredStubView.setVisibility(View.GONE);
                    }
                }).setDuration(120).start();
            }
        } else {
            onDualCameraSuccess();
        }
    }

    protected void onDualCameraSuccess() {

    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e(GLES20.glGetShaderInfoLog(shader));
            }
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    private void createCamera(final SurfaceTexture surfaceTexture, int i) {
        AndroidUtilities.runOnUIThread(() -> {
            CameraGLThread cameraThread = this.cameraThread;
            if (cameraThread == null) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("CameraView " + "create camera session " + i);
            }
            if (previewSize[i] == null) {
                updateCameraInfoSize(i);
            }
            if (previewSize[i] == null) {
                return;
            }
            surfaceTexture.setDefaultBufferSize(previewSize[i].getWidth(), previewSize[i].getHeight());

            cameraSession[i] = new CameraSession(info[i], previewSize[i], pictureSize[i], ImageFormat.JPEG, false);
            cameraSession[i].setCurrentFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            cameraThread.setCurrentSession(cameraSession[i], i);
            requestLayout();

            CameraController.getInstance().open(cameraSession[i], surfaceTexture, () -> {
                if (cameraSession[i] != null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("CameraView " + "camera initied " + i);
                    }
                    cameraSession[i].setInitied();
                    requestLayout();
                }

                if (dual && i == 1 && initFirstCameraAfterSecond) {
                    initFirstCameraAfterSecond = false;
                    AndroidUtilities.runOnUIThread(() -> {
                        updateCameraInfoSize(0);
                        cameraThread.reinitForNewCamera();
                        addToDualWait(350L);
                    });
                }
            }, () -> cameraThread.setCurrentSession(cameraSession[i], i));
        });
    }


    private class VideoRecorder implements Runnable {

        private static final String VIDEO_MIME_TYPE = "video/hevc";
        private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
        private static final int FRAME_RATE = 30;
        private static final int IFRAME_INTERVAL = 1;

        private File videoFile;
        private File fileToWrite;
        private boolean writingToDifferentFile;
        private int videoBitrate;
        private boolean videoConvertFirstWrite = true;
        private boolean blendEnabled;

        private Surface surface;
        private android.opengl.EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
        private android.opengl.EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        private android.opengl.EGLContext sharedEglContext;
        private android.opengl.EGLConfig eglConfig;
        private android.opengl.EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

        private MediaCodec videoEncoder;
        private MediaCodec audioEncoder;

        private int prependHeaderSize;
        private boolean firstEncode;

        private MediaCodec.BufferInfo videoBufferInfo;
        private MediaCodec.BufferInfo audioBufferInfo;
        private MP4Builder mediaMuxer;
        private ArrayList<InstantCameraView.AudioBufferInfo> buffersToWrite = new ArrayList<>();
        private int videoTrackIndex = -5;
        private int audioTrackIndex = -5;

        private long lastCommitedFrameTime;
        private long audioStartTime = -1;

        private long currentTimestamp = 0;
        private long lastTimestamp = -1;

        private volatile EncoderHandler handler;

        private final Object sync = new Object();
        private boolean ready;
        private volatile boolean running;
        private volatile int sendWhenDone;
        private long skippedTime;
        private boolean skippedFirst;

        private long desyncTime;
        private long videoFirst = -1;
        private long videoLast;
        private long audioFirst = -1;
        private boolean audioStopedByTime;

        private int drawProgram;
        private int vertexMatrixHandle;
        private int textureMatrixHandle;
        private int cameraMatrixHandle;
        private int oppositeCameraMatrixHandle;
        private int positionHandle;
        private int textureHandle;
        private int roundRadiusHandle;
        private int pixelHandle;
        private int dualHandle;
        private int crossfadeHandle;
        private int shapeFromHandle, shapeToHandle, shapeHandle;
        private int alphaHandle;
        private int scaleHandle;
        private int blurHandle;
        private int zeroTimeStamps;
        private Integer lastCameraId = 0;

        private AudioRecord audioRecorder;
        private FloatBuffer textureBuffer;

        private ArrayBlockingQueue<InstantCameraView.AudioBufferInfo> buffers = new ArrayBlockingQueue<>(10);
        private ArrayList<Bitmap> keyframeThumbs = new ArrayList<>();

        DispatchQueue fileWriteQueue;

        private Runnable recorderRunnable = new Runnable() {

            @Override
            public void run() {
                long audioPresentationTimeUs = -1;
                int readResult;
                boolean done = false;
                while (!done) {
                    if (!running && audioRecorder.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
                        try {
                            audioRecorder.stop();
                        } catch (Exception e) {
                            done = true;
                        }
                        if (sendWhenDone == 0) {
                            break;
                        }
                    }
                    InstantCameraView.AudioBufferInfo buffer;
                    if (buffers.isEmpty()) {
                        buffer = new InstantCameraView.AudioBufferInfo();
                    } else {
                        buffer = buffers.poll();
                    }
                    buffer.lastWroteBuffer = 0;
                    buffer.results = InstantCameraView.AudioBufferInfo.MAX_SAMPLES;
                    for (int a = 0; a < InstantCameraView.AudioBufferInfo.MAX_SAMPLES; a++) {
                        if (audioPresentationTimeUs == -1) {
                            audioPresentationTimeUs = System.nanoTime() / 1000;
                        }

                        ByteBuffer byteBuffer = buffer.buffer[a];
                        byteBuffer.rewind();
                        readResult = audioRecorder.read(byteBuffer, 2048);

                        if (readResult <= 0) {
                            buffer.results = a;
                            if (!running) {
                                buffer.last = true;
                            }
                            break;
                        }
                        buffer.offset[a] = audioPresentationTimeUs;
                        buffer.read[a] = readResult;
                        int bufferDurationUs = 1000000 * readResult / audioSampleRate / 2;
                        audioPresentationTimeUs += bufferDurationUs;
                    }
                    if (buffer.results >= 0 || buffer.last) {
                        if (!running && buffer.results < InstantCameraView.AudioBufferInfo.MAX_SAMPLES) {
                            done = true;
                        }
                        handler.sendMessage(handler.obtainMessage(MSG_AUDIOFRAME_AVAILABLE, buffer));
                    } else {
                        if (!running) {
                            done = true;
                        } else {
                            try {
                                buffers.put(buffer);
                            } catch (Exception ignore) {

                            }
                        }
                    }
                }
                try {
                    audioRecorder.release();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                handler.sendMessage(handler.obtainMessage(MSG_STOP_RECORDING, sendWhenDone, 0));
            }
        };
        private String outputMimeType;

        public void startRecording(File outputFile, android.opengl.EGLContext sharedContext) {
            String model = Build.DEVICE;
            if (model == null) {
                model = "";
            }

            Size pictureSize;
            int bitrate;
            pictureSize = previewSize[0];
            if (Math.min(pictureSize.mHeight, pictureSize.mWidth) >= 720) {
                bitrate = 3500000;
            } else {
                bitrate = 1800000;
            }

            videoFile = outputFile;

            if (cameraSession[0].getWorldAngle() == 90 || cameraSession[0].getWorldAngle() == 270) {
                videoWidth = pictureSize.getWidth();
                videoHeight = pictureSize.getHeight();
            } else {
                videoWidth = pictureSize.getHeight();
                videoHeight = pictureSize.getWidth();
            }
            videoBitrate = bitrate;
            sharedEglContext = sharedContext;
            synchronized (sync) {
                if (running) {
                    return;
                }
                running = true;
                Thread thread = new Thread(this, "TextureMovieEncoder");
                thread.setPriority(Thread.MAX_PRIORITY);
                thread.start();
                while (!ready) {
                    try {
                        sync.wait();
                    } catch (InterruptedException ie) {
                        // ignore
                    }
                }
            }
            fileWriteQueue = new DispatchQueue("VR_FileWriteQueue");
            fileWriteQueue.setPriority(Thread.MAX_PRIORITY);

            keyframeThumbs.clear();
            handler.sendMessage(handler.obtainMessage(MSG_START_RECORDING));
        }

        public void stopRecording(int send) {
            handler.sendMessage(handler.obtainMessage(MSG_STOP_RECORDING, send, 0));
        }

        public void frameAvailable(SurfaceTexture st, Integer cameraId, long timestampInternal) {
            synchronized (sync) {
                if (!ready) {
                    return;
                }
            }

            long timestamp = st.getTimestamp();
            if (timestamp == 0) {
                zeroTimeStamps++;
                if (zeroTimeStamps > 1) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("CameraView " + "fix timestamp enabled");
                    }
                    timestamp = timestampInternal;
                } else {
                    return;
                }
            } else {
                zeroTimeStamps = 0;
            }

            handler.sendMessage(handler.obtainMessage(MSG_VIDEOFRAME_AVAILABLE, (int) (timestamp >> 32), (int) timestamp, cameraId));
        }

        @Override
        public void run() {
            Looper.prepare();
            synchronized (sync) {
                handler = new EncoderHandler(this);
                ready = true;
                sync.notify();
            }
            Looper.loop();

            synchronized (sync) {
                ready = false;
            }
        }

        private void handleAudioFrameAvailable(InstantCameraView.AudioBufferInfo input) {
            if (audioStopedByTime) {
                return;
            }
            buffersToWrite.add(input);
            if (audioFirst == -1) {
                if (videoFirst == -1) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("CameraView " + "video record not yet started");
                    }
                    return;
                }
                while (true) {
                    boolean ok = false;
                    for (int a = 0; a < input.results; a++) {
                        if (a == 0 && Math.abs(videoFirst - input.offset[a]) > 10000000L) {
                            desyncTime = videoFirst - input.offset[a];
                            audioFirst = input.offset[a];
                            ok = true;
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("CameraView " + "detected desync between audio and video " + desyncTime);
                            }
                            break;
                        }
                        if (input.offset[a] >= videoFirst) {
                            input.lastWroteBuffer = a;
                            audioFirst = input.offset[a];
                            ok = true;
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("CameraView " + "found first audio frame at " + a + " timestamp = " + input.offset[a]);
                            }
                            break;
                        } else {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("CameraView " + "ignore first audio frame at " + a + " timestamp = " + input.offset[a]);
                            }
                        }
                    }
                    if (!ok) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("CameraView " + "first audio frame not found, removing buffers " + input.results);
                        }
                        buffersToWrite.remove(input);
                    } else {
                        break;
                    }
                    if (!buffersToWrite.isEmpty()) {
                        input = buffersToWrite.get(0);
                    } else {
                        return;
                    }
                }
            }

            if (audioStartTime == -1) {
                audioStartTime = input.offset[input.lastWroteBuffer];
            }
            if (buffersToWrite.size() > 1) {
                input = buffersToWrite.get(0);
            }
            try {
                drainEncoder(false);
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                boolean isLast = false;
                while (input != null) {
                    int inputBufferIndex = audioEncoder.dequeueInputBuffer(0);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer;
                        if (Build.VERSION.SDK_INT >= 21) {
                            inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex);
                        } else {
                            ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
                            inputBuffer = inputBuffers[inputBufferIndex];
                            inputBuffer.clear();
                        }
                        long startWriteTime = input.offset[input.lastWroteBuffer];
                        for (int a = input.lastWroteBuffer; a <= input.results; a++) {
                            if (a < input.results) {
                                if (!running && input.offset[a] >= videoLast - desyncTime) {
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("CameraView " + "stop audio encoding because of stoped video recording at " + input.offset[a] + " last video " + videoLast);
                                    }
                                    audioStopedByTime = true;
                                    isLast = true;
                                    input = null;
                                    buffersToWrite.clear();
                                    break;
                                }
                                if (inputBuffer.remaining() < input.read[a]) {
                                    input.lastWroteBuffer = a;
                                    input = null;
                                    break;
                                }
                                inputBuffer.put(input.buffer[a]);
                            }
                            if (a >= input.results - 1) {
                                buffersToWrite.remove(input);
                                if (running) {
                                    buffers.put(input);
                                }
                                if (!buffersToWrite.isEmpty()) {
                                    input = buffersToWrite.get(0);
                                } else {
                                    isLast = input.last;
                                    input = null;
                                    break;
                                }
                            }
                        }
                        audioEncoder.queueInputBuffer(inputBufferIndex, 0, inputBuffer.position(), startWriteTime == 0 ? 0 : startWriteTime - audioStartTime, isLast ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        private void handleVideoFrameAvailable(long timestampNanos, Integer cameraId) {
            try {
                drainEncoder(false);
            } catch (Exception e) {
                FileLog.e(e);
            }
            long dt;
            long currentTime = System.currentTimeMillis();
            if (!lastCameraId.equals(cameraId)) {
                lastTimestamp = -1;
                lastCameraId = cameraId;
            }
            if (lastTimestamp == -1) {
                lastTimestamp = timestampNanos;
                if (currentTimestamp != 0) {
                    dt = (currentTime - lastCommitedFrameTime) * 1000000;
                } else {
                    dt = 0;
                }
            } else {
                dt = (timestampNanos - lastTimestamp);
                lastTimestamp = timestampNanos;
            }
            lastCommitedFrameTime = currentTime;
            if (!skippedFirst) {
                skippedTime += dt;
                if (skippedTime < 200000000) {
                    return;
                }
                skippedFirst = true;
            }
            currentTimestamp += dt;
            if (videoFirst == -1) {
                videoFirst = timestampNanos / 1000;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("CameraView " + "first video frame was at " + videoFirst);
                }
            }
            videoLast = timestampNanos;

            if (cameraTexture[1][0] != 0 && !blendEnabled) {
                GLES20.glEnable(GLES20.GL_BLEND);
                blendEnabled = true;
            }
            final boolean isDual = dual;
            if (isDual) {
                GLES20.glClearColor(0.f, 0.f, 0.f, 1.f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            }
            final float crossfade = lastCrossfadeValue;
            final boolean crossfading = crossfade > 0;
            for (int a = -1; a < 2; ++a) {
                if (a == -1 && !crossfading) {
                    continue;
                }
                final int i = a < 0 ? 1 : a;
                if (cameraTexture[i][0] == 0) {
                    continue;
                }

                GLES20.glUseProgram(drawProgram);
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);
                GLES20.glEnableVertexAttribArray(positionHandle);
                GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
                GLES20.glEnableVertexAttribArray(textureHandle);
                GLES20.glUniformMatrix4fv(vertexMatrixHandle, 1, false, mMVPMatrix[i], 0);

                GLES20.glUniformMatrix4fv(cameraMatrixHandle, 1, false, cameraMatrix[i], 0);
                GLES20.glUniformMatrix4fv(oppositeCameraMatrixHandle, 1, false, cameraMatrix[1 - i], 0);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, mSTMatrix[i], 0);

                GLES20.glUniform1f(blurHandle, 0);
                if (i == 0) {
                    GLES20.glUniform2f(pixelHandle, pixelW, pixelH);
                    GLES20.glUniform1f(dualHandle, isDual ? 1f : 0f);
                } else {
                    GLES20.glUniform2f(pixelHandle, pixelDualW, pixelDualH);
                    GLES20.glUniform1f(dualHandle, 1f);
                }
                if (i == 1) {
                    GLES20.glUniform1f(alphaHandle, 1);
                    if (a < 0) {
                        GLES20.glUniform1f(roundRadiusHandle, 0);
                        GLES20.glUniform1f(scaleHandle, 1);
                        GLES20.glUniform1f(shapeFromHandle, 2);
                        GLES20.glUniform1f(shapeToHandle, 2);
                        GLES20.glUniform1f(shapeHandle, 0);
                        GLES20.glUniform1f(crossfadeHandle, 1);
                    } else if (!crossfading) {
                        GLES20.glUniform1f(roundRadiusHandle, AndroidUtilities.dp(16));
                        GLES20.glUniform1f(scaleHandle, 1f);
                        GLES20.glUniform1f(shapeFromHandle, (float) Math.floor(shapeValue));
                        GLES20.glUniform1f(shapeToHandle, (float) Math.ceil(shapeValue));
                        GLES20.glUniform1f(shapeHandle, shapeValue - (float) Math.floor(shapeValue));
                        GLES20.glUniform1f(crossfadeHandle, 0);
                    } else {
                        GLES20.glUniform1f(roundRadiusHandle, AndroidUtilities.dp(16));
                        GLES20.glUniform1f(scaleHandle, 1f - crossfade);
                        GLES20.glUniform1f(shapeFromHandle, (float) Math.floor(shapeValue));
                        GLES20.glUniform1f(shapeToHandle, (float) Math.ceil(shapeValue));
                        GLES20.glUniform1f(shapeHandle, shapeValue - (float) Math.floor(shapeValue));
                        GLES20.glUniform1f(shapeHandle, crossfade);
                        GLES20.glUniform1f(crossfadeHandle, 0);
                    }
                } else {
                    GLES20.glUniform1f(alphaHandle, 1f);
                    if (crossfading) {
                        GLES20.glUniform1f(roundRadiusHandle, AndroidUtilities.lerp(AndroidUtilities.dp(12), AndroidUtilities.dp(16), crossfade));
                        GLES20.glUniform1f(scaleHandle, 1f);
                        GLES20.glUniform1f(shapeFromHandle, lastShapeTo);
                        GLES20.glUniform1f(shapeToHandle, 2);
                        GLES20.glUniform1f(shapeHandle, Utilities.clamp((1f - crossfade), 1, 0));
                        GLES20.glUniform1f(crossfadeHandle, crossfade);
                    } else {
                        GLES20.glUniform1f(roundRadiusHandle, 0);
                        GLES20.glUniform1f(scaleHandle, 1f);
                        GLES20.glUniform1f(shapeFromHandle, 2);
                        GLES20.glUniform1f(shapeToHandle, 2);
                        GLES20.glUniform1f(shapeHandle, 0);
                        GLES20.glUniform1f(crossfadeHandle, 0f);
                    }
                }
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[i][0]);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

                GLES20.glDisableVertexAttribArray(positionHandle);
                GLES20.glDisableVertexAttribArray(textureHandle);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
                GLES20.glUseProgram(0);
            }

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, currentTimestamp);
            EGL14.eglSwapBuffers(eglDisplay, eglSurface);
        }

        private void handleStopRecording(final int send) {
            if (running) {
                sendWhenDone = send;
                running = false;
                return;
            }
            try {
                drainEncoder(true);
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (videoEncoder != null) {
                try {
                    videoEncoder.stop();
                    videoEncoder.release();
                    videoEncoder = null;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (audioEncoder != null) {
                try {
                    audioEncoder.stop();
                    audioEncoder.release();
                    audioEncoder = null;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            CountDownLatch countDownLatch = new CountDownLatch(1);
            fileWriteQueue.postRunnable(() -> {
                try {
                    mediaMuxer.finishMovie();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                countDownLatch.countDown();
            });
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (writingToDifferentFile) {
                if (!fileToWrite.renameTo(videoFile)) {
                    FileLog.e("unable to rename file, try move file");
                    try {
                        AndroidUtilities.copyFile(fileToWrite, videoFile);
                        fileToWrite.delete();
                    } catch (IOException e) {
                        FileLog.e(e);
                        FileLog.e("unable to move file");
                    }
                }
            }

            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = EGL14.EGL_NO_SURFACE;
            if (surface != null) {
                surface.release();
                surface = null;
            }
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                EGL14.eglDestroyContext(eglDisplay, eglContext);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(eglDisplay);
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            eglContext = EGL14.EGL_NO_CONTEXT;
            eglConfig = null;
            handler.exit();

            AndroidUtilities.runOnUIThread(() -> {
                if (cameraSession[0] != null) {
                    cameraSession[0].stopVideoRecording();
                }
                if (cameraSession[1] != null) {
                    cameraSession[1].stopVideoRecording();
                }
                onRecordingFinishRunnable.run();
            });
        }

        private void prepareEncoder() {
            try {
                int recordBufferSize = AudioRecord.getMinBufferSize(audioSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (recordBufferSize <= 0) {
                    recordBufferSize = 3584;
                }
                int bufferSize = 2048 * 24;
                if (bufferSize < recordBufferSize) {
                    bufferSize = ((recordBufferSize / 2048) + 1) * 2048 * 2;
                }
                for (int a = 0; a < 3; a++) {
                    buffers.add(new InstantCameraView.AudioBufferInfo());
                }
                audioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, audioSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                audioRecorder.startRecording();
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("CameraView " + "initied audio record with channels " + audioRecorder.getChannelCount() + " sample rate = " + audioRecorder.getSampleRate() + " bufferSize = " + bufferSize);
                }
                Thread thread = new Thread(recorderRunnable);
                thread.setPriority(Thread.MAX_PRIORITY);
                thread.start();

                audioBufferInfo = new MediaCodec.BufferInfo();
                videoBufferInfo = new MediaCodec.BufferInfo();

                MediaFormat audioFormat = new MediaFormat();
                audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
                audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, audioSampleRate);
                audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
                audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, 32000);
                audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 2048 * InstantCameraView.AudioBufferInfo.MAX_SAMPLES);

                audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
                audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                audioEncoder.start();

                boolean shouldUseHevc = isStory;
                outputMimeType = shouldUseHevc ? "video/hevc" : "video/avc";
                try {
                    if (shouldUseHevc) {
                        String encoderName = SharedConfig.findGoodHevcEncoder();
                        if (encoderName != null) {
                            videoEncoder = MediaCodec.createByCodecName(encoderName);
                        }
                    } else {
                        outputMimeType = "video/avc";
                        videoEncoder = MediaCodec.createEncoderByType(outputMimeType);
                    }
                    if (outputMimeType.equals("video/hevc") && videoEncoder != null && !videoEncoder.getCodecInfo().isHardwareAccelerated()) {
                        FileLog.e("hevc encoder isn't hardware accelerated");
                        videoEncoder.release();
                        videoEncoder = null;
                    }
                } catch (Throwable e) {
                    FileLog.e("can't get hevc encoder");
                    FileLog.e(e);
                }
                if (videoEncoder == null && outputMimeType.equals("video/hevc")) {
                    outputMimeType = "video/avc";
                    videoEncoder = MediaCodec.createEncoderByType(outputMimeType);
                }
                firstEncode = true;

                MediaFormat format = MediaFormat.createVideoFormat(outputMimeType, videoWidth, videoHeight);

                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

                videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                surface = videoEncoder.createInputSurface();
                videoEncoder.start();

                boolean isSdCard = ImageLoader.isSdCardPath(videoFile);
                fileToWrite = videoFile;
                if (isSdCard) {
                    try {
                        fileToWrite = new File(ApplicationLoader.getFilesDirFixed(), "camera_tmp.mp4");
                        if (fileToWrite.exists()) {
                            fileToWrite.delete();
                        }
                        writingToDifferentFile = true;
                    } catch (Throwable e) {
                        FileLog.e(e);
                        fileToWrite = videoFile;
                        writingToDifferentFile = false;
                    }
                }

                Mp4Movie movie = new Mp4Movie();
                movie.setCacheFile(fileToWrite);
                movie.setRotation(0);
                movie.setSize(videoWidth, videoHeight);
                mediaMuxer = new MP4Builder().createMovie(movie, false, false);
                mediaMuxer.setAllowSyncFiles(false);

            } catch (Exception ioe) {
                throw new RuntimeException(ioe);
            }

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException("EGL already set up");
            }

            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException("unable to get EGL14 display");
            }
            int[] version = new int[2];
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                eglDisplay = null;
                throw new RuntimeException("unable to initialize EGL14");
            }

            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                int renderableType = EGL14.EGL_OPENGL_ES2_BIT;

                int[] attribList = {
                        EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_RENDERABLE_TYPE, renderableType,
                        0x3142, 1,
                        EGL14.EGL_NONE
                };
                android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
                int[] numConfigs = new int[1];
                if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
                    throw new RuntimeException("Unable to find a suitable EGLConfig");
                }

                int[] attrib2_list = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE
                };
                eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], sharedEglContext, attrib2_list, 0);
                eglConfig = configs[0];
            }

            int[] values = new int[1];
            EGL14.eglQueryContext(eglDisplay, eglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0);

            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                throw new IllegalStateException("surface already created");
            }

            int[] surfaceAttribs = {
                    EGL14.EGL_NONE
            };
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0);
            if (eglSurface == null) {
                throw new RuntimeException("surface was null");
            }

            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
                }
                throw new RuntimeException("eglMakeCurrent failed");
            }
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            float tX = 1.0f / 2.0f;
            float tY = 1.0f / 2.0f;

            float[] texData = {
                    0.5f - tX, 0.5f - tY,
                    0.5f + tX, 0.5f - tY,
                    0.5f - tX, 0.5f + tY,
                    0.5f + tX, 0.5f + tY
            };
            textureBuffer = ByteBuffer.allocateDirect(texData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            textureBuffer.put(texData).position(0);


            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, RLottieDrawable.readRes(null, R.raw.camera_vert));
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, RLottieDrawable.readRes(null, R.raw.camera_frag));
            if (vertexShader != 0 && fragmentShader != 0) {
                drawProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(drawProgram, vertexShader);
                GLES20.glAttachShader(drawProgram, fragmentShader);
                GLES20.glLinkProgram(drawProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(drawProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(drawProgram);
                    drawProgram = 0;
                } else {
                    positionHandle = GLES20.glGetAttribLocation(drawProgram, "aPosition");
                    textureHandle = GLES20.glGetAttribLocation(drawProgram, "aTextureCoord");
                    vertexMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uMVPMatrix");
                    textureMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uSTMatrix");
                    cameraMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "cameraMatrix");
                    oppositeCameraMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "oppositeCameraMatrix");

                    roundRadiusHandle = GLES20.glGetUniformLocation(drawProgram, "roundRadius");
                    pixelHandle = GLES20.glGetUniformLocation(drawProgram, "pixelWH");
                    dualHandle = GLES20.glGetUniformLocation(drawProgram, "dual");
                    scaleHandle = GLES20.glGetUniformLocation(drawProgram, "scale");
                    blurHandle = GLES20.glGetUniformLocation(drawProgram, "blur");
                    alphaHandle = GLES20.glGetUniformLocation(drawProgram, "alpha");
                    crossfadeHandle = GLES20.glGetUniformLocation(drawProgram, "crossfade");
                    shapeFromHandle = GLES20.glGetUniformLocation(drawProgram, "shapeFrom");
                    shapeToHandle = GLES20.glGetUniformLocation(drawProgram, "shapeTo");
                    shapeHandle = GLES20.glGetUniformLocation(drawProgram, "shapeT");
                }
            }
        }

        public Surface getInputSurface() {
            return surface;
        }

        public void drainEncoder(boolean endOfStream) throws Exception {
            if (endOfStream) {
                videoEncoder.signalEndOfInputStream();
            }

            ByteBuffer[] encoderOutputBuffers = null;
            if (Build.VERSION.SDK_INT < 21) {
                encoderOutputBuffers = videoEncoder.getOutputBuffers();
            }
            while (true) {
                int encoderStatus = videoEncoder.dequeueOutputBuffer(videoBufferInfo, 10000);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) {
                        break;
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    if (Build.VERSION.SDK_INT < 21) {
                        encoderOutputBuffers = videoEncoder.getOutputBuffers();
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = videoEncoder.getOutputFormat();
                    if (videoTrackIndex == -5) {
                        videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                        if (newFormat.containsKey(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) && newFormat.getInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) == 1) {
                            ByteBuffer spsBuff = newFormat.getByteBuffer("csd-0");
                            ByteBuffer ppsBuff = newFormat.getByteBuffer("csd-1");
                            prependHeaderSize = (spsBuff == null ? 0 : spsBuff.limit()) + (ppsBuff == null ? 0 : ppsBuff.limit());
                        }
                    }
                } else if (encoderStatus >= 0) {
                    ByteBuffer encodedData;
                    if (Build.VERSION.SDK_INT < 21) {
                        encodedData = encoderOutputBuffers[encoderStatus];
                    } else {
                        encodedData = videoEncoder.getOutputBuffer(encoderStatus);
                    }
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                    }
                    if (videoBufferInfo.size > 1) {
                        if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (prependHeaderSize != 0 && (videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                                videoBufferInfo.offset += prependHeaderSize;
                                videoBufferInfo.size -= prependHeaderSize;
                            }
                            if (firstEncode && (videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                                MediaCodecVideoConvertor.cutOfNalData(outputMimeType, encodedData, videoBufferInfo);
                                firstEncode = false;
                            }
                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                            bufferInfo.size = videoBufferInfo.size;
                            bufferInfo.offset = videoBufferInfo.offset;
                            bufferInfo.flags = videoBufferInfo.flags;
                            bufferInfo.presentationTimeUs = videoBufferInfo.presentationTimeUs;
                            ByteBuffer byteBuffer = AndroidUtilities.cloneByteBuffer(encodedData);
                            fileWriteQueue.postRunnable(() -> {
                                try {
                                    mediaMuxer.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo, true);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            });
                        } else if (videoTrackIndex == -5) {
                            if (outputMimeType.equals("video/hevc")) {
                                throw new RuntimeException("need fix parsing csd data");
                            }
                            byte[] csd = new byte[videoBufferInfo.size];
                            encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size);
                            encodedData.position(videoBufferInfo.offset);
                            encodedData.get(csd);
                            ByteBuffer sps = null;
                            ByteBuffer pps = null;
                            for (int a = videoBufferInfo.size - 1; a >= 0; a--) {
                                if (a > 3) {
                                    if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                        sps = ByteBuffer.allocate(a - 3);
                                        pps = ByteBuffer.allocate(videoBufferInfo.size - (a - 3));
                                        sps.put(csd, 0, a - 3).position(0);
                                        pps.put(csd, a - 3, videoBufferInfo.size - (a - 3)).position(0);
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }

                            MediaFormat newFormat = MediaFormat.createVideoFormat("video/avc", videoWidth, videoHeight);
                            if (sps != null && pps != null) {
                                newFormat.setByteBuffer("csd-0", sps);
                                newFormat.setByteBuffer("csd-1", pps);
                            }
                            videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                        }
                    }
                    videoEncoder.releaseOutputBuffer(encoderStatus, false);
                    if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }

            if (Build.VERSION.SDK_INT < 21) {
                encoderOutputBuffers = audioEncoder.getOutputBuffers();
            }
            boolean encoderOutputAvailable = true;
            while (true) {
                int encoderStatus = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 0);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream || !running && sendWhenDone == 0) {
                        break;
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    if (Build.VERSION.SDK_INT < 21) {
                        encoderOutputBuffers = audioEncoder.getOutputBuffers();
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = audioEncoder.getOutputFormat();
                    if (audioTrackIndex == -5) {
                        audioTrackIndex = mediaMuxer.addTrack(newFormat, true);
                    }
                } else if (encoderStatus >= 0) {
                    ByteBuffer encodedData;
                    if (Build.VERSION.SDK_INT < 21) {
                        encodedData = encoderOutputBuffers[encoderStatus];
                    } else {
                        encodedData = audioEncoder.getOutputBuffer(encoderStatus);
                    }
                    if (encodedData == null) {
                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                    }
                    if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        audioBufferInfo.size = 0;
                    }
                    if (audioBufferInfo.size != 0) {
                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        bufferInfo.size = audioBufferInfo.size;
                        bufferInfo.offset = audioBufferInfo.offset;
                        bufferInfo.flags = audioBufferInfo.flags;
                        bufferInfo.presentationTimeUs = audioBufferInfo.presentationTimeUs;
                        ByteBuffer byteBuffer = AndroidUtilities.cloneByteBuffer(encodedData);
                        fileWriteQueue.postRunnable(() -> {
                            try {
                                mediaMuxer.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo, false);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        });
                    }
                    audioEncoder.releaseOutputBuffer(encoderStatus, false);
                    if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
        }

        @Override
        protected void finalize() throws Throwable {
            if (fileWriteQueue != null) {
                fileWriteQueue.recycle();
                fileWriteQueue = null;
            }
            try {
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    EGL14.eglDestroyContext(eglDisplay, eglContext);
                    EGL14.eglReleaseThread();
                    EGL14.eglTerminate(eglDisplay);
                    eglDisplay = EGL14.EGL_NO_DISPLAY;
                    eglContext = EGL14.EGL_NO_CONTEXT;
                    eglConfig = null;
                }
            } finally {
                super.finalize();
            }
        }
    }

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_VIDEOFRAME_AVAILABLE = 2;
    private static final int MSG_AUDIOFRAME_AVAILABLE = 3;

    private static class EncoderHandler extends Handler {
        private WeakReference<VideoRecorder> mWeakEncoder;

        public EncoderHandler(VideoRecorder encoder) {
            mWeakEncoder = new WeakReference<>(encoder);
        }

        @Override
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            VideoRecorder encoder = mWeakEncoder.get();
            if (encoder == null) {
                return;
            }

            switch (what) {
                case MSG_START_RECORDING: {
                    try {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.e("start encoder");
                        }
                        encoder.prepareEncoder();
                    } catch (Exception e) {
                        FileLog.e(e);
                        encoder.handleStopRecording(0);
                        Looper.myLooper().quit();
                    }
                    break;
                }
                case MSG_STOP_RECORDING: {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("stop encoder");
                    }
                    encoder.handleStopRecording(inputMessage.arg1);
                    break;
                }
                case MSG_VIDEOFRAME_AVAILABLE: {
                    long timestamp = (((long) inputMessage.arg1) << 32) | (((long) inputMessage.arg2) & 0xffffffffL);
                    Integer cameraId = (Integer) inputMessage.obj;
                    encoder.handleVideoFrameAvailable(timestamp, cameraId);
                    break;
                }
                case MSG_AUDIOFRAME_AVAILABLE: {
                    encoder.handleAudioFrameAvailable((InstantCameraView.AudioBufferInfo) inputMessage.obj);
                    break;
                }
            }
        }

        public void exit() {
            Looper.myLooper().quit();
        }
    }

    public void setFpsLimit(int fpsLimit) {
        this.fpsLimit = fpsLimit;
    }

    public void pauseAsTakingPicture() {
        if (cameraThread != null) {
            cameraThread.pause(600);
        }
    }
}
