package org.telegram.messenger;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.utils.recorder.audio.AudioRecordingSession;
import org.telegram.utils.settings.SharedSettings;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

/** Bridges the standalone recorder to Telegram's existing voice-recording lifecycle. */
final class AudioRecorderV2Controller {

    interface Listener {
        void onOutputStarted(@NonNull File file);

        void onOutputInvalidated(@NonNull File file);

        void onRecordingStarted();

        void onRecordingResumed();

        void onRecordingProgress(long durationMs, double amplitude);

        void onPreviewReady(
                long durationMs,
                @NonNull File file,
                @Nullable byte[] waveform
        );

        void onCompleted(@NonNull AudioRecordingSession.Result result);

        void onRecordingTooShort(long durationMs);

        void onError(@NonNull Exception error);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final AudioRecordingSession session;
    private final Object outputLock = new Object();
    private final HashMap<Long, File> outputFiles = new HashMap<>();
    private final HashSet<Long> deliveredOutputIds = new HashSet<>();

    private AudioRecordingSession.State previousState = AudioRecordingSession.State.IDLE;
    private boolean finishRequested;
    private boolean released;

    AudioRecorderV2Controller(
            @NonNull Context context,
            @NonNull File outputDirectory,
            @NonNull Listener listener
    ) {
        this.listener = listener;
        session = new AudioRecordingSession.Builder(context)
                .setOutputDirectory(outputDirectory)
                .setMinimumDurationMs(700L)
                .setMaximumDurationMs(0L)
                .setNoiseSuppression(SharedSettings.audioRecorderNoiseSuppression.get())
                .setPreviewPlaybackEnabled(false)
                .setListener(new AudioRecordingSession.Listener() {
                    @Override
                    public void onStateChanged(@NonNull AudioRecordingSession.StateInfo stateInfo) {
                        AudioRecordingSession.State state = stateInfo.getState();
                        AudioRecordingSession.State previous = previousState;
                        previousState = state;
                        if (state == AudioRecordingSession.State.RECORDING) {
                            if (!finishRequested
                                    && previous == AudioRecordingSession.State.STARTING) {
                                listener.onRecordingStarted();
                            } else if (!finishRequested
                                    && previous == AudioRecordingSession.State.RESUMING) {
                                listener.onRecordingResumed();
                            }
                            completePendingFinish(state);
                        }
                    }

                    @Override
                    public void onRecordingProgress(long durationMs, double amplitude) {
                        listener.onRecordingProgress(durationMs, amplitude);
                    }

                    @Override
                    public void onPreviewReady(
                            long durationMs,
                            long trimStartMs,
                            long trimEndMs,
                            @Nullable byte[] waveform
                    ) {
                        if (finishRequested) {
                            finishRequested = false;
                            session.finish();
                            return;
                        }
                        File file = session.getPreviewFile();
                        if (file == null) {
                            listener.onError(new IllegalStateException("Preview file is unavailable"));
                            return;
                        }
                        listener.onPreviewReady(durationMs, file, waveform);
                    }

                    @Override
                    public void onRecordingTooShort(long durationMs) {
                        listener.onRecordingTooShort(durationMs);
                    }

                    @Override
                    public void onCompleted(@NonNull AudioRecordingSession.Result result) {
                        listener.onCompleted(result);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        listener.onError(error);
                    }
                })
                .setOutputListener(new AudioRecordingSession.OutputListener() {
                    @Override
                    public void onOutputStarted(long outputId, @NonNull File file) {
                        synchronized (outputLock) {
                            outputFiles.put(outputId, file);
                        }
                        mainHandler.post(() -> {
                            if (released) return;
                            deliveredOutputIds.add(outputId);
                            listener.onOutputStarted(file);
                        });
                    }

                    @Override
                    public void onOutputInvalidated(
                            long outputId,
                            @NonNull AudioRecordingSession.OutputInvalidationReason reason
                    ) {
                        File file;
                        synchronized (outputLock) {
                            file = outputFiles.remove(outputId);
                        }
                        if (file != null) {
                            mainHandler.post(() -> {
                                if (deliveredOutputIds.remove(outputId)) {
                                    listener.onOutputInvalidated(file);
                                }
                            });
                        }
                    }

                    @Override
                    public void onOutputCompleted(
                            long outputId,
                            @NonNull File file,
                            long finalSize,
                            long durationMs
                    ) {
                        synchronized (outputLock) {
                            outputFiles.put(outputId, file);
                        }
                    }
                })
                .setLogger(new AudioRecordingSession.Logger() {
                    @Override
                    public void log(@NonNull String message) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("AudioRecorderV2 " + message);
                        }
                    }

                    @Override
                    public void error(@NonNull String message, @NonNull Throwable error) {
                        FileLog.e("AudioRecorderV2 " + message, error);
                    }
                })
                .build();
    }

    boolean start() {
        return session.start();
    }

    boolean togglePause() {
        AudioRecordingSession.State state = session.getStateInfo().getState();
        if (state == AudioRecordingSession.State.RECORDING) {
            return session.pauseRecording();
        }
        if (state == AudioRecordingSession.State.PREVIEWING) {
            return session.resumeRecording();
        }
        return false;
    }

    boolean setTrimRange(long startMs, long endMs) {
        return session.setTrimRange(startMs, endMs);
    }

    void finishWhenReady() {
        finishRequested = true;
        completePendingFinish(session.getStateInfo().getState());
    }

    void cancel() {
        finishRequested = false;
        session.cancel();
    }

    void release() {
        session.release();
        released = true;
    }

    boolean detachPreviewOutput() {
        AudioRecordingSession.Result result = session.detachPreviewOutput();
        if (result == null) return false;
        session.release();
        released = true;
        return true;
    }

    boolean isPreviewing() {
        return session.getStateInfo().getState() == AudioRecordingSession.State.PREVIEWING;
    }

    private void completePendingFinish(@NonNull AudioRecordingSession.State state) {
        if (!finishRequested) return;
        if (state == AudioRecordingSession.State.RECORDING
                || state == AudioRecordingSession.State.PREVIEWING) {
            finishRequested = false;
            session.finish();
        }
    }
}
