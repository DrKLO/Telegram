package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.os.Build;
import android.os.Trace;

import org.telegram.messenger.AnimatedFileDrawableStream;
import org.telegram.messenger.BuildConfig;

import java.util.concurrent.atomic.AtomicBoolean;

public class AnimatedFileNative {

    private final int[] mMetaData;
    private long mNativePtr;
    private final AtomicBoolean mRecycled = new AtomicBoolean(false);

    private AnimatedFileNative(long nativePtr, int[] metaData) {
        mNativePtr = nativePtr;
        mMetaData = metaData;
    }

    public static AnimatedFileNative createDecoderFrom(String src, int[] params, int account, long streamFileSize, AnimatedFileDrawableStream readCallback, boolean preview) {
        long ptr = createDecoder(src, params, account, streamFileSize, readCallback, preview);
        if (ptr == 0) {
            return null;
        }
        return new AnimatedFileNative(ptr, params);
    }

    public void stopDecoder() {
        checkNotDestroyed();
        stopDecoder(mNativePtr);
    }

    public int getVideoFrame(Bitmap bitmap, boolean preview, float startTimeSeconds, float endTimeSeconds, boolean loop) {
        checkNotDestroyed();
        return getVideoFrame(mNativePtr, bitmap, mMetaData, preview, startTimeSeconds, endTimeSeconds, loop);
    }

    public void seekToMs(long ms, boolean precise) {
        checkNotDestroyed();
        seekToMs(mNativePtr, ms, mMetaData, precise);
    }

    public int getFrameAtTime(long ms, Bitmap bitmap) {
        checkNotDestroyed();
        return getFrameAtTime(mNativePtr, ms, bitmap, mMetaData);
    }

    public void prepareToSeek() {
        checkNotDestroyed();
        prepareToSeek(mNativePtr);
    }

    public boolean isDestroyed() {
        return mRecycled.get();
    }

    public void recycle() {
        if (mRecycled.compareAndSet(false, true)) {
            long ptr = mNativePtr;
            mNativePtr = 0;
            if (ptr != 0) {
                destroyDecoder(ptr);
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (!mRecycled.get()) {
                recycle();
            }
        } finally {
            super.finalize();
        }
    }

    private void checkNotDestroyed() {
        if (mRecycled.get()) {
            if (BuildConfig.DEBUG_PRIVATE_VERSION) {
                throw new IllegalStateException("Called method on a destroyed AnimatedFileNative instance");
            }
        }
    }

    private static long createDecoder(String src, int[] params, int account, long streamFileSize, AnimatedFileDrawableStream readCallback, boolean preview) {
        Trace.beginSection("AnimatedFileNative#createDecoder");
        try {
            return nCreateDecoder(src, params, account, streamFileSize, readCallback, preview);
        } finally {
            Trace.endSection();
        }
    }

    private static void destroyDecoder(long ptr) {
        Trace.beginSection("AnimatedFileNative#destroyDecoder");
        try {
            nDestroyDecoder(ptr);
        } finally {
            Trace.endSection();
        }
    }

    private static void stopDecoder(long ptr) {
        Trace.beginSection("AnimatedFileNative#stopDecoder");
        try {
            nStopDecoder(ptr);
        } finally {
            Trace.endSection();
        }
    }

    private static int getVideoFrame(long ptr, Bitmap bitmap, int[] params, boolean preview, float startTimeSeconds, float endTimeSeconds, boolean loop) {
        Trace.beginSection("AnimatedFileNative#getVideoFrame");
        try {
            return nGetVideoFrame(ptr, bitmap, params, preview, startTimeSeconds, endTimeSeconds, loop);
        } finally {
            Trace.endSection();
        }
    }

    private static void seekToMs(long ptr, long ms, int[] params, boolean precise) {
        Trace.beginSection("AnimatedFileNative#seekToMs");
        try {
            nSeekToMs(ptr, ms, params, precise);
        } finally {
            Trace.endSection();
        }
    }

    private static int getFrameAtTime(long ptr, long ms, Bitmap bitmap, int[] data) {
        Trace.beginSection("AnimatedFileNative#getFrameAtTime");
        try {
            return nGetFrameAtTime(ptr, ms, bitmap, data);
        } finally {
            Trace.endSection();
        }
    }

    private static void prepareToSeek(long ptr) {
        Trace.beginSection("AnimatedFileNative#prepareToSeek");
        try {
            nPrepareToSeek(ptr);
        } finally {
            Trace.endSection();
        }
    }

    public static void getVideoInfo(String src, int[] params, long fileOffset) {
        Trace.beginSection("AnimatedFileNative#getVideoInfo");
        try {
            nGetVideoInfo(Build.VERSION.SDK_INT, src, params, fileOffset);
        } finally {
            Trace.endSection();
        }
    }



    private static native long nCreateDecoder(String src, int[] params, int account, long streamFileSize, Object readCallback, boolean preview);

    private static native void nDestroyDecoder(long ptr);

    private static native void nStopDecoder(long ptr);

    private static native int nGetVideoFrame(long ptr, Bitmap bitmap, int[] params, boolean preview, float startTimeSeconds, float endTimeSeconds, boolean loop);

    private static native void nSeekToMs(long ptr, long ms, int[] params, boolean precise);

    private static native int nGetFrameAtTime(long ptr, long ms, Bitmap bitmap, int[] data);

    private static native void nPrepareToSeek(long ptr);

    private static native void nGetVideoInfo(int sdkVersion, String src, int[] params, long fileOffset);
}