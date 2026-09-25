package org.telegram.utils.camera.roundvideo;

import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.util.concurrent.atomic.AtomicLong;

final class RoundVideoDiagnostics {
    private static final AtomicLong NEXT_SESSION_ID = new AtomicLong(1);

    private final long sessionId = NEXT_SESSION_ID.getAndIncrement();
    private final long startedAtMs = SystemClock.elapsedRealtime();

    long getSessionId() {
        return sessionId;
    }

    boolean isEnabled() {
        return BuildVars.LOGS_ENABLED;
    }

    void log(@NonNull String message) {
        FileLog.d(prefix() + message);
    }

    void error(@NonNull String message, @NonNull Throwable error) {
        FileLog.e(prefix() + message + ": " + error);
        FileLog.e(error);
    }

    @NonNull
    private String prefix() {
        return "RoundVideo[" + sessionId + "] t+"
                + (SystemClock.elapsedRealtime() - startedAtMs)
                + "ms [" + Thread.currentThread().getName() + "] ";
    }
}
