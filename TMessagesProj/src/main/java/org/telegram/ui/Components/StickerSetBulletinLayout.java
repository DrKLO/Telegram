package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class StickerSetBulletinLayout extends Bulletin.TwoLineLayout {

    public static final int TYPE_EMPTY = -1;
    public static final int TYPE_REMOVED = 0;
    public static final int TYPE_ARCHIVED = 1;
    public static final int TYPE_ADDED = 2;
    public static final int TYPE_REMOVED_FROM_RECENT = 3;
    public static final int TYPE_REMOVED_FROM_FAVORITES = 4;
    public static final int TYPE_ADDED_TO_FAVORITES = 5;

    @IntDef(value = {TYPE_EMPTY, TYPE_REMOVED, TYPE_ARCHIVED, TYPE_ADDED, TYPE_REMOVED_FROM_RECENT, TYPE_REMOVED_FROM_FAVORITES, TYPE_ADDED_TO_FAVORITES})
    public @interface Type {}

    public StickerSetBulletinLayout(@NonNull Context context, TLObject setObject, @Type int type) {
        this(context, setObject, type, null, null);
    }

    public StickerSetBulletinLayout(@NonNull Context context, TLObject setObject, @Type int type, TLRPC.Document sticker, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        final TLRPC.StickerSet stickerSet;

        if (setObject instanceof TLRPC.TL_messages_stickerSet) {
            final TLRPC.TL_messages_stickerSet obj = (TLRPC.TL_messages_stickerSet) setObject;
            stickerSet = obj.set;
            final ArrayList<TLRPC.Document> documents = obj.documents;
            if (documents != null && !documents.isEmpty()) {
                sticker = documents.get(0);
            } else {
                sticker = null;
            }
        } else if (setObject instanceof TLRPC.StickerSetCovered) {
            final TLRPC.StickerSetCovered obj = (TLRPC.StickerSetCovered) setObject;
            stickerSet = obj.set;
            if (obj.cover != null) {
                sticker = obj.cover;
            } else if (!obj.covers.isEmpty()) {
                sticker = obj.covers.get(0);
            } else {
                sticker = null;
            }
        } else {
            if (sticker == null && setObject != null && BuildVars.DEBUG_VERSION) {
                throw new IllegalArgumentException("Invalid type of the given setObject: " + setObject.getClass());
            }
            stickerSet = null;
        }


        if (sticker != null) {
            TLObject object = stickerSet == null ? null : FileLoader.getClosestPhotoSizeWithSize(stickerSet.thumbs, 90);
            if (object == null) {
                object = sticker;
            }

            ImageLocation imageLocation;
            if (object instanceof TLRPC.Document) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                imageLocation = ImageLocation.getForDocument(thumb, sticker);
            } else {
                TLRPC.PhotoSize thumb = (TLRPC.PhotoSize) object;
                int thumbVersion = 0;
                if (setObject instanceof TLRPC.StickerSetCovered) {
                    thumbVersion = ((TLRPC.StickerSetCovered) setObject).set.thumb_version;
                } else if (setObject instanceof TLRPC.TL_messages_stickerSet) {
                    thumbVersion = ((TLRPC.TL_messages_stickerSet) setObject).set.thumb_version;
                }
                imageLocation = ImageLocation.getForSticker(thumb, sticker, thumbVersion);
            }

            if (object instanceof TLRPC.Document && MessageObject.isAnimatedStickerDocument(sticker, true)) {
                imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", imageLocation, null, 0, setObject);
            } else if (imageLocation != null && imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
                imageView.setImage(imageLocation, "50_50", "tgs", null, setObject);
            } else {
                imageView.setImage(imageLocation, "50_50", "webp", null, setObject);
            }
        } else {
            imageView.setImage(null, null, "webp", null, setObject);
        }

        switch (type) {
            case TYPE_ADDED:
                if (stickerSet.masks) {
                    titleTextView.setText(LocaleController.getString("AddMasksInstalled", R.string.AddMasksInstalled));
                    subtitleTextView.setText(LocaleController.formatString("AddMasksInstalledInfo", R.string.AddMasksInstalledInfo, stickerSet.title));
                } else {
                    titleTextView.setText(LocaleController.getString("AddStickersInstalled", R.string.AddStickersInstalled));
                    subtitleTextView.setText(LocaleController.formatString("AddStickersInstalledInfo", R.string.AddStickersInstalledInfo, stickerSet.title));
                }
                break;
            case TYPE_REMOVED:
                if (stickerSet.masks) {
                    titleTextView.setText(LocaleController.getString("MasksRemoved", R.string.MasksRemoved));
                    subtitleTextView.setText(LocaleController.formatString("MasksRemovedInfo", R.string.MasksRemovedInfo, stickerSet.title));
                } else {
                    titleTextView.setText(LocaleController.getString("StickersRemoved", R.string.StickersRemoved));
                    subtitleTextView.setText(LocaleController.formatString("StickersRemovedInfo", R.string.StickersRemovedInfo, stickerSet.title));
                }
                break;
            case TYPE_ARCHIVED:
                if (stickerSet.masks) {
                    titleTextView.setText(LocaleController.getString("MasksArchived", R.string.MasksArchived));
                    subtitleTextView.setText(LocaleController.formatString("MasksArchivedInfo", R.string.MasksArchivedInfo, stickerSet.title));
                } else {
                    titleTextView.setText(LocaleController.getString("StickersArchived", R.string.StickersArchived));
                    subtitleTextView.setText(LocaleController.formatString("StickersArchivedInfo", R.string.StickersArchivedInfo, stickerSet.title));
                }
                break;
            case TYPE_REMOVED_FROM_FAVORITES:
                titleTextView.setText(LocaleController.getString("RemovedFromFavorites", R.string.RemovedFromFavorites));
                subtitleTextView.setVisibility(ViewPagerFixed.GONE);
                break;
            case TYPE_ADDED_TO_FAVORITES:
                titleTextView.setText(LocaleController.getString("AddedToFavorites", R.string.AddedToFavorites));
                subtitleTextView.setVisibility(ViewPagerFixed.GONE);
                break;
            case TYPE_REMOVED_FROM_RECENT:
                titleTextView.setText(LocaleController.getString("RemovedFromRecent", R.string.RemovedFromRecent));
                subtitleTextView.setVisibility(ViewPagerFixed.GONE);
                break;
        }
    }
}
