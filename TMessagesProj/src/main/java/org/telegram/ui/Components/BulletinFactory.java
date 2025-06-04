package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SavedMessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public static BulletinFactory global() {
        BaseFragment baseFragment = LaunchActivity.getSafeLastFragment();
        if (baseFragment == null) {
            return BulletinFactory.of(Bulletin.BulletinWindow.make(ApplicationLoader.applicationContext), null);
        }
        if (baseFragment.visibleDialog instanceof BottomSheet) {
            return BulletinFactory.of(((BottomSheet) baseFragment.visibleDialog).container, baseFragment.getResourceProvider());
        }
        return BulletinFactory.of(baseFragment);
    }

    public Bulletin makeForError(TLRPC.TL_error error) {
        if (!LaunchActivity.isActive) return new Bulletin.EmptyBulletin();
        if (error == null) {
            return createErrorBulletin(LocaleController.formatString(R.string.UnknownError));
        } else {
            return createErrorBulletin(LocaleController.formatString(R.string.UnknownErrorCode, error.text));
        }
    }

    public void showForError(TLRPC.TL_error error) {
        if (!LaunchActivity.isActive) return;
        if (error == null) {
            Bulletin b = createErrorBulletin(LocaleController.formatString(R.string.UnknownError));
            b.hideAfterBottomSheet = false;
            b.show();
        } else {
            Bulletin b = createErrorBulletin(LocaleController.formatString(R.string.UnknownErrorCode, error.text));
            b.hideAfterBottomSheet = false;
            b.show();
        }
    }

    public static void showError(TLRPC.TL_error error) {
        if (!LaunchActivity.isActive) return;
        global().createErrorBulletin(LocaleController.formatString(R.string.UnknownErrorCode, error.text)).show();
    }

    public enum FileType {

        PHOTO("PhotoSavedHint", R.string.PhotoSavedHint, Icon.SAVED_TO_GALLERY),
        PHOTOS("PhotosSavedHint", Icon.SAVED_TO_GALLERY),

        VIDEO("VideoSavedHint", R.string.VideoSavedHint, Icon.SAVED_TO_GALLERY),
        VIDEOS("VideosSavedHint", Icon.SAVED_TO_GALLERY),

        MEDIA("MediaSavedHint", Icon.SAVED_TO_GALLERY),

        PHOTO_TO_DOWNLOADS("PhotoSavedToDownloadsHintLinked", R.string.PhotoSavedToDownloadsHintLinked, Icon.SAVED_TO_DOWNLOADS),
        VIDEO_TO_DOWNLOADS("VideoSavedToDownloadsHintLinked", R.string.VideoSavedToDownloadsHintLinked, Icon.SAVED_TO_DOWNLOADS),

        GIF("GifSavedHint", R.string.GifSavedHint, Icon.SAVED_TO_GIFS),
        GIF_TO_DOWNLOADS("GifSavedToDownloadsHintLinked", R.string.GifSavedToDownloadsHintLinked, Icon.SAVED_TO_DOWNLOADS),

        AUDIO("AudioSavedHint", R.string.AudioSavedHint, Icon.SAVED_TO_MUSIC),
        AUDIOS("AudiosSavedHint", Icon.SAVED_TO_MUSIC),

        UNKNOWN("FileSavedHintLinked", R.string.FileSavedHintLinked, Icon.SAVED_TO_DOWNLOADS),
        UNKNOWNS("FilesSavedHintLinked", Icon.SAVED_TO_DOWNLOADS);

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
            SAVED_TO_MUSIC(R.raw.ic_save_to_music, 2, "Box", "Arrow"),
            SAVED_TO_GIFS(R.raw.ic_save_to_gifs, 0, "gif");

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
        if (fragment != null && fragment.getLastStoryViewer() != null && fragment.getLastStoryViewer().attachedToParent()) {
            this.fragment = null;
            this.containerLayout = fragment.getLastStoryViewer().getContainerForBulletin();
            this.resourcesProvider = fragment.getLastStoryViewer().getResourceProvider();
        } else {
            this.fragment = fragment;
            this.containerLayout = null;
            this.resourcesProvider = fragment == null ? null : fragment.getResourceProvider();
        }
    }

    private BulletinFactory(FrameLayout containerLayout, Theme.ResourcesProvider resourcesProvider) {
        this.containerLayout = containerLayout;
        this.fragment = null;
        this.resourcesProvider = resourcesProvider;
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text) {
        return createSimpleBulletinWithIconSize(iconRawId, text, 36);
    }

    public Bulletin createSimpleBulletin(TLRPC.MessageMedia media, CharSequence text) {
        if (media == null) return new Bulletin.EmptyBulletin();
        if (media.document != null)
            return createSimpleBulletin(media.document, text);
        if (media.photo != null)
            return createSimpleBulletin(media.photo, text);
        return new Bulletin.EmptyBulletin();
    }

    public Bulletin createSimpleBulletin(TLRPC.Document document, CharSequence text) {
        if (document == null) return new Bulletin.EmptyBulletin();
        final Bulletin.TwoLineLayout layout = new Bulletin.TwoLineLayout(getContext(), resourcesProvider);
        TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(28), true, null, false);
        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(28), true, thumbSize, true);
        layout.imageView.setImage(
            ImageLocation.getForDocument(photoSize, document), "28_28",
            ImageLocation.getForDocument(thumbSize, document), "28_28",
            null, 0, 0, null
        );
        layout.imageView.getImageReceiver().setRoundRadius(dp(5));
        layout.titleTextView.setText(text);
        layout.titleTextView.setSingleLine(true);
        layout.titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        layout.titleTextView.setMaxLines(1);
        layout.titleTextView.setTypeface(null);
        layout.subtitleTextView.setVisibility(View.GONE);
        return create(layout, text.length() < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletin(TLRPC.Document document, CharSequence title, CharSequence text) {
        if (document == null) return new Bulletin.EmptyBulletin();
        final Bulletin.TwoLineLayout layout = new Bulletin.TwoLineLayout(getContext(), resourcesProvider);
        TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(28), true, null, false);
        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(28), true, thumbSize, true);
        layout.imageView.setImage(
            ImageLocation.getForDocument(photoSize, document), "28_28",
            ImageLocation.getForDocument(thumbSize, document), "28_28",
            null, 0, 0, null
        );
        layout.imageView.getImageReceiver().setRoundRadius(dp(5));
        layout.titleTextView.setText(title);
        layout.titleTextView.setSingleLine(true);
        layout.titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        layout.titleTextView.setMaxLines(1);
        layout.titleTextView.setTypeface(AndroidUtilities.bold());
        layout.subtitleTextView.setText(text);
        layout.subtitleTextView.setSingleLine(false);
        layout.subtitleTextView.setMaxLines(5);
        return create(layout, text.length() < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletin(TLRPC.Photo photo, CharSequence text) {
        if (photo == null) return new Bulletin.EmptyBulletin();
        final Bulletin.TwoLineLayout layout = new Bulletin.TwoLineLayout(getContext(), resourcesProvider);
        TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, dp(28), true, null, false);
        TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, dp(28), true, thumbSize, true);
        layout.imageView.setImage(
                ImageLocation.getForPhoto(photoSize, photo), "28_28",
                ImageLocation.getForPhoto(thumbSize, photo), "28_28",
                null, 0, 0, null
        );
        layout.imageView.getImageReceiver().setRoundRadius(dp(5));
        layout.titleTextView.setText(text);
        layout.titleTextView.setSingleLine(true);
        layout.titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        layout.titleTextView.setMaxLines(1);
        layout.subtitleTextView.setVisibility(View.GONE);
        return create(layout, text.length() < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletinWithIconSize(int iconRawId, CharSequence text, int iconSize) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(iconRawId, iconSize, iconSize);
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        return create(layout, text.length() < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletinDetail(int iconRawId, CharSequence text) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(iconRawId, 36, 36);
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        layout.textView.setMaxLines(4);
        return create(layout, text.length() < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG);
    }

    public Bulletin createImageBulletin(int iconRawId, CharSequence title) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setBackground(Theme.getColor(Theme.key_undo_background, resourcesProvider), 12);
        layout.imageView.setImageResource(iconRawId);
        layout.textView.setText(title);
        layout.textView.setSingleLine(false);
        layout.textView.setLines(2);
        layout.textView.setMaxLines(4);
        layout.textView.setMaxWidth(HintView2.cutInFancyHalf(layout.textView.getText(), layout.textView.getPaint()));
        layout.textView.setLineSpacing(dp(1.33f), 1f);
        ((ViewGroup.MarginLayoutParams) layout.textView.getLayoutParams()).rightMargin = dp(12);
        layout.setWrapWidth();
        return create(layout, Bulletin.DURATION_PROLONG);
    }

    public Bulletin createSimpleLargeBulletin(int iconRawId, CharSequence title, CharSequence subtitle) {
        final Bulletin.TwoLineLayout layout = new Bulletin.TwoLineLayout(getContext(), resourcesProvider);
        layout.imageView.setImageResource(iconRawId);
        layout.titleTextView.setText(title);

        layout.subtitleTextView.setText(subtitle);
        layout.subtitleTextView.setSingleLine(false);
        layout.subtitleTextView.setMaxLines(5);
        return create(layout, Bulletin.DURATION_PROLONG);
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, int maxLines) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(iconRawId, 36, 36);
        if (text != null) {
            String string = text.toString();
            SpannableStringBuilder ssb = text instanceof SpannableStringBuilder ? (SpannableStringBuilder) text : new SpannableStringBuilder(text);
            for (int index = string.indexOf('\n'), l = 0; index >= 0 && index < text.length(); l++, index = string.indexOf('\n', index + 1)) {
                if (l >= maxLines) {
                    ssb.replace(index, index + 1, " ");
                }
            }
            text = ssb;
        }
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(maxLines);
        layout.textView.setText(text);
        return create(layout, text.length() < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG);
    }


    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, int maxLines, int duration) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(iconRawId, 36, 36);
        if (text != null) {
            String string = text.toString();
            SpannableStringBuilder ssb = text instanceof SpannableStringBuilder ? (SpannableStringBuilder) text : new SpannableStringBuilder(text);
            for (int index = string.indexOf('\n'), l = 0; index >= 0 && index < text.length(); l++, index = string.indexOf('\n', index + 1)) {
                if (l >= maxLines) {
                    ssb.replace(index, index + 1, " ");
                }
            }
            text = ssb;
        }
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(maxLines);
        return create(layout, duration);
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, CharSequence subtext) {
        final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(iconRawId, 36, 36);
        layout.titleTextView.setText(text);
        layout.subtitleTextView.setText(subtext);
        return create(layout, (text.length() + subtext.length()) < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, CharSequence button, Runnable onButtonClick) {
        return createSimpleBulletin(iconRawId, text, button, text.length() < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG, onButtonClick);
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, CharSequence subtext, CharSequence button, Runnable onButtonClick) {
        final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(iconRawId, 36, 36);
        layout.titleTextView.setText(text);
        layout.subtitleTextView.setText(subtext);
        layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(button).setUndoAction(onButtonClick));
        return create(layout, Bulletin.DURATION_PROLONG);
    }

    public Bulletin createSimpleBulletin(int iconRawId, CharSequence text, CharSequence button, int duration, Runnable onButtonClick) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        if (iconRawId != 0) {
            layout.setAnimation(iconRawId, 36, 36);
        } else {
            layout.imageView.setVisibility(View.INVISIBLE);
            ((ViewGroup.MarginLayoutParams) layout.textView.getLayoutParams()).leftMargin = dp(16);
        }
        layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        layout.textView.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(3);
        layout.textView.setText(text);
        layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(button).setUndoAction(onButtonClick));
        return create(layout, duration);
    }

    public Bulletin createSimpleBulletin(Drawable drawable, CharSequence text, String button, Runnable onButtonClick) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.imageView.setImageDrawable(drawable);
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(button).setUndoAction(onButtonClick));
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletin(Drawable drawable, CharSequence text) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.imageView.setImageDrawable(drawable);
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletin(Drawable drawable, CharSequence text, CharSequence subtitle) {
        final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
        layout.imageView.setImageDrawable(drawable);
        layout.titleTextView.setText(text);
        layout.subtitleTextView.setText(subtitle);
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletin(CharSequence text, CharSequence subtitle) {
        final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
        layout.hideImage();
        layout.titleTextView.setText(text);
        layout.subtitleTextView.setText(subtitle);
        return create(layout, Bulletin.DURATION_PROLONG);
    }

    public Bulletin createSimpleBulletin(Drawable drawable, CharSequence text, CharSequence subtitle, String button, Runnable onButtonClick) {
        final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
        layout.imageView.setImageDrawable(drawable);
        layout.titleTextView.setText(text);
        layout.subtitleTextView.setText(subtitle);
        layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(button).setUndoAction(onButtonClick));
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createSimpleBulletin(CharSequence text, CharSequence subtitle, String button, Runnable onButtonClick) {
        final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
        layout.hideImage();
        layout.titleTextView.setText(text);
        layout.subtitleTextView.setText(subtitle);
        layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(button).setUndoAction(onButtonClick));
        return create(layout, Bulletin.DURATION_PROLONG);
    }

    public Bulletin createUndoBulletin(CharSequence text, Runnable onUndo, Runnable onAction) {
        return this.createUndoBulletin(text, false, onUndo, onAction);
    }

    public Bulletin createUndoBulletin(CharSequence text, boolean textAndIcon, Runnable onUndo, Runnable onAction) {
        return this.createUndoBulletin(text, null, textAndIcon, onUndo, onAction);
    }

    public Bulletin createUndoBulletin(CharSequence text, CharSequence subtitle, Runnable onUndo, Runnable onAction) {
        return this.createUndoBulletin(text, subtitle, true, onUndo, onAction);
    }

    public Bulletin createUndoBulletin(CharSequence text, CharSequence subtitle, boolean textAndIcon, Runnable onUndo, Runnable onAction) {
        final Bulletin.ButtonLayout layout;
        if (!TextUtils.isEmpty(subtitle)) {
            final Bulletin.TwoLineLottieLayout twoLineLayout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
            twoLineLayout.titleTextView.setText(text);
            twoLineLayout.subtitleTextView.setText(subtitle);
            layout = twoLineLayout;
        } else {
            final Bulletin.LottieLayout singleLineLayout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
            singleLineLayout.textView.setText(text);
            singleLineLayout.textView.setSingleLine(false);
            singleLineLayout.textView.setMaxLines(2);
            layout = singleLineLayout;
        }
        layout.setTimer();
        layout.setButton(new Bulletin.UndoButton(getContext(), true, textAndIcon, resourcesProvider).setText(LocaleController.getString(R.string.Undo)).setUndoAction(onUndo).setDelayedAction(onAction));
        return create(layout, Bulletin.DURATION_PROLONG);
    }

    public Bulletin createUsersBulletin(List<? extends TLObject> users, CharSequence text) {
       return createUsersBulletin(users, text, null, null);
    }

    public Bulletin createUsersBulletin(TLObject user, CharSequence text, CharSequence subtitle) {
        return createUsersBulletin(Arrays.asList(user), text, subtitle, null);
    }

    public Bulletin createUsersBulletin(TLObject user, CharSequence text) {
        return createUsersBulletin(Arrays.asList(user), text, null, null);
    }

    public Bulletin createUsersBulletin(List<? extends TLObject> users, CharSequence text, CharSequence subtitle) {
        return createUsersBulletin(users, text, subtitle, null);
    }

    public Bulletin createUsersBulletin(List<? extends TLObject> users, CharSequence text, CharSequence subtitle, UndoObject undoObject) {
        final Bulletin.UsersLayout layout = new Bulletin.UsersLayout(getContext(), subtitle != null, resourcesProvider);
        int count = 0;
        if (users != null) {
            for (int i = 0; i < users.size(); ++i) {
                if (count >= 3)
                    break;
                TLObject user = users.get(i);
                if (user != null) {
                    layout.avatarsImageView.setCount(++count);
                    layout.avatarsImageView.setObject(count - 1, UserConfig.selectedAccount, user);
                }
            }
            if (users.size() == 1) {
                layout.avatarsImageView.setTranslationX(dp(4));
                layout.avatarsImageView.setScaleX(1.2f);
                layout.avatarsImageView.setScaleY(1.2f);
            } else {
                layout.avatarsImageView.setScaleX(1f);
                layout.avatarsImageView.setScaleY(1f);
            }
        }
        layout.avatarsImageView.commitTransition(false);


        if (subtitle != null) {
            layout.textView.setSingleLine(true);
            layout.textView.setMaxLines(1);
            layout.textView.setText(text);
            layout.subtitleView.setText(subtitle);
            layout.subtitleView.setSingleLine(false);
            layout.subtitleView.setMaxLines(3);
            if (layout.linearLayout.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                int margin = dp(12 + 56 + 2 - (3 - count) * 12);
                if (count == 1) {
                    margin += dp(4);
                }
                if (LocaleController.isRTL) {
                    ((ViewGroup.MarginLayoutParams) layout.linearLayout.getLayoutParams()).rightMargin = margin;
                } else {
                    ((ViewGroup.MarginLayoutParams) layout.linearLayout.getLayoutParams()).leftMargin = margin;
                }
            }
        } else {
            layout.textView.setSingleLine(false);
            layout.textView.setMaxLines(4);
            layout.textView.setText(text);
            if (layout.textView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                int margin = dp(12 + 56 + 2 - (3 - count) * 12);
                if (count == 1) {
                    layout.textView.setTranslationY(-dp(1));
                    margin += dp(4);
                }
                if (LocaleController.isRTL) {
                    ((ViewGroup.MarginLayoutParams) layout.textView.getLayoutParams()).rightMargin = margin;
                } else {
                    ((ViewGroup.MarginLayoutParams) layout.textView.getLayoutParams()).leftMargin = margin;
                }
            }
        }

        if (undoObject != null) {
            layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(LocaleController.getString(R.string.Undo)).setUndoAction(undoObject.onUndo).setDelayedAction(undoObject.onAction));
        }

        return create(layout, Bulletin.DURATION_PROLONG);
    }

    public Bulletin createChatsBulletin(List<TLObject> objects, CharSequence text, CharSequence subtitle) {
        final Bulletin.UsersLayout layout = new Bulletin.UsersLayout(getContext(), subtitle != null, resourcesProvider);
        int count = 0;
        if (objects != null) {
            for (int i = 0; i < objects.size(); ++i) {
                if (count >= 3)
                    break;
                TLObject object = objects.get(i);
                if (object != null) {
                    layout.avatarsImageView.setCount(++count);
                    layout.avatarsImageView.setObject(count - 1, UserConfig.selectedAccount, object);
                }
            }
            if (objects.size() == 1) {
                layout.avatarsImageView.setTranslationX(dp(4));
                layout.avatarsImageView.setScaleX(1.2f);
                layout.avatarsImageView.setScaleY(1.2f);
            } else {
                layout.avatarsImageView.setScaleX(1f);
                layout.avatarsImageView.setScaleY(1f);
            }
        }
        layout.avatarsImageView.commitTransition(false);

        if (subtitle != null) {
            layout.textView.setSingleLine(true);
            layout.textView.setMaxLines(1);
            layout.textView.setText(text);
            layout.subtitleView.setText(subtitle);
            layout.subtitleView.setSingleLine(true);
            layout.subtitleView.setMaxLines(1);
            if (layout.linearLayout.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                int margin = dp(12 + 56 + 6 - (3 - count) * 12);
                if (LocaleController.isRTL) {
                    ((ViewGroup.MarginLayoutParams) layout.linearLayout.getLayoutParams()).rightMargin = margin;
                } else {
                    ((ViewGroup.MarginLayoutParams) layout.linearLayout.getLayoutParams()).leftMargin = margin;
                }
            }
        } else {
            layout.textView.setSingleLine(false);
            layout.textView.setMaxLines(2);
            layout.textView.setText(text);
            if (layout.textView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                int margin = dp(12 + 56 + 6 - (3 - count) * 12);
                if (LocaleController.isRTL) {
                    ((ViewGroup.MarginLayoutParams) layout.textView.getLayoutParams()).rightMargin = margin;
                } else {
                    ((ViewGroup.MarginLayoutParams) layout.textView.getLayoutParams()).leftMargin = margin;
                }
            }
        }
        if (LocaleController.isRTL) {
            layout.avatarsImageView.setTranslationX(dp(32 - (count - 1) * 12));
        }

        return create(layout, Bulletin.DURATION_PROLONG);
    }

    public Bulletin createUsersAddedBulletin(ArrayList<TLRPC.User> users, TLRPC.Chat chat) {
        CharSequence text;
        if (users == null || users.size() == 0) {
            text = null;
        } else if (users.size() == 1) {
            if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                text = AndroidUtilities.replaceTags(LocaleController.formatString("HasBeenAddedToChannel", R.string.HasBeenAddedToChannel, "**" + UserObject.getFirstName(users.get(0)) + "**"));
            } else {
                text = AndroidUtilities.replaceTags(LocaleController.formatString("HasBeenAddedToGroup", R.string.HasBeenAddedToGroup, "**" + UserObject.getFirstName(users.get(0)) + "**"));
            }
        } else {
            if (ChatObject.isChannelAndNotMegaGroup(chat)) {
                text = AndroidUtilities.replaceTags(LocaleController.formatPluralString("AddedMembersToChannel", users.size()));
            } else {
                text = AndroidUtilities.replaceTags(LocaleController.formatPluralString("AddedSubscribersToChannel", users.size()));
            }
        }
        return createUsersBulletin(users, text);
    }

    public Bulletin createEmojiBulletin(String emoji, String text) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(MediaDataController.getInstance(UserConfig.selectedAccount).getEmojiAnimatedSticker(emoji), 36, 36);
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createEmojiBulletin(String emoji, String text, String button, Runnable onButtonClick) {
        return createEmojiBulletin(MediaDataController.getInstance(UserConfig.selectedAccount).getEmojiAnimatedSticker(emoji), text, button, onButtonClick);
    }

    public Bulletin createEmojiBulletin(TLRPC.Document document, CharSequence text) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        if (MessageObject.isTextColorEmoji(document)) {
            layout.imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_infoColor), PorterDuff.Mode.SRC_IN));
        }
        layout.setAnimation(document, 36, 36);
        layout.textView.setText(text);
        layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(3);
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createEmojiBulletin(TLRPC.Document document, CharSequence text, CharSequence subtext) {
        final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
        if (MessageObject.isTextColorEmoji(document)) {
            layout.imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_infoColor), PorterDuff.Mode.SRC_IN));
        }
        layout.setAnimation(document, 36, 36);
        layout.titleTextView.setText(text);
        layout.subtitleTextView.setText(subtext);
        return create(layout, (text.length() + subtext.length()) < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG);
    }

    public Bulletin createEmojiBulletin(TLRPC.Document document, CharSequence text, CharSequence subtext, CharSequence buttonText, Runnable onButtonClick) {
        final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
        if (MessageObject.isTextColorEmoji(document)) {
            layout.imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_infoColor), PorterDuff.Mode.SRC_IN));
        }
        layout.setAnimation(document, 36, 36);
        layout.titleTextView.setText(text);
        layout.subtitleTextView.setText(subtext);
        layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(buttonText).setUndoAction(onButtonClick));
        return create(layout, (text.length() + subtext.length()) < 20 ? Bulletin.DURATION_SHORT : Bulletin.DURATION_LONG);
    }

    public Bulletin createStaticEmojiBulletin(TLRPC.Document document, CharSequence text) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        if (MessageObject.isTextColorEmoji(document)) {
            layout.imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_infoColor), PorterDuff.Mode.SRC_IN));
        }
        layout.setAnimation(document, 36, 36);
        layout.imageView.stopAnimation();
        layout.textView.setText(text);
        layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(3);
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createEmojiBulletin(TLRPC.Document document, CharSequence text, CharSequence button, Runnable onButtonClick) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        if (MessageObject.isTextColorEmoji(document)) {
            layout.imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_infoColor), PorterDuff.Mode.SRC_IN));
        }
        layout.setAnimation(document, 36, 36);
        if (layout.imageView.getImageReceiver() != null) {
            layout.imageView.getImageReceiver().setRoundRadius(dp(4));
        }
        layout.textView.setText(text);
        layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(3);
        layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(button).setUndoAction(onButtonClick));
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createEmojiLoadingBulletin(TLRPC.Document document, CharSequence text, CharSequence button, Runnable onButtonClick) {
        final Bulletin.LoadingLottieLayout layout = new Bulletin.LoadingLottieLayout(getContext(), resourcesProvider);
        if (MessageObject.isTextColorEmoji(document)) {
            layout.imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_infoColor), PorterDuff.Mode.SRC_IN));
        }
        layout.setAnimation(document, 36, 36);
        layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(3);
        layout.textLoadingView.setText(text);
        layout.textLoadingView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        layout.textLoadingView.setSingleLine(false);
        layout.textLoadingView.setMaxLines(3);
        layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(button).setUndoAction(onButtonClick));
        return create(layout, Bulletin.DURATION_LONG);
    }

    public static final int CONTAINS_EMOJI_IN_MESSAGE = 0;
    public static final int CONTAINS_EMOJI_IN_TOPIC = 1;
    public static final int CONTAINS_EMOJI_IN_STORY = 2;

    public Bulletin createContainsEmojiBulletin(TLRPC.Document document, int type, Utilities.Callback<TLRPC.InputStickerSet> openSet) {
        TLRPC.InputStickerSet inputStickerSet = MessageObject.getInputStickerSet(document);
        if (inputStickerSet == null) {
            return null;
        }
        TLRPC.TL_messages_stickerSet cachedSet = MediaDataController.getInstance(UserConfig.selectedAccount).getStickerSet(inputStickerSet, true);
        if (cachedSet == null || cachedSet.set == null) {
            final String loadingPlaceholder = "<{LOADING}>";
            SpannableStringBuilder stringBuilder;
            if (type == CONTAINS_EMOJI_IN_TOPIC) {
                stringBuilder = new SpannableStringBuilder(AndroidUtilities.replaceTags(LocaleController.formatString("TopicContainsEmojiPackSingle", R.string.TopicContainsEmojiPackSingle, loadingPlaceholder)));
            } else if (type == CONTAINS_EMOJI_IN_STORY) {
                stringBuilder = new SpannableStringBuilder(AndroidUtilities.replaceTags(LocaleController.formatString("StoryContainsEmojiPackSingle", R.string.StoryContainsEmojiPackSingle, loadingPlaceholder)));
            } else {
                stringBuilder = new SpannableStringBuilder(AndroidUtilities.replaceTags(LocaleController.formatString("MessageContainsEmojiPackSingle", R.string.MessageContainsEmojiPackSingle, loadingPlaceholder)));
            }
            LoadingSpan loadingSpan = null;
            int index;
            if ((index = stringBuilder.toString().indexOf(loadingPlaceholder)) >= 0) {
                stringBuilder.setSpan(loadingSpan = new LoadingSpan(null, dp(100), dp(2), resourcesProvider), index, index + loadingPlaceholder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                loadingSpan.setColors(
                    ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider), 0x20),
                    ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider), 0x48)
                );
            }
            final long startTime = System.currentTimeMillis();
            final long minDuration = 750;
            Bulletin bulletin = createEmojiLoadingBulletin(document, stringBuilder, LocaleController.getString(R.string.ViewAction), () -> openSet.run(inputStickerSet));
            if (loadingSpan != null && bulletin.getLayout() instanceof Bulletin.LoadingLottieLayout) {
                loadingSpan.setView(((Bulletin.LoadingLottieLayout) bulletin.getLayout()).textLoadingView);
            }
            MediaDataController.getInstance(UserConfig.selectedAccount).getStickerSet(inputStickerSet, null, false, set -> {
                CharSequence message;
                if (set != null && set.set != null) {
                    if (type == CONTAINS_EMOJI_IN_TOPIC) {
                        message = AndroidUtilities.replaceTags(LocaleController.formatString("TopicContainsEmojiPackSingle", R.string.TopicContainsEmojiPackSingle, set.set.title));
                    } else if (type == CONTAINS_EMOJI_IN_STORY) {
                        message = AndroidUtilities.replaceTags(LocaleController.formatString("StoryContainsEmojiPackSingle", R.string.StoryContainsEmojiPackSingle, set.set.title));
                    } else {
                        message = AndroidUtilities.replaceTags(LocaleController.formatString("MessageContainsEmojiPackSingle", R.string.MessageContainsEmojiPackSingle, set.set.title));
                    }
                } else {
                    message = LocaleController.getString(R.string.AddEmojiNotFound);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    bulletin.onLoaded(message);
                }, Math.max(1, minDuration - (System.currentTimeMillis() - startTime)));
            });
            return bulletin;
        } else {
            CharSequence message;
            if (type == CONTAINS_EMOJI_IN_TOPIC) {
                message = AndroidUtilities.replaceTags(LocaleController.formatString("TopicContainsEmojiPackSingle", R.string.TopicContainsEmojiPackSingle, cachedSet.set.title));
            } else if (type == CONTAINS_EMOJI_IN_STORY) {
                message = AndroidUtilities.replaceTags(LocaleController.formatString("StoryContainsEmojiPackSingle", R.string.StoryContainsEmojiPackSingle, cachedSet.set.title));
            } else {
                message = AndroidUtilities.replaceTags(LocaleController.formatString("MessageContainsEmojiPackSingle", R.string.MessageContainsEmojiPackSingle, cachedSet.set.title));
            }
            return createEmojiBulletin(document, message, LocaleController.getString(R.string.ViewAction), () -> openSet.run(inputStickerSet));
        }
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
        layout.textView.setText(LocaleController.getString(R.string.ReportChatSent));
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
        layout.textView.setText(AndroidUtilities.replaceSingleTag(fileType.getText(filesAmount), () -> {
            if (LaunchActivity.instance == null || LaunchActivity.instance.isFinishing()) return;

            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            LaunchActivity.instance.startActivity(intent);
        }));
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

    public Bulletin createSuccessBulletin(CharSequence successMessage) {
        return createSuccessBulletin(successMessage, null);
    }

    public Bulletin createSuccessBulletin(CharSequence successMessage, Theme.ResourcesProvider resourcesProvider) {
        Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(R.raw.contact_check);
        layout.textView.setText(successMessage);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        return create(layout, Bulletin.DURATION_SHORT);
    }

    public Bulletin createCaptionLimitBulletin(int count, Runnable callback) {
        Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), null);
        layout.setAnimation(R.raw.caption_limit);
        String str = LocaleController.formatPluralString("ChannelCaptionLimitPremiumPromo", count);
        SpannableStringBuilder spannable = SpannableStringBuilder.valueOf(AndroidUtilities.replaceTags(str));
        int indexStart = str.indexOf('*'), indexEnd = str.indexOf('*', indexStart + 1);
        spannable.replace(indexStart, indexEnd + 1, str.substring(indexStart + 1, indexEnd));
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                callback.run();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        }, indexStart, indexEnd - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        layout.textView.setText(spannable);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(3);
        return create(layout, 5000);
    }

    public Bulletin createRestrictVoiceMessagesPremiumBulletin() {
        Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), null);
        layout.setAnimation(R.raw.voip_muted);
        String str = LocaleController.getString(R.string.PrivacyVoiceMessagesPremiumOnly);
        SpannableStringBuilder spannable = new SpannableStringBuilder(str);
        int indexStart = str.indexOf('*'), indexEnd = str.lastIndexOf('*');
        if (indexStart >= 0) {
            spannable.replace(indexStart, indexEnd + 1, str.substring(indexStart + 1, indexEnd));
            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    fragment.presentFragment(new PremiumPreviewFragment("settings"));
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(false);
                }
            }, indexStart, indexEnd - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        layout.textView.setText(spannable);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        return create(layout, Bulletin.DURATION_LONG);
    }

    public Bulletin createErrorBulletinSubtitle(CharSequence errorMessage, CharSequence errorDescription, Theme.ResourcesProvider resourcesProvider) {
        Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(R.raw.chats_infotip);
        layout.titleTextView.setText(errorMessage);
        layout.subtitleTextView.setText(errorDescription);
        return create(layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public Bulletin createCopyLinkBulletin() {
        return createCopyLinkBulletin(false);
    }

    @CheckResult
    public Bulletin createCopyBulletin(String message) {
        return createCopyBulletin(message, null);
    }

    @CheckResult
    public Bulletin createCopyBulletin(String message, Theme.ResourcesProvider resourcesProvider) {
        if (!AndroidUtilities.shouldShowClipboardToast()) {
            return new Bulletin.EmptyBulletin();
        }
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), null);
        layout.setAnimation(R.raw.copy, 36, 36, "NULL ROTATION", "Back", "Front");
        layout.textView.setText(message);
        return create(layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public Bulletin createCopyLinkBulletin(boolean isPrivate) {
        if (!AndroidUtilities.shouldShowClipboardToast()) {
            return new Bulletin.EmptyBulletin();
        }
        if (isPrivate) {
            final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(getContext(), resourcesProvider);
            layout.setAnimation(R.raw.voip_invite, 36, 36, "Wibe", "Circle");
            layout.titleTextView.setText(LocaleController.getString(R.string.LinkCopied));
            layout.subtitleTextView.setText(LocaleController.getString(R.string.LinkCopiedPrivateInfo));
            return create(layout, Bulletin.DURATION_LONG);
        } else {
            final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
            layout.setAnimation(R.raw.voip_invite, 36, 36, "Wibe", "Circle");
            layout.textView.setText(LocaleController.getString(R.string.LinkCopied));
            return create(layout, Bulletin.DURATION_SHORT);
        }
    }

    @CheckResult
    public Bulletin createCopyLinkBulletin(String text, Theme.ResourcesProvider resourcesProvider) {
        if (!AndroidUtilities.shouldShowClipboardToast()) {
            return new Bulletin.EmptyBulletin();
        }
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(R.raw.voip_invite, 36, 36, "Wibe", "Circle");
        layout.textView.setText(text);
        return create(layout, Bulletin.DURATION_SHORT);
    }

    public Bulletin create(Bulletin.Layout layout, int duration) {
        if (fragment != null) {
            return Bulletin.make(fragment, layout, duration);
        } else {
            return Bulletin.make(containerLayout, layout, duration);
        }
    }

    private Context getContext() {
        Context context = null;
        if (fragment != null) {
            context = fragment.getParentActivity();
            if (context == null && fragment.getLayoutContainer() != null) {
                context = fragment.getLayoutContainer().getContext();
            }
        } else if (containerLayout != null) {
            context = containerLayout.getContext();
        }
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        return context;
    }

    //region Static Factory

    @CheckResult
    public static Bulletin createMuteBulletin(BaseFragment fragment, int setting) {
        return createMuteBulletin(fragment, setting, 0, null);
    }

    @CheckResult
    public static Bulletin createMuteBulletin(BaseFragment fragment, int setting, int timeInSeconds, Theme.ResourcesProvider resourcesProvider) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);

        final String text;
        final boolean mute;
        boolean muteFor = false;

        switch (setting) {
            case NotificationsController.SETTING_MUTE_CUSTOM:
                text = LocaleController.formatString("NotificationsMutedForHint", R.string.NotificationsMutedForHint, LocaleController.formatTTLString(timeInSeconds));
                mute = true;
                muteFor = true;
                break;
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
                text = LocaleController.getString(R.string.NotificationsMutedHint);
                mute = true;
                break;
            case NotificationsController.SETTING_MUTE_UNMUTE:
                text = LocaleController.getString(R.string.NotificationsUnmutedHint);
                mute = false;
                break;
            default:
                throw new IllegalArgumentException();
        }

        if (muteFor) {
            layout.setAnimation(R.raw.mute_for);
        } else if (mute) {
            layout.setAnimation(R.raw.ic_mute, "Body Main", "Body Top", "Line", "Curve Big", "Curve Small");
        } else {
            layout.setAnimation(R.raw.ic_unmute, "BODY", "Wibe Big", "Wibe Big 3", "Wibe Small");
        }

        layout.textView.setText(text);
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public static Bulletin createMuteBulletin(BaseFragment fragment, boolean mute, int chatsCount, Theme.ResourcesProvider resourcesProvider) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);
        layout.textView.setText(
            mute ?
                LocaleController.formatPluralString("NotificationsMutedHintChats", chatsCount) :
                LocaleController.formatPluralString("NotificationsUnmutedHintChats", chatsCount)
        );
        if (mute) {
            layout.setAnimation(R.raw.ic_mute, "Body Main", "Body Top", "Line", "Curve Big", "Curve Small");
        } else {
            layout.setAnimation(R.raw.ic_unmute, "BODY", "Wibe Big", "Wibe Big 3", "Wibe Small");
        }
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public static Bulletin createMuteBulletin(BaseFragment fragment, boolean muted, Theme.ResourcesProvider resourcesProvider) {
        return createMuteBulletin(fragment, muted ? NotificationsController.SETTING_MUTE_FOREVER : NotificationsController.SETTING_MUTE_UNMUTE, 0, resourcesProvider);
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
            layout.titleTextView.setText(LocaleController.getString(R.string.PinnedMessagesHidden));
            layout.subtitleTextView.setText(LocaleController.getString(R.string.PinnedMessagesHiddenInfo));
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
    public static Bulletin createSaveToGalleryBulletin(FrameLayout containerLayout, boolean video, Theme.ResourcesProvider resourcesProvider) {
        return of(containerLayout, resourcesProvider).createDownloadBulletin(video ? FileType.VIDEO : FileType.PHOTO, resourcesProvider);
    }

    @CheckResult
    public static Bulletin createSaveToGalleryBulletin(FrameLayout containerLayout, boolean video, int backgroundColor, int textColor) {
        return of(containerLayout, null).createDownloadBulletin(video ? FileType.VIDEO : FileType.PHOTO, 1, backgroundColor, textColor);
    }

    @CheckResult
    public static Bulletin createSaveToGalleryBulletin(FrameLayout containerLayout, int amount, boolean video, int backgroundColor, int textColor) {
        return of(containerLayout, null).createDownloadBulletin(video ? (amount > 1 ? FileType.VIDEOS : FileType.VIDEO) : (amount > 1 ? FileType.PHOTOS : FileType.PHOTO), amount, backgroundColor, textColor);
    }

    @CheckResult
    public static Bulletin createPromoteToAdminBulletin(BaseFragment fragment, String userFirstName) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), fragment.getResourceProvider());
        layout.setAnimation(R.raw.ic_admin, "Shield");
        layout.textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("UserSetAsAdminHint", R.string.UserSetAsAdminHint, userFirstName)));
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public static Bulletin createAddedAsAdminBulletin(BaseFragment fragment, String userFirstName) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), fragment.getResourceProvider());
        layout.setAnimation(R.raw.ic_admin, "Shield");
        layout.textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("UserAddedAsAdminHint", R.string.UserAddedAsAdminHint, userFirstName)));
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public static Bulletin createInviteSentBulletin(Context context, FrameLayout containerLayout, int dialogsCount, long did, int messagesCount, int backgroundColor, int textColor) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(context, null, backgroundColor, textColor);
        CharSequence text;
        int hapticDelay = -1;
        if (dialogsCount <= 1) {
            if (did == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
                text = AndroidUtilities.replaceTags(LocaleController.getString(R.string.InvLinkToSavedMessages));
                layout.setAnimation(R.raw.saved_messages, 30, 30);
            } else {
                if (DialogObject.isChatDialog(did)) {
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-did);
                    text = AndroidUtilities.replaceTags(LocaleController.formatString("InvLinkToGroup", R.string.InvLinkToGroup, chat.title));
                } else {
                    TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(did);
                    text = AndroidUtilities.replaceTags(LocaleController.formatString("InvLinkToUser", R.string.InvLinkToUser, UserObject.getFirstName(user)));
                }
                layout.setAnimation(R.raw.forward, 30, 30);
                hapticDelay = 300;
            }
        } else {
            text = AndroidUtilities.replaceTags(LocaleController.formatString("InvLinkToChats", R.string.InvLinkToChats, LocaleController.formatPluralString("Chats", dialogsCount)));
            layout.setAnimation(R.raw.forward, 30, 30);
            hapticDelay = 300;
        }
        layout.textView.setText(text);
        if (hapticDelay > 0) {
            layout.postDelayed(() -> {
                layout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }, hapticDelay);
        }
        return Bulletin.make(containerLayout, layout, Bulletin.DURATION_SHORT);
    }

    public Bulletin createAdReportedBulletin(CharSequence text) {
        if (getContext() == null) return new Bulletin.EmptyBulletin();
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(R.raw.ic_admin, "Shield");
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(3);
        layout.textView.setText(text);
        return Bulletin.make(fragment, layout, Bulletin.DURATION_LONG);
    }

    public boolean showForwardedBulletinWithTag(long did, int messagesCount) {
        if (!UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
            return false;
        }
        final Bulletin.LottieLayoutWithReactions layout = new Bulletin.LottieLayoutWithReactions(fragment, messagesCount);
        CharSequence text;
        if (did == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
            if (messagesCount <= 1) {
                text = AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.FwdMessageToSavedMessages), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, SavedMessagesController::openSavedMessages);
            } else {
                text = AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.FwdMessagesToSavedMessages), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, SavedMessagesController::openSavedMessages);
            }
        } else {
            return false;
        }
        layout.setAnimation(R.raw.saved_messages, 36, 36);
        layout.textView.setText(text);
        layout.textView.setSingleLine(false);
        layout.textView.setMaxLines(2);
        Bulletin bulletin = create(layout, 3500);
        layout.setBulletin(bulletin);
        bulletin.hideAfterBottomSheet(false);
        bulletin.show(true);
        return true;
    }

    @CheckResult
    public static Bulletin createForwardedBulletin(Context context, FrameLayout containerLayout, int dialogsCount, long did, int messagesCount, int backgroundColor, int textColor) {
        return createForwardedBulletin(context, null, containerLayout, dialogsCount, did, messagesCount, backgroundColor, textColor, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public static Bulletin createForwardedBulletin(Context context, BaseFragment fragment, FrameLayout containerLayout, int dialogsCount, long did, int messagesCount, int backgroundColor, int textColor, int duration) {
        return createForwardedBulletin(context, fragment, containerLayout, dialogsCount, did, messagesCount, backgroundColor, textColor, duration, null, null);
    }

    public static Bulletin createForwardedBulletin(Context context, BaseFragment fragment, FrameLayout containerLayout, int dialogsCount, long did, int messagesCount, int backgroundColor, int textColor, int duration, Runnable undoAction, Runnable delayedAction) {
        final Bulletin.LottieLayout layout = UserConfig.getInstance(UserConfig.selectedAccount).isPremium() && fragment != null && dialogsCount <= 1 && did == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId ?
            new Bulletin.LottieLayoutWithReactions(fragment, messagesCount) :
            new Bulletin.LottieLayout(context, fragment != null ? fragment.getResourceProvider() : null, backgroundColor, textColor);
        final CharSequence text;
        final boolean hasUndoButton = delayedAction != null || undoAction != null;
        final boolean[] isCanceled = new boolean[]{ false };
        final Runnable delayedActionOnce = delayedAction != null ? () -> {
            if (!isCanceled[0]) {
                isCanceled[0] = true;
                delayedAction.run();
            }
        } : null;

        int hapticDelay = -1;
        if (dialogsCount <= 1) {
            if (did == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
                if (messagesCount <= 1) {
                    text = AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.FwdMessageToSavedMessages), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, SavedMessagesController::openSavedMessages);
                } else {
                    text = AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.FwdMessagesToSavedMessages), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, SavedMessagesController::openSavedMessages);
                }
                layout.setAnimation(R.raw.saved_messages, 30, 30);
                hapticDelay = 300;
            } else {
                final Runnable onClick = () -> {
                    if (delayedActionOnce != null) {
                        delayedActionOnce.run();
                    }
                    if (fragment != null) {
                        fragment.presentFragment(ChatActivity.of(did));
                    }
                };

                if (DialogObject.isChatDialog(did)) {
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-did);
                    if (messagesCount <= 1) {
                        if (fragment != null) {
                            text = AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.FwdMessageToGroup, chat.title), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, onClick);
                        } else {
                            text = AndroidUtilities.replaceTags(LocaleController.formatString(R.string.FwdMessageToGroup, chat.title));
                        }
                    } else {
                        if (fragment != null) {
                            text = AndroidUtilities.replaceSingleTag(LocaleController.formatString(R.string.FwdMessagesToGroup, chat.title), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, onClick);
                        } else {
                            text = AndroidUtilities.replaceTags(LocaleController.formatString(R.string.FwdMessagesToGroup, chat.title));
                        }
                    }
                } else {
                    final TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(did);
                    if (messagesCount <= 1) {
                        final @StringRes int res = hasUndoButton ? R.string.FwdMessageToUserShort : R.string.FwdMessageToUser;
                        if (fragment != null) {
                            text = AndroidUtilities.replaceSingleTag(LocaleController.formatString(res, UserObject.getFirstName(user)), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, onClick);
                        } else {
                            text = AndroidUtilities.replaceTags(LocaleController.formatString(res, UserObject.getFirstName(user)));
                        }
                    } else {
                        final @StringRes int res = hasUndoButton ? R.string.FwdMessagesToUserShort : R.string.FwdMessagesToUser;
                        if (fragment != null) {
                            text = AndroidUtilities.replaceSingleTag(LocaleController.formatString(res, UserObject.getFirstName(user)), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, onClick);
                        } else {
                            text = AndroidUtilities.replaceTags(LocaleController.formatString(res, UserObject.getFirstName(user)));
                        }
                    }
                }
                layout.setAnimation(R.raw.forward, 30, 30);
                hapticDelay = 300;
            }
        } else {
            if (messagesCount <= 1) {
                text = AndroidUtilities.replaceTags(LocaleController.formatPluralString("FwdMessageToManyChats", dialogsCount));
            } else {
                text = AndroidUtilities.replaceTags(LocaleController.formatPluralString("FwdMessagesToManyChats", dialogsCount));
            }
            layout.setAnimation(R.raw.forward, 30, 30);
            hapticDelay = 300;
        }
        layout.textView.setText(text);
        if (hasUndoButton) {
            layout.setButton(new Bulletin.UndoButton(layout.getContext(), true, true, fragment != null ? fragment.getResourceProvider() : null)
                .setUndoAction(undoAction)
                .setDelayedAction(delayedActionOnce)
            );
        }

        if (hapticDelay > 0) {
            layout.postDelayed(() -> {
                layout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }, hapticDelay);
        }

        final Bulletin bulletin;
        if (containerLayout != null) {
            bulletin = Bulletin.make(containerLayout, layout, duration);
        } else if (fragment != null) {
            bulletin = Bulletin.make(fragment, layout, duration);
        } else {
            throw new IllegalArgumentException();
        }

        if (layout instanceof Bulletin.LottieLayoutWithReactions) {
            layout.textView.setSingleLine(false);
            layout.textView.setMaxLines(2);
            ((Bulletin.LottieLayoutWithReactions) layout).setBulletin(bulletin);

            bulletin.hideAfterBottomSheet(false);
        }

        return bulletin;
    }

    @CheckResult
    public static Bulletin createRemoveFromChatBulletin(BaseFragment fragment, TLRPC.User user, String chatName) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), fragment.getResourceProvider());
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
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), fragment.getResourceProvider());
        final String text;
        if (banned) {
            layout.setAnimation(R.raw.ic_ban, "Hand");
            text = LocaleController.getString(R.string.UserBlocked);
        } else {
            layout.setAnimation(R.raw.ic_unban, "Main", "Finger 1", "Finger 2", "Finger 3", "Finger 4");
            text = LocaleController.getString(R.string.UserUnblocked);
        }
        layout.textView.setText(AndroidUtilities.replaceTags(text));
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    @CheckResult
    public Bulletin createBanBulletin(boolean banned) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        final String text;
        if (banned) {
            layout.setAnimation(R.raw.ic_ban, "Hand");
            text = LocaleController.getString(R.string.UserBlocked);
        } else {
            layout.setAnimation(R.raw.ic_unban, "Main", "Finger 1", "Finger 2", "Finger 3", "Finger 4");
            text = LocaleController.getString(R.string.UserUnblocked);
        }
        layout.textView.setText(AndroidUtilities.replaceTags(text));
        return create(layout, Bulletin.DURATION_SHORT);
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

    @CheckResult
    public static Bulletin createSoundEnabledBulletin(BaseFragment fragment, int setting, Theme.ResourcesProvider resourcesProvider) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);

        final String text;
        final boolean soundOn;

        switch (setting) {
            case NotificationsController.SETTING_SOUND_ON:
                text = LocaleController.getString(R.string.SoundOnHint);
                soundOn = true;
                break;
            case NotificationsController.SETTING_SOUND_OFF:
                text = LocaleController.getString(R.string.SoundOffHint);
                soundOn = false;
                break;
            default:
                throw new IllegalArgumentException();
        }

        if (soundOn) {
            layout.setAnimation(R.raw.sound_on);
        } else {
            layout.setAnimation(R.raw.sound_off);
        }

        layout.textView.setText(text);
        return Bulletin.make(fragment, layout, Bulletin.DURATION_SHORT);
    }

    public Bulletin createMessagesTaggedBulletin(int messagesCount, TLRPC.Document document, Runnable onViewButton) {
        final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext(), resourcesProvider);
        layout.setAnimation(R.raw.tag_icon_3, 36, 36);
        layout.removeView(layout.textView);
        layout.textView = new AnimatedEmojiSpan.TextViewEmojis(layout.getContext());
        layout.textView.setTypeface(Typeface.SANS_SERIF);
        layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        layout.textView.setEllipsize(TextUtils.TruncateAt.END);
        layout.textView.setPadding(0, 0, 0, dp(8));

        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(dp(20));
        SpannableString spannable = new SpannableString("d");
        spannable.setSpan(new AnimatedEmojiSpan(document, textPaint.getFontMetricsInt()), 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        layout.textView.setText(
                new SpannableStringBuilder(messagesCount > 1 ?
                        LocaleController.formatPluralString("SavedTagMessagesTagged", messagesCount) :
                        LocaleController.getString(R.string.SavedTagMessageTagged))
                        .append(" ")
                        .append(spannable)
        );

        if (onViewButton != null) {
            layout.setButton(new Bulletin.UndoButton(getContext(), true, resourcesProvider).setText(LocaleController.getString(R.string.ViewAction)).setUndoAction(onViewButton));
        }

        layout.setTextColor(Theme.getColor(Theme.key_undo_infoColor, resourcesProvider));
        layout.addView(layout.textView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 56, 2, 8, 0));

        return create(layout, Bulletin.DURATION_LONG);
    }

    //endregion

    public static class UndoObject {
        public CharSequence undoText;
        public Runnable onUndo;
        public Runnable onAction;
    }
}
