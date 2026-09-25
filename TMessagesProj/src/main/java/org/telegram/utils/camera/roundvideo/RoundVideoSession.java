package org.telegram.utils.camera.roundvideo;

import android.content.Context;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.TextureView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Records, previews, trims and produces a Telegram-style round video.
 *
 * <p>All public methods must be called on the main thread. UI callbacks are delivered on the
 * main thread. Output callbacks are serialized on a dedicated background thread. The host must
 * pause, finish or release an active session before losing foreground camera access.</p>
 */
public final class RoundVideoSession {

    /** Square resolution written by the video encoder. */
    public enum OutputResolution {
        /** 480 x 480 output. */
        P480(480),
        /** 360 x 360 output. */
        P360(360);

        private final int size;

        OutputResolution(int size) {
            this.size = size;
        }

        /** Returns the encoded width and height in pixels. */
        public int getSize() {
            return size;
        }
    }

    /** Preferred Camera2 source resolution relative to the encoded output. */
    public enum CameraResolution {
        /** Uses a source crop twice the output size. */
        HIGH,
        /** Uses a 720 crop for 480 output or tries 540 then 480 for 360 output. */
        MEDIUM,
        /** Prefers a source crop equal to the output size. */
        LOW
    }

    /** Camera selected for the next or current recording segment. */
    public enum CameraFacing {
        /** User-facing camera. */
        FRONT,
        /** World-facing camera. */
        BACK
    }

    /** Requested video capture and encoder frame rate. */
    public enum FrameRate {
        /** Records at 30 frames per second. */
        FPS_30(30),
        /** Requests 60 fps through regular AE ranges and falls back when either camera lacks it. */
        FPS_60(60);

        private final int value;

        FrameRate(int value) {
            this.value = value;
        }

        /** Returns the requested frames per second. */
        public int getValue() {
            return value;
        }
    }

    /** High-level lifecycle state of the session. */
    public enum State {
        /** The session is configured but has not started. */
        IDLE,
        /** Camera and codecs are being prepared. */
        STARTING,
        /** Camera and audio are being recorded. */
        RECORDING,
        /** The current segment is being finalized for preview. */
        PAUSING,
        /** The recorded snapshot is available for playback and trimming. */
        PREVIEWING,
        /** A selected range is being prepared for another segment. */
        RESUMING,
        /** The final file is being completed or rebuilt. */
        FINISHING,
        /** The final output is ready. */
        COMPLETED,
        /** An unrecoverable error ended the session. */
        ERROR,
        /** Resources were released and temporary output was cancelled. */
        RELEASED
    }

    /** Flash implementation available for the selected camera. */
    public enum FlashType {
        /** No flash implementation is available. */
        NONE,
        /** Camera2 torch mode is available. */
        TORCH,
        /** The host can provide a white-screen flash. */
        SCREEN
    }

    /** Reason why a previously announced output generation must no longer be uploaded. */
    public enum OutputInvalidationReason {
        /** A trim operation replaced the uploaded prefix. */
        TRIMMED,
        /** The final output was rebuilt without an audio track. */
        AUDIO_REMOVED,
        /** The user cancelled the recording. */
        CANCELLED,
        /** A fatal processing error invalidated the output. */
        ERROR
    }

    /** Receives state and playback events used by the UI. */
    public interface Listener {
        /** Called whenever the public state snapshot changes. */
        default void onStateChanged(@NonNull StateInfo stateInfo) {}

        /** Called when camera-specific controls become available or change. */
        default void onCameraCapabilitiesChanged(@NonNull CameraCapabilities capabilities) {}

        /** Called before the active Camera2 device starts changing. */
        default void onCameraSwitchStarted(@NonNull CameraFacing facing) {}

        /** Called after the requested camera capture session has been configured. */
        default void onCameraSwitchCompleted(@NonNull CameraFacing facing) {}

        /** Called when the preview renders the first frame from the switched camera. */
        default void onCameraSwitchFirstFrame() {}

        /** Called after a playable snapshot has been attached to the preview TextureView. */
        default void onPreviewReady(long durationMs, long trimStartMs, long trimEndMs) {}

        /** Called when recorded-video playback renders its first frame. */
        default void onPreviewFirstFrame() {}

        /** Called when recorded-video playback starts or pauses. */
        default void onPreviewPlaybackChanged(boolean playing) {}

        /** Called at most thirty times per second while recorded-video playback is active. */
        default void onPreviewProgress(long positionMs, long durationMs) {}

        /** Called after the final output generation has been completed. */
        default void onCompleted(@NonNull Result result) {}

        /** Called after an unrecoverable camera, codec, playback or file error. */
        default void onError(@NonNull Exception error) {}
    }

    /** Receives stable ranges of the growing MP4 output. */
    public interface OutputListener {
        /** Called before ranges from a new output generation are announced. */
        default void onOutputStarted(long outputId, @NonNull File file) {}

        /** Called when the specified immutable file range is ready to be read and uploaded. */
        default void onBytesAvailable(
                long outputId,
                @NonNull File file,
                long offset,
                long length
        ) {}

        /** Called when all data previously announced for an output generation is obsolete. */
        default void onOutputInvalidated(
                long outputId,
                @NonNull OutputInvalidationReason reason
        ) {}

        /** Called after the final MP4 metadata has been written. */
        default void onOutputCompleted(
                long outputId,
                @NonNull File file,
                long finalSize,
                long durationMs,
                boolean hasAudio
        ) {}

        /** Called when an output generation cannot be written. */
        default void onOutputError(long outputId, @NonNull Exception error) {}
    }

    /** Applies and restores the UI implementation of the front-camera screen flash. */
    public interface ScreenFlashController {
        /** Enables or disables the white overlay and maximum window brightness. */
        void setScreenFlashEnabled(boolean enabled);
    }

    /** Builds an immutable round-video session configuration. */
    public static final class Builder {
        private final Context context;
        private final TextureView previewView;
        private CameraFacing initialFacing;
        private OutputResolution outputResolution;
        private CameraResolution cameraResolution;
        private FrameRate frameRate;
        private int videoBitrate;
        private long maximumDurationMs = DEFAULT_MAXIMUM_DURATION_MS;
        private boolean compositionEnabled = true;
        private Listener listener;
        private OutputListener outputListener;
        private ScreenFlashController screenFlashController;

