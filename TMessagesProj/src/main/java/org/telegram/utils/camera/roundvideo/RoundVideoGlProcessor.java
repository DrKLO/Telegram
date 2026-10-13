package org.telegram.utils.camera.roundvideo;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;

final class RoundVideoGlProcessor {

    public interface FrameTimingListener {
        void onFrameSubmitted(long presentationTimeUs, long submittedNs);
    }

    interface ErrorListener {
        void onError(@NonNull Exception error);
    }

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static final boolean ENABLE_BLOCKING_GPU_TIMING = false;
    private static final int GPU_TIMING_SAMPLE_INTERVAL = 30;
    private static final int OUTPUT_SIZE_CHECK_INTERVAL = 60;
    private static final long FRAME_GAP_SIZE_CHECK_NS = 50_000_000L;
    private static final long SWITCH_FRAME_DELAY_MS = 33L;
    private static final long SWITCH_LIVE_FALLBACK_DELAY_MS = 70L;
    private static final float SWITCH_OVERDUE_BLUR_GROWTH = 0.35f;
    private static final int SWITCH_MIN_REVEAL_MS = 210;
    private static final int SWITCH_MAX_REVEAL_MS = 300;

    private static final int SWITCH_NONE = 0;
    private static final int SWITCH_BLUR_OLD = 1;
    private static final int SWITCH_WAIT_NEW = 2;
    private static final int SWITCH_CROSSFADE = 3;
    private static final int SWITCH_UNBLUR_NEW = 4;

    private Size inputSize;
    private Size primaryInputSize;
    private Size secondaryInputSize;
    private final Surface outputSurface;
    private final Surface previewOutputSurface;
    private final boolean dualInputEnabled;
    private final int outputSize;
    private final boolean compositionEnabled;
    private final RoundVideoDiagnostics diagnostics;
    private int sourceCropSize;
    private int primarySourceCropSize;
    private int secondarySourceCropSize;
    private boolean cameraTimestampRealtime;
    private boolean primaryTimestampRealtime;
    private boolean secondaryTimestampRealtime;
    private volatile long presentationTimeOriginNs;
    private volatile long presentationTimeLimitNs = Long.MAX_VALUE;
    private final FrameTimingListener frameTimingListener;
    private final ErrorListener errorListener;

    private HandlerThread thread;
    private Handler handler;
    private SurfaceTexture inputSurfaceTexture;
    private Surface cameraSurface;
    private SurfaceTexture secondaryInputSurfaceTexture;
    private Surface secondaryCameraSurface;

    private android.opengl.EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private android.opengl.EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private android.opengl.EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private android.opengl.EGLSurface previewEglSurface = EGL14.EGL_NO_SURFACE;

    private int textureId;
    private int secondaryTextureId;
    private volatile boolean secondaryInputActive;
    private int outputWidth;
    private int outputHeight;
    private int previewOutputWidth;
    private int previewOutputHeight;
    private final int[] eglSurfaceValue = new int[1];
    private final float[] textureMatrix = new float[16];
    private final float[] secondaryTextureMatrix = new float[16];
    private RoundVideoOverlayRenderer overlayRenderer;
    private int gpuTimingFrame;
    private int outputSizeCheckFrame;
    private long lastFrameTime;
    private long firstCameraTimestamp = -1;
    private long firstPresentationTime;
    private long lastPresentationTime = -1;
    private boolean hasCameraFrame;
    private boolean hasNewCameraFrame;
    private boolean newCameraSessionReady;
    private int switchState;
    private long switchStageStartNs;
    private long cameraSwitchStartNs;
    private long switchCrossfadeDurationNs;
    private long switchUnblurDurationNs;
    private int switchExpectedWaitMs;
    private float switchOldBlurRadiusPx;
    private RoundVideoSession.CameraFacing switchFromFacing;
    private RoundVideoSession.CameraFacing switchToFacing;
    private int swapTimingFrames;
    private long swapTimingTotalNs;
    private long swapTimingMaxNs;
    private long inputFrames;
    private long primaryInputFrames;
    private long secondaryInputFrames;
    private long preOriginFrames;
    private long submittedFrames;
    private int syntheticFrames;
    private int largeFrameGaps;
    private long maximumFrameGapNs;
    private int gpuTimingSamples;
    private long gpuTimingTotalNs;
    private long gpuTimingMaxNs;
    private long glStartedNs;

    private volatile boolean started;
    private volatile boolean recordingStarted;
    private volatile Runnable firstFrameListener;
    private Runnable dualSwitchFrameListener;
    private boolean firstFrameReported;
    private volatile RuntimeException initializationError;

    private final Runnable switchFrameRunnable = this::drawSwitchFrame;

