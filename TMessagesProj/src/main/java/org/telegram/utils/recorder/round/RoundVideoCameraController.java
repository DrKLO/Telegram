package org.telegram.utils.recorder.round;

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
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

final class RoundVideoCameraController {

    interface Callback {
        void onConfigured(@NonNull CameraInfo info, boolean cameraSwitch);

        void onRecordingStarted();

        void onCameraSwitchStarted(@NonNull RoundVideoSession.CameraFacing facing);

        void onCameraSwitchFirstFrame();

        void onRecordingStopped();

        /** The active segment cannot continue, but the samples written so far stay valid. */
        void onSegmentInterrupted(@NonNull Exception error);

        void onError(@NonNull Exception error);
    }

    static final class CameraInfo {
        final RoundVideoSession.CameraFacing facing;
        final RoundVideoSession.CameraResolution cameraResolution;
        final RoundVideoSession.FrameRate frameRate;
        final Size recordingSize;
        final float maximumZoomRatio;
        final boolean torchAvailable;

        CameraInfo(
                RoundVideoSession.CameraFacing facing,
                RoundVideoSession.CameraResolution cameraResolution,
                RoundVideoSession.FrameRate frameRate,
                Size recordingSize,
                float maximumZoomRatio,
                boolean torchAvailable
        ) {
            this.facing = facing;
            this.cameraResolution = cameraResolution;
            this.frameRate = frameRate;
            this.recordingSize = recordingSize;
            this.maximumZoomRatio = maximumZoomRatio;
            this.torchAvailable = torchAvailable;
        }
    }

    /** What the controller does after the devices it is closing are gone. */
    private enum AfterClose {
        NONE,
        /** Open the camera for {@link #requestedFacing} again (sequential switch, recovery). */
        REOPEN,
        /** Create the single-camera session on the still open active device. */
        RETRY_SESSION,
        /** Continue a camera switch sequentially after a dual-active fallback. */
        CONTINUE_SWITCH
    }

    private static final class CameraSelection {
        final String id;
        final RoundVideoSession.CameraFacing facing;
        final CameraCharacteristics characteristics;
        final Size recordingSize;
        final int sourceCropSize;
        final RoundVideoSession.CameraResolution cameraResolution;
        final RoundVideoSession.FrameRate frameRate;
        final Range<Integer> fpsRange;
        final OutputPair regularFallbackPair;
        final float maximumZoomRatio;

