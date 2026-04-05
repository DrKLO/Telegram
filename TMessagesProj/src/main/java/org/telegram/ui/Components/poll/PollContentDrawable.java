package org.telegram.ui.Components.poll;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ClipRoundedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.Text;

import java.util.Locale;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;

public class PollContentDrawable extends Drawable implements DownloadController.FileDownloadProgressListener, SeekBar.SeekBarDelegate {
    private final BoolAnimator animatorIsPlaying;

    public final ImageReceiver imageReceiver;
    private final int currentAccount;
    private final ViewGroup parent;

    private CharSequence fileName, authorInfo, fileInfo;
    private @Nullable Text fileNameText, authorInfoText, fileInfoText, videoDurationText;
    private final Paint durationBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialProgress2 radialProgress;
    private final boolean isExplanation;
    private String attachFileName;
    private String attachPath;
    private boolean isVideo;
    private MessageObject messageObject;
    private double musicDuration;
    private int videoDuration;
    private final SeekBar seekBar;
    private float seekBarX, seekBarY;
    private SvgHelper.SvgDrawable locationSvgThumb;
    private ClipRoundedDrawable locationLoadingThumb;
    private Drawable redLocationIcon;
    private FileState fileState;

    public PollContentDrawable(int currentAccount, ViewGroup parent, Theme.ResourcesProvider resourcesProvider, boolean isExplanation) {
        this.imageReceiver = new ImageReceiver(parent);
        this.imageReceiver.setRoundRadius(dp(6));
        this.currentAccount = currentAccount;
        this.isExplanation = isExplanation;
        this.radialProgress = new RadialProgress2(parent, resourcesProvider);
        this.parent = parent;
        this.seekBar = new SeekBar(parent);
        this.seekBar.setDelegate(this);
        animatorIsPlaying = new BoolAnimator(parent, AnimatorUtils.DECELERATE_INTERPOLATOR, 180);

        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

    public boolean seekBarOnTouch(int action, float x, float y) {
        return seekBar.onTouch(action, x - seekBarX, y - seekBarY);
    }

    private boolean miniButtonPressed;
    public boolean miniButtonOnTouch(int action, float x, float y) {
        if (!isMusic || lastIconMini == MediaActionDrawable.ICON_NONE) {
            return false;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            final int side = dp(36);
            final int offset = dp(27);
            final boolean area2 = x >= fileButtonX + offset && x <= fileButtonX + offset + side
                    && y >= fileButtonY + offset && y <= fileButtonY + offset + side;

            if (area2) {
                miniButtonPressed = true;
                return true;
            }
        }

        if (miniButtonPressed) {
            if (action == MotionEvent.ACTION_UP) {
                if (fileState != null) {
                    if (fileState.isLoading()) {
                        fileState.downloadCancel();
                    } else if (!fileState.isExists()) {
                        fileState.downloadStart();
                    }
                    checkFileState();
                }
                miniButtonPressed = false;
                return true;
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                miniButtonPressed = false;
                return true;
            }
        }

        return miniButtonPressed;
    }

    private boolean hasMedia;
    private TLRPC.MessageMedia media;

    public void attach() {
        imageReceiver.onAttachedToWindow();
        radialProgress.onAttachedToWindow();
    }

    public void detach() {
        imageReceiver.onDetachedFromWindow();
        radialProgress.onDetachedFromWindow();
    }

    public void setMedia(MessageObject messageObject, TLRPC.MessageMedia media, Object parentObject, int targetWidth, String attachPath, boolean animated) {
        final String attachFileNameOld = attachFileName;

        this.mediaWidth = 0;
        this.mediaHeight = 0;
        this.messageObject = messageObject;
        this.media = media;
        this.isFile = false;
        this.isMusic = false;
        this.isVideo = false;
        this.isLocation = false;
        this.musicDuration = 0;
        this.videoDuration = 0;
        this.attachPath = attachPath;
        this.attachFileName = null;
        this.fileState = null;
        hasMedia = setMediaImpl(media, parentObject, targetWidth, attachPath);
        if (!hasMedia) {
            imageReceiver.clearImage();
        }

        if (!TextUtils.equals(attachFileNameOld, attachFileName)) {
            if (!TextUtils.isEmpty(attachFileNameOld)) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            }
            if (!TextUtils.isEmpty(attachFileName)) {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(attachFileName, this);
            }
        }

        if (!isMusic) {
            radialProgress.setImageOverlay(null, null, null);
            setIconMini(MediaActionDrawable.ICON_NONE, false);
        }

        updatePlayingMessageProgress(animated);
    }

