package org.telegram.utils.recorder.audio;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records, previews, trims and incrementally exposes a Telegram-compatible Ogg/Opus voice file.
 *
 * <p>All public methods must be called on the main thread. UI callbacks are delivered on the
 * main thread. Output callbacks are serialized on a dedicated background thread. Audio focus,
 * playback interruption and Bluetooth routing belong to the host and must be configured before
 * {@link #start()}.</p>
 */
public final class AudioRecordingSession {

    /** High-level lifecycle state of the recording session. */
    public enum State {
        /** The session has been configured but has not started. */
        IDLE,
        /** Native Opus and AudioRecord resources are being prepared. */
        STARTING,
        /** Microphone samples are being recorded. */
        RECORDING,
        /** The active segment is being finalized. */
        PAUSING,
        /** The finalized recording is available for playback and trimming. */
        PREVIEWING,
        /** Capture is restarting after the retained range was selected. */
        RESUMING,
        /** The final Ogg file or trimmed replacement is being completed. */
        FINISHING,
        /** The final output is ready. */
        COMPLETED,
        /** An unrecoverable capture, encoding, playback or file error ended the session. */
        ERROR,
        /** Resources were released and unfinished output was cancelled. */
        RELEASED
    }

    /** Reason why a previously announced output generation must no longer be uploaded. */
    public enum OutputInvalidationReason {
        /** Pause, resume or trimming replaced the encoded stream with a new generation. */
        REBUILT,
        /** The user cancelled the recording. */
        CANCELLED,
        /** An unrecoverable error invalidated the output. */
        ERROR
    }

    /** Selects optional processing applied to captured PCM before waveform and Opus encoding. */
    public enum NoiseSuppression {
        /** Records microphone PCM without software noise suppression. */
        DISABLED,
        /** Applies the bundled RNNoise model to 48 kHz mono PCM. */
        RNNOISE
    }

    /** Receives lifecycle, waveform and playback events used by the host UI. */
    public interface Listener {
        /** Called whenever the immutable public state snapshot changes. */
        default void onStateChanged(@NonNull StateInfo stateInfo) {
        }

        /** Called at most about thirty times per second while microphone capture is active. */
        default void onRecordingProgress(long durationMs, double amplitude) {
        }

        /** Called when the configured maximum duration has been reached. */
        default void onMaximumDurationReached() {
        }

        /** Called after a playable paused snapshot and its waveform are ready. */
        default void onPreviewReady(
                long durationMs,
                long trimStartMs,
                long trimEndMs,
                @Nullable byte[] waveform
        ) {
        }

        /** Called when recorded-audio playback starts or pauses. */
        default void onPreviewPlaybackChanged(boolean playing) {
        }

        /** Called at most about thirty times per second while preview playback is active. */
        default void onPreviewProgress(long positionMs, long durationMs) {
        }

        /** Called instead of completion when the retained recording is shorter than the limit. */
        default void onRecordingTooShort(long durationMs) {
        }

        /** Called after the final output generation has been completed. */
        default void onCompleted(@NonNull Result result) {
        }

        /** Called after an unrecoverable capture, encoding, playback or file error. */
        default void onError(@NonNull Exception error) {
        }
    }

    /** Receives stable ranges of the growing Ogg output. */
    public interface OutputListener {
        /** Called before ranges from a new output generation are announced. */
        default void onOutputStarted(long outputId, @NonNull File file) {
        }

        /** Called when the specified immutable file range is ready to be read and uploaded. */
        default void onBytesAvailable(
                long outputId,
                @NonNull File file,
                long offset,
                long length
        ) {
        }

        /** Called when all ranges from an output generation have become obsolete. */
        default void onOutputInvalidated(
                long outputId,
                @NonNull OutputInvalidationReason reason
        ) {
        }

        /** Called after all finalized bytes of the retained recording have been written. */
        default void onOutputCompleted(
                long outputId,
                @NonNull File file,
                long finalSize,
                long durationMs
        ) {
        }

        /** Called when an output generation cannot be written. */
        default void onOutputError(long outputId, @NonNull Exception error) {
        }
    }

    /** Receives diagnostic messages without coupling the recorder to Telegram FileLog. */
    public interface Logger {
        /** Writes a normal diagnostic message. */
        void log(@NonNull String message);

        /** Writes a diagnostic error and its cause. */
        void error(@NonNull String message, @NonNull Throwable error);
    }

    /** Builds an immutable audio recording session configuration. */
    public static final class Builder {
        private final Context context;
        private File outputDirectory;
        private long minimumDurationMs = DEFAULT_MINIMUM_DURATION_MS;
        private long maximumDurationMs;
        private NoiseSuppression noiseSuppression = NoiseSuppression.DISABLED;
        private boolean previewPlaybackEnabled = true;
        private Listener listener;
        private OutputListener outputListener;
        private Logger logger = NO_OP_LOGGER;

        /** Creates a builder. The recorder stores only the application context when available. */
        public Builder(@NonNull Context context) {
            this.context = context;
        }

        /** Sets the directory used for output generations and temporary trim files. */
        public @NonNull Builder setOutputDirectory(@NonNull File directory) {
            outputDirectory = directory;
            return this;
        }

        /** Sets the minimum duration accepted by {@link #finish()}. */
        public @NonNull Builder setMinimumDurationMs(long durationMs) {
            minimumDurationMs = durationMs;
            return this;
        }

        /** Sets the maximum duration, or zero to disable the automatic limit. */
        public @NonNull Builder setMaximumDurationMs(long durationMs) {
            maximumDurationMs = durationMs;
            return this;
        }

        /** Sets software noise suppression applied before Opus encoding. */
        public @NonNull Builder setNoiseSuppression(@NonNull NoiseSuppression value) {
            if (value == null) {
                throw new IllegalArgumentException("Noise suppression mode is required");
            }
            noiseSuppression = value;
            return this;
        }

        /**
         * Enables the built-in preview player. Hosts that play the preview file with their own
         * player disable it to avoid preparing an unused decoder on every pause.
         */
        public @NonNull Builder setPreviewPlaybackEnabled(boolean enabled) {
            previewPlaybackEnabled = enabled;
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

        /** Sets the optional diagnostic logger. */
        public @NonNull Builder setLogger(@Nullable Logger value) {
            logger = value == null ? NO_OP_LOGGER : value;
            return this;
        }

        /** Validates the required options and creates the session. */
        public @NonNull AudioRecordingSession build() {
            if (minimumDurationMs < 0L) {
                throw new IllegalStateException("Minimum duration cannot be negative");
            }
            if (maximumDurationMs < 0L) {
                throw new IllegalStateException("Maximum duration cannot be negative");
            }
            if (maximumDurationMs > 0L && maximumDurationMs < minimumDurationMs) {
                throw new IllegalStateException("Maximum duration is shorter than minimum duration");
            }
            if (listener == null) {
                throw new IllegalStateException("Session listener is required");
            }
            if (outputListener == null) {
                throw new IllegalStateException("Output listener is required");
            }
            return new AudioRecordingSession(this);
        }
    }

    /** Immutable state used to drive recording and preview controls. */
    public static final class StateInfo {
        private final State state;
        private final long recordedDurationMs;
        private final long recordingStartedAtRealtimeMs;
        private final long minimumDurationMs;
        private final long maximumDurationMs;
        private final long trimStartMs;
        private final long trimEndMs;
        private final boolean previewPlaying;

        private StateInfo(
                @NonNull State state,
                long recordedDurationMs,
                long recordingStartedAtRealtimeMs,
                long minimumDurationMs,
                long maximumDurationMs,
                long trimStartMs,
                long trimEndMs,
                boolean previewPlaying
        ) {
            this.state = state;
            this.recordedDurationMs = recordedDurationMs;
            this.recordingStartedAtRealtimeMs = recordingStartedAtRealtimeMs;
            this.minimumDurationMs = minimumDurationMs;
            this.maximumDurationMs = maximumDurationMs;
            this.trimStartMs = trimStartMs;
            this.trimEndMs = trimEndMs;
            this.previewPlaying = previewPlaying;
        }

        /** Returns the high-level lifecycle state. */
        public @NonNull State getState() {
            return state;
        }

        /** Returns the exact duration derived from captured PCM sample count. */
        public long getRecordedDurationMs() {
            return recordedDurationMs;
        }

        /** Returns the elapsed-realtime origin of the active capture segment. */
        public long getRecordingStartedAtRealtimeMs() {
            return recordingStartedAtRealtimeMs;
        }

        /** Returns the minimum duration accepted by finish. */
        public long getMinimumDurationMs() {
            return minimumDurationMs;
        }

        /** Returns the hard duration limit, or zero when no limit is configured. */
        public long getMaximumDurationMs() {
            return maximumDurationMs;
        }

        /** Returns the selected preview range start. */
        public long getTrimStartMs() {
            return trimStartMs;
        }

        /** Returns the selected preview range end. */
        public long getTrimEndMs() {
            return trimEndMs;
        }

        /** Returns whether recorded-audio preview is playing. */
        public boolean isPreviewPlaying() {
            return previewPlaying;
        }

        /** Returns whether microphone capture can be paused now. */
        public boolean canPauseRecording() {
            return state == State.RECORDING;
        }

        /** Returns whether another capture segment can be started. */
        public boolean canResumeRecording() {
            return state == State.PREVIEWING
                    && (maximumDurationMs == 0L || trimEndMs - trimStartMs < maximumDurationMs);
        }

        /** Returns whether playback and trimming controls are available. */
        public boolean canControlPreview() {
            return state == State.PREVIEWING;
        }

        /** Returns whether the current recording can be finalized. */
        public boolean canFinish() {
            return state == State.RECORDING || state == State.PREVIEWING;
        }
    }

    /** Immutable description of the completed Telegram voice file. */
    public static final class Result {
        private final long outputId;
        private final File file;
        private final long durationMs;
        private final byte[] waveform;

        private Result(long outputId, @NonNull File file, long durationMs, @Nullable byte[] waveform) {
            this.outputId = outputId;
            this.file = file;
            this.durationMs = durationMs;
            this.waveform = waveform == null ? null : waveform.clone();
        }

        /** Returns the final output generation identifier. */
        public long getOutputId() {
            return outputId;
        }

        /** Returns the finalized Ogg/Opus file. */
        public @NonNull File getFile() {
            return file;
        }

        /** Returns the retained audio duration derived from PCM samples. */
        public long getDurationMs() {
            return durationMs;
        }

        /** Returns Telegram's packed five-bit waveform, or null when extraction failed. */
        public @Nullable byte[] getWaveform() {
            return waveform == null ? null : waveform.clone();
        }

        /** Returns the fixed capture sample rate. */
        public int getSampleRate() {
            return AudioCapture.SAMPLE_RATE;
        }

        /** Returns the output MIME type. */
        public @NonNull String getMimeType() {
            return MIME_TYPE;
        }
    }

    private static final Logger NO_OP_LOGGER = new Logger() {
        @Override
        public void log(@NonNull String message) {
        }

        @Override
        public void error(@NonNull String message, @NonNull Throwable error) {
        }
    };

    public static final String MIME_TYPE = "audio/ogg";
    public static final int SAMPLE_RATE = AudioCapture.SAMPLE_RATE;

    private static final long DEFAULT_MINIMUM_DURATION_MS = 700L;
    private static final long PREVIEW_PROGRESS_INTERVAL_MS = 32L;
    private static final AtomicLong NEXT_FILE_ID = new AtomicLong(1L);

    private final Context context;
    private final File outputDirectory;
    private final long minimumDurationMs;
    private final long maximumDurationMs;
    private final NoiseSuppression noiseSuppression;
    private final boolean previewPlaybackEnabled;
    private final Listener listener;
    private final OutputListener outputListener;
    private final AudioRecorderDiagnostics diagnostics;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "AudioRecorderFiles")
    );
    private final ExecutorService outputExecutor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "AudioRecorderOutput")
    );
    private final Object outputLock = new Object();
    private final ArrayList<File> temporaryFiles = new ArrayList<>();

    private State state = State.IDLE;
    private AudioCapture capture;
    private long captureToken;
    private long retainedSamples;
    private long activeSegmentSamples;
    private long recordingStartedAtRealtimeMs;
    private long trimStartMs;
    private long trimEndMs;
    private long nextOutputId = 1L;
    private boolean previewPlaying;
    private boolean finishAfterCaptureStops;
    private boolean maximumDurationDispatched;
    private boolean cleanupStarted;
    private boolean summaryLogged;
    private volatile OutputGeneration outputGeneration;
    private ExoPlayer player;
    private byte[] previewWaveform;
    private Result result;

    private final Runnable maximumDurationRunnable;
    private final Runnable previewProgressRunnable;

    private void onMaximumDurationTimer() {
        if (state != State.RECORDING || maximumDurationDispatched) return;
        maximumDurationDispatched = true;
        diagnostics.log("maximum duration reached: durationMs=" + getRecordedDurationMs());
        listener.onMaximumDurationReached();
        pauseRecording();
    }

    private void updatePreviewProgress() {
        if (state != State.PREVIEWING || player == null || !previewPlaying) return;
        long positionMs = player.getCurrentPosition();
        if (positionMs < trimStartMs || positionMs >= trimEndMs) {
            player.seekTo(trimStartMs);
            positionMs = trimStartMs;
        }
        listener.onPreviewProgress(positionMs, getRecordedDurationMs());
        mainHandler.postDelayed(previewProgressRunnable, PREVIEW_PROGRESS_INTERVAL_MS);
    }

    private AudioRecordingSession(@NonNull Builder builder) {
        requireMainThread();
        Context applicationContext = builder.context.getApplicationContext();
        context = applicationContext == null ? builder.context : applicationContext;
        outputDirectory = builder.outputDirectory == null
                ? context.getCacheDir()
                : builder.outputDirectory;
        minimumDurationMs = builder.minimumDurationMs;
        maximumDurationMs = builder.maximumDurationMs;
        noiseSuppression = builder.noiseSuppression;
        previewPlaybackEnabled = builder.previewPlaybackEnabled;
        listener = builder.listener;
        outputListener = builder.outputListener;
        diagnostics = new AudioRecorderDiagnostics(builder.logger);
        maximumDurationRunnable = this::onMaximumDurationTimer;
        previewProgressRunnable = this::updatePreviewProgress;
        diagnostics.log("session created: device=" + Build.MANUFACTURER + " " + Build.MODEL
                + ", sdk=" + Build.VERSION.SDK_INT
                + ", sampleRate=" + SAMPLE_RATE
                + ", noiseSuppression=" + noiseSuppression
                + ", minimumDurationMs=" + minimumDurationMs
                + ", maximumDurationMs=" + maximumDurationMs);
    }

    /** Starts microphone capture after the host has configured audio focus and routing. */
    @MainThread
    public boolean start() {
        requireMainThread();
        if (state != State.IDLE) return false;
        if (!ensureOutputDirectory()) return false;
        diagnostics.log("start requested");
        createOutputGeneration();
        setState(State.STARTING);
        startCapture(null, 0L, -1L);
        return true;
    }

    /** Finalizes the active segment and enters recorded-audio preview. */
    @MainThread
    public boolean pauseRecording() {
        requireMainThread();
        if (state != State.RECORDING || capture == null) return false;
        diagnostics.log("pause requested: durationMs=" + getRecordedDurationMs());
        mainHandler.removeCallbacks(maximumDurationRunnable);
        setState(State.PAUSING);
        capture.stop();
        return true;
    }

    /** Applies the selected trim range and continues microphone capture. */
    @MainThread
    public boolean resumeRecording() {
        requireMainThread();
        if (state != State.PREVIEWING) return false;
        long retainedDurationMs = trimEndMs - trimStartMs;
        if (maximumDurationMs > 0L && retainedDurationMs >= maximumDurationMs) return false;
        diagnostics.log("resume requested: trim=" + trimStartMs + ".." + trimEndMs
                + ", sourceDurationMs=" + getRecordedDurationMs());
        releasePlayer();
        setState(State.RESUMING);
        rebuildPrefixAndResume();
        return true;
    }

    /** Updates the selected range used by preview, resume and finish. */
    @MainThread
    public boolean setTrimRange(long startMs, long endMs) {
        requireMainThread();
        if (state != State.PREVIEWING) return false;
        long durationMs = getRecordedDurationMs();
        long clampedStart = clamp(startMs, 0L, durationMs);
        long clampedEnd = clamp(endMs, clampedStart, durationMs);
        if (clampedEnd - clampedStart < Math.min(minimumDurationMs, durationMs)) return false;
        trimStartMs = clampedStart;
        trimEndMs = clampedEnd;
        if (player != null) player.seekTo(trimStartMs);
        listener.onPreviewProgress(trimStartMs, durationMs);
        notifyState();
        return true;
    }

    /** Starts or resumes playback inside the selected trim range. */
    @MainThread
    public boolean playPreview() {
        requireMainThread();
        if (state != State.PREVIEWING || player == null) return false;
        long positionMs = player.getCurrentPosition();
        if (positionMs < trimStartMs || positionMs >= trimEndMs) player.seekTo(trimStartMs);
        player.play();
        updatePreviewPlaying(true);
        return true;
    }

    /** Pauses recorded-audio playback without leaving preview state. */
    @MainThread
    public boolean pausePreview() {
        requireMainThread();
        if (state != State.PREVIEWING || player == null) return false;
        player.pause();
        updatePreviewPlaying(false);
        listener.onPreviewProgress(player.getCurrentPosition(), getRecordedDurationMs());
        return true;
    }

    /** Seeks recorded-audio playback inside the selected trim range. */
    @MainThread
    public boolean seekPreview(long positionMs) {
        requireMainThread();
        if (state != State.PREVIEWING || player == null) return false;
        long position = clamp(positionMs, trimStartMs, Math.max(trimStartMs, trimEndMs - 1L));
        player.seekTo(position);
        listener.onPreviewProgress(position, getRecordedDurationMs());
        return true;
    }

    /** Sets preview playback volume in the inclusive range from zero to one. */
    @MainThread
    public boolean setPreviewVolume(float volume) {
        requireMainThread();
        if (state != State.PREVIEWING || player == null) return false;
        player.setVolume(clamp(volume, 0f, 1f));
        return true;
    }

    /** Finalizes the active or selected recording. */
    @MainThread
    public boolean finish() {
        requireMainThread();
        if (state != State.RECORDING && state != State.PREVIEWING) return false;
        diagnostics.log("finish requested: state=" + state
                + ", durationMs=" + getRecordedDurationMs()
                + ", trim=" + trimStartMs + ".." + trimEndMs);
        if (state == State.RECORDING) {
            finishAfterCaptureStops = true;
            mainHandler.removeCallbacks(maximumDurationRunnable);
            setState(State.FINISHING);
            capture.stop();
        } else {
            releasePlayer();
            setState(State.FINISHING);
            finishFromPreview();
        }
        return true;
    }

    /**
     * Transfers ownership of the untrimmed preview file to the host without dispatching
     * {@link Listener#onCompleted(Result)}. This is intended for hosts that already exposed the
     * preview file through their existing UI and are about to send that exact file themselves.
     */
    @MainThread
    public @Nullable Result detachPreviewOutput() {
        requireMainThread();
        if (state != State.PREVIEWING || outputGeneration == null) return null;
        if (trimStartMs != 0L || trimEndMs != getRecordedDurationMs()) return null;

        releasePlayer();
        OutputGeneration generation = outputGeneration;
        long durationMs = getRecordedDurationMs();
        long finalSize = generation.file.length();
        announceBytes(generation, finalSize);
        synchronized (outputLock) {
            if (!generation.valid || generation.completed) return null;
            generation.completed = true;
            outputExecutor.execute(() -> outputListener.onOutputCompleted(
                    generation.id,
                    generation.file,
                    finalSize,
                    durationMs
            ));
        }
        result = new Result(
                generation.id,
                generation.file,
                durationMs,
                cloneOrNull(previewWaveform)
        );
        setState(State.COMPLETED);
        cleanupUnfinishedFiles();
        shutdownExecutors();
        logSummary("detached_preview");
        return result;
    }

    /** Cancels unfinished output, invalidates upload data and deletes temporary files. */
    @MainThread
    public boolean cancel() {
        requireMainThread();
        if (state == State.RELEASED || state == State.COMPLETED) return false;
        diagnostics.log("cancel requested: state=" + state);
        removeScheduledCallbacks();
        releasePlayer();
        invalidateCurrentOutput(OutputInvalidationReason.CANCELLED);
        setState(State.RELEASED);
        if (capture != null) {
            capture.stop();
        } else {
            cleanupUnfinishedFiles();
            shutdownExecutors();
        }
        return true;
    }

    /** Releases preview and capture resources. Unfinished recordings are cancelled. */
    @MainThread
    public void release() {
        requireMainThread();
        if (state == State.RELEASED) return;
        if (state != State.COMPLETED) {
            cancel();
            return;
        }
        releasePlayer();
        cleanupUnfinishedFiles();
        setState(State.RELEASED);
        logSummary("released");
    }

    /** Returns the latest immutable state snapshot. */
    @MainThread
    public @NonNull StateInfo getStateInfo() {
        requireMainThread();
        return createStateInfo();
    }

    /** Returns the current finalized preview file, or null before the first pause. */
    @MainThread
    public @Nullable File getPreviewFile() {
        requireMainThread();
        OutputGeneration generation = outputGeneration;
        return state == State.PREVIEWING && generation != null ? generation.file : null;
    }

    /** Returns the completed result, or null before successful completion. */
    @MainThread
    public @Nullable Result getResult() {
        requireMainThread();
        return result;
    }

    /** Returns the exact duration derived from retained and actively captured PCM samples. */
    @MainThread
    public long getRecordedDurationMs() {
        requireMainThread();
        return samplesToMs(retainedSamples + activeSegmentSamples);
    }

    private void startCapture(@Nullable File prefixFile, long prefixStartMs, long prefixEndMs) {
        final long token = ++captureToken;
        final OutputGeneration generation = outputGeneration;
        if (generation == null) {
            fail(new IllegalStateException("Output generation is missing"));
            return;
        }
        AudioCapture nextCapture = new AudioCapture(
                generation.file,
                prefixFile,
                prefixStartMs,
                prefixEndMs,
                noiseSuppression,
                diagnostics,
                new AudioCapture.Callback() {
                    @Override
                    public void onStarted() {
                        mainHandler.post(() -> onCaptureStarted(token));
                    }

                    @Override
                    public void onProgress(long capturedSamples, double amplitude) {
                        mainHandler.post(() -> onCaptureProgress(token, capturedSamples, amplitude));
                    }

                    @Override
                    public void onOutputCommitted(long committedFileSize) {
                        announceBytes(generation, committedFileSize);
                    }

                    @Override
                    public void onStopped(long capturedSamples, long finalFileSize) {
                        announceBytes(generation, finalFileSize);
                        mainHandler.post(() -> onCaptureStopped(token, capturedSamples));
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        mainHandler.post(() -> onCaptureError(token, error));
                    }
                }
        );
        capture = nextCapture;
        activeSegmentSamples = 0L;
        nextCapture.start();
    }

    private void onCaptureStarted(long token) {
        if (token != captureToken || isTerminalState()) return;
        recordingStartedAtRealtimeMs = SystemClock.elapsedRealtime();
        maximumDurationDispatched = false;
        setState(State.RECORDING);
        scheduleMaximumDuration();
        diagnostics.log("recording started: retainedDurationMs=" + samplesToMs(retainedSamples));
    }

    private void onCaptureProgress(long token, long capturedSamples, double amplitude) {
        if (token != captureToken || state != State.RECORDING) return;
        activeSegmentSamples = capturedSamples;
        long durationMs = getRecordedDurationMs();
        if (maximumDurationMs > 0L && durationMs >= maximumDurationMs) {
            maximumDurationRunnable.run();
            return;
        }
        if (amplitude >= 0d) listener.onRecordingProgress(durationMs, amplitude);
    }

    private void onCaptureStopped(long token, long capturedSamples) {
        if (token != captureToken) return;
        capture = null;
        retainedSamples += capturedSamples;
        activeSegmentSamples = 0L;
        recordingStartedAtRealtimeMs = 0L;
        mainHandler.removeCallbacks(maximumDurationRunnable);
        if (state == State.RELEASED) {
            cleanupUnfinishedFiles();
            shutdownExecutors();
            logSummary("cancelled");
            return;
        }
        if (state == State.ERROR) {
            cleanupUnfinishedFiles();
            shutdownExecutors();
            return;
        }
        if (finishAfterCaptureStops) {
            finishAfterCaptureStops = false;
            finishFinalGeneration();
        } else if (state == State.PAUSING) {
            preparePreview();
        }
    }

    private void onCaptureError(long token, @NonNull Exception error) {
        if (token != captureToken) return;
        capture = null;
        if (state == State.RELEASED || state == State.ERROR) {
            // The session already ended; the capture only reports how it shut down.
            diagnostics.error("capture error after session end", error);
            cleanupUnfinishedFiles();
            shutdownExecutors();
            return;
        }
        fail(error);
    }

    private void preparePreview() {
        OutputGeneration generation = outputGeneration;
        if (generation == null) {
            fail(new IllegalStateException("Output generation is missing"));
            return;
        }
        long durationMs = getRecordedDurationMs();
        trimStartMs = 0L;
        trimEndMs = durationMs;
        fileExecutor.execute(() -> {
            byte[] waveform = null;
            try {
                waveform = AudioRecorderNative.getWaveform(generation.file.getAbsolutePath());
            } catch (Throwable error) {
                diagnostics.error("waveform extraction failed", error);
            }
            byte[] readyWaveform = waveform;
            mainHandler.post(() -> enterPreview(generation.file, readyWaveform));
        });
    }

    private void enterPreview(@NonNull File file, @Nullable byte[] waveform) {
        if (state != State.PAUSING) return;
        previewWaveform = waveform;
        try {
            releasePlayer();
            if (previewPlaybackEnabled) {
                player = new ExoPlayer.Builder(context).build();
                player.addListener(playerListener);
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
                player.prepare();
            }
            setState(State.PREVIEWING);
            listener.onPreviewReady(
                    getRecordedDurationMs(),
                    trimStartMs,
                    trimEndMs,
                    cloneOrNull(previewWaveform)
            );
        } catch (Throwable error) {
            fail(asException(error));
        }
    }

    private void rebuildPrefixAndResume() {
        OutputGeneration previous = outputGeneration;
        if (previous == null) {
            fail(new IllegalStateException("Output generation is missing"));
            return;
        }
        long selectedStartMs = trimStartMs;
        long selectedEndMs = trimEndMs;
        boolean wholeSource = selectedEndMs >= samplesToMs(retainedSamples);
        invalidateCurrentOutput(OutputInvalidationReason.REBUILT);
        retainedSamples = msToSamples(selectedEndMs - selectedStartMs);
        trimStartMs = 0L;
        trimEndMs = samplesToMs(retainedSamples);
        createOutputGeneration();
        diagnostics.log("resume prefix selected: source=" + previous.file.getName()
                + ", trim=" + selectedStartMs + ".." + selectedEndMs);
        // The encoder re-encodes the selected range itself while the microphone already records.
        startCapture(previous.file, selectedStartMs, wholeSource ? -1L : selectedEndMs);
    }

    private void finishFromPreview() {
        OutputGeneration previous = outputGeneration;
        if (previous == null) {
            fail(new IllegalStateException("Output generation is missing"));
            return;
        }
        long sourceDurationMs = samplesToMs(retainedSamples);
        if (trimStartMs == 0L && trimEndMs == sourceDurationMs) {
            finishFinalGeneration();
            return;
        }

        final long selectedStartMs = trimStartMs;
        final long selectedEndMs = trimEndMs;
        invalidateCurrentOutput(OutputInvalidationReason.REBUILT);
        OutputGeneration replacement = createOutputGeneration();
        fileExecutor.execute(() -> {
            long trimStartedAtMs = SystemClock.elapsedRealtime();
            try {
                boolean success = AudioRecorderNative.cropOpusFile(
                        previous.file.getAbsolutePath(),
                        replacement.file.getAbsolutePath(),
                        selectedStartMs,
                        selectedEndMs
                );
                if (!success) throw new IllegalStateException("Unable to trim final Opus output");
                diagnostics.log("final trim completed: source=" + previous.file.getName()
                        + ", output=" + replacement.file.getName()
                        + ", trim=" + selectedStartMs + ".." + selectedEndMs
                        + ", bytes=" + replacement.file.length()
                        + ", elapsedMs=" + (SystemClock.elapsedRealtime() - trimStartedAtMs));
                announceBytes(replacement, replacement.file.length());
                mainHandler.post(() -> {
                    if (state != State.FINISHING) return;
                    retainedSamples = msToSamples(selectedEndMs - selectedStartMs);
                    trimStartMs = 0L;
                    trimEndMs = samplesToMs(retainedSamples);
                    finishFinalGeneration();
                });
            } catch (Throwable error) {
                mainHandler.post(() -> fail(asException(error)));
            }
        });
    }

    private void finishFinalGeneration() {
        if (state != State.FINISHING) return;
        OutputGeneration generation = outputGeneration;
        if (generation == null) {
            fail(new IllegalStateException("Output generation is missing"));
            return;
        }
        long durationMs = getRecordedDurationMs();
        if (durationMs < minimumDurationMs) {
            diagnostics.log("recording rejected as too short: durationMs=" + durationMs);
            listener.onRecordingTooShort(durationMs);
            invalidateCurrentOutput(OutputInvalidationReason.CANCELLED);
            setState(State.RELEASED);
            cleanupUnfinishedFiles();
            shutdownExecutors();
            logSummary("too_short");
            return;
        }
        fileExecutor.execute(() -> {
            byte[] waveform = null;
            try {
                waveform = AudioRecorderNative.getWaveform(generation.file.getAbsolutePath());
            } catch (Throwable error) {
                diagnostics.error("final waveform extraction failed", error);
            }
            byte[] finalWaveform = waveform;
            mainHandler.post(() -> complete(generation, durationMs, finalWaveform));
        });
    }

    private void complete(
            @NonNull OutputGeneration generation,
            long durationMs,
            @Nullable byte[] waveform
    ) {
        if (state != State.FINISHING) return;
        long finalSize = generation.file.length();
        announceBytes(generation, finalSize);
        synchronized (outputLock) {
            if (!generation.valid || generation.completed) return;
            generation.completed = true;
            outputExecutor.execute(() -> outputListener.onOutputCompleted(
                    generation.id,
                    generation.file,
                    finalSize,
                    durationMs
            ));
        }
        result = new Result(generation.id, generation.file, durationMs, waveform);
        setState(State.COMPLETED);
        listener.onCompleted(result);
        cleanupUnfinishedFiles();
        shutdownExecutors();
        logSummary("completed");
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            fail(error);
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updatePreviewPlaying(isPlaying);
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_ENDED && state == State.PREVIEWING && player != null) {
                player.seekTo(trimStartMs);
                player.pause();
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

    private void releasePlayer() {
        mainHandler.removeCallbacks(previewProgressRunnable);
        previewPlaying = false;
        if (player != null) {
            player.removeListener(playerListener);
            player.release();
            player = null;
        }
    }

    private void scheduleMaximumDuration() {
        mainHandler.removeCallbacks(maximumDurationRunnable);
        if (maximumDurationMs == 0L) return;
        long remainingMs = maximumDurationMs - getRecordedDurationMs();
        if (remainingMs <= 0L) {
            mainHandler.post(maximumDurationRunnable);
        } else {
            mainHandler.postDelayed(maximumDurationRunnable, remainingMs);
        }
    }

    private boolean ensureOutputDirectory() {
        if ((outputDirectory.isDirectory() || outputDirectory.mkdirs()) && outputDirectory.canWrite()) {
            return true;
        }
        fail(new IllegalStateException("Output directory is not writable: " + outputDirectory));
        return false;
    }

    private @NonNull OutputGeneration createOutputGeneration() {
        File file = createTemporaryFile("voice", ".ogg");
        OutputGeneration generation = new OutputGeneration(nextOutputId++, file);
        synchronized (outputLock) {
            outputGeneration = generation;
            outputExecutor.execute(() -> outputListener.onOutputStarted(generation.id, file));
        }
        diagnostics.log("output generation started: id=" + generation.id
                + ", file=" + file.getName());
        return generation;
    }

    private @NonNull File createTemporaryFile(@NonNull String role, @NonNull String suffix) {
        File file = new File(
                outputDirectory,
                "audio_recording_" + role + "_" + System.currentTimeMillis() + "_"
                        + NEXT_FILE_ID.getAndIncrement() + suffix
        );
        synchronized (temporaryFiles) {
            temporaryFiles.add(file);
        }
        return file;
    }

    private void announceBytes(@NonNull OutputGeneration generation, long fileSize) {
        if (fileSize <= 0L) return;
        final long offset;
        final long length;
        synchronized (outputLock) {
            if (!generation.valid || generation.completed || fileSize <= generation.announcedBytes) {
                return;
            }
            offset = generation.announcedBytes;
            length = fileSize - offset;
            generation.announcedBytes = fileSize;
            outputExecutor.execute(() -> outputListener.onBytesAvailable(
                    generation.id,
                    generation.file,
                    offset,
                    length
            ));
        }
    }

    private void invalidateCurrentOutput(@NonNull OutputInvalidationReason reason) {
        OutputGeneration generation;
        synchronized (outputLock) {
            generation = outputGeneration;
            if (generation == null || !generation.valid || generation.completed) return;
            generation.valid = false;
            outputExecutor.execute(() -> outputListener.onOutputInvalidated(generation.id, reason));
        }
        diagnostics.log("output generation invalidated: id=" + generation.id + ", reason=" + reason);
    }

    private void cleanupUnfinishedFiles() {
        if (cleanupStarted) return;
        cleanupStarted = true;
        File retainedResult = result == null ? null : result.file;
        // Runs after any file task in flight, so files that task creates are removed as well.
        Runnable deletion = () -> deleteTemporaryFiles(retainedResult);
        try {
            fileExecutor.execute(deletion);
        } catch (RejectedExecutionException ignore) {
            deletion.run();
        }
    }

    private void deleteTemporaryFiles(@Nullable File retainedResult) {
        synchronized (temporaryFiles) {
            for (int index = 0; index < temporaryFiles.size(); index++) {
                File file = temporaryFiles.get(index);
                if (retainedResult == null || !retainedResult.equals(file)) {
                    if (file.exists() && !file.delete()) {
                        diagnostics.log("temporary file deletion failed: " + file.getName());
                    }
                }
            }
            temporaryFiles.clear();
        }
    }

    private void removeScheduledCallbacks() {
        // Capture callbacks stay queued: they are filtered by token and finish the cleanup.
        mainHandler.removeCallbacks(maximumDurationRunnable);
        mainHandler.removeCallbacks(previewProgressRunnable);
    }

    private void fail(@NonNull Exception error) {
        if (state == State.ERROR || state == State.RELEASED || state == State.COMPLETED) return;
        diagnostics.error("fatal error in state=" + state, error);
        removeScheduledCallbacks();
        releasePlayer();
        invalidateCurrentOutput(OutputInvalidationReason.ERROR);
        setState(State.ERROR);
        OutputGeneration generation = outputGeneration;
        if (generation != null) {
            outputExecutor.execute(() -> outputListener.onOutputError(generation.id, error));
        }
        listener.onError(error);
        if (capture != null) {
            capture.stop();
        } else {
            cleanupUnfinishedFiles();
            shutdownExecutors();
        }
        logSummary("error");
    }

    private void setState(@NonNull State newState) {
        if (state == newState) return;
        State previous = state;
        state = newState;
        diagnostics.log("state: " + previous + " -> " + newState
                + ", durationMs=" + samplesToMs(retainedSamples + activeSegmentSamples));
        notifyState();
    }

    private void notifyState() {
        listener.onStateChanged(createStateInfo());
    }

    private @NonNull StateInfo createStateInfo() {
        return new StateInfo(
                state,
                samplesToMs(retainedSamples + activeSegmentSamples),
                recordingStartedAtRealtimeMs,
                minimumDurationMs,
                maximumDurationMs,
                trimStartMs,
                trimEndMs,
                previewPlaying
        );
    }

    private boolean isTerminalState() {
        return state == State.ERROR || state == State.RELEASED || state == State.COMPLETED;
    }

    private void shutdownExecutors() {
        fileExecutor.shutdown();
        outputExecutor.shutdown();
    }

    private void logSummary(@NonNull String terminal) {
        if (summaryLogged) return;
        summaryLogged = true;
        diagnostics.log("session summary: terminal=" + terminal
                + ", durationMs=" + samplesToMs(retainedSamples + activeSegmentSamples)
                + ", generations=" + (nextOutputId - 1L));
    }

    private static long samplesToMs(long samples) {
        return samples * 1000L / AudioCapture.SAMPLE_RATE;
    }

    private static long msToSamples(long durationMs) {
        return durationMs * AudioCapture.SAMPLE_RATE / 1000L;
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static @Nullable byte[] cloneOrNull(@Nullable byte[] value) {
        return value == null ? null : value.clone();
    }

    private static @NonNull Exception asException(@NonNull Throwable error) {
        return error instanceof Exception ? (Exception) error : new RuntimeException(error);
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("AudioRecordingSession must be used on the main thread");
        }
    }

    private static final class OutputGeneration {
        final long id;
        final File file;
        long announcedBytes;
        boolean valid = true;
        boolean completed;

        OutputGeneration(long id, @NonNull File file) {
            this.id = id;
            this.file = file;
        }
    }
}