        /** Creates a builder attached to a UI-owned preview TextureView. */
        public Builder(@NonNull Context context, @NonNull TextureView previewView) {
            this.context = context;
            this.previewView = previewView;
        }

        /** Sets the camera opened for the first recording segment. */
        public @NonNull Builder setInitialFacing(@NonNull CameraFacing facing) {
            initialFacing = facing;
            return this;
        }

        /** Sets the required square encoder resolution. */
        public @NonNull Builder setOutputResolution(@NonNull OutputResolution resolution) {
            outputResolution = resolution;
            return this;
        }

        /** Sets the required AVC bitrate in bits per second. */
        public @NonNull Builder setVideoBitrate(int bitrate) {
            videoBitrate = bitrate;
            return this;
        }

        /** Sets the preferred camera source tier. */
        public @NonNull Builder setCameraResolution(@NonNull CameraResolution resolution) {
            cameraResolution = resolution;
            return this;
        }

        /** Sets the required capture frame-rate policy. */
        public @NonNull Builder setFrameRate(@NonNull FrameRate rate) {
            frameRate = rate;
            return this;
        }

        /** Sets the maximum retained recording duration. */
        public @NonNull Builder setMaximumDurationMs(long durationMs) {
            maximumDurationMs = durationMs;
            return this;
        }

        /** Enables the circular blur, dimming and Telegram overlays. */
        public @NonNull Builder setCompositionEnabled(boolean enabled) {
            compositionEnabled = enabled;
            return this;
        }

        /** Sets the required listener receiving main-thread session events. */
        public @NonNull Builder setListener(@NonNull Listener value) {
            listener = value;
            return this;
        }

        /** Sets the required listener receiving serialized growing-file events. */
        public @NonNull Builder setOutputListener(@NonNull OutputListener value) {
            outputListener = value;
            return this;
        }

        /** Sets the optional host implementation of the front-camera screen flash. */
        public @NonNull Builder setScreenFlashController(@Nullable ScreenFlashController value) {
            screenFlashController = value;
            return this;
        }

        /** Validates the required options and creates the session. */
        public @NonNull RoundVideoSession build() {
            if (initialFacing == null) throw new IllegalStateException("Initial camera is required");
            if (outputResolution == null) throw new IllegalStateException("Output resolution is required");
            if (videoBitrate <= 0) throw new IllegalStateException("Video bitrate is required");
            if (cameraResolution == null) throw new IllegalStateException("Camera resolution is required");
            if (frameRate == null) throw new IllegalStateException("Frame rate is required");
            if (maximumDurationMs <= 0) throw new IllegalStateException("Invalid duration limit");
            if (listener == null) throw new IllegalStateException("Session listener is required");
            if (outputListener == null) throw new IllegalStateException("Output listener is required");
            return new RoundVideoSession(this);
        }
    }

    /** Immutable state used to drive UI controls and recording progress. */
    public static final class StateInfo {
        private final State state;
        private final long recordedDurationMs;
        private final long recordingStartedAtRealtimeMs;
        private final long maximumDurationMs;
        private final long trimStartMs;
        private final long trimEndMs;
        private final boolean previewPlaying;
        private final boolean cameraSwitching;
        private final boolean flashEnabled;
        private final float zoomProgress;

        private StateInfo(
                State state,
                long recordedDurationMs,
                long recordingStartedAtRealtimeMs,
                long maximumDurationMs,
                long trimStartMs,
                long trimEndMs,
                boolean previewPlaying,
                boolean cameraSwitching,
                boolean flashEnabled,
                float zoomProgress
        ) {
            this.state = state;
            this.recordedDurationMs = recordedDurationMs;
            this.recordingStartedAtRealtimeMs = recordingStartedAtRealtimeMs;
            this.maximumDurationMs = maximumDurationMs;
            this.trimStartMs = trimStartMs;
            this.trimEndMs = trimEndMs;
            this.previewPlaying = previewPlaying;
            this.cameraSwitching = cameraSwitching;
            this.flashEnabled = flashEnabled;
            this.zoomProgress = zoomProgress;
        }

        /** Returns the high-level lifecycle state. */
        public @NonNull State getState() { return state; }

        /** Returns the duration committed before the active recording segment. */
        public long getRecordedDurationMs() { return recordedDurationMs; }

        /** Returns the elapsed-realtime origin of the active recording segment. */
        public long getRecordingStartedAtRealtimeMs() { return recordingStartedAtRealtimeMs; }

        /** Returns the hard recording-duration limit. */
        public long getMaximumDurationMs() { return maximumDurationMs; }

        /** Returns the selected preview range start. */
        public long getTrimStartMs() { return trimStartMs; }

        /** Returns the selected preview range end. */
        public long getTrimEndMs() { return trimEndMs; }

        /** Returns whether the recorded preview is playing. */
        public boolean isPreviewPlaying() { return previewPlaying; }

        /** Returns whether an active camera change is in progress. */
        public boolean isCameraSwitching() { return cameraSwitching; }

        /** Returns whether torch or screen flash is enabled. */
        public boolean isFlashEnabled() { return flashEnabled; }

        /** Returns normalized zoom in the inclusive range from zero to one. */
        public float getZoomProgress() { return zoomProgress; }

        /** Returns whether recording can be paused now. */
        public boolean canPauseRecording() { return state == State.RECORDING; }

        /** Returns whether another recording segment can be started. */
        public boolean canResumeRecording() {
            return state == State.PREVIEWING && trimEndMs - trimStartMs < maximumDurationMs;
        }

        /** Returns whether playback and trimming controls are available. */
        public boolean canControlPreview() { return state == State.PREVIEWING; }

        /** Returns whether zoom, flash and live camera switching are available. */
        public boolean canControlCamera() { return state == State.RECORDING && !cameraSwitching; }

        /** Returns whether the current recording can be finalized. */
        public boolean canFinish() { return state == State.RECORDING || state == State.PREVIEWING; }
    }

    /** Immutable capabilities of the selected Camera2 device. */
    public static final class CameraCapabilities {
        private final CameraFacing requestedFacing;
        private final CameraFacing activeFacing;
        private final CameraResolution requestedResolution;
        private final CameraResolution activeResolution;
        private final FrameRate requestedFrameRate;
        private final FrameRate activeFrameRate;
        private final FlashType flashType;
        private final float maximumZoomRatio;

