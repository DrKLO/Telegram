package org.telegram.ui.iv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;

public class RichMediaUploader implements NotificationCenter.NotificationCenterDelegate {

    public interface Listener {
        default void onWidthHeightResolved(int w, int h) {}
        default void onProgress(float progress) {}
        default void onPhotoUploaded(TLRPC.Photo photo) {}
        default void onVideoUploaded(TLRPC.Document document) {}
        default void onAudioUploaded(TLRPC.Document document) {}
        default void onError() {}
    }

    private final int currentAccount;
    private final String path;
    private final boolean isVideo;
    private final boolean isAudio;
    private final int videoWidth;
    private final int videoHeight;
    private final int videoDurationSec;
    private final TLRPC.Document audioDocument;
    private final Listener listener;

    private boolean started;
    private boolean cancelled;
    private boolean finished;
    private int requestToken;
    private volatile String uploadPath;

    public RichMediaUploader(int currentAccount, String path, Listener listener) {
        this(currentAccount, path, false, 0, 0, 0, listener);
    }

    public RichMediaUploader(int currentAccount, String path, boolean isVideo, int width, int height, int durationSec, Listener listener) {
        this.currentAccount = currentAccount;
        this.path = path;
        this.isVideo = isVideo;
        this.isAudio = false;
        this.videoWidth = width;
        this.videoHeight = height;
        this.videoDurationSec = durationSec;
        this.audioDocument = null;
        this.listener = listener;
    }

    private RichMediaUploader(int currentAccount, String path, TLRPC.Document audioDocument, Listener listener) {
        this.currentAccount = currentAccount;
        this.path = path;
        this.isVideo = false;
        this.isAudio = true;
        this.videoWidth = 0;
        this.videoHeight = 0;
        this.videoDurationSec = 0;
        this.audioDocument = audioDocument;
        this.listener = listener;
    }

    public static RichMediaUploader forAudio(int currentAccount, String path, TLRPC.Document audioDocument, Listener listener) {
        return new RichMediaUploader(currentAccount, path, audioDocument, listener);
    }

