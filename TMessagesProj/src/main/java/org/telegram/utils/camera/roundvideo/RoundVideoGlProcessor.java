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
    private final Surface outputSurface;
    private final int outputSize;
    private final boolean compositionEnabled;
    private final RoundVideoDiagnostics diagnostics;
    private int sourceCropSize;
    private boolean cameraTimestampRealtime;
    private volatile long presentationTimeOriginNs;
    private volatile long presentationTimeLimitNs = Long.MAX_VALUE;
    private final FrameTimingListener frameTimingListener;
    private final ErrorListener errorListener;

    private HandlerThread thread;
    private Handler handler;
    private SurfaceTexture inputSurfaceTexture;
    private Surface cameraSurface;

    private android.opengl.EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private android.opengl.EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private android.opengl.EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

    private int textureId;
    private int outputWidth;
    private int outputHeight;
    private final int[] eglSurfaceValue = new int[1];
    private final float[] textureMatrix = new float[16];
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
        this.inputSize = inputSize;
        this.outputSurface = outputSurface;
        this.outputSize = outputSize;
        this.sourceCropSize = sourceCropSize;
        this.cameraTimestampRealtime = cameraTimestampRealtime;
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
                + ", composition=" + compositionEnabled);
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
                sourceCropSize = cropSize;
                cameraTimestampRealtime = timestampRealtime;
                if (inputSurfaceTexture != null) {
                    inputSurfaceTexture.setDefaultBufferSize(size.getWidth(), size.getHeight());
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
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        setInputTextureFilter();
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

        inputSurfaceTexture = new SurfaceTexture(textureId);
        inputSurfaceTexture.setDefaultBufferSize(inputSize.getWidth(), inputSize.getHeight());
        inputSurfaceTexture.setOnFrameAvailableListener(surfaceTexture -> drawFrame(), handler);
        cameraSurface = new Surface(inputSurfaceTexture);
    }

    private void setInputTextureFilter() {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        int filter = sourceCropSize == outputSize ? GLES20.GL_NEAREST : GLES20.GL_LINEAR;
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

    private void drawFrame() {
        if (!started || inputSurfaceTexture == null) {
            return;
        }
        try {
            inputSurfaceTexture.updateTexImage();
            inputSurfaceTexture.getTransformMatrix(textureMatrix);
            long timestamp = inputSurfaceTexture.getTimestamp();
            inputFrames++;
            if (inputFrames == 1) {
                diagnostics.log("first GL input frame: cameraTimestampNs=" + timestamp
                        + ", elapsedMs=" + elapsedMs(glStartedNs));
            }
            if (!recordingStarted) {
                Runnable listener = firstFrameListener;
                if (!firstFrameReported && listener != null) {
                    firstFrameReported = true;
                    listener.run();
                }
                return;
            }
            if (cameraTimestampRealtime && timestamp < presentationTimeOriginNs) {
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
                if (!newCameraSessionReady) {
                    return;
                }
                boolean firstNewFrame = !hasNewCameraFrame;
                overlayRenderer.captureSwitchFrame(textureId, textureMatrix, true);
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
                    textureId,
                    textureMatrix,
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
        } catch (RuntimeException e) {
            reportError(e);
        }
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
        return sourceCropSize == outputSize ? "NEAREST" : "LINEAR";
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
    }

    private void releaseGl() {
        long elapsedNs = glStartedNs == 0 ? 0 : System.nanoTime() - glStartedNs;
        diagnostics.log("GL summary: inputFrames=" + inputFrames
                + ", inputFps=" + frameRate(inputFrames, elapsedNs)
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
        if (cameraSurface != null) {
            cameraSurface.release();
            cameraSurface = null;
        }
        if (inputSurfaceTexture != null) {
            inputSurfaceTexture.release();
            inputSurfaceTexture = null;
        }
        if (overlayRenderer != null) {
            overlayRenderer.release();
            overlayRenderer = null;
        }
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            textureId = 0;
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
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext);
            }
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }

        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;
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