    RoundVideoGlProcessor(
            @NonNull Size inputSize,
            @NonNull Surface outputSurface,
            int outputSize,
            int sourceCropSize,
            boolean cameraTimestampRealtime,
            boolean compositionEnabled,
            @NonNull RoundVideoDiagnostics diagnostics,
            long presentationTimeOriginNs,
            @Nullable FrameTimingListener frameTimingListener,
            @Nullable ErrorListener errorListener
    ) {
        this(
                inputSize,
                outputSurface,
                null,
                false,
                outputSize,
                sourceCropSize,
                cameraTimestampRealtime,
                compositionEnabled,
                diagnostics,
                presentationTimeOriginNs,
                frameTimingListener,
                errorListener
        );
    }

    RoundVideoGlProcessor(
            @NonNull Size inputSize,
            @NonNull Surface outputSurface,
            @Nullable Surface previewOutputSurface,
            boolean dualInputEnabled,
            int outputSize,
            int sourceCropSize,
            boolean cameraTimestampRealtime,
            boolean compositionEnabled,
            @NonNull RoundVideoDiagnostics diagnostics,
            long presentationTimeOriginNs,
            @Nullable FrameTimingListener frameTimingListener,
            @Nullable ErrorListener errorListener
    ) {
        this.inputSize = inputSize;
        this.primaryInputSize = inputSize;
        this.secondaryInputSize = inputSize;
        this.outputSurface = outputSurface;
        this.previewOutputSurface = previewOutputSurface;
        this.dualInputEnabled = dualInputEnabled;
        this.outputSize = outputSize;
        this.sourceCropSize = sourceCropSize;
        this.primarySourceCropSize = sourceCropSize;
        this.secondarySourceCropSize = sourceCropSize;
        this.cameraTimestampRealtime = cameraTimestampRealtime;
        this.primaryTimestampRealtime = cameraTimestampRealtime;
        this.secondaryTimestampRealtime = cameraTimestampRealtime;
        this.compositionEnabled = compositionEnabled;
        this.diagnostics = diagnostics;
        outputWidth = outputSize;
        outputHeight = outputSize;
        this.presentationTimeOriginNs = presentationTimeOriginNs;
        this.recordingStarted = presentationTimeOriginNs != 0;
        this.frameTimingListener = frameTimingListener;
        this.errorListener = errorListener;
    }

