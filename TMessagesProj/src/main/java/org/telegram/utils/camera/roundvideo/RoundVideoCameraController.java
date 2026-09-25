package org.telegram.utils.camera.roundvideo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

final class RoundVideoCameraController {

    interface Callback {
        void onConfigured(@NonNull CameraInfo info, boolean cameraSwitch);

        void onRecordingStarted();

        void onCameraSwitchStarted(@NonNull RoundVideoSession.CameraFacing facing);

        void onCameraSwitchFirstFrame();

        void onRecordingStopped();

        void onError(@NonNull Exception error);
    }

    static final class CameraInfo {
        final RoundVideoSession.CameraFacing facing;
        final RoundVideoSession.CameraResolution cameraResolution;
        final RoundVideoSession.FrameRate frameRate;
        final Size previewSize;
        final Size recordingSize;
        final float maximumZoomRatio;
        final boolean torchAvailable;

        CameraInfo(
                RoundVideoSession.CameraFacing facing,
                RoundVideoSession.CameraResolution cameraResolution,
                RoundVideoSession.FrameRate frameRate,
                Size previewSize,
                Size recordingSize,
                float maximumZoomRatio,
                boolean torchAvailable
        ) {
            this.facing = facing;
            this.cameraResolution = cameraResolution;
            this.frameRate = frameRate;
            this.previewSize = previewSize;
            this.recordingSize = recordingSize;
            this.maximumZoomRatio = maximumZoomRatio;
            this.torchAvailable = torchAvailable;
        }
    }

    private static final int PREVIEW_SHORT_SIDE = 720;
    private static final int MAXIMUM_SOURCE_SHORT_SIDE = 1088;
    private static final int MAXIMUM_SOURCE_LONG_SIDE = 1920;
    private static final int MAXIMUM_CROP_OVERSIZE_PERCENT = 15;
    private static final float MAXIMUM_UI_ZOOM_RATIO = 4f;
    private static final long FRAME_METRICS_PERIOD_NS = 5_000_000_000L;
    private static final long FRAME_GAP_WARNING_NS = 50_000_000L;
    private static final long FRAME_GAP_SEVERE_NS = 100_000_000L;
    private static final int TORCH_VERIFICATION_RETRY_FRAME = 3;
    private static final int TORCH_VERIFICATION_FAILURE_FRAME = 6;
    private static final long COMMON_START_TIMEOUT_MS = 3_000L;

    private final Context context;
    private final CameraManager cameraManager;
    private final TextureView previewView;
    private final RoundVideoSession.OutputResolution outputResolution;
    private final int outputSize;
    private final int videoBitrate;
    private final RoundVideoSession.CameraResolution requestedCameraResolution;
    private final RoundVideoSession.FrameRate requestedFrameRate;
    private final boolean compositionEnabled;
    private final RoundVideoDiagnostics diagnostics;
    private final Callback callback;
    private final Rect zoomCropRect = new Rect();

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private String cameraId;
    private CameraCharacteristics characteristics;
    private Size previewSize;
    private volatile Size recordingSize;
    private volatile int sourceCropSize;
    private Surface previewSurface;
    private Surface recordingSurface;
    private RoundVideoGlProcessor glProcessor;
    private RoundVideoCodecRecorder recorder;
    private RoundVideoCodecRecorder commonStartRecorder;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder repeatingBuilder;
    private RoundVideoMp4Writer writer;
    private volatile RoundVideoSession.CameraFacing requestedFacing;
    private RoundVideoSession.CameraFacing activeFacing;
    private RoundVideoSession.CameraFacing selectedFacing;
    private RoundVideoSession.CameraResolution activeCameraResolution;
    private RoundVideoSession.FrameRate activeFrameRate;
    private Range<Integer> activeFpsRange;
    private OutputPair regularFallbackPair;
    private long timelineOffsetUs;
    private volatile float zoomProgress;
    private float maximumZoomRatio = 1f;
    private boolean torchEnabled;
    private boolean torchVerificationPending;
    private int torchVerificationFrames;
    private int torchVerificationRetries;
    private int requestGeneration;
    private int torchVerificationGeneration;
    private volatile boolean active;
    private boolean opening;
    private boolean switchingCamera;
    private boolean reopenAfterCameraClose;
    private boolean notifySwitchCompletion;
    private volatile boolean awaitingSwitchPreviewFrame;
    private volatile boolean stopping;
    private volatile boolean previewTransformEnabled;
    private boolean frontConfigurationLogged;
    private boolean backConfigurationLogged;
    private boolean frameRateFallbackAttempted;
    private boolean frameRatePolicyResolved;
    private boolean force30Fps;
    private long segmentStartedNs;
    private long cameraOpenRequestedNs;
    private long captureSessionRequestedNs;
    private long cameraSwitchStartedNs;
    private long previewFpsStartedNs;
    private long previewFrames;
    private long previewLastCallbackNs;
    private long previewLastTimestampNs;
    private long previewIntervalCount;
    private long previewIntervalTotalNs;
    private double previewIntervalSquaredTotalNs;
    private long previewIntervalMinimumNs;
    private long previewIntervalMaximumNs;
    private long previewGapsOver50Ms;
    private long previewGapsOver100Ms;
    private long previewTimestampFrames;
    private long previewTimestampFirstNs;
    private long previewTimestampLastNs;
    private long previewTimestampNonMonotonic;
    private long captureFpsFirstTimestampNs;
    private long captureFpsLastTimestampNs;
    private long captureFrames;
    private long captureLastCallbackNs;
    private long captureSensorIntervalCount;
    private long captureSensorIntervalTotalNs;
    private double captureSensorIntervalSquaredTotalNs;
    private long captureSensorIntervalMinimumNs;
    private long captureSensorIntervalMaximumNs;
    private long captureSensorGapsOver50Ms;
    private long captureSensorGapsOver100Ms;
    private long captureCallbackIntervalCount;
    private long captureCallbackIntervalTotalNs;
    private long captureCallbackIntervalMaximumNs;

    private final View.OnLayoutChangeListener layoutChangeListener = (
            view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom
    ) -> updatePreviewTransform();

    private final Runnable commonStartTimeoutRunnable = () -> {
        RoundVideoCodecRecorder pendingRecorder = commonStartRecorder;
        if (!active || stopping || pendingRecorder == null || recorder != pendingRecorder) return;
        commonStartRecorder = null;
        reportError(new IllegalStateException(
                "Timed out waiting for synchronized audio and video start"
        ));
    };

    RoundVideoCameraController(
            @NonNull Context context,
            @NonNull TextureView previewView,
            @NonNull RoundVideoSession.OutputResolution outputResolution,
            int videoBitrate,
            @NonNull RoundVideoSession.CameraResolution cameraResolution,
            @NonNull RoundVideoSession.FrameRate frameRate,
            boolean compositionEnabled,
            @NonNull RoundVideoDiagnostics diagnostics,
            @NonNull Callback callback
    ) {
        this.context = context.getApplicationContext();
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.previewView = previewView;
        this.outputResolution = outputResolution;
        this.outputSize = outputResolution.getSize();
        this.videoBitrate = videoBitrate;
        this.requestedCameraResolution = cameraResolution;
        this.requestedFrameRate = frameRate;
        this.compositionEnabled = compositionEnabled;
        this.diagnostics = diagnostics;
        this.callback = callback;
        previewView.addOnLayoutChangeListener(layoutChangeListener);
    }

    void startSegment(
            @NonNull RoundVideoMp4Writer writer,
            long timelineOffsetUs,
            @NonNull RoundVideoSession.CameraFacing facing
    ) {
        ensureThread();
        this.writer = writer;
        this.timelineOffsetUs = timelineOffsetUs;
        requestedFacing = facing;
        active = true;
        stopping = false;
        commonStartRecorder = null;
        previewTransformEnabled = true;
        segmentStartedNs = SystemClock.elapsedRealtimeNanos();
        resetPreviewFrameMetrics();
        resetCaptureFpsMeasurement();
        diagnostics.log("camera segment start: facing=" + facing
                + ", timelineOffsetUs=" + timelineOffsetUs
                + ", textureAvailable=" + previewView.isAvailable());
        previewView.setSurfaceTextureListener(previewListener);
        maybeOpenCamera();
    }

