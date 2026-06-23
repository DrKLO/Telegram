package org.Tajgram.ui.Components;

import android.content.Context;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.DocumentObject;
import org.Tajgram.messenger.ImageLocation;
import org.Tajgram.messenger.MediaDataController;
import org.Tajgram.messenger.NotificationCenter;
import org.Tajgram.messenger.SvgHelper;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.ui.ActionBar.Theme;

public class StickerImageView extends BackupImageView implements NotificationCenter.NotificationCenterDelegate {

    int currentAccount;
    int stickerNum;
    String stickerPackName = AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME;

    public StickerImageView(Context context, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
    }

    public void setStickerNum(int stickerNum) {
        if (this.stickerNum != stickerNum) {
            this.stickerNum = stickerNum;
            setSticker();
        }
    }

    public void setStickerPackName(String stickerPackName) {
        this.stickerPackName = stickerPackName;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setSticker();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.diceStickersDidLoad) {
            String name = (String) args[0];
            if (stickerPackName.equals(name)) {
                setSticker();
            }
        }
    }

    public void setSticker() {
        String imageFilter = null;
        TLRPC.Document document = null;
        TLRPC.TL_messages_stickerSet set = null;

        set = MediaDataController.getInstance(currentAccount).getStickerSetByName(stickerPackName);
        if (set == null) {
            set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(stickerPackName);
        }
        if (set != null && set.documents.size() > stickerNum) {
            document = set.documents.get(stickerNum);
        }
        imageFilter = "130_130";

        SvgHelper.SvgDrawable svgThumb = null;
        if (document != null) {
            svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_emptyListPlaceholder, 0.2f);
        }
        if (svgThumb != null) {
            svgThumb.overrideWidthAndHeight(512, 512);
        }

        if (document != null) {
            ImageLocation imageLocation = ImageLocation.getForDocument(document);
            setImage(imageLocation, imageFilter, "tgs", svgThumb, set);
        } else {
            imageReceiver.clearImage();
            MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(stickerPackName, false, set == null);
        }
    }
}