    @NonNull
    public Surface start() {
        if (started) {
            return cameraSurface;
        }

        CountDownLatch latch = new CountDownLatch(1);
        thread = new HandlerThread("RoundVideoGlProcessor");
        thread.start();
        glStartedNs = System.nanoTime();
        diagnostics.log("GL processor start requested: input=" + inputSize
                + ", crop=" + sourceCropSize
                + ", output=" + outputSize + "x" + outputSize
                + ", filter=" + inputFilterName()
                + ", composition=" + compositionEnabled
                + ", dualInput=" + dualInputEnabled
                + ", glPreview=" + (previewOutputSurface != null));
        handler = new Handler(thread.getLooper());
        handler.post(() -> {
            try {
                initializeGl();
                started = true;
            } catch (RuntimeException e) {
                initializationError = e;
                releaseGl();
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stop();
            throw new IllegalStateException("GL initialization was interrupted", e);
        }

        if (initializationError != null) {
            RuntimeException error = initializationError;
            initializationError = null;
            stop();
            throw error;
        }
        return cameraSurface;
    }

    public void stop() {
        Handler currentHandler = handler;
        HandlerThread currentThread = thread;
        if (currentHandler != null) {
            currentHandler.removeCallbacks(switchFrameRunnable);
        }
        handler = null;
        thread = null;
        started = false;

        if (currentHandler == null || currentThread == null) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        currentHandler.post(() -> {
            try {
                releaseGl();
            } finally {
                currentThread.quitSafely();
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @NonNull
    public Surface getCameraSurface() {
        if (cameraSurface == null) {
            throw new IllegalStateException("GL processor is not started");
        }
        return cameraSurface;
    }

    @NonNull
    public Surface getSecondaryCameraSurface() {
        if (!dualInputEnabled || secondaryCameraSurface == null) {
            throw new IllegalStateException("Secondary GL camera surface is unavailable");
        }
        return secondaryCameraSurface;
    }

    public void setSecondaryInputActive(boolean secondary) {
        setSecondaryInputActive(secondary, null);
    }

    public void setSecondaryInputActive(
            boolean secondary,
            @Nullable Runnable firstSelectedFrameListener
    ) {
        Handler currentHandler = handler;
        if (!started || currentHandler == null) return;
        currentHandler.post(() -> {
            if (!dualInputEnabled) return;
            secondaryInputActive = secondary;
            inputSize = secondary ? secondaryInputSize : primaryInputSize;
            sourceCropSize = secondary
                    ? secondarySourceCropSize
                    : primarySourceCropSize;
            cameraTimestampRealtime = secondary
                    ? secondaryTimestampRealtime
                    : primaryTimestampRealtime;
            if (overlayRenderer != null) {
                overlayRenderer.updateInputConfiguration(inputSize, sourceCropSize);
            }
            firstCameraTimestamp = -1;
            dualSwitchFrameListener = firstSelectedFrameListener;
            diagnostics.log("GL dual input selected: input=" + (secondary ? 1 : 0));
        });
    }

    public void beginCameraSwitch(
            @NonNull RoundVideoSession.CameraFacing from,
            @NonNull RoundVideoSession.CameraFacing to
    ) {
        Handler currentHandler = handler;
        if (!started || currentHandler == null) {
            return;
        }
        currentHandler.post(() -> {
            if (!started || switchState != SWITCH_NONE || !hasCameraFrame) {
                return;
            }
            overlayRenderer.captureSwitchFrame(textureId, textureMatrix, false);
            hasNewCameraFrame = false;
            newCameraSessionReady = false;
            switchOldBlurRadiusPx = 0f;
            switchState = SWITCH_BLUR_OLD;
            cameraSwitchStartNs = switchStageStartNs = SystemClock.elapsedRealtimeNanos();
            switchFromFacing = from;
            switchToFacing = to;
            configureSwitchTiming(RoundVideoSwitchTimingStore.getAverageMs(from, to));
            diagnostics.log("synthetic camera switch started: from=" + from
                    + ", to=" + to
                    + ", expectedWaitMs=" + switchExpectedWaitMs
                    + ", targetBlurRadiusPx=" + overlayRenderer.getSwitchTargetBlurRadiusPx()
                    + ", overdueBlurGrowth=" + SWITCH_OVERDUE_BLUR_GROWTH
                    + ", revealMs="
                    + (switchCrossfadeDurationNs + switchUnblurDurationNs) / 1_000_000L);
            firstCameraTimestamp = -1;
            currentHandler.removeCallbacks(switchFrameRunnable);
            currentHandler.post(switchFrameRunnable);
        });
    }

    public void setFirstFrameListener(@Nullable Runnable listener) {
        firstFrameListener = listener;
    }

    public void onCameraSessionConfigured() {
        Handler currentHandler = handler;
        if (!started || currentHandler == null) return;
        currentHandler.post(() -> {
            if (switchState != SWITCH_NONE) {
                newCameraSessionReady = true;
            }
        });
    }

    public void startRecording(long timeOriginNs) {
        if (timeOriginNs <= 0) {
            throw new IllegalArgumentException("Invalid recording time origin");
        }
        presentationTimeOriginNs = timeOriginNs;
        firstCameraTimestamp = -1;
        firstPresentationTime = 0;
        lastPresentationTime = -1;
        presentationTimeLimitNs = Long.MAX_VALUE;
        recordingStarted = true;
    }

    public void stopRecordingAt(long presentationTimeUs) {
        presentationTimeLimitNs = Math.max(0, presentationTimeUs) * 1000L;
        Handler currentHandler = handler;
        if (currentHandler != null) {
            currentHandler.removeCallbacks(switchFrameRunnable);
        }
    }

    public void updateInputConfiguration(
            @NonNull Size size,
            int cropSize,
            boolean timestampRealtime
    ) {
        Handler currentHandler = handler;
        if (!started || currentHandler == null) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        RuntimeException[] error = new RuntimeException[1];
        currentHandler.post(() -> {
            try {
                inputSize = size;
                primaryInputSize = size;
                secondaryInputSize = size;
                sourceCropSize = cropSize;
                primarySourceCropSize = cropSize;
                secondarySourceCropSize = cropSize;
                cameraTimestampRealtime = timestampRealtime;
                primaryTimestampRealtime = timestampRealtime;
                secondaryTimestampRealtime = timestampRealtime;
                if (inputSurfaceTexture != null) {
                    inputSurfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
                }
                if (secondaryInputSurfaceTexture != null) {
                    secondaryInputSurfaceTexture.setDefaultBufferSize(
                            size.getWidth(),
                            size.getHeight()
                    );
                }
                setInputTextureFilter();
                if (overlayRenderer != null) {
                    overlayRenderer.updateInputConfiguration(size, cropSize);
                }
                diagnostics.log("GL input updated: input=" + size
                        + ", crop=" + cropSize
                        + ", filter=" + inputFilterName());
            } catch (RuntimeException e) {
                error[0] = e;
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Input size update was interrupted", e);
        }
        if (error[0] != null) {
            throw error[0];
        }
    }

    public void configureSecondaryInput(
            @NonNull Size size,
            int cropSize,
            boolean timestampRealtime
    ) {
        Handler currentHandler = handler;
        if (!started || !dualInputEnabled || currentHandler == null) return;
        CountDownLatch latch = new CountDownLatch(1);
        RuntimeException[] error = new RuntimeException[1];
        currentHandler.post(() -> {
            try {
                secondaryInputSize = size;
                secondarySourceCropSize = cropSize;
                secondaryTimestampRealtime = timestampRealtime;
                if (secondaryInputSurfaceTexture != null) {
                    secondaryInputSurfaceTexture.setDefaultBufferSize(
                            size.getWidth(),
                            size.getHeight()
                    );
                }
                if (secondaryTextureId != 0) {
                    setInputTextureFilter(secondaryTextureId, cropSize);
                }
                diagnostics.log("GL secondary input configured: input=" + size
                        + ", crop=" + cropSize
                        + ", filter=" + inputFilterName(cropSize)
                        + ", timestampRealtime=" + timestampRealtime);
            } catch (RuntimeException e) {
                error[0] = e;
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Secondary input update was interrupted", e);
        }
        if (error[0] != null) throw error[0];
    }

    private void initializeGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new IllegalStateException("Unable to get EGL display");
        }

        int[] versions = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) {
            throw new IllegalStateException("Unable to initialize EGL");
        }

        int[] configAttributes = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };
        android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
        int[] configCount = new int[1];
        if (!EGL14.eglChooseConfig(
                eglDisplay,
                configAttributes,
                0,
                configs,
                0,
                configs.length,
                configCount,
                0
        ) || configCount[0] == 0) {
            throw new IllegalStateException("Unable to choose EGL config");
        }

        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(
                eglDisplay,
                configs[0],
                EGL14.EGL_NO_CONTEXT,
                contextAttributes,
                0
        );
        checkEglObject(eglContext != EGL14.EGL_NO_CONTEXT, "Unable to create EGL context");

        int[] surfaceAttributes = {EGL14.EGL_NONE};
        eglSurface = EGL14.eglCreateWindowSurface(
                eglDisplay,
                configs[0],
                outputSurface,
                surfaceAttributes,
                0
        );
        checkEglObject(eglSurface != EGL14.EGL_NO_SURFACE, "Unable to create EGL surface");
        if (previewOutputSurface != null) {
            previewEglSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay,
                    configs[0],
                    previewOutputSurface,
                    surfaceAttributes,
                    0
            );
            checkEglObject(
                    previewEglSurface != EGL14.EGL_NO_SURFACE,
                    "Unable to create preview EGL surface"
            );
            previewOutputWidth = querySurfaceValue(previewEglSurface, EGL14.EGL_WIDTH);
            previewOutputHeight = querySurfaceValue(previewEglSurface, EGL14.EGL_HEIGHT);
        }

        makeCurrent();
        diagnostics.log("GL initialized: egl=" + versions[0] + "." + versions[1]
                + ", vendor=" + GLES20.glGetString(GLES20.GL_VENDOR)
                + ", renderer=" + GLES20.glGetString(GLES20.GL_RENDERER)
                + ", version=" + GLES20.glGetString(GLES20.GL_VERSION)
                + ", elapsedMs=" + elapsedMs(glStartedNs));
        createInputSurface();
        overlayRenderer = new RoundVideoOverlayRenderer(
                outputSize,
                inputSize,
                sourceCropSize,
                compositionEnabled
        );
        GLES20.glViewport(0, 0, outputSize, outputSize);
    }

    private void createInputSurface() {
        int[] textures = new int[dualInputEnabled ? 2 : 1];
        GLES20.glGenTextures(textures.length, textures, 0);
        textureId = textures[0];
        configureExternalTexture(textureId);

        inputSurfaceTexture = new SurfaceTexture(textureId);
        inputSurfaceTexture.setDefaultBufferSize(inputSize.getWidth(), inputSize.getHeight());
        inputSurfaceTexture.setOnFrameAvailableListener(
                surfaceTexture -> drawFrame(false),
                handler
        );
        cameraSurface = new Surface(inputSurfaceTexture);
        if (dualInputEnabled) {
            secondaryTextureId = textures[1];
            configureExternalTexture(secondaryTextureId);
            secondaryInputSurfaceTexture = new SurfaceTexture(secondaryTextureId);
            secondaryInputSurfaceTexture.setDefaultBufferSize(
                    inputSize.getWidth(),
                    inputSize.getHeight()
            );
            secondaryInputSurfaceTexture.setOnFrameAvailableListener(
                    surfaceTexture -> drawFrame(true),
                    handler
            );
            secondaryCameraSurface = new Surface(secondaryInputSurfaceTexture);
        }
    }

    private void configureExternalTexture(int id) {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id);
        setInputTextureFilter(id, sourceCropSize);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );
    }

    private void setInputTextureFilter() {
        setInputTextureFilter(textureId, primarySourceCropSize);
        if (secondaryTextureId != 0) {
            setInputTextureFilter(secondaryTextureId, secondarySourceCropSize);
        }
    }

    private void setInputTextureFilter(int id, int cropSize) {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id);
        int filter = cropSize == outputSize ? GLES20.GL_NEAREST : GLES20.GL_LINEAR;
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER,
                filter
        );
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER,
                filter
        );
    }

