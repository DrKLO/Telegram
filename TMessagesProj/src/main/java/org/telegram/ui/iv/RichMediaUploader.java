package org.Tajgram.ui.iv;

import android.graphics.BitmapFactory;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.FileLoader;
import org.Tajgram.messenger.NotificationCenter;
import org.Tajgram.tgnet.ConnectionsManager;
import org.Tajgram.tgnet.TLRPC;

public class RichMediaUploader implements NotificationCenter.NotificationCenterDelegate {

    public interface Listener {
        default void onWidthHeightResolved(int w, int h) {}
        default void onProgress(float progress) {}
        default void onPhotoUploaded(TLRPC.Photo photo) {}
        default void onVideoUploaded(TLRPC.Document document) {}
        default void onError() {}
    }

    private final int currentAccount;
    private final String path;
    private final boolean isVideo;
    private final int videoWidth;
    private final int videoHeight;
    private final int videoDurationSec;
    private final Listener listener;

    private boolean started;
    private boolean cancelled;
    private boolean finished;
    private int requestToken;

    public RichMediaUploader(int currentAccount, String path, Listener listener) {
        this(currentAccount, path, false, 0, 0, 0, listener);
    }

    public RichMediaUploader(int currentAccount, String path, boolean isVideo, int width, int height, int durationSec, Listener listener) {
        this.currentAccount = currentAccount;
        this.path = path;
        this.isVideo = isVideo;
        this.videoWidth = width;
        this.videoHeight = height;
        this.videoDurationSec = durationSec;
        this.listener = listener;
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
        if (!isVideo) {
            resolvePhotoDimensions();
        } else if (listener != null && videoWidth > 0 && videoHeight > 0) {
            listener.onWidthHeightResolved(videoWidth, videoHeight);
        }
        NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.addObserver(this, NotificationCenter.fileUploaded);
        nc.addObserver(this, NotificationCenter.fileUploadFailed);
        nc.addObserver(this, NotificationCenter.fileUploadProgressChanged);
        int fileType = isVideo ? ConnectionsManager.FileTypeVideo : ConnectionsManager.FileTypePhoto;
        FileLoader.getInstance(currentAccount).uploadFile(path, false, !isVideo, fileType);
    }

    public void cancel() {
        if (finished || cancelled) return;
        cancelled = true;
        try {
            FileLoader.getInstance(currentAccount).cancelFileUpload(path, false);
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
        if (!path.equals(p)) return;
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
        } else {
            TLRPC.TL_inputMediaUploadedPhoto media = new TLRPC.TL_inputMediaUploadedPhoto();
            media.file = inputFile;
            req.media = media;
        }
        requestToken = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (cancelled) return;
            requestToken = 0;
            if (isVideo) {
                if (response instanceof TLRPC.TL_messageMediaDocument) {
                    TLRPC.Document doc = ((TLRPC.TL_messageMediaDocument) response).document;
                    if (doc != null) {
                        finishWithVideo(doc);
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

    private void finishWithError() {
        finished = true;
        teardown();
        if (listener != null) listener.onError();
    }
}