    boolean setCameraFacing(@NonNull RoundVideoSession.CameraFacing facing) {
        requestedFacing = facing;
        Handler handler = cameraHandler;
        if (!active || handler == null) return false;
        handler.post(() -> switchCameraIfNeeded(facing));
        return true;
    }

    boolean setZoom(float progress) {
        zoomProgress = clamp(progress, 0f, 1f);
        Handler handler = cameraHandler;
        if (!active || handler == null) return false;
        handler.removeCallbacks(applyZoomRunnable);
        handler.post(applyZoomRunnable);
        return true;
    }

    boolean setTorchEnabled(boolean enabled) {
        Handler handler = cameraHandler;
        if (!active || handler == null) return false;
        handler.post(() -> {
            torchEnabled = enabled;
            torchVerificationPending = isTorchAvailable();
            torchVerificationFrames = 0;
            torchVerificationRetries = 0;
            diagnostics.log("torch requested: enabled=" + enabled
                    + ", available=" + isTorchAvailable()
                    + ", cameraId=" + cameraId
                    + ", facing=" + activeFacing);
            applyRepeatingRequest();
        });
        return true;
    }

    boolean stopSegment() {
        Handler handler = cameraHandler;
        if (!active || stopping || handler == null) return false;
        stopping = true;
        previewTransformEnabled = false;
        diagnostics.log("camera segment stop requested");
        handler.post(() -> {
            active = false;
            cancelCommonStartWait();
            if (recorder != null) {
                long stopPresentationTimeUs = recorder.requestStop();
                if (glProcessor != null && stopPresentationTimeUs != Long.MAX_VALUE) {
                    glProcessor.stopRecordingAt(stopPresentationTimeUs);
                }
            }
            closeCameraAndSurfaces();
            if (recorder != null) {
                recorder.stop();
                recorder = null;
            }
            stopping = false;
            callback.onRecordingStopped();
        });
        return true;
    }

    void release() {
        active = false;
        previewView.setSurfaceTextureListener(null);
        previewView.removeOnLayoutChangeListener(layoutChangeListener);
        Handler handler = cameraHandler;
        HandlerThread thread = cameraThread;
        cameraHandler = null;
        cameraThread = null;
        if (handler == null || thread == null) return;
        handler.post(() -> {
            cancelCommonStartWait();
            if (recorder != null) {
                long stopPresentationTimeUs = recorder.requestStop();
                if (glProcessor != null && stopPresentationTimeUs != Long.MAX_VALUE) {
                    glProcessor.stopRecordingAt(stopPresentationTimeUs);
                }
            }
            closeCameraAndSurfaces();
            if (recorder != null) {
                recorder.stop();
                recorder = null;
            }
            thread.quitSafely();
        });
    }

