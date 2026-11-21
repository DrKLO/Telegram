package org.telegram.utils.recorder.audio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one native Ogg/Opus encoder context. */
final class AudioRecorderNative {

    private long nativeHandle;
    private final AtomicBoolean released = new AtomicBoolean(false);

    private AudioRecorderNative(long nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    static @Nullable AudioRecorderNative create(
            @NonNull File outputFile,
            int sampleRate,
            @Nullable File prefixFile,
            long prefixStartMs,
            long prefixEndMs
    ) {
        long handle = nCreate(
                outputFile.getAbsolutePath(),
                sampleRate,
                prefixFile == null ? null : prefixFile.getAbsolutePath(),
                prefixStartMs,
                prefixEndMs
        );
        return handle == 0L ? null : new AudioRecorderNative(handle);
    }

    int writeFrame(@NonNull ByteBuffer frame, int length) {
        checkNotReleased();
        return nWriteFrame(nativeHandle, frame, length);
    }

    long flush() {
        checkNotReleased();
        return nFlush(nativeHandle);
    }

    boolean finish() {
        if (!released.compareAndSet(false, true)) {
            return false;
        }
        long handle = nativeHandle;
        nativeHandle = 0L;
        return handle != 0L && nFinish(handle);
    }

    void recycle() {
        if (!released.compareAndSet(false, true)) {
            return;
        }
        long handle = nativeHandle;
        nativeHandle = 0L;
        if (handle != 0L) {
            nDestroy(handle);
        }
    }

    static boolean cropOpusFile(
            @NonNull String source,
            @NonNull String destination,
            long startMs,
            long endMs
    ) {
        return nCropOpusFile(source, destination, startMs, endMs);
    }

    static @Nullable byte[] getWaveform(@NonNull String path) {
        return nGetWaveform(path);
    }

    private void checkNotReleased() {
        if (released.get()) {
            throw new IllegalStateException("Native audio recorder is already released");
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            recycle();
        } finally {
            super.finalize();
        }
    }

    private static native long nCreate(
            String outputPath,
            int sampleRate,
            @Nullable String prefixPath,
            long prefixStartMs,
            long prefixEndMs
    );

    private static native int nWriteFrame(long handle, ByteBuffer frame, int length);

    private static native long nFlush(long handle);

    private static native boolean nFinish(long handle);

    private static native void nDestroy(long handle);

    private static native boolean nCropOpusFile(
            String source,
            String destination,
            long startMs,
            long endMs
    );

    private static native byte[] nGetWaveform(String path);
}

