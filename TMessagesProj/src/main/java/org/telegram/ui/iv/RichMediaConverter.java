package org.telegram.ui.iv;

import android.graphics.BitmapFactory;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

// Converts a still photo that carries animated overlays (animated stickers / animated emoji) into a
// muted mp4, reusing the same VideoEditedInfo + MediaController.scheduleVideoConvert machinery that
// PhotoViewer uses for regular sends. The converter writes a complete file to cache; once writing is
// done the editor uploads it through the existing video branch of RichMediaUploader.
public class RichMediaConverter implements NotificationCenter.NotificationCenterDelegate {

    public interface Listener {
        default void onProgress(float progress) {}
        // mp4Path is the finished converted file; w/h/durationSec describe the produced video.
        void onDone(String mp4Path, int width, int height, int durationSec);
        void onError();
    }

    private final int currentAccount;
    private final MediaController.PhotoEntry entry;
    private final Listener listener;

    private MessageObject messageObject;
    private VideoEditedInfo info;
    private String outPath;
    private boolean started;
    private boolean cancelled;
    private boolean finished;

    public RichMediaConverter(int currentAccount, MediaController.PhotoEntry entry, Listener listener) {
        this.currentAccount = currentAccount;
        this.entry = entry;
        this.listener = listener;
    }

    // Mirrors PhotoViewer.getAnimatedMediaEntitiesCount: a sticker entity flagged RLottie (subType&1)
    // or webm/mp4 (subType&4), or a text entity containing animated emoji.
    public static boolean hasAnimatedMediaEntities(MediaController.PhotoEntry entry) {
        if (entry == null || entry.isVideo) return false;
        ArrayList<VideoEditedInfo.MediaEntity> list = entry.croppedMediaEntities != null && !entry.croppedMediaEntities.isEmpty()
            ? entry.croppedMediaEntities : entry.mediaEntities;
        if (list == null) return false;
        for (int a = 0, N = list.size(); a < N; a++) {
            VideoEditedInfo.MediaEntity entity = list.get(a);
            if (entity == null) continue;
            if (entity.type == 0 && ((entity.subType & 1) != 0 || (entity.subType & 4) != 0)
                || (entity.entities != null && !entity.entities.isEmpty())) {
                return true;
            }
        }
        return false;
    }