    private void ensureThread() {
        if (cameraThread != null) return;
        cameraThread = new HandlerThread("RoundVideoCamera2");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private final Runnable applyZoomRunnable = this::applyRepeatingRequest;

    private final TextureView.SurfaceTextureListener previewListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        @NonNull SurfaceTexture surfaceTexture,
                        int width,
                        int height
                ) {
                    diagnostics.log("preview surface available: view=" + width + "x" + height);
                    maybeOpenCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        @NonNull SurfaceTexture surfaceTexture,
                        int width,
                        int height
                ) {
                    diagnostics.log("preview surface size changed: view=" + width + "x" + height);
                    updatePreviewTransform();
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                    diagnostics.log("preview surface destroyed: active=" + active);
                    if (active) {
                        reportError(new IllegalStateException("Preview SurfaceTexture was destroyed"));
                    }
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
                    if (awaitingSwitchPreviewFrame) {
                        awaitingSwitchPreviewFrame = false;
                        diagnostics.log("camera switch first preview frame");
                        callback.onCameraSwitchFirstFrame();
                    }
                    long now = SystemClock.elapsedRealtimeNanos();
                    long timestampNs = surfaceTexture.getTimestamp();
                    if (previewFpsStartedNs == 0L) {
                        previewFpsStartedNs = now;
                        diagnostics.log("preview frame delivery started: thread="
                                + Thread.currentThread().getName()
                                + ", view=" + previewView.getWidth() + "x" + previewView.getHeight()
                                + ", buffer=" + previewSize
                                + ", attached=" + previewView.isAttachedToWindow()
                                + ", shown=" + previewView.isShown()
                                + ", alpha=" + previewView.getAlpha()
                                + ", hardwareAccelerated=" + previewView.isHardwareAccelerated()
                                + ", displayRefreshRate=" + (previewView.getDisplay() == null
                                ? "unknown" : previewView.getDisplay().getRefreshRate()));
                    }
                    if (previewLastCallbackNs != 0L) {
                        recordPreviewInterval(now - previewLastCallbackNs);
                    }
                    previewLastCallbackNs = now;
                    if (timestampNs > previewLastTimestampNs) {
                        if (previewTimestampFirstNs == 0L) previewTimestampFirstNs = timestampNs;
                        previewTimestampLastNs = timestampNs;
                        previewTimestampFrames++;
                    } else if (previewLastTimestampNs != 0L) {
                        previewTimestampNonMonotonic++;
                    }
                    previewLastTimestampNs = timestampNs;
                    previewFrames++;
                    long elapsedNs = now - previewFpsStartedNs;
                    if (elapsedNs >= FRAME_METRICS_PERIOD_NS) {
                        logPreviewFrameMetrics(now, elapsedNs);
                    }
                }
            };

    private void maybeOpenCamera() {
        Handler handler = cameraHandler;
        if (!active || handler == null || !previewView.isAvailable()) return;
        handler.post(this::openCameraIfReady);
    }

    @SuppressLint("MissingPermission")
    private void openCameraIfReady() {
        if (!active || opening || cameraDevice != null) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            reportError(new SecurityException("Camera permission is not granted"));
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            reportError(new SecurityException("Audio recording permission is not granted"));
            return;
        }
        try {
            selectCameraAndSizes(requestedFacing);
            SurfaceTexture previewTexture = previewView.getSurfaceTexture();
            if (previewTexture == null) return;
            previewTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            if (previewSurface == null) previewSurface = new Surface(previewTexture);
            if (recorder == null) {
                createRecordingPipeline();
            } else if (glProcessor != null) {
                glProcessor.updateInputConfiguration(
                        recordingSize,
                        sourceCropSize,
                        isCameraTimestampRealtime()
                );
                recordingSurface = glProcessor.getCameraSurface();
            }
            opening = true;
            cameraOpenRequestedNs = SystemClock.elapsedRealtimeNanos();
            diagnostics.log("camera open requested: id=" + cameraId
                    + ", preview=" + previewSize
                    + ", recording=" + recordingSize
                    + ", crop=" + sourceCropSize);
            cameraManager.openCamera(cameraId, deviceCallback, cameraHandler);
        } catch (Exception e) {
            opening = false;
            reportError(e);
        }
    }

    private void createRecordingPipeline() throws IOException {
        recorder = new RoundVideoCodecRecorder(
                writer,
                timelineOffsetUs,
                outputSize,
                videoBitrate,
                activeFrameRate.getValue(),
                diagnostics,
                callback::onError
        );
        Surface encoderSurface = recorder.prepare();
        glProcessor = new RoundVideoGlProcessor(
                recordingSize,
                encoderSurface,
                outputSize,
                sourceCropSize,
                isCameraTimestampRealtime(),
                compositionEnabled,
                diagnostics,
                0,
                recorder,
                callback::onError
        );
        glProcessor.setFirstFrameListener(() -> {
            Handler handler = cameraHandler;
            if (handler != null) handler.post(this::startRecordingAfterFirstFrame);
        });
        recordingSurface = glProcessor.start();
    }

    private void switchCameraIfNeeded(@NonNull RoundVideoSession.CameraFacing facing) {
        if (!active || switchingCamera || facing == activeFacing) return;
        switchingCamera = true;
        notifySwitchCompletion = true;
        torchEnabled = false;
        torchVerificationPending = false;
        zoomProgress = 0f;
        callback.onCameraSwitchStarted(facing);
        cameraSwitchStartedNs = SystemClock.elapsedRealtimeNanos();
        diagnostics.log("camera device switch started: from=" + activeFacing + ", to=" + facing);
        if (glProcessor != null) glProcessor.beginCameraSwitch(activeFacing, facing);
        closeCaptureSession();
        if (cameraDevice != null) {
            CameraDevice device = cameraDevice;
            cameraDevice = null;
            device.close();
        } else if (!opening) {
            switchingCamera = false;
            openCameraIfReady();
        }
    }

    private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            opening = false;
            diagnostics.log("camera opened: id=" + device.getId()
                    + ", elapsedMs=" + elapsedMs(cameraOpenRequestedNs));
            if (!active || switchingCamera) {
                device.close();
                return;
            }
            cameraDevice = device;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice device) {
            opening = false;
            diagnostics.log("camera disconnected: id=" + device.getId());
            boolean unexpected = active
                    && !stopping
                    && !switchingCamera
                    && !reopenAfterCameraClose;
            if (cameraDevice == device) {
                closeCaptureSession();
                cameraDevice = null;
            }
            device.close();
            if (unexpected) {
                reportError(new IllegalStateException("Camera device disconnected"));
            }
        }

        @Override
        public void onError(@NonNull CameraDevice device, int error) {
            opening = false;
            if (cameraDevice == device) {
                closeCaptureSession();
                cameraDevice = null;
            }
            device.close();
            reportError(new IllegalStateException("Camera device error: "
                    + cameraErrorName(error) + " (" + error + ")"));
        }

        @Override
        public void onClosed(@NonNull CameraDevice device) {
            if (switchingCamera) {
                switchingCamera = false;
                if (active) openCameraIfReady();
            } else if (reopenAfterCameraClose) {
                reopenAfterCameraClose = false;
                if (active) openCameraIfReady();
            }
        }
    };

    private void createCaptureSession() {
        CameraDevice device = cameraDevice;
        if (device == null || previewSurface == null || recordingSurface == null) return;
        try {
            captureSessionRequestedNs = SystemClock.elapsedRealtimeNanos();
            diagnostics.log("capture session requested: preview=" + previewSize
                    + ", recording=" + recordingSize
                    + ", fpsRange=" + activeFpsRange);
            device.createCaptureSession(
                    Arrays.asList(previewSurface, recordingSurface),
                    sessionCallback,
                    cameraHandler
            );
        } catch (CameraAccessException | IllegalArgumentException e) {
            if (activeFrameRate == RoundVideoSession.FrameRate.FPS_60) {
                fallbackTo30Fps("60 fps session creation rejected", e);
            } else {
                reportError(e);
            }
        }
    }

    private final CameraCaptureSession.StateCallback sessionCallback =
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (!active || cameraDevice == null) {
                        session.close();
                        return;
                    }
                    captureSession = session;
                    try {
                        activeFacing = selectedFacing;
                        repeatingBuilder = createRepeatingRequestBuilder(true);
                        if (glProcessor != null) glProcessor.onCameraSessionConfigured();
                        boolean wasSwitch = notifySwitchCompletion;
                        notifySwitchCompletion = false;
                        awaitingSwitchPreviewFrame = wasSwitch;
                        submitRepeatingRequest();
                        diagnostics.log("capture session configured: facing=" + activeFacing
                                + ", fpsRange=" + activeFpsRange
                                + ", elapsedMs=" + elapsedMs(captureSessionRequestedNs)
                                + ", segmentElapsedMs=" + elapsedMs(segmentStartedNs)
                                + (wasSwitch ? ", switchElapsedMs="
                                + elapsedMs(cameraSwitchStartedNs) : ""));
                        previewView.post(RoundVideoCameraController.this::updatePreviewTransform);
                        callback.onConfigured(
                                new CameraInfo(
                                        activeFacing,
                                        activeCameraResolution,
                                        activeFrameRate,
                                        previewSize,
                                        recordingSize,
                                        maximumZoomRatio,
                                        isTorchAvailable()
                                ),
                                wasSwitch
                        );
                        if (requestedFacing != activeFacing) switchCameraIfNeeded(requestedFacing);
                    } catch (Exception e) {
                        if (activeFrameRate == RoundVideoSession.FrameRate.FPS_60) {
                            fallbackTo30Fps("60 fps request submission rejected", e);
                        } else {
                            reportError(e);
                        }
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    session.close();
                    if (captureSession == session) {
                        captureSession = null;
                        repeatingBuilder = null;
                    }
                    if (!active || cameraDevice == null || switchingCamera || stopping) {
                        diagnostics.log("stale capture session configuration failure ignored");
                        return;
                    }
                    if (activeFrameRate == RoundVideoSession.FrameRate.FPS_60) {
                        fallbackTo30Fps("60 fps session configuration failed", null);
                    } else {
                        reportError(new IllegalStateException(
                                "Camera capture session configuration failed"
                        ));
                    }
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    if (captureSession != session) return;
                    captureSession = null;
                    repeatingBuilder = null;
                    torchVerificationPending = false;
                    diagnostics.log("capture session closed");
                }
            };

    private void startRecordingAfterFirstFrame() {
        if (!active || captureSession == null || recorder == null || recorder.isStarted()) return;
        try {
            RoundVideoCodecRecorder currentRecorder = recorder;
            commonStartRecorder = currentRecorder;
            Handler handler = cameraHandler;
            if (handler != null) {
                handler.removeCallbacks(commonStartTimeoutRunnable);
                handler.postDelayed(commonStartTimeoutRunnable, COMMON_START_TIMEOUT_MS);
            }
            currentRecorder.startRecording(
                    () -> postToCameraThread(
                            () -> startVideoAfterAudioReady(currentRecorder)
                    ),
                    () -> postToCameraThread(
                            () -> announceRecordingStarted(currentRecorder)
                    )
            );
            diagnostics.log("first camera frame received; audio startup requested: segmentElapsedMs="
                    + elapsedMs(segmentStartedNs));
        } catch (RuntimeException e) {
            cancelCommonStartWait();
            reportError(e);
        }
    }

    private void startVideoAfterAudioReady(@NonNull RoundVideoCodecRecorder expectedRecorder) {
        if (!active || stopping || recorder != expectedRecorder
                || captureSession == null || glProcessor == null) {
            return;
        }
        try {
            glProcessor.startRecording(expectedRecorder.getTimeOriginNs());
            diagnostics.log("common A/V start armed; waiting for next camera frame: segmentElapsedMs="
                    + elapsedMs(segmentStartedNs));
        } catch (RuntimeException e) {
            cancelCommonStartWait();
            reportError(e);
        }
    }

    private void announceRecordingStarted(@NonNull RoundVideoCodecRecorder expectedRecorder) {
        if (!active || stopping || recorder != expectedRecorder) return;
        cancelCommonStartWait();
        diagnostics.log("common A/V start completed: segmentElapsedMs="
                + elapsedMs(segmentStartedNs));
        callback.onRecordingStarted();
    }

    private void postToCameraThread(@NonNull Runnable runnable) {
        Handler handler = cameraHandler;
        if (handler != null) handler.post(runnable);
    }

    private void cancelCommonStartWait() {
        Handler handler = cameraHandler;
        if (handler != null) handler.removeCallbacks(commonStartTimeoutRunnable);
        commonStartRecorder = null;
    }

    private void applyRepeatingRequest() {
        CameraCaptureSession session = captureSession;
        if (!active || session == null || cameraDevice == null) return;
        try {
            repeatingBuilder = createRepeatingRequestBuilder(false);
            submitRepeatingRequest();
        } catch (IllegalStateException e) {
            recoverFromClosedCamera(session, e);
        } catch (CameraAccessException | IllegalArgumentException e) {
            if (activeFrameRate == RoundVideoSession.FrameRate.FPS_60) {
                fallbackTo30Fps("60 fps updated request rejected", e);
            } else {
                reportError(e);
            }
        }
    }

    private void recoverFromClosedCamera(
            @NonNull CameraCaptureSession session,
            @NonNull IllegalStateException error
    ) {
        if (!active || stopping || session != captureSession) {
            diagnostics.log("stale repeating request rejection ignored: " + error.getMessage());
            return;
        }

        diagnostics.log("repeating request rejected by closed camera; reopening device: "
                + error.getMessage());
        closeCaptureSession();
        CameraDevice device = cameraDevice;
        cameraDevice = null;
        opening = false;
        if (device != null) {
            reopenAfterCameraClose = true;
            device.close();
        } else {
            openCameraIfReady();
        }
    }

    @NonNull
    private CaptureRequest.Builder createRepeatingRequestBuilder(boolean logConfiguration)
            throws CameraAccessException {
        CameraDevice device = cameraDevice;
        if (device == null || previewSurface == null || recordingSurface == null) {
            throw new IllegalStateException("Camera request surfaces are unavailable");
        }
        CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        builder.addTarget(previewSurface);
        builder.addTarget(recordingSurface);
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(
                CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD
        );
        setContinuousVideoAf(builder);
        setPreferredFps(builder, logConfiguration);
        applyZoom(builder);
        applyTorch(builder);
        requestGeneration++;
        builder.setTag(requestGeneration);
        if (torchVerificationPending) torchVerificationGeneration = requestGeneration;
        return builder;
    }

    private void submitRepeatingRequest() throws CameraAccessException {
        CameraCaptureSession session = captureSession;
        CaptureRequest.Builder builder = repeatingBuilder;
        if (session == null || builder == null) return;
        CaptureRequest request = builder.build();
        session.setRepeatingRequest(request, captureCallback, cameraHandler);
    }

    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(
                        @NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull TotalCaptureResult result
                ) {
                    verifyTorchResult(request, result);
                    Long timestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP);
                    if (timestampNs == null || timestampNs <= captureFpsLastTimestampNs) return;
                    long callbackNs = SystemClock.elapsedRealtimeNanos();
                    if (captureFpsFirstTimestampNs == 0L) {
                        captureFpsFirstTimestampNs = timestampNs;
                        captureFrames = 1L;
                    } else {
                        recordCaptureSensorInterval(timestampNs - captureFpsLastTimestampNs);
                        captureFrames++;
                    }
                    if (captureLastCallbackNs != 0L) {
                        long callbackIntervalNs = callbackNs - captureLastCallbackNs;
                        captureCallbackIntervalCount++;
                        captureCallbackIntervalTotalNs += callbackIntervalNs;
                        captureCallbackIntervalMaximumNs = Math.max(
                                captureCallbackIntervalMaximumNs,
                                callbackIntervalNs
                        );
                    }
                    captureLastCallbackNs = callbackNs;
                    captureFpsLastTimestampNs = timestampNs;
                    long durationNs = timestampNs - captureFpsFirstTimestampNs;
                    if (durationNs < 3_000_000_000L) return;
                    float measuredFps = (captureFrames - 1L) * 1_000_000_000f / durationNs;
                    int expectedFps = activeFrameRate == null
                            ? 0
                            : activeFrameRate.getValue();
                    diagnostics.log("camera capture rate: measuredFps=" + measuredFps
                            + ", requestedFps=" + requestedFrameRate.getValue()
                            + ", activeFps=" + (expectedFps == 0 ? "unknown" : expectedFps)
                            + ", targetMet=" + (expectedFps == 0
                            || measuredFps >= expectedFps * 0.85f)
                            + ", fpsRange=" + activeFpsRange
                            + ", sensorIntervalMs={avg="
                            + averageMs(captureSensorIntervalTotalNs, captureSensorIntervalCount)
                            + ", min=" + nanosToMs(captureSensorIntervalMinimumNs)
                            + ", max=" + nanosToMs(captureSensorIntervalMaximumNs)
                            + ", jitter=" + standardDeviationMs(
                            captureSensorIntervalTotalNs,
                            captureSensorIntervalSquaredTotalNs,
                            captureSensorIntervalCount)
                            + "}, sensorGaps={over50ms=" + captureSensorGapsOver50Ms
                            + ", over100ms=" + captureSensorGapsOver100Ms
                            + "}, callbackIntervalMs={avg="
                            + averageMs(captureCallbackIntervalTotalNs, captureCallbackIntervalCount)
                            + ", max=" + nanosToMs(captureCallbackIntervalMaximumNs) + "}");
                    resetCaptureFpsMeasurement();
                    captureFpsFirstTimestampNs = timestampNs;
                    captureFpsLastTimestampNs = timestampNs;
                    captureFrames = 1L;
                    captureLastCallbackNs = callbackNs;
                }
            };

    private void verifyTorchResult(
            @NonNull CaptureRequest request,
            @NonNull TotalCaptureResult result
    ) {
        if (!torchVerificationPending) return;
        Object tag = request.getTag();
        if (!(tag instanceof Integer) || (Integer) tag != torchVerificationGeneration) return;
        torchVerificationFrames++;
        Integer resultMode = result.get(CaptureResult.FLASH_MODE);
        Integer flashState = result.get(CaptureResult.FLASH_STATE);
        boolean modeMatches = torchEnabled
                ? resultMode != null && resultMode == CaptureResult.FLASH_MODE_TORCH
                : resultMode == null || resultMode == CaptureResult.FLASH_MODE_OFF;
        boolean stateMatches = !torchEnabled
                || flashState == null
                || flashState == CaptureResult.FLASH_STATE_FIRED
                || flashState == CaptureResult.FLASH_STATE_PARTIAL;
        if (modeMatches && stateMatches) {
            diagnostics.log("torch result confirmed: enabled=" + torchEnabled
                    + ", resultMode=" + resultMode
                    + ", flashState=" + flashState
                    + ", frames=" + torchVerificationFrames
                    + ", retries=" + torchVerificationRetries);
            torchVerificationPending = false;
            return;
        }
        if (torchVerificationFrames == TORCH_VERIFICATION_RETRY_FRAME
                && torchVerificationRetries == 0) {
            torchVerificationRetries++;
            torchVerificationFrames = 0;
            diagnostics.log("torch result not applied; rebuilding request: enabled="
                    + torchEnabled
                    + ", resultMode=" + resultMode
                    + ", flashState=" + flashState);
            applyRepeatingRequest();
        } else if (torchVerificationFrames >= TORCH_VERIFICATION_FAILURE_FRAME) {
            diagnostics.log("torch result failed: enabled=" + torchEnabled
                    + ", resultMode=" + resultMode
                    + ", flashState=" + flashState
                    + ", retries=" + torchVerificationRetries);
            torchVerificationPending = false;
        }
    }

    private void recordPreviewInterval(long intervalNs) {
        previewIntervalCount++;
        previewIntervalTotalNs += intervalNs;
        previewIntervalSquaredTotalNs += (double) intervalNs * intervalNs;
        if (previewIntervalMinimumNs == 0L || intervalNs < previewIntervalMinimumNs) {
            previewIntervalMinimumNs = intervalNs;
        }
        previewIntervalMaximumNs = Math.max(previewIntervalMaximumNs, intervalNs);
        if (intervalNs > FRAME_GAP_WARNING_NS) previewGapsOver50Ms++;
        if (intervalNs > FRAME_GAP_SEVERE_NS) previewGapsOver100Ms++;
    }

    private void logPreviewFrameMetrics(long nowNs, long elapsedNs) {
        long timestampDurationNs = previewTimestampLastNs - previewTimestampFirstNs;
        float timestampFps = timestampDurationNs > 0L && previewTimestampFrames > 1L
                ? (previewTimestampFrames - 1L) * 1_000_000_000f / timestampDurationNs
                : 0f;
        diagnostics.log("preview frame delivery: callbackFps="
                + previewFrames * 1_000_000_000f / elapsedNs
                + ", timestampFps=" + timestampFps
                + ", callbackIntervalMs={avg="
                + averageMs(previewIntervalTotalNs, previewIntervalCount)
                + ", min=" + nanosToMs(previewIntervalMinimumNs)
                + ", max=" + nanosToMs(previewIntervalMaximumNs)
                + ", jitter=" + standardDeviationMs(
                previewIntervalTotalNs,
                previewIntervalSquaredTotalNs,
                previewIntervalCount)
                + "}, gaps={over50ms=" + previewGapsOver50Ms
                + ", over100ms=" + previewGapsOver100Ms
                + "}, nonMonotonicTimestamps=" + previewTimestampNonMonotonic
                + ", viewState={shown=" + previewView.isShown()
                + ", alpha=" + previewView.getAlpha()
                + ", windowVisibility=" + previewView.getWindowVisibility() + "}");
        resetPreviewFrameMetrics();
        previewFpsStartedNs = nowNs;
    }

    private void recordCaptureSensorInterval(long intervalNs) {
        captureSensorIntervalCount++;
        captureSensorIntervalTotalNs += intervalNs;
        captureSensorIntervalSquaredTotalNs += (double) intervalNs * intervalNs;
        if (captureSensorIntervalMinimumNs == 0L
                || intervalNs < captureSensorIntervalMinimumNs) {
            captureSensorIntervalMinimumNs = intervalNs;
        }
        captureSensorIntervalMaximumNs = Math.max(captureSensorIntervalMaximumNs, intervalNs);
        if (intervalNs > FRAME_GAP_WARNING_NS) captureSensorGapsOver50Ms++;
        if (intervalNs > FRAME_GAP_SEVERE_NS) captureSensorGapsOver100Ms++;
    }

    private void resetPreviewFrameMetrics() {
        previewFpsStartedNs = 0L;
        previewFrames = 0L;
        previewLastCallbackNs = 0L;
        previewLastTimestampNs = 0L;
        previewIntervalCount = 0L;
        previewIntervalTotalNs = 0L;
        previewIntervalSquaredTotalNs = 0d;
        previewIntervalMinimumNs = 0L;
        previewIntervalMaximumNs = 0L;
        previewGapsOver50Ms = 0L;
        previewGapsOver100Ms = 0L;
        previewTimestampFrames = 0L;
        previewTimestampFirstNs = 0L;
        previewTimestampLastNs = 0L;
        previewTimestampNonMonotonic = 0L;
    }

    private void applyZoom(@NonNull CaptureRequest.Builder builder) {
        float ratio = 1f + zoomProgress * (maximumZoomRatio - 1f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> range = characteristics == null
                    ? null
                    : characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range != null) {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, clamp(ratio, range.getLower(), range.getUpper()));
                return;
            }
        }
        Rect activeArray = characteristics == null
                ? null
                : characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (activeArray == null) return;
        int cropWidth = Math.max(1, Math.round(activeArray.width() / ratio));
        int cropHeight = Math.max(1, Math.round(activeArray.height() / ratio));
        int left = activeArray.centerX() - cropWidth / 2;
        int top = activeArray.centerY() - cropHeight / 2;
        zoomCropRect.set(left, top, left + cropWidth, top + cropHeight);
        builder.set(CaptureRequest.SCALER_CROP_REGION, zoomCropRect);
    }

    private void applyTorch(@NonNull CaptureRequest.Builder builder) {
        boolean enabled = torchEnabled && isTorchAvailable();
        int[] aeModes = characteristics == null
                ? null
                : characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
        if (contains(aeModes, CaptureRequest.CONTROL_AE_MODE_ON)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        }
        builder.set(
                CaptureRequest.FLASH_MODE,
                enabled
                        ? CaptureRequest.FLASH_MODE_TORCH
                        : CaptureRequest.FLASH_MODE_OFF
        );
        if (torchVerificationPending) {
            diagnostics.log("torch request configured: requested=" + torchEnabled
                    + ", applied=" + enabled
                    + ", aeMode=" + builder.get(CaptureRequest.CONTROL_AE_MODE)
                    + ", flashMode=" + builder.get(CaptureRequest.FLASH_MODE)
                    + ", cameraId=" + cameraId);
        }
    }

    private void selectCameraAndSizes(@NonNull RoundVideoSession.CameraFacing facing)
            throws CameraAccessException {
        resolveSessionFrameRatePolicy();
        int requiredFacing = facing == RoundVideoSession.CameraFacing.FRONT
                ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK;
        CameraCandidate fallbackCandidate = null;
        CameraCandidate selectedCandidate = null;
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics candidate = cameraManager.getCameraCharacteristics(id);
            Integer candidateFacing = candidate.get(CameraCharacteristics.LENS_FACING);
            if (candidateFacing == null || candidateFacing != requiredFacing) continue;
            StreamConfigurationMap candidateMap = candidate.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            );
            if (candidateMap == null) {
                diagnostics.log("camera candidate rejected: id=" + id + ", reason=no stream map");
                continue;
            }
            Size[] candidateSizes = candidateMap.getOutputSizes(SurfaceTexture.class);
            if (candidateSizes == null || candidateSizes.length == 0) {
                diagnostics.log("camera candidate rejected: id=" + id
                        + ", reason=no SurfaceTexture outputs");
                continue;
            }
            try {
                OutputPair regularPair = chooseOutputPair(
                        candidateSizes,
                        outputResolution,
                        requestedCameraResolution
                );
                FrameRatePlan frameRatePlan = resolveFrameRate(
                        id,
                        candidate,
                        candidateMap,
                        candidateSizes,
                        regularPair
                );
                CameraCandidate cameraCandidate = new CameraCandidate(
                        id,
                        candidate,
                        candidateSizes,
                        regularPair,
                        frameRatePlan
                );
                diagnostics.log("camera candidate accepted: id=" + id
                        + ", facing=" + facing
                        + ", flashAvailable=" + hasFlashUnit(candidate)
                        + ", fps=" + frameRatePlan.frameRate.getValue()
                        + ", preview=" + frameRatePlan.outputPair.preview
                        + ", recording=" + frameRatePlan.outputPair.recording);
                if (fallbackCandidate == null
                        || isPreferredCameraCandidate(
                                cameraCandidate,
                                fallbackCandidate,
                                facing
                        )) {
                    fallbackCandidate = cameraCandidate;
                }
                if (requestedFrameRate == RoundVideoSession.FrameRate.FPS_30
                        || frameRatePlan.frameRate == requestedFrameRate) {
                    if (selectedCandidate == null
                            || isPreferredCameraCandidate(
                                    cameraCandidate,
                                    selectedCandidate,
                                    facing
                            )) {
                        selectedCandidate = cameraCandidate;
                    }
                }
            } catch (RuntimeException e) {
                diagnostics.log("camera candidate rejected: id=" + id
                        + ", reason=" + e);
            }
        }
        if (selectedCandidate == null) selectedCandidate = fallbackCandidate;
        if (selectedCandidate == null) {
            throw new IllegalStateException("Requested camera is not available");
        }
        String selectedId = selectedCandidate.id;
        CameraCharacteristics selectedCharacteristics = selectedCandidate.characteristics;
        Size[] sizes = selectedCandidate.sizes;
        OutputPair regularPair = selectedCandidate.regularPair;
        FrameRatePlan frameRatePlan = selectedCandidate.frameRatePlan;
        OutputPair pair = frameRatePlan.outputPair;
        cameraId = selectedId;
        selectedFacing = facing;
        characteristics = selectedCharacteristics;
        activeFrameRate = frameRatePlan.frameRate;
        activeFpsRange = frameRatePlan.fpsRange;
        frameRateFallbackAttempted = false;
        regularFallbackPair = regularPair;
        resetCaptureFpsMeasurement();
        previewSize = pair.preview;
        recordingSize = pair.recording;
        sourceCropSize = pair.cropSize;
        activeCameraResolution = pair.cameraResolution;
        maximumZoomRatio = readMaximumZoomRatio(selectedCharacteristics);
        long previewMinFrameDurationNs = getMinimumFrameDurationNs(
                selectedCharacteristics,
                previewSize
        );
        long recordingMinFrameDurationNs = getMinimumFrameDurationNs(
                selectedCharacteristics,
                recordingSize
        );
        diagnostics.log("camera outputs selected: output=" + outputSize + "x" + outputSize
                + ", cameraMode=" + activeCameraResolution
                + (activeCameraResolution != requestedCameraResolution
                ? " (fallback from " + requestedCameraResolution + ")" : "")
                + ", crop=" + sourceCropSize
                + ", preview=" + previewSize
                + ", recording=" + recordingSize
                + ", previewMinFrameDurationNs=" + previewMinFrameDurationNs
                + ", recordingMinFrameDurationNs=" + recordingMinFrameDurationNs
                + ", fps=" + activeFrameRate.getValue()
                + ", fpsRange=" + activeFpsRange
                + ", timestampSource="
                + (isCameraTimestampRealtime() ? "REALTIME" : "UNKNOWN")
                + (activeFrameRate != requestedFrameRate
                ? " (fallback from " + requestedFrameRate.getValue() + ")" : ""));
        logCameraConfigurationOnce(facing, selectedId, selectedCharacteristics, sizes);
    }

    private void resolveSessionFrameRatePolicy() throws CameraAccessException {
        if (frameRatePolicyResolved) return;
        frameRatePolicyResolved = true;
        if (requestedFrameRate != RoundVideoSession.FrameRate.FPS_60) return;
        boolean front = supports60FpsForFacing(RoundVideoSession.CameraFacing.FRONT);
        boolean back = supports60FpsForFacing(RoundVideoSession.CameraFacing.BACK);
        force30Fps = !front || !back;
        diagnostics.log("60 fps session capability: front=" + front
                + ", back=" + back
                + ", selected=" + (force30Fps ? 30 : 60));
    }

    private boolean supports60FpsForFacing(@NonNull RoundVideoSession.CameraFacing facing)
            throws CameraAccessException {
        int requiredFacing = facing == RoundVideoSession.CameraFacing.FRONT
                ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK;
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics candidate = cameraManager.getCameraCharacteristics(id);
            Integer candidateFacing = candidate.get(CameraCharacteristics.LENS_FACING);
            if (candidateFacing == null || candidateFacing != requiredFacing) continue;
            StreamConfigurationMap map = candidate.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            );
            if (map == null) continue;
            Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
            if (sizes == null || sizes.length == 0) continue;
            try {
                OutputPair regularPair = chooseOutputPair(
                        sizes,
                        outputResolution,
                        requestedCameraResolution
                );
                if (resolveFrameRate(id, candidate, map, sizes, regularPair).frameRate
                        == RoundVideoSession.FrameRate.FPS_60) {
                    return true;
                }
            } catch (RuntimeException ignore) {
            }
        }
        return false;
    }

    private float readMaximumZoomRatio(@NonNull CameraCharacteristics cameraCharacteristics) {
        float maximum = 1f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> range = cameraCharacteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (range != null) maximum = range.getUpper();
        } else {
            Float value = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            if (value != null) maximum = value;
        }
        return clamp(maximum, 1f, MAXIMUM_UI_ZOOM_RATIO);
    }

    private boolean isTorchAvailable() {
        return activeFacing == RoundVideoSession.CameraFacing.BACK
                && characteristics != null
                && hasFlashUnit(characteristics);
    }

    private boolean isCameraTimestampRealtime() {
        if (characteristics == null) return false;
        Integer source = characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        return source != null
                && source == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;
    }

    private static boolean isPreferredCameraCandidate(
            @NonNull CameraCandidate candidate,
            @NonNull CameraCandidate current,
            @NonNull RoundVideoSession.CameraFacing facing
    ) {
        return facing == RoundVideoSession.CameraFacing.BACK
                && hasFlashUnit(candidate.characteristics)
                && !hasFlashUnit(current.characteristics);
    }

    private static boolean hasFlashUnit(@NonNull CameraCharacteristics characteristics) {
        return Boolean.TRUE.equals(
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        );
    }

    private void setContinuousVideoAf(@NonNull CaptureRequest.Builder builder) {
        int[] modes = characteristics == null
                ? null
                : characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (contains(modes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        }
    }

    @NonNull
    private FrameRatePlan resolveFrameRate(
            @NonNull String id,
            @NonNull CameraCharacteristics cameraCharacteristics,
            @NonNull StreamConfigurationMap map,
            @NonNull Size[] sizes,
            @NonNull OutputPair regularPair
    ) {
        Range<Integer>[] ranges = cameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        );
        if (requestedFrameRate == RoundVideoSession.FrameRate.FPS_30 || force30Fps) {
            Range<Integer> range = findBestFpsRange(ranges, 30);
            diagnostics.log("fps selection: id=" + id
                    + ", requested=" + requestedFrameRate.getValue()
                    + ", mode=REGULAR, range=" + range
                    + (force30Fps ? ", reason=session-wide fallback" : ""));
            return new FrameRatePlan(
                    RoundVideoSession.FrameRate.FPS_30,
                    range,
                    regularPair
            );
        }
        final int targetFps = requestedFrameRate.getValue();
        Range<Integer> normalRange = findBestFpsRange(ranges, targetFps);
        if (normalRange != null) {
            Size[] fastSizes = filterSizesForFrameRate(map, sizes, targetFps);
            try {
                OutputPair pair = chooseOutputPair(
                        fastSizes,
                        outputResolution,
                        requestedCameraResolution
                );
                diagnostics.log("fps selection: id=" + id
                        + ", requested=" + targetFps
                        + ", mode=REGULAR, range=" + normalRange
                        + ", compatibleSizes=" + Arrays.toString(fastSizes));
                return new FrameRatePlan(
                        RoundVideoSession.FrameRate.FPS_60,
                        normalRange,
                        pair
                );
            } catch (RuntimeException e) {
                diagnostics.log("fps selection: id=" + id
                        + ", regular " + targetFps + " fps rejected: no compatible output pair"
                        + ", compatibleSizes=" + Arrays.toString(fastSizes));
            }
        } else {
            diagnostics.log("fps selection: id=" + id
                    + ", regular " + targetFps + " fps rejected: advertisedRanges="
                    + Arrays.toString(ranges));
        }

        Range<Integer> fallbackRange = findBestFpsRange(ranges, 30);
        diagnostics.log("fps selection: id=" + id
                + ", requested=" + targetFps
                + ", fallback=30, range=" + fallbackRange);
        return new FrameRatePlan(
                RoundVideoSession.FrameRate.FPS_30,
                fallbackRange,
                regularPair
        );
    }

    private static boolean supportsFrameDuration(
            @NonNull StreamConfigurationMap map,
            @NonNull Size size,
            int targetFps
    ) {
        long minimumDurationNs = map.getOutputMinFrameDuration(SurfaceTexture.class, size);
        return minimumDurationNs <= 0L || minimumDurationNs <= 1_000_000_000L / targetFps;
    }

    private static long getMinimumFrameDurationNs(
            @NonNull CameraCharacteristics cameraCharacteristics,
            @NonNull Size size
    ) {
        StreamConfigurationMap map = cameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        );
        if (map == null) return -1L;
        try {
            return map.getOutputMinFrameDuration(SurfaceTexture.class, size);
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    private void setPreferredFps(
            @NonNull CaptureRequest.Builder builder,
            boolean logConfiguration
    ) {
        if (activeFpsRange != null) {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, activeFpsRange);
            if (logConfiguration) {
                diagnostics.log("capture FPS range selected: " + activeFpsRange);
            }
        } else if (logConfiguration) {
            diagnostics.log("capture FPS range unavailable; HAL default will be used");
        }
    }

    private void fallbackTo30Fps(
            @NonNull String reason,
            @Nullable Exception error
    ) {
        if (frameRateFallbackAttempted) {
            reportError(error != null
                    ? error
                    : new IllegalStateException("30 fps fallback session failed"));
            return;
        }
        frameRateFallbackAttempted = true;
        closeCaptureSession();
        OutputPair pair = regularFallbackPair;
        if (pair == null) {
            reportError(new IllegalStateException("Regular camera fallback is unavailable", error));
            return;
        }
        activeFrameRate = RoundVideoSession.FrameRate.FPS_30;
        Range<Integer>[] ranges = characteristics == null
                ? null
                : characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        activeFpsRange = findBestFpsRange(ranges, 30);
        previewSize = pair.preview;
        recordingSize = pair.recording;
        sourceCropSize = pair.cropSize;
        activeCameraResolution = pair.cameraResolution;
        SurfaceTexture previewTexture = previewView.getSurfaceTexture();
        if (previewTexture != null) {
            previewTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        }
        if (recorder != null && recorder.isStarted()) {
            reportError(new IllegalStateException(
                    "Unable to change encoder frame rate after recording started",
                    error
            ));
            return;
        }
        if (glProcessor != null) {
            glProcessor.stop();
            glProcessor = null;
        }
        if (recorder != null) {
            recorder.stop();
            recorder = null;
        }
        recordingSurface = null;
        try {
            createRecordingPipeline();
        } catch (Exception pipelineError) {
            reportError(pipelineError);
            return;
        }
        resetCaptureFpsMeasurement();
        diagnostics.log("60 fps fallback: reason=" + reason
                + (error == null ? "" : ", error=" + error)
                + ", preview=" + previewSize
                + ", recording=" + recordingSize
                + ", crop=" + sourceCropSize
                + ", fpsRange=" + activeFpsRange);
        Handler handler = cameraHandler;
        if (active && handler != null) handler.post(this::createCaptureSession);
    }

    @Nullable
    private static Range<Integer> findBestFpsRange(
            @Nullable Range<Integer>[] ranges,
            int targetFps
    ) {
        if (ranges == null) return null;
        Range<Integer> best = null;
        for (Range<Integer> range : ranges) {
            if (!range.contains(targetFps)) continue;
            if (best == null
                    || range.getLower() > best.getLower()
                    || range.getLower().equals(best.getLower())
                    && range.getUpper() < best.getUpper()) {
                best = range;
            }
        }
        return best;
    }

    @NonNull
    private static Size[] filterSizesForFrameRate(
            @NonNull StreamConfigurationMap map,
            @NonNull Size[] sizes,
            int targetFps
    ) {
        ArrayList<Size> result = new ArrayList<>(sizes.length);
        for (Size size : sizes) {
            if (supportsFrameDuration(map, size, targetFps)) result.add(size);
        }
        return result.toArray(new Size[0]);
    }

    private void resetCaptureFpsMeasurement() {
        captureFpsFirstTimestampNs = 0L;
        captureFpsLastTimestampNs = 0L;
        captureFrames = 0L;
        captureLastCallbackNs = 0L;
        captureSensorIntervalCount = 0L;
        captureSensorIntervalTotalNs = 0L;
        captureSensorIntervalSquaredTotalNs = 0d;
        captureSensorIntervalMinimumNs = 0L;
        captureSensorIntervalMaximumNs = 0L;
        captureSensorGapsOver50Ms = 0L;
        captureSensorGapsOver100Ms = 0L;
        captureCallbackIntervalCount = 0L;
        captureCallbackIntervalTotalNs = 0L;
        captureCallbackIntervalMaximumNs = 0L;
    }

    private static float averageMs(long totalNs, long count) {
        return count == 0L ? 0f : totalNs / (float) count / 1_000_000f;
    }

    private static float nanosToMs(long valueNs) {
        return valueNs / 1_000_000f;
    }

    private static float standardDeviationMs(long totalNs, double squaredTotalNs, long count) {
        if (count == 0L) return 0f;
        double averageNs = totalNs / (double) count;
        double varianceNs = Math.max(0d, squaredTotalNs / count - averageNs * averageNs);
        return (float) (Math.sqrt(varianceNs) / 1_000_000d);
    }

    private void updatePreviewTransform() {
        if (!previewTransformEnabled) return;
        Size size = recordingSize;
        if (size == null || previewView.getWidth() == 0 || previewView.getHeight() == 0) return;
        int cropSize = Math.min(sourceCropSize, Math.min(size.getWidth(), size.getHeight()));
        boolean swapped = isOutputRotationSwapped();
        float scaleX = (swapped ? size.getHeight() : size.getWidth()) / (float) cropSize;
        float scaleY = (swapped ? size.getWidth() : size.getHeight()) / (float) cropSize;
        float centerX = previewView.getWidth() * 0.5f;
        float centerY = previewView.getHeight() * 0.5f;
        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, centerX, centerY);
        previewView.setTransform(matrix);
        diagnostics.log("preview transform: view=" + previewView.getWidth() + "x"
                + previewView.getHeight()
                + ", source=" + size
                + ", crop=" + cropSize
                + ", axesSwapped=" + swapped
                + ", scale=" + scaleX + "x" + scaleY);
    }

    private boolean isOutputRotationSwapped() {
        if (characteristics == null || previewView.getDisplay() == null) return false;
        Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (sensorOrientation == null) return false;
        int displayRotation = previewView.getDisplay().getRotation() * 90;
        int relativeRotation = (sensorOrientation - displayRotation + 360) % 360;
        return relativeRotation == 90 || relativeRotation == 270;
    }

    private void closeCameraAndSurfaces() {
        cancelCommonStartWait();
        opening = false;
        switchingCamera = false;
        reopenAfterCameraClose = false;
        previewTransformEnabled = false;
        notifySwitchCompletion = false;
        awaitingSwitchPreviewFrame = false;
        repeatingBuilder = null;
        torchVerificationPending = false;
        closeCaptureSession();
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
        if (glProcessor != null) {
            glProcessor.stop();
            glProcessor = null;
        }
        recordingSurface = null;
    }

    private void closeCaptureSession() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        repeatingBuilder = null;
    }

    private void reportError(@NonNull Exception error) {
        diagnostics.error("camera error", error);
        callback.onError(error);
    }

    private void logCameraConfigurationOnce(
            @NonNull RoundVideoSession.CameraFacing facing,
            @NonNull String id,
            @NonNull CameraCharacteristics cameraCharacteristics,
            @NonNull Size[] sizes
    ) {
        if (facing == RoundVideoSession.CameraFacing.FRONT) {
            if (frontConfigurationLogged) return;
            frontConfigurationLogged = true;
        } else {
            if (backConfigurationLogged) return;
            backConfigurationLogged = true;
        }
        Integer hardwareLevel = cameraCharacteristics.get(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
        );
        Integer sensorOrientation = cameraCharacteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION
        );
        Range<Integer>[] fpsRanges = cameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        );
        int[] aeModes = cameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES
        );
        Boolean flashAvailable = cameraCharacteristics.get(
                CameraCharacteristics.FLASH_INFO_AVAILABLE
        );
        Rect activeArray = cameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
        );
        diagnostics.log("camera capabilities: id=" + id
                + ", facing=" + facing
                + ", hardwareLevel=" + hardwareLevelName(hardwareLevel)
                + ", sensorOrientation=" + sensorOrientation
                + ", activeArray=" + activeArray
                + ", flashAvailable=" + flashAvailable
                + ", aeModes=" + Arrays.toString(aeModes)
                + ", fpsRanges=" + Arrays.toString(fpsRanges)
                + ", outputSizes=" + Arrays.toString(sizes));
    }

    @NonNull
    private static String hardwareLevelName(@Nullable Integer level) {
        if (level == null) return "unknown";
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) return "LEGACY";
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED) return "LIMITED";
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL) return "FULL";
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3) return "LEVEL_3";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL) {
            return "EXTERNAL";
        }
        return String.valueOf(level);
    }

    @NonNull
    private static String cameraErrorName(int error) {
        switch (error) {
            case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                return "CAMERA_IN_USE";
            case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                return "MAX_CAMERAS_IN_USE";
            case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                return "CAMERA_DISABLED";
            case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                return "CAMERA_DEVICE";
            case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                return "CAMERA_SERVICE";
            default:
                return "UNKNOWN";
        }
    }

    private static long elapsedMs(long startedNs) {
        return startedNs == 0
                ? -1
                : (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L;
    }

    @NonNull
    private static OutputPair chooseOutputPair(
            @NonNull Size[] sizes,
            @NonNull RoundVideoSession.OutputResolution outputResolution,
            @NonNull RoundVideoSession.CameraResolution requestedResolution
    ) {
        int preferredCrop = getSourceCropSize(outputResolution, requestedResolution);
        OutputPair preferred = chooseTierPair(
                sizes,
                preferredCrop,
                requestedResolution
        );
        if (preferred != null) return preferred;

        if (outputResolution == RoundVideoSession.OutputResolution.P360
                && requestedResolution == RoundVideoSession.CameraResolution.MEDIUM) {
            OutputPair mediumFallback = chooseTierPair(
                    sizes,
                    480,
                    RoundVideoSession.CameraResolution.MEDIUM
            );
            if (mediumFallback != null) return mediumFallback;
        }

        int lowCrop = outputResolution.getSize();
        if (requestedResolution != RoundVideoSession.CameraResolution.LOW) {
            OutputPair low = chooseTierPair(
                    sizes,
                    lowCrop,
                    RoundVideoSession.CameraResolution.LOW
            );
            if (low != null) return low;
        }

        Size recording = chooseFallbackRecordingSize(sizes, outputResolution.getSize());
        int cropSize = Math.min(shortSide(recording), MAXIMUM_SOURCE_SHORT_SIDE);
        return new OutputPair(
                choosePreviewSize(sizes, recording),
                recording,
                RoundVideoSession.CameraResolution.LOW,
                cropSize
        );
    }

    @Nullable
    private static OutputPair chooseTierPair(
            @NonNull Size[] sizes,
            int cropSize,
            @NonNull RoundVideoSession.CameraResolution resolution
    ) {
        OutputPair best = null;
        for (Size recording : sizes) {
            if (!isValidTierSize(recording, cropSize)) continue;
            Size preview = choosePreviewSize(sizes, recording);
            OutputPair candidate = new OutputPair(preview, recording, resolution, cropSize);
            if (best == null || compareOutputPairs(candidate, best) < 0) best = candidate;
        }
        return best;
    }

    @NonNull
    private static Size chooseFallbackRecordingSize(@NonNull Size[] sizes, int outputSize) {
        Size best = null;
        for (Size size : sizes) {
            if (!isWithinAbsoluteLimit(size) || shortSide(size) < outputSize) continue;
            if (best == null || area(size) < area(best)) best = size;
        }
        if (best != null) return best;
        for (Size size : sizes) {
            if (!isWithinAbsoluteLimit(size)) continue;
            if (best == null
                    || shortSide(size) > shortSide(best)
                    || shortSide(size) == shortSide(best) && area(size) < area(best)) {
                best = size;
            }
        }
        if (best != null) return best;
        throw new IllegalStateException("Camera has no output at or below the bandwidth cap");
    }

    private static int getSourceCropSize(
            @NonNull RoundVideoSession.OutputResolution outputResolution,
            @NonNull RoundVideoSession.CameraResolution cameraResolution
    ) {
        if (cameraResolution == RoundVideoSession.CameraResolution.LOW) {
            return outputResolution.getSize();
        }
        if (outputResolution == RoundVideoSession.OutputResolution.P480) {
            return cameraResolution == RoundVideoSession.CameraResolution.HIGH ? 960 : 720;
        }
        return cameraResolution == RoundVideoSession.CameraResolution.HIGH ? 720 : 540;
    }

    @NonNull
    private static Size choosePreviewSize(@NonNull Size[] sizes, @NonNull Size recording) {
        Size best = recording;
        for (Size size : sizes) {
            if (hasSameAspectRatio(size, recording) && comparePreviewSizes(size, best) < 0) best = size;
        }
        return best;
    }

    private static int comparePreviewSizes(Size left, Size right) {
        int leftError = Math.abs(shortSide(left) - PREVIEW_SHORT_SIDE);
        int rightError = Math.abs(shortSide(right) - PREVIEW_SHORT_SIDE);
        return leftError != rightError
                ? Integer.compare(leftError, rightError)
                : Long.compare(area(left), area(right));
    }

    private static int compareOutputPairs(OutputPair left, OutputPair right) {
        int leftError = Math.abs(shortSide(left.preview) - PREVIEW_SHORT_SIDE);
        int rightError = Math.abs(shortSide(right.preview) - PREVIEW_SHORT_SIDE);
        if (leftError != rightError) return Integer.compare(leftError, rightError);
        long leftArea = area(left.preview) + area(left.recording);
        long rightArea = area(right.preview) + area(right.recording);
        return leftArea != rightArea
                ? Long.compare(leftArea, rightArea)
                : Long.compare(area(left.recording), area(right.recording));
    }

    private static boolean hasSameAspectRatio(Size left, Size right) {
        return (long) left.getWidth() * right.getHeight()
                == (long) left.getHeight() * right.getWidth();
    }

    private static boolean isValidTierSize(@NonNull Size size, int cropSize) {
        int maximumShortSide = cropSize
                + cropSize * MAXIMUM_CROP_OVERSIZE_PERCENT / 100;
        int maximumLongSide = Math.min(MAXIMUM_SOURCE_LONG_SIDE, cropSize * 2);
        int shortSide = shortSide(size);
        int longSide = Math.max(size.getWidth(), size.getHeight());
        return shortSide >= cropSize
                && shortSide <= maximumShortSide
                && longSide <= maximumLongSide;
    }

    private static boolean isWithinAbsoluteLimit(@NonNull Size size) {
        return shortSide(size) <= MAXIMUM_SOURCE_SHORT_SIDE
                && Math.max(size.getWidth(), size.getHeight()) <= MAXIMUM_SOURCE_LONG_SIDE;
    }

    private static int shortSide(Size size) {
        return Math.min(size.getWidth(), size.getHeight());
    }

    private static long area(Size size) {
        return (long) size.getWidth() * size.getHeight();
    }

    private static boolean contains(@Nullable int[] values, int target) {
        if (values == null) return false;
        for (int value : values) if (value == target) return true;
        return false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class FrameRatePlan {
        final RoundVideoSession.FrameRate frameRate;
        final Range<Integer> fpsRange;
        final OutputPair outputPair;

        FrameRatePlan(
                @NonNull RoundVideoSession.FrameRate frameRate,
                @Nullable Range<Integer> fpsRange,
                @NonNull OutputPair outputPair
        ) {
            this.frameRate = frameRate;
            this.fpsRange = fpsRange;
            this.outputPair = outputPair;
        }
    }

    private static final class CameraCandidate {
        final String id;
        final CameraCharacteristics characteristics;
        final Size[] sizes;
        final OutputPair regularPair;
        final FrameRatePlan frameRatePlan;

        CameraCandidate(
                @NonNull String id,
                @NonNull CameraCharacteristics characteristics,
                @NonNull Size[] sizes,
                @NonNull OutputPair regularPair,
                @NonNull FrameRatePlan frameRatePlan
        ) {
            this.id = id;
            this.characteristics = characteristics;
            this.sizes = sizes;
            this.regularPair = regularPair;
            this.frameRatePlan = frameRatePlan;
        }
    }

    private static final class OutputPair {
        final Size preview;
        final Size recording;
        final RoundVideoSession.CameraResolution cameraResolution;
        final int cropSize;

        OutputPair(
                Size preview,
                Size recording,
                RoundVideoSession.CameraResolution cameraResolution,
                int cropSize
        ) {
            this.preview = preview;
            this.recording = recording;
            this.cameraResolution = cameraResolution;
            this.cropSize = cropSize;
        }
    }
}