    public TLRPC.MessageMedia getMedia() {
        return media;
    }

    public boolean isHasMedia() {
        return hasMedia;
    }

    private int mediaWidth;
    private int mediaHeight;

    private boolean isFile;
    private boolean isMusic;
    private boolean isLocation;

    private boolean setMediaImpl(TLRPC.MessageMedia media, Object parentObject, int targetWidth, String attachPath) {
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty) {
            return false;
        }

        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            final TLRPC.TL_messageMediaPhoto messageMediaPhoto = (TLRPC.TL_messageMediaPhoto) media;

            final TLRPC.Photo photoParentObject = messageMediaPhoto.photo;
            final TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(photoParentObject.sizes, 40);
            final TLRPC.PhotoSize currentPhotoObjectObject = FileLoader.getClosestPhotoSizeWithSize(photoParentObject.sizes, AndroidUtilities.getPhotoSize(), true, currentPhotoObjectThumb , true);

            if (currentPhotoObjectObject == null) {
                return false;
            }

            final int w = mediaWidth = currentPhotoObjectObject.w;
            final int h = mediaHeight = currentPhotoObjectObject.h;

            final String currentPhotoFilter = String.format(Locale.US, "%d_%d", (int) (w / AndroidUtilities.density), (int) (h / AndroidUtilities.density));
            final String currentPhotoFilterThumb = currentPhotoFilter + "_b";

