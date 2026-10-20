package org.telegram.utils.recorder.audio;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicLong;

final class AudioRecorderDiagnostics {

    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private final long id = NEXT_ID.getAndIncrement();
    private final long startedAtMs = SystemClock.elapsedRealtime();
    private final AudioRecordingSession.Logger logger;

    AudioRecorderDiagnostics(@NonNull AudioRecordingSession.Logger logger) {
        this.logger = logger;
    }

    void log(@NonNull String message) {
        logger.log("AudioRecorder[" + id + "] t+"
                + (SystemClock.elapsedRealtime() - startedAtMs) + "ms ["
                + Thread.currentThread().getName() + "] " + message);
    }

    void error(@NonNull String message, @NonNull Throwable error) {
        logger.error("AudioRecorder[" + id + "] t+"
                + (SystemClock.elapsedRealtime() - startedAtMs) + "ms ["
                + Thread.currentThread().getName() + "] " + message, error);
    }
}

