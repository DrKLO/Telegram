package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.os.Trace;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin wrapper around a native Lottie animation pointer.
 *
 * <p>Instances are created exclusively through the static factory methods
 * {@link #createFromFile} and {@link #createFromRawJson}. When you are done
 * with an instance, call {@link #recycle()}; after that every method throws
 * {@link IllegalStateException}, matching the {@link Bitmap} contract.
 */
public final class RLottieNative {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Metadata filled by native create: [frameCount, fps, reserved]. */
    private final int[] mMetaData;

    /** Native pointer; 0 means not yet created or already recycled. */
    private long mNativePtr;

    /** True once {@link #recycle()} has been called. */
    private final AtomicBoolean mRecycled = new AtomicBoolean(false);

    // -------------------------------------------------------------------------
    // Private constructor — only factory methods may call this
    // -------------------------------------------------------------------------

    private RLottieNative(long nativePtr, int[] metaData) {
        mNativePtr = nativePtr;
        mMetaData = metaData;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates an instance from a Lottie file, optionally overriding its JSON
     * content.
     *
     * @param path             absolute path to the .json / .tgs file
     * @param json             override JSON string, or {@code null} to read from file
     * @param w                render width in pixels
     * @param h                render height in pixels
     * @param precache         whether to enable frame pre-caching
     * @param colorReplacement optional color replacement table, may be {@code null}
     * @param limitFps         cap rendering to 30 fps
     * @param fitzModifier     Fitzpatrick skin-tone modifier (0 = none)
     * @return a new instance, or {@code null} if the native layer failed
     */
    public static RLottieNative createFromFile(
            String path,
            String json,
            int w, int h,
            boolean precache,
            int[] colorReplacement,
            boolean limitFps,
            int fitzModifier) {
        int[] meta = new int[3];
        long ptr = create(path, json, w, h, meta, precache, colorReplacement, limitFps, fitzModifier);
        if (ptr == 0) {
            return null;
        }
        return new RLottieNative(ptr, meta);
    }

    /**
     * Creates an instance from a raw JSON string using an animation
     * {@code name} for debugging.
     *
     * @param json             Lottie JSON string (must not be null or empty)
     * @param name             debug name shown in logs
     * @param colorReplacement optional color replacement table, may be {@code null}
     * @return a new instance, or {@code null} if the native layer failed
     */
    public static RLottieNative createFromRawJson(
            String json,
            String name,
            int[] colorReplacement) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        int[] meta = new int[3];
        long ptr = createWithJson(json, name, meta, colorReplacement);
        if (ptr == 0) {
            return null;
        }
        return new RLottieNative(ptr, meta);
    }

    // -------------------------------------------------------------------------
    // Instance API
    // -------------------------------------------------------------------------

    /**
     * Renders {@code frame} into {@code bitmap}.
     *
     * @param frame  zero-based frame index
     * @param bitmap target bitmap; must be {@link Bitmap.Config#ARGB_8888}
     * @param clear  whether to clear the bitmap before rendering
     * @return the frame index that was rendered, -1 on error, or -5 when the
     *         decoder is busy (caller should retry after a short delay)
     * @throws IllegalStateException if already recycled
     */
    public int getFrame(int frame, Bitmap bitmap, boolean clear) {
        checkNotRecycled();
        return getFrame(mNativePtr, frame, bitmap, clear);
    }

    /**
     * Updates the fill color of a named layer.
     *
     * @param layer layer name as defined in the Lottie JSON
     * @param color ARGB color value
     * @throws IllegalStateException if already recycled
     */
    public void setLayerColor(String layer, int color) {
        checkNotRecycled();
        setLayerColor(mNativePtr, layer, color);
    }

    /**
     * Replaces the animation palette using a flat color-replacement array.
     *
     * @param colors interleaved [from, to, from, to, …] ARGB values
     * @throws IllegalStateException if already recycled
     */
    public void replaceColors(int[] colors) {
        checkNotRecycled();
        replaceColors(mNativePtr, colors);
    }

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    /** Total number of frames ({@code metaData[0]}). */
    public int getFrameCount() {
        return mMetaData[0];
    }

    /** Frame-rate in fps ({@code metaData[1]}). */
    public int getFps() {
        return mMetaData[1];
    }

    /** Animation duration in milliseconds, derived from frame count and fps. */
    public long getDurationMs() {
        int fps = mMetaData[1];
        if (fps == 0) return 0;
        return (long) (mMetaData[0] / (float) fps * 1000L);
    }

    /**
     * Raw metadata array in the form {@code [frameCount, fps, reserved]}.
     * <p><b>Do not modify</b> the returned array.
     */
    public int[] getMetaData() {
        return mMetaData;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@link #recycle()} has been called.
     * Mirrors {@link Bitmap#isRecycled()}.
     */
    public boolean isRecycled() {
        return mRecycled.get();
    }

    /**
     * Frees native resources associated with this animation.
     * After this call every other method throws {@link IllegalStateException}.
     * Calling {@code recycle()} more than once is a no-op, and it is safe to
     * call from any thread — the underlying {@code destroy} is guaranteed to
     * execute exactly once.
     *
     * <p>Mirrors {@link Bitmap#recycle()}.
     */
    public void recycle() {
        if (mRecycled.compareAndSet(false, true)) {
            long ptr = mNativePtr;
            mNativePtr = 0;
            if (ptr != 0) {
                destroy(ptr);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Safety net — same pattern as Bitmap
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void checkNotRecycled() {
        if (mRecycled.get()) {
            throw new IllegalStateException("Called method on a recycled RLottie instance");
        }
    }



    // -------------------------------------------------------------------------
    // Static native wrappers — for legacy code that still operates on raw pointers
    // -------------------------------------------------------------------------

    /**
     * Creates a native animation from a file and returns the raw pointer.
     * Prefer {@link #createFromFile} for new code.
     */
    public static long create(String src, String json, int w, int h, int[] params, boolean precache, int[] colorReplacement, boolean limitFps, int fitzModifier) {
        Trace.beginSection("RLottieNative#create");
        try {
            return nCreate(src, json, w, h, params, precache, colorReplacement, limitFps, fitzModifier);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Creates a native animation from a JSON string and returns the raw pointer.
     * Prefer {@link #createFromRawJson} for new code.
     */
    public static long createWithJson(String json, String name, int[] params, int[] colorReplacement) {
        Trace.beginSection("RLottieNative#createWithJson");
        try {
            return nCreateWithJson(json, name, params, colorReplacement);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Renders a frame directly using a raw pointer.
     * Prefer the instance method {@link #getFrame(int, Bitmap, boolean)} for new code.
     */
    public static int getFrame(long ptr, int frame, Bitmap bitmap, boolean clear) {
        Trace.beginSection("RLottieNative#getFrame");
        try {
            return nGetFrame(ptr, frame, bitmap, clear);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Updates a layer color directly using a raw pointer.
     * Prefer the instance method {@link #setLayerColor(String, int)} for new code.
     */
    public static void setLayerColor(long ptr, String layer, int color) {
        nSetLayerColor(ptr, layer, color);
    }

    /**
     * Replaces colors directly using a raw pointer.
     * Prefer the instance method {@link #replaceColors(int[])} for new code.
     */
    public static void replaceColors(long ptr, int[] colorReplacement) {
        nReplaceColors(ptr, colorReplacement);
    }

    /**
     * Destroys a native animation directly using a raw pointer.
     * Prefer {@link #recycle()} for new code.
     */
    public static void destroy(long ptr) {
        nDestroy(ptr);
    }

    // -------------------------------------------------------------------------
    // Static helpers — file inspection without creating an instance
    // -------------------------------------------------------------------------

    /**
     * Returns the total frame count of a file without keeping it open.
     * Safe to call on any thread; does not produce an instance.
     */
    public static long getFramesCount(String src, String json) {
        return nGetFramesCount(src, json);
    }

    /**
     * Returns the animation duration in seconds without keeping the file open.
     */
    public static double getDuration(String src, String json) {
        return nGetDuration(src, json);
    }



    // -------------------------------------------------------------------------
    // Native
    // -------------------------------------------------------------------------

    private static native long nCreate(String src, String json, int w, int h, int[] params, boolean precache, int[] colorReplacement, boolean limitFps, int fitzModifier);

    private static native long nCreateWithJson(String json, String name, int[] params, int[] colorReplacement);

    private static native int nGetFrame(long ptr, int frame, Bitmap bitmap, boolean clear);

    private static native void nSetLayerColor(long ptr, String layer, int color);

    private static native void nReplaceColors(long ptr, int[] colorReplacement);

    private static native void nDestroy(long ptr);

    private static native long nGetFramesCount(String src, String json);

    private static native double nGetDuration(String src, String json);
}