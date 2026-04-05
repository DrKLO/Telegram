package org.telegram.ui.Components.poll.buttons;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarsListDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;

import java.util.List;

import me.vkryl.android.animator.BoolAnimator;

public class PollButtonDrawable extends Drawable implements DownloadController.FileDownloadProgressListener {
    private final AnimatedTextView.AnimatedTextDrawable votersCountDrawable;
    private final AvatarsListDrawable lastVotersDrawable;
    private final ImageReceiver imageReceiver;
    private final View parent;
    private final int currentAccount;
    private boolean hasMediaPadding;
    private boolean hasMedia;
    private boolean isVideo;
    private final Paint darkenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BoolAnimator animatorShowVoters;
    private final RadialProgress2 radialProgress;
    private MessageObject messageObject;

    private String attachFileName;
    private String attachPath;

    private final int TAG;
    private boolean needDrawProgress;

    public PollButtonDrawable(int currentAccount, View parent) {
        this.currentAccount = currentAccount;
        this.parent = parent;
        animatorShowVoters = new BoolAnimator(parent, CubicBezierInterpolator.EASE_OUT_QUINT, 380L);

        votersCountDrawable = new AnimatedTextView.AnimatedTextDrawable();
        votersCountDrawable.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        votersCountDrawable.setTextSize(dp(11));
        votersCountDrawable.setCallback(parent);
        lastVotersDrawable = new AvatarsListDrawable(currentAccount, parent, dp(18), dp(8.33f), dpf2(1));
        imageReceiver = new ImageReceiver(parent);
        imageReceiver.setRoundRadius(dp(5));
        darkenPaint.setColor(0x60000000);
        radialProgress = new RadialProgress2(parent);
        radialProgress.setCircleRadius(dp(18));
        radialProgress.setProgressColor(Color.WHITE);

        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
    }

    public void setVotersVisible(boolean visible, boolean animated) {
        animatorShowVoters.setValue(visible, animated);
    }

    public void attach() {
        lastVotersDrawable.attach();
        imageReceiver.onAttachedToWindow();
        radialProgress.onAttachedToWindow();
    }

    public void detach() {
        lastVotersDrawable.detach();
        imageReceiver.onDetachedFromWindow();
        radialProgress.onDetachedFromWindow();
    }

    public void setVotersCountTextColor(int color) {
        votersCountDrawable.setTextColor(color);
    }

    public void setHasMediaPadding(boolean hasMediaPadding) {
        this.hasMediaPadding = hasMediaPadding;
    }

