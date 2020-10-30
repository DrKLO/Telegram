package org.telegram.ui.Components;

import android.content.Context;
import android.widget.FrameLayout;

import androidx.core.util.Preconditions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

public final class BulletinFactory {

    public static BulletinFactory of(BaseFragment fragment) {
        Preconditions.checkNotNull(fragment);
        return new BulletinFactory(fragment);
    }

    public static BulletinFactory of(FrameLayout containerLayout) {
        Preconditions.checkNotNull(containerLayout);
        return new BulletinFactory(containerLayout);
    }

    public static boolean canShowBulletin(BaseFragment fragment) {
        return fragment != null && fragment.getParentActivity() != null && fragment.getLayoutContainer() != null;
    }

    public enum FileType {
        PHOTO("PhotoSavedHint", R.string.PhotoSavedHint),
        PHOTO_TO_DOWNLOADS("PhotoSavedToDownloadsHint", R.string.PhotoSavedToDownloadsHint),
        PHOTOS("PhotosSavedHint"),

        VIDEO("VideoSavedHint", R.string.VideoSavedHint),
        VIDEO_TO_DOWNLOADS("VideoSavedToDownloadsHint", R.string.VideoSavedToDownloadsHint),
        VIDEOS("VideosSavedHint"),

        AUDIO("AudioSavedHint", R.string.AudioSavedHint),
        AUDIOS("AudiosSavedHint"),
        GIF("GifSavedToDownloadsHint", R.string.GifSavedToDownloadsHint),
        MEDIA("MediaSavedHint"),

        UNKNOWN("FileSavedHint", R.string.FileSavedHint),
        UNKNOWNS("FilesSavedHint");

        private final String localeKey;
        private final int localeRes;
        private final boolean plural;

        FileType(String localeKey, int localeRes) {
            this.localeKey = localeKey;
            this.localeRes = localeRes;
            this.plural = false;
        }

        FileType(String localeKey) {
            this.localeKey = localeKey;
            this.localeRes = 0;
            this.plural = true;
        }

        private String getText() {
            return getText(1);
        }

        private String getText(int amount) {
            if (plural) {
                return LocaleController.formatPluralString(localeKey, amount);
            } else {
                return LocaleController.getString(localeKey, localeRes);
            }
        }
    }

    private final BaseFragment fragment;
    private final FrameLayout containerLayout;

    private BulletinFactory(BaseFragment fragment) {
        this.fragment = fragment;
        this.containerLayout = null;
    }

    private BulletinFactory(FrameLayout containerLayout) {
        this.containerLayout = containerLayout;
        this.fragment = null;
    }

    public Bulletin createDownloadBulletin(FileType fileType) {
        return createDownloadBulletin(fileType, 1);
    }

    public Bulletin createDownloadBulletin(FileType fileType, int filesAmount) {
        return createDownloadBulletin(fileType, filesAmount, 0, 0);
    }

    public Bulletin createDownloadBulletin(FileType fileType, int filesAmount, int backgroundColor, int textColor) {
        final Bulletin.LottieLayout layout;
        if (backgroundColor != 0 && textColor != 0) {
            layout = new Bulletin.LottieLayout(getContext(), backgroundColor, textColor);
        } else {
            layout = new Bulletin.LottieLayout(getContext());
        }
        layout.setAnimation(R.raw.ic_download, "Box", "Arrow", "Mask", "Arrow 2", "Splash");
        layout.textView.setText(fileType.getText(filesAmount));
        return create(layout, Bulletin.DURATION_SHORT);
    }

    private Bulletin create(Bulletin.Layout layout, int duration) {
        if (fragment != null) {
            return Bulletin.make(fragment, layout, duration);
        } else {
            return Bulletin.make(containerLayout, layout, duration);
        }
    }

    private Context getContext() {
        return fragment != null ? fragment.getParentActivity() : containerLayout.getContext();
    }

    //region Static Factory
    public static Bulletin createMuteBulletin(BaseFragment fragment, int setting) {
        Preconditions.checkArgument(canShowBulletin(fragment));
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity());

        final String text;
        final boolean mute;

        switch (setting) {
            case NotificationsController.SETTING_MUTE_HOUR:
                text = LocaleController.formatString("NotificationsMutedForHint", R.string.NotificationsMutedForHint, LocaleController.formatPluralString("Hours", 1));
                mute = true;
                break;
            case NotificationsController.SETTING_MUTE_8_HOURS:
                text = LocaleController.formatString("NotificationsMutedForHint", R.string.NotificationsMutedForHint, LocaleController.formatPluralString("Hours", 8));
                mute = true;
                break;
            case NotificationsController.SETTING_MUTE_2_DAYS:
                text = LocaleController.formatString("NotificationsMutedForHint", R.string.NotificationsMutedForHint, LocaleController.formatPluralString("Days", 2));
                mute = true;
                break;
            case NotificationsController.SETTING_MUTE_FOREVER:
                text = LocaleController.getString("NotificationsMutedHint", R.string.NotificationsMutedHint);
                mute = true;
                break;
            case NotificationsController.SETTING_MUTE_UNMUTE:
                text = LocaleController.getString("NotificationsUnmutedHint", R.string.NotificationsUnmutedHint);
                mute = false;
                break;
            default:
                throw new IllegalArgumentException();
        }

