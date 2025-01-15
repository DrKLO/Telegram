/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.LongSparseArray;
import android.util.Property;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Business.ChatAttachAlertQuickRepliesLayout;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GradientClip;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MessageSendPreview;
import org.telegram.ui.PassportActivity;
import org.telegram.ui.PaymentFormActivity;
import org.telegram.ui.PhotoPickerActivity;
import org.telegram.ui.PhotoPickerSearchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.WebAppDisclaimerAlert;
import org.telegram.ui.web.BotWebViewContainer;
import org.telegram.ui.bots.BotWebViewMenuContainer;
import org.telegram.ui.bots.ChatAttachAlertBotWebViewLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChatAttachAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, BottomSheet.BottomSheetDelegateInterface {

    public ChatActivity.ThemeDelegate parentThemeDelegate;

    private final NumberTextView captionLimitView;
    private final NumberTextView topCaptionLimitView;
    public boolean forUser;
    public boolean isPhotoPicker;
    public boolean isStickerMode;
    public Utilities.Callback2<String, TLRPC.InputDocument> customStickerHandler;
    private int currentLimit;
    private int codepointCount;

    public boolean canOpenPreview = false;
    private boolean isSoundPicker = false;
    public boolean isStoryLocationPicker = false;
    public boolean isBizLocationPicker = false;
    public boolean isStoryAudioPicker = false;
    private ImageUpdater.AvatarFor setAvatarFor;
    public boolean pinnedToTop;

    private PasscodeView passcodeView;
    private ChatAttachRestrictedLayout restrictedLayout;
    public ImageUpdater parentImageUpdater;
    public boolean destroyed;
    public boolean allowEnterCaption;
    private ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate documentsDelegate;
    public long dialogId;
    private boolean overrideBackgroundColor;

    public boolean captionAbove;

    public TLRPC.Chat getChat() {
        if (baseFragment instanceof ChatActivity) {
            return ((ChatActivity) baseFragment).getCurrentChat();
        } else {
            return MessagesController.getInstance(currentAccount).getChat(-dialogId);
        }
    }
    public void setCanOpenPreview(boolean canOpenPreview) {
        this.canOpenPreview = canOpenPreview;
        selectedArrowImageView.setVisibility(canOpenPreview && avatarPicker != 2 ? View.VISIBLE : View.GONE);
    }

    public float getClipLayoutBottom() {
        float alphaOffset = (frameLayout2.getMeasuredHeight() - dp(84)) * (1f - frameLayout2.getAlpha());
        return frameLayout2.getMeasuredHeight() - alphaOffset;
    }

    public void showBotLayout(long id, boolean animated) {
        showBotLayout(id, null, false, animated);
    }

    public void showBotLayout(long id, String startCommand, boolean justAdded, boolean animated) {
        if (botAttachLayouts.get(id) == null || !Objects.equals(startCommand, botAttachLayouts.get(id).getStartCommand()) || botAttachLayouts.get(id).needReload()) {
            if (baseFragment instanceof ChatActivity) {
                ChatAttachAlertBotWebViewLayout webViewLayout = new ChatAttachAlertBotWebViewLayout(this, getContext(), resourcesProvider);
                botAttachLayouts.put(id, webViewLayout);
                botAttachLayouts.get(id).setDelegate(new BotWebViewContainer.Delegate() {
                    private ValueAnimator botButtonAnimator;

                    @Override
                    public void onWebAppSetupClosingBehavior(boolean needConfirmation) {
                        webViewLayout.setNeedCloseConfirmation(needConfirmation);
                    }

                    @Override
                    public void onWebAppSwipingBehavior(boolean allowSwipes) {
                        webViewLayout.setAllowSwipes(allowSwipes);
                    }

                    @Override
                    public void onCloseRequested(Runnable callback) {
                        if (currentAttachLayout != webViewLayout) {
                            return;
                        }
                        ChatAttachAlert.this.setFocusable(false);
                        ChatAttachAlert.this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

                        dismiss();
                        AndroidUtilities.runOnUIThread(() -> {
                            if (callback != null) {
                                callback.run();
                            }
                        }, 150);
                    }

                    @Override
                    public void onWebAppSetActionBarColor(int colorKey, int color, boolean isOverrideColor) {
                        int from = ((ColorDrawable) actionBar.getBackground()).getColor();
                        int to = color;

                        BotWebViewMenuContainer.ActionBarColorsAnimating actionBarColorsAnimating = new BotWebViewMenuContainer.ActionBarColorsAnimating();
                        actionBarColorsAnimating.setFrom(overrideBackgroundColor ? from : 0, resourcesProvider);
                        overrideBackgroundColor = isOverrideColor;
                       // actionBarIsLight = ColorUtils.calculateLuminance(color) < 0.5f;
                        actionBarColorsAnimating.setTo(overrideBackgroundColor ? to : 0, resourcesProvider);

                        ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(200);
                        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        animator.addUpdateListener(animation -> {
                            float progress = (float) animation.getAnimatedValue();
                            actionBar.setBackgroundColor(ColorUtils.blendARGB(from, to, progress));
                            webViewLayout.setCustomActionBarBackground(ColorUtils.blendARGB(from, to, progress));
                            currentAttachLayout.invalidate();
                            sizeNotifierFrameLayout.invalidate();
                            actionBarColorsAnimating.updateActionBar(actionBar, progress);
                        });
                        animator.start();
                    }

                    @Override
                    public void onWebAppSetBackgroundColor(int color) {
                        webViewLayout.setCustomBackground(color);
                    }

                    @Override
                    public void onWebAppOpenInvoice(TLRPC.InputInvoice inputInvoice, String slug, TLObject response) {
                        BaseFragment parentFragment = baseFragment;
                        PaymentFormActivity paymentFormActivity = null;
                        if (response instanceof TLRPC.TL_payments_paymentFormStars) {
                            final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                            progressDialog.showDelayed(150);
                            StarsController.getInstance(currentAccount).openPaymentForm(null, inputInvoice, (TLRPC.TL_payments_paymentFormStars) response, () -> {
                                progressDialog.dismiss();
                            }, status -> {
                                webViewLayout.getWebViewContainer().onInvoiceStatusUpdate(slug, status);
                            });
                            AndroidUtilities.hideKeyboard(webViewLayout);
                            return;
                        } else if (response instanceof TLRPC.PaymentForm) {
                            TLRPC.PaymentForm form = (TLRPC.PaymentForm) response;
                            MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                            paymentFormActivity = new PaymentFormActivity(form, slug, parentFragment);
                        } else if (response instanceof TLRPC.PaymentReceipt) {
                            paymentFormActivity = new PaymentFormActivity((TLRPC.PaymentReceipt) response);
                        }

                        if (paymentFormActivity != null) {
                            webViewLayout.scrollToTop();

                            AndroidUtilities.hideKeyboard(webViewLayout);
                            OverlayActionBarLayoutDialog overlayActionBarLayoutDialog = new OverlayActionBarLayoutDialog(parentFragment.getParentActivity(), resourcesProvider);
                            overlayActionBarLayoutDialog.show();
                            paymentFormActivity.setPaymentFormCallback(status -> {
                                if (status != PaymentFormActivity.InvoiceStatus.PENDING) {
                                    overlayActionBarLayoutDialog.dismiss();
                                }

                                webViewLayout.getWebViewContainer().onInvoiceStatusUpdate(slug, status.name().toLowerCase(Locale.ROOT));
                            });
                            paymentFormActivity.setResourcesProvider(resourcesProvider);
                            overlayActionBarLayoutDialog.addFragment(paymentFormActivity);
                        }
                    }

                    @Override
                    public void onWebAppExpand() {
                        if (currentAttachLayout != webViewLayout) {
                            return;
                        }

                        if (webViewLayout.canExpandByRequest()) {
                            webViewLayout.scrollToTop();
                        }
                    }

                    @Override
                    public void onWebAppSwitchInlineQuery(TLRPC.User botUser, String query, List<String> chatTypes) {
                        if (chatTypes.isEmpty()) {
                            if (baseFragment instanceof ChatActivity) {
                                ((ChatActivity) baseFragment).getChatActivityEnterView().setFieldText("@" + UserObject.getPublicUsername(botUser) + " " + query);
                            }
                            dismiss(true);
                        } else {
                            Bundle args = new Bundle();
                            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_START_ATTACH_BOT);
                            args.putBoolean("onlySelect", true);

                            args.putBoolean("allowGroups", chatTypes.contains("groups"));
                            args.putBoolean("allowLegacyGroups", chatTypes.contains("groups"));
                            args.putBoolean("allowMegagroups", chatTypes.contains("groups"));
                            args.putBoolean("allowUsers", chatTypes.contains("users"));
                            args.putBoolean("allowChannels", chatTypes.contains("channels"));
                            args.putBoolean("allowBots", chatTypes.contains("bots"));

                            DialogsActivity dialogsActivity = new DialogsActivity(args);
                            OverlayActionBarLayoutDialog overlayActionBarLayoutDialog = new OverlayActionBarLayoutDialog(getContext(), resourcesProvider);
                            dialogsActivity.setDelegate((fragment, dids, message1, param, notify, scheduleDate, topicsFragment) -> {
                                long did = dids.get(0).dialogId;

                                Bundle args1 = new Bundle();
                                args1.putBoolean("scrollToTopOnResume", true);
                                if (DialogObject.isEncryptedDialog(did)) {
                                    args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                                } else if (DialogObject.isUserDialog(did)) {
                                    args1.putLong("user_id", did);
                                } else {
                                    args1.putLong("chat_id", -did);
                                }
                                args1.putString("start_text", "@" + UserObject.getPublicUsername(botUser) + " " + query);

                                BaseFragment lastFragment = baseFragment;
                                if (MessagesController.getInstance(currentAccount).checkCanOpenChat(args1, lastFragment)) {
                                    overlayActionBarLayoutDialog.dismiss();
                                    dismiss(true);

                                    lastFragment.presentFragment(new INavigationLayout.NavigationParams(new ChatActivity(args1)).setRemoveLast(true));
                                }
                                return true;
                            });
                            overlayActionBarLayoutDialog.show();
                            overlayActionBarLayoutDialog.addFragment(dialogsActivity);
                        }
                    }

                    @Override
                    public void onSetupSecondaryButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect, String position) {

                    }

                    @Override
                    public void onSetupMainButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible, boolean hasShineEffect) {
                        if (currentAttachLayout != webViewLayout || !webViewLayout.isBotButtonAvailable() && startCommand == null) {
                            return;
                        }
                        botMainButtonTextView.setClickable(isActive);
                        botMainButtonTextView.setText(text);
                        botMainButtonTextView.setTextColor(textColor);
                        botMainButtonTextView.setBackground(BotWebViewContainer.getMainButtonRippleDrawable(color));
                        if (botButtonWasVisible != isVisible) {
                            botButtonWasVisible = isVisible;

                            if (botButtonAnimator != null) {
                                botButtonAnimator.cancel();
                            }
                            botButtonAnimator = ValueAnimator.ofFloat(isVisible ? 0 : 1, isVisible ? 1 : 0).setDuration(250);
                            botButtonAnimator.addUpdateListener(animation -> {
                                float value = (float) animation.getAnimatedValue();
                                buttonsRecyclerView.setAlpha(1f - value);
                                botMainButtonTextView.setAlpha(value);
                                botMainButtonOffsetY = value * dp(36);
                                shadow.setTranslationY(botMainButtonOffsetY);
                                buttonsRecyclerView.setTranslationY(botMainButtonOffsetY);
                            });
                            botButtonAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(Animator animation) {
                                    if (isVisible) {
                                        botMainButtonTextView.setAlpha(0f);
                                        botMainButtonTextView.setVisibility(View.VISIBLE);

                                        int offsetY = dp(36);
                                        for (int i = 0; i < botAttachLayouts.size(); i++) {
                                            botAttachLayouts.valueAt(i).setMeasureOffsetY(offsetY);
                                        }
                                    } else {
                                        buttonsRecyclerView.setAlpha(0f);
                                        buttonsRecyclerView.setVisibility(View.VISIBLE);
                                    }
                                }

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (!isVisible) {
                                        botMainButtonTextView.setVisibility(View.GONE);
                                    } else {
                                        buttonsRecyclerView.setVisibility(View.GONE);
                                    }

                                    int offsetY = isVisible ? dp(36) : 0;
                                    for (int i = 0; i < botAttachLayouts.size(); i++) {
                                        botAttachLayouts.valueAt(i).setMeasureOffsetY(offsetY);
                                    }

                                    if (botButtonAnimator == animation) {
                                        botButtonAnimator = null;
                                    }
                                }
                            });
                            botButtonAnimator.start();
                        }
                        botProgressView.setProgressColor(textColor);
                        if (botButtonProgressWasVisible != isProgressVisible) {
                            botProgressView.animate().cancel();
                            if (isProgressVisible) {
                                botProgressView.setAlpha(0f);
                                botProgressView.setVisibility(View.VISIBLE);
                            }
                            botProgressView.animate().alpha(isProgressVisible ? 1f : 0f)
                                    .scaleX(isProgressVisible ? 1f : 0.1f)
                                    .scaleY(isProgressVisible ? 1f : 0.1f)
                                    .setDuration(250)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            botButtonProgressWasVisible = isProgressVisible;
                                            if (!isProgressVisible) {
                                                botProgressView.setVisibility(View.GONE);
                                            }
                                        }
                                    }).start();
                        }
                    }

                    @Override
                    public void onSetBackButtonVisible(boolean visible) {
                        AndroidUtilities.updateImageViewImageAnimated(actionBar.getBackButton(), visible ? R.drawable.ic_ab_back : R.drawable.ic_close_white);
                    }

                    @Override
                    public void onSetSettingsButtonVisible(boolean visible) {
                        if (webViewLayout.settingsItem != null) {
                            webViewLayout.settingsItem.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }
                    }

                    @Override
                    public boolean isClipboardAvailable() {
                        return MediaDataController.getInstance(currentAccount).botInAttachMenu(id);
                    }
                });
                MessageObject replyingObject = ((ChatActivity) baseFragment).getChatActivityEnterView().getReplyingMessageObject();
                botAttachLayouts.get(id).requestWebView(currentAccount, ((ChatActivity) baseFragment).getDialogId(), id, false, replyingObject != null ? replyingObject.messageOwner.id : 0, startCommand);
            }
        }
        if (botAttachLayouts.get(id) != null) {
            botAttachLayouts.get(id).disallowSwipeOffsetAnimation();
            showLayout(botAttachLayouts.get(id), -id, animated);

            if (justAdded) {
                botAttachLayouts.get(id).showJustAddedBulletin();
            }
        }
    }

    public boolean checkCaption(CharSequence text) {
        if (baseFragment instanceof ChatActivity) {
            long dialogId = ((ChatActivity) baseFragment).getDialogId();
            return ChatActivityEnterView.checkPremiumAnimatedEmoji(currentAccount, dialogId, baseFragment, sizeNotifierFrameLayout, text);
        } else {
            return false;
        }
    }

    public void avatarFor(ImageUpdater.AvatarFor avatarFor) {
        setAvatarFor = avatarFor;
    }

    public ImageUpdater.AvatarFor getAvatarFor() {
        return setAvatarFor;
    }

    public void setImageUpdater(ImageUpdater imageUpdater) {
        parentImageUpdater = imageUpdater;
    }

    public void setupPhotoPicker(String title) {
        avatarPicker = 1;
        isPhotoPicker = true;
        avatarSearch = false;
        typeButtonsAvailable = false;
        videosEnabled = false;
        buttonsRecyclerView.setVisibility(View.GONE);
        shadow.setVisibility(View.GONE);
        selectedTextView.setText(title);
        if (photoLayout != null) {
            photoLayout.updateAvatarPicker();
        }
    }

    public void presentFragment(PhotoPickerActivity fragment) {
        if (baseFragment != null) {
            baseFragment.presentFragment(fragment);
        } else {
            BaseFragment lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment != null) {
                lastFragment.presentFragment(fragment);
            }
        }
    }

    public void setDialogId(long dialogId) {
        this.dialogId = dialogId;
    }

    public interface ChatAttachViewDelegate {
        default boolean selectItemOnClicking() {
            return false;
        }

        void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, long effectId, boolean invertMedia, boolean forceDocument);

        default void onCameraOpened() {
        }

        default View getRevealView() {
            return null;
        }

        default void didSelectBot(TLRPC.User user) {

        }

        default boolean needEnterComment() {
            return false;
        }

        default void doOnIdle(Runnable runnable) {
            runnable.run();
        }

        default void openAvatarsSearch() {

        }

        default void onWallpaperSelected(Object object) {

        }

        default void sendAudio(ArrayList<MessageObject> audios, CharSequence caption, boolean notify, int scheduleDate, long effectId, boolean invertMedia) {

        }
    }

    public float translationProgress = 0;
    public final Property<AttachAlertLayout, Float> ATTACH_ALERT_LAYOUT_TRANSLATION = new AnimationProperties.FloatProperty<AttachAlertLayout>("translation") {
        @Override
        public void setValue(AttachAlertLayout object, float value) {
            translationProgress = value;
            if (nextAttachLayout == null) {
                return;
            }
            if (nextAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview || currentAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview) {
                int width = Math.max(nextAttachLayout.getWidth(), currentAttachLayout.getWidth());
                if (nextAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview) {
                    currentAttachLayout.setTranslationX(value * -width);
                    nextAttachLayout.setTranslationX((1f - value) * width);
                } else {
                    currentAttachLayout.setTranslationX(value * width);
                    nextAttachLayout.setTranslationX(-width * (1f - value));
                }
            } else {
                nextAttachLayout.setAlpha(value);
                nextAttachLayout.onHideShowProgress(value);
                if (nextAttachLayout == pollLayout || currentAttachLayout == pollLayout) {
                    updateSelectedPosition(nextAttachLayout == pollLayout ? 1 : 0);
                }
                nextAttachLayout.setTranslationY(dp(78) * value);
                currentAttachLayout.onHideShowProgress(1.0f - Math.min(1.0f, value / 0.7f));
                currentAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
            }
            if (viewChangeAnimator != null) {
                updateSelectedPosition(1);
            }
            containerView.invalidate();
        }

        @Override
        public Float get(AttachAlertLayout object) {
            return translationProgress;
        }
    };

    public static class AttachAlertLayout extends FrameLayout {

        protected final Theme.ResourcesProvider resourcesProvider;
        protected ChatAttachAlert parentAlert;

        public AttachAlertLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            parentAlert = alert;
        }

        public boolean onSheetKeyDown(int keyCode, KeyEvent event) {
            return false;
        }

        public boolean onDismiss() {
            return false;
        }

        public boolean onCustomMeasure(View view, int width, int height) {
            return false;
        }

        public boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
            return false;
        }

        public boolean onContainerViewTouchEvent(MotionEvent event) {
            return false;
        }

        public void onPreMeasure(int availableWidth, int availableHeight) {

        }

        public void onMenuItemClick(int id) {

        }

        public boolean hasCustomBackground() {
            return false;
        }

        public int getCustomBackground() {
            return 0;
        }

        public boolean hasCustomActionBarBackground() {
            return false;
        }

        public int getCustomActionBarBackground() {
            return 0;
        }


        public void onButtonsTranslationYUpdated() {

        }

        public boolean canScheduleMessages() {
            return true;
        }

        public void checkColors() {

        }

        public ArrayList<ThemeDescription> getThemeDescriptions() {
            return null;
        }

        public void onPause() {

        }

        public void onResume() {

        }

        public boolean onDismissWithTouchOutside() {
            return true;
        }

        public boolean canDismissWithTouchOutside() {
            return true;
        }

        public void onDismissWithButtonClick(int item) {

        }

        public void onContainerTranslationUpdated(float currentPanTranslationY) {

        }

        public void onHideShowProgress(float progress) {

        }

        public void onOpenAnimationEnd() {

        }

        public void onInit(boolean hasVideo, boolean hasPhoto, boolean hasDocuments) {

        }

        public int getSelectedItemsCount() {
            return 0;
        }

        public void onSelectedItemsCountChanged(int count) {

        }

        public void applyCaption(CharSequence text) {

        }

        public void onDestroy() {

        }

        public void onHide() {

        }

        public void onHidden() {

        }

        public int getCurrentItemTop() {
            return 0;
        }

        public int getFirstOffset() {
            return 0;
        }

        public int getButtonsHideOffset() {
            return dp(needsActionBar() != 0 ? 12 : 17);
        }

        public int getListTopPadding() {
            return 0;
        }

        public int needsActionBar() {
            return 0;
        }

        public void sendSelectedItems(boolean notify, int scheduleDate, long effectId, boolean invertMedia) {

        }

        public void onShow(AttachAlertLayout previousLayout) {

        }

        public void onShown() {

        }

        public void scrollToTop() {

        }

        public boolean onBackPressed() {
            return false;
        }

        public int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }

        public boolean shouldHideBottomButtons() {
            return true;
        }

        public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        }

        public void onPanTransitionEnd() {
        }
    }

    @Nullable
    public final BaseFragment baseFragment;
    public boolean inBubbleMode;
    private MessageSendPreview messageSendPreview;

    private View shadow;

    private ChatAttachAlertPhotoLayout photoLayout;
    private ChatAttachAlertContactsLayout contactsLayout;
    private ChatAttachAlertAudioLayout audioLayout;
    private ChatAttachAlertPollLayout pollLayout;
    private ChatAttachAlertLocationLayout locationLayout;
    private ChatAttachAlertDocumentLayout documentLayout;
    private ChatAttachAlertPhotoLayoutPreview photoPreviewLayout;
    public ChatAttachAlertColorsLayout colorsLayout;
    private ChatAttachAlertQuickRepliesLayout quickRepliesLayout;
    private AttachAlertLayout[] layouts = new AttachAlertLayout[8];
    private LongSparseArray<ChatAttachAlertBotWebViewLayout> botAttachLayouts = new LongSparseArray<>();
    private AttachAlertLayout currentAttachLayout;
    private AttachAlertLayout nextAttachLayout;

    private FrameLayout captionContainer;
    private FrameLayout frameLayout2;
    public EditTextEmoji commentTextView;
    public FrameLayout moveCaptionButton;
    public ImageView moveCaptionButtonIcon;
    private int[] commentTextViewLocation = new int[2];
    private FrameLayout writeButtonContainer;
    private ChatActivityEnterView.SendButton writeButton;
    private Drawable writeButtonDrawable;
    private View selectedCountView;
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AnimatorSet commentsAnimator;

    public FrameLayout topCommentContainer;
    public EditTextEmoji topCommentTextView;
    public ImageView topCommentMoveButton;

    protected int avatarPicker;
    protected boolean avatarSearch;
    protected boolean typeButtonsAvailable;

    private boolean stories;
    public boolean storyMediaPicker;

    boolean sendButtonEnabled = true;
    private float sendButtonEnabledProgress = 1f;
    private ValueAnimator sendButtonColorAnimator;

    private long selectedId;

    protected float cornerRadius = 1.0f;

    public ActionBar actionBar;
    private View actionBarShadow;
    private AnimatorSet actionBarAnimation;
    private AnimatorSet menuAnimator;
    protected ActionBarMenuItem selectedMenuItem;
    @Nullable
    protected ActionBarMenuItem searchItem;
    protected ActionBarMenuItem doneItem;
    protected FrameLayout headerView;
    protected TextView selectedTextView;
    protected ActionBarMenuItem optionsItem;
    protected LinearLayout selectedView;
    protected ImageView selectedArrowImageView;
    protected LinearLayout mediaPreviewView;
    protected TextView mediaPreviewTextView;
    private float baseSelectedTextViewTranslationY;
    private boolean menuShowed;
    public SizeNotifierFrameLayout sizeNotifierFrameLayout;
    private boolean openTransitionFinished;

    private Object viewChangeAnimator;

    private boolean enterCommentEventSent;

    protected RecyclerListView buttonsRecyclerView;
    private LinearLayoutManager buttonsLayoutManager;
    private ButtonsAdapter buttonsAdapter;

    private boolean botButtonProgressWasVisible = false;
    private RadialProgressView botProgressView;

    private boolean botButtonWasVisible = false;
    private TextView botMainButtonTextView;
    private float botMainButtonOffsetY;

    private int editType;
    protected MessageObject editingMessageObject;

    private boolean buttonPressed;

    public final int currentAccount = UserConfig.selectedAccount;

    private boolean documentsEnabled = true;
    private boolean photosEnabled = true;
    private boolean videosEnabled = true;
    private boolean musicEnabled = true;
    private boolean pollsEnabled = true;
    private boolean plainTextEnabled = true;

    protected int maxSelectedPhotos = -1;
    protected boolean allowOrder = true;
    protected boolean openWithFrontFaceCamera;
    private float captionEditTextTopOffset;
    private float chatActivityEnterViewAnimateFromTop;
    private ValueAnimator topBackgroundAnimator;

    private int attachItemSize = dp(85);

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    protected ChatAttachViewDelegate delegate;

    public int[] scrollOffsetY = new int[2];
    private int previousScrollOffsetY;
    private float fromScrollY;
    private float toScrollY;

    protected boolean paused;

    private final Paint attachButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float bottomPannelTranslation;
    private final boolean forceDarkTheme;
    private final boolean showingFromDialog;

    protected boolean captionLimitBulletinShown = false;

    private class AttachButton extends FrameLayout {

        private TextView textView;
        private RLottieImageView imageView;
        private boolean checked;
        private int backgroundKey;
        private int textKey;
        private float checkedState;
        private Animator checkAnimator;
        private int currentId;

        public AttachButton(Context context) {
            super(context);
            setWillNotDraw(false);
            setFocusable(true);

            imageView = new RLottieImageView(context) {
                @Override
                public void setScaleX(float scaleX) {
                    super.setScaleX(scaleX);
                    AttachButton.this.invalidate();
                }
            };
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(32, 32, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 0));

            textView = new TextView(context);
            textView.setMaxLines(2);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setLineSpacing(-dp(2), 1.0f);
            textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 62, 0, 0));
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setText(textView.getText());
            info.setEnabled(true);
            info.setSelected(checked);
        }

        void updateCheckedState(boolean animate) {
            if (checked == (currentId == selectedId)) {
                return;
            }
            checked = currentId == selectedId;
            if (checkAnimator != null) {
                checkAnimator.cancel();
            }
            if (animate) {
                if (checked) {
                    imageView.setProgress(0.0f);
                    imageView.playAnimation();
                }
                checkAnimator = ObjectAnimator.ofFloat(this, "checkedState", checked ? 1f : 0f);
                checkAnimator.setDuration(200);
                checkAnimator.start();
            } else {
                imageView.stopAnimation();
                imageView.setProgress(0.0f);
                setCheckedState(checked ? 1f : 0f);
            }
        }

        @Keep
        public void setCheckedState(float state) {
            checkedState = state;
            imageView.setScaleX(1.0f - 0.06f * state);
            imageView.setScaleY(1.0f - 0.06f * state);
            textView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextGray2), getThemedColor(textKey), checkedState));
            invalidate();
        }

        @Keep
        public float getCheckedState() {
            return checkedState;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateCheckedState(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(attachItemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(84), MeasureSpec.EXACTLY));
        }

        public void setTextAndIcon(int id, CharSequence text, RLottieDrawable drawable, int background, int textColor) {
            currentId = id;
            textView.setText(text);
            imageView.setAnimation(drawable);
            backgroundKey = background;
            textKey = textColor;
            textView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextGray2), getThemedColor(textKey), checkedState));
        }

        public void setTextAndIcon(int id, CharSequence text, Drawable drawable, int background, int textColor) {
            currentId = id;
            textView.setText(text);
            imageView.setImageDrawable(drawable);
            backgroundKey = background;
            textKey = textColor;
            textView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextGray2), getThemedColor(textKey), checkedState));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float scale = imageView.getScaleX() + 0.06f * checkedState;
            float radius = dp(23) * scale;

            float cx = imageView.getLeft() + imageView.getMeasuredWidth() / 2f;
            float cy = imageView.getTop() + imageView.getMeasuredWidth() / 2f;

            attachButtonPaint.setColor(getThemedColor(backgroundKey));
            attachButtonPaint.setStyle(Paint.Style.STROKE);
            attachButtonPaint.setStrokeWidth(dp(3) * scale);
            attachButtonPaint.setAlpha(Math.round(255f * checkedState));
            canvas.drawCircle(cx, cy, radius - 0.5f * attachButtonPaint.getStrokeWidth(), attachButtonPaint);

            attachButtonPaint.setAlpha(255);
            attachButtonPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, radius - dp(5) * checkedState, attachButtonPaint);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    private class AttachBotButton extends FrameLayout {
        private BackupImageView imageView;
        private TextView nameTextView;
        private AvatarDrawable avatarDrawable = new AvatarDrawable();

        private TLRPC.User currentUser;
        private TLRPC.TL_attachMenuBot attachMenuBot;

        private float checkedState;
        private Boolean checked;
        private ValueAnimator checkAnimator;

        private int textColor;
        private int iconBackgroundColor;

        private View selector;

        public AttachBotButton(Context context) {
            super(context);

            setWillNotDraw(false);
            setFocusable(true);
            setFocusableInTouchMode(true);

            imageView = new BackupImageView(context) {
                {
                    imageReceiver.setDelegate((imageReceiver1, set, thumb, memCache) -> {
                        Drawable drawable = imageReceiver1.getDrawable();
                        if (drawable instanceof RLottieDrawable) {
                            ((RLottieDrawable) drawable).setCustomEndFrame(0);
                            ((RLottieDrawable) drawable).stop();
                            ((RLottieDrawable) drawable).setProgress(0, false);
                        }
                    });
                }

                @Override
                public void setScaleX(float scaleX) {
                    super.setScaleX(scaleX);
                    AttachBotButton.this.invalidate();
                }
            };
            imageView.setRoundRadius(dp(25));
            addView(imageView, LayoutHelper.createFrame(46, 46, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 9, 0, 0));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                selector = new View(context);
                selector.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 1, dp(23)));
                addView(selector, LayoutHelper.createFrame(46, 46, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 9, 0, 0));
            }

            nameTextView = new TextView(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            nameTextView.setLines(1);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 60, 6, 0));
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            if (selector != null && checked) {
                info.setCheckable(true);
                info.setChecked(true);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(attachItemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(100), MeasureSpec.EXACTLY));
        }

        public void setCheckedState(float state) {
            checkedState = state;
            imageView.setScaleX(1.0f - 0.06f * state);
            imageView.setScaleY(1.0f - 0.06f * state);
            nameTextView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextGray2), textColor, checkedState));
            invalidate();
        }

        private void updateMargins() {
            MarginLayoutParams params = (MarginLayoutParams) nameTextView.getLayoutParams();
            params.topMargin = dp(attachMenuBot != null ? 62 : 60);
            params = (MarginLayoutParams) imageView.getLayoutParams();
            params.topMargin = dp(attachMenuBot != null ? 11 : 9);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (attachMenuBot != null) {
                float imageScale = imageView.getScaleX();
                float scale = imageScale + 0.06f * checkedState;
                float radius = dp(23) * scale;

                float cx = imageView.getLeft() + imageView.getMeasuredWidth() / 2f;
                float cy = imageView.getTop() + imageView.getMeasuredWidth() / 2f;

                attachButtonPaint.setColor(iconBackgroundColor);
                attachButtonPaint.setStyle(Paint.Style.STROKE);
                attachButtonPaint.setStrokeWidth(dp(3) * scale);
                attachButtonPaint.setAlpha(Math.round(255f * checkedState));
                canvas.drawCircle(cx, cy, radius - 0.5f * attachButtonPaint.getStrokeWidth(), attachButtonPaint);

                attachButtonPaint.setAlpha(255);
                attachButtonPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, radius - dp(5) * checkedState, attachButtonPaint);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateCheckedState(false);
        }

        void updateCheckedState(boolean animate) {
            boolean newChecked = attachMenuBot != null && -currentUser.id == selectedId;
            if (checked != null && checked == newChecked && animate) {
                return;
            }
            checked = newChecked;
            if (checkAnimator != null) {
                checkAnimator.cancel();
            }
            RLottieDrawable drawable = imageView.getImageReceiver().getLottieAnimation();
            if (animate) {
                if (checked && drawable != null) {
                    drawable.setAutoRepeat(0);
                    drawable.setCustomEndFrame(-1);
                    drawable.setProgress(0, false);
                    drawable.start();
                }

                checkAnimator = ValueAnimator.ofFloat(checked ? 0f : 1f, checked ? 1f : 0f);
                checkAnimator.addUpdateListener(animation -> setCheckedState((float) animation.getAnimatedValue()));
                checkAnimator.setDuration(200);
                checkAnimator.start();
            } else {
                if (drawable != null) {
                    drawable.stop();
                    drawable.setProgress(0, false);
                }
                setCheckedState(checked ? 1f : 0f);
            }
        }

        public void setUser(TLRPC.User user) {
            if (user == null) {
                return;
            }
            nameTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            currentUser = user;
            nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
            avatarDrawable.setInfo(currentAccount, user);
            imageView.setForUserOrChat(user, avatarDrawable);
            imageView.setSize(-1, -1);
            imageView.setColorFilter(null);
            attachMenuBot = null;
            selector.setVisibility(VISIBLE);
            updateMargins();
            setCheckedState(0f);
            invalidate();
        }

        public void setAttachBot(TLRPC.User user, TLRPC.TL_attachMenuBot bot) {
            if (user == null || bot == null) {
                return;
            }
            nameTextView.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            currentUser = user;
            nameTextView.setText(bot.short_name);
            avatarDrawable.setInfo(currentAccount, user);

            boolean animated = true;
            TLRPC.TL_attachMenuBotIcon icon = MediaDataController.getAnimatedAttachMenuBotIcon(bot);
            if (icon == null) {
                icon = MediaDataController.getStaticAttachMenuBotIcon(bot);
                animated = false;
            }
            if (icon != null) {
                textColor = getThemedColor(Theme.key_chat_attachContactText);
                iconBackgroundColor = getThemedColor(Theme.key_chat_attachContactBackground);

                for (TLRPC.TL_attachMenuBotIconColor color : icon.colors) {
                    switch (color.name) {
                        case MediaDataController.ATTACH_MENU_BOT_COLOR_LIGHT_ICON:
                            if (!Theme.getCurrentTheme().isDark()) {
                                iconBackgroundColor = color.color;
                            }
                            break;
                        case MediaDataController.ATTACH_MENU_BOT_COLOR_LIGHT_TEXT:
                            if (!Theme.getCurrentTheme().isDark()) {
                                textColor = color.color;
                            }
                            break;
                        case MediaDataController.ATTACH_MENU_BOT_COLOR_DARK_ICON:
                            if (Theme.getCurrentTheme().isDark()) {
                                iconBackgroundColor = color.color;
                            }
                            break;
                        case MediaDataController.ATTACH_MENU_BOT_COLOR_DARK_TEXT:
                            if (Theme.getCurrentTheme().isDark()) {
                                textColor = color.color;
                            }
                            break;
                    }
                }
                textColor = ColorUtils.setAlphaComponent(textColor, 0xFF);
                iconBackgroundColor = ColorUtils.setAlphaComponent(iconBackgroundColor, 0xFF);

                TLRPC.Document iconDoc = icon.icon;
                imageView.getImageReceiver().setAllowStartLottieAnimation(false);
                imageView.setImage(ImageLocation.getForDocument(iconDoc), String.valueOf(bot.bot_id), animated ? "tgs" : "svg", DocumentObject.getSvgThumb(iconDoc, Theme.key_windowBackgroundGray, 1f), bot);
            }

            imageView.setSize(dp(28), dp(28));
            imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_attachIcon), PorterDuff.Mode.SRC_IN));
            attachMenuBot = bot;
            selector.setVisibility(GONE);
            updateMargins();
            setCheckedState(0f);
            invalidate();
        }
    }

    private ArrayList<android.graphics.Rect> exclusionRects = new ArrayList<>();
    private android.graphics.Rect exclustionRect = new Rect();

    float currentPanTranslationY;

    public ChatAttachAlert(Context context, final BaseFragment parentFragment, boolean forceDarkTheme, boolean showingFromDialog) {
        this(context, parentFragment, forceDarkTheme, showingFromDialog, true, null);
    }

    @SuppressLint("ClickableViewAccessibility")
    public ChatAttachAlert(Context context, final @Nullable BaseFragment parentFragment, boolean forceDarkTheme, boolean showingFromDialog, boolean needCamera, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        if (parentFragment instanceof ChatActivity) {
            setImageReceiverNumLevel(0, 4);
        }
        this.forceDarkTheme = forceDarkTheme;
        this.showingFromDialog = showingFromDialog;
        drawNavigationBar = true;
        inBubbleMode = parentFragment instanceof ChatActivity && parentFragment.isInBubbleMode();
        openInterpolator = new OvershootInterpolator(0.7f);
        baseFragment = parentFragment;
        useSmoothKeyboard = true;
        setDelegate(this);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.reloadInlineHints);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.attachMenuBotsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.quickRepliesUpdated);
        exclusionRects.add(exclustionRect);

        sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private Bulletin.Delegate bulletinDelegate = new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return getHeight() - frameLayout2.getTop() + dp(52);
                }
            };
            private int lastNotifyWidth;
            private RectF rect = new RectF();
            private boolean ignoreLayout;
            private float initialTranslationY;

            AdjustPanLayoutHelper adjustPanLayoutHelper = new AdjustPanLayoutHelper(this) {

                @Override
                protected void onTransitionStart(boolean keyboardVisible, int contentHeight) {
                    super.onTransitionStart(keyboardVisible, contentHeight);
                    if (previousScrollOffsetY > 0 && previousScrollOffsetY != scrollOffsetY[0] && keyboardVisible) {
                        fromScrollY = previousScrollOffsetY;
                        toScrollY = scrollOffsetY[0];
                    } else {
                        fromScrollY = -1;
                    }
                    invalidate();

                    if (currentAttachLayout instanceof ChatAttachAlertBotWebViewLayout) {
                        if (!botButtonWasVisible) {
                            if (keyboardVisible) {
                                shadow.setVisibility(GONE);
                                buttonsRecyclerView.setVisibility(GONE);
                            } else {
                                shadow.setVisibility(VISIBLE);
                                buttonsRecyclerView.setVisibility(VISIBLE);
                            }
                        }
                    }

                    currentAttachLayout.onPanTransitionStart(keyboardVisible, contentHeight);
                }

                @Override
                protected void onTransitionEnd() {
                    super.onTransitionEnd();
                    updateLayout(currentAttachLayout, false, 0);
                    previousScrollOffsetY = scrollOffsetY[0];
                    currentAttachLayout.onPanTransitionEnd();

                    if (currentAttachLayout instanceof ChatAttachAlertBotWebViewLayout) {
                        if (!botButtonWasVisible) {
                            int offsetY = keyboardVisible ? dp(84) : 0;
                            for (int i = 0; i < botAttachLayouts.size(); i++) {
                                botAttachLayouts.valueAt(i).setMeasureOffsetY(offsetY);
                            }
                        }
                    }
                }

                @Override
                protected void onPanTranslationUpdate(float y, float progress, boolean keyboardVisible) {
                    currentPanTranslationY = y;
                    if (fromScrollY > 0) {
                        currentPanTranslationY += (fromScrollY - toScrollY) * (1f - progress);
                    }
                    actionBar.setTranslationY(currentPanTranslationY);
                    selectedMenuItem.setTranslationY(currentPanTranslationY);
                    if (searchItem != null) {
                        searchItem.setTranslationY(currentPanTranslationY);
                    }
                    doneItem.setTranslationY(currentPanTranslationY);
                    actionBarShadow.setTranslationY(currentPanTranslationY + topCommentContainer.getMeasuredHeight() * topCommentContainer.getAlpha());
                    updateSelectedPosition(0);

                    setCurrentPanTranslationY(currentPanTranslationY);
                    invalidate();
                    frameLayout2.invalidate();

                    updateCommentTextViewPosition();

                    if (currentAttachLayout != null) {
                        currentAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
                    }
                }

                @Override
                protected boolean heightAnimationEnabled() {
                    if (isDismissed() || !openTransitionFinished) {
                        return false;
                    }
                    return currentAttachLayout != pollLayout && !getCommentView().isPopupVisible() || currentAttachLayout == pollLayout && !pollLayout.isPopupVisible();
                }
            };

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (currentAttachLayout.onContainerViewTouchEvent(ev)) {
                    return true;
                }
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY[0] != 0 && ev.getY() < getCurrentTop() && actionBar.getAlpha() == 0.0f) {
                    onDismissWithTouchOutside();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (currentAttachLayout.onContainerViewTouchEvent(event)) {
                    return true;
                }
                return !isDismissed() && super.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight;
                if (getLayoutParams().height > 0) {
                    totalHeight = getLayoutParams().height;
                } else {
                    totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                }
                if (Build.VERSION.SDK_INT >= 21 && !inBubbleMode) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop();
                int availableWidth = MeasureSpec.getSize(widthMeasureSpec) - backgroundPaddingLeft * 2;

                if (AndroidUtilities.isTablet()) {
                    selectedMenuItem.setAdditionalYOffset(-dp(3));
                } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    selectedMenuItem.setAdditionalYOffset(0);
                } else {
                    selectedMenuItem.setAdditionalYOffset(-dp(3));
                }

                LayoutParams layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

                layoutParams = (LayoutParams) doneItem.getLayoutParams();
                layoutParams.height = ActionBar.getCurrentActionBarHeight();

                ignoreLayout = true;
                int newSize = (int) (availableWidth / Math.min(4.5f, buttonsAdapter.getItemCount()));
                if (attachItemSize != newSize) {
                    attachItemSize = newSize;
                    AndroidUtilities.runOnUIThread(() -> buttonsAdapter.notifyDataSetChanged());
                }
                ignoreLayout = false;
                onMeasureInternal(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            private void onMeasureInternal(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                widthSize -= backgroundPaddingLeft * 2;

                int keyboardSize = 0;
                if (!commentTextView.isWaitingForKeyboardOpen() && keyboardSize <= AndroidUtilities.dp(20) && !commentTextView.isPopupShowing() && !commentTextView.isAnimatePopupClosing()) {
                    ignoreLayout = true;
                    commentTextView.hideEmojiView();
                    ignoreLayout = false;
                }
                if (!topCommentTextView.isWaitingForKeyboardOpen() && keyboardSize <= AndroidUtilities.dp(20) && !topCommentTextView.isPopupShowing() && !topCommentTextView.isAnimatePopupClosing()) {
                    ignoreLayout = true;
                    topCommentTextView.hideEmojiView();
                    ignoreLayout = false;
                }

                if (pollLayout != null && keyboardSize <= dp(20) && !pollLayout.isWaitingForKeyboardOpen() && !pollLayout.isPopupShowing() && !pollLayout.isAnimatePopupClosing() && !pollLayout.isEmojiSearchOpened) {
                    ignoreLayout = true;
                    pollLayout.hideEmojiView();
                    ignoreLayout = false;
                }

                if (keyboardSize <= dp(20)) {
                    int paddingBottom;
                    if (keyboardVisible) {
                        paddingBottom = 0;
                        if (currentAttachLayout == pollLayout && pollLayout.emojiView != null && pollLayout.isEmojiSearchOpened) {
                            paddingBottom += dp(120);
                        }
                    } else {
                        if (currentAttachLayout == pollLayout && pollLayout.emojiView != null) {
                            paddingBottom = pollLayout.getEmojiPadding();
                        } else if (captionAbove) {
                            paddingBottom = topCommentTextView.getEmojiPadding();
                        } else {
                            paddingBottom = commentTextView.getEmojiPadding();
                        }
                    }
                    if (!AndroidUtilities.isInMultiwindow) {
                        heightSize -= paddingBottom;
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                    }
                    ignoreLayout = true;
                    currentAttachLayout.onPreMeasure(widthSize, heightSize);
                    if (nextAttachLayout != null) {
                        nextAttachLayout.onPreMeasure(widthSize, heightSize);
                    }
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE) {
                        continue;
                    }
                    if (commentTextView != null && commentTextView.isPopupView(child) || topCommentTextView != null && topCommentTextView.isPopupView(child) || pollLayout != null && child == pollLayout.emojiView) {
                        if (inBubbleMode) {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize + getPaddingTop(), MeasureSpec.EXACTLY));
                        } else if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                if (lastNotifyWidth != r - l) {
                    lastNotifyWidth = r - l;
                    if (messageSendPreview != null && messageSendPreview.isShowing()) {
                        messageSendPreview.dismiss();
                    }
                }
                final int count = getChildCount();

                if (Build.VERSION.SDK_INT >= 29) {
                    exclustionRect.set(l, t, r, b);
                    setSystemGestureExclusionRects(exclusionRects);
                }

                int keyboardSize = measureKeyboardHeight();
                int paddingBottom = getPaddingBottom();
                if (!keyboardVisible) {
                    if (pollLayout != null && currentAttachLayout == pollLayout && pollLayout.emojiView != null) {
                        if (keyboardSize <= dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
                            paddingBottom += pollLayout.getEmojiPadding();
                        }
                    } else {
                        paddingBottom += keyboardSize <= dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? getCommentView().getEmojiPadding() : 0;
                    }
                }
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = (r - l) - width - lp.rightMargin - getPaddingRight() - backgroundPaddingLeft;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin + getPaddingLeft();
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (commentTextView != null && commentTextView.isPopupView(child) || topCommentTextView != null && topCommentTextView.isPopupView(child) || pollLayout != null && child == pollLayout.emojiView) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    } else if (child == mentionContainer) {
                        if (captionAbove) {
                            childTop = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
                updateLayout(currentAttachLayout, false, 0);
                updateLayout(nextAttachLayout, false, 0);
                if (captionAbove) {
                    updateCommentTextViewPosition();
                }
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            private float getY(View child) {
                if (child instanceof AttachAlertLayout) {
                    AttachAlertLayout layout = (AttachAlertLayout) child;
                    int actionBarType = layout.needsActionBar();

                    int offset = dp(13) + (int) ((headerView != null ? headerView.getAlpha() : 0f) * dp(26)) + (int) (topCommentContainer != null ? topCommentContainer.getAlpha() * topCommentContainer.getMeasuredHeight() : 0);
                    int top = getScrollOffsetY(0) - backgroundPaddingTop - offset;
                    if (currentSheetAnimationType == 1 || viewChangeAnimator != null) {
                        top += child.getTranslationY();
                    }
                    int y = top + dp(20);
                    int h = (actionBarType != 0 ? ActionBar.getCurrentActionBarHeight() : backgroundPaddingTop);
                    if (actionBarType != 2 && top + backgroundPaddingTop < h) {
                        float toMove = offset;
                        if (layout == locationLayout) {
                            toMove += dp(11);
                        } else if (layout == pollLayout) {
                            toMove -= dp(3);
                        } else {
                            toMove += dp(4);
                        }
                        float availableToMove = h - toMove + AndroidUtilities.statusBarHeight;
                        y -= (int) (availableToMove * actionBar.getAlpha());
                    }
                    if (Build.VERSION.SDK_INT >= 21 && !inBubbleMode) {
                        y += AndroidUtilities.statusBarHeight;
                    }
                    return y;
                }
                return 0;
            }

            private void drawChildBackground(Canvas canvas, View child) {
                if (child instanceof AttachAlertLayout) {
                    canvas.save();
                    canvas.translate(0, currentPanTranslationY);
                    int viewAlpha = (int) (255 * child.getAlpha());
                    AttachAlertLayout layout = (AttachAlertLayout) child;
                    int actionBarType = layout.needsActionBar();

                    int offset = dp(13) + (int) ((headerView != null ? headerView.getAlpha() : 0f) * dp(26)) + (int) (topCommentContainer != null ? topCommentContainer.getAlpha() * topCommentContainer.getMeasuredHeight() : 0);
                    int top = getScrollOffsetY(0) - backgroundPaddingTop - offset;
                    if (currentSheetAnimationType == 1 || viewChangeAnimator != null) {
                        top += child.getTranslationY();
                    }
                    int y = top + dp(20);

                    int height = getMeasuredHeight() + dp(45) + backgroundPaddingTop;
                    float rad = 1.0f;

                    int h = (actionBarType != 0 ? ActionBar.getCurrentActionBarHeight() : backgroundPaddingTop);
                    if (actionBarType == 2) {
                        if (top < h) {
                            rad = Math.max(0, 1.0f - (h - top) / (float) backgroundPaddingTop);
                        }
                    } else {
                        float toMove = offset;
                        if (layout == locationLayout) {
                            toMove += dp(11);
                        } else if (layout == pollLayout) {
                            toMove -= dp(3);
                        } else {
                            toMove += dp(4);
                        }
                        float moveProgress = actionBar.getAlpha();
                        float availableToMove = h - toMove + AndroidUtilities.statusBarHeight;

                        int diff = (int) (availableToMove * moveProgress);
                        top -= diff;
                        y -= diff;
                        height += diff;
                        rad = 1.0f - moveProgress;
                    }

                    if (Build.VERSION.SDK_INT >= 21 && !inBubbleMode) {
                        top += AndroidUtilities.statusBarHeight;
                        y += AndroidUtilities.statusBarHeight;
                        height -= AndroidUtilities.statusBarHeight;
                    }

                    int backgroundColor = currentAttachLayout.hasCustomBackground() ? currentAttachLayout.getCustomBackground() : getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground);
                    shadowDrawable.setAlpha(viewAlpha);
                    shadowDrawable.setBounds(0, top, getMeasuredWidth(), getMeasuredHeight() + dp(45) + backgroundPaddingTop);
                    shadowDrawable.draw(canvas);
                    if (actionBarType == 2) {
                        Theme.dialogs_onlineCirclePaint.setColor(backgroundColor);
                        Theme.dialogs_onlineCirclePaint.setAlpha(viewAlpha);
                        rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + dp(24));
                        canvas.save();
                        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() / 2);
                        canvas.drawRoundRect(rect, dp(12) * rad, dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                        canvas.restore();
                    }

                    if ((rad != 1.0f && actionBarType != 2) || currentAttachLayout.hasCustomActionBarBackground()) {
                        Theme.dialogs_onlineCirclePaint.setColor(currentAttachLayout.hasCustomActionBarBackground() ? currentAttachLayout.getCustomActionBarBackground() : backgroundColor);
                        Theme.dialogs_onlineCirclePaint.setAlpha(viewAlpha);
                        rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + dp(24));
                        canvas.save();
                        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() / 2);
                        canvas.drawRoundRect(rect, dp(12) * rad, dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                        canvas.restore();
                    }

                    if (currentAttachLayout.hasCustomActionBarBackground()) {
                        Theme.dialogs_onlineCirclePaint.setColor(currentAttachLayout.getCustomActionBarBackground());
                        Theme.dialogs_onlineCirclePaint.setAlpha(viewAlpha);
                        int top2 = getScrollOffsetY(0);
                        if (Build.VERSION.SDK_INT >= 21 && !inBubbleMode) {
                            top2 += AndroidUtilities.statusBarHeight;
                        }
                        rect.set(backgroundPaddingLeft, (backgroundPaddingTop + top + dp(12)) * (rad), getMeasuredWidth() - backgroundPaddingLeft, top2 + dp(12));
                        canvas.save();
                        canvas.drawRect(rect, Theme.dialogs_onlineCirclePaint);
                        canvas.restore();
                    }

                    if ((headerView == null || headerView.getAlpha() != 1.0f) && rad != 0) {
                        int w = dp(36);
                        rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + dp(4));
                        int color;
                        float alphaProgress;
                        if (actionBarType == 2) {
                            color = 0x20000000;
                            alphaProgress = rad;
                        } else if (currentAttachLayout.hasCustomActionBarBackground()) {
                            int actionBarColor = currentAttachLayout.getCustomActionBarBackground();
                            int blendColor = ColorUtils.calculateLuminance(actionBarColor) < 0.5f ? Color.WHITE : Color.BLACK;
                            color = ColorUtils.blendARGB(actionBarColor, blendColor, 0.5f);
                            alphaProgress = headerView == null ? 1.0f : 1.0f - headerView.getAlpha();
                        } else {
                            color = getThemedColor(Theme.key_sheet_scrollUp);
                            alphaProgress = headerView == null ? 1.0f : 1.0f - headerView.getAlpha();
                        }
                        int alpha = Color.alpha(color);
                        Theme.dialogs_onlineCirclePaint.setColor(color);
                        Theme.dialogs_onlineCirclePaint.setAlpha((int) (alpha * alphaProgress * rad * child.getAlpha()));
                        canvas.drawRoundRect(rect, dp(2), dp(2), Theme.dialogs_onlineCirclePaint);
                    }
                    canvas.restore();
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child instanceof AttachAlertLayout && child.getAlpha() > 0.0f) {
                    canvas.save();
                    canvas.translate(0, currentPanTranslationY);
                    int viewAlpha = (int) (255 * child.getAlpha());
                    AttachAlertLayout layout = (AttachAlertLayout) child;
                    int actionBarType = layout.needsActionBar();

                    int offset = dp(13) + (headerView != null ? dp(headerView.getAlpha() * 26) : 0) + (int) (topCommentContainer != null ? topCommentContainer.getAlpha() * topCommentContainer.getMeasuredHeight() : 0);
                    int top = getScrollOffsetY(layout == currentAttachLayout ? 0 : 1) - backgroundPaddingTop - offset;
                    if (currentSheetAnimationType == 1 || viewChangeAnimator != null) {
                        top += child.getTranslationY();
                    }
                    int y = top + dp(20);

                    int height = getMeasuredHeight() + dp(45) + backgroundPaddingTop;
                    float rad = 1.0f;

                    int h = (actionBarType != 0 ? ActionBar.getCurrentActionBarHeight() : backgroundPaddingTop);
                    if (actionBarType == 2) {
                        if (top < h) {
                            rad = Math.max(0, 1.0f - (h - top) / (float) backgroundPaddingTop);
                        }
                    } else if (top + backgroundPaddingTop < h) {
                        float toMove = offset;
                        if (layout == locationLayout) {
                            toMove += dp(11);
                        } else if (layout == pollLayout) {
                            toMove -= dp(3);
                        } else {
                            toMove += dp(4);
                        }
                        float moveProgress = Math.min(1.0f, (h - top - backgroundPaddingTop) / toMove);
                        float availableToMove = h - toMove;

                        int diff = (int) (availableToMove * moveProgress);
                        top -= diff;
                        y -= diff;
                        height += diff;
                        rad = 1.0f - moveProgress;
                    }

                    if (Build.VERSION.SDK_INT >= 21 && !inBubbleMode) {
                        top += AndroidUtilities.statusBarHeight;
                        y += AndroidUtilities.statusBarHeight;
                        height -= AndroidUtilities.statusBarHeight;
                    }

                    int backgroundColor = currentAttachLayout.hasCustomBackground() ? currentAttachLayout.getCustomBackground() : getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground);
                    boolean drawBackground = !(currentAttachLayout == photoPreviewLayout || nextAttachLayout == photoPreviewLayout || (currentAttachLayout == photoLayout && nextAttachLayout == null));
                    if (drawBackground) {
                        shadowDrawable.setAlpha(viewAlpha);
                        shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                        shadowDrawable.draw(canvas);
                        if (actionBarType == 2) {
                            Theme.dialogs_onlineCirclePaint.setColor(backgroundColor);
                            Theme.dialogs_onlineCirclePaint.setAlpha(viewAlpha);
                            rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + dp(24));
                            canvas.save();
                            canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() / 2);
                            canvas.drawRoundRect(rect, dp(12) * rad, dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                            canvas.restore();
                        }
                    }

                    boolean result;
                    if (child != contactsLayout && child != quickRepliesLayout && child != audioLayout) {
                        canvas.save();
                        canvas.clipRect(backgroundPaddingLeft, actionBar.getY() + actionBar.getMeasuredHeight() - currentPanTranslationY, getMeasuredWidth() - backgroundPaddingLeft, getMeasuredHeight());
                        result = super.drawChild(canvas, child, drawingTime);
                        canvas.restore();
                    } else {
                        result = super.drawChild(canvas, child, drawingTime);
                    }

                    if (drawBackground) {
                        if (rad != 1.0f && actionBarType != 2) {
                            Theme.dialogs_onlineCirclePaint.setColor(backgroundColor);
                            Theme.dialogs_onlineCirclePaint.setAlpha(viewAlpha);
                            rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + dp(24));
                            canvas.save();
                            canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() / 2);
                            canvas.drawRoundRect(rect, dp(12) * rad, dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                            canvas.restore();
                        }

                        if ((headerView == null || headerView.getAlpha() != 1.0f) && rad != 0) {
                            int w = dp(36);
                            rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + dp(4));
                            int color;
                            float alphaProgress;
                            if (actionBarType == 2) {
                                color = 0x20000000;
                                alphaProgress = rad;
                            } else {
                                color = getThemedColor(Theme.key_sheet_scrollUp);
                                alphaProgress = headerView == null ? 1.0f : 1.0f - headerView.getAlpha();
                            }
                            int alpha = Color.alpha(color);
                            Theme.dialogs_onlineCirclePaint.setColor(color);
                            Theme.dialogs_onlineCirclePaint.setAlpha((int) (alpha * alphaProgress * rad * child.getAlpha()));
                            canvas.drawRoundRect(rect, dp(2), dp(2), Theme.dialogs_onlineCirclePaint);
                        }
                    }
                    canvas.restore();
                    return result;
                } else if (child == actionBar) {
                    final float alpha = actionBar.getAlpha();
                    if (alpha <= 0) {
                        return false;
                    }
                    if (alpha >= 1) {
                        return super.drawChild(canvas, child, drawingTime);
                    }
                    canvas.save();
                    canvas.clipRect(actionBar.getX(), getY(currentAttachLayout), actionBar.getX() + actionBar.getWidth(), actionBar.getY() + actionBar.getHeight());
                    boolean result = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return result;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (inBubbleMode) {
                    return;
                }
            }

            private int getCurrentTop() {
                int y = scrollOffsetY[0] - backgroundPaddingTop * 2 - (dp(13) + (headerView != null ? dp(headerView.getAlpha() * 26) : 0)) - (int) (topCommentContainer != null ? topCommentContainer.getAlpha() * topCommentContainer.getMeasuredHeight() : 0) + dp(20);
                if (Build.VERSION.SDK_INT >= 21 && !inBubbleMode) {
                    y += AndroidUtilities.statusBarHeight;
                }
                return y;
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
//                canvas.clipRect(0, getPaddingTop() + currentPanTranslationY, getMeasuredWidth(), getMeasuredHeight() + currentPanTranslationY - getPaddingBottom());
                if (currentAttachLayout == photoPreviewLayout || nextAttachLayout == photoPreviewLayout || (currentAttachLayout == photoLayout && nextAttachLayout == null)) {
                    drawChildBackground(canvas, currentAttachLayout);
                }
                super.dispatchDraw(canvas);
                canvas.restore();
            }

            @Override
            public void setTranslationY(float translationY) {
                translationY += currentPanTranslationY;
                if (currentSheetAnimationType == 0) {
                    initialTranslationY = translationY;
                }
                if (currentSheetAnimationType == 1) {
                    if (translationY < 0) {
                        currentAttachLayout.setTranslationY(translationY);
                        if (avatarPicker != 0 || storyMediaPicker) {
                            headerView.setTranslationY(baseSelectedTextViewTranslationY + translationY - currentPanTranslationY);
                        }
                        translationY = 0;
                        buttonsRecyclerView.setTranslationY(0);
                    } else {
                        currentAttachLayout.setTranslationY(0);
                        buttonsRecyclerView.setTranslationY(-translationY + buttonsRecyclerView.getMeasuredHeight() * (translationY / initialTranslationY));
                    }
                    containerView.invalidate();
                }
                super.setTranslationY(translationY - currentPanTranslationY);
                if (currentSheetAnimationType != 1) {
                    currentAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                adjustPanLayoutHelper.setResizableView(this);
                adjustPanLayoutHelper.onAttach();
                commentTextView.setAdjustPanLayoutHelper(adjustPanLayoutHelper);
                topCommentTextView.setAdjustPanLayoutHelper(adjustPanLayoutHelper);
                //   Bulletin.addDelegate(this, bulletinDelegate);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                adjustPanLayoutHelper.onDetach();
                //  Bulletin.removeDelegate(this);
            }
        };
        sizeNotifierFrameLayout.setDelegate(new SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate() {
            @Override
            public void onSizeChanged(int keyboardHeight, boolean isWidthGreater) {
                if (currentAttachLayout == photoPreviewLayout) {
                    currentAttachLayout.invalidate();
                }
            }
        });
        containerView = sizeNotifierFrameLayout;
        containerView.setWillNotDraw(false);
        containerView.setClipChildren(false);
        containerView.setClipToPadding(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        actionBar = new ActionBar(context, resourcesProvider) {
            @Override
            public void setAlpha(float alpha) {
                float oldAlpha = getAlpha();
                super.setAlpha(alpha);
                if (oldAlpha != alpha) {
                    containerView.invalidate();
                    if (frameLayout2 != null && buttonsRecyclerView != null) {
                        if (frameLayout2.getTag() == null) {
                            if (currentAttachLayout == null || currentAttachLayout.shouldHideBottomButtons()) {
                                buttonsRecyclerView.setAlpha(1.0f - alpha);
                                shadow.setAlpha(1.0f - alpha);
                                buttonsRecyclerView.setTranslationY(dp(44) * alpha);
                            }
                            frameLayout2.setTranslationY(dp(48) * alpha);
                            shadow.setTranslationY(dp(84) * alpha + botMainButtonOffsetY);
                        } else if (currentAttachLayout == null) {
                            float value = alpha == 0.0f ? 1.0f : 0.0f;
                            if (buttonsRecyclerView.getAlpha() != value) {
                                buttonsRecyclerView.setAlpha(value);
                            }
                        }
                    }
                }
            }
        };
        actionBar.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(getThemedColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_dialogButtonSelector), false);
        actionBar.setTitleColor(getThemedColor(Theme.key_dialogTextBlack));
        actionBar.setOccupyStatusBar(false);
        actionBar.setAlpha(0.0f);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (currentAttachLayout.onBackPressed()) {
                        return;
                    }
                    dismiss();
                } else {
                    currentAttachLayout.onMenuItemClick(id);
                }
            }
        });

        selectedMenuItem = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_dialogTextBlack), false, resourcesProvider);
        selectedMenuItem.setLongClickEnabled(false);
        selectedMenuItem.setIcon(R.drawable.ic_ab_other);
        selectedMenuItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
        selectedMenuItem.setVisibility(View.INVISIBLE);
        selectedMenuItem.setAlpha(0.0f);
        selectedMenuItem.setScaleX(0.6f);
        selectedMenuItem.setScaleY(0.6f);
        selectedMenuItem.setSubMenuOpenSide(2);
        selectedMenuItem.setDelegate(id -> actionBar.getActionBarMenuOnItemClick().onItemClick(id));
        selectedMenuItem.setAdditionalYOffset(dp(72));
        selectedMenuItem.setTranslationX(dp(6));
        selectedMenuItem.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 6));
        selectedMenuItem.setOnClickListener(v -> selectedMenuItem.toggleSubMenu());

        doneItem = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader), true, resourcesProvider);
        doneItem.setLongClickEnabled(false);
        doneItem.setText(getString(R.string.Create).toUpperCase());
        doneItem.setVisibility(View.INVISIBLE);
        doneItem.setAlpha(0.0f);
        doneItem.setTranslationX(-dp(12));
        doneItem.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 3));
        doneItem.setOnClickListener(v -> currentAttachLayout.onMenuItemClick(40));

        if (baseFragment != null) {
            searchItem = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_dialogTextBlack), false, resourcesProvider);
            searchItem.setLongClickEnabled(false);
            searchItem.setIcon(R.drawable.ic_ab_search);
            searchItem.setContentDescription(getString(R.string.Search));
            searchItem.setVisibility(View.INVISIBLE);
            searchItem.setAlpha(0.0f);
            searchItem.setTranslationX(-dp(42));
            searchItem.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 6));
            searchItem.setOnClickListener(v -> {
                if (avatarPicker != 0) {
                    delegate.openAvatarsSearch();
                    dismiss();
                    return;
                }
                final HashMap<Object, Object> photos = new HashMap<>();
                final ArrayList<Object> order = new ArrayList<>();
                PhotoPickerSearchActivity fragment = new PhotoPickerSearchActivity(photos, order, 0, true, (ChatActivity) baseFragment);
                fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {

                    private boolean sendPressed;

                    @Override
                    public void selectedPhotosChanged() {

                    }

                    @Override
                    public void actionButtonPressed(boolean canceled, boolean notify, int scheduleDate) {
                        if (canceled) {
                            return;
                        }
                        if (photos.isEmpty() || sendPressed) {
                            return;
                        }
                        sendPressed = true;

                        ArrayList<SendMessagesHelper.SendingMediaInfo> media = new ArrayList<>();
                        for (int a = 0; a < order.size(); a++) {
                            Object object = photos.get(order.get(a));
                            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                            media.add(info);
                            MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
                            if (searchImage.imagePath != null) {
                                info.path = searchImage.imagePath;
                            } else {
                                info.searchImage = searchImage;
                            }
                            info.thumbPath = searchImage.thumbPath;
                            info.videoEditedInfo = searchImage.editedInfo;
                            info.caption = searchImage.caption != null ? searchImage.caption.toString() : null;
                            info.entities = searchImage.entities;
                            info.masks = searchImage.stickers;
                            info.ttl = searchImage.ttl;
                            if (searchImage.inlineResult != null && searchImage.type == 1) {
                                info.inlineResult = searchImage.inlineResult;
                                info.params = searchImage.params;
                            }

                            searchImage.date = (int) (System.currentTimeMillis() / 1000);
                        }
                        ((ChatActivity) baseFragment).didSelectSearchPhotos(media, notify, scheduleDate);
                    }

                    @Override
                    public void onCaptionChanged(CharSequence text) {

                    }
                });
                fragment.setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
                if (showingFromDialog) {
                    baseFragment.showAsSheet(fragment);
                } else {
                    baseFragment.presentFragment(fragment);
                }
                dismiss();
            });
        }

        optionsItem = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_dialogTextBlack), false, resourcesProvider);
        optionsItem.setLongClickEnabled(false);
        optionsItem.setIcon(R.drawable.ic_ab_other);
        optionsItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
        optionsItem.setVisibility(View.GONE);
        optionsItem.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), Theme.RIPPLE_MASK_CIRCLE_TO_BOUND_EDGE));
        optionsItem.addSubItem(1, R.drawable.msg_addbot, getString(R.string.StickerCreateEmpty)).setOnClickListener(v -> {
            optionsItem.toggleSubMenu();
            PhotoViewer.getInstance().setParentActivity(baseFragment, resourcesProvider);
            PhotoViewer.getInstance().setParentAlert(this);
            PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
            if (!delegate.needEnterComment()) {
                AndroidUtilities.hideKeyboard(baseFragment.getFragmentView().findFocus());
                AndroidUtilities.hideKeyboard(getContainer().findFocus());
            }
            File file = StoryEntry.makeCacheFile(currentAccount, "webp");
            int w = AndroidUtilities.displaySize.x, h = AndroidUtilities.displaySize.y;
            if (w > 1080 || h > 1080) {
                float scale = Math.min(w, h) / 1080f;
                w = (int) (w * scale);
                h = (int) (h * scale);
            }
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            try {
                b.compress(Bitmap.CompressFormat.WEBP, 100, new FileOutputStream(file));
            } catch (Throwable e) {
                FileLog.e(e);
            }
            b.recycle();

            ArrayList<Object> arrayList = new ArrayList<>();
            final MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, 0, 0, file.getAbsolutePath(), 0, false, 0, 0, 0);
            arrayList.add(entry);
            PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, PhotoViewer.SELECT_TYPE_STICKER, false, new PhotoViewer.EmptyPhotoViewerProvider() {
                @Override
                public boolean allowCaption() {
                    return false;
                }
                @Override
                public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
                    sent = true;
                    if (delegate == null) {
                        return;
                    }
                    entry.editedInfo = videoEditedInfo;
                    ChatAttachAlertPhotoLayout.selectedPhotosOrder.clear();
                    ChatAttachAlertPhotoLayout.selectedPhotos.clear();
                    ChatAttachAlertPhotoLayout.selectedPhotosOrder.add(0);
                    ChatAttachAlertPhotoLayout.selectedPhotos.put(0, entry);
                    delegate.didPressedButton(7, true, notify, scheduleDate, 0, isCaptionAbove(), forceDocument);
                }
            }, baseFragment instanceof ChatActivity ? (ChatActivity) baseFragment : null);
            if (isStickerMode) {
                PhotoViewer.getInstance().enableStickerMode(null, true, customStickerHandler);
            }
        });
        optionsItem.setMenuYOffset(dp(-12));
        optionsItem.setAdditionalXOffset(dp(12));
        optionsItem.setOnClickListener(v -> {
            optionsItem.toggleSubMenu();
        });

        headerView = new FrameLayout(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                updateSelectedPosition(0);
                containerView.invalidate();
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (headerView.getVisibility() != View.VISIBLE) {
                    return false;
                }
                return super.onTouchEvent(event);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                if (headerView.getVisibility() != View.VISIBLE) {
                    return false;
                }
                return super.onInterceptTouchEvent(event);
            }
        };
        headerView.setOnClickListener(e -> {
            this.updatePhotoPreview(currentAttachLayout != photoPreviewLayout);
        });
        headerView.setAlpha(0.0f);
        headerView.setVisibility(View.INVISIBLE);

        selectedView = new LinearLayout(context);
        selectedView.setOrientation(LinearLayout.HORIZONTAL);
        selectedView.setGravity(Gravity.CENTER_VERTICAL);

        selectedTextView = new TextView(context);
        selectedTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        selectedTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        selectedTextView.setTypeface(AndroidUtilities.bold());
        selectedTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        selectedTextView.setMaxLines(1);
        selectedTextView.setEllipsize(TextUtils.TruncateAt.END);
        selectedView.addView(selectedTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        selectedArrowImageView = new ImageView(context);
        Drawable arrowRight = getContext().getResources().getDrawable(R.drawable.attach_arrow_right).mutate();
        arrowRight.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
        selectedArrowImageView.setImageDrawable(arrowRight);
        selectedArrowImageView.setVisibility(View.GONE);
        selectedView.addView(selectedArrowImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 1, 0, 0));
        selectedView.setAlpha(1);
        headerView.addView(selectedView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        mediaPreviewView = new LinearLayout(context);
        mediaPreviewView.setOrientation(LinearLayout.HORIZONTAL);
        mediaPreviewView.setGravity(Gravity.CENTER_VERTICAL);

        ImageView arrowView = new ImageView(context);
        Drawable arrowLeft = getContext().getResources().getDrawable(R.drawable.attach_arrow_left).mutate();
        arrowLeft.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));
        arrowView.setImageDrawable(arrowLeft);

        mediaPreviewView.addView(arrowView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 1, 4, 0));

        mediaPreviewTextView = new TextView(context);
        mediaPreviewTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        mediaPreviewTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        mediaPreviewTextView.setTypeface(AndroidUtilities.bold());
        mediaPreviewTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        mediaPreviewTextView.setText(getString("AttachMediaPreview", R.string.AttachMediaPreview));
        mediaPreviewView.setAlpha(0);

        mediaPreviewView.addView(mediaPreviewTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        headerView.addView(mediaPreviewView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        layouts[0] = photoLayout = new ChatAttachAlertPhotoLayout(this, context, forceDarkTheme, needCamera, resourcesProvider);
        photoLayout.setTranslationX(0);
        currentAttachLayout = photoLayout;
        selectedId = 1;

        containerView.addView(photoLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        containerView.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 0, 12, 0));
        topCommentContainer = new FrameLayout(context) {
            private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint backgroundPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();
            private final GradientClip clip = new GradientClip();
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                final float r = dp(20);
                AndroidUtilities.rectTmp.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
                path.rewind();
                path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
                canvas.save();
                canvas.clipRect(0, 0, getWidth(), getHeight() * getAlpha());
                backgroundPaint.setColor(getThemedColor(Theme.key_dialogBackground));
                canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
                canvas.clipPath(path);
                backgroundPaint2.setColor(getThemedColor(Theme.key_graySection));
                canvas.drawPaint(backgroundPaint2);
                canvas.saveLayerAlpha(AndroidUtilities.rectTmp, 0xFF, Canvas.ALL_SAVE_FLAG);
                super.dispatchDraw(canvas);
                AndroidUtilities.rectTmp.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getPaddingTop() + dp(6));
                clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.TOP, 1.0f);
                AndroidUtilities.rectTmp.set(getPaddingLeft(), getHeight() - getPaddingBottom() - dp(6), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
                clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.BOTTOM, 1.0f);
                canvas.restore();
                canvas.restore();
            }
        };
        containerView.addView(topCommentContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        containerView.addView(selectedMenuItem, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.RIGHT));
        if (searchItem != null) {
            containerView.addView(searchItem, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.RIGHT));
        }
        if (optionsItem != null) {
            headerView.addView(optionsItem, LayoutHelper.createFrame(32, 32, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 0, 8));
        }
        containerView.addView(doneItem, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.TOP | Gravity.RIGHT));

        actionBarShadow = new View(context);
        actionBarShadow.setAlpha(0.0f);
        actionBarShadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        containerView.addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));

        shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.attach_shadow);
        shadow.getBackground().setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
        containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 84));

        buttonsRecyclerView = new RecyclerListView(context) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                currentAttachLayout.onButtonsTranslationYUpdated();
            }
        };
        buttonsRecyclerView.setAdapter(buttonsAdapter = new ButtonsAdapter(context));
        buttonsRecyclerView.setLayoutManager(buttonsLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        buttonsRecyclerView.setVerticalScrollBarEnabled(false);
        buttonsRecyclerView.setHorizontalScrollBarEnabled(false);
        buttonsRecyclerView.setItemAnimator(null);
        buttonsRecyclerView.setLayoutAnimation(null);
        buttonsRecyclerView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        buttonsRecyclerView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        buttonsRecyclerView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        containerView.addView(buttonsRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 84, Gravity.BOTTOM | Gravity.LEFT));
        buttonsRecyclerView.setOnItemClickListener((view, position) -> {
            BaseFragment lastFragment = baseFragment;
            if (lastFragment == null) {
                lastFragment = LaunchActivity.getLastFragment();
            }
            if (lastFragment == null || lastFragment.getParentActivity() == null) {
                return;
            }
            if (view instanceof AttachButton) {
                final Activity activity = lastFragment.getParentActivity();
                int num = view.getTag() instanceof Integer ? (Integer) view.getTag() : -1;
                if (num == 1) {
                    if (!photosEnabled && !videosEnabled && checkCanRemoveRestrictionsByBoosts()) {
                        return;
                    }
                    if (!photosEnabled && !videosEnabled) {
                        showLayout(restrictedLayout = new ChatAttachRestrictedLayout(1, this, getContext(), resourcesProvider));
                    }
                    showLayout(photoLayout);
                } else if (num == 3) {
                    if (!musicEnabled && checkCanRemoveRestrictionsByBoosts()) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 33) {
                        if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            activity.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                            return;
                        }
                    } else if (Build.VERSION.SDK_INT >= 23 && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        AndroidUtilities.findActivity(getContext()).requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                        return;
                    }
                    openAudioLayout(true);
                } else if (num == 4) {
                    if (!documentsEnabled && checkCanRemoveRestrictionsByBoosts()) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 33) {
                        if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED ||
                                activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                            activity.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                            return;
                        }
                    } else if (Build.VERSION.SDK_INT >= 23 && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        AndroidUtilities.findActivity(getContext()).requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                        return;
                    }
                    openDocumentsLayout(true);
                } else if (num == 5) {
                    if (!plainTextEnabled && checkCanRemoveRestrictionsByBoosts()) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 23 && plainTextEnabled) {
                        if (getContext().checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                            AndroidUtilities.findActivity(getContext()).requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, BasePermissionsActivity.REQUEST_CODE_ATTACH_CONTACT);
                            return;
                        }
                    }
                    openContactsLayout();
                } else if (num == 6) {
                    if (!plainTextEnabled && checkCanRemoveRestrictionsByBoosts()) {
                        return;
                    }
                    if (!AndroidUtilities.isMapsInstalled(baseFragment)) {
                        return;
                    }
                    if (!plainTextEnabled) {
                        restrictedLayout = new ChatAttachRestrictedLayout(6, this, getContext(), resourcesProvider);
                        showLayout(restrictedLayout);
                    } else {
                        if (locationLayout == null) {
                            layouts[5] = locationLayout = new ChatAttachAlertLocationLayout(this, getContext(), resourcesProvider);
                            locationLayout.setDelegate((location, live, notify, scheduleDate) -> ((ChatActivity) baseFragment).didSelectLocation(location, live, notify, scheduleDate));
                        }
                        showLayout(locationLayout);
                    }
                } else if (num == 9) {
                    if (!pollsEnabled && checkCanRemoveRestrictionsByBoosts()) {
                        return;
                    }
                    if (!pollsEnabled) {
                        restrictedLayout = new ChatAttachRestrictedLayout(9, this, getContext(), resourcesProvider);
                        showLayout(restrictedLayout);
                    } else {
                        if (pollLayout == null) {
                            layouts[1] = pollLayout = new ChatAttachAlertPollLayout(this, getContext(), resourcesProvider);
                            pollLayout.setDelegate((poll, params, notify, scheduleDate) -> ((ChatActivity) baseFragment).sendPoll(poll, params, notify, scheduleDate));
                        }
                        showLayout(pollLayout);
                    }
                } else if (num == 11) {
                    openQuickRepliesLayout();
                } else if (view.getTag() instanceof Integer) {
                    delegate.didPressedButton((Integer) view.getTag(), true, true, 0, 0, isCaptionAbove(), false);
                }
                int left = view.getLeft();
                int right = view.getRight();
                int extra = dp(10);
                if (left - extra < 0) {
                    buttonsRecyclerView.smoothScrollBy(left - extra, 0);
                } else if (right + extra > buttonsRecyclerView.getMeasuredWidth()) {
                    buttonsRecyclerView.smoothScrollBy(right + extra - buttonsRecyclerView.getMeasuredWidth(), 0);
                }
            } else if (view instanceof AttachBotButton) {
                AttachBotButton button = (AttachBotButton) view;
                if (button.attachMenuBot != null) {
                    if (button.attachMenuBot.inactive) {
                        WebAppDisclaimerAlert.show(getContext(), (allowSendMessage) -> {
                            TLRPC.TL_messages_toggleBotInAttachMenu botRequest = new TLRPC.TL_messages_toggleBotInAttachMenu();
                            botRequest.bot = MessagesController.getInstance(currentAccount).getInputUser(button.attachMenuBot.bot_id);
                            botRequest.enabled = true;
                            botRequest.write_allowed = true;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(botRequest, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                                button.attachMenuBot.inactive = button.attachMenuBot.side_menu_disclaimer_needed = false;
                                showBotLayout(button.attachMenuBot.bot_id, true);
                                MediaDataController.getInstance(currentAccount).updateAttachMenuBotsInCache();
                            }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                        }, null, null);
                    } else {
                        showBotLayout(button.attachMenuBot.bot_id, true);
                    }
                } else {
                    delegate.didSelectBot(button.currentUser);
                    dismiss();
                }
            }

            if (view.getX() + view.getWidth() >= buttonsRecyclerView.getMeasuredWidth() - dp(32)) {
                buttonsRecyclerView.smoothScrollBy((int) (view.getWidth() * 1.5f), 0);
            }
        });
        buttonsRecyclerView.setOnItemLongClickListener((view, position) -> {
            if (view instanceof AttachBotButton) {
                AttachBotButton button = (AttachBotButton) view;
                if (destroyed || button.currentUser == null) {
                    return false;
                }
                onLongClickBotButton(button.attachMenuBot, button.currentUser);
                return true;
            }
            return false;
        });

        botMainButtonTextView = new TextView(context);
        botMainButtonTextView.setVisibility(View.GONE);
        botMainButtonTextView.setAlpha(0f);
        botMainButtonTextView.setSingleLine();
        botMainButtonTextView.setGravity(Gravity.CENTER);
        botMainButtonTextView.setTypeface(AndroidUtilities.bold());
        int padding = dp(16);
        botMainButtonTextView.setPadding(padding, 0, padding, 0);
        botMainButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        botMainButtonTextView.setOnClickListener(v -> {
            if (selectedId < 0) {
                ChatAttachAlertBotWebViewLayout webViewLayout = botAttachLayouts.get(-selectedId);
                if (webViewLayout != null) {
                    webViewLayout.getWebViewContainer().onMainButtonPressed();
                }
            }
        });
        containerView.addView(botMainButtonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        botProgressView = new RadialProgressView(context);
        botProgressView.setSize(dp(18));
        botProgressView.setAlpha(0f);
        botProgressView.setScaleX(0.1f);
        botProgressView.setScaleY(0.1f);
        botProgressView.setVisibility(View.GONE);
        containerView.addView(botProgressView, LayoutHelper.createFrame(28, 28, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 10, 10));

        moveCaptionButton = new FrameLayout(context);
        ScaleStateListAnimator.apply(moveCaptionButton, .20f, 1.5f);
        Drawable shadowDrawable = getContext().getResources().getDrawable(R.drawable.popup_fixed_alert3).mutate();
        Rect shadowDrawablePadding = new Rect();
        shadowDrawable.getPadding(shadowDrawablePadding);
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhite), PorterDuff.Mode.SRC_IN));
        moveCaptionButton.setBackground(shadowDrawable);
        moveCaptionButton.setAlpha(0.0f);
        moveCaptionButtonIcon = new ImageView(context);
        moveCaptionButtonIcon.setScaleType(ImageView.ScaleType.CENTER);
        moveCaptionButtonIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
        moveCaptionButtonIcon.setImageResource(R.drawable.menu_link_above);
        moveCaptionButton.addView(moveCaptionButtonIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        FrameLayout.LayoutParams moveCaptionButtonLayoutParams = LayoutHelper.createFrame(38, 32, Gravity.BOTTOM | Gravity.LEFT);
        moveCaptionButtonLayoutParams.width += shadowDrawablePadding.left + shadowDrawablePadding.right;
        moveCaptionButtonLayoutParams.height += shadowDrawablePadding.top + shadowDrawablePadding.bottom;
        moveCaptionButtonLayoutParams.leftMargin = dp(10) - shadowDrawablePadding.left;
        moveCaptionButtonLayoutParams.bottomMargin = dp(10) - shadowDrawablePadding.bottom;
        containerView.addView(moveCaptionButton, moveCaptionButtonLayoutParams);
        moveCaptionButton.setOnClickListener(v -> {
            if (moveCaptionButton.getAlpha() < 1.0f) return;
            if (!captionAbove) {
                toggleCaptionAbove();
            }
        });

        frameLayout2 = new FrameLayout(context) {

            private final Paint p = new Paint();
            private int color;

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                invalidate();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (captionContainer.getAlpha() <= 0) {
                    return;
                }
                if (chatActivityEnterViewAnimateFromTop != 0 && chatActivityEnterViewAnimateFromTop != frameLayout2.getTop() + chatActivityEnterViewAnimateFromTop) {
                    if (topBackgroundAnimator != null) {
                        topBackgroundAnimator.cancel();
                    }
                    captionEditTextTopOffset = chatActivityEnterViewAnimateFromTop - (frameLayout2.getTop() + captionEditTextTopOffset);
                    topBackgroundAnimator = ValueAnimator.ofFloat(captionEditTextTopOffset, 0);
                    topBackgroundAnimator.addUpdateListener(valueAnimator -> {
                        captionEditTextTopOffset = (float) valueAnimator.getAnimatedValue();
                        frameLayout2.invalidate();
                        invalidate();
                    });
                    topBackgroundAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    topBackgroundAnimator.setDuration(200);
                    topBackgroundAnimator.start();
                    chatActivityEnterViewAnimateFromTop = 0;
                }

                float alphaOffset = (frameLayout2.getMeasuredHeight() - dp(84)) * (1f - getAlpha());
                shadow.setTranslationY(-(frameLayout2.getMeasuredHeight() - dp(84)) + captionEditTextTopOffset + currentPanTranslationY + bottomPannelTranslation + alphaOffset + botMainButtonOffsetY + captionContainer.getTranslationY());

                int newColor = currentAttachLayout.hasCustomBackground() ? currentAttachLayout.getCustomBackground() : getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground);
                if (color != newColor) {
                    color = newColor;
                    p.setColor(color);
                }
                canvas.drawRect(0, captionEditTextTopOffset + captionContainer.getTranslationY(), getMeasuredWidth(), getMeasuredHeight(), p);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.clipRect(0, captionEditTextTopOffset, getMeasuredWidth(), getMeasuredHeight());
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };

        captionContainer = new FrameLayout(context);
        frameLayout2.addView(captionContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        frameLayout2.setWillNotDraw(false);
        frameLayout2.setVisibility(View.INVISIBLE);
        frameLayout2.setAlpha(0.0f);
        containerView.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        frameLayout2.setOnTouchListener((v, event) -> true);

        captionLimitView = new NumberTextView(context);
        captionLimitView.setVisibility(View.GONE);
        captionLimitView.setTextSize(15);
        captionLimitView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        captionLimitView.setTypeface(AndroidUtilities.bold());
        captionLimitView.setCenterAlign(true);
        captionContainer.addView(captionLimitView, LayoutHelper.createFrame(56, 20, Gravity.BOTTOM | Gravity.RIGHT, 3, 0, 14, 78));

        currentLimit = MessagesController.getInstance(UserConfig.selectedAccount).getCaptionMaxLengthLimit();

        commentTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, null, EditTextEmoji.STYLE_DIALOG, true, resourcesProvider) {

            private boolean shouldAnimateEditTextWithBounds;
            private int messageEditTextPredrawHeigth;
            private int messageEditTextPredrawScrollY;
            private ValueAnimator messageEditTextAnimator;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (!enterCommentEventSent) {
                    if (ev.getX() > commentTextView.getEditText().getLeft() && ev.getX() < commentTextView.getEditText().getRight()
                            && ev.getY() > commentTextView.getEditText().getTop() && ev.getY() < commentTextView.getEditText().getBottom()) {
                        makeFocusable(commentTextView.getEditText(), true);
                    } else {
                        makeFocusable(commentTextView.getEditText(), false);
                    }
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (shouldAnimateEditTextWithBounds) {
                    EditTextCaption editText = commentTextView.getEditText();
                    float dy = (messageEditTextPredrawHeigth - editText.getMeasuredHeight()) + (messageEditTextPredrawScrollY - editText.getScrollY());
                    editText.setOffsetY(editText.getOffsetY() - dy);
                    ValueAnimator a = ValueAnimator.ofFloat(editText.getOffsetY(), 0);
                    a.addUpdateListener(animation -> {
                        editText.setOffsetY((float) animation.getAnimatedValue());
                        updateCommentTextViewPosition();
                        if (currentAttachLayout == photoLayout) {
                            photoLayout.onContainerTranslationUpdated(currentPanTranslationY);
                        }
                    });
                    if (messageEditTextAnimator != null) {
                        messageEditTextAnimator.cancel();
                    }
                    messageEditTextAnimator = a;
                    a.setDuration(200);
                    a.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    a.start();
                    shouldAnimateEditTextWithBounds = false;
                }
                super.dispatchDraw(canvas);
            }

            @Override
            protected void onLineCountChanged(int oldLineCount, int newLineCount) {
                if (!TextUtils.isEmpty(getEditText().getText())) {
                    shouldAnimateEditTextWithBounds = true;
                    messageEditTextPredrawHeigth = getEditText().getMeasuredHeight();
                    messageEditTextPredrawScrollY = getEditText().getScrollY();
                    invalidate();
                } else {
                    getEditText().animate().cancel();
                    getEditText().setOffsetY(0);
                    shouldAnimateEditTextWithBounds = false;
                }
                chatActivityEnterViewAnimateFromTop = frameLayout2.getTop() + captionEditTextTopOffset;
                frameLayout2.invalidate();
                updateCommentTextViewPosition();
            }

            @Override
            protected void bottomPanelTranslationY(float translation) {
                bottomPannelTranslation = translation;
                frameLayout2.setTranslationY(translation);
                moveCaptionButton.setTranslationY(bottomPannelTranslation - commentTextView.getHeight() + captionContainer.getTranslationY());
                writeButtonContainer.setTranslationY(translation);
                frameLayout2.invalidate();
                updateLayout(currentAttachLayout, true, 0);
            }

            @Override
            protected void closeParent() {
                ChatAttachAlert.super.dismiss();
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateCommentTextViewPosition();
            }

            @Override
            protected void extendActionMode(ActionMode actionMode, Menu menu) {
                if (baseFragment instanceof ChatActivity) {
                    ChatActivity.fillActionModeMenu(menu, ((ChatActivity) baseFragment).getCurrentEncryptedChat(), true);
                }
                super.extendActionMode(actionMode, menu);
            }
        };
        commentTextView.setHint(getString("AddCaption", R.string.AddCaption));
        commentTextView.onResume();
        commentTextView.getEditText().addTextChangedListener(new TextWatcher() {

            private boolean processChange;
            private boolean wasEmpty;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if ((count - before) >= 1) {
                    processChange = true;
                }
                if (mentionContainer == null) {
                    createMentionsContainer();
                }
                if (mentionContainer.getAdapter() != null) {
                    mentionContainer.setReversed(false);
                    mentionContainer.getAdapter().searchUsernameOrHashtag(charSequence, commentTextView.getEditText().getSelectionStart(), null, false, false);
                    updateCommentTextViewPosition();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (wasEmpty != TextUtils.isEmpty(editable)) {
                    if (currentAttachLayout != null) {
                        currentAttachLayout.onSelectedItemsCountChanged(currentAttachLayout.getSelectedItemsCount());
                    }
                    wasEmpty = !wasEmpty;
                }
                if (processChange) {
                    ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);
                    for (int i = 0; i < spans.length; i++) {
                        editable.removeSpan(spans[i]);
                    }
                    Emoji.replaceEmoji(editable, commentTextView.getEditText().getPaint().getFontMetricsInt(), dp(20), false);
                    processChange = false;
                }
                int beforeLimit;
                codepointCount = Character.codePointCount(editable, 0, editable.length());
                boolean sendButtonEnabledLocal = true;
                if (currentLimit > 0 && (beforeLimit = currentLimit - codepointCount) <= 100) {
                    if (beforeLimit < -9999) {
                        beforeLimit = -9999;
                    }
                    captionLimitView.setNumber(beforeLimit, captionLimitView.getVisibility() == View.VISIBLE);
                    if (captionLimitView.getVisibility() != View.VISIBLE) {
                        captionLimitView.setVisibility(View.VISIBLE);
                        captionLimitView.setAlpha(0);
                        captionLimitView.setScaleX(0.5f);
                        captionLimitView.setScaleY(0.5f);
                    }
                    captionLimitView.animate().setListener(null).cancel();
                    captionLimitView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start();
                    if (beforeLimit < 0) {
                        sendButtonEnabledLocal = false;
                        captionLimitView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    } else {
                        captionLimitView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                    }
                    topCaptionLimitView.setNumber(beforeLimit, false);
                    topCaptionLimitView.setAlpha(1.0f);
                } else {
                    captionLimitView.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setDuration(100).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            captionLimitView.setVisibility(View.GONE);
                        }
                    });
                    topCaptionLimitView.setAlpha(0.0f);
                }

                if (sendButtonEnabled != sendButtonEnabledLocal) {
                    sendButtonEnabled = sendButtonEnabledLocal;
                    writeButton.invalidate();
                }

