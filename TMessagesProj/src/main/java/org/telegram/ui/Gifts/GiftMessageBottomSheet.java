package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.getActivity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.HintsController;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.Components.chat.ChatInputViewsContainer;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.chat.WallpaperBitmapProvider;
import org.telegram.ui.Components.inset.WindowAnimatedInsetsProvider;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;
import org.telegram.ui.Stories.recorder.PreviewView;

import me.vkryl.android.animator.FactorAnimator;

public class GiftMessageBottomSheet extends BottomSheet {
    public static final long TRANSITION_DURATION = 350;

    private final SizeNotifierFrameLayout sizeNotifierFrameLayout;
    private final StarGiftUniqueActionView starGiftUniqueActionView;

    private final WallpaperBitmapProvider wallpaperBitmapProvider = new WallpaperBitmapProvider();
    private final @NonNull BlurredBackgroundSourceWrapped navbarContentSourceWallpaper;
    private final @NonNull BlurredBackgroundDrawableViewFactory navbarContentDrawableFactory;

    private final ChatInputViewsContainer chatInputViewsContainer;
    private final FrameLayout chatInputBubbleContainer;
    private final FrameLayout chatInputInAppContainer;
    private final WindowAnimatedInsetsProvider rootAnimatedInsetsListener;
    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::checkInsets);
    private final ChatActivityEnterView commentView;
    private final FrameLayout publicCheckboxButton;
    private final CheckBox2 publicCheckboxView;
    private final TextView previewInChatHeader;
    private final AnimatedTextView captionLimitView;
    private final ImageView closeButton;
    private final Drawable backgroundDrawable;

    private final int captionLimit;
    private int codepointCount;
    private boolean hideMyName;

    private ChatActivityEnterView.SendButton writeButton;

    private final TL_stars.TL_starGiftUnique gift;
    private final long toDialogId;

    public GiftMessageBottomSheet(Context context, Theme.ResourcesProvider resourcesProvider, TL_stars.TL_starGiftUnique gift, long toDialogId) {
        super(context, true, true, null);
        AndroidUtilities.enableEdgeToEdge(getWindow());

        this.gift = gift;
        this.toDialogId = toDialogId;
        this.captionLimit = MessagesController.getInstance(currentAccount).stargiftsMessageLengthMax;

        navbarContentSourceWallpaper = new BlurredBackgroundSourceWrapped();
        navbarContentDrawableFactory = new BlurredBackgroundDrawableViewFactory(navbarContentSourceWallpaper);
        containerView = sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }

            @Override
            protected boolean isStatusBarVisible() {
                return false;
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                checkUi_GiftLayoutPosition();
            }

            @Override
            protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
                if (child == backgroundView) {
                    if (navbarContentSourceWallpaper.getSource() instanceof BlurredBackgroundSourceBitmap) {
                        ((BlurredBackgroundSourceBitmap) navbarContentSourceWallpaper.getSource())
                                .setParentSize(getWidth(), getHeight(), 0);
                    }
                    navbarContentSourceWallpaper.draw(canvas, 0, 0, getWidth(), getHeight());
                    return false;
                } else {
                    return super.drawChild(canvas, child, drawingTime);
                }
            }

            @Override
            protected Drawable getNewDrawable() {
                return backgroundDrawable != null ? backgroundDrawable : super.getNewDrawable();
            }

            @Override
            public void onUpdateBackgroundDrawable(Drawable drawable) {
                super.onUpdateBackgroundDrawable(drawable);
                if (drawable instanceof MotionBackgroundDrawable) {
                    ((MotionBackgroundDrawable) drawable).setFastRenderAllowed();
                }

                final BlurredBackgroundSource source = wallpaperBitmapProvider.updateSourceFromBackgroundViewDrawable(drawable);
                navbarContentSourceWallpaper.setSource(source);
            }
        };
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        navbarContentDrawableFactory.setSourceRootView(new ViewPositionWatcher(containerView), containerView);

        rootAnimatedInsetsListener = new WindowAnimatedInsetsProvider(container);
        windowInsetsStateHolder.setupAnimatedInsetsProvider(rootAnimatedInsetsListener, containerView);

        backgroundDrawable = PreviewView.getBackgroundDrawable(null, currentAccount, toDialogId, Theme.isCurrentThemeDark());
        sizeNotifierFrameLayout.setBackgroundImage(backgroundDrawable, false);

        starGiftUniqueActionView = new StarGiftUniqueActionView(context, currentAccount, resourcesProvider);
        starGiftUniqueActionView.set(gift, UserConfig.getInstance(currentAccount).getClientUserId(), null, LocaleController.getString(R.string.GiftMessageSendNow), false);
        starGiftUniqueActionView.setPadding(0, dp(4), 0, dp(4));
        starGiftUniqueActionView.setLayoutBackground(Theme.createServiceDrawable(AndroidUtilities.dp(18), starGiftUniqueActionView, containerView, getThemedPaint(Theme.key_paint_chatActionBackground)));
        sizeNotifierFrameLayout.addView(starGiftUniqueActionView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        chatInputViewsContainer = new ChatInputViewsContainer(context);
        chatInputViewsContainer.setClipChildren(false);
        chatInputViewsContainer.setWindowInsetsProvider(windowInsetsStateHolder);
        chatInputViewsContainer.setInputIslandBubbleDrawable(
            navbarContentDrawableFactory.create(chatInputViewsContainer, BlurredBackgroundProviderImpl.bottomPanelChatActivity(resourcesProvider)));
        chatInputViewsContainer.setUnderKeyboardBackgroundDrawable(
            navbarContentDrawableFactory.create(chatInputViewsContainer, BlurredBackgroundProviderImpl.bottomPanelChatActivity(resourcesProvider)));
        chatInputBubbleContainer = chatInputViewsContainer.getInputIslandBubbleContainer();
        chatInputBubbleContainer.setClipChildren(false);
        chatInputInAppContainer = chatInputViewsContainer.getInAppKeyboardBubbleContainer();


        commentView = new ChatActivityEnterView(getActivity(), sizeNotifierFrameLayout, null, false) {
            @Override
            protected void onChangedIslandTotalHeight(float h) {
                chatInputViewsContainer.setInputBubbleHeight(h);
                checkUi_GiftLayoutPosition();
            }

            @Override
            public void extendActionMode(Menu menu) {
                ChatActivity.fillActionModeMenu(menu, null, false, false, false, false);
            }
        };
        commentView.setInAppInsetsController(windowInsetsStateHolder);
        commentView.setOverrideHint(LocaleController.getString(R.string.GiftMessageAddHint));
        commentView.shouldDrawBackground = false;
        containerView.setClipChildren(false);
        containerView.setClipToPadding(false);
        commentView.allowBlur = false;
        commentView.forceSmoothKeyboard(true);
        commentView.setAllowStickersAndGifs(true, false, false);
        commentView.setForceShowSendButton(true, false);
        commentView.textFieldContainer.setPadding(0, dp(1), dp(20), 0);
        commentView.getSendButton().setAlpha(0);
        commentView.getEditField().setMaxLines(3);
        commentView.setCustomWindowView(container);

        commentView.setViewParentForEmoji(chatInputInAppContainer);
        chatInputBubbleContainer.addView(commentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 7, 0, 7, 0));
        containerView.addView(chatInputViewsContainer.getFadeView(), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        containerView.addView(chatInputViewsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        commentView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend(CharSequence message, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {

            }

            @Override
            public void onSwitchRecordMode(boolean video) {

            }

            @Override
            public void onTextSelectionChanged(int start, int end) {

            }

            @Override
            public void bottomPanelTranslationYChanged(float translation) {

            }

            @Override
            public void onStickersExpandedChange() {

            }

            @Override
            public void onPreAudioVideoRecord() {

            }

            @Override
            public void onTextChanged(final CharSequence text, boolean bigChange, boolean fromDraft) {
                onTextChangedInternal(text);
            }

            @Override
            public void onTextSpansChanged(CharSequence text) {
                onTextChangedInternal(text);
            }

            private void onTextChangedInternal(CharSequence text) {
                starGiftUniqueActionView.set(gift, UserConfig.getInstance(currentAccount).getClientUserId(), commentView.getTextWithEntities(), LocaleController.getString(R.string.GiftMessageSendNow), true);

                int beforeLimit;
                codepointCount = Character.codePointCount(text, 0, text.length());
                if (captionLimit > 0 && (beforeLimit = captionLimit - codepointCount) <= 15) {
                    if (beforeLimit < -9999) {
                        beforeLimit = -9999;
                    }
                    captionLimitView.setText(LocaleController.formatNumber(beforeLimit, ','), captionLimitView.getVisibility() == View.VISIBLE);
                    if (captionLimitView.getVisibility() != View.VISIBLE) {
                        captionLimitView.setVisibility(View.VISIBLE);
                        captionLimitView.setAlpha(0);
                        captionLimitView.setScaleX(0.5f);
                        captionLimitView.setScaleY(0.5f);
                    }
                    captionLimitView.animate().setListener(null).cancel();
                    captionLimitView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(100).start();
                    if (beforeLimit < 0) {
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
            }

            @Override
            public void needSendTyping() {

            }

            @Override
            public void onAttachButtonHidden() {

            }

            @Override
            public void onAttachButtonShow() {

            }

            @Override
            public void onMessageEditEnd(boolean loading) {

            }

            @Override
            public boolean isVideoRecordingPaused() {
                return false;
            }

            @Override
            public void onWindowSizeChanged(int size) {

            }

            @Override
            public void onStickersTab(boolean opened) {

            }

            @Override
            public void didPressAttachButton() {

            }

            @Override
            public void needStartRecordVideo(int state, boolean notify, int scheduleDate, int scheduleRepeatPeriod, int ttl, long effectId, long stars) {

            }

            @Override
            public void toggleVideoRecordingPause() {

            }

            @Override
            public void needChangeVideoPreviewState(int state, float seekProgress) {

            }

            @Override
            public void needStartRecordAudio(int state) {

            }

            @Override
            public void needShowMediaBanHint() {

            }

            @Override
            public void onUpdateSlowModeButton(View button, boolean show, CharSequence time) {

            }

            @Override
            public void onSendLongClick() {

            }

            @Override
            public void onAudioVideoInterfaceUpdated() {

            }
        });
        ChatActivityEnterView.disableNewLines(commentView.messageEditText);

        captionLimitView = new AnimatedTextView(context);
        captionLimitView.setAllowCancel(true);
        captionLimitView.setScaleProperty(0.6f);
        captionLimitView.setVisibility(View.GONE);
        captionLimitView.setTextSize(dp(15));
        captionLimitView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        captionLimitView.setTypeface(AndroidUtilities.bold());
        captionLimitView.setGravity(Gravity.CENTER);
        containerView.addView(captionLimitView, LayoutHelper.createFrame(56, 20, Gravity.BOTTOM | Gravity.RIGHT, 3, 0, 3, 54));

        writeButton = new ChatActivityEnterView.SendButton(context, R.drawable.send_plane_24, resourcesProvider) {
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
                return false;
            }

            @Override
            public boolean shouldDrawBackground() {
                return true;
            }
        };
        writeButton.setCircleSize(dp(38), dp(38));
        writeButton.setCirclePadding(dp(6), dp(8));
        writeButton.newCounterPos = true;
        containerView.addView(writeButton, LayoutHelper.createFrame(110, 50, Gravity.RIGHT | Gravity.BOTTOM));
        writeButton.setScrimViewBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        writeButton.setOnClickListener(v -> {
            if (captionLimit - codepointCount < 0) {
                AndroidUtilities.shakeView(captionLimitView);
                return;
            }

            if (mCallback != null) {
                mCallback.performSend(commentView.getTextWithEntities(), hideMyName);
            }
        });

        previewInChatHeader = new TextView(context);
        previewInChatHeader.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        previewInChatHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        previewInChatHeader.setTypeface(AndroidUtilities.bold());
        previewInChatHeader.setText(LocaleController.getString(R.string.GiftMessagePreviewInChat));
        previewInChatHeader.setGravity(Gravity.CENTER);
        previewInChatHeader.setPadding(dp(10), 0, dp(10), 0);
        previewInChatHeader.setBackground(Theme.createServiceDrawable(AndroidUtilities.dp(23) / 2, previewInChatHeader, containerView, getThemedPaint(Theme.key_paint_chatActionBackground)));
        containerView.addView(previewInChatHeader, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 23, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        publicCheckboxButton = new FrameLayout(context);
        TextView publicCheckboxTextView = new TextView(context);
        publicCheckboxTextView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        publicCheckboxTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        publicCheckboxTextView.setTypeface(AndroidUtilities.bold());
        publicCheckboxTextView.setText(LocaleController.getString(R.string.GiftMessageMakeMessagePublic));
        publicCheckboxButton.addView(publicCheckboxTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 22 + 14, 0, 14, 0));

        publicCheckboxView = new CheckBox2(context, 18, resourcesProvider);
        publicCheckboxView.getCheckBoxBase().setCuttingCheck(true);
        publicCheckboxView.getCheckBoxBase().checkScale = 0.9f;
        publicCheckboxView.setColor(Theme.key_chat_serviceText, Theme.key_chat_serviceText, Theme.key_checkboxCheck);
        publicCheckboxView.setDrawUnchecked(true);
        publicCheckboxView.setChecked(!hideMyName, false);
        starGiftUniqueActionView.getLayout().setOnButtonClickListener(() -> writeButton.performClick());

        // starGiftUniqueActionView.getLayout().getMessageDrawable().setAvatarVisible(!hideMyName, false);
        publicCheckboxView.setDrawBackgroundAsArc(10);
        publicCheckboxButton.addView(publicCheckboxView, LayoutHelper.createFrame(18, 18, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 0, 0, 0));
        publicCheckboxButton.setBackground(Theme.createServiceDrawable(AndroidUtilities.dp(16), publicCheckboxButton, containerView, getThemedPaint(Theme.key_paint_chatActionBackground)));
        publicCheckboxButton.setOnClickListener(v -> {
            hideMyName = !hideMyName;
            publicCheckboxView.setChecked(!hideMyName, true);
            //starGiftUniqueActionView.getLayout().getMessageDrawable().setAvatarVisible(!hideMyName, true);
        });
        containerView.addView(publicCheckboxButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageResource(R.drawable.ic_close_white);
        closeButton.setBackground(Blur3Utils.wrapCenteredDrawable(
            Theme.createServiceDrawable(AndroidUtilities.dp(16), closeButton, containerView, getThemedPaint(Theme.key_paint_chatActionBackground)),
            dp(32), dp(32)));
        closeButton.setOnClickListener(v -> dismiss());
        containerView.addView(closeButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        ScaleStateListAnimator.apply(publicCheckboxButton, 0.05f, 1.2f);
        ScaleStateListAnimator.apply(closeButton);

        ViewCompat.setOnApplyWindowInsetsListener(containerView, this::onApplyWindowInsets);
    }

    protected Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        setAllowNestedScroll(false);
        Bulletin.addDelegate(container, new Bulletin.Delegate() {
            @Override
            public void onShow(Bulletin bulletin) {
                bulletin.getLayout().setCustomBackground(navbarContentDrawableFactory
                    .create(bulletin.getLayout(), true)
                    .setColorProvider(BlurredBackgroundProviderImpl.bulletin(resourcesProvider))
                    .setRadius(dp(16)));
            }

            @Override
            public int getTopOffset(int tag) {
                return AndroidUtilities.statusBarHeight;
            }
        });

        if (HintsController.Hint.GiftMessageHint.show()) {
            HintsController.Hint.GiftMessageHint.increment();
            final TLObject toObject = MessagesController.getInstance(currentAccount).getUserOrChat(toDialogId);
            final String giftName = gift.title + " #" + LocaleController.formatNumber(gift.num, ',');

            BulletinFactory.of(container, resourcesProvider).createUsersBulletin(toObject,
                            LocaleController.getString(R.string.GiftMessageAddTitle),
                            AndroidUtilities.replaceTags(LocaleController.formatString(R.string.GiftMessageAddDescription, DialogObject.getShortName(toObject), giftName)))
                    .show(true);
        }
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        windowInsetsStateHolder.setInsets(insets);

        return WindowInsetsCompat.CONSUMED;
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    @Override
    public void onBackPressed() {
        if (commentView != null && commentView.isPopupShowing()) {
            commentView.hidePopup(true);
            return;
        }
        super.onBackPressed();
    }

    private void checkInsets() {
        if (chatInputViewsContainer != null) {
            chatInputViewsContainer.checkInsets();
        }
        if (writeButton != null) {
            writeButton.setTranslationY(-windowInsetsStateHolder.getAnimatedMaxBottomInset());
        }
        if (captionLimitView != null) {
            captionLimitView.setTranslationY(-windowInsetsStateHolder.getAnimatedMaxBottomInset());
        }
        checkUi_GiftLayoutPosition();
    }

    private void checkUi_GiftLayoutPosition() {
        final int flags = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();
        final int topBase = windowInsetsStateHolder.getInsets(flags).top;
        final float top = topBase + dp(36);
        final float bottomBase = windowInsetsStateHolder.getAnimatedMaxBottomInset() + dp(9) + chatInputViewsContainer.getInputBubbleHeight();


        final float translationY = (containerView.getHeight() - starGiftUniqueActionView.getHeight()) / 2f
            + (top - (bottomBase + dp(7 + 32 + 7))) / 2f;

        final float maxTranslationY = (containerView.getHeight() - bottomBase) - dp(14) - publicCheckboxButton.getHeight() - dp(10) - starGiftUniqueActionView.getHeight();

        starGiftUniqueActionView.setTranslationY(Math.min(translationY, maxTranslationY));
        starGiftUniqueActionView.invalidate();

        previewInChatHeader.setTranslationY(starGiftUniqueActionView.getY() - dp(33));
        previewInChatHeader.invalidate();

        publicCheckboxButton.setTranslationY(-(bottomBase + dp(14)));
        publicCheckboxButton.invalidate();

        closeButton.setTranslationY(topBase);
        closeButton.invalidate();
    }

    public void setLoading(boolean loading) {
        if (mLoading != loading) {
            mLoading = loading;
            writeButton.setLoading(loading, ChatActivityEnterView.SendButton.INFINITE_LOADING);
        }
    }

    public boolean isLoading() {
        return mLoading;
    }


    private Callback mCallback;
    private boolean mLoading;

    public interface Callback {
        void performSend(TLRPC.TL_textWithEntities message, boolean hideMyName);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }
}
