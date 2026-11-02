package org.telegram.utils.recorder.audio;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one native RNNoise state. */
final class AudioNoiseSuppressor {

    private long nativeHandle;
    private final AtomicBoolean released = new AtomicBoolean(false);

    private AudioNoiseSuppressor(long nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    static @Nullable AudioNoiseSuppressor create() {
        long handle = nCreate();
        return handle == 0L ? null : new AudioNoiseSuppressor(handle);
    }

    boolean process(@NonNull ByteBuffer pcm, int sampleCount) {
        if (released.get()) {
            throw new IllegalStateException("Native noise suppressor is already released");
        }
        return nProcess(nativeHandle, pcm, sampleCount);
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

    @Override
    protected void finalize() throws Throwable {
        try {
            recycle();
        } finally {
            super.finalize();
        }
    }

    private static native long nCreate();

    private static native boolean nProcess(long handle, ByteBuffer pcm, int sampleCount);

    private static native void nDestroy(long handle);
}