//                if (!captionLimitBulletinShown && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && !UserConfig.getInstance(currentAccount).isPremium() && codepointCount > MessagesController.getInstance(currentAccount).captionLengthLimitDefault && codepointCount < MessagesController.getInstance(currentAccount).captionLengthLimitPremium) {
//                    captionLimitBulletinShown = true;
//                    showCaptionLimitBulletin(parentFragment);
//                }
            }
        });
        captionContainer.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 84, 0));
        captionContainer.setClipChildren(false);
        frameLayout2.setClipChildren(false);
        commentTextView.setClipChildren(false);

        topCommentContainer.setPadding(dp(10), dp(2), dp(10), dp(10));
        topCommentContainer.setWillNotDraw(false);
        topCommentTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, null, EditTextEmoji.STYLE_DIALOG, true, resourcesProvider) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (!enterCommentEventSent) {
                    if (ev.getX() > topCommentTextView.getEditText().getLeft() && ev.getX() < topCommentTextView.getEditText().getRight()
                            && ev.getY() > topCommentTextView.getEditText().getTop() && ev.getY() < topCommentTextView.getEditText().getBottom()) {
                        makeFocusable(topCommentTextView.getEditText(), true);
                    } else {
                        makeFocusable(topCommentTextView.getEditText(), false);
                    }
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            protected void onLineCountChanged(int oldLineCount, int newLineCount) {
                super.onLineCountChanged(oldLineCount, newLineCount);
                updatedTopCaptionHeight();
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updatedTopCaptionHeight();
            }

            @Override
            protected void extendActionMode(ActionMode actionMode, Menu menu) {
                if (baseFragment instanceof ChatActivity) {
                    ChatActivity.fillActionModeMenu(menu, ((ChatActivity) baseFragment).getCurrentEncryptedChat(), true);
                }
                super.extendActionMode(actionMode, menu);
            }
        };
        topCommentTextView.getEditText().addTextChangedListener(new TextWatcher() {

            private boolean processChange;
            private boolean wasEmpty;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if ((count - before) >= 1) {
                    processChange = true;
                }
                if (mentionContainer == null) {
                    createMentionsContainer();
                }
                if (mentionContainer.getAdapter() != null) {
                    mentionContainer.setReversed(true);
                    mentionContainer.getAdapter().searchUsernameOrHashtag(charSequence, topCommentTextView.getEditText().getSelectionStart(), null, false, false);
                    updateCommentTextViewPosition();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (wasEmpty != TextUtils.isEmpty(editable)) {
                    if (currentAttachLayout != null) {
                        currentAttachLayout.onSelectedItemsCountChanged(currentAttachLayout.getSelectedItemsCount());
                    }
                    wasEmpty = !wasEmpty;
                }
                if (processChange) {
                    ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);
                    for (int i = 0; i < spans.length; i++) {
                        editable.removeSpan(spans[i]);
                    }
                    Emoji.replaceEmoji(editable, topCommentTextView.getEditText().getPaint().getFontMetricsInt(), dp(20), false);
                    processChange = false;
                }
                int beforeLimit;
                codepointCount = Character.codePointCount(editable, 0, editable.length());
                boolean sendButtonEnabledLocal = true;
                if (currentLimit > 0 && (beforeLimit = currentLimit - codepointCount) <= 100) {
                    if (beforeLimit < -9999) {
                        beforeLimit = -9999;
                    }
                    topCaptionLimitView.setNumber(beforeLimit, topCaptionLimitView.getVisibility() == View.VISIBLE);
                    if (topCaptionLimitView.getVisibility() != View.VISIBLE) {
                        topCaptionLimitView.setVisibility(View.VISIBLE);
                        topCaptionLimitView.setAlpha(0);
                        topCaptionLimitView.setScaleX(0.5f);
                        topCaptionLimitView.setScaleY(0.5f);
                    }
                    topCaptionLimitView.animate().setListener(null).cancel();
                    topCaptionLimitView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start();
                    if (beforeLimit < 0) {
                        sendButtonEnabledLocal = false;
                        topCaptionLimitView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    } else {
                        topCaptionLimitView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                    }
                    captionLimitView.setNumber(beforeLimit, false);
                    captionLimitView.setAlpha(1.0f);
                } else {
                    topCaptionLimitView.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setDuration(100).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            topCaptionLimitView.setVisibility(View.GONE);
                        }
                    });
                    captionLimitView.setAlpha(0.0f);
                }

                if (sendButtonEnabled != sendButtonEnabledLocal) {
                    sendButtonEnabled = sendButtonEnabledLocal;
                    writeButton.invalidate();
                }

                if (!captionLimitBulletinShown && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && !UserConfig.getInstance(currentAccount).isPremium() && codepointCount > MessagesController.getInstance(currentAccount).captionLengthLimitDefault && codepointCount < MessagesController.getInstance(currentAccount).captionLengthLimitPremium) {
                    captionLimitBulletinShown = true;
                    showCaptionLimitBulletin(parentFragment);
                }
            }
        });
        topCommentTextView.getEditText().setPadding(0, dp(9), 0, dp(9));
        topCommentTextView.getEditText().setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 48, 0, 36, 0));
        topCommentTextView.getEditText().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        topCommentTextView.getEmojiButton().setLayoutParams(LayoutHelper.createFrame(40, 40, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
        topCommentTextView.setHint(getString("AddCaption", R.string.AddCaption));
        topCommentContainer.addView(topCommentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL));
        topCommentContainer.setAlpha(0.0f);
        topCommentContainer.setVisibility(View.GONE);

        topCaptionLimitView = new NumberTextView(context);
        topCaptionLimitView.setVisibility(View.GONE);
        topCaptionLimitView.setTextSize(12);
        topCaptionLimitView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        topCaptionLimitView.setTypeface(AndroidUtilities.bold());
        topCaptionLimitView.setCenterAlign(true);
        topCommentTextView.addView(topCaptionLimitView, LayoutHelper.createFrame(46, 20, Gravity.BOTTOM | Gravity.RIGHT, 3, 0, 0, 40));

        topCommentMoveButton = new ImageView(context);
        topCommentMoveButton.setScaleType(ImageView.ScaleType.CENTER);
        topCommentMoveButton.setImageResource(R.drawable.menu_link_below);
        topCommentMoveButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.SRC_IN));
        topCommentTextView.addView(topCommentMoveButton, LayoutHelper.createFrame(40, 40, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 2, 0));
        topCommentMoveButton.setOnClickListener(v -> {
            if (captionAbove) {
                toggleCaptionAbove();
            }
        });

        writeButtonContainer = new FrameLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (currentAttachLayout == photoLayout) {
                    info.setText(LocaleController.formatPluralString("AccDescrSendPhotos", photoLayout.getSelectedItemsCount()));
                } else if (currentAttachLayout == documentLayout) {
                    info.setText(LocaleController.formatPluralString("AccDescrSendFiles", documentLayout.getSelectedItemsCount()));
                } else if (currentAttachLayout == audioLayout) {
                    info.setText(LocaleController.formatPluralString("AccDescrSendAudio", audioLayout.getSelectedItemsCount()));
                }
                info.setClassName(Button.class.getName());
                info.setLongClickable(true);
                info.setClickable(true);
            }
        };
        writeButtonContainer.setFocusable(true);
        writeButtonContainer.setFocusableInTouchMode(true);
        writeButtonContainer.setVisibility(View.INVISIBLE);
        writeButtonContainer.setScaleX(0.2f);
        writeButtonContainer.setScaleY(0.2f);
        writeButtonContainer.setAlpha(0.0f);
        containerView.addView(writeButtonContainer, LayoutHelper.createFrame(60, 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 6, 10));

        writeButton = new ChatActivityEnterView.SendButton(context, R.drawable.attach_send, resourcesProvider) {
            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean isInScheduleMode() {
                return super.isInScheduleMode();
            }

            @Override
            public boolean isInactive() {
                return !sendButtonEnabled;
            }

            @Override
            public boolean shouldDrawBackground() {
                return true;
            }

            @Override
            public int getFillColor() {
                return getThemedColor(Theme.key_dialogFloatingButton);
            }
        };
        writeButton.center = true;
        writeButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        writeButtonContainer.addView(writeButton, LayoutHelper.createFrame(64, 64, Gravity.LEFT | Gravity.TOP, -4, -4, 0, 0));
        writeButton.setOnClickListener(v -> {
            if (currentLimit - codepointCount < 0) {
                AndroidUtilities.shakeView(captionLimitView);
                AndroidUtilities.shakeView(topCaptionLimitView);
                try {
                    writeButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {}

                if (!MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && MessagesController.getInstance(currentAccount).captionLengthLimitPremium > codepointCount) {
                    showCaptionLimitBulletin(parentFragment);
                }
                return;
            }
            if (editingMessageObject == null && baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getContext(), ((ChatActivity) baseFragment).getDialogId(), (notify, scheduleDate) -> {
                    if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                        sendPressed(notify, scheduleDate, 0, isCaptionAbove());
                    } else {
                        currentAttachLayout.sendSelectedItems(notify, scheduleDate, 0, isCaptionAbove());
                        allowPassConfirmationAlert = true;
                        dismiss();
                    }
                }, resourcesProvider);
            } else {
                if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                    sendPressed(true, 0, 0, isCaptionAbove());
                } else {
                    currentAttachLayout.sendSelectedItems(true, 0, 0, isCaptionAbove());
                    allowPassConfirmationAlert = true;
                    dismiss();
                }
            }
        });
        writeButton.setOnLongClickListener(view -> {
            if ((dialogId == 0 && !(baseFragment instanceof ChatActivity)) || currentLimit - codepointCount < 0) {
                return false;
            }
            ChatActivity chatActivity = null;
            TLRPC.User user = null;
            long dialogId = this.dialogId;
            MessageObject replyMessage = null;
            MessageObject replyTopMessage = null;
            if (baseFragment instanceof ChatActivity) {
                chatActivity = (ChatActivity) baseFragment;
                TLRPC.Chat chat = chatActivity.getCurrentChat();
                user = chatActivity.getCurrentUser();
                replyMessage = chatActivity.getReplyMessage();
                replyTopMessage = chatActivity.getReplyTopMessage();
                if (chatActivity.isInScheduleMode() || chatActivity.getChatMode() == ChatActivity.MODE_QUICK_REPLIES) {
                    return false;
                }
                dialogId = chatActivity.getDialogId();
            } else {
                user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            }

            if (messageSendPreview != null) {
                messageSendPreview.dismiss();
            }
            messageSendPreview = new MessageSendPreview(context, resourcesProvider);
            messageSendPreview.setSendButton(writeButton, false, v -> {
                final long effectId = messageSendPreview != null ? messageSendPreview.getSelectedEffect() : 0;
                forceKeyboardOnDismiss();
                if (messageSendPreview != null) {
                    messageSendPreview.dismiss(true);
                    messageSendPreview = null;
                }
                if (currentLimit - codepointCount < 0) {
                    AndroidUtilities.shakeView(captionLimitView);
                    AndroidUtilities.shakeView(topCaptionLimitView);
                    try {
                        writeButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {}
                    if (!MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && MessagesController.getInstance(currentAccount).captionLengthLimitPremium > codepointCount) {
                        showCaptionLimitBulletin(parentFragment);
                    }
                    return;
                }
                if (editingMessageObject == null && baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).isInScheduleMode()) {
                    AlertsCreator.createScheduleDatePickerDialog(getContext(), ((ChatActivity) baseFragment).getDialogId(), (notify, scheduleDate) -> {
                        if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                            sendPressed(notify, scheduleDate, effectId, isCaptionAbove());
                        } else {
                            currentAttachLayout.sendSelectedItems(notify, scheduleDate, effectId, isCaptionAbove());
                            allowPassConfirmationAlert = true;
                            dismiss();
                        }
                    }, resourcesProvider);
                } else {
                    if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                        sendPressed(true, 0, effectId, isCaptionAbove());
                    } else {
                        currentAttachLayout.sendSelectedItems(true, 0, effectId, isCaptionAbove());
                        allowPassConfirmationAlert = true;
                        dismiss();
                    }
                }
                setCaptionAbove(false, false);
            });

            boolean hasMessageToEffect = false;
            MessageObject messageWithCaption = null;

            boolean canHaveStars = false;
            ArrayList<MessageObject> messageObjects = new ArrayList<>();
            int id = 0;
            if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                HashMap<Object, Object> selectedPhotos = photoLayout.getSelectedPhotos();
                ArrayList<Object> selectedPhotosOrder = photoLayout.getSelectedPhotosOrder();
                if (!selectedPhotos.isEmpty()) {
                    final int albumsCount = (int) Math.ceil(selectedPhotos.size() / 10f);
                    for (int i = 0; i < albumsCount; ++i) {
                        int count = Math.min(10, selectedPhotos.size() - (i * 10));
                        long group_id = Utilities.random.nextLong();
                        for (int a = 0; a < count; a++) {
                            if (i * 10 + a >= selectedPhotosOrder.size()) {
                                continue;
                            }
                            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) selectedPhotos.get(selectedPhotosOrder.get(i * 10 + a));
                            TLRPC.TL_message msg = new TLRPC.TL_message();
                            msg.id = id++;
                            msg.out = true;
                            msg.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
                            msg.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId);
                            if (!photoEntry.isVideo && photoEntry.imagePath != null) {
                                msg.attachPath = photoEntry.imagePath;
                            } else if (photoEntry.path != null) {
                                msg.attachPath = photoEntry.path;
                            }
                            if (count > 0) {
                                msg.grouped_id = group_id;
                            }
                            int w = photoEntry.width;
                            int h = photoEntry.height;
                            int orientation = photoEntry.orientation;
                            if (photoEntry.isVideo) {
                                if (photoEntry.videoOrientation == -1) {
                                    MediaMetadataRetriever retriever = null;
                                    try {
                                        retriever = new MediaMetadataRetriever();
                                        retriever.setDataSource(photoEntry.path);
                                        photoEntry.videoOrientation = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                                    } catch (Exception e) {
                                        photoEntry.videoOrientation = 0;
                                        FileLog.e(e);
                                    } finally {
                                        if (retriever != null) {
                                            try {
                                                retriever.release();
                                            } catch (IOException e) {
                                                FileLog.e(e);
                                            }
                                        }
                                    }
                                }
                                orientation = photoEntry.videoOrientation;
                            }
                            if (orientation / 90 % 2 != 0) {
                                int _w = w;
                                w = h;
                                h = _w;
                            }
                            if (photoEntry.isVideo) {
                                msg.media = new TLRPC.TL_messageMediaDocument();
                                msg.media.document = new TLRPC.TL_document();
                                msg.media.document.mime_type = MimeTypeMap.getSingleton().getExtensionFromMimeType(msg.attachPath);
                                TLRPC.TL_documentAttributeVideo attr = new TLRPC.TL_documentAttributeVideo();
                                attr.w = w;
                                attr.h = h;
                                attr.duration = photoEntry.duration;
                                msg.media.document.attributes.add(attr);
                            } else {
                                msg.media = new TLRPC.TL_messageMediaPhoto();
                                msg.media.photo = new TLRPC.TL_photo();
                                TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                                photoSize.w = w;
                                photoSize.h = h;
                                photoSize.location = new TLRPC.TL_fileLocationToBeDeprecated();
                                msg.media.photo.sizes.add(photoSize);
                            }
                            msg.media.spoiler = photoEntry.hasSpoiler;
                            msg.message = photoEntry.caption == null ? "" : photoEntry.caption.toString();
                            if (TextUtils.isEmpty(msg.message) && i == 0 && a == 0) {
                                CharSequence[] message = new CharSequence[]{ getCommentView().getText() };
                                MessageObject.addLinks(true, message[0]);
                                msg.entities = MediaDataController.getInstance(currentAccount).getEntities(message, true);
                                msg.message = message[0].toString();
                            }
                            if (i == 0 && replyMessage != null && !replyMessage.isTopicMainMessage) {
                                TLRPC.TL_messageReplyHeader reply_to = new TLRPC.TL_messageReplyHeader();
                                if (replyTopMessage != null) {
                                    reply_to.flags |= 2;
                                    reply_to.reply_to_top_id = replyTopMessage.getId();
                                }
                                reply_to.flags |= 16;
                                reply_to.reply_to_msg_id = replyMessage.getId();
                                msg.reply_to = reply_to;
                            }
                            MessageObject messageObject = new MessageObject(currentAccount, msg, true, false);
                            if (i == 0 && replyMessage != null && !replyMessage.isTopicMainMessage) {
                                messageObject.replyMessageObject = replyMessage;
                            }
                            messageObject.sendPreviewEntry = photoEntry;
                            messageObject.sendPreview = true;
                            messageObject.notime = true;
                            messageObject.isOutOwnerCached = true;
                            hasMessageToEffect = true;
                            messageObjects.add(messageObject);
                            if (messageWithCaption == null && !TextUtils.isEmpty(msg.message)) {
                                messageWithCaption = messageObject;
                            }
                            canHaveStars = true;
                        }
                    }
                }
            } else {
                if (currentAttachLayout == contactsLayout) {
                    if (!TextUtils.isEmpty(getCommentView().getText())) {
                        hasMessageToEffect = true;
                        TLRPC.TL_message msg = new TLRPC.TL_message();
                        msg.id = id++;
                        msg.out = true;
                        msg.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
                        msg.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId);
                        CharSequence[] message = new CharSequence[]{ getCommentView().getText() };
                        MessageObject.addLinks(true, message[0]);
                        msg.entities = MediaDataController.getInstance(currentAccount).getEntities(message, true);
                        msg.message = message[0].toString();
                        MessageObject messageObject = new MessageObject(currentAccount, msg, true, false);
                        messageObject.sendPreview = true;
                        messageObject.notime = true;
                        messageObject.isOutOwnerCached = true;
                        messageObjects.add(messageObject);
                    }
                    ArrayList<TLRPC.User> users = contactsLayout.getSelected();
                    for (int i = 0; i < users.size(); ++i) {
                        TLRPC.User contact = users.get(i);
                        hasMessageToEffect = true;
                        TLRPC.TL_message msg = new TLRPC.TL_message();
                        msg.id = id++;
                        msg.out = true;
                        msg.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
                        msg.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId);
                        msg.media = new TLRPC.TL_messageMediaContact();
                        msg.media.phone_number = contact.phone;
                        msg.media.first_name = contact.first_name;
                        msg.media.last_name = contact.last_name;
                        if (!contact.restriction_reason.isEmpty() && contact.restriction_reason.get(0).text.startsWith("BEGIN:VCARD")) {
                            msg.media.vcard = contact.restriction_reason.get(0).text;
                        } else {
                            msg.media.vcard = "";
                        }
                        msg.media.user_id = contact.id;
                        MessageObject messageObject = new MessageObject(currentAccount, msg, true, false);
                        messageObject.sendPreview = true;
                        messageObject.notime = true;
                        messageObject.isOutOwnerCached = true;
                        messageObjects.add(messageObject);
                    }
                } else if (currentAttachLayout == documentLayout) {
                    for (int i = 0; i < documentLayout.selectedFilesOrder.size(); ++i) {
                        final String path = documentLayout.selectedFilesOrder.get(i);
                        if (path == null) continue;
                        int index = path.lastIndexOf(File.separator);
                        String filename = index < 0 ? path : path.substring(index + 1);
                        if (TextUtils.isEmpty(filename)) continue;
                        TLRPC.TL_message msg = new TLRPC.TL_message();
                        msg.id = id++;
                        msg.out = true;
                        msg.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
                        msg.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialogId);
                        msg.media = new TLRPC.TL_messageMediaDocument();
                        msg.attachPath = path;
                        msg.media.document = new TLRPC.TL_document();
                        msg.media.document.file_name = filename;
                        msg.media.document.size = new File(path).length();
                        if (TextUtils.isEmpty(msg.message) && i == 0) {
                            CharSequence[] message = new CharSequence[]{ getCommentView().getText() };
                            msg.entities = MediaDataController.getInstance(currentAccount).getEntities(message, true);
                            msg.message = message[0].toString();
                        }
                        MessageObject messageObject = new MessageObject(currentAccount, msg, true, false);
                        messageObject.attachPathExists = true;
                        messageObject.sendPreview = true;
                        messageObject.notime = true;
                        messageObject.isOutOwnerCached = true;
                        messageObjects.add(messageObject);
                        hasMessageToEffect = true;
                        if (i == 0 && messageWithCaption == null && !TextUtils.isEmpty(msg.message)) {
                            messageWithCaption = messageObject;
                        }
                    }
                } else if (currentAttachLayout == audioLayout) {
                    hasMessageToEffect = true;
                    messageObjects.addAll(audioLayout.getSelected());
                    if (!messageObjects.isEmpty()) {
                        MessageObject firstMessageObject = messageObjects.get(0);
                        CharSequence[] message = new CharSequence[]{ getCommentView().getText() };
                        MessageObject.addLinks(true, message[0]);
                        firstMessageObject.messageOwner.entities = MediaDataController.getInstance(currentAccount).getEntities(message, true);
                        firstMessageObject.messageOwner.message = message[0].toString();
                        if (!TextUtils.isEmpty(firstMessageObject.messageOwner.message)) {
                            messageWithCaption = firstMessageObject;
                            firstMessageObject.generateCaption();
                        }
                    }
                    if (messageObjects.size() > 1) {
                        for (int i = 0; i < Math.ceil(messageObjects.size() / 10f); ++i) {
                            int count = Math.min(10, messageObjects.size() - (i * 10));
                            long group_id = Utilities.random.nextLong();
                            for (int a = 0; a < count; a++) {
                                final int j = i * 10 + a;
                                if (j >= messageObjects.size()) {
                                    continue;
                                }
                                messageObjects.get(j).messageOwner.grouped_id = group_id;
                            }
                        }
                    }
                }
            }

            if (messageObjects.isEmpty()) {
                return false;
            }

            ItemOptions options = ItemOptions.makeOptions(containerView, resourcesProvider, writeButton);
            if (messageWithCaption != null && (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout)) {
                MessagePreviewView.ToggleButton button = new MessagePreviewView.ToggleButton(
                    context,
                    R.raw.position_below, getString(R.string.CaptionAbove),
                    R.raw.position_above, getString(R.string.CaptionBelow),
                    resourcesProvider
                );
                final MessageObject msg = messageWithCaption;
                button.setState(!(msg.messageOwner.invert_media = captionAbove), false);
                button.setOnClickListener(v -> {
                    setCaptionAbove(!captionAbove);
                    msg.messageOwner.invert_media = captionAbove;
                    button.setState(!captionAbove, true);
                    messageSendPreview.changeMessage(msg);
                    if (photoLayout != null && photoLayout.captionItem != null) {
                        photoLayout.captionItem.setState(!captionAbove, true);
                    }
                    messageSendPreview.scrollTo(!captionAbove);
                });
                options.addView(button);
                if (editingMessageObject == null) {
                    options.addGap();
                }
            }
            final boolean self = UserObject.isUserSelf(user);
            if (editingMessageObject == null && ((chatActivity != null && chatActivity.canScheduleMessage()) || currentAttachLayout.canScheduleMessages())) {
                final long finalDialogId = dialogId;
                options.add(R.drawable.msg_calendar2, getString(self ? R.string.SetReminder : R.string.ScheduleMessage), () -> {
                    AlertsCreator.createScheduleDatePickerDialog(getContext(), finalDialogId, (notify, scheduleDate) -> {
                        final long effectId = messageSendPreview != null ? messageSendPreview.getSelectedEffect() : 0;
                        if (messageSendPreview != null) {
                            messageSendPreview.dismiss(true);
                            messageSendPreview = null;
                        }
                        if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                            sendPressed(notify, scheduleDate, effectId, isCaptionAbove());
                        } else {
                            currentAttachLayout.sendSelectedItems(notify, scheduleDate, effectId, isCaptionAbove());
                            dismiss();
                        }
                    }, resourcesProvider);
                });
            }
            if (editingMessageObject == null && !self) {
                options.add(R.drawable.input_notify_off, getString(R.string.SendWithoutSound), () -> {
                    final long effectId = messageSendPreview != null ? messageSendPreview.getSelectedEffect() : 0;
                    if (messageSendPreview != null) {
                        messageSendPreview.dismiss(true);
                        messageSendPreview = null;
                    }
                    if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                        sendPressed(false, 0, effectId, isCaptionAbove());
                    } else {
                        currentAttachLayout.sendSelectedItems(false, 0, effectId, isCaptionAbove());
                        dismiss();
                    }
                });
            }
            if (editingMessageObject == null && canHaveStars && chatActivity != null && ChatObject.isChannelAndNotMegaGroup(chatActivity.getCurrentChat()) && chatActivity.getCurrentChatInfo() != null && chatActivity.getCurrentChatInfo().paid_media_allowed) {
                ActionBarMenuSubItem item = options.add(R.drawable.menu_feature_paid, getString(R.string.PaidMediaButton), null).getLast();
                item.setOnClickListener(v -> {
                    if (photoLayout == null) return;
                    StarsIntroActivity.showMediaPriceSheet(context, photoLayout.getStarsPrice(), true, (amount, done) -> {
                        done.run();
                        photoLayout.setStarsPrice(amount);
                        if (amount != null && amount > 0) {
                            item.setText(getString(R.string.PaidMediaPriceButton));
                            item.setSubtext(formatPluralString("Stars", (int) (long) amount));
                            messageSendPreview.setStars(amount);
                        } else {
                            item.setText(getString(R.string.PaidMediaButton));
                            item.setSubtext(null);
                            messageSendPreview.setStars(0);
                        }
                    }, resourcesProvider);
                });
                long amount = photoLayout.getStarsPrice();
                if (amount > 0) {
                    item.setText(getString(R.string.PaidMediaPriceButton));
                    item.setSubtext(formatPluralString("Stars", (int) amount));
                } else {
                    item.setText(getString(R.string.PaidMediaButton));
                    item.setSubtext(null);
                }
                messageSendPreview.setStars(amount);
            }
            options.setupSelectors();
            messageSendPreview.setItemOptions(options);

            messageSendPreview.setMessageObjects(messageObjects);
            if (editingMessageObject == null && dialogId >= 0 && hasMessageToEffect) {
                messageSendPreview.allowEffectSelector(parentFragment);
            }

            messageSendPreview.show();

            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

            return true;
        });

        textPaint.setTextSize(dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());

        selectedCountView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                String text = String.format("%d", Math.max(1, currentAttachLayout.getSelectedItemsCount()));
                int textSize = (int) Math.ceil(textPaint.measureText(text));
                int size = Math.max(dp(16) + textSize, dp(24));
                int cx = getMeasuredWidth() / 2;

                int color = getThemedColor(Theme.key_dialogRoundCheckBoxCheck);
                textPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * (0.58 + 0.42 * sendButtonEnabledProgress))));
                paint.setColor(getThemedColor(Theme.key_dialogBackground));
                rect.set(cx - size / 2, 0, cx + size / 2, getMeasuredHeight());
                canvas.drawRoundRect(rect, dp(12), dp(12), paint);

                paint.setColor(getThemedColor(Theme.key_chat_attachCheckBoxBackground));
                rect.set(cx - size / 2 + dp(2), dp(2), cx + size / 2 - dp(2), getMeasuredHeight() - dp(2));
                canvas.drawRoundRect(rect, dp(10), dp(10), paint);

                canvas.drawText(text, cx - textSize / 2, dp(16.2f), textPaint);
            }
        };
        selectedCountView.setAlpha(0.0f);
        selectedCountView.setScaleX(0.2f);
        selectedCountView.setScaleY(0.2f);