        if (mute) {
            layout.setAnimation(R.raw.ic_mute, "Body Main", "Body Top", "Line", "Curve Big", "Curve Small");
        } else {
            layout.setAnimation(R.raw.ic_unmute, "BODY", "Wibe Big", "Wibe Big 3", "Wibe Small");
        }

        layout.textView.setText(text);
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    public static Bulletin createMuteBulletin(BaseFragment fragment, boolean muted) {
        return createMuteBulletin(fragment, muted ? NotificationsController.SETTING_MUTE_FOREVER : NotificationsController.SETTING_MUTE_UNMUTE);
    }

    public static Bulletin createDeleteMessagesBulletin(BaseFragment fragment, int count) {
        Preconditions.checkArgument(canShowBulletin(fragment));
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity());
        layout.setAnimation(R.raw.ic_delete, "Envelope", "Cover", "Bucket");
        layout.textView.setText(LocaleController.formatPluralString("MessagesDeletedHint", count));
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    public static Bulletin createUnpinAllMessagesBulletin(BaseFragment fragment, int count, boolean hide, Runnable undoAction, Runnable delayedAction) {
        Preconditions.checkArgument(canShowBulletin(fragment));
        Bulletin.ButtonLayout buttonLayout;
        if (hide) {
            final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(fragment.getParentActivity());
            layout.setAnimation(R.raw.ic_unpin, "Pin", "Line");
            layout.titleTextView.setText(LocaleController.getString("PinnedMessagesHidden", R.string.PinnedMessagesHidden));
            layout.subtitleTextView.setText(LocaleController.getString("PinnedMessagesHiddenInfo", R.string.PinnedMessagesHiddenInfo));
            buttonLayout = layout;
        } else {
            final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity());
            layout.setAnimation(R.raw.ic_unpin, "Pin", "Line");
            layout.textView.setText(LocaleController.formatPluralString("MessagesUnpinned", count));
            buttonLayout = layout;
        }
        buttonLayout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true).setUndoAction(undoAction).setDelayedAction(delayedAction));
        return Bulletin.make(fragment, buttonLayout, 5000);
    }

    public static Bulletin createSaveToGalleryBulletin(BaseFragment fragment, boolean video) {
        return of(fragment).createDownloadBulletin(video ? FileType.VIDEO : FileType.PHOTO);
    }

    public static Bulletin createSaveToGalleryBulletin(FrameLayout containerLayout, boolean video, int backgroundColor, int textColor) {
        return of(containerLayout).createDownloadBulletin(video ? FileType.VIDEO : FileType.PHOTO, 1, backgroundColor, textColor);
    }

    public static Bulletin createPromoteToAdminBulletin(BaseFragment fragment, String userFirstName) {
        Preconditions.checkArgument(canShowBulletin(fragment));
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity());
        layout.setAnimation(R.raw.ic_admin, "Shield");
        layout.textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("UserSetAsAdminHint", R.string.UserSetAsAdminHint, userFirstName)));
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    public static Bulletin createPinMessageBulletin(BaseFragment fragment) {
        return createPinMessageBulletin(fragment, true, null, null);
    }

    public static Bulletin createUnpinMessageBulletin(BaseFragment fragment, Runnable undoAction, Runnable delayedAction) {
        return createPinMessageBulletin(fragment, false, undoAction, delayedAction);
    }

    private static Bulletin createPinMessageBulletin(BaseFragment fragment, boolean pinned, Runnable undoAction, Runnable delayedAction) {
        Preconditions.checkArgument(canShowBulletin(fragment));
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity());
        layout.setAnimation(pinned ? R.raw.ic_pin : R.raw.ic_unpin, "Pin", "Line");
        layout.textView.setText(LocaleController.getString(pinned ? "MessagePinnedHint" : "MessageUnpinnedHint", pinned ? R.string.MessagePinnedHint : R.string.MessageUnpinnedHint));
        if (!pinned) {
            layout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true).setUndoAction(undoAction).setDelayedAction(delayedAction));
        }
        return Bulletin.make(fragment, layout, pinned ? Bulletin.DURATION_SHORT : 5000);
    }
    //endregion
}