    private void drawFrame(boolean secondary) {
        SurfaceTexture surfaceTexture = secondary
                ? secondaryInputSurfaceTexture
                : inputSurfaceTexture;
        if (!started || surfaceTexture == null) {
            return;
        }
        try {
            float[] matrix = secondary ? secondaryTextureMatrix : textureMatrix;
            int cameraTexture = secondary ? secondaryTextureId : textureId;
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(matrix);
            long timestamp = surfaceTexture.getTimestamp();
            inputFrames++;
            long inputCount;
            if (secondary) {
                inputCount = ++secondaryInputFrames;
            } else {
                inputCount = ++primaryInputFrames;
            }
            if (inputCount == 1) {
                diagnostics.log("first GL input frame: input=" + (secondary ? 1 : 0)
                        + ", cameraTimestampNs=" + timestamp
                        + ", elapsedMs=" + elapsedMs(glStartedNs));
            }
            if (secondary != secondaryInputActive) return;
            if (!recordingStarted) {
                renderPreviewAndNotify(cameraTexture, matrix);
                Runnable listener = firstFrameListener;
                if (!firstFrameReported && listener != null) {
                    firstFrameReported = true;
                    listener.run();
                }
                return;
            }
            if (cameraTimestampRealtime && timestamp < presentationTimeOriginNs) {
                renderPreviewAndNotify(cameraTexture, matrix);
                preOriginFrames++;
                if (preOriginFrames == 1) {
                    diagnostics.log("dropping pre-origin camera frame: deltaUs="
                            + (timestamp - presentationTimeOriginNs) / 1000L);
                }
                return;
            }
            long frameTime = SystemClock.elapsedRealtimeNanos();
            if (lastFrameTime != 0) {
                long gapNs = frameTime - lastFrameTime;
                maximumFrameGapNs = Math.max(maximumFrameGapNs, gapNs);
                if (gapNs > FRAME_GAP_SIZE_CHECK_NS) largeFrameGaps++;
            }
            if (lastFrameTime == 0
                    || frameTime - lastFrameTime > FRAME_GAP_SIZE_CHECK_NS
                    || outputSizeCheckFrame++ % OUTPUT_SIZE_CHECK_INTERVAL == 0) {
                updateOutputSize();
            }
            lastFrameTime = frameTime;
            if (switchState != SWITCH_NONE) {
                renderPreviewAndNotify(cameraTexture, matrix);
                if (!newCameraSessionReady) {
                    return;
                }
                boolean firstNewFrame = !hasNewCameraFrame;
                overlayRenderer.captureSwitchFrame(cameraTexture, matrix, true);
                hasNewCameraFrame = true;
                if (firstNewFrame) {
                    long now = SystemClock.elapsedRealtimeNanos();
                    long waitNs = now - cameraSwitchStartNs;
                    int updatedAverageMs = RoundVideoSwitchTimingStore.recordAndGetAverageMs(
                            switchFromFacing,
                            switchToFacing,
                            Math.round(waitNs / 1_000_000f)
                    );
                    diagnostics.log("camera switch first new frame: waitMs="
                            + waitNs / 1_000_000f
                            + ", rollingAverageMs=" + updatedAverageMs);
                    switchState = SWITCH_CROSSFADE;
                    switchStageStartNs = now;
                }
                if (switchState == SWITCH_CROSSFADE || switchState == SWITCH_UNBLUR_NEW) {
                    Handler currentHandler = handler;
                    if (currentHandler != null) {
                        currentHandler.removeCallbacks(switchFrameRunnable);
                    }
                    drawSwitchFrame();
                }
                return;
            }

            hasCameraFrame = true;
            boolean measureGpu = ENABLE_BLOCKING_GPU_TIMING
                    && diagnostics.isEnabled()
                    && gpuTimingFrame++ % GPU_TIMING_SAMPLE_INTERVAL == 0;
            long gpuStartTime = 0;
            if (measureGpu) {
                GLES20.glFinish();
                gpuStartTime = System.nanoTime();
            }

            overlayRenderer.render(
                    cameraTexture,
                    matrix,
                    frameTime,
                    outputWidth,
                    outputHeight
            );

            if (measureGpu) {
                GLES20.glFinish();
                long gpuTimeNs = System.nanoTime() - gpuStartTime;
                gpuTimingSamples++;
                gpuTimingTotalNs += gpuTimeNs;
                gpuTimingMaxNs = Math.max(gpuTimingMaxNs, gpuTimeNs);
                diagnostics.log("GPU pipeline sample: currentMs=" + gpuTimeNs / 1_000_000f
                        + ", averageMs="
                        + gpuTimingTotalNs / (float) gpuTimingSamples / 1_000_000f
                        + ", maxMs=" + gpuTimingMaxNs / 1_000_000f);
            }

            long presentationTime = getCameraPresentationTime(timestamp);
            if (presentationTime < presentationTimeLimitNs) {
                submitFrame(presentationTime);
            }
            renderPreviewAndNotify(cameraTexture, matrix);
        } catch (RuntimeException e) {
            reportError(e);
        }
    }