        private CameraCapabilities(
                CameraFacing requestedFacing,
                CameraFacing activeFacing,
                CameraResolution requestedResolution,
                CameraResolution activeResolution,
                FrameRate requestedFrameRate,
                FrameRate activeFrameRate,
                FlashType flashType,
                float maximumZoomRatio
        ) {
            this.requestedFacing = requestedFacing;
            this.activeFacing = activeFacing;
            this.requestedResolution = requestedResolution;
            this.activeResolution = activeResolution;
            this.requestedFrameRate = requestedFrameRate;
            this.activeFrameRate = activeFrameRate;
            this.flashType = flashType;
            this.maximumZoomRatio = maximumZoomRatio;
        }

        /** Returns the camera requested for the current or next segment. */
        public @NonNull CameraFacing getRequestedFacing() { return requestedFacing; }

        /** Returns the configured camera, or null before Camera2 is ready. */
        public @Nullable CameraFacing getActiveFacing() { return activeFacing; }

        /** Returns the source resolution requested when the session was created. */
        public @NonNull CameraResolution getRequestedResolution() { return requestedResolution; }

        /** Returns the selected source resolution, or null before Camera2 is ready. */
        public @Nullable CameraResolution getActiveResolution() { return activeResolution; }

        /** Returns the frame-rate policy requested when the session was created. */
        public @NonNull FrameRate getRequestedFrameRate() { return requestedFrameRate; }

        /** Returns the negotiated frame rate, or null before Camera2 is ready. */
        public @Nullable FrameRate getActiveFrameRate() { return activeFrameRate; }

        /** Returns the flash implementation available for the configured camera. */
        public @NonNull FlashType getFlashType() { return flashType; }

        /** Returns the maximum zoom ratio exposed by the normalized zoom control. */
        public float getMaximumZoomRatio() { return maximumZoomRatio; }

        /** Returns whether the configured camera supports zoom. */
        public boolean isZoomSupported() { return maximumZoomRatio > 1f; }
    }

    /** Immutable description of the completed output file. */
    public static final class Result {
        private final long outputId;
        private final File file;
        private final long durationMs;
        private final boolean hasAudio;
        private final FrameRate frameRate;

        private Result(
                long outputId,
                File file,
                long durationMs,
                boolean hasAudio,
                FrameRate frameRate
        ) {
            this.outputId = outputId;
            this.file = file;
            this.durationMs = durationMs;
            this.hasAudio = hasAudio;
            this.frameRate = frameRate;
        }

        /** Returns the final output generation identifier. */
        public long getOutputId() { return outputId; }

        /** Returns the finalized MP4 file. */
        public @NonNull File getFile() { return file; }

        /** Returns the finalized media duration. */
        public long getDurationMs() { return durationMs; }

        /** Returns whether the final MP4 contains an audio track. */
        public boolean hasAudio() { return hasAudio; }

        /** Returns the negotiated frame rate used by the encoder. */
        public @NonNull FrameRate getFrameRate() { return frameRate; }
    }

    private static final long DEFAULT_MAXIMUM_DURATION_MS = 60_000L;
    private static final long MINIMUM_TRIM_DURATION_MS = 800L;
    private static final long PREVIEW_PROGRESS_DELAY_MS = 33L;

