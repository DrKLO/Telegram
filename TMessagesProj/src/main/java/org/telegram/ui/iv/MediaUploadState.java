package org.telegram.ui.iv;

import org.telegram.tgnet.TLRPC;

public class MediaUploadState {

    public static final int STATE_EMPTY = 0;
    public static final int STATE_UPLOADING = 1;
    public static final int STATE_DONE = 2;
    public static final int STATE_ERROR = 3;

    public int state = STATE_EMPTY;
    public boolean isVideo;
    public boolean isAudio;
    public String localPath;
    public String thumbPath;
    public int imageId;
    public android.graphics.Bitmap localThumbBitmap;
    public float progress;
    public TLRPC.Photo photo;
    public TLRPC.Document document;
    public TLRPC.Document audioDisplayDocument;
    public int width;
    public int height;
    public int duration;
    // EXIF orientation of the local file, applied to the preview thumb at draw time
    public int orientation;
    public int invert;
    public boolean hasSpoiler;

    public boolean isReady() {
        if (state != STATE_DONE) return false;
        return (isVideo || isAudio) ? document != null : photo != null;
    }

    public boolean isPending() {
        return state == STATE_UPLOADING;
    }
}
