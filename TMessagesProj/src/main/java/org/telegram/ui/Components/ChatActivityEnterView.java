/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.LaunchActivity.getLastFragment;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.Property;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.os.BuildCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.ChatListItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SharedPrefsHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.camera.CameraController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Business.BusinessLinksController;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.GroupStickersActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MessageSendPreview;
import org.telegram.ui.MultiContactsSelectorBottomSheet;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.StickersActivity;
import org.telegram.ui.Stories.recorder.CaptionContainerView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.bots.BotCommandsMenuContainer;
import org.telegram.ui.bots.BotCommandsMenuView;
import org.telegram.ui.bots.BotKeyboardView;
import org.telegram.ui.bots.BotWebViewAttachedSheet;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.bots.ChatActivityBotWebViewButton;
import org.telegram.ui.bots.WebViewRequestProps;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ChatActivityEnterView extends BlurredFrameLayout implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate, StickersAlert.StickersAlertDelegate, SuggestEmojiView.AnchorViewDelegate {

    private int commonInputType;
    private boolean stickersEnabled;
    private ActionBarMenuSubItem sendWhenOnlineButton;
    private LinearLayout recordTimeContainer;
    private CharSequence overrideHint;
    private CharSequence overrideHint2;
    float emojiButtonScale = 1f;
    float emojiButtonAlpha = 1f;
    float emojiButtonPaddingScale = 1f;
    float emojiButtonPaddingAlpha = 1f;
    private boolean overrideKeyboardAnimation;
    private Runnable animationEndRunnable;
    private float attachLayoutTranslationX;
    protected float attachLayoutPaddingTranslationX;
    private float attachLayoutAlpha = 1f;
    private float attachLayoutPaddingAlpha = 1f;
    private float messageTextTranslationX;
    private float messageTextPaddingTranslationX;
    private float horizontalPadding = 0;
    private boolean sendButtonEnabled = true;
    private TLRPC.UserFull userInfo;
    public HintView2 birthdayHint;

    public boolean voiceOnce;
    public boolean onceVisible;

    public void drawRecordedPannel(Canvas canvas) {
        if (getAlpha() == 0 || recordedAudioPanel == null || recordedAudioPanel.getParent() == null || recordedAudioPanel.getVisibility() != View.VISIBLE) {
            return;
        }
        int restoreCount = canvas.save();
        canvas.translate(getX() + textFieldContainer.getX() + messageEditTextContainer.getX() + recordedAudioPanel.getX(), getY() + textFieldContainer.getY() + messageEditTextContainer.getY() + recordedAudioPanel.getY());
        if (getAlpha() != 1f) {
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) (255 * getAlpha()), Canvas.ALL_SAVE_FLAG);
        }
        recordedAudioPanel.draw(canvas);
        canvas.restoreToCount(restoreCount);
    }

    public interface ChatActivityEnterViewDelegate {

        default void onEditTextScroll() {};
        
        default void onContextMenuOpen() {};

        default void onContextMenuClose() {};

        void onMessageSend(CharSequence message, boolean notify, int scheduleDate);

        void needSendTyping();

        void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft);

        void onTextSelectionChanged(int start, int end);

        void onTextSpansChanged(CharSequence text);

        void onAttachButtonHidden();

        void onAttachButtonShow();

        void onWindowSizeChanged(int size);

        void onStickersTab(boolean opened);

        void onMessageEditEnd(boolean loading);

        void didPressAttachButton();

        void needStartRecordVideo(int state, boolean notify, int scheduleDate, int ttl, long effectId);

        void toggleVideoRecordingPause();

        void needChangeVideoPreviewState(int state, float seekProgress);

        void onSwitchRecordMode(boolean video);

        void onPreAudioVideoRecord();

        void needStartRecordAudio(int state);

        void needShowMediaBanHint();

        void onStickersExpandedChange();

        void onUpdateSlowModeButton(View button, boolean show, CharSequence time);

        default boolean checkCanRemoveRestrictionsByBoosts() {
            return false;
        }

        default void scrollToSendingMessage() {

        }

        default void openScheduledMessages() {

        }

        default boolean hasScheduledMessages() {
            return true;
        }

        void onSendLongClick();

        void onAudioVideoInterfaceUpdated();

        default void bottomPanelTranslationYChanged(float translation) {

        }

        default void prepareMessageSending() {

        }

        default void onTrendingStickersShowed(boolean show) {

        }

        default boolean hasForwardingMessages() {
            return false;
        }

        /**
         * @return Height of the content view
         */
        default int getContentViewHeight() {
            return 0;
        }

        /**
         * @return Measured keyboard height
         */
        default int measureKeyboardHeight() {
            return 0;
        }

        /**
         * @return A list of available peers to send messages as
         */
        @Nullable
        default TLRPC.TL_channels_sendAsPeers getSendAsPeers() {
            return null;
        }

        default TL_stories.StoryItem getReplyToStory() {
            return null;
        }

        default ChatActivity.ReplyQuote getReplyQuote() {
            return null;
        }

        default void onKeyboardRequested() {

        }

        default boolean onceVoiceAvailable() {
            return false;
        }
    }

    public final static int RECORD_STATE_ENTER = 0;
    public final static int RECORD_STATE_SENDING = 1;
    public final static int RECORD_STATE_CANCEL = 2;
    public final static int RECORD_STATE_PREPARING = 3;
    public final static int RECORD_STATE_CANCEL_BY_TIME = 4;
    public final static int RECORD_STATE_CANCEL_BY_GESTURE = 5;

    private final static int POPUP_CONTENT_BOT_KEYBOARD = 1;

    private int currentAccount = UserConfig.selectedAccount;
    private AccountInstance accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount);

    private SeekBarWaveform seekBarWaveform;
    private boolean isInitLineCount;
    private int lineCount = 1;
    private AdjustPanLayoutHelper adjustPanLayoutHelper;
    private Runnable showTopViewRunnable;
    private Runnable setTextFieldRunnable;
    public boolean preventInput;
    @Nullable
    private NumberTextView captionLimitView;
    private int currentLimit = -1;
    private int codePointCount;
    private CrossOutDrawable notifySilentDrawable;

    private Runnable moveToSendStateRunnable;
    boolean messageTransitionIsRunning;
    boolean textTransitionIsRunning;

    private BotMenuButtonType botMenuButtonType = BotMenuButtonType.NO_BUTTON;
    private String botMenuWebViewTitle;
    private String botMenuWebViewUrl;

//    public BotWebViewMenuContainer botWebViewMenuContainer;
    private ChatActivityBotWebViewButton botWebViewButton;

    @Nullable
    private BotCommandsMenuView botCommandsMenuButton;
    public BotCommandsMenuContainer botCommandsMenuContainer;
    private BotCommandsMenuView.BotCommandsAdapter botCommandsAdapter;

    private boolean captionLimitBulletinShown;

    // Send as... stuff
    @Nullable
    private SenderSelectView senderSelectView;
    private SenderSelectPopup senderSelectPopupWindow;
    private Runnable onEmojiSearchClosed;
    private int popupX, popupY;
    private Runnable onKeyboardClosed;

    private ValueAnimator searchAnimator;
    private float searchToOpenProgress;
    private float chatSearchExpandOffset;

    private boolean sendRoundEnabled = true;
    private boolean sendVoiceEnabled = true;
    public boolean sendPlainEnabled = true;
    private boolean emojiButtonRestricted;


    private HashMap<View, Float> animationParamsX = new HashMap<>();

    protected class SeekBarWaveformView extends View {

        public SeekBarWaveformView(Context context) {
            super(context);
            seekBarWaveform = new SeekBarWaveform(context);
            seekBarWaveform.setDelegate(progress -> {
                if (audioToSendMessageObject != null) {
                    audioToSendMessageObject.audioProgress = progress;
                    MediaController.getInstance().seekToProgress(audioToSendMessageObject, progress);
                }
            });
        }

        public void setWaveform(byte[] waveform) {
            seekBarWaveform.setWaveform(waveform);
            invalidate();
        }

        public void setProgress(float progress) {
            seekBarWaveform.setProgress(progress);
            invalidate();
        }

        public boolean isDragging() {
            return seekBarWaveform.isDragging();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean result = seekBarWaveform.onTouch(event.getAction(), event.getX(), event.getY());
            if (result) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
            }
            return result || super.onTouchEvent(event);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            seekBarWaveform.setSize((int) (right - left - horizontalPadding * 2), bottom - top);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            seekBarWaveform.setColors(getThemedColor(Theme.key_chat_recordedVoiceProgress), getThemedColor(Theme.key_chat_recordedVoiceProgressInner), getThemedColor(Theme.key_chat_recordedVoiceProgress));
            seekBarWaveform.draw(canvas, this);
        }
    }

    private static class SlowModeBtn extends FrameLayout {
        private final SimpleTextView textView;
        private final RectF bgRect = new RectF();
        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable closeDrawable;
        private boolean isPremiumMode = false;

        public SlowModeBtn(@NonNull Context context) {
            super(context);
            textView = new SimpleTextView(context);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            setWillNotDraw(false);
            closeDrawable = ContextCompat.getDrawable(context, R.drawable.msg_mini_close_tooltip);
            closeDrawable.setBounds(0, 0, closeDrawable.getIntrinsicWidth(), closeDrawable.getIntrinsicHeight());
            setClipToPadding(false);
            setClipChildren(false);
            ScaleStateListAnimator.apply(this);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            gradientPaint.setShader(new LinearGradient(0, 0, getMeasuredWidth(), 0, new int[]{0xff7593ff, 0xffa472ff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        }

        public void setTextSize(int size) {
            textView.setTextSize(size);
            invalidate();
        }

        public void setTextColor(int color) {
            textView.setTextColor(color);
            invalidate();
        }

        public void setGravity(int gravity) {
            textView.setGravity(gravity);
            invalidate();
        }

        public void setText(CharSequence text) {
            textView.setText(text);
            invalidate();
        }

        public void setPremiumMode(boolean premiumMode) {
            this.isPremiumMode = premiumMode;
            invalidate();
        }

        public CharSequence getText() {
            return textView.getText();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (isPremiumMode) {
                canvas.save();
                int heightRect = dp(26);
                canvas.translate(0, ((getMeasuredHeight() - heightRect) / 2f) - dp(1));
                bgRect.set(0, 0f, (float) getMeasuredWidth() - getPaddingEnd(), (float) heightRect);
                canvas.drawRoundRect(bgRect, heightRect / 2f, heightRect / 2f, gradientPaint);
                canvas.translate(getMeasuredWidth() - getPaddingEnd() - dp(6) - closeDrawable.getIntrinsicWidth(), dp(5));
                closeDrawable.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            if (child instanceof SimpleTextView && isPremiumMode) {
                SimpleTextView simpleTextView = (SimpleTextView) child;
                canvas.save();
                canvas.scale(0.8f, 0.8f);
                canvas.translate(-dp(16), dp(5));
                int oldColor = simpleTextView.getTextPaint().getColor();
                simpleTextView.getTextPaint().setColor(Color.WHITE);
                boolean result = super.drawChild(canvas, child, drawingTime);
                simpleTextView.getTextPaint().setColor(oldColor);
                canvas.restore();
                return result;
            }
            return super.drawChild(canvas, child, drawingTime);
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    private View.AccessibilityDelegate mediaMessageButtonsDelegate = new View.AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName("android.widget.ImageButton");
            info.setClickable(true);
            info.setLongClickable(true);
        }
    };

    boolean ctrlPressed = false;
    boolean shiftPressed = false;

    @Nullable
    protected EditTextCaption messageEditText;
    private SlowModeBtn slowModeButton;
    private int slowModeTimer;
    private Runnable updateSlowModeRunnable;
    private SendButton sendButton;
    private int sendButtonBackgroundColor;
    public MessageSendPreview messageSendPreview;
    private long sentFromPreview;
    private ActionBarPopupWindow sendPopupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout;
    private ImageView cancelBotButton;
    private ChatActivityEnterViewAnimatedIconView emojiButton;
    @Nullable
    private ImageView expandStickersButton;
    private boolean emojiViewFrozen;
    private EmojiView emojiView;
    private AnimatorSet panelAnimation;
    private boolean emojiViewVisible;
    private boolean botKeyboardViewVisible;
    private TimerView recordTimerView;
    private FrameLayout audioVideoButtonContainer;
    private ChatActivityEnterViewAnimatedIconView audioVideoSendButton;
    private boolean isInVideoMode;
    @Nullable
    private FrameLayout recordPanel;
    @Nullable
    protected FrameLayout recordedAudioPanel;
    @Nullable
    protected VideoTimelineView videoTimelineView;
    @SuppressWarnings("FieldCanBeLocal")
    private RLottieImageView recordDeleteImageView;
    @Nullable
    protected SeekBarWaveformView recordedAudioSeekBar;
    @Nullable
    private View recordedAudioBackground;
    @Nullable
    private ImageView recordedAudioPlayButton;
    private long millisecondsRecorded;
    @Nullable
    private TextView recordedAudioTimeTextView;
    @Nullable
    private SlideTextView slideText;
    @Nullable
    private RecordDot recordDot;
    private SizeNotifierFrameLayout sizeNotifierLayout;
    private int originalViewHeight;
    private LinearLayout attachLayout;
    private ImageView attachButton;
    @Nullable
    private ImageView botButton;
    @Nullable
    private ImageView reactionsButton;
    protected FrameLayout messageEditTextContainer;
    private FrameLayout textFieldContainer;
    private FrameLayout sendButtonContainer;
    @Nullable
    private SendButton doneButton;
    private AnimatorSet doneButtonAnimation;
    protected View topView;
    protected View topLineView;
    private BotKeyboardView botKeyboardView;
    private ImageView notifyButton;
    @Nullable
    private ImageView scheduledButton;
    @Nullable
    private ImageView giftButton;
    private boolean scheduleButtonHidden;
    private AnimatorSet scheduledButtonAnimation;
    @Nullable
    private RecordCircle recordCircle;
    public ControlsView controlsView;
    private CloseProgressDrawable2 progressDrawable;
    private Paint dotPaint;
    private MediaActionDrawable playPauseDrawable;
    private int searchingType;
    private Runnable focusRunnable;
    protected float topViewEnterProgress;
    protected int animatedTop;
    public ValueAnimator currentTopViewAnimation;
    @Nullable
    private ReplaceableIconDrawable botButtonDrawable;

    private CharSequence draftMessage;
    private boolean draftSearchWebpage;

    private boolean isPaste;

    private boolean destroyed;

    private MessageObject editingMessageObject;
    private boolean editingCaption;

    private TLRPC.TL_businessChatLink editingBusinessLink;

    private BusinessLinkPresetMessage lastSavedBusinessLinkMessage;

    private TLRPC.ChatFull info;

    private boolean hasRecordVideo;

    private int currentPopupContentType = -1;

    private boolean silent;
    private boolean canWriteToChannel;

    private boolean smoothKeyboard;

    private boolean isPaused = true;
    private boolean recordIsCanceled;
    private boolean showKeyboardOnResume;

    private MessageObject botButtonsMessageObject;
    private TLRPC.TL_replyKeyboardMarkup botReplyMarkup;
    private int botCount;
    private boolean hasBotCommands;
    private boolean hasQuickReplies;

    private PowerManager.WakeLock wakeLock;
    private AnimatorSet runningAnimation;
    private AnimatorSet runningAnimation2;
    private AnimatorSet runningAnimationAudio;
    private AnimatorSet recordPannelAnimation;
    private int runningAnimationType;
    private int recordInterfaceState;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private int emojiPadding;
    private boolean sendByEnter;
    private long lastTypingTimeSend;
    private float startedDraggingX = -1;
    private float distCanMove = dp(80);
    private boolean recordingAudioVideo;
    public int recordingGuid;
    private boolean forceShowSendButton;
    private boolean allowAnimatedEmoji;
    private boolean allowStickers;
    private boolean allowGifs;

    private int lastSizeChangeValue1;
    private boolean lastSizeChangeValue2;

    private int[] location = new int[2];

    private Activity parentActivity;
    private ChatActivity parentFragment;
    private long dialog_id;
    private boolean ignoreTextChange;
    private int innerTextChange;
    private MessageObject replyingMessageObject;
    private MessageObject replyingTopMessage;
    private ChatActivity.ReplyQuote replyingQuote;
    private MessageObject botMessageObject;
    private TLRPC.WebPage messageWebPage;
    private boolean messageWebPageSearch = true;
    private ChatActivityEnterViewDelegate delegate;
    private TrendingStickersAlert trendingStickersAlert;

    private TLRPC.TL_document audioToSend;
    private String audioToSendPath;
    private MessageObject audioToSendMessageObject;
    private VideoEditedInfo videoToSendMessageObject;

    protected boolean topViewShowed;

    private boolean needShowTopView;
    private boolean allowShowTopView;

    private MessageObject pendingMessageObject;
    private TLRPC.KeyboardButton pendingLocationButton;

    private boolean waitingForKeyboardOpen;
    private boolean waitingForKeyboardOpenAfterAnimation;
    private boolean wasSendTyping;
    protected boolean shouldAnimateEditTextWithBounds;
    private int animatingContentType = -1;

    private boolean clearBotButtonsOnKeyboardOpen;
    private boolean expandStickersWithKeyboard;
    private float doneButtonEnabledProgress = 1f;
    @Nullable
    private Drawable doneCheckDrawable;
    boolean doneButtonEnabled = true;
    private ValueAnimator doneButtonColorAnimator;

    private Runnable openKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            if (hasBotWebView() && botCommandsMenuIsShowing() || BaseFragment.hasSheets(parentFragment)) {
                return;
            }

            if (!destroyed && messageEditText != null && waitingForKeyboardOpen && !keyboardVisible && !AndroidUtilities.usingHardwareInput && !AndroidUtilities.isInMultiwindow) {
                if (delegate != null) {
                    delegate.onKeyboardRequested();
                }
                messageEditText.requestFocus();
                AndroidUtilities.showKeyboard(messageEditText);
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    };
    private Runnable updateExpandabilityRunnable = new Runnable() {

        private int lastKnownPage = -1;

        @Override
        public void run() {
            if (emojiView != null) {
                int curPage = emojiView.getCurrentPage();
                if (curPage != lastKnownPage) {
                    lastKnownPage = curPage;
                    boolean prevOpen = stickersTabOpen;
                    stickersTabOpen = curPage == 1 || curPage == 2;
                    boolean prevOpen2 = emojiTabOpen;
                    emojiTabOpen = curPage == 0;
                    if (stickersExpanded) {
                        if (searchingType != 0) {
                            setSearchingTypeInternal(curPage == 0 ? 2 : 1, true);
                            checkStickresExpandHeight();
                        } else if (!stickersTabOpen) {
                            setStickersExpanded(false, true, false);
                        }
                    }
                    if (prevOpen != stickersTabOpen || prevOpen2 != emojiTabOpen) {
                        checkSendButton(true);
                    }
                }
            }
        }
    };

    private Property<View, Integer> roundedTranslationYProperty = new Property<View, Integer>(Integer.class, "translationY") {
        @Override
        public Integer get(View object) {
            return Math.round(object.getTranslationY());
        }

        @Override
        public void set(View object, Integer value) {
            object.setTranslationY(value);
        }
    };

    private Property<RecordCircle, Float> recordCircleScale = new Property<RecordCircle, Float>(Float.class, "scale") {
        @Override
        public Float get(RecordCircle object) {
            return object.getScale();
        }

        @Override
        public void set(RecordCircle object, Float value) {
            object.setScale(value);
        }
    };

    private Property<RecordCircle, Float> recordControlsCircleScale = new Property<RecordCircle, Float>(Float.class, "controlsScale") {
        @Override
        public Float get(RecordCircle object) {
            return object.getControlsScale();
        }

        @Override
        public void set(RecordCircle object, Float value) {
            object.setControlsScale(value);
        }
    };

    private Paint redDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean stickersTabOpen;
    private boolean emojiTabOpen;
    private boolean gifsTabOpen;
    private boolean stickersExpanded;
    private boolean closeAnimationInProgress;
    private Animator stickersExpansionAnim;
    private Animator currentResizeAnimation;
    private float stickersExpansionProgress;
    private int stickersExpandedHeight;
    private boolean stickersDragging;
    private AnimatedArrowDrawable stickersArrow;
    private boolean removeEmojiViewAfterAnimation;

    private Runnable onFinishInitCameraRunnable = new Runnable() {
        @Override
        public void run() {
            if (delegate != null) {
                delegate.needStartRecordVideo(0, true, 0, 0, 0);
            }
        }
    };

    private boolean recordAudioVideoRunnableStarted;
    private boolean calledRecordRunnable;
    private Runnable recordAudioVideoRunnable = new Runnable() {
        @Override
        public void run() {
            if (delegate == null || parentActivity == null) {
                return;
            }
            delegate.onPreAudioVideoRecord();
            calledRecordRunnable = true;
            recordAudioVideoRunnableStarted = false;
            if (slideText != null) {
                slideText.setAlpha(1.0f);
                slideText.setTranslationY(0);
            }
            audioToSendPath = null;
            audioToSend = null;
            if (isInVideoMode()) {
                if (Build.VERSION.SDK_INT >= 23) {
                    boolean hasAudio = parentActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                    boolean hasVideo = parentActivity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                    if (!hasAudio || !hasVideo) {
                        String[] permissions = new String[!hasAudio && !hasVideo ? 2 : 1];
                        if (!hasAudio && !hasVideo) {
                            permissions[0] = Manifest.permission.RECORD_AUDIO;
                            permissions[1] = Manifest.permission.CAMERA;
                        } else if (!hasAudio) {
                            permissions[0] = Manifest.permission.RECORD_AUDIO;
                        } else {
                            permissions[0] = Manifest.permission.CAMERA;
                        }
                        parentActivity.requestPermissions(permissions, BasePermissionsActivity.REQUEST_CODE_VIDEO_MESSAGE);
                        return;
                    }
                }
                if (!CameraController.getInstance().isCameraInitied()) {
                    CameraController.getInstance().initCamera(onFinishInitCameraRunnable);
                } else {
                    onFinishInitCameraRunnable.run();
                }
                if (!recordingAudioVideo) {
                    recordingAudioVideo = true;
                    updateRecordInterface(RECORD_STATE_ENTER, true);
                    if (recordCircle != null) {
                        recordCircle.showWaves(false, false);
                    }
                    if (recordTimerView != null) {
                        recordTimerView.reset();
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    parentActivity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                    return;
                }

                delegate.needStartRecordAudio(1);
                startedDraggingX = -1;
                TL_stories.StoryItem storyItem = delegate != null ? delegate.getReplyToStory() : null;
                MediaController.getInstance().startRecording(currentAccount, dialog_id, replyingMessageObject, getThreadMessage(), storyItem, recordingGuid, true, parentFragment != null ? parentFragment.quickReplyShortcut : null, parentFragment != null ? parentFragment.getQuickReplyId() : 0);
                recordingAudioVideo = true;
                updateRecordInterface(RECORD_STATE_ENTER, true);
                if (recordTimerView != null) {
                    recordTimerView.start(0);
                }
                if (recordDot != null) {
                    recordDot.enterAnimation = false;
                }
                audioVideoButtonContainer.getParent().requestDisallowInterceptTouchEvent(true);
                if (recordCircle != null) {
                    recordCircle.showWaves(true, false);
                }
            }
        }
    };

    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    private class RecordDot extends View {

        private float alpha;
        private long lastUpdateTime;
        private boolean isIncr;
        boolean attachedToWindow;
        boolean playing;
        RLottieDrawable drawable;
        private boolean enterAnimation;

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attachedToWindow = true;
            if (playing) {
                drawable.start();
            }
            drawable.setMasterParent(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attachedToWindow = false;
            drawable.stop();
            drawable.setMasterParent(null);
        }

        public RecordDot(Context context) {
            super(context);
            int resId = R.raw.chat_audio_record_delete_2;
            drawable = new RLottieDrawable(resId, "" + resId, dp(28), dp(28), false, null);
            drawable.setCurrentParentView(this);
            drawable.setInvalidateOnProgressSet(true);
            updateColors();
        }

        public void updateColors() {
            int dotColor = getThemedColor(Theme.key_chat_recordedVoiceDot);
            int background = getThemedColor(Theme.key_chat_messagePanelBackground);
            redDotPaint.setColor(dotColor);
            drawable.beginApplyLayerColors();
            drawable.setLayerColor("Cup Red.**", dotColor);
            drawable.setLayerColor("Box.**", dotColor);
            drawable.setLayerColor("Line 1.**", background);
            drawable.setLayerColor("Line 2.**", background);
            drawable.setLayerColor("Line 3.**", background);
            drawable.commitApplyLayerColors();
            if (playPauseDrawable != null) {
                playPauseDrawable.setColor(getThemedColor(Theme.key_chat_recordedVoicePlayPause));
            }
        }

        public void resetAlpha() {
            alpha = 1.0f;
            lastUpdateTime = System.currentTimeMillis();
            isIncr = false;
            playing = false;
            drawable.stop();
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (playing) {
                drawable.setAlpha((int) (255 * alpha));
            }
            redDotPaint.setAlpha((int) (255 * alpha));

            long dt = (System.currentTimeMillis() - lastUpdateTime);
            if (enterAnimation) {
                alpha = 1;
            } else {
                if (!isIncr && !playing) {
                    alpha -= dt / 600.0f;
                    if (alpha <= 0) {
                        alpha = 0;
                        isIncr = true;
                    }
                } else {
                    alpha += dt / 600.0f;
                    if (alpha >= 1) {
                        alpha = 1;
                        isIncr = false;
                    }
                }
            }
            lastUpdateTime = System.currentTimeMillis();
            if (playing) {
                drawable.draw(canvas);
            }
            if (!playing || !drawable.hasBitmap()) {
                canvas.drawCircle(this.getMeasuredWidth() >> 1, this.getMeasuredHeight() >> 1, dp(5), redDotPaint);
            }
            invalidate();
        }

        public void playDeleteAnimation() {
            playing = true;
            drawable.setProgress(0);
            if (attachedToWindow) {
                drawable.start();
            }
        }
    }

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable micOutline;
    private Drawable cameraOutline;
    private Drawable micDrawable;
    private Drawable cameraDrawable;
    private Drawable sendDrawable;
    private RectF pauseRect = new RectF();
    private android.graphics.Rect sendRect = new Rect();
    private android.graphics.Rect rect = new Rect();

    private Drawable lockShadowDrawable;
    private final Theme.ResourcesProvider resourcesProvider;

    private final boolean isChat;

    private Runnable runEmojiPanelAnimation = new Runnable() {
        @Override
        public void run() {
            if (panelAnimation != null && !panelAnimation.isRunning()) {
                panelAnimation.start();
            }
        }
    };

    private final Property<? super View, Float> EMOJI_BUTTON_SCALE = new Property<View, Float>(Float.class, "emoji_button_scale") {
        @Override
        public Float get(View object) {
            return emojiButtonScale;
        }

        @Override
        public void set(View object, Float value) {
            emojiButtonScale = value;
            updateEmojiButtonParams();
        }
    };

    private final Property<? super View, Float> ATTACH_LAYOUT_ALPHA = new Property<View, Float>(Float.class, "attach_scale") {
        @Override
        public Float get(View object) {
            return attachLayoutAlpha;
        }

        @Override
        public void set(View object, Float value) {
            attachLayoutAlpha = value;
            updateAttachLayoutParams();
        }
    };

    private final Property<? super View, Float> EMOJI_BUTTON_ALPHA = new Property<View, Float>(Float.class, "emoji_button_alpha") {
        @Override
        public Float get(View object) {
            return emojiButtonAlpha;
        }

        @Override
        public void set(View object, Float value) {
            emojiButtonAlpha = value;
            updateEmojiButtonParams();
        }
    };

    private final Property<? super View, Float> ATTACH_LAYOUT_TRANSLATION_X = new Property<View, Float>(Float.class, "attach_layout_translation_x") {
        @Override
        public Float get(View object) {
            return attachLayoutTranslationX;
        }

        @Override
        public void set(View object, Float value) {
            attachLayoutTranslationX = value;
            updateAttachLayoutParams();
        }
    };

    private final Property<? super View, Float> MESSAGE_TEXT_TRANSLATION_X = new Property<View, Float>(Float.class, "message_text_translation_x") {
        @Override
        public Float get(View object) {
            return messageTextTranslationX;
        }

        @Override
        public void set(View object, Float value) {
            messageTextTranslationX = value;
            updateMessageTextParams();
        }
    };

    private boolean showTooltip;
    private long showTooltipStartTime;
    private float tooltipAlpha;

    public class ControlsView extends FrameLayout {

        private HintView2 hintView;

        private Drawable tooltipBackground;
        private Drawable tooltipBackgroundArrow;
        private String tooltipMessage;
        private StaticLayout tooltipLayout;
        private float tooltipWidth;
        private TextPaint tooltipPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        Paint lockBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint lockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint lockOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Path path = new Path();
        private Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        private CaptionContainerView.PeriodDrawable periodDrawable;

        private Drawable micDrawable;
        private Drawable vidDrawable;

        @Override
        public void setAlpha(float alpha) {
            super.setAlpha(alpha);
        }

        @Override
        protected boolean onSetAlpha(int alpha) {
            return super.onSetAlpha(alpha);
        }

        private VirtualViewHelper virtualViewHelper;

        public ControlsView(Context context) {
            super(context);

            virtualViewHelper = new VirtualViewHelper(this);
            ViewCompat.setAccessibilityDelegate(this, virtualViewHelper);

            periodDrawable = new CaptionContainerView.PeriodDrawable();
            periodDrawable.setCallback(this);
            periodDrawable.setValue(1, voiceOnce, false);

            lockOutlinePaint.setStyle(Paint.Style.STROKE);
            lockOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
            lockOutlinePaint.setStrokeWidth(dpf2(1.7f));

            lockShadowDrawable = getResources().getDrawable(R.drawable.lock_round_shadow);
            lockShadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelVoiceLockShadow), PorterDuff.Mode.MULTIPLY));
            tooltipBackground = Theme.createRoundRectDrawable(dp(5), getThemedColor(Theme.key_chat_gifSaveHintBackground));

            tooltipPaint.setTextSize(dp(14));
            tooltipBackgroundArrow = ContextCompat.getDrawable(context, R.drawable.tooltip_arrow);
            tooltipMessage = getString("SlideUpToLock", R.string.SlideUpToLock);

            radiiLeft[0] = radiiLeft[1] = radiiLeft[6] = radiiLeft[7] = dp(3);
            radiiLeft[2] = radiiLeft[3] = radiiLeft[4] = radiiLeft[5] = 0;

            radiiRight[0] = radiiRight[1] = radiiRight[6] = radiiRight[7] = 0;
            radiiRight[2] = radiiRight[3] = radiiRight[4] = radiiRight[5] = dp(3);

            micDrawable = getResources().getDrawable(R.drawable.input_mic).mutate();
            vidDrawable = getResources().getDrawable(R.drawable.input_video).mutate();

            setWillNotDraw(false);
            updateColors();
        }

        public void showTooltipIfNeed() {
            if (SharedConfig.lockRecordAudioVideoHint < 3) {
                showTooltip = true;
                showTooltipStartTime = System.currentTimeMillis();
            }
        }

        public void showHintView() {
            hideHintView();
            hintView = new HintView2(getContext(), HintView2.DIRECTION_RIGHT);
            hintView.setJoint(1, 0);
            hintView.setMultilineText(true);
            int text;
            if (isInVideoMode) {
                text = voiceOnce ? R.string.VideoSetOnceHintEnabled : R.string.VideoSetOnceHint;
            } else {
                text = voiceOnce ? R.string.VoiceSetOnceHintEnabled : R.string.VoiceSetOnceHint;
            }
            hintView.setText(AndroidUtilities.replaceTags(getString(text)));
            hintView.setMaxWidthPx(HintView2.cutInFancyHalf(hintView.getText(), hintView.getTextPaint()));
            if (voiceOnce) {
                hintView.setIcon(R.raw.fire_on);
            } else {
                MessagesController.getGlobalMainSettings().edit().putInt("voiceoncehint", MessagesController.getGlobalMainSettings().getInt("voiceoncehint", 0) + 1).apply();
            }
            addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 54, 58));
            final HintView2 thisHintView = hintView;
            hintView.setOnHiddenListener(() -> {
                removeView(thisHintView);
                if (hintView == thisHintView) {
                    hintView = null;
                }
            });
            hintView.show();
        }

        public void hideHintView() {
            if (hintView != null) {
                HintView2 oldHintView = hintView;
                oldHintView.setOnHiddenListener(() -> removeView(oldHintView));
                oldHintView.hide();
                hintView = null;
            }
        }

        private int lastSize;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int currentSize = MeasureSpec.getSize(widthMeasureSpec);
            int h = dp(194 + 48 + 12);
            if (lastSize != currentSize) {
                lastSize = currentSize;
                tooltipLayout = new StaticLayout(tooltipMessage, tooltipPaint, dp(220), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
                int n = tooltipLayout.getLineCount();
                tooltipWidth = 0;
                for (int i = 0; i < n; i++) {
                    float w = tooltipLayout.getLineWidth(i);
                    if (w > tooltipWidth) {
                        tooltipWidth = w;
                    }
                }
            }
//            if (tooltipLayout != null && tooltipLayout.getLineCount() > 1) {
//                h += tooltipLayout.getHeight() - tooltipLayout.getLineBottom(0);
//            }
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
            );
        }

        private final RectF rectF = new RectF();
        public final RectF onceRect = new RectF();

        private long lastUpdateTime;

        private final Path path2 = new Path();
        private final float[] radiiLeft = new float[8], radiiRight = new float[8];

        private AnimatedFloat hidePauseT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        @Override
        protected void onDraw(Canvas canvas) {
            float sc;
            if (scale <= 0.5f) {
                sc = scale / 0.5f;
            } else if (scale <= 0.75f) {
                sc = 1.0f - (scale - 0.5f) / 0.25f * 0.1f;
            } else {
                sc = 0.9f + (scale - 0.75f) / 0.25f * 0.1f;
            }
            long dt = System.currentTimeMillis() - lastUpdateTime;
            lastUpdateTime = System.currentTimeMillis();

            float yAdd = 0;
            if (lockAnimatedTranslation != 10000) {
                yAdd = Math.max(0, (int) (startTranslation - lockAnimatedTranslation));
                if (yAdd > dp(57)) {
                    yAdd = dp(57);
                }
            }

            final int cx = getMeasuredWidth() - AndroidUtilities.dp2(26);
            float moveProgress = 1.0f - yAdd / dp(57);
            final float multilinTooltipOffset = getMeasuredHeight() - dp(194);

            float lockSize;
            float lockY;
            float lockTopY;
            float lockMiddleY;

            float lockRotation;
            float transformToPauseProgress = 0;
            if (sendButtonVisible) {
                lockSize = dp(36);
                lockY = dp(60) + multilinTooltipOffset + dpf2(30) * (1.0f - sc) - yAdd + dpf2(14f) * moveProgress;

                lockMiddleY = lockY + lockSize / 2f - dpf2(8) + dpf2(2);
                lockTopY =    lockY + lockSize / 2f - dpf2(16) + dpf2(2);
                float snapRotateBackProgress = moveProgress > 0.4f ? 1f : moveProgress / 0.4f;

                lockRotation = 9 * (1f - moveProgress) * (1f - snapAnimationProgress) - 15 * snapAnimationProgress * (1f - snapRotateBackProgress);

                transformToPauseProgress = moveProgress;
            } else {
                lockSize = dp(36) + (int) (dp(14) * moveProgress);
                lockY = dp(60) + multilinTooltipOffset + (int) (dp(30) * (1.0f - sc)) - (int) yAdd + (moveProgress) * idleProgress * -dp(8);
                lockMiddleY = lockY + lockSize / 2f - dpf2(8) + dpf2(2) + dpf2(2) * moveProgress;
                lockTopY =    lockY + lockSize / 2f - dpf2(16) + dpf2(2) + dpf2(2) * moveProgress;
                lockRotation = 9 * (1f - moveProgress);
                snapAnimationProgress = 0;
            }

            if ((showTooltip && System.currentTimeMillis() - showTooltipStartTime > 200) || tooltipAlpha != 0f) {
                if (moveProgress < 0.8f || sendButtonVisible || exitTransition != 0 || transformToSeekbar != 0) {
                    showTooltip = false;
                }
                if (showTooltip) {
                    if (tooltipAlpha != 1f) {
                        tooltipAlpha += dt / 150f;
                        if (tooltipAlpha >= 1f) {
                            tooltipAlpha = 1f;
                            SharedConfig.increaseLockRecordAudioVideoHintShowed();
                        }
                    }
                } else {
                    tooltipAlpha -= dt / 150f;
                    if (tooltipAlpha < 0) {
                        tooltipAlpha = 0f;
                    }
                }


                int alphaInt = (int) (tooltipAlpha * 255);

                tooltipBackground.setAlpha(alphaInt);
                tooltipBackgroundArrow.setAlpha(alphaInt);
                tooltipPaint.setAlpha(alphaInt);

                if (tooltipLayout != null) {
                    canvas.save();
                    rectF.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    canvas.translate(getMeasuredWidth() - tooltipWidth - dp(44), multilinTooltipOffset + dpf2(16));
                    tooltipBackground.setBounds(
                            -dp(8), -dp(2),
                            (int) (tooltipWidth + dp(36)), (int) (tooltipLayout.getHeight() + dpf2(4))
                    );
                    tooltipBackground.draw(canvas);
                    tooltipLayout.draw(canvas);
                    canvas.restore();

                    canvas.save();
                    canvas.translate(getMeasuredWidth() - dp(26), multilinTooltipOffset + dpf2(16 + 1) + tooltipLayout.getHeight() / 2f - idleProgress * dpf2(3f));
                    path.reset();
                    path.setLastPoint(-dpf2(5), dpf2(4));
                    path.lineTo(0, 0);
                    path.lineTo(dpf2(5), dpf2(4));

                    p.setColor(Color.WHITE);
                    p.setAlpha(alphaInt);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeJoin(Paint.Join.ROUND);
                    p.setStrokeWidth(dpf2(1.5f));
                    canvas.drawPath(path, p);
                    canvas.restore();

                    canvas.save();
                    tooltipBackgroundArrow.setBounds(
                            cx - tooltipBackgroundArrow.getIntrinsicWidth() / 2, (int) (tooltipLayout.getHeight() + multilinTooltipOffset + dpf2(20)),
                            cx + tooltipBackgroundArrow.getIntrinsicWidth() / 2, (int) (tooltipLayout.getHeight() + multilinTooltipOffset + dpf2(20)) + tooltipBackgroundArrow.getIntrinsicHeight()
                    );
                    tooltipBackgroundArrow.draw(canvas);
                    canvas.restore();
                }
            }

            float progressToSeekbarStep1 = 0f;
            float progressToSeekbarStep2 = 0;
            float exitProgress2 = 0f;
            float hidePause = hidePauseT.set(isInVideoMode && millisecondsRecorded >= 59_000);

            if (transformToSeekbar != 0 && recordedAudioBackground != null) {
                float step1Time = 0.38f;
                float step2Time = 0.25f;
//                float step3Time = 1f - step1Time - step2Time;

                progressToSeekbarStep1 = transformToSeekbar > step1Time ? 1f : transformToSeekbar / step1Time;
                progressToSeekbarStep2 = transformToSeekbar > step1Time + step2Time ? 1f : Math.max(0, (transformToSeekbar - step1Time) / step2Time);
//                progressToSeekbarStep3 = Math.max(0, (transformToSeekbar - step1Time - step2Time) / step3Time);

                progressToSeekbarStep1 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep1);
                progressToSeekbarStep2 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep2);
//                progressToSeekbarStep3 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep3);
//
//                radius = radius + AndroidUtilities.dp(16) * progressToSeekbarStep1;
//
//                float toRadius = recordedAudioBackground.getMeasuredHeight() / 2f;
//                radius = toRadius + (radius - toRadius) * (1f - progressToSeekbarStep2);
            } else if (exitTransition != 0) {
                float step1Time = 0.6f;
                float step2Time = 0.4f;

                progressToSeekbarStep1 = exitTransition > step1Time ? 1f : exitTransition / step1Time;
                exitProgress2 = messageTransitionIsRunning ? exitTransition : Math.max(0, (exitTransition - step1Time) / step2Time);

                progressToSeekbarStep1 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep1);
                exitProgress2 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(exitProgress2);
//
//                radius = radius + AndroidUtilities.dp(16) * progressToSeekbarStep1;
//                radius *= (1f - exitProgress2);
//
//                if (LiteMode.isEnabled(LiteMode.FLAGS_CHAT) && exitTransition > 0.6f) {
//                    circleAlpha = Math.max(0, 1f - (exitTransition - 0.6f) / 0.4f);
//                }
            }

            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - textFieldContainer.getMeasuredHeight());
            float translation = 0;
            if (1f - controlsScale != 0) {
                translation = 1f - controlsScale;
//            } else if (progressToSeekbarStep2 != 0) {
//                translation = progressToSeekbarStep2;
            } else if (exitProgress2 != 0) {
                translation = exitProgress2;
            }
            if (slideToCancelProgress < 0.7f || canceledByGesture) {
                showTooltip = false;
                if (slideToCancelLockProgress != 0) {
                    slideToCancelLockProgress -= 0.12f;
                    if (slideToCancelLockProgress < 0) {
                        slideToCancelLockProgress = 0;
                    }
                }
            } else {
                if (slideToCancelLockProgress != 1f) {
                    slideToCancelLockProgress += 0.12f;
                    if (slideToCancelLockProgress > 1f) {
                        slideToCancelLockProgress = 1f;
                    }
                }
            }

            float maxTranslationDy = dpf2(72);
            float dy = (
                maxTranslationDy * translation
                + dpf2(24) * (progressToSeekbarStep1) * (1f - translation)
                + maxTranslationDy * (1f - slideToCancelLockProgress)
            );
            if (dy > maxTranslationDy) {
                dy = maxTranslationDy;
            }
//            canvas.translate(0, dy);
            float s = (1f - hidePause) * controlsScale * (1f - exitProgress2) * slideToCancelLockProgress;
            canvas.scale(s, s, cx, lockMiddleY + dy);

            rectF.set(cx - dpf2(18), lockY + dy, cx + dpf2(18), lockY + dy + lockSize);
            lockShadowDrawable.setBounds(
                    (int) (rectF.left - dpf2(3)), (int) (rectF.top - dpf2(3)),
                    (int) (rectF.right + dpf2(3)), (int) (rectF.bottom + dpf2(3))
            );
            lockShadowDrawable.draw(canvas);
            canvas.drawRoundRect(rectF, dpf2(18), dpf2(18), lockBackgroundPaint);
            pauseRect.set(rectF);
            scale(pauseRect, s);

            rectF.set(
                    cx - dpf2(6) - dpf2(2) * (1f - transformToPauseProgress),
                    lockMiddleY + dy - dpf2(2) * (1f - transformToPauseProgress),
                    cx + dp(6) + dpf2(2) * (1f - transformToPauseProgress),
                    lockMiddleY + dy + dp(12) + dpf2(2) * (1f - transformToPauseProgress)
            );
            float lockBottom = rectF.bottom;
            float locCx = rectF.centerX();
            float locCy = rectF.centerY();
            canvas.save();
            canvas.translate(0, dpf2(2) * (1f - moveProgress));
            canvas.rotate(lockRotation, locCx, locCy);

            if (transformToPauseProgress != 1f) {
                AndroidUtilities.rectTmp.set(0, 0, dpf2(8), dpf2(8));
                canvas.save();
                canvas.clipRect(0, 0, getMeasuredWidth(), dy + lockBottom + dpf2(2) * (1f - moveProgress));
                canvas.translate(cx - dpf2(4), rectF.top - dp(6) - lerp(dpf2(2), dpf2(1.5f) * (1f - idleProgress), moveProgress) + dpf2(12) * transformToPauseProgress + dpf2(2) * snapAnimationProgress);
                if (lockRotation > 0) {
                    canvas.rotate(lockRotation, dp(8), dp(8));
                }
                canvas.drawLine(dpf2(8), dpf2(4), dpf2(8), dpf2(6) + dpf2(4) * (1f - transformToPauseProgress), lockOutlinePaint);
                canvas.drawArc(AndroidUtilities.rectTmp, 0, -180, false, lockOutlinePaint);
                canvas.drawLine(
                        0, dpf2(4),
                        0, dpf2(4) + dpf2(4) * idleProgress * (moveProgress) * (sendButtonVisible ? 0 : 1) + dpf2(4) * snapAnimationProgress * (1f - moveProgress),
                        lockOutlinePaint
                );
                canvas.restore();
            }

            Drawable resumeDrawable = null;
            float transformToResume = Utilities.clamp(transformToSeekbar * 2f, 1, 0);
            if (transformToResume > 0) {
                resumeDrawable = isInVideoMode ? vidDrawable : micDrawable;
            }

            int wasAlpha = lockPaint.getAlpha();
            lockPaint.setAlpha((int) (wasAlpha * (1f - transformToResume)));
            if (transformToPauseProgress > 0) {
                canvas.drawRoundRect(rectF, dpf2(3), dpf2(3), lockBackgroundPaint);

                path2.rewind();
                AndroidUtilities.rectTmp.set(rectF);
                AndroidUtilities.rectTmp.right = rectF.centerX() - dp(1.66f) * transformToPauseProgress;
                radiiLeft[0] = radiiLeft[1] = radiiLeft[6] = radiiLeft[7] = lerp(dp(3), dp(1.5f), transformToPauseProgress);
                radiiLeft[2] = radiiLeft[3] = radiiLeft[4] = radiiLeft[5] = dp(1.5f) * transformToPauseProgress;
                path2.addRoundRect(AndroidUtilities.rectTmp, radiiLeft, Path.Direction.CW);
                AndroidUtilities.rectTmp.set(rectF);
                AndroidUtilities.rectTmp.left = rectF.centerX() + dp(1.66f) * transformToPauseProgress;
                radiiRight[2] = radiiRight[3] = radiiRight[4] = radiiRight[5] = lerp(dp(3), dp(1.5f), transformToPauseProgress);
                radiiRight[0] = radiiRight[1] = radiiRight[6] = radiiRight[7] = dp(1.5f) * transformToPauseProgress;
                path2.addRoundRect(AndroidUtilities.rectTmp, radiiRight, Path.Direction.CW);
                canvas.drawPath(path2, lockPaint);
            } else {
                canvas.drawRoundRect(rectF, dpf2(3), dpf2(3), lockPaint);
            }
            lockPaint.setAlpha(wasAlpha);

            if (resumeDrawable != null) {
                final float _s = 0.9285f;
                AndroidUtilities.rectTmp2.set(
                    (int) (rectF.centerX() - resumeDrawable.getIntrinsicWidth() / 2 * _s),
                    (int) (rectF.centerY() - resumeDrawable.getIntrinsicHeight() / 2 * _s),
                    (int) (rectF.centerX() + resumeDrawable.getIntrinsicWidth() / 2 * _s),
                    (int) (rectF.centerY() + resumeDrawable.getIntrinsicHeight() / 2 * _s)
                );
                resumeDrawable.setBounds(AndroidUtilities.rectTmp2);
                resumeDrawable.setAlpha((int) (0xFF * transformToResume));
                resumeDrawable.draw(canvas);
            }

            if (transformToPauseProgress != 1) {
                canvas.drawCircle(locCx, locCy, dpf2(2) * (1f - transformToPauseProgress), lockBackgroundPaint);
            }
            canvas.restore();
            canvas.restore();

            final float cy = lerp(lockY, getMeasuredHeight() - dp(118), Math.max(exitTransition, Math.min(progressToSeekbarStep1, slideToCancelLockProgress))) + dy + dp(38) * hidePause;
            rectF.set(cx - dpf2(18), cy, cx + dpf2(18), cy + lockSize);
            onceVisible = delegate != null && delegate.onceVoiceAvailable();
            if (onceVisible) {
                final float onceOffset = dpf2(12);
                rectF.set(
                    rectF.left, rectF.top - dpf2(36) - onceOffset, rectF.right, rectF.top - onceOffset
                );
                if (hintView != null) {
                    hintView.setJointPx(0, rectF.centerY());
                    hintView.invalidate();
                }
                onceRect.set(rectF);
                canvas.save();
                final float s2 = controlsScale * (1f - exitTransition) * slideToCancelLockProgress * snapAnimationProgress;
                canvas.scale(s2, s2, rectF.centerX(), rectF.centerY());
                lockShadowDrawable.setBounds(
                    (int) (rectF.left - dpf2(3)), (int) (rectF.top - dpf2(3)),
                    (int) (rectF.right + dpf2(3)), (int) (rectF.bottom + dpf2(3))
                );
                lockShadowDrawable.draw(canvas);
                canvas.drawRoundRect(rectF, dpf2(18), dpf2(18), lockBackgroundPaint);
                periodDrawable.setBounds((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
                periodDrawable.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        protected boolean dispatchHoverEvent(MotionEvent event) {
            return super.dispatchHoverEvent(event) || virtualViewHelper.dispatchHoverEvent(event);
        }

        public void updateColors() {
            periodDrawable.updateColors(
                getThemedColor(Theme.key_chat_messagePanelVoiceLock),
                getThemedColor(Theme.key_chat_messagePanelVoiceBackground),
                0xFFFFFFFF
            );
            lockBackgroundPaint.setColor(getThemedColor(Theme.key_chat_messagePanelVoiceLockBackground));

            tooltipPaint.setColor(getThemedColor(Theme.key_chat_gifSaveHintText));
            tooltipBackground = Theme.createRoundRectDrawable(dp(5), getThemedColor(Theme.key_chat_gifSaveHintBackground));
            tooltipBackgroundArrow.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_gifSaveHintBackground), PorterDuff.Mode.SRC_IN));

            lockBackgroundPaint.setColor(getThemedColor(Theme.key_chat_messagePanelVoiceLockBackground));
            lockPaint.setColor(getThemedColor(Theme.key_chat_messagePanelVoiceLock));
            lockOutlinePaint.setColor(getThemedColor(Theme.key_chat_messagePanelVoiceLock));

            micDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelVoiceLock), PorterDuff.Mode.SRC_IN));
            vidDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelVoiceLock), PorterDuff.Mode.SRC_IN));
        }

        private void scale(RectF rect, float s) {
            final float cx = rect.centerX(), cy = rect.centerY();
            rect.left = lerp(cx, rect.left, s);
            rect.right = lerp(cx, rect.right, s);
            rect.top = lerp(cy, rect.top, s);
            rect.bottom = lerp(cy, rect.bottom, s);
        }

        private boolean oncePressed;
        private boolean pausePressed;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int x = (int) event.getX();
            final int y = (int) event.getY();

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (sendButtonVisible) {
                    pausePressed = pauseRect.contains(x, y);
                }
                if (onceVisible && (recordCircle != null && snapAnimationProgress > .1f)) {
                    oncePressed = onceRect.contains(x, y);
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pausePressed && pauseRect.contains(x, y)) {
                    if (isInVideoMode()) {
                        if (slideText != null) {
                            slideText.setEnabled(false);
                        }
                        delegate.toggleVideoRecordingPause();
                    } else {
                        if (sendButtonVisible) {
                            calledRecordRunnable = true;
                        }
                        MediaController.getInstance().toggleRecordingPause(voiceOnce);
                        delegate.needStartRecordAudio(0);
                        if (slideText != null) {
                            slideText.setEnabled(false);
                        }
                    }
                    pausePressed = oncePressed = false;
                    return true;
                } else if (oncePressed && onceRect.contains(x, y)) {
                    voiceOnce = !voiceOnce;
                    periodDrawable.setValue(1, voiceOnce, true);
                    MediaDataController.getInstance(currentAccount).toggleDraftVoiceOnce(dialog_id, parentFragment != null && parentFragment.isTopic ? parentFragment.getTopicId() : 0, voiceOnce);
                    if (voiceOnce) {
                        showHintView();
                    } else {
                        hideHintView();
                    }
                    invalidate();
                    pausePressed = oncePressed = false;
                    return true;
                }
                pausePressed = oncePressed = false;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                pausePressed = oncePressed = false;
            }
            return pausePressed || oncePressed;
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == periodDrawable || super.verifyDrawable(who);
        }


        private class VirtualViewHelper extends ExploreByTouchHelper {

            public VirtualViewHelper(@NonNull View host) {
                super(host);
            }

            @Override
            protected int getVirtualViewAt(float x, float y) {
                if (sendButtonVisible && recordCircle != null && pauseRect.contains(x, y)) {
                    return 2;
                }
                if (onceVisible && (recordCircle != null && snapAnimationProgress > .1f) && onceRect.contains(x, y)) {
                    return 4;
                }
                return HOST_ID;
            }

            @Override
            protected void getVisibleVirtualViews(List<Integer> list) {
                if (sendButtonVisible) {
                    list.add(2);
                }
                if (onceVisible && (recordCircle != null && snapAnimationProgress > .1f)) {
                    list.add(4);
                }
            }

            @Override
            protected void onPopulateNodeForVirtualView(int id, @NonNull AccessibilityNodeInfoCompat info) {
                if (id == 2) {
                    rect.set((int) pauseRect.left, (int) pauseRect.top, (int) pauseRect.right, (int) pauseRect.bottom);
                    info.setBoundsInParent(rect);
                    info.setText(getString(transformToSeekbar > .5f ? R.string.AccActionResume : R.string.AccActionPause));
                } else if (id == 4) {
                    rect.set((int) onceRect.left, (int) onceRect.top, (int) onceRect.right, (int) onceRect.bottom);
                    info.setBoundsInParent(rect);
                    info.setText(getString(voiceOnce ? R.string.AccActionOnceDeactivate : R.string.AccActionOnceActivate));
                }
            }

            @Override
            protected boolean onPerformActionForVirtualView(int id, int action, @Nullable Bundle args) {
                return true;
            }
        }
    }

    private float scale;
    private float controlsScale;
    private float slideToCancelProgress;
    private float startTranslation;
    private float lockAnimatedTranslation;
    private float exitTransition;
    private float snapAnimationProgress;
    float idleProgress;
    private float transformToSeekbar;
    private float progressToSeekbarStep1, progressToSeekbarStep2;
    private float slideToCancelLockProgress;
    private boolean canceledByGesture;
    private boolean sendButtonVisible;
    private int slideDelta;

    @Keep
    public float getExitTransition() {
        return exitTransition;
    }

    @Keep
    public void setExitTransition(float value) {
        exitTransition = value;
        if (recordCircle != null) {
            recordCircle.invalidate();
        }
    }

    @Keep
    public float getSlideToCancelProgress() {
        return slideToCancelProgress;
    }

    @Keep
    public void setSlideToCancelProgress(float value) {
        slideToCancelProgress = value;
        float distance = getMeasuredWidth() * 0.35f;
        if (distance > dp(140)) {
            distance = dp(140);
        }
        slideDelta = (int) (-distance * (1f - slideToCancelProgress));
        if (recordCircle != null) {
            recordCircle.invalidate();
        }
    }

    @Keep
    public float getLockAnimatedTranslation() {
        return lockAnimatedTranslation;
    }
    @Keep
    public void setLockAnimatedTranslation(float value) {
        lockAnimatedTranslation = value;
        if (recordCircle != null) {
            recordCircle.invalidate();
        }
    }

    @Keep
    public void setSnapAnimationProgress(float value) {
        snapAnimationProgress = value;
        invalidate();
    }

    public boolean seekbarVisible() {
        return !recordIsCanceled && transformToSeekbar > 0;
    }

    public class RecordCircle extends View {

        private float amplitude;
        private float animateToAmplitude;
        private float animateAmplitudeDiff;
        private long lastUpdateTime;
        private boolean pressed;
        public float progressToSeekbarStep3;
        private float progressToSendButton;

        public float iconScale;

        BlobDrawable tinyWaveDrawable = new BlobDrawable(11, LiteMode.FLAGS_CHAT);
        BlobDrawable bigWaveDrawable = new BlobDrawable(12, LiteMode.FLAGS_CHAT);

        private float circleRadius = dpf2(41);
        private float circleRadiusAmplitude = dp(30);
        RectF rectF = new RectF();

        boolean incIdle;

        private VirtualViewHelper virtualViewHelper;

        private int paintAlpha;
        private float touchSlop;

        private float lastMovingX;
        private float lastMovingY;

        private float wavesEnterAnimation = 0f;
        private boolean showWaves = true;

        public float drawingCx, drawingCy, drawingCircleRadius;

        public boolean voiceEnterTransitionInProgress;
        public boolean skipDraw;
        private int lastSize;

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
        }

        public RecordCircle(Context context) {
            super(context);

            virtualViewHelper = new VirtualViewHelper(this);
            ViewCompat.setAccessibilityDelegate(this, virtualViewHelper);

            tinyWaveDrawable.minRadius = dp(47);
            tinyWaveDrawable.maxRadius = dp(55);
            tinyWaveDrawable.generateBlob();

            bigWaveDrawable.minRadius = dp(47);
            bigWaveDrawable.maxRadius = dp(55);
            bigWaveDrawable.generateBlob();
            iconScale = 1f;

            final ViewConfiguration vc = ViewConfiguration.get(context);
            touchSlop = vc.getScaledTouchSlop();
            touchSlop *= touchSlop;

            updateColors();
        }

        private void checkDrawables() {
            if (micDrawable != null) {
                return;
            }
            micDrawable = getResources().getDrawable(R.drawable.input_mic_pressed).mutate();
            micDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));

            cameraDrawable = getResources().getDrawable(R.drawable.input_video_pressed).mutate();
            cameraDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));

            sendDrawable = getResources().getDrawable(R.drawable.attach_send).mutate();
            sendDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));

            micOutline = getResources().getDrawable(R.drawable.input_mic).mutate();
            micOutline.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));

            cameraOutline = getResources().getDrawable(R.drawable.input_video).mutate();
            cameraOutline.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        }

        public void setAmplitude(double value) {
            bigWaveDrawable.setValue((float) (Math.min(WaveDrawable.MAX_AMPLITUDE, value) / WaveDrawable.MAX_AMPLITUDE), true);
            tinyWaveDrawable.setValue((float) (Math.min(WaveDrawable.MAX_AMPLITUDE, value) / WaveDrawable.MAX_AMPLITUDE), false);

            animateToAmplitude = (float) (Math.min(WaveDrawable.MAX_AMPLITUDE, value) / WaveDrawable.MAX_AMPLITUDE);
            animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100 + 500.0f * WaveDrawable.animationSpeedCircle);

            invalidate();
        }

        public float getScale() {
            return scale;
        }

        @Keep
        public void setScale(float value) {
            scale = value;
            invalidate();
        }

        public float getControlsScale() {
            return controlsScale;
        }

        @Keep
        public void setControlsScale(float value) {
            controlsScale = value;
            if (controlsView != null) {
                controlsView.invalidate();
            }
        }

        public boolean isSendButtonVisible() {
            return sendButtonVisible;
        }

        public void setSendButtonInvisible() {
            sendButtonVisible = false;
            invalidate();
            if (controlsView != null) {
                controlsView.invalidate();
            }
        }

        public void resetLockTranslation(boolean toLock) {
            if (!toLock) {
                sendButtonVisible = false;
                lockAnimatedTranslation = -1;
                startTranslation = -1;
                slideToCancelProgress = 1f;
                slideToCancelLockProgress = 1f;
                snapAnimationProgress = 0;
                controlsScale = 0f;
            }
            invalidate();
            transformToSeekbar = 0;
            isRecordingStateChanged();
            exitTransition = 0;
            iconScale = 1f;
            scale = 0f;
            tooltipAlpha = 0f;
            showTooltip = false;
            progressToSendButton = 0f;
            canceledByGesture = false;
            if (controlsView != null) {
                controlsView.invalidate();
            }
        }

        public int setLockTranslation(float value) {
            if (sendButtonVisible) {
                return 2;
            }
            if (lockAnimatedTranslation == -1) {
                startTranslation = value;
            }
            lockAnimatedTranslation = value;
            invalidate();
            if (canceledByGesture || slideToCancelProgress < 0.7f) {
                return 1;
            }
            if (startTranslation - lockAnimatedTranslation >= dp(57)) {
                sendButtonVisible = true;
                return 2;
            }
            return 1;
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int currentSize = MeasureSpec.getSize(widthMeasureSpec);
            int h = dp(194);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));

            float distance = getMeasuredWidth() * 0.35f;
            if (distance > dp(140)) {
                distance = dp(140);
            }
            slideDelta = (int) (-distance * (1f - slideToCancelProgress));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (skipDraw) {
                return;
            }
            float multilinTooltipOffset = 0;
//            if (tooltipLayout != null && tooltipLayout.getLineCount() > 1) {
//                multilinTooltipOffset = tooltipLayout.getHeight() - tooltipLayout.getLineBottom(0);
//            }
            int cx = getMeasuredWidth() - AndroidUtilities.dp2(26);
            int cy = (int) (dp(170) + multilinTooltipOffset);
//            float yAdd = 0;
//            if (lockAnimatedTranslation != 10000) {
//                yAdd = Math.max(0, (int) (startTranslation - lockAnimatedTranslation));
//                if (yAdd > AndroidUtilities.dp(57)) {
//                    yAdd = AndroidUtilities.dp(57);
//                }
//            }

            drawingCx = cx + slideDelta;
            drawingCy = cy;

            float sc;
            float circleAlpha = 1f;
            if (scale <= 0.5f) {
                sc = scale / 0.5f;
            } else if (scale <= 0.75f) {
                sc = 1.0f - (scale - 0.5f) / 0.25f * 0.1f;
            } else {
                sc = 0.9f + (scale - 0.75f) / 0.25f * 0.1f;
            }
            long dt = System.currentTimeMillis() - lastUpdateTime;
            if (animateToAmplitude != amplitude) {
                amplitude += animateAmplitudeDiff * dt;
                if (animateAmplitudeDiff > 0) {
                    if (amplitude > animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                } else {
                    if (amplitude < animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                }
                invalidate();
            }

            float slideToCancelScale;
            if (canceledByGesture) {
                slideToCancelScale = 0.7f * CubicBezierInterpolator.EASE_OUT.getInterpolation(1f - slideToCancelProgress);
            } else {
                slideToCancelScale = (0.7f + slideToCancelProgress * 0.3f);
            }
            float radius = (circleRadius + circleRadiusAmplitude * amplitude) * sc * slideToCancelScale;

            progressToSeekbarStep3 = 0f;
            float progressToSeekbarStep1 = 0f;
            float progressToSeekbarStep2 = 0;
            float exitProgress2 = 0f;

            if (transformToSeekbar != 0 && recordedAudioBackground != null) {
                float step1Time = 0.38f;
                float step2Time = 0.25f;
                float step3Time = 1f - step1Time - step2Time;

                progressToSeekbarStep1 = transformToSeekbar > step1Time ? 1f : transformToSeekbar / step1Time;
                progressToSeekbarStep2 = transformToSeekbar > step1Time + step2Time ? 1f : Math.max(0, (transformToSeekbar - step1Time) / step2Time);
                progressToSeekbarStep3 = Math.max(0, (transformToSeekbar - step1Time - step2Time) / step3Time);

                progressToSeekbarStep1 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep1);
                progressToSeekbarStep2 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep2);
                progressToSeekbarStep3 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep3);

                radius = radius + dp(16) * progressToSeekbarStep1;

                float toRadius = recordedAudioBackground.getMeasuredHeight() / 2f;
                radius = toRadius + (radius - toRadius) * (1f - progressToSeekbarStep2);
            } else if (exitTransition != 0) {
                float step1Time = 0.6f;
                float step2Time = 0.4f;

                progressToSeekbarStep1 = exitTransition > step1Time ? 1f : exitTransition / step1Time;
                exitProgress2 = messageTransitionIsRunning ? exitTransition : Math.max(0, (exitTransition - step1Time) / step2Time);

                progressToSeekbarStep1 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep1);
                exitProgress2 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(exitProgress2);

                radius = radius + dp(16) * progressToSeekbarStep1;
                radius *= (1f - exitProgress2);

                if (LiteMode.isEnabled(LiteMode.FLAGS_CHAT) && exitTransition > 0.6f) {
                    circleAlpha = Math.max(0, 1f - (exitTransition - 0.6f) / 0.4f);
                }
            }

            if (canceledByGesture && slideToCancelProgress > 0.7f) {
                circleAlpha *= (1f - (slideToCancelProgress - 0.7f) / 0.3f);
            }

            if (progressToSeekbarStep3 > 0) {
                paint.setColor(ColorUtils.blendARGB(getThemedColor(Theme.key_chat_messagePanelVoiceBackground), getThemedColor(Theme.key_chat_recordedVoiceBackground), progressToSeekbarStep3));
            } else {
                paint.setColor(getThemedColor(Theme.key_chat_messagePanelVoiceBackground));
            }

            Drawable drawable;
            Drawable replaceDrawable = null;
            checkDrawables();
            if (isSendButtonVisible()) {
                if (progressToSendButton != 1f) {
                    progressToSendButton += dt / 150f;
                    if (progressToSendButton > 1f) {
                        progressToSendButton = 1f;
                    }
                    replaceDrawable = isInVideoMode() ? cameraDrawable : micDrawable;
                }
                drawable = sendDrawable;
            } else {
                drawable = isInVideoMode() ? cameraDrawable : micDrawable;
            }
            sendRect.set(cx - drawable.getIntrinsicWidth() / 2, cy - drawable.getIntrinsicHeight() / 2, cx + drawable.getIntrinsicWidth() / 2, cy + drawable.getIntrinsicHeight() / 2);
            drawable.setBounds(sendRect);
            if (replaceDrawable != null) {
                replaceDrawable.setBounds(cx - replaceDrawable.getIntrinsicWidth() / 2, cy - replaceDrawable.getIntrinsicHeight() / 2, cx + replaceDrawable.getIntrinsicWidth() / 2, cy + replaceDrawable.getIntrinsicHeight() / 2);
            }

            if (incIdle) {
                idleProgress += 0.01f;
                if (idleProgress > 1f) {
                    incIdle = false;
                    idleProgress = 1f;
                }
            } else {
                idleProgress -= 0.01f;
                if (idleProgress < 0) {
                    incIdle = true;
                    idleProgress = 0;
                }
            }

            if (LiteMode.isEnabled(LiteMode.FLAGS_CHAT)) {
                tinyWaveDrawable.minRadius = dp(47);
                tinyWaveDrawable.maxRadius = dp(47) + dp(15) * BlobDrawable.FORM_SMALL_MAX;

                bigWaveDrawable.minRadius = dp(50);
                bigWaveDrawable.maxRadius = dp(50) + dp(12) * BlobDrawable.FORM_BIG_MAX;

                bigWaveDrawable.updateAmplitude(dt);
                bigWaveDrawable.update(bigWaveDrawable.amplitude, 1.01f);
                tinyWaveDrawable.updateAmplitude(dt);
                tinyWaveDrawable.update(tinyWaveDrawable.amplitude, 1.02f);

//                bigWaveDrawable.tick(radius);
//                tinyWaveDrawable.tick(radius);
            }
            lastUpdateTime = System.currentTimeMillis();
            float slideToCancelProgress1 = slideToCancelProgress > 0.7f ? 1f : slideToCancelProgress / 0.7f;

            if (LiteMode.isEnabled(LiteMode.FLAGS_CHAT) && progressToSeekbarStep2 != 1 && exitProgress2 < 0.4f && slideToCancelProgress1 > 0 && !canceledByGesture) {
                if (showWaves && wavesEnterAnimation != 1f) {
                    wavesEnterAnimation += 0.04f;
                    if (wavesEnterAnimation > 1f) {
                        wavesEnterAnimation = 1f;
                    }
                }
                if (!voiceEnterTransitionInProgress) {
                    float enter = CubicBezierInterpolator.EASE_OUT.getInterpolation(wavesEnterAnimation);
                    canvas.save();
                    float s = scale * (1f - progressToSeekbarStep1) * slideToCancelProgress1 * enter * (BlobDrawable.SCALE_BIG_MIN + 1.4f * bigWaveDrawable.amplitude);
                    canvas.scale(s, s, cx + slideDelta, cy);
                    bigWaveDrawable.draw(cx + slideDelta, cy, canvas, bigWaveDrawable.paint);
                    canvas.restore();
                    s = scale * (1f - progressToSeekbarStep1) * slideToCancelProgress1 * enter * (BlobDrawable.SCALE_SMALL_MIN + 1.4f * tinyWaveDrawable.amplitude);
                    canvas.save();
                    canvas.scale(s, s, cx + slideDelta, cy);
                    tinyWaveDrawable.draw(cx + slideDelta, cy, canvas, tinyWaveDrawable.paint);
                    canvas.restore();
                }
            }

            if (!voiceEnterTransitionInProgress) {
                paint.setAlpha((int) (paintAlpha * circleAlpha));
                if (scale == 1f) {
                    if (transformToSeekbar != 0) {
                        if (!isInVideoMode && progressToSeekbarStep3 > 0 && recordedAudioBackground != null) {
                            float circleB = cy + radius;
                            float circleT = cy - radius;
                            float circleR = cx + slideDelta + radius;
                            float circleL = cx + slideDelta - radius;

                            int topOffset = 0;
                            int leftOffset = 0;

                            View transformToView = recordedAudioBackground;
                            View v = (View) transformToView.getParent();
                            while (v != getParent()) {
                                topOffset += v.getY();
                                leftOffset += v.getX();
                                v = (View) v.getParent();
                            }

                            float seekbarT = transformToView.getY() + topOffset - getY();
                            float seekbarB = transformToView.getY() + transformToView.getMeasuredHeight() + topOffset - getY();
                            float seekbarR = transformToView.getX() + transformToView.getMeasuredWidth() + leftOffset - getX() - horizontalPadding;
                            float seekbarL = transformToView.getX() + leftOffset - getX() + horizontalPadding;
                            float toRadius = isInVideoMode() ? 0 : transformToView.getMeasuredHeight() / 2f;

                            float top = seekbarT + (circleT - seekbarT) * (1f - progressToSeekbarStep3);
                            float bottom = seekbarB + (circleB - seekbarB) * (1f - progressToSeekbarStep3);
                            float left = seekbarL + (circleL - seekbarL) * (1f - progressToSeekbarStep3);
                            float right = seekbarR + (circleR - seekbarR) * (1f - progressToSeekbarStep3);
                            float transformRadius = toRadius + (radius - toRadius) * (1f - progressToSeekbarStep3);

                            rectF.set(left, top, right, bottom);
                            canvas.drawRoundRect(rectF, transformRadius, transformRadius, paint);
                        } else {
                            canvas.drawCircle(cx + slideDelta, cy, radius * (1f - progressToSeekbarStep3), paint);
                        }
                    } else {
                        canvas.drawCircle(cx + slideDelta, cy, radius, paint);
                    }
                    canvas.save();
                    float a = (1f - exitProgress2);
                    canvas.translate(slideDelta, 0);
                    drawIconInternal(canvas, drawable, replaceDrawable, progressToSendButton, (int) ((1f - progressToSeekbarStep2) * a * 255));
                    canvas.restore();
                }
            }

            if (scale != 1f) {
                canvas.drawCircle(cx + slideDelta, cy, radius, paint);
                float a = (canceledByGesture ? (1f - slideToCancelProgress) : 1);
                canvas.save();
                canvas.translate(slideDelta, 0);
                drawIconInternal(canvas, drawable, replaceDrawable, progressToSendButton, (int) (255 * a));
                canvas.restore();
            }
            drawingCircleRadius = radius;
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (controlsView != null) {
                controlsView.invalidate();
            }
        }

        public void drawIcon(Canvas canvas, int cx, int cy, float alpha) {
            Drawable drawable;
            Drawable replaceDrawable = null;
            checkDrawables();
            if (isSendButtonVisible()) {
                if (progressToSendButton != 1f) {
                    replaceDrawable = isInVideoMode() ? cameraDrawable : micDrawable;
                }
                drawable = sendDrawable;
            } else {
                drawable = isInVideoMode() ? cameraDrawable : micDrawable;
            }
            sendRect.set(cx - drawable.getIntrinsicWidth() / 2, cy - drawable.getIntrinsicHeight() / 2, cx + drawable.getIntrinsicWidth() / 2, cy + drawable.getIntrinsicHeight() / 2);
            drawable.setBounds(sendRect);
            if (replaceDrawable != null) {
                replaceDrawable.setBounds(cx - replaceDrawable.getIntrinsicWidth() / 2, cy - replaceDrawable.getIntrinsicHeight() / 2, cx + replaceDrawable.getIntrinsicWidth() / 2, cy + replaceDrawable.getIntrinsicHeight() / 2);
            }

            drawIconInternal(canvas, drawable, replaceDrawable, progressToSendButton, (int) (255 * alpha));
        }

        private void drawIconInternal(Canvas canvas, Drawable drawable, Drawable replaceDrawable, float progressToSendButton, int alpha) {
            checkDrawables();
            if (progressToSendButton == 0 || progressToSendButton == 1 || replaceDrawable == null) {
                if (canceledByGesture && slideToCancelProgress == 1f) {
                    View v = audioVideoSendButton;
                    v.setAlpha(1f);
                    setVisibility(View.GONE);
                    return;
                }
                if (canceledByGesture && slideToCancelProgress < 1f) {
                    Drawable outlineDrawable = isInVideoMode() ? cameraOutline : micOutline;
                    outlineDrawable.setBounds(drawable.getBounds());
                    int a = (int) (slideToCancelProgress < 0.93f ? 0f : (slideToCancelProgress - 0.93f) / 0.07f * 255);
                    outlineDrawable.setAlpha(a);
                    outlineDrawable.draw(canvas);
                    outlineDrawable.setAlpha(255);

                    drawable.setAlpha(255 - a);
                    drawable.draw(canvas);
                } else if (!canceledByGesture) {
                    drawable.setAlpha(alpha);
                    drawable.draw(canvas);
                }
            } else {
                canvas.save();
                canvas.scale(progressToSendButton, progressToSendButton, drawable.getBounds().centerX(), drawable.getBounds().centerY());
                drawable.setAlpha((int) (alpha * progressToSendButton));
                drawable.draw(canvas);
                canvas.restore();

                canvas.save();
                canvas.scale(1f - progressToSendButton, 1f - progressToSendButton, drawable.getBounds().centerX(), drawable.getBounds().centerY());
                replaceDrawable.setAlpha((int) (alpha * (1f - progressToSendButton)));
                replaceDrawable.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        protected boolean dispatchHoverEvent(MotionEvent event) {
            return super.dispatchHoverEvent(event) || virtualViewHelper.dispatchHoverEvent(event);
        }

        public void setTransformToSeekbar(float value) {
            transformToSeekbar = value;
            invalidate();
        }

        public float getTransformToSeekbarProgressStep3() {
            return progressToSeekbarStep3;
        }

        public void updateColors() {
            paint.setColor(getThemedColor(Theme.key_chat_messagePanelVoiceBackground));
            tinyWaveDrawable.paint.setColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_chat_messagePanelVoiceBackground), (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
            bigWaveDrawable.paint.setColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_chat_messagePanelVoiceBackground), (int) (255 * WaveDrawable.CIRCLE_ALPHA_1)));

            paintAlpha = paint.getAlpha();
        }

        public void canceledByGesture() {
            canceledByGesture = true;
        }

        public void setMovingCords(float x, float y) {
            float delta = (x - lastMovingX) * (x - lastMovingX) + (y - lastMovingY) * (y - lastMovingY);
            lastMovingY = y;
            lastMovingX = x;
            if (showTooltip && tooltipAlpha == 0f && delta > touchSlop) {
                showTooltipStartTime = System.currentTimeMillis();
            }
        }

        public void showWaves(boolean b, boolean animated) {
            if (!animated) {
                wavesEnterAnimation = b ? 1f : 0.5f;
            }
            showWaves = b;
        }

        public void drawWaves(Canvas canvas, float cx, float cy, float additionalScale) {
            float enter = CubicBezierInterpolator.EASE_OUT.getInterpolation(wavesEnterAnimation);
            float slideToCancelProgress1 = slideToCancelProgress > 0.7f ? 1f : slideToCancelProgress / 0.7f;
            canvas.save();
            float s = scale * slideToCancelProgress1 * enter * (BlobDrawable.SCALE_BIG_MIN + 1.4f * bigWaveDrawable.amplitude) * additionalScale;
            canvas.scale(s, s, cx, cy);
            bigWaveDrawable.draw(cx, cy, canvas, bigWaveDrawable.paint);
            canvas.restore();
            s = scale * slideToCancelProgress1 * enter * (BlobDrawable.SCALE_SMALL_MIN + 1.4f * tinyWaveDrawable.amplitude) * additionalScale;
            canvas.save();
            canvas.scale(s, s, cx, cy);
            tinyWaveDrawable.draw(cx, cy, canvas, tinyWaveDrawable.paint);
            canvas.restore();
        }

        private class VirtualViewHelper extends ExploreByTouchHelper {

            public VirtualViewHelper(@NonNull View host) {
                super(host);
            }

            private int[] coords = new int[2];

            @Override
            protected int getVirtualViewAt(float x, float y) {
                if (isSendButtonVisible() && recordCircle != null) {
                    if (sendRect.contains((int) x, (int) y)) {
                        return 1;
                    } else if (pauseRect.contains(x, y)) {
                        return 2;
                    } else if (slideText != null && slideText.cancelRect != null) {
                        AndroidUtilities.rectTmp.set(slideText.cancelRect);
                        slideText.getLocationOnScreen(coords);
                        AndroidUtilities.rectTmp.offset(coords[0], coords[1]);
                        recordCircle.getLocationOnScreen(coords);
                        AndroidUtilities.rectTmp.offset(-coords[0], -coords[1]);
                        if (AndroidUtilities.rectTmp.contains(x, y)) {
                            return 3;
                        }
                    }
                }
                return HOST_ID;
            }

            @Override
            protected void getVisibleVirtualViews(List<Integer> list) {
                if (isSendButtonVisible()) {
                    list.add(1);
//                    list.add(2);
                    list.add(3);
                }
            }

            @Override
            protected void onPopulateNodeForVirtualView(int id, @NonNull AccessibilityNodeInfoCompat info) {
                if (id == 1) {
                    info.setBoundsInParent(sendRect);
                    info.setText(getString("Send", R.string.Send));
                } else if (id == 2) {
                    rect.set((int) pauseRect.left, (int) pauseRect.top, (int) pauseRect.right, (int) pauseRect.bottom);
                    info.setBoundsInParent(rect);
                    info.setText(getString(R.string.Stop));
                } else if (id == 3 && recordCircle != null) {
                    if (slideText != null && slideText.cancelRect != null) {
                        AndroidUtilities.rectTmp2.set(slideText.cancelRect);
                        slideText.getLocationOnScreen(coords);
                        AndroidUtilities.rectTmp2.offset(coords[0], coords[1]);
                        recordCircle.getLocationOnScreen(coords);
                        AndroidUtilities.rectTmp2.offset(-coords[0], -coords[1]);
                        info.setBoundsInParent(AndroidUtilities.rectTmp2);
                    }
                    info.setText(getString("Cancel", R.string.Cancel));
                }
            }

            @Override
            protected boolean onPerformActionForVirtualView(int id, int action, @Nullable Bundle args) {
                return true;
            }
        }
    }

    public ChatActivityEnterView(Activity context, SizeNotifierFrameLayout parent, ChatActivity fragment, final boolean isChat) {
        this(context, parent, fragment, isChat, null);
    }

    @SuppressLint("ClickableViewAccessibility")
    public ChatActivityEnterView(Activity context, SizeNotifierFrameLayout parent, ChatActivity fragment, final boolean isChat, Theme.ResourcesProvider resourcesProvider) {
        super(context, fragment == null ? null : fragment.contentView);
        this.resourcesProvider = resourcesProvider;
        this.backgroundColor = getThemedColor(Theme.key_chat_messagePanelBackground);
        this.drawBlur = false;
        this.isChat = isChat;

        smoothKeyboard = isChat && !AndroidUtilities.isInMultiwindow && (fragment == null || !fragment.isInBubbleMode());
        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(getThemedColor(Theme.key_chat_emojiPanelNewTrending));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);
        setClipChildren(false);

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordPaused);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordResumed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.audioRouteChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messageReceivedByServer2);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.sendingMessagesChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.audioRecordTooShort);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateBotMenuButton);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdatePremiumGiftFieldIcon);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        parentActivity = context;
        parentFragment = fragment;
        if (fragment != null) {
            recordingGuid = parentFragment.getClassGuid();
        }
        sizeNotifierLayout = parent;
        sizeNotifierLayout.setDelegate(this);
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        sendByEnter = preferences.getBoolean("send_by_enter", false);

        textFieldContainer = new FrameLayout(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (botWebViewButton != null && botWebViewButton.getVisibility() == VISIBLE) {
                    return botWebViewButton.dispatchTouchEvent(ev);
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        textFieldContainer.setClipChildren(false);
        textFieldContainer.setClipToPadding(false);
        textFieldContainer.setPadding(0, dp(1), 0, 0);
        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 1, 0, 0));

        FrameLayout frameLayout = messageEditTextContainer = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (scheduledButton != null) {
                    int x = getMeasuredWidth() - dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48) - dp(48);
                    scheduledButton.layout(x, scheduledButton.getTop(), x + scheduledButton.getMeasuredWidth(), scheduledButton.getBottom());
                }
                if (!animationParamsX.isEmpty()) {
                    for (int i = 0; i < getChildCount(); i++) {
                        View child = getChildAt(i);
                        Float fromX = animationParamsX.get(child);
                        if (fromX != null) {
                            child.setTranslationX(fromX - child.getLeft());
                            child.animate().translationX(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                        }
                    }
                    animationParamsX.clear();
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child != null && child == messageEditText) {
                    return drawMessageEditText(canvas, () -> super.drawChild(canvas, child, drawingTime));
                }
                if (shouldDrawRecordedAudioPanelInParent && child == recordedAudioPanel) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return super.onTouchEvent(event);
            }
        };
        frameLayout.setClipChildren(false);
        textFieldContainer.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 48, 0));

        emojiButton = new ChatActivityEnterViewAnimatedIconView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (getTag() != null && attachLayout != null && !emojiViewVisible && !MediaDataController.getInstance(currentAccount).getUnreadStickerSets().isEmpty() && dotPaint != null) {
                    int x = getWidth() / 2 + dp(4 + 5);
                    int y = getHeight() / 2 - dp(13 - 5);
                    canvas.drawCircle(x, y, dp(5), dotPaint);
                }
            }
        };
        emojiButton.setContentDescription(getString(R.string.AccDescrEmojiButton));
        emojiButton.setFocusable(true);
        int padding = dp(9.5f);
        emojiButton.setPadding(padding, padding, padding, padding);
        emojiButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.SRC_IN));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            emojiButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }
        emojiButton.setOnClickListener(v -> {
            if (adjustPanLayoutHelper != null && adjustPanLayoutHelper.animationInProgress()) {
                return;
            }
            if (emojiButtonRestricted) {
                showRestrictedHint();
                return;
            }
//            if (hasBotWebView() && botCommandsMenuIsShowing()) {
//                if (botWebViewMenuContainer != null) {
//                    botWebViewMenuContainer.dismiss(v::callOnClick);
//                }
//                return;
//            }

            if (!isPopupShowing() || currentPopupContentType != 0) {
                showPopup(1, 0);
                emojiView.onOpen(messageEditText != null && messageEditText.length() > 0, parentFragment != null && parentFragment.groupEmojiPackHintWasVisible());
            } else {
                if (searchingType != 0) {
                    setSearchingTypeInternal(0, true);
                    if (emojiView != null) {
                        emojiView.closeSearch(false);
                    }
                    if (messageEditText != null) {
                        messageEditText.requestFocus();
                    }
                }
                if (stickersExpanded) {
                    setStickersExpanded(false, true, false);
                    waitingForKeyboardOpenAfterAnimation = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        waitingForKeyboardOpenAfterAnimation = false;
                        openKeyboardInternal();
                    }, 200);
                } else {
                    openKeyboardInternal();
                }
            }
        });
        messageEditTextContainer.addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT, 3, 0, 0, 0));
        setEmojiButtonImage(false, false);

        if (isChat) {
            attachLayout = new LinearLayout(context);
            attachLayout.setOrientation(LinearLayout.HORIZONTAL);
            attachLayout.setEnabled(false);
            attachLayout.setPivotX(dp(48));
            attachLayout.setClipChildren(false);
            messageEditTextContainer.addView(attachLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.BOTTOM | Gravity.RIGHT));

            notifyButton = new ImageView(context);
            notifySilentDrawable = new CrossOutDrawable(context, R.drawable.input_notify_on, Theme.key_chat_messagePanelIcons);
            notifyButton.setImageDrawable(notifySilentDrawable);
            notifySilentDrawable.setCrossOut(silent, false);
            notifyButton.setContentDescription(silent ? getString("AccDescrChanSilentOn", R.string.AccDescrChanSilentOn) : getString("AccDescrChanSilentOff", R.string.AccDescrChanSilentOff));
            notifyButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            notifyButton.setScaleType(ImageView.ScaleType.CENTER);
            if (Build.VERSION.SDK_INT >= 21) {
                notifyButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
            }
            notifyButton.setVisibility(canWriteToChannel && (delegate == null || !delegate.hasScheduledMessages()) ? VISIBLE : GONE);
            attachLayout.addView(notifyButton, LayoutHelper.createLinear(48, 48));
            notifyButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    silent = !silent;
                    if (notifySilentDrawable == null) {
                        notifySilentDrawable = new CrossOutDrawable(context, R.drawable.input_notify_on, Theme.key_chat_messagePanelIcons);
                    }
                    notifySilentDrawable.setCrossOut(silent, true);
                    notifyButton.setImageDrawable(notifySilentDrawable);
                    MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("silent_" + dialog_id, silent).commit();
                    NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(dialog_id, fragment == null ? 0 : fragment.getTopicId());
                    UndoView undoView = fragment.getUndoView();
                    if (undoView != null) {
                        undoView.showWithAction(0, !silent ? UndoView.ACTION_NOTIFY_ON : UndoView.ACTION_NOTIFY_OFF, null);
                    }
                    notifyButton.setContentDescription(silent ? getString("AccDescrChanSilentOn", R.string.AccDescrChanSilentOn) : getString("AccDescrChanSilentOff", R.string.AccDescrChanSilentOff));
                    updateFieldHint(true);
                }
            });

            attachButton = new ImageView(context);
            attachButton.setScaleType(ImageView.ScaleType.CENTER);
            attachButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            attachButton.setImageResource(R.drawable.msg_input_attach2);
            if (Build.VERSION.SDK_INT >= 21) {
                attachButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
            }
            attachLayout.addView(attachButton, LayoutHelper.createLinear(48, 48));
            attachButton.setOnClickListener(v -> {
                if (adjustPanLayoutHelper != null && adjustPanLayoutHelper.animationInProgress() || attachLayoutPaddingAlpha == 0f) {
                    return;
                }
                delegate.didPressAttachButton();
            });
            attachButton.setContentDescription(getString("AccDescrAttachButton", R.string.AccDescrAttachButton));
        }

        if (audioToSend != null) {
            createRecordAudioPanel();
        }

        sendButtonContainer = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == sendButton && textTransitionIsRunning) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (!sendButtonEnabled) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (!sendButtonEnabled) {
                    return false;
                }
                return super.onTouchEvent(event);
            }
        };
        sendButtonContainer.setClipChildren(false);
        sendButtonContainer.setClipToPadding(false);
        textFieldContainer.addView(sendButtonContainer, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.RIGHT));

        audioVideoButtonContainer = new FrameLayout(context) {

            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }

            @Override
            public boolean onTouchEvent(MotionEvent motionEvent) {
                createRecordCircle();
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (recordCircle.isSendButtonVisible()) {
                        if (!hasRecordVideo || calledRecordRunnable) {
                            startedDraggingX = -1;
                            if (hasRecordVideo && isInVideoMode()) {
                                delegate.needStartRecordVideo(1, true, 0, voiceOnce ? 0x7FFFFFFF : 0, effectId);
                                sendButton.setEffect(effectId = 0);
                            } else {
                                if (recordingAudioVideo && isInScheduleMode()) {
                                    AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), (notify, scheduleDate) -> MediaController.getInstance().stopRecording(1, notify, scheduleDate, false), () -> MediaController.getInstance().stopRecording(0, false, 0, false), resourcesProvider);
                                }
                                MediaController.getInstance().stopRecording(isInScheduleMode() ? 3 : 1, true, 0, voiceOnce);
                                delegate.needStartRecordAudio(0);
                            }
                            recordingAudioVideo = false;
                            messageTransitionIsRunning = false;
                            AndroidUtilities.runOnUIThread(moveToSendStateRunnable = () -> {
                                moveToSendStateRunnable = null;
                                updateRecordInterface(RECORD_STATE_SENDING, true);
                            }, 200);
                        }
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }

                    TLRPC.Chat chat = parentFragment == null ? null : parentFragment.getCurrentChat();
                    TLRPC.UserFull userFull = parentFragment == null ? userInfo : parentFragment.getCurrentUserInfo();
                    if (chat != null && !(ChatObject.canSendVoice(chat) || (ChatObject.canSendRoundVideo(chat) && hasRecordVideo)) || userFull != null && userFull.voice_messages_forbidden) {
                        delegate.needShowMediaBanHint();
                        return true;
                    }

                    if (hasRecordVideo) {
                        calledRecordRunnable = false;
                        recordAudioVideoRunnableStarted = true;
                        AndroidUtilities.runOnUIThread(recordAudioVideoRunnable, 150);
                    } else {
                        recordAudioVideoRunnable.run();
                    }
                    return true;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (motionEvent.getAction() == MotionEvent.ACTION_CANCEL && recordingAudioVideo) {
                        if (slideToCancelProgress < 0.7f) {
                            if (hasRecordVideo && isInVideoMode()) {
                                CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
                                delegate.needStartRecordVideo(2, true, 0, voiceOnce ? 0x7FFFFFFF : 0, effectId);
                                sendButton.setEffect(effectId = 0);
                            } else {
                                delegate.needStartRecordAudio(0);
                                MediaController.getInstance().stopRecording(0, false, 0, voiceOnce);
                            }
                            millisecondsRecorded = 0;
                            recordingAudioVideo = false;
                            updateRecordInterface(RECORD_STATE_CANCEL_BY_GESTURE, true);
                        } else {
                            sendButtonVisible = true;
                            startLockTransition();
                        }
                        return false;
                    }
                    if (recordCircle != null && recordCircle.isSendButtonVisible() || recordedAudioPanel != null && recordedAudioPanel.getVisibility() == VISIBLE) {
                        if (recordAudioVideoRunnableStarted) {
                            AndroidUtilities.cancelRunOnUIThread(recordAudioVideoRunnable);
                        }
                        return false;
                    }

                    float x = motionEvent.getX() + audioVideoButtonContainer.getX();
                    float dist = (x - startedDraggingX);
                    float alpha = 1.0f + dist / distCanMove;
                    if (alpha < 0.45) {
                        if (hasRecordVideo && isInVideoMode()) {
                            CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
                            delegate.needStartRecordVideo(2, true, 0, voiceOnce ? 0x7FFFFFFF : 0, effectId);
                            sendButton.setEffect(effectId = 0);
                        } else {
                            delegate.needStartRecordAudio(0);
                            MediaController.getInstance().stopRecording(0, false, 0, voiceOnce);
                        }
                        millisecondsRecorded = 0;
                        recordingAudioVideo = false;
                        updateRecordInterface(RECORD_STATE_CANCEL_BY_GESTURE, true);
                    } else {
                        if (recordAudioVideoRunnableStarted) {
                            AndroidUtilities.cancelRunOnUIThread(recordAudioVideoRunnable);
                            if (sendVoiceEnabled && sendRoundEnabled) {
                                delegate.onSwitchRecordMode(!isInVideoMode());
                                setRecordVideoButtonVisible(!isInVideoMode(), true);
                            } else {
                                delegate.needShowMediaBanHint();
                            }
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                        } else if (!hasRecordVideo || calledRecordRunnable) {
                            startedDraggingX = -1;
                            if (hasRecordVideo && isInVideoMode()) {
                                CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
                                delegate.needStartRecordVideo(1, true, 0, voiceOnce ? 0x7FFFFFFF : 0, effectId);
                                sendButton.setEffect(effectId = 0);
                            } else if (!sendVoiceEnabled) {
                                delegate.needShowMediaBanHint();
                            } else {
                                if (recordingAudioVideo && isInScheduleMode()) {
                                    AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), (notify, scheduleDate) -> MediaController.getInstance().stopRecording(1, notify, scheduleDate, false), () -> MediaController.getInstance().stopRecording(0, false, 0, false), resourcesProvider);
                                }
                                delegate.needStartRecordAudio(0);
                                MediaController.getInstance().stopRecording(isInScheduleMode() ? 3 : 1, true, 0, voiceOnce);
                            }
                            recordingAudioVideo = false;
                            messageTransitionIsRunning = false;
                            AndroidUtilities.runOnUIThread(moveToSendStateRunnable = () -> {
                                moveToSendStateRunnable = null;
                                updateRecordInterface(RECORD_STATE_SENDING, true);
                            }, shouldDrawBackground ? 500 : 0);
                        }
                    }
                    return true;
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && recordingAudioVideo) {
                    float x = motionEvent.getX();
                    float y = motionEvent.getY();
                    if (recordCircle.isSendButtonVisible()) {
                        return false;
                    }
                    if (recordCircle.setLockTranslation(y) == 2) {
                        startLockTransition();
                        return false;
                    } else {
                        recordCircle.setMovingCords(x, y);
                    }

                    if (startedDraggingX == -1) {
                        startedDraggingX = x;
                        distCanMove = (float) (sizeNotifierLayout.getMeasuredWidth() * 0.35);
                        if (distCanMove > dp(140)) {
                            distCanMove = dp(140);
                        }
                    }

                    x = x + audioVideoButtonContainer.getX();
                    float dist = (x - startedDraggingX);
                    float alpha = 1.0f + dist / distCanMove;
                    if (startedDraggingX != -1) {
                        if (alpha > 1) {
                            alpha = 1;
                        } else if (alpha < 0) {
                            alpha = 0;
                        }
                        if (slideText != null) {
                            slideText.setSlideX(alpha);
                        }
                        setSlideToCancelProgress(alpha);
                    }

                    if (alpha == 0) {
                        if (hasRecordVideo && isInVideoMode()) {
                            CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
                            delegate.needStartRecordVideo(2, true, 0, voiceOnce ? 0x7FFFFFFF : 0, effectId);
                            sendButton.setEffect(effectId = 0);
                        } else {
                            delegate.needStartRecordAudio(0);
                            MediaController.getInstance().stopRecording(0, false, 0, voiceOnce);
                        }
                        recordingAudioVideo = false;
                        updateRecordInterface(RECORD_STATE_CANCEL_BY_GESTURE, true);
                    }
                    return true;
                }
                return true;
            }
        };
        audioVideoButtonContainer.setSoundEffectsEnabled(false);
        sendButtonContainer.addView(audioVideoButtonContainer, LayoutHelper.createFrame(48, 48));
        audioVideoButtonContainer.setFocusable(true);
        audioVideoButtonContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

//        audioVideoButtonContainer.setOnTouchListener((view, motionEvent) -> {
//            createRecordCircle();
//            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
//                if (recordCircle.isSendButtonVisible()) {
//                    if (!hasRecordVideo || calledRecordRunnable) {
//                        startedDraggingX = -1;
//                        if (hasRecordVideo && isInVideoMode()) {
//                            delegate.needStartRecordVideo(1, true, 0);
//                        } else {
//                            if (recordingAudioVideo && isInScheduleMode()) {
//                                AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), (notify, scheduleDate) -> MediaController.getInstance().stopRecording(1, notify, scheduleDate), () -> MediaController.getInstance().stopRecording(0, false, 0), resourcesProvider);
//                            }
//                            MediaController.getInstance().stopRecording(isInScheduleMode() ? 3 : 1, true, 0);
//                            delegate.needStartRecordAudio(0);
//                        }
//                        recordingAudioVideo = false;
//                        messageTransitionIsRunning = false;
//                        AndroidUtilities.runOnUIThread(moveToSendStateRunnable = () -> {
//                            moveToSendStateRunnable = null;
//                            updateRecordInterface(RECORD_STATE_SENDING);
//                        }, 200);
//                    }
//                    getParent().requestDisallowInterceptTouchEvent(true);
//                    return true;
//                }
//                if (parentFragment != null) {
//                    TLRPC.Chat chat = parentFragment.getCurrentChat();
//                    TLRPC.UserFull userFull = parentFragment.getCurrentUserInfo();
//                    if (chat != null && !(ChatObject.canSendVoice(chat) || (ChatObject.canSendRoundVideo(chat) && hasRecordVideo)) || userFull != null && userFull.voice_messages_forbidden) {
//                        delegate.needShowMediaBanHint();
//                        return true;
//                    }
//                }
//                if (hasRecordVideo) {
//                    calledRecordRunnable = false;
//                    recordAudioVideoRunnableStarted = true;
//                    AndroidUtilities.runOnUIThread(recordAudioVideoRunnable, 150);
//                } else {
//                    recordAudioVideoRunnable.run();
//                }
//                return true;
//            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
//                if (motionEvent.getAction() == MotionEvent.ACTION_CANCEL && recordingAudioVideo) {
//                    if (recordCircle.slideToCancelProgress < 0.7f) {
//                        if (hasRecordVideo && isInVideoMode()) {
//                            CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
//                            delegate.needStartRecordVideo(2, true, 0);
//                        } else {
//                            delegate.needStartRecordAudio(0);
//                            MediaController.getInstance().stopRecording(0, false, 0);
//                        }
//                        recordingAudioVideo = false;
//                        updateRecordInterface(RECORD_STATE_CANCEL_BY_GESTURE);
//                    } else {
//                        recordCircle.sendButtonVisible = true;
//                        startLockTransition();
//                    }
//                    return false;
//                }
//                if (recordCircle != null && recordCircle.isSendButtonVisible() || recordedAudioPanel != null && recordedAudioPanel.getVisibility() == VISIBLE) {
//                    if (recordAudioVideoRunnableStarted) {
//                        AndroidUtilities.cancelRunOnUIThread(recordAudioVideoRunnable);
//                    }
//                    return false;
//                }
//
//                float x = motionEvent.getX() + audioVideoButtonContainer.getX();
//                float dist = (x - startedDraggingX);
//                float alpha = 1.0f + dist / distCanMove;
//                if (alpha < 0.45) {
//                    if (hasRecordVideo && isInVideoMode()) {
//                        CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
//                        delegate.needStartRecordVideo(2, true, 0);
//                    } else {
//                        delegate.needStartRecordAudio(0);
//                        MediaController.getInstance().stopRecording(0, false, 0);
//                    }
//                    recordingAudioVideo = false;
//                    updateRecordInterface(RECORD_STATE_CANCEL_BY_GESTURE);
//                } else {
//                    if (recordAudioVideoRunnableStarted) {
//                        AndroidUtilities.cancelRunOnUIThread(recordAudioVideoRunnable);
//                        if (sendVoiceEnabled && sendRoundEnabled) {
//                            delegate.onSwitchRecordMode(!isInVideoMode());
//                            setRecordVideoButtonVisible(!isInVideoMode(), true);
//                        } else {
//                            delegate.needShowMediaBanHint();
//                        }
//                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
//                        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
//                    } else if (!hasRecordVideo || calledRecordRunnable) {
//                        startedDraggingX = -1;
//                        if (hasRecordVideo && isInVideoMode()) {
//                            CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
//                            delegate.needStartRecordVideo(1, true, 0);
//                        } else if (!sendVoiceEnabled) {
//                            delegate.needShowMediaBanHint();
//                        } else {
//                            if (recordingAudioVideo && isInScheduleMode()) {
//                                AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), (notify, scheduleDate) -> MediaController.getInstance().stopRecording(1, notify, scheduleDate), () -> MediaController.getInstance().stopRecording(0, false, 0), resourcesProvider);
//                            }
//                            delegate.needStartRecordAudio(0);
//                            MediaController.getInstance().stopRecording(isInScheduleMode() ? 3 : 1, true, 0);
//                        }
//                        recordingAudioVideo = false;
//                        messageTransitionIsRunning = false;
//                        AndroidUtilities.runOnUIThread(moveToSendStateRunnable = () -> {
//                            moveToSendStateRunnable = null;
//                            updateRecordInterface(RECORD_STATE_SENDING);
//                        }, 500);
//                    }
//                }
//                return true;
//            } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && recordingAudioVideo) {
//                float x = motionEvent.getX();
//                float y = motionEvent.getY();
//                if (recordCircle.isSendButtonVisible()) {
//                    return false;
//                }
//                if (recordCircle.setLockTranslation(y) == 2) {
//                    startLockTransition();
//                    return false;
//                } else {
//                    recordCircle.setMovingCords(x, y);
//                }
//
//                if (startedDraggingX == -1) {
//                    startedDraggingX = x;
//                    distCanMove = (float) (sizeNotifierLayout.getMeasuredWidth() * 0.35);
//                    if (distCanMove > AndroidUtilities.dp(140)) {
//                        distCanMove = AndroidUtilities.dp(140);
//                    }
//                }
//
//                x = x + audioVideoButtonContainer.getX();
//                float dist = (x - startedDraggingX);
//                float alpha = 1.0f + dist / distCanMove;
//                if (startedDraggingX != -1) {
//                    if (alpha > 1) {
//                        alpha = 1;
//                    } else if (alpha < 0) {
//                        alpha = 0;
//                    }
//                    if (slideText != null) {
//                        slideText.setSlideX(alpha);
//                    }
//                    if (recordCircle != null) {
//                        recordCircle.setSlideToCancelProgress(alpha);
//                    }
//                }
//
//                if (alpha == 0) {
//                    if (hasRecordVideo && isInVideoMode()) {
//                        CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
//                        delegate.needStartRecordVideo(2, true, 0);
//                    } else {
//                        delegate.needStartRecordAudio(0);
//                        MediaController.getInstance().stopRecording(0, false, 0);
//                    }
//                    recordingAudioVideo = false;
//                    updateRecordInterface(RECORD_STATE_CANCEL_BY_GESTURE);
//                }
//                return true;
//            }
//            view.onTouchEvent(motionEvent);
//            return true;
//        });

        audioVideoSendButton = new ChatActivityEnterViewAnimatedIconView(context);
        audioVideoSendButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
//        audioVideoSendButton.setFocusable(true);
//        audioVideoSendButton.setAccessibilityDelegate(mediaMessageButtonsDelegate);
        padding = dp(9.5f);
        audioVideoSendButton.setPadding(padding, padding, padding, padding);
        audioVideoSendButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.SRC_IN));
        audioVideoButtonContainer.addView(audioVideoSendButton, LayoutHelper.createFrame(48, 48));

        cancelBotButton = new ImageView(context);
        cancelBotButton.setVisibility(INVISIBLE);
        cancelBotButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cancelBotButton.setImageDrawable(progressDrawable = new CloseProgressDrawable2() {
            @Override
            protected int getCurrentColor() {
                return Theme.getColor(Theme.key_chat_messagePanelCancelInlineBot);
            }
        });
        cancelBotButton.setContentDescription(getString("Cancel", R.string.Cancel));
        cancelBotButton.setSoundEffectsEnabled(false);
        cancelBotButton.setScaleX(0.1f);
        cancelBotButton.setScaleY(0.1f);
        cancelBotButton.setAlpha(0.0f);
        if (Build.VERSION.SDK_INT >= 21) {
            cancelBotButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }
        sendButtonContainer.addView(cancelBotButton, LayoutHelper.createFrame(48, 48));
        cancelBotButton.setOnClickListener(view -> {
            String text = messageEditText != null ? messageEditText.getText().toString() : "";
            int idx = text.indexOf(' ');
            if (idx == -1 || idx == text.length() - 1) {
                setFieldText("");
            } else {
                setFieldText(text.substring(0, idx + 1));
            }
        });

        sendButton = new SendButton(context, isInScheduleMode() ? R.drawable.input_schedule : R.drawable.ic_send, resourcesProvider) {
            @Override
            public boolean isInScheduleMode() {
                return ChatActivityEnterView.this.isInScheduleMode();
            }
            @Override
            public boolean isOpen() {
                return messageSendPreview != null && messageSendPreview.isShowing();
            }

            @Override
            public boolean isInactive() {
                return !isInScheduleMode() && slowModeTimer == Integer.MAX_VALUE;
            }

            @Override
            public boolean shouldDrawBackground() {
                return shouldDrawBackground;
            }
        };
        sendButton.setVisibility(INVISIBLE);
        int color = getThemedColor(Theme.key_chat_messagePanelSend);
        sendButton.setContentDescription(getString("Send", R.string.Send));
        sendButton.setSoundEffectsEnabled(false);
        sendButton.setScaleX(0.1f);
        sendButton.setScaleY(0.1f);
        sendButton.setAlpha(0.0f);
        if (Build.VERSION.SDK_INT >= 21) {
            sendButton.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(color, 24), 1));
        }
        sendButtonContainer.addView(sendButton, LayoutHelper.createFrame(48, 48));
        sendButton.setOnClickListener(view -> {
            if ((messageSendPreview != null && messageSendPreview.isShowing()) || (runningAnimationAudio != null && runningAnimationAudio.isRunning()) || moveToSendStateRunnable != null) {
                return;
            }
            sendMessage();
        });
        sendButton.setOnLongClickListener(this::onSendLongClick);

        slowModeButton = new SlowModeBtn(context);
        slowModeButton.setTextSize(18);
        slowModeButton.setVisibility(INVISIBLE);
        slowModeButton.setSoundEffectsEnabled(false);
        slowModeButton.setScaleX(0.1f);
        slowModeButton.setScaleY(0.1f);
        slowModeButton.setAlpha(0.0f);
        slowModeButton.setPadding(0, 0, dp(10), 0);
        slowModeButton.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        slowModeButton.setTextColor(getThemedColor(Theme.key_chat_messagePanelIcons));
        sendButtonContainer.addView(slowModeButton, LayoutHelper.createFrame(74, 48, Gravity.RIGHT | Gravity.TOP));
        slowModeButton.setOnClickListener(v -> {
            if (delegate != null) {
                if (delegate.checkCanRemoveRestrictionsByBoosts()) {
                    return;
                }
                delegate.onUpdateSlowModeButton(slowModeButton, true, slowModeButton.getText());
            }
        });
        slowModeButton.setOnLongClickListener(v -> {
            if (messageEditText == null || messageEditText.length() <= 0) {
                return false;
            }
            return onSendLongClick(v);
        });

        SharedPreferences sharedPreferences = MessagesController.getGlobalEmojiSettings();
        keyboardHeight = sharedPreferences.getInt("kbd_height", dp(200));
        keyboardHeightLand = sharedPreferences.getInt("kbd_height_land3", dp(200));

        setRecordVideoButtonVisible(false, false);
        checkSendButton(false);
        checkChannelRights();

        createMessageEditText();
    }

    private void createCaptionLimitView() {
        if (captionLimitView != null) {
            return;
        }

        captionLimitView = new NumberTextView(getContext());
        captionLimitView.setVisibility(View.GONE);
        captionLimitView.setTextSize(15);
        captionLimitView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        captionLimitView.setTypeface(AndroidUtilities.bold());
        captionLimitView.setCenterAlign(true);
        addView(captionLimitView, 3, LayoutHelper.createFrame(48, 20, Gravity.BOTTOM | Gravity.RIGHT, 3, 0, 0, 48));
    }

    private void createScheduledButton() {
        if (scheduledButton != null || parentFragment == null) {
            return;
        }

        Drawable drawable1 = getContext().getResources().getDrawable(R.drawable.input_calendar1).mutate();
        Drawable drawable2 = getContext().getResources().getDrawable(R.drawable.input_calendar2).mutate();
        drawable1.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        drawable2.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_recordedVoiceDot), PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);

        scheduledButton = new ImageView(getContext());
        scheduledButton.setImageDrawable(combinedDrawable);
        scheduledButton.setVisibility(GONE);
        scheduledButton.setContentDescription(getString("ScheduledMessages", R.string.ScheduledMessages));
        scheduledButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            scheduledButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }
        messageEditTextContainer.addView(scheduledButton, 2, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.RIGHT));
        scheduledButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.openScheduledMessages();
            }
        });
    }

    private void createGiftButton() {
        if (giftButton != null || parentFragment == null) {
            return;
        }

        giftButton = new ImageView(getContext()) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                post(ChatActivityEnterView.this::checkBirthdayHint);
            }
        };
        giftButton.setImageResource(R.drawable.msg_input_gift);
        giftButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        giftButton.setVisibility(GONE);
        giftButton.setContentDescription(getString(R.string.GiftPremium));
        giftButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            giftButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }
        attachLayout.addView(giftButton, 0, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        giftButton.setOnClickListener(v -> {
            SharedPreferences.Editor edit = MessagesController.getInstance(currentAccount).getMainSettings().edit();
            if (BirthdayController.isToday(parentFragment.getCurrentUserInfo())) {
                edit.putBoolean(Calendar.getInstance().get(Calendar.YEAR) + "show_gift_for_" + parentFragment.getDialogId(), false);
            } else {
                edit.putBoolean("show_gift_for_" + parentFragment.getDialogId(), false);
            }
            if (MessagesController.getInstance(currentAccount).giftAttachMenuIcon &&
                MessagesController.getInstance(currentAccount).giftTextFieldIcon &&
                !getParentFragment().getCurrentUserInfo().premium_gifts.isEmpty()) {
                edit.putBoolean("show_gift_for_" + parentFragment.getDialogId(), false);
            }
            edit.apply();

            AndroidUtilities.updateViewVisibilityAnimated(giftButton, false);

            TLRPC.User user = getParentFragment().getCurrentUser();
            if (user == null) return;
            final boolean birthday = getParentFragment().getCurrentUserInfo() != null && BirthdayController.isToday(getParentFragment().getCurrentUserInfo().birthday);
            ArrayList<TLRPC.TL_premiumGiftOption> options = new ArrayList<>(getParentFragment().getCurrentUserInfo().premium_gifts);
            if (options.isEmpty()) {
                final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                progressDialog.showDelayed(200);
                final int reqId = BoostRepository.loadGiftOptions(currentAccount, null, loadedOptions -> {
                    progressDialog.dismiss();
                    loadedOptions = BoostRepository.filterGiftOptions(loadedOptions, 1);
                    loadedOptions = BoostRepository.filterGiftOptionsByBilling(loadedOptions);
                    new GiftSheet(getContext(), currentAccount, user.id, loadedOptions, null).setBirthday(birthday).show();
                });
                progressDialog.setOnCancelListener(di -> {
                    parentFragment.getConnectionsManager().cancelRequest(reqId, true);
                });
            } else {
                new GiftSheet(getContext(), currentAccount, user.id, null, null).setBirthday(birthday).show();
            }
        });
    }

    private void createBotButton() {
        if (botButton != null) {
            return;
        }
        botButton = new ImageView(getContext());
        botButton.setImageDrawable(botButtonDrawable = new ReplaceableIconDrawable(getContext()));
        botButtonDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        botButtonDrawable.setIcon(R.drawable.input_bot2, false);
        botButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            botButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }
        botButton.setVisibility(GONE);
        AndroidUtilities.updateViewVisibilityAnimated(botButton, false, 0.1f, false);
        attachLayout.addView(botButton, 0, LayoutHelper.createLinear(48, 48));
        botButton.setOnClickListener(v -> {
//            if (hasBotWebView() && botCommandsMenuIsShowing()) {
//                botWebViewMenuContainer.dismiss(v::callOnClick);
//                return;
//            }
            if (searchingType != 0) {
                setSearchingTypeInternal(0, false);
                emojiView.closeSearch(false);
                if (messageEditText != null) {
                    messageEditText.requestFocus();
                }
            }
            if (botReplyMarkup != null) {
                if (!isPopupShowing() || currentPopupContentType != POPUP_CONTENT_BOT_KEYBOARD) {
                    showPopup(1, POPUP_CONTENT_BOT_KEYBOARD);
                } else if (isPopupShowing() && currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD) {
                    showPopup(0, POPUP_CONTENT_BOT_KEYBOARD);
                }
            } else if (hasBotCommands || hasQuickReplies) {
                setFieldText("/");
                if (messageEditText != null) {
                    messageEditText.requestFocus();
                }
                openKeyboard();
            }
            if (stickersExpanded) {
                setStickersExpanded(false, false, false);
            }
        });
    }

    private void createDoneButton(boolean bounceable) {
        if (doneButton != null) {
            return;
        }

        doneButton = new SendButton(getContext(), R.drawable.input_done, resourcesProvider) {
            @Override
            public boolean shouldDrawBackground() {
                return true;
            }

            @Override
            public boolean isOpen() {
                return true;
            }

            @Override
            public boolean isInactive() {
                return !doneButtonEnabled;
            }
        };
        doneButton.center = true;
        if (bounceable) {
            ScaleStateListAnimator.apply(doneButton);
        }
        textFieldContainer.addView(doneButton, LayoutHelper.createFrame(38, 38, Gravity.BOTTOM | Gravity.RIGHT, 5, 5, 5, 5));
//
//        Drawable doneCircleDrawable = Theme.createCircleDrawable(dp(16), getThemedColor(Theme.key_chat_messagePanelSend));
//        doneCheckDrawable = getContext().getResources().getDrawable(R.drawable.input_done).mutate();
//        doneCheckDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));
//        CombinedDrawable combinedDrawable = new CombinedDrawable(doneCircleDrawable, doneCheckDrawable, 0, dp(1));
//        combinedDrawable.setCustomSize(dp(32), dp(32));
//
//        if (bounceable) {
//            doneButtonImage = new CaptionContainerView.BounceableImageView(getContext());
//        } else {
//            doneButtonImage = new ImageView(getContext());
//        }
//        doneButtonImage.setScaleType(ImageView.ScaleType.CENTER);
//        doneButtonImage.setImageDrawable(combinedDrawable);
//        doneButtonImage.setContentDescription(getString("Done", R.string.Done));
//        doneButtonContainer.addView(doneButtonImage, LayoutHelper.createFrame(48, 48));
//
//        doneButtonProgress = new ContextProgressView(getContext(), 0);
//        doneButtonProgress.setVisibility(View.INVISIBLE);
//        doneButtonContainer.addView(doneButtonProgress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void createReactionsButton(View.OnClickListener onClickListener) {
        if (reactionsButton != null) {
            return;
        }
        reactionsButton = new ImageView(getContext());
        reactionsButton.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.msg_input_like));
        reactionsButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            reactionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }
        //reactionsButton.setVisibility(GONE);
        AndroidUtilities.updateViewVisibilityAnimated(reactionsButton, true, 0.1f, false);
        attachLayout.addView(reactionsButton, 0, LayoutHelper.createLinear(48, 48));
        reactionsButton.setOnClickListener(onClickListener);
    }

    private void createExpandStickersButton() {
        if (expandStickersButton != null) {
            return;
        }
        expandStickersButton = new ImageView(getContext()) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (getAlpha() <= 0f) { // for accessibility
                    return false;
                }
                return super.onTouchEvent(event);
            }
        };
        expandStickersButton.setScaleType(ImageView.ScaleType.CENTER);
        expandStickersButton.setImageDrawable(stickersArrow = new AnimatedArrowDrawable(getThemedColor(Theme.key_chat_messagePanelIcons), false));
        expandStickersButton.setVisibility(GONE);
        expandStickersButton.setScaleX(0.1f);
        expandStickersButton.setScaleY(0.1f);
        expandStickersButton.setAlpha(0.0f);
        if (Build.VERSION.SDK_INT >= 21) {
            expandStickersButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }
        sendButtonContainer.addView(expandStickersButton, LayoutHelper.createFrame(48, 48));
        expandStickersButton.setOnClickListener(v -> {
            if (expandStickersButton.getVisibility() != VISIBLE || expandStickersButton.getAlpha() != 1.0f || waitingForKeyboardOpen || (keyboardVisible && messageEditText != null && messageEditText.isFocused())) {
                return;
            }
            if (stickersExpanded) {
                if (searchingType != 0) {
                    setSearchingTypeInternal(0, true);
                    emojiView.closeSearch(true);
                    emojiView.hideSearchKeyboard();
                    if (emojiTabOpen) {
                        checkSendButton(true);
                    }
                } else if (!stickersDragging) {
                    if (emojiView != null) {
                        emojiView.showSearchField(false);
                    }
                }
            } else if (!stickersDragging) {
                emojiView.showSearchField(true);
            }
            if (!stickersDragging) {
                setStickersExpanded(!stickersExpanded, true, false);
            }
        });
        expandStickersButton.setContentDescription(getString("AccDescrExpandPanel", R.string.AccDescrExpandPanel));
    }

    private void createRecordAudioPanel() {
        if (recordedAudioPanel != null) {
            return;
        }

        recordedAudioPanel = new FrameLayout(getContext()) {
            @Override
            public void setVisibility(int visibility) {
                super.setVisibility(visibility);
                updateSendAsButton();
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                return super.dispatchTouchEvent(ev);
            }
        };
        recordedAudioPanel.setVisibility(audioToSend == null ? GONE : VISIBLE);
        recordedAudioPanel.setFocusable(true);
        recordedAudioPanel.setFocusableInTouchMode(true);
        recordedAudioPanel.setClickable(true);
        messageEditTextContainer.addView(recordedAudioPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));

        recordDeleteImageView = new RLottieImageView(getContext());
        recordDeleteImageView.setScaleType(ImageView.ScaleType.CENTER);
        recordDeleteImageView.setAnimation(R.raw.chat_audio_record_delete_2, 28, 28);
        recordDeleteImageView.getAnimatedDrawable().setInvalidateOnProgressSet(true);
        updateRecordedDeleteIconColors();
        recordDeleteImageView.setContentDescription(getString("Delete", R.string.Delete));
        if (Build.VERSION.SDK_INT >= 21) {
            recordDeleteImageView.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }
        recordedAudioPanel.addView(recordDeleteImageView, LayoutHelper.createFrame(48, 48));
        recordDeleteImageView.setOnClickListener(v -> {
            if (runningAnimationAudio != null && runningAnimationAudio.isRunning()) {
                return;
            }
            resetRecordedState();
        });

        videoTimelineView = new VideoTimelineView(getContext());
        videoTimelineView.setVisibility(INVISIBLE);
        videoTimelineView.useClip = !shouldDrawBackground;
        videoTimelineView.setRoundFrames(true);
        videoTimelineView.setDelegate(new VideoTimelineView.VideoTimelineViewDelegate() {
            @Override
            public void onLeftProgressChanged(float progress) {
                if (videoToSendMessageObject == null) {
                    return;
                }
                videoToSendMessageObject.startTime = (long) (progress * videoToSendMessageObject.estimatedDuration);
                delegate.needChangeVideoPreviewState(2, progress);
            }

            @Override
            public void onRightProgressChanged(float progress) {
                if (videoToSendMessageObject == null) {
                    return;
                }
                videoToSendMessageObject.endTime = (long) (progress * videoToSendMessageObject.estimatedDuration);
                delegate.needChangeVideoPreviewState(2, progress);
            }

            @Override
            public void didStartDragging() {
                delegate.needChangeVideoPreviewState(1, 0);
            }

            @Override
            public void didStopDragging() {
                delegate.needChangeVideoPreviewState(0, 0);
            }
        });
        recordedAudioPanel.addView(videoTimelineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 56, 0, 8, 0));

        VideoTimelineView.TimeHintView videoTimeHintView = new VideoTimelineView.TimeHintView(getContext());
        videoTimelineView.setTimeHintView(videoTimeHintView);
        sizeNotifierLayout.addView(videoTimeHintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, 52));

        recordedAudioBackground = new View(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                getBackground().setBounds((int) horizontalPadding, 0, (int) (getMeasuredWidth() - horizontalPadding), getMeasuredHeight());
                getBackground().draw(canvas);
            }
        };
        recordedAudioBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(dp(18), getThemedColor(Theme.key_chat_recordedVoiceBackground)));
        recordedAudioPanel.addView(recordedAudioBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL | Gravity.LEFT, 48, 0, 0, 0));

        LinearLayout waveFormTimerLayout = new LinearLayout(getContext());
        waveFormTimerLayout.setOrientation(LinearLayout.HORIZONTAL);
        recordedAudioPanel.addView(waveFormTimerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 48 + 44, 0, 13, 0));

        recordedAudioPlayButton = new ImageView(getContext());
        Matrix matrix = new Matrix();
        matrix.postScale(0.8f, 0.8f, dpf2(24), dpf2(24));
        recordedAudioPlayButton.setImageMatrix(matrix);
        recordedAudioPlayButton.setImageDrawable(playPauseDrawable = new MediaActionDrawable());
        recordedAudioPlayButton.setScaleType(ImageView.ScaleType.MATRIX);
        recordedAudioPlayButton.setContentDescription(getString("AccActionPlay", R.string.AccActionPlay));
        recordedAudioPanel.addView(recordedAudioPlayButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.BOTTOM, 48, 0, 13, 0));
        recordedAudioPlayButton.setOnClickListener(v -> {
            if (audioToSend == null) {
                return;
            }
            if (MediaController.getInstance().isPlayingMessage(audioToSendMessageObject) && !MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().pauseMessage(audioToSendMessageObject);
                playPauseDrawable.setIcon(MediaActionDrawable.ICON_PLAY, true);
                recordedAudioPlayButton.setContentDescription(getString("AccActionPlay", R.string.AccActionPlay));
            } else {
                playPauseDrawable.setIcon(MediaActionDrawable.ICON_PAUSE, true);
                MediaController.getInstance().playMessage(audioToSendMessageObject);
                recordedAudioPlayButton.setContentDescription(getString("AccActionPause", R.string.AccActionPause));
            }
        });

        recordedAudioSeekBar = new SeekBarWaveformView(getContext());
        recordedAudioSeekBar.setVisibility(View.INVISIBLE);
        waveFormTimerLayout.addView(recordedAudioSeekBar, LayoutHelper.createLinear(0, 32, 1f, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        recordedAudioTimeTextView = new TextView(getContext());
        recordedAudioTimeTextView.setTextColor(getThemedColor(Theme.key_chat_messagePanelVoiceDuration));
        recordedAudioTimeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        waveFormTimerLayout.addView(recordedAudioTimeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, Gravity.CENTER_VERTICAL));
    }

    private void resetRecordedState() {
        if (videoToSendMessageObject != null) {
            CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
            delegate.needStartRecordVideo(2, true, 0, voiceOnce ? 0x7FFFFFFF : 0, effectId);
            sendButton.setEffect(effectId = 0);
        } else {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null && playing == audioToSendMessageObject) {
                MediaController.getInstance().cleanupPlayer(true, true);
            }
        }
        if (audioToSendPath != null) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("delete file " + audioToSendPath);
            }
            new File(audioToSendPath).delete();
        }
        MediaController.getInstance().cleanRecording(true);
        MediaDataController.getInstance(currentAccount).pushDraftVoiceMessage(dialog_id, parentFragment != null && parentFragment.isTopic ? parentFragment.getTopicId() : 0, null);
        MediaController.getInstance().stopRecording(0, false, 0, false);
        millisecondsRecorded = 0;
        hideRecordedAudioPanel(false);
        checkSendButton(true);
    }

    private void createSenderSelectView() {
        if (senderSelectView != null) {
            return;
        }
        senderSelectView = new SenderSelectView(getContext());
        senderSelectView.setOnClickListener(v -> {
            if (getTranslationY() != 0) {
                onEmojiSearchClosed = () -> senderSelectView.callOnClick();
                hidePopup(true, true);
                return;
            }
            if (delegate.measureKeyboardHeight() > dp(20)) {
                int totalHeight = delegate.getContentViewHeight();
                int keyboard = delegate.measureKeyboardHeight();
                if (keyboard <= dp(20)) {
                    totalHeight += keyboard;
                }
                if (emojiViewVisible) {
                    totalHeight -= getEmojiPadding();
                }

                if (totalHeight < dp(200)) {
                    onKeyboardClosed = () -> senderSelectView.callOnClick();
                    closeKeyboard();
                    return;
                }
            }
            if (delegate.getSendAsPeers() != null) {
                try {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {
                }
                if (senderSelectPopupWindow != null) {
                    senderSelectPopupWindow.setPauseNotifications(false);
                    senderSelectPopupWindow.startDismissAnimation();
                    return;
                }
                MessagesController controller = MessagesController.getInstance(currentAccount);
                TLRPC.ChatFull chatFull = controller.getChatFull(-dialog_id);
                if (chatFull == null) {
                    return;
                }

                ViewGroup fl = parentFragment.getParentLayout().getOverlayContainerView();

                senderSelectPopupWindow = new SenderSelectPopup(getContext(), parentFragment, controller, chatFull, delegate.getSendAsPeers(), (recyclerView, senderView, peer) -> {
                    if (senderSelectPopupWindow == null) return;
                    if (chatFull != null) {
                        chatFull.default_send_as = peer;
                        updateSendAsButton();
                    }

                    parentFragment.getMessagesController().setDefaultSendAs(dialog_id, peer.user_id != 0 ? peer.user_id : -peer.channel_id);

                    int[] loc = new int[2];
                    boolean wasSelected = senderView.avatar.isSelected();
                    senderView.avatar.getLocationInWindow(loc);
                    senderView.avatar.setSelected(true, true);

                    SimpleAvatarView avatar = new SimpleAvatarView(getContext());
                    if (peer.channel_id != 0) {
                        TLRPC.Chat chat = controller.getChat(peer.channel_id);
                        if (chat != null) {
                            avatar.setAvatar(chat);
                        }
                    } else if (peer.user_id != 0) {
                        TLRPC.User user = controller.getUser(peer.user_id);
                        if (user != null) {
                            avatar.setAvatar(user);
                        }
                    }
                    for (int i = 0; i < recyclerView.getChildCount(); i++) {
                        View ch = recyclerView.getChildAt(i);
                        if (ch instanceof SenderSelectPopup.SenderView && ch != senderView) {
                            SenderSelectPopup.SenderView childSenderView = (SenderSelectPopup.SenderView) ch;
                            childSenderView.avatar.setSelected(false, true);
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        if (senderSelectPopupWindow == null) {
                            return;
                        }
                        Dialog d = new Dialog(getContext(), R.style.TransparentDialogNoAnimation);
                        FrameLayout aFrame = new FrameLayout(getContext());
                        aFrame.addView(avatar, LayoutHelper.createFrame(SenderSelectPopup.AVATAR_SIZE_DP, SenderSelectPopup.AVATAR_SIZE_DP, Gravity.LEFT));
                        d.setContentView(aFrame);
                        d.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                            d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                            d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                            d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                            d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                            d.getWindow().getAttributes().windowAnimations = 0;
                            d.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                            d.getWindow().setStatusBarColor(0);
                            d.getWindow().setNavigationBarColor(0);

                            int color = Theme.getColor(Theme.key_actionBarDefault, null, true);
                            AndroidUtilities.setLightStatusBar(d.getWindow(), color == Color.WHITE);

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                int color2 = Theme.getColor(Theme.key_windowBackgroundGray, null, true);
                                float brightness = AndroidUtilities.computePerceivedBrightness(color2);
                                AndroidUtilities.setLightNavigationBar(d.getWindow(), brightness >= 0.721f);
                            }
                        }
                        float offX = 0, offY = 0;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            WindowInsets wi = getRootWindowInsets();
                            popupX += wi.getSystemWindowInsetLeft();
                        }

                        senderSelectView.getLocationInWindow(location);
                        float endX = location[0], endY = location[1];

                        float off = dp(5);
                        float startX = loc[0] + popupX + off + dp(4) + offX, startY = loc[1] + popupY + off + offY;
                        avatar.setTranslationX(startX);
                        avatar.setTranslationY(startY);

                        float startScale = (float) (SenderSelectPopup.AVATAR_SIZE_DP - 10) / SenderSelectPopup.AVATAR_SIZE_DP, endScale = senderSelectView.getLayoutParams().width / (float) dp(SenderSelectPopup.AVATAR_SIZE_DP);
                        avatar.setPivotX(0);
                        avatar.setPivotY(0);
                        avatar.setScaleX(startScale);
                        avatar.setScaleY(startScale);

                        avatar.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
                            @Override
                            public void onDraw() {
                                avatar.post(() -> {
                                    avatar.getViewTreeObserver().removeOnDrawListener(this);
                                    senderView.avatar.setHideAvatar(true);
                                });
                            }
                        });
                        d.show();

                        senderSelectView.setScaleX(1f);
                        senderSelectView.setScaleY(1f);
                        senderSelectView.setAlpha(1f);

                        float translationStiffness = 700f;
                        senderSelectPopupWindow.startDismissAnimation(
                                new SpringAnimation(senderSelectView, DynamicAnimation.SCALE_X)
                                        .setSpring(new SpringForce(0.5f)
                                                .setStiffness(SenderSelectPopup.SPRING_STIFFNESS)
                                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)),
                                new SpringAnimation(senderSelectView, DynamicAnimation.SCALE_Y)
                                        .setSpring(new SpringForce(0.5f)
                                                .setStiffness(SenderSelectPopup.SPRING_STIFFNESS)
                                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)),
                                new SpringAnimation(senderSelectView, DynamicAnimation.ALPHA)
                                        .setSpring(new SpringForce(0f)
                                                .setStiffness(SenderSelectPopup.SPRING_STIFFNESS)
                                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
                                        .addEndListener((animation, canceled, value, velocity) -> {
                                            if (d.isShowing()) {
                                                avatar.setTranslationX(endX);
                                                avatar.setTranslationY(endY);

                                                senderSelectView.setProgress(0, false);
                                                senderSelectView.setScaleX(1);
                                                senderSelectView.setScaleY(1);
                                                senderSelectView.setAlpha(1);

                                                senderSelectView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                                    @Override
                                                    public boolean onPreDraw() {
                                                        senderSelectView.getViewTreeObserver().removeOnPreDrawListener(this);
                                                        senderSelectView.postDelayed(d::dismiss, 100);
                                                        return true;
                                                    }
                                                });
                                            }
                                        }),
                                new SpringAnimation(avatar, DynamicAnimation.TRANSLATION_X)
                                        .setStartValue(MathUtils.clamp(startX, endX - dp(6), startX))
                                        .setSpring(new SpringForce(endX)
                                                .setStiffness(translationStiffness)
                                                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY))
                                        .setMinValue(endX - dp(6)),
                                new SpringAnimation(avatar, DynamicAnimation.TRANSLATION_Y)
                                        .setStartValue(MathUtils.clamp(startY, startY, endY + dp(6)))
                                        .setSpring(new SpringForce(endY)
                                                .setStiffness(translationStiffness)
                                                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY))
                                        .setMaxValue(endY + dp(6))
                                        .addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
                                            boolean performedHapticFeedback = false;

                                            @Override
                                            public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                                                if (!performedHapticFeedback && value >= endY) {
                                                    performedHapticFeedback = true;
                                                    try {
                                                        avatar.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                                    } catch (Exception ignored) {
                                                    }
                                                }
                                            }
                                        })
                                        .addEndListener((animation, canceled, value, velocity) -> {
                                            if (d.isShowing()) {
                                                avatar.setTranslationX(endX);
                                                avatar.setTranslationY(endY);

                                                senderSelectView.setProgress(0, false);
                                                senderSelectView.setScaleX(1);
                                                senderSelectView.setScaleY(1);
                                                senderSelectView.setAlpha(1);

                                                senderSelectView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                                    @Override
                                                    public boolean onPreDraw() {
                                                        senderSelectView.getViewTreeObserver().removeOnPreDrawListener(this);
                                                        senderSelectView.postDelayed(d::dismiss, 100);
                                                        return true;
                                                    }
                                                });
                                            }
                                        }),
                                new SpringAnimation(avatar, DynamicAnimation.SCALE_X)
                                        .setSpring(new SpringForce(endScale)
                                                .setStiffness(1000f)
                                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)),
                                new SpringAnimation(avatar, DynamicAnimation.SCALE_Y)
                                        .setSpring(new SpringForce(endScale)
                                                .setStiffness(1000f)
                                                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)));
                    }, wasSelected ? 0 : SimpleAvatarView.SELECT_ANIMATION_DURATION);
                }) {
                    @Override
                    public void dismiss() {
                        if (senderSelectPopupWindow != this) {
                            fl.removeView(dimView);
                            super.dismiss();
                            return;
                        }

                        senderSelectPopupWindow = null;

                        if (!runningCustomSprings) {
                            startDismissAnimation();
                            senderSelectView.setProgress(0, true, true);
                        } else {
                            for (SpringAnimation springAnimation : springAnimations) {
                                springAnimation.cancel();
                            }
                            springAnimations.clear();
                            super.dismiss();
                        }
                    }
                };
                senderSelectPopupWindow.setPauseNotifications(true);
                senderSelectPopupWindow.setDismissAnimationDuration(220);
                senderSelectPopupWindow.setOutsideTouchable(true);
                senderSelectPopupWindow.setClippingEnabled(true);
                senderSelectPopupWindow.setFocusable(true);
                senderSelectPopupWindow.getContentView().measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
                senderSelectPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                senderSelectPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
                senderSelectPopupWindow.getContentView().setFocusableInTouchMode(true);
                senderSelectPopupWindow.setAnimationEnabled(false);

                int pad = -dp(4);
                int[] location = new int[2];
                int popupX = pad;
                if (AndroidUtilities.isTablet()) {
                    parentFragment.getFragmentView().getLocationInWindow(location);
                    popupX += location[0];
                }
                int totalHeight = delegate.getContentViewHeight();
                int height = senderSelectPopupWindow.getContentView().getMeasuredHeight();
                int keyboard = delegate.measureKeyboardHeight();
                if (keyboard <= dp(20)) {
                    totalHeight += keyboard;
                }
                if (emojiViewVisible) {
                    totalHeight -= getEmojiPadding();
                }

                int shadowPad = dp(1);
                int popupY;
                if (height < totalHeight + pad * 2 - (parentFragment.isInBubbleMode() ? 0 : AndroidUtilities.statusBarHeight) - senderSelectPopupWindow.headerText.getMeasuredHeight()) {
                    ChatActivityEnterView.this.getLocationInWindow(location);
                    popupY = location[1] - height - pad - dp(2);
                    fl.addView(senderSelectPopupWindow.dimView, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, popupY + pad + height + shadowPad + dp(2)));
                } else {
                    popupY = parentFragment.isInBubbleMode() ? 0 : AndroidUtilities.statusBarHeight;
                    int off = dp(14);
                    senderSelectPopupWindow.recyclerContainer.getLayoutParams().height = totalHeight - popupY - off - getHeightWithTopView();
                    fl.addView(senderSelectPopupWindow.dimView, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, off + popupY + senderSelectPopupWindow.recyclerContainer.getLayoutParams().height + shadowPad));
                }

                senderSelectPopupWindow.startShowAnimation();

                senderSelectPopupWindow.showAtLocation(v, Gravity.LEFT | Gravity.TOP, this.popupX = popupX, this.popupY = popupY);
                senderSelectView.setProgress(1);
            }
        });
        senderSelectView.setVisibility(GONE);
        messageEditTextContainer.addView(senderSelectView, LayoutHelper.createFrame(32, 32, Gravity.BOTTOM | Gravity.LEFT, 10, 8, 10, 8));
    }

    private void createBotCommandsMenuButton() {
        if (botCommandsMenuButton != null) {
            return;
        }
        botCommandsMenuButton = new BotCommandsMenuView(getContext());
        botCommandsMenuButton.setOnClickListener(view -> {
            boolean open = !botCommandsMenuButton.isOpened();
            botCommandsMenuButton.setOpened(open);
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {
            }
            if (hasBotWebView()) {
                if (open) {
                    if (emojiViewVisible || botKeyboardViewVisible) {
                        AndroidUtilities.runOnUIThread(this::openWebViewMenu, 275);
                        hidePopup(false);
                        return;
                    }

                    openWebViewMenu();
                }
//                else if (botWebViewMenuContainer != null) {
//                    botWebViewMenuContainer.dismiss();
//                }
                return;
            }

            if (open) {
                createBotCommandsMenuContainer();
                botCommandsMenuContainer.show();
            } else if (botCommandsMenuContainer != null) {
                botCommandsMenuContainer.dismiss();
            }
        });
        messageEditTextContainer.addView(botCommandsMenuButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.BOTTOM | Gravity.LEFT, 10, 8, 10, 8));
        AndroidUtilities.updateViewVisibilityAnimated(botCommandsMenuButton, false, 1f, false);
        botCommandsMenuButton.setExpanded(true, false);
    }

    private void createBotWebViewButton() {
        if (botWebViewButton != null) {
            return;
        }
        botWebViewButton = new ChatActivityBotWebViewButton(getContext());
        botWebViewButton.setVisibility(GONE);
        createBotCommandsMenuButton();
        botWebViewButton.setBotMenuButton(botCommandsMenuButton);
        messageEditTextContainer.addView(botWebViewButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM));
    }

    private void createRecordCircle() {
        createControlsView();
        if (recordCircle != null) {
            return;
        }
        recordCircle = new RecordCircle(getContext());
        recordCircle.setVisibility(GONE);
        sizeNotifierLayout.addView(recordCircle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
    }

    private void createControlsView() {
        if (controlsView != null) {
            return;
        }
        controlsView = new ControlsView(getContext());
        controlsView.setVisibility(GONE);
        sizeNotifierLayout.addView(controlsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
    }

    private void showRestrictedHint() {
        if (delegate != null && delegate.checkCanRemoveRestrictionsByBoosts()) {
            return;
        }
        if (DialogObject.isChatDialog(dialog_id)) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-dialog_id);
            BulletinFactory.of(parentFragment).createSimpleBulletin(R.raw.passcode_lock_close, LocaleController.formatString("SendPlainTextRestrictionHint", R.string.SendPlainTextRestrictionHint, ChatObject.getAllowedSendString(chat)), 3).show();
        }
    }

    private void openWebViewMenu() {
        createBotWebViewMenuContainer();
        Runnable onRequestWebView = () -> {
            AndroidUtilities.hideKeyboard(this);
            WebViewRequestProps props = WebViewRequestProps.of(currentAccount, dialog_id, dialog_id, botMenuWebViewTitle, botMenuWebViewUrl, BotWebViewAttachedSheet.TYPE_BOT_MENU_BUTTON, 0, false, null, false, null, null, 0, false, false);
            if (LaunchActivity.instance != null && LaunchActivity.instance.getBottomSheetTabs() != null && LaunchActivity.instance.getBottomSheetTabs().tryReopenTab(props) != null) {
                if (botCommandsMenuButton != null) {
                    botCommandsMenuButton.setOpened(false);
                }
                return;
            }
            if (AndroidUtilities.isWebAppLink(botMenuWebViewUrl)) {
                Browser.Progress progress = new Browser.Progress();
                progress.onEnd(() -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (botCommandsMenuButton != null) {
                            botCommandsMenuButton.setOpened(false);
                        }
                    });
                });
                Browser.openAsInternalIntent(getContext(), botMenuWebViewUrl, false, false, progress);
                return;
            }
//            if (AndroidUtilities.isTablet() || true) {
                BotWebViewSheet webViewSheet = new BotWebViewSheet(getContext(), resourcesProvider);
                webViewSheet.setDefaultFullsize(false);
                webViewSheet.setNeedsContext(true);
                webViewSheet.setParentActivity(parentActivity);
                webViewSheet.requestWebView(parentFragment, props);
                webViewSheet.show();

                if (botCommandsMenuButton != null) {
                    botCommandsMenuButton.setOpened(false);
                }
//            } else {
//                if (parentFragment != null && parentFragment.getParentActivity() != null) {
//                    BotWebViewAttachedSheet sheet = parentFragment.createBotViewer();
//                    sheet.setDefaultFullsize(false);
//                    sheet.setNeedsContext(false);
//                    sheet.setParentActivity(parentFragment.getParentActivity());
//                    sheet.requestWebView(parentFragment, props);
//                    sheet.show();
//
//                    if (botCommandsMenuButton != null) {
//                        botCommandsMenuButton.setOpened(false);
//                    }
//                }
//            }
        };

        if (SharedPrefsHelper.isWebViewConfirmShown(currentAccount, dialog_id)) {
            onRequestWebView.run();
        } else {
            AlertsCreator.createBotLaunchAlert(parentFragment, MessagesController.getInstance(currentAccount).getUser(dialog_id), () -> {
                onRequestWebView.run();
                SharedPrefsHelper.setWebViewConfirmShown(currentAccount, dialog_id, true);
            }, () -> {
                if (botCommandsMenuButton != null && !SharedPrefsHelper.isWebViewConfirmShown(currentAccount, dialog_id))  {
                    botCommandsMenuButton.setOpened(false);
                }
            });
        }
    }

    public void setBotWebViewButtonOffsetX(float offset) {
        emojiButton.setTranslationX(offset);
        if (messageEditText != null) {
            messageTextTranslationX = offset;
            updateMessageTextParams();
        }
        attachButton.setTranslationX(offset);
        audioVideoSendButton.setTranslationX(offset);
        if (botButton != null) {
            botButton.setTranslationX(offset);
        }
    }

    public void setComposeShadowAlpha(float alpha) {
        composeShadowAlpha = alpha;
        invalidate();
    }

    public ChatActivityBotWebViewButton getBotWebViewButton() {
        createBotWebViewButton();
        return botWebViewButton;
    }

    public ChatActivity getParentFragment() {
        return parentFragment;
    }

    private void checkBotMenu() {
        final boolean shouldBeExpanded = (messageEditText == null || TextUtils.isEmpty(messageEditText.getText())) && !(keyboardVisible || waitingForKeyboardOpen || isPopupShowing());
        if (shouldBeExpanded) {
            createBotCommandsMenuButton();
        }
        if (botCommandsMenuButton != null) {
            boolean wasExpanded = botCommandsMenuButton.expanded;
            botCommandsMenuButton.setExpanded(shouldBeExpanded, true);
            if (wasExpanded != botCommandsMenuButton.expanded) {
                beginDelayedTransition();
            }
        }
    }

    public void forceSmoothKeyboard(boolean smoothKeyboard) {
        this.smoothKeyboard = smoothKeyboard && !AndroidUtilities.isInMultiwindow && (parentFragment == null || !parentFragment.isInBubbleMode());
    }

    protected void onLineCountChanged(int oldLineCount, int newLineCount) {

    }

    private void startLockTransition() {
        AnimatorSet animatorSet = new AnimatorSet();
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

        ObjectAnimator translate = ObjectAnimator.ofFloat(this, "lockAnimatedTranslation", startTranslation);
        translate.setStartDelay(100);
        translate.setDuration(350);
        ObjectAnimator snap = ObjectAnimator.ofFloat(this, "snapAnimationProgress", 1f);
        snap.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        snap.setDuration(250);

        SharedConfig.removeLockRecordAudioVideoHint();

        animatorSet.playTogether(
                snap,
                translate,
                ObjectAnimator.ofFloat(this, "slideToCancelProgress", 1f).setDuration(200),
                ObjectAnimator.ofFloat(slideText, "cancelToProgress", 1f)
        );

        animatorSet.start();
    }

    public int getBackgroundTop() {
        int t = getTop();
        if (topView != null && topView.getVisibility() == View.VISIBLE) {
            t += topView.getLayoutParams().height;
        }
        return t;
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean clip = child == topView || child == textFieldContainer;
        if (clip) {
            canvas.save();
            if (child == textFieldContainer) {
                int top = (int) (animatedTop + dp(2) + chatSearchExpandOffset);
                if (topView != null && topView.getVisibility() == View.VISIBLE) {
                    top += topView.getHeight();
                }
                canvas.clipRect(0, top, getMeasuredWidth(), getMeasuredHeight());
            } else {
                canvas.clipRect(0, animatedTop, getMeasuredWidth(), animatedTop + child.getLayoutParams().height + dp(2));
            }
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (clip) {
            canvas.restore();
        }
        return result;
    }

    public boolean allowBlur = true;
    public boolean shouldDrawBackground = true;
    public boolean shouldDrawRecordedAudioPanelInParent;
    public boolean isStories;

    Paint backgroundPaint = new Paint();
    private float composeShadowAlpha = 1f;
    private Rect blurBounds = new Rect();
    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas, true);
    }

    public void drawBackground(Canvas canvas, boolean withComposeShadowDrawable) {
        if (!shouldDrawBackground) {
            return;
        }
        int top = animatedTop;
        top += Theme.chat_composeShadowDrawable.getIntrinsicHeight() * (1f - composeShadowAlpha);
        if (topView != null && topView.getVisibility() == View.VISIBLE) {
            top += (1f - topViewEnterProgress) * topView.getLayoutParams().height;
        }
        int bottom = top + Theme.chat_composeShadowDrawable.getIntrinsicHeight();

        if (withComposeShadowDrawable) {
            Theme.chat_composeShadowDrawable.setAlpha((int) (composeShadowAlpha * 0xFF));
            Theme.chat_composeShadowDrawable.setBounds(0, top, getMeasuredWidth(), bottom);
            Theme.chat_composeShadowDrawable.draw(canvas);
        }
        bottom += chatSearchExpandOffset;

        if (allowBlur) {
            backgroundPaint.setColor(getThemedColor(Theme.key_chat_messagePanelBackground));
            if (SharedConfig.chatBlurEnabled() && sizeNotifierLayout != null) {
                blurBounds.set(0, bottom, getWidth(), getHeight());
                sizeNotifierLayout.drawBlurRect(canvas, getTop(), blurBounds, backgroundPaint, false);
            } else {
                canvas.drawRect(0, bottom, getWidth(), getHeight(), backgroundPaint);
            }
        } else {
            canvas.drawRect(0, bottom, getWidth(), getHeight(), getThemedPaint(Theme.key_paint_chatComposeBackground));
        }
    }

    private final Runnable dismissSendPreview = () -> {
        if (messageSendPreview != null) {
            messageSendPreview.dismiss(true);
            messageSendPreview = null;
        };
    };

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private ActionBarMenuSubItem actionScheduleButton;
    private boolean onSendLongClick(View view) {
        if (isInScheduleMode() || parentFragment != null && parentFragment.getChatMode() == ChatActivity.MODE_QUICK_REPLIES) {
            return false;
        }

        if (isStories || (messageEditText == null || TextUtils.isEmpty(messageEditText.getText())) && parentFragment != null && parentFragment.messagePreviewParams != null && parentFragment.messagePreviewParams.forwardMessages != null && parentFragment.messagePreviewParams.forwardMessages.messages != null && !parentFragment.messagePreviewParams.forwardMessages.messages.isEmpty()) {

            boolean self = parentFragment != null && UserObject.isUserSelf(parentFragment.getCurrentUser());

            if (sendPopupLayout == null) {
                sendPopupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity, resourcesProvider);
                sendPopupLayout.setAnimationEnabled(false);
                sendPopupLayout.setOnTouchListener(new OnTouchListener() {

                    private android.graphics.Rect popupRect = new android.graphics.Rect();

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

                boolean scheduleButtonValue = parentFragment != null && parentFragment.canScheduleMessage();
                boolean sendWithoutSoundButtonValue = !(self || slowModeTimer > 0 && !isInScheduleMode());
                if (scheduleButtonValue) {
                    actionScheduleButton = new ActionBarMenuSubItem(getContext(), true, !sendWithoutSoundButtonValue, resourcesProvider);
                    if (self) {
                        actionScheduleButton.setTextAndIcon(LocaleController.getString(R.string.SetReminder), R.drawable.msg_calendar2);
                    } else {
                        actionScheduleButton.setTextAndIcon(LocaleController.getString(R.string.ScheduleMessage), R.drawable.msg_calendar2);
                    }
                    actionScheduleButton.setMinimumWidth(dp(196));
                    actionScheduleButton.setOnClickListener(v -> {
                        if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                            sendPopupWindow.dismiss();
                        }
                        AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), new AlertsCreator.ScheduleDatePickerDelegate() {
                            @Override
                            public void didSelectDate(boolean notify, int scheduleDate) {
                                sendMessageInternal(notify, scheduleDate, true);
                            }
                        }, resourcesProvider);
                    });
                    sendPopupLayout.addView(actionScheduleButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    SharedConfig.removeScheduledHint();
                    if (!self && dialog_id > 0) {
                        sendWhenOnlineButton = new ActionBarMenuSubItem(getContext(), true, !sendWithoutSoundButtonValue, resourcesProvider);
                        sendWhenOnlineButton.setTextAndIcon(LocaleController.getString(R.string.SendWhenOnline), R.drawable.msg_online);
                        sendWhenOnlineButton.setMinimumWidth(dp(196));
                        sendWhenOnlineButton.setOnClickListener(v -> {
                            if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                                sendPopupWindow.dismiss();
                            }
                            sendMessageInternal(true, 0x7FFFFFFE, true);
                        });
                        sendPopupLayout.addView(sendWhenOnlineButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    }
                }
                if (sendWithoutSoundButtonValue) {
                    ActionBarMenuSubItem sendWithoutSoundButton = new ActionBarMenuSubItem(getContext(), !scheduleButtonValue, true, resourcesProvider);
                    sendWithoutSoundButton.setTextAndIcon(LocaleController.getString(R.string.SendWithoutSound), R.drawable.input_notify_off);
                    sendWithoutSoundButton.setMinimumWidth(dp(196));
                    sendWithoutSoundButton.setOnClickListener(v -> {
                        if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                            sendPopupWindow.dismiss();
                        }
                        sendMessageInternal(false, 0, true);
                    });
                    sendPopupLayout.addView(sendWithoutSoundButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                }
                sendPopupLayout.setupRadialSelectors(getThemedColor(Theme.key_dialogButtonSelector));

                sendPopupWindow = new ActionBarPopupWindow(sendPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                    @Override
                    public void dismiss() {
                        super.dismiss();
                        sendButton.invalidate();
                    }
                };
                sendPopupWindow.setAnimationEnabled(false);
                sendPopupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
                sendPopupWindow.setOutsideTouchable(true);
                sendPopupWindow.setClippingEnabled(true);
                sendPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                sendPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
                sendPopupWindow.getContentView().setFocusableInTouchMode(true);
                SharedConfig.removeScheduledOrNoSoundHint();

                if (delegate != null) {
                    delegate.onSendLongClick();
                }
            }
            if (actionScheduleButton != null) {
                actionScheduleButton.setVisibility(voiceOnce ? View.GONE : View.VISIBLE);
            }

            if (sendWhenOnlineButton != null) {
                TLRPC.User user = parentFragment == null ? null : parentFragment.getCurrentUser();
                if (user != null && !user.bot && !(user.status instanceof TLRPC.TL_userStatusEmpty) && !(user.status instanceof TLRPC.TL_userStatusOnline) && !(user.status instanceof TLRPC.TL_userStatusRecently) && !(user.status instanceof TLRPC.TL_userStatusLastMonth) && !(user.status instanceof TLRPC.TL_userStatusLastWeek)) {
                    sendWhenOnlineButton.setVisibility(VISIBLE);
                } else {
                    sendWhenOnlineButton.setVisibility(GONE);
                }
            }
            sendPopupLayout.measure(MeasureSpec.makeMeasureSpec(dp(1000), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(1000), MeasureSpec.AT_MOST));
            sendPopupWindow.setFocusable(true);
            view.getLocationInWindow(location);
            int y;
            if (keyboardVisible && ChatActivityEnterView.this.getMeasuredHeight() > dp(topView != null && topView.getVisibility() == VISIBLE ? 48 + 58 : 58)) {
                y = location[1] + view.getMeasuredHeight();
            } else {
                y = location[1] - sendPopupLayout.getMeasuredHeight() - dp(2);
            }
            sendPopupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, location[0] + view.getMeasuredWidth() - sendPopupLayout.getMeasuredWidth() + dp(8), y);
            sendPopupWindow.dimBehind();
            sendButton.invalidate();
            try {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}

            return true;
        }

        if (messageSendPreview != null) {
            messageSendPreview.dismiss(false);
        }
        AndroidUtilities.cancelRunOnUIThread(dismissSendPreview);
        messageSendPreview = new MessageSendPreview(getContext(), resourcesProvider) {
            @Override
            protected void onEffectChange(long effectId) {
                ChatActivityEnterView.this.setEffectId(effectId);
            }
        };
        messageSendPreview.setOnDismissListener(di -> {
            messageSendPreview = null;
        });

        final boolean containsSendMessage = audioToSendMessageObject != null || messageEditText != null && !TextUtils.isEmpty(messageEditText.getText());
        boolean putCameraTexture = false;
        ArrayList<MessageObject> messages = new ArrayList<>();
        int id = 0;

        if (audioToSend != null) {
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.id = id++;
            message.out = true;
            message.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialog_id);
            message.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
            message.media = new TLRPC.TL_messageMediaDocument();
            message.media.voice = true;
            message.media.document = audioToSend;
            message.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            message.attachPath = audioToSendPath;
            MessageObject messageObject = new MessageObject(currentAccount, message, false, true);
            if (replyingMessageObject != null && !replyingMessageObject.isTopicMainMessage) {
                messageObject.replyMessageObject = replyingMessageObject;
            }
            messageObject.isOutOwnerCached = true;
            messageObject.generateLayout(null);
            messageObject.notime = true;
            messageObject.sendPreview = true;
            messages.add(messageObject);
        } else if (containsSendMessage) {
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.id = id++;
            message.out = true;
            message.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialog_id);
            message.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
            CharSequence[] text = new CharSequence[]{ new SpannableStringBuilder(messageEditText == null ? "" : messageEditText.getTextToUse()) };
            MessageObject.addLinks(true, text[0]);
            message.entities.addAll(MediaDataController.getInstance(currentAccount).getEntities(text, true));
            message.message = text[0].toString();
            if (replyingMessageObject != null && !replyingMessageObject.isTopicMainMessage) {
                TLRPC.TL_messageReplyHeader reply_to = new TLRPC.TL_messageReplyHeader();
                if (replyingTopMessage != null) {
                    reply_to.flags |= 2;
                    reply_to.reply_to_top_id = replyingTopMessage.getId();
                }
                reply_to.flags |= 16;
                reply_to.reply_to_msg_id = replyingMessageObject.getId();
                message.reply_to = reply_to;
            }
            if (messageWebPage != null) {
                TLRPC.TL_messageMediaWebPage media = new TLRPC.TL_messageMediaWebPage();
                media.webpage = messageWebPage;
                if (parentFragment != null && parentFragment.messagePreviewParams != null && parentFragment.messagePreviewParams.hasMedia) {
                    media.force_small_media = parentFragment.messagePreviewParams.webpageSmall;
                    media.force_large_media = !parentFragment.messagePreviewParams.webpageSmall;
                    message.invert_media = parentFragment.messagePreviewParams.webpageTop;
                }
                message.media = media;
            }

            MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
            if (replyingMessageObject != null && !replyingMessageObject.isTopicMainMessage) {
                messageObject.replyMessageObject = replyingMessageObject;
            }
            messageObject.sendPreview = true;
            messageObject.isOutOwnerCached = true;
            messageObject.type = MessageObject.TYPE_TEXT;
            messageObject.generateLayout(null);
            messageObject.notime = true;
            messages.add(messageObject);
        } else if (parentFragment != null && parentFragment.instantCameraView != null && parentFragment.instantCameraView.getTextureView() != null) {
            putCameraTexture = true;
            messageSendPreview.setCameraTexture(parentFragment.instantCameraView.getTextureView());
        }

//        if (parentFragment != null && parentFragment.messagePreviewParams != null && parentFragment.messagePreviewParams.forwardMessages != null && parentFragment.messagePreviewParams.forwardMessages.messages != null && !parentFragment.messagePreviewParams.forwardMessages.messages.isEmpty()) {
//            for (MessageObject msg : parentFragment.messagePreviewParams.forwardMessages.messages) {
//                TLRPC.TL_message tlmsg = new TLRPC.TL_message();
//                tlmsg.id = id++;
//                tlmsg.flags = msg.messageOwner.flags;
//                tlmsg.message = msg.messageOwner.message;
//                tlmsg.entities = msg.messageOwner.entities;
//                tlmsg.media = msg.messageOwner.media;
//                tlmsg.peer_id = MessagesController.getInstance(currentAccount).getPeer(dialog_id);
//                tlmsg.out = true;
//                tlmsg.grouped_id = msg.messageOwner.grouped_id;
//                TLRPC.TL_messageFwdHeader fwd = new TLRPC.TL_messageFwdHeader();
//                fwd.flags |= 1;
//                fwd.date = tlmsg.date;
//                tlmsg.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
//                fwd.from_id = msg.messageOwner.from_id;
//                tlmsg.fwd_from = fwd;
//                tlmsg.flags |= TLRPC.MESSAGE_FLAG_FWD;
//                tlmsg.from_id = MessagesController.getInstance(currentAccount).getPeer(UserConfig.getInstance(currentAccount).getClientUserId());
//                MessageObject fwdmsg = new MessageObject(currentAccount, tlmsg, true, false);
//                fwdmsg.notime = true;
//                fwdmsg.isOutOwnerCached = true;
//                messages.add(fwdmsg);
//            }
//        }

        messageSendPreview.setMessageObjects(messages);
        if (containsSendMessage && audioToSend == null) {
            messageSendPreview.setEditText(messageEditText, this::drawMessageEditText, canvas -> drawBackground(canvas, false));
        }
        messageSendPreview.setSendButton(sendButton, true, v -> {
            sentFromPreview = System.currentTimeMillis();
            sendMessage();
            if (!containsSendMessage && messageSendPreview != null) {
                messageSendPreview.dismiss(true);
                messageSendPreview = null;
            } else {
                AndroidUtilities.cancelRunOnUIThread(dismissSendPreview);
                AndroidUtilities.runOnUIThread(dismissSendPreview, 500);
            }
        });
        if ((containsSendMessage || putCameraTexture) && dialog_id >= 0) {
            messageSendPreview.allowEffectSelector(parentFragment);
            messageSendPreview.setEffectId(effectId);
        }

        ItemOptions options = ItemOptions.makeOptions(this, resourcesProvider, sendButton);

        final boolean self = parentFragment != null && UserObject.isUserSelf(parentFragment.getCurrentUser());
        boolean scheduleButtonValue = parentFragment != null && parentFragment.canScheduleMessage();
        boolean sendWithoutSoundButtonValue = !(self || slowModeTimer > 0 && !isInScheduleMode());
        if (scheduleButtonValue) {
            options.add(R.drawable.msg_calendar2, getString(self ? R.string.SetReminder : R.string.ScheduleMessage), () -> {
                AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), new AlertsCreator.ScheduleDatePickerDelegate() {
                    @Override
                    public void didSelectDate(boolean notify, int scheduleDate) {
                        sendMessageInternal(notify, scheduleDate, true);
                        if (messageSendPreview != null) {
                            messageSendPreview.dismiss(true);
                            messageSendPreview = null;
                        }
                    }
                }, resourcesProvider);
            });

            if (!self && dialog_id > 0) {
                options.add(R.drawable.msg_online, getString(R.string.SendWhenOnline), () -> {
                    sendMessageInternal(true, 0x7FFFFFFE, true);
                    if (messageSendPreview != null) {
                        messageSendPreview.dismiss(true);
                        messageSendPreview = null;
                    }
                });
                sendWhenOnlineButton = options.getLast();
            }
        }
        if (sendWithoutSoundButtonValue) {
            options.add(R.drawable.input_notify_off, getString(R.string.SendWithoutSound), () -> {
                sentFromPreview = System.currentTimeMillis();
                sendMessageInternal(false, 0, true);
                if (!containsSendMessage && messageSendPreview != null) {
                    messageSendPreview.dismiss(true);
                    messageSendPreview = null;
                } else {
                    AndroidUtilities.cancelRunOnUIThread(dismissSendPreview);
                    AndroidUtilities.runOnUIThread(dismissSendPreview, 500);
                }
            });
        }
        options.setupSelectors();
        if (sendWhenOnlineButton != null) {
            TLRPC.User user = parentFragment == null ? null : parentFragment.getCurrentUser();
            if (user != null && !user.bot && !(user.status instanceof TLRPC.TL_userStatusEmpty) && !(user.status instanceof TLRPC.TL_userStatusOnline) && !(user.status instanceof TLRPC.TL_userStatusRecently) && !(user.status instanceof TLRPC.TL_userStatusLastMonth) && !(user.status instanceof TLRPC.TL_userStatusLastWeek)) {
                sendWhenOnlineButton.setVisibility(VISIBLE);
            } else {
                sendWhenOnlineButton.setVisibility(GONE);
            }
        }
        messageSendPreview.setItemOptions(options);

        messageSendPreview.show();

        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}

        return false;
    }

    private void createBotCommandsMenuContainer() {
        if (botCommandsMenuContainer != null) {
            return;
        }
        botCommandsMenuContainer = new BotCommandsMenuContainer(getContext()) {
            boolean ignoreLayout = false;

            @Override
            protected void onDismiss() {
                super.onDismiss();
                if (botCommandsMenuButton != null) {
                    botCommandsMenuButton.setOpened(false);
                }
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (!ignoreLayout) {
                    ignoreLayout = true;
                    updateBotCommandsMenuContainerTopPadding();
                }
            }
        };
        botCommandsMenuContainer.listView.setLayoutManager(new LinearLayoutManager(getContext()));
        botCommandsMenuContainer.listView.setAdapter(botCommandsAdapter = new BotCommandsMenuView.BotCommandsAdapter());
        botCommandsMenuContainer.listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (view instanceof BotCommandsMenuView.BotCommandView) {
                    String command = ((BotCommandsMenuView.BotCommandView) view).getCommand();
                    if (TextUtils.isEmpty(command)) {
                        return;
                    }
                    if (isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(parentActivity, dialog_id, (notify, scheduleDate) -> {
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(command, dialog_id, replyingMessageObject, getThreadMessage(), null, false, null, null, null, notify, scheduleDate, null, false));
                            setFieldText("");
                            botCommandsMenuContainer.dismiss();
                        }, resourcesProvider);
                    } else {
                        if (parentFragment != null && parentFragment.checkSlowMode(view)) {
                            return;
                        }
                        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(command, dialog_id, replyingMessageObject, getThreadMessage(), null, false, null, null, null, true, 0, null, false);
                        params.quick_reply_shortcut = parentFragment != null ? parentFragment.quickReplyShortcut : null;
                        params.quick_reply_shortcut_id = parentFragment != null ? parentFragment.getQuickReplyId() : 0;
                        params.effect_id = effectId;
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                        setFieldText("");
                        botCommandsMenuContainer.dismiss();
                        sendButton.setEffect(effectId = 0);
                    }
                }
            }
        });
        botCommandsMenuContainer.listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (view instanceof BotCommandsMenuView.BotCommandView) {
                    String command = ((BotCommandsMenuView.BotCommandView) view).getCommand();
                    setFieldText(command + " ");
                    botCommandsMenuContainer.dismiss();
                    return true;
                }
                return false;
            }
        });
        botCommandsMenuContainer.setClipToPadding(false);
        sizeNotifierLayout.addView(botCommandsMenuContainer, 14, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM));
        botCommandsMenuContainer.setVisibility(View.GONE);

        if (lastBotInfo != null) {
            botCommandsAdapter.setBotInfo(lastBotInfo);
        }
        updateBotCommandsMenuContainerTopPadding();
    }

    private void updateBotCommandsMenuContainerTopPadding() {
        if (botCommandsMenuContainer == null) {
            return;
        }
        int padding;

        final int viewsCount = botCommandsMenuContainer.listView.getChildCount();
        int measuredFourViewsHeight = 0;
        for (int i = 0; i < viewsCount; i++) {
            final View child = botCommandsMenuContainer.listView.getChildAt(i);
            if (i < 4) {
                measuredFourViewsHeight += child.getMeasuredHeight();
            }
        }

        if (measuredFourViewsHeight > 0) {
            padding = Math.max(0, sizeNotifierLayout.getMeasuredHeight() - measuredFourViewsHeight - dp(8) - dp(viewsCount > 4 ? 12 : 0));
        } else {
            if (botCommandsAdapter.getItemCount() > 4) {
                padding = Math.max(0, sizeNotifierLayout.getMeasuredHeight() - dp(8 + 36 * 4.3f));
            } else {
                padding = Math.max(0, sizeNotifierLayout.getMeasuredHeight() - dp(8 + 36 * Math.max(1, Math.min(4, botCommandsAdapter.getItemCount()))));
            }
        }

        if (botCommandsMenuContainer.listView.getPaddingTop() != padding) {
            botCommandsMenuContainer.listView.setTopGlowOffset(padding);
            if (botCommandLastPosition == -1 && botCommandsMenuContainer.getVisibility() == View.VISIBLE && botCommandsMenuContainer.listView.getLayoutManager() != null) {
                LinearLayoutManager layoutManager = (LinearLayoutManager) botCommandsMenuContainer.listView.getLayoutManager();
                int p = layoutManager.findFirstVisibleItemPosition();
                if (p >= 0) {
                    View view = layoutManager.findViewByPosition(p);
                    if (view != null) {
                        botCommandLastPosition = p;
                        botCommandLastTop = view.getTop() - botCommandsMenuContainer.listView.getPaddingTop();
                    }
                }
            }
            botCommandsMenuContainer.listView.setPadding(0, padding, 0, dp(8));
        }
    }

    private void createBotWebViewMenuContainer() {
//        if (botWebViewMenuContainer != null) {
//            return;
//        }
//        botWebViewMenuContainer = new BotWebViewMenuContainer(getContext(), this) {
//            @Override
//            public void onDismiss() {
//                super.onDismiss();
//                if (botCommandsMenuButton != null) {
//                    botCommandsMenuButton.setOpened(false);
//                }
//            }
//        };
//        sizeNotifierLayout.addView(botWebViewMenuContainer, 15, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM));
//        botWebViewMenuContainer.setVisibility(GONE);
//        botWebViewMenuContainer.setOnDismissGlobalListener(()->{
//            if (botButtonsMessageObject != null && (messageEditText == null || TextUtils.isEmpty(messageEditText.getTextToUse())) && !botWebViewMenuContainer.hasSavedText()) {
//                showPopup(1, POPUP_CONTENT_BOT_KEYBOARD);
//            }
//        });
    }

    private ArrayList<TextWatcher> messageEditTextWatchers;
    private boolean messageEditTextEnabled = true;

    private class ChatActivityEditTextCaption extends EditTextCaption {
        public ChatActivityEditTextCaption(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
        }

        CanvasButton canvasButton;

        @Override
        protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
            super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
            if (delegate != null) {
                delegate.onEditTextScroll();
            }
        }

        @Override
        protected void onContextMenuOpen() {
            if (delegate != null) {
                delegate.onContextMenuOpen();
            }
        }

        @Override
        protected void onContextMenuClose() {
            if (delegate != null) {
                delegate.onContextMenuClose();
            }
        }

        private void send(InputContentInfoCompat inputContentInfo, boolean notify, int scheduleDate) {
            if (messageSendPreview != null) {
                messageSendPreview.dismiss(true);
                messageSendPreview = null;
            }
            if (replyingQuote != null && parentFragment != null && replyingQuote.outdated) {
                parentFragment.showQuoteMessageUpdate();
                return;
            }
            ClipDescription description = inputContentInfo.getDescription();
            if (description.hasMimeType("image/gif")) {
                SendMessagesHelper.prepareSendingDocument(accountInstance, null, null, inputContentInfo.getContentUri(), null, "image/gif", dialog_id, replyingMessageObject, getThreadMessage(), null, replyingQuote, null, notify, 0, inputContentInfo, parentFragment != null ? parentFragment.quickReplyShortcut : null, parentFragment != null ? parentFragment.getQuickReplyId() : 0, false);
            } else {
                SendMessagesHelper.prepareSendingPhoto(accountInstance, null, inputContentInfo.getContentUri(), dialog_id, replyingMessageObject, getThreadMessage(), replyingQuote, null, null, null, inputContentInfo, 0, null, notify, 0, parentFragment == null ? 0 : parentFragment.getChatMode(), parentFragment != null ? parentFragment.quickReplyShortcut : null, parentFragment != null ? parentFragment.getQuickReplyId() : 0);
            }
            if (delegate != null) {
                delegate.onMessageSend(null, true, scheduleDate);
            }
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
            final InputConnection ic = super.onCreateInputConnection(editorInfo);
            if (ic == null) {
                return null;
            }
            try {
                if (isEditingBusinessLink()) {
                    EditorInfoCompat.setContentMimeTypes(editorInfo, null);
                } else {
                    EditorInfoCompat.setContentMimeTypes(editorInfo, new String[]{"image/gif", "image/*", "image/jpg", "image/png", "image/webp"});
                }
                final InputConnectionCompat.OnCommitContentListener callback = (inputContentInfo, flags, opts) -> {
                    if (BuildCompat.isAtLeastNMR1() && (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                        try {
                            inputContentInfo.requestPermission();
                        } catch (Exception e) {
                            return false;
                        }
                    }
                    if (inputContentInfo.getDescription().hasMimeType("image/gif") || SendMessagesHelper.shouldSendWebPAsSticker(null, inputContentInfo.getContentUri())) {
                        if (isInScheduleMode()) {
                            AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), (notify, scheduleDate) -> send(inputContentInfo, notify, scheduleDate), resourcesProvider);
                        } else {
                            send(inputContentInfo, true, 0);
                        }
                    } else {
                        editPhoto(inputContentInfo.getContentUri(), inputContentInfo.getDescription().getMimeType(0));
                    }
                    return true;
                };
                return InputConnectionCompat.createWrapper(ic, editorInfo, callback);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            return ic;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (stickersDragging || stickersExpansionAnim != null) {
                return false;
            }
            if (!sendPlainEnabled && !isEditingMessage()) {
                if (canvasButton == null) {
                    canvasButton = new CanvasButton(this);
                    canvasButton.setDelegate(() -> {
                        showRestrictedHint();
                    });
                }
                canvasButton.setRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                return canvasButton.checkTouchEvent(event);
            }
            if (isPopupShowing() && event.getAction() == MotionEvent.ACTION_DOWN) {
                if (searchingType != 0) {
                    setSearchingTypeInternal(0, false);
                    emojiView.closeSearch(false);
                    requestFocus();
                }
                showPopup(AndroidUtilities.usingHardwareInput ? 0 : 2, 0);
                if (stickersExpanded) {
                    setStickersExpanded(false, true, false);
                    waitingForKeyboardOpenAfterAnimation = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        waitingForKeyboardOpenAfterAnimation = false;
                        openKeyboardInternal();
                    }, 200);
                } else {
                    openKeyboardInternal();
                }
                return true;
            }
            try {
                return super.onTouchEvent(event);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (preventInput) {
                return false;
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        protected void onSelectionChanged(int selStart, int selEnd) {
            super.onSelectionChanged(selStart, selEnd);
            if (delegate != null) {
                delegate.onTextSelectionChanged(selStart, selEnd);
            }
        }

        @Override
        protected void extendActionMode(ActionMode actionMode, Menu menu) {
            if (parentFragment != null) {
                parentFragment.extendActionMode(menu);
            } else {
                ChatActivityEnterView.this.extendActionMode(menu);
            }
        }

        @Override
        public boolean requestRectangleOnScreen(Rect rectangle) {
            rectangle.bottom += dp(1000);
            return super.requestRectangleOnScreen(rectangle);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            isInitLineCount = getMeasuredWidth() == 0 && getMeasuredHeight() == 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (isInitLineCount) {
                lineCount = getLineCount();
            }
            isInitLineCount = false;
        }

        @Override
        public boolean onTextContextMenuItem(int id) {
            if (id == android.R.id.paste) {
                isPaste = true;

                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null) {
                    if (clipData.getItemCount() == 1 && clipData.getDescription().hasMimeType("image/*") && !isEditingBusinessLink()) {
                        editPhoto(clipData.getItemAt(0).getUri(), clipData.getDescription().getMimeType(0));
                    }
                }
            }
            return super.onTextContextMenuItem(id);
        }

        private void editPhoto(Uri uri, String mime) {
            final File file = AndroidUtilities.generatePicturePath(parentFragment != null && parentFragment.isSecretChat(), MimeTypeMap.getSingleton().getExtensionFromMimeType(mime));
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    InputStream in = getContext().getContentResolver().openInputStream(uri);
                    FileOutputStream fos = new FileOutputStream(file);
                    byte[] buffer = new byte[1024];
                    int lengthRead;
                    while ((lengthRead = in.read(buffer)) > 0) {
                        fos.write(buffer, 0, lengthRead);
                        fos.flush();
                    }
                    in.close();
                    fos.close();
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, -1, 0, file.getAbsolutePath(), 0, false, 0, 0, 0);
                    ArrayList<Object> entries = new ArrayList<>();
                    entries.add(photoEntry);
                    AndroidUtilities.runOnUIThread(() -> {
                        openPhotoViewerForEdit(entries, file);
                    });
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
        }

        private void openPhotoViewerForEdit(ArrayList<Object> entries, File sourceFile) {
            if (parentFragment == null || parentFragment.getParentActivity() == null) {
                return;
            }
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entries.get(0);
            if (keyboardVisible) {
                AndroidUtilities.hideKeyboard(this);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        openPhotoViewerForEdit(entries, sourceFile);
                    }
                }, 100);
                return;
            }

            PhotoViewer.getInstance().setParentActivity(parentFragment, resourcesProvider);
            PhotoViewer.getInstance().openPhotoForSelect(entries, 0, 2, false, new PhotoViewer.EmptyPhotoViewerProvider() {
                boolean sending;
                @Override
                public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
                    if (replyingQuote != null && parentFragment != null && replyingQuote.outdated) {
                        parentFragment.showQuoteMessageUpdate();
                        return;
                    }
                    ArrayList<SendMessagesHelper.SendingMediaInfo> photos = new ArrayList<>();
                    SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                    if (!photoEntry.isVideo && photoEntry.imagePath != null) {
                        info.path = photoEntry.imagePath;
                    } else if (photoEntry.path != null) {
                        info.path = photoEntry.path;
                    }
                    info.thumbPath = photoEntry.thumbPath;
                    info.isVideo = photoEntry.isVideo;
                    info.caption = photoEntry.caption != null ? photoEntry.caption.toString() : null;
                    info.entities = photoEntry.entities;
                    info.masks = photoEntry.stickers;
                    info.ttl = photoEntry.ttl;
                    info.videoEditedInfo = videoEditedInfo;
                    info.canDeleteAfter = true;
                    photos.add(info);
                    photoEntry.reset();
                    sending = true;
                    boolean updateStickersOrder = SendMessagesHelper.checkUpdateStickersOrder(info.caption);
                    SendMessagesHelper.prepareSendingMedia(accountInstance, photos, dialog_id, replyingMessageObject, getThreadMessage(), null, replyingQuote, false, false, editingMessageObject, notify, scheduleDate, parentFragment == null ? 0 : parentFragment.getChatMode(), updateStickersOrder, null, parentFragment != null ? parentFragment.quickReplyShortcut : null, parentFragment != null ? parentFragment.getQuickReplyId() : 0, 0, false);
                    if (delegate != null) {
                        delegate.onMessageSend(null, true, scheduleDate);
                    }
                }

                @Override
                public void willHidePhotoViewer() {
                    if (!sending) {
                        try {
                            sourceFile.delete();
                        } catch (Throwable ignore) {

                        }
                    }
                }

                @Override
                public boolean canCaptureMorePhotos() {
                    return false;
                }
            }, parentFragment);
        }

        @Override
        protected Theme.ResourcesProvider getResourcesProvider() {
            return resourcesProvider;
        }

        @Override
        public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
            if (!sendPlainEnabled && !isEditingMessage()) {
                return false;
            }
            return super.requestFocus(direction, previouslyFocusedRect);
        }

        @Override
        public void setOffsetY(float offset) {
            super.setOffsetY(offset);
            if (sizeNotifierLayout.getForeground() != null) {
                sizeNotifierLayout.invalidateDrawable(sizeNotifierLayout.getForeground());
            }
        }
    }

    public void extendActionMode(Menu menu) {

    }

    /**
     * Samsung keyboard does not support incognito mode.
     * Also on samsung keyboard when the EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING flag is set, the keyboard starts to lag.
     */
    private boolean isKeyboardSupportIncognitoMode() {
        String keyboardName = Settings.Secure.getString(getContext().getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return keyboardName == null || !keyboardName.startsWith("com.samsung");
    }

    private void createMessageEditText() {
        if (messageEditText != null) {
            return;
        }

        messageEditText = new ChatActivityEditTextCaption(getContext(), resourcesProvider) {

            float touchX, touchY;
            boolean clickMaybe;
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (botCommandsMenuIsShowing()) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        touchX = event.getX();
                        touchY = event.getY();
                        clickMaybe = true;
                    } else if (clickMaybe && event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (Math.abs(event.getX() - touchX) > AndroidUtilities.touchSlop || Math.abs(event.getY() - touchY) > AndroidUtilities.touchSlop) {
                            clickMaybe = false;
                        }
                    } else {
                        if (clickMaybe) {
                            if (delegate != null) {
                                fixHandlesColor();
                                delegate.onKeyboardRequested();
                            }
                            if (messageEditText != null && !AndroidUtilities.showKeyboard(messageEditText)) {
                                messageEditText.clearFocus();
                                messageEditText.requestFocus();
                            }
                        }
                    }
                    return clickMaybe;
                } else {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (delegate != null) {
                            fixHandlesColor();
                            delegate.onKeyboardRequested();
                        }
                    }
                    return super.onTouchEvent(event);
                }
            }

            /*
             * The color of the handles changes when opened PhotoView and write a comment.
             */
            private void fixHandlesColor() {
                setHandlesColor(getThemedColor(Theme.key_chat_TextSelectionCursor));
            }

            @Override
            public void setOffsetY(float offset) {
                super.setOffsetY(offset);
                messageEditTextContainer.invalidate();
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                if (parentFragment != null && parentFragment.getParentLayout() != null && parentFragment.getParentLayout().isSheet()) {
                    setWindowView(parentFragment.getParentLayout().getWindow().getDecorView());
                } else {
                    setWindowView(parentActivity.getWindow().getDecorView());
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            messageEditText.setFallbackLineSpacing(false);
        }
        messageEditText.setDelegate(() -> {
            messageEditText.invalidateEffects();
            if (delegate != null) {
                delegate.onTextSpansChanged(messageEditText.getTextToUse());
            }
        });
        if (parentFragment != null && parentFragment.getParentLayout() != null && parentFragment.getParentLayout().isSheet()) {
            messageEditText.setWindowView(parentFragment.getParentLayout().getWindow().getDecorView());
        } else {
            messageEditText.setWindowView(parentActivity.getWindow().getDecorView());
        }
        TLRPC.EncryptedChat encryptedChat = parentFragment != null ? parentFragment.getCurrentEncryptedChat() : null;
        messageEditText.setAllowTextEntitiesIntersection(supportsSendingNewEntities());
        int flags = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        if (isKeyboardSupportIncognitoMode() && encryptedChat != null) {
            flags |= EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        messageEditText.setIncludeFontPadding(false);
        messageEditText.setImeOptions(flags);
        messageEditText.setInputType(commonInputType = (messageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE));
        updateFieldHint(false);
        messageEditText.setSingleLine(false);
        messageEditText.setMaxLines(6);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messageEditText.setGravity(Gravity.BOTTOM);
        messageEditText.setPadding(0, dp(11), 0, dp(12));
        messageEditText.setBackgroundDrawable(null);
        messageEditText.setTextColor(getThemedColor(Theme.key_chat_messagePanelText));
        messageEditText.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkOut));
        messageEditText.setHighlightColor(getThemedColor(Theme.key_chat_inTextSelectionHighlight));
        messageEditText.setHintColor(getThemedColor(Theme.key_chat_messagePanelHint));
        messageEditText.setHintTextColor(getThemedColor(Theme.key_chat_messagePanelHint));
        messageEditText.setCursorColor(getThemedColor(Theme.key_chat_messagePanelCursor));
        messageEditText.setHandlesColor(getThemedColor(Theme.key_chat_TextSelectionCursor));
        messageEditTextContainer.addView(messageEditText, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 52, 0, isChat ? 50 : 2, 1.5f));
        messageEditText.setOnKeyListener(new OnKeyListener() {

            @Override
            public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                if (keyEvent != null) {
                    shiftPressed = keyEvent.isShiftPressed();
                    ctrlPressed = keyEvent.isCtrlPressed();
                }
                if (keyCode == KeyEvent.KEYCODE_BACK && !keyboardVisible && isPopupShowing() && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    if (ContentPreviewViewer.hasInstance() && ContentPreviewViewer.getInstance().isVisible()) {
                        ContentPreviewViewer.getInstance().closeWithMenu();
                        return true;
                    }
                    if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD && botButtonsMessageObject != null) {
                        return false;
                    }
                    if (keyEvent.getAction() == 1) {
                        if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD && botButtonsMessageObject != null) {
                            SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
                            preferences.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
                        }
                        if (searchingType != 0) {
                            setSearchingTypeInternal(0, true);
                            if (emojiView != null) {
                                emojiView.closeSearch(true);
                            }
                            messageEditText.requestFocus();
                        } else {
                            if (stickersExpanded) {
                                setStickersExpanded(false, true, false);
                            } else {
                                if (stickersExpansionAnim == null) {
                                    if (botButtonsMessageObject != null && currentPopupContentType != POPUP_CONTENT_BOT_KEYBOARD && TextUtils.isEmpty(messageEditText.getTextToUse())) {
                                        showPopup(1, POPUP_CONTENT_BOT_KEYBOARD);
                                    } else {
                                        showPopup(0, 0);
                                    }
                                }
                            }
                        }
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_ENTER && !keyEvent.isShiftPressed() && (sendByEnter ? !keyEvent.isCtrlPressed() : keyEvent.isCtrlPressed()) && keyEvent.getAction() == KeyEvent.ACTION_DOWN && editingMessageObject == null) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });
        messageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                } else if (keyEvent != null && i == EditorInfo.IME_NULL) {
                    if (!keyEvent.isShiftPressed() && (sendByEnter ? !keyEvent.isCtrlPressed() : keyEvent.isCtrlPressed()) && keyEvent.getAction() == KeyEvent.ACTION_DOWN && editingMessageObject == null) {
                        sendMessage();
                        return true;
                    }
                }
                return false;
            }
        });
        messageEditText.addTextChangedListener(new TextWatcher() {

            private boolean processChange;
            private boolean nextChangeIsSend;
            private CharSequence prevText;
            private boolean ignorePrevTextChange;
            boolean heightShouldBeChanged;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                if (ignorePrevTextChange) {
                    return;
                }
                if (recordingAudioVideo) {
                    prevText = charSequence.toString();
                }
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (ignorePrevTextChange) {
                    return;
                }

                boolean allowChangeToSmile = true;
                int currentPage;
                if (emojiView == null) {
                    currentPage = MessagesController.getGlobalEmojiSettings().getInt("selected_page", 0);
                } else {
                    currentPage = emojiView.getCurrentPage();
                }
                if (currentPage == 0 || !allowStickers && !allowGifs) {
                    allowChangeToSmile = false;
                }

                if ((before == 0 && !TextUtils.isEmpty(charSequence) || before != 0 && TextUtils.isEmpty(charSequence)) && allowChangeToSmile) {
                    setEmojiButtonImage(false, true);
                }
                if (lineCount != messageEditText.getLineCount()) {
                    heightShouldBeChanged = (messageEditText.getLineCount() >= 4) != (lineCount >= 4);
                    if (!isInitLineCount && messageEditText.getMeasuredWidth() > 0) {
                        onLineCountChanged(lineCount, messageEditText.getLineCount());
                    }
                    lineCount = messageEditText.getLineCount();
                } else {
                    heightShouldBeChanged = false;
                }

                if (innerTextChange == 1) {
                    return;
                }
                if (sendByEnter && !ctrlPressed && !shiftPressed && !ignoreTextChange && !isPaste && editingMessageObject == null && count > before && charSequence.length() > 0 && charSequence.length() == start + count && charSequence.charAt(charSequence.length() - 1) == '\n') {
                    nextChangeIsSend = true;
                }
                isPaste = false;
                checkSendButton(true);
                CharSequence message = AndroidUtilities.getTrimmedString(charSequence.toString());
                if (delegate != null) {
                    if (!ignoreTextChange) {
                        if (before > count + 1 || (count - before) > 2 || TextUtils.isEmpty(charSequence)) {
                            messageWebPageSearch = true;
                        }
                        delegate.onTextChanged(charSequence, before > count + 1 || (count - before) > 2, false);
                    }
                }
                if (innerTextChange != 2 && (count - before) > 1) {
                    processChange = true;
                }
                if (editingMessageObject == null && !canWriteToChannel && message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
                    lastTypingTimeSend = System.currentTimeMillis();
                    if (delegate != null) {
                        delegate.needSendTyping();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (ignorePrevTextChange) {
                    return;
                }
                if (prevText != null) {
                    ignorePrevTextChange = true;
                    editable.replace(0, editable.length(), prevText);
                    prevText = null;
                    ignorePrevTextChange = false;
                    return;
                }
                if (innerTextChange == 0) {
                    if (nextChangeIsSend) {
                        sendMessage();
                        nextChangeIsSend = false;
                    }
                    if (processChange) {
                        ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);
                        for (int i = 0; i < spans.length; i++) {
                            editable.removeSpan(spans[i]);
                        }
                        Emoji.replaceEmoji(editable, messageEditText.getPaint().getFontMetricsInt(), false, null);
                        processChange = false;
                    }
                }

                int beforeLimit;
                codePointCount = Character.codePointCount(editable, 0, editable.length());
                boolean doneButtonEnabledLocal = true;
                if (currentLimit > 0 && (beforeLimit = currentLimit - codePointCount) <= 100) {
                    if (beforeLimit < -9999) {
                        beforeLimit = -9999;
                    }
                    createCaptionLimitView();
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
                        doneButtonEnabledLocal = false;
                        captionLimitView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    } else {
                        captionLimitView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
                    }
                } else if (captionLimitView != null) {
                    captionLimitView.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setDuration(100).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            captionLimitView.setVisibility(View.GONE);
                        }
                    });
                }

                if (doneButtonEnabled != doneButtonEnabledLocal && (doneButton != null)) {
                    doneButtonEnabled = doneButtonEnabledLocal;
                    if (doneButton != null) {
                        doneButton.invalidate();
                    }
                }
                if (botCommandsMenuContainer != null) {
                    botCommandsMenuContainer.dismiss();
                }
                checkBotMenu();

                if (editingCaption && !captionLimitBulletinShown && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && !UserConfig.getInstance(currentAccount).isPremium() && codePointCount > MessagesController.getInstance(currentAccount).captionLengthLimitDefault && codePointCount < MessagesController.getInstance(currentAccount).captionLengthLimitPremium) {
                    captionLimitBulletinShown = true;
                    if (heightShouldBeChanged) {
                        AndroidUtilities.runOnUIThread(()->showCaptionLimitBulletin(), 300);
                    } else {
                        showCaptionLimitBulletin();
                    }
                }
            }
        });
        messageEditText.addTextChangedListener(new EditTextSuggestionsFix());
        messageEditText.setEnabled(messageEditTextEnabled);
        if (messageEditTextWatchers != null) {
            for (TextWatcher textWatcher : messageEditTextWatchers) {
                messageEditText.addTextChangedListener(textWatcher);
            }
            messageEditTextWatchers.clear();
        }
        updateFieldHint(false);
        updateSendAsButton(parentFragment != null && parentFragment.getFragmentBeginToShow());
        if (parentFragment != null) {
            parentFragment.applyDraftMaybe(false);
        }
    }

    public void addTextChangedListener(TextWatcher textWatcher) {
        if (messageEditText != null) {
            messageEditText.addTextChangedListener(textWatcher);
        } else {
            if (messageEditTextWatchers == null) {
                messageEditTextWatchers = new ArrayList<>();
            }
            messageEditTextWatchers.add(textWatcher);
        }
    }

    public boolean isSendButtonVisible() {
        return sendButton.getVisibility() == VISIBLE;
    }

    private void setRecordVideoButtonVisible(boolean visible, boolean animated) {
        if (audioVideoSendButton == null) {
            return;
        }
        isInVideoMode = visible;

        if (animated) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean isChannel = false;
            if (DialogObject.isChatDialog(dialog_id)) {
                TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-dialog_id);
                isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            }
            preferences.edit().putBoolean(isChannel ? "currentModeVideoChannel" : "currentModeVideo", visible).apply();
        }
        audioVideoSendButton.setState(isInVideoMode() ? ChatActivityEnterViewAnimatedIconView.State.VIDEO : ChatActivityEnterViewAnimatedIconView.State.VOICE, animated);
        audioVideoSendButton.setContentDescription(getString(isInVideoMode() ? R.string.AccDescrVideoMessage : R.string.AccDescrVoiceMessage));
        audioVideoButtonContainer.setContentDescription(getString(isInVideoMode() ? R.string.AccDescrVideoMessage : R.string.AccDescrVoiceMessage));
        audioVideoSendButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    public boolean isRecordingAudioVideo() {
        return recordingAudioVideo || (runningAnimationAudio != null && runningAnimationAudio.isRunning()) && !recordIsCanceled;
    }

    public boolean isRecordLocked() {
        return recordingAudioVideo && recordCircle.isSendButtonVisible();
    }

    public void cancelRecordingAudioVideo() {
        if (hasRecordVideo && isInVideoMode()) {
            CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
            delegate.needStartRecordVideo(5, true, 0, voiceOnce ? 0x7FFFFFFF : 0, effectId);
            sendButton.setEffect(effectId = 0);
        } else {
            delegate.needStartRecordAudio(0);
            MediaController.getInstance().stopRecording(0, false, 0, false);
        }
        recordingAudioVideo = false;
        updateRecordInterface(RECORD_STATE_CANCEL, true);
    }

    public void showContextProgress(boolean show) {
        if (progressDrawable == null) {
            return;
        }
        if (show) {
            progressDrawable.startAnimation();
        } else {
            progressDrawable.stopAnimation();
        }
    }

    public void setCaption(String caption) {
        if (messageEditText != null) {
            messageEditText.setCaption(caption);
            checkSendButton(true);
        }
    }

    public void setSlowModeTimer(int time) {
        slowModeTimer = time;
        updateSlowModeText();
    }

    public CharSequence getSlowModeTimer() {
        return slowModeTimer > 0 ? slowModeButton.getText() : null;
    }

    private void updateSlowModeText() {
        int serverTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        AndroidUtilities.cancelRunOnUIThread(updateSlowModeRunnable);
        updateSlowModeRunnable = null;
        int currentTime;
        boolean isUploading;
        if (info != null && info.slowmode_seconds != 0 && info.slowmode_next_send_date <= serverTime && (
                (isUploading = SendMessagesHelper.getInstance(currentAccount).isUploadingMessageIdDialog(dialog_id)) ||
                        SendMessagesHelper.getInstance(currentAccount).isSendingMessageIdDialog(dialog_id))
        ) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(info.id);
            if (!ChatObject.hasAdminRights(chat) && !ChatObject.isIgnoredChatRestrictionsForBoosters(info)) {
                currentTime = info.slowmode_seconds;
                slowModeTimer = isUploading ? Integer.MAX_VALUE : Integer.MAX_VALUE - 1;
            } else {
                currentTime = 0;
            }
        } else if (slowModeTimer >= Integer.MAX_VALUE - 1) {
            currentTime = 0;
            if (info != null) {
                accountInstance.getMessagesController().loadFullChat(info.id, 0, true);
            }
        } else {
            currentTime = slowModeTimer - serverTime;
        }
        if (slowModeTimer != 0 && currentTime > 0) {
            slowModeButton.setText(AndroidUtilities.formatDurationNoHours(Math.max(1, currentTime), false));
            if (delegate != null) {
                delegate.onUpdateSlowModeButton(slowModeButton, false, slowModeButton.getText());
            }
            AndroidUtilities.runOnUIThread(updateSlowModeRunnable = this::updateSlowModeText, 100);
        } else {
            slowModeTimer = 0;
        }
        if (!isInScheduleMode()) {
            checkSendButton(true);
        }
    }

    public void addTopView(View view, View lineView, int height) {
        if (view == null) {
            return;
        }
        topLineView = lineView;
        topLineView.setVisibility(GONE);
        topLineView.setAlpha(0.0f);
        addView(topLineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.TOP | Gravity.LEFT, 0, 1 + height, 0, 0));

        topView = view;
        topView.setVisibility(GONE);
        topViewEnterProgress = 0f;
        topView.setTranslationY(height);
        addView(topView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, height, Gravity.TOP | Gravity.LEFT, 0, 2, 0, 0));
        needShowTopView = false;
    }

    public void setForceShowSendButton(boolean value, boolean animated) {
        forceShowSendButton = value;
        checkSendButton(animated);
    }

    public void setAllowStickersAndGifs(boolean needAnimatedEmoji, boolean needStickers, boolean needGifs) {
        setAllowStickersAndGifs(needAnimatedEmoji, needStickers, needGifs, false);
    }

    public void setAllowStickersAndGifs(boolean needAnimatedEmoji, boolean needStickers, boolean needGifs, boolean waitingForKeyboardOpen) {
        if ((allowStickers != needStickers || allowGifs != needGifs) && emojiView != null) {
            if (emojiViewVisible && !waitingForKeyboardOpen) {
                removeEmojiViewAfterAnimation = true;
                hidePopup(false);
            } else {
                if (waitingForKeyboardOpen) {
                    openKeyboardInternal();
                }
            }
        }
        allowAnimatedEmoji = needAnimatedEmoji;
        allowStickers = needStickers;
        allowGifs = needGifs;
        if (emojiView != null) {
            emojiView.setAllow(allowStickers, allowGifs, true);
        }
        setEmojiButtonImage(false, !isPaused);
    }

    public void addEmojiToRecent(String code) {
        createEmojiView();
        emojiView.addEmojiToRecent(code);
    }

    public void setOpenGifsTabFirst() {
        createEmojiView();
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, true, true, false);
        emojiView.switchToGifRecent();
    }

    private final ValueAnimator.AnimatorUpdateListener topViewUpdateListener = animation -> {
        if (topView != null) {
            float v = (float) animation.getAnimatedValue();
            topViewEnterProgress = v;
            topView.setTranslationY(animatedTop + (1f - v) * topView.getLayoutParams().height);
            topLineView.setAlpha(v);
            topLineView.setTranslationY(animatedTop);
            if (parentFragment != null && parentFragment.mentionContainer != null) {
                parentFragment.mentionContainer.setTranslationY((1f - v) * topView.getLayoutParams().height);
            }
        }
    };

    public void showTopView(boolean animated, final boolean openKeyboard) {
        showTopView(animated, openKeyboard, false);
    }

    private void showTopView(boolean animated, final boolean openKeyboard, boolean skipAwait) {
        if (topView == null || topViewShowed || getVisibility() != VISIBLE) {
            if ((recordedAudioPanel == null || recordedAudioPanel.getVisibility() != VISIBLE) && (!forceShowSendButton && (replyingQuote == null && replyingMessageObject == null) || openKeyboard)) {
                openKeyboard();
            }
            return;
        }
        boolean openKeyboardInternal = (recordedAudioPanel == null || recordedAudioPanel.getVisibility() != VISIBLE) && (!forceShowSendButton && replyingQuote == null || openKeyboard) && (botReplyMarkup == null || editingMessageObject != null);
        if (!skipAwait && animated && openKeyboardInternal && !(keyboardVisible || isPopupShowing())) {
            openKeyboard();
            if (showTopViewRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(showTopViewRunnable);
            }
            AndroidUtilities.runOnUIThread(showTopViewRunnable = () -> {
                showTopView(true, false, true);
                showTopViewRunnable = null;
            }, 200);
            return;
        }
        needShowTopView = true;
        topViewShowed = true;
        if (allowShowTopView) {
            topView.setVisibility(VISIBLE);
            topLineView.setVisibility(VISIBLE);
            if (currentTopViewAnimation != null) {
                currentTopViewAnimation.cancel();
                currentTopViewAnimation = null;
            }
            resizeForTopView(true);
            if (animated) {
                currentTopViewAnimation = ValueAnimator.ofFloat(topViewEnterProgress, 1f);
                currentTopViewAnimation.addUpdateListener(topViewUpdateListener);
                currentTopViewAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            currentTopViewAnimation = null;
                        }
                        notificationsLocker.unlock();
                        if (parentFragment != null && parentFragment.mentionContainer != null) {
                            parentFragment.mentionContainer.setTranslationY(0);
                        }
                    }
                });
                currentTopViewAnimation.setDuration(ChatListItemAnimator.DEFAULT_DURATION + 20);
                currentTopViewAnimation.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
                currentTopViewAnimation.start();
                notificationsLocker.lock();
            } else {
                topViewEnterProgress = 1f;
                topView.setTranslationY(0);
                topLineView.setAlpha(1.0f);
            }
            if (openKeyboardInternal) {
                if (messageEditText != null) {
                    messageEditText.requestFocus();
                }
                openKeyboard();
            }
        }
    }

    public void onEditTimeExpired() {
        if (doneButton != null) {
            doneButton.setVisibility(View.GONE);
        }
    }

    public void showEditDoneProgress(final boolean show, boolean animated) {
        if (doneButton == null) {
            return;
        }
        if (doneButtonAnimation != null) {
            doneButtonAnimation.cancel();
        }
        if (show) {
            doneButton.setEnabled(false);
            doneButton.setLoading(true, SendButton.INFINITE_LOADING);
        } else {
            doneButton.setEnabled(true);
            doneButton.setLoading(false, SendButton.INFINITE_LOADING);
        }
    }

    public void hideTopView(final boolean animated) {
        if (topView == null || !topViewShowed) {
            return;
        }

        if (showTopViewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(showTopViewRunnable);
        }

        topViewShowed = false;
        needShowTopView = false;
        if (allowShowTopView) {
            if (currentTopViewAnimation != null) {
                currentTopViewAnimation.cancel();
                currentTopViewAnimation = null;
            }
            if (animated) {
                currentTopViewAnimation = ValueAnimator.ofFloat(topViewEnterProgress, 0);
                currentTopViewAnimation.addUpdateListener(topViewUpdateListener);
                currentTopViewAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            topView.setVisibility(GONE);
                            topLineView.setVisibility(GONE);
                            resizeForTopView(false);
                            currentTopViewAnimation = null;
                        }
                        if (parentFragment != null && parentFragment.mentionContainer != null) {
                            parentFragment.mentionContainer.setTranslationY(0);
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            currentTopViewAnimation = null;
                        }
                    }
                });
                currentTopViewAnimation.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
                currentTopViewAnimation.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
                currentTopViewAnimation.start();
            } else {
                topViewEnterProgress = 0f;
                topView.setVisibility(GONE);
                topLineView.setVisibility(GONE);
                topLineView.setAlpha(0.0f);
                resizeForTopView(false);
                topView.setTranslationY(topView.getLayoutParams().height);
            }
        }
    }

    public boolean isTopViewVisible() {
        return topView != null && topView.getVisibility() == VISIBLE;
    }

    public float topViewVisible() {
        return topViewEnterProgress;
    }

    public void onAdjustPanTransitionUpdate(float y, float progress, boolean keyboardVisible) {
//        if (botWebViewMenuContainer != null) {
//            botWebViewMenuContainer.setTranslationY(y);
//        }
    }

    public void onAdjustPanTransitionEnd() {
//        if (botWebViewMenuContainer != null) {
//            botWebViewMenuContainer.onPanTransitionEnd();
//        }
        if (onKeyboardClosed != null) {
            onKeyboardClosed.run();
            onKeyboardClosed = null;
        }
    }

    public void onAdjustPanTransitionStart(boolean keyboardVisible, int contentHeight) {
//        if (botWebViewMenuContainer != null) {
//            botWebViewMenuContainer.onPanTransitionStart(keyboardVisible, contentHeight);
//        }
        if (keyboardVisible && showTopViewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(showTopViewRunnable);
            showTopViewRunnable.run();
        }

        if (setTextFieldRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(setTextFieldRunnable);
            setTextFieldRunnable.run();
        }

//        if (keyboardVisible && messageEditText != null && messageEditText.hasFocus() && hasBotWebView() && botCommandsMenuIsShowing() && botWebViewMenuContainer != null) {
//            botWebViewMenuContainer.dismiss();
//        }
    }

    private void onWindowSizeChanged() {
        int size = sizeNotifierLayout.getHeight();
        if (!keyboardVisible) {
            size -= emojiPadding;
        }
        if (delegate != null) {
            delegate.onWindowSizeChanged(size);
        }
        if (topView != null) {
            if (size < dp(72) + ActionBar.getCurrentActionBarHeight()) {
                if (allowShowTopView) {
                    allowShowTopView = false;
                    if (needShowTopView) {
                        topView.setVisibility(GONE);
                        topLineView.setVisibility(GONE);
                        topLineView.setAlpha(0.0f);
                        resizeForTopView(false);
                        topViewEnterProgress = 0f;
                        topView.setTranslationY(topView.getLayoutParams().height);
                    }
                }
            } else {
                if (!allowShowTopView) {
                    allowShowTopView = true;
                    if (needShowTopView) {
                        topView.setVisibility(VISIBLE);
                        topLineView.setVisibility(VISIBLE);
                        topLineView.setAlpha(1.0f);
                        resizeForTopView(true);
                        topViewEnterProgress = 1f;
                        topView.setTranslationY(0);
                    }
                }
            }
        }
    }

    private void resizeForTopView(boolean show) {
        LayoutParams layoutParams = (LayoutParams) textFieldContainer.getLayoutParams();
        layoutParams.topMargin = dp(2) + (show ? topView.getLayoutParams().height : 0);
        textFieldContainer.setLayoutParams(layoutParams);
        setMinimumHeight(dp(51) + (show ? topView.getLayoutParams().height : 0));
        if (stickersExpanded) {
            if (searchingType == 0) {
                setStickersExpanded(false, true, false);
            } else {
                checkStickresExpandHeight();
            }
        }
    }

    public void onDestroy() {
        destroyed = true;
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordPaused);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordResumed);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.audioRouteChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messageReceivedByServer2);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.sendingMessagesChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.audioRecordTooShort);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateBotMenuButton);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdatePremiumGiftFieldIcon);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (emojiView != null) {
            emojiView.onDestroy();
        }
        if (updateSlowModeRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateSlowModeRunnable);
            updateSlowModeRunnable = null;
        }
        if (wakeLock != null) {
            try {
                wakeLock.release();
                wakeLock = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (sizeNotifierLayout != null) {
            sizeNotifierLayout.setDelegate(null);
        }
        if (senderSelectPopupWindow != null) {
            senderSelectPopupWindow.setPauseNotifications(false);
            senderSelectPopupWindow.dismiss();
        }
    }

    public void checkChannelRights() {
        if (parentFragment == null) {
            return;
        }
        updateRecordButton(parentFragment.getCurrentChat(), parentFragment.getCurrentUserInfo());
    }

    public void updateRecordButton(TLRPC.Chat chat, TLRPC.UserFull userFull) {
        emojiButtonRestricted = false;
        stickersEnabled = true;
        sendPlainEnabled = true;
        sendRoundEnabled = true;
        sendVoiceEnabled = true;

        if (chat != null) {
            audioVideoButtonContainer.setAlpha(ChatObject.canSendVoice(chat) || (ChatObject.canSendRoundVideo(chat) && hasRecordVideo)? 1.0f : 0.5f);

            stickersEnabled = ChatObject.canSendStickers(chat);
            sendPlainEnabled = ChatObject.canSendPlain(chat);
            emojiButtonRestricted = !stickersEnabled && !sendPlainEnabled;
            emojiButtonAlpha = emojiButtonRestricted ? 0.5f : 1.0f;
            updateEmojiButtonParams();
            if (!emojiButtonRestricted) {
                if (emojiView != null) {
                    emojiView.setStickersBanned(!sendPlainEnabled, !stickersEnabled, -dialog_id);
                }
            }
            sendRoundEnabled = ChatObject.canSendRoundVideo(chat);
            sendVoiceEnabled = ChatObject.canSendVoice(chat);
        } else if (userFull != null) {
            userInfo = userFull;
            audioVideoButtonContainer.setAlpha(userFull.voice_messages_forbidden ? 0.5f : 1.0f);
        }
        updateFieldHint(false);
        boolean currentModeVideo = isInVideoMode;
        if (!sendRoundEnabled && currentModeVideo) {
            currentModeVideo = false;
        }
        if (!sendVoiceEnabled && !currentModeVideo) {
            if (hasRecordVideo) {
                currentModeVideo = true;
            } else {
                currentModeVideo = false;
            }
        }
        setRecordVideoButtonVisible(currentModeVideo, false);
    }

    public void onBeginHide() {
        if (focusRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(focusRunnable);
            focusRunnable = null;
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (senderSelectPopupWindow != null){
            senderSelectPopupWindow.setPauseNotifications(false);
            senderSelectPopupWindow.dismiss();
        }
    }

    private Runnable hideKeyboardRunnable;

    public void onPause() {
        isPaused = true;
        if (senderSelectPopupWindow != null) {
            senderSelectPopupWindow.setPauseNotifications(false);
            senderSelectPopupWindow.dismiss();
        }
        if (keyboardVisible) {
            showKeyboardOnResume = true;
        }
        AndroidUtilities.runOnUIThread(hideKeyboardRunnable = () -> {
            if (parentFragment == null || parentFragment.isLastFragment()) {
                closeKeyboard();
            }
            hideKeyboardRunnable = null;
        }, 500);
    }

    public void onResume() {
        isPaused = false;
        if (hideKeyboardRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideKeyboardRunnable);
            hideKeyboardRunnable = null;
        }

        if (hasBotWebView() && botCommandsMenuIsShowing()) {
            return;
        }

        int visibility = getVisibility();
        if (showKeyboardOnResume && !BaseFragment.hasSheets(parentFragment)) {
            showKeyboardOnResume = false;
            if (delegate != null) {
                delegate.onKeyboardRequested();
            }
            if (searchingType == 0 && messageEditText != null) {
                messageEditText.requestFocus();
            }
            AndroidUtilities.showKeyboard(messageEditText);
            if (!AndroidUtilities.usingHardwareInput && !keyboardVisible && !AndroidUtilities.isInMultiwindow) {
                waitingForKeyboardOpen = true;
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        messageEditTextEnabled = visibility == VISIBLE;
        if (messageEditText != null) {
            messageEditText.setEnabled(messageEditTextEnabled);
        }
    }

    public void setDialogId(long id, int account) {
        dialog_id = id;
        if (currentAccount != account) {
            notificationsLocker.unlock();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStarted);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordPaused);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordResumed);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStartError);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStopped);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordProgressChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.audioDidSent);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.audioRouteChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messageReceivedByServer2);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.sendingMessagesChanged);
            currentAccount = account;
            accountInstance = AccountInstance.getInstance(currentAccount);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStarted);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordPaused);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordResumed);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStartError);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStopped);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordProgressChanged);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.audioDidSent);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.audioRouteChanged);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messageReceivedByServer2);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.sendingMessagesChanged);
        }

        sendPlainEnabled = true;
        if (DialogObject.isChatDialog(dialog_id)) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-dialog_id);
            sendPlainEnabled = ChatObject.canSendPlain(chat);
        }

        updateScheduleButton(false);
        updateGiftButton(false);
        checkRoundVideo();
        checkChannelRights();
        updateFieldHint(false);
        if (messageEditText != null) {
            updateSendAsButton(parentFragment != null && parentFragment.getFragmentBeginToShow());
        }
    }

    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        info = chatInfo;
        if (emojiView != null) {
            emojiView.setChatInfo(info);
        }
        if (slowModeButton != null) {
            slowModeButton.setPremiumMode(ChatObject.isPossibleRemoveChatRestrictionsByBoosts(chatInfo));
        }
        if (ChatObject.isIgnoredChatRestrictionsForBoosters(chatInfo)) {
            return;
        }
        setSlowModeTimer(chatInfo.slowmode_next_send_date);
    }

    public void checkRoundVideo() {
        if (hasRecordVideo) {
            return;
        }
        if (attachLayout == null || Build.VERSION.SDK_INT < 18) {
            hasRecordVideo = false;
            setRecordVideoButtonVisible(false, false);
            return;
        }
        hasRecordVideo = true;
        sendRoundEnabled = true;
        sendVoiceEnabled = true;
        boolean isChannel = false;
        if (DialogObject.isChatDialog(dialog_id)) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-dialog_id);
            isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            if (isChannel && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages)) {
                hasRecordVideo = false;
            }
            sendRoundEnabled = ChatObject.canSendRoundVideo(chat);
            sendVoiceEnabled = ChatObject.canSendVoice(chat);
        }
        if (!SharedConfig.inappCamera) {
            hasRecordVideo = false;
        }
        boolean currentModeVideo = false;
        if (hasRecordVideo) {
            if (SharedConfig.hasCameraCache) {
                CameraController.getInstance().initCamera(null);
            }
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            currentModeVideo = preferences.getBoolean(isChannel ? "currentModeVideoChannel" : "currentModeVideo", isChannel);
        }

        if (!sendRoundEnabled && currentModeVideo) {
            currentModeVideo = false;
        }
        if (!sendVoiceEnabled && !currentModeVideo) {
            if (hasRecordVideo) {
                currentModeVideo = true;
            } else {
                currentModeVideo = false;
            }
        }
        setRecordVideoButtonVisible(currentModeVideo, false);
    }

    public boolean isInVideoMode() {
        return isInVideoMode;
    }

    public boolean hasRecordVideo() {
        return hasRecordVideo;
    }

    public MessageObject getReplyingMessageObject() {
        return replyingMessageObject;
    }

    public void updateFieldHint(boolean animated) {
        if (messageEditText == null) {
            return;
        }
        if (overrideHint != null) {
            messageEditText.setHintText(overrideHint, animated);
            messageEditText.setHintText2(overrideHint2, animated);
            return;
        }
        if (!sendPlainEnabled && !isEditingMessage()) {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(" d " + getString("PlainTextRestrictedHint", R.string.PlainTextRestrictedHint));
            spannableStringBuilder.setSpan(new ColoredImageSpan(R.drawable.msg_mini_lock3), 1, 2, 0);
            messageEditText.setHintText(spannableStringBuilder, animated);
            messageEditText.setText(null);
            messageEditText.setEnabled(false);
            messageEditText.setInputType(EditorInfo.IME_ACTION_NONE);
            return;
        } else {
            messageEditText.setEnabled(true);
            if (messageEditText.getInputType() != commonInputType) {
                messageEditText.setInputType(commonInputType);
            } else {

            }
        }
        if (parentFragment != null && parentFragment.getChatMode() == ChatActivity.MODE_QUICK_REPLIES) {
            if (QuickRepliesController.GREETING.equalsIgnoreCase(parentFragment.quickReplyShortcut)) {
                messageEditText.setHintText(getString(R.string.BusinessGreetingEnter));
            } else if (QuickRepliesController.AWAY.equalsIgnoreCase(parentFragment.quickReplyShortcut)) {
                messageEditText.setHintText(getString(R.string.BusinessAwayEnter));
            } else {
                messageEditText.setHintText(getString(R.string.BusinessRepliesEnter));
            }
        } else if (isEditingBusinessLink()) {
            messageEditText.setHintText(getString(R.string.BusinessLinksEnter));
        } else if (replyingMessageObject != null && replyingMessageObject.messageOwner.reply_markup != null && !TextUtils.isEmpty(replyingMessageObject.messageOwner.reply_markup.placeholder)) {
            messageEditText.setHintText(replyingMessageObject.messageOwner.reply_markup.placeholder, animated);
        } else if (editingMessageObject != null) {
            messageEditText.setHintText(editingCaption ? getString("Caption", R.string.Caption) : getString("TypeMessage", R.string.TypeMessage));
        } else if (botKeyboardViewVisible && botButtonsMessageObject != null && botButtonsMessageObject.messageOwner.reply_markup != null && !TextUtils.isEmpty(botButtonsMessageObject.messageOwner.reply_markup.placeholder)) {
            messageEditText.setHintText(botButtonsMessageObject.messageOwner.reply_markup.placeholder, animated);
        } else if (parentFragment != null && parentFragment.isForumInViewAsMessagesMode()) {
            if (replyingTopMessage != null && replyingTopMessage.replyToForumTopic != null && replyingTopMessage.replyToForumTopic.title != null) {
                messageEditText.setHintText(LocaleController.formatString("TypeMessageIn", R.string.TypeMessageIn, replyingTopMessage.replyToForumTopic.title), animated);
            } else {
                TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(parentFragment.getCurrentChat().id, 1);
                if (topic != null && topic.title != null) {
                    messageEditText.setHintText(LocaleController.formatString("TypeMessageIn", R.string.TypeMessageIn, topic.title), animated);
                } else {
                    messageEditText.setHintText(getString("TypeMessage", R.string.TypeMessage), animated);
                }
            }
        } else {
            boolean isChannel = false;
            boolean anonymously = false;
            if (DialogObject.isChatDialog(dialog_id)) {
                TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-dialog_id);
                TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(-dialog_id);
                isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
                anonymously = !isChannel && ChatObject.getSendAsPeerId(chat, chatFull) == -dialog_id;
            }
            if (anonymously) {
                messageEditText.setHintText(getString("SendAnonymously", R.string.SendAnonymously));
            } else {
                if (parentFragment != null && parentFragment.isThreadChat() && !parentFragment.isTopic) {
                    if (parentFragment.isReplyChatComment()) {
                        messageEditText.setHintText(getString("Comment", R.string.Comment));
                    } else {
                        messageEditText.setHintText(getString("Reply", R.string.Reply));
                    }
                } else if (isChannel) {
                    if (silent) {
                        messageEditText.setHintText(getString("ChannelSilentBroadcast", R.string.ChannelSilentBroadcast), animated);
                    } else {
                        messageEditText.setHintText(getString("ChannelBroadcast", R.string.ChannelBroadcast), animated);
                    }
                } else {
                    messageEditText.setHintText(getString("TypeMessage", R.string.TypeMessage));
                }
            }
        }
    }

    public void setReplyingMessageObject(MessageObject messageObject, ChatActivity.ReplyQuote quote) {
        setReplyingMessageObject(messageObject, quote, null);
    }

    public void setReplyingMessageObject(MessageObject messageObject, ChatActivity.ReplyQuote quote, MessageObject replyingTopMessage) {
        boolean animated = parentFragment != null && parentFragment.isForumInViewAsMessagesMode() && this.replyingTopMessage != replyingTopMessage;
        if (messageObject != null) {
            if (botMessageObject == null && botButtonsMessageObject != replyingMessageObject) {
                botMessageObject = botButtonsMessageObject;
            }
            replyingMessageObject = messageObject;
            replyingQuote = quote;
            this.replyingTopMessage = replyingTopMessage;
            if (!(parentFragment != null && parentFragment.isTopic && parentFragment.getThreadMessage() == replyingMessageObject)) {
                setButtons(replyingMessageObject, true);
            }
        } else if (replyingMessageObject == botButtonsMessageObject) {
            replyingMessageObject = null;
            this.replyingTopMessage = null;
            replyingQuote = null;
            setButtons(botMessageObject, false);
            botMessageObject = null;
        } else {
            replyingMessageObject = null;
            replyingQuote = null;
            this.replyingTopMessage = null;
        }
        TL_stories.StoryItem storyItem = delegate != null ? delegate.getReplyToStory() : null;
        MediaController.getInstance().setReplyingMessage(messageObject, getThreadMessage(), storyItem);
        updateFieldHint(animated);
    }

    public void setWebPage(TLRPC.WebPage webPage, boolean searchWebPages) {
        messageWebPage = webPage;
        messageWebPageSearch = searchWebPages;
    }

    public boolean isMessageWebPageSearchEnabled() {
        return messageWebPageSearch;
    }

    private void hideRecordedAudioPanel(boolean wasSent) {
        if (recordPannelAnimation != null && recordPannelAnimation.isRunning()) {
            return;
        }

        audioToSendPath = null;
        audioToSend = null;
        audioToSendMessageObject = null;
        videoToSendMessageObject = null;
        if (videoTimelineView != null) {
            videoTimelineView.destroy();
        }

        if (audioVideoSendButton != null) {
            audioVideoSendButton.setVisibility(View.VISIBLE);
        }

        if (wasSent) {
            attachButton.setAlpha(0f);
            attachButton.setScaleX(0);
            attachButton.setScaleY(0);

            emojiButtonAlpha = 0;
            emojiButtonScale = 0;
            updateEmojiButtonParams();

            recordPannelAnimation = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_ALPHA, emojiButtonRestricted ? 0.5f : 1.0f));
            animators.add(ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_SCALE, 1.0f));
            animators.add(ObjectAnimator.ofFloat(recordDeleteImageView, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(recordDeleteImageView, View.SCALE_X, 0.0f));
            animators.add(ObjectAnimator.ofFloat(recordDeleteImageView, View.SCALE_Y, 0.0f));

            animators.add(ObjectAnimator.ofFloat(recordedAudioPanel, View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(attachButton, View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(attachButton, View.SCALE_X, 1.0f));
            animators.add(ObjectAnimator.ofFloat(attachButton, View.SCALE_Y, 1.0f));
            animators.add(ObjectAnimator.ofFloat(messageEditText, View.ALPHA, 1f));
            animators.add(ObjectAnimator.ofFloat(messageEditText, MESSAGE_TEXT_TRANSLATION_X, 0));
            if (controlsView != null) {
                animators.add(ObjectAnimator.ofFloat(controlsView, View.ALPHA, 0));
                controlsView.hideHintView();
            }
            recordPannelAnimation.playTogether(animators);

            if (botCommandsMenuButton != null) {
                botCommandsMenuButton.setAlpha(0f);
                botCommandsMenuButton.setScaleY(0);
                botCommandsMenuButton.setScaleX(0);

                recordPannelAnimation.playTogether(
                        ObjectAnimator.ofFloat(botCommandsMenuButton, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_Y, 1.0f)
                );
            }
            recordPannelAnimation.setDuration(150);
            recordPannelAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (recordedAudioPanel != null) {
                        recordedAudioPanel.setVisibility(GONE);
                    }
                    if (messageEditText != null) {
                        messageEditText.requestFocus();
                    }
                    isRecordingStateChanged();
                }
            });

        } else {
            if (recordDeleteImageView != null) {
                recordDeleteImageView.playAnimation();
            }
            AnimatorSet exitAnimation = new AnimatorSet();

            ArrayList<Animator> animators = new ArrayList<>();
            if (isInVideoMode()) {
                animators.add(ObjectAnimator.ofFloat(videoTimelineView, View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(videoTimelineView, View.TRANSLATION_X, -dp(20)));
                animators.add(ObjectAnimator.ofFloat(messageEditText, MESSAGE_TEXT_TRANSLATION_X, 0));
                if (controlsView != null) {
                    animators.add(ObjectAnimator.ofFloat(controlsView, View.ALPHA, 0.0f));
                    controlsView.hideHintView();
                }
                exitAnimation.playTogether(animators);
                if (emojiButtonPaddingAlpha == 1f) {
                    exitAnimation.playTogether(ObjectAnimator.ofFloat(messageEditText, View.ALPHA, 1f));
                } else {
                    ObjectAnimator messageEditTextAniamtor = ObjectAnimator.ofFloat(messageEditText, View.ALPHA, 1);
                    messageEditTextAniamtor.setStartDelay(750);
                    messageEditTextAniamtor.setDuration(200);
                    exitAnimation.playTogether(messageEditTextAniamtor);
                }
            } else {
                if (messageEditText != null && emojiButtonPaddingAlpha == 1f) {
                    messageEditText.setAlpha(1f);
                    messageTextTranslationX = 0;
                    updateMessageTextParams();
                } else {
                    messageTextTranslationX = 0;
                    updateMessageTextParams();
                    ObjectAnimator messageEditTextAniamtor = ObjectAnimator.ofFloat(messageEditText, View.ALPHA, 1);
                    messageEditTextAniamtor.setStartDelay(750);
                    messageEditTextAniamtor.setDuration(200);
                    exitAnimation.playTogether(messageEditTextAniamtor);
                }
                animators.add(ObjectAnimator.ofFloat(recordedAudioSeekBar, View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(recordedAudioPlayButton, View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(recordedAudioBackground, View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(recordedAudioTimeTextView, View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(recordedAudioSeekBar, View.TRANSLATION_X, -dp(20)));
                animators.add(ObjectAnimator.ofFloat(recordedAudioPlayButton, View.TRANSLATION_X, -dp(20)));
                animators.add(ObjectAnimator.ofFloat(recordedAudioBackground, View.TRANSLATION_X, -dp(20)));
                animators.add(ObjectAnimator.ofFloat(recordedAudioTimeTextView, View.TRANSLATION_X, -dp(20)));
                if (controlsView != null) {
                    animators.add(ObjectAnimator.ofFloat(controlsView, View.ALPHA, 0));
                    controlsView.hideHintView();
                }
                exitAnimation.playTogether(animators);
            }
            exitAnimation.setDuration(200);

            AnimatorSet attachIconAnimator;
            if (attachButton != null) {
                attachButton.setAlpha(0f);
                attachButton.setScaleX(0);
                attachButton.setScaleY(0);

                attachIconAnimator = new AnimatorSet();
                attachIconAnimator.playTogether(
                        ObjectAnimator.ofFloat(attachButton, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(attachButton, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(attachButton, View.SCALE_Y, 1.0f)
                );
                attachIconAnimator.setDuration(150);
            } else {
                attachIconAnimator = null;
            }

            emojiButtonAlpha = emojiButtonScale = 0;
            updateEmojiButtonParams();

            AnimatorSet iconsEndAnimator = new AnimatorSet();

            iconsEndAnimator.playTogether(
                    ObjectAnimator.ofFloat(recordDeleteImageView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(recordDeleteImageView, View.SCALE_X, 0.0f),
                    ObjectAnimator.ofFloat(recordDeleteImageView, View.SCALE_Y, 0.0f),
                    ObjectAnimator.ofFloat(recordDeleteImageView, View.ALPHA, 0.0f),

                    ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_ALPHA, emojiButtonRestricted ? 0.5f : 1.0f),
                    ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_SCALE, 1.0f)
            );

            if (botCommandsMenuButton != null) {
                botCommandsMenuButton.setAlpha(0f);
                botCommandsMenuButton.setScaleY(0);
                botCommandsMenuButton.setScaleX(0);

                iconsEndAnimator.playTogether(
                        ObjectAnimator.ofFloat(botCommandsMenuButton, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_Y, 1.0f)
                );
            }

            iconsEndAnimator.setDuration(150);
            iconsEndAnimator.setStartDelay(600);

            recordPannelAnimation = new AnimatorSet();
            if (attachIconAnimator != null) {
                recordPannelAnimation.playTogether(
                        exitAnimation,
                        attachIconAnimator,
                        iconsEndAnimator
                );
            } else {
                recordPannelAnimation.playTogether(
                        exitAnimation,
                        iconsEndAnimator
                );
            }

            recordPannelAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (videoTimelineView != null) {
                        videoTimelineView.setVisibility(GONE);
                    }
                    if (recordedAudioSeekBar != null) {
                        recordedAudioSeekBar.setVisibility(GONE);
                    }
                    if (recordedAudioPlayButton != null) {
                        recordedAudioPlayButton.setVisibility(GONE);
                    }
                    if (recordedAudioBackground != null) {
                        recordedAudioBackground.setVisibility(GONE);
                    }
                    if (recordedAudioTimeTextView != null) {
                        recordedAudioTimeTextView.setVisibility(GONE);
                    }
                    transformToSeekbar = 0;
                    isRecordingStateChanged();
                    hideRecordedAudioPanelInternal();
                    if (recordCircle != null) {
                        recordCircle.setSendButtonInvisible();
                    }
                }
            });
        }
        if (recordPannelAnimation != null) {
            recordPannelAnimation.start();
        }
        if (controlsView != null) {
            controlsView.invalidate();
        }
    }

    private void hideRecordedAudioPanelInternal() {
        audioToSendPath = null;
        audioToSend = null;
        audioToSendMessageObject = null;
        videoToSendMessageObject = null;
        if (videoTimelineView != null) {
            videoTimelineView.destroy();
        }
        if (recordedAudioSeekBar != null) {
            recordedAudioSeekBar.setAlpha(1f);
            recordedAudioSeekBar.setTranslationX(0);
        }
        if (recordedAudioPlayButton != null) {
            recordedAudioPlayButton.setAlpha(1f);
            recordedAudioPlayButton.setTranslationX(0);
        }
        if (recordedAudioBackground != null) {
            recordedAudioBackground.setAlpha(1f);
            recordedAudioBackground.setTranslationX(0);
        }
        if (recordedAudioTimeTextView != null) {
            recordedAudioTimeTextView.setAlpha(1f);
            recordedAudioTimeTextView.setTranslationX(0);
        }
        if (videoTimelineView != null) {
            videoTimelineView.setAlpha(1f);
            videoTimelineView.setTranslationX(0);
        }
        if (messageEditText != null) {
            messageEditText.setAlpha(1f);
            messageTextTranslationX = 0;
            updateMessageTextParams();
            messageEditText.requestFocus();
        }
        if (recordedAudioPanel != null) {
            recordedAudioPanel.setVisibility(GONE);
        }
        isRecordingStateChanged();
    }

    private void sendMessage() {
        if (isInScheduleMode()) {
            AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), new AlertsCreator.ScheduleDatePickerDelegate() {
                @Override
                public void didSelectDate(boolean notify, int scheduleDate) {
                    if (messageSendPreview != null) {
                        messageSendPreview.dismiss(true);
                        messageSendPreview = null;
                    }
                    sendMessageInternal(notify, scheduleDate, true);
                }
            }, resourcesProvider);
        } else {
            sendMessageInternal(true, 0, true);
        }
    }

    private boolean premiumEmojiBulletin = true;
    private void sendMessageInternal(boolean notify, int scheduleDate, boolean allowConfirm) {
        if (slowModeTimer == Integer.MAX_VALUE && !isInScheduleMode()) {
            if (delegate != null) {
                delegate.scrollToSendingMessage();
            }
            return;
        }
        if (parentFragment != null) {
            TLRPC.Chat chat = parentFragment.getCurrentChat();
            TLRPC.User user = parentFragment.getCurrentUser();
            if (user != null || ChatObject.isChannel(chat) && chat.megagroup || !ChatObject.isChannel(chat)) {
                MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("silent_" + dialog_id, !notify).commit();
            }
        }
        if (stickersExpanded) {
            setStickersExpanded(false, true, false);
            if (searchingType != 0 && emojiView != null) {
                emojiView.closeSearch(false);
                emojiView.hideSearchKeyboard();
            }
        }
        if (allowConfirm && showConfirmAlert(() -> {
            sendMessageInternal(notify, scheduleDate, false);
        })) {
            return;
        }
        if (videoToSendMessageObject != null) {
            delegate.needStartRecordVideo(4, notify, scheduleDate, voiceOnce ? 0x7FFFFFFF : 0, effectId);
            sendButton.setEffect(effectId = 0);
            hideRecordedAudioPanel(true);
            checkSendButton(true);
            AndroidUtilities.runOnUIThread(() -> {
                if (recordCircle != null) {
                    recordCircle.setSendButtonInvisible();
                }
            }, 100);
            millisecondsRecorded = 0;
            return;
        } else if (audioToSend != null) {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null && playing == audioToSendMessageObject) {
                MediaController.getInstance().cleanupPlayer(true, true);
            }
            MediaController.getInstance().cleanRecording(false);
            MediaDataController.getInstance(currentAccount).pushDraftVoiceMessage(dialog_id, parentFragment != null && parentFragment.isTopic ? parentFragment.getTopicId() : 0, null);
            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(audioToSend, null, audioToSendPath, dialog_id, replyingMessageObject, getThreadMessage(), null, null, null, null, notify, scheduleDate, voiceOnce ? 0x7FFFFFFF : 0, null, null, false);
            params.quick_reply_shortcut = parentFragment != null ? parentFragment.quickReplyShortcut : null;
            params.quick_reply_shortcut_id = parentFragment != null ? parentFragment.getQuickReplyId() : 0;
            params.effect_id = effectId;
            sendButton.setEffect(effectId = 0);
            if (!delegate.hasForwardingMessages()) {
                MessageObject.SendAnimationData sendAnimationData = new MessageObject.SendAnimationData();
                sendAnimationData.fromPreview = System.currentTimeMillis() - sentFromPreview < 200;
                params.sendAnimationData = sendAnimationData;
            }
            applyStoryToSendMessageParams(params);
            SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
            if (delegate != null) {
                delegate.onMessageSend(null, notify, scheduleDate);
            }
            hideRecordedAudioPanel(true);
            checkSendButton(true);
            AndroidUtilities.runOnUIThread(() -> {
                if (recordCircle != null) {
                    recordCircle.setSendButtonInvisible();
                }
            }, 100);
            millisecondsRecorded = 0;
            return;
        }
        CharSequence message = messageEditText == null ? "" : messageEditText.getTextToUse();
        if (parentFragment != null) {
            TLRPC.Chat chat = parentFragment.getCurrentChat();
            if (chat != null && chat.slowmode_enabled && !ChatObject.hasAdminRights(chat)) {
                if (message.length() > accountInstance.getMessagesController().maxMessageLength) {
                    AlertsCreator.showSimpleAlert(parentFragment, getString("Slowmode", R.string.Slowmode), getString("SlowmodeSendErrorTooLong", R.string.SlowmodeSendErrorTooLong), resourcesProvider);
                    return;
                } else if (forceShowSendButton && message.length() > 0) {
                    AlertsCreator.showSimpleAlert(parentFragment, getString("Slowmode", R.string.Slowmode), getString("SlowmodeSendError", R.string.SlowmodeSendError), resourcesProvider);
                    return;
                }
            }
        }
        if (checkPremiumAnimatedEmoji(currentAccount, dialog_id, parentFragment, null, message)) {
            return;
        }
        if (processSendingText(message, notify, scheduleDate)) {
            if (delegate.hasForwardingMessages() || (scheduleDate != 0 && !isInScheduleMode()) || isInScheduleMode()) {
                if (messageEditText != null) {
                    messageEditText.setText("");
                }
                if (delegate != null) {
                    delegate.onMessageSend(message, notify, scheduleDate);
                }
            } else {
                messageTransitionIsRunning = false;
                AndroidUtilities.runOnUIThread(moveToSendStateRunnable = () -> {
                    moveToSendStateRunnable = null;
                    hideTopView(true);
                    if (messageEditText != null) {
                        messageEditText.setText("");
                    }
                    if (delegate != null) {
                        delegate.onMessageSend(message, notify, scheduleDate);
                    }
                }, 200);
            }
            lastTypingTimeSend = 0;
        } else if (forceShowSendButton) {
            if (delegate != null) {
                delegate.onMessageSend(null, notify, scheduleDate);
            }
        }
    }

    protected boolean showConfirmAlert(Runnable onConfirmed) {
        return false;
    }

    public static boolean checkPremiumAnimatedEmoji(int currentAccount, long dialogId, BaseFragment parentFragment, FrameLayout container, CharSequence message) {
        if (message == null || parentFragment == null) {
            return false;
        }
        final boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();
        if (!isPremium && UserConfig.getInstance(currentAccount).getClientUserId() != dialogId && message instanceof Spanned) {
            AnimatedEmojiSpan[] animatedEmojis = ((Spanned) message).getSpans(0, message.length(), AnimatedEmojiSpan.class);
            if (animatedEmojis != null) {
                for (int i = 0; i < animatedEmojis.length; ++i) {
                    if (animatedEmojis[i] != null) {
                        TLRPC.Document emoji = animatedEmojis[i].document;
                        if (emoji == null) {
                            emoji = AnimatedEmojiDrawable.findDocument(currentAccount, animatedEmojis[i].getDocumentId());
                        }
                        long documentId = animatedEmojis[i].getDocumentId();
                        if (emoji == null) {
                            ArrayList<TLRPC.TL_messages_stickerSet> sets1 = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_EMOJIPACKS);
                            for (TLRPC.TL_messages_stickerSet set : sets1) {
                                if (set != null && set.documents != null && !set.documents.isEmpty()) {
                                    for (TLRPC.Document document : set.documents) {
                                        if (document.id == documentId) {
                                            emoji = document;
                                            break;
                                        }
                                    }
                                }
                                if (emoji != null) {
                                    break;
                                }
                            }
                        }
                        if (emoji == null) {
                            ArrayList<TLRPC.StickerSetCovered> sets2 = MediaDataController.getInstance(currentAccount).getFeaturedEmojiSets();
                            for (TLRPC.StickerSetCovered set : sets2) {
                                if (set != null && set.covers != null && !set.covers.isEmpty()) {
                                    for (TLRPC.Document document : set.covers) {
                                        if (document.id == documentId) {
                                            emoji = document;
                                            break;
                                        }
                                    }
                                }
                                if (emoji != null) {
                                    break;
                                }
                                ArrayList<TLRPC.Document> documents = null;
                                if (set instanceof TLRPC.TL_stickerSetFullCovered) {
                                    documents = ((TLRPC.TL_stickerSetFullCovered) set).documents;
                                } else if (set instanceof TLRPC.TL_stickerSetNoCovered && set.set != null) {
                                    TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
                                    inputStickerSetID.id = set.set.id;
                                    TLRPC.TL_messages_stickerSet fullSet = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSetID, true);
                                    if (fullSet != null && fullSet.documents != null) {
                                        documents = fullSet.documents;
                                    }
                                }
                                if (documents != null && !documents.isEmpty()) {
                                    for (TLRPC.Document document : documents) {
                                        if (document.id == documentId) {
                                            emoji = document;
                                            break;
                                        }
                                    }
                                }
                                if (emoji != null) {
                                    break;
                                }
                            }
                        }

                        if (emoji != null) {
                            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
                            if (chatFull != null && chatFull.emojiset != null) {
                                TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getGroupStickerSetById(chatFull.emojiset);
                                if (stickerSet != null) {
                                    for (TLRPC.Document document : stickerSet.documents) {
                                        if (document.id == documentId) {
                                            return false;
                                        }
                                    }
                                }
                            }
                        }

                        if (emoji == null || !MessageObject.isFreeEmoji(emoji)) {
                            BulletinFactory.of(parentFragment)
                                .createEmojiBulletin(
                                    emoji,
                                    AndroidUtilities.replaceTags(getString("UnlockPremiumEmojiHint", R.string.UnlockPremiumEmojiHint)),
                                    getString("PremiumMore", R.string.PremiumMore),
                                    () -> {
                                        if (parentFragment != null) {
                                            new PremiumFeatureBottomSheet(parentFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false).show();
                                        } else if (parentFragment.getContext() instanceof LaunchActivity) {
                                            ((LaunchActivity) parentFragment.getContext()).presentFragment(new PremiumPreviewFragment(null));
                                        }
                                    }
                                ).show();
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void showCaptionLimitBulletin() {
        if (parentFragment == null || !ChatObject.isChannelAndNotMegaGroup(parentFragment.getCurrentChat())) {
            return;
        }
        BulletinFactory.of(parentFragment).createCaptionLimitBulletin(MessagesController.getInstance(currentAccount).captionLengthLimitPremium, () -> {
            if (parentFragment != null) {
                parentFragment.presentFragment(new PremiumPreviewFragment("caption_limit"));
            }
        }).show();
    }

    private static class BusinessLinkPresetMessage {
        public String text;
        public ArrayList<TLRPC.MessageEntity> entities;
    }

    private BusinessLinkPresetMessage calculateBusinessLinkPresetMessage() {
        CharSequence text = messageEditText == null ? "" : messageEditText.getTextToUse();
        text = AndroidUtilities.getTrimmedString(text);

        CharSequence[] message = new CharSequence[]{text};
        ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(message, true);
        text = message[0];
        for (int a = 0, N = entities.size(); a < N; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset + entity.length > text.length()) {
                entity.length = text.length() - entity.offset;
            }
        }

        BusinessLinkPresetMessage presetMessage = new BusinessLinkPresetMessage();
        presetMessage.text = text.toString();
        presetMessage.entities = entities;
        return presetMessage;
    }

    public boolean businessLinkHasChanges() {
        BusinessLinkPresetMessage currentMessage = calculateBusinessLinkPresetMessage();
        if (!TextUtils.equals(currentMessage.text, lastSavedBusinessLinkMessage.text)) {
            return true;
        }

        if (!MediaDataController.entitiesEqual(lastSavedBusinessLinkMessage.entities, currentMessage.entities)) {
            return true;
        }

        return false;
    }

    private void saveBusinessLink() {
        if (!isEditingBusinessLink()) {
            return;
        }

        if (currentLimit - codePointCount < 0) {
            if (captionLimitView != null) {
                AndroidUtilities.shakeViewSpring(captionLimitView, 3.5f);
                try {
                    captionLimitView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {
                }
            }
            return;
        }

        BusinessLinkPresetMessage message = calculateBusinessLinkPresetMessage();
        lastSavedBusinessLinkMessage = message;
        BusinessLinksController.getInstance(currentAccount).editLinkMessage(editingBusinessLink.link, message.text, message.entities, () -> {
            BulletinFactory.of(parentFragment).createSuccessBulletin(getString(R.string.BusinessLinkSaved)).show();
        });
    }

    public void doneEditingMessage() {
        if (editingMessageObject == null) {
            return;
        }
        if (currentLimit - codePointCount < 0) {
            if (captionLimitView != null) {
                AndroidUtilities.shakeViewSpring(captionLimitView, 3.5f);
                try {
                    captionLimitView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {}
            }

            if (!MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && MessagesController.getInstance(currentAccount).captionLengthLimitPremium > codePointCount) {
                showCaptionLimitBulletin();
            }
            return;
        }
        if (searchingType != 0) {
            setSearchingTypeInternal(0, true);
            emojiView.closeSearch(false);

            if (stickersExpanded) {
                setStickersExpanded(false, true, false);
                waitingForKeyboardOpenAfterAnimation = true;
                AndroidUtilities.runOnUIThread(() -> {
                    waitingForKeyboardOpenAfterAnimation = false;
                    openKeyboardInternal();
                }, 200);
            }
        }
        CharSequence text = messageEditText == null ? "" : messageEditText.getTextToUse();
        if (editingMessageObject == null || editingMessageObject.type != MessageObject.TYPE_EMOJIS) {
            text = AndroidUtilities.getTrimmedString(text);
        }
        CharSequence[] message = new CharSequence[]{text};
        if (TextUtils.isEmpty(message[0]) && (editingMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage || editingMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty || editingMessageObject.messageOwner.media == null)) {
            AndroidUtilities.shakeViewSpring(messageEditText, -3);
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            return;
        }
        ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(message, supportsSendingNewEntities());
        if (!TextUtils.equals(message[0], editingMessageObject.messageText) || entities != null && !entities.isEmpty() || !editingMessageObject.messageOwner.entities.isEmpty() || editingMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            editingMessageObject.editingMessage = message[0];
            editingMessageObject.editingMessageEntities = entities;
            editingMessageObject.editingMessageSearchWebPage = messageWebPageSearch;
            if (parentFragment != null && parentFragment.getCurrentChat() != null && (editingMessageObject.type == MessageObject.TYPE_TEXT || editingMessageObject.type == MessageObject.TYPE_EMOJIS) && !ChatObject.canSendEmbed(parentFragment.getCurrentChat())) {
                editingMessageObject.editingMessageSearchWebPage = false;
                editingMessageObject.messageOwner.flags &=~ 512;
                editingMessageObject.messageOwner.media = null;
            } else if (parentFragment != null && parentFragment.messagePreviewParams != null) {
                if (parentFragment.foundWebPage instanceof TLRPC.TL_webPagePending) {
                    editingMessageObject.editingMessageSearchWebPage = false;
                    if (editingMessageObject.type == MessageObject.TYPE_TEXT || editingMessageObject.type == MessageObject.TYPE_EMOJIS) {
                        editingMessageObject.messageOwner.media = new TLRPC.TL_messageMediaEmpty();
                        editingMessageObject.messageOwner.flags |= 512;
                    }
                } else if (parentFragment.messagePreviewParams.webpage != null) {
                    editingMessageObject.editingMessageSearchWebPage = false;
                    editingMessageObject.messageOwner.flags |= 512;
                    editingMessageObject.messageOwner.media = new TLRPC.TL_messageMediaWebPage();
                    editingMessageObject.messageOwner.media.webpage = parentFragment.messagePreviewParams.webpage;
                } else {
                    editingMessageObject.editingMessageSearchWebPage = false;
                    if (editingMessageObject.type == MessageObject.TYPE_TEXT || editingMessageObject.type == MessageObject.TYPE_EMOJIS) {
                        editingMessageObject.messageOwner.flags |= 512;
                        editingMessageObject.messageOwner.media = new TLRPC.TL_messageMediaEmpty();
                    }
                }
                editingMessageObject.messageOwner.invert_media = parentFragment.messagePreviewParams.webpageTop;
                if (parentFragment.messagePreviewParams.hasMedia && editingMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
                    editingMessageObject.messageOwner.media.force_small_media = parentFragment.messagePreviewParams.webpageSmall;
                    editingMessageObject.messageOwner.media.force_large_media = !parentFragment.messagePreviewParams.webpageSmall;
                }
            } else {
                editingMessageObject.editingMessageSearchWebPage = false;
                if (editingMessageObject.type == MessageObject.TYPE_TEXT || editingMessageObject.type == MessageObject.TYPE_EMOJIS) {
                    editingMessageObject.messageOwner.flags |= 512;
                    editingMessageObject.messageOwner.media = new TLRPC.TL_messageMediaEmpty();
                }
            }
            SendMessagesHelper.getInstance(currentAccount).editMessage(editingMessageObject, null, null, null, null, null, false, editingMessageObject.hasMediaSpoilers(), null);
        }
        setEditingMessageObject(null, null, false);
    }

    public boolean processSendingText(CharSequence text, boolean notify, int scheduleDate) {
        if (replyingQuote != null && parentFragment != null && replyingQuote.outdated) {
            parentFragment.showQuoteMessageUpdate();
            return false;
        }
        int[] emojiOnly = new int[1];
        Emoji.parseEmojis(text, emojiOnly);
        boolean hasOnlyEmoji = emojiOnly[0] > 0;
        if (!hasOnlyEmoji) {
            text = AndroidUtilities.getTrimmedString(text);
        }
        boolean supportsNewEntities = supportsSendingNewEntities();
        int maxLength = accountInstance.getMessagesController().maxMessageLength;
        if (text.length() != 0) {
            if (delegate != null && parentFragment != null && (scheduleDate != 0) == parentFragment.isInScheduleMode()) {
                delegate.prepareMessageSending();
            }
            int end;
            int start = 0;
            do {
                int whitespaceIndex = -1;
                int dotIndex = -1;
                int tabIndex = -1;
                int enterIndex = -1;
                if (text.length() > start + maxLength) {
                    int i = start + maxLength - 1;
                    int k = 0;
                    while (i > start && k < 300) {
                        char c = text.charAt(i);
                        char c2 = i > 0 ? text.charAt(i - 1) : ' ';
                        if (c == '\n' && c2 == '\n') {
                            tabIndex = i;
                            break;
                        } else if (c == '\n') {
                            enterIndex = i;
                        } else if (dotIndex < 0 && Character.isWhitespace(c) && c2 == '.') {
                            dotIndex = i;
                        } else if (whitespaceIndex < 0 && Character.isWhitespace(c)) {
                            whitespaceIndex = i;
                        }
                        i--;
                        k++;
                    }
                }
                end = Math.min(start + maxLength, text.length());
                if (tabIndex > 0) {
                    end = tabIndex;
                } else if (enterIndex > 0) {
                    end = enterIndex;
                } else if (dotIndex > 0) {
                    end = dotIndex;
                } else if (whitespaceIndex > 0) {
                    end = whitespaceIndex;
                }

                CharSequence part = text.subSequence(start, end);
                if (!hasOnlyEmoji) {
                    part = AndroidUtilities.getTrimmedString(part);
                }
                CharSequence[] message = new CharSequence[]{ part };
                ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(message, supportsNewEntities);
                MessageObject.SendAnimationData sendAnimationData = null;

                if (!delegate.hasForwardingMessages()) {
                    sendAnimationData = new MessageObject.SendAnimationData();
                    sendAnimationData.fromPreview = System.currentTimeMillis() - sentFromPreview < 200;
                    sendAnimationData.width = sendAnimationData.height = dp(22);
                    if (messageEditText != null) {
                        messageEditText.getLocationInWindow(location);
                        sendAnimationData.x = location[0] + dp(11);
                        sendAnimationData.y = location[1] + dp(8 + 11);
                    } else {
                        sendAnimationData.x = dp(48 + 11);
                        sendAnimationData.y = AndroidUtilities.displaySize.y - dp(8 + 11);
                    }
                } else if (messageSendPreview != null) {
                    sendAnimationData = new MessageObject.SendAnimationData();
                    sendAnimationData.fromPreview = System.currentTimeMillis() - sentFromPreview < 200;
                }

                boolean updateStickersOrder = false;
                updateStickersOrder = SendMessagesHelper.checkUpdateStickersOrder(text);

                MessageObject replyToTopMsg = getThreadMessage();
                if (replyToTopMsg == null && replyingTopMessage != null) {
                    replyToTopMsg = replyingTopMessage;
                }
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(message[0].toString(), dialog_id, replyingMessageObject, replyToTopMsg, messageWebPage, messageWebPageSearch, entities, null, null, notify, scheduleDate, sendAnimationData, updateStickersOrder);
                params.quick_reply_shortcut = parentFragment != null ? parentFragment.quickReplyShortcut : null;
                params.quick_reply_shortcut_id = parentFragment != null ? parentFragment.getQuickReplyId() : 0;
                params.effect_id = effectId;
                sendButton.setEffect(effectId = 0);
                applyStoryToSendMessageParams(params);
                params.invert_media = parentFragment != null && parentFragment.messagePreviewParams != null && parentFragment.messagePreviewParams.webpageTop;
                if (parentFragment != null && parentFragment.getCurrentChat() != null && !ChatObject.canSendEmbed(parentFragment.getCurrentChat())) {
                    params.searchLinks = false;
                    params.mediaWebPage = null;
                } else if (messageWebPage instanceof TLRPC.TL_webPagePending) {
                    params.searchLinks = true;
                    params.mediaWebPage = null;
                } else if (messageWebPage != null) {
                    params.mediaWebPage = new TLRPC.TL_messageMediaWebPage();
                    params.mediaWebPage.webpage = messageWebPage;
                    params.mediaWebPage.force_large_media = parentFragment != null && parentFragment.messagePreviewParams != null && !parentFragment.messagePreviewParams.webpageSmall;
                    params.mediaWebPage.force_small_media = parentFragment != null && parentFragment.messagePreviewParams != null && parentFragment.messagePreviewParams.webpageSmall;
                }
                if (parentFragment != null) {
                    parentFragment.editingMessageObject = null;
                    parentFragment.foundWebPage = null;
                    if (parentFragment.messagePreviewParams != null) {
                        parentFragment.messagePreviewParams.updateLink(currentAccount, null, "", null, null, null);
                    }
                    setWebPage(null, true);
                    parentFragment.fallbackFieldPanel();
                }
                SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                start = end + 1;
            } while (end != text.length());
            return true;
        }
        return false;
    }

    private void applyStoryToSendMessageParams(SendMessagesHelper.SendMessageParams params) {
        if (delegate != null) {
            params.replyToStoryItem = delegate.getReplyToStory();
            params.replyQuote = delegate.getReplyQuote();
        }
    }

    private boolean supportsSendingNewEntities() {
        TLRPC.EncryptedChat encryptedChat = parentFragment != null ? parentFragment.getCurrentEncryptedChat() : null;
        return encryptedChat == null || AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 101;
    }

    private void checkSendButton(boolean animated) {
        if (editingMessageObject != null || recordingAudioVideo) {
            return;
        }
        if (isPaused) {
            animated = false;
        }
        CharSequence message = messageEditText == null ? "" : AndroidUtilities.getTrimmedString(messageEditText.getTextToUse());
        if (slowModeTimer > 0 && slowModeTimer != Integer.MAX_VALUE && !isInScheduleMode()) {
            if (slowModeButton.getVisibility() != VISIBLE) {
                if (animated) {
                    if (runningAnimationType == 5) {
                        return;
                    }
                    if (runningAnimation != null) {
                        runningAnimation.cancel();
                        runningAnimation = null;
                    }
                    if (runningAnimation2 != null) {
                        runningAnimation2.cancel();
                        runningAnimation2 = null;
                    }

                    if (attachLayout != null) {
                        runningAnimation2 = new AnimatorSet();
                        ArrayList<Animator> animators = new ArrayList<>();
                        animators.add(ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_ALPHA, 0.0f));
                        animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 0.0f));
                        scheduleButtonHidden = false;
                        boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                        if (hasScheduled) {
                            createScheduledButton();
                        }
                        if (scheduledButton != null) {
                            scheduledButton.setScaleY(1.0f);
                            if (hasScheduled) {
                                scheduledButton.setVisibility(VISIBLE);
                                scheduledButton.setTag(1);
                                scheduledButton.setPivotX(dp(48));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48) - dp(giftButton != null && giftButton.getVisibility() == VISIBLE ? 48 : 0)));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1.0f));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 1.0f));
                            } else {
                                scheduledButton.setTranslationX(dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48) - dp(giftButton != null && giftButton.getVisibility() == VISIBLE ? 48 : 0));
                                scheduledButton.setAlpha(1.0f);
                                scheduledButton.setScaleX(1.0f);
                            }
                        }
                        runningAnimation2.playTogether(animators);
                        runningAnimation2.setDuration(100);
                        runningAnimation2.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (animation.equals(runningAnimation2)) {
                                    attachLayout.setVisibility(GONE);
                                    runningAnimation2 = null;
                                }
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                if (animation.equals(runningAnimation2)) {
                                    runningAnimation2 = null;
                                }
                            }
                        });
                        runningAnimation2.start();
                        updateFieldRight(0);
                        if (delegate != null && getVisibility() == VISIBLE) {
                            delegate.onAttachButtonHidden();
                        }
                    }

                    runningAnimationType = 5;
                    runningAnimation = new AnimatorSet();

                    ArrayList<Animator> animators = new ArrayList<>();
                    if (audioVideoButtonContainer.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 0.0f));
                    }
                    if (expandStickersButton != null && expandStickersButton.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.ALPHA, 0.0f));
                    }
                    if (sendButton.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, View.ALPHA, 0.0f));
                    }
                    if (cancelBotButton.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.ALPHA, 0.0f));
                    }
                    animators.add(ObjectAnimator.ofFloat(slowModeButton, View.SCALE_X, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(slowModeButton, View.SCALE_Y, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(slowModeButton, View.ALPHA, 1.0f));
                    setSlowModeButtonVisible(true);
                    runningAnimation.playTogether(animators);
                    runningAnimation.setDuration(150);
                    runningAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation.equals(runningAnimation)) {
                                sendButton.setVisibility(GONE);
                                cancelBotButton.setVisibility(GONE);
                                audioVideoButtonContainer.setVisibility(GONE);
                                if (expandStickersButton != null) {
                                    expandStickersButton.setVisibility(GONE);
                                }
                                runningAnimation = null;
                                runningAnimationType = 0;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            if (animation.equals(runningAnimation)) {
                                runningAnimation = null;
                            }
                        }
                    });
                    runningAnimation.start();
                } else {
                    slowModeButton.setScaleX(1.0f);
                    slowModeButton.setScaleY(1.0f);
                    slowModeButton.setAlpha(1.0f);
                    setSlowModeButtonVisible(true);

                    audioVideoButtonContainer.setScaleX(0.1f);
                    audioVideoButtonContainer.setScaleY(0.1f);
                    audioVideoButtonContainer.setAlpha(0.0f);
                    audioVideoButtonContainer.setVisibility(GONE);

                    sendButton.setScaleX(0.1f);
                    sendButton.setScaleY(0.1f);
                    sendButton.setAlpha(0.0f);
                    sendButton.setVisibility(GONE);

                    cancelBotButton.setScaleX(0.1f);
                    cancelBotButton.setScaleY(0.1f);
                    cancelBotButton.setAlpha(0.0f);
                    cancelBotButton.setVisibility(GONE);

                    if (expandStickersButton != null && expandStickersButton.getVisibility() == VISIBLE) {
                        expandStickersButton.setScaleX(0.1f);
                        expandStickersButton.setScaleY(0.1f);
                        expandStickersButton.setAlpha(0.0f);
                        expandStickersButton.setVisibility(GONE);
                    }
                    if (attachLayout != null) {
                        attachLayout.setVisibility(GONE);
                        if (delegate != null && getVisibility() == VISIBLE) {
                            delegate.onAttachButtonHidden();
                        }
                        updateFieldRight(0);
                    }
                    scheduleButtonHidden = false;
                    final boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                    if (hasScheduled) {
                        createScheduledButton();
                    }
                    if (scheduledButton != null) {
                        if (hasScheduled) {
                            scheduledButton.setVisibility(VISIBLE);
                            scheduledButton.setTag(1);
                        }
                        scheduledButton.setTranslationX(dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48) - dp(giftButton != null && giftButton.getVisibility() == VISIBLE ? 48 : 0));
                        scheduledButton.setAlpha(1.0f);
                        scheduledButton.setScaleX(1.0f);
                        scheduledButton.setScaleY(1.0f);
                    }
                }
            }
        } else if (message.length() > 0 || forceShowSendButton || audioToSend != null || videoToSendMessageObject != null || slowModeTimer == Integer.MAX_VALUE && !isInScheduleMode()) {
            final String caption = messageEditText == null ? null : messageEditText.getCaption();
            boolean showBotButton = caption != null && (sendButton.getVisibility() == VISIBLE || expandStickersButton != null && expandStickersButton.getVisibility() == VISIBLE);
            boolean showSendButton = caption == null && (cancelBotButton.getVisibility() == VISIBLE || expandStickersButton != null && expandStickersButton.getVisibility() == VISIBLE);
            int color;
            if (slowModeTimer == Integer.MAX_VALUE && !isInScheduleMode()) {
                color = getThemedColor(Theme.key_chat_messagePanelIcons);
            } else {
                color = getThemedColor(Theme.key_chat_messagePanelSend);
            }

            if (color != sendButtonBackgroundColor) {
                sendButtonBackgroundColor = color;
                Theme.setSelectorDrawableColor(sendButton.getBackground(), Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)), true);
            }

            if (audioVideoButtonContainer.getVisibility() == VISIBLE || slowModeButton.getVisibility() == VISIBLE || showBotButton || showSendButton) {
                if (animated) {
                    if (runningAnimationType == 1 && caption == null || runningAnimationType == 3 && caption != null) {
                        return;
                    }
                    if (runningAnimation != null) {
                        runningAnimation.cancel();
                        runningAnimation = null;
                    }
                    if (runningAnimation2 != null) {
                        runningAnimation2.cancel();
                        runningAnimation2 = null;
                    }

                    if (attachLayout != null) {
                        runningAnimation2 = new AnimatorSet();
                        ArrayList<Animator> animators = new ArrayList<>();
                        animators.add(ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_ALPHA, 0.0f));
                        animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 0.0f));
                        boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                        scheduleButtonHidden = true;
                        if (scheduledButton != null) {
                            scheduledButton.setScaleY(1.0f);
                            if (hasScheduled) {
                                scheduledButton.setTag(null);
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 0.0f));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 0.0f));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48) - dp(giftButton != null && giftButton.getVisibility() == VISIBLE ? 48 : 0)));
                            } else {
                                scheduledButton.setAlpha(0.0f);
                                scheduledButton.setScaleX(0.0f);
                                scheduledButton.setTranslationX(dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48) - dp(giftButton != null && giftButton.getVisibility() == VISIBLE ? 48 : 0));
                            }
                        }
                        runningAnimation2.playTogether(animators);
                        runningAnimation2.setDuration(100);
                        runningAnimation2.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (animation.equals(runningAnimation2)) {
                                    attachLayout.setVisibility(GONE);
                                    if (hasScheduled && scheduledButton != null) {
                                        scheduledButton.setVisibility(GONE);
                                    }
                                    runningAnimation2 = null;
                                }
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                if (animation.equals(runningAnimation2)) {
                                    runningAnimation2 = null;
                                }
                            }
                        });
                        runningAnimation2.start();
                        updateFieldRight(0);
                        if (delegate != null && getVisibility() == VISIBLE) {
                            delegate.onAttachButtonHidden();
                        }
                    }

                    runningAnimation = new AnimatorSet();

                    ArrayList<Animator> animators = new ArrayList<>();
                    if (audioVideoButtonContainer.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 0.0f));
                    }
                    if (expandStickersButton != null && expandStickersButton.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.ALPHA, 0.0f));
                    }
                    if (slowModeButton.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(slowModeButton, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(slowModeButton, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(slowModeButton, View.ALPHA, 0.0f));
                    }
                    if (showBotButton) {
                        animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, View.ALPHA, 0.0f));
                    } else if (showSendButton) {
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.ALPHA, 0.0f));
                    }
                    if (caption != null) {
                        runningAnimationType = 3;
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_X, 1.0f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_Y, 1.0f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.ALPHA, 1.0f));
                        cancelBotButton.setVisibility(VISIBLE);
                    } else {
                        runningAnimationType = 1;
                        animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_X, 1.0f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_Y, 1.0f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, View.ALPHA, 1.0f));
                        sendButton.setVisibility(VISIBLE);
                    }

                    runningAnimation.playTogether(animators);
                    runningAnimation.setDuration(150);
                    runningAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation.equals(runningAnimation)) {
                                if (caption != null) {
                                    cancelBotButton.setVisibility(VISIBLE);
                                    sendButton.setVisibility(GONE);
                                } else {
                                    sendButton.setVisibility(VISIBLE);
                                    cancelBotButton.setVisibility(GONE);
                                }
                                audioVideoButtonContainer.setVisibility(GONE);
                                if (expandStickersButton != null) {
                                    expandStickersButton.setVisibility(GONE);
                                }
                                setSlowModeButtonVisible(false);
                                runningAnimation = null;
                                runningAnimationType = 0;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            if (animation.equals(runningAnimation)) {
                                runningAnimation = null;
                            }
                        }
                    });
                    runningAnimation.start();
                } else {
                    audioVideoButtonContainer.setScaleX(0.1f);
                    audioVideoButtonContainer.setScaleY(0.1f);
                    audioVideoButtonContainer.setAlpha(0.0f);
                    audioVideoButtonContainer.setVisibility(GONE);
                    if (slowModeButton.getVisibility() == VISIBLE) {
                        slowModeButton.setScaleX(0.1f);
                        slowModeButton.setScaleY(0.1f);
                        slowModeButton.setAlpha(0.0f);
                        setSlowModeButtonVisible(false);
                    }
                    if (caption != null) {
                        sendButton.setScaleX(0.1f);
                        sendButton.setScaleY(0.1f);
                        sendButton.setAlpha(0.0f);
                        sendButton.setVisibility(GONE);
                        cancelBotButton.setScaleX(1.0f);
                        cancelBotButton.setScaleY(1.0f);
                        cancelBotButton.setAlpha(1.0f);
                        cancelBotButton.setVisibility(VISIBLE);
                    } else {
                        cancelBotButton.setScaleX(0.1f);
                        cancelBotButton.setScaleY(0.1f);
                        cancelBotButton.setAlpha(0.0f);
                        sendButton.setVisibility(VISIBLE);
                        sendButton.setScaleX(1.0f);
                        sendButton.setScaleY(1.0f);
                        sendButton.setAlpha(1.0f);
                        cancelBotButton.setVisibility(GONE);
                    }
                    if (expandStickersButton != null && expandStickersButton.getVisibility() == VISIBLE) {
                        expandStickersButton.setScaleX(0.1f);
                        expandStickersButton.setScaleY(0.1f);
                        expandStickersButton.setAlpha(0.0f);
                        expandStickersButton.setVisibility(GONE);
                    }
                    if (attachLayout != null) {
                        attachLayout.setVisibility(GONE);
                        if (delegate != null && getVisibility() == VISIBLE) {
                            delegate.onAttachButtonHidden();
                        }
                        updateFieldRight(0);
                    }
                    scheduleButtonHidden = true;
                    if (scheduledButton != null) {
                        if (delegate != null && delegate.hasScheduledMessages()) {
                            scheduledButton.setVisibility(GONE);
                            scheduledButton.setTag(null);
                        }
                        scheduledButton.setAlpha(0.0f);
                        scheduledButton.setScaleX(0.0f);
                        scheduledButton.setScaleY(1.0f);
                        scheduledButton.setTranslationX(dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48) - dp(giftButton != null && giftButton.getVisibility() == VISIBLE ? 48 : 0));
                    }
                }
            }
        } else if (emojiView != null && emojiViewVisible && (stickersTabOpen || emojiTabOpen && searchingType == 2) && !AndroidUtilities.isInMultiwindow) {
            if (animated) {
                if (runningAnimationType == 4) {
                    return;
                }

                if (runningAnimation != null) {
                    runningAnimation.cancel();
                    runningAnimation = null;
                }
                if (runningAnimation2 != null) {
                    runningAnimation2.cancel();
                    runningAnimation2 = null;
                }

                if (attachLayout != null && recordInterfaceState == 0) {
                    attachLayout.setVisibility(VISIBLE);
                    runningAnimation2 = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    animators.add(ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_ALPHA, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 1.0f));
                    boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                    scheduleButtonHidden = false;
                    if (hasScheduled) {
                        createScheduledButton();
                    }
                    if (scheduledButton != null) {
                        scheduledButton.setScaleY(1.0f);
                        if (hasScheduled) {
                            scheduledButton.setVisibility(VISIBLE);
                            scheduledButton.setTag(1);
                            scheduledButton.setPivotX(dp(48));
                            animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, 0));
                        } else {
                            scheduledButton.setAlpha(1.0f);
                            scheduledButton.setScaleX(1.0f);
                            scheduledButton.setTranslationX(0);
                        }
                    }
                    runningAnimation2.playTogether(animators);
                    runningAnimation2.setDuration(100);
                    runningAnimation2.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation.equals(runningAnimation2)) {
                                runningAnimation2 = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            if (animation.equals(runningAnimation2)) {
                                runningAnimation2 = null;
                            }
                        }
                    });
                    runningAnimation2.start();
                    updateFieldRight(1);
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                }

                createExpandStickersButton();
                expandStickersButton.setVisibility(VISIBLE);
                runningAnimation = new AnimatorSet();
                runningAnimationType = 4;

                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.SCALE_X, 1.0f));
                animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.SCALE_Y, 1.0f));
                animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.ALPHA, 1.0f));
                if (cancelBotButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_X, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_Y, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.ALPHA, 0.0f));
                } else if (audioVideoButtonContainer.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_X, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_Y, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 0.0f));
                } else if (slowModeButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(slowModeButton, View.SCALE_X, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(slowModeButton, View.SCALE_Y, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(slowModeButton, View.ALPHA, 0.0f));
                } else {
                    animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_X, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_Y, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, View.ALPHA, 0.0f));
                }

                runningAnimation.playTogether(animators);
                runningAnimation.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                runningAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(runningAnimation)) {
                            sendButton.setVisibility(GONE);
                            cancelBotButton.setVisibility(GONE);
                            setSlowModeButtonVisible(false);
                            audioVideoButtonContainer.setVisibility(GONE);
                            expandStickersButton.setVisibility(VISIBLE);
                            runningAnimation = null;
                            runningAnimationType = 0;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (animation.equals(runningAnimation)) {
                            runningAnimation = null;
                        }
                    }
                });
                runningAnimation.start();
            } else {
                slowModeButton.setScaleX(0.1f);
                slowModeButton.setScaleY(0.1f);
                slowModeButton.setAlpha(0.0f);
                setSlowModeButtonVisible(false);
                sendButton.setScaleX(0.1f);
                sendButton.setScaleY(0.1f);
                sendButton.setAlpha(0.0f);
                sendButton.setVisibility(GONE);
                cancelBotButton.setScaleX(0.1f);
                cancelBotButton.setScaleY(0.1f);
                cancelBotButton.setAlpha(0.0f);
                cancelBotButton.setVisibility(GONE);
                audioVideoButtonContainer.setScaleX(0.1f);
                audioVideoButtonContainer.setScaleY(0.1f);
                audioVideoButtonContainer.setAlpha(0.0f);
                audioVideoButtonContainer.setVisibility(GONE);
                createExpandStickersButton();
                expandStickersButton.setScaleX(1.0f);
                expandStickersButton.setScaleY(1.0f);
                expandStickersButton.setAlpha(1.0f);
                expandStickersButton.setVisibility(VISIBLE);
                if (attachLayout != null) {
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                    attachLayout.setVisibility(VISIBLE);
                    updateFieldRight(1);
                }
                scheduleButtonHidden = false;
                final boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                if (hasScheduled) {
                    createScheduledButton();
                }
                if (scheduledButton != null) {
                    if (hasScheduled) {
                        scheduledButton.setVisibility(VISIBLE);
                        scheduledButton.setTag(1);
                    }
                    scheduledButton.setAlpha(1.0f);
                    scheduledButton.setScaleX(1.0f);
                    scheduledButton.setScaleY(1.0f);
                    scheduledButton.setTranslationX(0);
                }
            }
        } else if (
            sendButton.getVisibility() == VISIBLE ||
            cancelBotButton.getVisibility() == VISIBLE ||
            expandStickersButton != null && expandStickersButton.getVisibility() == VISIBLE ||
            slowModeButton.getVisibility() == VISIBLE
        ) {
            if (animated) {
                if (runningAnimationType == 2) {
                    return;
                }

                if (runningAnimation != null) {
                    runningAnimation.cancel();
                    runningAnimation = null;
                }
                if (runningAnimation2 != null) {
                    runningAnimation2.cancel();
                    runningAnimation2 = null;
                }

                if (attachLayout != null) {
                    if (attachLayout.getVisibility() != View.VISIBLE) {
                        attachLayout.setVisibility(VISIBLE);
                        attachLayoutAlpha = 0f;
                        updateAttachLayoutParams();
                        attachLayout.setScaleX(0f);
                    }
                    runningAnimation2 = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    animators.add(ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_ALPHA, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 1.0f));
                    boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                    scheduleButtonHidden = false;
                    if (hasScheduled) {
                        createScheduledButton();
                    }
                    if (scheduledButton != null) {
                        if (hasScheduled) {
                            scheduledButton.setVisibility(VISIBLE);
                            scheduledButton.setTag(1);
                            scheduledButton.setPivotX(dp(48));
                            animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, giftButton != null && giftButton.getVisibility() == VISIBLE ? -dp(48) : 0));
                            if (notifyButton != null && notifyButton.getVisibility() == View.VISIBLE) {
                                notifyButton.setVisibility(View.GONE);
                            }
                        } else {
                            scheduledButton.setAlpha(1.0f);
                            scheduledButton.setScaleX(1.0f);
                            scheduledButton.setScaleY(1.0f);
                            scheduledButton.setTranslationX(0);
                        }
                    }
                    runningAnimation2.playTogether(animators);
                    runningAnimation2.setDuration(100);
                    runningAnimation2.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation.equals(runningAnimation2)) {
                                runningAnimation2 = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            if (animation.equals(runningAnimation2)) {
                                runningAnimation2 = null;
                            }
                        }
                    });
                    runningAnimation2.start();
                    updateFieldRight(1);
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                }

                audioVideoButtonContainer.setVisibility(VISIBLE);
                runningAnimation = new AnimatorSet();
                runningAnimationType = 2;

                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_X, 1.0f));
                animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_Y, 1.0f));

                float alpha = 1.0f;
                TLRPC.Chat chat = parentFragment == null ? null : parentFragment.getCurrentChat();
                TLRPC.UserFull userFull = parentFragment == null ? userInfo : parentFragment.getCurrentUserInfo();
                if (chat != null) {
                    alpha = (ChatObject.canSendVoice(chat) || ChatObject.canSendRoundVideo(chat)) ? 1.0f : 0.5f;
                } else if (userFull != null) {
                    alpha = userFull.voice_messages_forbidden ? 0.5f : 1.0f;
                }

                animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, alpha));
                if (cancelBotButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_X, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_Y, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.ALPHA, 0.0f));
                } else if (expandStickersButton != null && expandStickersButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.SCALE_X, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.SCALE_Y, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(expandStickersButton, View.ALPHA, 0.0f));
                } else if (slowModeButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(slowModeButton, View.SCALE_X, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(slowModeButton, View.SCALE_Y, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(slowModeButton, View.ALPHA, 0.0f));
                } else {
                    animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_X, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, View.SCALE_Y, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, View.ALPHA, 0.0f));
                }

                runningAnimation.playTogether(animators);
                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(runningAnimation)) {
                            setSlowModeButtonVisible(false);
                            runningAnimation = null;
                            runningAnimationType = 0;

                            if (audioVideoButtonContainer != null) {
                                audioVideoButtonContainer.setVisibility(VISIBLE);
                            }
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (animation.equals(runningAnimation)) {
                            runningAnimation = null;
                        }
                    }
                });
                runningAnimation.start();
            } else {
                slowModeButton.setScaleX(0.1f);
                slowModeButton.setScaleY(0.1f);
                slowModeButton.setAlpha(0.0f);
                setSlowModeButtonVisible(false);
                sendButton.setScaleX(0.1f);
                sendButton.setScaleY(0.1f);
                sendButton.setAlpha(0.0f);
                sendButton.setVisibility(GONE);
                cancelBotButton.setScaleX(0.1f);
                cancelBotButton.setScaleY(0.1f);
                cancelBotButton.setAlpha(0.0f);
                cancelBotButton.setVisibility(GONE);
                if (expandStickersButton != null) {
                    expandStickersButton.setScaleX(0.1f);
                    expandStickersButton.setScaleY(0.1f);
                    expandStickersButton.setAlpha(0.0f);
                    expandStickersButton.setVisibility(GONE);
                }
                audioVideoButtonContainer.setScaleX(1.0f);
                audioVideoButtonContainer.setScaleY(1.0f);
                audioVideoButtonContainer.setAlpha(1.0f);
                audioVideoButtonContainer.setVisibility(VISIBLE);
                if (attachLayout != null) {
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                    attachLayoutAlpha = 1.0f;
                    updateAttachLayoutParams();
                    attachLayout.setScaleX(1.0f);
                    attachLayout.setVisibility(VISIBLE);
                    updateFieldRight(1);
                }
                scheduleButtonHidden = false;
                final boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                if (hasScheduled) {
                    createScheduledButton();
                }
                if (scheduledButton != null) {
                    if (delegate != null && delegate.hasScheduledMessages()) {
                        scheduledButton.setVisibility(VISIBLE);
                        scheduledButton.setTag(1);
                    }
                    scheduledButton.setAlpha(1.0f);
                    scheduledButton.setScaleX(1.0f);
                    scheduledButton.setScaleY(1.0f);
                    scheduledButton.setTranslationX(0);
                }
            }
        }
    }

    private void setSlowModeButtonVisible(boolean visible) {
        slowModeButton.setVisibility(visible ? VISIBLE : GONE);
        int padding = visible ? dp(slowModeButton.isPremiumMode ? 26 : 16) : 0;
        if (messageEditText != null && messageEditText.getPaddingRight() != padding) {
            messageEditText.setPadding(0, dp(11), padding, dp(12));
        }
    }

    private void updateFieldRight(int attachVisible) {
        if (messageEditText == null || editingMessageObject != null) {
            return;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messageEditText.getLayoutParams();
        int oldRightMargin = layoutParams.rightMargin;
        if (attachVisible == 1) {
            if (botButton != null && botButton.getVisibility() == VISIBLE && scheduledButton != null && scheduledButton.getVisibility() == VISIBLE && attachLayout != null && attachLayout.getVisibility() == VISIBLE) {
                layoutParams.rightMargin = dp(146);
            } else if (botButton != null && botButton.getVisibility() == VISIBLE || notifyButton != null && notifyButton.getVisibility() == VISIBLE || scheduledButton != null && scheduledButton.getTag() != null) {
                layoutParams.rightMargin = dp(98);
            } else {
                layoutParams.rightMargin = dp(50);
            }
        } else if (attachVisible == 2) {
            if (layoutParams.rightMargin != dp(2)) {
                if (botButton != null && botButton.getVisibility() == VISIBLE && scheduledButton != null && scheduledButton.getVisibility() == VISIBLE && attachLayout != null && attachLayout.getVisibility() == VISIBLE) {
                    layoutParams.rightMargin = dp(146);
                } else if (botButton != null && botButton.getVisibility() == VISIBLE || notifyButton != null && notifyButton.getVisibility() == VISIBLE || scheduledButton != null && scheduledButton.getTag() != null) {
                    layoutParams.rightMargin = dp(98);
                } else {
                    layoutParams.rightMargin = dp(50);
                }
            }
        } else {
            if (scheduledButton != null && scheduledButton.getTag() != null) {
                layoutParams.rightMargin = dp(50);
            } else {
                layoutParams.rightMargin = dp(2);
            }
        }
        if (oldRightMargin != layoutParams.rightMargin) {
            messageEditText.setLayoutParams(layoutParams);
        }
    }

    public void startMessageTransition() {
        if (moveToSendStateRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(moveToSendStateRunnable);
            messageTransitionIsRunning = true;
            moveToSendStateRunnable.run();
            moveToSendStateRunnable = null;
        }
    }

    public boolean canShowMessageTransition() {
        boolean r = moveToSendStateRunnable != null && (messageSendPreview == null || !messageSendPreview.isShowing()) && System.currentTimeMillis() - sentFromPreview > 300;
        sentFromPreview = -1;
        return r;
    }

    private int lastRecordState;
    protected void updateRecordInterface(int recordState, boolean animated) {
        if (moveToSendStateRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(moveToSendStateRunnable);
            moveToSendStateRunnable = null;
        }
        if (recordCircle != null) {
            recordCircle.voiceEnterTransitionInProgress = false;
        }
        if (recordingAudioVideo) {
            if (recordInterfaceState == 1) {
                lastRecordState = recordState;
                return;
            }
            final boolean fromPause = lastRecordState == RECORD_STATE_PREPARING;

            if (!fromPause) {
                voiceOnce = false;
                if (controlsView != null) {
                    controlsView.periodDrawable.setValue(1, false, false);
                }
                MediaDataController.getInstance(currentAccount).toggleDraftVoiceOnce(dialog_id, parentFragment != null && parentFragment.isTopic ? parentFragment.getTopicId() : 0, voiceOnce);
                millisecondsRecorded = 0;
            }

            createRecordAudioPanel();
            recordInterfaceState = 1;
            if (emojiView != null) {
                emojiView.setEnabled(false);
            }
            try {
                if (wakeLock == null) {
                    PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "telegram:audio_record_lock");
                    wakeLock.acquire();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.lockOrientation(parentActivity);

            if (delegate != null) {
                delegate.needStartRecordAudio(0);
            }

            if (runningAnimationAudio != null) {
                runningAnimationAudio.cancel();
            }

            if (recordPannelAnimation != null) {
                recordPannelAnimation.cancel();
            }

            createRecordPanel();
            if (recordPanel != null) {
                recordPanel.setVisibility(VISIBLE);
            }
            createRecordCircle();
            if (recordCircle != null) {
                recordCircle.voiceEnterTransitionInProgress = false;
                recordCircle.setVisibility(VISIBLE);
                recordCircle.setAmplitude(0);
            }
            if (controlsView != null) {
                controlsView.setVisibility(VISIBLE);
            }
            if (recordDot != null) {
                recordDot.resetAlpha();

                recordDot.setScaleX(0);
                recordDot.setScaleY(0);
                recordDot.enterAnimation = true;
            }

            runningAnimationAudio = new AnimatorSet();

            recordTimerView.setTranslationX(dp(20));
            recordTimerView.setAlpha(0);
            if (lastRecordState != RECORD_STATE_PREPARING) {
                slideText.setTranslationX(dp(20));
                slideText.setAlpha(0);
                slideText.setCancelToProgress(0f);
                slideText.setSlideX(1f);
                slideText.setEnabled(true);
            } else {
                slideText.setTranslationX(0);
                slideText.setAlpha(0);
                slideText.setCancelToProgress(1f);
                slideText.setEnabled(true);
            }
            recordCircle.resetLockTranslation(lastRecordState == RECORD_STATE_PREPARING);

            recordIsCanceled = false;
            isRecordingStateChanged();

            //ENTER TRANSITION
            AnimatorSet iconChanges = new AnimatorSet();
            iconChanges.playTogether(
                    ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_SCALE, 0),
                    ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_ALPHA, 0),
                    ObjectAnimator.ofFloat(recordDot, View.SCALE_Y, 1),
                    ObjectAnimator.ofFloat(recordDot, View.SCALE_X, 1),
                    ObjectAnimator.ofFloat(recordTimerView, View.TRANSLATION_X, 0),
                    ObjectAnimator.ofFloat(recordTimerView, View.ALPHA, 1)
            );
            iconChanges.playTogether(ObjectAnimator.ofFloat(slideText, View.TRANSLATION_X, 0));
            iconChanges.playTogether(ObjectAnimator.ofFloat(slideText, View.ALPHA, 1));
            if (controlsView != null) {
                iconChanges.playTogether(ObjectAnimator.ofFloat(controlsView, View.ALPHA, 1));
            }
            if (audioVideoSendButton != null) {
                iconChanges.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, View.ALPHA, 0));
            }
            if (botCommandsMenuButton != null) {
                iconChanges.playTogether(
                        ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_Y, 0),
                        ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_X, 0),
                        ObjectAnimator.ofFloat(botCommandsMenuButton, View.ALPHA, 0)
                );
            }

            AnimatorSet viewTransition = new AnimatorSet();
            viewTransition.playTogether(
                    ObjectAnimator.ofFloat(messageEditText, MESSAGE_TEXT_TRANSLATION_X, dp(20)),
                    ObjectAnimator.ofFloat(messageEditText, View.ALPHA, 0),
                    ObjectAnimator.ofFloat(recordedAudioPanel, View.ALPHA, 1f)
            );
            if (fromPause) {
                viewTransition.playTogether(ObjectAnimator.ofFloat(recordedAudioSeekBar, View.ALPHA, 0.0f));
                viewTransition.playTogether(ObjectAnimator.ofFloat(recordedAudioPlayButton, View.ALPHA, 0.0f));
                viewTransition.playTogether(ObjectAnimator.ofFloat(recordedAudioBackground, View.ALPHA, 0.0f));
                viewTransition.playTogether(ObjectAnimator.ofFloat(recordedAudioTimeTextView, View.ALPHA, 0.0f));

                viewTransition.playTogether(ObjectAnimator.ofFloat(recordDeleteImageView, View.ALPHA, 0.0f));
                viewTransition.playTogether(ObjectAnimator.ofFloat(recordDeleteImageView, View.SCALE_X, 0.0f));
                viewTransition.playTogether(ObjectAnimator.ofFloat(recordDeleteImageView, View.SCALE_Y, 0.0f));

                viewTransition.playTogether(ObjectAnimator.ofFloat(videoTimelineView, View.ALPHA, 0));
            }

            if (scheduledButton != null) {
                viewTransition.playTogether(
                        ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, dp(30)),
                        ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 0f)
                );
            }
            if (attachLayout != null) {
                viewTransition.playTogether(
                        ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_TRANSLATION_X, dp(30)),
                        ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_ALPHA, 0f)
                );
            }

            runningAnimationAudio.playTogether(
                    iconChanges.setDuration(150),
                    viewTransition.setDuration(150),
                    ObjectAnimator.ofFloat(recordCircle, recordCircleScale, 1).setDuration(300)
            );
            if (!fromPause) {
                runningAnimationAudio.playTogether(ObjectAnimator.ofFloat(recordCircle, recordControlsCircleScale, 1).setDuration(300));
            }
            runningAnimationAudio.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (animator.equals(runningAnimationAudio)) {
                        runningAnimationAudio = null;
                    }
                    isRecordingStateChanged();
                    slideText.setAlpha(1f);
                    slideText.setTranslationX(0);

                    if (controlsView != null) {
                        controlsView.showTooltipIfNeed();
                    }
                    if (messageEditText != null) {
                        messageEditText.setAlpha(0f);
                    }
                    if (fromPause) {
                        if (recordedAudioSeekBar != null) {
                            recordedAudioSeekBar.setVisibility(View.GONE);
                        }
                        if (recordedAudioPanel != null) {
                            recordedAudioPanel.setVisibility(View.GONE);
                        }
                        isRecordingStateChanged();
                    }
                }
            });
            runningAnimationAudio.setInterpolator(new DecelerateInterpolator());
            runningAnimationAudio.start();
            recordTimerView.start(millisecondsRecorded);
        } else {
            if (recordIsCanceled && recordState == RECORD_STATE_PREPARING) {
                return;
            }
            if (wakeLock != null) {
                try {
                    wakeLock.release();
                    wakeLock = null;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            AndroidUtilities.unlockOrientation(parentActivity);
            wasSendTyping = false;
            if (recordInterfaceState == 0) {
                lastRecordState = recordState;
                return;
            }
            accountInstance.getMessagesController().sendTyping(dialog_id, getThreadMessageId(), 2, 0);
            recordInterfaceState = 0;
            if (emojiView != null) {
                emojiView.setEnabled(true);
            }

            boolean shouldShowFastTransition = false;
            if (runningAnimationAudio != null) {
                shouldShowFastTransition = runningAnimationAudio.isRunning();
                if (audioVideoSendButton != null) {
                    audioVideoSendButton.setScaleX(1f);
                    audioVideoSendButton.setScaleY(1f);
                }
                runningAnimationAudio.removeAllListeners();
                runningAnimationAudio.cancel();
            }

            if (recordPannelAnimation != null) {
                recordPannelAnimation.cancel();
            }
            if (messageEditText != null) {
                messageEditText.setVisibility(View.VISIBLE);
            }

            runningAnimationAudio = new AnimatorSet();
            //EXIT TRANSITION
            if (shouldShowFastTransition || recordState == RECORD_STATE_CANCEL_BY_TIME) {
                if (audioVideoSendButton != null) {
                    audioVideoSendButton.setVisibility(View.VISIBLE);
                }
                runningAnimationAudio.playTogether(
                        ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_SCALE, 1),
                        ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_ALPHA, emojiButtonRestricted ? 0.5f : 1.0f),
                        ObjectAnimator.ofFloat(recordDot, View.SCALE_Y, 0),
                        ObjectAnimator.ofFloat(recordDot, View.SCALE_X, 0),
                        ObjectAnimator.ofFloat(recordCircle, recordCircleScale, 0.0f),
                        ObjectAnimator.ofFloat(recordCircle, recordControlsCircleScale, 0.0f),
                        ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(recordTimerView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(messageEditText, View.ALPHA, 1),
                        ObjectAnimator.ofFloat(messageEditText, MESSAGE_TEXT_TRANSLATION_X, 0),
                        ObjectAnimator.ofFloat(this, "slideToCancelProgress", 1f)
                );
                if (controlsView != null) {
                    runningAnimationAudio.playTogether(ObjectAnimator.ofFloat(controlsView, View.ALPHA, 0));
                    controlsView.hideHintView();
                }
                if (botCommandsMenuButton != null) {
                    runningAnimationAudio.playTogether(
                            ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_Y, 1),
                            ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_X, 1),
                            ObjectAnimator.ofFloat(botCommandsMenuButton, View.ALPHA, 1)
                    );
                }
                if (audioVideoSendButton != null) {
                    audioVideoSendButton.setScaleX(1f);
                    audioVideoSendButton.setScaleY(1f);
                    runningAnimationAudio.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, View.ALPHA, 1));
                    audioVideoSendButton.setState(isInVideoMode() ? ChatActivityEnterViewAnimatedIconView.State.VIDEO : ChatActivityEnterViewAnimatedIconView.State.VOICE, true);
                }
                if (scheduledButton != null) {
                    runningAnimationAudio.playTogether(
                            ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, 0),
                            ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1f)
                    );
                }
                if (attachLayout != null) {
                    runningAnimationAudio.playTogether(
                            ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_TRANSLATION_X, 0),
                            ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_ALPHA, 1f)
                    );
                }

                recordIsCanceled = true;
                isRecordingStateChanged();
                runningAnimationAudio.setDuration(150);
            } else if (recordState == RECORD_STATE_PREPARING) {
                createRecordAudioPanel();
                createRecordCircle();
                if (slideText != null) {
                    slideText.setEnabled(false);
                }
                if (isInVideoMode()) {
                    if (recordedAudioBackground != null) {
                        recordedAudioBackground.setVisibility(GONE);
                    }
                    if (recordedAudioTimeTextView != null) {
                        recordedAudioTimeTextView.setVisibility(GONE);
                    }
                    if (recordedAudioPlayButton != null) {
                        recordedAudioPlayButton.setVisibility(GONE);
                    }
                    if (recordedAudioSeekBar != null) {
                        recordedAudioSeekBar.setVisibility(GONE);
                        isRecordingStateChanged();
                    }
                    if (recordedAudioPanel != null) {
                        recordedAudioPanel.setAlpha(1.0f);
                        recordedAudioPanel.setVisibility(VISIBLE);
                    }
                    if (recordDeleteImageView != null) {
                        recordDeleteImageView.setProgress(0);
                        recordDeleteImageView.stopAnimation();
                    }
                } else {
                    if (videoTimelineView != null) {
                        videoTimelineView.setVisibility(GONE);
                        isRecordingStateChanged();
                    }
                    if (recordedAudioTimeTextView != null) {
                        recordedAudioTimeTextView.setVisibility(VISIBLE);
                        recordedAudioTimeTextView.setAlpha(0f);
                    }
                    if (recordedAudioPanel != null) {
                        recordedAudioPanel.setVisibility(VISIBLE);
                        recordedAudioPanel.setAlpha(1.0f);
                    }
                    if (recordedAudioBackground != null) {
                        recordedAudioBackground.setVisibility(VISIBLE);
                        recordedAudioBackground.setAlpha(0f);
                    }
                    if (recordedAudioPlayButton != null) {
                        recordedAudioPlayButton.setVisibility(VISIBLE);
                        recordedAudioPlayButton.setAlpha(0f);
                    }
                    if (recordedAudioSeekBar != null) {
                        recordedAudioSeekBar.setVisibility(VISIBLE);
                        recordedAudioSeekBar.setAlpha(0f);
                        isRecordingStateChanged();
                    }
                }

                sendButtonVisible = true;
                snapAnimationProgress = 1f;
                lockAnimatedTranslation = startTranslation;
                slideToCancelProgress = 1f;
                if (slideText != null) {
                    slideText.setCancelToProgress(1f);
                }
                if (controlsView != null) {
                    controlsView.invalidate();
                }

                if (recordDeleteImageView != null) {
                    recordDeleteImageView.setAlpha(0f);
                    recordDeleteImageView.setScaleX(0f);
                    recordDeleteImageView.setScaleY(0f);
                    recordDeleteImageView.setProgress(0);
                    recordDeleteImageView.stopAnimation();
                }

                ViewGroup.LayoutParams oldLayoutParams = null;
                ViewGroup parent = null;
                if (!isInVideoMode() && !shouldDrawRecordedAudioPanelInParent) {
                    parent = (ViewGroup) recordedAudioPanel.getParent();
                    oldLayoutParams = recordedAudioPanel.getLayoutParams();
                    parent.removeView(recordedAudioPanel);

                    FrameLayout.LayoutParams newLayoutParams = new FrameLayout.LayoutParams(parent.getMeasuredWidth(), dp(48));
                    newLayoutParams.gravity = Gravity.BOTTOM;
                    sizeNotifierLayout.addView(recordedAudioPanel, newLayoutParams);
                    videoTimelineView.setVisibility(GONE);
                } else {
                    videoTimelineView.setVisibility(VISIBLE);
                }
                isRecordingStateChanged();

                AnimatorSet videoAdditionalAnimations = new AnimatorSet();
                if (!animated) {
                    createRecordPanel();

                    recordCircleScale.set(recordCircle, 1f);
                    recordCircle.setTransformToSeekbar(1f);
                    if (!isInVideoMode()) {
                        if (transformToSeekbar != 0 && recordedAudioBackground != null) {
                            float step1Time = 0.38f;
                            float step2Time = 0.25f;
                            float step3Time = 1f - step1Time - step2Time;

                            float progressToSeekbarStep3 = Math.max(0, (transformToSeekbar - step1Time - step2Time) / step3Time);
                            progressToSeekbarStep3 = CubicBezierInterpolator.EASE_BOTH.getInterpolation(progressToSeekbarStep3);

                            seekBarWaveform.setWaveScaling(progressToSeekbarStep3);
                            recordedAudioTimeTextView.setAlpha(progressToSeekbarStep3);
                            recordedAudioPlayButton.setAlpha(progressToSeekbarStep3);
                            recordedAudioPlayButton.setScaleX(progressToSeekbarStep3);
                            recordedAudioPlayButton.setScaleY(progressToSeekbarStep3);
                            recordedAudioSeekBar.setAlpha(progressToSeekbarStep3);
                            recordedAudioSeekBar.invalidate();
                        }
                    }

                    recordDot.setScaleY(0);
                    recordDot.setScaleX(0);
                    recordTimerView.setAlpha(0f);
                    recordTimerView.setTranslationX(-dp(20));
                    slideText.setAlpha(0);
                    recordDeleteImageView.setAlpha(1f);
                    recordDeleteImageView.setScaleY(1f);
                    recordDeleteImageView.setScaleX(1f);
                    EMOJI_BUTTON_SCALE.set(emojiButton, 0f);
                    EMOJI_BUTTON_ALPHA.set(emojiButton, 0f);
                    messageEditText.setAlpha(0f);

                    if (audioVideoSendButton != null) {
                        audioVideoSendButton.setState(isInVideoMode() ? ChatActivityEnterViewAnimatedIconView.State.VIDEO : ChatActivityEnterViewAnimatedIconView.State.VOICE, animated);
                        audioVideoSendButton.setAlpha(1f);
                        audioVideoSendButton.setScaleX(1f);
                        audioVideoSendButton.setScaleY(1f);
                    }
                    if (botCommandsMenuButton != null) {
                        botCommandsMenuButton.setAlpha(0f);
                        botCommandsMenuButton.setScaleX(0f);
                        botCommandsMenuButton.setScaleY(0f);
                    }

                    if (isInVideoMode()) {
                        recordedAudioTimeTextView.setAlpha(1f);
                        videoTimelineView.setAlpha(1f);
                    }

                    ViewGroup finalParent = parent;
                    ViewGroup.LayoutParams finalOldLayoutParams = oldLayoutParams;
                    if (finalParent != null) {
                        sizeNotifierLayout.removeView(recordedAudioPanel);
                        finalParent.addView(recordedAudioPanel, finalOldLayoutParams);
                    }
                    recordedAudioPanel.setAlpha(1.0f);
                    recordedAudioBackground.setAlpha(1f);
                    recordedAudioTimeTextView.setAlpha(1f);
                    recordedAudioPlayButton.setAlpha(1f);
                    recordedAudioPlayButton.setScaleY(1f);
                    recordedAudioPlayButton.setScaleX(1f);
                    recordedAudioSeekBar.setAlpha(1f);

                    emojiButtonAlpha = emojiButtonScale = 0f;
                    updateEmojiButtonParams();

                    isRecordingStateChanged();
                } else {
                    ValueAnimator transformToSeekbar = ValueAnimator.ofFloat(0, 1f);
                    transformToSeekbar.addUpdateListener(animation -> {
                        float value = (float) animation.getAnimatedValue();
                        recordCircle.setTransformToSeekbar(value);
                        if (!isInVideoMode()) {
                            seekBarWaveform.setWaveScaling(recordCircle.getTransformToSeekbarProgressStep3());
                            recordedAudioTimeTextView.setAlpha(recordCircle.getTransformToSeekbarProgressStep3());
                            recordedAudioPlayButton.setAlpha(recordCircle.getTransformToSeekbarProgressStep3());
                            recordedAudioPlayButton.setScaleX(recordCircle.getTransformToSeekbarProgressStep3());
                            recordedAudioPlayButton.setScaleY(recordCircle.getTransformToSeekbarProgressStep3());
                            recordedAudioSeekBar.setAlpha(recordCircle.getTransformToSeekbarProgressStep3());
                            recordedAudioSeekBar.invalidate();
                        }
                        isRecordingStateChanged();
                    });
                    transformToSeekbar.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            recordCircle.setTransformToSeekbar(1);
                            isRecordingStateChanged();
                        }
                    });
                    transformToSeekbar.setDuration(isInVideoMode() ? 490 : 580);

                    AnimatorSet iconsAnimator = new AnimatorSet();

                    iconsAnimator.playTogether(
                            ObjectAnimator.ofFloat(recordDot, View.SCALE_Y, 0),
                            ObjectAnimator.ofFloat(recordDot, View.SCALE_X, 0),
                            ObjectAnimator.ofFloat(recordTimerView, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(recordTimerView, View.TRANSLATION_X, -dp(20)),
                            ObjectAnimator.ofFloat(slideText, View.ALPHA, 0),
                            ObjectAnimator.ofFloat(recordDeleteImageView, View.ALPHA, 1),
                            ObjectAnimator.ofFloat(recordDeleteImageView, View.SCALE_Y, 1f),
                            ObjectAnimator.ofFloat(recordDeleteImageView, View.SCALE_X, 1f),
                            ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_SCALE, 0),
                            ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_ALPHA, 0),
                            ObjectAnimator.ofFloat(messageEditText, View.ALPHA, 0)
                    );
                    if (recordDeleteImageView != null) {
                        recordDeleteImageView.setAlpha(0f);
                        recordDeleteImageView.setScaleX(0f);
                        recordDeleteImageView.setScaleY(0f);
                    }
                    if (audioVideoSendButton != null) {
                        iconsAnimator.playTogether(
                                ObjectAnimator.ofFloat(audioVideoSendButton, View.ALPHA, 1),
                                ObjectAnimator.ofFloat(audioVideoSendButton, View.SCALE_X, 1),
                                ObjectAnimator.ofFloat(audioVideoSendButton, View.SCALE_Y, 1)
                        );
                        audioVideoSendButton.setState(isInVideoMode() ? ChatActivityEnterViewAnimatedIconView.State.VIDEO : ChatActivityEnterViewAnimatedIconView.State.VOICE, true);
                    }
                    if (botCommandsMenuButton != null) {
                        iconsAnimator.playTogether(
                                ObjectAnimator.ofFloat(botCommandsMenuButton, View.ALPHA, 0),
                                ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_X, 0),
                                ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_Y, 0)
                        );
                    }
                    iconsAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (audioVideoSendButton != null) {
                                audioVideoSendButton.setScaleX(1f);
                                audioVideoSendButton.setScaleY(1f);
                            }
                        }
                    });

                    iconsAnimator.setDuration(150);
                    iconsAnimator.setStartDelay(150);

                    if (isInVideoMode()) {
                        recordedAudioTimeTextView.setAlpha(0);
                        videoTimelineView.setAlpha(0);
                        videoAdditionalAnimations.playTogether(
                            ObjectAnimator.ofFloat(recordedAudioTimeTextView, View.ALPHA, 1),
                            ObjectAnimator.ofFloat(videoTimelineView, View.ALPHA, 1)
                        );
                        videoAdditionalAnimations.setDuration(150);
                        videoAdditionalAnimations.setStartDelay(430);
                    }

                    runningAnimationAudio.playTogether(
                            iconsAnimator,
                            transformToSeekbar,
                            videoAdditionalAnimations
                    );

                    ViewGroup finalParent = parent;
                    ViewGroup.LayoutParams finalOldLayoutParams = oldLayoutParams;
                    runningAnimationAudio.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (finalParent != null) {
                                sizeNotifierLayout.removeView(recordedAudioPanel);
                                finalParent.addView(recordedAudioPanel, finalOldLayoutParams);
                            }
                            recordedAudioPanel.setAlpha(1.0f);
                            recordedAudioBackground.setAlpha(1f);
                            recordedAudioTimeTextView.setAlpha(1f);
                            recordedAudioPlayButton.setAlpha(1f);
                            recordedAudioPlayButton.setScaleY(1f);
                            recordedAudioPlayButton.setScaleX(1f);
                            recordedAudioSeekBar.setAlpha(1f);

                            emojiButtonAlpha = emojiButtonScale = 0f;
                            updateEmojiButtonParams();

                            if (botCommandsMenuButton != null) {
                                botCommandsMenuButton.setAlpha(0f);
                                botCommandsMenuButton.setScaleX(0f);
                                botCommandsMenuButton.setScaleY(0f);
                            }

                            if (controlsView != null && onceVisible && !voiceOnce && (MessagesController.getGlobalMainSettings().getInt("voiceoncehint", 0) < 3)) {
                                controlsView.showHintView();
                            }
                        }
                    });
                }

            } else if (recordState == RECORD_STATE_CANCEL || recordState == RECORD_STATE_CANCEL_BY_GESTURE) {
                if (audioVideoSendButton != null) {
                    audioVideoSendButton.setVisibility(View.VISIBLE);
                }
                recordIsCanceled = true;
                isRecordingStateChanged();
                AnimatorSet iconsAnimator = new AnimatorSet();
                iconsAnimator.playTogether(
                        ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_SCALE, 1),
                        ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_ALPHA, emojiButtonRestricted ? 0.5f : 1.0f),
                        ObjectAnimator.ofFloat(recordDot, View.SCALE_Y, 0),
                        ObjectAnimator.ofFloat(recordDot, View.SCALE_X, 0)
                );
                if (controlsView != null) {
                    iconsAnimator.playTogether(ObjectAnimator.ofFloat(controlsView, View.ALPHA, 0));
                    controlsView.hideHintView();
                }

                if (botCommandsMenuButton != null) {
                    iconsAnimator.playTogether(
                            ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_Y, 1),
                            ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_X, 1),
                            ObjectAnimator.ofFloat(botCommandsMenuButton, View.ALPHA, 1));
                }
                AnimatorSet recordTimer = new AnimatorSet();
                recordTimer.playTogether(
                        ObjectAnimator.ofFloat(recordTimerView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(recordTimerView, View.TRANSLATION_X, -dp(20)),
                        ObjectAnimator.ofFloat(slideText, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(slideText, View.TRANSLATION_X, -dp(20))
                );

                if (recordState != RECORD_STATE_CANCEL_BY_GESTURE) {
                    audioVideoButtonContainer.setScaleX(0);
                    audioVideoButtonContainer.setScaleY(0);

                    if (attachButton != null && attachButton.getVisibility() == View.VISIBLE) {
                        attachButton.setScaleX(0);
                        attachButton.setScaleY(0);
                    }

                    if (botButton != null && botButton.getVisibility() == View.VISIBLE) {
                        botButton.setScaleX(0);
                        botButton.setScaleY(0);
                    }

                    iconsAnimator.playTogether(
                            ObjectAnimator.ofFloat(this, "slideToCancelProgress", 1f),
                            ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_X, 1f),
                            ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_Y, 1f),
                            ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 1f)
                    );
                    if (attachLayout != null) {
                        iconsAnimator.playTogether(
                                ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_ALPHA, 1f),
                                ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_TRANSLATION_X, 0)
                        );
                    }
                    if (attachButton != null) {
                        iconsAnimator.playTogether(
                                ObjectAnimator.ofFloat(attachButton, View.SCALE_X, 1f),
                                ObjectAnimator.ofFloat(attachButton, View.SCALE_Y, 1f)
                        );
                    }
                    if (botButton != null) {
                        iconsAnimator.playTogether(
                                ObjectAnimator.ofFloat(botButton, View.SCALE_X, 1f),
                                ObjectAnimator.ofFloat(botButton, View.SCALE_Y, 1f)
                        );
                    }
                    if (audioVideoSendButton != null) {
                        iconsAnimator.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, View.ALPHA, 1));
                        iconsAnimator.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, View.SCALE_X, 1));
                        iconsAnimator.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, View.SCALE_Y, 1));
                        audioVideoSendButton.setState(isInVideoMode() ? ChatActivityEnterViewAnimatedIconView.State.VIDEO : ChatActivityEnterViewAnimatedIconView.State.VOICE, true);
                    }
                    if (scheduledButton != null) {
                        iconsAnimator.playTogether(
                                ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1f),
                                ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, 0)
                        );
                    }
                } else {
                    AnimatorSet icons2 = new AnimatorSet();
                    icons2.playTogether(
                            ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 1.0f)
                    );
                    if (attachLayout != null) {
                        icons2.playTogether(
                                ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_TRANSLATION_X, 0),
                                ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_ALPHA, 1f)
                        );
                    }
                    if (scheduledButton != null) {
                        icons2.playTogether(
                                ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1f),
                                ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, 0)
                        );
                    }

                    icons2.setDuration(150);
                    icons2.setStartDelay(110);
                    icons2.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            if (audioVideoSendButton != null) {
                                audioVideoSendButton.setAlpha(1f);
                            }
                        }
                    });
                    runningAnimationAudio.playTogether(icons2);
                }

                iconsAnimator.setDuration(150);
                iconsAnimator.setStartDelay(700);

                recordTimer.setDuration(200);
                recordTimer.setStartDelay(200);

                messageTextTranslationX = 0;
                updateMessageTextParams();

                ObjectAnimator messageEditTextAniamtor = ObjectAnimator.ofFloat(messageEditText, View.ALPHA, 1);
                messageEditTextAniamtor.setStartDelay(emojiButtonPaddingAlpha == 1f ? 300 : 700);
                messageEditTextAniamtor.setDuration(200);

                runningAnimationAudio.playTogether(
                        iconsAnimator,
                        recordTimer,
                        messageEditTextAniamtor,
                        ObjectAnimator.ofFloat(this, "lockAnimatedTranslation", startTranslation).setDuration(200)
                );

                if (recordState == RECORD_STATE_CANCEL_BY_GESTURE) {
                    recordCircle.canceledByGesture();
                    ObjectAnimator cancel = ObjectAnimator.ofFloat(this, "slideToCancelProgress", 1f).setDuration(200);
                    cancel.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    runningAnimationAudio.playTogether(cancel);
                } else {
                    Animator recordCircleAnimator = ObjectAnimator.ofFloat(this, "exitTransition", 1.0f);
                    recordCircleAnimator.setDuration(360);
                    recordCircleAnimator.setStartDelay(490);
                    runningAnimationAudio.playTogether(
                            recordCircleAnimator
                    );
                }
                if (recordDot != null) {
                    recordDot.playDeleteAnimation();
                }
            } else {

                if (audioVideoSendButton != null) {
                    audioVideoSendButton.setVisibility(View.VISIBLE);
                }

                AnimatorSet iconsAnimator = new AnimatorSet();
                iconsAnimator.playTogether(
                        ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_SCALE, 1),
                        ObjectAnimator.ofFloat(emojiButton, EMOJI_BUTTON_ALPHA, emojiButtonRestricted ? 0.5f : 1.0f),
                        ObjectAnimator.ofFloat(recordDot, View.SCALE_Y, 0),
                        ObjectAnimator.ofFloat(recordDot, View.SCALE_X, 0),
                        ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 1.0f)
                );
                if (controlsView != null) {
                    iconsAnimator.playTogether(ObjectAnimator.ofFloat(controlsView, View.ALPHA, 0));
                    controlsView.hideHintView();
                }
                if (botCommandsMenuButton != null) {
                    iconsAnimator.playTogether(
                            ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_Y, 1),
                            ObjectAnimator.ofFloat(botCommandsMenuButton, View.SCALE_X, 1),
                            ObjectAnimator.ofFloat(botCommandsMenuButton, View.ALPHA, 1));
                }
                if (audioVideoSendButton != null) {
                    audioVideoSendButton.setScaleX(1f);
                    audioVideoSendButton.setScaleY(1f);
                    iconsAnimator.playTogether(ObjectAnimator.ofFloat(audioVideoSendButton, View.ALPHA, 1));
                    audioVideoSendButton.setState(isInVideoMode() ? ChatActivityEnterViewAnimatedIconView.State.VIDEO : ChatActivityEnterViewAnimatedIconView.State.VOICE, true);
                }
                if (attachLayout != null) {
                    attachLayoutTranslationX = 0;
                    updateAttachLayoutParams();
                    iconsAnimator.playTogether(
                            ObjectAnimator.ofFloat(attachLayout, ATTACH_LAYOUT_ALPHA, 1f)
                    );
                }
                if (scheduledButton != null) {
                    scheduledButton.setTranslationX(0);
                    iconsAnimator.playTogether(
                            ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1f)
                    );
                }

                iconsAnimator.setDuration(150);
                iconsAnimator.setStartDelay(200);

                AnimatorSet recordTimer = new AnimatorSet();
                recordTimer.playTogether(
                        ObjectAnimator.ofFloat(recordTimerView, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(recordTimerView, View.TRANSLATION_X, dp(40)),
                        ObjectAnimator.ofFloat(slideText, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(slideText, View.TRANSLATION_X, dp(40))
                );

                recordTimer.setDuration(150);
                Animator recordCircleAnimator = ObjectAnimator.ofFloat(this, "exitTransition", 1.0f);
                recordCircleAnimator.setDuration(messageTransitionIsRunning ? 220 : 360);

                messageTextTranslationX = 0;
                updateMessageTextParams();

                ObjectAnimator messageEditTextAniamtor = ObjectAnimator.ofFloat(messageEditText, View.ALPHA, 1);
                messageEditTextAniamtor.setStartDelay(emojiButtonPaddingAlpha == 1f ? 150 : 450);
                messageEditTextAniamtor.setDuration(200);

                runningAnimationAudio.playTogether(
                        iconsAnimator,
                        recordTimer,
                        messageEditTextAniamtor,
                        recordCircleAnimator
                );
            }
            runningAnimationAudio.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (animator.equals(runningAnimationAudio)) {
                        if (recordState != RECORD_STATE_PREPARING && messageEditText != null && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
                            messageEditText.requestFocus();
                        }
                        cancelRecordInterfaceInternal();
                        if (recordState != RECORD_STATE_PREPARING) {
                            if (controlsView != null) {
                                controlsView.setVisibility(GONE);
                            }
                            if (recordCircle != null) {
                                recordCircle.setSendButtonInvisible();
                            }
                        }
                    }
                }
            });
            runningAnimationAudio.start();
            if (recordTimerView != null) {
                recordTimerView.stop();
            }
        }
        delegate.onAudioVideoInterfaceUpdated();
        updateSendAsButton();
        lastRecordState = recordState;
    }

    private void cancelRecordInterfaceInternal() {
        if (recordPanel != null) {
            recordPanel.setVisibility(GONE);
        }
        if (recordCircle != null) {
            recordCircle.setVisibility(GONE);
        }
        runningAnimationAudio = null;
        isRecordingStateChanged();
        if (recordedAudioBackground != null) {
            recordedAudioBackground.setAlpha(1f);
        }
        if (attachLayout != null) {
            attachLayoutTranslationX = 0;
            updateAttachLayoutParams();
        }
        if (slideText != null) {
            slideText.setCancelToProgress(0f);
        }

        delegate.onAudioVideoInterfaceUpdated();
        updateSendAsButton();
    }

    protected void isRecordingStateChanged() {

    }

    private void createRecordPanel() {
        if (recordPanel != null || getContext() == null) {
            return;
        }

        recordPanel = new FrameLayout(getContext()) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return super.onTouchEvent(event);
            }
        };
        recordPanel.setClipChildren(false);
        recordPanel.setVisibility(GONE);
        messageEditTextContainer.addView(recordPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        recordPanel.setOnTouchListener((v, event) -> true);
        recordPanel.addView(slideText = new SlideTextView(getContext()), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.NO_GRAVITY, 45, 0, 0, 0));

        recordTimeContainer = new LinearLayout(getContext());
        recordTimeContainer.setOrientation(LinearLayout.HORIZONTAL);
        recordTimeContainer.setPadding(dp(13), 0, 0, 0);
        recordTimeContainer.setFocusable(false);

        recordTimeContainer.addView(recordDot = new RecordDot(getContext()), LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        recordTimeContainer.addView(recordTimerView = new TimerView(getContext()), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

        recordPanel.addView(recordTimeContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (recordingAudioVideo) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN && AndroidUtilities.findChildViewUnder(this, ev.getX(), ev.getY()) != birthdayHint) {
            hideHints();
        }
        return super.onInterceptTouchEvent(ev);
    }

    public void setDelegate(ChatActivityEnterViewDelegate chatActivityEnterViewDelegate) {
        delegate = chatActivityEnterViewDelegate;
    }

    public void setCommand(MessageObject messageObject, String command, boolean longPress, boolean username) {
        if (command == null || getVisibility() != VISIBLE || messageEditText == null) {
            return;
        }
        if (longPress) {
            String text = messageEditText.getText().toString();
            TLRPC.User user = messageObject != null && DialogObject.isChatDialog(dialog_id) ? accountInstance.getMessagesController().getUser(messageObject.messageOwner.from_id.user_id) : null;
            if ((botCount != 1 || username) && user != null && user.bot && !command.contains("@")) {
                text = String.format(Locale.US, "%s@%s", command, UserObject.getPublicUsername(user)) + " " + text.replaceFirst("^/[a-zA-Z@\\d_]{1,255}(\\s|$)", "");
            } else {
                text = command + " " + text.replaceFirst("^/[a-zA-Z@\\d_]{1,255}(\\s|$)", "");
            }
            ignoreTextChange = true;
            messageEditText.setText(text);
            messageEditText.setSelection(messageEditText.getText().length());
            ignoreTextChange = false;
            if (delegate != null) {
                delegate.onTextChanged(messageEditText.getText(), true, false);
            }
            if (!keyboardVisible && currentPopupContentType == -1) {
                openKeyboard();
            }
        } else {
            if (slowModeTimer > 0 && !isInScheduleMode()) {
                if (delegate != null) {
                    delegate.onUpdateSlowModeButton(slowModeButton, true, slowModeButton.getText());
                }
                return;
            }
            TLRPC.User user = messageObject != null && DialogObject.isChatDialog(dialog_id) ? accountInstance.getMessagesController().getUser(messageObject.messageOwner.from_id.user_id) : null;
            SendMessagesHelper.SendMessageParams sendMessageParams;
            if ((botCount != 1 || username) && user != null && user.bot && !command.contains("@")) {
                sendMessageParams = SendMessagesHelper.SendMessageParams.of(String.format(Locale.US, "%s@%s", command, UserObject.getPublicUsername(user)), dialog_id, replyingMessageObject, getThreadMessage(), null, false, null, null, null, true, 0, null, false);
            } else {
                sendMessageParams = SendMessagesHelper.SendMessageParams.of(command, dialog_id, replyingMessageObject, getThreadMessage(), null, false, null, null, null, true, 0, null, false);
            }
            sendMessageParams.quick_reply_shortcut = parentFragment != null ? parentFragment.quickReplyShortcut : null;
            sendMessageParams.quick_reply_shortcut_id = parentFragment != null ? parentFragment.getQuickReplyId() : 0;
            sendMessageParams.effect_id = effectId;
            sendButton.setEffect(effectId = 0);
            applyStoryToSendMessageParams(sendMessageParams);
            SendMessagesHelper.getInstance(currentAccount).sendMessage(sendMessageParams);
        }
    }

    public void setEditingBusinessLink(TLRPC.TL_businessChatLink businessLink) {
        editingBusinessLink = businessLink;
        updateFieldHint(false);
        if (editingBusinessLink != null) {
            if (doneButtonAnimation != null) {
                doneButtonAnimation.cancel();
                doneButtonAnimation = null;
            }
            createDoneButton(true);
            doneButton.setOnClickListener(view -> saveBusinessLink());

            doneButton.setVisibility(View.VISIBLE);
            doneButton.setScaleX(0.1f);
            doneButton.setScaleY(0.1f);
            doneButton.setAlpha(0.0f);
            doneButton.animate().alpha(1f).scaleX(1).scaleY(1).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

            currentLimit = accountInstance.getMessagesController().maxMessageLength;
            final Paint.FontMetricsInt fontMetricsInt;
            Paint paint = null;
            if (messageEditText != null) {
                paint = messageEditText.getPaint();
            }
            if (paint == null) {
                paint = new TextPaint();
                paint.setTextSize(dp(18));
            }
            fontMetricsInt = paint.getFontMetricsInt();

            ArrayList<TLRPC.MessageEntity> entities = editingBusinessLink.entities;
            if (entities != null && businessLink.message != null) {
                CharSequence spannableText = applyMessageEntities(entities, businessLink.message, fontMetricsInt);
                setFieldText(spannableText);
            } else if (businessLink.message != null) {
                setFieldText(businessLink.message);
            }
            lastSavedBusinessLinkMessage = calculateBusinessLinkPresetMessage();

            setAllowStickersAndGifs(true, false, false);
            sendButton.setVisibility(GONE);
            setSlowModeButtonVisible(false);
            cancelBotButton.setVisibility(GONE);
            audioVideoButtonContainer.setVisibility(GONE);
            if (attachLayout != null) {
                attachLayout.setVisibility(GONE);
            }
            sendButtonContainer.setVisibility(GONE);
            if (scheduledButton != null) {
                scheduledButton.setVisibility(GONE);
            }
        }
    }

    private boolean captionAbove;
    private long effectId;

    public void setEffectId(long effectId) {
        this.effectId = effectId;
        if (sendButton != null) {
            sendButton.setEffect(effectId);
        }
    }

    public long getEffectId() {
        return effectId;
    }

    private MessageObject editingMessageObjectPreview(MessageObject msg, boolean applyCaption) {
        MessageObject previewMessage = new MessageObject(msg.currentAccount, msg.messageOwner, true, true) {
            @Override
            public boolean isOutOwner() {
                return true;
            }

            @Override
            public boolean needDrawShareButton() {
                return false;
            }
        };
        if (applyCaption) {
            CharSequence[] text = new CharSequence[] { messageEditText == null ? "" : messageEditText.getTextToUse() };
            ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(text, true);
            String message = text[0].toString();
            CharSequence cs = new SpannableStringBuilder(message);
            MessageObject.addEntitiesToText(cs, entities, true, true, false, true);
            cs = Emoji.replaceEmoji(cs, Theme.chat_msgTextPaint.getFontMetricsInt(), false, null);
            cs = MessageObject.replaceAnimatedEmoji(cs, entities, Theme.chat_msgTextPaint.getFontMetricsInt());
            previewMessage.caption = cs;
        }
        return previewMessage;
    }

    public void setEditingMessageObject(MessageObject messageObject, MessageObject.GroupedMessages groupedMessages, boolean caption) {
        if (audioToSend != null || videoToSendMessageObject != null || editingMessageObject == messageObject) {
            return;
        }
        createMessageEditText();
        boolean hadEditingMessage = editingMessageObject != null;
        editingMessageObject = messageObject;
        editingCaption = caption;
        CharSequence textToSetWithKeyboard;
        if (editingMessageObject != null) {
            captionAbove = groupedMessages != null ? groupedMessages.captionAbove : messageObject.messageOwner.invert_media;
            if (doneButtonAnimation != null) {
                doneButtonAnimation.cancel();
                doneButtonAnimation = null;
            }
            createDoneButton(false);
            doneButton.setOnClickListener(view -> doneEditingMessage());
            doneButton.setOnLongClickListener(v -> {
                if (messageObject.isMediaEmpty()) return false;
                if (messageEditText == null || TextUtils.isEmpty(messageEditText.getTextToUse())) return false;
                if (groupedMessages != null && (!groupedMessages.hasCaption || groupedMessages.isDocuments)) return false;
                if (messageObject != null && messageObject.type != MessageObject.TYPE_PHOTO && messageObject.type != MessageObject.TYPE_VIDEO && messageObject.type != MessageObject.TYPE_GIF) return false;

                MessageSendPreview sendPreview = new MessageSendPreview(getContext(), resourcesProvider);
                sendPreview.allowRelayout = true;

                ArrayList<MessageObject> messageObjects = new ArrayList<>();
                if (groupedMessages != null) {
                    for (int i = 0; i < groupedMessages.messages.size(); ++i) {
                        messageObjects.add(editingMessageObjectPreview(groupedMessages.messages.get(i), i == 0));
                    }
                } else {
                    messageObjects.add(editingMessageObjectPreview(messageObject, true));
                }
                sendPreview.setMessageObjects(messageObjects);

                ItemOptions options = ItemOptions.makeOptions(sizeNotifierLayout, resourcesProvider, doneButton);
                MessagePreviewView.ToggleButton button = new MessagePreviewView.ToggleButton(
                        getContext(),
                        R.raw.position_below, getString(R.string.CaptionAbove),
                        R.raw.position_above, getString(R.string.CaptionBelow),
                        resourcesProvider
                );
                button.setState(!captionAbove, false);
                button.setOnClickListener(v2 -> {
                    captionAbove = !captionAbove;
                    for (int i = 0; i < messageObjects.size(); ++i) {
                        messageObjects.get(i).messageOwner.invert_media = captionAbove;
                    }
                    button.setState(!captionAbove, true);
                    if (!messageObjects.isEmpty()) {
                        sendPreview.changeMessage(messageObjects.get(0));
                    }
                    sendPreview.scrollTo(!captionAbove);
                });
                options.addView(button);
                options.setupSelectors();
                sendPreview.setItemOptions(options);
                sendPreview.setSendButton(doneButton, false, v2 -> {
                    if (groupedMessages != null) {
                        for (MessageObject msg : groupedMessages.messages) {
                            msg.messageOwner.invert_media = captionAbove;
                        }
                        groupedMessages.calculate();
                    } else {
                        messageObject.messageOwner.invert_media = captionAbove;
                    }
                    doneEditingMessage();
                    sendPreview.dismiss(true);
                    captionAbove = false;
                });
                sendPreview.show();

                return true;
            });

            doneButton.setVisibility(View.VISIBLE);
            doneButton.setScaleX(0.1f);
            doneButton.setScaleY(0.1f);
            doneButton.setAlpha(0.0f);
            doneButton.animate().alpha(1f).scaleX(1).scaleY(1).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

            CharSequence editingText;
            if (caption) {
                currentLimit = accountInstance.getMessagesController().maxCaptionLength;
                editingText = editingMessageObject.caption;
            } else {
                currentLimit = accountInstance.getMessagesController().maxMessageLength;
                editingText = editingMessageObject.messageText;
            }
            if (editingText != null) {
                final Paint.FontMetricsInt fontMetricsInt;
                Paint paint = null;
                if (messageEditText != null) {
                    paint = messageEditText.getPaint();
                }
                if (paint == null) {
                    paint = new TextPaint();
                    paint.setTextSize(dp(18));
                }
                fontMetricsInt = paint.getFontMetricsInt();

                ArrayList<TLRPC.MessageEntity> entities = editingMessageObject.messageOwner.entities;
                textToSetWithKeyboard = applyMessageEntities(entities, editingText, fontMetricsInt);
            } else {
                textToSetWithKeyboard = "";
            }
            if (draftMessage == null && !hadEditingMessage) {
                draftMessage = messageEditText != null && messageEditText.length() > 0 ? messageEditText.getText() : null;
                draftSearchWebpage = messageWebPageSearch;
            }
            messageWebPageSearch = !(editingMessageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && editingMessageObject.messageOwner.media.manual) && (editingMessageObject.type == MessageObject.TYPE_TEXT || editingMessageObject.type == MessageObject.TYPE_EMOJIS);
            if (!keyboardVisible) {
                final CharSequence textToSetWithKeyboardFinal = textToSetWithKeyboard;
                AndroidUtilities.runOnUIThread(setTextFieldRunnable = () -> {
                    setFieldText(textToSetWithKeyboardFinal);
                    setTextFieldRunnable = null;
                }, 200);
            } else {
                if (setTextFieldRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(setTextFieldRunnable);
                    setTextFieldRunnable = null;
                }
                setFieldText(textToSetWithKeyboard);
            }
            if (messageEditText != null) {
                messageEditText.requestFocus();
            }
            openKeyboard();
            if (messageEditText != null) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messageEditText.getLayoutParams();
                layoutParams.rightMargin = dp(4);
                messageEditText.setLayoutParams(layoutParams);
            }
            sendButton.setVisibility(GONE);
            setSlowModeButtonVisible(false);
            cancelBotButton.setVisibility(GONE);
            audioVideoButtonContainer.setVisibility(GONE);
            attachLayout.setVisibility(GONE);
            sendButtonContainer.setVisibility(GONE);
            if (scheduledButton != null) {
                scheduledButton.setVisibility(GONE);
            }
        } else {
            if (setTextFieldRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(setTextFieldRunnable);
                setTextFieldRunnable = null;
            }
            if (doneButton != null) {
                doneButton.setVisibility(View.GONE);
            }
            currentLimit = -1;
            delegate.onMessageEditEnd(false);
            sendButtonContainer.setVisibility(VISIBLE);
            cancelBotButton.setScaleX(0.1f);
            cancelBotButton.setScaleY(0.1f);
            cancelBotButton.setAlpha(0.0f);
            cancelBotButton.setVisibility(GONE);
            if (slowModeTimer > 0 && !isInScheduleMode()) {
                if (slowModeTimer == Integer.MAX_VALUE) {
                    sendButton.setScaleX(1.0f);
                    sendButton.setScaleY(1.0f);
                    sendButton.setAlpha(1.0f);
                    sendButton.setVisibility(VISIBLE);
                    slowModeButton.setScaleX(0.1f);
                    slowModeButton.setScaleY(0.1f);
                    slowModeButton.setAlpha(0.0f);
                    setSlowModeButtonVisible(false);
                } else {
                    sendButton.setScaleX(0.1f);
                    sendButton.setScaleY(0.1f);
                    sendButton.setAlpha(0.0f);
                    sendButton.setVisibility(GONE);
                    slowModeButton.setScaleX(1.0f);
                    slowModeButton.setScaleY(1.0f);
                    slowModeButton.setAlpha(1.0f);
                    setSlowModeButtonVisible(true);
                }
                attachLayout.setScaleX(0.01f);
                attachLayoutAlpha = 0f;
                updateAttachLayoutParams();
                attachLayout.setVisibility(GONE);
                audioVideoButtonContainer.setScaleX(0.1f);
                audioVideoButtonContainer.setScaleY(0.1f);
                audioVideoButtonContainer.setAlpha(0.0f);
                audioVideoButtonContainer.setVisibility(GONE);
            } else {
                sendButton.setScaleX(0.1f);
                sendButton.setScaleY(0.1f);
                sendButton.setAlpha(0.0f);
                sendButton.setVisibility(GONE);
                slowModeButton.setScaleX(0.1f);
                slowModeButton.setScaleY(0.1f);
                slowModeButton.setAlpha(0.0f);
                setSlowModeButtonVisible(false);
                attachLayout.setScaleX(1.0f);
                attachLayoutAlpha = 1.0f;
                updateAttachLayoutParams();
                attachLayout.setVisibility(VISIBLE);
                audioVideoButtonContainer.setScaleX(1.0f);
                audioVideoButtonContainer.setScaleY(1.0f);
                audioVideoButtonContainer.setAlpha(1.0f);
                audioVideoButtonContainer.setVisibility(VISIBLE);
            }
            createScheduledButton();
            if (scheduledButton != null && scheduledButton.getTag() != null) {
                scheduledButton.setScaleX(1.0f);
                scheduledButton.setScaleY(1.0f);
                scheduledButton.setAlpha(1.0f);
                scheduledButton.setVisibility(VISIBLE);
            }
            if (parentFragment != null) {
                parentFragment.editingMessageObject = null;
                parentFragment.foundWebPage = null;
                if (parentFragment.messagePreviewParams != null) {
                    parentFragment.messagePreviewParams.updateLink(currentAccount, null, "", null, null, null);
                }
                setWebPage(null, true);
                parentFragment.fallbackFieldPanel();
            }
            createMessageEditText();
            if (messageEditText != null) {
                messageEditText.setText(draftMessage);
                messageEditText.setSelection(messageEditText.length());
            }
            draftMessage = null;
            messageWebPageSearch = draftSearchWebpage;
            if (getVisibility() == VISIBLE) {
                delegate.onAttachButtonShow();
            }
            updateFieldRight(1);
        }
        updateFieldHint(true);
        updateSendAsButton(true);
    }

    @Nullable
    public static CharSequence applyMessageEntities(ArrayList<TLRPC.MessageEntity> entities, CharSequence text, Paint.FontMetricsInt fontMetricsInt) {
        CharSequence result;
        MediaDataController.sortEntities(entities);
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
        Object[] spansToRemove = stringBuilder.getSpans(0, stringBuilder.length(), Object.class);
        if (spansToRemove != null && spansToRemove.length > 0) {
            for (int a = 0; a < spansToRemove.length; a++) {
                stringBuilder.removeSpan(spansToRemove[a]);
            }
        }
        if (entities != null) {
            try {
                for (int a = 0; a < entities.size(); a++) {
                    TLRPC.MessageEntity entity = entities.get(a);
                    if (entity.offset + entity.length > stringBuilder.length()) {
                        continue;
                    }
                    if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                        if (entity.offset + entity.length < stringBuilder.length() && stringBuilder.charAt(entity.offset + entity.length) == ' ') {
                            entity.length++;
                        }
                        stringBuilder.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id, 3), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                        if (entity.offset + entity.length < stringBuilder.length() && stringBuilder.charAt(entity.offset + entity.length) == ' ') {
                            entity.length++;
                        }
                        stringBuilder.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_messageEntityMentionName) entity).user_id, 3), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (entity instanceof TLRPC.TL_messageEntityCode) {
                        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                        run.flags |= TextStyleSpan.FLAG_STYLE_MONO;
                        MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                    } else if (entity instanceof TLRPC.TL_messageEntityPre) {
                        continue;
                    } else if (entity instanceof TLRPC.TL_messageEntityBold) {
                        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                        run.flags |= TextStyleSpan.FLAG_STYLE_BOLD;
                        MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                    } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                        run.flags |= TextStyleSpan.FLAG_STYLE_ITALIC;
                        MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                    } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
                        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                        run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
                        MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                    } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
                        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                        run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
                        MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                    } else if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                        stringBuilder.setSpan(new URLSpanReplacement(entity.url), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
                        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                        run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                        MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
                    } else if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                        TLRPC.TL_messageEntityCustomEmoji emojiEntity = (TLRPC.TL_messageEntityCustomEmoji) entity;
                        AnimatedEmojiSpan span;
                        if (emojiEntity.document != null) {
                            span = new AnimatedEmojiSpan(emojiEntity.document, fontMetricsInt);
                        } else {
                            span = new AnimatedEmojiSpan(emojiEntity.document_id, fontMetricsInt);
                        }
                        stringBuilder.setSpan(span, entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        QuoteSpan.mergeQuotes(stringBuilder, entities);
        result = Emoji.replaceEmoji(new SpannableStringBuilder(stringBuilder), fontMetricsInt, false, null);
        if (entities != null) {
            try {
                for (int a = entities.size() - 1; a >= 0; --a) {
                    TLRPC.MessageEntity entity = entities.get(a);
                    if (entity instanceof TLRPC.TL_messageEntityPre && entity.offset + entity.length <= result.length()) {
                        if (!(result instanceof Spannable)) {
                            result = new SpannableStringBuilder(result);
                        }
                        ((SpannableStringBuilder) result).insert(entity.offset + entity.length, "```\n");
                        ((SpannableStringBuilder) result).insert(entity.offset, "```" + (entity.language == null ? "" : entity.language) + "\n");
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return result;
    }

    public ImageView getAttachButton() {
        return attachButton;
    }

    public View getSendButton() {
        return sendButton.getVisibility() == VISIBLE ? sendButton : audioVideoButtonContainer;
    }

    public View getAudioVideoButtonContainer() {
        return audioVideoButtonContainer;
    }

    public View getEmojiButton() {
        return emojiButton;
    }

    public EmojiView getEmojiView() {
        return emojiView;
    }

    public TrendingStickersAlert getTrendingStickersAlert() {
        return trendingStickersAlert;
    }

    public void updateColors() {
        if (messageSendPreview != null) {
            messageSendPreview.updateColors();
        }

        updateRecordedDeleteIconColors();
        if (recordCircle != null) {
            recordCircle.updateColors();
        }
        if (recordDot != null) {
            recordDot.updateColors();
        }
        if (slideText != null) {
            slideText.updateColors();
        }
        if (recordTimerView != null) {
            recordTimerView.updateColors();
        }
        if (videoTimelineView != null) {
            videoTimelineView.updateColors();
        }

        if (captionLimitView != null && messageEditText != null) {
            if (codePointCount - currentLimit < 0) {
                captionLimitView.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            } else {
                captionLimitView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
            }
        }
        int color = getThemedColor(Theme.key_chat_messagePanelVoicePressed);
        int defaultAlpha = Color.alpha(color);
        if (doneCheckDrawable != null) {
            doneCheckDrawable.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(color, (int) (defaultAlpha * (0.58f + 0.42f * doneButtonEnabledProgress))), PorterDuff.Mode.MULTIPLY));
        }
        if (botCommandsMenuContainer != null) {
            botCommandsMenuContainer.updateColors();
        }
        if (botKeyboardView != null) {
            botKeyboardView.updateColors();
        }
        audioVideoSendButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.SRC_IN));
        emojiButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.SRC_IN));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            emojiButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }

    }

    private void updateRecordedDeleteIconColors() {
        int dotColor = getThemedColor(Theme.key_chat_recordedVoiceDot);
        int background = getThemedColor(Theme.key_chat_messagePanelBackground);
        int greyColor = getThemedColor(Theme.key_chat_messagePanelVoiceDelete);

        if (recordDeleteImageView != null) {
            recordDeleteImageView.setLayerColor("Cup Red.**", dotColor);
            recordDeleteImageView.setLayerColor("Box Red.**", dotColor);
            recordDeleteImageView.setLayerColor("Cup Grey.**", greyColor);
            recordDeleteImageView.setLayerColor("Box Grey.**", greyColor);

            recordDeleteImageView.setLayerColor("Line 1.**", background);
            recordDeleteImageView.setLayerColor("Line 2.**", background);
            recordDeleteImageView.setLayerColor("Line 3.**", background);
        }
    }

    public void setFieldText(CharSequence text) {
        setFieldText(text, true, false);
    }

    public void setFieldText(CharSequence text, boolean ignoreChange) {
        setFieldText(text, ignoreChange, false);
    }

    public void setFieldText(CharSequence text, boolean ignoreChange, boolean fromDraft) {
        if (messageEditText == null) {
            return;
        }
        ignoreTextChange = ignoreChange;
        messageEditText.setText(text);
        messageEditText.invalidateQuotes(true);
        messageEditText.setSelection(messageEditText.getText().length());
        ignoreTextChange = false;
        if (ignoreChange && delegate != null) {
            delegate.onTextChanged(messageEditText.getText(), true, fromDraft);
        }
    }

    public void setVoiceDraft(MediaDataController.DraftVoice draft) {
        if (draft == null) return;
        voiceOnce = draft.once;
        if (controlsView != null) {
            controlsView.periodDrawable.setValue(1, voiceOnce, true);
        }
        TL_stories.StoryItem storyItem = delegate != null ? delegate.getReplyToStory() : null;
        MediaController.getInstance().prepareResumedRecording(currentAccount, draft, dialog_id, replyingMessageObject, getThreadMessage(), storyItem, recordingGuid, parentFragment != null ? parentFragment.quickReplyShortcut : null, parentFragment != null ? parentFragment.getQuickReplyId() : 0);
    }

    public void setSelection(int start) {
        if (messageEditText == null) {
            return;
        }
        messageEditText.setSelection(start, messageEditText.length());
    }

    public int getCursorPosition() {
        if (messageEditText == null) {
            return 0;
        }
        return messageEditText.getSelectionStart();
    }

    public int getSelectionLength() {
        if (messageEditText == null) {
            return 0;
        }
        try {
            return messageEditText.getSelectionEnd() - messageEditText.getSelectionStart();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public void replaceWithText(int start, int len, CharSequence text, boolean parseEmoji) {
        if (messageEditText == null) {
            return;
        }
        try {
            SpannableStringBuilder builder = new SpannableStringBuilder(messageEditText.getText());
            builder.replace(start, start + len, text);
            if (parseEmoji) {
                Emoji.replaceEmoji(builder, messageEditText.getPaint().getFontMetricsInt(), false, null);
            }
            messageEditText.setText(builder);
            messageEditText.setSelection(start + text.length());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setFieldFocused() {
        AccessibilityManager am = (AccessibilityManager) parentActivity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (messageEditText != null && !am.isTouchExplorationEnabled()) {
            try {
                messageEditText.requestFocus();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void setFieldFocused(boolean focus) {
        AccessibilityManager am = (AccessibilityManager) parentActivity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (messageEditText == null || am.isTouchExplorationEnabled()) {
            return;
        }
        if (focus && BaseFragment.hasSheets(parentFragment)) {
            focus = false;
        }
        if (focus) {
            if (searchingType == 0 && !messageEditText.isFocused()/* && (botWebViewMenuContainer == null || botWebViewMenuContainer.getVisibility() == View.GONE)*/) {
                AndroidUtilities.runOnUIThread(focusRunnable = () -> {
                    focusRunnable = null;
                    boolean allowFocus;
                    if (AndroidUtilities.isTablet()) {
                        if (parentActivity instanceof LaunchActivity) {
                            LaunchActivity launchActivity = (LaunchActivity) parentActivity;
                            View layout = launchActivity != null && launchActivity.getLayersActionBarLayout() != null ? launchActivity.getLayersActionBarLayout().getView() : null;
                            allowFocus = layout == null || layout.getVisibility() != View.VISIBLE;
                        } else {
                            allowFocus = true;
                        }
                    } else {
                        allowFocus = true;
                    }
                    if (!isPaused && allowFocus && messageEditText != null) {
                        try {
                            messageEditText.requestFocus();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }, 600);
            }
        } else {
            if (messageEditText != null && messageEditText.isFocused() && (!keyboardVisible || isPaused)) {
                messageEditText.clearFocus();
            }
        }
    }

    public boolean hasText() {
        return messageEditText != null && messageEditText.length() > 0;
    }

    @Nullable
    public EditTextCaption getEditField() {
        return messageEditText;
    }

    public Editable getEditText() {
        if (messageEditText == null) {
            return null;
        }
        return messageEditText.getText();
    }

    public CharSequence getDraftMessage() {
        if (editingMessageObject != null) {
            return TextUtils.isEmpty(draftMessage) ? null : draftMessage;
        }
        if (messageEditText != null && hasText()) {
            return messageEditText.getText();
        }
        return null;
    }

    public CharSequence getFieldText() {
        if (messageEditText != null && hasText()) {
            return messageEditText.getText();
        }
        return null;
    }

    public void updateGiftButton(boolean animated) {

        boolean visible =
                !MessagesController.getInstance(currentAccount).premiumPurchaseBlocked() &&
                getParentFragment() != null && getParentFragment().getCurrentUser() != null &&
                !BuildVars.IS_BILLING_UNAVAILABLE && !getParentFragment().getCurrentUser().self &&
                getParentFragment().getCurrentUserInfo() != null &&
                (
                    (
                        !getParentFragment().getCurrentUserInfo().premium_gifts.isEmpty() &&
                        !getParentFragment().getCurrentUser().premium &&
                        MessagesController.getInstance(currentAccount).giftAttachMenuIcon &&
                        MessagesController.getInstance(currentAccount).giftTextFieldIcon &&
                        MessagesController.getInstance(currentAccount).getMainSettings().getBoolean("show_gift_for_" + parentFragment.getDialogId(), true)
                    ) || (
                        BirthdayController.isToday(getParentFragment().getCurrentUserInfo().birthday) &&
                        MessagesController.getInstance(currentAccount).getMainSettings().getBoolean(Calendar.getInstance().get(Calendar.YEAR) + "show_gift_for_" + parentFragment.getDialogId(), true)
                    )
                ) &&
                parentFragment != null && parentFragment.getChatMode() == 0;

        if (!visible && birthdayHint != null) {
            birthdayHint.hide();
        }
        if (!visible && giftButton == null) {
            return;
        }
        createGiftButton();

        AndroidUtilities.updateViewVisibilityAnimated(giftButton, visible, 1f, animated);
        if (scheduledButton != null && scheduledButton.getVisibility() == View.VISIBLE) {
            float tX = (visible ? -dp(48) : 0) + dp(botButton != null && botButton.getVisibility() == VISIBLE ? 48 : 0);
            if (animated) {
                scheduledButton.animate().translationX(tX).setDuration(150).start();
            } else {
                scheduledButton.setTranslationX(tX);
            }
        }
        if (visible) {
            checkBirthdayHint();
        }
    }

    private void checkBirthdayHint() {
        if (
            birthdayHint == null && giftButton != null && giftButton.getRight() != 0 &&
            parentFragment != null && BirthdayController.isToday(parentFragment.getCurrentUserInfo()) &&
            MessagesController.getInstance(currentAccount).getMainSettings().getBoolean(Calendar.getInstance().get(Calendar.YEAR) + "bdayhint_" + parentFragment.getDialogId(), true)
        ) {
            MessagesController.getInstance(currentAccount).getMainSettings().edit().putBoolean(Calendar.getInstance().get(Calendar.YEAR) + "bdayhint_" + parentFragment.getDialogId(), false).apply();

            birthdayHint = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM);
            birthdayHint.setRounding(13);
            birthdayHint.setMultilineText(true);
            setBirthdayHintText();
            birthdayHint.setPadding(dp(12), 0, dp(12), 0);
            birthdayHint.setJointPx(1f, -(getWidth() - dp(12) - (messageEditTextContainer.getX() + attachLayout.getX() + giftButton.getX() + giftButton.getMeasuredWidth() / 2f)));
            addView(birthdayHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.TOP, 0, -200 + 8, 0, 0));
            birthdayHint.setOnHiddenListener(() -> removeView(birthdayHint));
            birthdayHint.setDuration(8000);
            birthdayHint.show();
        }
    }

    private void setBirthdayHintText() {
        if (birthdayHint == null) return;
        birthdayHint.setText(Emoji.replaceWithRestrictedEmoji(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBirthdayHint, UserObject.getFirstName(parentFragment.getCurrentUser()))), birthdayHint.getTextPaint().getFontMetricsInt(), this::setBirthdayHintText));
        birthdayHint.setMaxWidthPx(HintView2.cutInFancyHalf(birthdayHint.getText(), birthdayHint.getTextPaint()));
    }

    public void hideHints() {
        if (birthdayHint != null) {
            birthdayHint.hide();
        }
    }

    public void updateScheduleButton(boolean animated) {
        boolean notifyVisible = false;
        if (DialogObject.isChatDialog(dialog_id)) {
            TLRPC.Chat currentChat = accountInstance.getMessagesController().getChat(-dialog_id);
            silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + dialog_id, false);
            canWriteToChannel = ChatObject.isChannel(currentChat) && (currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.post_messages) && !currentChat.megagroup;
            if (notifyButton != null) {
                notifyVisible = canWriteToChannel;
                if (notifySilentDrawable == null) {
                    notifySilentDrawable = new CrossOutDrawable(getContext(), R.drawable.input_notify_on, Theme.key_chat_messagePanelIcons);
                }
                notifySilentDrawable.setCrossOut(silent, false);
                notifyButton.setImageDrawable(notifySilentDrawable);
            }
            if (attachLayout != null) {
                updateFieldRight(attachLayout.getVisibility() == VISIBLE ? 1 : 0);
            }
        }
        boolean hasScheduled = delegate != null && !isInScheduleMode() && delegate.hasScheduledMessages();
        boolean visible = hasScheduled && !scheduleButtonHidden && !recordingAudioVideo;
        if (visible) {
            createScheduledButton();
        }
        if (scheduledButton != null) {
            if (scheduledButton.getTag() != null && visible || scheduledButton.getTag() == null && !visible) {
                if (notifyButton != null) {
                    int newVisibility = !hasScheduled && notifyVisible && scheduledButton.getVisibility() != VISIBLE ? VISIBLE : GONE;
                    if (newVisibility != notifyButton.getVisibility()) {
                        notifyButton.setVisibility(newVisibility);
                        if (attachLayout != null) {
                            attachLayout.setPivotX(dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
                        }
                    }
                }
                return;
            }
            scheduledButton.setTag(visible ? 1 : null);
        } else if (notifyButton != null) {
            int newVisibility = !hasScheduled && notifyVisible ? VISIBLE : GONE;
            if (newVisibility != notifyButton.getVisibility()) {
                notifyButton.setVisibility(newVisibility);
                if (attachLayout != null) {
                    attachLayout.setPivotX(dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
                }
            }
        }
        if (scheduledButtonAnimation != null) {
            scheduledButtonAnimation.cancel();
            scheduledButtonAnimation = null;
        }
        if (!animated || notifyVisible) {
            if (scheduledButton != null) {
                scheduledButton.setVisibility(visible ? VISIBLE : GONE);
                scheduledButton.setAlpha(visible ? 1.0f : 0.0f);
                scheduledButton.setScaleX(visible ? 1.0f : 0.1f);
                scheduledButton.setScaleY(visible ? 1.0f : 0.1f);
                if (notifyButton != null) {
                    notifyButton.setVisibility(notifyVisible && scheduledButton.getVisibility() != VISIBLE ? VISIBLE : GONE);
                }
                if (giftButton != null && giftButton.getVisibility() == VISIBLE) {
                    scheduledButton.setTranslationX(-dp(48));
                }
            } else if (notifyButton != null) {
                notifyButton.setVisibility(notifyVisible ? VISIBLE : GONE);
            }
        } else if (scheduledButton != null) {
            if (visible) {
                scheduledButton.setVisibility(VISIBLE);
            }
            scheduledButton.setPivotX(dp(24));
            scheduledButtonAnimation = new AnimatorSet();
            scheduledButtonAnimation.playTogether(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, visible ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, visible ? 1.0f : 0.1f),
                    ObjectAnimator.ofFloat(scheduledButton, View.SCALE_Y, visible ? 1.0f : 0.1f));
            scheduledButtonAnimation.setDuration(180);
            scheduledButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    scheduledButtonAnimation = null;
                    if (!visible) {
                        scheduledButton.setVisibility(GONE);
                    }
                }
            });
            scheduledButtonAnimation.start();
        }
        if (attachLayout != null) {
            attachLayout.setPivotX(dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
        }
    }

    public void updateSendAsButton() {
        updateSendAsButton(true);
    }

    public void updateSendAsButton(boolean animated) {
        if (parentFragment == null || delegate == null) {
            return;
        }
        createMessageEditText();
        TLRPC.Chat chat = parentFragment.getMessagesController().getChat(-dialog_id);
        TLRPC.ChatFull full = parentFragment.getMessagesController().getChatFull(-dialog_id);
        TLRPC.Peer defPeer = full != null ? full.default_send_as : null;
        if (defPeer == null && delegate.getSendAsPeers() != null && !delegate.getSendAsPeers().peers.isEmpty()) {
            defPeer = delegate.getSendAsPeers().peers.get(0).peer;
        }
        boolean isVisible = defPeer != null && (delegate.getSendAsPeers() == null || delegate.getSendAsPeers().peers.size() > 1) &&
            !isEditingMessage() && !isRecordingAudioVideo() && (recordedAudioPanel == null || recordedAudioPanel.getVisibility() != View.VISIBLE) &&
            (!ChatObject.isChannelAndNotMegaGroup(chat) || ChatObject.canSendAsPeers(chat));
        if (isVisible) {
            createSenderSelectView();
        }
        if (defPeer != null) {
            if (defPeer.channel_id != 0) {
                TLRPC.Chat ch = MessagesController.getInstance(currentAccount).getChat(defPeer.channel_id);
                if (ch != null && senderSelectView != null) {
                    senderSelectView.setAvatar(ch);
                    senderSelectView.setContentDescription(LocaleController.formatString(R.string.AccDescrSendAs, ch.title));
                }
            } else {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(defPeer.user_id);
                if (user != null && senderSelectView != null) {
                    senderSelectView.setAvatar(user);
                    senderSelectView.setContentDescription(LocaleController.formatString(R.string.AccDescrSendAs, ContactsController.formatName(user.first_name, user.last_name)));
                }
            }
        }
        boolean wasVisible = senderSelectView != null && senderSelectView.getVisibility() == View.VISIBLE;
        int pad = dp(2);
        float startAlpha = isVisible ? 0 : 1;
        float endAlpha = isVisible ? 1 : 0;
        final float startX, endX;
        if (senderSelectView != null) {
            MarginLayoutParams params = (MarginLayoutParams) senderSelectView.getLayoutParams();
            startX = isVisible ? -senderSelectView.getLayoutParams().width - params.leftMargin - pad : 0;
            endX = isVisible ? 0 : -senderSelectView.getLayoutParams().width - params.leftMargin - pad;
        } else {
            startX = endX = 0;
        }

        if (wasVisible != isVisible) {
            ValueAnimator animator = senderSelectView == null ? null : (ValueAnimator) senderSelectView.getTag();
            if (animator != null) {
                animator.cancel();
                senderSelectView.setTag(null);
            }

            if (parentFragment.getOtherSameChatsDiff() == 0 && parentFragment.fragmentOpened && animated) {
                ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(150);
                if (senderSelectView != null) {
                    senderSelectView.setTranslationX(startX);
                }
                messageTextTranslationX = startX;
                updateMessageTextParams();
                anim.addUpdateListener(animation -> {
                    final float val = (float) animation.getAnimatedValue();
                    final float tx = startX + (endX - startX) * val;
                    if (senderSelectView != null) {
                        senderSelectView.setAlpha(startAlpha + (endAlpha - startAlpha) * val);
                        senderSelectView.setTranslationX(tx);
                    }
                    emojiButton.setTranslationX(tx);
                    messageTextTranslationX = tx;
                    updateMessageTextParams();
                });
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (isVisible) {
                            createSenderSelectView();
                            senderSelectView.setVisibility(VISIBLE);
                        }
                        float tx = 0;
                        if (senderSelectView != null) {
                            senderSelectView.setAlpha(startAlpha);
                            senderSelectView.setTranslationX(startX);
                            tx = senderSelectView.getTranslationX();
                        }
                        emojiButton.setTranslationX(tx);
                        messageTextTranslationX = tx;
                        updateMessageTextParams();

                        if (botCommandsMenuButton != null && botCommandsMenuButton.getTag() == null) {
                            animationParamsX.clear();
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!isVisible) {
                            if (senderSelectView != null) {
                                senderSelectView.setVisibility(GONE);
                            }
                            emojiButton.setTranslationX(0);
                            messageTextTranslationX = 0;
                            updateMessageTextParams();
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        float tx = 0;
                        if (isVisible) {
                            createSenderSelectView();
                        }
                        if (senderSelectView != null) {
                            senderSelectView.setVisibility(isVisible ? VISIBLE : GONE);
                            senderSelectView.setAlpha(endAlpha);
                            senderSelectView.setTranslationX(endX);
                            tx = senderSelectView.getTranslationX();
                        }
                        emojiButton.setTranslationX(tx);
                        messageTextTranslationX = tx;
                        updateMessageTextParams();
                        requestLayout();
                    }
                });
                anim.start();
                if (senderSelectView != null) {
                    senderSelectView.setTag(anim);
                }
            } else {
                if (isVisible) {
                    createSenderSelectView();
                }
                if (senderSelectView != null) {
                    senderSelectView.setVisibility(isVisible ? VISIBLE : GONE);
                    senderSelectView.setTranslationX(endX);
                }
                float translationX = isVisible ? endX : 0;
                emojiButton.setTranslationX(translationX);
                messageTextTranslationX = translationX;
                updateMessageTextParams();
                if (senderSelectView != null) {
                    senderSelectView.setAlpha(endAlpha);
                    senderSelectView.setTag(null);
                }
            }
        }
    }

    public boolean onBotWebViewBackPressed() {
        return false;
//        return botWebViewMenuContainer != null && botWebViewMenuContainer.onBackPressed();
    }

    public boolean hasBotWebView() {
        return botMenuButtonType == BotMenuButtonType.WEB_VIEW;
    }

    private void updateBotButton(boolean animated) {
        if (!isChat) {
            return;
        }
        if (parentFragment != null && !parentFragment.openAnimationEnded) {
            animated = false;
        }
        boolean hasBotWebView = hasBotWebView();
        boolean canShowBotsMenu = botMenuButtonType != BotMenuButtonType.NO_BUTTON && dialog_id > 0;
        boolean wasVisible = botButton != null && botButton.getVisibility() == VISIBLE;
        if (hasBotWebView || hasBotCommands || hasQuickReplies || botReplyMarkup != null) {
            if (botReplyMarkup != null) {
                if (isPopupShowing() && currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD && botReplyMarkup.is_persistent) {
                    if (botButton != null && botButton.getVisibility() != GONE) {
                        botButton.setVisibility(GONE);
                    }
                } else {
                    createBotButton();
                    if (botButton.getVisibility() != VISIBLE) {
                        botButton.setVisibility(VISIBLE);
                    }

                    botButtonDrawable.setIcon(R.drawable.input_bot2, true);
                    botButton.setContentDescription(getString("AccDescrBotKeyboard", R.string.AccDescrBotKeyboard));
                }
            } else {
                if (!canShowBotsMenu) {
                    createBotButton();
                    botButtonDrawable.setIcon(R.drawable.input_bot1, true);
                    botButton.setContentDescription(getString("AccDescrBotCommands", R.string.AccDescrBotCommands));
                    botButton.setVisibility(VISIBLE);
                } else if (botButton != null) {
                    botButton.setVisibility(GONE);
                }
            }
        } else if (botButton != null) {
            botButton.setVisibility(GONE);
        }
        if (canShowBotsMenu) {
            createBotCommandsMenuButton();
        }
        boolean changed = (botButton != null && botButton.getVisibility() == VISIBLE) != wasVisible;
        if (botCommandsMenuButton != null) {
            boolean wasWebView = botCommandsMenuButton.isWebView;
            botCommandsMenuButton.setWebView(botMenuButtonType == BotMenuButtonType.WEB_VIEW);
            boolean textChanged = botCommandsMenuButton.setMenuText(botMenuButtonType == BotMenuButtonType.COMMANDS ? getString(R.string.BotsMenuTitle) : botMenuWebViewTitle);
            AndroidUtilities.updateViewVisibilityAnimated(botCommandsMenuButton, canShowBotsMenu, 0.5f, animated);
            changed = changed || textChanged || wasWebView != botCommandsMenuButton.isWebView;
        }
        if (changed && animated) {
            beginDelayedTransition();

            boolean show = botButton != null && botButton.getVisibility() == VISIBLE;
            if (show != wasVisible && botButton != null) {
                botButton.setVisibility(VISIBLE);
                if (show) {
                    botButton.setAlpha(0f);
                    botButton.setScaleX(0.1f);
                    botButton.setScaleY(0.1f);
                } else if (!show) {
                    botButton.setAlpha(1f);
                    botButton.setScaleX(1f);
                    botButton.setScaleY(1f);
                }
                AndroidUtilities.updateViewVisibilityAnimated(botButton, show, 0.1f, true);
            }
        }
        updateFieldRight(2);
        attachLayout.setPivotX(dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
    }

    public boolean isRtlText() {
        try {
            return messageEditText != null && messageEditText.getLayout().getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT;
        } catch (Throwable ignore) {

        }
        return false;
    }

    public void updateBotWebView(boolean animated) {
        if (botMenuButtonType != BotMenuButtonType.NO_BUTTON && dialog_id > 0) {
            createBotCommandsMenuButton();
        }
        if (botCommandsMenuButton != null) {
            botCommandsMenuButton.setWebView(hasBotWebView());
        }
        updateBotButton(animated);
    }

    public void setBotsCount(int count, boolean hasCommands, boolean hasQuickReplies, boolean animated) {
        botCount = count;
        if (hasBotCommands != hasCommands || this.hasQuickReplies != hasQuickReplies) {
            hasBotCommands = hasCommands;
            this.hasQuickReplies = hasQuickReplies;
            updateBotButton(animated);
        }
    }

    public void setButtons(MessageObject messageObject) {
        setButtons(messageObject, true);
    }

    public void setButtons(MessageObject messageObject, boolean openKeyboard) {
        if (replyingMessageObject != null && replyingMessageObject == botButtonsMessageObject && replyingMessageObject != messageObject) {
            botMessageObject = messageObject;
            return;
        }
        if (botButtonsMessageObject != null && botButtonsMessageObject == messageObject || botButtonsMessageObject == null && messageObject == null) {
            return;
        }
        if (botKeyboardView == null) {
            botKeyboardView = new BotKeyboardView(parentActivity, resourcesProvider) {
                @Override
                public void setTranslationY(float translationY) {
                    super.setTranslationY(translationY);
                    if (panelAnimation != null && animatingContentType == 1) {
                        delegate.bottomPanelTranslationYChanged(translationY);
                    }
                }
            };
            botKeyboardView.setVisibility(GONE);
            botKeyboardViewVisible = false;
            botKeyboardView.setDelegate(button -> {
                boolean replyingIsTopicStarter = replyingMessageObject != null && parentFragment != null && parentFragment.isTopic && parentFragment.getTopicId() == replyingMessageObject.getId();
                MessageObject object = replyingMessageObject != null && !replyingIsTopicStarter ? replyingMessageObject : (DialogObject.isChatDialog(dialog_id) ? botButtonsMessageObject : null);
                boolean open = didPressedBotButton(button, object, replyingMessageObject != null && !replyingIsTopicStarter ? replyingMessageObject : botButtonsMessageObject);
                if (replyingMessageObject != null && !replyingIsTopicStarter) {
                    openKeyboardInternal();
                    setButtons(botMessageObject, false);
                } else if (botButtonsMessageObject != null && botButtonsMessageObject.messageOwner.reply_markup.single_use) {
                    if (open) {
                        openKeyboardInternal();
                    } else {
                        showPopup(0, 0);
                    }
                    SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
                    preferences.edit().putInt("answered_" + getTopicKeyString(), botButtonsMessageObject.getId()).commit();
                }
                if (delegate != null) {
                    delegate.onMessageSend(null, true, 0);
                }
            });
            sizeNotifierLayout.addView(botKeyboardView, sizeNotifierLayout.getChildCount() - 1);
        }
        botButtonsMessageObject = messageObject;
        botReplyMarkup = messageObject != null && messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyKeyboardMarkup ? (TLRPC.TL_replyKeyboardMarkup) messageObject.messageOwner.reply_markup : null;

        botKeyboardView.setPanelHeight(AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight);

        if (botReplyMarkup != null) {
            SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
            boolean showPopup = true;
            if (botButtonsMessageObject != replyingMessageObject) {
                if (messageObject != null && (
                        botReplyMarkup.single_use && preferences.getInt("answered_" + getTopicKeyString(), 0) == messageObject.getId() ||
                        !botReplyMarkup.is_persistent && preferences.getInt("closed_botkeyboard_" + getTopicKeyString(), 0) == messageObject.getId()
                )) {
                    showPopup = false;
                }
            }
            botKeyboardView.setButtons(botReplyMarkup);
            if (showPopup && (messageEditText == null || messageEditText.length() == 0) && !isPopupShowing()) {
                showPopup(1, 1);
            }
        } else {
            if (isPopupShowing() && currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD) {
                if (openKeyboard) {
                    clearBotButtonsOnKeyboardOpen = true;
                    openKeyboardInternal();
                } else {
                    showPopup(0, 1);
                }
            }
        }
        updateBotButton(true);
    }

    public boolean didPressedBotButton(final TLRPC.KeyboardButton button, final MessageObject replyMessageObject, final MessageObject messageObject) {
        return didPressedBotButton(button, replyMessageObject, messageObject, null);
    }

    public boolean didPressedBotButton(final TLRPC.KeyboardButton button, final MessageObject replyMessageObject, final MessageObject messageObject, final Browser.Progress progress) {
        if (button == null || messageObject == null) {
            return false;
        }
        if (parentFragment != null && parentFragment.getChatMode() == ChatActivity.MODE_QUICK_REPLIES) return false;
        if (button instanceof TLRPC.TL_keyboardButtonCopy) {
            final TLRPC.TL_keyboardButtonCopy btn = (TLRPC.TL_keyboardButtonCopy) button;
            AndroidUtilities.addToClipboard(btn.copy_text);
            BulletinFactory.of(parentFragment).createCopyBulletin(LocaleController.formatString(R.string.ExactTextCopied, btn.copy_text)).show(true);
        } else if (button instanceof TLRPC.TL_keyboardButton) {
            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(button.text, dialog_id, replyMessageObject, getThreadMessage(), null, false, null, null, null, true, 0, null, false);
            params.quick_reply_shortcut = parentFragment != null ? parentFragment.quickReplyShortcut : null;
            params.quick_reply_shortcut_id = parentFragment != null ? parentFragment.getQuickReplyId() : 0;
            params.effect_id = effectId;
            sendButton.setEffect(effectId = 0);
            SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
        } else if (button instanceof TLRPC.TL_keyboardButtonUrl) {
            if (Browser.urlMustNotHaveConfirmation(button.url)) {
                Browser.openUrl(parentActivity, Uri.parse(button.url), true, true, progress);
            } else {
                AlertsCreator.showOpenUrlAlert(parentFragment, button.url, false, true, true, progress, resourcesProvider);
            }
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestPhone) {
            parentFragment.shareMyContact(2, messageObject);
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestPoll) {
            parentFragment.openPollCreate((button.flags & 1) != 0 ? button.quiz : null);
            return false;
        } else if (button instanceof TLRPC.TL_keyboardButtonWebView || button instanceof TLRPC.TL_keyboardButtonSimpleWebView) {
            long botId = messageObject.messageOwner.via_bot_id != 0 ? messageObject.messageOwner.via_bot_id : messageObject.messageOwner.from_id.user_id;
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(botId);
            Runnable onRequestWebView = new Runnable() {
                @Override
                public void run() {
                    if (sizeNotifierLayout.measureKeyboardHeight() > dp(20) || isPopupShowing()) {
                        hidePopup(false);
                        AndroidUtilities.hideKeyboard(ChatActivityEnterView.this);
                        AndroidUtilities.runOnUIThread(this, 150);
                        return;
                    }

                    if (parentFragment == null) {
                        return;
                    }

                    final WebViewRequestProps props = WebViewRequestProps.of(currentAccount, messageObject.messageOwner.dialog_id, botId, button.text, button.url, button instanceof TLRPC.TL_keyboardButtonSimpleWebView ? BotWebViewAttachedSheet.TYPE_SIMPLE_WEB_VIEW_BUTTON : BotWebViewAttachedSheet.TYPE_WEB_VIEW_BUTTON, replyMessageObject != null ? replyMessageObject.messageOwner.id : 0, false, null, false, null, null, 0, false, false);
                    if (LaunchActivity.instance != null && LaunchActivity.instance.getBottomSheetTabs() != null && LaunchActivity.instance.getBottomSheetTabs().tryReopenTab(props) != null) {
                        if (botCommandsMenuButton != null) {
                            botCommandsMenuButton.setOpened(false);
                        }
                        return;
                    }
//                    if (AndroidUtilities.isTablet() || true) {
                        BotWebViewSheet webViewSheet = new BotWebViewSheet(getContext(), resourcesProvider);
                        webViewSheet.setParentActivity(parentActivity);
                        webViewSheet.requestWebView(parentFragment, props);
                        webViewSheet.show();
//                    } else {
//                        BotWebViewAttachedSheet webViewSheet = parentFragment.createBotViewer();
//                        webViewSheet.setDefaultFullsize(false);
//                        webViewSheet.setNeedsContext(true);
//                        webViewSheet.setParentActivity(parentActivity);
//                        webViewSheet.requestWebView(parentFragment, props);
//                        webViewSheet.show();
//                    }
                }
            };
            if (SharedPrefsHelper.isWebViewConfirmShown(currentAccount, botId)) {
                onRequestWebView.run();
            } else {
                AlertsCreator.createBotLaunchAlert(parentFragment, MessagesController.getInstance(currentAccount).getUser(dialog_id), () -> {
                    onRequestWebView.run();
                    SharedPrefsHelper.setWebViewConfirmShown(currentAccount, botId, true);
                }, null);
            }
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setTitle(getString("ShareYouLocationTitle", R.string.ShareYouLocationTitle));
            builder.setMessage(getString("ShareYouLocationInfo", R.string.ShareYouLocationInfo));
            builder.setPositiveButton(getString("OK", R.string.OK), (dialogInterface, i) -> {
                if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    parentActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                    pendingMessageObject = messageObject;
                    pendingLocationButton = button;
                    return;
                }
                SendMessagesHelper.getInstance(currentAccount).sendCurrentLocation(messageObject, button);
            });
            builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
            parentFragment.showDialog(builder.create());
        } else if (button instanceof TLRPC.TL_keyboardButtonCallback || button instanceof TLRPC.TL_keyboardButtonGame || button instanceof TLRPC.TL_keyboardButtonBuy || button instanceof TLRPC.TL_keyboardButtonUrlAuth) {
            SendMessagesHelper.getInstance(currentAccount).sendCallback(true, messageObject, button, parentFragment);
        } else if (button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
            if (parentFragment.processSwitchButton((TLRPC.TL_keyboardButtonSwitchInline) button)) {
                return true;
            }
            if (button.same_peer) {
                long uid = messageObject.messageOwner.from_id.user_id;
                if (messageObject.messageOwner.via_bot_id != 0) {
                    uid = messageObject.messageOwner.via_bot_id;
                }
                TLRPC.User user = accountInstance.getMessagesController().getUser(uid);
                if (user == null) {
                    return true;
                }
                setFieldText("@" + UserObject.getPublicUsername(user) + " " + button.query);
            } else {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_BOT_SHARE);

                if ((button.flags & 2) != 0) {
                    args.putBoolean("allowGroups", false);
                    args.putBoolean("allowMegagroups", false);
                    args.putBoolean("allowLegacyGroups", false);
                    args.putBoolean("allowUsers", false);
                    args.putBoolean("allowChannels", false);
                    args.putBoolean("allowBots", false);
                    for (TLRPC.InlineQueryPeerType peerType : button.peer_types) {
                        if (peerType instanceof TLRPC.TL_inlineQueryPeerTypePM) {
                            args.putBoolean("allowUsers", true);
                        } else if (peerType instanceof TLRPC.TL_inlineQueryPeerTypeBotPM) {
                            args.putBoolean("allowBots", true);
                        } else if (peerType instanceof TLRPC.TL_inlineQueryPeerTypeBroadcast) {
                            args.putBoolean("allowChannels", true);
                        } else if (peerType instanceof TLRPC.TL_inlineQueryPeerTypeChat) {
                            args.putBoolean("allowLegacyGroups", true);
                        } else if (peerType instanceof TLRPC.TL_inlineQueryPeerTypeMegagroup) {
                            args.putBoolean("allowMegagroups", true);
                        }
                    }
                }

                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, topicsFragment) -> {
                    long uid = messageObject.messageOwner.from_id.user_id;
                    if (messageObject.messageOwner.via_bot_id != 0) {
                        uid = messageObject.messageOwner.via_bot_id;
                    }
                    TLRPC.User user = accountInstance.getMessagesController().getUser(uid);
                    if (user == null) {
                        fragment1.finishFragment();
                        return true;
                    }
                    long did = dids.get(0).dialogId;
                    MediaDataController.getInstance(currentAccount).saveDraft(did, 0, "@" + UserObject.getPublicUsername(user) + " " + button.query, null, null, true, 0);
                    if (did != dialog_id) {
                        if (!DialogObject.isEncryptedDialog(did)) {
                            Bundle args1 = new Bundle();
                            if (DialogObject.isUserDialog(did)) {
                                args1.putLong("user_id", did);
                            } else {
                                args1.putLong("chat_id", -did);
                            }
                            if (!accountInstance.getMessagesController().checkCanOpenChat(args1, fragment1)) {
                                return true;
                            }
                            ChatActivity chatActivity = new ChatActivity(args1);
                            if (parentFragment.presentFragment(chatActivity, true)) {
                                if (!AndroidUtilities.isTablet()) {
                                    parentFragment.removeSelfFromStack();
                                }
                            } else {
                                fragment1.finishFragment();
                            }
                        } else {
                            fragment1.finishFragment();
                        }
                    } else {
                        fragment1.finishFragment();
                    }
                    return true;
                });
                parentFragment.presentFragment(fragment);
            }
        } else if (button instanceof TLRPC.TL_keyboardButtonUserProfile) {
            if (MessagesController.getInstance(currentAccount).getUser(button.user_id) != null) {
                Bundle args = new Bundle();
                args.putLong("user_id", button.user_id);
                ProfileActivity fragment = new ProfileActivity(args);
                parentFragment.presentFragment(fragment);
            }
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestPeer) {
            TLRPC.TL_keyboardButtonRequestPeer btn = (TLRPC.TL_keyboardButtonRequestPeer) button;
            if (btn.peer_type != null && messageObject != null && messageObject.messageOwner != null) {
                if (btn.peer_type instanceof TLRPC.TL_requestPeerTypeUser && btn.max_quantity > 1) {
                    MultiContactsSelectorBottomSheet.open(btn.max_quantity, ids -> {
                        if (ids != null && !ids.isEmpty()) {
                            TLRPC.TL_messages_sendBotRequestedPeer req = new TLRPC.TL_messages_sendBotRequestedPeer();
                            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.messageOwner.peer_id);
                            req.msg_id = messageObject.getId();
                            req.button_id = btn.button_id;
                            for (Long id : ids) {
                                req.requested_peers.add(MessagesController.getInstance(currentAccount).getInputPeer(id));
                            }
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                        }
                    });
                    return false;
                }
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER);
                if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.from_id instanceof TLRPC.TL_peerUser) {
                    args.putLong("requestPeerBotId", messageObject.messageOwner.from_id.user_id);
                }
                try {
                    SerializedData buffer = new SerializedData(btn.peer_type.getObjectSize());
                    btn.peer_type.serializeToStream(buffer);
                    args.putByteArray("requestPeerType", buffer.toByteArray());
                    buffer.cleanup();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate((dialogFragment, dids, message, param, notify, scheduleDate, topicsFragment) -> {
                    if (dids != null && !dids.isEmpty()) {
                        TLRPC.TL_messages_sendBotRequestedPeer req = new TLRPC.TL_messages_sendBotRequestedPeer();
                        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.messageOwner.peer_id);
                        req.msg_id = messageObject.getId();
                        req.button_id = btn.button_id;
                        req.requested_peers.add(MessagesController.getInstance(currentAccount).getInputPeer(dids.get(0).dialogId));
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                    }
                    dialogFragment.finishFragment();
                    return true;
                });
                parentFragment.presentFragment(fragment);
                return false;
            } else {
                FileLog.e("button.peer_type is null");
            }
        }
        return true;
    }

    public boolean isPopupView(View view) {
        return view == botKeyboardView || view == emojiView;
    }

    public int getPopupViewHeight(View view) {
        if (view == botKeyboardView && botKeyboardView != null) {
            return botKeyboardView.getKeyboardHeight();
        }
        return -1;
    }

    public boolean isRecordCircle(View view) {
        return view == recordCircle;
    }

    public SizeNotifierFrameLayout getSizeNotifierLayout() {
        return sizeNotifierLayout;
    }

    private void createEmojiView() {
        if (emojiView != null && emojiView.currentAccount != UserConfig.selectedAccount) {
            sizeNotifierLayout.removeView(emojiView);
            emojiView = null;
        }
        if (emojiView != null) {
            return;
        }
        emojiView = new EmojiView(parentFragment, allowAnimatedEmoji, true, true, getContext(), true, info, sizeNotifierLayout, shouldDrawBackground, resourcesProvider, emojiViewFrozen) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                if (panelAnimation != null && animatingContentType == 0) {
                    delegate.bottomPanelTranslationYChanged(translationY);
                }
            }
        };
        if (!shouldDrawBackground) {
            emojiView.updateColors();
        }
        emojiView.setAllow(allowStickers, allowGifs, true);
        emojiView.setVisibility(GONE);
        emojiView.setShowing(false);
        emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {

            @Override
            public boolean isUserSelf() {
                return dialog_id == UserConfig.getInstance(currentAccount).getClientUserId();
            }

            @Override
            public boolean onBackspace() {
                if (messageEditText == null || messageEditText.length() == 0) {
                    return false;
                }
                messageEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                return true;
            }

            @Override
            public void onEmojiSelected(String symbol) {
                if (messageEditText == null) {
                    return;
                }
                int i = messageEditText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    innerTextChange = 2;
                    CharSequence localCharSequence = Emoji.replaceEmoji(symbol, messageEditText.getPaint().getFontMetricsInt(), false, null);
                    messageEditText.setText(messageEditText.getText().insert(i, localCharSequence));
                    int j = i + localCharSequence.length();
                    messageEditText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    innerTextChange = 0;
                }
            }

            public void onCustomEmojiSelected(long documentId, TLRPC.Document document,  String emoticon, boolean isRecent) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (messageEditText == null) {
                        return;
                    }
                    int i = messageEditText.getSelectionEnd();
                    if (i < 0) {
                        i = 0;
                    }
                    try {
                        innerTextChange = 2;
                        SpannableString emoji = new SpannableString(emoticon == null ? "\uD83D\uDE00" : emoticon);
                        AnimatedEmojiSpan span;
                        if (document != null) {
                            span = new AnimatedEmojiSpan(document, messageEditText.getPaint().getFontMetricsInt());
                        } else {
                            span = new AnimatedEmojiSpan(documentId, messageEditText.getPaint().getFontMetricsInt());
                        }
                        if (!isRecent) {
                            span.fromEmojiKeyboard = true;
                        }
                        span.cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView();
                        emoji.setSpan(span, 0, emoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        messageEditText.setText(messageEditText.getText().insert(i, emoji));
                        messageEditText.setSelection(i + emoji.length(), i + emoji.length());
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        innerTextChange = 0;
                    }
                });
            }

            @Override
            public void onAnimatedEmojiUnlockClick() {
                BaseFragment fragment = parentFragment;
                if (fragment == null) {
                    fragment = getLastFragment();
                }
                BottomSheet alert = new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false);
                if (fragment != null) {
                    fragment.showDialog(alert);
                } else {
                    alert.show();
                }
            }

            @Override
            public void onStickerSelected(View view, TLRPC.Document sticker, String query, Object parent, MessageObject.SendAnimationData sendAnimationData, boolean notify, int scheduleDate) {
                if (trendingStickersAlert != null) {
                    trendingStickersAlert.dismiss();
                    trendingStickersAlert = null;
                }
                if (slowModeTimer > 0 && !isInScheduleMode()) {
                    if (delegate != null) {
                        delegate.onUpdateSlowModeButton(view != null ? view : slowModeButton, true, slowModeButton.getText());
                    }
                    return;
                }
                if (stickersExpanded) {
                    if (searchingType != 0) {
                        setSearchingTypeInternal(0, true);
                        emojiView.closeSearch(true, MessageObject.getStickerSetId(sticker));
                        emojiView.hideSearchKeyboard();
                    }
                    setStickersExpanded(false, true, false);
                }
                ChatActivityEnterView.this.onStickerSelected(sticker, query, parent, sendAnimationData, false, notify, scheduleDate);
                if (DialogObject.isEncryptedDialog(dialog_id) && MessageObject.isGifDocument(sticker)) {
                    accountInstance.getMessagesController().saveGif(parent, sticker);
                }
            }

            @Override
            public void onStickersSettingsClick() {
                if (parentFragment != null) {
                    parentFragment.presentFragment(new StickersActivity(MediaDataController.TYPE_IMAGE, null));
                }
            }

            @Override
            public void onEmojiSettingsClick(ArrayList<TLRPC.TL_messages_stickerSet> frozenEmojiPacks) {
                if (parentFragment != null) {
                    parentFragment.presentFragment(new StickersActivity(MediaDataController.TYPE_EMOJIPACKS, frozenEmojiPacks));
                }
            }

            @Override
            public void onGifSelected(View view, Object gif, String query, Object parent, boolean notify, int scheduleDate) {
                if (replyingQuote != null && parentFragment != null && replyingQuote.outdated) {
                    parentFragment.showQuoteMessageUpdate();
                    return;
                }
                if (isInScheduleMode() && scheduleDate == 0) {
                    AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), (n, s) -> onGifSelected(view, gif, query, parent, n, s), resourcesProvider);
                } else {
                    if (slowModeTimer > 0 && !isInScheduleMode()) {
                        if (delegate != null) {
                            delegate.onUpdateSlowModeButton(view != null ? view : slowModeButton, true, slowModeButton.getText());
                        }
                        return;
                    }
                    Runnable runnable = () -> {

                        if (stickersExpanded) {
                            if (searchingType != 0) {
                                emojiView.hideSearchKeyboard();
                            }
                            setStickersExpanded(false, true, false);
                        }
                        TL_stories.StoryItem storyItem = delegate != null ? delegate.getReplyToStory() : null;
                        if (gif instanceof TLRPC.Document) {
                            TLRPC.Document document = (TLRPC.Document) gif;
                            SendMessagesHelper.getInstance(currentAccount).sendSticker(document, query, dialog_id, replyingMessageObject, getThreadMessage(), storyItem, replyingQuote, null, notify, scheduleDate, false, parent, parentFragment != null ? parentFragment.quickReplyShortcut : null, parentFragment != null ? parentFragment.getQuickReplyId() : 0);
                            MediaDataController.getInstance(currentAccount).addRecentGif(document, (int) (System.currentTimeMillis() / 1000), true);
                            if (DialogObject.isEncryptedDialog(dialog_id)) {
                                accountInstance.getMessagesController().saveGif(parent, document);
                            }
                        } else if (gif instanceof TLRPC.BotInlineResult) {
                            TLRPC.BotInlineResult result = (TLRPC.BotInlineResult) gif;

                            if (result.document != null) {
                                MediaDataController.getInstance(currentAccount).addRecentGif(result.document, (int) (System.currentTimeMillis() / 1000), false);
                                if (DialogObject.isEncryptedDialog(dialog_id)) {
                                    accountInstance.getMessagesController().saveGif(parent, result.document);
                                }
                            }

                            TLRPC.User bot = (TLRPC.User) parent;

                            HashMap<String, String> params = new HashMap<>();
                            params.put("id", result.id);
                            params.put("query_id", "" + result.query_id);
                            params.put("force_gif", "1");

                            if (storyItem == null) {
                                SendMessagesHelper.prepareSendingBotContextResult(parentFragment, accountInstance, result, params, dialog_id, replyingMessageObject, getThreadMessage(), null, replyingQuote, notify, scheduleDate, parentFragment != null ? parentFragment.quickReplyShortcut : null, parentFragment != null ? parentFragment.getQuickReplyId() : 0);
                            } else {
                                SendMessagesHelper.getInstance(currentAccount).sendSticker(result.document, query, dialog_id, replyingMessageObject, getThreadMessage(), storyItem, replyingQuote, null, notify, scheduleDate, false, parent, parentFragment != null ? parentFragment.quickReplyShortcut : null, parentFragment != null ? parentFragment.getQuickReplyId() : 0);
                            }
                            if (searchingType != 0) {
                                setSearchingTypeInternal(0, true);
                                emojiView.closeSearch(true);
                                emojiView.hideSearchKeyboard();
                            }
                        }
                        if (delegate != null) {
                            delegate.onMessageSend(null, notify, scheduleDate);
                        }
                    };
                    if (!showConfirmAlert(runnable)) {
                        runnable.run();
                    }
                }
            }

            @Override
            public void onTabOpened(int type) {
                delegate.onStickersTab(type == 3);
                post(updateExpandabilityRunnable);
            }

            @Override
            public void onClearEmojiRecent() {
                if (parentFragment == null || parentActivity == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity, resourcesProvider);
                builder.setTitle(getString(R.string.ClearRecentEmojiTitle));
                builder.setMessage(getString(R.string.ClearRecentEmojiText));
                builder.setPositiveButton(getString(R.string.ClearButton), (dialogInterface, i) -> emojiView.clearRecentEmoji());
                builder.setNegativeButton(getString(R.string.Cancel), null);
                parentFragment.showDialog(builder.create());
            }

            @Override
            public void onShowStickerSet(TLRPC.StickerSet stickerSet, TLRPC.InputStickerSet inputStickerSet, boolean edit) {
                if (trendingStickersAlert != null && !trendingStickersAlert.isDismissed()) {
                    trendingStickersAlert.getLayout().showStickerSet(stickerSet, inputStickerSet);
                    return;
                }
                BaseFragment fragment = parentFragment;
                if (fragment == null) {
                    fragment = getLastFragment();
                }
                if (fragment == null || parentActivity == null) {
                    return;
                }
                if (stickerSet != null) {
                    inputStickerSet = new TLRPC.TL_inputStickerSetID();
                    inputStickerSet.access_hash = stickerSet.access_hash;
                    inputStickerSet.id = stickerSet.id;
                }
                StickersAlert alert = new StickersAlert(parentActivity, fragment, inputStickerSet, null, ChatActivityEnterView.this, resourcesProvider, false);
                fragment.showDialog(alert);
                if (edit) {
                    alert.enableEditMode();
                }
            }

            @Override
            public void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet) {
                MediaDataController.getInstance(currentAccount).toggleStickerSet(parentActivity, stickerSet, 2, parentFragment, false, false);
            }

            @Override
            public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
                MediaDataController.getInstance(currentAccount).toggleStickerSet(parentActivity, stickerSet, 0, parentFragment, false, false);
            }

            @Override
            public void onStickersGroupClick(long chatId) {
                if (parentFragment != null) {
                    if (AndroidUtilities.isTablet()) {
                        hidePopup(false);
                    }
                    GroupStickersActivity fragment = new GroupStickersActivity(chatId);
                    fragment.setInfo(info);
                    parentFragment.presentFragment(fragment);
                }
            }

            @Override
            public void onSearchOpenClose(int type) {
                setSearchingTypeInternal(type, true);
                if (type != 0) {
                    setStickersExpanded(true, true, false, type == 1);
                }
                if (emojiTabOpen && searchingType == 2) {
                    checkStickresExpandHeight();
                }
            }

            @Override
            public boolean isSearchOpened() {
                return searchingType != 0;
            }

            @Override
            public boolean isExpanded() {
                return stickersExpanded;
            }

            @Override
            public boolean canSchedule() {
                return parentFragment != null && parentFragment.canScheduleMessage();
            }

            @Override
            public boolean isInScheduleMode() {
                return parentFragment != null && parentFragment.isInScheduleMode();
            }

            @Override
            public long getDialogId() {
                return dialog_id;
            }

            @Override
            public int getThreadId() {
                return getThreadMessageId();
            }

            @Override
            public void showTrendingStickersAlert(TrendingStickersLayout layout) {
                BaseFragment fragment = parentFragment;
                if (fragment == null) {
                    fragment = getLastFragment();
                }
                if (fragment != null) {
                    trendingStickersAlert = new TrendingStickersAlert(getContext(), fragment, layout, resourcesProvider) {
                        @Override
                        public void dismiss() {
                            super.dismiss();
                            if (trendingStickersAlert == this) {
                                trendingStickersAlert = null;
                            }
                            if (ChatActivityEnterView.this.delegate != null) {
                                ChatActivityEnterView.this.delegate.onTrendingStickersShowed(false);
                            }
                        }
                    };
                    if (ChatActivityEnterView.this.delegate != null) {
                        ChatActivityEnterView.this.delegate.onTrendingStickersShowed(true);
                    }
                    fragment.showDialog(trendingStickersAlert);
                }
            }

            @Override
            public void invalidateEnterView() {
                invalidate();
            }

            @Override
            public float getProgressToSearchOpened() {
                return searchToOpenProgress;
            }
        });
        emojiView.setDragListener(new EmojiView.DragListener() {

            boolean wasExpanded;
            int initialOffset;

            @Override
            public void onDragStart() {
                if (!allowDragging()) {
                    return;
                }
                if (stickersExpansionAnim != null) {
                    stickersExpansionAnim.cancel();
                }
                stickersDragging = true;
                wasExpanded = stickersExpanded;
                stickersExpanded = true;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 1);
                stickersExpandedHeight = sizeNotifierLayout.getHeight() - (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0) - ActionBar.getCurrentActionBarHeight() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                if (searchingType == 2) {
                    stickersExpandedHeight = Math.min(stickersExpandedHeight, dp(120) + (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight));
                }
                emojiView.getLayoutParams().height = stickersExpandedHeight;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                sizeNotifierLayout.requestLayout();
                if (shouldDrawBackground) {
                    sizeNotifierLayout.setForeground(new ScrimDrawable());
                }
                initialOffset = (int) getTranslationY();
                if (delegate != null) {
                    delegate.onStickersExpandedChange();
                }
            }

            @Override
            public void onDragEnd(float velocity) {
                if (!allowDragging()) {
                    return;
                }
                stickersDragging = false;
                if ((wasExpanded && velocity >= dp(200)) || (!wasExpanded && velocity <= dp(-200)) || (wasExpanded && stickersExpansionProgress <= 0.6f) || (!wasExpanded && stickersExpansionProgress >= 0.4f)) {
                    setStickersExpanded(!wasExpanded, true, true);
                } else {
                    setStickersExpanded(wasExpanded, true, true);
                }
            }

            @Override
            public void onDragCancel() {
                if (!stickersTabOpen) {
                    return;
                }
                stickersDragging = false;
                setStickersExpanded(wasExpanded, true, false);
            }

            @Override
            public void onDrag(int offset) {
                if (!allowDragging()) {
                    return;
                }
                int origHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
                offset += initialOffset;
                offset = Math.max(Math.min(offset, 0), -(stickersExpandedHeight - origHeight));
                emojiView.setTranslationY(offset);
                setTranslationY(offset);
                stickersExpansionProgress = (float) offset / (-(stickersExpandedHeight - origHeight));
                sizeNotifierLayout.invalidate();
            }

            private boolean allowDragging() {
                return stickersTabOpen && !(!stickersExpanded && messageEditText != null && messageEditText.length() > 0) && emojiView.areThereAnyStickers() && !waitingForKeyboardOpen;
            }
        });
        if (emojiView != null) {
            emojiView.setStickersBanned(!sendPlainEnabled, !stickersEnabled, -dialog_id);
        }
        attachEmojiView();
        checkChannelRights();
    }

    @Override
    public void onStickerSelected(TLRPC.Document sticker, String query, Object parent, MessageObject.SendAnimationData sendAnimationData, boolean clearsInputField, boolean notify, int scheduleDate) {
        if (replyingQuote != null && parentFragment != null && replyingQuote.outdated) {
            parentFragment.showQuoteMessageUpdate();
            return;
        }
        if (isInScheduleMode() && scheduleDate == 0) {
            AlertsCreator.createScheduleDatePickerDialog(parentActivity, parentFragment.getDialogId(), (n, s) -> onStickerSelected(sticker, query, parent, sendAnimationData, clearsInputField, n, s), resourcesProvider);
        } else {
            Runnable runnable = () -> {
                if (slowModeTimer > 0 && !isInScheduleMode()) {
                    if (delegate != null) {
                        delegate.onUpdateSlowModeButton(slowModeButton, true, slowModeButton.getText());
                    }
                    return;
                }
                if (searchingType != 0) {
                    setSearchingTypeInternal(0, true);
                    emojiView.closeSearch(true);
                    emojiView.hideSearchKeyboard();
                }
                setStickersExpanded(false, true, false);
                TL_stories.StoryItem storyItem = delegate != null ? delegate.getReplyToStory() : null;
                SendMessagesHelper.getInstance(currentAccount).sendSticker(sticker, query, dialog_id, replyingMessageObject, getThreadMessage(), storyItem, replyingQuote, sendAnimationData, notify, scheduleDate, parent instanceof TLRPC.TL_messages_stickerSet, parent, parentFragment != null ? parentFragment.quickReplyShortcut : null, parentFragment != null ? parentFragment.getQuickReplyId() : 0);
                if (delegate != null) {
                    delegate.onMessageSend(null, true, scheduleDate);
                }
                if (clearsInputField) {
                    setFieldText("");
                }
                MediaDataController.getInstance(currentAccount).addRecentSticker(MediaDataController.TYPE_IMAGE, parent, sticker, (int) (System.currentTimeMillis() / 1000), false);
            };
            if (!showConfirmAlert(runnable)) {
                runnable.run();
            }
        }
    }

    @Override
    public boolean canSchedule() {
        return parentFragment != null && parentFragment.canScheduleMessage();
    }

    @Override
    public boolean isInScheduleMode() {
        return parentFragment != null && parentFragment.isInScheduleMode();
    }

    private boolean isEditingBusinessLink() {
        return editingBusinessLink != null;
    }

    public void addStickerToRecent(TLRPC.Document sticker) {
        createEmojiView();
        emojiView.addRecentSticker(sticker);
    }

    public void hideEmojiView() {
        if (!emojiViewVisible && emojiView != null && emojiView.getVisibility() != GONE) {
            sizeNotifierLayout.removeView(emojiView);
            emojiView.setVisibility(GONE);
            emojiView.setShowing(false);
        }
    }

    public void showEmojiView() {
        showPopup(1, 0);
    }

    private void showPopup(int show, int contentType) {
        showPopup(show, contentType, true);
    }

    private void showPopup(int show, int contentType, boolean allowAnimation) {
        if (show == 2) {
            return;
        }
        if (show == 1) {
            if (contentType == 0) {
                if (parentActivity == null && emojiView == null) {
                    return;
                }
                createEmojiView();
            }

            View currentView = null;
            boolean anotherPanelWasVisible = false;
            boolean samePannelWasVisible = false;
            int previousHeight = 0;
            if (contentType == 0) {
                attachEmojiView();
                samePannelWasVisible = emojiViewVisible && emojiView.getVisibility() == View.VISIBLE;
                emojiView.setVisibility(VISIBLE);
                emojiViewVisible = true;
                if (botKeyboardView != null && botKeyboardView.getVisibility() != GONE) {
                    botKeyboardView.setVisibility(GONE);
                    botKeyboardViewVisible = false;
                    anotherPanelWasVisible = true;
                    previousHeight = botKeyboardView.getMeasuredHeight();
                }
                emojiView.setShowing(true);
                currentView = emojiView;
                animatingContentType = 0;
            } else if (contentType == POPUP_CONTENT_BOT_KEYBOARD) {
                samePannelWasVisible = botKeyboardViewVisible && botKeyboardView.getVisibility() == View.VISIBLE;
                botKeyboardViewVisible = true;
                if (emojiView != null && emojiView.getVisibility() != GONE) {
                    sizeNotifierLayout.removeView(emojiView);
                    emojiView.setVisibility(GONE);
                    emojiView.setShowing(false);
                    emojiViewVisible = false;
                    anotherPanelWasVisible = true;
                    previousHeight = emojiView.getMeasuredHeight();
                }
                botKeyboardView.setVisibility(VISIBLE);
                currentView = botKeyboardView;
                animatingContentType = POPUP_CONTENT_BOT_KEYBOARD;
                MessagesController.getMainSettings(currentAccount).edit().remove("closed_botkeyboard_" + getTopicKeyString()).apply();
            }
            currentPopupContentType = contentType;

            if (keyboardHeight <= 0) {
                keyboardHeight = MessagesController.getGlobalEmojiSettings().getInt("kbd_height", dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = MessagesController.getGlobalEmojiSettings().getInt("kbd_height_land3", dp(200));
            }
            int currentHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
            if (parentFragment != null && parentFragment.getParentLayout() != null) {
                currentHeight -= parentFragment.getParentLayout().getBottomTabsHeight(false);
            }
            /*if (!samePannelWasVisible && !anotherPanelWasVisible) {
                currentHeight = 0;
            } else */
            if (contentType == POPUP_CONTENT_BOT_KEYBOARD) {
                currentHeight = Math.min(botKeyboardView.getKeyboardHeight(), currentHeight);
            }
            if (botKeyboardView != null) {
                botKeyboardView.setPanelHeight(currentHeight);
            }
            if (currentView != null) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
                layoutParams.height = currentHeight;
                currentView.setLayoutParams(layoutParams);
            }
            if (!AndroidUtilities.isInMultiwindow) {
                AndroidUtilities.hideKeyboard(messageEditText);
            }
            if (sizeNotifierLayout != null) {
                emojiPadding = currentHeight;
                sizeNotifierLayout.requestLayout();
                setEmojiButtonImage(true, true);
                updateBotButton(true);
                onWindowSizeChanged();
                if (smoothKeyboard && !keyboardVisible && currentHeight != previousHeight && allowAnimation) {
                    Runnable onAnimationEndRunnuble = () -> {
                        if (delegate != null) {
                            delegate.bottomPanelTranslationYChanged(0);
                        }
                        requestLayout();
                    };
                    if (overrideKeyboardAnimation) {
                        this.animationEndRunnable = onAnimationEndRunnuble;
                    } else {
                        panelAnimation = new AnimatorSet();
                        currentView.setTranslationY(currentHeight - previousHeight);
                        panelAnimation.playTogether(ObjectAnimator.ofFloat(currentView, View.TRANSLATION_Y, currentHeight - previousHeight, 0));
                        panelAnimation.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                        panelAnimation.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                        panelAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                panelAnimation = null;
                                notificationsLocker.unlock();
                                onAnimationEndRunnuble.run();
                            }
                        });
                        AndroidUtilities.runOnUIThread(runEmojiPanelAnimation, 50);
                        notificationsLocker.lock();
                    }
                    requestLayout();
                }
            }
        } else {
            if (emojiButton != null) {
                setEmojiButtonImage(false, true);
            }
            currentPopupContentType = -1;
            if (emojiView != null) {
                if (show != 2 || AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) {
                    if (smoothKeyboard && !keyboardVisible && !stickersExpanded) {
                        if (emojiViewVisible = true) {
                            animatingContentType = 0;
                        }
                        emojiView.setShowing(false);
                        Runnable animationEndRunnable = () -> {
                            if (show == 0) {
                                emojiPadding = 0;
                            }
                            panelAnimation = null;
                            if (emojiView != null) {
                                emojiView.setTranslationY(0);
                                emojiView.setVisibility(GONE);
                                sizeNotifierLayout.removeView(emojiView);
                                if (removeEmojiViewAfterAnimation) {
                                    removeEmojiViewAfterAnimation = false;
                                    emojiView = null;
                                }
                            }
                            if (delegate != null) {
                                delegate.bottomPanelTranslationYChanged(0);
                            }
                            requestLayout();
                        };
                        if (!overrideKeyboardAnimation) {
                            panelAnimation = new AnimatorSet();
                            panelAnimation.playTogether(ObjectAnimator.ofFloat(emojiView, View.TRANSLATION_Y, emojiView.getMeasuredHeight()));
                            panelAnimation.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                            panelAnimation.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                            notificationsLocker.lock();
                            panelAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    animationEndRunnable.run();
                                    notificationsLocker.unlock();
                                }
                            });
                        } else {
                            this.animationEndRunnable = animationEndRunnable;
                        }
                        AndroidUtilities.runOnUIThread(runEmojiPanelAnimation, 50);
                        requestLayout();
                    } else {
                        if (delegate != null) {
                            delegate.bottomPanelTranslationYChanged(0);
                        }
                        emojiPadding = 0;
                        sizeNotifierLayout.removeView(emojiView);
                        emojiView.setVisibility(GONE);
                        emojiView.setShowing(false);
                    }
                } else {
                    removeEmojiViewAfterAnimation = false;
                    if (delegate != null) {
                        delegate.bottomPanelTranslationYChanged(0);
                    }
                    sizeNotifierLayout.removeView(emojiView);
                    emojiView = null;
                }
                emojiViewVisible = false;
            }
            if (botKeyboardView != null && botKeyboardView.getVisibility() == View.VISIBLE) {
                if (show != 2 || AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) {
                    if (smoothKeyboard && !keyboardVisible) {
                        if (botKeyboardViewVisible) {
                            animatingContentType = 1;
                        }

                        panelAnimation = new AnimatorSet();
                        panelAnimation.playTogether(ObjectAnimator.ofFloat(botKeyboardView, View.TRANSLATION_Y, botKeyboardView.getMeasuredHeight()));
                        panelAnimation.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                        panelAnimation.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                        panelAnimation.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (show == 0) {
                                    emojiPadding = 0;
                                }
                                panelAnimation = null;
                                botKeyboardView.setTranslationY(0);
                                botKeyboardView.setVisibility(GONE);
                                notificationsLocker.unlock();
                                if (delegate != null) {
                                    delegate.bottomPanelTranslationYChanged(0);
                                }
                                requestLayout();
                            }
                        });
                        notificationsLocker.lock();
                        AndroidUtilities.runOnUIThread(runEmojiPanelAnimation, 50);
                        requestLayout();
                    } else {
                        if (!waitingForKeyboardOpen) {
                            botKeyboardView.setVisibility(GONE);
                        }
                    }
                }
                botKeyboardViewVisible = false;
            }
            if (contentType == POPUP_CONTENT_BOT_KEYBOARD && botButtonsMessageObject != null) {
                MessagesController.getMainSettings(currentAccount).edit().putInt("closed_botkeyboard_" + getTopicKeyString(), botButtonsMessageObject.getId()).apply();
            }
            updateBotButton(true);
        }

        if (stickersTabOpen || emojiTabOpen) {
            checkSendButton(true);
        }
        if (stickersExpanded && show != 1) {
            setStickersExpanded(false, false, false);
        }
        updateFieldHint(false);
        checkBotMenu();
    }

    private void attachEmojiView() {
        if (emojiView.getParent() == null) {
            int index = sizeNotifierLayout.getChildCount() - 5;
            if (!shouldDrawBackground) {
                index = sizeNotifierLayout.getChildCount();
            }
            sizeNotifierLayout.addView(emojiView, index);
        }
    }

    private String getTopicKeyString() {
        if (parentFragment != null && parentFragment.isTopic) {
            return dialog_id + "_" + parentFragment.getTopicId();
        }
        return "" + dialog_id;
    }

    private void setEmojiButtonImage(boolean byOpen, boolean animated) {
        if (emojiButton == null) {
            return;
        }
        boolean showingRecordInterface = recordInterfaceState == 1 || (recordedAudioPanel != null && recordedAudioPanel.getVisibility() == View.VISIBLE);
        if (showingRecordInterface) {
            emojiButtonAlpha = emojiButtonScale = 0f;
            updateEmojiButtonParams();
            animated = false;
        }
        ChatActivityEnterViewAnimatedIconView.State nextIcon;
        if (byOpen && currentPopupContentType == 0) {
            if (!sendPlainEnabled) {
                return;
            }
            nextIcon = ChatActivityEnterViewAnimatedIconView.State.KEYBOARD;
        } else {
            int currentPage;
            if (emojiView == null) {
                currentPage = MessagesController.getGlobalEmojiSettings().getInt("selected_page", 0);
            } else {
                currentPage = emojiView.getCurrentPage();
            }
            if (currentPage == 0 || !allowStickers && !allowGifs) {
                nextIcon = ChatActivityEnterViewAnimatedIconView.State.SMILE;
            } else if (messageEditText != null && !TextUtils.isEmpty(messageEditText.getText())) {
                nextIcon = ChatActivityEnterViewAnimatedIconView.State.SMILE;
            } else {
                if (currentPage == 1) {
                    nextIcon = ChatActivityEnterViewAnimatedIconView.State.STICKER;
                } else {
                    nextIcon = ChatActivityEnterViewAnimatedIconView.State.GIF;
                }
            }
        }
        if (!sendPlainEnabled && nextIcon == ChatActivityEnterViewAnimatedIconView.State.SMILE) {
            nextIcon = ChatActivityEnterViewAnimatedIconView.State.GIF;
        } else if (!stickersEnabled && nextIcon != ChatActivityEnterViewAnimatedIconView.State.SMILE) {
            nextIcon = ChatActivityEnterViewAnimatedIconView.State.SMILE;
        }

        emojiButton.setState(nextIcon, animated);
        onEmojiIconChanged(nextIcon);
    }

    protected void onEmojiIconChanged(ChatActivityEnterViewAnimatedIconView.State currentIcon) {
        if (currentIcon == ChatActivityEnterViewAnimatedIconView.State.GIF && emojiView == null) {
            MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, true, true, false);
            final ArrayList<String> gifSearchEmojies = MessagesController.getInstance(currentAccount).gifSearchEmojies;
            for (int i = 0, N = Math.min(10, gifSearchEmojies.size()); i < N; i++) {
                Emoji.preloadEmoji(gifSearchEmojies.get(i));
            }
        }
    }

    public boolean hidePopup(boolean byBackButton) {
        return hidePopup(byBackButton, false);
    }

    public boolean hidePopup(boolean byBackButton, boolean forceAnimate) {
        if (isPopupShowing()) {
            if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD && botReplyMarkup != null && byBackButton && botButtonsMessageObject != null) {
                if (botReplyMarkup.is_persistent) {
                    return false;
                }
                MessagesController.getMainSettings(currentAccount).edit().putInt("closed_botkeyboard_" + getTopicKeyString(), botButtonsMessageObject.getId()).apply();
            }
            if (byBackButton && searchingType != 0 || forceAnimate) {
                setSearchingTypeInternal(0, true);
                if (emojiView != null) {
                    emojiView.closeSearch(true);
                }
                if (messageEditText != null) {
                    messageEditText.requestFocus();
                }
                setStickersExpanded(false, true, false);
                if (emojiTabOpen) {
                    checkSendButton(true);
                }
            } else {
                if (searchingType != 0) {
                    setSearchingTypeInternal(0, false);
                    emojiView.closeSearch(false);
                    if (messageEditText != null) {
                        messageEditText.requestFocus();
                    }
                } else if (stickersExpanded) {
                    setStickersExpanded(false, true, false);
                } else {
                    showPopup(0, 0);
                }
            }
            return true;
        }
        return false;
    }

    private void setSearchingTypeInternal(int searchingType, boolean animated) {
        boolean showSearchingNew = searchingType != 0;
        boolean showSearchingOld = this.searchingType != 0;
        if (showSearchingNew != showSearchingOld) {
            if (searchAnimator != null) {
                searchAnimator.removeAllListeners();
                searchAnimator.cancel();
            }
            if (!animated) {
                searchToOpenProgress = showSearchingNew ? 1f : 0f;
                if (emojiView != null) {
                    emojiView.searchProgressChanged();
                }
            } else {
                searchAnimator = ValueAnimator.ofFloat(searchToOpenProgress, showSearchingNew ? 1f : 0f);
                searchAnimator.addUpdateListener(valueAnimator -> {
                    searchToOpenProgress = (float) valueAnimator.getAnimatedValue();
                    if (emojiView != null) {
                        emojiView.searchProgressChanged();
                    }
                });
                searchAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        searchToOpenProgress = showSearchingNew ? 1f : 0f;
                        if (emojiView != null) {
                            emojiView.searchProgressChanged();
                        }
                    }
                });
                searchAnimator.setDuration(220);
                searchAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                searchAnimator.start();
            }
        }

        this.searchingType = searchingType;
    }

    public void openKeyboardInternal() {
        if (hasBotWebView() && botCommandsMenuIsShowing() || BaseFragment.hasSheets(parentFragment)) {
            return;
        }
        showPopup(AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow || parentFragment != null && parentFragment.isInBubbleMode() || isPaused ? 0 : 2, 0);
        if (delegate != null) {
            delegate.onKeyboardRequested();
        }
        if (messageEditText != null) {
            messageEditText.requestFocus();
        }
        AndroidUtilities.showKeyboard(messageEditText);
        if (isPaused) {
            showKeyboardOnResume = true;
        } else if (!AndroidUtilities.usingHardwareInput && !keyboardVisible && !AndroidUtilities.isInMultiwindow && (parentFragment == null || !parentFragment.isInBubbleMode())) {
            waitingForKeyboardOpen = true;
            if (emojiView != null) {
                emojiView.onTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0, 0, 0));
            }
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
            AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
        }
    }

    public boolean isEditingMessage() {
        return editingMessageObject != null;
    }

    public MessageObject getEditingMessageObject() {
        return editingMessageObject;
    }

    public boolean isEditingCaption() {
        return editingCaption;
    }

    public boolean hasAudioToSend() {
        return audioToSendMessageObject != null || videoToSendMessageObject != null;
    }

    public void openKeyboard() {
        if (hasBotWebView() && botCommandsMenuIsShowing() || BaseFragment.hasSheets(parentFragment)) {
            return;
        }
        if (delegate != null) {
            delegate.onKeyboardRequested();
        }
        if (messageEditText != null && !AndroidUtilities.showKeyboard(messageEditText)) {
            messageEditText.clearFocus();
            messageEditText.requestFocus();
        }
    }

    public void closeKeyboard() {
        AndroidUtilities.hideKeyboard(messageEditText);
    }

    public boolean isPopupShowing() {
        return emojiViewVisible || botKeyboardViewVisible;
    }

    public boolean closeCreationLinkDialog() {
        return messageEditText != null && messageEditText.closeCreationLinkDialog();
    }

    public boolean isKeyboardVisible() {
        return keyboardVisible;
    }

    public boolean isWaitingForKeyboard() {
        return waitingForKeyboardOpen;
    }

    public void addRecentGif(TLRPC.Document searchImage) {
        MediaDataController.getInstance(currentAccount).addRecentGif(searchImage, (int) (System.currentTimeMillis() / 1000), true);
        if (emojiView != null) {
            emojiView.addRecentGif(searchImage);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw && stickersExpanded) {
            setSearchingTypeInternal(0, false);
            emojiView.closeSearch(false);
            setStickersExpanded(false, false, false);
        }
        if (videoTimelineView != null) {
            videoTimelineView.clearFrames();
        }
    }

    public boolean isStickersExpanded() {
        return stickersExpanded;
    }

    @Override
    public void onSizeChanged(int height, boolean isWidthGreater) {
        if (searchingType != 0) {
            lastSizeChangeValue1 = height;
            lastSizeChangeValue2 = isWidthGreater;
            keyboardVisible = height > 0;
            checkBotMenu();
            return;
        }
        if (height > dp(50) && keyboardVisible && !AndroidUtilities.isInMultiwindow) {
            if (isWidthGreater) {
                keyboardHeightLand = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }

        if (keyboardVisible) {
            if (emojiViewVisible && emojiView == null) {
                emojiViewVisible = false;
            }
        }

        if (isPopupShowing()) {
            int newHeight = isWidthGreater ? keyboardHeightLand : keyboardHeight;
            if (parentFragment != null && parentFragment.getParentLayout() != null) {
                newHeight -= parentFragment.getParentLayout().getBottomTabsHeight(false);
            }
            if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD && !botKeyboardView.isFullSize()) {
                newHeight = Math.min(botKeyboardView.getKeyboardHeight(), newHeight);
            }

            View currentView = null;
            if (currentPopupContentType == 0) {
                currentView = emojiView;
            } else if (currentPopupContentType == POPUP_CONTENT_BOT_KEYBOARD) {
                currentView = botKeyboardView;
            }
            if (botKeyboardView != null) {
                botKeyboardView.setPanelHeight(newHeight);
            }

            if (currentView != null) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
                if (!closeAnimationInProgress && (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) && !stickersExpanded) {
                    layoutParams.width = AndroidUtilities.displaySize.x;
                    layoutParams.height = newHeight;
                    currentView.setLayoutParams(layoutParams);
                    if (sizeNotifierLayout != null) {
                        int oldHeight = emojiPadding;
                        emojiPadding = layoutParams.height;
                        sizeNotifierLayout.requestLayout();
                        onWindowSizeChanged();
                        if (smoothKeyboard && !keyboardVisible && oldHeight != emojiPadding && pannelAnimationEnabled()) {
                            panelAnimation = new AnimatorSet();
                            panelAnimation.playTogether(ObjectAnimator.ofFloat(currentView, View.TRANSLATION_Y, emojiPadding - oldHeight, 0));
                            panelAnimation.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                            panelAnimation.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                            panelAnimation.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    panelAnimation = null;
                                    if (delegate != null) {
                                        delegate.bottomPanelTranslationYChanged(0);
                                    }
                                    requestLayout();
                                    notificationsLocker.unlock();
                                }
                            });
                            AndroidUtilities.runOnUIThread(runEmojiPanelAnimation, 50);
                            notificationsLocker.lock();
                            requestLayout();
                        }
                    }
                }
            }
        }

        if (lastSizeChangeValue1 == height && lastSizeChangeValue2 == isWidthGreater) {
            onWindowSizeChanged();
            return;
        }
        lastSizeChangeValue1 = height;
        lastSizeChangeValue2 = isWidthGreater;

        boolean oldValue = keyboardVisible;
        keyboardVisible = height > 0;
        checkBotMenu();
        if (keyboardVisible && isPopupShowing() && stickersExpansionAnim == null) {
            showPopup(0, currentPopupContentType);
        } else if (!keyboardVisible && !isPopupShowing() && botButtonsMessageObject != null && replyingMessageObject != botButtonsMessageObject && (!hasBotWebView() && !botCommandsMenuIsShowing() && !BaseFragment.hasSheets(parentFragment)) && (messageEditText == null || TextUtils.isEmpty(messageEditText.getText())) && botReplyMarkup != null && !botReplyMarkup.rows.isEmpty()) {
            if (sizeNotifierLayout.adjustPanLayoutHelper.animationInProgress()) {
                sizeNotifierLayout.adjustPanLayoutHelper.stopTransition();
            } else {
                sizeNotifierLayout.adjustPanLayoutHelper.ignoreOnce();
            }
            showPopup(1, POPUP_CONTENT_BOT_KEYBOARD, false);
        }
        if (emojiPadding != 0 && !keyboardVisible && keyboardVisible != oldValue && !isPopupShowing()) {
            emojiPadding = 0;
            sizeNotifierLayout.requestLayout();
        }
        if (keyboardVisible && waitingForKeyboardOpen) {
            waitingForKeyboardOpen = false;
            if (clearBotButtonsOnKeyboardOpen) {
                clearBotButtonsOnKeyboardOpen = false;
                botKeyboardView.setButtons(botReplyMarkup);
            }

            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
        }
        onWindowSizeChanged();
    }

    public void checkReactionsButton(boolean visible) {
        AndroidUtilities.updateViewVisibilityAnimated(reactionsButton, visible, 0.1f, true);
    }

    public int getEmojiPadding() {
        return emojiPadding;
    }

    public int getVisibleEmojiPadding() {
        return emojiViewVisible ? emojiPadding : 0;
    }

    private MessageObject getThreadMessage() {
        return parentFragment != null ? parentFragment.getThreadMessage() : null;
    }

    private int getThreadMessageId() {
        return parentFragment != null && parentFragment.getThreadMessage() != null ? parentFragment.getThreadMessage().getId() : 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
            if (botKeyboardView != null) {
                botKeyboardView.invalidateViews();
            }
            if (messageEditText != null) {
                messageEditText.postInvalidate();
                messageEditText.invalidateForce();
            }
        } else if (id == NotificationCenter.recordProgressChanged) {
            int guid = (Integer) args[0];
            if (guid != recordingGuid) {
                return;
            }

            if (recordInterfaceState != 0 && !wasSendTyping && !isInScheduleMode()) {
                wasSendTyping = true;
                accountInstance.getMessagesController().sendTyping(dialog_id, getThreadMessageId(), isInVideoMode() ? 7 : 1, 0);
            }

            if (recordCircle != null) {
                recordCircle.setAmplitude((Double) args[1]);
            }
        } else if (id == NotificationCenter.closeChats) {
            if (messageEditText != null && messageEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(messageEditText);
            }
        } else if (id == NotificationCenter.recordStartError || id == NotificationCenter.recordStopped) {
            int guid = (Integer) args[0];
            if (guid != recordingGuid) {
                return;
            }
            if (recordingAudioVideo) {
                recordingAudioVideo = false;
                if (id == NotificationCenter.recordStopped) {
                    Integer reason = (Integer) args[1];
                    int state;
                    if (reason == 4) {
                        state = RECORD_STATE_CANCEL_BY_TIME;
                    } else if (isInVideoMode() && reason == 5) {
                        state = RECORD_STATE_SENDING;
                    } else {
                        if (reason == 0) {
                            state = RECORD_STATE_CANCEL_BY_GESTURE;
                        } else if (reason == 6) {
                            state = RECORD_STATE_CANCEL;
                        } else {
                            state = RECORD_STATE_PREPARING;
                        }
                    }
                    if (state != RECORD_STATE_PREPARING) {
                        updateRecordInterface(state, true);
                    }
                } else {
                    updateRecordInterface(RECORD_STATE_CANCEL, true);
                }
            }
        } else if (id == NotificationCenter.recordStarted) {
            int guid = (Integer) args[0];
            if (guid != recordingGuid) {
                return;
            }
            boolean audio = (Boolean) args[1];
            isInVideoMode = !audio;
            if (audioVideoSendButton != null) {
                audioVideoSendButton.setState(audio ? ChatActivityEnterViewAnimatedIconView.State.VOICE : ChatActivityEnterViewAnimatedIconView.State.VIDEO, true);
            }
            if (!recordingAudioVideo) {
                recordingAudioVideo = true;
                updateRecordInterface(RECORD_STATE_ENTER, true);
            } else if (recordCircle != null) {
                recordCircle.showWaves(true, true);
            }
            if (recordTimerView != null) {
                recordTimerView.start(millisecondsRecorded);
            }
            if (recordDot != null) {
                recordDot.enterAnimation = false;
            }
        } else if (id == NotificationCenter.recordPaused) {
            recordingAudioVideo = false;
            audioToSend = null;
            videoToSendMessageObject = null;
        } else if (id == NotificationCenter.recordResumed) {
            audioToSend = null;
            videoToSendMessageObject = null;
            checkSendButton(true);
            recordingAudioVideo = true;
            updateRecordInterface(RECORD_STATE_ENTER, true);
        } else if (id == NotificationCenter.audioDidSent) {
            int guid = (Integer) args[0];
            if (guid != recordingGuid) {
                return;
            }
            millisecondsRecorded = 0;
            Object audio = args[1];
            if (audio instanceof VideoEditedInfo) {
                videoToSendMessageObject = (VideoEditedInfo) audio;

                audioToSendPath = (String) args[2];
                ArrayList<Bitmap> keyframes = (ArrayList<Bitmap>) args[3];
                millisecondsRecorded = videoToSendMessageObject.estimatedDuration;

                if (videoTimelineView != null) {
                    videoTimelineView.setVideoPath(audioToSendPath);
                    videoTimelineView.setKeyframes(keyframes);
                    videoTimelineView.setVisibility(VISIBLE);
                    videoTimelineView.setMinProgressDiff(1000.0f / videoToSendMessageObject.estimatedDuration);
                    isRecordingStateChanged();
                }
                updateRecordInterface(RECORD_STATE_PREPARING, true);
                checkSendButton(false);
            } else {
                audioToSend = (TLRPC.TL_document) args[1];
                audioToSendPath = (String) args[2];
                boolean fromDraft = args.length >= 4 && (boolean) args[3];
                if (audioToSend != null) {
                    createRecordAudioPanel();
                    if (recordedAudioPanel == null) {
                        return;
                    }

                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.out = true;
                    message.id = 0;
                    message.peer_id = new TLRPC.TL_peerUser();
                    message.from_id = new TLRPC.TL_peerUser();
                    message.peer_id.user_id = message.from_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    message.date = (int) (System.currentTimeMillis() / 1000);
                    message.message = "";
                    message.attachPath = audioToSendPath;
                    message.media = new TLRPC.TL_messageMediaDocument();
                    message.media.flags |= 3;
                    message.media.document = audioToSend;
                    message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    audioToSendMessageObject = new MessageObject(UserConfig.selectedAccount, message, false, true);

                    recordedAudioPanel.setAlpha(1.0f);
                    recordedAudioPanel.setVisibility(VISIBLE);
                    recordDeleteImageView.setVisibility(VISIBLE);
                    recordDeleteImageView.setAlpha(0f);
                    recordDeleteImageView.setScaleY(0f);
                    recordDeleteImageView.setScaleX(0f);
                    double duration = 0;
                    for (int a = 0; a < audioToSend.attributes.size(); a++) {
                        TLRPC.DocumentAttribute attribute = audioToSend.attributes.get(a);
                        if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                            duration = attribute.duration;
                            break;
                        }
                    }

                    for (int a = 0; a < audioToSend.attributes.size(); a++) {
                        TLRPC.DocumentAttribute attribute = audioToSend.attributes.get(a);
                        if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                            if (attribute.waveform == null || attribute.waveform.length == 0) {
                                attribute.waveform = MediaController.getWaveform(audioToSendPath);
                            }
                            recordedAudioSeekBar.setWaveform(attribute.waveform);
                            break;
                        }
                    }
                    millisecondsRecorded = (long) (duration * 1000L);
                    recordedAudioTimeTextView.setText(AndroidUtilities.formatShortDuration((int) (duration)));
                    checkSendButton(false);
                    if (fromDraft) {
                        createRecordCircle();
                        createRecordPanel();
                        createRecordAudioPanel();
                        recordInterfaceState = 1;
                        recordCircle.resetLockTranslation(false);
                        recordControlsCircleScale.set(recordCircle, 1f);
                        if (controlsView != null) {
                            controlsView.setVisibility(VISIBLE);
                            controlsView.setAlpha(1f);
                        }
                    }
                    updateRecordInterface(RECORD_STATE_PREPARING, !fromDraft);
                } else {
                    if (delegate != null) {
                        delegate.onMessageSend(null, true, 0);
                    }
                }
            }
        } else if (id == NotificationCenter.audioRouteChanged) {
            if (parentActivity != null) {
                boolean frontSpeaker = (Boolean) args[0];
                parentActivity.setVolumeControlStream(frontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.USE_DEFAULT_STREAM_TYPE);
            }
        } else if (id == NotificationCenter.messagePlayingDidReset) {
            if (audioToSendMessageObject != null && !MediaController.getInstance().isPlayingMessage(audioToSendMessageObject)) {
                if (playPauseDrawable != null) {
                    playPauseDrawable.setIcon(MediaActionDrawable.ICON_PLAY, true);
                }
                if (recordedAudioPlayButton != null) {
                    recordedAudioPlayButton.setContentDescription(getString("AccActionPlay", R.string.AccActionPlay));
                }
                if (recordedAudioSeekBar != null) {
                    recordedAudioSeekBar.setProgress(0);
                }
            }
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (audioToSendMessageObject != null && MediaController.getInstance().isPlayingMessage(audioToSendMessageObject)) {
                MessageObject player = MediaController.getInstance().getPlayingMessageObject();
                audioToSendMessageObject.audioProgress = player.audioProgress;
                audioToSendMessageObject.audioProgressSec = player.audioProgressSec;
                if (!recordedAudioSeekBar.isDragging()) {
                    recordedAudioSeekBar.setProgress(audioToSendMessageObject.audioProgress);
                }
            }
        } else if (id == NotificationCenter.featuredStickersDidLoad) {
            if (emojiButton != null) {
                emojiButton.invalidate();
            }
        } else if (id == NotificationCenter.messageReceivedByServer2) {
            Boolean scheduled = (Boolean) args[6];
            if (scheduled) {
                return;
            }
            long did = (Long) args[3];
            if (did == dialog_id && info != null && info.slowmode_seconds != 0) {
                TLRPC.Chat chat = accountInstance.getMessagesController().getChat(info.id);
                if (chat != null && !ChatObject.hasAdminRights(chat) && !ChatObject.isIgnoredChatRestrictionsForBoosters(chat)) {
                    info.slowmode_next_send_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + info.slowmode_seconds;
                    info.flags |= 262144;
                    setSlowModeTimer(info.slowmode_next_send_date);
                }
            }
        } else if (id == NotificationCenter.sendingMessagesChanged) {
            if (info != null) {
                updateSlowModeText();
            }
        } else if (id == NotificationCenter.audioRecordTooShort) {
            audioToSend = null;
            videoToSendMessageObject = null;
            updateRecordInterface(RECORD_STATE_CANCEL_BY_TIME, true);
        } else if (id == NotificationCenter.updateBotMenuButton) {
            long botId = (long) args[0];
            TL_bots.BotMenuButton botMenuButton = (TL_bots.BotMenuButton) args[1];

            if (botId == dialog_id) {
                if (botMenuButton instanceof TL_bots.TL_botMenuButton) {
                    TL_bots.TL_botMenuButton webViewButton = (TL_bots.TL_botMenuButton) botMenuButton;
                    botMenuWebViewTitle = webViewButton.text;
                    botMenuWebViewUrl = webViewButton.url;
                    botMenuButtonType = BotMenuButtonType.WEB_VIEW;
                } else if (hasBotCommands || hasQuickReplies) {
                    botMenuButtonType = BotMenuButtonType.COMMANDS;
                } else {
                    botMenuButtonType = BotMenuButtonType.NO_BUTTON;
                }

                updateBotButton(false);
            }
        } else if (id == NotificationCenter.didUpdatePremiumGiftFieldIcon) {
            updateGiftButton(true);
        }
    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 2) {
            if (pendingLocationButton != null) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    SendMessagesHelper.getInstance(currentAccount).sendCurrentLocation(pendingMessageObject, pendingLocationButton);
                }
                pendingLocationButton = null;
                pendingMessageObject = null;
            }
        }
    }

    public void checkStickresExpandHeight() {
        if (emojiView == null) {
            return;
        }
        final int origHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
        int newHeight = originalViewHeight - (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0) - ActionBar.getCurrentActionBarHeight() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
        if (searchingType == 2) {
            newHeight = Math.min(newHeight, dp(120) + origHeight);
        }
        int currentHeight = emojiView.getLayoutParams().height;
        if (currentHeight == newHeight) {
            return;
        }
        if (stickersExpansionAnim != null) {
            stickersExpansionAnim.cancel();
            stickersExpansionAnim = null;
        }
        stickersExpandedHeight = newHeight;
        if (currentHeight > newHeight) {
            Runnable animationEndRunnable = () -> {
                if (emojiView != null) {
                    emojiView.getLayoutParams().height = stickersExpandedHeight;
                    emojiView.setLayerType(LAYER_TYPE_NONE, null);
                }
            };
            emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
            if (overrideKeyboardAnimation) {
                this.animationEndRunnable = animationEndRunnable;
            } else {
                AnimatorSet anims = new AnimatorSet();
                anims.playTogether(
                        ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                        ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight))
                );
                ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(animation -> sizeNotifierLayout.invalidate());
                anims.setDuration(300);
                anims.setInterpolator(CubicBezierInterpolator.DEFAULT);
                anims.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        stickersExpansionAnim = null;
                        animationEndRunnable.run();
                    }
                });
                stickersExpansionAnim = anims;
                anims.start();
            }
        } else {
            emojiView.getLayoutParams().height = stickersExpandedHeight;
            sizeNotifierLayout.requestLayout();
            int start = 0, end = 0;
            if (messageEditText != null) {
                start = messageEditText.getSelectionStart();
                end = messageEditText.getSelectionEnd();
                messageEditText.setText(messageEditText.getText()); // dismiss action mode, if any
                messageEditText.setSelection(start, end);
            }
            AnimatorSet anims = new AnimatorSet();
            anims.playTogether(
                    ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                    ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight))
            );
            ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(animation -> sizeNotifierLayout.invalidate());
            anims.setDuration(300);
            anims.setInterpolator(CubicBezierInterpolator.DEFAULT);
            anims.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    stickersExpansionAnim = null;
                    emojiView.setLayerType(LAYER_TYPE_NONE, null);
                }
            });
            stickersExpansionAnim = anims;
            emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
            anims.start();
        }
    }

    public void setStickersExpanded(boolean expanded, boolean animated, boolean byDrag) {
        setStickersExpanded(expanded, animated, byDrag, true);
    }
    public void setStickersExpanded(boolean expanded, boolean animated, boolean byDrag, boolean stopAllHeavy) {
        if (adjustPanLayoutHelper != null && adjustPanLayoutHelper.animationInProgress() || waitingForKeyboardOpenAfterAnimation) {
            return;
        }
        if (emojiView == null || !byDrag && stickersExpanded == expanded) {
            return;
        }
        stickersExpanded = expanded;
        if (delegate != null) {
            delegate.onStickersExpandedChange();
        }
        final int origHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
        if (stickersExpansionAnim != null) {
            stickersExpansionAnim.cancel();
            stickersExpansionAnim = null;
        }
        if (stickersExpanded) {
            if (stopAllHeavy) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 1);
            }
            originalViewHeight = sizeNotifierLayout.getHeight();
            stickersExpandedHeight = originalViewHeight - (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0) - ActionBar.getCurrentActionBarHeight() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
            if (searchingType == 2) {
                stickersExpandedHeight = Math.min(stickersExpandedHeight, dp(120) + origHeight);
            }
            emojiView.getLayoutParams().height = stickersExpandedHeight;
            sizeNotifierLayout.requestLayout();
            if (shouldDrawBackground) {
                sizeNotifierLayout.setForeground(new ScrimDrawable());
            }
            int start = 0, end = 0;
            if (messageEditText != null) {
                start = messageEditText.getSelectionStart();
                end = messageEditText.getSelectionEnd();
                messageEditText.setText(messageEditText.getText()); // dismiss action mode, if any
                messageEditText.setSelection(start, end);
            }
            if (animated) {
                AnimatorSet anims = new AnimatorSet();
                anims.playTogether(
                        ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                        ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                        ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 1)
                );
                anims.setDuration(300);
                anims.setInterpolator(CubicBezierInterpolator.DEFAULT);
                ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(animation -> {
                    stickersExpansionProgress = Math.abs(getTranslationY() / (-(stickersExpandedHeight - origHeight)));
                    sizeNotifierLayout.invalidate();
                });
                anims.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        stickersExpansionAnim = null;
                        emojiView.setLayerType(LAYER_TYPE_NONE, null);
                        notificationsLocker.unlock();
                    }
                });
                stickersExpansionAnim = anims;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                notificationsLocker.lock();
                stickersExpansionProgress = 0f;
                sizeNotifierLayout.invalidate();
                anims.start();
            } else {
                stickersExpansionProgress = 1;
                setTranslationY(-(stickersExpandedHeight - origHeight));
                emojiView.setTranslationY(-(stickersExpandedHeight - origHeight));
                if (stickersArrow != null) {
                    stickersArrow.setAnimationProgress(1);
                }
            }
        } else {
            if (stopAllHeavy) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 1);
            }
            if (animated) {
                closeAnimationInProgress = true;
                AnimatorSet anims = new AnimatorSet();
                anims.playTogether(
                        ObjectAnimator.ofInt(this, roundedTranslationYProperty, 0),
                        ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, 0),
                        ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 0)
                );
                anims.setDuration(300);
                anims.setInterpolator(CubicBezierInterpolator.DEFAULT);
                ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(animation -> {
                    stickersExpansionProgress = getTranslationY() / (-(stickersExpandedHeight - origHeight));
                    sizeNotifierLayout.invalidate();
                });
                anims.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        closeAnimationInProgress = false;
                        stickersExpansionAnim = null;
                        if (emojiView != null) {
                            emojiView.getLayoutParams().height = origHeight;
                            emojiView.setLayerType(LAYER_TYPE_NONE, null);
                        }
                        if (sizeNotifierLayout != null) {
                            sizeNotifierLayout.requestLayout();
                            sizeNotifierLayout.setForeground(null);
                            sizeNotifierLayout.setWillNotDraw(false);
                        }
                        if (keyboardVisible && isPopupShowing()) {
                            showPopup(0, currentPopupContentType);
                        }
                        if (onEmojiSearchClosed != null) {
                            onEmojiSearchClosed.run();
                            onEmojiSearchClosed = null;
                        }
                        notificationsLocker.unlock();
                    }
                });
                stickersExpansionProgress = 1f;
                sizeNotifierLayout.invalidate();
                stickersExpansionAnim = anims;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                notificationsLocker.lock();
                anims.start();
            } else {
                stickersExpansionProgress = 0;
                setTranslationY(0);
                emojiView.setTranslationY(0);
                emojiView.getLayoutParams().height = origHeight;
                sizeNotifierLayout.requestLayout();
                sizeNotifierLayout.setForeground(null);
                sizeNotifierLayout.setWillNotDraw(false);
                if (stickersArrow != null) {
                    stickersArrow.setAnimationProgress(0);
                }
            }
        }
        if (expandStickersButton != null) {
            if (stickersExpanded) {
                expandStickersButton.setContentDescription(getString("AccDescrCollapsePanel", R.string.AccDescrCollapsePanel));
            } else {
                expandStickersButton.setContentDescription(getString("AccDescrExpandPanel", R.string.AccDescrExpandPanel));
            }
        }
    }

    public boolean swipeToBackEnabled() {
        if (recordingAudioVideo) {
            return false;
        }
        if (isInVideoMode() && recordedAudioPanel != null && recordedAudioPanel.getVisibility() == View.VISIBLE) {
            return false;
        }
        if (hasBotWebView() && botCommandsMenuButton.isOpened() || BaseFragment.hasSheets(parentFragment)) {
            return false;
        }
        return true;
    }

    public int getHeightWithTopView() {
        int h = getMeasuredHeight();
        if (topView != null && topView.getVisibility() == View.VISIBLE) {
            h -= (1f - topViewEnterProgress) * topView.getLayoutParams().height;
        }
        return h;
    }

    public void setAdjustPanLayoutHelper(AdjustPanLayoutHelper adjustPanLayoutHelper) {
        this.adjustPanLayoutHelper = adjustPanLayoutHelper;
    }

    public AdjustPanLayoutHelper getAdjustPanLayoutHelper() {
        return adjustPanLayoutHelper;
    }

    public boolean panelAnimationInProgress() {
        return panelAnimation != null;
    }

    public float getTopViewTranslation() {
        if (topView == null || topView.getVisibility() == View.GONE) {
            return 0;
        }
        return topView.getTranslationY();
    }

    public int getAnimatedTop() {
        return animatedTop;
    }

    public void checkAnimation() {

    }

    private class ScrimDrawable extends Drawable {

        private Paint paint;

        public ScrimDrawable() {
            paint = new Paint();
            paint.setColor(0);
        }

        @Override
        public void draw(Canvas canvas) {
            if (emojiView == null) {
                return;
            }
            paint.setAlpha(Math.round(102 * stickersExpansionProgress));
            canvas.drawRect(0, 0, getWidth(), emojiView.getY() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight() + (messageEditText == null ? 0 : messageEditText.getOffsetY()), paint);
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    private class SlideTextView extends View {

        TextPaint grayPaint;
        TextPaint bluePaint;

        Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        String slideToCancelString;
        String cancelString;

        float slideToCancelWidth;
        float cancelWidth;
        float cancelToProgress;
        float slideProgress;

        float slideToAlpha;
        float cancelAlpha;

        float xOffset = 0;
        boolean moveForward;
        long lastUpdateTime;

        int cancelCharOffset;

        Path arrowPath = new Path();

        StaticLayout slideToLayout;
        StaticLayout cancelLayout;

        private boolean pressed;
        public Rect cancelRect = new Rect();

        Drawable selectableBackground;
        private int lastSize;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
                setPressed(false);
            }
            if (cancelToProgress == 0 || !isEnabled()) {
                return false;
            }
            int x = (int) event.getX();
            int y = (int) event.getY();
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = cancelRect.contains(x, y);
                if (pressed) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        selectableBackground.setHotspot(x, y);
                    }
                    setPressed(true);
                }
                return pressed;
            } else if (pressed) {
                if (event.getAction() == MotionEvent.ACTION_MOVE && !cancelRect.contains(x, y)) {
                    setPressed(false);
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_UP && cancelRect.contains(x, y)) {
                    onCancelButtonPressed();
                }
                return true;
            }
            return pressed;
        }

        public void onCancelButtonPressed() {
            if (hasRecordVideo && isInVideoMode()) {
                CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
                delegate.needStartRecordVideo(5, true, 0, voiceOnce ? 0x7FFFFFFF : 0, effectId);
                sendButton.setEffect(effectId = 0);
            } else {
                delegate.needStartRecordAudio(0);
                MediaController.getInstance().stopRecording(0, false, 0, voiceOnce);
            }
            audioToSend = null;
            audioToSendMessageObject = null;
            videoToSendMessageObject = null;
            millisecondsRecorded = 0;
            recordingAudioVideo = false;
            MediaDataController.getInstance(currentAccount).pushDraftVoiceMessage(dialog_id, parentFragment != null && parentFragment.isTopic ? parentFragment.getTopicId() : 0, null);
            updateRecordInterface(RECORD_STATE_CANCEL, true);
            checkSendButton(true);
        }


        boolean smallSize;

        public SlideTextView(@NonNull Context context) {
            super(context);
            smallSize = AndroidUtilities.displaySize.x <= dp(320);
            grayPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            grayPaint.setTextSize(dp(smallSize ? 13 : 15));

            bluePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            bluePaint.setTextSize(dp(15));

            bluePaint.setTypeface(AndroidUtilities.bold());

            arrowPaint.setColor(getThemedColor(Theme.key_chat_messagePanelIcons));
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeWidth(dpf2(smallSize ? 1f : 1.6f));
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);

            slideToCancelString = getString(R.string.SlideToCancel2);

            cancelString = getString("Cancel", R.string.Cancel).toUpperCase();

            cancelCharOffset = slideToCancelString.indexOf(cancelString);

            updateColors();
        }

        public void updateColors() {
            grayPaint.setColor(getThemedColor(Theme.key_chat_recordTime));
            bluePaint.setColor(getThemedColor(Theme.key_chat_recordVoiceCancel));
            slideToAlpha = grayPaint.getAlpha();
            cancelAlpha = bluePaint.getAlpha();
            selectableBackground = Theme.createSimpleSelectorCircleDrawable(dp(60), 0, ColorUtils.setAlphaComponent(getThemedColor(Theme.key_chat_recordVoiceCancel), 26));
            selectableBackground.setCallback(this);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            selectableBackground.setState(getDrawableState());
        }

        @Override
        public boolean verifyDrawable(Drawable drawable) {
            return selectableBackground == drawable || super.verifyDrawable(drawable);
        }

        @Override
        public void jumpDrawablesToCurrentState() {
            super.jumpDrawablesToCurrentState();
            if (selectableBackground != null) {
                selectableBackground.jumpToCurrentState();
            }
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int currentSize = getMeasuredHeight() + (getMeasuredWidth() << 16);
            if (lastSize != currentSize) {
                lastSize = currentSize;
                slideToCancelWidth = grayPaint.measureText(slideToCancelString);
                cancelWidth = bluePaint.measureText(cancelString);
                lastUpdateTime = System.currentTimeMillis();

                int heightHalf = getMeasuredHeight() >> 1;
                arrowPath.reset();
                if (smallSize) {
                    arrowPath.setLastPoint(dpf2(2.5f), heightHalf - dpf2(3.12f));
                    arrowPath.lineTo(0, heightHalf);
                    arrowPath.lineTo(dpf2(2.5f), heightHalf + dpf2(3.12f));
                } else {
                    arrowPath.setLastPoint(dpf2(4f), heightHalf - dpf2(5f));
                    arrowPath.lineTo(0, heightHalf);
                    arrowPath.lineTo(dpf2(4f), heightHalf + dpf2(5f));
                }

                slideToLayout = new StaticLayout(slideToCancelString, grayPaint, (int) slideToCancelWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                cancelLayout = new StaticLayout(cancelString, bluePaint, (int) cancelWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (slideToLayout == null || cancelLayout == null || recordCircle == null) {
                return;
            }
            int w = cancelLayout.getWidth() + dp(16);

            grayPaint.setColor(getThemedColor(Theme.key_chat_recordTime));
            grayPaint.setAlpha((int) (slideToAlpha * (1f - cancelToProgress) * slideProgress));
            bluePaint.setAlpha((int) (cancelAlpha * cancelToProgress));
            arrowPaint.setColor(grayPaint.getColor());

            if (smallSize) {
                xOffset = dp(16);
            } else {
                long dt = (System.currentTimeMillis() - lastUpdateTime);
                lastUpdateTime = System.currentTimeMillis();
                if (cancelToProgress == 0 && slideProgress > 0.8f) {
                    if (moveForward) {
                        xOffset += (dp(3) / 250f) * dt;
                        if (xOffset > dp(6)) {
                            xOffset = dp(6);
                            moveForward = false;
                        }
                    } else {
                        xOffset -= (dp(3) / 250f) * dt;
                        if (xOffset < -dp(6)) {
                            xOffset = -dp(6);
                            moveForward = true;
                        }
                    }
                }
            }

            boolean enableTransition = cancelCharOffset >= 0;

            int slideX = (int) ((getMeasuredWidth() - slideToCancelWidth) / 2) + dp(5);
            int cancelX = (int) ((getMeasuredWidth() - cancelWidth) / 2);
            float offset = enableTransition ? slideToLayout.getPrimaryHorizontal(cancelCharOffset) : 0;
            float cancelDiff = enableTransition ? slideX + offset - cancelX : 0;
            float x = slideX + xOffset * (1f - cancelToProgress) * slideProgress - cancelDiff * cancelToProgress + dp(16);

            float offsetY = enableTransition ? 0 : cancelToProgress * dp(12);

            if (cancelToProgress != 1) {
                int slideDelta = (int) (-getMeasuredWidth() / 4 * (1f - slideProgress) + recordCircle.getTranslationX() * 0.3f);
                canvas.save();
                canvas.clipRect((recordTimerView == null ? 0 : recordTimerView.getLeftProperty()) + dp(4), 0, getMeasuredWidth(), getMeasuredHeight());
                canvas.save();
                canvas.translate((int) x - (smallSize ? dp(7) : dp(10)) + slideDelta, offsetY);
                canvas.drawPath(arrowPath, arrowPaint);
                canvas.restore();

                canvas.save();
                canvas.translate((int) x + slideDelta, (getMeasuredHeight() - slideToLayout.getHeight()) / 2f + offsetY);
                slideToLayout.draw(canvas);
                canvas.restore();
                canvas.restore();
            }

            float xi;
            float yi = (getMeasuredHeight() - cancelLayout.getHeight()) / 2f;
            if (!enableTransition) {
                yi -= (dp(12) - offsetY);
            }
            if (enableTransition) {
                xi = x + offset;
            } else {
                xi = cancelX;
            }
            cancelRect.set((int) xi, (int) yi, (int) (xi + cancelLayout.getWidth()), (int) (yi + cancelLayout.getHeight()));
            cancelRect.inset(-dp(16), -dp(16));
            if (cancelToProgress > 0) {
                selectableBackground.setBounds(
                        getMeasuredWidth() / 2 - w, getMeasuredHeight() / 2 - w,
                        getMeasuredWidth() / 2 + w, getMeasuredHeight() / 2 + w
                );
                selectableBackground.draw(canvas);

                canvas.save();
                canvas.translate(xi, yi);
                cancelLayout.draw(canvas);
                canvas.restore();
            } else {
                setPressed(false);
            }

            if (cancelToProgress != 1) {
                invalidate();
            }
        }

        @Keep
        public void setCancelToProgress(float cancelToProgress) {
            this.cancelToProgress = cancelToProgress;
        }

        @Keep
        public float getSlideToCancelWidth() {
            return slideToCancelWidth;
        }

        public void setSlideX(float v) {
            slideProgress = v;
        }
    }

    public class TimerView extends View {
        boolean isRunning;
        boolean stoppedInternal;
        String oldString;
        long startTime;
        long stopTime;
        long lastSendTypingTime;

        SpannableStringBuilder replaceIn = new SpannableStringBuilder();
        SpannableStringBuilder replaceOut = new SpannableStringBuilder();
        SpannableStringBuilder replaceStable = new SpannableStringBuilder();

        StaticLayout inLayout;
        StaticLayout outLayout;

        float replaceTransition;

        TextPaint textPaint;
        final float replaceDistance = dp(15);
        float left;

        public TimerView(Context context) {
            super(context);
        }

        public void start(long milliseconds) {
            isRunning = true;
            startTime = System.currentTimeMillis() - milliseconds;
            lastSendTypingTime = startTime;
            invalidate();
        }

        public void stop() {
            if (isRunning) {
                isRunning = false;
                if (startTime > 0) {
                    stopTime = System.currentTimeMillis();
                }
                invalidate();
            }
            lastSendTypingTime = 0;
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onDraw(Canvas canvas) {
            if (textPaint == null) {
                textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setTextSize(dp(15));
                textPaint.setTypeface(AndroidUtilities.bold());
                textPaint.setColor(getThemedColor(Theme.key_chat_recordTime));
            }
            long currentTimeMillis = System.currentTimeMillis();
            long t = isRunning ? (currentTimeMillis - startTime) : stopTime - startTime;
            long time = t / 1000;
            int ms = (int) (t % 1000L) / 10;

            if (isInVideoMode()) {
                if (t >= 59500 && !stoppedInternal) {
                    startedDraggingX = -1;
                    delegate.needStartRecordVideo(3, true, 0, voiceOnce ? 0x7FFFFFFF : 0, effectId);
                    sendButton.setEffect(effectId = 0);
                    stoppedInternal = true;
                }
            }

            if (isRunning && currentTimeMillis > lastSendTypingTime + 5000) {
                lastSendTypingTime = currentTimeMillis;
                MessagesController.getInstance(currentAccount).sendTyping(dialog_id, getThreadMessageId(), isInVideoMode() ? 7 : 1, 0);
            }

            String newString = AndroidUtilities.formatTimerDurationFast((int) time, ms);
            if (newString.length() >= 3 && oldString != null && oldString.length() >= 3 && newString.length() == oldString.length() && newString.charAt(newString.length() - 3) != oldString.charAt(newString.length() - 3)) {
                int n = newString.length();

                replaceIn.clear();
                replaceOut.clear();
                replaceStable.clear();
                replaceIn.append(newString);
                replaceOut.append(oldString);
                replaceStable.append(newString);

                int inLast = -1;
                int inCount = 0;
                int outLast = -1;
                int outCount = 0;


                for (int i = 0; i < n - 1; i++) {
                    if (oldString.charAt(i) != newString.charAt(i)) {
                        if (outCount == 0) {
                            outLast = i;
                        }
                        outCount++;

                        if (inCount != 0) {
                            EmptyStubSpan span = new EmptyStubSpan();
                            if (i == n - 2) {
                                inCount++;
                            }
                            replaceIn.setSpan(span, inLast, inLast + inCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            replaceOut.setSpan(span, inLast, inLast + inCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            inCount = 0;
                        }
                    } else {
                        if (inCount == 0) {
                            inLast = i;
                        }
                        inCount++;
                        if (outCount != 0) {
                            replaceStable.setSpan(new EmptyStubSpan(), outLast, outLast + outCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            outCount = 0;
                        }
                    }
                }

                if (inCount != 0) {
                    EmptyStubSpan span = new EmptyStubSpan();
                    replaceIn.setSpan(span, inLast, inLast + inCount + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    replaceOut.setSpan(span, inLast, inLast + inCount + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (outCount != 0) {
                    replaceStable.setSpan(new EmptyStubSpan(), outLast, outLast + outCount, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                inLayout = new StaticLayout(replaceIn, textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                outLayout = new StaticLayout(replaceOut, textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                replaceTransition = 1f;
            } else {
                if (replaceStable == null) {
                    replaceStable = new SpannableStringBuilder(newString);
                }
                if (replaceStable.length() == 0 || replaceStable.length() != newString.length()) {
                    replaceStable.clear();
                    replaceStable.append(newString);
                } else {
                    replaceStable.replace(replaceStable.length() - 1, replaceStable.length(), newString, newString.length() - 1 - (newString.length() - replaceStable.length()), newString.length());
                }
            }

            if (replaceTransition != 0) {
                replaceTransition -= 0.15f;
                if (replaceTransition < 0f) {
                    replaceTransition = 0f;
                }
            }

            float y = getMeasuredHeight() / 2;
            float x = 0;

            if (replaceTransition == 0) {
                replaceStable.clearSpans();
                StaticLayout staticLayout = new StaticLayout(replaceStable, textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                canvas.save();
                canvas.translate(x, y - staticLayout.getHeight() / 2f);
                staticLayout.draw(canvas);
                canvas.restore();
                left = x + staticLayout.getLineWidth(0);
            } else {
                if (inLayout != null) {
                    canvas.save();
                    textPaint.setAlpha((int) (255 * (1f - replaceTransition)));
                    canvas.translate(x, y - inLayout.getHeight() / 2f - (replaceDistance * replaceTransition));
                    inLayout.draw(canvas);
                    canvas.restore();
                }

                if (outLayout != null) {
                    canvas.save();
                    textPaint.setAlpha((int) (255 * replaceTransition));
                    canvas.translate(x, y - outLayout.getHeight() / 2f + (replaceDistance * (1f - replaceTransition)));
                    outLayout.draw(canvas);
                    canvas.restore();
                }

                canvas.save();
                textPaint.setAlpha(255);
                StaticLayout staticLayout = new StaticLayout(replaceStable, textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                canvas.translate(x, y - staticLayout.getHeight() / 2f);
                staticLayout.draw(canvas);
                canvas.restore();
                left = x + staticLayout.getLineWidth(0);
            }

            oldString = newString;

            if (isRunning || replaceTransition != 0) {
                invalidate();
            }
        }

        public void updateColors() {
            if (textPaint != null) {
                textPaint.setColor(getThemedColor(Theme.key_chat_recordTime));
            }
        }

        public float getLeftProperty() {
            return left;
        }

        public void reset() {
            isRunning = false;
            stopTime = startTime = 0;
            stoppedInternal = false;
        }
    }

    protected boolean pannelAnimationEnabled() {
        return true;
    }

    @Nullable
    public RecordCircle getRecordCircle() {
        return recordCircle;
    }

    int botCommandLastPosition = -1;
    int botCommandLastTop;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (botCommandsMenuButton != null && botCommandsMenuButton.getTag() != null) {
            botCommandsMenuButton.measure(widthMeasureSpec, heightMeasureSpec);
            ((MarginLayoutParams) emojiButton.getLayoutParams()).leftMargin = dp(10) + (botCommandsMenuButton == null ? 0 : botCommandsMenuButton.getMeasuredWidth());
            if (messageEditText != null) {
                ((MarginLayoutParams) messageEditText.getLayoutParams()).leftMargin = dp(57) + (botCommandsMenuButton == null ? 0 : botCommandsMenuButton.getMeasuredWidth());
            }
        } else if (senderSelectView != null && senderSelectView.getVisibility() == View.VISIBLE) {
            int width = senderSelectView.getLayoutParams().width, height = senderSelectView.getLayoutParams().height;
            senderSelectView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            ((MarginLayoutParams) emojiButton.getLayoutParams()).leftMargin = dp(16) + width;
            if (messageEditText != null) {
                ((MarginLayoutParams) messageEditText.getLayoutParams()).leftMargin = dp(63) + width;
            }
        } else {
            ((MarginLayoutParams) emojiButton.getLayoutParams()).leftMargin = dp(3);
            if (messageEditText != null) {
                ((MarginLayoutParams) messageEditText.getLayoutParams()).leftMargin = dp(50);
            }
        }
        updateBotCommandsMenuContainerTopPadding();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (botWebViewButton != null) {
            if (botCommandsMenuButton != null) {
                botWebViewButton.setMeasuredButtonWidth(botCommandsMenuButton.getMeasuredWidth());
            }
            botWebViewButton.getLayoutParams().height = getMeasuredHeight() - dp(2);
            measureChild(botWebViewButton, widthMeasureSpec, heightMeasureSpec);
        }
//        if (botWebViewMenuContainer != null) {
//            MarginLayoutParams params = (MarginLayoutParams) botWebViewMenuContainer.getLayoutParams();
//            params.bottomMargin = messageEditText == null ? 0 : messageEditText.getMeasuredHeight();
//            measureChild(botWebViewMenuContainer, widthMeasureSpec, heightMeasureSpec);
//        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (botCommandLastPosition != -1 && botCommandsMenuContainer != null) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) botCommandsMenuContainer.listView.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(botCommandLastPosition, botCommandLastTop);
            }
            botCommandLastPosition = -1;
        }
    }

    private void beginDelayedTransition() {
        animationParamsX.put(emojiButton, emojiButton.getX());
        if (messageEditText != null) {
            animationParamsX.put(messageEditText, messageEditText.getX());
        }
    }

    private LongSparseArray<TL_bots.BotInfo> lastBotInfo;

    public void setBotInfo(LongSparseArray<TL_bots.BotInfo> botInfo) {
        setBotInfo(botInfo, true);
    }

    public void setBotInfo(LongSparseArray<TL_bots.BotInfo> botInfo, boolean animate) {
        lastBotInfo = botInfo;

        if (botInfo.size() == 1 && botInfo.valueAt(0).user_id == dialog_id) {
            TL_bots.BotInfo info = botInfo.valueAt(0);
            TL_bots.BotMenuButton menuButton = info.menu_button;
            if (menuButton instanceof TL_bots.TL_botMenuButton) {
                TL_bots.TL_botMenuButton webViewButton = (TL_bots.TL_botMenuButton) menuButton;
                botMenuWebViewTitle = webViewButton.text;
                botMenuWebViewUrl = webViewButton.url;
                botMenuButtonType = BotMenuButtonType.WEB_VIEW;
            } else if (!info.commands.isEmpty()) {
                botMenuButtonType = BotMenuButtonType.COMMANDS;
            } else {
                botMenuButtonType = BotMenuButtonType.NO_BUTTON;
            }
        } else {
            botMenuButtonType = BotMenuButtonType.NO_BUTTON;
        }

        if (botCommandsAdapter != null) {
            botCommandsAdapter.setBotInfo(botInfo);
        }

        updateBotButton(animate);
    }

    public boolean botCommandsMenuIsShowing() {
        return botCommandsMenuButton != null && botCommandsMenuButton.isOpened();
    }

    public void hideBotCommands() {
        if (botCommandsMenuButton != null) {
            botCommandsMenuButton.setOpened(false);
        }

        if (hasBotWebView()) {
//            if (botWebViewMenuContainer != null) {
//                botWebViewMenuContainer.dismiss();
//            }
        } else {
            if (botCommandsMenuContainer != null) {
                botCommandsMenuContainer.dismiss();
            }
        }
    }

    public void setTextTransitionIsRunning(boolean b) {
        textTransitionIsRunning = b;
        sendButtonContainer.invalidate();
    }

    public float getTopViewHeight() {
        if (topView != null && topView.getVisibility() == View.VISIBLE) {
            return topView.getLayoutParams().height;
        }
        return 0;
    }

    public void runEmojiPanelAnimation() {
        AndroidUtilities.cancelRunOnUIThread(runEmojiPanelAnimation);
        runEmojiPanelAnimation.run();
    }

    public Drawable getStickersArrowDrawable() {
        return stickersArrow;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (emojiView == null || emojiView.getVisibility() != View.VISIBLE || emojiView.getStickersExpandOffset() == 0) {
            super.dispatchDraw(canvas);
        } else {
            canvas.save();
            canvas.clipRect(0, dp(2), getMeasuredWidth(), getMeasuredHeight());
            canvas.translate(0, -emojiView.getStickersExpandOffset());
            super.dispatchDraw(canvas);
            canvas.restore();
        }
    }

    private int getThemedColor(int key) {
        if (resourcesProvider != null) {
            return resourcesProvider.getColor(key);
        }
        return Theme.getColor(key);
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public void setChatSearchExpandOffset(float chatSearchExpandOffset) {
        this.chatSearchExpandOffset = chatSearchExpandOffset;
        invalidate();
    }

    public void setHorizontalPadding(float padding, float progress, boolean allowShare) {
        float leftPadding = -padding * (1f - progress);
        float rightPadding = -(padding + dp(40)) * (1f - progress);
        float s = 0.5f + 0.5f * progress;
        emojiButtonPaddingScale = s;
        emojiButtonPaddingAlpha = progress;
        updateEmojiButtonParams();
        emojiButton.setTranslationX(-leftPadding);
        messageTextPaddingTranslationX = -leftPadding - dp(31) * (1f - progress);
        if (recordDeleteImageView != null) {
            recordDeleteImageView.setTranslationX(-leftPadding);
        }
        if (recordCircle != null) {
            recordCircle.setTranslationX(rightPadding);
        }
        if (controlsView != null) {
            controlsView.setTranslationX(rightPadding);
        }
        if (recordTimeContainer != null) {
            recordTimeContainer.setTranslationX(-leftPadding);
        }
        if (recordedAudioPlayButton != null) {
            recordedAudioPlayButton.setTranslationX(-leftPadding);
        }
        if (recordedAudioTimeTextView != null) {
            recordedAudioTimeTextView.setTranslationX(leftPadding);
        }
        sendButtonContainer.setTranslationX(rightPadding);
        sendButtonContainer.setAlpha(allowShare ? progress : 1f);
        sendButtonEnabled = allowShare ? progress == 1f : true;
        attachLayoutPaddingTranslationX = rightPadding;
        attachLayoutPaddingAlpha = progress;
        updateAttachLayoutParams();
        updateMessageTextParams();
        float newPadding = padding * (1f - progress);

        if (horizontalPadding != newPadding) {
            horizontalPadding = newPadding;
            if (seekBarWaveform != null && recordedAudioSeekBar != null) {
                recordedAudioSeekBar.setTranslationX(horizontalPadding);
                recordedAudioSeekBar.invalidate();
                seekBarWaveform.setSize((int) (recordedAudioSeekBar.getMeasuredWidth() - horizontalPadding * 2), recordedAudioSeekBar.getMeasuredHeight());
            }
            if (recordedAudioBackground != null) {
                recordedAudioBackground.invalidate();
            }
        }
        if (messageEditText != null) {
            float scale = lerp(0.88f, 1f, progress);
            messageEditText.setPivotX(0);
            messageEditText.setPivotY(messageEditText.getMeasuredHeight() / 2f);
            messageEditText.setScaleX(scale);
            messageEditText.setScaleY(scale);
            messageEditText.setHintRightOffset(lerp(dp(30), 0, progress));
        }
    }

    private void updateMessageTextParams() {
        if (messageEditText != null) {
            messageEditText.setTranslationX(messageTextPaddingTranslationX + messageTextTranslationX);
        }
    }

    private void updateAttachLayoutParams() {
        if (attachLayout != null) {
            attachLayout.setTranslationX(attachLayoutPaddingTranslationX + attachLayoutTranslationX);
            attachLayout.setAlpha(attachLayoutAlpha * attachLayoutPaddingAlpha);
            attachLayout.setVisibility(attachLayout.getAlpha() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void updateEmojiButtonParams() {
        emojiButton.setScaleX(emojiButtonPaddingScale * emojiButtonScale);
        emojiButton.setScaleY(emojiButtonPaddingScale * emojiButtonScale);
        emojiButton.setAlpha(emojiButtonPaddingAlpha * emojiButtonAlpha);
    }

    public void setOverrideHint(CharSequence overrideHint) {
        setOverrideHint(overrideHint, false);
    }

    public void setOverrideHint(CharSequence overrideHint, boolean animated) {
        this.overrideHint = overrideHint;
        this.overrideHint2 = null;
        updateFieldHint(animated);
    }

    public void setOverrideHint(CharSequence overrideHint, CharSequence overrideHint2, boolean animated) {
        this.overrideHint = overrideHint;
        this.overrideHint2 = overrideHint2;
        updateFieldHint(animated);
    }

    public void setOverrideKeyboardAnimation(boolean overrideKeyboardAnimation) {
        this.overrideKeyboardAnimation = overrideKeyboardAnimation;
    }

    public void onOverrideAnimationEnd() {
        if (animationEndRunnable != null) {
            animationEndRunnable.run();
            animationEndRunnable = null;
        }
    }

    public int getStickersExpandedHeight() {
        return stickersExpandedHeight;
    }

    public void reset() {
        setStickersExpanded(false, true, false);
        showPopup(0, 0, false);
        if (getEditField() != null) {
            if (!TextUtils.isEmpty(getEditField().getText())) {
                getEditField().setText("");
            }
        }
        recordingAudioVideo = false;
        if (audioVideoSendButton != null) {
            audioVideoSendButton.setVisibility(View.VISIBLE);
        }
        recordIsCanceled = true;
        isRecordingStateChanged();
        cancelRecordInterfaceInternal();
        hideRecordedAudioPanelInternal();
        if (controlsView != null) {
            controlsView.setVisibility(GONE);
        }
        if (recordCircle != null) {
            recordCircle.setSendButtonInvisible();
        }
    }

    public enum BotMenuButtonType {
        NO_BUTTON,
        COMMANDS,
        WEB_VIEW
    }

    public void freezeEmojiView(boolean freeze) {
        emojiViewFrozen = freeze;
        if (emojiView != null) {
            emojiView.freeze(freeze);
        }
    }

    public static class SendButton extends View {

        public static final float INFINITE_LOADING = -3f;

        public final Theme.ResourcesProvider resourcesProvider;

        public final int resId;

        private final Drawable drawable;
        private final Drawable inactiveDrawable;
        private final Drawable drawableInverse;
        private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emojiDrawable;

        public boolean center;

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint countClearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public SendButton(Context context, int resId, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            this.resId = resId;

            this.resourcesProvider = resourcesProvider;

            drawable = context.getResources().getDrawable(resId).mutate();
            inactiveDrawable = context.getResources().getDrawable(resId).mutate();
            drawableInverse = context.getResources().getDrawable(resId).mutate();
            emojiDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, dp(14));

            loadingPaint.setStyle(Paint.Style.STROKE);
            loadingPaint.setStrokeWidth(dp(2));
            loadingPaint.setStrokeJoin(Paint.Join.ROUND);
            loadingPaint.setStrokeCap(Paint.Cap.ROUND);

            countClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            count.setCallback(this);
            count.setTextColor(0xFFFFFFFF);
            count.setTextSize(dp(14));
            count.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
            count.setGravity(Gravity.CENTER);
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == count || who == emojiDrawable || super.verifyDrawable(who);
        }

        public void setEffect(long effectId) {
            TLRPC.TL_availableEffect effect = MessagesController.getInstance(UserConfig.selectedAccount).getEffect(effectId);
            setEmoji(effect != null ? Emoji.getEmojiDrawable(effect.emoticon) : null);
        }

        public void setEmoji(Drawable drawable) {
            emojiDrawable.set(drawable, true);
        }

        public void copyEmojiTo(SendButton sendButton) {
            sendButton.setEmoji(emojiDrawable.getDrawable());
        }

        @Override
        public void invalidate(int l, int t, int r, int b) {
            super.invalidate(l, t, r, b);
        }

        public boolean isOpen() {
            return false;
        }

        public boolean isInScheduleMode() {
            return false;
        }

        public boolean isInactive() {
            return false;
        }

        public boolean shouldDrawBackground() {
            return false;
        }

        public int getFillColor() {
            return Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider);
        }

        public final AnimatedFloat open = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

        private boolean infiniteLoading;
        private final FastOutSlowInInterpolator loadingInterpolator = new FastOutSlowInInterpolator();

        private boolean loadingShown;
        private float loadingProgress;
        private final AnimatedFloat loadingAnimatedShown = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final AnimatedFloat loadingAnimatedProgress = new AnimatedFloat(this, 0, 500, CubicBezierInterpolator.EASE_OUT_QUINT);

        private final Path path = new Path();
        private final Paint loadingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final AnimatedTextView.AnimatedTextDrawable count = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
        private float countBounceScale = 1f;

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

            final boolean showingPopup = isOpen();
            updateColors(showingPopup);

            final boolean inactive = isInactive();
            final Drawable drawable = inactive ? this.inactiveDrawable : this.drawable;

            int x = (getMeasuredWidth() - drawable.getIntrinsicWidth()) / 2;
            int y = (getMeasuredHeight() - drawable.getIntrinsicHeight()) / 2;
            if (center) {

            } else if (isInScheduleMode()) {
                y -= dp(1);
            } else {
                x += dp(2);
            }

            final float loadingShown = this.loadingAnimatedShown.set(this.loadingShown);

            final float openProgress = open.set(showingPopup);
            if (openProgress < 1) {
                drawable.setBounds(x, y, x + drawable.getIntrinsicWidth(), y + drawable.getIntrinsicHeight());
                drawable.draw(canvas);

                final int cx = getMeasuredWidth() / 2 + dp(12);
                final int cy = getMeasuredHeight() / 2 + dp(12);
                final int r = dp(8);

                emojiDrawable.setBounds(cx-r, cy-r, cx+r, cy+r);
                emojiDrawable.setAlpha((int) (0xFF * (1f - openProgress)));
                emojiDrawable.draw(canvas);
            }
            if (openProgress > 0) {
                final int cx = getMeasuredWidth() / 2;
                final int cy = getMeasuredHeight() / 2;
                canvas.save();
                path.rewind();
                path.addCircle(cx, cy, (cx - dp(4)) * openProgress, Path.Direction.CW);
                canvas.drawCircle(cx, cy, (cx - dp(4)) * openProgress, backgroundPaint);
                canvas.clipPath(path);
                if (loadingShown > 0) {
                    loadingPaint.setColor(0xFFFFFFFF);
                    loadingPaint.setAlpha((int) (0xFF * loadingShown));
                    final float r = dp(8.66f);
                    AndroidUtilities.rectTmp.set(cx - r, cy - r, cx + r, cy + r);
                    if (infiniteLoading) {
                        final long t = System.currentTimeMillis() % 5400L;
                        float segment0 = Math.max(0, 1520 * t / 5400f - 20);
                        float segment1 = 1520 * t / 5400f;
                        for (int i = 0; i < 4; ++i) {
                            segment1 += loadingInterpolator.getInterpolation((t - i * 1350) / 667f) * 250;
                            segment0 += loadingInterpolator.getInterpolation((t - (667 + i * 1350)) / 667f) * 250;
                        }
                        canvas.drawArc(AndroidUtilities.rectTmp, segment0, segment1 - segment0, false, loadingPaint);
                    } else {
                        float startAngle = -90f;
                        startAngle += (System.currentTimeMillis() % 3000L / 1000f * 120f) % 360f;
                        canvas.drawArc(AndroidUtilities.rectTmp, startAngle, 360 * loadingAnimatedProgress.set(this.loadingProgress), false, loadingPaint);
                    }
                    canvas.save();
                    final float s = AndroidUtilities.lerp(1f, .6f, loadingShown);
                    canvas.scale(s, s, cx, cy);
                    invalidate();
                }
                drawableInverse.setAlpha((int) (0xFF * (1f - loadingShown)));
                drawableInverse.setBounds(x, y, x + drawableInverse.getIntrinsicWidth(), y + drawableInverse.getIntrinsicHeight());
                drawableInverse.draw(canvas);
                if (loadingShown > 0) {
                    canvas.restore();
                }
                canvas.restore();
            }

            final float sz = Math.max(dp(12) + count.getCurrentWidth(), dp(24));
            final float cx = getMeasuredWidth() - sz / 2f;
            final float cy = getMeasuredHeight() - sz / 2f;
            count.setBounds((int) (cx - sz / 2f), (int) (cy - sz / 2f), (int) (cx + sz / 2f), (int) (cy + sz / 2f));
            final float countScale = count.isNotEmpty();
            if (countScale > 0) {
                canvas.drawCircle(cx, cy, (sz / 2f + dp(2)) * countScale * countBounceScale, countClearPaint);
                canvas.drawCircle(cx, cy, sz / 2f * countScale * countBounceScale, backgroundPaint);
                count.draw(canvas);
            }

            canvas.restore();

            super.onDraw(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (getAlpha() <= 0f) { // for accessibility
                return false;
            }
            return super.onTouchEvent(event);
        }

        private int drawableColor;

        private void updateColors(boolean showingPopup) {
            int color = Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider);
            if (color != drawableColor) {
                drawableColor = color;
                drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend, resourcesProvider), PorterDuff.Mode.SRC_IN));
                int c = Theme.getColor(Theme.key_chat_messagePanelIcons, resourcesProvider);
                inactiveDrawable.setColorFilter(new PorterDuffColorFilter(Color.argb(0xb4, Color.red(c), Color.green(c), Color.blue(c)), PorterDuff.Mode.SRC_IN));
                drawableInverse.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed, resourcesProvider), PorterDuff.Mode.SRC_IN));
            }
            if (shouldDrawBackground()) {
                backgroundPaint.setColor(getFillColor());
            } else {
                backgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 75));
            }
        }

        public void setLoading(boolean shown, float progress) {
            if (loadingShown == shown && (!shown || Math.abs(loadingProgress - progress) < 0.01f) && infiniteLoading == Math.abs(progress - INFINITE_LOADING) < 0.01f)
                return;
            infiniteLoading = Math.abs(progress - INFINITE_LOADING) < 0.01f;
            if (!loadingShown && shown) {
                loadingAnimatedProgress.set(0, true);
            }
            loadingAnimatedShown.setDelay(shown && loadingAnimatedShown.get() >= 1f ? 650 : 0);
            loadingShown = shown;
            loadingProgress = !shown ? 1f : progress;
            invalidate();
        }

        public void setCount(int count, boolean animated) {
            this.count.setText(count <= 0 ? "" : "" + count, animated);
            invalidate();
        }

        public void copyCountTo(SendButton sendButton) {
            sendButton.count.setText(this.count.getText(), false);
            sendButton.countBounceScale = countBounceScale;
        }

        private ValueAnimator bounceCountAnimator;
        public void bounceCount() {
            if (bounceCountAnimator != null) {
                bounceCountAnimator.cancel();
            }
            bounceCountAnimator = ValueAnimator.ofFloat(.9f, 1f);
            bounceCountAnimator.addUpdateListener(anm -> {
                countBounceScale = (float) anm.getAnimatedValue();
            });
            bounceCountAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    countBounceScale = 1f;
                }
            });
            bounceCountAnimator.setDuration(180);
            bounceCountAnimator.setInterpolator(new OvershootInterpolator());
            bounceCountAnimator.start();
        }
    }

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient clipGradient = new LinearGradient(0, 0, 0, 16, new int[] { 0xffffffff, 0x00ffffff }, new float[] { 0, 1 }, Shader.TileMode.CLAMP);
    private final Matrix clipMatrix = new Matrix();
    private final AnimatedFloat topGradientAlpha = new AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat bottomGradientAlpha = new AnimatedFloat(this, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
    {
        gradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        gradientPaint.setShader(clipGradient);
    }

    public boolean drawMessageEditText(Canvas canvas, Utilities.Callback0Return<Boolean> drawChild) {
        float topAlpha = topGradientAlpha.set(messageEditText.canScrollVertically(-1));
        float bottomAlpha = bottomGradientAlpha.set(messageEditText.canScrollVertically(1));
        if (topAlpha <= 0 && bottomAlpha <= 0) {
            return drawChild.run();
        }
        canvas.saveLayerAlpha(0, 0, messageEditText.getX() + messageEditText.getMeasuredWidth() + dp(5), messageEditText.getY() + messageEditText.getMeasuredHeight() + dp(2), 0xFF, Canvas.ALL_SAVE_FLAG);
        final boolean result = drawChild.run();
        canvas.save();

        if (topAlpha > 0) {
            AndroidUtilities.rectTmp.set(
                    messageEditText.getX() - dp(5),
                    messageEditText.getY() + animatedTop - 1,
                    messageEditText.getX() + messageEditText.getMeasuredWidth() + dp(5),
                    messageEditText.getY() + animatedTop + dp(13)
            );
            clipMatrix.reset();
            clipMatrix.postScale(1f, AndroidUtilities.rectTmp.height() / 16f);
            clipMatrix.postTranslate(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);
            clipGradient.setLocalMatrix(clipMatrix);
            gradientPaint.setAlpha((int) (0xFF * topAlpha));
            canvas.drawRect(AndroidUtilities.rectTmp, gradientPaint);
        }

        if (bottomAlpha > 0) {
            AndroidUtilities.rectTmp.set(
                    messageEditText.getX() - dp(5),
                    messageEditText.getY() + messageEditText.getMeasuredHeight() - dp(13 + 2),
                    messageEditText.getX() + messageEditText.getMeasuredWidth() + dp(5),
                    messageEditText.getY() + messageEditText.getMeasuredHeight() + dp(2) + 1
            );
            clipMatrix.reset();
            clipMatrix.postScale(1f, AndroidUtilities.rectTmp.height() / 16f);
            clipMatrix.postRotate(180);
            clipMatrix.postTranslate(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.bottom);
            clipGradient.setLocalMatrix(clipMatrix);
            gradientPaint.setAlpha((int) (0xFF * bottomAlpha));
            canvas.drawRect(AndroidUtilities.rectTmp, gradientPaint);
        }

        canvas.restore();
        canvas.restore();
        return result;
    }
}