    public String getPath() {
        return path;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public void start() {
        if (started || cancelled || finished) return;
        started = true;
        if (isVideo) {
            if (listener != null && videoWidth > 0 && videoHeight > 0) {
                listener.onWidthHeightResolved(videoWidth, videoHeight);
            }
            beginUpload(path);
            return;
        }
        if (isAudio) {
            beginUpload(path);
            return;
        }
        resolvePhotoDimensions();
        Utilities.globalQueue.postRunnable(() -> {
            final String jpegPath = ensureJpegPath(path);
            AndroidUtilities.runOnUIThread(() -> {
                if (cancelled || finished) return;
                beginUpload(jpegPath);
            });
        });
    }

    private void beginUpload(String filePath) {
        if (cancelled || finished) return;
        uploadPath = filePath;
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.addObserver(this, NotificationCenter.fileUploaded);
        nc.addObserver(this, NotificationCenter.fileUploadFailed);
        nc.addObserver(this, NotificationCenter.fileUploadProgressChanged);
        final int fileType;
        if (isVideo) {
            fileType = ConnectionsManager.FileTypeVideo;
        } else if (isAudio) {
            fileType = ConnectionsManager.FileTypeAudio;
        } else {
            fileType = ConnectionsManager.FileTypePhoto;
        }
        FileLoader.getInstance(currentAccount).uploadFile(uploadPath, false, !isVideo && !isAudio, fileType);
    }

    private String ensureJpegPath(String src) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(src, opts);
            final String mime = opts.outMimeType;
            final boolean isJpeg = mime != null && (mime.equalsIgnoreCase("image/jpeg") || mime.equalsIgnoreCase("image/jpg"));
            final float maxSize = AndroidUtilities.getPhotoSize();
            Bitmap bitmap = ImageLoader.loadBitmap(src, null, maxSize, maxSize, true);
            if (bitmap == null) {
                bitmap = ImageLoader.loadBitmap(src, null, 800, 800, true);
            }
            if (bitmap == null) {
                return src;
            }
            final File dst = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "rich_jpeg_" + Math.abs(src.hashCode()) + ".jpg");
            boolean ok = false;
            try (FileOutputStream stream = new FileOutputStream(dst)) {
                ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 89, stream);
            } finally {
                bitmap.recycle();
            }
            if (!ok || dst.length() <= 0) {
                return src;
            }
            if (isJpeg) {
                final long srcLen = new File(src).length();
                if (srcLen > 0 && dst.length() >= srcLen) {
                    return src;
                }
            }
            return dst.getAbsolutePath();
        } catch (Throwable ignore) {
            return src;
        }
    }

    public void cancel() {
        if (finished || cancelled) return;
        cancelled = true;
        try {
            if (uploadPath != null) {
                FileLoader.getInstance(currentAccount).cancelFileUpload(uploadPath, false);
            }
        } catch (Throwable ignore) {}
        if (requestToken != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(requestToken, true);
            requestToken = 0;
        }
        teardown();
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    private void teardown() {
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.removeObserver(this, NotificationCenter.fileUploaded);
        nc.removeObserver(this, NotificationCenter.fileUploadFailed);
        nc.removeObserver(this, NotificationCenter.fileUploadProgressChanged);
    }

    private void resolvePhotoDimensions() {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            if (opts.outWidth > 0 && opts.outHeight > 0 && listener != null) {
                listener.onWidthHeightResolved(opts.outWidth, opts.outHeight);
            }
        } catch (Exception ignore) {}
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount || cancelled || finished) return;
        String p = (String) args[0];
        if (uploadPath == null || !uploadPath.equals(p)) return;
        if (id == NotificationCenter.fileUploaded) {
            TLRPC.InputFile inputFile = (TLRPC.InputFile) args[1];
            sendUploadMediaRequest(inputFile);
        } else if (id == NotificationCenter.fileUploadFailed) {
            finishWithError();
        } else if (id == NotificationCenter.fileUploadProgressChanged) {
            long uploaded = (Long) args[1];
            long total = (Long) args[2];
            if (listener != null) {
                listener.onProgress(total > 0 ? (float) uploaded / total : 0f);
            }
        }
    }

    private void sendUploadMediaRequest(TLRPC.InputFile inputFile) {
        TLRPC.TL_messages_uploadMedia req = new TLRPC.TL_messages_uploadMedia();
        req.peer = new TLRPC.TL_inputPeerSelf();
        if (isVideo) {
            TLRPC.TL_inputMediaUploadedDocument media = new TLRPC.TL_inputMediaUploadedDocument();
            media.file = inputFile;
            media.mime_type = "video/mp4";
            TLRPC.TL_documentAttributeVideo attr = new TLRPC.TL_documentAttributeVideo();
            attr.supports_streaming = true;
            attr.duration = videoDurationSec;
            attr.w = videoWidth;
            attr.h = videoHeight;
            media.attributes.add(attr);
            req.media = media;
        } else if (isAudio) {
            TLRPC.TL_inputMediaUploadedDocument media = new TLRPC.TL_inputMediaUploadedDocument();
            media.file = inputFile;
            media.mime_type = audioDocument != null && audioDocument.mime_type != null ? audioDocument.mime_type : "audio/mpeg";
            if (audioDocument != null) {
                media.attributes.addAll(audioDocument.attributes);
            }
            req.media = media;
        } else {
            TLRPC.TL_inputMediaUploadedPhoto media = new TLRPC.TL_inputMediaUploadedPhoto();
            media.file = inputFile;
            req.media = media;
        }
        requestToken = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (cancelled) return;
            requestToken = 0;
            if (isVideo || isAudio) {
                if (response instanceof TLRPC.TL_messageMediaDocument) {
                    TLRPC.Document doc = ((TLRPC.TL_messageMediaDocument) response).document;
                    if (doc != null) {
                        if (isAudio) {
                            finishWithAudio(doc);
                        } else {
                            finishWithVideo(doc);
                        }
                        return;
                    }
                }
            } else {
                if (response instanceof TLRPC.TL_messageMediaPhoto) {
                    TLRPC.Photo photo = ((TLRPC.TL_messageMediaPhoto) response).photo;
                    if (photo != null) {
                        finishWithPhoto(photo);
                        return;
                    }
                }
            }
            finishWithError();
        }));
    }

    private void finishWithPhoto(TLRPC.Photo photo) {
        finished = true;
        teardown();
        if (listener != null) listener.onPhotoUploaded(photo);
    }

    private void finishWithVideo(TLRPC.Document doc) {
        finished = true;
        teardown();
        if (listener != null) listener.onVideoUploaded(doc);
    }

    private void finishWithAudio(TLRPC.Document doc) {
        finished = true;
        teardown();
        if (listener != null) listener.onAudioUploaded(doc);
    }

    private void finishWithError() {
        finished = true;
        teardown();
        if (listener != null) listener.onError();
    }
}