    public void setMedia(MessageObject messageObject, TLRPC.MessageMedia media, Object parentObject, String attachPath, boolean animated) {
        this.messageObject = messageObject;

        final String attachFileNameOld = attachFileName;

        needDrawProgress = false;
        attachFileName = null;
        hasMedia = setMediaImpl(media, parentObject, attachPath);
        if (!hasMedia) {
            imageReceiver.clearImage();
        }

        radialProgress.setColors(
            isVideo ? 0 : Theme.getColor(Theme.key_chat_mediaLoaderPhoto),
            isVideo ? 0 : Theme.getColor(Theme.key_chat_mediaLoaderPhotoSelected),
            Theme.getColor(Theme.key_chat_mediaLoaderPhotoIcon),
            Theme.getColor(Theme.key_chat_mediaLoaderPhotoIconSelected));

        if (!TextUtils.equals(attachFileNameOld, attachFileName)) {
            if (!TextUtils.isEmpty(attachFileNameOld)) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            }
            if (!TextUtils.isEmpty(attachFileName)) {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(attachFileName, this);
            }
        }
        checkIcon(animated);
    }

    public boolean isHasMedia() {
        return hasMedia;
    }

    public ImageReceiver getImageReceiver() {
        return imageReceiver;
    }

    private boolean setMediaImpl(TLRPC.MessageMedia media, Object parentObject, String attachPath) {
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty) {
            return false;
        }

        isVideo = false;
        this.attachPath = attachPath;

        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            final TLRPC.TL_messageMediaPhoto messageMediaPhoto = (TLRPC.TL_messageMediaPhoto) media;

            final TLRPC.Photo photoObject = messageMediaPhoto.photo;
            final TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(photoObject.sizes, 40);
            final TLRPC.PhotoSize currentPhotoObjectObject = FileLoader.getClosestPhotoSizeWithSize(photoObject.sizes, dp(36),
                    false, currentPhotoObjectThumb , true);

            needDrawProgress = true;
            attachFileName = !TextUtils.isEmpty(attachPath) ? attachPath : MessageObject.getFileName(media);
            imageReceiver.setImage(
                ImageLocation.getForObject(currentPhotoObjectObject, photoObject),
                "36_36",
                ImageLocation.getForObject(currentPhotoObjectThumb, photoObject),
                "36_36_b",
                null, // currentPhotoObjectThumbStripped
                currentPhotoObjectObject.size,
                null,
                parentObject,
                1);
            return true;
        } else if (media instanceof TLRPC.TL_messageMediaDocument) {
            final TLRPC.TL_messageMediaDocument messageMediaDocument = (TLRPC.TL_messageMediaDocument) media;
            final TLRPC.Document document = messageMediaDocument.document;
            if (document == null) {
                return false;
            }

            attachFileName = !TextUtils.isEmpty(attachPath) ? attachPath : MessageObject.getFileName(media);
            if (MessageObject.isVideoDocument(messageMediaDocument.document)) {
                final TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 40);
                final TLRPC.PhotoSize currentPhotoObjectObject = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(36),
                        false, currentPhotoObjectThumb , true);

                isVideo = true;
                needDrawProgress = true;
                imageReceiver.setImage(
                    ImageLocation.getForObject(currentPhotoObjectObject, document),
                    "36_36",
                    ImageLocation.getForObject(currentPhotoObjectThumb, document),
                    "36_36_b",
                    null, // currentPhotoObjectThumbStripped
                    currentPhotoObjectObject != null ? currentPhotoObjectObject.size : 0,
                    null,
                    parentObject,
                    1);
                return true;
            }


            final boolean isWebpSticker = MessageObject.isStickerDocument(document) || MessageObject.isVideoSticker(document);
            final boolean isAnimatedSticker = MessageObject.isAnimatedStickerDocument(document, true);

            if (isWebpSticker || isAnimatedSticker) {
                final Drawable thumb = DocumentObject.getSvgThumb(document, Theme.key_chat_serviceBackground, 1.0f);
                imageReceiver.setImage(ImageLocation.getForDocument(document), "36_36", thumb,
                    document.size, isWebpSticker ? "webp" : null, parentObject, 1);
                return true;
            }

        } else if (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaVenue) {
            if (media.geo != null) {
                WebFile currentWebFile = WebFile.createWithGeoPoint(media.geo, 36, 36,
                        13, Math.min(2, (int) Math.ceil(AndroidUtilities.density)));
                imageReceiver.setImage(ImageLocation.getForWebFile(currentWebFile), (String) null, (ImageLocation) null, null, (Drawable) null, parentObject, 0);
                return true;
            }
        }

        return false;
    }




    public void setVotersCount(int count, boolean animated) {
        votersCountDrawable.setText(count > 0 ? LocaleController.formatShortNumber(count, null) : null, animated);
    }

    private int recentVotersCount;

    public void setRecentVoters(List<TLRPC.Peer> recentVoters, boolean animated) {
        recentVotersCount = recentVoters != null ? recentVoters.size() : 0;
        lastVotersDrawable.set(recentVoters, animated);
    }

    public float getVotersCountTargetWidth() {
        return votersCountDrawable.getAnimateToWidth() + (recentVotersCount > 0 ? dp(8.66f + (18 - 8.66f) * recentVotersCount) : 0);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        draw(canvas, null);
    }

    public void draw(@NonNull Canvas canvas, @Nullable Paint avatarsPaint) {
        final Rect bounds = getBounds();

        final int rightPadding = dp(hasMediaPadding ? 56.33f : 19);



        if (animatorShowVoters.getFloatValue() > 0) {
            final float lastVotersVisibility = lastVotersDrawable.getTotalVisibility();
            final float lastVotersWidth = lastVotersDrawable.getAnimatedWidth();
            final int votersCountRight = bounds.right - rightPadding
                    - lerp(dp(2), (int) lastVotersWidth + dp(4), lastVotersVisibility);

            if (lastVotersVisibility > 0) {
                lastVotersDrawable.setAlpha((int) (animatorShowVoters.getFloatValue() * 255));
                lastVotersDrawable.setBounds(
                        bounds.right - rightPadding - (int) lastVotersWidth,
                        bounds.bottom - dp(31.33f),
                        bounds.right - rightPadding,
                        bounds.bottom
                );
                lastVotersDrawable.draw(canvas, avatarsPaint);
            }

            int cy = bounds.bottom - dp(23);
            votersCountDrawable.setAlpha((int) (animatorShowVoters.getFloatValue() * 255));
            votersCountDrawable.setBounds(bounds.left, cy + dp(15), votersCountRight, cy - dp(15));
            votersCountDrawable.draw(canvas);
        }


        if (hasMedia) {
            final int mediaSize = dp(36);
            AndroidUtilities.rectTmp2.set(
                bounds.right - dp(9) - mediaSize,
                bounds.bottom - dp(4) - mediaSize,
                bounds.right - dp(9),
                bounds.bottom - dp(4));
            AndroidUtilities.rectTmp.set(AndroidUtilities.rectTmp2);

            radialProgress.setProgressRect(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top, AndroidUtilities.rectTmp.right, AndroidUtilities.rectTmp.bottom);
            imageReceiver.setImageCoords(AndroidUtilities.rectTmp2);
            imageReceiver.draw(canvas);

            if (isVideo) {
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(5), dp(5), darkenPaint);
            }

            checkIcon(true);
            if (needDrawProgress) {
                radialProgress.draw(canvas);
            }
        }
    }

    private void checkIcon(boolean animated) {
        if (messageObject.isSending() || messageObject.isEditing()) {

        } else if (!TextUtils.isEmpty(attachFileName) && FileLoader.getInstance(currentAccount).isLoadingFile(attachFileName)) {
            setIcon(MediaActionDrawable.ICON_CANCEL, animated);
        } else {
            setIcon(getDefaultIcon(), animated);
        }
    }

    public boolean verifyDrawable(Drawable who) {
        return who == this || who == votersCountDrawable || who == lastVotersDrawable;
    }


    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }


    private int getDefaultIcon() {
        return isVideo ? MediaActionDrawable.ICON_PLAY : MediaActionDrawable.ICON_NONE;
    }

    private int lastIcon;
    private void setIcon(int icon, boolean animated) {
        if (lastIcon != icon) {
            lastIcon = icon;
            radialProgress.setIcon(icon, true, animated);
        }
    }


    @Override
    public void onFailedDownload(String fileName, boolean canceled) {

    }

    @Override
    public void onSuccessDownload(String fileName) {

    }

    @Override
    public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
        float progress = totalSize == 0 ? 0 : Math.min(1f, downloadSize / (float) totalSize);
        radialProgress.setProgress(progress, true);
        setIcon(progress < 1f ? MediaActionDrawable.ICON_CANCEL : getDefaultIcon(), true);
        parent.invalidate();
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {
        float progress = totalSize == 0 ? 0 : Math.min(1f, uploadedSize / (float) totalSize);
        radialProgress.setProgress(progress, true);
        setIcon(progress < 1f ? MediaActionDrawable.ICON_CANCEL : getDefaultIcon(), true);
        parent.invalidate();
    }

    @Override
    public int getObserverTag() {
        return TAG;
    }
}