            attachFileName = !TextUtils.isEmpty(attachPath) ? attachPath : MessageObject.getFileName(media);
            imageReceiver.setImage(
                ImageLocation.getForObject(currentPhotoObjectObject, photoParentObject),
                currentPhotoFilter,
                ImageLocation.getForObject(currentPhotoObjectThumb, photoParentObject),
                currentPhotoFilterThumb,
                null,
                currentPhotoObjectObject.size,
                null,
                parentObject,
                1);
            return true;
        } else if (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaVenue) {
            if (media.geo != null) {
                if (locationSvgThumb == null) {
                    locationSvgThumb = DocumentObject.getSvgThumb(R.raw.map_placeholder, Theme.key_chat_outLocationIcon, (Theme.isCurrentThemeDark() ? 3 : 6) * .12f);
                    locationSvgThumb.setAspectCenter(true);
                    locationLoadingThumb = new ClipRoundedDrawable(locationSvgThumb);
                }
                if (redLocationIcon == null) {
                    redLocationIcon = parent.getContext().getResources().getDrawable(R.drawable.map_pin).mutate();
                }

                isLocation = true;
                mediaWidth = targetWidth;
                mediaHeight = targetWidth * 9 / 16;
                final WebFile currentWebFile = WebFile.createWithGeoPoint(media.geo,
                    (int) (mediaWidth / AndroidUtilities.density),
                    (int) (mediaHeight / AndroidUtilities.density),
                    15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));
                imageReceiver.setImage(ImageLocation.getForWebFile(currentWebFile), null, null, null, locationLoadingThumb, parentObject, 0);
                return true;
            }
        } else if (media instanceof TLRPC.TL_messageMediaDocument) {
            final TLRPC.TL_messageMediaDocument messageMediaDocument = (TLRPC.TL_messageMediaDocument) media;
            final TLRPC.Document document = messageMediaDocument.document;
            if (document == null) {
                return false;
            }

            fileState = new FileState(currentAccount, messageObject, document, attachPath);
            attachFileName = !TextUtils.isEmpty(attachPath) ? attachPath : MessageObject.getFileName(media);

            if (MessageObject.isMusicDocument(document)) {
                isMusic = true;
                fileName = MessageObject.getMusicTitle(document, true);
                authorInfo = MessageObject.getMusicAuthor(document, true);

                double duration = 0;
                for (int a = 0; a < document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                        duration = attribute.duration;
                        break;
                    }
                }

                if (MessageObject.isDocumentHasThumb(document)) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(22), true, null, false);
                    TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(44), true, thumb, true);
                    radialProgress.setImageOverlay(image, thumb, document, messageObject);
                } else {
                    String artworkUrl = MessageObject.getArtworkUrl(document, true);
                    if (!TextUtils.isEmpty(artworkUrl)) {
                        radialProgress.setImageOverlay(artworkUrl);
                    } else {
                        radialProgress.setImageOverlay(null, null, null);
                    }
                }

                musicDuration = duration;
                fileInfo = AndroidUtilities.formatShortDuration(getCurrentPlayingProgress(), (int) musicDuration);
            } else if (MessageObject.isVideoDocument(document)) {
                videoDuration = (int) Math.max(1, Math.round(MessageObject.getDocumentDuration(document)));
                isVideo = true;

                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, AndroidUtilities.getPhotoSize(), true, null, true);
                TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, (int) (targetWidth / AndroidUtilities.density), false, photoSize, false);
                // ImageLocation mediaLocation = ImageLocation.getForDocument(document);
                ImageLocation photoLocation = ImageLocation.getForDocument(photoSize, document);
                ImageLocation thumbLocation = ImageLocation.getForDocument(thumbSize, document);
                // boolean autoplay = false;

                final int w = (int) (targetWidth / AndroidUtilities.density);
                final int h;
                if (photoSize != null) {
                    mediaWidth = photoSize.w;
                    mediaHeight = photoSize.h;
                    h = mediaWidth != 0 ? w * mediaHeight / mediaWidth : w;
                } else if (thumbSize != null) {
                    mediaWidth = thumbSize.w;
                    mediaHeight = thumbSize.h;
                    h = mediaWidth != 0 ? w * mediaHeight / mediaWidth : w;
                } else {
                    h = w;
                }

                String filter = w + "_" + h;
                imageReceiver.setImage(
                    null, filter,
                    photoLocation, filter,
                    thumbLocation, filter,
                    null, 0, null,
                    parentObject, 0);
            } else {
                isFile = true;
                fileName = FileLoader.getDocumentFileName(document); //MessageObject.getFileName(document);
                fileInfo = AndroidUtilities.formatFileSize(document.size) + " " + FileLoader.getDocumentExtension(document);
                authorInfo = fileInfo;
            }
            checkFileTexts(true);
            return true;
        }

        return false;
    }

    private int lastFileNameWidth = 0;

    public int getHeightForWidth(int width) {
        if (isMusic) {
            return dp(63);
        } else if (isFile) {
            return dp(56);
        }

        if (mediaWidth == 0) {
            return dp(100);
        }
        return Math.min(Math.round(mediaHeight * (float) (width / mediaWidth)), isExplanation ? (width * 4 / 5) : (width * 5 / 4));
    }

    public void checkColors(boolean isOut) {
        if (fileNameText != null) {
            fileNameText.setColor(Theme.getColor(isOut ? Theme.key_chat_outFileNameText : Theme.key_chat_inFileNameText));
        }
        durationBackgroundPaint.setColor(0x66000000);
        if (videoDurationText != null) {
            videoDurationText.setColor(Color.WHITE);
        }

        if (locationSvgThumb != null) {
            locationSvgThumb.setColorKey(isOut ? Theme.key_chat_outLocationIcon : Theme.key_chat_inLocationIcon);
        }

        if (isMusic || isFile) {
            if (isOut) {
                seekBar.setColors(getThemedColor(Theme.key_chat_outAudioSeekbar), getThemedColor(Theme.key_chat_outAudioCacheSeekbar), getThemedColor(Theme.key_chat_outAudioSeekbarFill), getThemedColor(Theme.key_chat_outAudioSeekbarFill), getThemedColor(Theme.key_chat_outAudioSeekbarSelected));
                radialProgress.setColorKeys(Theme.key_chat_outLoader, Theme.key_chat_outLoaderSelected, Theme.key_chat_outMediaIcon, Theme.key_chat_outMediaIconSelected);
            } else {
                radialProgress.setColorKeys(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected);
                seekBar.setColors(getThemedColor(Theme.key_chat_inAudioSeekbar), getThemedColor(Theme.key_chat_inAudioCacheSeekbar), getThemedColor(Theme.key_chat_inAudioSeekbarFill), getThemedColor(Theme.key_chat_inAudioSeekbarFill), getThemedColor(Theme.key_chat_inAudioSeekbarSelected));
            }
        } else {
            radialProgress.setColorKeys(Theme.key_chat_mediaLoaderPhoto, Theme.key_chat_mediaLoaderPhotoSelected, Theme.key_chat_mediaLoaderPhotoIcon, Theme.key_chat_mediaLoaderPhotoIconSelected);
        }
    }

    public void setColors(int primary, int normal, int info, int file) {
        if (authorInfoText != null) {
            authorInfoText.setColor(normal);
        }
        if (fileInfoText != null) {
            fileInfoText.setColor(info);
        }
    }

    private void checkFileTexts(boolean force) {
        int w = getBounds().width() - dp(isExplanation ? 64 : 72);
        if (lastFileNameWidth != w || force) {
            lastFileNameWidth = w;

            if (fileName != null) {
                if (fileNameText == null) {
                    fileNameText = new Text(fileName, 15, AndroidUtilities.bold());
                }
                fileNameText.setText(TextUtils.ellipsize(fileName, fileNameText.paint, w, TextUtils.TruncateAt.MIDDLE));
            }
            if (authorInfo != null) {
                if (authorInfoText == null) {
                    authorInfoText = new Text(authorInfo, 14);
                }
                authorInfoText.setText(TextUtils.ellipsize(authorInfo, authorInfoText.paint, w, TextUtils.TruncateAt.END));
            }
            if (fileInfo != null) {
                if (fileInfoText == null) {
                    fileInfoText = new Text(fileInfo, 12);
                }
                fileInfoText.setText(TextUtils.ellipsize(fileInfo, fileInfoText.paint, w, TextUtils.TruncateAt.END));
            }
            if (isVideo) {
                if (videoDurationText == null) {
                    videoDurationText = new Text(AndroidUtilities.formatLongDuration(videoDuration), 12);
                }
            }
        }
    }

    private int fileButtonX;
    private int fileButtonY;

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        if (alpha == 0 || bounds.isEmpty()) {
            return;
        }

        checkFileTexts(false);
        if (isFile || isMusic) {
            final int left = bounds.left + (isExplanation ? 0 : dp(8));
            final int top = bounds.top + (isExplanation ? 0 : dp(3));
            final int ty = !isMusic ? dp(3) : 0;

            if (fileNameText != null) {
                fileNameText.draw(canvas, left + dp(56), top + ty + dp(15));
            }
            if (isMusic) {
                float isPlayingFactor = animatorIsPlaying.getFloatValue();
                if (authorInfoText != null && isPlayingFactor < 1) {
                    canvas.save();
                    canvas.scale(1f - isPlayingFactor, 1f - isPlayingFactor, left + dp(56), top + ty + dp(15 + 20));
                    authorInfoText.setAlpha((int) (255 * (1f - isPlayingFactor)));
                    authorInfoText.draw(canvas, left + dp(56), top + ty + dp(15 + 20));
                    canvas.restore();
                }
                if (isPlayingFactor > 0) {
                    seekBar.setAlpha(isPlayingFactor);
                    seekBar.setSize(bounds.right - (left + dp(56)), dp(30));
                    canvas.save();
                    canvas.translate(seekBarX = (left + dp(56 - 11)), seekBarY = (top + ty + dp(21)));
                    seekBar.draw(canvas);
                    canvas.restore();
                }
            }
            if (fileInfoText != null) {
                fileInfoText.draw(canvas, left + dp(56), top + ty + dp(15 + (isMusic ? 20 : 2) + 19));
            }
            radialProgress.setProgressRect(fileButtonX = (left + dp(2)), fileButtonY = (top + dp(5)),
                    left + dp(2) + dp(44), top + dp(5) + dp(44));
        } else {
            imageReceiver.setAlpha(alpha / 255f);
            imageReceiver.setImageCoords(bounds);
            imageReceiver.draw(canvas);

            if (isLocation && redLocationIcon != null) {
                int w = (int) (redLocationIcon.getIntrinsicWidth() * 0.8f);
                int h = (int) (redLocationIcon.getIntrinsicHeight() * 0.8f);
                int x = (int) (imageReceiver.getImageX() + (imageReceiver.getImageWidth() - w) / 2);
                int y = (int) (imageReceiver.getImageY() + (imageReceiver.getImageHeight() / 2 - h) - dp(16) * (1f - CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(imageReceiver.getCurrentAlpha())));
                redLocationIcon.setAlpha((int) (255 * Math.min(1, imageReceiver.getCurrentAlpha() * 5) * imageReceiver.getAlpha()));
                redLocationIcon.setBounds(x, y, x + w, y + h);
                redLocationIcon.draw(canvas);
            }

            radialProgress.setProgressRect(bounds.centerX() - dp(22), bounds.centerY() - dp(22),
                bounds.centerX() + dp(22), bounds.centerY() + dp(22));

            if (isVideo && videoDurationText != null) {
                canvas.drawRoundRect(
                    bounds.left + dp(6),
                    bounds.top + dp(6),
                    bounds.left + videoDurationText.getCurrentWidth() + dp(18),
                    bounds.top + dp(6 + 17),
                    dp(17 / 2f),
                    dp(17 / 2f),
                    durationBackgroundPaint);
                videoDurationText.draw(canvas, bounds.left + dp(12), bounds.top + dp(15));
            }
        }

        if (!isLocation) {
            if (messageObject != null && messageObject.isSending()) {
                long[] progress = ImageLoader.getInstance().getFileProgressSizes(attachPath);
                if (progress == null) {
                    radialProgress.setProgress(1.0f, true);
                    if (isMusic) {
                        setIconMini(MediaActionDrawable.ICON_CHECK, true);
                    } else {
                        setIcon(MediaActionDrawable.ICON_CHECK, true);
                    }
                }
            } else if (fileState != null && fileState.isLoading()) {
                if (isMusic) {
                    setIconMini(MediaActionDrawable.ICON_CANCEL, true);
                } else {
                    setIcon(MediaActionDrawable.ICON_CANCEL, true);
                }
            } else {
                if (isMusic) {
                    setIconMini(fileState != null && fileState.isExists() ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_DOWNLOAD, true);
                } else {
                    setIcon(getDefaultIcon(), true);
                }
            }

            if (isMusic) {
                setIcon(getDefaultIcon(), true);
            }
            radialProgress.draw(canvas);
        }
    }

    public boolean isFile() {
        return isFile;
    }

    public boolean isMusic() {
        return isMusic;
    }


    private int alpha = 255;

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }



    private final int TAG;

    private int getDefaultIcon() {
        if (isMusic && animatorIsPlaying.getValue()) {
            return MediaController.getInstance().isMessagePaused() ? MediaActionDrawable.ICON_PLAY : MediaActionDrawable.ICON_PAUSE;
        }

        return isVideo || isMusic ? MediaActionDrawable.ICON_PLAY :
            (isFile ? (fileState != null && fileState.isExists() ? MediaActionDrawable.ICON_FILE : MediaActionDrawable.ICON_DOWNLOAD) : MediaActionDrawable.ICON_NONE);
    }

    private int lastIcon;
    private int lastIconMini;

    private void setIcon(int icon, boolean animated) {
        if (lastIcon != icon) {
            lastIcon = icon;
            radialProgress.setIcon(icon, true, animated);
        }
    }

    private void setIconMini(int icon, boolean animated) {
        if (lastIconMini != icon) {
            lastIconMini = icon;
            radialProgress.setMiniIcon(icon, true, animated);
        }
    }


    @Override
    public void onFailedDownload(String fileName, boolean canceled) {
        checkFileState();
    }

    @Override
    public void onSuccessDownload(String fileName) {
        checkFileState();
    }

    public void checkFileState() {
        if (fileState != null) {
            fileState.checkState();
        }
        parent.invalidate();
    }

    @Override
    public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
        float progress = totalSize == 0 ? 0 : Math.min(1f, downloadSize / (float) totalSize);
        radialProgress.setProgress(progress, true);
        if (fileState != null) {
            fileState.checkState();
        }
        if (isMusic) {
            setIconMini(progress < 1f ? MediaActionDrawable.ICON_CANCEL : MediaActionDrawable.ICON_NONE, true);
        } else {
            setIcon(progress < 1f ? MediaActionDrawable.ICON_CANCEL : getDefaultIcon(), true);
        }
        parent.invalidate();
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {
        float progress = totalSize == 0 ? 0 : Math.min(1f, uploadedSize / (float) totalSize);
        radialProgress.setProgress(progress, true);
        if (fileState != null) {
            fileState.checkState();
        }
        if (isMusic) {
            setIconMini(progress < 1f ? MediaActionDrawable.ICON_CANCEL : MediaActionDrawable.ICON_NONE, true);
        } else {
            setIcon(progress < 1f ? MediaActionDrawable.ICON_CANCEL : getDefaultIcon(), true);
        }
        parent.invalidate();
    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    public boolean isPlaying() {
        return animatorIsPlaying.getValue();
    }

    /* */

    int lastTime;
    public void updatePlayingMessageProgress() {
        updatePlayingMessageProgress(true);
    }

    private void updatePlayingMessageProgress(boolean animated) {
        if (!isMusic || messageObject == null) {
            return;
        }

        final MessageObject currentPlayingObject = MediaController.getInstance().getPlayingMessageObject();
        final boolean isCurrentPlayingObject = isCurrentPlayingMessageMusic();
        final int currentProgress;

        animatorIsPlaying.setValue(isCurrentPlayingObject, animated);
        if (currentPlayingObject != null && isCurrentPlayingObject) {
            currentProgress = currentPlayingObject.audioProgressSec;

            if (!seekBar.isDragging()) {
                seekBar.setProgress(currentPlayingObject.audioProgress);
                seekBar.setBufferedProgress(currentPlayingObject.bufferedProgress);
            }
            seekBar.updateTimestamps(currentPlayingObject, null);
        } else {
            currentProgress = 0;
        }

        if (lastTime != currentProgress) {
            lastTime = currentProgress;
            fileInfo = AndroidUtilities.formatShortDuration(currentProgress, (int) musicDuration);
            checkFileTexts(true);
            parent.invalidate();
        }
    }

    private boolean isCurrentPlayingMessageMusic() {
        if (!isMusic) {
            return false;
        }

        if (MediaController.getInstance().isPlayingMessage(messageObject) && MediaController.getInstance().getPlayingMessageObject() != null) {
            return MediaController.getInstance().getPlayingMessageObject().isPlayingExplanationObject == isExplanation;
        }
        return false;
    }

    private int getCurrentPlayingProgress() {
        if (isCurrentPlayingMessageMusic()) {
            return MediaController.getInstance().getPlayingMessageObject().audioProgressSec;
        }
        return 0;
    }

    public boolean isDraggingSeekBar() {
        return seekBar.isDragging();
    }


    private int getThemedColor(int key) {
        return Theme.getColor(key);
    }

    @Override
    public void onSeekBarDrag(float progress) {
        if (isCurrentPlayingMessageMusic()) {
            MessageObject currentMessageObject = MediaController.getInstance().getPlayingMessageObject();
            MediaController.getInstance().seekToProgress(currentMessageObject, progress);
            updatePlayingMessageProgress();
        }
    }

    @Override
    public void onSeekBarContinuousDrag(float progress) {
        if (isCurrentPlayingMessageMusic()) {
            MessageObject currentMessageObject = MediaController.getInstance().getPlayingMessageObject();
            currentMessageObject.audioProgress = progress;
            currentMessageObject.audioProgressSec = (int) (currentMessageObject.getDuration() * progress);
            updatePlayingMessageProgress();
        }
    }

    @Override
    public void onSeekBarPressed() {
        parent.requestDisallowInterceptTouchEvent(true);
    }

    @Override
    public void onSeekBarReleased() {
        parent.requestDisallowInterceptTouchEvent(false);
    }
}