    // Mirrors PhotoViewer.getCurrentVideoEditedInfo for the photo-with-animated-overlays case.
    private static VideoEditedInfo buildVideoEditedInfo(MediaController.PhotoEntry entry) {
        final float maxSize = 854;

        int width = entry.width;
        int height = entry.height;
        if (width <= 0 || height <= 0) {
            try {
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(entry.path, opts);
                width = opts.outWidth;
                height = opts.outHeight;
            } catch (Exception ignore) {}
        }
        if (width <= 0 || height <= 0) {
            return null;
        }
        // entry.width/height are the RAW (pre-EXIF) pixel dimensions. TextureRenderer rotates the source
        // photo by its own EXIF orientation when rendering (no cropState -> useMatrixForImagePath == false),
        // so the produced frame must use the ORIENTED dimensions, mirroring PhotoViewer reading the already
        // rotated centerImage size. Otherwise a portrait photo yields a landscape video.
        if (entry.orientation == 90 || entry.orientation == 270) {
            int temp = width;
            width = height;
            height = temp;
        }

        final VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
        videoEditedInfo.start = videoEditedInfo.startTime = 0;
        videoEditedInfo.endTime = Math.min(3000, entry.averageDuration);
        while (videoEditedInfo.endTime > 0 && videoEditedInfo.endTime < 1000) {
            videoEditedInfo.endTime *= 2;
        }
        if (videoEditedInfo.endTime <= 0) {
            videoEditedInfo.endTime = 3000;
        }
        videoEditedInfo.end = videoEditedInfo.endTime;
        // For an isPhoto source the output dimensions/bitrate are set explicitly below; compressQuality
        // is not used for sizing, so a sane default is fine.
        videoEditedInfo.compressQuality = 1;
        videoEditedInfo.rotationValue = 0;
        videoEditedInfo.originalPath = entry.path;
        videoEditedInfo.estimatedSize = (int) (videoEditedInfo.endTime / 1000.0f * 115200);
        videoEditedInfo.estimatedDuration = videoEditedInfo.endTime;
        videoEditedInfo.framerate = 30;
        videoEditedInfo.originalDuration = videoEditedInfo.endTime;
        videoEditedInfo.filterState = entry.savedFilterState;
        if (entry.croppedPaintPath != null) {
            videoEditedInfo.paintPath = entry.croppedPaintPath;
            videoEditedInfo.mediaEntities = entry.croppedMediaEntities != null && !entry.croppedMediaEntities.isEmpty() ? entry.croppedMediaEntities : null;
        } else {
            videoEditedInfo.paintPath = entry.paintPath;
            videoEditedInfo.mediaEntities = entry.mediaEntities;
        }
        videoEditedInfo.isPhoto = true;
        if (entry.cropState != null) {
            if (entry.cropState.transformRotation == 90 || entry.cropState.transformRotation == 270) {
                int temp = width;
                width = height;
                height = temp;
            }
            width *= entry.cropState.cropPw;
            height *= entry.cropState.cropPh;
        }
        float scale = Math.max(width / maxSize, height / maxSize);
        if (scale < 1) {
            scale = 1;
        }
        width /= scale;
        height /= scale;
        if (width % 16 != 0) {
            width = Math.max(1, Math.round(width / 16.0f)) * 16;
        }
        if (height % 16 != 0) {
            height = Math.max(1, Math.round(height / 16.0f)) * 16;
        }
        videoEditedInfo.originalWidth = videoEditedInfo.resultWidth = width;
        videoEditedInfo.originalHeight = videoEditedInfo.resultHeight = height;
        videoEditedInfo.bitrate = -1;
        videoEditedInfo.muted = true;
        videoEditedInfo.avatarStartTime = 0;
        return videoEditedInfo;
    }

    public void start() {
        if (started || cancelled || finished) return;
        started = true;

        info = buildVideoEditedInfo(entry);
        if (info == null || !info.needConvert()) {
            fail();
            return;
        }

        final TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 1;
        outPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "rich_anim_" + SharedConfig.getLastLocalId() + ".mp4").getAbsolutePath();
        message.attachPath = outPath;
        messageObject = new MessageObject(currentAccount, message, (MessageObject) null, false, false);
        messageObject.videoEditedInfo = info;

        final NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.addObserver(this, NotificationCenter.filePreparingStarted);
        nc.addObserver(this, NotificationCenter.fileNewChunkAvailable);
        nc.addObserver(this, NotificationCenter.filePreparingFailed);

        MediaController.getInstance().scheduleVideoConvert(messageObject, false, false, false);
    }

    public void cancel() {
        if (finished || cancelled) return;
        cancelled = true;
        if (messageObject != null && info != null) {
            try {
                MediaController.getInstance().cancelVideoConvert(messageObject);
            } catch (Throwable ignore) {}
        }
        teardown();
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (cancelled || finished) return;
        if (account != currentAccount) return;
        if (args.length == 0 || args[0] != messageObject) return;

        if (id == NotificationCenter.fileNewChunkAvailable) {
            final long finalSize = (Long) args[3];
            final float progress = (Float) args[4];
            if (listener != null) listener.onProgress(progress);
            if (finalSize > 0) {
                // last == true: the file is fully written.
                finished = true;
                teardown();
                if (listener != null) {
                    listener.onDone(outPath, info.resultWidth, info.resultHeight, (int) Math.ceil(info.estimatedDuration / 1000.0));
                }
            }
        } else if (id == NotificationCenter.filePreparingFailed) {
            fail();
        }
    }

    private void fail() {
        if (finished) return;
        finished = true;
        teardown();
        if (listener != null) listener.onError();
    }

    private void teardown() {
        final NotificationCenter nc = NotificationCenter.getInstance(currentAccount);
        nc.removeObserver(this, NotificationCenter.filePreparingStarted);
        nc.removeObserver(this, NotificationCenter.fileNewChunkAvailable);
        nc.removeObserver(this, NotificationCenter.filePreparingFailed);
    }
}