        CameraSelection(
                String id,
                RoundVideoSession.CameraFacing facing,
                CameraCharacteristics characteristics,
                Size recordingSize,
                int sourceCropSize,
                RoundVideoSession.CameraResolution cameraResolution,
                RoundVideoSession.FrameRate frameRate,
                Range<Integer> fpsRange,
                OutputPair regularFallbackPair,
                float maximumZoomRatio
        ) {
            this.id = id;
            this.facing = facing;
            this.characteristics = characteristics;
            this.recordingSize = recordingSize;
            this.sourceCropSize = sourceCropSize;
            this.cameraResolution = cameraResolution;
            this.frameRate = frameRate;
            this.fpsRange = fpsRange;
            this.regularFallbackPair = regularFallbackPair;
            this.maximumZoomRatio = maximumZoomRatio;
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
    private volatile Size recordingSize;
    private volatile int sourceCropSize;
    private Surface previewSurface;
    private Surface recordingSurface;
    private Surface secondaryRecordingSurface;
    private RoundVideoGlProcessor glProcessor;
    private RoundVideoCodecRecorder recorder;
    private RoundVideoCodecRecorder commonStartRecorder;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CameraCaptureSession warmCaptureSession;
    private CaptureRequest.Builder repeatingBuilder;
    private CaptureRequest.Builder warmRepeatingBuilder;
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
    private boolean switchingCamera;
    private boolean notifySwitchCompletion;
    private volatile boolean awaitingSwitchPreviewFrame;
    private volatile boolean stopping;
    private volatile boolean previewTransformEnabled;
    private boolean frontConfigurationLogged;
    private boolean backConfigurationLogged;
    private boolean frameRateFallbackAttempted;
    private boolean frameRatePolicyResolved;
    private boolean force30Fps;
    private int encoderFrameRate;
    private CameraSelection frontSelection;
    private CameraSelection backSelection;
    /** Both cameras of the session form an advertised concurrent pair; kept across segments. */
    private boolean dualPairSupported;
    /** Dual-active was given up for this session after a failure; never retried. */
    private boolean dualDisabled;
    /** The current segment streams both cameras, so a switch only swaps roles. */
    private boolean dualActiveMode;
    private boolean activeDualSessionConfigured;
    private boolean warmDualSessionConfigured;
    private boolean activeInputSecondary;
    private CameraDevice warmCameraDevice;
    private CameraSelection warmCameraSelection;
    private RoundVideoSession.CameraFacing pendingWarmSwitchFacing;
    /**
     * Every device handed to the controller by a current open request, until its onClosed.
     * Closing goes through {@link #closeDevice}, so no role field can leak a device.
     */
    private final ArrayList<CameraDevice> ownedDevices = new ArrayList<>();
    /** Owned devices whose close() was issued; their callbacks are ignored until onClosed. */
    private final ArrayList<CameraDevice> closingDevices = new ArrayList<>();
    /** Continuation run once every closing device has reported onClosed. */
    private AfterClose afterClose = AfterClose.NONE;
    /** Set on the main thread by {@link #release()}; the controller is not reusable after it. */
    private volatile boolean released;
    /** The main and warm open requests whose result may still be adopted; camera thread only. */
    private OpenRequest mainOpenRequest;
    private OpenRequest warmOpenRequest;
    /** Open requests without a first result yet; camera thread only. */
    private int unsettledOpenRequests;
    /** Camera thread to quit once {@link #unsettledOpenRequests} drops to zero after release. */
    private HandlerThread quitWhenOpensSettle;
    private long warmCameraOpenRequestedNs;
    private long cameraCloseRequestedNs;
    private long switchSelectionStartedNs;
    /** Display rotation the GL processor currently compensates; camera thread only. */
    private int appliedDisplayRotation;
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
        if (released) {
            diagnostics.log("camera segment start ignored: controller released");
            return;
        }
        ensureThread();
        this.writer = writer;
        this.timelineOffsetUs = timelineOffsetUs;
        requestedFacing = facing;
        active = true;
        stopping = false;
        frameRateFallbackAttempted = false;
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
        released = true;
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
            if (unsettledOpenRequests > 0) {
                // A result posted to a quit looper is dropped and its device never closed, so
                // the thread lives until every open request has answered.
                diagnostics.log("release waits for camera open results: pending="
                        + unsettledOpenRequests);
                quitWhenOpensSettle = thread;
            } else {
                thread.quitSafely();
            }
        });
    }

    /**
     * Issues one camera open with its own callback. Only the request still current for its role
     * when the device opens may hand it to the controller; a released, stopped or superseded
     * request closes the device it receives.
     */
    @SuppressLint("MissingPermission")
    private void openCameraTracked(@NonNull String id, boolean warm) throws CameraAccessException {
        OpenRequest request = new OpenRequest(warm ? warmDeviceCallback : deviceCallback, warm);
        if (warm) {
            warmOpenRequest = request;
        } else {
            mainOpenRequest = request;
        }
        try {
            cameraManager.openCamera(id, request, cameraHandler);
        } catch (CameraAccessException | RuntimeException e) {
            request.settle();
            if (warm ? warmOpenRequest == request : mainOpenRequest == request) {
                if (warm) warmOpenRequest = null; else mainOpenRequest = null;
            }
            throw e;
        }
    }

    private final class OpenRequest extends CameraDevice.StateCallback {
        private final CameraDevice.StateCallback delegate;
        private final boolean warm;
        private boolean settled;
        /** The delegate received the opened device and owns it from then on. */
        private boolean adopted;
        /** Any callback reached the delegate, so it also expects the device's onClosed. */
        private boolean delivered;

        OpenRequest(@NonNull CameraDevice.StateCallback delegate, boolean warm) {
            this.delegate = delegate;
            this.warm = warm;
            unsettledOpenRequests++;
        }

        void settle() {
            if (settled) return;
            settled = true;
            unsettledOpenRequests--;
            HandlerThread thread = quitWhenOpensSettle;
            if (unsettledOpenRequests <= 0 && thread != null) {
                quitWhenOpensSettle = null;
                diagnostics.log("camera open results settled after release; quitting thread");
                thread.quitSafely();
            }
        }

        private boolean isCurrent() {
            return !released && (warm ? warmOpenRequest : mainOpenRequest) == this;
        }

        private void detach() {
            if (warm ? warmOpenRequest == this : mainOpenRequest == this) {
                if (warm) warmOpenRequest = null; else mainOpenRequest = null;
            }
        }

        @Override
        public void onOpened(@NonNull CameraDevice device) {
            boolean current = isCurrent();
            detach();
            settle();
            if (!current) {
                diagnostics.log("stale camera open closed: id=" + device.getId()
                        + ", warm=" + warm + ", released=" + released);
                device.close();
                return;
            }
            adopted = true;
            delivered = true;
            ownedDevices.add(device);
            delegate.onOpened(device);
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice device) {
            boolean current = isCurrent();
            settle();
            if (released || !adopted && !current) {
                device.close();
                return;
            }
            detach();
            delivered = true;
            delegate.onDisconnected(device);
        }

        @Override
        public void onError(@NonNull CameraDevice device, int error) {
            boolean current = isCurrent();
            settle();
            if (released || !adopted && !current) {
                diagnostics.log("stale camera error ignored: id=" + device.getId()
                        + ", error=" + cameraErrorName(error) + ", warm=" + warm);
                device.close();
                return;
            }
            detach();
            delivered = true;
            delegate.onError(device, error);
        }

        @Override
        public void onClosed(@NonNull CameraDevice device) {
            // A device that failed before opening is still closed by the delegate and tracked in
            // closingDevices until this callback, so it is forwarded after any delivery.
            if (delivered && !released) delegate.onClosed(device);
        }
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
                        diagnostics.log("camera switch first preview frame: path="
                                + (dualActiveMode ? "DUAL_ACTIVE" : "SEQUENTIAL")
                                + ", totalElapsedMs=" + elapsedMs(cameraSwitchStartedNs));
                        callback.onCameraSwitchFirstFrame();
                    }
                    long now = SystemClock.elapsedRealtimeNanos();
                    long timestampNs = surfaceTexture.getTimestamp();
                    if (previewFpsStartedNs == 0L) {
                        previewFpsStartedNs = now;
                        diagnostics.log("preview frame delivery started: thread="
                                + Thread.currentThread().getName()
                                + ", view=" + previewView.getWidth() + "x" + previewView.getHeight()
                                + ", buffer=" + glPreviewBufferSize()
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
        if (!active || isOpening() || cameraDevice != null) return;
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
            switchSelectionStartedNs = SystemClock.elapsedRealtimeNanos();
            selectCameraAndSizes(requestedFacing);
            long selectionElapsedMs = elapsedMs(switchSelectionStartedNs);
            if (cameraDevice == null && recorder == null) {
                prepareDualActiveSupport();
            }
            SurfaceTexture previewTexture = previewView.getSurfaceTexture();
            if (previewTexture == null) return;
            configurePreviewBuffer(previewTexture);
            if (previewSurface == null) previewSurface = new Surface(previewTexture);
            if (recorder == null) {
                createRecordingPipelineWithFallback();
            } else if (glProcessor != null) {
                glProcessor.updateInputConfiguration(
                        recordingSize,
                        sourceCropSize,
                        isCameraTimestampRealtime()
                );
                if (!dualActiveMode && activeInputSecondary) {
                    activeInputSecondary = false;
                    glProcessor.setSecondaryInputActive(false);
                }
                recordingSurface = glProcessor.getCameraSurface();
                secondaryRecordingSurface = dualActiveMode
                        ? glProcessor.getSecondaryCameraSurface()
                        : null;
            }
            openStandbyCameraIfNeeded();
            cameraOpenRequestedNs = SystemClock.elapsedRealtimeNanos();
            diagnostics.log("camera open requested: id=" + cameraId
                    + ", recording=" + recordingSize
                    + ", crop=" + sourceCropSize
                    + ", selectionElapsedMs=" + selectionElapsedMs
                    + ", dualActive=" + dualActiveMode);
            openCameraTracked(cameraId, false);
        } catch (Exception e) {
            reportError(e);
        }
    }

    private boolean isOpening() {
        return mainOpenRequest != null;
    }

    private boolean isStandbyOpening() {
        return warmOpenRequest != null;
    }

    /**
     * The preview is always rendered by the GL processor from the single camera stream, so the
     * camera has one output in every mode and the preview shows exactly the recorded crop.
     */
    private void configurePreviewBuffer(@NonNull SurfaceTexture previewTexture) {
        int glPreviewSize = glPreviewBufferSize();
        previewTexture.setDefaultBufferSize(glPreviewSize, glPreviewSize);
    }

    /** Side of the square buffer the GL processor renders the preview into. */
    private int glPreviewBufferSize() {
        return Math.max(
                1,
                Math.min(
                        PREVIEW_SHORT_SIDE,
                        Math.min(previewView.getWidth(), previewView.getHeight())
                )
        );
    }

    private void createRecordingPipeline() throws IOException {
        // Later segments keep the first segment's encoder configuration: a different frame rate
        // can change the SPS, which cannot be appended to the same MP4 track.
        recorder = new RoundVideoCodecRecorder(
                writer,
                timelineOffsetUs,
                outputSize,
                videoBitrate,
                encoderFrameRate > 0 ? encoderFrameRate : activeFrameRate.getValue(),
                diagnostics,
                callback::onError,
                callback::onSegmentInterrupted
        );
        Surface encoderSurface = recorder.prepare();
        glProcessor = new RoundVideoGlProcessor(
                recordingSize,
                encoderSurface,
                previewSurface,
                dualActiveMode,
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
        appliedDisplayRotation = displayRotationDegrees();
        glProcessor.setDisplayRotation(appliedDisplayRotation);
        recordingSurface = glProcessor.start();
        if (dualActiveMode) {
            CameraSelection secondarySelection = selectionForFacing(
                    selectedFacing == RoundVideoSession.CameraFacing.FRONT
                            ? RoundVideoSession.CameraFacing.BACK
                            : RoundVideoSession.CameraFacing.FRONT
            );
            if (secondarySelection == null) {
                throw new IllegalStateException("Secondary camera selection is unavailable");
            }
            glProcessor.configureSecondaryInput(
                    secondarySelection.recordingSize,
                    secondarySelection.sourceCropSize,
                    isCameraTimestampRealtime(secondarySelection.characteristics)
            );
            secondaryRecordingSurface = glProcessor.getSecondaryCameraSurface();
        } else {
            secondaryRecordingSurface = null;
        }
    }

    private void createRecordingPipelineWithFallback() throws IOException {
        try {
            createRecordingPipeline();
        } catch (IOException | RuntimeException e) {
            if (!dualActiveMode) throw e;
            diagnostics.log("camera dual-active fallback: reason=GL pipeline initialization failed"
                    + ", error=" + e);
            if (glProcessor != null) {
                glProcessor.stop();
                glProcessor = null;
            }
            if (recorder != null) {
                recorder.stop();
                recorder = null;
            }
            recordingSurface = null;
            secondaryRecordingSurface = null;
            dualActiveMode = false;
            dualPairSupported = false;
            dualDisabled = true;
            createRecordingPipeline();
        }
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
        if (switchDualActiveCamera(facing)) {
            return;
        }
        if (dualActiveMode) {
            pendingWarmSwitchFacing = facing;
            diagnostics.log("camera switch path: waiting for DUAL_ACTIVE standby session");
            return;
        }
        if (glProcessor != null) glProcessor.beginCameraSwitch(activeFacing, facing);
        continueSequentialSwitch();
    }

    private boolean switchDualActiveCamera(
            @NonNull RoundVideoSession.CameraFacing facing
    ) {
        if (!dualActiveMode
                || !activeDualSessionConfigured
                || !warmDualSessionConfigured
                || cameraDevice == null
                || warmCameraDevice == null
                || captureSession == null
                || warmCaptureSession == null
                || warmCameraSelection == null
                || warmCameraSelection.facing != facing
                || glProcessor == null) {
            return false;
        }

        CameraDevice previousDevice = cameraDevice;
        cameraDevice = warmCameraDevice;
        warmCameraDevice = previousDevice;

        CameraCaptureSession previousSession = captureSession;
        captureSession = warmCaptureSession;
        warmCaptureSession = previousSession;

        CaptureRequest.Builder previousBuilder = repeatingBuilder;
        repeatingBuilder = warmRepeatingBuilder;
        warmRepeatingBuilder = previousBuilder;

        Surface previousSurface = recordingSurface;
        recordingSurface = secondaryRecordingSurface;
        secondaryRecordingSurface = previousSurface;

        CameraSelection previousSelection = selectionForFacing(activeFacing);
        CameraSelection targetSelection = warmCameraSelection;
        warmCameraSelection = previousSelection;
        applySelection(targetSelection);
        activeFacing = facing;
        try {
            repeatingBuilder = createRepeatingRequestBuilder(
                    cameraDevice,
                    recordingSurface,
                    targetSelection,
                    true,
                    false
            );
            captureSession.setRepeatingRequest(
                    repeatingBuilder.build(),
                    captureCallback,
                    cameraHandler
            );
            if (warmCameraSelection != null) {
                warmRepeatingBuilder = createRepeatingRequestBuilder(
                        warmCameraDevice,
                        secondaryRecordingSurface,
                        warmCameraSelection,
                        false,
                        false
                );
                warmCaptureSession.setRepeatingRequest(
                        warmRepeatingBuilder.build(),
                        captureCallback,
                        cameraHandler
                );
            }
        } catch (CameraAccessException | IllegalArgumentException | IllegalStateException e) {
            fallbackFromDualMode("role switch request failed", e, false);
            return true;
        }
        activeInputSecondary = !activeInputSecondary;
        long switchStartedNs = cameraSwitchStartedNs;
        RoundVideoSession.CameraFacing switchedFacing = facing;
        glProcessor.setSecondaryInputActive(activeInputSecondary, () -> previewView.post(() -> {
            if (!active || activeFacing != switchedFacing) return;
            diagnostics.log("camera switch first preview frame: path=DUAL_ACTIVE"
                    + ", totalElapsedMs=" + elapsedMs(switchStartedNs));
            callback.onCameraSwitchFirstFrame();
        }));

        activeDualSessionConfigured = true;
        warmDualSessionConfigured = true;
        notifySwitchCompletion = false;
        switchingCamera = false;
        awaitingSwitchPreviewFrame = false;
        resetCaptureFpsMeasurement();

        diagnostics.log("camera switch path: DUAL_ACTIVE, activeId=" + cameraDevice.getId()
                + ", standbyId=" + warmCameraDevice.getId()
                + ", input=" + (activeInputSecondary ? 1 : 0)
                + ", elapsedMs=" + elapsedMs(cameraSwitchStartedNs));
        previewView.post(RoundVideoCameraController.this::updatePreviewTransform);
        callback.onConfigured(
                new CameraInfo(
                        activeFacing,
                        activeCameraResolution,
                        activeFrameRate,
                        recordingSize,
                        maximumZoomRatio,
                        isTorchAvailable()
                ),
                true
        );
        return true;
    }

    private void completePendingDualSwitchIfReady() {
        RoundVideoSession.CameraFacing pending = pendingWarmSwitchFacing;
        if (pending == null
                || !dualActiveMode
                || !activeDualSessionConfigured
                || !warmDualSessionConfigured) {
            return;
        }
        pendingWarmSwitchFacing = null;
        if (!switchDualActiveCamera(pending)) {
            switchingCamera = false;
            switchCameraIfNeeded(pending);
        }
    }

    private void continueSequentialSwitch() {
        pendingWarmSwitchFacing = null;
        diagnostics.log("camera switch path: sequential"
                + (dualPairSupported
                ? ", reason=standby session not ready"
                : ", reason=concurrent pair unavailable"));
        closeCaptureSession();
        if (cameraDevice != null) {
            CameraDevice device = cameraDevice;
            cameraDevice = null;
            cameraCloseRequestedNs = SystemClock.elapsedRealtimeNanos();
            closeDevice(device, AfterClose.REOPEN);
        } else if (!isOpening()) {
            switchingCamera = false;
            openCameraIfReady();
        }
        // Otherwise an open for the previous facing is in flight: its result arrives while
        // switchingCamera is set, is closed, and the target is opened after that.
    }

    /**
     * Issues the only close() the controller ever calls on a device it owns. The device stays
     * in {@link #closingDevices} until onClosed, so its late callbacks are ignored, and the
     * continuation runs once nothing is closing any more.
     */
    private void closeDevice(@NonNull CameraDevice device, @NonNull AfterClose action) {
        if (!closingDevices.contains(device)) closingDevices.add(device);
        if (action != AfterClose.NONE) afterClose = action;
        device.close();
    }

    private boolean isClosing(@NonNull CameraDevice device) {
        return closingDevices.contains(device);
    }

    private void onOwnedDeviceClosed(@NonNull CameraDevice device) {
        ownedDevices.remove(device);
        closingDevices.remove(device);
        if (device == cameraDevice) cameraDevice = null;
        if (device == warmCameraDevice) warmCameraDevice = null;
        diagnostics.log("camera device closed: id=" + device.getId()
                + ", owned=" + ownedDevices.size()
                + ", closing=" + closingDevices.size()
                + ", afterClose=" + afterClose
                + ", closeElapsedMs=" + elapsedMs(cameraCloseRequestedNs));
        if (!closingDevices.isEmpty()) return;
        AfterClose action = afterClose;
        afterClose = AfterClose.NONE;
        if (!active || stopping || action == AfterClose.NONE) return;
        switch (action) {
            case REOPEN:
                switchingCamera = false;
                openCameraIfReady();
                break;
            case RETRY_SESSION:
                if (cameraDevice != null && captureSession == null) {
                    diagnostics.log("retrying capture session after standby camera closed");
                    createCaptureSession();
                }
                break;
            case CONTINUE_SWITCH:
                diagnostics.log("continuing pending switch after DUAL_ACTIVE fallback");
                continueSequentialSwitch();
                break;
            default:
                break;
        }
    }

    /** Closes every owned device and opens the requested camera again once all are gone. */
    private void closeAllAndReopen(@NonNull String reason, @Nullable Exception error) {
        dualDisabled = true;
        dualPairSupported = false;
        dualActiveMode = false;
        pendingWarmSwitchFacing = null;
        closeCaptureSession();
        closeWarmCaptureSession();
        cameraDevice = null;
        warmCameraDevice = null;
        warmCameraSelection = null;
        // Opens still in flight are superseded: their devices are closed when they arrive.
        mainOpenRequest = null;
        warmOpenRequest = null;
        diagnostics.log("camera recovery: reason=" + reason
                + ", target=" + requestedFacing
                + ", devices=" + ownedDevices.size()
                + ", switchElapsedMs=" + elapsedMs(cameraSwitchStartedNs)
                + (error == null ? "" : ", error=" + error));
        if (ownedDevices.isEmpty()) {
            switchingCamera = false;
            if (active && !stopping) openCameraIfReady();
            return;
        }
        cameraCloseRequestedNs = SystemClock.elapsedRealtimeNanos();
        for (CameraDevice device : new ArrayList<>(ownedDevices)) {
            closeDevice(device, AfterClose.REOPEN);
        }
    }

    private final CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice device) {
            diagnostics.log("camera opened: id=" + device.getId()
                    + ", elapsedMs=" + elapsedMs(cameraOpenRequestedNs)
                    + (notifySwitchCompletion ? ", switchElapsedMs="
                    + elapsedMs(cameraSwitchStartedNs) : ""));
            if (!active || switchingCamera) {
                closeDevice(device, switchingCamera ? AfterClose.REOPEN : AfterClose.NONE);
                return;
            }
            cameraDevice = device;
            if (dualActiveMode) {
                if (isStandbyOpening() || warmCameraDevice == null) {
                    diagnostics.log("active camera opened; waiting for dual peer before sessions");
                } else {
                    createDualCaptureSessions();
                }
            } else {
                createCaptureSession();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice device) {
            diagnostics.log("camera disconnected: id=" + device.getId()
                    + ", closing=" + isClosing(device));
            if (isClosing(device)) {
                device.close();
                return;
            }
            if (device == warmCameraDevice) {
                handleStandbyDeviceFailure(device, "disconnected", null);
                return;
            }
            boolean unexpected = active && !stopping && !switchingCamera;
            if (cameraDevice == device) {
                closeCaptureSession();
                cameraDevice = null;
            }
            closeDevice(device, AfterClose.NONE);
            if (unexpected) {
                reportCameraLoss(new IllegalStateException("Camera device disconnected"));
            }
        }

        @Override
        public void onError(@NonNull CameraDevice device, int error) {
            diagnostics.log("camera error: id=" + device.getId()
                    + ", error=" + cameraErrorName(error) + " (" + error + ")"
                    + ", closing=" + isClosing(device));
            if (isClosing(device)) {
                device.close();
                return;
            }
            if (device == warmCameraDevice) {
                handleStandbyDeviceFailure(
                        device,
                        "error=" + cameraErrorName(error) + " (" + error + ")",
                        null
                );
                return;
            }
            if (cameraDevice == device) {
                closeCaptureSession();
                cameraDevice = null;
            }
            closeDevice(device, AfterClose.NONE);
            reportCameraLoss(new IllegalStateException("Camera device error: "
                    + cameraErrorName(error) + " (" + error + ")"));
        }

        @Override
        public void onClosed(@NonNull CameraDevice device) {
            onOwnedDeviceClosed(device);
        }
    };

    /** Callback of the standby camera opened for dual-active; roles may swap after a switch. */
    private final CameraDevice.StateCallback warmDeviceCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice device) {
                    if (!active || stopping || !dualActiveMode
                            || warmCameraSelection == null
                            || !device.getId().equals(warmCameraSelection.id)) {
                        diagnostics.log("stale warm camera open ignored: id=" + device.getId());
                        closeDevice(device, AfterClose.NONE);
                        return;
                    }
                    warmCameraDevice = device;
                    diagnostics.log("warm camera opened: id=" + device.getId()
                            + ", facing=" + warmCameraSelection.facing
                            + ", elapsedMs=" + elapsedMs(warmCameraOpenRequestedNs));
                    if (cameraDevice != null && captureSession == null && !switchingCamera) {
                        diagnostics.log("both camera devices opened; configuring dual sessions");
                        createDualCaptureSessions();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice device) {
                    diagnostics.log("warm camera disconnected: id=" + device.getId()
                            + ", closing=" + isClosing(device));
                    if (isClosing(device)) {
                        device.close();
                        return;
                    }
                    handleStandbyDeviceFailure(device, "disconnected", null);
                }

                @Override
                public void onError(@NonNull CameraDevice device, int error) {
                    diagnostics.log("warm camera error: id=" + device.getId()
                            + ", error=" + cameraErrorName(error) + " (" + error + ")"
                            + ", closing=" + isClosing(device));
                    if (isClosing(device)) {
                        device.close();
                        return;
                    }
                    handleStandbyDeviceFailure(
                            device,
                            "error=" + cameraErrorName(error) + " (" + error + ")",
                            new IllegalStateException("Warm camera device error: "
                                    + cameraErrorName(error) + " (" + error + ")")
                    );
                }

                @Override
                public void onClosed(@NonNull CameraDevice device) {
                    onOwnedDeviceClosed(device);
                }
            };

    private void openStandbyCameraIfNeeded() {
        if (!dualActiveMode || dualDisabled
                || isStandbyOpening() || warmCameraDevice != null) {
            return;
        }
        CameraSelection selection = selectionForFacing(
                selectedFacing == RoundVideoSession.CameraFacing.FRONT
                        ? RoundVideoSession.CameraFacing.BACK
                        : RoundVideoSession.CameraFacing.FRONT
        );
        if (selection == null) return;
        try {
            warmCameraSelection = selection;
            warmCameraOpenRequestedNs = SystemClock.elapsedRealtimeNanos();
            diagnostics.log("warm camera open requested: id=" + selection.id
                    + ", facing=" + selection.facing);
            openCameraTracked(selection.id, true);
        } catch (Exception e) {
            disableDualActive("standby open request failed", e);
        }
    }

    /**
     * A device opened as the standby failed. If it has become the active device through a
     * dual role switch the active segment is lost; otherwise dual-active ends and the session
     * continues on the active device alone.
     */
    private void handleStandbyDeviceFailure(
            @NonNull CameraDevice device,
            @NonNull String reason,
            @Nullable Exception error
    ) {
        if (device == cameraDevice) {
            closeCaptureSession();
            cameraDevice = null;
            closeDevice(device, AfterClose.NONE);
            reportCameraLoss(error != null
                    ? error
                    : new IllegalStateException("Active camera " + reason));
            return;
        }
        if (device == warmCameraDevice) warmCameraDevice = null;
        if (!dualActiveMode) {
            closeDevice(device, AfterClose.NONE);
            return;
        }
        diagnostics.log("camera dual-active fallback: reason=standby device " + reason
                + (error == null ? "" : ", error=" + error));
        dualActiveMode = false;
        dualPairSupported = false;
        dualDisabled = true;
        warmCameraSelection = null;
        closeWarmCaptureSession();
        boolean retrySession = active
                && cameraDevice != null
                && captureSession == null
                && !switchingCamera;
        boolean continueSwitch = switchingCamera && pendingWarmSwitchFacing != null;
        closeDevice(
                device,
                retrySession
                        ? AfterClose.RETRY_SESSION
                        : continueSwitch ? AfterClose.CONTINUE_SWITCH : AfterClose.NONE
        );
    }

    private void disableDualActive(@NonNull String reason, @Nullable Exception error) {
        dualDisabled = true;
        dualPairSupported = false;
        dualActiveMode = false;
        diagnostics.log("camera dual-active disabled: reason=" + reason
                + (error == null ? "" : ", error=" + error));
        closeWarmCaptureSession();
        CameraDevice warm = warmCameraDevice;
        warmCameraDevice = null;
        warmCameraSelection = null;
        // A standby open still in flight is superseded and closed on arrival.
        warmOpenRequest = null;
        if (warm != null && warm != cameraDevice) closeDevice(warm, AfterClose.NONE);
    }

    private void createDualCaptureSessions() {
        CameraDevice activeDevice = cameraDevice;
        CameraDevice standbyDevice = warmCameraDevice;
        if (!dualActiveMode
                || activeDevice == null
                || standbyDevice == null
                || recordingSurface == null
                || secondaryRecordingSurface == null
                || captureSession != null
                || warmCaptureSession != null) {
            return;
        }
        activeDualSessionConfigured = false;
        warmDualSessionConfigured = false;
        try {
            captureSessionRequestedNs = SystemClock.elapsedRealtimeNanos();
            diagnostics.log("dual capture sessions requested: activeId="
                    + activeDevice.getId() + ", standbyId=" + standbyDevice.getId()
                    + ", input=" + recordingSize + ", fpsRange=" + activeFpsRange);
            activeDevice.createCaptureSession(
                    Arrays.asList(recordingSurface),
                    sessionCallback,
                    cameraHandler
            );
            standbyDevice.createCaptureSession(
                    Arrays.asList(secondaryRecordingSurface),
                    warmSessionCallback,
                    cameraHandler
            );
        } catch (CameraAccessException | IllegalArgumentException e) {
            fallbackFromDualMode("session request failed", e, false);
        }
    }

    private final CameraCaptureSession.StateCallback warmSessionCallback =
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    if (!active || !dualActiveMode || warmCameraDevice == null
                            || session.getDevice() != warmCameraDevice
                            || secondaryRecordingSurface == null
                            || warmCameraSelection == null) {
                        diagnostics.log("stale standby capture session ignored: id="
                                + session.getDevice().getId());
                        session.close();
                        return;
                    }
                    try {
                        warmCaptureSession = session;
                        warmRepeatingBuilder = createRepeatingRequestBuilder(
                                warmCameraDevice,
                                secondaryRecordingSurface,
                                warmCameraSelection,
                                false,
                                true
                        );
                        session.setRepeatingRequest(
                                warmRepeatingBuilder.build(),
                                captureCallback,
                                cameraHandler
                        );
                        warmDualSessionConfigured = true;
                        diagnostics.log("standby capture session configured: id="
                                + session.getDevice().getId()
                                + ", elapsedMs=" + elapsedMs(captureSessionRequestedNs));
                        completePendingDualSwitchIfReady();
                    } catch (Exception e) {
                        fallbackFromDualMode("standby request submission failed", e, true);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    session.close();
                    if (!dualActiveMode || warmCameraDevice == null
                            || session.getDevice() != warmCameraDevice) {
                        return;
                    }
                    fallbackFromDualMode("standby session configuration failed", null, true);
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    if (warmCaptureSession == session) {
                        warmCaptureSession = null;
                        warmRepeatingBuilder = null;
                        warmDualSessionConfigured = false;
                    }
                }
            };

    private void fallbackFromDualMode(
            @NonNull String reason,
            @Nullable Exception error,
            boolean keepActiveSession
    ) {
        if (!dualActiveMode) return;
        diagnostics.log("camera dual-active fallback: reason=" + reason
                + ", keepActive=" + keepActiveSession
                + (error == null ? "" : ", error=" + error));
        dualActiveMode = false;
        dualPairSupported = false;
        dualDisabled = true;
        activeDualSessionConfigured = false;
        warmDualSessionConfigured = false;
        if (!keepActiveSession || captureSession == null) {
            closeAllAndReopen("DUAL_ACTIVE " + reason, error);
            return;
        }
        closeWarmCaptureSession();
        CameraDevice standby = warmCameraDevice;
        warmCameraDevice = null;
        warmCameraSelection = null;
        boolean continueSwitch = switchingCamera && pendingWarmSwitchFacing != null;
        if (standby != null) {
            closeDevice(
                    standby,
                    continueSwitch ? AfterClose.CONTINUE_SWITCH : AfterClose.NONE
            );
        } else if (continueSwitch) {
            continueSequentialSwitch();
        }
    }

    private void createCaptureSession() {
        CameraDevice device = cameraDevice;
        if (device == null || previewSurface == null || recordingSurface == null) return;
        try {
            captureSessionRequestedNs = SystemClock.elapsedRealtimeNanos();
            diagnostics.log("capture session requested: recording=" + recordingSize
                    + ", fpsRange=" + activeFpsRange);
            device.createCaptureSession(
                    Arrays.asList(recordingSurface),
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
                    if (!active || cameraDevice == null
                            || session.getDevice() != cameraDevice) {
                        diagnostics.log("stale capture session ignored: deviceId="
                                + session.getDevice().getId());
                        session.close();
                        return;
                    }
                    captureSession = session;
                    try {
                        activeFacing = selectedFacing;
                        repeatingBuilder = createRepeatingRequestBuilder(true);
                        if (glProcessor != null) glProcessor.onCameraSessionConfigured();
                        boolean pendingDualSwitch = dualActiveMode
                                && pendingWarmSwitchFacing != null;
                        boolean wasSwitch = notifySwitchCompletion && !pendingDualSwitch;
                        submitRepeatingRequest();
                        if (dualActiveMode) activeDualSessionConfigured = true;
                        if (!pendingDualSwitch) notifySwitchCompletion = false;
                        if (wasSwitch) switchingCamera = false;
                        awaitingSwitchPreviewFrame = wasSwitch;
                        diagnostics.log("capture session configured: facing=" + activeFacing
                                + ", fpsRange=" + activeFpsRange
                                + ", elapsedMs=" + elapsedMs(captureSessionRequestedNs)
                                + ", segmentElapsedMs=" + elapsedMs(segmentStartedNs)
                                + (wasSwitch ? ", switchPath=SEQUENTIAL"
                                + ", switchElapsedMs=" + elapsedMs(cameraSwitchStartedNs) : ""));
                        previewView.post(RoundVideoCameraController.this::updatePreviewTransform);
                        callback.onConfigured(
                                new CameraInfo(
                                        activeFacing,
                                        activeCameraResolution,
                                        activeFrameRate,
                                        recordingSize,
                                        maximumZoomRatio,
                                        isTorchAvailable()
                                ),
                                wasSwitch
                        );
                        if (pendingDualSwitch) {
                            completePendingDualSwitchIfReady();
                        } else if (requestedFacing != activeFacing) {
                            switchCameraIfNeeded(requestedFacing);
                        }
                    } catch (Exception e) {
                        if (dualActiveMode) {
                            fallbackFromDualMode("active request submission failed", e, false);
                        } else if (activeFrameRate == RoundVideoSession.FrameRate.FPS_60) {
                            fallbackTo30Fps("60 fps request submission rejected", e);
                        } else {
                            reportError(e);
                        }
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    session.close();
                    if (cameraDevice == null || session.getDevice() != cameraDevice) {
                        diagnostics.log("stale capture session configuration failure ignored: id="
                                + session.getDevice().getId());
                        return;
                    }
                    if (captureSession == session) {
                        captureSession = null;
                        repeatingBuilder = null;
                    }
                    if (!active || cameraDevice == null || stopping) {
                        diagnostics.log("stale capture session configuration failure ignored");
                        return;
                    }
                    if (dualActiveMode) {
                        fallbackFromDualMode(
                                "active session configuration failed",
                                null,
                                false
                        );
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

    private void startVideoAfterAudioReady(
            @NonNull RoundVideoCodecRecorder expectedRecorder
    ) {
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

    private void announceRecordingStarted(
            @NonNull RoundVideoCodecRecorder expectedRecorder
    ) {
        if (!active || stopping || recorder != expectedRecorder) return;
        cancelCommonStartWait();
        if (encoderFrameRate == 0) encoderFrameRate = expectedRecorder.getVideoFrameRate();
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
            if (dualActiveMode) {
                fallbackFromDualMode("active repeating request rejected", e, false);
            } else {
                recoverFromClosedCamera(session, e);
            }
        } catch (CameraAccessException | IllegalArgumentException e) {
            if (dualActiveMode) {
                fallbackFromDualMode("active repeating request failed", e, false);
            } else if (activeFrameRate == RoundVideoSession.FrameRate.FPS_60) {
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
        if (device != null) {
            cameraCloseRequestedNs = SystemClock.elapsedRealtimeNanos();
            closeDevice(device, AfterClose.REOPEN);
        } else {
            openCameraIfReady();
        }
    }

    @NonNull
    private CaptureRequest.Builder createRepeatingRequestBuilder(boolean logConfiguration)
            throws CameraAccessException {
        CameraDevice device = cameraDevice;
        if (device == null || recordingSurface == null) {
            throw new IllegalStateException("Camera request surfaces are unavailable");
        }
        CameraSelection selection = captureSelection();
        return createRepeatingRequestBuilder(
                device,
                recordingSurface,
                selection,
                true,
                logConfiguration
        );
    }

    @NonNull
    private CaptureRequest.Builder createRepeatingRequestBuilder(
            @NonNull CameraDevice device,
            @NonNull Surface cameraInputSurface,
            @NonNull CameraSelection selection,
            boolean activeControls,
            boolean logConfiguration
    ) throws CameraAccessException {
        CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        builder.addTarget(cameraInputSurface);
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(
                CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD
        );
        setContinuousVideoAf(builder, selection.characteristics);
        if (selection.fpsRange != null) {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selection.fpsRange);
            if (logConfiguration) {
                diagnostics.log("capture FPS range selected: " + selection.fpsRange
                        + ", cameraId=" + selection.id
                        + ", active=" + activeControls);
            }
        }
        if (activeControls) {
            applyZoom(builder);
            applyTorch(builder);
        } else {
            int[] aeModes = selection.characteristics.get(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES
            );
            if (contains(aeModes, CaptureRequest.CONTROL_AE_MODE_ON)) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            }
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
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
                    if (session != captureSession) return;
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
        CameraSelection cached = selectionForFacing(facing);
        if (cached != null) {
            applySelection(cached);
            diagnostics.log("camera outputs restored from cache: id=" + cached.id
                    + ", facing=" + facing
                    + ", recording=" + cached.recordingSize
                    + ", fps=" + cached.frameRate.getValue());
            return;
        }
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
                        sensorAspectRatio(candidate),
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
                        + ", recording=" + frameRatePlan.outputPair.recording
                        + ", crop=" + frameRatePlan.outputPair.cropSize);
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
        recordingSize = pair.recording;
        sourceCropSize = pair.cropSize;
        activeCameraResolution = pair.cameraResolution;
        maximumZoomRatio = readMaximumZoomRatio(selectedCharacteristics);
        long recordingMinFrameDurationNs = getMinimumFrameDurationNs(
                selectedCharacteristics,
                recordingSize
        );
        diagnostics.log("camera outputs selected: output=" + outputSize + "x" + outputSize
                + ", displayRotation=" + displayRotationDegrees()
                + ", cameraMode=" + activeCameraResolution
                + (activeCameraResolution != requestedCameraResolution
                ? " (fallback from " + requestedCameraResolution + ")" : "")
                + ", crop=" + sourceCropSize
                + ", recording=" + recordingSize
                + ", sensorAspect=" + sensorAspectRatio(selectedCharacteristics)
                + ", recordingMinFrameDurationNs=" + recordingMinFrameDurationNs
                + ", fps=" + activeFrameRate.getValue()
                + ", fpsRange=" + activeFpsRange
                + ", timestampSource="
                + (isCameraTimestampRealtime() ? "REALTIME" : "UNKNOWN")
                + (activeFrameRate != requestedFrameRate
                ? " (fallback from " + requestedFrameRate.getValue() + ")" : ""));
        logCameraConfigurationOnce(facing, selectedId, selectedCharacteristics, sizes);
        cacheSelection(captureSelection());
    }

    @Nullable
    private CameraSelection selectionForFacing(@NonNull RoundVideoSession.CameraFacing facing) {
        return facing == RoundVideoSession.CameraFacing.FRONT
                ? frontSelection
                : backSelection;
    }

    private void cacheSelection(@NonNull CameraSelection selection) {
        if (selection.facing == RoundVideoSession.CameraFacing.FRONT) {
            frontSelection = selection;
        } else {
            backSelection = selection;
        }
    }

    @NonNull
    private CameraSelection captureSelection() {
        return new CameraSelection(
                cameraId,
                selectedFacing,
                characteristics,
                recordingSize,
                sourceCropSize,
                activeCameraResolution,
                activeFrameRate,
                activeFpsRange,
                regularFallbackPair,
                maximumZoomRatio
        );
    }

    private void applySelection(@NonNull CameraSelection selection) {
        cameraId = selection.id;
        selectedFacing = selection.facing;
        characteristics = selection.characteristics;
        recordingSize = selection.recordingSize;
        sourceCropSize = selection.sourceCropSize;
        activeCameraResolution = selection.cameraResolution;
        activeFrameRate = selection.frameRate;
        activeFpsRange = selection.fpsRange;
        regularFallbackPair = selection.regularFallbackPair;
        maximumZoomRatio = selection.maximumZoomRatio;
        frameRateFallbackAttempted = false;
        resetCaptureFpsMeasurement();
    }

    private void prepareDualActiveSupport() {
        if (dualDisabled) {
            return;
        }
        if (dualPairSupported) {
            dualActiveMode = true;
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            dualDisabled = true;
            diagnostics.log("camera dual-active capability: supported=false, reason=API<30");
            return;
        }
        CameraSelection initial = captureSelection();
        RoundVideoSession.CameraFacing otherFacing = initial.facing
                == RoundVideoSession.CameraFacing.FRONT
                ? RoundVideoSession.CameraFacing.BACK
                : RoundVideoSession.CameraFacing.FRONT;
        try {
            selectCameraAndSizes(otherFacing);
            CameraSelection other = captureSelection();
            dualPairSupported = isConcurrentPair(initial.id, other.id);
            if (dualPairSupported && !isDualConfigurationCompatible(initial, other)) {
                dualPairSupported = false;
                diagnostics.log("camera dual-active capability rejected: incompatible frame rate, "
                        + "first=" + initial.recordingSize + "/" + initial.sourceCropSize
                        + ", second=" + other.recordingSize + "/" + other.sourceCropSize
                        + ", firstFps=" + initial.frameRate.getValue()
                        + ", secondFps=" + other.frameRate.getValue());
            }
            if (!dualPairSupported) dualDisabled = true;
            dualActiveMode = dualPairSupported;
            diagnostics.log("camera dual-active capability: supported="
                    + dualPairSupported
                    + ", pair=" + initial.id + "+" + other.id
                    + (dualPairSupported ? "" : ", reason=pair not advertised"));
        } catch (Exception e) {
            dualDisabled = true;
            diagnostics.log("camera dual-active capability unavailable: " + e);
        } finally {
            applySelection(initial);
        }
    }

    private static boolean isDualConfigurationCompatible(
            @NonNull CameraSelection first,
            @NonNull CameraSelection second
    ) {
        return first.frameRate == second.frameRate;
    }

    private boolean isConcurrentPair(@NonNull String first, @NonNull String second)
            throws CameraAccessException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        Set<Set<String>> concurrentSets = cameraManager.getConcurrentCameraIds();
        for (Set<String> ids : concurrentSets) {
            if (ids.contains(first) && ids.contains(second)) return true;
        }
        return false;
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
                        sensorAspectRatio(candidate),
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
        return isCameraTimestampRealtime(characteristics);
    }

    private static boolean isCameraTimestampRealtime(
            @Nullable CameraCharacteristics cameraCharacteristics
    ) {
        if (cameraCharacteristics == null) return false;
        Integer source = cameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE
        );
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
        setContinuousVideoAf(builder, characteristics);
    }

    private static void setContinuousVideoAf(
            @NonNull CaptureRequest.Builder builder,
            @Nullable CameraCharacteristics cameraCharacteristics
    ) {
        int[] modes = cameraCharacteristics == null
                ? null
                : cameraCharacteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
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
                        sensorAspectRatio(cameraCharacteristics),
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

    private void fallbackTo30Fps(
            @NonNull String reason,
            @Nullable Exception error
    ) {
        if (recorder != null && recorder.isStarted()) {
            reportError(new IllegalStateException(
                    "Unable to apply 30 fps fallback after recording started: " + reason,
                    error
            ));
            return;
        }
        if (frameRateFallbackAttempted) {
            reportError(error != null
                    ? error
                    : new IllegalStateException("30 fps fallback session failed"));
            return;
        }
        frameRateFallbackAttempted = true;
        force30Fps = true;
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
        recordingSize = pair.recording;
        sourceCropSize = pair.cropSize;
        activeCameraResolution = pair.cameraResolution;
        frontSelection = null;
        backSelection = null;
        disableDualActive("frame-rate fallback changed stream configuration", error);
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
                + ", recording=" + recordingSize
                + ", crop=" + sourceCropSize
                + ", fpsRange=" + activeFpsRange);
        Handler handler = cameraHandler;
        if (active && handler != null) handler.post(this::createCaptureSession);
    }

    /**
     * Picks the AE target range for the requested frame rate. A range topping out at the target
     * with the lowest lower bound is preferred, so auto exposure lengthens the exposure in low
     * light instead of underexposing at a locked rate. Weak front sensors often advertise only
     * [10,30]-like ranges besides a locked [30,30], so at 30 fps any lower bound is accepted;
     * at higher rates the range may drop only to half of the target. The narrowest range
     * containing the target is only the fallback.
     */
    @Nullable
    private static Range<Integer> findBestFpsRange(
            @Nullable Range<Integer>[] ranges,
            int targetFps
    ) {
        if (ranges == null) return null;
        int minimumFps = targetFps > 30 ? targetFps / 2 : 1;
        Range<Integer> variable = null;
        Range<Integer> narrowest = null;
        for (Range<Integer> range : ranges) {
            if (!range.contains(targetFps)) continue;
            if (range.getUpper() == targetFps
                    && range.getLower() >= minimumFps
                    && (variable == null || range.getLower() < variable.getLower())) {
                variable = range;
            }
            if (narrowest == null
                    || range.getLower() > narrowest.getLower()
                    || range.getLower().equals(narrowest.getLower())
                    && range.getUpper() < narrowest.getUpper()) {
                narrowest = range;
            }
        }
        return variable != null ? variable : narrowest;
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
        // The GL processor renders the preview already cropped and oriented.
        previewView.setTransform(new Matrix());
        int rotation = displayRotationDegrees();
        if (rotation != appliedDisplayRotation) postToCameraThread(this::applyDisplayRotation);
    }

    /** Rotation of the display from the device's natural orientation, in degrees. */
    private int displayRotationDegrees() {
        Display display = previewView.getDisplay();
        if (display == null) return 0;
        switch (display.getRotation()) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private void applyDisplayRotation() {
        int rotation = displayRotationDegrees();
        if (rotation == appliedDisplayRotation || glProcessor == null) return;
        appliedDisplayRotation = rotation;
        glProcessor.setDisplayRotation(rotation);
        diagnostics.log("display rotation applied: degrees=" + rotation);
    }

    private void closeCameraAndSurfaces() {
        cancelCommonStartWait();
        switchingCamera = false;
        afterClose = AfterClose.NONE;
        // Opens still in flight are superseded: their devices are closed when they arrive.
        mainOpenRequest = null;
        warmOpenRequest = null;
        previewTransformEnabled = false;
        notifySwitchCompletion = false;
        awaitingSwitchPreviewFrame = false;
        repeatingBuilder = null;
        torchVerificationPending = false;
        pendingWarmSwitchFacing = null;
        closeCaptureSession();
        closeWarmCaptureSession();
        cameraDevice = null;
        warmCameraDevice = null;
        warmCameraSelection = null;
        if (!ownedDevices.isEmpty()) {
            cameraCloseRequestedNs = SystemClock.elapsedRealtimeNanos();
            diagnostics.log("closing camera devices: count=" + ownedDevices.size());
            for (CameraDevice device : new ArrayList<>(ownedDevices)) {
                closeDevice(device, AfterClose.NONE);
            }
        }
        if (glProcessor != null) {
            glProcessor.stop();
            glProcessor = null;
        }
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
        recordingSurface = null;
        secondaryRecordingSurface = null;
        dualActiveMode = false;
        activeDualSessionConfigured = false;
        warmDualSessionConfigured = false;
        activeInputSecondary = false;
    }

    private void closeCaptureSession() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        repeatingBuilder = null;
    }

    private void closeWarmCaptureSession() {
        if (warmCaptureSession != null) {
            warmCaptureSession.close();
            warmCaptureSession = null;
        }
        warmRepeatingBuilder = null;
        warmDualSessionConfigured = false;
    }

    private void reportError(@NonNull Exception error) {
        diagnostics.error("camera error", error);
        callback.onError(error);
    }

    /**
     * Losing the camera after recording started, for example on eviction when the host goes to
     * the background, ends only the active segment so the recorded part can still be previewed.
     */
    private void reportCameraLoss(@NonNull Exception error) {
        if (recorder == null || !recorder.isStarted()) {
            reportError(error);
            return;
        }
        diagnostics.error("camera lost while recording", error);
        callback.onSegmentInterrupted(error);
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

    /**
     * Picks the single camera stream the GL processor crops the square from. Within a tier every
     * size whose short side fits the crop is a candidate; the one whose centre square keeps the
     * widest field of view wins. A stream wider than the sensor (16:9 off a 4:3 sensor) loses
     * rows top and bottom, and a short side above the crop loses the oversize, so a 4:3 or square
     * stream whose short side equals the crop keeps the whole sensor height, like the old
     * recorder did. Ties go to the smaller stream.
     */
    @NonNull
    private static OutputPair chooseOutputPair(
            @NonNull Size[] sizes,
            float sensorAspectRatio,
            @NonNull RoundVideoSession.OutputResolution outputResolution,
            @NonNull RoundVideoSession.CameraResolution requestedResolution
    ) {
        int preferredCrop = getSourceCropSize(outputResolution, requestedResolution);
        OutputPair preferred = chooseTierPair(
                sizes,
                sensorAspectRatio,
                preferredCrop,
                requestedResolution
        );
        if (preferred != null) return preferred;

        if (outputResolution == RoundVideoSession.OutputResolution.P360
                && requestedResolution == RoundVideoSession.CameraResolution.MEDIUM) {
            OutputPair mediumFallback = chooseTierPair(
                    sizes,
                    sensorAspectRatio,
                    480,
                    RoundVideoSession.CameraResolution.MEDIUM
            );
            if (mediumFallback != null) return mediumFallback;
        }

        int lowCrop = outputResolution.getSize();
        if (requestedResolution != RoundVideoSession.CameraResolution.LOW) {
            OutputPair low = chooseTierPair(
                    sizes,
                    sensorAspectRatio,
                    lowCrop,
                    RoundVideoSession.CameraResolution.LOW
            );
            if (low != null) return low;
        }

        Size recording = chooseFallbackRecordingSize(sizes, outputResolution.getSize());
        int cropSize = Math.min(shortSide(recording), MAXIMUM_SOURCE_SHORT_SIDE);
        return new OutputPair(
                recording,
                RoundVideoSession.CameraResolution.LOW,
                cropSize
        );
    }

    @Nullable
    private static OutputPair chooseTierPair(
            @NonNull Size[] sizes,
            float sensorAspectRatio,
            int cropSize,
            @NonNull RoundVideoSession.CameraResolution resolution
    ) {
        OutputPair best = null;
        float bestCoverage = 0f;
        for (Size recording : sizes) {
            if (!isValidTierSize(recording, cropSize)) continue;
            float coverage = squareFieldCoverage(recording, cropSize, sensorAspectRatio);
            if (best == null
                    || coverage > bestCoverage
                    || coverage == bestCoverage && area(recording) < area(best.recording)) {
                best = new OutputPair(recording, resolution, cropSize);
                bestCoverage = coverage;
            }
        }
        return best;
    }

    /**
     * Fraction of the sensor's short side that the centre {@code cropSize} square of
     * {@code stream} covers. Camera2 crops a stream to its own aspect ratio inside the crop
     * region, so a stream wider than the sensor keeps only {@code sensorAspect / streamAspect}
     * of the sensor height; a narrower or square stream keeps all of it.
     */
    private static float squareFieldCoverage(
            @NonNull Size stream,
            int cropSize,
            float sensorAspectRatio
    ) {
        float streamAspect = Math.max(stream.getWidth(), stream.getHeight())
                / (float) shortSide(stream);
        float heightFraction = Math.min(1f, sensorAspectRatio / streamAspect);
        return heightFraction * cropSize / shortSide(stream);
    }

    /** Long side over short side of the active sensor area; 4:3 when unknown. */
    private static float sensorAspectRatio(@Nullable CameraCharacteristics cameraCharacteristics) {
        Rect activeArray = cameraCharacteristics == null
                ? null
                : cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (activeArray == null || activeArray.width() <= 0 || activeArray.height() <= 0) {
            return 4f / 3f;
        }
        return Math.max(activeArray.width(), activeArray.height())
                / (float) Math.min(activeArray.width(), activeArray.height());
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
        final Size recording;
        final RoundVideoSession.CameraResolution cameraResolution;
        final int cropSize;

        OutputPair(
                Size recording,
                RoundVideoSession.CameraResolution cameraResolution,
                int cropSize
        ) {
            this.recording = recording;
            this.cameraResolution = cameraResolution;
            this.cropSize = cropSize;
        }
    }
}
