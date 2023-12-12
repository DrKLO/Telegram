/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
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
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PassportActivity;
import org.telegram.ui.PaymentFormActivity;
import org.telegram.ui.PhotoPickerActivity;
import org.telegram.ui.PhotoPickerSearchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.WebAppDisclaimerAlert;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChatAttachAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, BottomSheet.BottomSheetDelegateInterface {

    public ChatActivity.ThemeDelegate parentThemeDelegate;

    private final NumberTextView captionLimitView;
    public boolean forUser;
    public boolean isPhotoPicker;
    private int currentLimit;
    private int codepointCount;

    public boolean canOpenPreview = false;
    private boolean isSoundPicker = false;
    public boolean isStoryLocationPicker = false;
    public boolean isStoryAudioPicker = false;
    private ImageUpdater.AvatarFor setAvatarFor;
    public boolean pinnedToTop;

    private PasscodeView passcodeView;
    private ChatAttachRestrictedLayout restrictedLayout;
    public ImageUpdater parentImageUpdater;
    public boolean destroyed;
    public boolean allowEnterCaption;
    private ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate documentsDelegate;
    private long dialogId;
    private boolean overrideBackgroundColor;

    public void setCanOpenPreview(boolean canOpenPreview) {
        this.canOpenPreview = canOpenPreview;
        selectedArrowImageView.setVisibility(canOpenPreview && avatarPicker != 2 ? View.VISIBLE : View.GONE);
    }

    public float getClipLayoutBottom() {
        float alphaOffset = (frameLayout2.getMeasuredHeight() - AndroidUtilities.dp(84)) * (1f - frameLayout2.getAlpha());
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
                    public void onWebAppSetActionBarColor(int color, boolean isOverrideColor) {
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
                    public void onWebAppOpenInvoice(String slug, TLObject response) {
                        BaseFragment parentFragment = baseFragment;
                        PaymentFormActivity paymentFormActivity = null;
                        if (response instanceof TLRPC.TL_payments_paymentForm) {
                            TLRPC.TL_payments_paymentForm form = (TLRPC.TL_payments_paymentForm) response;
                            MessagesController.getInstance(currentAccount).putUsers(form.users, false);
                            paymentFormActivity = new PaymentFormActivity(form, slug, parentFragment);
                        } else if (response instanceof TLRPC.TL_payments_paymentReceipt) {
                            paymentFormActivity = new PaymentFormActivity((TLRPC.TL_payments_paymentReceipt) response);
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
                            dialogsActivity.setDelegate((fragment, dids, message1, param, topicsFragment) -> {
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
                                args1.putString("inline_query_input", "@" + UserObject.getPublicUsername(botUser) + " " + query);

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
                    public void onSetupMainButton(boolean isVisible, boolean isActive, String text, int color, int textColor, boolean isProgressVisible) {
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
                                botMainButtonOffsetY = value * AndroidUtilities.dp(36);
                                shadow.setTranslationY(botMainButtonOffsetY);
                                buttonsRecyclerView.setTranslationY(botMainButtonOffsetY);
                            });
                            botButtonAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(Animator animation) {
                                    if (isVisible) {
                                        botMainButtonTextView.setAlpha(0f);
                                        botMainButtonTextView.setVisibility(View.VISIBLE);

                                        int offsetY = AndroidUtilities.dp(36);
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

                                    int offsetY = isVisible ? AndroidUtilities.dp(36) : 0;
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

        void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, boolean forceDocument);

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

        default void sendAudio(ArrayList<MessageObject> audios, CharSequence caption, boolean notify, int scheduleDate) {

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
                if (value > 0.7f) {
                    float alpha = 1.0f - (1.0f - value) / 0.3f;
                    if (nextAttachLayout == locationLayout) {
                        currentAttachLayout.setAlpha(1.0f - alpha);
                        nextAttachLayout.setAlpha(1.0f);
                    } else {
                        nextAttachLayout.setAlpha(alpha);
                        nextAttachLayout.onHideShowProgress(alpha);
                    }
                } else {
                    if (nextAttachLayout == locationLayout) {
                        nextAttachLayout.setAlpha(0.0f);
                    }
                }
                if (nextAttachLayout == pollLayout || currentAttachLayout == pollLayout) {
                    updateSelectedPosition(nextAttachLayout == pollLayout ? 1 : 0);
                }
                nextAttachLayout.setTranslationY(AndroidUtilities.dp(78) * value);
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

        boolean onSheetKeyDown(int keyCode, KeyEvent event) {
            return false;
        }

        boolean onDismiss() {
            return false;
        }

        boolean onCustomMeasure(View view, int width, int height) {
            return false;
        }

        boolean onCustomLayout(View view, int left, int top, int right, int bottom) {
            return false;
        }

        boolean onContainerViewTouchEvent(MotionEvent event) {
            return false;
        }

        void onPreMeasure(int availableWidth, int availableHeight) {

        }

        void onMenuItemClick(int id) {

        }

        boolean hasCustomBackground() {
            return false;
        }

        int getCustomBackground() {
            return 0;
        }

        boolean hasCustomActionBarBackground() {
            return false;
        }

        int getCustomActionBarBackground() {
            return 0;
        }


        void onButtonsTranslationYUpdated() {

        }

        boolean canScheduleMessages() {
            return true;
        }

        void checkColors() {

        }

        ArrayList<ThemeDescription> getThemeDescriptions() {
            return null;
        }

        void onPause() {

        }

        void onResume() {

        }

        boolean onDismissWithTouchOutside() {
            return true;
        }

        boolean canDismissWithTouchOutside() {
            return true;
        }

        void onDismissWithButtonClick(int item) {

        }

        void onContainerTranslationUpdated(float currentPanTranslationY) {

        }

        void onHideShowProgress(float progress) {

        }

        void onOpenAnimationEnd() {

        }

        void onInit(boolean hasVideo, boolean hasPhoto, boolean hasDocuments) {

        }

        int getSelectedItemsCount() {
            return 0;
        }

        void onSelectedItemsCountChanged(int count) {

        }

        void applyCaption(CharSequence text) {

        }

        void onDestroy() {

        }

        void onHide() {

        }

        void onHidden() {

        }

        int getCurrentItemTop() {
            return 0;
        }

        int getFirstOffset() {
            return 0;
        }

        int getButtonsHideOffset() {
            return AndroidUtilities.dp(needsActionBar() != 0 ? 12 : 17);
        }

        int getListTopPadding() {
            return 0;
        }

        int needsActionBar() {
            return 0;
        }

        void sendSelectedItems(boolean notify, int scheduleDate) {

        }

        void onShow(AttachAlertLayout previousLayout) {

        }

        void onShown() {

        }

        void scrollToTop() {

        }

        boolean onBackPressed() {
            return false;
        }

        protected int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }

        boolean shouldHideBottomButtons() {
            return true;
        }

        public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        }

        public void onPanTransitionEnd() {
        }
    }

    @Nullable
    final BaseFragment baseFragment;
    protected boolean inBubbleMode;
    private ActionBarPopupWindow sendPopupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout;
    private ActionBarMenuSubItem[] itemCells;

    private View shadow;

    private ChatAttachAlertPhotoLayout photoLayout;
    private ChatAttachAlertContactsLayout contactsLayout;
    private ChatAttachAlertAudioLayout audioLayout;
    private ChatAttachAlertPollLayout pollLayout;
    private ChatAttachAlertLocationLayout locationLayout;
    private ChatAttachAlertDocumentLayout documentLayout;
    private ChatAttachAlertPhotoLayoutPreview photoPreviewLayout;
    public ChatAttachAlertColorsLayout colorsLayout;
    private AttachAlertLayout[] layouts = new AttachAlertLayout[7];
    private LongSparseArray<ChatAttachAlertBotWebViewLayout> botAttachLayouts = new LongSparseArray<>();
    private AttachAlertLayout currentAttachLayout;
    private AttachAlertLayout nextAttachLayout;

    private FrameLayout frameLayout2;
    protected EditTextEmoji commentTextView;
    private int[] commentTextViewLocation = new int[2];
    private FrameLayout writeButtonContainer;
    private ImageView writeButton;
    private Drawable writeButtonDrawable;
    private View selectedCountView;
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AnimatorSet commentsAnimator;

    protected int avatarPicker;
    protected boolean avatarSearch;
    protected boolean typeButtonsAvailable;

    private boolean stories;

    boolean sendButtonEnabled = true;
    private float sendButtonEnabledProgress = 1f;
    private ValueAnimator sendButtonColorAnimator;

    private long selectedId;

    protected float cornerRadius = 1.0f;

    protected ActionBar actionBar;
    private View actionBarShadow;
    private AnimatorSet actionBarAnimation;
    private AnimatorSet menuAnimator;
    protected ActionBarMenuItem selectedMenuItem;
    @Nullable
    protected ActionBarMenuItem searchItem;
    protected ActionBarMenuItem doneItem;
    protected FrameLayout headerView;
    protected TextView selectedTextView;
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

    protected MessageObject editingMessageObject;

    private boolean buttonPressed;

    protected int currentAccount = UserConfig.selectedAccount;

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

    private int attachItemSize = AndroidUtilities.dp(85);

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    protected ChatAttachViewDelegate delegate;

    protected int[] scrollOffsetY = new int[2];
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
            textView.setLineSpacing(-AndroidUtilities.dp(2), 1.0f);
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
            super.onMeasure(MeasureSpec.makeMeasureSpec(attachItemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(84), MeasureSpec.EXACTLY));
        }

        public void setTextAndIcon(int id, CharSequence text, RLottieDrawable drawable, int background, int textColor) {
            currentId = id;
            textView.setText(text);
            imageView.setAnimation(drawable);
            backgroundKey = background;
            textKey = textColor;
            textView.setTextColor(ColorUtils.blendARGB(getThemedColor(Theme.key_dialogTextGray2), getThemedColor(textKey), checkedState));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float scale = imageView.getScaleX() + 0.06f * checkedState;
            float radius = AndroidUtilities.dp(23) * scale;

            float cx = imageView.getLeft() + imageView.getMeasuredWidth() / 2f;
            float cy = imageView.getTop() + imageView.getMeasuredWidth() / 2f;

            attachButtonPaint.setColor(getThemedColor(backgroundKey));
            attachButtonPaint.setStyle(Paint.Style.STROKE);
            attachButtonPaint.setStrokeWidth(AndroidUtilities.dp(3) * scale);
            attachButtonPaint.setAlpha(Math.round(255f * checkedState));
            canvas.drawCircle(cx, cy, radius - 0.5f * attachButtonPaint.getStrokeWidth(), attachButtonPaint);

            attachButtonPaint.setAlpha(255);
            attachButtonPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, radius - AndroidUtilities.dp(5) * checkedState, attachButtonPaint);
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
            imageView.setRoundRadius(AndroidUtilities.dp(25));
            addView(imageView, LayoutHelper.createFrame(46, 46, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 9, 0, 0));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                selector = new View(context);
                selector.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 1, AndroidUtilities.dp(23)));
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
            super.onMeasure(MeasureSpec.makeMeasureSpec(attachItemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY));
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
            params.topMargin = AndroidUtilities.dp(attachMenuBot != null ? 62 : 60);
            params = (MarginLayoutParams) imageView.getLayoutParams();
            params.topMargin = AndroidUtilities.dp(attachMenuBot != null ? 11 : 9);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (attachMenuBot != null) {
                float imageScale = imageView.getScaleX();
                float scale = imageScale + 0.06f * checkedState;
                float radius = AndroidUtilities.dp(23) * scale;

                float cx = imageView.getLeft() + imageView.getMeasuredWidth() / 2f;
                float cy = imageView.getTop() + imageView.getMeasuredWidth() / 2f;

                attachButtonPaint.setColor(iconBackgroundColor);
                attachButtonPaint.setStyle(Paint.Style.STROKE);
                attachButtonPaint.setStrokeWidth(AndroidUtilities.dp(3) * scale);
                attachButtonPaint.setAlpha(Math.round(255f * checkedState));
                canvas.drawCircle(cx, cy, radius - 0.5f * attachButtonPaint.getStrokeWidth(), attachButtonPaint);

                attachButtonPaint.setAlpha(255);
                attachButtonPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, radius - AndroidUtilities.dp(5) * checkedState, attachButtonPaint);
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

            imageView.setSize(AndroidUtilities.dp(28), AndroidUtilities.dp(28));
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
        exclusionRects.add(exclustionRect);

        sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private Bulletin.Delegate bulletinDelegate = new Bulletin.Delegate() {
                @Override
                public int getBottomOffset(int tag) {
                    return getHeight() - frameLayout2.getTop() + AndroidUtilities.dp(52);
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
                            int offsetY = keyboardVisible ? AndroidUtilities.dp(84) : 0;
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
                    actionBarShadow.setTranslationY(currentPanTranslationY);
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
                    return !commentTextView.isPopupVisible();
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
                    selectedMenuItem.setAdditionalYOffset(-AndroidUtilities.dp(3));
                } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    selectedMenuItem.setAdditionalYOffset(0);
                } else {
                    selectedMenuItem.setAdditionalYOffset(-AndroidUtilities.dp(3));
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

                if (keyboardSize <= AndroidUtilities.dp(20)) {
                    int paddingBottom;
                    if (keyboardVisible) {
                        paddingBottom = 0;
                    } else {
                        paddingBottom = commentTextView.getEmojiPadding();
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
                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (inBubbleMode) {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize + getPaddingTop(), MeasureSpec.EXACTLY));
                        } else if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
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
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        sendPopupWindow.dismiss();
                    }
                }
                final int count = getChildCount();

                if (Build.VERSION.SDK_INT >= 29) {
                    exclustionRect.set(l, t, r, b);
                    setSystemGestureExclusionRects(exclusionRects);
                }

                int keyboardSize = measureKeyboardHeight();
                int paddingBottom = getPaddingBottom();
                if (keyboardVisible) {
                    paddingBottom += 0;
                } else {
                    paddingBottom += keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? commentTextView.getEmojiPadding() : 0;
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

                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
                updateLayout(currentAttachLayout, false, 0);
                updateLayout(nextAttachLayout, false, 0);
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

                    int offset = AndroidUtilities.dp(13) + (int) ((headerView != null ? headerView.getAlpha() : 0f) * AndroidUtilities.dp(26));
                    int top = getScrollOffsetY(0) - backgroundPaddingTop - offset;
                    if (currentSheetAnimationType == 1 || viewChangeAnimator != null) {
                        top += child.getTranslationY();
                    }
                    int y = top + AndroidUtilities.dp(20);
                    int h = (actionBarType != 0 ? ActionBar.getCurrentActionBarHeight() : backgroundPaddingTop);
                    if (actionBarType != 2 && top + backgroundPaddingTop < h) {
                        float toMove = offset;
                        if (layout == locationLayout) {
                            toMove += AndroidUtilities.dp(11);
                        } else if (layout == pollLayout) {
                            toMove -= AndroidUtilities.dp(3);
                        } else {
                            toMove += AndroidUtilities.dp(4);
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

                    int offset = AndroidUtilities.dp(13) + (int) ((headerView != null ? headerView.getAlpha() : 0f) * AndroidUtilities.dp(26));
                    int top = getScrollOffsetY(0) - backgroundPaddingTop - offset;
                    if (currentSheetAnimationType == 1 || viewChangeAnimator != null) {
                        top += child.getTranslationY();
                    }
                    int y = top + AndroidUtilities.dp(20);

                    int height = getMeasuredHeight() + AndroidUtilities.dp(45) + backgroundPaddingTop;
                    float rad = 1.0f;

                    int h = (actionBarType != 0 ? ActionBar.getCurrentActionBarHeight() : backgroundPaddingTop);
                    if (actionBarType == 2) {
                        if (top < h) {
                            rad = Math.max(0, 1.0f - (h - top) / (float) backgroundPaddingTop);
                        }
                    } else {
                        float toMove = offset;
                        if (layout == locationLayout) {
                            toMove += AndroidUtilities.dp(11);
                        } else if (layout == pollLayout) {
                            toMove -= AndroidUtilities.dp(3);
                        } else {
                            toMove += AndroidUtilities.dp(4);
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
                    shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                    shadowDrawable.draw(canvas);
                    if (actionBarType == 2) {
                        Theme.dialogs_onlineCirclePaint.setColor(backgroundColor);
                        Theme.dialogs_onlineCirclePaint.setAlpha(viewAlpha);
                        rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                        canvas.save();
                        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() / 2);
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                        canvas.restore();
                    }

                    if ((rad != 1.0f && actionBarType != 2) || currentAttachLayout.hasCustomActionBarBackground()) {
                        Theme.dialogs_onlineCirclePaint.setColor(currentAttachLayout.hasCustomActionBarBackground() ? currentAttachLayout.getCustomActionBarBackground() : backgroundColor);
                        Theme.dialogs_onlineCirclePaint.setAlpha(viewAlpha);
                        rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                        canvas.save();
                        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() / 2);
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                        canvas.restore();
                    }

                    if (currentAttachLayout.hasCustomActionBarBackground()) {
                        Theme.dialogs_onlineCirclePaint.setColor(currentAttachLayout.getCustomActionBarBackground());
                        Theme.dialogs_onlineCirclePaint.setAlpha(viewAlpha);
                        int top2 = getScrollOffsetY(0);
                        if (Build.VERSION.SDK_INT >= 21 && !inBubbleMode) {
                            top2 += AndroidUtilities.statusBarHeight;
                        }
                        rect.set(backgroundPaddingLeft, (backgroundPaddingTop + top + AndroidUtilities.dp(12)) * (rad), getMeasuredWidth() - backgroundPaddingLeft, top2 + AndroidUtilities.dp(12));
                        canvas.save();
                        canvas.drawRect(rect, Theme.dialogs_onlineCirclePaint);
                        canvas.restore();
                    }

                    if ((headerView == null || headerView.getAlpha() != 1.0f) && rad != 0) {
                        int w = AndroidUtilities.dp(36);
                        rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
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
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
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

                    int offset = AndroidUtilities.dp(13) + (headerView != null ? AndroidUtilities.dp(headerView.getAlpha() * 26) : 0);
                    int top = getScrollOffsetY(layout == currentAttachLayout ? 0 : 1) - backgroundPaddingTop - offset;
                    if (currentSheetAnimationType == 1 || viewChangeAnimator != null) {
                        top += child.getTranslationY();
                    }
                    int y = top + AndroidUtilities.dp(20);

                    int height = getMeasuredHeight() + AndroidUtilities.dp(45) + backgroundPaddingTop;
                    float rad = 1.0f;

                    int h = (actionBarType != 0 ? ActionBar.getCurrentActionBarHeight() : backgroundPaddingTop);
                    if (actionBarType == 2) {
                        if (top < h) {
                            rad = Math.max(0, 1.0f - (h - top) / (float) backgroundPaddingTop);
                        }
                    } else if (top + backgroundPaddingTop < h) {
                        float toMove = offset;
                        if (layout == locationLayout) {
                            toMove += AndroidUtilities.dp(11);
                        } else if (layout == pollLayout) {
                            toMove -= AndroidUtilities.dp(3);
                        } else {
                            toMove += AndroidUtilities.dp(4);
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
                            rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                            canvas.save();
                            canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() / 2);
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                            canvas.restore();
                        }
                    }

                    boolean result;
                    if (child != contactsLayout && child != audioLayout) {
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
                            rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                            canvas.save();
                            canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() / 2);
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                            canvas.restore();
                        }

                        if ((headerView == null || headerView.getAlpha() != 1.0f) && rad != 0) {
                            int w = AndroidUtilities.dp(36);
                            rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
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
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
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
                int y = scrollOffsetY[0] - backgroundPaddingTop * 2 - (AndroidUtilities.dp(13) + (headerView != null ? AndroidUtilities.dp(headerView.getAlpha() * 26) : 0)) + AndroidUtilities.dp(20);
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
                        if (avatarPicker != 0) {
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
                                buttonsRecyclerView.setTranslationY(AndroidUtilities.dp(44) * alpha);
                            }
                            frameLayout2.setTranslationY(AndroidUtilities.dp(48) * alpha);
                            shadow.setTranslationY(AndroidUtilities.dp(84) * alpha + botMainButtonOffsetY);
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
        selectedMenuItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        selectedMenuItem.setVisibility(View.INVISIBLE);
        selectedMenuItem.setAlpha(0.0f);
        selectedMenuItem.setSubMenuOpenSide(2);
        selectedMenuItem.setDelegate(id -> actionBar.getActionBarMenuOnItemClick().onItemClick(id));
        selectedMenuItem.setAdditionalYOffset(AndroidUtilities.dp(72));
        selectedMenuItem.setTranslationX(AndroidUtilities.dp(6));
        selectedMenuItem.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 6));
        selectedMenuItem.setOnClickListener(v -> selectedMenuItem.toggleSubMenu());

        doneItem = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader), true, resourcesProvider);
        doneItem.setLongClickEnabled(false);
        doneItem.setText(LocaleController.getString("Create", R.string.Create).toUpperCase());
        doneItem.setVisibility(View.INVISIBLE);
        doneItem.setAlpha(0.0f);
        doneItem.setTranslationX(-AndroidUtilities.dp(12));
        doneItem.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_dialogButtonSelector), 3));
        doneItem.setOnClickListener(v -> currentAttachLayout.onMenuItemClick(40));

        if (baseFragment != null) {
            searchItem = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_dialogTextBlack), false, resourcesProvider);
            searchItem.setLongClickEnabled(false);
            searchItem.setIcon(R.drawable.ic_ab_search);
            searchItem.setContentDescription(LocaleController.getString("Search", R.string.Search));
            searchItem.setVisibility(View.INVISIBLE);
            searchItem.setAlpha(0.0f);
            searchItem.setTranslationX(-AndroidUtilities.dp(42));
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
        selectedTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
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
        mediaPreviewTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        mediaPreviewTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        mediaPreviewTextView.setText(LocaleController.getString("AttachMediaPreview", R.string.AttachMediaPreview));
        mediaPreviewView.setAlpha(0);

        mediaPreviewView.addView(mediaPreviewTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        headerView.addView(mediaPreviewView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        layouts[0] = photoLayout = new ChatAttachAlertPhotoLayout(this, context, forceDarkTheme, needCamera, resourcesProvider);
        photoLayout.setTranslationX(0);
        currentAttachLayout = photoLayout;
        selectedId = 1;

        containerView.addView(photoLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        containerView.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 0, 48, 0));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        containerView.addView(selectedMenuItem, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.RIGHT));
        if (searchItem != null) {
            containerView.addView(searchItem, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.RIGHT));
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
                int num = (Integer) view.getTag();
                if (num == 1) {
                    if (!photosEnabled && !videosEnabled) {
                        showLayout(restrictedLayout = new ChatAttachRestrictedLayout(1, this, getContext(), resourcesProvider));
                    }
                    showLayout(photoLayout);
                } else if (num == 3) {
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
                    if (Build.VERSION.SDK_INT >= 23 && plainTextEnabled) {
                        if (getContext().checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                            AndroidUtilities.findActivity(getContext()).requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, BasePermissionsActivity.REQUEST_CODE_ATTACH_CONTACT);
                            return;
                        }
                    }
                    openContactsLayout();
                } else if (num == 6) {
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
                } else {
                    delegate.didPressedButton((Integer) view.getTag(), true, true, 0, false);
                }
                int left = view.getLeft();
                int right = view.getRight();
                int extra = AndroidUtilities.dp(10);
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
                        }, null);
                    } else {
                        showBotLayout(button.attachMenuBot.bot_id, true);
                    }
                } else {
                    delegate.didSelectBot(button.currentUser);
                    dismiss();
                }
            }

            if (view.getX() + view.getWidth() >= buttonsRecyclerView.getMeasuredWidth() - AndroidUtilities.dp(32)) {
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
        botMainButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        int padding = AndroidUtilities.dp(16);
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
        botProgressView.setSize(AndroidUtilities.dp(18));
        botProgressView.setAlpha(0f);
        botProgressView.setScaleX(0.1f);
        botProgressView.setScaleY(0.1f);
        botProgressView.setVisibility(View.GONE);
        containerView.addView(botProgressView, LayoutHelper.createFrame(28, 28, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 10, 10));

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

                float alphaOffset = (frameLayout2.getMeasuredHeight() - AndroidUtilities.dp(84)) * (1f - getAlpha());
                shadow.setTranslationY(-(frameLayout2.getMeasuredHeight() - AndroidUtilities.dp(84)) + captionEditTextTopOffset + currentPanTranslationY + bottomPannelTranslation + alphaOffset + botMainButtonOffsetY);

                int newColor = currentAttachLayout.hasCustomBackground() ? currentAttachLayout.getCustomBackground() : getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground);
                if (color != newColor) {
                    color = newColor;
                    p.setColor(color);
                }
                canvas.drawRect(0, captionEditTextTopOffset, getMeasuredWidth(), getMeasuredHeight(), p);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.clipRect(0, captionEditTextTopOffset, getMeasuredWidth(), getMeasuredHeight());
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };

        frameLayout2.setWillNotDraw(false);
        frameLayout2.setVisibility(View.INVISIBLE);
        frameLayout2.setAlpha(0.0f);
        containerView.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));
        frameLayout2.setOnTouchListener((v, event) -> true);

        captionLimitView = new NumberTextView(context);
        captionLimitView.setVisibility(View.GONE);
        captionLimitView.setTextSize(15);
        captionLimitView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        captionLimitView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        captionLimitView.setCenterAlign(true);
        frameLayout2.addView(captionLimitView, LayoutHelper.createFrame(56, 20, Gravity.BOTTOM | Gravity.RIGHT, 3, 0, 14, 78));

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
                //     buttonsRecyclerView.setTranslationY(translation);
                frameLayout2.setTranslationY(translation);
                writeButtonContainer.setTranslationY(translation);
                selectedCountView.setTranslationY(translation);
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
        };
        commentTextView.setHint(LocaleController.getString("AddCaption", R.string.AddCaption));
        commentTextView.onResume();
        commentTextView.getEditText().addTextChangedListener(new TextWatcher() {

            private boolean processChange;

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
                    mentionContainer.getAdapter().searchUsernameOrHashtag(charSequence, commentTextView.getEditText().getSelectionStart(), null, false, false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (processChange) {
                    ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);
                    for (int i = 0; i < spans.length; i++) {
                        editable.removeSpan(spans[i]);
                    }
                    Emoji.replaceEmoji(editable, commentTextView.getEditText().getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
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
                } else {
                    captionLimitView.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setDuration(100).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            captionLimitView.setVisibility(View.GONE);
                        }
                    });
                }

                if (sendButtonEnabled != sendButtonEnabledLocal) {
                    sendButtonEnabled = sendButtonEnabledLocal;
                    if (sendButtonColorAnimator != null) {
                        sendButtonColorAnimator.cancel();
                    }
                    sendButtonColorAnimator = ValueAnimator.ofFloat(sendButtonEnabled ? 0 : 1f, sendButtonEnabled ? 1f : 0);
                    sendButtonColorAnimator.addUpdateListener(valueAnimator -> {
                        sendButtonEnabledProgress = (float) valueAnimator.getAnimatedValue();
                        int color = getThemedColor(Theme.key_dialogFloatingIcon);
                        int defaultAlpha = Color.alpha(color);
                        writeButton.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(color, (int) (defaultAlpha * (0.58f + 0.42f * sendButtonEnabledProgress))), PorterDuff.Mode.MULTIPLY));
                        selectedCountView.invalidate();

                    });
                    sendButtonColorAnimator.setDuration(150).start();
                }