    private final Context context;
    private final TextureView previewView;
    private final Listener listener;
    private final OutputListener outputListener;
    private final ScreenFlashController screenFlashController;
    private final Object outputLock = new Object();
    private final Matrix identityPreviewTransform = new Matrix();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "RoundVideoFiles")
    );
    private final ExecutorService outputExecutor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "RoundVideoOutput")
    );
    private final RoundVideoCameraController cameraController;
    private final RoundVideoDiagnostics diagnostics;
    private final OutputResolution outputResolution;
    private final int videoBitrate;
    private final CameraResolution requestedCameraResolution;
    private final FrameRate requestedFrameRate;
    private final boolean compositionEnabled;
    private final long maximumDurationMs;

    private State state = State.IDLE;
    private CameraFacing requestedFacing;
    private CameraFacing activeFacing;
    private CameraResolution activeCameraResolution;
    private FrameRate activeFrameRate;
    private FlashType flashType = FlashType.NONE;
    private float maximumZoomRatio = 1f;
    private float zoomProgress;
    private boolean flashEnabled;
    private boolean screenFlashEnabled;
    private boolean cameraSwitching;
    private boolean previewPlaying;
    private boolean finishAfterCameraStops;
    private boolean finishWithAudio;
    private boolean cancelAfterCameraStops;
    private boolean cameraStopPending;
    private boolean cleanupStarted;
    private volatile boolean cancelRequested;
    private long recordedDurationMs;
    private long recordingStartedAtRealtimeMs;
    private long trimStartMs;
    private long trimEndMs;
    private long nextOutputId = 1;
    private long previewSnapshotStartedNs;
    private long previewSourceDurationMs;
    private int pauseCount;
    private int resumeCount;
    private int cameraSwitchCount;
    private boolean summaryLogged;
    private volatile OutputGeneration outputGeneration;
    private volatile RoundVideoMp4Writer writer;
    private volatile File previewFile;
    private ExoPlayer player;

    private final Runnable durationLimitRunnable = () -> {
        if (state == State.RECORDING) pauseRecording();
    };

    private final Runnable previewProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (state != State.PREVIEWING || player == null || !previewPlaying) return;
            long positionMs = player.getCurrentPosition();
            if (positionMs < trimStartMs || positionMs >= trimEndMs) {
                player.seekTo(trimStartMs);
                positionMs = trimStartMs;
            }
            listener.onPreviewProgress(positionMs, recordedDurationMs);
            mainHandler.postDelayed(this, PREVIEW_PROGRESS_DELAY_MS);
        }
    };

    private RoundVideoSession(@NonNull Builder builder) {
        requireMainThread();
        Context context = builder.context;
        Context applicationContext = context.getApplicationContext();
        this.context = applicationContext == null ? context : applicationContext;
        previewView = builder.previewView;
        requestedFacing = builder.initialFacing;
        outputResolution = builder.outputResolution;
        videoBitrate = builder.videoBitrate;
        requestedCameraResolution = builder.cameraResolution;
        requestedFrameRate = builder.frameRate;
        maximumDurationMs = builder.maximumDurationMs;
        compositionEnabled = builder.compositionEnabled;
        listener = builder.listener;
        outputListener = builder.outputListener;
        screenFlashController = builder.screenFlashController;
        diagnostics = new RoundVideoDiagnostics();
        diagnostics.log("session created: device=" + Build.MANUFACTURER + " " + Build.MODEL
                + ", sdk=" + Build.VERSION.SDK_INT
                + ", output=" + outputResolution.getSize() + "x" + outputResolution.getSize()
                + ", bitrate=" + videoBitrate
                + ", cameraMode=" + requestedCameraResolution
                + ", fps=" + requestedFrameRate.getValue()
                + ", composition=" + compositionEnabled
                + ", facing=" + requestedFacing
                + ", maxDurationMs=" + maximumDurationMs);
        cameraController = new RoundVideoCameraController(
                this.context,
                previewView,
                outputResolution,
                videoBitrate,
                requestedCameraResolution,
                requestedFrameRate,
                compositionEnabled,
                diagnostics,
                cameraCallback
        );
    }

    /** Opens the requested camera and starts recording after its first frame arrives. */
    @MainThread
    public boolean start() {
        requireMainThread();
        if (state != State.IDLE) return false;
        try {
            diagnostics.log("start requested");
            createOutputGeneration(true);
            setState(State.STARTING);
            cameraController.startSegment(writer, 0, requestedFacing);
            return true;
        } catch (Exception e) {
            fail(asException(e));
            return false;
        }
    }

    /** Finalizes the current segment and starts recorded-video preview. */
    @MainThread
    public boolean pauseRecording() {
        requireMainThread();
        if (state != State.RECORDING) return false;
        recordedDurationMs = getRecordedDurationMs();
        pauseCount++;
        diagnostics.log("pause requested: durationMs=" + recordedDurationMs);
        disableCameraEffects();
        mainHandler.removeCallbacks(durationLimitRunnable);
        setState(State.PAUSING);
        cameraStopPending = cameraController.stopSegment();
        if (!cameraStopPending) fail(new IllegalStateException("Unable to stop the camera segment"));
        return true;
    }

    /** Applies the selected trim range and continues recording with the requested camera. */
    @MainThread
    public boolean resumeRecording() {
        requireMainThread();
        if (state != State.PREVIEWING || trimEndMs - trimStartMs >= maximumDurationMs) return false;
        resumeCount++;
        diagnostics.log("resume requested: trim=" + trimStartMs + ".." + trimEndMs
                + ", sourceDurationMs=" + recordedDurationMs);
        releasePlayer();
        setState(State.RESUMING);
        rebuildForResume();
        return true;
    }

    /** Updates the selected recorded-video range used by preview, resume and finish. */
    @MainThread
    public boolean setTrimRange(long startMs, long endMs) {
        requireMainThread();
        if (state != State.PREVIEWING) return false;
        long clampedStart = clamp(startMs, 0, recordedDurationMs);
        long clampedEnd = clamp(endMs, clampedStart, recordedDurationMs);
        if (clampedEnd - clampedStart < Math.min(MINIMUM_TRIM_DURATION_MS, recordedDurationMs)) {
            return false;
        }
        trimStartMs = clampedStart;
        trimEndMs = clampedEnd;
        if (player != null) player.seekTo(trimStartMs);
        listener.onPreviewProgress(trimStartMs, recordedDurationMs);
        notifyState();
        return true;
    }

    /** Starts or resumes playback of the currently selected recorded-video range. */
    @MainThread
    public boolean playPreview() {
        requireMainThread();
        if (state != State.PREVIEWING || player == null) return false;
        long position = player.getCurrentPosition();
        if (position < trimStartMs || position >= trimEndMs) player.seekTo(trimStartMs);
        player.play();
        updatePreviewPlaying(true);
        return true;
    }

    /** Pauses recorded-video playback without leaving PREVIEWING state. */
    @MainThread
    public boolean pausePreview() {
        requireMainThread();
        if (state != State.PREVIEWING || player == null) return false;
        player.pause();
        updatePreviewPlaying(false);
        listener.onPreviewProgress(player.getCurrentPosition(), recordedDurationMs);
        return true;
    }

    /** Mutes or restores audio only for local recorded-video preview playback. */
    @MainThread
    public boolean setPreviewMuted(boolean muted) {
        requireMainThread();
        if (state != State.PREVIEWING || player == null) return false;
        player.setVolume(muted ? 0f : 1f);
        return true;
    }

    /** Seeks recorded-video playback inside the currently selected trim range. */
    @MainThread
    public boolean seekPreview(long positionMs) {
        requireMainThread();
        if (state != State.PREVIEWING || player == null) return false;
        long position = clamp(positionMs, trimStartMs, Math.max(trimStartMs, trimEndMs - 1));
        player.seekTo(position);
        listener.onPreviewProgress(position, recordedDurationMs);
        return true;
    }

    /** Selects the camera used immediately or when the next segment starts. */
    @MainThread
    public boolean setCameraFacing(@NonNull CameraFacing facing) {
        requireMainThread();
        if (isTerminalState()) return false;
        if (requestedFacing == facing) return true;
        diagnostics.log("camera facing requested: " + requestedFacing + " -> " + facing
                + ", state=" + state);
        requestedFacing = facing;
        if (state == State.RECORDING || state == State.STARTING) {
            disableCameraEffects();
            cameraSwitching = cameraController.setCameraFacing(facing);
        }
        notifyCapabilities();
        notifyState();
        return true;
    }

    /** Applies normalized camera zoom in the inclusive range from zero to one. */
    @MainThread
    public boolean setZoom(float progress) {
        requireMainThread();
        if (state != State.RECORDING || cameraSwitching) return false;
        zoomProgress = clamp(progress, 0f, 1f);
        return cameraController.setZoom(zoomProgress);
    }

    /** Enables the rear torch or requests front-camera screen flash. */
    @MainThread
    public boolean setFlashEnabled(boolean enabled) {
        requireMainThread();
        if (state != State.RECORDING || cameraSwitching || flashType == FlashType.NONE) return false;
        flashEnabled = enabled;
        if (flashType == FlashType.SCREEN) {
            setScreenFlashEnabled(enabled);
            cameraController.setTorchEnabled(false);
        } else {
            setScreenFlashEnabled(false);
            cameraController.setTorchEnabled(enabled);
        }
        notifyState();
        return true;
    }

    /** Finalizes the selected recording and optionally removes its audio track. */
    @MainThread
    public boolean finish(boolean includeAudio) {
        requireMainThread();
        if (state != State.RECORDING && state != State.PREVIEWING) return false;
        diagnostics.log("finish requested: state=" + state
                + ", includeAudio=" + includeAudio
                + ", durationMs=" + getRecordedDurationMs()
                + ", trim=" + trimStartMs + ".." + trimEndMs);
        finishWithAudio = includeAudio;
        if (state == State.RECORDING) {
            recordedDurationMs = getRecordedDurationMs();
            finishAfterCameraStops = true;
            disableCameraEffects();
            mainHandler.removeCallbacks(durationLimitRunnable);
            setState(State.FINISHING);
            cameraStopPending = cameraController.stopSegment();
            if (!cameraStopPending) fail(new IllegalStateException("Unable to stop the camera segment"));
        } else {
            releasePlayer();
            setState(State.FINISHING);
            finishFromPreview(includeAudio);
        }
        return true;
    }

    /** Cancels the session, invalidates upload data and deletes temporary media. */
    @MainThread
    public boolean cancel() {
        requireMainThread();
        if (state == State.RELEASED || state == State.COMPLETED) return false;
        diagnostics.log("cancel requested: state=" + state);
        if (!cancelOutput(OutputInvalidationReason.CANCELLED)) return false;
        disableCameraEffects();
        releasePlayer();
        setState(State.RELEASED);
        if (cameraStopPending || cameraController.stopSegment()) {
            cameraStopPending = true;
            cancelAfterCameraStops = true;
        } else {
            finishCancellation();
        }
        return true;
    }

    /** Releases UI, camera and playback resources. Active recordings are cancelled. */
    @MainThread
    public void release() {
        requireMainThread();
        if (state == State.RELEASED) {
            if (!cleanupStarted) finishCancellation();
            return;
        }
        if (state != State.COMPLETED) {
            cancel();
            return;
        }
        disableCameraEffects();
        releasePlayer();
        cameraController.release();
        cleanupStarted = true;
        setState(State.RELEASED);
        logSummary("released");
        shutdownExecutors();
    }

    /** Returns the latest immutable session state. */
    @MainThread
    public @NonNull StateInfo getStateInfo() {
        requireMainThread();
        return createStateInfo();
    }

    /** Returns the current finalized preview snapshot, or null outside preview mode. */
    @MainThread
    public @Nullable File getPreviewFile() {
        requireMainThread();
        return previewFile;
    }

    /** Returns the immutable encoded output resolution. */
    public @NonNull OutputResolution getOutputResolution() {
        return outputResolution;
    }

    /** Returns the immutable AVC bitrate in bits per second. */
    public int getVideoBitrate() {
        return videoBitrate;
    }

    /** Returns the requested frame-rate policy. */
    public @NonNull FrameRate getRequestedFrameRate() {
        return requestedFrameRate;
    }

    /** Returns the negotiated frame rate, or null before Camera2 configuration completes. */
    public @Nullable FrameRate getActiveFrameRate() {
        return activeFrameRate;
    }

    /** Returns the current recording duration, including the active segment. */
    @MainThread
    public long getRecordedDurationMs() {
        requireMainThread();
        if (state != State.RECORDING) return recordedDurationMs;
        return Math.min(
                maximumDurationMs,
                recordedDurationMs + SystemClock.elapsedRealtime() - recordingStartedAtRealtimeMs
        );
    }

    private final RoundVideoCameraController.Callback cameraCallback =
            new RoundVideoCameraController.Callback() {
                @Override
                public void onConfigured(
                        @NonNull RoundVideoCameraController.CameraInfo info,
                        boolean cameraSwitch
                ) {
                    mainHandler.post(() -> handleCameraConfigured(info, cameraSwitch));
                }

                @Override
                public void onRecordingStarted() {
                    mainHandler.post(RoundVideoSession.this::handleRecordingStarted);
                }

                @Override
                public void onCameraSwitchStarted(@NonNull CameraFacing facing) {
                    mainHandler.post(() -> {
                        cameraSwitchCount++;
                        cameraSwitching = true;
                        diagnostics.log("camera switch started: target=" + facing);
                        listener.onCameraSwitchStarted(facing);
                        notifyState();
                    });
                }

                @Override
                public void onCameraSwitchFirstFrame() {
                    mainHandler.post(listener::onCameraSwitchFirstFrame);
                }

                @Override
                public void onRecordingStopped() {
                    mainHandler.post(RoundVideoSession.this::handleRecordingStopped);
                }

                @Override
                public void onError(@NonNull Exception error) {
                    mainHandler.post(() -> fail(error));
                }
            };

    private void handleCameraConfigured(
            @NonNull RoundVideoCameraController.CameraInfo info,
            boolean cameraSwitch
    ) {
        if (isTerminalState()) return;
        activeFacing = info.facing;
        activeCameraResolution = info.cameraResolution;
        activeFrameRate = info.frameRate;
        maximumZoomRatio = info.maximumZoomRatio;
        flashType = info.facing == CameraFacing.FRONT
                ? screenFlashController == null ? FlashType.NONE : FlashType.SCREEN
                : info.torchAvailable ? FlashType.TORCH : FlashType.NONE;
        notifyCapabilities();
        diagnostics.log("camera configured: facing=" + info.facing
                + ", mode=" + info.cameraResolution
                + ", preview=" + info.previewSize
                + ", recording=" + info.recordingSize
                + ", fps=" + info.frameRate.getValue()
                + ", maxZoom=" + maximumZoomRatio
                + ", flash=" + flashType
                + ", switch=" + cameraSwitch);
        if (cameraSwitch || cameraSwitching) {
            cameraSwitching = false;
            listener.onCameraSwitchCompleted(info.facing);
            notifyState();
        }
    }

    private void handleRecordingStarted() {
        if (state != State.STARTING && state != State.RESUMING) return;
        recordingStartedAtRealtimeMs = SystemClock.elapsedRealtime();
        diagnostics.log("recording started: retainedDurationMs=" + recordedDurationMs);
        setState(State.RECORDING);
        long remaining = maximumDurationMs - recordedDurationMs;
        if (remaining <= 0) pauseRecording();
        else mainHandler.postDelayed(durationLimitRunnable, remaining);
    }

    private void handleRecordingStopped() {
        cameraStopPending = false;
        diagnostics.log("recording segment stopped: state=" + state
                + ", retainedDurationMs=" + recordedDurationMs);
        if (cancelAfterCameraStops) {
            cancelAfterCameraStops = false;
            finishCancellation();
            return;
        }
        if (finishAfterCameraStops) {
            finishAfterCameraStops = false;
            finishActiveRecording(finishWithAudio);
            return;
        }
        if (state != State.PAUSING) return;
        createPreviewSnapshot();
    }

    private void createPreviewSnapshot() {
        File snapshot;
        try {
            snapshot = File.createTempFile("round_video_preview_", ".mp4", context.getCacheDir());
        } catch (IOException e) {
            fail(e);
            return;
        }
        previewFile = snapshot;
        previewSnapshotStartedNs = System.nanoTime();
        diagnostics.log("preview snapshot started: file=" + snapshot.getName());
        RoundVideoMp4Writer currentWriter = writer;
        fileExecutor.execute(() -> {
            try {
                currentWriter.createPreview(snapshot);
                ensureNotCancelled();
                long duration = RoundVideoRemuxer.getDurationMs(snapshot);
                diagnostics.log("preview snapshot completed: durationMs=" + duration
                        + ", size=" + snapshot.length()
                        + ", elapsedMs=" + elapsedMs(previewSnapshotStartedNs));
                mainHandler.post(() -> enterPreview(duration));
            } catch (Exception e) {
                mainHandler.post(() -> fail(asException(e)));
            }
        });
    }

    private void enterPreview(long durationMs) {
        if (state != State.PAUSING) return;
        previewSourceDurationMs = durationMs;
        recordedDurationMs = Math.min(maximumDurationMs, durationMs);
        trimStartMs = 0;
        trimEndMs = recordedDurationMs;
        previewView.setSurfaceTextureListener(null);
        previewView.setTransform(identityPreviewTransform);
        player = new ExoPlayer.Builder(context).build();
        player.setVideoTextureView(previewView);
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(previewFile)));
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.setVolume(1f);
        player.addListener(playerListener);
        player.prepare();
        player.seekTo(trimStartMs);
        diagnostics.log("preview player prepared: durationMs=" + recordedDurationMs
                + ", trim=" + trimStartMs + ".." + trimEndMs);
        setState(State.PREVIEWING);
        listener.onPreviewReady(recordedDurationMs, trimStartMs, trimEndMs);
        playPreview();
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onRenderedFirstFrame() {
            if (state == State.PREVIEWING) listener.onPreviewFirstFrame();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            diagnostics.log("preview player error: code=" + error.errorCode);
            fail(error);
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (state == State.PREVIEWING) updatePreviewPlaying(isPlaying);
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            diagnostics.log("preview playback state=" + playbackState);
            if (state == State.PREVIEWING
                    && playbackState == Player.STATE_ENDED
                    && player != null) {
                player.seekTo(trimStartMs);
                player.play();
            }
        }
    };

    private void updatePreviewPlaying(boolean playing) {
        if (previewPlaying == playing) return;
        previewPlaying = playing;
        mainHandler.removeCallbacks(previewProgressRunnable);
        if (playing) mainHandler.post(previewProgressRunnable);
        listener.onPreviewPlaybackChanged(playing);
        notifyState();
    }

    private void rebuildForResume() {
        final boolean trimChanged = isTrimChanged();
        final File sourcePreview = previewFile;
        final RoundVideoMp4Writer oldWriter = writer;
        final OutputGeneration oldGeneration = outputGeneration;
        fileExecutor.execute(() -> {
            long rebuildStartedNs = System.nanoTime();
            File replacedFile = null;
            try {
                ensureNotCancelled();
                long retainedDuration = recordedDurationMs;
                if (trimChanged) {
                    oldWriter.closeForReplacement();
                    ensureNotCancelled();
                    invalidateOutput(oldGeneration, OutputInvalidationReason.TRIMMED);
                    File oldFile = oldWriter.getFile();
                    replacedFile = oldFile;
                    createOutputGenerationOnFileThread(true);
                    RoundVideoRemuxer.Result result = RoundVideoRemuxer.copyRange(
                            sourcePreview,
                            writer,
                            trimStartMs,
                            trimEndMs,
                            true
                    );
                    retainedDuration = result.durationMs;
                    diagnostics.log("resume trim remux completed: requested="
                            + trimStartMs + ".." + trimEndMs
                            + ", actualStartMs=" + result.startMs
                            + ", retainedDurationMs=" + retainedDuration
                            + ", outputSize=" + writer.getFile().length()
                            + ", elapsedMs=" + elapsedMs(rebuildStartedNs));
                }
                ensureNotCancelled();
                previewFile = null;
                long finalRetainedDuration = retainedDuration;
                if (!trimChanged) {
                    diagnostics.log("resume prepared without remux: retainedDurationMs="
                            + retainedDuration + ", elapsedMs=" + elapsedMs(rebuildStartedNs));
                }
                mainHandler.post(() -> {
                    if (state != State.RESUMING) return;
                    recordedDurationMs = finalRetainedDuration;
                    previewSourceDurationMs = 0;
                    trimStartMs = 0;
                    trimEndMs = recordedDurationMs;
                    setState(State.STARTING);
                    cameraController.startSegment(
                            writer,
                            recordedDurationMs * 1000L,
                            requestedFacing
                    );
                });
            } catch (Exception e) {
                mainHandler.post(() -> fail(asException(e)));
            } finally {
                if (sourcePreview != null) RoundVideoRemuxer.erase(sourcePreview);
                if (replacedFile != null) RoundVideoRemuxer.erase(replacedFile);
            }
        });
    }

    private void finishActiveRecording(boolean includeAudio) {
        RoundVideoMp4Writer currentWriter = writer;
        OutputGeneration currentGeneration = outputGeneration;
        fileExecutor.execute(() -> {
            long finishStartedNs = System.nanoTime();
            try {
                currentWriter.finish();
                long actualDurationMs = RoundVideoRemuxer.getDurationMs(currentWriter.getFile());
                diagnostics.log("active output finalized: size=" + currentWriter.getFile().length()
                        + ", durationMs=" + actualDurationMs
                        + ", elapsedMs=" + elapsedMs(finishStartedNs));
                ensureNotCancelled();
                boolean exceedsLimit = actualDurationMs > maximumDurationMs;
                if (includeAudio && !exceedsLimit) {
                    completeOutput(currentGeneration, currentWriter.getFile(), actualDurationMs, true);
                } else {
                    File source = currentWriter.getFile();
                    try {
                        replaceWithFinalRange(
                                source,
                                0,
                                Math.min(maximumDurationMs, actualDurationMs),
                                includeAudio,
                                exceedsLimit
                                        ? OutputInvalidationReason.TRIMMED
                                        : OutputInvalidationReason.AUDIO_REMOVED
                        );
                    } finally {
                        RoundVideoRemuxer.erase(source);
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> fail(asException(e)));
            }
        });
    }

    private void finishFromPreview(boolean includeAudio) {
        final boolean replace = isTrimChanged() || !includeAudio;
        final OutputInvalidationReason reason = isTrimChanged()
                ? OutputInvalidationReason.TRIMMED
                : OutputInvalidationReason.AUDIO_REMOVED;
        final File source = previewFile;
        final RoundVideoMp4Writer currentWriter = writer;
        final OutputGeneration currentGeneration = outputGeneration;
        fileExecutor.execute(() -> {
            long finishStartedNs = System.nanoTime();
            try {
                currentWriter.finish();
                diagnostics.log("preview output finalized: size=" + currentWriter.getFile().length()
                        + ", replace=" + replace
                        + ", elapsedMs=" + elapsedMs(finishStartedNs));
                ensureNotCancelled();
                if (replace) {
                    replaceWithFinalRange(source, trimStartMs, trimEndMs, includeAudio, reason);
                } else {
                    completeOutput(
                            currentGeneration,
                            currentWriter.getFile(),
                            previewSourceDurationMs > 0
                                    ? previewSourceDurationMs
                                    : recordedDurationMs,
                            true
                    );
                }
                previewFile = null;
            } catch (Exception e) {
                mainHandler.post(() -> fail(asException(e)));
            } finally {
                if (source != null) RoundVideoRemuxer.erase(source);
                if (replace) RoundVideoRemuxer.erase(currentWriter.getFile());
            }
        });
    }

    private void replaceWithFinalRange(
            @NonNull File source,
            long startMs,
            long endMs,
            boolean includeAudio,
            @NonNull OutputInvalidationReason reason
    ) throws IOException {
        long remuxStartedNs = System.nanoTime();
        diagnostics.log("final range remux started: range=" + startMs + ".." + endMs
                + ", includeAudio=" + includeAudio + ", reason=" + reason);
        ensureNotCancelled();
        OutputGeneration oldGeneration = outputGeneration;
        invalidateOutput(oldGeneration, reason);
        createOutputGenerationOnFileThread(includeAudio);
        ensureNotCancelled();
        RoundVideoRemuxer.Result result = RoundVideoRemuxer.copyRange(
                source,
                writer,
                startMs,
                endMs,
                includeAudio
        );
        writer.finish();
        long outputDurationMs = RoundVideoRemuxer.getDurationMs(writer.getFile());
        diagnostics.log("final range remux completed: durationMs=" + outputDurationMs
                + ", requestedDurationMs=" + result.durationMs
                + ", actualStartMs=" + result.startMs
                + ", size=" + writer.getFile().length()
                + ", elapsedMs=" + elapsedMs(remuxStartedNs));
        ensureNotCancelled();
        completeOutput(outputGeneration, writer.getFile(), outputDurationMs, includeAudio);
    }

    private void completeOutput(
            @NonNull OutputGeneration generation,
            @NonNull File file,
            long durationMs,
            boolean hasAudio
    ) {
        outputExecutor.execute(() -> {
            synchronized (outputLock) {
                if (cancelRequested || generation.invalidated || generation.completed) return;
                generation.completed = true;
            }
            outputListener.onOutputCompleted(
                    generation.id,
                    file,
                    file.length(),
                    durationMs,
                    hasAudio
            );
            mainHandler.post(() -> {
                if (state == State.RELEASED || state == State.ERROR) return;
                setState(State.COMPLETED);
                diagnostics.log("output completed: generation=" + generation.id
                        + ", durationMs=" + durationMs
                        + ", size=" + file.length()
                        + ", hasAudio=" + hasAudio);
                logSummary("completed");
                listener.onCompleted(new Result(
                        generation.id,
                        file,
                        durationMs,
                        hasAudio,
                        activeFrameRate == null ? FrameRate.FPS_30 : activeFrameRate
                ));
            });
        });
    }

    private void createOutputGeneration(boolean includeAudio) throws IOException {
        createOutputGenerationOnFileThread(includeAudio);
    }

    private void createOutputGenerationOnFileThread(boolean includeAudio) throws IOException {
        synchronized (outputLock) {
            ensureNotCancelled();
            File file = File.createTempFile("round_video_", ".mp4", context.getCacheDir());
            OutputGeneration generation = new OutputGeneration(nextOutputId++, file);
            outputGeneration = generation;
            writer = new RoundVideoMp4Writer(
                    file,
                    outputResolution.getSize(),
                    includeAudio,
                    diagnostics,
                    availableSize -> {
                        synchronized (outputLock) {
                            long offset = generation.availableSize;
                            long length = availableSize - offset;
                            if (length <= 0 || generation.invalidated) return;
                            generation.availableSize = availableSize;
                            outputExecutor.execute(() -> {
                                synchronized (outputLock) {
                                    if (generation.invalidated || generation.completed) return;
                                }
                                outputListener.onBytesAvailable(
                                        generation.id,
                                        generation.file,
                                        offset,
                                        length
                                );
                            });
                        }
                    }
            );
            diagnostics.log("output generation started: id=" + generation.id
                    + ", includeAudio=" + includeAudio
                    + ", file=" + file.getName());
            outputExecutor.execute(() -> outputListener.onOutputStarted(generation.id, file));
        }
    }

    private void invalidateOutput(@NonNull OutputInvalidationReason reason) {
        OutputGeneration generation = outputGeneration;
        if (generation != null) invalidateOutput(generation, reason);
    }

    private void invalidateOutput(
            @NonNull OutputGeneration generation,
            @NonNull OutputInvalidationReason reason
    ) {
        synchronized (outputLock) {
            if (generation.invalidated || generation.completed) return;
            generation.invalidated = true;
            diagnostics.log("output generation invalidated: id=" + generation.id
                    + ", reason=" + reason
                    + ", availableSize=" + generation.availableSize);
            outputExecutor.execute(() -> outputListener.onOutputInvalidated(generation.id, reason));
        }
    }

    private void setState(@NonNull State newState) {
        State oldState = state;
        state = newState;
        diagnostics.log("state: " + oldState + " -> " + newState
                + ", durationMs=" + getRecordedDurationMs());
        notifyState();
    }

    private void notifyState() {
        listener.onStateChanged(createStateInfo());
    }

    @NonNull
    private StateInfo createStateInfo() {
        return new StateInfo(
                state,
                recordedDurationMs,
                recordingStartedAtRealtimeMs,
                maximumDurationMs,
                trimStartMs,
                trimEndMs,
                previewPlaying,
                cameraSwitching,
                flashEnabled,
                zoomProgress
        );
    }

    private void notifyCapabilities() {
        listener.onCameraCapabilitiesChanged(new CameraCapabilities(
                requestedFacing,
                activeFacing,
                requestedCameraResolution,
                activeCameraResolution,
                requestedFrameRate,
                activeFrameRate,
                flashType,
                maximumZoomRatio
        ));
    }

    private void disableCameraEffects() {
        zoomProgress = 0f;
        flashEnabled = false;
        cameraController.setZoom(0f);
        cameraController.setTorchEnabled(false);
        setScreenFlashEnabled(false);
    }

    private void setScreenFlashEnabled(boolean enabled) {
        if (screenFlashEnabled == enabled) return;
        screenFlashEnabled = enabled;
        if (screenFlashController != null) screenFlashController.setScreenFlashEnabled(enabled);
    }

    private void releasePlayer() {
        mainHandler.removeCallbacks(previewProgressRunnable);
        previewPlaying = false;
        if (player == null) return;
        player.removeListener(playerListener);
        player.clearVideoTextureView(previewView);
        player.release();
        player = null;
    }

    private boolean isTrimChanged() {
        long sourceDurationMs = previewSourceDurationMs > 0
                ? previewSourceDurationMs
                : recordedDurationMs;
        return sourceDurationMs > maximumDurationMs
                || trimStartMs > 0
                || trimEndMs + 10 < sourceDurationMs;
    }

    private boolean isTerminalState() {
        return state == State.FINISHING
                || state == State.COMPLETED
                || state == State.ERROR
                || state == State.RELEASED;
    }

    private void fail(@NonNull Exception error) {
        if (state == State.ERROR || state == State.RELEASED) return;
        diagnostics.error("fatal error in state=" + state, error);
        if (!cancelOutput(OutputInvalidationReason.ERROR)) return;
        disableCameraEffects();
        releasePlayer();
        mainHandler.removeCallbacks(durationLimitRunnable);
        OutputGeneration generation = outputGeneration;
        if (generation != null) {
            outputExecutor.execute(() -> outputListener.onOutputError(generation.id, error));
        }
        setState(State.ERROR);
        logSummary("error");
        listener.onError(error);
        if (cameraStopPending || cameraController.stopSegment()) {
            cameraStopPending = true;
            cancelAfterCameraStops = true;
        } else {
            finishCancellation();
        }
    }

    private void deleteMediaFiles() {
        fileExecutor.execute(() -> {
            RoundVideoMp4Writer currentWriter = writer;
            File currentPreview = previewFile;
            if (currentWriter != null) {
                try {
                    currentWriter.closeForReplacement();
                } catch (IOException ignore) {
                }
                RoundVideoRemuxer.erase(currentWriter.getFile());
            }
            if (currentPreview != null) RoundVideoRemuxer.erase(currentPreview);
        });
    }

    private void finishCancellation() {
        if (cleanupStarted) return;
        cleanupStarted = true;
        logSummary("cancelled");
        deleteMediaFiles();
        cameraController.release();
        shutdownExecutors();
    }

    private void shutdownExecutors() {
        mainHandler.removeCallbacksAndMessages(null);
        fileExecutor.shutdown();
        outputExecutor.shutdown();
    }

    private void ensureNotCancelled() throws IOException {
        if (cancelRequested) throw new IOException("Round-video operation was cancelled");
    }

    private boolean cancelOutput(@NonNull OutputInvalidationReason reason) {
        synchronized (outputLock) {
            OutputGeneration generation = outputGeneration;
            if (generation != null && generation.completed) return false;
            cancelRequested = true;
            if (generation != null && !generation.invalidated) {
                generation.invalidated = true;
                diagnostics.log("output generation invalidated: id=" + generation.id
                        + ", reason=" + reason
                        + ", availableSize=" + generation.availableSize);
                outputExecutor.execute(() -> outputListener.onOutputInvalidated(
                        generation.id,
                        reason
                ));
            }
            return true;
        }
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void logSummary(@NonNull String terminalReason) {
        if (summaryLogged) return;
        summaryLogged = true;
        OutputGeneration generation = outputGeneration;
        diagnostics.log("session summary: terminal=" + terminalReason
                + ", state=" + state
                + ", durationMs=" + getRecordedDurationMs()
                + ", pauses=" + pauseCount
                + ", resumes=" + resumeCount
                + ", cameraSwitches=" + cameraSwitchCount
                + ", facing=" + activeFacing
                + ", cameraMode=" + activeCameraResolution
                + ", generation=" + (generation == null ? 0 : generation.id)
                + ", availableSize=" + (generation == null ? 0 : generation.availableSize));
    }

    private static long elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000L;
    }

    @NonNull
    private static Exception asException(@NonNull Exception error) {
        return error;
    }

    private static final class OutputGeneration {
        final long id;
        final File file;
        volatile long availableSize;
        volatile boolean invalidated;
        volatile boolean completed;

        OutputGeneration(long id, File file) {
            this.id = id;
            this.file = file;
        }
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("RoundVideoSession must be used from the main thread");
        }
    }
}
