/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.WebFile;
import org.telegram.messenger.utils.tlutils.TLKeyboardHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PlayingGameDrawable;
import org.telegram.ui.Components.PopupAudioView;
import org.telegram.ui.Components.RecordStatusDrawable;
import org.telegram.ui.Components.RoundStatusDrawable;
import org.telegram.ui.Components.SendingFileDrawable;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.StatusDrawable;
import org.telegram.ui.Components.TypingDotsDrawable;
import org.telegram.ui.Components.inset.InAppKeyboardInsetView;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;

import java.io.File;
import java.util.ArrayList;

public class PopupNotificationActivity extends Activity implements NotificationCenter.NotificationCenterDelegate {

    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::fixLayout);
    private SizeNotifierFrameLayout contentView = null;
    private LinearLayout headerView = null;
    private LinearLayout navigationView = null;
    private ImageView closeButton = null;
    private ImageView previousButton = null;
    private ImageView nextButton = null;
    private TextView dateTextView = null;
    private ChatActivityEnterView chatActivityEnterView;
    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private TextView onlineTextView;
    private FrameLayout avatarContainer;
    private TextView countText;
    private ViewGroup messageContainer;
    private ViewGroup centerView;
    private ViewGroup leftView;
    private ViewGroup rightView;

    private ViewGroup centerButtonsView;
    private ViewGroup leftButtonsView;
    private ViewGroup rightButtonsView;

    private FrameLayout popupContainer;
    private ArrayList<ViewGroup> textViews = new ArrayList<>();
    private ArrayList<ViewGroup> imageViews = new ArrayList<>();
    private ArrayList<ViewGroup> audioViews = new ArrayList<>();
    private VelocityTracker velocityTracker = null;
    private StatusDrawable[] statusDrawables = new StatusDrawable[5];

    private int classGuid;
    private int lastResumedAccount = -1;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private boolean finished = false;
    private CharSequence lastPrintString;
    private MessageObject currentMessageObject = null;
    private MessageObject[] setMessageObjects = new MessageObject[3];
    private int currentMessageNum = 0;
    private PowerManager.WakeLock wakeLock = null;
    private boolean animationInProgress = false;
    private long animationStartTime = 0;
    private float moveStartX = -1;
    private boolean startedMoving = false;
    private Runnable onAnimationEndRunnable = null;
    private AnimatorSet transitionAnimation = null;
    private boolean isReply;
    private ArrayList<MessageObject> popupMessages = new ArrayList<>();

    private class FrameLayoutTouch extends FrameLayout {
        public FrameLayoutTouch(Context context) {
            super(context);
        }

        public FrameLayoutTouch(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public FrameLayoutTouch(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return checkTransitionAnimation() || ((PopupNotificationActivity) getContext()).onTouchEventMy(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            return checkTransitionAnimation() || ((PopupNotificationActivity) getContext()).onTouchEventMy(ev);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            ((PopupNotificationActivity) getContext()).onTouchEventMy(null);
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.createDialogsResources(this);
        Theme.createChatResources(this, false);

        AndroidUtilities.fillStatusBarHeight(this, false);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.updateInterfaces);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).addObserver(this, NotificationCenter.contactsDidLoad);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pushMessagesUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        classGuid = ConnectionsManager.generateClassGuid();

        statusDrawables[0] = new TypingDotsDrawable(false);
        statusDrawables[1] = new RecordStatusDrawable(false);
        statusDrawables[2] = new SendingFileDrawable(false);
        statusDrawables[3] = new PlayingGameDrawable(false, null);
        statusDrawables[4] = new RoundStatusDrawable(false);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        contentView = new SizeNotifierFrameLayout(this) {
            private final Rect recordedAudioBounds = new Rect();

            @Override
            public int measureKeyboardHeight() {
                return Math.max(0, windowInsetsStateHolder.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        - windowInsetsStateHolder.getCurrentNavigationBarInset());
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);
                Insets insets = windowInsetsStateHolder.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                int availableWidth = Math.max(0, width - insets.left - insets.right);
                int availableHeight = Math.max(0, height - insets.top - Math.round(windowInsetsStateHolder.getAnimatedMaxBottomInset()) - dp(32));
                popupContainer.measure(
                        MeasureSpec.makeMeasureSpec(Math.min(dp(440), Math.max(0, availableWidth - dp(32))), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (child == popupContainer || child.getVisibility() == GONE) {
                        continue;
                    }
                    if (chatActivityEnterView.isPopupView(child)) {
                        child.setBackgroundColor(Theme.getColor(Theme.key_chat_emojiPanelBackground));
                        child.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(windowInsetsStateHolder.getInAppKeyboardRecommendedViewHeight(), MeasureSpec.EXACTLY));
                    } else if (chatActivityEnterView.isRecordCircleOrControlsView(child)) {
                        child.measure(MeasureSpec.makeMeasureSpec(chatActivityEnterView.getMeasuredWidth(), MeasureSpec.EXACTLY),
                                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                Insets insets = windowInsetsStateHolder.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                int availableWidth = getMeasuredWidth() - insets.left - insets.right;
                int availableHeight = getMeasuredHeight() - insets.top - Math.round(windowInsetsStateHolder.getAnimatedMaxBottomInset());
                int popupLeft = insets.left + (availableWidth - popupContainer.getMeasuredWidth()) / 2;
                int popupTop = insets.top + Math.max(0, (availableHeight - popupContainer.getMeasuredHeight()) / 2);
                popupContainer.layout(popupLeft, popupTop,
                        popupLeft + popupContainer.getMeasuredWidth(), popupTop + popupContainer.getMeasuredHeight());
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (child == popupContainer || child.getVisibility() == GONE) {
                        continue;
                    }
                    int childLeft = insets.left;
                    int childTop = getMeasuredHeight() - child.getMeasuredHeight();
                    if (chatActivityEnterView.isPopupView(child)) {
                        childTop = getMeasuredHeight() - Math.round(windowInsetsStateHolder.getAnimatedImeBottomInset());
                        if (child instanceof InAppKeyboardInsetView insetView) {
                            insetView.applyNavigationBarHeight(windowInsetsStateHolder.getCurrentNavigationBarInset());
                            insetView.applyInAppKeyboardAnimatedHeight(windowInsetsStateHolder.getAnimatedImeBottomInset());
                        }
                    } else if (chatActivityEnterView.isRecordCircleOrControlsView(child)) {
                        childLeft = popupLeft + chatActivityEnterView.getLeft();
                        childTop = popupTop + chatActivityEnterView.getBottom() - child.getMeasuredHeight();
                    } else if (child == chatActivityEnterView.recordedAudioPanel) {
                        chatActivityEnterView.messageEditTextContainer.getDrawingRect(recordedAudioBounds);
                        offsetDescendantRectToMyCoords(chatActivityEnterView.messageEditTextContainer, recordedAudioBounds);
                        childLeft = recordedAudioBounds.left;
                        childTop = recordedAudioBounds.bottom - child.getMeasuredHeight();
                    }
                    child.layout(childLeft, childTop, childLeft + child.getMeasuredWidth(), childTop + child.getMeasuredHeight());
                }
                notifyHeightChanged();
            }

        };
        contentView.setBackgroundColor(0x99000000);
        setContentView(contentView);
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (view, insets) -> {
            windowInsetsStateHolder.setInsets(insets);
            return WindowInsetsCompat.CONSUMED;
        });

        popupContainer = new FrameLayout(this) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
                int composerWidthSpec = MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(24)), MeasureSpec.EXACTLY);
                chatActivityEnterView.measure(composerWidthSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
                int composerHeight = chatActivityEnterView.getMeasuredHeight();
                int headerHeightLimit = Math.max(0, maxHeight - composerHeight - dp(12));
                headerView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(headerHeightLimit, MeasureSpec.AT_MOST));
                int fixedHeight = headerView.getMeasuredHeight() + composerHeight + dp(12);
                int remainingHeight = Math.max(0, maxHeight - fixedHeight);
                navigationView.setVisibility(popupMessages.size() > 1 && remainingHeight >= dp(96) ? VISIBLE : GONE);
                if (navigationView.getVisibility() == VISIBLE) {
                    navigationView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
                    fixedHeight += navigationView.getMeasuredHeight();
                }
                dateTextView.setVisibility(maxHeight - fixedHeight >= dp(64) ? VISIBLE : GONE);
                if (dateTextView.getVisibility() == VISIBLE) {
                    dateTextView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight - fixedHeight, MeasureSpec.AT_MOST));
                    fixedHeight += dateTextView.getMeasuredHeight();
                }
                int messageHeightLimit = Math.min(dp(320), Math.max(0, maxHeight - fixedHeight));
                int messageHeight = getMessageHeight(width, messageHeightLimit);
                messageContainer.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(messageHeight, MeasureSpec.EXACTLY));
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (child.getTag() instanceof String) {
                        child.measure(composerWidthSpec, MeasureSpec.makeMeasureSpec(composerHeight, MeasureSpec.EXACTLY));
                    }
                }
                setMeasuredDimension(width, Math.min(maxHeight, fixedHeight + messageHeight));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int width = getMeasuredWidth();
                headerView.layout(0, 0, width, headerView.getMeasuredHeight());
                int y = headerView.getBottom();
                messageContainer.layout(0, y, width, y + messageContainer.getMeasuredHeight());
                y = messageContainer.getBottom();
                if (dateTextView.getVisibility() == VISIBLE) {
                    dateTextView.layout(0, y, width, y + dateTextView.getMeasuredHeight());
                    y = dateTextView.getBottom();
                }
                if (navigationView.getVisibility() == VISIBLE) {
                    navigationView.layout(0, y, width, y + navigationView.getMeasuredHeight());
                }
                int composerBottom = getMeasuredHeight() - dp(12);
                chatActivityEnterView.layout(dp(12), composerBottom - chatActivityEnterView.getMeasuredHeight(),
                        width - dp(12), composerBottom);
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    if (child.getTag() instanceof String) {
                        child.layout(chatActivityEnterView.getLeft(), chatActivityEnterView.getTop(),
                                chatActivityEnterView.getRight(), chatActivityEnterView.getBottom());
                    }
                }
                if (!animationInProgress) {
                    applyViewsLayoutParams(startedMoving && centerView != null ? (int) centerView.getTranslationX() : 0);
                }
            }

        };
        popupContainer.setClipToOutline(true);
        popupContainer.setElevation(dp(8));
        contentView.addView(popupContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        chatActivityEnterView = new ChatActivityEnterView(this, contentView, null, false);
        chatActivityEnterView.shouldDrawBackground = false;
        chatActivityEnterView.setInAppInsetsController(windowInsetsStateHolder);
        chatActivityEnterView.setOverrideHint(LocaleController.getString(R.string.Reply));
        popupContainer.addView(chatActivityEnterView);
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend(CharSequence message, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long payStars) {
                if (currentMessageObject == null) {
                    return;
                }
                if (currentMessageNum >= 0 && currentMessageNum < popupMessages.size()) {
                    popupMessages.remove(currentMessageNum);
                }
                MessagesController.getInstance(currentMessageObject.currentAccount).markDialogAsRead(currentMessageObject.getDialogId(), currentMessageObject.getId(), Math.max(0, currentMessageObject.getId()), currentMessageObject.messageOwner.date, true, 0, 0, true, 0);
                currentMessageObject = null;
                getNewMessage();
            }

            @Override
            public void onTextChanged(CharSequence text, boolean big, boolean fromDraft) {

            }

            @Override
            public void onTextSelectionChanged(int start, int end) {

            }

            @Override
            public void onTextSpansChanged(CharSequence text) {

            }

            @Override
            public void onStickersExpandedChange() {

            }

            @Override
            public void onSwitchRecordMode(boolean video) {

            }

            @Override
            public void onPreAudioVideoRecord() {
                cancelTransitionAnimation();
            }

            @Override
            public void onMessageEditEnd(boolean loading) {

            }

            @Override
            public void needSendTyping() {
                if (currentMessageObject != null) {
                    MessagesController.getInstance(currentMessageObject.currentAccount).sendTyping(currentMessageObject.getDialogId(), 0, 0, classGuid);
                }
            }

            @Override
            public void onAttachButtonHidden() {

            }

            @Override
            public void onAttachButtonShow() {

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
            public boolean isVideoRecordingPaused() {
                return false;
            }

            @Override
            public void needStartRecordAudio(int state) {

            }

            @Override
            public void needChangeVideoPreviewState(int state, float seekProgress) {

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

        messageContainer = new FrameLayoutTouch(this);
        popupContainer.addView(messageContainer, 0);

        headerView = new LinearLayout(this);
        headerView.setGravity(Gravity.CENTER_VERTICAL);
        headerView.setPadding(dp(16), dp(12), dp(8), dp(4));
        popupContainer.addView(headerView);

        avatarContainer = new FrameLayout(this);
        avatarContainer.setMinimumHeight(dp(48));
        avatarContainer.setContentDescription(LocaleController.getString(R.string.AccDescrGoToMessage));
        avatarContainer.setOnClickListener(view -> openCurrentMessage());
        headerView.addView(avatarContainer, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
        avatarImageView = new BackupImageView(this);
        avatarImageView.setRoundRadius(dp(21));
        avatarContainer.addView(avatarImageView, LayoutHelper.createFrame(42, 42, Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams labelsParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        labelsParams.setMarginStart(dp(54));
        avatarContainer.addView(labels, labelsParams);
        nameTextView = new TextView(this);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity(Gravity.START);
        labels.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        onlineTextView = new TextView(this);
        onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        onlineTextView.setSingleLine(true);
        onlineTextView.setEllipsize(TextUtils.TruncateAt.END);
        onlineTextView.setGravity(Gravity.START);
        labels.addView(onlineTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        closeButton = createIconButton(R.drawable.ic_close_white, R.string.Close, () -> {
            onFinish();
            finish();
        });
        headerView.addView(closeButton, LayoutHelper.createLinear(48, 48));
        createNavigation();
        dateTextView = new TextView(this);
        dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dateTextView.setGravity(Gravity.START);
        dateTextView.setPadding(dp(20), 0, dp(20), dp(12));
        popupContainer.addView(dateTextView);
        updateColors();
        ViewCompat.requestApplyInsets(contentView);

        PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "screen");
        wakeLock.setReferenceCounted(false);

        handleIntent(getIntent());
    }

    private int getMessageHeight(int width, int maxHeight) {
        if (centerView == null) {
            return 0;
        }
        TextView messageText = centerView.findViewWithTag(301);
        if (messageText != null) {
            messageText.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(40)), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(Math.max(0, maxHeight - dp(24)), View.MeasureSpec.AT_MOST));
            return Math.min(maxHeight, messageText.getMeasuredHeight() + dp(24));
        }
        return Math.min(maxHeight, dp((Integer) centerView.getTag() == 3 ? 88 : 220));
    }

    private void createNavigation() {
        navigationView = new LinearLayout(this);
        navigationView.setGravity(Gravity.CENTER_VERTICAL);
        navigationView.setPadding(dp(8), 0, dp(8), 0);
        popupContainer.addView(navigationView);
        previousButton = createIconButton(R.drawable.msg_arrow_back, R.string.AccDescrPrevious, () -> switchMessage(-1));
        navigationView.addView(previousButton, LayoutHelper.createLinear(48, 48));
        countText = new TextView(this);
        countText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        countText.setGravity(Gravity.CENTER);
        navigationView.addView(countText, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f));
        nextButton = createIconButton(R.drawable.msg_arrow_back, R.string.Next, () -> switchMessage(1));
        nextButton.setRotation(180);
        navigationView.addView(nextButton, LayoutHelper.createLinear(48, 48));
    }

    private ImageView createIconButton(int icon, int description, Runnable action) {
        ImageView button = new ImageView(this);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setImageResource(icon);
        button.setContentDescription(LocaleController.getString(description));
        button.setFocusable(true);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void updateMessageCount() {
        countText.setText(String.format(LocaleController.getInstance().getCurrentLocale(), "%d / %d", currentMessageNum + 1, popupMessages.size()));
        popupContainer.requestLayout();
    }

    private void updateColors() {
        int backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        int secondaryTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        popupContainer.setBackground(Theme.createRoundRectDrawable(dp(26), backgroundColor));
        updateTitleColor();
        onlineTextView.setTextColor(secondaryTextColor);
        dateTextView.setTextColor(secondaryTextColor);
        countText.setTextColor(secondaryTextColor);
        for (ImageView button : new ImageView[]{closeButton, previousButton, nextButton}) {
            button.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN);
            button.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
        }
        for (StatusDrawable drawable : statusDrawables) {
            drawable.setColor(secondaryTextColor);
        }
        chatActivityEnterView.updateColors();
        chatActivityEnterView.messageEditTextContainer.setBackground(Theme.createRoundRectDrawable(dp(24), Theme.getColor(Theme.key_dialogSearchBackground)));
        EditTextCaption editField = chatActivityEnterView.getEditField();
        int hintColor = Theme.getColor(Theme.key_chat_messagePanelHint);
        editField.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        editField.setHintTextColor(hintColor);
        editField.setHintColor(hintColor);
        editField.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
        if (chatActivityEnterView.getEmojiButton() instanceof ImageView emojiButton) {
            emojiButton.setColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.SRC_IN);
        }
        if (chatActivityEnterView.getEmojiView() != null) {
            chatActivityEnterView.getEmojiView().updateColors();
        }
        for (int i = 0; i < popupContainer.getChildCount(); i++) {
            if (popupContainer.getChildAt(i) instanceof LinearLayout buttons && buttons.getTag() instanceof String) {
                buttons.setBackground(Theme.createRoundRectDrawable(dp(24), backgroundColor));
                for (int j = 0; j < buttons.getChildCount(); j++) {
                    if (buttons.getChildAt(j) instanceof TextView button) {
                        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                        button.setBackground(Theme.getSelectorDrawable(true));
                    }
                }
            }
        }
        for (int i = 0; i < messageContainer.getChildCount(); i++) {
            View view = messageContainer.getChildAt(i);
            TextView text = view.findViewWithTag((Integer) view.getTag() == 1 ? 301 : 312);
            if (text != null) {
                text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                text.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            }
            view.invalidate();
        }
        contentView.requestLayout();
        contentView.invalidate();
    }

    private void updateTitleColor() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        nameTextView.setTextColor(color);
        for (Drawable drawable : nameTextView.getCompoundDrawables()) {
            if (drawable != null) {
                drawable.mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    private boolean hasPendingAudio() {
        return chatActivityEnterView.isRecordingAudioVideo()
                || chatActivityEnterView.recordedAudioPanel != null
                && chatActivityEnterView.recordedAudioPanel.getVisibility() == View.VISIBLE;
    }

    private void cancelTransitionAnimation() {
        onAnimationEndRunnable = null;
        animationInProgress = false;
        if (transitionAnimation != null) {
            transitionAnimation.cancel();
            transitionAnimation = null;
        }
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        startedMoving = false;
        moveStartX = -1;
        AndroidUtilities.unlockOrientation(this);
        if (messageContainer != null) {
            applyViewsLayoutParams(0);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        cancelTransitionAnimation();
        AndroidUtilities.checkDisplaySize(this, newConfig);
        AndroidUtilities.setPreferredMaxRefreshRate(getWindow());
        fixLayout();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 3) {
            if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(LocaleController.getString(R.string.AppName));
            builder.setMessage(LocaleController.getString(R.string.PermissionNoAudioWithHint));
            builder.setNegativeButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialog, which) -> {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            builder.show();
        }
    }

    private void switchMessage(int direction) {
        int messageCount = popupMessages.size();
        if (messageCount <= 1 || animationInProgress || hasPendingAudio()) {
            return;
        }
        currentMessageNum = (currentMessageNum + direction + messageCount) % messageCount;
        currentMessageObject = popupMessages.get(currentMessageNum);
        updateInterfaceForCurrentMessage(direction > 0 ? 2 : 1);
        updateMessageCount();
    }

    public boolean checkTransitionAnimation() {
        if (animationInProgress && animationStartTime < System.currentTimeMillis() - 400) {
            animationInProgress = false;
            if (onAnimationEndRunnable != null) {
                onAnimationEndRunnable.run();
                onAnimationEndRunnable = null;
            }
        }
        return animationInProgress;
    }

    public boolean onTouchEventMy(MotionEvent motionEvent) {
        if (checkTransitionAnimation() || currentMessageObject == null || hasPendingAudio()) {
            return false;
        }
        if (motionEvent != null && motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            moveStartX = motionEvent.getX();
        } else if (motionEvent != null && motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
            float x = motionEvent.getX();
            int diff = (int) (x - moveStartX);
            if (moveStartX != -1 && !startedMoving) {
                if (popupMessages.size() > 1 && Math.abs(diff) > AndroidUtilities.dp(10)) {
                    startedMoving = true;
                    moveStartX = x;
                    AndroidUtilities.lockOrientation(this);
                    diff = 0;
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    } else {
                        velocityTracker.clear();
                    }
                }
            }
            if (startedMoving) {
                if (leftView == null && diff > 0) {
                    diff = 0;
                }
                if (rightView == null && diff < 0) {
                    diff = 0;
                }
                if (velocityTracker != null) {
                    velocityTracker.addMovement(motionEvent);
                }
                applyViewsLayoutParams(diff);
            }
        } else if (motionEvent == null || motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
            if (motionEvent != null && motionEvent.getActionMasked() == MotionEvent.ACTION_UP && startedMoving) {
                int diff = (int) (motionEvent.getX() - moveStartX);
                int width = popupContainer.getMeasuredWidth();
                float moveDiff = 0;
                int forceMove = 0;
                View otherView = null;
                View otherButtonsView = null;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    if (velocityTracker.getXVelocity() >= 3500) {
                        forceMove = 1;
                    } else if (velocityTracker.getXVelocity() <= -3500) {
                        forceMove = 2;
                    }
                }
                if ((forceMove == 1 || diff > width / 3) && leftView != null) {
                    moveDiff = width - centerView.getTranslationX();
                    otherView = leftView;
                    otherButtonsView = leftButtonsView;
                    onAnimationEndRunnable = () -> {
                        animationInProgress = false;
                        switchMessage(-1);
                        AndroidUtilities.unlockOrientation(PopupNotificationActivity.this);
                    };
                } else if ((forceMove == 2 || diff < -width / 3) && rightView != null) {
                    moveDiff = -width - centerView.getTranslationX();
                    otherView = rightView;
                    otherButtonsView = rightButtonsView;
                    onAnimationEndRunnable = () -> {
                        animationInProgress = false;
                        switchMessage(1);
                        AndroidUtilities.unlockOrientation(PopupNotificationActivity.this);
                    };
                } else if (centerView.getTranslationX() != 0) {
                    moveDiff = -centerView.getTranslationX();
                    otherView = diff > 0 ? leftView : rightView;
                    otherButtonsView = diff > 0 ? leftButtonsView : rightButtonsView;
                    onAnimationEndRunnable = () -> {
                        animationInProgress = false;
                        applyViewsLayoutParams(0);
                        AndroidUtilities.unlockOrientation(PopupNotificationActivity.this);
                    };
                }
                if (moveDiff != 0) {
                    int time = (int) (Math.abs(moveDiff / (float) width) * 200);
                    ArrayList<Animator> animators = new ArrayList<>();
                    animators.add(ObjectAnimator.ofFloat(centerView, "translationX", centerView.getTranslationX() + moveDiff));
                    if (centerButtonsView != null) {
                        animators.add(ObjectAnimator.ofFloat(centerButtonsView, "translationX", centerButtonsView.getTranslationX() + moveDiff));
                    }
                    if (otherView != null) {
                        animators.add(ObjectAnimator.ofFloat(otherView, "translationX", otherView.getTranslationX() + moveDiff));
                    }
                    if (otherButtonsView != null) {
                        animators.add(ObjectAnimator.ofFloat(otherButtonsView, "translationX", otherButtonsView.getTranslationX() + moveDiff));
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(animators);
                    animatorSet.setDuration(time);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            transitionAnimation = null;
                            if (onAnimationEndRunnable != null) {
                                onAnimationEndRunnable.run();
                                onAnimationEndRunnable = null;
                            }
                        }
                    });
                    transitionAnimation = animatorSet;
                    animationInProgress = true;
                    animationStartTime = System.currentTimeMillis();
                    animatorSet.start();
                }
            } else {
                applyViewsLayoutParams(0);
            }
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
            if (!animationInProgress) {
                AndroidUtilities.unlockOrientation(this);
            }
            startedMoving = false;
            moveStartX = -1;
        }
        return startedMoving;
    }

    private void applyViewsLayoutParams(int xOffset) {
        int width = popupContainer.getMeasuredWidth();
        if (leftView != null) {
            leftView.setTranslationX(-width + xOffset);
        }
        if (leftButtonsView != null) {
            leftButtonsView.setTranslationX(-width + xOffset);
        }
        if (centerView != null) {
            centerView.setTranslationX(xOffset);
        }
        if (centerButtonsView != null) {
            centerButtonsView.setTranslationX(xOffset);
        }
        if (rightView != null) {
            rightView.setTranslationX(width + xOffset);
        }
        if (rightButtonsView != null) {
            rightButtonsView.setTranslationX(width + xOffset);
        }
        messageContainer.invalidate();
    }

    private LinearLayout getButtonsViewForMessage(int num, boolean applyOffset) {
        if (popupMessages.size() == 1 && (num < 0 || num >= popupMessages.size())) {
            return null;
        }
        if (num == -1) {
            num = popupMessages.size() - 1;
        } else if (num == popupMessages.size()) {
            num = 0;
        }
        LinearLayout view = null;
        final MessageObject messageObject = popupMessages.get(num);
        int buttonsCount = 0;

        TLRPC.ReplyMarkup markup = messageObject.messageOwner.reply_markup;

        if (messageObject.getDialogId() == 777000 && markup instanceof TLRPC.TL_replyInlineMarkup) {
            final TLRPC.TL_replyInlineMarkup replyInlineMarkup = (TLRPC.TL_replyInlineMarkup) markup;
            ArrayList<TL_keyboard.KeyboardInlineButtonRow> rows = replyInlineMarkup.rows;
            for (int a = 0, size = rows.size(); a < size; a++) {
                TL_keyboard.KeyboardInlineButtonRow row = rows.get(a);
                for (int b = 0, size2 = row.buttons.size(); b < size2; b++) {
                    TL_keyboard.KeyboardInlineButton button = row.buttons.get(b);
                    if (TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeCallback.class)) {
                        buttonsCount++;
                    }
                }
            }
        }

        final int account = messageObject.currentAccount;
        if (buttonsCount > 0 && markup instanceof TLRPC.TL_replyInlineMarkup) {
            final TLRPC.TL_replyInlineMarkup replyInlineMarkup = (TLRPC.TL_replyInlineMarkup) markup;
            ArrayList<TL_keyboard.KeyboardInlineButtonRow> rows = replyInlineMarkup.rows;
            for (int a = 0, size = rows.size(); a < size; a++) {
                TL_keyboard.KeyboardInlineButtonRow row = rows.get(a);
                for (int b = 0, size2 = row.buttons.size(); b < size2; b++) {
                    TL_keyboard.KeyboardInlineButton button = row.buttons.get(b);
                    if (TLKeyboardHelper.isType(button, TL_keyboard.TL_inlineButtonTypeCallback.class)) {
                        if (view == null) {
                            view = new LinearLayout(this);
                            view.setOrientation(LinearLayout.HORIZONTAL);
                            view.setBackground(Theme.createRoundRectDrawable(dp(24), Theme.getColor(Theme.key_windowBackgroundWhite)));
                            view.setWeightSum(100);
                            view.setTag("b");
                            view.setOnTouchListener((v, event) -> true);
                        }

                        TextView textView = new TextView(this);
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                        textView.setTypeface(AndroidUtilities.bold());
                        textView.setText(button.text.toUpperCase());
                        textView.setTag(button);
                        textView.setGravity(Gravity.CENTER);
                        textView.setBackgroundDrawable(Theme.getSelectorDrawable(true));
                        view.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 100.0f / buttonsCount));
                        textView.setOnClickListener(v -> {
                            TL_keyboard.KeyboardButtonProto button1 = (TL_keyboard.KeyboardButtonProto) v.getTag();
                            if (button1 != null) {
                                SendMessagesHelper.getInstance(account).sendNotificationCallback(messageObject.getDialogId(), messageObject.getId(), button1.getData());
                            }
                        });
                    }
                }
            }
        }

        if (view != null) {
            int width = popupContainer.getMeasuredWidth();
            FrameLayout.LayoutParams layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM);

            if (applyOffset) {
                if (num == currentMessageNum) {
                    view.setTranslationX(0);
                } else if (num == currentMessageNum - 1) {
                    view.setTranslationX(-width);
                } else if (num == currentMessageNum + 1) {
                    view.setTranslationX(width);
                }
            }
            popupContainer.addView(view, layoutParams);
        }

        return view;
    }

    private ViewGroup getViewForMessage(int num, boolean applyOffset) {
        if (popupMessages.size() == 1 && (num < 0 || num >= popupMessages.size())) {
            return null;
        }
        if (num == -1) {
            num = popupMessages.size() - 1;
        } else if (num == popupMessages.size()) {
            num = 0;
        }
        ViewGroup view;
        MessageObject messageObject = popupMessages.get(num);
        if ((messageObject.type == MessageObject.TYPE_PHOTO || messageObject.type == MessageObject.TYPE_GEO) && !messageObject.isSecretMedia()) {
            if (imageViews.size() > 0) {
                view = imageViews.get(0);
                imageViews.remove(0);
            } else {
                view = new FrameLayout(this);

                FrameLayout frameLayout = new FrameLayout(this);
                frameLayout.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
                frameLayout.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                view.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                BackupImageView backupImageView = new BackupImageView(this);
                backupImageView.setTag(311);
                frameLayout.addView(backupImageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                TextView textView = new TextView(this);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                textView.setGravity(Gravity.CENTER);
                textView.setTag(312);
                frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

                view.setTag(2);

                view.setOnClickListener(v -> openCurrentMessage());
            }

            TextView messageText = view.findViewWithTag(312);
            BackupImageView imageView = view.findViewWithTag(311);
            imageView.setAspectFit(true);

            if (messageObject.type == MessageObject.TYPE_PHOTO) {
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize());
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 100);
                boolean photoSet = false;
                if (currentPhotoObject != null) {
                    boolean photoExist = true;
                    if (messageObject.type == MessageObject.TYPE_PHOTO) {
                        File cacheFile = FileLoader.getInstance(messageObject.currentAccount).getPathToMessage(messageObject.messageOwner);
                        if (!cacheFile.exists()) {
                            photoExist = false;
                        }
                    }
                    if (!messageObject.needDrawBluredPreview()) {
                        if (photoExist || DownloadController.getInstance(messageObject.currentAccount).canDownloadMedia(messageObject)) {
                            imageView.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "100_100", ImageLocation.getForObject(thumb, messageObject.photoThumbsObject), "100_100_b", currentPhotoObject.size, messageObject);
                            photoSet = true;
                        } else {
                            if (thumb != null) {
                                imageView.setImage(ImageLocation.getForObject(thumb, messageObject.photoThumbsObject), "100_100_b", null, null, messageObject);
                                photoSet = true;
                            }
                        }
                    }
                }
                if (!photoSet) {
                    imageView.setVisibility(View.GONE);
                    messageText.setVisibility(View.VISIBLE);
                    messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, SharedConfig.fontSize);
                    messageText.setText(messageObject.messageText);
                } else {
                    imageView.setVisibility(View.VISIBLE);
                    messageText.setVisibility(View.GONE);
                }
            } else if (messageObject.type == MessageObject.TYPE_GEO) {
                messageText.setVisibility(View.GONE);
                messageText.setText(messageObject.messageText);
                imageView.setVisibility(View.VISIBLE);
                TLRPC.GeoPoint geoPoint = messageObject.messageOwner.media.geo;
                double lat = geoPoint.lat;
                double lon = geoPoint._long;

                if (MessagesController.getInstance(messageObject.currentAccount).mapProvider == 2) {
                    imageView.setImage(ImageLocation.getForWebFile(WebFile.createWithGeoPoint(geoPoint, 100, 100, 15, Math.min(2, (int) Math.ceil(AndroidUtilities.density)))), null, null, null, messageObject);
                } else {
                    String currentUrl = AndroidUtilities.formapMapUrl(messageObject.currentAccount, lat, lon, 100, 100, true, 15, -1);
                    imageView.setImage(currentUrl, null, null);
                }
            }
        } else if (messageObject.type == MessageObject.TYPE_VOICE) {
            PopupAudioView cell;
            if (audioViews.size() > 0) {
                view = audioViews.get(0);
                audioViews.remove(0);
                cell = view.findViewWithTag(300);
            } else {
                view = new FrameLayout(this);

                FrameLayout frameLayout = new FrameLayout(this);
                frameLayout.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
                frameLayout.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                view.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                FrameLayout frameLayout1 = new FrameLayout(this);
                frameLayout.addView(frameLayout1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 0));
                cell = new PopupAudioView(this);
                cell.setTag(300);
                frameLayout1.addView(cell);

                view.setTag(3);

                view.setOnClickListener(v -> openCurrentMessage());
            }

            cell.setMessageObject(messageObject);
            if (DownloadController.getInstance(messageObject.currentAccount).canDownloadMedia(messageObject)) {
                cell.downloadAudioIfNeed();
            }
        } else {
            if (textViews.size() > 0) {
                view = textViews.get(0);
                textViews.remove(0);
            } else {
                view = new FrameLayout(this);

                ScrollView scrollView = new ScrollView(this);
                scrollView.setFillViewport(true);
                view.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                LinearLayout linearLayout = new LinearLayout(this);
                linearLayout.setOrientation(LinearLayout.HORIZONTAL);
                linearLayout.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
                linearLayout.setPadding(dp(20), dp(16), dp(20), dp(8));
                linearLayout.setOnClickListener(v -> openCurrentMessage());

                TextView textView = new TextView(this);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                textView.setTag(301);
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
                textView.setGravity(Gravity.START | Gravity.TOP);
                linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                view.setTag(1);
            }
            TextView messageText = view.findViewWithTag(301);
            ((ScrollView) view.getChildAt(0)).scrollTo(0, 0);
            messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, SharedConfig.fontSize);
            messageText.setText(messageObject.messageText);
        }
        if (view.getParent() == null) {
            messageContainer.addView(view);
        }
        view.setVisibility(View.VISIBLE);

        if (applyOffset) {
            int width = popupContainer.getMeasuredWidth();
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            if (num == currentMessageNum) {
                view.setTranslationX(0);
            } else if (num == currentMessageNum - 1) {
                view.setTranslationX(-width);
            } else if (num == currentMessageNum + 1) {
                view.setTranslationX(width);
            }
            view.setLayoutParams(layoutParams);
            view.invalidate();
        }

        return view;
    }

    private void reuseButtonsView(ViewGroup view) {
        if (view == null) {
            return;
        }
        popupContainer.removeView(view);
    }

    private void reuseView(ViewGroup view) {
        if (view == null) {
            return;
        }
        int tag = (Integer) view.getTag();
        view.setVisibility(View.GONE);
        if (tag == 1) {
            textViews.add(view);
        } else if (tag == 2) {
            imageViews.add(view);
        } else if (tag == 3) {
            audioViews.add(view);
        }
    }

    private void prepareLayouts(int move) {
        if (move == 0) {
            reuseView(centerView);
            reuseView(leftView);
            reuseView(rightView);
            reuseButtonsView(centerButtonsView);
            reuseButtonsView(leftButtonsView);
            reuseButtonsView(rightButtonsView);
            for (int a = currentMessageNum - 1; a < currentMessageNum + 2; a++) {
                if (a == currentMessageNum - 1) {
                    leftView = getViewForMessage(a, true);
                    leftButtonsView = getButtonsViewForMessage(a, true);
                } else if (a == currentMessageNum) {
                    centerView = getViewForMessage(a, true);
                    centerButtonsView = getButtonsViewForMessage(a, true);
                } else if (a == currentMessageNum + 1) {
                    rightView = getViewForMessage(a, true);
                    rightButtonsView = getButtonsViewForMessage(a, true);
                }
            }
        } else if (move == 1) {
            reuseView(rightView);
            reuseButtonsView(rightButtonsView);
            rightView = centerView;
            centerView = leftView;
            leftView = getViewForMessage(currentMessageNum - 1, true);

            rightButtonsView = centerButtonsView;
            centerButtonsView = leftButtonsView;
            leftButtonsView = getButtonsViewForMessage(currentMessageNum - 1, true);
        } else if (move == 2) {
            reuseView(leftView);
            reuseButtonsView(leftButtonsView);
            leftView = centerView;
            centerView = rightView;
            rightView = getViewForMessage(currentMessageNum + 1, true);

            leftButtonsView = centerButtonsView;
            centerButtonsView = rightButtonsView;
            rightButtonsView = getButtonsViewForMessage(currentMessageNum + 1, true);
        } else if (move == 3) {
            if (rightView != null) {
                float offset = rightView.getTranslationX();
                reuseView(rightView);
                if ((rightView = getViewForMessage(currentMessageNum + 1, false)) != null) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) rightView.getLayoutParams();
                    layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    rightView.setLayoutParams(layoutParams);
                    rightView.setTranslationX(offset);
                    rightView.invalidate();
                }
            }
            if (rightButtonsView != null) {
                float offset = rightButtonsView.getTranslationX();
                reuseButtonsView(rightButtonsView);
                if ((rightButtonsView = getButtonsViewForMessage(currentMessageNum + 1, false)) != null) {
                    rightButtonsView.setTranslationX(offset);
                }
            }
        } else if (move == 4) {
            if (leftView != null) {
                float offset = leftView.getTranslationX();
                reuseView(leftView);
                if ((leftView = getViewForMessage(0, false)) != null) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) leftView.getLayoutParams();
                    layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    leftView.setLayoutParams(layoutParams);
                    leftView.setTranslationX(offset);
                    leftView.invalidate();
                }
            }
            if (leftButtonsView != null) {
                float offset = leftButtonsView.getTranslationX();
                reuseButtonsView(leftButtonsView);
                if ((leftButtonsView = getButtonsViewForMessage(0, false)) != null) {
                    leftButtonsView.setTranslationX(offset);
                }
            }
        }
        for (int a = 0; a < 3; a++) {
            int num = currentMessageNum - 1 + a;
            MessageObject messageObject;
            if (popupMessages.size() == 1 && (num < 0 || num >= popupMessages.size())) {
                messageObject = null;
            } else {
                if (num == -1) {
                    num = popupMessages.size() - 1;
                } else if (num == popupMessages.size()) {
                    num = 0;
                }
                messageObject = popupMessages.get(num);
            }
            setMessageObjects[a] = messageObject;
        }
    }

    private void fixLayout() {
        if (contentView != null && !finished) {
            contentView.requestLayout();
        }
    }

    private void handleIntent(Intent intent) {
        isReply = intent != null && intent.getBooleanExtra("force", false);
        popupMessages.clear();
        if (isReply) {
            int account = intent != null ? intent.getIntExtra("currentAccount", UserConfig.selectedAccount) : UserConfig.selectedAccount;
            if (!UserConfig.isValidAccount(account)) {
                return;
            }
            popupMessages.addAll(NotificationsController.getInstance(account).popupReplyMessages);
        } else {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    popupMessages.addAll(NotificationsController.getInstance(a).popupMessages);
                }
            }
        }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode() || !ApplicationLoader.isScreenOn) {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                            WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }

        if (currentMessageObject == null) {
            currentMessageNum = 0;
        }
        getNewMessage();
    }

    private void getNewMessage() {
        if (popupMessages.isEmpty()) {
            onFinish();
            finish();
            return;
        }

        boolean found = false;
        if ((currentMessageNum != 0 || chatActivityEnterView.hasText() || hasPendingAudio() || startedMoving) && currentMessageObject != null) {
            for (int a = 0, size = popupMessages.size(); a < size; a++) {
                MessageObject messageObject = popupMessages.get(a);
                if (messageObject.currentAccount == currentMessageObject.currentAccount && messageObject.getDialogId() == currentMessageObject.getDialogId() && messageObject.getId() == currentMessageObject.getId()) {
                    currentMessageNum = a;
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            currentMessageNum = 0;
            currentMessageObject = popupMessages.get(0);
            updateInterfaceForCurrentMessage(0);
        } else if (startedMoving) {
            if (currentMessageNum == popupMessages.size() - 1) {
                prepareLayouts(3);
            } else if (currentMessageNum == 1) {
                prepareLayouts(4);
            }
        }
        updateMessageCount();
    }

    private void openCurrentMessage() {
        if (currentMessageObject == null) {
            return;
        }
        Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
        long dialogId = currentMessageObject.getDialogId();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            intent.putExtra("encId", DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isUserDialog(dialogId)) {
            intent.putExtra("userId", dialogId);
        } else if (DialogObject.isChatDialog(dialogId)) {
            intent.putExtra("chatId", -dialogId);
        }
        intent.putExtra("currentAccount", currentMessageObject.currentAccount);
        intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        onFinish();
        finish();
    }

    private void updateInterfaceForCurrentMessage(int move) {
        if (headerView == null) {
            return;
        }
        if (lastResumedAccount != currentMessageObject.currentAccount) {
            if (lastResumedAccount >= 0) {
                ConnectionsManager.getInstance(lastResumedAccount).setAppPaused(true, false);
            }
            lastResumedAccount = currentMessageObject.currentAccount;
            ConnectionsManager.getInstance(lastResumedAccount).setAppPaused(false, false);
        }
        currentChat = null;
        currentUser = null;
        long dialogId = currentMessageObject.getDialogId();
        chatActivityEnterView.setDialogId(dialogId, currentMessageObject.currentAccount);
        if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentMessageObject.currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
            currentUser = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(encryptedChat.user_id);
        } else if (DialogObject.isUserDialog(dialogId)) {
            currentUser = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(dialogId);
        } else if (DialogObject.isChatDialog(dialogId)) {
            currentChat = MessagesController.getInstance(currentMessageObject.currentAccount).getChat(-dialogId);
            if (currentMessageObject.isFromUser()) {
                currentUser = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(currentMessageObject.messageOwner.from_id.user_id);
            }
        }

        if (currentChat != null) {
            nameTextView.setText(currentChat.title);
            if (currentUser != null) {
                onlineTextView.setText(UserObject.getUserName(currentUser));
            } else {
                onlineTextView.setText(null);
            }
            nameTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            nameTextView.setCompoundDrawablePadding(0);
        } else if (currentUser != null) {
            nameTextView.setText(UserObject.getUserName(currentUser));
            if (DialogObject.isEncryptedDialog(dialogId)) {
                nameTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_lock_white, 0, 0, 0);
                nameTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
            } else {
                nameTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                nameTextView.setCompoundDrawablePadding(0);
            }
        }

        updateTitleColor();
        dateTextView.setText(LocaleController.getInstance().getFormatterDay().format((long) currentMessageObject.messageOwner.date * 1000));
        prepareLayouts(move);
        popupContainer.requestLayout();
        updateSubtitle();
        checkAndUpdateAvatar();
        applyViewsLayoutParams(0);
    }

    private void updateSubtitle() {
        if (headerView == null || currentMessageObject == null) {
            return;
        }
        if (currentChat != null || currentUser == null) {
            return;
        }
        if (currentUser.id / 1000 != 777 && currentUser.id / 1000 != 333 && ContactsController.getInstance(currentMessageObject.currentAccount).contactsDict.get(currentUser.id) == null && (ContactsController.getInstance(currentMessageObject.currentAccount).contactsDict.size() != 0 || !ContactsController.getInstance(currentMessageObject.currentAccount).isLoadingContacts())) {
            if (currentUser.phone != null && currentUser.phone.length() != 0) {
                nameTextView.setText(PhoneFormat.getInstance().format("+" + currentUser.phone));
            } else {
                nameTextView.setText(UserObject.getUserName(currentUser));
            }
        } else {
            nameTextView.setText(UserObject.getUserName(currentUser));
        }
        if (currentUser != null && currentUser.id == UserObject.VERIFY) {
            onlineTextView.setText(LocaleController.getString(R.string.VerifyCodesNotifications));
        } else if (currentUser != null && currentUser.id == 777000) {
            onlineTextView.setText(LocaleController.getString(R.string.ServiceNotifications));
        } else {
            CharSequence printString = MessagesController.getInstance(currentMessageObject.currentAccount).getPrintingString(currentMessageObject.getDialogId(), 0, false);
            if (printString == null || printString.length() == 0) {
                lastPrintString = null;
                setTypingAnimation(false);
                TLRPC.User user = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(currentUser.id);
                if (user != null) {
                    currentUser = user;
                }
                onlineTextView.setText(LocaleController.formatUserStatus(currentMessageObject.currentAccount, currentUser));
            } else {
                lastPrintString = printString;
                onlineTextView.setText(printString);
                setTypingAnimation(true);
            }
        }
    }

    private void checkAndUpdateAvatar() {
        if (currentMessageObject == null) {
            return;
        }
        if (currentChat != null) {
            TLRPC.Chat chat = MessagesController.getInstance(currentMessageObject.currentAccount).getChat(currentChat.id);
            if (chat == null) {
                return;
            }
            currentChat = chat;
            if (avatarImageView != null) {
                AvatarDrawable avatarDrawable = new AvatarDrawable(currentChat);
                avatarImageView.setForUserOrChat(chat, avatarDrawable);
            }
        } else if (currentUser != null) {
            TLRPC.User user = MessagesController.getInstance(currentMessageObject.currentAccount).getUser(currentUser.id);
            if (user == null) {
                return;
            }
            currentUser = user;
            if (avatarImageView != null) {
                AvatarDrawable avatarDrawable = new AvatarDrawable(currentUser);
                avatarImageView.setForUserOrChat(user, avatarDrawable);
            }
        }
    }

    private void setTypingAnimation(boolean start) {
        if (headerView == null) {
            return;
        }
        if (start) {
            try {
                Integer type = MessagesController.getInstance(currentMessageObject.currentAccount).getPrintingStringType(currentMessageObject.getDialogId(), 0);
                onlineTextView.setCompoundDrawablesWithIntrinsicBounds(statusDrawables[type], null, null, null);
                onlineTextView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
                for (int a = 0; a < statusDrawables.length; a++) {
                    if (a == type) {
                        statusDrawables[a].start();
                    } else {
                        statusDrawables[a].stop();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            onlineTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            onlineTextView.setCompoundDrawablePadding(0);
            for (int a = 0; a < statusDrawables.length; a++) {
                statusDrawables[a].stop();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (chatActivityEnterView.isPopupShowing()) {
            chatActivityEnterView.hidePopup(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MediaController.getInstance().setFeedbackView(chatActivityEnterView, true);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setFieldFocused(true);
        }
        fixLayout();
        checkAndUpdateAvatar();
        updateColors();
        if (lastResumedAccount >= 0) {
            ConnectionsManager.getInstance(lastResumedAccount).setAppPaused(false, false);
        }
        wakeLock.acquire(7000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelTransitionAnimation();
        overridePendingTransition(0, 0);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.hidePopup(false);
            chatActivityEnterView.setFieldFocused(false);
        }
        if (lastResumedAccount >= 0) {
            ConnectionsManager.getInstance(lastResumedAccount).setAppPaused(true, false);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            updateColors();
        } else if (id == NotificationCenter.appDidLogout) {
            if (account == lastResumedAccount) {
                onFinish();
                finish();
            }
        } else if (id == NotificationCenter.pushMessagesUpdated) {
            if (!isReply) {
                popupMessages.clear();
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        popupMessages.addAll(NotificationsController.getInstance(a).popupMessages);
                    }
                }
                getNewMessage();
                if (!popupMessages.isEmpty()) {
                    for (int a = 0; a < 3; a++) {
                        int num = currentMessageNum - 1 + a;
                        MessageObject messageObject;
                        if (popupMessages.size() == 1 && (num < 0 || num >= popupMessages.size())) {
                            messageObject = null;
                        } else {
                            if (num == -1) {
                                num = popupMessages.size() - 1;
                            } else if (num == popupMessages.size()) {
                                num = 0;
                            }
                            messageObject = popupMessages.get(num);
                        }
                        if (setMessageObjects[a] != messageObject) {
                            updateInterfaceForCurrentMessage(0);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            if (currentMessageObject == null || account != lastResumedAccount) {
                return;
            }
            int updateMask = (Integer) args[0];
            if ((updateMask & MessagesController.UPDATE_MASK_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_STATUS) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_MEMBERS) != 0) {
                updateSubtitle();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (updateMask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0) {
                checkAndUpdateAvatar();
            }
            if ((updateMask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                CharSequence printString = MessagesController.getInstance(currentMessageObject.currentAccount).getPrintingString(currentMessageObject.getDialogId(), 0, false);
                if (lastPrintString != null && printString == null || lastPrintString == null && printString != null || lastPrintString != null && !lastPrintString.equals(printString)) {
                    updateSubtitle();
                }
            }
        } else if (id == NotificationCenter.messagePlayingDidReset) {
            Integer mid = (Integer) args[0];
            if (messageContainer != null) {
                int count = messageContainer.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = messageContainer.getChildAt(a);
                    if ((Integer) view.getTag() == 3) {
                        PopupAudioView cell = view.findViewWithTag(300);
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && messageObject.currentAccount == account && messageObject.getId() == mid) {
                            cell.updateButtonState();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (messageContainer != null) {
                int count = messageContainer.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = messageContainer.getChildAt(a);
                    if ((Integer) view.getTag() == 3) {
                        PopupAudioView cell = view.findViewWithTag(300);
                        MessageObject messageObject = cell.getMessageObject();
                        if (messageObject != null && messageObject.currentAccount == account && messageObject.getId() == mid) {
                            cell.updateProgress();
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            if (messageContainer != null) {
                int count = messageContainer.getChildCount();
                for (int a = 0; a < count; a++) {
                    View view = messageContainer.getChildAt(a);
                    if ((Integer) view.getTag() == 1) {
                        TextView textView = view.findViewWithTag(301);
                        if (textView != null) {
                            textView.invalidate();
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.contactsDidLoad) {
            if (account == lastResumedAccount) {
                updateSubtitle();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onFinish();
        MediaController.getInstance().setFeedbackView(chatActivityEnterView, false);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (avatarImageView != null) {
            avatarImageView.setImageDrawable(null);
        }
    }

    protected void onFinish() {
        if (finished) {
            return;
        }
        finished = true;
        cancelTransitionAnimation();
        for (StatusDrawable drawable : statusDrawables) {
            drawable.stop();
        }
        if (isReply) {
            popupMessages.clear();
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.appDidLogout);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.updateInterfaces);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(a).removeObserver(this, NotificationCenter.contactsDidLoad);
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pushMessagesUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onDestroy();
        }
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

}
