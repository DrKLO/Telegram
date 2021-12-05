package org.telegram.ui.Components;

import android.content.Context;
import android.widget.FrameLayout;

import androidx.annotation.CheckResult;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public final class BulletinFactory {

    public static BulletinFactory of(BaseFragment fragment) {
        return new BulletinFactory(fragment);
    }

    public static BulletinFactory of(FrameLayout containerLayout, Theme.ResourcesProvider resourcesProvider) {
        return new BulletinFactory(containerLayout, resourcesProvider);
    }

    public static boolean canShowBulletin(BaseFragment fragment) {
        return fragment != null && fragment.getParentActivity() != null && fragment.getLayoutContainer() != null;
    }

    public static final int ICON_TYPE_NOT_FOUND = 0;
    public static final int ICON_TYPE_WARNING = 1;

    public enum FileType {

        PHOTO("PhotoSavedHint", R.string.PhotoSavedHint, Icon.SAVED_TO_GALLERY),
        PHOTOS("PhotosSavedHint", Icon.SAVED_TO_GALLERY),

        VIDEO("VideoSavedHint", R.string.VideoSavedHint, Icon.SAVED_TO_GALLERY),
        VIDEOS("VideosSavedHint", Icon.SAVED_TO_GALLERY),

        MEDIA("MediaSavedHint", Icon.SAVED_TO_GALLERY),

        PHOTO_TO_DOWNLOADS("PhotoSavedToDownloadsHint", R.string.PhotoSavedToDownloadsHint, Icon.SAVED_TO_DOWNLOADS),
        VIDEO_TO_DOWNLOADS("VideoSavedToDownloadsHint", R.string.VideoSavedToDownloadsHint, Icon.SAVED_TO_DOWNLOADS),

        GIF("GifSavedToDownloadsHint", Icon.SAVED_TO_DOWNLOADS),

        AUDIO("AudioSavedHint", R.string.AudioSavedHint, Icon.SAVED_TO_MUSIC),
        AUDIOS("AudiosSavedHint", Icon.SAVED_TO_MUSIC),

        UNKNOWN("FileSavedHint", R.string.FileSavedHint, Icon.SAVED_TO_DOWNLOADS),
        UNKNOWNS("FilesSavedHint", Icon.SAVED_TO_DOWNLOADS);

        private final String localeKey;
        private final int localeRes;
        private final boolean plural;
        private final Icon icon;

        FileType(String localeKey, int localeRes, Icon icon) {
            this.localeKey = localeKey;
            this.localeRes = localeRes;
            this.icon = icon;
            this.plural = false;
        }

        FileType(String localeKey, Icon icon) {
            this.localeKey = localeKey;
            this.icon = icon;
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

        private enum Icon {

            SAVED_TO_DOWNLOADS(R.raw.ic_download, 2, "Box", "Arrow"),
            SAVED_TO_GALLERY(R.raw.ic_save_to_gallery, 0, "Box", "Arrow", "Mask", "Arrow 2", "Splash"),
            SAVED_TO_MUSIC(R.raw.ic_save_to_music, 2, "Box", "Arrow");

            private final int resId;
            private final String[] layers;
            private final int paddingBottom;

            Icon(int resId, int paddingBottom, String... layers) {
                this.resId = resId;
                this.paddingBottom = paddingBottom;
                this.layers = layers;
            }
        }
    }

    private final BaseFragment fragment;
    private final FrameLayout containerLayout;
    private final Theme.ResourcesProvider resourcesProvider;

    private BulletinFactory(BaseFragment fragment) {
        this.fragment = fragment;
        this.containerLayout = null;
        this.resourcesProvider = fragment == null ? null : fragment.getResourceProvider();
    }

    private BulletinFactory(FrameLayout containerLayout, Theme.ResourcesProvider resourcesProvider) {
        this.containerLayout = containerLayout;
        this.fragment = null;
        this.resourcesProvider = resourcesProvider;
    }

    public Bulletin createSimpleBulletin(int iconRawId, String text) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(iconRawId, 36, 36);
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        return create(layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public Bulletin createDownloadBulletin(FileType fileType) {
        return createDownloadBulletin(fileType, resourcesProvider);
    }

    @CheckResult
    public Bulletin createDownloadBulletin(FileType fileType, Theme.ResourcesProvider resourcesProvider) {
        return createDownloadBulletin(fileType, 1, resourcesProvider);
    }

    @CheckResult
    public Bulletin createDownloadBulletin(FileType fileType, int filesAmount, Theme.ResourcesProvider resourcesProvider) {
        return createDownloadBulletin(fileType, filesAmount, 0, 0, resourcesProvider);
    }

    public Bulletin createReportSent(Theme.ResourcesProvider resourcesProvider) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(R.raw.chats_infotip);
        layout.textView.setText(LocaleController.getString("ReportChatSent", R.string.ReportChatSent));
        return create(layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public Bulletin createDownloadBulletin(FileType fileType, int filesAmount, int backgroundColor, int textColor) {
        return createDownloadBulletin(fileType, filesAmount, backgroundColor, textColor, null);
    }

    @CheckResult
    public Bulletin createDownloadBulletin(FileType fileType, int filesAmount, int backgroundColor, int textColor, Theme.ResourcesProvider resourcesProvider) {
        final Bulletin.LottieLayout layout;
        if (backgroundColor != 0 && textColor != 0) {
            layout = new Bulletin.LottieLayout(getContext(), resourcesProvider, backgroundColor, textColor);
        } else {
            layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        }
        layout.setAnimation(fileType.icon.resId, fileType.icon.layers);
        layout.textView.setText(fileType.getText(filesAmount));
        if (fileType.icon.paddingBottom != 0) {
            layout.setIconPaddingBottom(fileType.icon.paddingBottom);
        }
        return create(layout, Bulletin.DURATION_SHORT);
    }

    public Bulletin createErrorBulletin(CharSequence errorMessage) {
        return createErrorBulletin(errorMessage, null);
    }

    public Bulletin createErrorBulletin(CharSequence errorMessage, Theme.ResourcesProvider resourcesProvider) {
        Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(R.raw.chats_infotip);
        layout.textView.setText(errorMessage);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        return create(layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public Bulletin createCopyLinkBulletin() {
        return createCopyLinkBulletin(false, resourcesProvider);
    }

    @CheckResult
    public Bulletin createCopyBulletin(String message) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), null);
        layout.setAnimation(R.raw.copy, 36, 36, "NULL ROTATION", "Back", "Front");
        layout.textView.setText(message);
        return create(layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public Bulletin createCopyLinkBulletin(boolean isPrivate, Theme.ResourcesProvider resourcesProvider) {
        if (isPrivate) {
            final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
            layout.setAnimation(R.raw.voip_invite, 36, 36, "Wibe", "Circle");
            layout.titleTextView.setText(LocaleController.getString("LinkCopied", R.string.LinkCopied));
            layout.subtitleTextView.setText(LocaleController.getString("LinkCopiedPrivateInfo", R.string.LinkCopiedPrivateInfo));
            return create(layout, Bulletin.DURATION_LONG);
        } else {
            final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
            layout.setAnimation(R.raw.voip_invite, 36, 36, "Wibe", "Circle");
            layout.textView.setText(LocaleController.getString("LinkCopied", R.string.LinkCopied));
            return create(layout, Bulletin.DURATION_SHORT);
        }
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

    @CheckResult
    public static Bulletin createMuteBulletin(BaseFragment fragment, int setting) {
        return createMuteBulletin(fragment, setting, null);
    }

    @CheckResult
    public static Bulletin createMuteBulletin(BaseFragment fragment, int setting, Theme.ResourcesProvider resourcesProvider) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);

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

    @CheckResult
    public static Bulletin createMuteBulletin(BaseFragment fragment, boolean muted, Theme.ResourcesProvider resourcesProvider) {
        return createMuteBulletin(fragment, muted ? NotificationsController.SETTING_MUTE_FOREVER : NotificationsController.SETTING_MUTE_UNMUTE, resourcesProvider);
    }

    @CheckResult
    public static Bulletin createDeleteMessagesBulletin(BaseFragment fragment, int count, Theme.ResourcesProvider resourcesProvider) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);
        layout.setAnimation(R.raw.ic_delete, "Envelope", "Cover", "Bucket");
        layout.textView.setText(LocaleController.formatPluralString("MessagesDeletedHint", count));
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public static Bulletin createUnpinAllMessagesBulletin(BaseFragment fragment, int count, boolean hide, Runnable undoAction, Runnable delayedAction, Theme.ResourcesProvider resourcesProvider) {
        if (fragment.getParentActivity() == null) {
            if (delayedAction != null) {
                delayedAction.run();
            }
            return null;
        }
        Bulletin.ButtonLayout buttonLayout;
        if (hide) {
            final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(fragment.getParentActivity(), resourcesProvider);
            layout.setAnimation(R.raw.ic_unpin, 28, 28, "Pin", "Line");
            layout.titleTextView.setText(LocaleController.getString("PinnedMessagesHidden", R.string.PinnedMessagesHidden));
            layout.subtitleTextView.setText(LocaleController.getString("PinnedMessagesHiddenInfo", R.string.PinnedMessagesHiddenInfo));
            buttonLayout = layout;
        } else {
            final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);
            layout.setAnimation(R.raw.ic_unpin, 28, 28, "Pin", "Line");
            layout.textView.setText(LocaleController.formatPluralString("MessagesUnpinned", count));
            buttonLayout = layout;
        }
        buttonLayout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true, resourcesProvider).setUndoAction(undoAction).setDelayedAction(delayedAction));
        return Bulletin.make(fragment, buttonLayout, 5000);
    }

    @CheckResult
    public static Bulletin createSaveToGalleryBulletin(BaseFragment fragment, boolean video, Theme.ResourcesProvider resourcesProvider) {
        return of(fragment).createDownloadBulletin(video ? FileType.VIDEO : FileType.PHOTO, resourcesProvider);
    }

    @CheckResult
    public static Bulletin createSaveToGalleryBulletin(FrameLayout containerLayout, boolean video, int backgroundColor, int textColor) {
        return of(containerLayout, null).createDownloadBulletin(video ? FileType.VIDEO : FileType.PHOTO, 1, backgroundColor, textColor);
    }

    @CheckResult
    public static Bulletin createPromoteToAdminBulletin(BaseFragment fragment, String userFirstName) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), null);
        layout.setAnimation(R.raw.ic_admin, "Shield");
        layout.textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("UserSetAsAdminHint", R.string.UserSetAsAdminHint, userFirstName)));
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public static Bulletin createRemoveFromChatBulletin(BaseFragment fragment, TLRPC.User user, String chatName) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), null);
        layout.setAnimation(R.raw.ic_ban, "Hand");
        String name;
        if (user.deleted) {
            name = LocaleController.formatString("HiddenName", R.string.HiddenName);
        } else {
            name = user.first_name;
        }
        layout.textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("UserRemovedFromChatHint", R.string.UserRemovedFromChatHint, name, chatName)));
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public static Bulletin createBanBulletin(BaseFragment fragment, boolean banned) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), null);
        final String text;
        if (banned) {
            layout.setAnimation(R.raw.ic_ban, "Hand");
            text = LocaleController.getString("UserBlocked", R.string.UserBlocked);
        } else {
            layout.setAnimation(R.raw.ic_unban, "Main", "Finger 1", "Finger 2", "Finger 3", "Finger 4");
            text = LocaleController.getString("UserUnblocked", R.string.UserUnblocked);
        }
        layout.textView.setText(AndroidUtilities.replaceTags(text));
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public static Bulletin createCopyLinkBulletin(BaseFragment fragment) {
        return of(fragment).createCopyLinkBulletin();
    }

    @CheckResult
    public static Bulletin createCopyLinkBulletin(FrameLayout containerView) {
        return of(containerView, null).createCopyLinkBulletin();
    }

    @CheckResult
    public static Bulletin createPinMessageBulletin(BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        return createPinMessageBulletin(fragment, true, null, null, resourcesProvider);
    }

    @CheckResult
    public static Bulletin createUnpinMessageBulletin(BaseFragment fragment, Runnable undoAction, Runnable delayedAction, Theme.ResourcesProvider resourcesProvider) {
        return createPinMessageBulletin(fragment, false, undoAction, delayedAction, resourcesProvider);
    }

    @CheckResult
    private static Bulletin createPinMessageBulletin(BaseFragment fragment, boolean pinned, Runnable undoAction, Runnable delayedAction, Theme.ResourcesProvider resourcesProvider) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);
        layout.setAnimation(pinned ? R.raw.ic_pin : R.raw.ic_unpin, 28, 28, "Pin", "Line");
        layout.textView.setText(LocaleController.getString(pinned ? "MessagePinnedHint" : "MessageUnpinnedHint", pinned ? R.string.MessagePinnedHint : R.string.MessageUnpinnedHint));
        if (!pinned) {
            layout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true, resourcesProvider).setUndoAction(undoAction).setDelayedAction(delayedAction));
        }
        return Bulletin.make(fragment, layout, pinned ? Bulletin.DURATION_SHORT : 5000);
    }
    //endregion
}
