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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.os.BuildCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.CameraController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupStickersActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.StickersActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ChatActivityEnterView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate, StickersAlert.StickersAlertDelegate {

    public interface ChatActivityEnterViewDelegate {
        void onMessageSend(CharSequence message, boolean notify, int scheduleDate);
        void needSendTyping();
        void onTextChanged(CharSequence text, boolean bigChange);
        void onTextSelectionChanged(int start, int end);
        void onTextSpansChanged(CharSequence text);
        void onAttachButtonHidden();
        void onAttachButtonShow();
        void onWindowSizeChanged(int size);
        void onStickersTab(boolean opened);
        void onMessageEditEnd(boolean loading);
        void didPressedAttachButton();
        void needStartRecordVideo(int state, boolean notify, int scheduleDate);
        void needChangeVideoPreviewState(int state, float seekProgress);
        void onSwitchRecordMode(boolean video);
        void onPreAudioVideoRecord();
        void needStartRecordAudio(int state);
        void needShowMediaBanHint();
        void onStickersExpandedChange();
        void onUpdateSlowModeButton(View button, boolean show, CharSequence time);
        default void scrollToSendingMessage() {

        }
        default void openScheduledMessages() {

        }
        default boolean hasScheduledMessages() {
            return true;
        }
    }

    private int currentAccount = UserConfig.selectedAccount;
    private AccountInstance accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount);

    private SeekBarWaveform seekBarWaveform;

    private class SeekBarWaveformView extends View {

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
            seekBarWaveform.setSize(right - left, bottom - top);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            seekBarWaveform.setColors(Theme.getColor(Theme.key_chat_recordedVoiceProgress), Theme.getColor(Theme.key_chat_recordedVoiceProgressInner), Theme.getColor(Theme.key_chat_recordedVoiceProgress));
            seekBarWaveform.draw(canvas);
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

    private EditTextCaption messageEditText;
    private SimpleTextView slowModeButton;
    private int slowModeTimer;
    private Runnable updateSlowModeRunnable;
    private View sendButton;
    private Drawable sendButtonDrawable;
    private Drawable inactinveSendButtonDrawable;
    private Drawable sendButtonInverseDrawable;
    private ActionBarPopupWindow sendPopupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout;
    private ImageView cancelBotButton;
    private ImageView[] emojiButton = new ImageView[2];
    private ImageView expandStickersButton;
    private EmojiView emojiView;
    private boolean emojiViewVisible;
    private TextView recordTimeText;
    private FrameLayout audioVideoButtonContainer;
    private AnimatorSet audioVideoButtonAnimation;
    private AnimatorSet emojiButtonAnimation;
    private ImageView audioSendButton;
    private ImageView videoSendButton;
    private FrameLayout recordPanel;
    private FrameLayout recordedAudioPanel;
    private VideoTimelineView videoTimelineView;
    @SuppressWarnings("FieldCanBeLocal")
    private ImageView recordDeleteImageView;
    private SeekBarWaveformView recordedAudioSeekBar;
    private View recordedAudioBackground;
    private ImageView recordedAudioPlayButton;
    private TextView recordedAudioTimeTextView;
    private LinearLayout slideText;
    @SuppressWarnings("FieldCanBeLocal")
    private ImageView recordCancelImage;
    @SuppressWarnings("FieldCanBeLocal")
    private TextView recordCancelText;
    private TextView recordSendText;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayout recordTimeContainer;
    private RecordDot recordDot;
    private SizeNotifierFrameLayout sizeNotifierLayout;
    private int originalViewHeight;
    private LinearLayout attachLayout;
    private ImageView attachButton;
    private ImageView botButton;
    private LinearLayout textFieldContainer;
    private FrameLayout sendButtonContainer;
    private FrameLayout doneButtonContainer;
    private ImageView doneButtonImage;
    private AnimatorSet doneButtonAnimation;
    private ContextProgressView doneButtonProgress;
    private View topView;
    private View topLineView;
    private BotKeyboardView botKeyboardView;
    private ImageView notifyButton;
    private ImageView scheduledButton;
    private boolean scheduleButtonHidden;
    private AnimatorSet scheduledButtonAnimation;
    private RecordCircle recordCircle;
    private CloseProgressDrawable2 progressDrawable;
    private Paint dotPaint;
    private Drawable playDrawable;
    private Drawable pauseDrawable;
    private int searchingType;
    private Runnable focusRunnable;

    private boolean destroyed;

    private MessageObject editingMessageObject;
    private int editingMessageReqId;
    private boolean editingCaption;

    private TLRPC.ChatFull info;

    private boolean hasRecordVideo;

    private int currentPopupContentType = -1;
    private int currentEmojiIcon = -1;

    private boolean silent;
    private boolean canWriteToChannel;

    private boolean isPaused = true;
    private boolean showKeyboardOnResume;

    private MessageObject botButtonsMessageObject;
    private TLRPC.TL_replyKeyboardMarkup botReplyMarkup;
    private int botCount;
    private boolean hasBotCommands;

    private PowerManager.WakeLock wakeLock;
    private AnimatorSet runningAnimation;
    private AnimatorSet runningAnimation2;
    private AnimatorSet runningAnimationAudio;
    private int runningAnimationType;
    private int recordInterfaceState;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private int emojiPadding;
    private boolean sendByEnter;
    private long lastTypingTimeSend;
    private String lastTimeString;
    private long lastTypingSendTime;
    private float startedDraggingX = -1;
    private float distCanMove = AndroidUtilities.dp(80);
    private boolean recordingAudioVideo;
    private int recordingGuid;
    private boolean forceShowSendButton;
    private boolean allowStickers;
    private boolean allowGifs;

    private int lastSizeChangeValue1;
    private boolean lastSizeChangeValue2;

    private Activity parentActivity;
    private ChatActivity parentFragment;
    private long dialog_id;
    private boolean ignoreTextChange;
    private int innerTextChange;
    private MessageObject replyingMessageObject;
    private MessageObject botMessageObject;
    private TLRPC.WebPage messageWebPage;
    private boolean messageWebPageSearch = true;
    private ChatActivityEnterViewDelegate delegate;

    private TLRPC.TL_document audioToSend;
    private String audioToSendPath;
    private MessageObject audioToSendMessageObject;
    private VideoEditedInfo videoToSendMessageObject;

    private boolean topViewShowed;
    private boolean needShowTopView;
    private boolean allowShowTopView;
    private AnimatorSet currentTopViewAnimation;

    private MessageObject pendingMessageObject;
    private TLRPC.KeyboardButton pendingLocationButton;

    private boolean waitingForKeyboardOpen;
    private Runnable openKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            if (!destroyed && messageEditText != null && waitingForKeyboardOpen && !keyboardVisible && !AndroidUtilities.usingHardwareInput && !AndroidUtilities.isInMultiwindow) {
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
                        if (!stickersTabOpen && searchingType == 0) {
                            if (searchingType != 0) {
                                searchingType = 0;
                                emojiView.closeSearch(true);
                                emojiView.hideSearchKeyboard();
                            }
                            setStickersExpanded(false, true, false);
                        } else if (searchingType != 0) {
                            searchingType = curPage == 0 ? 2 : 1;
                            checkStickresExpandHeight();
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

    private Runnable onFinishInitCameraRunnable = new Runnable() {
        @Override
        public void run() {
            if (delegate != null) {
                delegate.needStartRecordVideo(0, true, 0);
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
            recordCircle.setLockTranslation(10000);
            recordSendText.setAlpha(0.0f);
            slideText.setAlpha(1.0f);
            slideText.setTranslationY(0);
            if (videoSendButton != null && videoSendButton.getTag() != null) {
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
                        parentActivity.requestPermissions(permissions, 3);
                        return;
                    }
                }
                if (!CameraController.getInstance().isCameraInitied()) {
                    CameraController.getInstance().initCamera(onFinishInitCameraRunnable);
                } else {
                    onFinishInitCameraRunnable.run();
                }
            } else {
                if (parentFragment != null) {
                    if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        parentActivity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                        return;
                    }
                }
                delegate.needStartRecordAudio(1);
                startedDraggingX = -1;
                MediaController.getInstance().startRecording(currentAccount, dialog_id, replyingMessageObject, recordingGuid);
                updateRecordIntefrace();
                audioVideoButtonContainer.getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
    };

    private class RecordDot extends View {

        private float alpha;
        private long lastUpdateTime;
        private boolean isIncr;

        public RecordDot(Context context) {
            super(context);
            redDotPaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceDot));
        }

        public void resetAlpha() {
            alpha = 1.0f;
            lastUpdateTime = System.currentTimeMillis();
            isIncr = false;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            redDotPaint.setAlpha((int) (255 * alpha));
            long dt = (System.currentTimeMillis() - lastUpdateTime);
            if (!isIncr) {
                alpha -= dt / 400.0f;
                if (alpha <= 0) {
                    alpha = 0;
                    isIncr = true;
                }
            } else {
                alpha += dt / 400.0f;
                if (alpha >= 1) {
                    alpha = 1;
                    isIncr = false;
                }
            }
            lastUpdateTime = System.currentTimeMillis();
            canvas.drawCircle(AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(5), redDotPaint);
            invalidate();
        }
    }

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintRecord = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable micDrawable;
    private Drawable cameraDrawable;
    private Drawable sendDrawable;
    private Drawable lockDrawable;
    private Drawable lockTopDrawable;
    private Drawable lockArrowDrawable;
    private Drawable lockBackgroundDrawable;
    private Drawable lockShadowDrawable;
    private RectF rect = new RectF();

    private class RecordCircle extends View {

        private float scale;
        private float amplitude;
        private float animateToAmplitude;
        private float animateAmplitudeDiff;
        private long lastUpdateTime;
        private float lockAnimatedTranslation;
        private float startTranslation;
        private boolean sendButtonVisible;
        private boolean pressed;

        private VirtualViewHelper virtualViewHelper;

        public RecordCircle(Context context) {
            super(context);
            paint.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
            paintRecord.setColor(Theme.getColor(Theme.key_chat_messagePanelVoiceShadow));

            lockDrawable = getResources().getDrawable(R.drawable.lock_middle);
            lockDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoiceLock), PorterDuff.Mode.MULTIPLY));
            lockTopDrawable = getResources().getDrawable(R.drawable.lock_top);
            lockTopDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoiceLock), PorterDuff.Mode.MULTIPLY));
            lockArrowDrawable = getResources().getDrawable(R.drawable.lock_arrow);
            lockArrowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoiceLock), PorterDuff.Mode.MULTIPLY));
            lockBackgroundDrawable = getResources().getDrawable(R.drawable.lock_round);
            lockBackgroundDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoiceLockBackground), PorterDuff.Mode.MULTIPLY));
            lockShadowDrawable = getResources().getDrawable(R.drawable.lock_round_shadow);
            lockShadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoiceLockShadow), PorterDuff.Mode.MULTIPLY));

            micDrawable = getResources().getDrawable(R.drawable.input_mic).mutate();
            micDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));

            cameraDrawable = getResources().getDrawable(R.drawable.input_video).mutate();
            cameraDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));

            sendDrawable = getResources().getDrawable(R.drawable.ic_send).mutate();
            sendDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));

            virtualViewHelper = new VirtualViewHelper(this);
            ViewCompat.setAccessibilityDelegate(this, virtualViewHelper);
        }

        public void setAmplitude(double value) {
            animateToAmplitude = (float) Math.min(100, value) / 100.0f;
            animateAmplitudeDiff = (animateToAmplitude - amplitude) / 150.0f;
            lastUpdateTime = System.currentTimeMillis();
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

        @Keep
        public void setLockAnimatedTranslation(float value) {
            lockAnimatedTranslation = value;
            invalidate();
        }

        public float getLockAnimatedTranslation() {
            return lockAnimatedTranslation;
        }

        public boolean isSendButtonVisible() {
            return sendButtonVisible;
        }

        public void setSendButtonInvisible() {
            sendButtonVisible = false;
            invalidate();
        }

        public int setLockTranslation(float value) {
            if (value == 10000) {
                sendButtonVisible = false;
                lockAnimatedTranslation = -1;
                startTranslation = -1;
                invalidate();
                return 0;
            } else {
                if (sendButtonVisible) {
                    return 2;
                }
                if (lockAnimatedTranslation == -1) {
                    startTranslation = value;
                }
                lockAnimatedTranslation = value;
                invalidate();
                if (startTranslation - lockAnimatedTranslation >= AndroidUtilities.dp(57)) {
                    sendButtonVisible = true;
                    return 2;
                }
            }
            return 1;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (sendButtonVisible) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    return pressed = lockBackgroundDrawable.getBounds().contains(x, y);
                } else if (pressed) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (lockBackgroundDrawable.getBounds().contains(x, y)) {
                            if (videoSendButton != null && videoSendButton.getTag() != null) {
                                delegate.needStartRecordVideo(3, true, 0);
                            } else {
                                MediaController.getInstance().stopRecording(2, true, 0);
                                delegate.needStartRecordAudio(0);
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int cx = getMeasuredWidth() / 2;
            int cy = AndroidUtilities.dp(170);
            float yAdd = 0;

            if (lockAnimatedTranslation != 10000) {
                yAdd = Math.max(0, (int) (startTranslation - lockAnimatedTranslation));
                if (yAdd > AndroidUtilities.dp(57)) {
                    yAdd = AndroidUtilities.dp(57);
                }
            }
            cy -= yAdd;

            float sc;
            float alpha;
            if (scale <= 0.5f) {
                alpha = sc = scale / 0.5f;
            } else if (scale <= 0.75f) {
                sc = 1.0f - (scale - 0.5f) / 0.25f * 0.1f;
                alpha = 1;
            } else {
                sc = 0.9f + (scale - 0.75f) / 0.25f * 0.1f;
                alpha = 1;
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
            lastUpdateTime = System.currentTimeMillis();
            if (amplitude != 0) {
                canvas.drawCircle(getMeasuredWidth() / 2.0f, cy, (AndroidUtilities.dp(42) + AndroidUtilities.dp(20) * amplitude) * scale, paintRecord);
            }
            canvas.drawCircle(getMeasuredWidth() / 2.0f, cy, AndroidUtilities.dp(42) * sc, paint);
            Drawable drawable;
            if (isSendButtonVisible()) {
                drawable = sendDrawable;
            } else {
                drawable = videoSendButton != null && videoSendButton.getTag() != null ? cameraDrawable : micDrawable;
            }
            drawable.setBounds(cx - drawable.getIntrinsicWidth() / 2, cy - drawable.getIntrinsicHeight() / 2, cx + drawable.getIntrinsicWidth() / 2, cy + drawable.getIntrinsicHeight() / 2);
            drawable.setAlpha((int) (255 * alpha));
            drawable.draw(canvas);

            float moveProgress = 1.0f - yAdd / AndroidUtilities.dp(57);
            float moveProgress2 = Math.max(0.0f, 1.0f - yAdd / AndroidUtilities.dp(57) * 2);
            int lockSize;
            int lockY;
            int lockTopY;
            int lockMiddleY;
            int lockArrowY;
            int intAlpha = (int) (alpha * 255);
            if (isSendButtonVisible()) {
                lockSize = AndroidUtilities.dp(31);
                lockY = AndroidUtilities.dp(57) + (int) (AndroidUtilities.dp(30) * (1.0f - sc) - yAdd + AndroidUtilities.dp(20) * moveProgress);
                lockTopY = lockY + AndroidUtilities.dp(5);
                lockMiddleY = lockY + AndroidUtilities.dp(11);
                lockArrowY = lockY + AndroidUtilities.dp(25);

                intAlpha *= yAdd / AndroidUtilities.dp(57);
                lockBackgroundDrawable.setAlpha(255);
                lockShadowDrawable.setAlpha(255);
                lockTopDrawable.setAlpha(intAlpha);
                lockDrawable.setAlpha(intAlpha);
                lockArrowDrawable.setAlpha((int) (intAlpha * moveProgress2));
            } else {
                lockSize = AndroidUtilities.dp(31) + (int) (AndroidUtilities.dp(29) * moveProgress);
                lockY = AndroidUtilities.dp(57) + (int) (AndroidUtilities.dp(30) * (1.0f - sc)) - (int) yAdd;
                lockTopY = lockY + AndroidUtilities.dp(5) + (int) (AndroidUtilities.dp(4) * moveProgress);
                lockMiddleY = lockY + AndroidUtilities.dp(11) + (int) (AndroidUtilities.dp(10) * moveProgress);
                lockArrowY = lockY + AndroidUtilities.dp(25) + (int) (AndroidUtilities.dp(16) * moveProgress);

                lockBackgroundDrawable.setAlpha(intAlpha);
                lockShadowDrawable.setAlpha(intAlpha);
                lockTopDrawable.setAlpha(intAlpha);
                lockDrawable.setAlpha(intAlpha);
                lockArrowDrawable.setAlpha((int) (intAlpha * moveProgress2));
            }

            lockBackgroundDrawable.setBounds(cx - AndroidUtilities.dp(15), lockY, cx + AndroidUtilities.dp(15), lockY + lockSize);
            lockBackgroundDrawable.draw(canvas);
            lockShadowDrawable.setBounds(cx - AndroidUtilities.dp(16), lockY - AndroidUtilities.dp(1), cx + AndroidUtilities.dp(16), lockY + lockSize + AndroidUtilities.dp(1));
            lockShadowDrawable.draw(canvas);
            lockTopDrawable.setBounds(cx - AndroidUtilities.dp(6), lockTopY, cx + AndroidUtilities.dp(6), lockTopY + AndroidUtilities.dp(14));
            lockTopDrawable.draw(canvas);
            lockDrawable.setBounds(cx - AndroidUtilities.dp(7), lockMiddleY, cx + AndroidUtilities.dp(7), lockMiddleY + AndroidUtilities.dp(12));
            lockDrawable.draw(canvas);
            lockArrowDrawable.setBounds(cx - AndroidUtilities.dp(7.5f), lockArrowY, cx + AndroidUtilities.dp(7.5f), lockArrowY + AndroidUtilities.dp(9));
            lockArrowDrawable.draw(canvas);
            if (isSendButtonVisible()) {
                redDotPaint.setAlpha(255);
                rect.set(cx - AndroidUtilities.dp2(6.5f), lockY + AndroidUtilities.dp(9), cx + AndroidUtilities.dp(6.5f), lockY + AndroidUtilities.dp(9 + 13));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(1), AndroidUtilities.dp(1), redDotPaint);
            }
        }

        @Override
        protected boolean dispatchHoverEvent(MotionEvent event) {
            return super.dispatchHoverEvent(event) || virtualViewHelper.dispatchHoverEvent(event);
        }

        private class VirtualViewHelper extends ExploreByTouchHelper {

            public VirtualViewHelper(@NonNull View host) {
                super(host);
            }

            @Override
            protected int getVirtualViewAt(float x, float y) {
                if (isSendButtonVisible()) {
                    if (sendDrawable.getBounds().contains((int) x, (int) y)) {
                        return 1;
                    } else if (lockBackgroundDrawable.getBounds().contains((int) x, (int) y)) {
                        return 2;
                    }
                }
                return HOST_ID;
            }

            @Override
            protected void getVisibleVirtualViews(List<Integer> list) {
                if(isSendButtonVisible()) {
                    list.add(1);
                    list.add(2);
                }
            }

            @Override
            protected void onPopulateNodeForVirtualView(int id, @NonNull AccessibilityNodeInfoCompat info) {
                if (id == 1) {
                    info.setBoundsInParent(sendDrawable.getBounds());
                    info.setText(LocaleController.getString("Send", R.string.Send));
                } else if(id == 2) {
                    info.setBoundsInParent(lockBackgroundDrawable.getBounds());
                    info.setText(LocaleController.getString("Stop", R.string.Stop));
                }
            }

            @Override
            protected boolean onPerformActionForVirtualView(int id, int action, @Nullable Bundle args) {
                return true;
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public ChatActivityEnterView(Activity context, SizeNotifierFrameLayout parent, ChatActivity fragment, final boolean isChat) {
        super(context);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Theme.getColor(Theme.key_chat_emojiPanelNewTrending));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.audioRouteChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.sendingMessagesChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoad);

        parentActivity = context;
        parentFragment = fragment;
        if (fragment != null) {
            recordingGuid = parentFragment.getClassGuid();
        }
        sizeNotifierLayout = parent;
        sizeNotifierLayout.setDelegate(this);
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        sendByEnter = preferences.getBoolean("send_by_enter", false);

        textFieldContainer = new LinearLayout(context);
        textFieldContainer.setOrientation(LinearLayout.HORIZONTAL);
        textFieldContainer.setClipChildren(false);
        textFieldContainer.setClipToPadding(false);
        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 2, 0, 0));

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (scheduledButton != null) {
                    int x = getMeasuredWidth() - AndroidUtilities.dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48) - AndroidUtilities.dp(48);
                    scheduledButton.layout(x, scheduledButton.getTop(), x + scheduledButton.getMeasuredWidth(), scheduledButton.getBottom());
                }
            }
        };
        textFieldContainer.addView(frameLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.BOTTOM));

        for (int a = 0; a < 2; a++) {
            emojiButton[a] = new ImageView(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (getTag() != null && attachLayout != null && !emojiViewVisible && !MediaDataController.getInstance(currentAccount).getUnreadStickerSets().isEmpty() && dotPaint != null) {
                        int x = getWidth() / 2 + AndroidUtilities.dp(4 + 5);
                        int y = getHeight() / 2 - AndroidUtilities.dp(13 - 5);
                        canvas.drawCircle(x, y, AndroidUtilities.dp(5), dotPaint);
                    }
                }
            };
            emojiButton[a].setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            emojiButton[a].setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            frameLayout.addView(emojiButton[a], LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT, 3, 0, 0, 0));
            emojiButton[a].setOnClickListener(view -> {
                if (!isPopupShowing() || currentPopupContentType != 0) {
                    showPopup(1, 0);
                    emojiView.onOpen(messageEditText.length() > 0);
                } else {
                    if (searchingType != 0) {
                        searchingType = 0;
                        emojiView.closeSearch(false);
                        messageEditText.requestFocus();
                    }
                    openKeyboardInternal();
                }
            });
            emojiButton[a].setContentDescription(LocaleController.getString("AccDescrEmojiButton", R.string.AccDescrEmojiButton));
            if (a == 1) {
                emojiButton[a].setVisibility(INVISIBLE);
                emojiButton[a].setAlpha(0.0f);
                emojiButton[a].setScaleX(0.1f);
                emojiButton[a].setScaleY(0.1f);
            }
        }
        setEmojiButtonImage(false, false);

        messageEditText = new EditTextCaption(context) {

            private void send(InputContentInfoCompat inputContentInfo, boolean notify, int scheduleDate) {
                ClipDescription description = inputContentInfo.getDescription();
                if (description.hasMimeType("image/gif")) {
                    SendMessagesHelper.prepareSendingDocument(accountInstance, null, null, inputContentInfo.getContentUri(), null, "image/gif", dialog_id, replyingMessageObject, inputContentInfo, null, notify, 0);
                } else {
                    SendMessagesHelper.prepareSendingPhoto(accountInstance, null, inputContentInfo.getContentUri(), dialog_id, replyingMessageObject, null, null, null, inputContentInfo, 0, null, notify, 0);
                }
                if (delegate != null) {
                    delegate.onMessageSend(null, true, scheduleDate);
                }
            }

            @Override
            public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
                final InputConnection ic = super.onCreateInputConnection(editorInfo);
                try {
                    EditorInfoCompat.setContentMimeTypes(editorInfo, new String[]{"image/gif", "image/*", "image/jpg", "image/png"});

                    final InputConnectionCompat.OnCommitContentListener callback = (inputContentInfo, flags, opts) -> {
                        if (BuildCompat.isAtLeastNMR1() && (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                            try {
                                inputContentInfo.requestPermission();
                            } catch (Exception e) {
                                return false;
                            }
                        }

                        if (isInScheduleMode()) {
                            AlertsCreator.createScheduleDatePickerDialog(parentActivity, UserObject.isUserSelf(parentFragment.getCurrentUser()), (notify, scheduleDate) -> send(inputContentInfo, notify, scheduleDate));
                        } else {
                            send(inputContentInfo, true, 0);
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
                if (isPopupShowing() && event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (searchingType != 0) {
                        searchingType = 0;
                        emojiView.closeSearch(false);
                    }
                    showPopup(AndroidUtilities.usingHardwareInput ? 0 : 2, 0);
                    openKeyboardInternal();
                }
                try {
                    return super.onTouchEvent(event);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return false;
            }

            @Override
            protected void onSelectionChanged(int selStart, int selEnd) {
                super.onSelectionChanged(selStart, selEnd);
                if (delegate != null) {
                    delegate.onTextSelectionChanged(selStart, selEnd);
                }
            }
        };
        messageEditText.setDelegate(() -> {
            if (delegate != null) {
                delegate.onTextSpansChanged(messageEditText.getText());
            }
        });
        TLRPC.EncryptedChat encryptedChat = parentFragment != null ? parentFragment.getCurrentEncryptedChat() : null;
        messageEditText.setAllowTextEntitiesIntersection(encryptedChat == null || encryptedChat != null && AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 101);
        updateFieldHint();
        int flags = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
        if (encryptedChat != null) {
            flags |= 0x01000000; //EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        messageEditText.setImeOptions(flags);
        messageEditText.setInputType(messageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messageEditText.setSingleLine(false);
        messageEditText.setMaxLines(6);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messageEditText.setGravity(Gravity.BOTTOM);
        messageEditText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        messageEditText.setBackgroundDrawable(null);
        messageEditText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        messageEditText.setHintColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        messageEditText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        messageEditText.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor));
        frameLayout.addView(messageEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 52, 0, isChat ? 50 : 2, 0));
        messageEditText.setOnKeyListener(new OnKeyListener() {

            boolean ctrlPressed = false;

            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (i == KeyEvent.KEYCODE_BACK && !keyboardVisible && isPopupShowing()) {
                    if (keyEvent.getAction() == 1) {
                        if (currentPopupContentType == 1 && botButtonsMessageObject != null) {
                            SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
                            preferences.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
                        }
                        if (searchingType != 0) {
                            searchingType = 0;
                            emojiView.closeSearch(true);
                            messageEditText.requestFocus();
                        } else {
                            showPopup(0, 0);
                        }
                    }
                    return true;
                } else if (i == KeyEvent.KEYCODE_ENTER && (ctrlPressed || sendByEnter) && keyEvent.getAction() == KeyEvent.ACTION_DOWN && editingMessageObject == null) {
                    sendMessage();
                    return true;
                } else if (i == KeyEvent.KEYCODE_CTRL_LEFT || i == KeyEvent.KEYCODE_CTRL_RIGHT) {
                    ctrlPressed = keyEvent.getAction() == KeyEvent.ACTION_DOWN;
                    return true;
                }
                return false;
            }
        });
        messageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            boolean ctrlPressed = false;

            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                } else if (keyEvent != null && i == EditorInfo.IME_NULL) {
                    if ((ctrlPressed || sendByEnter) && keyEvent.getAction() == KeyEvent.ACTION_DOWN && editingMessageObject == null) {
                        sendMessage();
                        return true;
                    } else if (i == KeyEvent.KEYCODE_CTRL_LEFT || i == KeyEvent.KEYCODE_CTRL_RIGHT) {
                        ctrlPressed = keyEvent.getAction() == KeyEvent.ACTION_DOWN;
                        return true;
                    }
                }
                return false;
            }
        });
        messageEditText.addTextChangedListener(new TextWatcher() {
            boolean processChange = false;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (innerTextChange == 1) {
                    return;
                }
                checkSendButton(true);
                CharSequence message = AndroidUtilities.getTrimmedString(charSequence.toString());
                if (delegate != null) {
                    if (!ignoreTextChange) {
                        if (count > 2 || charSequence == null || charSequence.length() == 0) {
                            messageWebPageSearch = true;
                        }
                        delegate.onTextChanged(charSequence, before > count + 1 || (count - before) > 2);
                    }
                }
                if (innerTextChange != 2 && (count - before) > 1) {
                    processChange = true;
                }
                if (editingMessageObject == null && !canWriteToChannel && message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
                    int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                    TLRPC.User currentUser = null;
                    if ((int) dialog_id > 0) {
                        currentUser = accountInstance.getMessagesController().getUser((int) dialog_id);
                    }
                    if (currentUser != null && (currentUser.id == UserConfig.getInstance(currentAccount).getClientUserId() || currentUser.status != null && currentUser.status.expires < currentTime && !accountInstance.getMessagesController().onlinePrivacy.containsKey(currentUser.id))) {
                        return;
                    }
                    lastTypingTimeSend = System.currentTimeMillis();
                    if (delegate != null) {
                        delegate.needSendTyping();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (innerTextChange != 0) {
                    return;
                }
                if (sendByEnter && editable.length() > 0 && editable.charAt(editable.length() - 1) == '\n' && editingMessageObject == null) {
                    sendMessage();
                }
                if (processChange) {
                    ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);
                    for (int i = 0; i < spans.length; i++) {
                        editable.removeSpan(spans[i]);
                    }
                    Emoji.replaceEmoji(editable, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    processChange = false;
                }
            }
        });

        if (isChat) {
            if (parentFragment != null) {
                Drawable drawable1 = context.getResources().getDrawable(R.drawable.input_calendar1).mutate();
                Drawable drawable2 = context.getResources().getDrawable(R.drawable.input_calendar2).mutate();
                drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
                drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_recordedVoiceDot), PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);

                scheduledButton = new ImageView(context);
                scheduledButton.setImageDrawable(combinedDrawable);
                scheduledButton.setVisibility(GONE);
                scheduledButton.setContentDescription(LocaleController.getString("ScheduledMessages", R.string.ScheduledMessages));
                scheduledButton.setScaleType(ImageView.ScaleType.CENTER);
                frameLayout.addView(scheduledButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.RIGHT));
                scheduledButton.setOnClickListener(v -> {
                    if (delegate != null) {
                        delegate.openScheduledMessages();
                    }
                });
            }

            attachLayout = new LinearLayout(context);
            attachLayout.setOrientation(LinearLayout.HORIZONTAL);
            attachLayout.setEnabled(false);
            attachLayout.setPivotX(AndroidUtilities.dp(48));
            frameLayout.addView(attachLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.BOTTOM | Gravity.RIGHT));

            botButton = new ImageView(context);
            botButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            botButton.setImageResource(R.drawable.input_bot2);
            botButton.setScaleType(ImageView.ScaleType.CENTER);
            botButton.setVisibility(GONE);
            attachLayout.addView(botButton, LayoutHelper.createLinear(48, 48));
            botButton.setOnClickListener(v -> {
                if (searchingType != 0) {
                    searchingType = 0;
                    emojiView.closeSearch(false);
                    messageEditText.requestFocus();
                }
                if (botReplyMarkup != null) {
                    if (!isPopupShowing() || currentPopupContentType != 1) {
                        showPopup(1, 1);
                        SharedPreferences preferences1 = MessagesController.getMainSettings(currentAccount);
                        preferences1.edit().remove("hidekeyboard_" + dialog_id).commit();
                    } else {
                        if (currentPopupContentType == 1 && botButtonsMessageObject != null) {
                            SharedPreferences preferences1 = MessagesController.getMainSettings(currentAccount);
                            preferences1.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
                        }
                        openKeyboardInternal();
                    }
                } else if (hasBotCommands) {
                    setFieldText("/");
                    messageEditText.requestFocus();
                    openKeyboard();
                }
                if (stickersExpanded) {
                    setStickersExpanded(false, false, false);
                }
            });

            notifyButton = new ImageView(context);
            notifyButton.setImageResource(silent ? R.drawable.input_notify_off : R.drawable.input_notify_on);
            notifyButton.setContentDescription(silent ? LocaleController.getString("AccDescrChanSilentOn", R.string.AccDescrChanSilentOn) : LocaleController.getString("AccDescrChanSilentOff", R.string.AccDescrChanSilentOff));
            notifyButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            notifyButton.setScaleType(ImageView.ScaleType.CENTER);
            notifyButton.setVisibility(canWriteToChannel && (delegate == null || !delegate.hasScheduledMessages()) ? VISIBLE : GONE);
            attachLayout.addView(notifyButton, LayoutHelper.createLinear(48, 48));
            notifyButton.setOnClickListener(new OnClickListener() {

                private Toast visibleToast;

                @Override
                public void onClick(View v) {
                    silent = !silent;
                    notifyButton.setImageResource(silent ? R.drawable.input_notify_off : R.drawable.input_notify_on);
                    MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("silent_" + dialog_id, silent).commit();
                    NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(dialog_id);
                    try {
                        if (visibleToast != null) {
                            visibleToast.cancel();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (silent) {
                        visibleToast = Toast.makeText(parentActivity, LocaleController.getString("ChannelNotifyMembersInfoOff", R.string.ChannelNotifyMembersInfoOff), Toast.LENGTH_SHORT);
                        visibleToast.show();
                    } else {
                        visibleToast = Toast.makeText(parentActivity, LocaleController.getString("ChannelNotifyMembersInfoOn", R.string.ChannelNotifyMembersInfoOn), Toast.LENGTH_SHORT);
                        visibleToast.show();
                    }
                    notifyButton.setContentDescription(silent ? LocaleController.getString("AccDescrChanSilentOn", R.string.AccDescrChanSilentOn) : LocaleController.getString("AccDescrChanSilentOff", R.string.AccDescrChanSilentOff));
                    updateFieldHint();
                }
            });

            attachButton = new ImageView(context);
            attachButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            attachButton.setImageResource(R.drawable.input_attach);
            attachButton.setScaleType(ImageView.ScaleType.CENTER);
//            if (Build.VERSION.SDK_INT >= 21) {
//                attachButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//            }
            attachLayout.addView(attachButton, LayoutHelper.createLinear(48, 48));
            attachButton.setOnClickListener(v -> delegate.didPressedAttachButton());
            attachButton.setContentDescription(LocaleController.getString("AccDescrAttachButton", R.string.AccDescrAttachButton));
        }

        recordedAudioPanel = new FrameLayout(context);
        recordedAudioPanel.setVisibility(audioToSend == null ? GONE : VISIBLE);
        recordedAudioPanel.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        recordedAudioPanel.setFocusable(true);
        recordedAudioPanel.setFocusableInTouchMode(true);
        recordedAudioPanel.setClickable(true);
        frameLayout.addView(recordedAudioPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));

        recordDeleteImageView = new ImageView(context);
        recordDeleteImageView.setScaleType(ImageView.ScaleType.CENTER);
        recordDeleteImageView.setImageResource(R.drawable.msg_delete);
        recordDeleteImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoiceDelete), PorterDuff.Mode.MULTIPLY));
        recordDeleteImageView.setContentDescription(LocaleController.getString("Delete", R.string.Delete));
        recordedAudioPanel.addView(recordDeleteImageView, LayoutHelper.createFrame(48, 48));
        recordDeleteImageView.setOnClickListener(v -> {
            if (videoToSendMessageObject != null) {
                CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
                delegate.needStartRecordVideo(2, true, 0);
            } else {
                MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                if (playing != null && playing == audioToSendMessageObject) {
                    MediaController.getInstance().cleanupPlayer(true, true);
                }
            }
            if (audioToSendPath != null) {
                new File(audioToSendPath).delete();
            }
            hideRecordedAudioPanel();
            checkSendButton(true);
        });

        videoTimelineView = new VideoTimelineView(context);
        videoTimelineView.setColor(Theme.getColor(Theme.key_chat_messagePanelVideoFrame));
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
        recordedAudioPanel.addView(videoTimelineView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 40, 0, 0, 0));

        recordedAudioBackground = new View(context);
        recordedAudioBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_chat_recordedVoiceBackground)));
        recordedAudioPanel.addView(recordedAudioBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL | Gravity.LEFT, 48, 0, 0, 0));

        recordedAudioSeekBar = new SeekBarWaveformView(context);
        recordedAudioPanel.addView(recordedAudioSeekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 48 + 44, 0, 52, 0));

        playDrawable = Theme.createSimpleSelectorDrawable(context, R.drawable.s_play, Theme.getColor(Theme.key_chat_recordedVoicePlayPause), Theme.getColor(Theme.key_chat_recordedVoicePlayPausePressed));
        pauseDrawable = Theme.createSimpleSelectorDrawable(context, R.drawable.s_pause, Theme.getColor(Theme.key_chat_recordedVoicePlayPause), Theme.getColor(Theme.key_chat_recordedVoicePlayPausePressed));

        recordedAudioPlayButton = new ImageView(context);
        recordedAudioPlayButton.setImageDrawable(playDrawable);
        recordedAudioPlayButton.setScaleType(ImageView.ScaleType.CENTER);
        recordedAudioPlayButton.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
        recordedAudioPanel.addView(recordedAudioPlayButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.BOTTOM, 48, 0, 0, 0));
        recordedAudioPlayButton.setOnClickListener(v -> {
            if (audioToSend == null) {
                return;
            }
            if (MediaController.getInstance().isPlayingMessage(audioToSendMessageObject) && !MediaController.getInstance().isMessagePaused()) {
                MediaController.getInstance().pauseMessage(audioToSendMessageObject);
                recordedAudioPlayButton.setImageDrawable(playDrawable);
                recordedAudioPlayButton.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
            } else {
                recordedAudioPlayButton.setImageDrawable(pauseDrawable);
                MediaController.getInstance().playMessage(audioToSendMessageObject);
                recordedAudioPlayButton.setContentDescription(LocaleController.getString("AccActionPause", R.string.AccActionPause));
            }
        });

        recordedAudioTimeTextView = new TextView(context);
        recordedAudioTimeTextView.setTextColor(Theme.getColor(Theme.key_chat_messagePanelVoiceDuration));
        recordedAudioTimeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        recordedAudioPanel.addView(recordedAudioTimeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 13, 0));

        recordPanel = new FrameLayout(context);
        recordPanel.setVisibility(GONE);
        recordPanel.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        frameLayout.addView(recordPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));
        recordPanel.setOnTouchListener((v, event) -> true);

        slideText = new LinearLayout(context);
        slideText.setOrientation(LinearLayout.HORIZONTAL);
        recordPanel.addView(slideText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 30, 0, 0, 0));

        recordCancelImage = new ImageView(context);
        recordCancelImage.setImageResource(R.drawable.slidearrow);
        recordCancelImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_recordVoiceCancel), PorterDuff.Mode.MULTIPLY));
        slideText.addView(recordCancelImage, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 1, 0, 0));

        recordCancelText = new TextView(context);
        recordCancelText.setText(LocaleController.getString("SlideToCancel", R.string.SlideToCancel));
        recordCancelText.setTextColor(Theme.getColor(Theme.key_chat_recordVoiceCancel));
        recordCancelText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        slideText.addView(recordCancelText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

        recordSendText = new TextView(context);
        recordSendText.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        recordSendText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        recordSendText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        recordSendText.setGravity(Gravity.CENTER);
        recordSendText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        recordSendText.setAlpha(0.0f);
        recordSendText.setPadding(AndroidUtilities.dp(36), 0, 0, 0);
        recordSendText.setOnClickListener(v -> {
            if (hasRecordVideo && videoSendButton.getTag() != null) {
                CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
                delegate.needStartRecordVideo(2, true, 0);
            } else {
                delegate.needStartRecordAudio(0);
                MediaController.getInstance().stopRecording(0, false, 0);
            }
            recordingAudioVideo = false;
            updateRecordIntefrace();
        });
        recordPanel.addView(recordSendText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        recordTimeContainer = new LinearLayout(context);
        recordTimeContainer.setOrientation(LinearLayout.HORIZONTAL);
        recordTimeContainer.setPadding(AndroidUtilities.dp(13), 0, 0, 0);
        recordTimeContainer.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        recordPanel.addView(recordTimeContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        recordDot = new RecordDot(context);
        recordTimeContainer.addView(recordDot, LayoutHelper.createLinear(11, 11, Gravity.CENTER_VERTICAL, 0, 1, 0, 0));

        recordTimeText = new TextView(context);
        recordTimeText.setTextColor(Theme.getColor(Theme.key_chat_recordTime));
        recordTimeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        recordTimeContainer.addView(recordTimeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

        sendButtonContainer = new FrameLayout(context);
        sendButtonContainer.setClipChildren(false);
        sendButtonContainer.setClipToPadding(false);
        textFieldContainer.addView(sendButtonContainer, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));

        audioVideoButtonContainer = new FrameLayout(context);
        audioVideoButtonContainer.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        audioVideoButtonContainer.setSoundEffectsEnabled(false);
        sendButtonContainer.addView(audioVideoButtonContainer, LayoutHelper.createFrame(48, 48));
        audioVideoButtonContainer.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                if (recordCircle.isSendButtonVisible()) {
                    if (!hasRecordVideo || calledRecordRunnable) {
                        startedDraggingX = -1;
                        if (hasRecordVideo && videoSendButton.getTag() != null) {
                            delegate.needStartRecordVideo(1, true, 0);
                        } else {
                            if (recordingAudioVideo && isInScheduleMode()) {
                                AlertsCreator.createScheduleDatePickerDialog(parentActivity, UserObject.isUserSelf(parentFragment.getCurrentUser()), (notify, scheduleDate) -> MediaController.getInstance().stopRecording(1, notify, scheduleDate), () -> MediaController.getInstance().stopRecording(0, false, 0));
                            }
                            delegate.needStartRecordAudio(0);
                            MediaController.getInstance().stopRecording(isInScheduleMode() ? 3 : 1, true, 0);
                        }
                        recordingAudioVideo = false;
                        updateRecordIntefrace();
                    }
                    return false;
                }
                if (parentFragment != null) {
                    TLRPC.Chat chat = parentFragment.getCurrentChat();
                    if (chat != null && !ChatObject.canSendMedia(chat)) {
                        delegate.needShowMediaBanHint();
                        return false;
                    }
                }
                if (hasRecordVideo) {
                    calledRecordRunnable = false;
                    recordAudioVideoRunnableStarted = true;
                    AndroidUtilities.runOnUIThread(recordAudioVideoRunnable, 150);
                } else {
                    recordAudioVideoRunnable.run();
                }
            } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                if (recordCircle.isSendButtonVisible() || recordedAudioPanel.getVisibility() == VISIBLE) {
                    return false;
                }
                if (recordAudioVideoRunnableStarted) {
                    AndroidUtilities.cancelRunOnUIThread(recordAudioVideoRunnable);
                    delegate.onSwitchRecordMode(videoSendButton.getTag() == null);
                    setRecordVideoButtonVisible(videoSendButton.getTag() == null, true);
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                } else if (!hasRecordVideo || calledRecordRunnable) {
                    startedDraggingX = -1;
                    if (hasRecordVideo && videoSendButton.getTag() != null) {
                        CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
                        delegate.needStartRecordVideo(1, true, 0);
                    } else {
                        if (recordingAudioVideo && isInScheduleMode()) {
                            AlertsCreator.createScheduleDatePickerDialog(parentActivity, UserObject.isUserSelf(parentFragment.getCurrentUser()), (notify, scheduleDate) -> MediaController.getInstance().stopRecording(1, notify, scheduleDate), () -> MediaController.getInstance().stopRecording(0, false, 0));
                        }
                        delegate.needStartRecordAudio(0);
                        MediaController.getInstance().stopRecording(isInScheduleMode() ? 3 : 1, true, 0);
                    }
                    recordingAudioVideo = false;
                    updateRecordIntefrace();
                }
            } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && recordingAudioVideo) {
                float x = motionEvent.getX();
                float y = motionEvent.getY();
                if (recordCircle.isSendButtonVisible()) {
                    return false;
                }
                if (recordCircle.setLockTranslation(y) == 2) {
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(recordCircle, "lockAnimatedTranslation", recordCircle.startTranslation),
                            ObjectAnimator.ofFloat(slideText, View.ALPHA, 0.0f),
                            ObjectAnimator.ofFloat(slideText, View.TRANSLATION_Y, AndroidUtilities.dp(20)),
                            ObjectAnimator.ofFloat(recordSendText, View.ALPHA, 1.0f),
                            ObjectAnimator.ofFloat(recordSendText, View.TRANSLATION_Y, -AndroidUtilities.dp(20), 0));
                    animatorSet.setInterpolator(new DecelerateInterpolator());
                    animatorSet.setDuration(150);
                    animatorSet.start();
                    return false;
                }
                if (x < -distCanMove) {
                    if (hasRecordVideo && videoSendButton.getTag() != null) {
                        CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
                        delegate.needStartRecordVideo(2, true, 0);
                    } else {
                        delegate.needStartRecordAudio(0);
                        MediaController.getInstance().stopRecording(0, false, 0);
                    }
                    recordingAudioVideo = false;
                    updateRecordIntefrace();
                }

                x = x + audioVideoButtonContainer.getX();
                LayoutParams params = (LayoutParams) slideText.getLayoutParams();
                if (startedDraggingX != -1) {
                    float dist = (x - startedDraggingX);
                    params.leftMargin = AndroidUtilities.dp(30) + (int) dist;
                    slideText.setLayoutParams(params);
                    float alpha = 1.0f + dist / distCanMove;
                    if (alpha > 1) {
                        alpha = 1;
                    } else if (alpha < 0) {
                        alpha = 0;
                    }
                    slideText.setAlpha(alpha);
                }
                if (x <= slideText.getX() + slideText.getWidth() + AndroidUtilities.dp(30)) {
                    if (startedDraggingX == -1) {
                        startedDraggingX = x;
                        distCanMove = (recordPanel.getMeasuredWidth() - slideText.getMeasuredWidth() - AndroidUtilities.dp(48)) / 2.0f;
                        if (distCanMove <= 0) {
                            distCanMove = AndroidUtilities.dp(80);
                        } else if (distCanMove > AndroidUtilities.dp(80)) {
                            distCanMove = AndroidUtilities.dp(80);
                        }
                    }
                }
                if (params.leftMargin > AndroidUtilities.dp(30)) {
                    params.leftMargin = AndroidUtilities.dp(30);
                    slideText.setLayoutParams(params);
                    slideText.setAlpha(1);
                    startedDraggingX = -1;
                }
            }
            view.onTouchEvent(motionEvent);
            return true;
        });

        audioSendButton = new ImageView(context);
        audioSendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        audioSendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        audioSendButton.setImageResource(R.drawable.input_mic);
        audioSendButton.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        audioSendButton.setContentDescription(LocaleController.getString("AccDescrVoiceMessage", R.string.AccDescrVoiceMessage));
        audioSendButton.setFocusable(true);
        audioSendButton.setAccessibilityDelegate(mediaMessageButtonsDelegate);
        audioVideoButtonContainer.addView(audioSendButton, LayoutHelper.createFrame(48, 48));

        if (isChat) {
            videoSendButton = new ImageView(context);
            videoSendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            videoSendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            videoSendButton.setImageResource(R.drawable.input_video);
            videoSendButton.setPadding(0, 0, AndroidUtilities.dp(4), 0);
            videoSendButton.setContentDescription(LocaleController.getString("AccDescrVideoMessage", R.string.AccDescrVideoMessage));
            videoSendButton.setFocusable(true);
            videoSendButton.setAccessibilityDelegate(mediaMessageButtonsDelegate);
            audioVideoButtonContainer.addView(videoSendButton, LayoutHelper.createFrame(48, 48));
        }

        recordCircle = new RecordCircle(context);
        recordCircle.setVisibility(GONE);
        sizeNotifierLayout.addView(recordCircle, LayoutHelper.createFrame(124, 194, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, -36, 0));

        cancelBotButton = new ImageView(context);
        cancelBotButton.setVisibility(INVISIBLE);
        cancelBotButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cancelBotButton.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
        cancelBotButton.setContentDescription(LocaleController.getString("Cancel", R.string.Cancel));
        progressDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelCancelInlineBot), PorterDuff.Mode.MULTIPLY));
        cancelBotButton.setSoundEffectsEnabled(false);
        cancelBotButton.setScaleX(0.1f);
        cancelBotButton.setScaleY(0.1f);
        cancelBotButton.setAlpha(0.0f);
        sendButtonContainer.addView(cancelBotButton, LayoutHelper.createFrame(48, 48));
        cancelBotButton.setOnClickListener(view -> {
            String text = messageEditText.getText().toString();
            int idx = text.indexOf(' ');
            if (idx == -1 || idx == text.length() - 1) {
                setFieldText("");
            } else {
                setFieldText(text.substring(0, idx + 1));
            }
        });

        if (isInScheduleMode()) {
            sendButtonDrawable = context.getResources().getDrawable(R.drawable.input_schedule).mutate();
            sendButtonInverseDrawable = context.getResources().getDrawable(R.drawable.input_schedule).mutate();
            inactinveSendButtonDrawable = context.getResources().getDrawable(R.drawable.input_schedule).mutate();
        } else {
            sendButtonDrawable = context.getResources().getDrawable(R.drawable.ic_send).mutate();
            sendButtonInverseDrawable = context.getResources().getDrawable(R.drawable.ic_send).mutate();
            inactinveSendButtonDrawable = context.getResources().getDrawable(R.drawable.ic_send).mutate();
        }
        sendButton = new View(context) {

            private int drawableColor;
            private float animationProgress;
            private float animateBounce;
            private long lastAnimationTime;
            private float animationDuration;

            @Override
            protected void onDraw(Canvas canvas) {
                int x = (getMeasuredWidth() - sendButtonDrawable.getIntrinsicWidth()) / 2;
                int y = (getMeasuredHeight() - sendButtonDrawable.getIntrinsicHeight()) / 2;
                if (isInScheduleMode()) {
                    y -= AndroidUtilities.dp(1);
                } else {
                    x += AndroidUtilities.dp(2);
                }

                int color;
                boolean showingPopup;
                if (showingPopup = (sendPopupWindow != null && sendPopupWindow.isShowing())) {
                    color = Theme.getColor(Theme.key_chat_messagePanelVoicePressed);
                } else {
                    color = Theme.getColor(Theme.key_chat_messagePanelSend);
                }
                if (color != drawableColor) {
                    lastAnimationTime = SystemClock.uptimeMillis();
                    if (showingPopup) {
                        animationProgress = 0.0f;
                        animationDuration = 200.0f;
                    } else if (drawableColor != 0) {
                        animationProgress = 0.0f;
                        animationDuration = 120.0f;
                    } else {
                        animationProgress = 1.0f;
                    }
                    drawableColor = color;
                    sendButtonDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend), PorterDuff.Mode.MULTIPLY));
                    int c = Theme.getColor(Theme.key_chat_messagePanelIcons);
                    inactinveSendButtonDrawable.setColorFilter(new PorterDuffColorFilter(Color.argb(0xb4, Color.red(c), Color.green(c), Color.blue(c)), PorterDuff.Mode.MULTIPLY));
                    sendButtonInverseDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));
                }
                if (animationProgress < 1.0f) {
                    long newTime = SystemClock.uptimeMillis();
                    long dt = newTime - lastAnimationTime;
                    animationProgress += dt / animationDuration;
                    if (animationProgress > 1.0f) {
                        animationProgress = 1.0f;
                    }
                    lastAnimationTime = newTime;
                    invalidate();
                }
                if (!showingPopup) {
                    if (slowModeTimer == Integer.MAX_VALUE && !isInScheduleMode()) {
                        inactinveSendButtonDrawable.setBounds(x, y, x + sendButtonDrawable.getIntrinsicWidth(), y + sendButtonDrawable.getIntrinsicHeight());
                        inactinveSendButtonDrawable.draw(canvas);
                    } else {
                        sendButtonDrawable.setBounds(x, y, x + sendButtonDrawable.getIntrinsicWidth(), y + sendButtonDrawable.getIntrinsicHeight());
                        sendButtonDrawable.draw(canvas);
                    }
                }
                if (showingPopup || animationProgress != 1.0f) {
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chat_messagePanelSend));
                    int rad = AndroidUtilities.dp(20);
                    if (showingPopup) {
                        sendButtonInverseDrawable.setAlpha(255);
                        float p = animationProgress;
                        if (p <= 0.25f) {
                            float progress = p / 0.25f;
                            rad += AndroidUtilities.dp(2) * CubicBezierInterpolator.EASE_IN.getInterpolation(progress);
                        } else {
                            p -= 0.25f;
                            if (p <= 0.5f) {
                                float progress = p / 0.5f;
                                rad += AndroidUtilities.dp(2) - AndroidUtilities.dp(3) * CubicBezierInterpolator.EASE_IN.getInterpolation(progress);
                            } else {
                                p -= 0.5f;
                                float progress = p / 0.25f;
                                rad += -AndroidUtilities.dp(1) + AndroidUtilities.dp(1) * CubicBezierInterpolator.EASE_IN.getInterpolation(progress);
                            }
                        }
                    } else {
                        int alpha = (int) (255 * (1.0f - animationProgress));
                        Theme.dialogs_onlineCirclePaint.setAlpha(alpha);
                        sendButtonInverseDrawable.setAlpha(alpha);
                    }
                    canvas.drawCircle(getMeasuredWidth() / 2, getMeasuredHeight() / 2, rad, Theme.dialogs_onlineCirclePaint);
                    sendButtonInverseDrawable.setBounds(x, y, x + sendButtonDrawable.getIntrinsicWidth(), y + sendButtonDrawable.getIntrinsicHeight());
                    sendButtonInverseDrawable.draw(canvas);
                }
            }
        };
        sendButton.setVisibility(INVISIBLE);
        int color = Theme.getColor(Theme.key_chat_messagePanelSend);
        sendButton.setContentDescription(LocaleController.getString("Send", R.string.Send));
        sendButton.setSoundEffectsEnabled(false);
        sendButton.setScaleX(0.1f);
        sendButton.setScaleY(0.1f);
        sendButton.setAlpha(0.0f);
        if (Build.VERSION.SDK_INT >= 21) {
            sendButton.setBackgroundDrawable(Theme.createSelectorDrawable(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)), 1));
        }
        sendButtonContainer.addView(sendButton, LayoutHelper.createFrame(48, 48));
        sendButton.setOnClickListener(view -> sendMessage());
        sendButton.setOnLongClickListener(this::onSendLongClick);

        slowModeButton = new SimpleTextView(context);
        slowModeButton.setTextSize(18);
        slowModeButton.setVisibility(INVISIBLE);
        slowModeButton.setSoundEffectsEnabled(false);
        slowModeButton.setScaleX(0.1f);
        slowModeButton.setScaleY(0.1f);
        slowModeButton.setAlpha(0.0f);
        slowModeButton.setPadding(0, 0, AndroidUtilities.dp(13), 0);
        slowModeButton.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        slowModeButton.setTextColor(Theme.getColor(Theme.key_chat_messagePanelIcons));
        sendButtonContainer.addView(slowModeButton, LayoutHelper.createFrame(64, 48, Gravity.RIGHT | Gravity.TOP));
        slowModeButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onUpdateSlowModeButton(slowModeButton, true, slowModeButton.getText());
            }
        });
        slowModeButton.setOnLongClickListener(v -> {
            if (messageEditText.length() == 0) {
                return false;
            }
            return onSendLongClick(v);
        });

        expandStickersButton = new ImageView(context);
        expandStickersButton.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        expandStickersButton.setScaleType(ImageView.ScaleType.CENTER);
        expandStickersButton.setImageDrawable(stickersArrow = new AnimatedArrowDrawable(Theme.getColor(Theme.key_chat_messagePanelIcons), false));
        expandStickersButton.setVisibility(GONE);
        expandStickersButton.setScaleX(0.1f);
        expandStickersButton.setScaleY(0.1f);
        expandStickersButton.setAlpha(0.0f);
        sendButtonContainer.addView(expandStickersButton, LayoutHelper.createFrame(48, 48));
        expandStickersButton.setOnClickListener(v -> {
            if (expandStickersButton.getVisibility() != VISIBLE || expandStickersButton.getAlpha() != 1.0f) {
                return;
            }
            if (stickersExpanded) {
                if (searchingType != 0) {
                    searchingType = 0;
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
        expandStickersButton.setContentDescription(LocaleController.getString("AccDescrExpandPanel", R.string.AccDescrExpandPanel));

        doneButtonContainer = new FrameLayout(context);
        doneButtonContainer.setVisibility(GONE);
        textFieldContainer.addView(doneButtonContainer, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));
        doneButtonContainer.setOnClickListener(view -> doneEditingMessage());

        Drawable drawable = Theme.createCircleDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_chat_messagePanelSend));
        Drawable checkDrawable = context.getResources().getDrawable(R.drawable.input_done).mutate();
        checkDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));
        CombinedDrawable combinedDrawable = new CombinedDrawable(drawable, checkDrawable, 0, AndroidUtilities.dp(1));
        combinedDrawable.setCustomSize(AndroidUtilities.dp(32), AndroidUtilities.dp(32));

        doneButtonImage = new ImageView(context);
        doneButtonImage.setScaleType(ImageView.ScaleType.CENTER);
        doneButtonImage.setImageDrawable(combinedDrawable);
        doneButtonImage.setContentDescription(LocaleController.getString("Done", R.string.Done));
        doneButtonContainer.addView(doneButtonImage, LayoutHelper.createFrame(48, 48));

        doneButtonProgress = new ContextProgressView(context, 0);
        doneButtonProgress.setVisibility(View.INVISIBLE);
        doneButtonContainer.addView(doneButtonProgress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        SharedPreferences sharedPreferences = MessagesController.getGlobalEmojiSettings();
        keyboardHeight = sharedPreferences.getInt("kbd_height", AndroidUtilities.dp(200));
        keyboardHeightLand = sharedPreferences.getInt("kbd_height_land3", AndroidUtilities.dp(200));

        setRecordVideoButtonVisible(false, false);
        checkSendButton(false);
        checkChannelRights();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == topView) {
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), child.getLayoutParams().height + AndroidUtilities.dp(2));
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == topView) {
            canvas.restore();
        }
        return result;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int top = topView != null && topView.getVisibility() == VISIBLE ? (int) topView.getTranslationY() : 0;
        int bottom = top + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
        Theme.chat_composeShadowDrawable.setBounds(0, top, getMeasuredWidth(), bottom);
        Theme.chat_composeShadowDrawable.draw(canvas);
        canvas.drawRect(0, bottom, getWidth(), getHeight(), Theme.chat_composeBackgroundPaint);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private boolean onSendLongClick(View view) {
        if (parentFragment == null || isInScheduleMode() || parentFragment.getCurrentEncryptedChat() != null) {
            return false;
        }
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        TLRPC.User user = parentFragment.getCurrentUser();

        if (sendPopupLayout == null) {
            sendPopupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(parentActivity);
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
            sendPopupLayout.setShowedFromBotton(false);

            for (int a = 0; a < 2; a++) {
                if (a == 1 && (UserObject.isUserSelf(user) || slowModeTimer > 0 && !isInScheduleMode())) {
                    continue;
                }
                int num = a;
                ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getContext());
                if (num == 0) {
                    if (UserObject.isUserSelf(user)) {
                        cell.setTextAndIcon(LocaleController.getString("SetReminder", R.string.SetReminder), R.drawable.msg_schedule);
                    } else {
                        cell.setTextAndIcon(LocaleController.getString("ScheduleMessage", R.string.ScheduleMessage), R.drawable.msg_schedule);
                    }
                } else if (num == 1) {
                    cell.setTextAndIcon(LocaleController.getString("SendWithoutSound", R.string.SendWithoutSound), R.drawable.input_notify_off);
                }
                cell.setMinimumWidth(AndroidUtilities.dp(196));
                sendPopupLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 48 * a, 0, 0));
                cell.setOnClickListener(v -> {
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        sendPopupWindow.dismiss();
                    }
                    if (num == 0) {
                        AlertsCreator.createScheduleDatePickerDialog(parentActivity, UserObject.isUserSelf(user), this::sendMessageInternal);
                    } else if (num == 1) {
                        sendMessageInternal(false, 0);
                    }
                });
            }

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
        }

        sendPopupLayout.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), MeasureSpec.AT_MOST));
        sendPopupWindow.setFocusable(true);
        int[] location = new int[2];
        view.getLocationInWindow(location);
        int y;
        if (keyboardVisible && ChatActivityEnterView.this.getMeasuredHeight() > AndroidUtilities.dp(topView != null && topView.getVisibility() == VISIBLE ? 48 + 58 : 58)) {
            y = location[1] + view.getMeasuredHeight();
        } else {
            y = location[1] - sendPopupLayout.getMeasuredHeight() - AndroidUtilities.dp(2);
        }
        sendPopupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, location[0] + view.getMeasuredWidth() - sendPopupLayout.getMeasuredWidth() + AndroidUtilities.dp(8), y);
        sendPopupWindow.dimBehind();
        sendButton.invalidate();
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);

        return false;
    }

    public boolean isSendButtonVisible() {
        return sendButton.getVisibility() == VISIBLE;
    }

    private void setRecordVideoButtonVisible(boolean visible, boolean animated) {
        if (videoSendButton == null) {
            return;
        }
        videoSendButton.setTag(visible ? 1 : null);
        if (audioVideoButtonAnimation != null) {
            audioVideoButtonAnimation.cancel();
            audioVideoButtonAnimation = null;
        }
        if (animated) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean isChannel = false;
            if ((int) dialog_id < 0) {
                TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-(int) dialog_id);
                isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            }
            preferences.edit().putBoolean(isChannel ? "currentModeVideoChannel" : "currentModeVideo", visible).commit();
            audioVideoButtonAnimation = new AnimatorSet();
            audioVideoButtonAnimation.playTogether(
                    ObjectAnimator.ofFloat(videoSendButton, View.SCALE_X, visible ? 1.0f : 0.1f),
                    ObjectAnimator.ofFloat(videoSendButton, View.SCALE_Y, visible ? 1.0f : 0.1f),
                    ObjectAnimator.ofFloat(videoSendButton, View.ALPHA, visible ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(audioSendButton, View.SCALE_X, visible ? 0.1f : 1.0f),
                    ObjectAnimator.ofFloat(audioSendButton, View.SCALE_Y, visible ? 0.1f : 1.0f),
                    ObjectAnimator.ofFloat(audioSendButton, View.ALPHA, visible ? 0.0f : 1.0f));
            audioVideoButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(audioVideoButtonAnimation)) {
                        audioVideoButtonAnimation = null;
                    }
                    (videoSendButton.getTag()==null ? audioSendButton : videoSendButton).sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                }
            });
            audioVideoButtonAnimation.setInterpolator(new DecelerateInterpolator());
            audioVideoButtonAnimation.setDuration(150);
            audioVideoButtonAnimation.start();
        } else {
            videoSendButton.setScaleX(visible ? 1.0f : 0.1f);
            videoSendButton.setScaleY(visible ? 1.0f : 0.1f);
            videoSendButton.setAlpha(visible ? 1.0f : 0.0f);
            audioSendButton.setScaleX(visible ? 0.1f : 1.0f);
            audioSendButton.setScaleY(visible ? 0.1f : 1.0f);
            audioSendButton.setAlpha(visible ? 0.0f : 1.0f);
        }
    }

    public boolean isRecordingAudioVideo() {
        return recordingAudioVideo;
    }

    public boolean isRecordLocked() {
        return recordingAudioVideo && recordCircle.isSendButtonVisible();
    }

    public void cancelRecordingAudioVideo() {
        if (hasRecordVideo && videoSendButton.getTag() != null) {
            CameraController.getInstance().cancelOnInitRunnable(onFinishInitCameraRunnable);
            delegate.needStartRecordVideo(2, true, 0);
        } else {
            delegate.needStartRecordAudio(0);
            MediaController.getInstance().stopRecording(0, false, 0);
        }
        recordingAudioVideo = false;
        updateRecordIntefrace();
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
                        SendMessagesHelper.getInstance(currentAccount).isSendingMessageIdDialog(dialog_id))) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(info.id);
            if (!ChatObject.hasAdminRights(chat)) {
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
            int minutes = currentTime / 60;
            int seconds = currentTime - minutes * 60;
            if (minutes == 0 && seconds == 0) {
                seconds = 1;
            }
            slowModeButton.setText(String.format("%d:%02d", minutes, seconds));
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
        topView.setTranslationY(height);
        addView(topView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, height, Gravity.TOP | Gravity.LEFT, 0, 2, 0, 0));
        needShowTopView = false;
    }

    public void setForceShowSendButton(boolean value, boolean animated) {
        forceShowSendButton = value;
        checkSendButton(animated);
    }

    public void setAllowStickersAndGifs(boolean value, boolean value2) {
        if ((allowStickers != value || allowGifs != value2) && emojiView != null) {
            if (emojiViewVisible) {
                hidePopup(false);
            }
            sizeNotifierLayout.removeView(emojiView);
            emojiView = null;
        }
        allowStickers = value;
        allowGifs = value2;
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

    public void showTopView(boolean animated, final boolean openKeyboard) {
        if (topView == null || topViewShowed || getVisibility() != VISIBLE) {
            if (recordedAudioPanel.getVisibility() != VISIBLE && (!forceShowSendButton || openKeyboard)) {
                openKeyboard();
            }
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
                currentTopViewAnimation = new AnimatorSet();
                currentTopViewAnimation.playTogether(
                        ObjectAnimator.ofFloat(topView, View.TRANSLATION_Y, 0),
                        ObjectAnimator.ofFloat(topLineView, View.ALPHA, 1.0f));
                currentTopViewAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            currentTopViewAnimation = null;
                        }
                    }
                });
                currentTopViewAnimation.setDuration(250);
                currentTopViewAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
                currentTopViewAnimation.start();
            } else {
                topView.setTranslationY(0);
                topLineView.setAlpha(1.0f);
            }
            if (recordedAudioPanel.getVisibility() != VISIBLE && (!forceShowSendButton || openKeyboard)) {
                messageEditText.requestFocus();
                openKeyboard();
            }
        }
    }

    public void onEditTimeExpired() {
        doneButtonContainer.setVisibility(View.GONE);
    }

    public void showEditDoneProgress(final boolean show, boolean animated) {
        if (doneButtonAnimation != null) {
            doneButtonAnimation.cancel();
        }
        if (!animated) {
            if (show) {
                doneButtonImage.setScaleX(0.1f);
                doneButtonImage.setScaleY(0.1f);
                doneButtonImage.setAlpha(0.0f);
                doneButtonProgress.setScaleX(1.0f);
                doneButtonProgress.setScaleY(1.0f);
                doneButtonProgress.setAlpha(1.0f);
                doneButtonImage.setVisibility(View.INVISIBLE);
                doneButtonProgress.setVisibility(View.VISIBLE);
                doneButtonContainer.setEnabled(false);
            } else {
                doneButtonProgress.setScaleX(0.1f);
                doneButtonProgress.setScaleY(0.1f);
                doneButtonProgress.setAlpha(0.0f);
                doneButtonImage.setScaleX(1.0f);
                doneButtonImage.setScaleY(1.0f);
                doneButtonImage.setAlpha(1.0f);
                doneButtonImage.setVisibility(View.VISIBLE);
                doneButtonProgress.setVisibility(View.INVISIBLE);
                doneButtonContainer.setEnabled(true);
            }
        } else {
            doneButtonAnimation = new AnimatorSet();
            if (show) {
                doneButtonProgress.setVisibility(View.VISIBLE);
                doneButtonContainer.setEnabled(false);
                doneButtonAnimation.playTogether(
                        ObjectAnimator.ofFloat(doneButtonImage, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(doneButtonImage, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(doneButtonImage, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(doneButtonProgress, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(doneButtonProgress, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(doneButtonProgress, View.ALPHA, 1.0f));
            } else {
                doneButtonImage.setVisibility(View.VISIBLE);
                doneButtonContainer.setEnabled(true);
                doneButtonAnimation.playTogether(
                        ObjectAnimator.ofFloat(doneButtonProgress, View.SCALE_X, 0.1f),
                        ObjectAnimator.ofFloat(doneButtonProgress, View.SCALE_Y, 0.1f),
                        ObjectAnimator.ofFloat(doneButtonProgress, View.ALPHA, 0.0f),
                        ObjectAnimator.ofFloat(doneButtonImage, View.SCALE_X, 1.0f),
                        ObjectAnimator.ofFloat(doneButtonImage, View.SCALE_Y, 1.0f),
                        ObjectAnimator.ofFloat(doneButtonImage, View.ALPHA, 1.0f));

            }
            doneButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (doneButtonAnimation != null && doneButtonAnimation.equals(animation)) {
                        if (!show) {
                            doneButtonProgress.setVisibility(View.INVISIBLE);
                        } else {
                            doneButtonImage.setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (doneButtonAnimation != null && doneButtonAnimation.equals(animation)) {
                        doneButtonAnimation = null;
                    }
                }
            });
            doneButtonAnimation.setDuration(150);
            doneButtonAnimation.start();
        }
    }

    public void hideTopView(final boolean animated) {
        if (topView == null || !topViewShowed) {
            return;
        }

        topViewShowed = false;
        needShowTopView = false;
        if (allowShowTopView) {
            if (currentTopViewAnimation != null) {
                currentTopViewAnimation.cancel();
                currentTopViewAnimation = null;
            }
            if (animated) {
                currentTopViewAnimation = new AnimatorSet();
                currentTopViewAnimation.playTogether(
                        ObjectAnimator.ofFloat(topView, View.TRANSLATION_Y, topView.getLayoutParams().height),
                        ObjectAnimator.ofFloat(topLineView, View.ALPHA, 0.0f));
                currentTopViewAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            topView.setVisibility(GONE);
                            topLineView.setVisibility(GONE);
                            resizeForTopView(false);
                            currentTopViewAnimation = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            currentTopViewAnimation = null;
                        }
                    }
                });
                currentTopViewAnimation.setDuration(200);
                currentTopViewAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
                currentTopViewAnimation.start();
            } else {
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

    private void onWindowSizeChanged() {
        int size = sizeNotifierLayout.getHeight();
        if (!keyboardVisible) {
            size -= emojiPadding;
        }
        if (delegate != null) {
            delegate.onWindowSizeChanged(size);
        }
        if (topView != null) {
            if (size < AndroidUtilities.dp(72) + ActionBar.getCurrentActionBarHeight()) {
                if (allowShowTopView) {
                    allowShowTopView = false;
                    if (needShowTopView) {
                        topView.setVisibility(GONE);
                        topLineView.setVisibility(GONE);
                        topLineView.setAlpha(0.0f);
                        resizeForTopView(false);
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
                        topView.setTranslationY(0);
                    }
                }
            }
        }
    }

    private void resizeForTopView(boolean show) {
        LayoutParams layoutParams = (LayoutParams) textFieldContainer.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(2) + (show ? topView.getLayoutParams().height : 0);
        textFieldContainer.setLayoutParams(layoutParams);
        setMinimumHeight(AndroidUtilities.dp(51) + (show ? topView.getLayoutParams().height : 0));
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
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.audioRouteChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messageReceivedByServer);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.sendingMessagesChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoad);
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
    }

    public void checkChannelRights() {
        if (parentFragment == null) {
            return;
        }
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        if (chat != null) {
            audioVideoButtonContainer.setAlpha(ChatObject.canSendMedia(chat) ? 1.0f : 0.5f);
            if (emojiView != null) {
                emojiView.setStickersBanned(!ChatObject.canSendStickers(chat), chat.id);
            }
        }
    }

    public void onBeginHide() {
        if (focusRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(focusRunnable);
            focusRunnable = null;
        }
    }

    public void onPause() {
        isPaused = true;
        closeKeyboard();
    }

    public void onResume() {
        isPaused = false;
        int visibility = getVisibility();
        if (showKeyboardOnResume) {
            showKeyboardOnResume = false;
            if (searchingType == 0) {
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
        messageEditText.setEnabled(visibility == VISIBLE);
    }

    public void setDialogId(long id, int account) {
        dialog_id = id;
        if (currentAccount != account) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStarted);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStartError);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordStopped);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recordProgressChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.audioDidSent);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.audioRouteChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messageReceivedByServer);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.sendingMessagesChanged);
            currentAccount = account;
            accountInstance = AccountInstance.getInstance(currentAccount);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStarted);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStartError);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordStopped);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recordProgressChanged);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.audioDidSent);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.audioRouteChanged);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingDidReset);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredStickersDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messageReceivedByServer);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.sendingMessagesChanged);
        }

        updateScheduleButton(false);
        checkRoundVideo();
        updateFieldHint();
    }

    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        info = chatInfo;
        if (emojiView != null) {
            emojiView.setChatInfo(info);
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
        int lower_id = (int) dialog_id;
        int high_id = (int) (dialog_id >> 32);
        if (lower_id == 0 && high_id != 0) {
            TLRPC.EncryptedChat encryptedChat = accountInstance.getMessagesController().getEncryptedChat(high_id);
            if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 66) {
                hasRecordVideo = true;
            }
        } else {
            hasRecordVideo = true;
        }
        boolean isChannel = false;
        if ((int) dialog_id < 0) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-(int) dialog_id);
            isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            if (isChannel && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages)) {
                hasRecordVideo = false;
            }
        }
        if (!SharedConfig.inappCamera) {
            hasRecordVideo = false;
        }
        if (hasRecordVideo) {
            if (SharedConfig.hasCameraCache) {
                CameraController.getInstance().initCamera(null);
            }
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean currentModeVideo = preferences.getBoolean(isChannel ? "currentModeVideoChannel" : "currentModeVideo", isChannel);
            setRecordVideoButtonVisible(currentModeVideo, false);
        } else {
            setRecordVideoButtonVisible(false, false);
        }
    }

    public boolean isInVideoMode() {
        return videoSendButton.getTag() != null;
    }

    public boolean hasRecordVideo() {
        return hasRecordVideo;
    }

    private void updateFieldHint() {
        boolean isChannel = false;
        if ((int) dialog_id < 0) {
            TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-(int) dialog_id);
            isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
        }
        if (editingMessageObject != null) {
            messageEditText.setHintText(editingCaption ? LocaleController.getString("Caption", R.string.Caption) : LocaleController.getString("TypeMessage", R.string.TypeMessage));
        } else if (isChannel) {
            if (silent) {
                messageEditText.setHintText(LocaleController.getString("ChannelSilentBroadcast", R.string.ChannelSilentBroadcast));
            } else {
                messageEditText.setHintText(LocaleController.getString("ChannelBroadcast", R.string.ChannelBroadcast));
            }
        } else {
            messageEditText.setHintText(LocaleController.getString("TypeMessage", R.string.TypeMessage));
        }
    }

    public void setReplyingMessageObject(MessageObject messageObject) {
        if (messageObject != null) {
            if (botMessageObject == null && botButtonsMessageObject != replyingMessageObject) {
                botMessageObject = botButtonsMessageObject;
            }
            replyingMessageObject = messageObject;
            setButtons(replyingMessageObject, true);
        } else if (messageObject == null && replyingMessageObject == botButtonsMessageObject) {
            replyingMessageObject = null;
            setButtons(botMessageObject, false);
            botMessageObject = null;
        } else {
            replyingMessageObject = messageObject;
        }
        MediaController.getInstance().setReplyingMessage(messageObject);
    }

    public void setWebPage(TLRPC.WebPage webPage, boolean searchWebPages) {
        messageWebPage = webPage;
        messageWebPageSearch = searchWebPages;
    }

    public boolean isMessageWebPageSearchEnabled() {
        return messageWebPageSearch;
    }

    private void hideRecordedAudioPanel() {
        audioToSendPath = null;
        audioToSend = null;
        audioToSendMessageObject = null;
        videoToSendMessageObject = null;
        videoTimelineView.destroy();
        AnimatorSet AnimatorSet = new AnimatorSet();
        AnimatorSet.playTogether(
                ObjectAnimator.ofFloat(recordedAudioPanel, View.ALPHA, 0.0f)
        );
        AnimatorSet.setDuration(200);
        AnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                recordedAudioPanel.setVisibility(GONE);

            }
        });
        AnimatorSet.start();
    }

    private void sendMessage() {
        if (isInScheduleMode()) {
            AlertsCreator.createScheduleDatePickerDialog(parentActivity, UserObject.isUserSelf(parentFragment.getCurrentUser()), this::sendMessageInternal);
        } else {
            sendMessageInternal(true, 0);
        }
    }

    private void sendMessageInternal(boolean notify, int scheduleDate) {
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
            if (searchingType != 0) {
                emojiView.closeSearch(false);
                emojiView.hideSearchKeyboard();
            }
        }
        if (videoToSendMessageObject != null) {
            delegate.needStartRecordVideo(4, notify, scheduleDate);
            hideRecordedAudioPanel();
            checkSendButton(true);
            return;
        } else if (audioToSend != null) {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null && playing == audioToSendMessageObject) {
                MediaController.getInstance().cleanupPlayer(true, true);
            }
            SendMessagesHelper.getInstance(currentAccount).sendMessage(audioToSend, null, audioToSendPath, dialog_id, replyingMessageObject, null, null, null, null, notify, scheduleDate, 0, null);
            if (delegate != null) {
                delegate.onMessageSend(null, notify, scheduleDate);
            }
            hideRecordedAudioPanel();
            checkSendButton(true);
            return;
        }
        CharSequence message = messageEditText.getText();
        if (parentFragment != null) {
            TLRPC.Chat chat = parentFragment.getCurrentChat();
            if (chat != null && chat.slowmode_enabled && !ChatObject.hasAdminRights(chat)) {
                if (message.length() > accountInstance.getMessagesController().maxMessageLength) {
                    AlertsCreator.showSimpleAlert(parentFragment, LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSendErrorTooLong", R.string.SlowmodeSendErrorTooLong));
                    return;
                } else if (forceShowSendButton && message.length() > 0) {
                    AlertsCreator.showSimpleAlert(parentFragment, LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSendError", R.string.SlowmodeSendError));
                    return;
                }
            }
        }
        if (processSendingText(message, notify, scheduleDate)) {
            messageEditText.setText("");
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend(message, notify, scheduleDate);
            }
        } else if (forceShowSendButton) {
            if (delegate != null) {
                delegate.onMessageSend(null, notify, scheduleDate);
            }
        }
    }

    public void doneEditingMessage() {
        if (editingMessageObject != null) {
            delegate.onMessageEditEnd(true);
            showEditDoneProgress(true, true);
            CharSequence[] message = new CharSequence[]{messageEditText.getText()};
            ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(message);
            editingMessageReqId = SendMessagesHelper.getInstance(currentAccount).editMessage(editingMessageObject, message[0].toString(), messageWebPageSearch, parentFragment, entities, editingMessageObject.scheduled ? editingMessageObject.messageOwner.date : 0, () -> {
                editingMessageReqId = 0;
                setEditingMessageObject(null, false);
            });
        }
    }

    public boolean processSendingText(CharSequence text, boolean notify, int scheduleDate) {
        text = AndroidUtilities.getTrimmedString(text);
        /*if (text.length() > 0 && text.length() <= 14 && replyingMessageObject != null && Emoji.isValidEmoji(text.toString()) && parentFragment != null) {
            SendMessagesHelper.getInstance(currentAccount).sendReaction(replyingMessageObject, text, parentFragment);
            return true;
        } else {*/
            int maxLength = accountInstance.getMessagesController().maxMessageLength;
            if (text.length() != 0) {
                int count = (int) Math.ceil(text.length() / (float) maxLength);
                for (int a = 0; a < count; a++) {
                    CharSequence[] message = new CharSequence[]{text.subSequence(a * maxLength, Math.min((a + 1) * maxLength, text.length()))};
                    ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(currentAccount).getEntities(message);
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(message[0].toString(), dialog_id, replyingMessageObject, messageWebPage, messageWebPageSearch, entities, null, null, notify, scheduleDate);
                }
                return true;
            }
            return false;
        //}
    }

    private void checkSendButton(boolean animated) {
        if (editingMessageObject != null) {
            return;
        }
        if (isPaused) {
            animated = false;
        }
        CharSequence message = AndroidUtilities.getTrimmedString(messageEditText.getText());
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
                        animators.add(ObjectAnimator.ofFloat(attachLayout, View.ALPHA, 0.0f));
                        animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 0.0f));
                        scheduleButtonHidden = false;
                        boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                        if (scheduledButton != null) {
                            scheduledButton.setScaleY(1.0f);
                            if (hasScheduled) {
                                scheduledButton.setVisibility(VISIBLE);
                                scheduledButton.setTag(1);
                                scheduledButton.setPivotX(AndroidUtilities.dp(48));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, AndroidUtilities.dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48)));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1.0f));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 1.0f));
                            } else {
                                scheduledButton.setTranslationX(AndroidUtilities.dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48));
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
                    }

                    runningAnimationType = 5;
                    runningAnimation = new AnimatorSet();

                    ArrayList<Animator> animators = new ArrayList<>();
                    if (audioVideoButtonContainer.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_X, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_Y, 0.1f));
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 0.0f));
                    }
                    if (expandStickersButton.getVisibility() == VISIBLE) {
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
                    slowModeButton.setVisibility(VISIBLE);
                    runningAnimation.playTogether(animators);
                    runningAnimation.setDuration(150);
                    runningAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation.equals(runningAnimation)) {
                                sendButton.setVisibility(GONE);
                                cancelBotButton.setVisibility(GONE);
                                audioVideoButtonContainer.setVisibility(GONE);
                                expandStickersButton.setVisibility(GONE);
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
                    slowModeButton.setVisibility(VISIBLE);

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

                    if (expandStickersButton.getVisibility() == VISIBLE) {
                        expandStickersButton.setScaleX(0.1f);
                        expandStickersButton.setScaleY(0.1f);
                        expandStickersButton.setAlpha(0.0f);
                        expandStickersButton.setVisibility(GONE);
                    }
                    if (attachLayout != null) {
                        attachLayout.setVisibility(GONE);
                        updateFieldRight(0);
                    }
                    scheduleButtonHidden = false;
                    if (scheduledButton != null) {
                        if (delegate != null && delegate.hasScheduledMessages()) {
                            scheduledButton.setVisibility(VISIBLE);
                            scheduledButton.setTag(1);
                        }
                        scheduledButton.setTranslationX(AndroidUtilities.dp(botButton != null && botButton.getVisibility() == VISIBLE ? 96 : 48));
                        scheduledButton.setAlpha(1.0f);
                        scheduledButton.setScaleX(1.0f);
                        scheduledButton.setScaleY(1.0f);
                    }
                }
            }
        } else if (message.length() > 0 || forceShowSendButton || audioToSend != null || videoToSendMessageObject != null || slowModeTimer == Integer.MAX_VALUE && !isInScheduleMode()) {
            final String caption = messageEditText.getCaption();
            boolean showBotButton = caption != null && (sendButton.getVisibility() == VISIBLE || expandStickersButton.getVisibility() == VISIBLE);
            boolean showSendButton = caption == null && (cancelBotButton.getVisibility() == VISIBLE || expandStickersButton.getVisibility() == VISIBLE);
            int color;
            if (slowModeTimer == Integer.MAX_VALUE && !isInScheduleMode()) {
                color = Theme.getColor(Theme.key_chat_messagePanelIcons);
            } else {
                color = Theme.getColor(Theme.key_chat_messagePanelSend);
            }
            Theme.setSelectorDrawableColor(sendButton.getBackground(), Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)), true);
            if (audioVideoButtonContainer.getVisibility() == VISIBLE || slowModeButton.getVisibility() == VISIBLE || showBotButton || showSendButton) {
                if (animated) {
                    if (runningAnimationType == 1 && messageEditText.getCaption() == null || runningAnimationType == 3 && caption != null) {
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
                        animators.add(ObjectAnimator.ofFloat(attachLayout, View.ALPHA, 0.0f));
                        animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 0.0f));
                        boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                        scheduleButtonHidden = true;
                        if (scheduledButton != null) {
                            scheduledButton.setScaleY(1.0f);
                            if (hasScheduled) {
                                scheduledButton.setTag(null);
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 0.0f));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 0.0f));
                                animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, AndroidUtilities.dp(botButton == null || botButton.getVisibility() == GONE ? 48 : 96)));
                            } else {
                                scheduledButton.setAlpha(0.0f);
                                scheduledButton.setScaleX(0.0f);
                                scheduledButton.setTranslationX(AndroidUtilities.dp(botButton == null || botButton.getVisibility() == GONE ? 48 : 96));
                            }
                        }
                        runningAnimation2.playTogether(animators);
                        runningAnimation2.setDuration(100);
                        runningAnimation2.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (animation.equals(runningAnimation2)) {
                                    attachLayout.setVisibility(GONE);
                                    if (hasScheduled) {
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
                    if (expandStickersButton.getVisibility() == VISIBLE) {
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
                                expandStickersButton.setVisibility(GONE);
                                slowModeButton.setVisibility(GONE);
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
                        slowModeButton.setVisibility(GONE);
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
                    if (expandStickersButton.getVisibility() == VISIBLE) {
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
                        scheduledButton.setTranslationX(AndroidUtilities.dp(botButton == null || botButton.getVisibility() == GONE ? 48 : 96));
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

                if (attachLayout != null) {
                    attachLayout.setVisibility(VISIBLE);
                    runningAnimation2 = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    animators.add(ObjectAnimator.ofFloat(attachLayout, View.ALPHA, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 1.0f));
                    boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                    scheduleButtonHidden = false;
                    if (scheduledButton != null) {
                        scheduledButton.setScaleY(1.0f);
                        if (hasScheduled) {
                            scheduledButton.setVisibility(VISIBLE);
                            scheduledButton.setTag(1);
                            scheduledButton.setPivotX(AndroidUtilities.dp(48));
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
                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(runningAnimation)) {
                            sendButton.setVisibility(GONE);
                            cancelBotButton.setVisibility(GONE);
                            slowModeButton.setVisibility(GONE);
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
                slowModeButton.setVisibility(GONE);
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
        } else if (sendButton.getVisibility() == VISIBLE || cancelBotButton.getVisibility() == VISIBLE || expandStickersButton.getVisibility() == VISIBLE || slowModeButton.getVisibility() == VISIBLE) {
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
                    attachLayout.setVisibility(VISIBLE);
                    runningAnimation2 = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    animators.add(ObjectAnimator.ofFloat(attachLayout, View.ALPHA, 1.0f));
                    animators.add(ObjectAnimator.ofFloat(attachLayout, View.SCALE_X, 1.0f));
                    boolean hasScheduled = delegate != null && delegate.hasScheduledMessages();
                    scheduleButtonHidden = false;
                    if (scheduledButton != null) {
                        if (hasScheduled) {
                            scheduledButton.setVisibility(VISIBLE);
                            scheduledButton.setTag(1);
                            scheduledButton.setPivotX(AndroidUtilities.dp(48));
                            animators.add(ObjectAnimator.ofFloat(scheduledButton, View.ALPHA, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(scheduledButton, View.SCALE_X, 1.0f));
                            animators.add(ObjectAnimator.ofFloat(scheduledButton, View.TRANSLATION_X, 0));
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
                animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 1.0f));
                if (cancelBotButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_X, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.SCALE_Y, 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, View.ALPHA, 0.0f));
                } else if (expandStickersButton.getVisibility() == VISIBLE) {
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
                            sendButton.setVisibility(GONE);
                            cancelBotButton.setVisibility(GONE);
                            slowModeButton.setVisibility(GONE);
                            audioVideoButtonContainer.setVisibility(VISIBLE);
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
                slowModeButton.setVisibility(GONE);
                sendButton.setScaleX(0.1f);
                sendButton.setScaleY(0.1f);
                sendButton.setAlpha(0.0f);
                sendButton.setVisibility(GONE);
                cancelBotButton.setScaleX(0.1f);
                cancelBotButton.setScaleY(0.1f);
                cancelBotButton.setAlpha(0.0f);
                cancelBotButton.setVisibility(GONE);
                expandStickersButton.setScaleX(0.1f);
                expandStickersButton.setScaleY(0.1f);
                expandStickersButton.setAlpha(0.0f);
                expandStickersButton.setVisibility(GONE);
                audioVideoButtonContainer.setScaleX(1.0f);
                audioVideoButtonContainer.setScaleY(1.0f);
                audioVideoButtonContainer.setAlpha(1.0f);
                audioVideoButtonContainer.setVisibility(VISIBLE);
                if (attachLayout != null) {
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                    attachLayout.setAlpha(1.0f);
                    attachLayout.setScaleX(1.0f);
                    attachLayout.setVisibility(VISIBLE);
                    updateFieldRight(1);
                }
                scheduleButtonHidden = false;
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

    private void updateFieldRight(int attachVisible) {
        if (messageEditText == null || editingMessageObject != null) {
            return;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messageEditText.getLayoutParams();
        if (attachVisible == 1) {
            if (botButton != null && botButton.getVisibility() == VISIBLE || notifyButton != null && notifyButton.getVisibility() == VISIBLE || scheduledButton != null && scheduledButton.getTag() != null) {
                layoutParams.rightMargin = AndroidUtilities.dp(98);
            } else {
                layoutParams.rightMargin = AndroidUtilities.dp(50);
            }
        } else if (attachVisible == 2) {
            if (layoutParams.rightMargin != AndroidUtilities.dp(2)) {
                if (botButton != null && botButton.getVisibility() == VISIBLE || notifyButton != null && notifyButton.getVisibility() == VISIBLE || scheduledButton != null && scheduledButton.getTag() != null) {
                    layoutParams.rightMargin = AndroidUtilities.dp(98);
                } else {
                    layoutParams.rightMargin = AndroidUtilities.dp(50);
                }
            }
        } else {
            if (scheduledButton != null && scheduledButton.getTag() != null) {
                layoutParams.rightMargin = AndroidUtilities.dp(50);
            } else {
                layoutParams.rightMargin = AndroidUtilities.dp(2);
            }
        }
        messageEditText.setLayoutParams(layoutParams);
    }

    private void updateRecordIntefrace() {
        if (recordingAudioVideo) {
            if (recordInterfaceState == 1) {
                return;
            }
            recordInterfaceState = 1;
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
            recordPanel.setVisibility(VISIBLE);
            recordCircle.setVisibility(VISIBLE);
            recordCircle.setAmplitude(0);
            recordTimeText.setText(String.format("%02d:%02d.%02d", 0, 0, 0));
            recordDot.resetAlpha();
            lastTimeString = null;
            lastTypingSendTime = -1;

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
            params.leftMargin = AndroidUtilities.dp(30);
            slideText.setLayoutParams(params);
            slideText.setAlpha(1);
            recordPanel.setX(AndroidUtilities.displaySize.x);
            if (runningAnimationAudio != null) {
                runningAnimationAudio.cancel();
            }
            runningAnimationAudio = new AnimatorSet();
            runningAnimationAudio.playTogether(ObjectAnimator.ofFloat(recordPanel, View.TRANSLATION_X, 0),
                    ObjectAnimator.ofFloat(recordCircle, recordCircleScale, 1),
                    ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 0));
            runningAnimationAudio.setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (animator.equals(runningAnimationAudio)) {
                        recordPanel.setX(0);
                        runningAnimationAudio = null;
                    }
                }
            });
            runningAnimationAudio.setInterpolator(new DecelerateInterpolator());
            runningAnimationAudio.start();
        } else {
            if (wakeLock != null) {
                try {
                    wakeLock.release();
                    wakeLock = null;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            AndroidUtilities.unlockOrientation(parentActivity);
            if (recordInterfaceState == 0) {
                return;
            }
            recordInterfaceState = 0;

            if (runningAnimationAudio != null) {
                runningAnimationAudio.cancel();
            }
            runningAnimationAudio = new AnimatorSet();
            runningAnimationAudio.playTogether(ObjectAnimator.ofFloat(recordPanel, View.TRANSLATION_X, AndroidUtilities.displaySize.x),
                    ObjectAnimator.ofFloat(recordCircle, recordCircleScale, 0.0f),
                    ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA, 1.0f));
            runningAnimationAudio.setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (animator.equals(runningAnimationAudio)) {
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
                        params.leftMargin = AndroidUtilities.dp(30);
                        slideText.setLayoutParams(params);
                        slideText.setAlpha(1);
                        recordPanel.setVisibility(GONE);
                        recordCircle.setVisibility(GONE);
                        recordCircle.setSendButtonInvisible();
                        runningAnimationAudio = null;
                    }
                }
            });
            runningAnimationAudio.setInterpolator(new AccelerateInterpolator());
            runningAnimationAudio.start();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (recordingAudioVideo) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.onInterceptTouchEvent(ev);
    }

    public void setDelegate(ChatActivityEnterViewDelegate chatActivityEnterViewDelegate) {
        delegate = chatActivityEnterViewDelegate;
    }

    public void setCommand(MessageObject messageObject, String command, boolean longPress, boolean username) {
        if (command == null || getVisibility() != VISIBLE) {
            return;
        }
        if (longPress) {
            String text = messageEditText.getText().toString();
            TLRPC.User user = messageObject != null && (int) dialog_id < 0 ? accountInstance.getMessagesController().getUser(messageObject.messageOwner.from_id) : null;
            if ((botCount != 1 || username) && user != null && user.bot && !command.contains("@")) {
                text = String.format(Locale.US, "%s@%s", command, user.username) + " " + text.replaceFirst("^/[a-zA-Z@\\d_]{1,255}(\\s|$)", "");
            } else {
                text = command + " " + text.replaceFirst("^/[a-zA-Z@\\d_]{1,255}(\\s|$)", "");
            }
            ignoreTextChange = true;
            messageEditText.setText(text);
            messageEditText.setSelection(messageEditText.getText().length());
            ignoreTextChange = false;
            if (delegate != null) {
                delegate.onTextChanged(messageEditText.getText(), true);
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
            TLRPC.User user = messageObject != null && (int) dialog_id < 0 ? accountInstance.getMessagesController().getUser(messageObject.messageOwner.from_id) : null;
            if ((botCount != 1 || username) && user != null && user.bot && !command.contains("@")) {
                SendMessagesHelper.getInstance(currentAccount).sendMessage(String.format(Locale.US, "%s@%s", command, user.username), dialog_id, replyingMessageObject, null, false, null, null, null, true, 0);
            } else {
                SendMessagesHelper.getInstance(currentAccount).sendMessage(command, dialog_id, replyingMessageObject, null, false, null, null, null, true, 0);
            }
        }
    }

    public void setEditingMessageObject(MessageObject messageObject, boolean caption) {
        if (audioToSend != null || videoToSendMessageObject != null || editingMessageObject == messageObject) {
            return;
        }
        if (editingMessageReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(editingMessageReqId, true);
            editingMessageReqId = 0;
        }
        editingMessageObject = messageObject;
        editingCaption = caption;
        if (editingMessageObject != null) {
            if (doneButtonAnimation != null) {
                doneButtonAnimation.cancel();
                doneButtonAnimation = null;
            }
            doneButtonContainer.setVisibility(View.VISIBLE);
            showEditDoneProgress(true, false);

            InputFilter[] inputFilters = new InputFilter[1];
            CharSequence editingText;
            if (caption) {
                inputFilters[0] = new InputFilter.LengthFilter(accountInstance.getMessagesController().maxCaptionLength);
                editingText = editingMessageObject.caption;
            } else {
                inputFilters[0] = new InputFilter.LengthFilter(accountInstance.getMessagesController().maxMessageLength);
                editingText = editingMessageObject.messageText;
            }
            if (editingText != null) {
                ArrayList<TLRPC.MessageEntity> entities = editingMessageObject.messageOwner.entities;
                MediaDataController.sortEntities(entities);
                SpannableStringBuilder stringBuilder = new SpannableStringBuilder(editingText);
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
                                stringBuilder.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id, 1), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            } else if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                                if (entity.offset + entity.length < stringBuilder.length() && stringBuilder.charAt(entity.offset + entity.length) == ' ') {
                                    entity.length++;
                                }
                                stringBuilder.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_messageEntityMentionName) entity).user_id, 1), entity.offset, entity.offset + entity.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            } else if (entity instanceof TLRPC.TL_messageEntityCode || entity instanceof TLRPC.TL_messageEntityPre) {
                                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                                run.flags |= TextStyleSpan.FLAG_STYLE_MONO;
                                MediaDataController.addStyleToText(new TextStyleSpan(run), entity.offset, entity.offset + entity.length, stringBuilder, true);
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
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                setFieldText(Emoji.replaceEmoji(new SpannableStringBuilder(stringBuilder), messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false));
            } else {
                setFieldText("");
            }
            messageEditText.setFilters(inputFilters);
            openKeyboard();
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messageEditText.getLayoutParams();
            layoutParams.rightMargin = AndroidUtilities.dp(4);
            messageEditText.setLayoutParams(layoutParams);
            sendButton.setVisibility(GONE);
            slowModeButton.setVisibility(GONE);
            cancelBotButton.setVisibility(GONE);
            audioVideoButtonContainer.setVisibility(GONE);
            attachLayout.setVisibility(GONE);
            sendButtonContainer.setVisibility(GONE);
            if (scheduledButton != null) {
                scheduledButton.setVisibility(GONE);
            }
        } else {
            doneButtonContainer.setVisibility(View.GONE);
            messageEditText.setFilters(new InputFilter[0]);
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
                    slowModeButton.setVisibility(GONE);
                } else {
                    sendButton.setScaleX(0.1f);
                    sendButton.setScaleY(0.1f);
                    sendButton.setAlpha(0.0f);
                    sendButton.setVisibility(GONE);
                    slowModeButton.setScaleX(1.0f);
                    slowModeButton.setScaleY(1.0f);
                    slowModeButton.setAlpha(1.0f);
                    slowModeButton.setVisibility(VISIBLE);
                }
                attachLayout.setScaleX(0.01f);
                attachLayout.setAlpha(0.0f);
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
                slowModeButton.setVisibility(GONE);
                attachLayout.setScaleX(1.0f);
                attachLayout.setAlpha(1.0f);
                attachLayout.setVisibility(VISIBLE);
                audioVideoButtonContainer.setScaleX(1.0f);
                audioVideoButtonContainer.setScaleY(1.0f);
                audioVideoButtonContainer.setAlpha(1.0f);
                audioVideoButtonContainer.setVisibility(VISIBLE);
            }
            if (scheduledButton.getTag() != null) {
                scheduledButton.setScaleX(1.0f);
                scheduledButton.setScaleY(1.0f);
                scheduledButton.setAlpha(1.0f);
                scheduledButton.setVisibility(VISIBLE);
            }
            messageEditText.setText("");
            if (getVisibility() == VISIBLE) {
                delegate.onAttachButtonShow();
            }
            updateFieldRight(1);
        }
        updateFieldHint();
    }

    public ImageView getAttachButton() {
        return attachButton;
    }

    public View getSendButton() {
        return sendButton.getVisibility() == VISIBLE ? sendButton : audioVideoButtonContainer;
    }

    public EmojiView getEmojiView() {
        return emojiView;
    }

    public void setFieldText(CharSequence text) {
        setFieldText(text, true);
    }

    public void setFieldText(CharSequence text, boolean ignoreChange) {
        if (messageEditText == null) {
            return;
        }
        ignoreTextChange = ignoreChange;
        messageEditText.setText(text);
        messageEditText.setSelection(messageEditText.getText().length());
        ignoreTextChange = false;
        if (ignoreChange && delegate != null) {
            delegate.onTextChanged(messageEditText.getText(), true);
        }
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
        try {
            SpannableStringBuilder builder = new SpannableStringBuilder(messageEditText.getText());
            builder.replace(start, start + len, text);
            if (parseEmoji) {
                Emoji.replaceEmoji(builder, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            }
            messageEditText.setText(builder);
            messageEditText.setSelection(start + text.length());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setFieldFocused() {
        AccessibilityManager am = (AccessibilityManager)parentActivity.getSystemService(Context.ACCESSIBILITY_SERVICE);
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
        if (focus) {
            if (searchingType == 0 && !messageEditText.isFocused()) {
                AndroidUtilities.runOnUIThread(focusRunnable = () -> {
                    focusRunnable = null;
                    boolean allowFocus;
                    if (AndroidUtilities.isTablet()) {
                        if (parentActivity instanceof LaunchActivity) {
                            LaunchActivity launchActivity = (LaunchActivity) parentActivity;
                            if (launchActivity != null) {
                                View layout = launchActivity.getLayersActionBarLayout();
                                allowFocus = layout == null || layout.getVisibility() != View.VISIBLE;
                            } else {
                                allowFocus = true;
                            }
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
            if (messageEditText != null && messageEditText.isFocused() && !keyboardVisible) {
                messageEditText.clearFocus();
            }
        }
    }

    public boolean hasText() {
        return messageEditText != null && messageEditText.length() > 0;
    }

    public EditTextCaption getEditField() {
        return messageEditText;
    }

    public CharSequence getFieldText() {
        if (hasText()) {
            return messageEditText.getText();
        }
        return null;
    }

    public void updateScheduleButton(boolean animated) {
        boolean notifyVisible = false;
        if ((int) dialog_id < 0) {
            TLRPC.Chat currentChat = accountInstance.getMessagesController().getChat(-(int) dialog_id);
            silent = MessagesController.getNotificationsSettings(currentAccount).getBoolean("silent_" + dialog_id, false);
            canWriteToChannel = ChatObject.isChannel(currentChat) && (currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.post_messages) && !currentChat.megagroup;
            if (notifyButton != null) {
                notifyVisible = canWriteToChannel;
                notifyButton.setImageResource(silent ? R.drawable.input_notify_off : R.drawable.input_notify_on);
            }
            if (attachLayout != null) {
                updateFieldRight(attachLayout.getVisibility() == VISIBLE ? 1 : 0);
            }
        }
        boolean hasScheduled = delegate != null && !isInScheduleMode() && delegate.hasScheduledMessages();
        boolean visible = hasScheduled && !scheduleButtonHidden;
        if (scheduledButton != null) {
            if (scheduledButton.getTag() != null && visible || scheduledButton.getTag() == null && !visible) {
                if (notifyButton != null) {
                    int newVisibility = !hasScheduled && notifyVisible && scheduledButton.getVisibility() != VISIBLE ? VISIBLE : GONE;
                    if (newVisibility != notifyButton.getVisibility()) {
                        notifyButton.setVisibility(newVisibility);
                        if (attachLayout != null) {
                            attachLayout.setPivotX(AndroidUtilities.dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
                        }
                    }
                }
                return;
            }
            scheduledButton.setTag(visible ? 1 : null);
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
            }
            if (notifyButton != null) {
                notifyButton.setVisibility(notifyVisible && scheduledButton.getVisibility() != VISIBLE ? VISIBLE : GONE);
            }
        } else {
            if (visible) {
                scheduledButton.setVisibility(VISIBLE);
            }
            scheduledButton.setPivotX(AndroidUtilities.dp(24));
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
            attachLayout.setPivotX(AndroidUtilities.dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
        }
    }

    private void updateBotButton() {
        if (botButton == null) {
            return;
        }
        if (hasBotCommands || botReplyMarkup != null) {
            if (botButton.getVisibility() != VISIBLE) {
                botButton.setVisibility(VISIBLE);
            }
            if (botReplyMarkup != null) {
                if (isPopupShowing() && currentPopupContentType == 1) {
                    botButton.setImageResource(R.drawable.input_keyboard);
                    botButton.setContentDescription(LocaleController.getString("AccDescrShowKeyboard", R.string.AccDescrShowKeyboard));
                } else {
                    botButton.setImageResource(R.drawable.input_bot2);
                    botButton.setContentDescription(LocaleController.getString("AccDescrBotKeyboard", R.string.AccDescrBotKeyboard));
                }
            } else {
                botButton.setImageResource(R.drawable.input_bot1);
                botButton.setContentDescription(LocaleController.getString("AccDescrBotCommands", R.string.AccDescrBotCommands));
            }
        } else {
            botButton.setVisibility(GONE);
        }
        updateFieldRight(2);
        attachLayout.setPivotX(AndroidUtilities.dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
    }

    public boolean isRtlText() {
        try {
            return messageEditText.getLayout().getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT;
        } catch (Throwable ignore) {

        }
        return false;
    }

    public void setBotsCount(int count, boolean hasCommands) {
        botCount = count;
        if (hasBotCommands != hasCommands) {
            hasBotCommands = hasCommands;
            updateBotButton();
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
        if (botButton == null || botButtonsMessageObject != null && botButtonsMessageObject == messageObject || botButtonsMessageObject == null && messageObject == null) {
            return;
        }
        if (botKeyboardView == null) {
            botKeyboardView = new BotKeyboardView(parentActivity);
            botKeyboardView.setVisibility(GONE);
            botKeyboardView.setDelegate(button -> {
                MessageObject object = replyingMessageObject != null ? replyingMessageObject : ((int) dialog_id < 0 ? botButtonsMessageObject : null);
                didPressedBotButton(button, object, replyingMessageObject != null ? replyingMessageObject : botButtonsMessageObject);
                if (replyingMessageObject != null) {
                    openKeyboardInternal();
                    setButtons(botMessageObject, false);
                } else if (botButtonsMessageObject.messageOwner.reply_markup.single_use) {
                    openKeyboardInternal();
                    SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
                    preferences.edit().putInt("answered_" + dialog_id, botButtonsMessageObject.getId()).commit();
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
        botKeyboardView.setButtons(botReplyMarkup);
        if (botReplyMarkup != null) {
            SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
            boolean keyboardHidden = preferences.getInt("hidekeyboard_" + dialog_id, 0) == messageObject.getId();
            boolean showPopup = true;
            if (botButtonsMessageObject != replyingMessageObject && botReplyMarkup.single_use) {
                if (preferences.getInt("answered_" + dialog_id, 0) == messageObject.getId()) {
                    showPopup = false;
                }
            }
            if (showPopup && !keyboardHidden && messageEditText.length() == 0 && !isPopupShowing()) {
                showPopup(1, 1);
            }
        } else {
            if (isPopupShowing() && currentPopupContentType == 1) {
                if (openKeyboard) {
                    openKeyboardInternal();
                } else {
                    showPopup(0, 1);
                }
            }
        }
        updateBotButton();
    }

    public void didPressedBotButton(final TLRPC.KeyboardButton button, final MessageObject replyMessageObject, final MessageObject messageObject) {
        if (button == null || messageObject == null) {
            return;
        }
        if (button instanceof TLRPC.TL_keyboardButton) {
            SendMessagesHelper.getInstance(currentAccount).sendMessage(button.text, dialog_id, replyMessageObject, null, false, null, null, null, true, 0);
        } else if (button instanceof TLRPC.TL_keyboardButtonUrl) {
            parentFragment.showOpenUrlAlert(button.url, true);
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestPhone) {
            parentFragment.shareMyContact(2, messageObject);
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setTitle(LocaleController.getString("ShareYouLocationTitle", R.string.ShareYouLocationTitle));
            builder.setMessage(LocaleController.getString("ShareYouLocationInfo", R.string.ShareYouLocationInfo));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialogInterface, i) -> {
                if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    parentActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                    pendingMessageObject = messageObject;
                    pendingLocationButton = button;
                    return;
                }
                SendMessagesHelper.getInstance(currentAccount).sendCurrentLocation(messageObject, button);
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            parentFragment.showDialog(builder.create());
        } else if (button instanceof TLRPC.TL_keyboardButtonCallback || button instanceof TLRPC.TL_keyboardButtonGame || button instanceof TLRPC.TL_keyboardButtonBuy || button instanceof TLRPC.TL_keyboardButtonUrlAuth) {
            SendMessagesHelper.getInstance(currentAccount).sendCallback(true, messageObject, button, parentFragment);
        } else if (button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
            if (parentFragment.processSwitchButton((TLRPC.TL_keyboardButtonSwitchInline) button)) {
                return;
            }
            if (button.same_peer) {
                int uid = messageObject.messageOwner.from_id;
                if (messageObject.messageOwner.via_bot_id != 0) {
                    uid = messageObject.messageOwner.via_bot_id;
                }
                TLRPC.User user = accountInstance.getMessagesController().getUser(uid);
                if (user == null) {
                    return;
                }
                setFieldText("@" + user.username + " " + button.query);
            } else {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", 1);
                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate((fragment1, dids, message, param) -> {
                    int uid = messageObject.messageOwner.from_id;
                    if (messageObject.messageOwner.via_bot_id != 0) {
                        uid = messageObject.messageOwner.via_bot_id;
                    }
                    TLRPC.User user = accountInstance.getMessagesController().getUser(uid);
                    if (user == null) {
                        fragment1.finishFragment();
                        return;
                    }
                    long did = dids.get(0);
                    MediaDataController.getInstance(currentAccount).saveDraft(did, "@" + user.username + " " + button.query, null, null, true);
                    if (did != dialog_id) {
                        int lower_part = (int) did;
                        if (lower_part != 0) {
                            Bundle args1 = new Bundle();
                            if (lower_part > 0) {
                                args1.putInt("user_id", lower_part);
                            } else if (lower_part < 0) {
                                args1.putInt("chat_id", -lower_part);
                            }
                            if (!accountInstance.getMessagesController().checkCanOpenChat(args1, fragment1)) {
                                return;
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
                });
                parentFragment.presentFragment(fragment);
            }
        }
    }

    public boolean isPopupView(View view) {
        return view == botKeyboardView || view == emojiView;
    }

    public boolean isRecordCircle(View view) {
        return view == recordCircle;
    }

    private void createEmojiView() {
        if (emojiView != null) {
            return;
        }
        emojiView = new EmojiView(allowStickers, allowGifs, parentActivity, true, info);
        emojiView.setVisibility(GONE);
        emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {

            @Override
            public boolean onBackspace() {
                if (messageEditText.length() == 0) {
                    return false;
                }
                messageEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                return true;
            }

            @Override
            public void onEmojiSelected(String symbol) {
                int i = messageEditText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    innerTextChange = 2;
                    CharSequence localCharSequence = Emoji.replaceEmoji(symbol, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    messageEditText.setText(messageEditText.getText().insert(i, localCharSequence));
                    int j = i + localCharSequence.length();
                    messageEditText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    innerTextChange = 0;
                }
            }

            @Override
            public void onStickerSelected(View view, TLRPC.Document sticker, Object parent, boolean notify, int scheduleDate) {
                if (slowModeTimer > 0 && !isInScheduleMode()) {
                    if (delegate != null) {
                        delegate.onUpdateSlowModeButton(view != null ? view : slowModeButton, true, slowModeButton.getText());
                    }
                    return;
                }
                if (stickersExpanded) {
                    if (searchingType != 0) {
                        searchingType = 0;
                        emojiView.closeSearch(true, MessageObject.getStickerSetId(sticker));
                        emojiView.hideSearchKeyboard();
                    }
                    setStickersExpanded(false, true, false);
                }
                ChatActivityEnterView.this.onStickerSelected(sticker, parent, false, notify, scheduleDate);
                if ((int) dialog_id == 0 && MessageObject.isGifDocument(sticker)) {
                    accountInstance.getMessagesController().saveGif(parent, sticker);
                }
            }

            @Override
            public void onStickersSettingsClick() {
                if (parentFragment != null) {
                    parentFragment.presentFragment(new StickersActivity(MediaDataController.TYPE_IMAGE));
                }
            }

            @Override
            public void onGifSelected(View view, Object gif, Object parent, boolean notify, int scheduleDate) {
                if (isInScheduleMode() && scheduleDate == 0) {
                    AlertsCreator.createScheduleDatePickerDialog(parentActivity, UserObject.isUserSelf(parentFragment.getCurrentUser()), (n, s) -> onGifSelected(view, gif, parent, n, s));
                } else {
                    if (slowModeTimer > 0 && !isInScheduleMode()) {
                        if (delegate != null) {
                            delegate.onUpdateSlowModeButton(view != null ? view : slowModeButton, true, slowModeButton.getText());
                        }
                        return;
                    }
                    if (stickersExpanded) {
                        if (searchingType != 0) {
                            emojiView.hideSearchKeyboard();
                        }
                        setStickersExpanded(false, true, false);
                    }
                    if (gif instanceof TLRPC.Document) {
                        TLRPC.Document document = (TLRPC.Document) gif;
                        SendMessagesHelper.getInstance(currentAccount).sendSticker(document, dialog_id, replyingMessageObject, parent, notify, scheduleDate);
                        MediaDataController.getInstance(currentAccount).addRecentGif(document, (int) (System.currentTimeMillis() / 1000));
                        if ((int) dialog_id == 0) {
                            accountInstance.getMessagesController().saveGif(parent, document);
                        }
                    } else if (gif instanceof TLRPC.BotInlineResult) {
                        TLRPC.BotInlineResult result = (TLRPC.BotInlineResult) gif;

                        if (result.document != null) {
                            MediaDataController.getInstance(currentAccount).addRecentGif(result.document, (int) (System.currentTimeMillis() / 1000));
                            if ((int) dialog_id == 0) {
                                accountInstance.getMessagesController().saveGif(parent, result.document);
                            }
                        }

                        TLRPC.User bot = (TLRPC.User) parent;

                        HashMap<String, String> params = new HashMap<>();
                        params.put("id", result.id);
                        params.put("query_id", "" + result.query_id);

                        SendMessagesHelper.prepareSendingBotContextResult(accountInstance, result, params, dialog_id, replyingMessageObject, notify, scheduleDate);

                        if (searchingType != 0) {
                            searchingType = 0;
                            emojiView.closeSearch(true);
                            emojiView.hideSearchKeyboard();
                        }
                    }
                    if (delegate != null) {
                        delegate.onMessageSend(null, notify, scheduleDate);
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
                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("ClearRecentEmoji", R.string.ClearRecentEmoji));
                builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), (dialogInterface, i) -> emojiView.clearRecentEmoji());
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                parentFragment.showDialog(builder.create());
            }

            @Override
            public void onShowStickerSet(TLRPC.StickerSet stickerSet, TLRPC.InputStickerSet inputStickerSet) {
                if (parentFragment == null || parentActivity == null) {
                    return;
                }
                if (stickerSet != null) {
                    inputStickerSet = new TLRPC.TL_inputStickerSetID();
                    inputStickerSet.access_hash = stickerSet.access_hash;
                    inputStickerSet.id = stickerSet.id;
                }
                parentFragment.showDialog(new StickersAlert(parentActivity, parentFragment, inputStickerSet, null, ChatActivityEnterView.this));
            }

            @Override
            public void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet) {
                MediaDataController.getInstance(currentAccount).removeStickersSet(parentActivity, stickerSet.set, 2, parentFragment, false);
            }

            @Override
            public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
                MediaDataController.getInstance(currentAccount).removeStickersSet(parentActivity, stickerSet.set, 0, parentFragment, false);
            }

            @Override
            public void onStickersGroupClick(int chatId) {
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
                searchingType = type;
                setStickersExpanded(type != 0, false, false);
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
                    stickersExpandedHeight = Math.min(stickersExpandedHeight, AndroidUtilities.dp(120) + (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight));
                }
                emojiView.getLayoutParams().height = stickersExpandedHeight;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                sizeNotifierLayout.requestLayout();
                sizeNotifierLayout.setForeground(new ScrimDrawable());
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
                if ((wasExpanded && velocity >= AndroidUtilities.dp(200)) || (!wasExpanded && velocity <= AndroidUtilities.dp(-200)) || (wasExpanded && stickersExpansionProgress <= 0.6f) || (!wasExpanded && stickersExpansionProgress >= 0.4f)) {
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
                return stickersTabOpen && !(!stickersExpanded && messageEditText.length() > 0) && emojiView.areThereAnyStickers();
            }
        });
        sizeNotifierLayout.addView(emojiView, sizeNotifierLayout.getChildCount() - 1);
        checkChannelRights();
    }

    @Override
    public void onStickerSelected(TLRPC.Document sticker, Object parent, boolean clearsInputField, boolean notify, int scheduleDate) {
        if (isInScheduleMode() && scheduleDate == 0) {
            AlertsCreator.createScheduleDatePickerDialog(parentActivity, UserObject.isUserSelf(parentFragment.getCurrentUser()), (n, s) -> onStickerSelected(sticker, parent, clearsInputField, n, s));
        } else {
            if (slowModeTimer > 0 && !isInScheduleMode()) {
                if (delegate != null) {
                    delegate.onUpdateSlowModeButton(slowModeButton, true, slowModeButton.getText());
                }
                return;
            }
            if (searchingType != 0) {
                searchingType = 0;
                emojiView.closeSearch(true);
                emojiView.hideSearchKeyboard();
            }
            setStickersExpanded(false, true, false);
            SendMessagesHelper.getInstance(currentAccount).sendSticker(sticker, dialog_id, replyingMessageObject, parent, notify, scheduleDate);
            if (delegate != null) {
                delegate.onMessageSend(null, true, scheduleDate);
            }
            if (clearsInputField) {
                setFieldText("");
            }
            MediaDataController.getInstance(currentAccount).addRecentSticker(MediaDataController.TYPE_IMAGE, parent, sticker, (int) (System.currentTimeMillis() / 1000), false);
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

    public void addStickerToRecent(TLRPC.Document sticker) {
        createEmojiView();
        emojiView.addRecentSticker(sticker);
    }

    public void hideEmojiView() {
        if (!emojiViewVisible && emojiView != null && emojiView.getVisibility() != GONE) {
            sizeNotifierLayout.removeView(emojiView);
            emojiView.setVisibility(GONE);
        }
    }

    public void showEmojiView() {
        showPopup(1, 0);
    }

    private void showPopup(int show, int contentType) {
        if (show == 1) {
            if (contentType == 0 && emojiView == null) {
                if (parentActivity == null) {
                    return;
                }
                createEmojiView();
            }

            View currentView = null;
            if (contentType == 0) {
                if (emojiView.getParent() == null) {
                    sizeNotifierLayout.addView(emojiView, sizeNotifierLayout.getChildCount() - 1);
                }
                emojiView.setVisibility(VISIBLE);
                emojiViewVisible = true;
                if (botKeyboardView != null && botKeyboardView.getVisibility() != GONE) {
                    botKeyboardView.setVisibility(GONE);
                }
                currentView = emojiView;
            } else if (contentType == 1) {
                if (emojiView != null && emojiView.getVisibility() != GONE) {
                    sizeNotifierLayout.removeView(emojiView);
                    emojiView.setVisibility(GONE);
                    emojiViewVisible = false;
                }
                botKeyboardView.setVisibility(VISIBLE);
                currentView = botKeyboardView;
            }
            currentPopupContentType = contentType;

            if (keyboardHeight <= 0) {
                keyboardHeight = MessagesController.getGlobalEmojiSettings().getInt("kbd_height", AndroidUtilities.dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = MessagesController.getGlobalEmojiSettings().getInt("kbd_height_land3", AndroidUtilities.dp(200));
            }
            int currentHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
            if (contentType == 1) {
                currentHeight = Math.min(botKeyboardView.getKeyboardHeight(), currentHeight);
            }
            if (botKeyboardView != null) {
                botKeyboardView.setPanelHeight(currentHeight);
            }
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
            layoutParams.height = currentHeight;
            currentView.setLayoutParams(layoutParams);
            if (!AndroidUtilities.isInMultiwindow) {
                AndroidUtilities.hideKeyboard(messageEditText);
            }
            if (sizeNotifierLayout != null) {
                emojiPadding = currentHeight;
                sizeNotifierLayout.requestLayout();
                setEmojiButtonImage(true, true);
                updateBotButton();
                onWindowSizeChanged();
            }
        } else {
            if (emojiButton != null) {
                setEmojiButtonImage(false, true);
            }
            currentPopupContentType = -1;
            if (emojiView != null) {
                emojiViewVisible = false;
                if (show != 2 || AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) {
                    sizeNotifierLayout.removeView(emojiView);
                    emojiView.setVisibility(GONE);
                }
            }
            if (botKeyboardView != null) {
                botKeyboardView.setVisibility(GONE);
            }
            if (sizeNotifierLayout != null) {
                if (show == 0) {
                    emojiPadding = 0;
                }
                sizeNotifierLayout.requestLayout();
                onWindowSizeChanged();
            }
            updateBotButton();
        }

        if (stickersTabOpen || emojiTabOpen) {
            checkSendButton(true);
        }
        if (stickersExpanded && show != 1) {
            setStickersExpanded(false, false, false);
        }
    }

    private void setEmojiButtonImage(boolean byOpen, boolean animated) {
        if (animated && currentEmojiIcon == -1) {
            animated = false;
        }
        int nextIcon;
        if (byOpen && currentPopupContentType == 0) {
            nextIcon = 0;
        } else {
            int currentPage;
            if (emojiView == null) {
                currentPage = MessagesController.getGlobalEmojiSettings().getInt("selected_page", 0);
            } else {
                currentPage = emojiView.getCurrentPage();
            }
            if (currentPage == 0 || !allowStickers && !allowGifs) {
                nextIcon = 1;
            } else if (currentPage == 1) {
                nextIcon = 2;
            } else {
                nextIcon = 3;
            }
        }
        if (currentEmojiIcon == nextIcon) {
            return;
        }
        if (emojiButtonAnimation != null) {
            emojiButtonAnimation.cancel();
            emojiButtonAnimation = null;
        }
        if (nextIcon == 0) {
            emojiButton[animated ? 1 : 0].setImageResource(R.drawable.input_keyboard);
        } else if (nextIcon == 1) {
            emojiButton[animated ? 1 : 0].setImageResource(R.drawable.input_smile);
        } else if (nextIcon == 2) {
            emojiButton[animated ? 1 : 0].setImageResource(R.drawable.input_sticker);
        } else if (nextIcon == 3) {
            emojiButton[animated ? 1 : 0].setImageResource(R.drawable.input_gif);
        }
        emojiButton[animated ? 1 : 0].setTag(nextIcon == 2 ? 1 : null);
        currentEmojiIcon = nextIcon;
        if (animated) {
            emojiButton[1].setVisibility(VISIBLE);
            emojiButtonAnimation = new AnimatorSet();
            emojiButtonAnimation.playTogether(
                    ObjectAnimator.ofFloat(emojiButton[0], View.SCALE_X, 0.1f),
                    ObjectAnimator.ofFloat(emojiButton[0], View.SCALE_Y, 0.1f),
                    ObjectAnimator.ofFloat(emojiButton[0], View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(emojiButton[1], View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(emojiButton[1], View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(emojiButton[1], View.ALPHA, 1.0f));
            emojiButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(emojiButtonAnimation)) {
                        emojiButtonAnimation = null;
                        ImageView temp = emojiButton[1];
                        emojiButton[1] = emojiButton[0];
                        emojiButton[0] = temp;
                        emojiButton[1].setVisibility(INVISIBLE);
                        emojiButton[1].setAlpha(0.0f);
                        emojiButton[1].setScaleX(0.1f);
                        emojiButton[1].setScaleY(0.1f);
                    }
                }
            });
            emojiButtonAnimation.setDuration(150);
            emojiButtonAnimation.start();
        }
    }

    public void hidePopup(boolean byBackButton) {
        if (isPopupShowing()) {
            if (currentPopupContentType == 1 && byBackButton && botButtonsMessageObject != null) {
                SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
                preferences.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
            }
            if (byBackButton && searchingType != 0) {
                searchingType = 0;
                emojiView.closeSearch(true);
                messageEditText.requestFocus();
                setStickersExpanded(false, true, false);
                if (emojiTabOpen) {
                    checkSendButton(true);
                }
            } else {
                if (searchingType != 0) {
                    searchingType = 0;
                    emojiView.closeSearch(false);
                    messageEditText.requestFocus();
                }
                showPopup(0, 0);
            }
        }
    }

    private void openKeyboardInternal() {
        showPopup(AndroidUtilities.usingHardwareInput || isPaused ? 0 : 2, 0);
        messageEditText.requestFocus();
        AndroidUtilities.showKeyboard(messageEditText);
        if (isPaused) {
            showKeyboardOnResume = true;
        } else if (!AndroidUtilities.usingHardwareInput && !keyboardVisible && !AndroidUtilities.isInMultiwindow) {
            waitingForKeyboardOpen = true;
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
        AndroidUtilities.showKeyboard(messageEditText);
    }

    public void closeKeyboard() {
        AndroidUtilities.hideKeyboard(messageEditText);
    }

    public boolean isPopupShowing() {
        return emojiViewVisible || botKeyboardView != null && botKeyboardView.getVisibility() == VISIBLE;
    }

    public boolean isKeyboardVisible() {
        return keyboardVisible;
    }

    public void addRecentGif(TLRPC.Document searchImage) {
        MediaDataController.getInstance(currentAccount).addRecentGif(searchImage, (int) (System.currentTimeMillis() / 1000));
        if (emojiView != null) {
            emojiView.addRecentGif(searchImage);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw && stickersExpanded) {
            searchingType = 0;
            emojiView.closeSearch(false);
            setStickersExpanded(false, false, false);
        }
        videoTimelineView.clearFrames();
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
            return;
        }
        if (height > AndroidUtilities.dp(50) && keyboardVisible && !AndroidUtilities.isInMultiwindow) {
            if (isWidthGreater) {
                keyboardHeightLand = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }

        if (isPopupShowing()) {
            int newHeight = isWidthGreater ? keyboardHeightLand : keyboardHeight;
            if (currentPopupContentType == 1 && !botKeyboardView.isFullSize()) {
                newHeight = Math.min(botKeyboardView.getKeyboardHeight(), newHeight);
            }

            View currentView = null;
            if (currentPopupContentType == 0) {
                currentView = emojiView;
            } else if (currentPopupContentType == 1) {
                currentView = botKeyboardView;
            }
            if (botKeyboardView != null) {
                botKeyboardView.setPanelHeight(newHeight);
            }

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
            if (!closeAnimationInProgress && (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) && !stickersExpanded) {
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                currentView.setLayoutParams(layoutParams);
                if (sizeNotifierLayout != null) {
                    emojiPadding = layoutParams.height;
                    sizeNotifierLayout.requestLayout();
                    onWindowSizeChanged();
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
        if (keyboardVisible && isPopupShowing()) {
            showPopup(0, currentPopupContentType);
        }
        if (emojiPadding != 0 && !keyboardVisible && keyboardVisible != oldValue && !isPopupShowing()) {
            emojiPadding = 0;
            sizeNotifierLayout.requestLayout();
        }
        if (keyboardVisible && waitingForKeyboardOpen) {
            waitingForKeyboardOpen = false;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
        }
        onWindowSizeChanged();
    }

    public int getEmojiPadding() {
        return emojiPadding;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiDidLoad) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
            if (botKeyboardView != null) {
                botKeyboardView.invalidateViews();
            }
        } else if (id == NotificationCenter.recordProgressChanged) {
            int guid = (Integer) args[0];
            if (guid != recordingGuid) {
                return;
            }
            long t = (Long) args[1];
            long time = t / 1000;
            int ms = (int) (t % 1000L) / 10;
            String str = String.format("%02d:%02d.%02d", time / 60, time % 60, ms);
            if (lastTimeString == null || !lastTimeString.equals(str)) {
                if (lastTypingSendTime != time && time % 5 == 0 && !isInScheduleMode()) {
                    lastTypingSendTime = time;
                    accountInstance.getMessagesController().sendTyping(dialog_id, videoSendButton != null && videoSendButton.getTag() != null ? 7 : 1, 0);
                }
                if (recordTimeText != null) {
                    recordTimeText.setText(str);
                }
            }
            if (recordCircle != null) {
                recordCircle.setAmplitude((Double) args[2]);
            }
            if (videoSendButton != null && videoSendButton.getTag() != null && t >= 59500) {
                startedDraggingX = -1;
                delegate.needStartRecordVideo(3, true, 0);
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
                accountInstance.getMessagesController().sendTyping(dialog_id, 2, 0);
                recordingAudioVideo = false;
                updateRecordIntefrace();
            }
            if (id == NotificationCenter.recordStopped) {
                Integer reason = (Integer) args[1];
                if (reason == 2) {
                    videoTimelineView.setVisibility(VISIBLE);
                    recordedAudioBackground.setVisibility(GONE);
                    recordedAudioTimeTextView.setVisibility(GONE);
                    recordedAudioPlayButton.setVisibility(GONE);
                    recordedAudioSeekBar.setVisibility(GONE);
                    recordedAudioPanel.setAlpha(1.0f);
                    recordedAudioPanel.setVisibility(VISIBLE);
                } else if (reason == 1) {
                    /*videoTimelineView.setVisibility(GONE);
                    recordedAudioBackground.setVisibility(VISIBLE);
                    recordedAudioTimeTextView.setVisibility(VISIBLE);
                    recordedAudioPlayButton.setVisibility(VISIBLE);
                    recordedAudioSeekBar.setVisibility(VISIBLE);
                    recordedAudioPanel.setAlpha(1.0f);
                    recordedAudioPanel.setVisibility(VISIBLE);*/
                }
            }
        } else if (id == NotificationCenter.recordStarted) {
            int guid = (Integer) args[0];
            if (guid != recordingGuid) {
                return;
            }
            if (!recordingAudioVideo) {
                recordingAudioVideo = true;
                updateRecordIntefrace();
            }
        } else if (id == NotificationCenter.audioDidSent) {
            int guid = (Integer) args[0];
            if (guid != recordingGuid) {
                return;
            }
            Object audio = args[1];
            if (audio instanceof VideoEditedInfo) {
                videoToSendMessageObject = (VideoEditedInfo) audio;

                audioToSendPath = (String) args[2];

                videoTimelineView.setVideoPath(audioToSendPath);
                videoTimelineView.setVisibility(VISIBLE);
                videoTimelineView.setMinProgressDiff(1000.0f / videoToSendMessageObject.estimatedDuration);
                recordedAudioBackground.setVisibility(GONE);
                recordedAudioTimeTextView.setVisibility(GONE);
                recordedAudioPlayButton.setVisibility(GONE);
                recordedAudioSeekBar.setVisibility(GONE);
                recordedAudioPanel.setAlpha(1.0f);
                recordedAudioPanel.setVisibility(VISIBLE);
                closeKeyboard();
                hidePopup(false);
                checkSendButton(false);
            } else {
                audioToSend = (TLRPC.TL_document) args[1];
                audioToSendPath = (String) args[2];
                if (audioToSend != null) {
                    if (recordedAudioPanel == null) {
                        return;
                    }

                    videoTimelineView.setVisibility(GONE);
                    recordedAudioBackground.setVisibility(VISIBLE);
                    recordedAudioTimeTextView.setVisibility(VISIBLE);
                    recordedAudioPlayButton.setVisibility(VISIBLE);
                    recordedAudioSeekBar.setVisibility(VISIBLE);

                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.out = true;
                    message.id = 0;
                    message.to_id = new TLRPC.TL_peerUser();
                    message.to_id.user_id = message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    message.date = (int) (System.currentTimeMillis() / 1000);
                    message.message = "";
                    message.attachPath = audioToSendPath;
                    message.media = new TLRPC.TL_messageMediaDocument();
                    message.media.flags |= 3;
                    message.media.document = audioToSend;
                    message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    audioToSendMessageObject = new MessageObject(UserConfig.selectedAccount, message, false);

                    recordedAudioPanel.setAlpha(1.0f);
                    recordedAudioPanel.setVisibility(VISIBLE);
                    int duration = 0;
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
                                attribute.waveform = MediaController.getInstance().getWaveform(audioToSendPath);
                            }
                            recordedAudioSeekBar.setWaveform(attribute.waveform);
                            break;
                        }
                    }
                    recordedAudioTimeTextView.setText(String.format("%d:%02d", duration / 60, duration % 60));
                    closeKeyboard();
                    hidePopup(false);
                    checkSendButton(false);
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
                recordedAudioPlayButton.setImageDrawable(playDrawable);
                recordedAudioPlayButton.setContentDescription(LocaleController.getString("AccActionPlay", R.string.AccActionPlay));
                recordedAudioSeekBar.setProgress(0);
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
                for (int a = 0; a < emojiButton.length; a++) {
                    emojiButton[a].invalidate();
                }
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Boolean scheduled = (Boolean) args[6];
            if (scheduled) {
                return;
            }
            long did = (Long) args[3];
            if (did == dialog_id && info != null && info.slowmode_seconds != 0) {
                TLRPC.Chat chat = accountInstance.getMessagesController().getChat(info.id);
                if (chat != null && !ChatObject.hasAdminRights(chat)) {
                    info.slowmode_next_send_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + info.slowmode_seconds;
                    info.flags |= 262144;
                    setSlowModeTimer(info.slowmode_next_send_date);
                }
            }
        } else if (id == NotificationCenter.sendingMessagesChanged) {
            if (info != null) {
                updateSlowModeText();
            }
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

    private void checkStickresExpandHeight() {
        final int origHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
        int newHeight = originalViewHeight - (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0) - ActionBar.getCurrentActionBarHeight() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
        if (searchingType == 2) {
            newHeight = Math.min(newHeight, AndroidUtilities.dp(120) + origHeight);
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
            AnimatorSet anims = new AnimatorSet();
            anims.playTogether(
                    ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                    ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight))
            );
            ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(animation -> sizeNotifierLayout.invalidate());
            anims.setDuration(400);
            anims.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            anims.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    stickersExpansionAnim = null;
                    if (emojiView != null) {
                        emojiView.getLayoutParams().height = stickersExpandedHeight;
                        emojiView.setLayerType(LAYER_TYPE_NONE, null);
                    }
                }
            });
            stickersExpansionAnim = anims;
            emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
            anims.start();
        } else {
            emojiView.getLayoutParams().height = stickersExpandedHeight;
            sizeNotifierLayout.requestLayout();
            int start = messageEditText.getSelectionStart();
            int end = messageEditText.getSelectionEnd();
            messageEditText.setText(messageEditText.getText()); // dismiss action mode, if any
            messageEditText.setSelection(start, end);
            AnimatorSet anims = new AnimatorSet();
            anims.playTogether(
                    ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                    ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight))
            );
            ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(animation -> sizeNotifierLayout.invalidate());
            anims.setDuration(400);
            anims.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
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

    private void setStickersExpanded(boolean expanded, boolean animated, boolean byDrag) {
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
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 1);
            originalViewHeight = sizeNotifierLayout.getHeight();
            stickersExpandedHeight = originalViewHeight - (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0) - ActionBar.getCurrentActionBarHeight() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
            if (searchingType == 2) {
                stickersExpandedHeight = Math.min(stickersExpandedHeight, AndroidUtilities.dp(120) + origHeight);
            }
            emojiView.getLayoutParams().height = stickersExpandedHeight;
            sizeNotifierLayout.requestLayout();
            sizeNotifierLayout.setForeground(new ScrimDrawable());
            int start = messageEditText.getSelectionStart();
            int end = messageEditText.getSelectionEnd();
            messageEditText.setText(messageEditText.getText()); // dismiss action mode, if any
            messageEditText.setSelection(start, end);
            if (animated) {
                AnimatorSet anims = new AnimatorSet();
                anims.playTogether(
                        ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                        ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                        ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 1)
                );
                anims.setDuration(400);
                anims.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(animation -> {
                    stickersExpansionProgress = getTranslationY() / (-(stickersExpandedHeight - origHeight));
                    sizeNotifierLayout.invalidate();
                });
                anims.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        stickersExpansionAnim = null;
                        emojiView.setLayerType(LAYER_TYPE_NONE, null);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    }
                });
                stickersExpansionAnim = anims;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
                anims.start();
            } else {
                stickersExpansionProgress = 1;
                setTranslationY(-(stickersExpandedHeight - origHeight));
                emojiView.setTranslationY(-(stickersExpandedHeight - origHeight));
                stickersArrow.setAnimationProgress(1);
            }
        } else {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 1);
            if (animated) {
                closeAnimationInProgress = true;
                AnimatorSet anims = new AnimatorSet();
                anims.playTogether(
                        ObjectAnimator.ofInt(this, roundedTranslationYProperty, 0),
                        ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, 0),
                        ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 0)
                );
                anims.setDuration(400);
                anims.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
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
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
                    }
                });
                stickersExpansionAnim = anims;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
                anims.start();
            } else {
                stickersExpansionProgress = 0;
                setTranslationY(0);
                emojiView.setTranslationY(0);
                emojiView.getLayoutParams().height = origHeight;
                sizeNotifierLayout.requestLayout();
                sizeNotifierLayout.setForeground(null);
                sizeNotifierLayout.setWillNotDraw(false);
                stickersArrow.setAnimationProgress(0);
            }
        }
        if (expanded) {
			expandStickersButton.setContentDescription(LocaleController.getString("AccDescrCollapsePanel", R.string.AccDescrCollapsePanel));
        } else {
            expandStickersButton.setContentDescription(LocaleController.getString("AccDescrExpandPanel", R.string.AccDescrExpandPanel));
        }
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
            canvas.drawRect(0, 0, getWidth(), emojiView.getY() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight(), paint);
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
}
