package org.telegram.messenger;

/**
 * JPEG quality / max-side for compressed chat photos.
 * Isolated so it can be unit-tested without the Android SDK.
 */
public final class PhotoCompressPolicy {
    private PhotoCompressPolicy() {}

    /** Default compressed chat send (MediaController rebuildPhoto / scaleAndSaveImage). */
    public static int chatJpegQuality(boolean highQuality) {
        return highQuality ? 99 : 92;
    }

    /** ImageLoader disk cache of decoded bitmaps. */
    public static int cacheJpegQuality(boolean big) {
        return big ? 90 : 60;
    }

    public static int photoMaxSide(boolean highQuality) {
        return highQuality ? 2560 : 1280;
    }
}
