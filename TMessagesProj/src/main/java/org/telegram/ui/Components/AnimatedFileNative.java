package org.Tajgram.ui.Components;

import android.graphics.Bitmap;
import android.os.Build;

import org.Tajgram.messenger.AnimatedFileDrawableStream;

public class AnimatedFileNative {

    public static long createDecoder(String src, int[] params, int account, long streamFileSize, AnimatedFileDrawableStream readCallback, boolean preview) {
        return nCreateDecoder(src, params, account, streamFileSize, readCallback, preview);
    }

    public static void destroyDecoder(long ptr) {
        nDestroyDecoder(ptr);
    }

    public static void stopDecoder(long ptr) {
        nStopDecoder(ptr);
    }

    public static int getVideoFrame(long ptr, Bitmap bitmap, int[] params, boolean preview, float startTimeSeconds, float endTimeSeconds, boolean loop) {
        return nGetVideoFrame(ptr, bitmap, params, preview, startTimeSeconds, endTimeSeconds, loop);
    }

    public static void seekToMs(long ptr, long ms, int[] params, boolean precise) {
        nSeekToMs(ptr, ms, params, precise);
    }

    public static int getFrameAtTime(long ptr, long ms, Bitmap bitmap, int[] data) {
        return nGetFrameAtTime(ptr, ms, bitmap, data);
    }

    public static void prepareToSeek(long ptr) {
        nPrepareToSeek(ptr);
    }

    public static void getVideoInfo(String src, int[] params, long fileOffset) {
        nGetVideoInfo(Build.VERSION.SDK_INT, src, params, fileOffset);
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