//                if (!captionLimitBulletinShown && !MessagesController.getInstance(currentAccount).premiumLocked && !UserConfig.getInstance(currentAccount).isPremium() && codepointCount > MessagesController.getInstance(currentAccount).captionLengthLimitDefault && codepointCount < MessagesController.getInstance(currentAccount).captionLengthLimitPremium) {
//                    captionLimitBulletinShown = true;
//                    showCaptionLimitBulletin(parentFragment);
//                }
            }
        });
        frameLayout2.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 84, 0));
        frameLayout2.setClipChildren(false);
        commentTextView.setClipChildren(false);

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

        writeButton = new ImageView(context);
        writeButtonDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), getThemedColor(Theme.key_dialogFloatingButton), getThemedColor(Build.VERSION.SDK_INT >= 21 ? Theme.key_dialogFloatingButtonPressed : Theme.key_dialogFloatingButton));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, writeButtonDrawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            writeButtonDrawable = combinedDrawable;
        }
        writeButton.setBackgroundDrawable(writeButtonDrawable);
        writeButton.setImageResource(R.drawable.attach_send);
        writeButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));
        writeButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        writeButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            writeButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        writeButtonContainer.addView(writeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.LEFT | Gravity.TOP, Build.VERSION.SDK_INT >= 21 ? 2 : 0, 0, 0, 0));
        writeButton.setOnClickListener(v -> {
            if (currentLimit - codepointCount < 0) {
                AndroidUtilities.shakeView(captionLimitView);
                try {
                    captionLimitView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {
                }

                if (!MessagesController.getInstance(currentAccount).premiumLocked && MessagesController.getInstance(currentAccount).captionLengthLimitPremium > codepointCount) {
                    showCaptionLimitBulletin(parentFragment);
                }
                return;
            }
            if (editingMessageObject == null && baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).isInScheduleMode()) {
                AlertsCreator.createScheduleDatePickerDialog(getContext(), ((ChatActivity) baseFragment).getDialogId(), (notify, scheduleDate) -> {
                    if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                        sendPressed(notify, scheduleDate);
                    } else {
                        currentAttachLayout.sendSelectedItems(notify, scheduleDate);
                        allowPassConfirmationAlert = true;
                        dismiss();
                    }
                }, resourcesProvider);
            } else {
                if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                    sendPressed(true, 0);
                } else {
                    currentAttachLayout.sendSelectedItems(true, 0);
                    allowPassConfirmationAlert = true;
                    dismiss();
                }
            }
        });
        writeButton.setOnLongClickListener(view -> {
            if ((dialogId == 0 && !(baseFragment instanceof ChatActivity)) || editingMessageObject != null || currentLimit - codepointCount < 0) {
                return false;
            }
            ChatActivity chatActivity = null;
            TLRPC.User user = null;
            long dialogId = this.dialogId;
            if (baseFragment instanceof ChatActivity) {
                chatActivity = (ChatActivity) baseFragment;
                TLRPC.Chat chat = chatActivity.getCurrentChat();
                user = chatActivity.getCurrentUser();
                if (chatActivity.isInScheduleMode()) {
                    return false;
                }
                dialogId = chatActivity.getDialogId();
            } else {
                user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            }

            sendPopupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext(), resourcesProvider);
            sendPopupLayout.setAnimationEnabled(false);
            sendPopupLayout.setOnTouchListener(new View.OnTouchListener() {

                private Rect popupRect = new Rect();

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                            v.getHitRect(popupRect);
                            if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                                sendPopupWindow.dismiss();
                            }
                        }
                    }
                    return false;
                }
            });
            sendPopupLayout.setDispatchKeyEventListener(keyEvent -> {
                if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && sendPopupWindow != null && sendPopupWindow.isShowing()) {
                    sendPopupWindow.dismiss();
                }
            });
            sendPopupLayout.setShownFromBottom(false);

            itemCells = new ActionBarMenuSubItem[2];
            int i = 0;
            for (int a = 0; a < 2; a++) {
                if (a == 0) {
                    if ((chatActivity != null && !chatActivity.canScheduleMessage()) || !currentAttachLayout.canScheduleMessages()) {
                        continue;
                    }
                } else if (a == 1 && UserObject.isUserSelf(user)) {
                    continue;
                }
                int num = a;
                itemCells[a] = new ActionBarMenuSubItem(getContext(), a == 0, a == 1, resourcesProvider);
                if (num == 0) {
                    if (UserObject.isUserSelf(user)) {
                        itemCells[a].setTextAndIcon(LocaleController.getString("SetReminder", R.string.SetReminder), R.drawable.msg_calendar2);
                    } else {
                        itemCells[a].setTextAndIcon(LocaleController.getString("ScheduleMessage", R.string.ScheduleMessage), R.drawable.msg_calendar2);
                    }
                } else if (num == 1) {
                    itemCells[a].setTextAndIcon(LocaleController.getString("SendWithoutSound", R.string.SendWithoutSound), R.drawable.input_notify_off);
                }
                itemCells[a].setMinimumWidth(AndroidUtilities.dp(196));

                sendPopupLayout.addView(itemCells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                long finalDialogId = dialogId;
                itemCells[a].setOnClickListener(v -> {
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        sendPopupWindow.dismiss();
                    }
                    if (num == 0) {
                        AlertsCreator.createScheduleDatePickerDialog(getContext(), finalDialogId, (notify, scheduleDate) -> {
                            if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                                sendPressed(notify, scheduleDate);
                            } else {
                                currentAttachLayout.sendSelectedItems(notify, scheduleDate);
                                dismiss();
                            }
                        }, resourcesProvider);
                    } else if (num == 1) {
                        if (currentAttachLayout == photoLayout || currentAttachLayout == photoPreviewLayout) {
                            sendPressed(false, 0);
                        } else {
                            currentAttachLayout.sendSelectedItems(false, 0);
                            dismiss();
                        }
                    }
                });
                i++;
            }
            sendPopupLayout.setupRadialSelectors(getThemedColor(Theme.key_dialogButtonSelector));

            sendPopupWindow = new ActionBarPopupWindow(sendPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            sendPopupWindow.setAnimationEnabled(false);
            sendPopupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
            sendPopupWindow.setOutsideTouchable(true);
            sendPopupWindow.setClippingEnabled(true);
            sendPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            sendPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            sendPopupWindow.getContentView().setFocusableInTouchMode(true);

            sendPopupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
            sendPopupWindow.setFocusable(true);
            int[] location = new int[2];
            view.getLocationInWindow(location);
            sendPopupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, location[0] + view.getMeasuredWidth() - sendPopupLayout.getMeasuredWidth() + AndroidUtilities.dp(8), location[1] - sendPopupLayout.getMeasuredHeight() - AndroidUtilities.dp(2));
            sendPopupWindow.dimBehind();
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

            return false;
        });

        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        selectedCountView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                String text = String.format("%d", Math.max(1, currentAttachLayout.getSelectedItemsCount()));
                int textSize = (int) Math.ceil(textPaint.measureText(text));
                int size = Math.max(AndroidUtilities.dp(16) + textSize, AndroidUtilities.dp(24));
                int cx = getMeasuredWidth() / 2;

                int color = getThemedColor(Theme.key_dialogRoundCheckBoxCheck);
                textPaint.setColor(ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * (0.58 + 0.42 * sendButtonEnabledProgress))));
                paint.setColor(getThemedColor(Theme.key_dialogBackground));
                rect.set(cx - size / 2, 0, cx + size / 2, getMeasuredHeight());
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

                paint.setColor(getThemedColor(Theme.key_chat_attachCheckBoxBackground));
                rect.set(cx - size / 2 + AndroidUtilities.dp(2), AndroidUtilities.dp(2), cx + size / 2 - AndroidUtilities.dp(2), getMeasuredHeight() - AndroidUtilities.dp(2));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);

                canvas.drawText(text, cx - textSize / 2, AndroidUtilities.dp(16.2f), textPaint);
            }
        };
        selectedCountView.setAlpha(0.0f);
        selectedCountView.setScaleX(0.2f);
        selectedCountView.setScaleY(0.2f);
        containerView.addView(selectedCountView, LayoutHelper.createFrame(42, 24, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, -8, 9));

        if (forceDarkTheme) {
            checkColors();
            navBarColorKey = -1;
        }

        passcodeView = new PasscodeView(context);
        containerView.addView(passcodeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
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
            float y = -commentTextView.getHeight();
            if (mentionContainer.getY() != y) {
                mentionContainer.setTranslationY(y);
                mentionContainer.invalidate();
            }
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
                .setTitle(LocaleController.getString(R.string.BotRemoveFromMenuTitle))
                .setMessage(AndroidUtilities.replaceTags(attachMenuBot != null ? description : LocaleController.formatString("BotRemoveInlineFromMenu", R.string.BotRemoveInlineFromMenu, botName)))
                .setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
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
                .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
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

    public void setEditingMessageObject(MessageObject messageObject) {
        if (editingMessageObject == messageObject) {
            return;
        }
        editingMessageObject = messageObject;
        if (editingMessageObject != null) {
            maxSelectedPhotos = 1;
            allowOrder = false;
        } else {
            maxSelectedPhotos = -1;
            allowOrder = true;
        }
        buttonsAdapter.notifyDataSetChanged();
    }

    public MessageObject getEditingMessageObject() {
        return editingMessageObject;
    }

    protected void applyCaption() {
        if (commentTextView.length() <= 0) {
            return;
        }
        currentAttachLayout.applyCaption(commentTextView.getText());
    }

    private void sendPressed(boolean notify, int scheduleDate) {
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
        if (checkCaption(commentTextView.getText())) {
            return;
        }
        applyCaption();
        buttonPressed = true;
        delegate.didPressedButton(7, true, notify, scheduleDate, false);
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
        int t = currentAttachLayout.getFirstOffset() - AndroidUtilities.dp(11) - scrollOffsetY[0];
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
        };

        if (!(currentAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview || nextAttachLayout instanceof ChatAttachAlertPhotoLayoutPreview)) {
            if (animated) {
                AnimatorSet animator = new AnimatorSet();
                nextAttachLayout.setAlpha(0.0f);
                nextAttachLayout.setTranslationY(AndroidUtilities.dp(78));
                animator.playTogether(
                        ObjectAnimator.ofFloat(currentAttachLayout, View.TRANSLATION_Y, AndroidUtilities.dp(78) + t),
                        ObjectAnimator.ofFloat(currentAttachLayout, ATTACH_ALERT_LAYOUT_TRANSLATION, 0.0f, 1.0f),
                        ObjectAnimator.ofFloat(actionBar, View.ALPHA, actionBar.getAlpha(), 0f)
                );
                animator.setDuration(180);
                animator.setStartDelay(20);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        currentAttachLayout.setAlpha(0.0f);
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
                            onEnd.run();
                            updateSelectedPosition(0);
                        });
                        viewChangeAnimator = springAnimation;
                        springAnimation.start();
                    }
                });
                viewChangeAnimator = animator;
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
            boolean showActionBar = nextAttachLayout.getCurrentItemTop() <= layout.getButtonsHideOffset();
            if (animated) {
                ATTACH_ALERT_LAYOUT_TRANSLATION.set(currentAttachLayout, 0.0f);
                AndroidUtilities.runOnUIThread(() -> {
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
                        mediaPreviewView.setAlpha(mediaPreviewAlpha);
                        selectedView.setAlpha(1f - mediaPreviewAlpha);
                        selectedView.setTranslationX(mediaPreviewAlpha * -AndroidUtilities.dp(16));
                        mediaPreviewView.setTranslationX((1f - mediaPreviewAlpha) * AndroidUtilities.dp(16));
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
            contactsLayout.setDelegate((user, notify, scheduleDate) -> ((ChatActivity) baseFragment).sendContact(user, notify, scheduleDate));
        }
        showLayout(contactsLayout);
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
            audioLayout.setDelegate((audios, caption, notify, scheduleDate) -> {
                if (baseFragment != null && baseFragment instanceof ChatActivity) {
                    ((ChatActivity) baseFragment).sendAudio(audios, caption, notify, scheduleDate);
                } else if (delegate != null) {
                    delegate.sendAudio(audios, caption, notify, scheduleDate);
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
                public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<MessageObject> fmessages, boolean notify, int scheduleDate) {
                    if (documentsDelegate != null) {
                        documentsDelegate.didSelectFiles(files, caption, fmessages, notify, scheduleDate);
                    } else if (baseFragment instanceof ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate) {
                        ((ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate) baseFragment).didSelectFiles(files, caption, fmessages, notify, scheduleDate);
                    } else if (baseFragment instanceof PassportActivity) {
                        ((PassportActivity) baseFragment).didSelectFiles(files, caption, notify, scheduleDate);
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
        if (animated) {
            commentsAnimator = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(frameLayout2, View.ALPHA, show ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, show ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(selectedCountView, View.ALPHA, show ? 1.0f : 0.0f));
            if (actionBar.getTag() != null) {
                animators.add(ObjectAnimator.ofFloat(frameLayout2, View.TRANSLATION_Y, show ? 0.0f : AndroidUtilities.dp(48)));
                animators.add(ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, show ? AndroidUtilities.dp(36) : AndroidUtilities.dp(48 + 36)));
                animators.add(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
            } else if (typeButtonsAvailable) {
                animators.add(ObjectAnimator.ofFloat(buttonsRecyclerView, View.TRANSLATION_Y, show ? AndroidUtilities.dp(36) : 0));
                animators.add(ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, show ? AndroidUtilities.dp(36) : 0));
            } else if (!isSoundPicker) {
                shadow.setTranslationY(AndroidUtilities.dp(36) + botMainButtonOffsetY);
                animators.add(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
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
            writeButtonContainer.setScaleX(show ? 1.0f : 0.2f);
            writeButtonContainer.setScaleY(show ? 1.0f : 0.2f);
            writeButtonContainer.setAlpha(show ? 1.0f : 0.0f);
            selectedCountView.setScaleX(show ? 1.0f : 0.2f);
            selectedCountView.setScaleY(show ? 1.0f : 0.2f);
            selectedCountView.setAlpha(show ? 1.0f : 0.0f);
            if (actionBar.getTag() != null) {
                frameLayout2.setTranslationY(show ? 0.0f : AndroidUtilities.dp(48));
                shadow.setTranslationY((show ? AndroidUtilities.dp(36) : AndroidUtilities.dp(48 + 36)) + botMainButtonOffsetY);
                shadow.setAlpha(show ? 1.0f : 0.0f);
            } else if (typeButtonsAvailable) {
                if (currentAttachLayout == null || currentAttachLayout.shouldHideBottomButtons()) {
                    buttonsRecyclerView.setTranslationY(show ? AndroidUtilities.dp(36) : 0);
                }
                shadow.setTranslationY((show ? AndroidUtilities.dp(36) : 0) + botMainButtonOffsetY);
            } else {
                shadow.setTranslationY(AndroidUtilities.dp(36) + botMainButtonOffsetY);
                shadow.setAlpha(show ? 1.0f : 0.0f);
            }
            if (!show) {
                frameLayout2.setVisibility(View.INVISIBLE);
                writeButtonContainer.setVisibility(View.INVISIBLE);
                if (!typeButtonsAvailable) {
                    shadow.setVisibility(View.INVISIBLE);
                }
            }
        }
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
        appearSpringAnimation.getSpring().setDampingRatio(0.75f);
        appearSpringAnimation.getSpring().setStiffness(350.0f);
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

    protected void makeFocusable(EditTextBoldCursor editText, boolean showKeyboard) {
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

        if (sendPopupLayout != null) {
            for (int a = 0; a < itemCells.length; a++) {
                if (itemCells[a] != null) {
                    itemCells[a].setColors(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon));
                    itemCells[a].setSelectorColor(forceDarkTheme ? getThemedColor(Theme.key_voipgroup_actionBarItemsSelector) : getThemedColor(Theme.key_dialogButtonSelector));
                }
            }
            sendPopupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
            if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                sendPopupLayout.invalidate();
            }
        }

        Theme.setSelectorDrawableColor(writeButtonDrawable, getThemedColor(Theme.key_dialogFloatingButton), false);
        Theme.setSelectorDrawableColor(writeButtonDrawable, getThemedColor(Build.VERSION.SDK_INT >= 21 ? Theme.key_dialogFloatingButtonPressed : Theme.key_dialogFloatingButton), true);
        writeButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));

        actionBarShadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));

        buttonsRecyclerView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        buttonsRecyclerView.setBackgroundColor(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground));

        frameLayout2.setBackgroundColor(getThemedColor(forceDarkTheme ? Theme.key_voipgroup_listViewBackground : Theme.key_dialogBackground));

        selectedCountView.invalidate();

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
        if (id == NotificationCenter.reloadInlineHints || id == NotificationCenter.attachMenuBotsDidLoad) {
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
            t -= AndroidUtilities.dp(13);
            toMove = AndroidUtilities.dp(11);
        } else {
            t -= AndroidUtilities.dp(39);
            toMove = AndroidUtilities.dp(43);
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

        float offset = actionBar.getAlpha() != 0 ? 0.0f : AndroidUtilities.dp(26 * (1.0f - headerView.getAlpha()));
        if (menuShowed && avatarPicker == 0) {
            selectedMenuItem.setTranslationY(scrollOffset - AndroidUtilities.dp(37 + finalMove * moveProgress) + offset + currentPanTranslationY);
        } else {
            selectedMenuItem.setTranslationY(ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(4) - AndroidUtilities.dp(37 + finalMove) + currentPanTranslationY);
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
            searchItem.setTranslationY(ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(4) - AndroidUtilities.dp(37 + finalMove) + currentPanTranslationY);
        }
        headerView.setTranslationY(baseSelectedTextViewTranslationY = scrollOffset - AndroidUtilities.dp(25 + finalMove * moveProgress) + offset + currentPanTranslationY + swapOffset);
        if (pollLayout != null && layout == pollLayout) {
            if (AndroidUtilities.isTablet()) {
                finalMove = 63;
            } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                finalMove = 53;
            } else {
                finalMove = 59;
            }
            doneItem.setTranslationY(Math.max(0, pollLayout.getTranslationY() + scrollOffset - AndroidUtilities.dp(7 + finalMove * moveProgress)) + currentPanTranslationY);
        }
    }

    private void updateActionBarVisibility(boolean show, boolean animated) {
        if (show && actionBar.getTag() == null || !show && actionBar.getTag() != null) {
            actionBar.setTag(show ? 1 : null);
            if (actionBarAnimation != null) {
                actionBarAnimation.cancel();
                actionBarAnimation = null;
            }

            boolean needsSearchItem = searchItem != null && (avatarSearch || currentAttachLayout == photoLayout && !menuShowed && baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).allowSendGifs() && ((ChatActivity) baseFragment).allowSendPhotos());
            boolean needMoreItem = !isPhotoPicker && (avatarPicker != 0 || !menuShowed) && currentAttachLayout == photoLayout && (photosEnabled || videosEnabled);
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
    protected void updateLayout(AttachAlertLayout layout, boolean animated, int dy) {
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
        newOffset += (layoutParams == null ? 0 : layoutParams.topMargin) - AndroidUtilities.dp(11);
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
            selectedCountView.setPivotX(0);
            selectedCountView.setPivotY(0);
            showCommentTextView(false, animated != 0);
        } else {
            selectedCountView.invalidate();
            if (!showCommentTextView(true, animated != 0) && animated != 0) {
                selectedCountView.setPivotX(AndroidUtilities.dp(21));
                selectedCountView.setPivotY(AndroidUtilities.dp(12));
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, animated == 1 ? 1.1f : 0.9f, 1.0f),
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, animated == 1 ? 1.1f : 0.9f, 1.0f));
                animatorSet.setInterpolator(new OvershootInterpolator());
                animatorSet.setDuration(180);
                animatorSet.start();
            } else {
                selectedCountView.setPivotX(0);
                selectedCountView.setPivotY(0);
            }
        }
        currentAttachLayout.onSelectedItemsCountChanged(count);

        if (currentAttachLayout == photoLayout && ((baseFragment instanceof ChatActivity) || avatarPicker != 0) && (count == 0 && menuShowed || (count != 0 || avatarPicker != 0) && !menuShowed)) {
            menuShowed = count != 0 || avatarPicker != 0;
            if (menuAnimator != null) {
                menuAnimator.cancel();
                menuAnimator = null;
            }
            boolean needsSearchItem = searchItem != null && actionBar.getTag() != null && baseFragment instanceof ChatActivity && ((ChatActivity) baseFragment).allowSendGifs();
            if (menuShowed) {
                if (avatarPicker == 0) {
                    selectedMenuItem.setVisibility(View.VISIBLE);
                }
                headerView.setVisibility(View.VISIBLE);
            } else {
                if (actionBar.getTag() != null && searchItem != null) {
                    searchItem.setVisibility(View.VISIBLE);
                }
            }
            if (animated == 0) {
                if (actionBar.getTag() == null && avatarPicker == 0) {
                    selectedMenuItem.setAlpha(menuShowed ? 1.0f : 0.0f);
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
                if (actionBar.getTag() == null && avatarPicker == 0) {
                    animators.add(ObjectAnimator.ofFloat(selectedMenuItem, View.ALPHA, menuShowed ? 1.0f : 0.0f));
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
                            if (actionBar.getTag() == null && avatarPicker == 0) {
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

        if (baseFragment instanceof ChatActivity && avatarPicker != 2) {
            TLRPC.Chat chat = ((ChatActivity) baseFragment).getCurrentChat();
            TLRPC.User user = ((ChatActivity) baseFragment).getCurrentUser();
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
        } else {
            if (allowEnterCaption) {
                commentTextView.setVisibility(View.VISIBLE);
            } else {
                commentTextView.setVisibility(View.INVISIBLE);
            }
        }
        photoLayout.onInit(videosEnabled, photosEnabled, documentsEnabled);
        commentTextView.hidePopup(true);
        enterCommentEventSent = false;
        setFocusable(false);
        ChatAttachAlert.AttachAlertLayout layoutToSet;
        if (isStoryLocationPicker) {
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
        } else if (editingMessageObject != null && (editingMessageObject.isMusic() || (editingMessageObject.isDocument() && !editingMessageObject.isGif()))) {
            if (editingMessageObject.isMusic()) {
                openAudioLayout(false);
                layoutToSet = audioLayout;
                selectedId = 3;
            } else {
                openDocumentsLayout(false);
                layoutToSet = documentLayout;
                selectedId = 4;
            }
            typeButtonsAvailable = !editingMessageObject.hasValidGroupId();
        } else {
            layoutToSet = photoLayout;
            typeButtonsAvailable = avatarPicker == 0;
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
            AttachAlertLayout previousLayout = currentAttachLayout;
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
        }
        if (currentAttachLayout != photoLayout) {
            photoLayout.setCheckCameraWhenShown(true);
        }
        updateCountButton(0);

        buttonsAdapter.notifyDataSetChanged();
        commentTextView.setText("");
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
        destroyed = true;
        if (commentTextView != null) {
            commentTextView.onDestroy();
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
        AndroidUtilities.makeAccessibilityAnnouncement(LocaleController.getString("AccDescrAttachButton", R.string.AccDescrAttachButton));
        openTransitionFinished = true;
    }

    @Override
    public void onOpenAnimationStart() {

    }

    @Override
    public boolean canDismiss() {
        return true;
    }

    @Override
    public void setAllowDrawContent(boolean value) {
        super.setAllowDrawContent(value);
        currentAttachLayout.onContainerTranslationUpdated(currentPanTranslationY);
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
                selectedTextView.setText(LocaleController.getString("ChoosePhotoOrVideo", R.string.ChoosePhotoOrVideo));
            } else {
                selectedTextView.setText(LocaleController.getString("ChoosePhoto", R.string.ChoosePhoto));
            }
        } else {
            typeButtonsAvailable = true;
        }
        if (photoLayout != null) {
            photoLayout.updateAvatarPicker();
        }
    }

    public TextView getSelectedTextView() {
        return selectedTextView;
    }

    public void setSoundPicker() {
        isSoundPicker = true;
        buttonsRecyclerView.setVisibility(View.GONE);
        shadow.setVisibility(View.GONE);
        selectedTextView.setText(LocaleController.getString("ChoosePhotoOrVideo", R.string.ChoosePhotoOrVideo));
    }

    public boolean storyLocationPickerFileIsVideo;
    public File storyLocationPickerPhotoFile;
    public double[] storyLocationPickerLatLong;

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
                        attachButton.setTextAndIcon(1, LocaleController.getString("ChatGallery", R.string.ChatGallery), Theme.chat_attachButtonDrawables[0], Theme.key_chat_attachGalleryBackground, Theme.key_chat_attachGalleryText);
                        attachButton.setTag(1);
                    } else if (position == documentButton) {
                        attachButton.setTextAndIcon(4, LocaleController.getString("ChatDocument", R.string.ChatDocument), Theme.chat_attachButtonDrawables[2], Theme.key_chat_attachFileBackground, Theme.key_chat_attachFileText);
                        attachButton.setTag(4);
                    } else if (position == locationButton) {
                        attachButton.setTextAndIcon(6, LocaleController.getString("ChatLocation", R.string.ChatLocation), Theme.chat_attachButtonDrawables[4], Theme.key_chat_attachLocationBackground, Theme.key_chat_attachLocationText);
                        attachButton.setTag(6);
                    } else if (position == musicButton) {
                        attachButton.setTextAndIcon(3, LocaleController.getString("AttachMusic", R.string.AttachMusic), Theme.chat_attachButtonDrawables[1], Theme.key_chat_attachAudioBackground, Theme.key_chat_attachAudioText);
                        attachButton.setTag(3);
                    } else if (position == pollButton) {
                        attachButton.setTextAndIcon(9, LocaleController.getString("Poll", R.string.Poll), Theme.chat_attachButtonDrawables[5], Theme.key_chat_attachPollBackground, Theme.key_chat_attachPollText);
                        attachButton.setTag(9);
                    } else if (position == contactButton) {
                        attachButton.setTextAndIcon(5, LocaleController.getString("AttachContact", R.string.AttachContact), Theme.chat_attachButtonDrawables[3], Theme.key_chat_attachContactBackground, Theme.key_chat_attachContactText);
                        attachButton.setTag(5);
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
                if ((editingMessageObject.isMusic() || editingMessageObject.isDocument()) && editingMessageObject.hasValidGroupId()) {
                    if (editingMessageObject.isMusic()) {
                        musicButton = buttonsCount++;
                    } else {
                        documentButton = buttonsCount++;
                    }
                } else {
                    galleryButton = buttonsCount++;
                    documentButton = buttonsCount++;
                    musicButton = buttonsCount++;
                }
            } else {
                galleryButton = buttonsCount++;
                if (photosEnabled || videosEnabled) {
                    if (baseFragment instanceof ChatActivity && !((ChatActivity) baseFragment).isInScheduleMode() && !((ChatActivity) baseFragment).isSecretChat()) {
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
                } else if (plainTextEnabled) {
                    contactButton = buttonsCount++;
                }
                musicButton = buttonsCount++;

                TLRPC.User user = baseFragment instanceof ChatActivity ? ((ChatActivity) baseFragment).getCurrentUser() : null;
                if (user != null && user.bot) {
                    contactButton = buttonsCount++;
                }
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
        if (commentTextView != null && commentTextView.isPopupShowing()) {
            commentTextView.hidePopup(true);
            return;
        }
        super.onBackPressed();
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
                            .setTitle(LocaleController.getString("DiscardSelectionAlertTitle", R.string.DiscardSelectionAlertTitle))
                            .setMessage(LocaleController.getString("DiscardSelectionAlertMessage", R.string.DiscardSelectionAlertMessage))
                            .setPositiveButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialogInterface, i) -> {
                                allowPassConfirmationAlert = true;
                                dismiss();
                            })
                            .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
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
        if (commentTextView == null) {
            return;
        }
        try {
            SpannableStringBuilder builder = new SpannableStringBuilder(commentTextView.getText());
            builder.replace(start, start + len, text);
            if (parseEmoji) {
                Emoji.replaceEmoji(builder, commentTextView.getEditText().getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            }
            commentTextView.setText(builder);
            commentTextView.setSelection(start + text.length());
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
        setupMentionContainer();
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
        mentionContainer.setTranslationY(-commentTextView.getHeight());

        setupMentionContainer();
    }

    protected void setupMentionContainer() {
        mentionContainer.getAdapter().setAllowStickers(false);
        mentionContainer.getAdapter().setAllowBots(false);
        mentionContainer.getAdapter().setAllowChats(false);
        mentionContainer.getAdapter().setSearchInDailogs(true);
        if (baseFragment instanceof ChatActivity) {
            mentionContainer.getAdapter().setChatInfo(((ChatActivity) baseFragment).getCurrentChatInfo());
            mentionContainer.getAdapter().setNeedUsernames(((ChatActivity) baseFragment).getCurrentChat() != null);
        } else {
            mentionContainer.getAdapter().setChatInfo(null);
            mentionContainer.getAdapter().setNeedUsernames(false);
        }
        mentionContainer.getAdapter().setNeedBotContext(false);
    }
}
