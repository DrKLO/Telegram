package org.telegram.ui.Components;

import android.widget.FrameLayout;

import androidx.core.util.Preconditions;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

public final class BulletinFactory {

    private BulletinFactory() {
    }

    public static boolean canShowBulletin(BaseFragment fragment) {
        return fragment != null && fragment.getParentActivity() != null;
    }

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

    public static Bulletin createSaveToGalleryBulletin(FrameLayout containerLayout, boolean video, int backgroundColor, int textColor) {
        Preconditions.checkNotNull(containerLayout);
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(containerLayout.getContext(), backgroundColor, textColor);
        layout.imageView.setAnimation(R.raw.ic_download, 28, 28);
        layout.setAnimation(R.raw.ic_download, "Box", "Arrow", "Mask", "Arrow 2", "Splash");
        if (video) {
            layout.textView.setText(LocaleController.getString("VideoSavedHint", R.string.VideoSavedHint));
        } else {
            layout.textView.setText(LocaleController.getString("PhotoSavedHint", R.string.PhotoSavedHint));
        }
        return Bulletin.make(containerLayout, layout, Bulletin.DURATION_SHORT);
    }
}