    private void renderPreviewAndNotify(int cameraTexture, @NonNull float[] matrix) {
        renderPreview(cameraTexture, matrix);
        Runnable selectedFrameListener = dualSwitchFrameListener;
        if (selectedFrameListener != null) {
            dualSwitchFrameListener = null;
            selectedFrameListener.run();
        }
    }

    private void renderPreview(int cameraTexture, @NonNull float[] matrix) {
        if (previewEglSurface == EGL14.EGL_NO_SURFACE || overlayRenderer == null) return;
        if (!EGL14.eglMakeCurrent(
                eglDisplay,
                previewEglSurface,
                previewEglSurface,
                eglContext
        )) {
            throw new IllegalStateException("Unable to make preview EGL surface current: 0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
        overlayRenderer.renderPreview(
                cameraTexture,
                matrix,
                previewOutputWidth,
                previewOutputHeight
        );
        if (!EGL14.eglSwapBuffers(eglDisplay, previewEglSurface)) {
            throw new IllegalStateException("Unable to swap preview EGL buffers: 0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
        makeCurrent();
    }

    private int querySurfaceValue(android.opengl.EGLSurface surface, int attribute) {
        if (!EGL14.eglQuerySurface(
                eglDisplay,
                surface,
                attribute,
                eglSurfaceValue,
                0
        )) {
            return outputSize;
        }
        return Math.max(1, eglSurfaceValue[0]);
    }

    private void drawSwitchFrame() {
        if (!started || switchState == SWITCH_NONE) {
            return;
        }
        try {
            long now = SystemClock.elapsedRealtimeNanos();
            float newBlur = 1f;
            float mix = 0f;
            long elapsed = now - switchStageStartNs;

            if (switchState == SWITCH_BLUR_OLD) {
                switchOldBlurRadiusPx = getSwitchOldBlurRadiusPx(now);
                if (elapsed >= getExpectedSwitchWaitNs()) {
                    switchState = hasNewCameraFrame ? SWITCH_CROSSFADE : SWITCH_WAIT_NEW;
                    switchStageStartNs = now;
                }
            } else if (switchState == SWITCH_WAIT_NEW) {
                switchOldBlurRadiusPx = getSwitchOldBlurRadiusPx(now);
            } else if (switchState == SWITCH_CROSSFADE
                    || switchState == SWITCH_UNBLUR_NEW) {
                if (elapsed < switchCrossfadeDurationNs) {
                    float progress = smoothStep(
                            elapsed / (float) switchCrossfadeDurationNs
                    );
                    mix = progress;
                } else {
                    switchState = SWITCH_UNBLUR_NEW;
                    mix = 1f;
                    long unblurElapsed = elapsed - switchCrossfadeDurationNs;
                    newBlur = 1f - smoothStep(
                            unblurElapsed / (float) switchUnblurDurationNs
                    );
                }
            }

            overlayRenderer.renderSwitch(
                    switchOldBlurRadiusPx,
                    newBlur,
                    mix,
                    outputWidth,
                    outputHeight
            );
            syntheticFrames++;
            long presentationTime = getRealtimePresentationTime();
            if (presentationTime < presentationTimeLimitNs) {
                submitFrame(presentationTime);
            }
            long revealDurationNs = switchCrossfadeDurationNs + switchUnblurDurationNs;
            if (switchState == SWITCH_UNBLUR_NEW && elapsed >= revealDurationNs) {
                overlayRenderer.finishCameraSwitch(now);
                switchState = SWITCH_NONE;
                diagnostics.log("synthetic camera switch completed: elapsedMs="
                        + (now - cameraSwitchStartNs) / 1_000_000f
                        + ", syntheticFrames=" + syntheticFrames);
            }
            if (switchState != SWITCH_NONE
                    && handler != null) {
                boolean liveReveal = hasNewCameraFrame
                        && (switchState == SWITCH_CROSSFADE
                        || switchState == SWITCH_UNBLUR_NEW);
                long remainingNs = liveReveal
                        ? revealDurationNs - elapsed
                        : getExpectedSwitchWaitNs() - elapsed;
                long maximumDelayMs = liveReveal
                        ? SWITCH_LIVE_FALLBACK_DELAY_MS
                        : SWITCH_FRAME_DELAY_MS;
                long remainingMs = switchState == SWITCH_WAIT_NEW
                        ? SWITCH_FRAME_DELAY_MS
                        : Math.max(1L, (remainingNs + 999_999L) / 1_000_000L);
                handler.postDelayed(
                        switchFrameRunnable,
                        Math.min(maximumDelayMs, remainingMs)
                );
            }
        } catch (RuntimeException e) {
            switchState = SWITCH_NONE;
            reportError(e);
        }
    }

    private void reportError(@NonNull RuntimeException error) {
        diagnostics.error("GL error", error);
        if (errorListener != null) errorListener.onError(error);
    }

    private long getCameraPresentationTime(long cameraTimestamp) {
        long presentationTime = cameraTimestamp;
        if (presentationTimeOriginNs != 0) {
            if (cameraTimestampRealtime) {
                presentationTime = Math.max(0, cameraTimestamp - presentationTimeOriginNs);
            } else {
                if (firstCameraTimestamp < 0) {
                    firstCameraTimestamp = cameraTimestamp;
                    firstPresentationTime = getRealtimePresentationTime();
                }
                presentationTime = firstPresentationTime
                        + Math.max(0, cameraTimestamp - firstCameraTimestamp);
            }
        }
        return Math.max(presentationTime, lastPresentationTime + 1);
    }

    private long getRealtimePresentationTime() {
        long presentationTime = presentationTimeOriginNs == 0
                ? lastPresentationTime + 33_333_333L
                : Math.max(0, SystemClock.elapsedRealtimeNanos() - presentationTimeOriginNs);
        return Math.max(presentationTime, lastPresentationTime + 1);
    }

    private void submitFrame(long presentationTime) {
        lastPresentationTime = presentationTime;
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTime);
        long swapStartNs = System.nanoTime();
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            throw new IllegalStateException("Unable to swap EGL buffers: 0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
        long submittedNs = System.nanoTime();
        submittedFrames++;
        recordSwapTiming(submittedNs - swapStartNs);
        if (frameTimingListener != null) {
            frameTimingListener.onFrameSubmitted(presentationTime / 1000L, submittedNs);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float smoothStep(float value) {
        float clamped = clamp01(value);
        return clamped * clamped * (3f - 2f * clamped);
    }

    private static float easeOutQuart(float value) {
        float inverse = 1f - clamp01(value);
        return 1f - inverse * inverse * inverse * inverse;
    }

    private float getSwitchOldBlurRadiusPx(long nowNs) {
        long elapsedNs = Math.max(0L, nowNs - cameraSwitchStartNs);
        long expectedNs = getExpectedSwitchWaitNs();
        float targetRadius = overlayRenderer.getSwitchTargetBlurRadiusPx();
        if (elapsedNs <= expectedNs) {
            return targetRadius * easeOutQuart(elapsedNs / (float) expectedNs);
        }
        float overdue = (elapsedNs - expectedNs) / (float) expectedNs;
        return targetRadius * (float) Math.sqrt(
                1f + SWITCH_OVERDUE_BLUR_GROWTH * Math.log1p(overdue)
        );
    }

    private long getExpectedSwitchWaitNs() {
        return Math.max(SWITCH_FRAME_DELAY_MS, switchExpectedWaitMs) * 1_000_000L;
    }

    private void configureSwitchTiming(int expectedWaitMs) {
        switchExpectedWaitMs = expectedWaitMs;
        int revealMs = clamp(
                SWITCH_MAX_REVEAL_MS - Math.max(0, expectedWaitMs - 400) * 3 / 20,
                SWITCH_MIN_REVEAL_MS,
                SWITCH_MAX_REVEAL_MS
        );
        int crossfadeMs = revealMs * 45 / 100;
        switchCrossfadeDurationNs = crossfadeMs * 1_000_000L;
        switchUnblurDurationNs = (revealMs - crossfadeMs) * 1_000_000L;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @NonNull
    private String inputFilterName() {
        return inputFilterName(sourceCropSize);
    }

    @NonNull
    private String inputFilterName(int cropSize) {
        return cropSize == outputSize ? "NEAREST" : "LINEAR";
    }

    private static long elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000L;
    }

    private void recordSwapTiming(long durationNs) {
        swapTimingFrames++;
        swapTimingTotalNs += durationNs;
        swapTimingMaxNs = Math.max(swapTimingMaxNs, durationNs);
        if (swapTimingFrames % GPU_TIMING_SAMPLE_INTERVAL == 0) {
            diagnostics.log("encoder swap: average="
                    + swapTimingTotalNs / (float) swapTimingFrames / 1_000_000f
                    + " ms, max=" + swapTimingMaxNs / 1_000_000f + " ms");
        }
    }

    private void makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new IllegalStateException("Unable to make EGL context current: 0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    private void updateOutputSize() {
        if (!EGL14.eglQuerySurface(
                eglDisplay,
                eglSurface,
                EGL14.EGL_WIDTH,
                eglSurfaceValue,
                0
        )) {
            return;
        }
        int width = eglSurfaceValue[0];
        if (!EGL14.eglQuerySurface(
                eglDisplay,
                eglSurface,
                EGL14.EGL_HEIGHT,
                eglSurfaceValue,
                0
        )) {
            return;
        }
        int height = eglSurfaceValue[0];
        if (width > 0 && height > 0 && (width != outputWidth || height != outputHeight)) {
            outputWidth = width;
            outputHeight = height;
            diagnostics.log("EGL output size changed: " + width + "x" + height);
        }
        if (previewEglSurface != EGL14.EGL_NO_SURFACE) {
            previewOutputWidth = querySurfaceValue(previewEglSurface, EGL14.EGL_WIDTH);
            previewOutputHeight = querySurfaceValue(previewEglSurface, EGL14.EGL_HEIGHT);
        }
    }

    private void releaseGl() {
        long elapsedNs = glStartedNs == 0 ? 0 : System.nanoTime() - glStartedNs;
        diagnostics.log("GL summary: inputFrames=" + inputFrames
                + ", inputFps=" + frameRate(inputFrames, elapsedNs)
                + ", primaryInputFrames=" + primaryInputFrames
                + ", secondaryInputFrames=" + secondaryInputFrames
                + ", preOriginFrames=" + preOriginFrames
                + ", submittedFrames=" + submittedFrames
                + ", submittedFps=" + frameRate(submittedFrames, elapsedNs)
                + ", syntheticFrames=" + syntheticFrames
                + ", largeFrameGaps=" + largeFrameGaps
                + ", maxFrameGapMs=" + maximumFrameGapNs / 1_000_000f
                + ", gpuSamples=" + gpuTimingSamples
                + ", gpuAverageMs=" + (gpuTimingSamples == 0 ? "n/a"
                : gpuTimingTotalNs / (float) gpuTimingSamples / 1_000_000f)
                + ", gpuMaxMs=" + gpuTimingMaxNs / 1_000_000f
                + ", swapAverageMs=" + (swapTimingFrames == 0 ? "n/a"
                : swapTimingTotalNs / (float) swapTimingFrames / 1_000_000f)
                + ", swapMaxMs=" + swapTimingMaxNs / 1_000_000f);
        if (handler != null) {
            handler.removeCallbacks(switchFrameRunnable);
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY
                && eglSurface != EGL14.EGL_NO_SURFACE
                && eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
        }

        if (inputSurfaceTexture != null) {
            inputSurfaceTexture.setOnFrameAvailableListener(null);
        }
        if (secondaryInputSurfaceTexture != null) {
            secondaryInputSurfaceTexture.setOnFrameAvailableListener(null);
        }
        if (cameraSurface != null) {
            cameraSurface.release();
            cameraSurface = null;
        }
        if (inputSurfaceTexture != null) {
            inputSurfaceTexture.release();
            inputSurfaceTexture = null;
        }
        if (secondaryCameraSurface != null) {
            secondaryCameraSurface.release();
            secondaryCameraSurface = null;
        }
        if (secondaryInputSurfaceTexture != null) {
            secondaryInputSurfaceTexture.release();
            secondaryInputSurfaceTexture = null;
        }
        if (overlayRenderer != null) {
            overlayRenderer.release();
            overlayRenderer = null;
        }
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            textureId = 0;
        }
        if (secondaryTextureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{secondaryTextureId}, 0);
            secondaryTextureId = 0;
        }

        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
            );
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface);
            }
            if (previewEglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, previewEglSurface);
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext);
            }
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }

        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;
        previewEglSurface = EGL14.EGL_NO_SURFACE;
    }

    private static void checkEglObject(boolean valid, @NonNull String message) {
        if (!valid) {
            throw new IllegalStateException(message + ": 0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    @NonNull
    private static String frameRate(long frames, long elapsedNs) {
        return elapsedNs <= 0 ? "n/a" : String.valueOf(frames * 1_000_000_000.0 / elapsedNs);
    }
}
