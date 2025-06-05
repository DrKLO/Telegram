package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;

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
    public static final int TYPE_REPLACED_TO_FAVORITES = 6;
    public static final int TYPE_REPLACED_TO_FAVORITES_GIFS = 7;

    @IntDef(value = {TYPE_EMPTY, TYPE_REMOVED, TYPE_ARCHIVED, TYPE_ADDED, TYPE_REMOVED_FROM_RECENT, TYPE_REMOVED_FROM_FAVORITES, TYPE_ADDED_TO_FAVORITES, TYPE_REPLACED_TO_FAVORITES})
    public @interface Type {
    }

    public StickerSetBulletinLayout(@NonNull Context context, TLObject setObject, @Type int type) {
        this(context, setObject, 1, type, null, null);
    }

    public StickerSetBulletinLayout(@NonNull Context context, TLObject setObject, @Type int type, TLRPC.Document sticker, Theme.ResourcesProvider resourcesProvider) {
        this(context, setObject, 1, type, sticker, resourcesProvider);
    }

    public StickerSetBulletinLayout(@NonNull Context context, TLObject setObject, int count, @Type int type, TLRPC.Document sticker, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        TLRPC.StickerSet stickerSet;

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

        if (stickerSet == null && sticker != null) {
            TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(UserConfig.selectedAccount).getStickerSet(MessageObject.getInputStickerSet(sticker), true);
            if (set != null) {
                stickerSet = set.set;
            }
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

            if (object instanceof TLRPC.Document && (MessageObject.isAnimatedStickerDocument(sticker, true) || MessageObject.isVideoSticker(sticker) || MessageObject.isGifDocument(sticker))) {
                imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", imageLocation, null, 0, setObject);
            } else if (imageLocation != null && imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
                imageView.setImage(imageLocation, "50_50", "tgs", null, setObject);
            } else {
                imageView.setImage(imageLocation, "50_50", "webp", null, setObject);
            }
        } else {
            imageView.setImage(null, null, "webp", null, setObject);
        }

        if (MessageObject.isTextColorEmoji(sticker)) {
            imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        }

        switch (type) {
            case TYPE_ADDED:
                if (stickerSet != null) {
                    if (stickerSet.masks) {
                        titleTextView.setText(LocaleController.getString(R.string.AddMasksInstalled));
                        subtitleTextView.setText(LocaleController.formatString("AddMasksInstalledInfo", R.string.AddMasksInstalledInfo, stickerSet.title));
                    } else if (stickerSet.emojis) {
                        titleTextView.setText(LocaleController.getString(R.string.AddEmojiInstalled));
                        if (count > 1) {
                            subtitleTextView.setText(LocaleController.formatPluralString("AddEmojiMultipleInstalledInfo", count));
                        } else {
                            subtitleTextView.setText(LocaleController.formatString("AddEmojiInstalledInfo", R.string.AddEmojiInstalledInfo, stickerSet.title));
                        }
                    } else {
                        titleTextView.setText(LocaleController.getString(R.string.AddStickersInstalled));
                        subtitleTextView.setText(LocaleController.formatString("AddStickersInstalledInfo", R.string.AddStickersInstalledInfo, stickerSet.title));
                    }
                }
                break;
            case TYPE_REMOVED:
                if (stickerSet != null) {
                    if (stickerSet.masks) {
                        titleTextView.setText(LocaleController.getString(R.string.MasksRemoved));
                        subtitleTextView.setText(LocaleController.formatString("MasksRemovedInfo", R.string.MasksRemovedInfo, stickerSet.title));
                    } else if (stickerSet.emojis) {
                        titleTextView.setText(LocaleController.getString(R.string.EmojiRemoved));
                        if (count > 1) {
                            subtitleTextView.setText(LocaleController.formatPluralString("EmojiRemovedMultipleInfo", count));
                        } else {
                            subtitleTextView.setText(LocaleController.formatString("EmojiRemovedInfo", R.string.EmojiRemovedInfo, stickerSet.title));
                        }
                    } else {
                        titleTextView.setText(LocaleController.getString(R.string.StickersRemoved));
                        subtitleTextView.setText(LocaleController.formatString("StickersRemovedInfo", R.string.StickersRemovedInfo, stickerSet.title));
                    }
                }
                break;
            case TYPE_ARCHIVED:
                if (stickerSet != null) {
                    if (stickerSet.masks) {
                        titleTextView.setText(LocaleController.getString(R.string.MasksArchived));
                        subtitleTextView.setText(LocaleController.formatString("MasksArchivedInfo", R.string.MasksArchivedInfo, stickerSet.title));
                    } else if (stickerSet.emojis) {
                        titleTextView.setText(LocaleController.getString(R.string.EmojiArchived));
                        subtitleTextView.setText(LocaleController.formatString("EmojiArchivedInfo", R.string.EmojiArchivedInfo, stickerSet.title));
                    } else {
                        titleTextView.setText(LocaleController.getString(R.string.StickersArchived));
                        subtitleTextView.setText(LocaleController.formatString("StickersArchivedInfo", R.string.StickersArchivedInfo, stickerSet.title));
                    }
                }
                break;
            case TYPE_REMOVED_FROM_FAVORITES:
                titleTextView.setText(LocaleController.getString(R.string.RemovedFromFavorites));
                subtitleTextView.setVisibility(ViewPagerFixed.GONE);
                break;
            case TYPE_ADDED_TO_FAVORITES:
                titleTextView.setText(LocaleController.getString(R.string.AddedToFavorites));
                subtitleTextView.setVisibility(ViewPagerFixed.GONE);
                break;
            case TYPE_REPLACED_TO_FAVORITES:
                if (!UserConfig.getInstance(UserConfig.selectedAccount).isPremium() && !MessagesController.getInstance(UserConfig.selectedAccount).premiumFeaturesBlocked()) {
                    titleTextView.setText(LocaleController.formatString("LimitReachedFavoriteStickers", R.string.LimitReachedFavoriteStickers, MessagesController.getInstance(UserConfig.selectedAccount).stickersFavedLimitDefault));
                    CharSequence str = AndroidUtilities.premiumText(LocaleController.formatString("LimitReachedFavoriteStickersSubtitle", R.string.LimitReachedFavoriteStickersSubtitle, MessagesController.getInstance(UserConfig.selectedAccount).stickersFavedLimitPremium), () -> {
                        Activity activity = AndroidUtilities.findActivity(context);
                        if (activity instanceof LaunchActivity) {
                            ((LaunchActivity) activity).presentFragment(new PremiumPreviewFragment(LimitReachedBottomSheet.limitTypeToServerString(LimitReachedBottomSheet.TYPE_STICKERS)));
                        }
                    });
                    subtitleTextView.setText(str);
                } else {
                    titleTextView.setText(LocaleController.formatString("LimitReachedFavoriteStickers", R.string.LimitReachedFavoriteStickers, MessagesController.getInstance(UserConfig.selectedAccount).stickersFavedLimitPremium));
                    subtitleTextView.setText(LocaleController.formatString("LimitReachedFavoriteStickersSubtitlePremium", R.string.LimitReachedFavoriteStickersSubtitlePremium));
                }
                break;
            case TYPE_REPLACED_TO_FAVORITES_GIFS:
                final boolean isPremium = UserConfig.getInstance(UserConfig.selectedAccount).isPremium();
                if (!MessagesController.getInstance(UserConfig.selectedAccount).premiumFeaturesBlocked() && !isPremium) {
                    titleTextView.setText(LocaleController.formatString(R.string.LimitReachedFavoriteGifs, MessagesController.getInstance(UserConfig.selectedAccount).savedGifsLimitDefault));
                    CharSequence str = AndroidUtilities.premiumText(LocaleController.formatString(R.string.LimitReachedFavoriteGifsSubtitle, MessagesController.getInstance(UserConfig.selectedAccount).savedGifsLimitPremium), () -> {
                        Activity activity = AndroidUtilities.findActivity(context);
                        if (activity instanceof LaunchActivity) {
                            ((LaunchActivity) activity).presentFragment(new PremiumPreviewFragment(LimitReachedBottomSheet.limitTypeToServerString(LimitReachedBottomSheet.TYPE_GIFS)));
                        }
                    });
                    subtitleTextView.setText(str);
                } else {
                    titleTextView.setText(LocaleController.formatString(R.string.LimitReachedFavoriteGifs, isPremium ? MessagesController.getInstance(UserConfig.selectedAccount).savedGifsLimitPremium : MessagesController.getInstance(UserConfig.selectedAccount).savedGifsLimitDefault));
                    subtitleTextView.setText(LocaleController.getString(R.string.LimitReachedFavoriteGifsSubtitlePremium));
                }
                break;
            case TYPE_REMOVED_FROM_RECENT:
                titleTextView.setText(LocaleController.getString(R.string.RemovedFromRecent));
                subtitleTextView.setVisibility(ViewPagerFixed.GONE);
                break;
        }
    }
}