//        containerView.addView(selectedCountView, LayoutHelper.createFrame(42, 24, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, -8, 9));

        if (forceDarkTheme) {
            checkColors();
            navBarColorKey = -1;
        }

        passcodeView = new PasscodeView(context);
        containerView.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public boolean hasCaption() {
        if (photoLayout == null) {
            return false;
        }
        boolean hasCaption = false;
        HashMap<Object, Object> selectedPhotos = photoLayout.getSelectedPhotos();
        ArrayList<Object> selectedPhotosOrder = photoLayout.getSelectedPhotosOrder();
        if (!selectedPhotos.isEmpty()) {
            for (int i = 0; i < Math.ceil(selectedPhotos.size() / 10f); ++i) {
                int count = Math.min(10, selectedPhotos.size() - (i * 10));
                long group_id = Utilities.random.nextLong();
                for (int a = 0; a < count; a++) {
                    if (i * 10 + a >= selectedPhotosOrder.size()) {
                        continue;
                    }
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) selectedPhotos.get(selectedPhotosOrder.get(i * 10 + a));
                    CharSequence caption = photoEntry.caption == null ? "" : photoEntry.caption.toString();
                    if (getCommentView() != null && TextUtils.isEmpty(caption) && a == 0) {
                        caption = getCommentView().getText().toString();
                    }
                    if (!TextUtils.isEmpty(caption)) {
                        if (hasCaption) return false;
                        hasCaption = true;
                    }
                }
            }
        }
        return hasCaption;
    }

    public boolean isCaptionAbove() {
        return captionAbove && (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Context context = getContext();
        if (context instanceof ContextWrapper && !(context instanceof LaunchActivity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof LaunchActivity) {
            ((LaunchActivity) context).addOverlayPasscodeView(passcodeView);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        Context context = getContext();
        if (context instanceof ContextWrapper && !(context instanceof LaunchActivity)) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof LaunchActivity) {
            ((LaunchActivity) context).removeOverlayPasscodeView(passcodeView);
        }
    }

    public void updateCommentTextViewPosition() {
        commentTextView.getLocationOnScreen(commentTextViewLocation);
        if (mentionContainer != null) {
            float y;
            final boolean above = (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) && captionAbove;
            if (above) {
                y = topCommentContainer.getY() - mentionContainer.getTop() + topCommentContainer.getMeasuredHeight() * topCommentContainer.getAlpha();
            } else {
                y = -commentTextView.getHeight();
            }
            if (Math.abs(mentionContainer.getTranslationY() - y) > 0.5f) {
                mentionContainer.setTranslationY(y);
                mentionContainer.invalidate();
                if (photoLayout != null) {
                    photoLayout.checkCameraViewPosition();
                }
            }
        }
        if (moveCaptionButton != null) {
            moveCaptionButton.setTranslationY(bottomPannelTranslation - commentTextView.getHeight() + captionContainer.getTranslationY());
        }
    }

    public int getCommentTextViewTop() {
        return commentTextViewLocation[1];
    }

    private void showCaptionLimitBulletin(BaseFragment parentFragment) {
        if (!(parentFragment instanceof ChatActivity) || !ChatObject.isChannelAndNotMegaGroup(((ChatActivity) parentFragment).getCurrentChat())) {
            return;
        }

        BulletinFactory.of(sizeNotifierFrameLayout, resourcesProvider).createCaptionLimitBulletin(MessagesController.getInstance(currentAccount).captionLengthLimitPremium, () -> {
            dismiss(true);
            if (parentFragment != null) {
                parentFragment.presentFragment(new PremiumPreviewFragment("caption_limit"));
            }
        }).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (baseFragment != null) {
            AndroidUtilities.setLightStatusBar(getWindow(), baseFragment.isLightStatusBar());
        }
    }

    private boolean isLightStatusBar() {
        int color = getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    public void onLongClickBotButton(TLRPC.TL_attachMenuBot attachMenuBot, TLRPC.User currentUser) {
        String botName = attachMenuBot != null ? attachMenuBot.short_name : UserObject.getUserName(currentUser);
        String description;
        TLRPC.TL_attachMenuBot currentBot = null;
        for (TLRPC.TL_attachMenuBot bot : MediaDataController.getInstance(currentAccount).getAttachMenuBots().bots) {
            if (bot.bot_id == currentUser.id) {
                currentBot = bot;
                break;
            }
        }
        description = LocaleController.formatString("BotRemoveFromMenu", R.string.BotRemoveFromMenu, botName);
        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.BotRemoveFromMenuTitle))
                .setMessage(AndroidUtilities.replaceTags(attachMenuBot != null ? description : LocaleController.formatString("BotRemoveInlineFromMenu", R.string.BotRemoveInlineFromMenu, botName)))
                .setPositiveButton(getString("OK", R.string.OK), (dialogInterface, i) -> {
                    if (attachMenuBot != null) {
                        TLRPC.TL_messages_toggleBotInAttachMenu req = new TLRPC.TL_messages_toggleBotInAttachMenu();
                        req.bot = MessagesController.getInstance(currentAccount).getInputUser(currentUser);
                        req.enabled = false;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            MediaDataController.getInstance(currentAccount).loadAttachMenuBots(false, true);
                            if (currentAttachLayout == botAttachLayouts.get(attachMenuBot.bot_id)) {
                                showLayout(photoLayout);
                            }
                        }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                    } else {
                        MediaDataController.getInstance(currentAccount).removeInline(currentUser.id);
                    }
                })
                .setNegativeButton(getString("Cancel", R.string.Cancel), null)
                .show();
    }

    @Override
    protected boolean shouldOverlayCameraViewOverNavBar() {
        return currentAttachLayout == photoLayout && photoLayout.cameraExpanded;
    }

    @Override
    public void show() {
        super.show();
        buttonPressed = false;
        if (baseFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            calcMandatoryInsets = chatActivity.isKeyboardVisible();
        }
        openTransitionFinished = false;
        if (Build.VERSION.SDK_INT >= 30) {
            navBarColorKey = -1;
            navBarColor = ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundGray), 0);
            AndroidUtilities.setNavigationBarColor(getWindow(), navBarColor, false);
            AndroidUtilities.setLightNavigationBar(getWindow(), AndroidUtilities.computePerceivedBrightness(navBarColor) > 0.721);
        }
    }

    public static final int EDITMEDIA_TYPE_ANY = -1;
    public static final int EDITMEDIA_TYPE_PHOTOVIDEO = 0;
    public static final int EDITMEDIA_TYPE_FILE = 1;
    public static final int EDITMEDIA_TYPE_MUSIC = 2;

    public void setEditingMessageObject(int type, MessageObject messageObject) {
        if (messageObject != null) {
            if (photoLayout != null) {
                photoLayout.clearSelectedPhotos();
            }
        }
        if (editingMessageObject == messageObject && editType == type) {
            return;
        }
        editingMessageObject = messageObject;
        if (editingMessageObject != null && editingMessageObject.hasValidGroupId()) {
            if (editingMessageObject.isMusic())
                type = EDITMEDIA_TYPE_MUSIC;
            else if (editingMessageObject.isDocument())
                type = EDITMEDIA_TYPE_FILE;
            else
                type = EDITMEDIA_TYPE_PHOTOVIDEO;
        }
        editType = type;
        if (editingMessageObject != null) {
            maxSelectedPhotos = 1;
            allowOrder = false;
        } else {
            maxSelectedPhotos = -1;
            allowOrder = true;
        }
        buttonsAdapter.notifyDataSetChanged();
        updateCountButton(0);
    }

    public MessageObject getEditingMessageObject() {
        return editingMessageObject;
    }

    protected void applyCaption() {
        if (getCommentView().length() <= 0) {
            return;
        }
        currentAttachLayout.applyCaption(getCommentView().getText());
    }

    private void sendPressed(boolean notify, int scheduleDate, long effectId, boolean invertMedia) {
        if (buttonPressed) {
            return;
        }
        if (baseFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            TLRPC.Chat chat = chatActivity.getCurrentChat();
            TLRPC.User user = chatActivity.getCurrentUser();
            if (user != null || ChatObject.isChannel(chat) && chat.megagroup || !ChatObject.isChannel(chat)) {
                MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("silent_" + chatActivity.getDialogId(), !notify).commit();
            }
        }
        if (checkCaption(getCommentView().getText())) {
            return;
        }
        applyCaption();
        buttonPressed = true;

        delegate.didPressedButton(7, true, notify, scheduleDate, effectId, invertMedia, false);
    }

    public void showLayout(AttachAlertLayout layout) {
        long newId = selectedId;
        if (layout == restrictedLayout) {
            newId = restrictedLayout.id;
        } else if (layout == photoLayout) {
            newId = 1;
        } else if (layout == audioLayout) {
            newId = 3;
        } else if (layout == documentLayout) {
            newId = 4;
        } else if (layout == contactsLayout) {
            newId = 5;
        } else if (layout == locationLayout) {
            newId = 6;
        } else if (layout == pollLayout) {
            newId = 9;
        } else if (layout == colorsLayout) {
            newId = 10;
        } else if (layout == quickRepliesLayout) {
            newId = 11;
        }
        showLayout(layout, newId);
    }

    private void showLayout(AttachAlertLayout layout, long newId) {
        showLayout(layout, newId, true);
    }

    private void showLayout(AttachAlertLayout layout, long newId, boolean animated) {
        if (viewChangeAnimator != null || commentsAnimator != null) {
            return;
        }
        if (currentAttachLayout == layout) {
            currentAttachLayout.scrollToTop();
            return;
        }

        botButtonWasVisible = false;
        botButtonProgressWasVisible = false;
        botMainButtonOffsetY = 0;
        botMainButtonTextView.setVisibility(View.GONE);
        botProgressView.setAlpha(0f);
        botProgressView.setScaleX(0.1f);
        botProgressView.setScaleY(0.1f);
        botProgressView.setVisibility(View.GONE);
        buttonsRecyclerView.setAlpha(1f);
        buttonsRecyclerView.setTranslationY(botMainButtonOffsetY);
        for (int i = 0; i < botAttachLayouts.size(); i++) {
            botAttachLayouts.valueAt(i).setMeasureOffsetY(0);
        }

        selectedId = newId;
        int count = buttonsRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = buttonsRecyclerView.getChildAt(a);
            if (child instanceof AttachButton) {
                AttachButton attachButton = (AttachButton) child;
                attachButton.updateCheckedState(true);
            } else if (child instanceof AttachBotButton) {
                AttachBotButton attachButton = (AttachBotButton) child;
                attachButton.updateCheckedState(true);
            }
        }
        int t = currentAttachLayout.getFirstOffset() - dp(11) - scrollOffsetY[0];
        nextAttachLayout = layout;
        if (Build.VERSION.SDK_INT >= 20) {
            container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        actionBar.setVisibility(nextAttachLayout.needsActionBar() != 0 ? View.VISIBLE : View.INVISIBLE);
        actionBarShadow.setVisibility(actionBar.getVisibility());
        if (actionBar.isSearchFieldVisible()) {
            actionBar.closeSearchField();
        }
        currentAttachLayout.onHide();
        if (nextAttachLayout == photoLayout) {
            photoLayout.setCheckCameraWhenShown(true);
        }
        nextAttachLayout.onShow(currentAttachLayout);
        nextAttachLayout.setVisibility(View.VISIBLE);

        if (layout.getParent() != null) {
            containerView.removeView(nextAttachLayout);
        }
        int index = containerView.indexOfChild(currentAttachLayout);
        if (nextAttachLayout.getParent() != containerView) {
            containerView.addView(nextAttachLayout, nextAttachLayout == locationLayout ? index : index + 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        Runnable onEnd = () -> {
            if (Build.VERSION.SDK_INT >= 20) {
                container.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            viewChangeAnimator = null;
            if ((currentAttachLayout != photoLayout && nextAttachLayout != photoPreviewLayout) && (currentAttachLayout != nextAttachLayout && currentAttachLayout != photoPreviewLayout)) {
                containerView.removeView(currentAttachLayout);
            }
            currentAttachLayout.setVisibility(View.GONE);
            currentAttachLayout.onHidden();
            nextAttachLayout.onShown();
            currentAttachLayout = nextAttachLayout;
            nextAttachLayout = null;
            scrollOffsetY[0] = scrollOffsetY[1];

            setCaptionAbove(captionAbove, false);
        };

        if (!(currentAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview || nextAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview)) {
            if (animated) {
                AnimatorSet animator = new AnimatorSet();
                nextAttachLayout.setAlpha(0.0f);
                nextAttachLayout.setTranslationY(dp(78));
                animator.playTogether(
                        ObjectAnimator.ofFloat(currentAttachLayout, View.TRANSLATION_Y, dp(78) + t),
                        ObjectAnimator.ofFloat(currentAttachLayout, ATTACH_ALERT_LAYOUT_TRANSLATION, 0.0f, 1.0f),
                        ObjectAnimator.ofFloat(actionBar, View.ALPHA, actionBar.getAlpha(), 0f)
                );
                animator.setDuration(180);
//                animator.setStartDelay(20);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        currentAttachLayout.setAlpha(0.0f);
                        currentAttachLayout.setTranslationY(dp(78) + t);
                        ATTACH_ALERT_LAYOUT_TRANSLATION.set(currentAttachLayout, 1.0f);
                        actionBar.setAlpha(0f);
                        SpringAnimation springAnimation = new SpringAnimation(nextAttachLayout, DynamicAnimation.TRANSLATION_Y, 0);
                        springAnimation.getSpring().setDampingRatio(0.75f);
                        springAnimation.getSpring().setStiffness(500.0f);
                        springAnimation.addUpdateListener((animation12, value, velocity) -> {
                            if (nextAttachLayout == pollLayout || (isPhotoPicker && viewChangeAnimator != null)) {
                                updateSelectedPosition(1);
                            }
                            nextAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
                            containerView.invalidate();
                        });
                        springAnimation.addEndListener((animation1, canceled, value, velocity) -> {
                            nextAttachLayout.setTranslationY(0);
                            nextAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
                            containerView.invalidate();
                            onEnd.run();
                            updateSelectedPosition(0);
                        });
                        viewChangeAnimator = springAnimation;
                        springAnimation.start();
                    }
                });
                viewChangeAnimator = animator;
                ATTACH_ALERT_LAYOUT_TRANSLATION.set(currentAttachLayout, 0f);
                animator.start();
            } else {
                currentAttachLayout.setAlpha(0.0f);
                onEnd.run();
                updateSelectedPosition(0);
                containerView.invalidate();
            }
        } else {
            int width = Math.max(nextAttachLayout.getWidth(), currentAttachLayout.getWidth());
            if (nextAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview) {
                nextAttachLayout.setTranslationX(width);
                if (currentAttachLayout instanceof ChatAttachAlertPhotoLayout) {
                    ChatAttachAlertPhotoLayout photoLayout = (ChatAttachAlertPhotoLayout) currentAttachLayout;
                    if (photoLayout.cameraView != null) {
                        photoLayout.cameraView.setVisibility(View.INVISIBLE);
                        photoLayout.cameraIcon.setVisibility(View.INVISIBLE);
                        photoLayout.cameraCell.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                currentAttachLayout.setTranslationX(-width);
                if (nextAttachLayout == photoLayout) {
                    ChatAttachAlertPhotoLayout photoLayout = (ChatAttachAlertPhotoLayout) nextAttachLayout;
                    if (photoLayout.cameraView != null) {
                        photoLayout.cameraView.setVisibility(View.VISIBLE);
                        photoLayout.cameraIcon.setVisibility(View.VISIBLE);
                    }
                }
            }
            nextAttachLayout.setAlpha(1);
            currentAttachLayout.setAlpha(1);
            if (animated) {
                ATTACH_ALERT_LAYOUT_TRANSLATION.set(currentAttachLayout, 0.0f);
                AndroidUtilities.runOnUIThread(() -> {
                    boolean showActionBar = nextAttachLayout.getCurrentItemTop() <= layout.getButtonsHideOffset();
                    float fromActionBarAlpha = actionBar.getAlpha();
                    float toActionBarAlpha = showActionBar ? 1f : 0f;
                    SpringAnimation springAnimation = new SpringAnimation(new FloatValueHolder(0));
                    springAnimation.addUpdateListener((animation, value, velocity) -> {
                        float f = value / 500f;
                        ATTACH_ALERT_LAYOUT_TRANSLATION.set(currentAttachLayout, f);
                        actionBar.setAlpha(AndroidUtilities.lerp(fromActionBarAlpha, toActionBarAlpha, f));
                        updateLayout(currentAttachLayout, false, 0);
                        updateLayout(nextAttachLayout, false, 0);
                        float mediaPreviewAlpha = nextAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview && !showActionBar ? f : 1f - f;
                        mediaPreviewAlpha = Utilities.clamp(mediaPreviewAlpha, 1, 0);
                        mediaPreviewView.setAlpha(mediaPreviewAlpha);
                        selectedView.setAlpha(1f - mediaPreviewAlpha);
                        selectedView.setTranslationX(mediaPreviewAlpha * -dp(16));
                        mediaPreviewView.setTranslationX((1f - mediaPreviewAlpha) * dp(16));
                    });
                    springAnimation.addEndListener((animation, canceled, value, velocity) -> {
                        currentAttachLayout.onHideShowProgress(1f);
                        nextAttachLayout.onHideShowProgress(1f);
                        currentAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
                        nextAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
                        containerView.invalidate();
                        actionBar.setTag(showActionBar ? 1 : null);
                        onEnd.run();
                    });
                    springAnimation.setSpring(new SpringForce(500f));
                    springAnimation.getSpring().setDampingRatio(1f);
                    springAnimation.getSpring().setStiffness(1000.0f);
                    springAnimation.start();

                    viewChangeAnimator = springAnimation;
                });
            } else {
                boolean showActionBar = nextAttachLayout.getCurrentItemTop() <= layout.getButtonsHideOffset();
                currentAttachLayout.onHideShowProgress(1f);
                nextAttachLayout.onHideShowProgress(1f);
                currentAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
                nextAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
                containerView.invalidate();
                ATTACH_ALERT_LAYOUT_TRANSLATION.set(currentAttachLayout, 1.0f);
                actionBar.setTag(showActionBar ? 1 : null);
                onEnd.run();
            }
        }
    }

    public AttachAlertLayout getCurrentAttachLayout() {
        return currentAttachLayout;
    }

    public ChatAttachAlertPhotoLayoutPreview getPhotoPreviewLayout() {
        return photoPreviewLayout;
    }

    public void updatePhotoPreview(boolean show) {
        if (show) {
            if (!canOpenPreview) {
                return;
            }
            if (photoPreviewLayout == null) {
                photoPreviewLayout = new ChatAttachAlertPhotoLayoutPreview(this, getContext(), (parentThemeDelegate != null ? parentThemeDelegate : resourcesProvider));
                photoPreviewLayout.bringToFront();
            }
            showLayout(currentAttachLayout == photoPreviewLayout ? photoLayout : photoPreviewLayout);
        } else {
            showLayout(photoLayout);
        }
    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == BasePermissionsActivity.REQUEST_CODE_ATTACH_CONTACT && grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openContactsLayout();
        } else if (requestCode == 30 && locationLayout != null && currentAttachLayout == locationLayout && isShowing()) {
            locationLayout.openShareLiveLocation();
        }
    }

    private void openContactsLayout() {
        if (!plainTextEnabled) {
            restrictedLayout = new ChatAttachRestrictedLayout(5, this, getContext(), resourcesProvider);
            showLayout(restrictedLayout);
        }
        if (contactsLayout == null) {
            layouts[2] = contactsLayout = new ChatAttachAlertContactsLayout(this, getContext(), resourcesProvider);
            contactsLayout.setDelegate(new ChatAttachAlertContactsLayout.PhonebookShareAlertDelegate() {
                @Override
                public void didSelectContact(TLRPC.User user, boolean notify, int scheduleDate, long effectId, boolean invertMedia) {
                    ((ChatActivity) baseFragment).sendContact(user, notify, scheduleDate, effectId, invertMedia);
                }

                @Override
                public void didSelectContacts(ArrayList<TLRPC.User> users, String caption, boolean notify, int scheduleDate, long effectId, boolean invertMedia) {
                    ((ChatActivity) baseFragment).sendContacts(users, caption, notify, scheduleDate, effectId, invertMedia);
                }
            });
        }
        if (baseFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            TLRPC.Chat currentChat = chatActivity.getCurrentChat();
            contactsLayout.setMultipleSelectionAllowed(!(currentChat != null && !ChatObject.hasAdminRights(currentChat) && currentChat.slowmode_enabled));
        }
        showLayout(contactsLayout);
    }

    private void openQuickRepliesLayout() {
        if (quickRepliesLayout == null) {
            layouts[7] = quickRepliesLayout = new ChatAttachAlertQuickRepliesLayout(this, getContext(), resourcesProvider);
        }
        showLayout(quickRepliesLayout);
    }

    public boolean checkCanRemoveRestrictionsByBoosts() {
        return (baseFragment instanceof ChatActivity) && ((ChatActivity) baseFragment).checkCanRemoveRestrictionsByBoosts();
    }

    private void openAudioLayout(boolean show) {
        if (!musicEnabled) {
            if (show) {
                restrictedLayout = new ChatAttachRestrictedLayout(3, this, getContext(), resourcesProvider);
                showLayout(restrictedLayout);
            }
        }
        if (audioLayout == null) {
            layouts[3] = audioLayout = new ChatAttachAlertAudioLayout(this, getContext(), resourcesProvider);
            audioLayout.setDelegate((audios, caption, notify, scheduleDate, effectId, invertMedia) -> {
                if (baseFragment != null && baseFragment instanceof ChatActivity) {
                    ((ChatActivity) baseFragment).sendAudio(audios, caption, notify, scheduleDate, effectId, invertMedia);
                } else if (delegate != null) {
                    delegate.sendAudio(audios, caption, notify, scheduleDate, effectId, invertMedia);
                }
            });
        }
        if (baseFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            TLRPC.Chat currentChat = chatActivity.getCurrentChat();
            audioLayout.setMaxSelectedFiles(currentChat != null && !ChatObject.hasAdminRights(currentChat) && currentChat.slowmode_enabled || editingMessageObject != null ? 1 : -1);
        }
        if (show) {
            showLayout(audioLayout);
        }
    }

    public void openColorsLayout() {
        if (colorsLayout == null) {
            colorsLayout = new ChatAttachAlertColorsLayout(this, getContext(), resourcesProvider);
            colorsLayout.setDelegate((wallpaper -> {
                if (delegate != null) {
                    delegate.onWallpaperSelected(wallpaper);
                }
            }));
        }
        showLayout(colorsLayout);
    }

    private void openDocumentsLayout(boolean show) {
        if (!documentsEnabled) {
            if (show) {
                restrictedLayout = new ChatAttachRestrictedLayout(4, this, getContext(), resourcesProvider);
                showLayout(restrictedLayout);
            }
        }
        if (documentLayout == null) {
            int type = isSoundPicker ? ChatAttachAlertDocumentLayout.TYPE_RINGTONE : ChatAttachAlertDocumentLayout.TYPE_DEFAULT;
            layouts[4] = documentLayout = new ChatAttachAlertDocumentLayout(this, getContext(), type, resourcesProvider);
            documentLayout.setDelegate(new ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate() {
                @Override
                public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<MessageObject> fmessages, boolean notify, int scheduleDate, long effectId, boolean invertMedia) {
                    if (documentsDelegate != null) {
                        documentsDelegate.didSelectFiles(files, caption, fmessages, notify, scheduleDate, effectId, invertMedia);
                    } else if (baseFragment instanceof ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate) {
                        ((ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate) baseFragment).didSelectFiles(files, caption, fmessages, notify, scheduleDate, effectId, invertMedia);
                    } else if (baseFragment instanceof PassportActivity) {
                        ((PassportActivity) baseFragment).didSelectFiles(files, caption, notify, scheduleDate, effectId, invertMedia);
                    }
                }

                @Override
                public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                    if (documentsDelegate != null) {
                        documentsDelegate.didSelectPhotos(photos, notify, scheduleDate);
                    } else if (baseFragment instanceof ChatActivity) {
                        ((ChatActivity) baseFragment).didSelectPhotos(photos, notify, scheduleDate);
                    } else if (baseFragment instanceof PassportActivity) {
                        ((PassportActivity) baseFragment).didSelectPhotos(photos, notify, scheduleDate);
                    }
                }

                @Override
                public void startDocumentSelectActivity() {
                    if (documentsDelegate != null) {
                        documentsDelegate.startDocumentSelectActivity();
                    } else if (baseFragment instanceof ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate) {
                        ((ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate) baseFragment).startDocumentSelectActivity();
                    } else if (baseFragment instanceof PassportActivity) {
                        ((PassportActivity) baseFragment).startDocumentSelectActivity();
                    }
                }

                @Override
                public void startMusicSelectActivity() {
                    openAudioLayout(true);
                }
            });
        }
        if (baseFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            TLRPC.Chat currentChat = chatActivity.getCurrentChat();
            documentLayout.setMaxSelectedFiles(currentChat != null && !ChatObject.hasAdminRights(currentChat) && currentChat.slowmode_enabled || editingMessageObject != null ? 1 : -1);
        } else {
            documentLayout.setMaxSelectedFiles(maxSelectedPhotos);
            documentLayout.setCanSelectOnlyImageFiles(!isSoundPicker && !allowEnterCaption);
        }
        documentLayout.isSoundPicker = isSoundPicker;
        if (show) {
            showLayout(documentLayout);
        }
    }

    private boolean showCommentTextView(boolean show, boolean animated) {
        if (show == (frameLayout2.getTag() != null)) {
            return false;
        }
        if (commentsAnimator != null) {
            commentsAnimator.cancel();
        }
        frameLayout2.setTag(show ? 1 : null);
        if (commentTextView.getEditText().isFocused()) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        commentTextView.hidePopup(true);
        topCommentTextView.hidePopup(true);
        if (show) {
            if (!isSoundPicker) {
                frameLayout2.setVisibility(View.VISIBLE);
            }
            writeButtonContainer.setVisibility(View.VISIBLE);
            if (!typeButtonsAvailable && !isSoundPicker) {
                shadow.setVisibility(View.VISIBLE);
            }
        } else if (typeButtonsAvailable) {
            buttonsRecyclerView.setVisibility(View.VISIBLE);
        }
        final boolean allowAbove = (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout);
        final boolean above = allowAbove && captionAbove;
        if (animated) {
            commentsAnimator = new AnimatorSet();
            if (above) {
                topCommentContainer.setVisibility(View.VISIBLE);
            }
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(frameLayout2, View.ALPHA, show ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(captionContainer, View.ALPHA, show && !above ? 1.0f : 0.0f));
            if (show && !above) {
                captionContainer.setVisibility(View.VISIBLE);
                animators.add(ObjectAnimator.ofFloat(captionContainer, View.TRANSLATION_Y, 0));
            }
            moveCaptionButton.setVisibility(View.VISIBLE);
            animators.add(ObjectAnimator.ofFloat(moveCaptionButton, View.ALPHA, show && allowAbove && !above ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(topCommentContainer, View.ALPHA, show && above ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, show ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_X, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButton, View.SCALE_Y, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButton, View.ALPHA, show ? 1.0f : 0.0f));
            if (actionBar.getTag() != null) {
                animators.add(ObjectAnimator.ofFloat(frameLayout2, View.TRANSLATION_Y, show ? 0.0f : dp(48)));
                animators.add(ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, show ? dp(36) : dp(48 + 36)));
                animators.add(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
            } else if (typeButtonsAvailable) {
                animators.add(ObjectAnimator.ofFloat(buttonsRecyclerView, View.TRANSLATION_Y, show ? dp(84) : 0));
                animators.add(ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, show ? dp(36) : 0));
            } else if (!isSoundPicker) {
                shadow.setTranslationY(dp(36) + botMainButtonOffsetY);
                animators.add(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
            }

            if (above) {
                ValueAnimator va = ValueAnimator.ofFloat(0, 1);
                va.addUpdateListener(a -> {
                    updatedTopCaptionHeight();
                });
                animators.add(va);
            }
            commentsAnimator.playTogether(animators);
            commentsAnimator.setInterpolator(new DecelerateInterpolator());
            commentsAnimator.setDuration(180);
            commentsAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(commentsAnimator)) {
                        if (!show) {
                            if (!isSoundPicker) {
                                frameLayout2.setVisibility(View.INVISIBLE);
                            }
                            writeButtonContainer.setVisibility(View.INVISIBLE);
                            if (!typeButtonsAvailable && !isSoundPicker) {
                                shadow.setVisibility(View.INVISIBLE);
                            }
                        } else if (typeButtonsAvailable) {
                            if (currentAttachLayout == null || currentAttachLayout.shouldHideBottomButtons()) {
                                buttonsRecyclerView.setVisibility(View.INVISIBLE);
                            }
                        }
                        moveCaptionButton.setTranslationY(bottomPannelTranslation - commentTextView.getHeight() + captionContainer.getTranslationY());
                        if (above) {
                            updatedTopCaptionHeight();
                            topCommentContainer.setVisibility(show ? View.VISIBLE : View.GONE);
                        }
                        commentsAnimator = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (animation.equals(commentsAnimator)) {
                        commentsAnimator = null;
                    }
                }
            });
            commentsAnimator.start();
        } else {
            frameLayout2.setAlpha(show ? 1.0f : 0.0f);
            captionContainer.setAlpha(show && above ? 1.0f : 0.0f);
            if (show && !above) {
                captionContainer.setVisibility(View.VISIBLE);
                captionContainer.setTranslationY(0);
            }
            moveCaptionButton.setAlpha(show && allowAbove && !above ? 1.0f : 0.0f);
            moveCaptionButton.setVisibility(show && allowAbove && !above ? View.VISIBLE : View.GONE);
            moveCaptionButton.setTranslationY(bottomPannelTranslation - commentTextView.getHeight() + captionContainer.getTranslationY());
            writeButtonContainer.setScaleX(show ? 1.0f : 0.2f);
            writeButtonContainer.setScaleY(show ? 1.0f : 0.2f);
            writeButtonContainer.setAlpha(show ? 1.0f : 0.0f);
            topCommentContainer.setVisibility(show && above ? View.VISIBLE : View.GONE);
            topCommentContainer.setAlpha(show && above ? 1.0f : 0.0f);
            writeButton.setScaleX(show ? 1.0f : 0.2f);
            writeButton.setScaleY(show ? 1.0f : 0.2f);
            writeButton.setAlpha(show ? 1.0f : 0.0f);
            if (actionBar.getTag() != null) {
                frameLayout2.setTranslationY(show ? 0.0f : dp(48));
                shadow.setTranslationY((show ? dp(36) : dp(48 + 36)) + botMainButtonOffsetY);
                shadow.setAlpha(show ? 1.0f : 0.0f);
            } else if (typeButtonsAvailable) {
                if (currentAttachLayout == null || currentAttachLayout.shouldHideBottomButtons()) {
                    buttonsRecyclerView.setTranslationY(show ? dp(84) : 0);
                }
                shadow.setTranslationY((show ? dp(36) : 0) + botMainButtonOffsetY);
            } else {
                shadow.setTranslationY(dp(36) + botMainButtonOffsetY);
                shadow.setAlpha(show ? 1.0f : 0.0f);
            }
            if (!show) {
                frameLayout2.setVisibility(View.INVISIBLE);
                writeButtonContainer.setVisibility(View.INVISIBLE);
                if (!typeButtonsAvailable) {
                    shadow.setVisibility(View.INVISIBLE);
                }
            }
            actionBarShadow.setTranslationY(currentPanTranslationY + topCommentContainer.getMeasuredHeight() * topCommentContainer.getAlpha());
            if (above) {
                updatedTopCaptionHeight();
            }
        }
        writeButton.setCount(show ? Math.max(1, currentAttachLayout.getSelectedItemsCount()) : 0, animated);
        return true;
    }

    private final Property<ChatAttachAlert, Float> ATTACH_ALERT_PROGRESS = new AnimationProperties.FloatProperty<ChatAttachAlert>("openProgress") {

        private float openProgress;

        @Override
        public void setValue(ChatAttachAlert object, float value) {
            for (int a = 0, N = buttonsRecyclerView.getChildCount(); a < N; a++) {
                float startTime = 32.0f * (3 - a);
                View child = buttonsRecyclerView.getChildAt(a);
                float scale;
                if (value > startTime) {
                    float elapsedTime = value - startTime;
                    if (elapsedTime <= 200.0f) {
                        scale = 1.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(elapsedTime / 200.0f);
                        child.setAlpha(CubicBezierInterpolator.EASE_BOTH.getInterpolation(elapsedTime / 200.0f));
                    } else {
                        child.setAlpha(1.0f);
                        elapsedTime -= 200.0f;
                        if (elapsedTime <= 100.0f) {
                            scale = 1.1f - 0.1f * CubicBezierInterpolator.EASE_IN.getInterpolation(elapsedTime / 100.0f);
                        } else {
                            scale = 1.0f;
                        }
                    }
                } else {
                    scale = 0;
                }
                if (child instanceof AttachButton) {
                    AttachButton attachButton = (AttachButton) child;
                    attachButton.textView.setScaleX(scale);
                    attachButton.textView.setScaleY(scale);
                    attachButton.imageView.setScaleX(scale);
                    attachButton.imageView.setScaleY(scale);
                } else if (child instanceof AttachBotButton) {
                    AttachBotButton attachButton = (AttachBotButton) child;
                    attachButton.nameTextView.setScaleX(scale);
                    attachButton.nameTextView.setScaleY(scale);
                    attachButton.imageView.setScaleX(scale);
                    attachButton.imageView.setScaleY(scale);
                }
            }
        }

        @Override
        public Float get(ChatAttachAlert object) {
            return openProgress;
        }
    };

    @Override
    protected void cancelSheetAnimation() {
        if (currentSheetAnimation != null) {
            currentSheetAnimation.cancel();
            if (appearSpringAnimation != null) {
                appearSpringAnimation.cancel();
            }
            if (buttonsAnimation != null) {
                buttonsAnimation.cancel();
            }
            currentSheetAnimation = null;
            currentSheetAnimationType = 0;
        }
    }

    private SpringAnimation appearSpringAnimation;
    private AnimatorSet buttonsAnimation;

    @Override
    protected boolean onCustomOpenAnimation() {
        photoLayout.setTranslationX(0);
        mediaPreviewView.setAlpha(0);
        selectedView.setAlpha(1);

        int fromTranslationY = super.containerView.getMeasuredHeight();
        super.containerView.setTranslationY(fromTranslationY);

        buttonsAnimation = new AnimatorSet();
        buttonsAnimation.playTogether(ObjectAnimator.ofFloat(this, ATTACH_ALERT_PROGRESS, 0.0f, 400.0f));
        buttonsAnimation.setDuration(400);
        buttonsAnimation.setStartDelay(20);
        ATTACH_ALERT_PROGRESS.set(this, 0.0f);
        buttonsAnimation.start();

        if (navigationBarAnimation != null) {
            navigationBarAnimation.cancel();
        }
        navigationBarAnimation = ValueAnimator.ofFloat(navigationBarAlpha, 1f);
        navigationBarAnimation.addUpdateListener(a -> {
            navigationBarAlpha = (float) a.getAnimatedValue();
            if (container != null) {
                container.invalidate();
            }
        });

        if (appearSpringAnimation != null) {
            appearSpringAnimation.cancel();
        }
        appearSpringAnimation = new SpringAnimation(super.containerView, DynamicAnimation.TRANSLATION_Y, 0);
        if (editingMessageObject != null) {
            appearSpringAnimation.getSpring().setDampingRatio(0.75f);
            appearSpringAnimation.getSpring().setStiffness(350.0f);
        } else {
            appearSpringAnimation.getSpring().setDampingRatio(0.75f);
            appearSpringAnimation.getSpring().setStiffness(350.0f);
        }
        appearSpringAnimation.start();

        if (Build.VERSION.SDK_INT >= 20 && useHardwareLayer) {
            container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        currentSheetAnimationType = 1;
        currentSheetAnimation = new AnimatorSet();
        currentSheetAnimation.playTogether(
                ObjectAnimator.ofInt(backDrawable, AnimationProperties.COLOR_DRAWABLE_ALPHA, dimBehind ? dimBehindAlpha : 0)
        );
        currentSheetAnimation.setDuration(400);
        currentSheetAnimation.setStartDelay(20);
        currentSheetAnimation.setInterpolator(openInterpolator);
        AnimationNotificationsLocker locker = new AnimationNotificationsLocker();
        BottomSheetDelegateInterface delegate = super.delegate;
        final Runnable onAnimationEnd = () -> {
            currentSheetAnimation = null;
            appearSpringAnimation = null;
            locker.unlock();
            currentSheetAnimationType = 0;
            if (delegate != null) {
                delegate.onOpenAnimationEnd();
            }
            if (useHardwareLayer) {
                container.setLayerType(View.LAYER_TYPE_NONE, null);
            }

            if (isFullscreen) {
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
                getWindow().setAttributes(params);
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
        };
        appearSpringAnimation.addEndListener((animation, cancelled, value, velocity) -> {
            if (currentSheetAnimation != null && !currentSheetAnimation.isRunning()) {
                onAnimationEnd.run();
            }
        });
        currentSheetAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    if (appearSpringAnimation != null && !appearSpringAnimation.isRunning()) {
                        onAnimationEnd.run();
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    currentSheetAnimation = null;
                    currentSheetAnimationType = 0;
                }
            }
        });
        locker.lock();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
        currentSheetAnimation.start();

//        AndroidUtilities.runOnUIThread(() -> {
//            if (currentSheetAnimation != null) {
//                // closes keyboard so navigation bar buttons can be accessible
//                ChatAttachAlert.this.delegate.needEnterComment();
//            }
//        }, 75);
        ValueAnimator navigationBarAnimator = ValueAnimator.ofFloat(0, 1);
        setNavBarAlpha(0);
        navigationBarAnimator.addUpdateListener(a -> {
            setNavBarAlpha((float) a.getAnimatedValue());
        });
        navigationBarAnimator.setStartDelay(25);
        navigationBarAnimator.setDuration(200);
        navigationBarAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        navigationBarAnimator.start();

        return true;
    }

    private void setNavBarAlpha(float alpha) {
        navBarColor = ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundGray), Math.min(255, Math.max(0, (int) (255 * alpha))));
        AndroidUtilities.setNavigationBarColor(getWindow(), navBarColor, false);
        AndroidUtilities.setLightNavigationBar(getWindow(), AndroidUtilities.computePerceivedBrightness(navBarColor) > 0.721);
        getContainer().invalidate();
    }

    @Override
    protected boolean onContainerTouchEvent(MotionEvent event) {
        return currentAttachLayout.onContainerViewTouchEvent(event);
    }

    public void makeFocusable(EditTextBoldCursor editText, boolean showKeyboard) {
        if (delegate == null) {
            return;
        }
        if (!enterCommentEventSent) {
            boolean keyboardVisible = delegate.needEnterComment();
            enterCommentEventSent = true;
            AndroidUtilities.runOnUIThread(() -> {
                setFocusable(true);
                editText.requestFocus();
                if (showKeyboard) {
                    AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText));
                }
            }, keyboardVisible ? 200 : 0);
        }
    }

    private void applyAttachButtonColors(View view) {
        if (view instanceof AttachButton) {
            AttachButton button = (AttachButton) view;
            button.textView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextGray2), getThemedColor(button.textKey), button.checkedState));
        } else if (view instanceof AttachBotButton) {
            AttachBotButton button = (AttachBotButton) view;
            button.nameTextView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextGray2), button.textColor, button.checkedState));
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        for (int a = 0; a < layouts.length; a++) {
            if (layouts[a] != null) {
                ArrayList<ThemeDescription> arrayList = layouts[a].getThemeDescriptions();
                if (arrayList != null) {
                    descriptions.addAll(arrayList);
                }
            }
        }
        descriptions.add(new ThemeDescription(container, 0, null, null, null, null, Theme.key_dialogBackgroundGray));
        return descriptions;
    }

    public void checkColors() {
        if (buttonsRecyclerView == null) {
            return;
        }
        int count = buttonsRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            applyAttachButtonColors(buttonsRecyclerView.getChildAt(a));
        }
        selectedTextView.setTextColor(forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItems) : getThemedColor(Theme.key_dialogTextBlack));
        mediaPreviewTextView.setTextColor(forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItems) : getThemedColor(Theme.key_dialogTextBlack));

        doneItem.getTextView().setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));

        selectedMenuItem.setIconColor(forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItems) : getThemedColor(Theme.key_dialogTextBlack));
        Theme.setDrawableColor(selectedMenuItem.getBackground(), forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItemsSelector) : getThemedColor(Theme.key_dialogButtonSelector));
        selectedMenuItem.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), false);
        selectedMenuItem.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), true);
        selectedMenuItem.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        if (searchItem != null) {
            searchItem.setIconColor(forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItems) : getThemedColor(Theme.key_dialogTextBlack));
            Theme.setDrawableColor(searchItem.getBackground(), forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItemsSelector) : getThemedColor(Theme.key_dialogButtonSelector));
        }

        commentTextView.updateColors();

        actionBarShadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));

        buttonsRecyclerView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        buttonsRecyclerView.setBackgroundColor(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground));

        captionContainer.setBackgroundColor(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground));
        topCommentContainer.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));

        actionBar.setBackgroundColor(forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBar) : getThemedColor(Theme.key_dialogBackground));
        actionBar.setItemsColor(forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItems) : getThemedColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItemsSelector) : getThemedColor(Theme.key_dialogButtonSelector), false);
        actionBar.setTitleColor(forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItems) : getThemedColor(Theme.key_dialogTextBlack));

        Theme.setDrawableColor(shadowDrawable, getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground));

        containerView.invalidate();

        for (int a = 0; a < layouts.length; a++) {
            if (layouts[a] != null) {
                layouts[a].checkColors();
            }
        }

        if (Build.VERSION.SDK_INT >= 30) {
            navBarColorKey = -1;
            navBarColor = getThemedColor(Theme.key_dialogBackgroundGray);
            AndroidUtilities.setNavigationBarColor(getWindow(), getThemedColor(Theme.key_dialogBackground), false);
            AndroidUtilities.setLightNavigationBar(getWindow(), AndroidUtilities.computePerceivedBrightness(navBarColor) > 0.721);
        } else {
            fixNavigationBar(getThemedColor(Theme.key_dialogBackground));
        }
    }

    @Override
    protected boolean onCustomMeasure(View view, int width, int height) {
        if (photoLayout.onCustomMeasure(view, width, height)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
        if (photoLayout.onCustomLayout(view, left, top, right, bottom)) {
            return true;
        }
        return false;
    }

    public void onPause() {
        for (int a = 0; a < layouts.length; a++) {
            if (layouts[a] != null) {
                layouts[a].onPause();
            }
        }
        paused = true;
    }

    public void onResume() {
        paused = false;
        for (int a = 0; a < layouts.length; a++) {
            if (layouts[a] != null) {
                layouts[a].onResume();
            }
        }
        if (isShowing()) {
            delegate.needEnterComment();
        }
    }

    public void onActivityResultFragment(int requestCode, Intent data, String currentPicturePath) {
        photoLayout.onActivityResultFragment(requestCode, data, currentPicturePath);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.reloadInlineHints || id == NotificationCenter.attachMenuBotsDidLoad || id == NotificationCenter.quickRepliesUpdated) {
            if (buttonsAdapter != null) {
                buttonsAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            currentLimit = MessagesController.getInstance(UserConfig.selectedAccount).getCaptionMaxLengthLimit();
        }
    }

    private int getScrollOffsetY(int idx) {
        if (nextAttachLayout != null && (currentAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview || nextAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview)) {
            return AndroidUtilities.lerp(scrollOffsetY[0], scrollOffsetY[1], translationProgress);
        } else {
            return scrollOffsetY[idx];
        }
    }

    private void updateSelectedPosition(int idx) {
        float moveProgress;
        AttachAlertLayout layout = idx == 0 ? currentAttachLayout : nextAttachLayout;
        if (layout == null || layout.getVisibility() != View.VISIBLE) {
            return;
        }
        int scrollOffset = getScrollOffsetY(idx);
        int t = scrollOffset - backgroundPaddingTop;
        float toMove;
        if (layout == pollLayout) {
            t -= dp(13);
            toMove = dp(11);
        } else {
            t -= dp(39);
            toMove = dp(43);
        }
        if (t + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
            moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - t - backgroundPaddingTop) / toMove);
            cornerRadius = 1.0f - moveProgress;
        } else {
            moveProgress = 0.0f;
            cornerRadius = 1.0f;
        }

        int finalMove;
        if (AndroidUtilities.isTablet()) {
            finalMove = 16;
        } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            finalMove = 6;
        } else {
            finalMove = 12;
        }

        float offset = actionBar.getAlpha() != 0 ? 0.0f : dp(26 * (1.0f - headerView.getAlpha()));
        if (menuShowed && avatarPicker == 0 && !storyMediaPicker) {
            selectedMenuItem.setTranslationY(Math.max(ActionBar.getCurrentActionBarHeight() - dp(4) - dp(37 + finalMove), scrollOffset - dp(37 + finalMove * moveProgress) + offset - topCommentContainer.getMeasuredHeight() * topCommentContainer.getAlpha()) + currentPanTranslationY);
        } else {
            selectedMenuItem.setTranslationY(ActionBar.getCurrentActionBarHeight() - dp(4) - dp(37 + finalMove) + currentPanTranslationY);
        }
        float swapOffset = 0;
        if (isPhotoPicker && openTransitionFinished) {
            if (nextAttachLayout != null && currentAttachLayout != null) {
                swapOffset = Math.min(nextAttachLayout.getTranslationY(), currentAttachLayout.getTranslationY());
            } else if (nextAttachLayout != null) {
                swapOffset = nextAttachLayout.getTranslationY();
            }
        }
        if (searchItem != null) {
            searchItem.setTranslationY(ActionBar.getCurrentActionBarHeight() - dp(4) - dp(37 + finalMove) + currentPanTranslationY);
        }
        baseSelectedTextViewTranslationY = scrollOffset - dp(25 + finalMove * moveProgress) + offset + currentPanTranslationY + swapOffset - topCommentContainer.getMeasuredHeight() * topCommentContainer.getAlpha();
        headerView.setTranslationY(Math.max(currentPanTranslationY, baseSelectedTextViewTranslationY));
        topCommentContainer.setTranslationY(Math.max(ActionBar.getCurrentActionBarHeight() + currentPanTranslationY, baseSelectedTextViewTranslationY + dp(26) * headerView.getAlpha() + dp(8)));
        if (captionAbove) {
            updateCommentTextViewPosition();
        }
        if (pollLayout != null && layout == pollLayout) {
            if (AndroidUtilities.isTablet()) {
                finalMove = 63;
            } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                finalMove = 53;
            } else {
                finalMove = 59;
            }
            doneItem.setTranslationY(Math.max(0, pollLayout.getTranslationY() + scrollOffset - dp(7 + finalMove * moveProgress)) + currentPanTranslationY);
        }
    }

    private void updateActionBarVisibility(boolean show, boolean animated) {
        if (show && actionBar.getTag() == null || !show && actionBar.getTag() != null) {
            actionBar.setTag(show ? 1 : null);
            if (actionBarAnimation != null) {
                actionBarAnimation.cancel();
                actionBarAnimation = null;
            }

            boolean needsSearchItem = searchItem != null && false && (avatarSearch || currentAttachLayout == photoLayout && !menuShowed && baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).allowSendGifs() && ((ChatActivity) baseFragment).allowSendPhotos());
            boolean needMoreItem = !isPhotoPicker && !storyMediaPicker && (avatarPicker != 0 || !menuShowed) && currentAttachLayout == photoLayout && (photosEnabled || videosEnabled);
            if (currentAttachLayout == restrictedLayout) {
                needsSearchItem = false;
                needMoreItem = false;
            }
            if (show) {
                if (needsSearchItem) {
                    searchItem.setVisibility(View.VISIBLE);
                }
                if (needMoreItem) {
                    selectedMenuItem.setVisibility(View.VISIBLE);
                    selectedMenuItem.setClickable(true);
                }
            } else if (typeButtonsAvailable && frameLayout2.getTag() == null) {
                buttonsRecyclerView.setVisibility(View.VISIBLE);
            }

            if (getWindow() != null && baseFragment != null) {
                if (show) {
                    AndroidUtilities.setLightStatusBar(getWindow(), isLightStatusBar());
                } else {
                    AndroidUtilities.setLightStatusBar(getWindow(), baseFragment.isLightStatusBar());
                }
            }
            if (animated) {
                actionBarAnimation = new AnimatorSet();
                actionBarAnimation.setDuration((long) (180 * Math.abs((show ? 1.0f : 0.0f) - actionBar.getAlpha())));
                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f));
                animators.add(ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, show ? 1.0f : 0.0f));
                if (needsSearchItem) {
                    animators.add(ObjectAnimator.ofFloat(searchItem, View.ALPHA, show ? 1.0f : 0.0f));
                }
                if (needMoreItem) {
                    animators.add(ObjectAnimator.ofFloat(selectedMenuItem, View.ALPHA, show ? 1.0f : 0.0f));
                }
                actionBarAnimation.playTogether(animators);
                actionBarAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (actionBarAnimation != null) {
                            if (show) {
                                if (typeButtonsAvailable && (currentAttachLayout == null || currentAttachLayout.shouldHideBottomButtons())) {
                                    buttonsRecyclerView.setVisibility(View.INVISIBLE);
                                }
                            } else {
                                if (searchItem != null) {
                                    searchItem.setVisibility(View.INVISIBLE);
                                }
                                if (avatarPicker != 0 || !menuShowed) {
                                    selectedMenuItem.setVisibility(View.INVISIBLE);
                                }
                            }
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        actionBarAnimation = null;
                    }
                });
                actionBarAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                actionBarAnimation.setDuration(380);
                actionBarAnimation.start();
            } else {
                if (show) {
                    if (typeButtonsAvailable && (currentAttachLayout == null || currentAttachLayout.shouldHideBottomButtons())) {
                        buttonsRecyclerView.setVisibility(View.INVISIBLE);
                    }
                }
                actionBar.setAlpha(show ? 1.0f : 0.0f);
                actionBarShadow.setAlpha(show ? 1.0f : 0.0f);
                if (needsSearchItem) {
                    searchItem.setAlpha(show ? 1.0f : 0.0f);
                }
                if (needMoreItem) {
                    selectedMenuItem.setAlpha(show ? 1.0f : 0.0f);
                    selectedMenuItem.setScaleX(show ? 1.0f : 0.6f);
                    selectedMenuItem.setScaleY(show ? 1.0f : 0.6f);
                }
                if (!show) {
                    if (searchItem != null) {
                        searchItem.setVisibility(View.INVISIBLE);
                    }
                    if (avatarPicker != 0 || !menuShowed) {
                        selectedMenuItem.setVisibility(View.INVISIBLE);
                    }
                }
            }
        }
    }

    @SuppressLint("NewApi")
    public void updateLayout(AttachAlertLayout layout, boolean animated, int dy) {
        if (layout == null) {
            return;
        }
        int newOffset = layout.getCurrentItemTop();
        if (newOffset == Integer.MAX_VALUE) {
            return;
        }
        boolean show = layout == currentAttachLayout && newOffset <= layout.getButtonsHideOffset();
        pinnedToTop = show;
        if (currentAttachLayout != photoPreviewLayout && keyboardVisible && animated && !(currentAttachLayout instanceof ChatAttachAlertBotWebViewLayout)) {
            animated = false;
        }
        if (layout == currentAttachLayout) {
            updateActionBarVisibility(show, true);
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) layout.getLayoutParams();
        newOffset += (layoutParams == null ? 0 : layoutParams.topMargin) - dp(11);
        int idx = currentAttachLayout == layout ? 0 : 1;
        boolean previewAnimationIsRunning = (currentAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview || nextAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview) && (viewChangeAnimator instanceof SpringAnimation && ((SpringAnimation) viewChangeAnimator).isRunning());
        if (scrollOffsetY[idx] != newOffset || previewAnimationIsRunning) {
            previousScrollOffsetY = scrollOffsetY[idx];
            scrollOffsetY[idx] = newOffset;
            updateSelectedPosition(idx);
            containerView.invalidate();
        } else if (dy != 0) {
            previousScrollOffsetY = scrollOffsetY[idx];
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public void updateCountButton(int animated) {
        if (viewChangeAnimator != null) {
            return;
        }
        int count = currentAttachLayout.getSelectedItemsCount();

        if (count == 0) {
            writeButton.setCount(0, animated != 0);
            showCommentTextView(false, animated != 0);
        } else {
            if (!showCommentTextView(true, animated != 0) && animated != 0) {
                writeButton.setCount(count, true);
                writeButton.bounceCount();
            } else {
                writeButton.setCount(count, animated != 0);
                writeButton.bounceCount();
            }
        }
        currentAttachLayout.onSelectedItemsCountChanged(count);

//        if (editingMessageObject != null) {
//            menuShowed = count > 0 && currentAttachLayout == photoLayout;
//            selectedTextView.setText(LocaleController.getString(R.string.ChoosePhotoOrVideo));
//            headerView.setAlpha(currentAttachLayout == photoLayout ? 1f : 0f);
//            headerView.setVisibility(currentAttachLayout == photoLayout ? View.VISIBLE : View.INVISIBLE);
//            selectedMenuItem.setVisibility(View.VISIBLE);
//            selectedMenuItem.setClickable(count > 0);
//            selectedMenuItem.animate().alpha(count > 0 ? 1f : 0f).scaleX(count > 0 ? 1f : .6f).scaleY(count > 0 ? 1f : .6f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(320).start();
//        } else
        if (currentAttachLayout == photoLayout && ((baseFragment instanceof ChatActivity) || avatarPicker != 0 || storyMediaPicker) && (count == 0 && menuShowed || (count != 0 || avatarPicker != 0 || storyMediaPicker) && !menuShowed)) {
            menuShowed = count != 0 || avatarPicker != 0 || storyMediaPicker;
            if (menuAnimator != null) {
                menuAnimator.cancel();
                menuAnimator = null;
            }
            boolean needsSearchItem = avatarPicker != 0 && searchItem != null && actionBar.getTag() != null && baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).allowSendGifs();
            if (menuShowed) {
                if (avatarPicker == 0 && !storyMediaPicker) {
                    selectedMenuItem.setVisibility(View.VISIBLE);
                    selectedMenuItem.setClickable(true);
                }
                headerView.setVisibility(View.VISIBLE);
            } else {
                if (actionBar.getTag() != null && searchItem != null) {
                    searchItem.setVisibility(View.VISIBLE);
                }
            }
            if (animated == 0) {
                if (actionBar.getTag() == null && avatarPicker == 0 && !storyMediaPicker) {
                    selectedMenuItem.setAlpha(menuShowed ? 1.0f : 0.0f);
                    selectedMenuItem.setScaleX(menuShowed ? 1.0f : 0.6f);
                    selectedMenuItem.setScaleY(menuShowed ? 1.0f : 0.6f);
                }
                headerView.setAlpha(menuShowed ? 1.0f : 0.0f);
                if (needsSearchItem) {
                    searchItem.setAlpha(menuShowed ? 0.0f : 1.0f);
                }
                if (menuShowed && searchItem != null) {
                    searchItem.setVisibility(View.INVISIBLE);
                }
            } else {
                menuAnimator = new AnimatorSet();
                ArrayList<Animator> animators = new ArrayList<>();
                if (actionBar.getTag() == null && avatarPicker == 0 && !storyMediaPicker) {
                    animators.add(ObjectAnimator.ofFloat(selectedMenuItem, View.ALPHA, menuShowed ? 1.0f : 0.0f));
                    animators.add(ObjectAnimator.ofFloat(selectedMenuItem, View.SCALE_X, menuShowed ? 1.0f : 0.6f));
                    animators.add(ObjectAnimator.ofFloat(selectedMenuItem, View.SCALE_Y, menuShowed ? 1.0f : 0.6f));
                }
                animators.add(ObjectAnimator.ofFloat(headerView, View.ALPHA, menuShowed ? 1.0f : 0.0f));
                if (needsSearchItem) {
                    animators.add(ObjectAnimator.ofFloat(searchItem, View.ALPHA, menuShowed ? 0.0f : 1.0f));
                }
                menuAnimator.playTogether(animators);
                menuAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        menuAnimator = null;
                        if (!menuShowed) {
                            if (actionBar.getTag() == null && avatarPicker == 0 && !storyMediaPicker) {
                                selectedMenuItem.setVisibility(View.INVISIBLE);
                            }
                            headerView.setVisibility(View.INVISIBLE);
                        } else if (searchItem != null) {
                            searchItem.setVisibility(View.INVISIBLE);
                        }
                    }
                });
                menuAnimator.setDuration(180);
                menuAnimator.start();
            }
        }
    }

    public void setDelegate(ChatAttachViewDelegate chatAttachViewDelegate) {
        delegate = chatAttachViewDelegate;
    }

    public void init() {
        botButtonWasVisible = false;
        botButtonProgressWasVisible = false;
        botMainButtonOffsetY = 0;
        botMainButtonTextView.setVisibility(View.GONE);
        botProgressView.setAlpha(0f);
        botProgressView.setScaleX(0.1f);
        botProgressView.setScaleY(0.1f);
        botProgressView.setVisibility(View.GONE);
        buttonsRecyclerView.setAlpha(1f);
        buttonsRecyclerView.setTranslationY(0);
        for (int i = 0; i < botAttachLayouts.size(); i++) {
            botAttachLayouts.valueAt(i).setMeasureOffsetY(0);
        }
        shadow.setAlpha(1f);
        shadow.setTranslationY(0);

        TLRPC.Chat chat = null;
        TLRPC.User user = null;
        if (avatarPicker != 2) {
            if (baseFragment instanceof ChatActivity) {
                chat = ((ChatActivity) baseFragment).getCurrentChat();
                user = ((ChatActivity) baseFragment).getCurrentUser();
            } else if (dialogId >= 0) {
                user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            } else if (dialogId < 0) {
                chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            }
        }

        if (baseFragment instanceof ChatActivity && avatarPicker != 2 || (chat != null || user != null)) {
            if (chat != null) {
                // mediaEnabled = ChatObject.canSendMedia(chat);
                photosEnabled = ChatObject.canSendPhoto(chat);
                videosEnabled = ChatObject.canSendVideo(chat);
                musicEnabled = ChatObject.canSendMusic(chat);
                pollsEnabled = ChatObject.canSendPolls(chat);
                plainTextEnabled = ChatObject.canSendPlain(chat);
                documentsEnabled = ChatObject.canSendDocument(chat);
            } else {
                pollsEnabled = user != null && user.bot;
            }
        }
        if (!(baseFragment instanceof ChatActivity && avatarPicker != 2)) {
            commentTextView.setVisibility(allowEnterCaption ? View.VISIBLE : View.INVISIBLE);
        }
        photoLayout.onInit(videosEnabled, photosEnabled, documentsEnabled);
        commentTextView.hidePopup(true);
        topCommentTextView.hidePopup(true);
        enterCommentEventSent = false;
        setFocusable(false);
        ChatAttachAlert.AttachAlertLayout layoutToSet;
        if (isStoryLocationPicker || isBizLocationPicker) {
            if (locationLayout == null) {
                layouts[5] = locationLayout = new ChatAttachAlertLocationLayout(this, getContext(), resourcesProvider);
                locationLayout.setDelegate((location, live, notify, scheduleDate) -> ((ChatActivity) baseFragment).didSelectLocation(location, live, notify, scheduleDate));
            }
            selectedId = 5;
            layoutToSet = locationLayout;
        } else if (isStoryAudioPicker) {
            openAudioLayout(false);
            layoutToSet = audioLayout;
            selectedId = 3;
        } else if (isSoundPicker) {
            openDocumentsLayout(false);
            layoutToSet = documentLayout;
            selectedId = 4;
        } else if (editingMessageObject != null) {
            if (editType == EDITMEDIA_TYPE_ANY) {
                typeButtonsAvailable = true;
                if (editingMessageObject.isMusic()) {
                    openAudioLayout(false);
                    layoutToSet = audioLayout;
                    selectedId = 3;
                } else if (editingMessageObject.isDocument()) {
                    openDocumentsLayout(false);
                    layoutToSet = documentLayout;
                    selectedId = 4;
                } else {
                    layoutToSet = photoLayout;
                    selectedId = 1;
                }
            } else {
                if (editType == EDITMEDIA_TYPE_MUSIC) {
                    openAudioLayout(false);
                    layoutToSet = audioLayout;
                    selectedId = 3;
                } else if (editType == EDITMEDIA_TYPE_FILE) {
                    openDocumentsLayout(false);
                    layoutToSet = documentLayout;
                    selectedId = 4;
                } else {
                    layoutToSet = photoLayout;
                    selectedId = 1;
                }
                typeButtonsAvailable = false;
            }
        } else {
            layoutToSet = photoLayout;
            typeButtonsAvailable = avatarPicker == 0 && !storyMediaPicker;
            selectedId = 1;
        }
        buttonsRecyclerView.setVisibility(typeButtonsAvailable ? View.VISIBLE : View.GONE);
        shadow.setVisibility(typeButtonsAvailable ? View.VISIBLE : View.INVISIBLE);
        if (currentAttachLayout != layoutToSet) {
            if (actionBar.isSearchFieldVisible()) {
                actionBar.closeSearchField();
            }
            containerView.removeView(currentAttachLayout);
            currentAttachLayout.onHide();
            currentAttachLayout.setVisibility(View.GONE);
            currentAttachLayout.onHidden();
            currentAttachLayout = layoutToSet;
            setAllowNestedScroll(true);
            if (currentAttachLayout.getParent() == null) {
                containerView.addView(currentAttachLayout, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            }
            layoutToSet.setAlpha(1.0f);
            layoutToSet.setVisibility(View.VISIBLE);
            layoutToSet.onShow(null);
            layoutToSet.onShown();
            actionBar.setVisibility(layoutToSet.needsActionBar() != 0 ? View.VISIBLE : View.INVISIBLE);
            actionBarShadow.setVisibility(actionBar.getVisibility());

            setCaptionAbove(captionAbove, false);
        }
        if (currentAttachLayout != photoLayout) {
            photoLayout.setCheckCameraWhenShown(true);
        }
        updateCountButton(0);

        buttonsAdapter.notifyDataSetChanged();
        getCommentView().setText("");
        buttonsLayoutManager.scrollToPositionWithOffset(0, 1000000);
    }

    public void onDestroy() {
        for (int a = 0; a < layouts.length; a++) {
            if (layouts[a] != null) {
                layouts[a].onDestroy();
            }
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.reloadInlineHints);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.attachMenuBotsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.quickRepliesUpdated);
        destroyed = true;
        if (commentTextView != null) {
            commentTextView.onDestroy();
        }
        if (topCommentTextView != null) {
            topCommentTextView.onDestroy();
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        MediaController.AlbumEntry albumEntry;
        if (baseFragment instanceof ChatActivity) {
            albumEntry = MediaController.allMediaAlbumEntry;
        } else {
            albumEntry = MediaController.allPhotosAlbumEntry;
        }
        if (Build.VERSION.SDK_INT <= 19 && albumEntry == null) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
        currentAttachLayout.onOpenAnimationEnd();
        AndroidUtilities.makeAccessibilityAnnouncement(getString("AccDescrAttachButton", R.string.AccDescrAttachButton));
        openTransitionFinished = true;
        if (!videosEnabled && !photosEnabled) {
            checkCanRemoveRestrictionsByBoosts();
        }
    }

    @Override
    public void onOpenAnimationStart() {
        sent = false;
    }

    @Override
    public boolean canDismiss() {
        return true;
    }

    private boolean allowDrawContent = true;
    public boolean sent = false;
    @Override
    public void setAllowDrawContent(boolean value) {
        super.setAllowDrawContent(value);
        currentAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
        if (allowDrawContent != value) {
            allowDrawContent = value;
            if (currentAttachLayout == photoLayout && photoLayout != null && !photoLayout.cameraExpanded) {
                photoLayout.pauseCamera(!allowDrawContent || sent);
            }
        }
    }

    public void setStories(boolean value) {
        stories = value;
    }

    public void setAvatarPicker(int type, boolean search) {
        avatarPicker = type;
        avatarSearch = search;
        if (avatarPicker != 0) {
            typeButtonsAvailable = false;
            if (currentAttachLayout == null || currentAttachLayout == photoLayout) {
                buttonsRecyclerView.setVisibility(View.GONE);
                shadow.setVisibility(View.GONE);
            }
            if (avatarPicker == 2) {
                selectedTextView.setText(getString(R.string.ChoosePhotoOrVideo));
            } else {
                selectedTextView.setText(getString(R.string.ChoosePhoto));
            }
        } else {
            typeButtonsAvailable = true;
        }
        if (photoLayout != null) {
            photoLayout.updateAvatarPicker();
        }
    }

    public void setStoryMediaPicker() {
        storyMediaPicker = true;
        typeButtonsAvailable = false;
        selectedTextView.setText(getString(R.string.ChoosePhotoOrVideo));
    }

    public void enableStickerMode(Utilities.Callback2<String, TLRPC.InputDocument> customStickerHandler) {
        selectedTextView.setText(getString(R.string.ChoosePhotoForSticker));
        typeButtonsAvailable = false;
        buttonsRecyclerView.setVisibility(View.GONE);
        shadow.setVisibility(View.GONE);
        avatarPicker = 1;
        isPhotoPicker = true;
        isStickerMode = true;
        this.customStickerHandler = customStickerHandler;
        if (optionsItem != null) {
            selectedTextView.setTranslationY(-dp(8));
            optionsItem.setVisibility(View.VISIBLE);
            optionsItem.setClickable(true);
            optionsItem.setAlpha(1f);
            optionsItem.setScaleX(1f);
            optionsItem.setScaleY(1f);
        }
    }

    public void enableDefaultMode() {
        typeButtonsAvailable = true;
        buttonsRecyclerView.setVisibility(View.VISIBLE);
        shadow.setVisibility(View.VISIBLE);
        avatarPicker = 0;
        isPhotoPicker = false;
        isStickerMode = false;
        customStickerHandler = null;
        if (optionsItem != null) {
            selectedTextView.setTranslationY(0);
            optionsItem.setVisibility(View.GONE);
        }
    }

    public TextView getSelectedTextView() {
        return selectedTextView;
    }

    public void setSoundPicker() {
        isSoundPicker = true;
        buttonsRecyclerView.setVisibility(View.GONE);
        shadow.setVisibility(View.GONE);
        selectedTextView.setText(getString(R.string.ChoosePhotoOrVideo));
    }

    public boolean storyLocationPickerFileIsVideo;
    public File storyLocationPickerPhotoFile;
    public double[] storyLocationPickerLatLong;

    public void setBusinessLocationPicker() {
        isBizLocationPicker = true;
        buttonsRecyclerView.setVisibility(View.GONE);
        shadow.setVisibility(View.GONE);
    }

    public void setStoryLocationPicker() {
        isStoryLocationPicker = true;
        buttonsRecyclerView.setVisibility(View.GONE);
        shadow.setVisibility(View.GONE);
    }

    public void setStoryLocationPicker(boolean isVideo, File photo) {
        storyLocationPickerFileIsVideo = isVideo;
        storyLocationPickerPhotoFile = photo;
        isStoryLocationPicker = true;
        buttonsRecyclerView.setVisibility(View.GONE);
        shadow.setVisibility(View.GONE);
    }

    public void setStoryLocationPicker(double lat, double lon) {
        storyLocationPickerLatLong = new double[]{lat, lon};
        isStoryLocationPicker = true;
        buttonsRecyclerView.setVisibility(View.GONE);
        shadow.setVisibility(View.GONE);
    }

    public void setStoryAudioPicker() {
        isStoryAudioPicker = true;
    }

    public void setMaxSelectedPhotos(int value, boolean order) {
        if (editingMessageObject != null) {
            return;
        }
        maxSelectedPhotos = value;
        allowOrder = order;
    }

    public void setOpenWithFrontFaceCamera(boolean value) {
        openWithFrontFaceCamera = value;
    }

    public ChatAttachAlertPhotoLayout getPhotoLayout() {
        return photoLayout;
    }

    public ChatAttachAlertLocationLayout getLocationLayout() {
        return locationLayout;
    }

    private class ButtonsAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_BUTTON = 0, VIEW_TYPE_BOT_BUTTON = 1;

        private Context mContext;
        private int galleryButton;

        private int attachBotsStartRow;
        private int attachBotsEndRow;
        private List<TLRPC.TL_attachMenuBot> attachMenuBots = new ArrayList<>();

        private int documentButton;
        private int musicButton;
        private int pollButton;
        private int contactButton;
        private int quickRepliesButton;
        private int locationButton;
        private int buttonsCount;

        public ButtonsAdapter(Context context) {
            mContext = context;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_BUTTON:
                    view = new AttachButton(mContext);
                    break;
                case VIEW_TYPE_BOT_BUTTON:
                default:
                    view = new AttachBotButton(mContext);
                    break;
            }
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            view.setFocusable(true);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_BUTTON:
                    AttachButton attachButton = (AttachButton) holder.itemView;
                    if (position == galleryButton) {
                        attachButton.setTextAndIcon(1, getString("ChatGallery", R.string.ChatGallery), Theme.chat_attachButtonDrawables[0], Theme.key_chat_attachGalleryBackground, Theme.key_chat_attachGalleryText);
                        attachButton.setTag(1);
                    } else if (position == documentButton) {
                        attachButton.setTextAndIcon(4, getString("ChatDocument", R.string.ChatDocument), Theme.chat_attachButtonDrawables[2], Theme.key_chat_attachFileBackground, Theme.key_chat_attachFileText);
                        attachButton.setTag(4);
                    } else if (position == locationButton) {
                        attachButton.setTextAndIcon(6, getString("ChatLocation", R.string.ChatLocation), Theme.chat_attachButtonDrawables[4], Theme.key_chat_attachLocationBackground, Theme.key_chat_attachLocationText);
                        attachButton.setTag(6);
                    } else if (position == musicButton) {
                        attachButton.setTextAndIcon(3, getString("AttachMusic", R.string.AttachMusic), Theme.chat_attachButtonDrawables[1], Theme.key_chat_attachAudioBackground, Theme.key_chat_attachAudioText);
                        attachButton.setTag(3);
                    } else if (position == pollButton) {
                        attachButton.setTextAndIcon(9, getString("Poll", R.string.Poll), Theme.chat_attachButtonDrawables[5], Theme.key_chat_attachPollBackground, Theme.key_chat_attachPollText);
                        attachButton.setTag(9);
                    } else if (position == contactButton) {
                        attachButton.setTextAndIcon(5, getString("AttachContact", R.string.AttachContact), Theme.chat_attachButtonDrawables[3], Theme.key_chat_attachContactBackground, Theme.key_chat_attachContactText);
                        attachButton.setTag(5);
                    } else if (position == quickRepliesButton) {
                        attachButton.setTextAndIcon(11, getString(R.string.AttachQuickReplies), getContext().getResources().getDrawable(R.drawable.ic_ab_reply).mutate(), Theme.key_chat_attachContactBackground, Theme.key_chat_attachContactText);
                        attachButton.setTag(11);
                    }
                    break;
                case VIEW_TYPE_BOT_BUTTON:
                    AttachBotButton child = (AttachBotButton) holder.itemView;
                    if (position >= attachBotsStartRow && position < attachBotsEndRow) {
                        position -= attachBotsStartRow;
                        child.setTag(position);
                        TLRPC.TL_attachMenuBot bot = attachMenuBots.get(position);
                        child.setAttachBot(MessagesController.getInstance(currentAccount).getUser(bot.bot_id), bot);
                        break;
                    }

                    position -= buttonsCount;
                    child.setTag(position);
                    child.setUser(MessagesController.getInstance(currentAccount).getUser(MediaDataController.getInstance(currentAccount).inlineBots.get(position).peer.user_id));
                    break;
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            applyAttachButtonColors(holder.itemView);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            int count = buttonsCount;
            if (editingMessageObject == null && baseFragment instanceof ChatActivity) {
                count += MediaDataController.getInstance(currentAccount).inlineBots.size();
            }
            return count;
        }

        @Override
        public void notifyDataSetChanged() {
            buttonsCount = 0;
            galleryButton = -1;
            documentButton = -1;
            musicButton = -1;
            pollButton = -1;
            contactButton = -1;
            quickRepliesButton = -1;
            locationButton = -1;
            attachBotsStartRow = -1;
            attachBotsEndRow = -1;
            if (!(baseFragment instanceof ChatActivity)) {
                galleryButton = buttonsCount++;
                documentButton = buttonsCount++;
                if (allowEnterCaption) {
                    musicButton = buttonsCount++;
                }
            } else if (editingMessageObject != null) {
                if (editType == EDITMEDIA_TYPE_ANY) {
                    galleryButton = buttonsCount++;
                    documentButton = buttonsCount++;
                    musicButton = buttonsCount++;
                } else {
                    if (editType == EDITMEDIA_TYPE_PHOTOVIDEO) {
                        galleryButton = buttonsCount++;
                    }
                    if (editType == EDITMEDIA_TYPE_FILE) {
                        documentButton = buttonsCount++;
                    }
                    if (editType == EDITMEDIA_TYPE_MUSIC) {
                        musicButton = buttonsCount++;
                    }
                }
            } else {
                galleryButton = buttonsCount++;
                if (photosEnabled || videosEnabled) {
                    if (baseFragment instanceof ChatActivity && !((ChatActivity) baseFragment).isInScheduleMode() && !((ChatActivity) baseFragment).isSecretChat() && ((ChatActivity) baseFragment).getChatMode() != ChatActivity.MODE_QUICK_REPLIES) {
                        ChatActivity chatActivity = (ChatActivity) baseFragment;

                        attachBotsStartRow = buttonsCount;
                        attachMenuBots.clear();
                        for (TLRPC.TL_attachMenuBot bot : MediaDataController.getInstance(currentAccount).getAttachMenuBots().bots) {
                            if (bot.show_in_attach_menu && MediaDataController.canShowAttachMenuBot(bot, chatActivity.getCurrentChat() != null ? chatActivity.getCurrentChat() : chatActivity.getCurrentUser())) {
                                attachMenuBots.add(bot);
                            }
                        }

                        buttonsCount += attachMenuBots.size();
                        attachBotsEndRow = buttonsCount;
                    }
                }
                documentButton = buttonsCount++;

                if (plainTextEnabled) {
                    locationButton = buttonsCount++;
                }

                if (pollsEnabled) {
                    pollButton = buttonsCount++;
                }
                if (plainTextEnabled) {
                    contactButton = buttonsCount++;
                }
                TLRPC.User user = baseFragment instanceof ChatActivity ? ((ChatActivity) baseFragment).getCurrentUser() : null;
                if (baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).getChatMode() == 0 && user != null && !user.bot && QuickRepliesController.getInstance(currentAccount).hasReplies()) {
                    quickRepliesButton = buttonsCount++;
                }
                musicButton = buttonsCount++;
            }
            super.notifyDataSetChanged();
        }

        public int getButtonsCount() {
            return buttonsCount;
        }

        @Override
        public int getItemViewType(int position) {
            if (position < buttonsCount) {
                if (position >= attachBotsStartRow && position < attachBotsEndRow) {
                    return VIEW_TYPE_BOT_BUTTON;
                }
                return VIEW_TYPE_BUTTON;
            }
            return VIEW_TYPE_BOT_BUTTON;
        }
    }

    @Override
    public void dismissInternal() {
        if (delegate != null) {
            delegate.doOnIdle(this::removeFromRoot);
        } else {
            removeFromRoot();
        }
    }

    private void removeFromRoot() {
        if (containerView != null) {
            containerView.setVisibility(View.INVISIBLE);
        }
        if (actionBar.isSearchFieldVisible()) {
            actionBar.closeSearchField();
        }
        contactsLayout = null;
        quickRepliesLayout = null;
        audioLayout = null;
        pollLayout = null;
        locationLayout = null;
        documentLayout = null;
        for (int a = 1; a < layouts.length; a++) {
            if (layouts[a] == null) {
                continue;
            }
            layouts[a].onDestroy();
            containerView.removeView(layouts[a]);
            layouts[a] = null;
        }
        updateActionBarVisibility(false, false);
        super.dismissInternal();
    }

    @Override
    public void onBackPressed() {
        if (passcodeView.getVisibility() == View.VISIBLE) {
            if (getOwnerActivity() != null) {
                getOwnerActivity().finish();
            }
            return;
        }
        if (actionBar.isSearchFieldVisible()) {
            actionBar.closeSearchField();
            return;
        }
        if (currentAttachLayout.onBackPressed()) {
            return;
        }
        if (getCommentView() != null && getCommentView().isPopupShowing()) {
            getCommentView().hidePopup(true);
            return;
        }
        super.onBackPressed();
    }

    public EditTextEmoji getCommentView() {
        return captionAbove && (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) ? topCommentTextView : commentTextView;
    }

    @Override
    public void dismissWithButtonClick(int item) {
        super.dismissWithButtonClick(item);
        currentAttachLayout.onDismissWithButtonClick(item);
    }

    @Override
    protected boolean canDismissWithTouchOutside() {
        return currentAttachLayout.canDismissWithTouchOutside();
    }

    @Override
    protected void onDismissWithTouchOutside() {
        if (currentAttachLayout.onDismissWithTouchOutside()) {
            dismiss();
        }
    }

    private boolean confirmationAlertShown = false;
    protected boolean allowPassConfirmationAlert = false;

    public void dismiss(boolean passConfirmationAlert) {
        if (passConfirmationAlert) {
            this.allowPassConfirmationAlert = passConfirmationAlert;
        }
        this.dismiss();
    }

    @Override
    public void dismiss() {
        if (currentAttachLayout.onDismiss() || isDismissed()) {
            return;
        }
        if (commentTextView != null) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        if (topCommentTextView != null) {
            AndroidUtilities.hideKeyboard(topCommentTextView.getEditText());
        }
        botAttachLayouts.clear();
        BaseFragment baseFragment = this.baseFragment;
        if (baseFragment == null) {
            baseFragment = LaunchActivity.getLastFragment();
        }
        if (!allowPassConfirmationAlert && baseFragment != null && currentAttachLayout.getSelectedItemsCount() > 0 && !isPhotoPicker) {
            if (confirmationAlertShown) {
                return;
            }
            confirmationAlertShown = true;
            AlertDialog dialog =
                new AlertDialog.Builder(baseFragment.getParentActivity(), resourcesProvider)
                    .setTitle(getString(R.string.DiscardSelectionAlertTitle))
                    .setMessage(getString(R.string.DiscardSelectionAlertMessage))
                    .setPositiveButton(getString(R.string.Discard), (dialogInterface, i) -> {
                        allowPassConfirmationAlert = true;
                        dismiss();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .setOnCancelListener(di -> {
                        if (appearSpringAnimation != null) {
                            appearSpringAnimation.cancel();
                        }
                        appearSpringAnimation = new SpringAnimation(super.containerView, DynamicAnimation.TRANSLATION_Y, 0);
                        appearSpringAnimation.getSpring().setDampingRatio(1.5f);
                        appearSpringAnimation.getSpring().setStiffness(1500.0f);
                        appearSpringAnimation.start();
                    })
                    .setOnPreDismissListener(di -> {
                        confirmationAlertShown = false;
                    })
                    .create();
            dialog.show();
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(getThemedColor(Theme.key_text_RedBold));
            }
            return;
        }
        for (int a = 0; a < layouts.length; a++) {
            if (layouts[a] != null && currentAttachLayout != layouts[a]) {
                layouts[a].onDismiss();
            }
        }
        AndroidUtilities.setNavigationBarColor(getWindow(), ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundGray), 0), true, tcolor -> {
            navBarColorKey = -1;
            navBarColor = tcolor;
            containerView.invalidate();
        });
        if (baseFragment != null) {
            AndroidUtilities.setLightStatusBar(getWindow(), baseFragment.isLightStatusBar());
        }
        captionLimitBulletinShown = false;
        super.dismiss();
        allowPassConfirmationAlert = false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (currentAttachLayout.onSheetKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void setAllowNestedScroll(boolean allowNestedScroll) {
        this.allowNestedScroll = allowNestedScroll;
    }

    public BaseFragment getBaseFragment() {
        return baseFragment;
    }

    public EditTextEmoji getCommentTextView() {
        return commentTextView;
    }

    public ChatAttachAlertDocumentLayout getDocumentLayout() {
        return documentLayout;
    }

    public void setAllowEnterCaption(boolean allowEnterCaption) {
        this.allowEnterCaption = allowEnterCaption;
    }

    public void setDocumentsDelegate(ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate documentsDelegate) {
        this.documentsDelegate = documentsDelegate;
    }

    private void replaceWithText(int start, int len, CharSequence text, boolean parseEmoji) {
        if (getCommentView() == null) {
            return;
        }
        try {
            SpannableStringBuilder builder = new SpannableStringBuilder(getCommentView().getText());
            builder.replace(start, start + len, text);
            if (parseEmoji) {
                Emoji.replaceEmoji(builder, getCommentView().getEditText().getPaint().getFontMetricsInt(), dp(20), false);
            }
            getCommentView().setText(builder);
            getCommentView().setSelection(start + text.length());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public MentionsContainerView mentionContainer;
    private void createMentionsContainer() {
        mentionContainer = new MentionsContainerView(getContext(), UserConfig.getInstance(currentAccount).getClientUserId(), 0, LaunchActivity.getLastFragment(), null, resourcesProvider) {
            @Override
            protected void onScrolled(boolean atTop, boolean atBottom) {
                if (photoLayout != null) {
                    photoLayout.checkCameraViewPosition();
                }
            }

            @Override
            protected void onAnimationScroll() {
                if (photoLayout != null) {
                    photoLayout.checkCameraViewPosition();
                }
            }
        };
        mentionContainer.withDelegate(new MentionsContainerView.Delegate() {
            @Override
            public void replaceText(int start, int len, CharSequence replacingString, boolean allowShort) {
                replaceWithText(start, len, replacingString, allowShort);
            }
            @Override
            public Paint.FontMetricsInt getFontMetrics() {
                return commentTextView.getEditText().getPaint().getFontMetricsInt();
            }
        });
        containerView.addView(mentionContainer, containerView.indexOfChild(frameLayout2), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.BOTTOM));
        setupMentionContainer(mentionContainer);
        updateCommentTextViewPosition();
    }

    protected void setupMentionContainer(MentionsContainerView mentionContainer) {
        mentionContainer.getAdapter().setAllowStickers(false);
        mentionContainer.getAdapter().setAllowBots(false);
        mentionContainer.getAdapter().setAllowChats(false);
        mentionContainer.getAdapter().setSearchInDailogs(true);
        if (baseFragment instanceof ChatActivity) {
            ChatActivity chatActivity = (ChatActivity) baseFragment;
            mentionContainer.getAdapter().setUserOrChat(chatActivity.getCurrentUser(), chatActivity.getCurrentChat());
            mentionContainer.getAdapter().setChatInfo(chatActivity.getCurrentChatInfo());
            mentionContainer.getAdapter().setNeedUsernames(chatActivity.getCurrentChat() != null);
        } else {
            mentionContainer.getAdapter().setChatInfo(null);
            mentionContainer.getAdapter().setNeedUsernames(false);
        }
        mentionContainer.getAdapter().setNeedBotContext(false);
    }

    public void setCaptionAbove(boolean above) {
        setCaptionAbove(above, true);
    }
    public void setCaptionAbove(boolean above, boolean animated) {
        final EditTextEmoji fromView = getCommentView();
        captionAbove = above;
        final EditTextEmoji toView = getCommentView();

        final boolean allowCaption = (frameLayout2.getTag() != null);
        final boolean allowCaptionAbove = currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout;
        final boolean showAbove = captionAbove && allowCaptionAbove;
        if (animated) {
            topCommentContainer.setVisibility(allowCaption ? View.VISIBLE : View.GONE);
            topCommentContainer.animate()
                .alpha(showAbove && allowCaption ? 1.0f : 0.0f)
                .setDuration(320)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setUpdateListener(a -> {
                    updatedTopCaptionHeight();
                })
                .withEndAction(() -> {
                    if (!(showAbove && allowCaption)) {
                        topCommentContainer.setVisibility(View.GONE);
                    }
                    updatedTopCaptionHeight();
                })
                .start();

            captionContainer.setVisibility(View.VISIBLE);
            if (moveCaptionButton != null) {
                moveCaptionButton.setVisibility(View.VISIBLE);
            }
            captionContainer.animate()
                .translationY(!showAbove && allowCaption ? 0 : captionContainer.getMeasuredHeight())
                .alpha(!showAbove && allowCaption ? 1.0f : 0.0f)
                .setDuration(320)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setUpdateListener(a -> {
                    if (moveCaptionButton != null) {
                        moveCaptionButton.setTranslationY(bottomPannelTranslation - commentTextView.getHeight() + captionContainer.getTranslationY());
                        moveCaptionButton.setAlpha(captionContainer.getAlpha());
                    }
                    frameLayout2.invalidate();
                })
                .withEndAction(() -> {
                    if (showAbove || !allowCaption) {
                        captionContainer.setVisibility(View.GONE);
                        if (moveCaptionButton != null) {
                            moveCaptionButton.setVisibility(View.GONE);
                        }
                    }
                })
                .start();
        } else {
            topCommentContainer.setVisibility(showAbove && allowCaption ? View.VISIBLE : View.GONE);
            topCommentContainer.setAlpha(showAbove && allowCaption ? 1.0f : 0.0f);
            updatedTopCaptionHeight();

            captionContainer.setAlpha(!showAbove && allowCaption ? 1.0f : 0.0f);
            captionContainer.setTranslationY(!showAbove && allowCaption ? 0 : captionContainer.getMeasuredHeight());
            captionContainer.setVisibility(!showAbove && allowCaption ? View.VISIBLE : View.GONE);
            moveCaptionButton.setAlpha(!showAbove && allowCaption ? 1.0f : 0.0f);
            moveCaptionButton.setVisibility(!showAbove && allowCaption ? View.VISIBLE : View.GONE);
        }

        if (fromView != toView) {
            fromView.hidePopup(true);
            toView.setText(AnimatedEmojiSpan.cloneSpans(fromView.getText()));
            toView.getEditText().setAllowTextEntitiesIntersection(fromView.getEditText().getAllowTextEntitiesIntersection());
            if (fromView.getEditText().isFocused()) {
                toView.getEditText().requestFocus();
                toView.getEditText().setSelection(
                        fromView.getEditText().getSelectionStart(),
                        fromView.getEditText().getSelectionEnd()
                );
            }
        }
    }

    private void updatedTopCaptionHeight() {
        actionBarShadow.setTranslationY(currentPanTranslationY + topCommentContainer.getMeasuredHeight() * topCommentContainer.getAlpha());
        updateSelectedPosition(0);
        sizeNotifierFrameLayout.invalidate();
        topCommentContainer.invalidate();
        if (photoLayout != null) {
            photoLayout.checkCameraViewPosition();
            if (photoLayout.gridView != null && photoLayout.gridView.getFastScroll() != null) {
                photoLayout.gridView.getFastScroll().topOffset = captionAbove ? (int) (topCommentContainer.getMeasuredHeight() * topCommentContainer.getAlpha()) : 0;
                photoLayout.gridView.getFastScroll().invalidate();
            }
        }
        updateCommentTextViewPosition();
    }

    private void toggleCaptionAbove() {
        setCaptionAbove(!captionAbove);
    }
}
