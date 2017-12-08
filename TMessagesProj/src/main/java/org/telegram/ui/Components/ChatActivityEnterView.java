/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v4.os.BuildCompat;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
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
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.query.DraftQuery;
import org.telegram.messenger.query.MessagesQuery;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupStickersActivity;
import org.telegram.ui.StickersActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class ChatActivityEnterView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate, StickersAlert.StickersAlertDelegate {

    public interface ChatActivityEnterViewDelegate {
        void onMessageSend(CharSequence message);
        void needSendTyping();
        void onTextChanged(CharSequence text, boolean bigChange);
        void onAttachButtonHidden();
        void onAttachButtonShow();
        void onWindowSizeChanged(int size);
        void onStickersTab(boolean opened);
        void onMessageEditEnd(boolean loading);
        void didPressedAttachButton();
        void needStartRecordVideo(int state);
        void needChangeVideoPreviewState(int state, float seekProgress);
        void onSwitchRecordMode(boolean video);
        void onPreAudioVideoRecord();
        void needStartRecordAudio(int state);
        void needShowMediaBanHint();
    }

    private class SeekBarWaveformView extends View {

        private SeekBarWaveform seekBarWaveform;

        public SeekBarWaveformView(Context context) {
            super(context);
            seekBarWaveform = new SeekBarWaveform(context);
            seekBarWaveform.setDelegate(new SeekBar.SeekBarDelegate() {
                @Override
                public void onSeekBarDrag(float progress) {
                    if (audioToSendMessageObject != null) {
                        audioToSendMessageObject.audioProgress = progress;
                        MediaController.getInstance().seekToProgress(audioToSendMessageObject, progress);
                    }
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

    private EditTextCaption messageEditText;
    private ImageView sendButton;
    private ImageView cancelBotButton;
    private ImageView emojiButton;
    private ImageView expandStickersButton;
    private EmojiView emojiView;
    private TextView recordTimeText;
    private FrameLayout audioVideoButtonContainer;
    private AnimatorSet audioVideoButtonAnimation;
    private ImageView audioSendButton;
    private ImageView videoSendButton;
    private FrameLayout recordPanel;
    private FrameLayout recordedAudioPanel;
    private VideoTimelineView videoTimelineView;
    private ImageView recordDeleteImageView;
    private SeekBarWaveformView recordedAudioSeekBar;
    private View recordedAudioBackground;
    private ImageView recordedAudioPlayButton;
    private TextView recordedAudioTimeTextView;
    private LinearLayout slideText;
    private ImageView recordCancelImage;
    private TextView recordCancelText;
    private TextView recordSendText;
    private LinearLayout recordTimeContainer;
    private RecordDot recordDot;
    private SizeNotifierFrameLayout sizeNotifierLayout;
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
    private PopupWindow botKeyboardPopup;
    private BotKeyboardView botKeyboardView;
    private ImageView notifyButton;
    private RecordCircle recordCircle;
    private CloseProgressDrawable2 progressDrawable;
    private Paint dotPaint;
    private Drawable playDrawable;
    private Drawable pauseDrawable;

    private MessageObject editingMessageObject;
    private int editingMessageReqId;
    private boolean editingCaption;

    private TLRPC.ChatFull info;

    private boolean hasRecordVideo;

    private int currentPopupContentType = -1;

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
            if (messageEditText != null && waitingForKeyboardOpen && !keyboardVisible && !AndroidUtilities.usingHardwareInput && !AndroidUtilities.isInMultiwindow) {
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
                    if (prevOpen != stickersTabOpen) {
                        checkSendButton(true);
                    }
                    if (!stickersTabOpen && stickersExpanded) {
                        setStickersExpanded(false, true);
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

    private Paint redDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean stickersTabOpen;
    private boolean gifsTabOpen;
    private boolean stickersExpanded;
    private Animator stickersExpansionAnim;
    private float stickersExpansionProgress;
    private int stickersExpandedHeight;
    private boolean stickersDragging;
    private AnimatedArrowDrawable stickersArrow;

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
                delegate.needStartRecordVideo(0);
            } else {
                if (parentFragment != null) {
                    if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        parentActivity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                        return;
                    }

                    String action;
                    TLRPC.Chat currentChat;
                    if ((int) dialog_id < 0) {
                        currentChat = MessagesController.getInstance().getChat(-(int) dialog_id);
                        if (currentChat != null && currentChat.participants_count > MessagesController.getInstance().groupBigSize) {
                            action = "bigchat_upload_audio";
                        } else {
                            action = "chat_upload_audio";
                        }
                    } else {
                        action = "pm_upload_audio";
                    }
                    if (!MessagesController.isFeatureEnabled(action, parentFragment)) {
                        return;
                    }
                }
                delegate.needStartRecordAudio(1);
                startedDraggingX = -1;
                MediaController.getInstance().startRecording(dialog_id, replyingMessageObject);
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

            micDrawable = getResources().getDrawable(R.drawable.mic).mutate();
            micDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));

            cameraDrawable = getResources().getDrawable(R.drawable.ic_msg_panel_video).mutate();
            cameraDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));

            sendDrawable = getResources().getDrawable(R.drawable.ic_send).mutate();
            sendDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoicePressed), PorterDuff.Mode.MULTIPLY));
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

        public void setScale(float value) {
            scale = value;
            invalidate();
        }

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
                    if (pressed = lockBackgroundDrawable.getBounds().contains(x, y)) {
                        return true;
                    }
                } else if (pressed) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (lockBackgroundDrawable.getBounds().contains(x, y)) {
                            if (videoSendButton != null && videoSendButton.getTag() != null) {
                                delegate.needStartRecordVideo(3);
                            } else {
                                MediaController.getInstance().stopRecording(2);
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
    }

    public ChatActivityEnterView(Activity context, SizeNotifierFrameLayout parent, ChatActivity fragment, final boolean isChat) {
        super(context);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Theme.getColor(Theme.key_chat_emojiPanelNewTrending));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioRouteChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.featuredStickersDidLoaded);
        parentActivity = context;
        parentFragment = fragment;
        sizeNotifierLayout = parent;
        sizeNotifierLayout.setDelegate(this);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        sendByEnter = preferences.getBoolean("send_by_enter", false);

        textFieldContainer = new LinearLayout(context);
        textFieldContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 2, 0, 0));

        FrameLayout frameLayout = new FrameLayout(context);
        textFieldContainer.addView(frameLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        emojiButton = new ImageView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (attachLayout != null && (emojiView == null || emojiView.getVisibility() != VISIBLE) && !StickersQuery.getUnreadStickerSets().isEmpty() && dotPaint != null) {
                    int x = canvas.getWidth() / 2 + AndroidUtilities.dp(4 + 5);
                    int y = canvas.getHeight() / 2 - AndroidUtilities.dp(13 - 5);
                    canvas.drawCircle(x, y, AndroidUtilities.dp(5), dotPaint);
                }
            }
        };
        emojiButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setPadding(0, AndroidUtilities.dp(1), 0, 0);
//        if (Build.VERSION.SDK_INT >= 21) {
//            emojiButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//        }
        setEmojiButtonImage();
        frameLayout.addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT, 3, 0, 0, 0));
        emojiButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isPopupShowing() || currentPopupContentType != 0) {
                    showPopup(1, 0);
                    emojiView.onOpen(messageEditText.length() > 0 && !messageEditText.getText().toString().startsWith("@gif"));
                } else {
                    openKeyboardInternal();
                    removeGifFromInputField();
                }
            }
        });

        messageEditText = new EditTextCaption(context) {
            @Override
            public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
                final InputConnection ic = super.onCreateInputConnection(editorInfo);
                EditorInfoCompat.setContentMimeTypes(editorInfo, new String[]{"image/gif", "image/*", "image/jpg", "image/png"});

                final InputConnectionCompat.OnCommitContentListener callback = new InputConnectionCompat.OnCommitContentListener() {
                    @Override
                    public boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts) {
                        if (BuildCompat.isAtLeastNMR1() && (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                            try {
                                inputContentInfo.requestPermission();
                            } catch (Exception e) {
                                return false;
                            }
                        }
                        ClipDescription description = inputContentInfo.getDescription();
                        if (description.hasMimeType("image/gif")) {
                            SendMessagesHelper.prepareSendingDocument(null, null, inputContentInfo.getContentUri(), "image/gif", dialog_id, replyingMessageObject, inputContentInfo);
                        } else {
                            SendMessagesHelper.prepareSendingPhoto(null, inputContentInfo.getContentUri(), dialog_id, replyingMessageObject, null, null, inputContentInfo, 0);
                        }
                        if (delegate != null) {
                            delegate.onMessageSend(null);
                        }
                        return true;
                    }
                };
                return InputConnectionCompat.createWrapper(ic, editorInfo, callback);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (isPopupShowing() && event.getAction() == MotionEvent.ACTION_DOWN) {
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
        };
        updateFieldHint();
        messageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messageEditText.setInputType(messageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messageEditText.setSingleLine(false);
        messageEditText.setMaxLines(4);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messageEditText.setGravity(Gravity.BOTTOM);
        messageEditText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        messageEditText.setBackgroundDrawable(null);
        messageEditText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        messageEditText.setHintColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        messageEditText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        frameLayout.addView(messageEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 52, 0, isChat ? 50 : 2, 0));
        messageEditText.setOnKeyListener(new OnKeyListener() {

            boolean ctrlPressed = false;

            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (i == KeyEvent.KEYCODE_BACK && !keyboardVisible && isPopupShowing()) {
                    if (keyEvent.getAction() == 1) {
                        if (currentPopupContentType == 1 && botButtonsMessageObject != null) {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            preferences.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
                        }
                        showPopup(0, 0);
                        removeGifFromInputField();
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
                if (innerTextChange != 2 && before != count && (count - before) > 1) {
                    processChange = true;
                }
                if (editingMessageObject == null && !canWriteToChannel && message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
                    int currentTime = ConnectionsManager.getInstance().getCurrentTime();
                    TLRPC.User currentUser = null;
                    if ((int) dialog_id > 0) {
                        currentUser = MessagesController.getInstance().getUser((int) dialog_id);
                    }
                    if (currentUser != null && (currentUser.id == UserConfig.getClientUserId() || currentUser.status != null && currentUser.status.expires < currentTime && !MessagesController.getInstance().onlinePrivacy.containsKey(currentUser.id))) {
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
            attachLayout = new LinearLayout(context);
            attachLayout.setOrientation(LinearLayout.HORIZONTAL);
            attachLayout.setEnabled(false);
            attachLayout.setPivotX(AndroidUtilities.dp(48));
            frameLayout.addView(attachLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.BOTTOM | Gravity.RIGHT));

            botButton = new ImageView(context);
            botButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            botButton.setImageResource(R.drawable.bot_keyboard2);
            botButton.setScaleType(ImageView.ScaleType.CENTER);
            botButton.setVisibility(GONE);
//            if (Build.VERSION.SDK_INT >= 21) {
//                botButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//            }
            attachLayout.addView(botButton, LayoutHelper.createLinear(48, 48));
            botButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (botReplyMarkup != null) {
                        if (!isPopupShowing() || currentPopupContentType != 1) {
                            showPopup(1, 1);
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            preferences.edit().remove("hidekeyboard_" + dialog_id).commit();
                        } else {
                            if (currentPopupContentType == 1 && botButtonsMessageObject != null) {
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                preferences.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
                            }
                            openKeyboardInternal();
                        }
                    } else if (hasBotCommands) {
                        setFieldText("/");
                        messageEditText.requestFocus();
                        openKeyboard();
                    }
                }
            });

            notifyButton = new ImageView(context);
            notifyButton.setImageResource(silent ? R.drawable.notify_members_off : R.drawable.notify_members_on);
            notifyButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            notifyButton.setScaleType(ImageView.ScaleType.CENTER);
            notifyButton.setVisibility(canWriteToChannel ? VISIBLE : GONE);
//            if (Build.VERSION.SDK_INT >= 21) {
//                notifyButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//            }
            attachLayout.addView(notifyButton, LayoutHelper.createLinear(48, 48));
            notifyButton.setOnClickListener(new OnClickListener() {

                private Toast visibleToast;

                @Override
                public void onClick(View v) {
                    silent = !silent;
                    notifyButton.setImageResource(silent ? R.drawable.notify_members_off : R.drawable.notify_members_on);
                    ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).edit().putBoolean("silent_" + dialog_id, silent).commit();
                    NotificationsController.updateServerNotificationsSettings(dialog_id);
                    try {
                        if (visibleToast != null) {
                            visibleToast.cancel();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (silent) {
                        visibleToast = Toast.makeText(parentActivity, LocaleController.getString("ChannelNotifyMembersInfoOff", R.string.ChannelNotifyMembersInfoOff), Toast.LENGTH_SHORT);
                    } else {
                        visibleToast = Toast.makeText(parentActivity, LocaleController.getString("ChannelNotifyMembersInfoOn", R.string.ChannelNotifyMembersInfoOn), Toast.LENGTH_SHORT);
                    }
                    visibleToast.show();
                    updateFieldHint();
                }
            });

            attachButton = new ImageView(context);
            attachButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            attachButton.setImageResource(R.drawable.ic_ab_attach);
            attachButton.setScaleType(ImageView.ScaleType.CENTER);
//            if (Build.VERSION.SDK_INT >= 21) {
//                attachButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//            }
            attachLayout.addView(attachButton, LayoutHelper.createLinear(48, 48));
            attachButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    delegate.didPressedAttachButton();
                }
            });
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
        recordDeleteImageView.setImageResource(R.drawable.ic_ab_delete);
        recordDeleteImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoiceDelete), PorterDuff.Mode.MULTIPLY));
        recordedAudioPanel.addView(recordDeleteImageView, LayoutHelper.createFrame(48, 48));
        recordDeleteImageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (videoToSendMessageObject != null) {
                    delegate.needStartRecordVideo(2);
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
            }
        });

        videoTimelineView = new VideoTimelineView(context);
        videoTimelineView.setColor(0xff4badf7);
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
        recordedAudioBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_chat_recordedVoiceBackground)));
        recordedAudioPanel.addView(recordedAudioBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.CENTER_VERTICAL | Gravity.LEFT, 48, 0, 0, 0));

        recordedAudioSeekBar = new SeekBarWaveformView(context);
        recordedAudioPanel.addView(recordedAudioSeekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 48 + 44, 0, 52, 0));

        playDrawable = Theme.createSimpleSelectorDrawable(context, R.drawable.s_play, Theme.getColor(Theme.key_chat_recordedVoicePlayPause), Theme.getColor(Theme.key_chat_recordedVoicePlayPausePressed));
        pauseDrawable = Theme.createSimpleSelectorDrawable(context, R.drawable.s_pause, Theme.getColor(Theme.key_chat_recordedVoicePlayPause), Theme.getColor(Theme.key_chat_recordedVoicePlayPausePressed));

        recordedAudioPlayButton = new ImageView(context);
        recordedAudioPlayButton.setImageDrawable(playDrawable);
        recordedAudioPlayButton.setScaleType(ImageView.ScaleType.CENTER);
        recordedAudioPanel.addView(recordedAudioPlayButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.BOTTOM, 48, 0, 0, 0));
        recordedAudioPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (audioToSend == null) {
                    return;
                }
                if (MediaController.getInstance().isPlayingMessage(audioToSendMessageObject) && !MediaController.getInstance().isMessagePaused()) {
                    MediaController.getInstance().pauseMessage(audioToSendMessageObject);
                    recordedAudioPlayButton.setImageDrawable(playDrawable);
                } else {
                    recordedAudioPlayButton.setImageDrawable(pauseDrawable);
                    MediaController.getInstance().playMessage(audioToSendMessageObject);
                }
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
        recordPanel.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

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
        recordSendText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasRecordVideo && videoSendButton.getTag() != null) {
                    delegate.needStartRecordVideo(2);
                } else {
                    delegate.needStartRecordAudio(0);
                    MediaController.getInstance().stopRecording(0);
                }
                recordingAudioVideo = false;
                updateRecordIntefrace();
            }
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
        textFieldContainer.addView(sendButtonContainer, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));

        audioVideoButtonContainer = new FrameLayout(context);
        audioVideoButtonContainer.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        audioVideoButtonContainer.setSoundEffectsEnabled(false);
        sendButtonContainer.addView(audioVideoButtonContainer, LayoutHelper.createFrame(48, 48));
        audioVideoButtonContainer.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (recordCircle.isSendButtonVisible()) {
                        if (!hasRecordVideo || calledRecordRunnable) {
                            startedDraggingX = -1;
                            if (hasRecordVideo && videoSendButton.getTag() != null) {
                                delegate.needStartRecordVideo(1);
                            } else {
                                delegate.needStartRecordAudio(0);
                                MediaController.getInstance().stopRecording(1);
                            }
                            recordingAudioVideo = false;
                            updateRecordIntefrace();
                        }
                        return false;
                    }
                    if (parentFragment != null) {
                        TLRPC.Chat chat = parentFragment.getCurrentChat();
                        if (ChatObject.isChannel(chat)) {
                            if (chat.banned_rights != null && chat.banned_rights.send_media) {
                                delegate.needShowMediaBanHint();
                                return false;
                            }
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
                    } else if (!hasRecordVideo || calledRecordRunnable) {
                        startedDraggingX = -1;
                        if (hasRecordVideo && videoSendButton.getTag() != null) {
                            delegate.needStartRecordVideo(1);
                        } else {
                            delegate.needStartRecordAudio(0);
                            MediaController.getInstance().stopRecording(1);
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
                                ObjectAnimator.ofFloat(slideText, "alpha", 0.0f),
                                ObjectAnimator.ofFloat(slideText, "translationY", AndroidUtilities.dp(20)),
                                ObjectAnimator.ofFloat(recordSendText, "alpha", 1.0f),
                                ObjectAnimator.ofFloat(recordSendText, "translationY", -AndroidUtilities.dp(20), 0));
                        animatorSet.setInterpolator(new DecelerateInterpolator());
                        animatorSet.setDuration(150);
                        animatorSet.start();
                        return false;
                    }
                    if (x < -distCanMove) {
                        if (hasRecordVideo && videoSendButton.getTag() != null) {
                            delegate.needStartRecordVideo(2);
                        } else {
                            delegate.needStartRecordAudio(0);
                            MediaController.getInstance().stopRecording(0);
                        }
                        recordingAudioVideo = false;
                        updateRecordIntefrace();
                    }

                    x = x + audioVideoButtonContainer.getX();
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
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
            }
        });

        audioSendButton = new ImageView(context);
        audioSendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        audioSendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        audioSendButton.setImageResource(R.drawable.mic);
        audioSendButton.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        audioVideoButtonContainer.addView(audioSendButton, LayoutHelper.createFrame(48, 48));

        if (isChat) {
            videoSendButton = new ImageView(context);
            videoSendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            videoSendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            videoSendButton.setImageResource(R.drawable.ic_msg_panel_video);
            videoSendButton.setPadding(0, 0, AndroidUtilities.dp(4), 0);
            audioVideoButtonContainer.addView(videoSendButton, LayoutHelper.createFrame(48, 48));
        }

        recordCircle = new RecordCircle(context);
        recordCircle.setVisibility(GONE);
        sizeNotifierLayout.addView(recordCircle, LayoutHelper.createFrame(124, 194, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, -36, 0));

        cancelBotButton = new ImageView(context);
        cancelBotButton.setVisibility(INVISIBLE);
        cancelBotButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cancelBotButton.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
        progressDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelCancelInlineBot), PorterDuff.Mode.MULTIPLY));
        cancelBotButton.setSoundEffectsEnabled(false);
        cancelBotButton.setScaleX(0.1f);
        cancelBotButton.setScaleY(0.1f);
        cancelBotButton.setAlpha(0.0f);
        sendButtonContainer.addView(cancelBotButton, LayoutHelper.createFrame(48, 48));
        cancelBotButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = messageEditText.getText().toString();
                int idx = text.indexOf(' ');
                if (idx == -1 || idx == text.length() - 1) {
                    setFieldText("");
                } else {
                    setFieldText(text.substring(0, idx + 1));
                }
            }
        });

        sendButton = new ImageView(context);
        sendButton.setVisibility(INVISIBLE);
        sendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend), PorterDuff.Mode.MULTIPLY));
        sendButton.setImageResource(R.drawable.ic_send);
        sendButton.setSoundEffectsEnabled(false);
        sendButton.setScaleX(0.1f);
        sendButton.setScaleY(0.1f);
        sendButton.setAlpha(0.0f);
        sendButtonContainer.addView(sendButton, LayoutHelper.createFrame(48, 48));
        sendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        expandStickersButton = new ImageView(context);
        expandStickersButton.setScaleType(ImageView.ScaleType.CENTER);
        expandStickersButton.setImageDrawable(stickersArrow = new AnimatedArrowDrawable(Theme.getColor(Theme.key_chat_messagePanelIcons)));
        expandStickersButton.setVisibility(GONE);
        expandStickersButton.setScaleX(0.1f);
        expandStickersButton.setScaleY(0.1f);
        expandStickersButton.setAlpha(0.0f);
        sendButtonContainer.addView(expandStickersButton, LayoutHelper.createFrame(48, 48));
        expandStickersButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (expandStickersButton.getVisibility() != VISIBLE || expandStickersButton.getAlpha() != 1.0f) {
                    return;
                }
                if (!stickersDragging) {
                    setStickersExpanded(!stickersExpanded, true);
                }
            }
        });


        doneButtonContainer = new FrameLayout(context);
        doneButtonContainer.setVisibility(GONE);
        textFieldContainer.addView(doneButtonContainer, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));
//        if (Build.VERSION.SDK_INT >= 21) {
//            doneButtonContainer.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//        }
        doneButtonContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                doneEditingMessage();
            }
        });

        doneButtonImage = new ImageView(context);
        doneButtonImage.setScaleType(ImageView.ScaleType.CENTER);
        doneButtonImage.setImageResource(R.drawable.edit_done);
        doneButtonImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_editDoneIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonContainer.addView(doneButtonImage, LayoutHelper.createFrame(48, 48));

        doneButtonProgress = new ContextProgressView(context, 0);
        doneButtonProgress.setVisibility(View.INVISIBLE);
        doneButtonContainer.addView(doneButtonProgress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE);
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
        canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
    }

    @Override
    public boolean hasOverlappingRendering() {
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
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            boolean isChannel = false;
            if ((int) dialog_id < 0) {
                TLRPC.Chat chat = MessagesController.getInstance().getChat(-(int) dialog_id);
                isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            }
            preferences.edit().putBoolean(isChannel ? "currentModeVideoChannel" : "currentModeVideo", visible).commit();
            audioVideoButtonAnimation = new AnimatorSet();
            audioVideoButtonAnimation.playTogether(
                    ObjectAnimator.ofFloat(videoSendButton, "scaleX", visible ? 1.0f : 0.1f),
                    ObjectAnimator.ofFloat(videoSendButton, "scaleY", visible ? 1.0f : 0.1f),
                    ObjectAnimator.ofFloat(videoSendButton, "alpha", visible ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(audioSendButton, "scaleX", visible ? 0.1f : 1.0f),
                    ObjectAnimator.ofFloat(audioSendButton, "scaleY", visible ? 0.1f : 1.0f),
                    ObjectAnimator.ofFloat(audioSendButton, "alpha", visible ? 0.0f : 1.0f));
            audioVideoButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(audioVideoButtonAnimation)) {
                        audioVideoButtonAnimation = null;
                    }
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
            delegate.needStartRecordVideo(2);
        } else {
            delegate.needStartRecordAudio(0);
            MediaController.getInstance().stopRecording(0);
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

    public void addTopView(View view, int height) {
        if (view == null) {
            return;
        }
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
            if (emojiView.getVisibility() == VISIBLE) {
                hidePopup(false);
            }
            sizeNotifierLayout.removeView(emojiView);
            emojiView = null;
        }
        allowStickers = value;
        allowGifs = value2;
        setEmojiButtonImage();
    }

    public void addEmojiToRecent(String code) {
        createEmojiView();
        emojiView.addEmojiToRecent(code);
    }

    public void setOpenGifsTabFirst() {
        createEmojiView();
        StickersQuery.loadRecents(StickersQuery.TYPE_IMAGE, true, true, false);
        emojiView.switchToGifRecent();
    }

    public void showTopView(boolean animated, final boolean openKeyboard) {
        if (topView == null || topViewShowed || getVisibility() != VISIBLE) {
            return;
        }
        needShowTopView = true;
        topViewShowed = true;
        if (allowShowTopView) {
            topView.setVisibility(VISIBLE);
            if (currentTopViewAnimation != null) {
                currentTopViewAnimation.cancel();
                currentTopViewAnimation = null;
            }
            resizeForTopView(true);
            if (animated) {
                if (keyboardVisible || isPopupShowing()) {
                    currentTopViewAnimation = new AnimatorSet();
                    currentTopViewAnimation.playTogether(ObjectAnimator.ofFloat(topView, "translationY", 0));
                    currentTopViewAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                                if (recordedAudioPanel.getVisibility() != VISIBLE && (!forceShowSendButton || openKeyboard)) {
                                    openKeyboard();
                                }
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
                    currentTopViewAnimation.start();
                } else {
                    topView.setTranslationY(0);
                    if (recordedAudioPanel.getVisibility() != VISIBLE && (!forceShowSendButton || openKeyboard)) {
                        openKeyboard();
                    }
                }
            } else {
                topView.setTranslationY(0);
                if (recordedAudioPanel.getVisibility() != VISIBLE && (!forceShowSendButton || openKeyboard)) {
                    openKeyboard();
                }
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
                        ObjectAnimator.ofFloat(doneButtonImage, "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(doneButtonImage, "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(doneButtonImage, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "alpha", 1.0f));
            } else {
                doneButtonImage.setVisibility(View.VISIBLE);
                doneButtonContainer.setEnabled(true);
                doneButtonAnimation.playTogether(
                        ObjectAnimator.ofFloat(doneButtonProgress, "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(doneButtonImage, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(doneButtonImage, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(doneButtonImage, "alpha", 1.0f));

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
                currentTopViewAnimation.playTogether(ObjectAnimator.ofFloat(topView, "translationY", topView.getLayoutParams().height));
                currentTopViewAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            topView.setVisibility(GONE);
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
                currentTopViewAnimation.start();
            } else {
                topView.setVisibility(GONE);
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
                        resizeForTopView(false);
                        topView.setTranslationY(topView.getLayoutParams().height);
                    }
                }
            } else {
                if (!allowShowTopView) {
                    allowShowTopView = true;
                    if (needShowTopView) {
                        topView.setVisibility(VISIBLE);
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
        if(stickersExpanded)
            setStickersExpanded(false, true);
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioRouteChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.featuredStickersDidLoaded);
        if (emojiView != null) {
            emojiView.onDestroy();
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
        if (ChatObject.isChannel(chat)) {
            audioVideoButtonContainer.setAlpha(chat.banned_rights == null || !chat.banned_rights.send_media ? 1.0f : 0.5f);
            if (emojiView != null) {
                emojiView.setStickersBanned(chat.banned_rights != null && chat.banned_rights.send_stickers, chat.id);
            }
        }
    }

    public void onPause() {
        isPaused = true;
        closeKeyboard();
    }

    public void onResume() {
        isPaused = false;
        if (showKeyboardOnResume) {
            showKeyboardOnResume = false;
            messageEditText.requestFocus();
            AndroidUtilities.showKeyboard(messageEditText);
            if (!AndroidUtilities.usingHardwareInput && !keyboardVisible && !AndroidUtilities.isInMultiwindow) {
                waitingForKeyboardOpen = true;
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    }

    public void setDialogId(long id) {
        dialog_id = id;
        int lower_id = (int) dialog_id;
        int high_id = (int) (dialog_id >> 32);
        if ((int) dialog_id < 0) {
            TLRPC.Chat currentChat = MessagesController.getInstance().getChat(-(int) dialog_id);
            silent = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + dialog_id, false);
            canWriteToChannel = ChatObject.isChannel(currentChat) && (currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.post_messages) && !currentChat.megagroup;
            if (notifyButton != null) {
                notifyButton.setVisibility(canWriteToChannel ? VISIBLE : GONE);
                notifyButton.setImageResource(silent ? R.drawable.notify_members_off : R.drawable.notify_members_on);
                attachLayout.setPivotX(AndroidUtilities.dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
            }
            if (attachLayout != null) {
                updateFieldRight(attachLayout.getVisibility() == VISIBLE ? 1 : 0);
            }
        }
        checkRoundVideo();
        updateFieldHint();
    }

    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        info = chatInfo;
        if (emojiView != null) {
            emojiView.setChatInfo(info);
        }
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
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance().getEncryptedChat(high_id);
            if (AndroidUtilities.getPeerLayerVersion(encryptedChat.layer) >= 66) {
                hasRecordVideo = true;
            }
        } else {
            hasRecordVideo = true;
        }
        boolean isChannel = false;
        if ((int) dialog_id < 0) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(-(int) dialog_id);
            isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
            if (isChannel && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages)) {
                hasRecordVideo = false;
            }
        }
        if (!MediaController.getInstance().canInAppCamera()) {
            hasRecordVideo = false;
        }
        if (hasRecordVideo) {
            CameraController.getInstance().initCamera();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
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
            TLRPC.Chat chat = MessagesController.getInstance().getChat(-(int) dialog_id);
            isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
        }
        if (isChannel) {
            if (editingMessageObject != null) {
                messageEditText.setHintText(editingCaption ? LocaleController.getString("Caption", R.string.Caption) : LocaleController.getString("TypeMessage", R.string.TypeMessage));
            } else {
                if (silent) {
                    messageEditText.setHintText(LocaleController.getString("ChannelSilentBroadcast", R.string.ChannelSilentBroadcast));
                } else {
                    messageEditText.setHintText(LocaleController.getString("ChannelBroadcast", R.string.ChannelBroadcast));
                }
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
                ObjectAnimator.ofFloat(recordedAudioPanel, "alpha", 0.0f)
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
        if (parentFragment != null) {
            String action;
            TLRPC.Chat currentChat;
            if ((int) dialog_id < 0) {
                currentChat = MessagesController.getInstance().getChat(-(int) dialog_id);
                if (currentChat != null && currentChat.participants_count > MessagesController.getInstance().groupBigSize) {
                    action = "bigchat_message";
                } else {
                    action = "chat_message";
                }
            } else {
                action = "pm_message";
            }
            if (!MessagesController.isFeatureEnabled(action, parentFragment)) {
                return;
            }
        }
        if (videoToSendMessageObject != null) {
            delegate.needStartRecordVideo(4);
            hideRecordedAudioPanel();
            checkSendButton(true);
            return;
        } else if (audioToSend != null) {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null && playing == audioToSendMessageObject) {
                MediaController.getInstance().cleanupPlayer(true, true);
            }
            SendMessagesHelper.getInstance().sendMessage(audioToSend, null, audioToSendPath, dialog_id, replyingMessageObject, null, null, 0);
            if (delegate != null) {
                delegate.onMessageSend(null);
            }
            hideRecordedAudioPanel();
            checkSendButton(true);
            return;
        }
        CharSequence message = messageEditText.getText();
        if (processSendingText(message)) {
            messageEditText.setText("");
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend(message);
            }
        } else if (forceShowSendButton) {
            if (delegate != null) {
                delegate.onMessageSend(null);
            }
        }
    }

    public void doneEditingMessage() {
        if (editingMessageObject != null) {
            delegate.onMessageEditEnd(true);
            showEditDoneProgress(true, true);
            CharSequence[] message = new CharSequence[]{messageEditText.getText()};
            ArrayList<TLRPC.MessageEntity> entities = MessagesQuery.getEntities(message);
            editingMessageReqId = SendMessagesHelper.getInstance().editMessage(editingMessageObject, message[0].toString(), messageWebPageSearch, parentFragment, entities, new Runnable() {
                @Override
                public void run() {
                    editingMessageReqId = 0;
                    setEditingMessageObject(null, false);
                }
            });
        }
    }

    public boolean processSendingText(CharSequence text) {
        text = AndroidUtilities.getTrimmedString(text);
        if (text.length() != 0) {
            int count = (int) Math.ceil(text.length() / 4096.0f);
            for (int a = 0; a < count; a++) {
                CharSequence[] message = new CharSequence[]{text.subSequence(a * 4096, Math.min((a + 1) * 4096, text.length()))};
                ArrayList<TLRPC.MessageEntity> entities = MessagesQuery.getEntities(message);
                SendMessagesHelper.getInstance().sendMessage(message[0].toString(), dialog_id, replyingMessageObject, messageWebPage, messageWebPageSearch, entities, null, null);
            }
            return true;
        }
        return false;
    }

    private void checkSendButton(boolean animated) {
        if (editingMessageObject != null) {
            return;
        }
        if (isPaused) {
            animated = false;
        }
        CharSequence message = AndroidUtilities.getTrimmedString(messageEditText.getText());
        if (message.length() > 0 || forceShowSendButton || audioToSend != null || videoToSendMessageObject != null) {
            final String caption = messageEditText.getCaption();
            boolean showBotButton = caption != null && (sendButton.getVisibility() == VISIBLE || expandStickersButton.getVisibility() == VISIBLE);
            boolean showSendButton = caption == null && (cancelBotButton.getVisibility() == VISIBLE || expandStickersButton.getVisibility() == VISIBLE);
            if (audioVideoButtonContainer.getVisibility() == VISIBLE || showBotButton || showSendButton) {
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
                        runningAnimation2.playTogether(
                                ObjectAnimator.ofFloat(attachLayout, "alpha", 0.0f),
                                ObjectAnimator.ofFloat(attachLayout, "scaleX", 0.0f)
                        );
                        runningAnimation2.setDuration(100);
                        runningAnimation2.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (runningAnimation2 != null && runningAnimation2.equals(animation)) {
                                    attachLayout.setVisibility(GONE);
                                }
                            }

                            @Override
                            public void onAnimationCancel(Animator animation) {
                                if (runningAnimation2 != null && runningAnimation2.equals(animation)) {
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
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "scaleX", 0.1f));
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "scaleY", 0.1f));
                        animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "alpha", 0.0f));
                    }
                    if (expandStickersButton.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleX", 0.1f));
                        animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleY", 0.1f));
                        animators.add(ObjectAnimator.ofFloat(expandStickersButton, "alpha", 0.0f));
                    }
                    if (showBotButton) {
                        animators.add(ObjectAnimator.ofFloat(sendButton, "scaleX", 0.1f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, "scaleY", 0.1f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, "alpha", 0.0f));
                    } else if (showSendButton) {
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleX", 0.1f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleY", 0.1f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, "alpha", 0.0f));
                    }
                    if (caption != null) {
                        runningAnimationType = 3;
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleX", 1.0f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleY", 1.0f));
                        animators.add(ObjectAnimator.ofFloat(cancelBotButton, "alpha", 1.0f));
                        cancelBotButton.setVisibility(VISIBLE);
                    } else {
                        runningAnimationType = 1;
                        animators.add(ObjectAnimator.ofFloat(sendButton, "scaleX", 1.0f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, "scaleY", 1.0f));
                        animators.add(ObjectAnimator.ofFloat(sendButton, "alpha", 1.0f));
                        sendButton.setVisibility(VISIBLE);
                    }

                    runningAnimation.playTogether(animators);
                    runningAnimation.setDuration(150);
                    runningAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (runningAnimation != null && runningAnimation.equals(animation)) {
                                if (caption != null) {
                                    cancelBotButton.setVisibility(VISIBLE);
                                    sendButton.setVisibility(GONE);
                                } else {
                                    sendButton.setVisibility(VISIBLE);
                                    cancelBotButton.setVisibility(GONE);
                                }
                                audioVideoButtonContainer.setVisibility(GONE);
                                expandStickersButton.setVisibility(GONE);
                                runningAnimation = null;
                                runningAnimationType = 0;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            if (runningAnimation != null && runningAnimation.equals(animation)) {
                                runningAnimation = null;
                            }
                        }
                    });
                    runningAnimation.start();
                } else {
                    audioVideoButtonContainer.setScaleX(0.1f);
                    audioVideoButtonContainer.setScaleY(0.1f);
                    audioVideoButtonContainer.setAlpha(0.0f);
                    if (caption != null) {
                        sendButton.setScaleX(0.1f);
                        sendButton.setScaleY(0.1f);
                        sendButton.setAlpha(0.0f);
                        cancelBotButton.setScaleX(1.0f);
                        cancelBotButton.setScaleY(1.0f);
                        cancelBotButton.setAlpha(1.0f);
                        cancelBotButton.setVisibility(VISIBLE);
                        sendButton.setVisibility(GONE);
                    } else {
                        cancelBotButton.setScaleX(0.1f);
                        cancelBotButton.setScaleY(0.1f);
                        cancelBotButton.setAlpha(0.0f);
                        sendButton.setScaleX(1.0f);
                        sendButton.setScaleY(1.0f);
                        sendButton.setAlpha(1.0f);
                        sendButton.setVisibility(VISIBLE);
                        cancelBotButton.setVisibility(GONE);
                    }
                    audioVideoButtonContainer.setVisibility(GONE);
                    if (attachLayout != null) {
                        attachLayout.setVisibility(GONE);
                        if (delegate != null && getVisibility() == VISIBLE) {
                            delegate.onAttachButtonHidden();
                        }
                        updateFieldRight(0);
                    }
                }
            }
        } else if (emojiView != null && emojiView.getVisibility() == VISIBLE && stickersTabOpen && !AndroidUtilities.isInMultiwindow) {
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
                    runningAnimation2.playTogether(
                            ObjectAnimator.ofFloat(attachLayout, "alpha", 1.0f),
                            ObjectAnimator.ofFloat(attachLayout, "scaleX", 1.0f)
                    );
                    runningAnimation2.setDuration(100);
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
                animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleX", 1.0f));
                animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleY", 1.0f));
                animators.add(ObjectAnimator.ofFloat(expandStickersButton, "alpha", 1.0f));
                if (cancelBotButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "alpha", 0.0f));
                } else if (audioVideoButtonContainer.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "alpha", 0.0f));
                } else {
                    animators.add(ObjectAnimator.ofFloat(sendButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, "alpha", 0.0f));
                }

                runningAnimation.playTogether(animators);
                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            sendButton.setVisibility(GONE);
                            cancelBotButton.setVisibility(GONE);
                            audioVideoButtonContainer.setVisibility(GONE);
                            expandStickersButton.setVisibility(VISIBLE);
                            runningAnimation = null;
                            runningAnimationType = 0;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            runningAnimation = null;
                        }
                    }
                });
                runningAnimation.start();
            } else {
                sendButton.setScaleX(0.1f);
                sendButton.setScaleY(0.1f);
                sendButton.setAlpha(0.0f);
                cancelBotButton.setScaleX(0.1f);
                cancelBotButton.setScaleY(0.1f);
                cancelBotButton.setAlpha(0.0f);
                audioVideoButtonContainer.setScaleX(0.1f);
                audioVideoButtonContainer.setScaleY(0.1f);
                audioVideoButtonContainer.setAlpha(0.0f);
                expandStickersButton.setScaleX(1.0f);
                expandStickersButton.setScaleY(1.0f);
                expandStickersButton.setAlpha(1.0f);
                cancelBotButton.setVisibility(GONE);
                sendButton.setVisibility(GONE);
                audioVideoButtonContainer.setVisibility(GONE);
                expandStickersButton.setVisibility(VISIBLE);
                if (attachLayout != null) {
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                    attachLayout.setVisibility(VISIBLE);
                    updateFieldRight(1);
                }
            }
            /*expandStickersButton.setAlpha(1f);
            expandStickersButton.setScaleX(1);
            expandStickersButton.setScaleY(1);
            expandStickersButton.setVisibility(VISIBLE);
            audioVideoButtonContainer.setVisibility(GONE);*/
        } else if (sendButton.getVisibility() == VISIBLE || cancelBotButton.getVisibility() == VISIBLE || expandStickersButton.getVisibility() == VISIBLE) {
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
                    runningAnimation2.playTogether(
                            ObjectAnimator.ofFloat(attachLayout, "alpha", 1.0f),
                            ObjectAnimator.ofFloat(attachLayout, "scaleX", 1.0f)
                    );
                    runningAnimation2.setDuration(100);
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
                animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "scaleX", 1.0f));
                animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "scaleY", 1.0f));
                animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "alpha", 1.0f));
                if (cancelBotButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "alpha", 0.0f));
                } else if (expandStickersButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(expandStickersButton, "alpha", 0.0f));
                } else {
                    animators.add(ObjectAnimator.ofFloat(sendButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, "alpha", 0.0f));
                }

                runningAnimation.playTogether(animators);
                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            sendButton.setVisibility(GONE);
                            cancelBotButton.setVisibility(GONE);
                            audioVideoButtonContainer.setVisibility(VISIBLE);
                            runningAnimation = null;
                            runningAnimationType = 0;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            runningAnimation = null;
                        }
                    }
                });
                runningAnimation.start();
            } else {
                sendButton.setScaleX(0.1f);
                sendButton.setScaleY(0.1f);
                sendButton.setAlpha(0.0f);
                cancelBotButton.setScaleX(0.1f);
                cancelBotButton.setScaleY(0.1f);
                cancelBotButton.setAlpha(0.0f);
                expandStickersButton.setScaleX(0.1f);
                expandStickersButton.setScaleY(0.1f);
                expandStickersButton.setAlpha(0.0f);
                audioVideoButtonContainer.setScaleX(1.0f);
                audioVideoButtonContainer.setScaleY(1.0f);
                audioVideoButtonContainer.setAlpha(1.0f);
                cancelBotButton.setVisibility(GONE);
                sendButton.setVisibility(GONE);
                expandStickersButton.setVisibility(GONE);
                audioVideoButtonContainer.setVisibility(VISIBLE);
                if (attachLayout != null) {
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                    attachLayout.setVisibility(VISIBLE);
                    updateFieldRight(1);
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
            if (botButton != null && botButton.getVisibility() == VISIBLE || notifyButton != null && notifyButton.getVisibility() == VISIBLE) {
                layoutParams.rightMargin = AndroidUtilities.dp(98);
            } else {
                layoutParams.rightMargin = AndroidUtilities.dp(50);
            }
        } else if (attachVisible == 2) {
            if (layoutParams.rightMargin != AndroidUtilities.dp(2)) {
                if (botButton != null && botButton.getVisibility() == VISIBLE || notifyButton != null && notifyButton.getVisibility() == VISIBLE) {
                    layoutParams.rightMargin = AndroidUtilities.dp(98);
                } else {
                    layoutParams.rightMargin = AndroidUtilities.dp(50);
                }
            }
        } else {
            layoutParams.rightMargin = AndroidUtilities.dp(2);
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
                    wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "audio record lock");
                    wakeLock.acquire();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.lockOrientation(parentActivity);

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
            runningAnimationAudio.playTogether(ObjectAnimator.ofFloat(recordPanel, "translationX", 0),
                    ObjectAnimator.ofFloat(recordCircle, "scale", 1),
                    ObjectAnimator.ofFloat(audioVideoButtonContainer, "alpha", 0));
            runningAnimationAudio.setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (runningAnimationAudio != null && runningAnimationAudio.equals(animator)) {
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
            runningAnimationAudio.playTogether(ObjectAnimator.ofFloat(recordPanel, "translationX", AndroidUtilities.displaySize.x),
                    ObjectAnimator.ofFloat(recordCircle, "scale", 0.0f),
                    ObjectAnimator.ofFloat(audioVideoButtonContainer, "alpha", 1.0f));
            runningAnimationAudio.setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (runningAnimationAudio != null && runningAnimationAudio.equals(animator)) {
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
            TLRPC.User user = messageObject != null && (int) dialog_id < 0 ? MessagesController.getInstance().getUser(messageObject.messageOwner.from_id) : null;
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
            TLRPC.User user = messageObject != null && (int) dialog_id < 0 ? MessagesController.getInstance().getUser(messageObject.messageOwner.from_id) : null;
            if ((botCount != 1 || username) && user != null && user.bot && !command.contains("@")) {
                SendMessagesHelper.getInstance().sendMessage(String.format(Locale.US, "%s@%s", command, user.username), dialog_id, replyingMessageObject, null, false, null, null, null);
            } else {
                SendMessagesHelper.getInstance().sendMessage(command, dialog_id, replyingMessageObject, null, false, null, null, null);
            }
        }
    }

    public void setEditingMessageObject(MessageObject messageObject, boolean caption) {
        if (audioToSend != null || videoToSendMessageObject != null || editingMessageObject == messageObject) {
            return;
        }
        if (editingMessageReqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(editingMessageReqId, true);
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
            if (caption) {
                inputFilters[0] = new InputFilter.LengthFilter(200);
                if (editingMessageObject.caption != null) {
                    setFieldText(Emoji.replaceEmoji(new SpannableStringBuilder(editingMessageObject.caption.toString()), messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false));
                } else {
                    setFieldText("");
                }
            } else {
                inputFilters[0] = new InputFilter.LengthFilter(4096);
                if (editingMessageObject.messageText != null) {
                    ArrayList<TLRPC.MessageEntity> entities = editingMessageObject.messageOwner.entities;//MessagesQuery.getEntities(message);
                    MessagesQuery.sortEntities(entities);
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(editingMessageObject.messageText);
                    Object spansToRemove[] = stringBuilder.getSpans(0, stringBuilder.length(), Object.class);
                    if (spansToRemove != null && spansToRemove.length > 0) {
                        for (int a = 0; a < spansToRemove.length; a++) {
                            stringBuilder.removeSpan(spansToRemove[a]);
                        }
                    }
                    if (entities != null) {
                        int addToOffset = 0;
                        try {
                            for (int a = 0; a < entities.size(); a++) {
                                TLRPC.MessageEntity entity = entities.get(a);
                                if (entity.offset + entity.length + addToOffset > stringBuilder.length()) {
                                    continue;
                                }
                                if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                                    if (entity.offset + entity.length + addToOffset < stringBuilder.length() && stringBuilder.charAt(entity.offset + entity.length + addToOffset) == ' ') {
                                        entity.length++;
                                    }
                                    stringBuilder.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id, true), entity.offset + addToOffset, entity.offset + entity.length + addToOffset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } else if (entity instanceof TLRPC.TL_messageEntityCode) {
                                    stringBuilder.insert(entity.offset + entity.length + addToOffset, "`");
                                    stringBuilder.insert(entity.offset + addToOffset, "`");
                                    addToOffset += 2;
                                } else if (entity instanceof TLRPC.TL_messageEntityPre) {
                                    stringBuilder.insert(entity.offset + entity.length + addToOffset, "```");
                                    stringBuilder.insert(entity.offset + addToOffset, "```");
                                    addToOffset += 6;
                                } else if (entity instanceof TLRPC.TL_messageEntityBold) {
                                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), entity.offset + addToOffset, entity.offset + entity.length + addToOffset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")), entity.offset + addToOffset, entity.offset + entity.length + addToOffset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    setFieldText(Emoji.replaceEmoji(stringBuilder, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false));
                } else {
                    setFieldText("");
                }
            }
            messageEditText.setFilters(inputFilters);
            openKeyboard();
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messageEditText.getLayoutParams();
            layoutParams.rightMargin = AndroidUtilities.dp(4);
            messageEditText.setLayoutParams(layoutParams);
            sendButton.setVisibility(GONE);
            cancelBotButton.setVisibility(GONE);
            audioVideoButtonContainer.setVisibility(GONE);
            attachLayout.setVisibility(GONE);
            sendButtonContainer.setVisibility(GONE);
        } else {
            doneButtonContainer.setVisibility(View.GONE);
            messageEditText.setFilters(new InputFilter[0]);
            delegate.onMessageEditEnd(false);
            audioVideoButtonContainer.setVisibility(VISIBLE);
            attachLayout.setVisibility(VISIBLE);
            sendButtonContainer.setVisibility(VISIBLE);
            attachLayout.setScaleX(1.0f);
            attachLayout.setAlpha(1.0f);
            sendButton.setScaleX(0.1f);
            sendButton.setScaleY(0.1f);
            sendButton.setAlpha(0.0f);
            cancelBotButton.setScaleX(0.1f);
            cancelBotButton.setScaleY(0.1f);
            cancelBotButton.setAlpha(0.0f);
            audioVideoButtonContainer.setScaleX(1.0f);
            audioVideoButtonContainer.setScaleY(1.0f);
            audioVideoButtonContainer.setAlpha(1.0f);
            sendButton.setVisibility(GONE);
            cancelBotButton.setVisibility(GONE);
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

    public ImageView getBotButton() {
        return botButton;
    }

    public ImageView getEmojiButton() {
        return emojiButton;
    }

    public ImageView getSendButton() {
        return sendButton;
    }

    public EmojiView getEmojiView() {
        return emojiView;
    }

    public void setFieldText(CharSequence text) {
        if (messageEditText == null) {
            return;
        }
        ignoreTextChange = true;
        messageEditText.setText(text);
        messageEditText.setSelection(messageEditText.getText().length());
        ignoreTextChange = false;
        if (delegate != null) {
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
        if (messageEditText != null) {
            try {
                messageEditText.requestFocus();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void setFieldFocused(boolean focus) {
        if (messageEditText == null) {
            return;
        }
        if (focus) {
            if (!messageEditText.isFocused()) {
                messageEditText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (messageEditText != null) {
                            try {
                                messageEditText.requestFocus();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }, 600);
            }
        } else {
            if (messageEditText.isFocused() && !keyboardVisible) {
                messageEditText.clearFocus();
            }
        }
    }

    public boolean hasText() {
        return messageEditText != null && messageEditText.length() > 0;
    }

    public CharSequence getFieldText() {
        if (messageEditText != null && messageEditText.length() > 0) {
            return messageEditText.getText();
        }
        return null;
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
                    botButton.setImageResource(R.drawable.ic_msg_panel_kb);
                } else {
                    botButton.setImageResource(R.drawable.bot_keyboard2);
                }
            } else {
                botButton.setImageResource(R.drawable.bot_keyboard);
            }
        } else {
            botButton.setVisibility(GONE);
        }
        updateFieldRight(2);
        attachLayout.setPivotX(AndroidUtilities.dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
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
            botKeyboardView.setDelegate(new BotKeyboardView.BotKeyboardViewDelegate() {
                @Override
                public void didPressedButton(TLRPC.KeyboardButton button) {
                    MessageObject object = replyingMessageObject != null ? replyingMessageObject : ((int) dialog_id < 0 ? botButtonsMessageObject : null);
                    didPressedBotButton(button, object, replyingMessageObject != null ? replyingMessageObject : botButtonsMessageObject);
                    if (replyingMessageObject != null) {
                        openKeyboardInternal();
                        setButtons(botMessageObject, false);
                    } else if (botButtonsMessageObject.messageOwner.reply_markup.single_use) {
                        openKeyboardInternal();
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        preferences.edit().putInt("answered_" + dialog_id, botButtonsMessageObject.getId()).commit();
                    }
                    if (delegate != null) {
                        delegate.onMessageSend(null);
                    }
                }
            });
            sizeNotifierLayout.addView(botKeyboardView);
        }
        botButtonsMessageObject = messageObject;
        botReplyMarkup = messageObject != null && messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyKeyboardMarkup ? (TLRPC.TL_replyKeyboardMarkup) messageObject.messageOwner.reply_markup : null;

        botKeyboardView.setPanelHeight(AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight);
        botKeyboardView.setButtons(botReplyMarkup);
        if (botReplyMarkup != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            boolean keyboardHidden = preferences.getInt("hidekeyboard_" + dialog_id, 0) == messageObject.getId();
            if (botButtonsMessageObject != replyingMessageObject && botReplyMarkup.single_use) {
                if (preferences.getInt("answered_" + dialog_id, 0) == messageObject.getId()) {
                    return;
                }
            }
            if (!keyboardHidden && messageEditText.length() == 0 && !isPopupShowing()) {
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
            SendMessagesHelper.getInstance().sendMessage(button.text, dialog_id, replyMessageObject, null, false, null, null, null);
        } else if (button instanceof TLRPC.TL_keyboardButtonUrl) {
            parentFragment.showOpenUrlAlert(button.url, true);
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestPhone) {
            parentFragment.shareMyContact(messageObject);
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setTitle(LocaleController.getString("ShareYouLocationTitle", R.string.ShareYouLocationTitle));
            builder.setMessage(LocaleController.getString("ShareYouLocationInfo", R.string.ShareYouLocationInfo));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        parentActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                        pendingMessageObject = messageObject;
                        pendingLocationButton = button;
                        return;
                    }
                    SendMessagesHelper.getInstance().sendCurrentLocation(messageObject, button);
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            parentFragment.showDialog(builder.create());
        } else if (button instanceof TLRPC.TL_keyboardButtonCallback || button instanceof TLRPC.TL_keyboardButtonGame || button instanceof TLRPC.TL_keyboardButtonBuy) {
            SendMessagesHelper.getInstance().sendCallback(true, messageObject, button, parentFragment);
        } else if (button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
            if (parentFragment.processSwitchButton((TLRPC.TL_keyboardButtonSwitchInline) button)) {
                return;
            }
            if (button.same_peer) {
                int uid = messageObject.messageOwner.from_id;
                if (messageObject.messageOwner.via_bot_id != 0) {
                    uid = messageObject.messageOwner.via_bot_id;
                }
                TLRPC.User user = MessagesController.getInstance().getUser(uid);
                if (user == null) {
                    return;
                }
                setFieldText("@" + user.username + " " + button.query);
            } else {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", 1);
                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                    @Override
                    public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
                        int uid = messageObject.messageOwner.from_id;
                        if (messageObject.messageOwner.via_bot_id != 0) {
                            uid = messageObject.messageOwner.via_bot_id;
                        }
                        TLRPC.User user = MessagesController.getInstance().getUser(uid);
                        if (user == null) {
                            fragment.finishFragment();
                            return;
                        }
                        long did = dids.get(0);
                        DraftQuery.saveDraft(did, "@" + user.username + " " + button.query, null, null, true);
                        if (did != dialog_id) {
                            int lower_part = (int) did;
                            if (lower_part != 0) {
                                Bundle args = new Bundle();
                                if (lower_part > 0) {
                                    args.putInt("user_id", lower_part);
                                } else if (lower_part < 0) {
                                    args.putInt("chat_id", -lower_part);
                                }
                                if (!MessagesController.checkCanOpenChat(args, fragment)) {
                                    return;
                                }
                                ChatActivity chatActivity = new ChatActivity(args);
                                if (parentFragment.presentFragment(chatActivity, true)) {
                                    if (!AndroidUtilities.isTablet()) {
                                        parentFragment.removeSelfFromStack();
                                    }
                                } else {
                                    fragment.finishFragment();
                                }
                            } else {
                                fragment.finishFragment();
                            }
                        } else {
                            fragment.finishFragment();
                        }
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
        emojiView = new EmojiView(allowStickers, allowGifs, parentActivity, info);
        emojiView.setVisibility(GONE);
        emojiView.setListener(new EmojiView.Listener() {
            public boolean onBackspace() {
                if (messageEditText.length() == 0) {
                    return false;
                }
                messageEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                return true;
            }

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

            public void onStickerSelected(TLRPC.Document sticker) {
                if (stickersExpanded) {
                    setStickersExpanded(false, true);
                }
                ChatActivityEnterView.this.onStickerSelected(sticker);
                StickersQuery.addRecentSticker(StickersQuery.TYPE_IMAGE, sticker, (int) (System.currentTimeMillis() / 1000), false);
                if ((int) dialog_id == 0) {
                    MessagesController.getInstance().saveGif(sticker);
                }
            }

            @Override
            public void onStickersSettingsClick() {
                if (parentFragment != null) {
                    parentFragment.presentFragment(new StickersActivity(StickersQuery.TYPE_IMAGE));
                }
            }

            @Override
            public void onGifSelected(TLRPC.Document gif) {
                if (stickersExpanded) {
                    setStickersExpanded(false, true);
                }
                SendMessagesHelper.getInstance().sendSticker(gif, dialog_id, replyingMessageObject);
                StickersQuery.addRecentGif(gif, (int) (System.currentTimeMillis() / 1000));
                if ((int) dialog_id == 0) {
                    MessagesController.getInstance().saveGif(gif);
                }
                if (delegate != null) {
                    delegate.onMessageSend(null);
                }
            }

            @Override
            public void onGifTab(boolean opened) {
                post(updateExpandabilityRunnable);
                if (!AndroidUtilities.usingHardwareInput) {
                    if (opened) {
                        if (messageEditText.length() == 0) {
                            messageEditText.setText("@gif ");
                            messageEditText.setSelection(messageEditText.length());
                        }
                    } else if (messageEditText.getText().toString().equals("@gif ")) {
                        messageEditText.setText("");
                    }
                }
            }

            @Override
            public void onStickersTab(boolean opened) {
                delegate.onStickersTab(opened);
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
                builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        emojiView.clearRecentEmoji();
                    }
                });
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
                StickersQuery.removeStickersSet(parentActivity, stickerSet.set, 2, parentFragment, false);
            }

            @Override
            public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
                StickersQuery.removeStickersSet(parentActivity, stickerSet.set, 0, parentFragment, false);
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
        });
        emojiView.setDragListener(new EmojiView.DragListener() {

            boolean wasExpanded;
            int initialOffset;

            @Override
            public void onDragStart() {
                if (!allowDragging()) {
                    return;
                }
                if (stickersExpansionAnim != null)
                    stickersExpansionAnim.cancel();
                stickersDragging = true;
                wasExpanded = stickersExpanded;
                stickersExpanded = true;
                stickersExpandedHeight = sizeNotifierLayout.getHeight() - (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0) - ActionBar.getCurrentActionBarHeight() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                emojiView.getLayoutParams().height = stickersExpandedHeight;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                sizeNotifierLayout.requestLayout();
                ((FrameLayout) sizeNotifierLayout).setForeground(new ScrimDrawable());
                initialOffset = (int) getTranslationY();
            }

            @Override
            public void onDragEnd(float velocity) {
                if (!allowDragging())
                    return;
                stickersDragging = false;
                if ((wasExpanded && velocity >= AndroidUtilities.dp(200)) || (!wasExpanded && velocity <= AndroidUtilities.dp(-200)) || (wasExpanded && stickersExpansionProgress <= 0.6f) || (!wasExpanded && stickersExpansionProgress >= 0.4f)) {
                    setStickersExpanded(!wasExpanded, true);
                } else {
                    setStickersExpanded(wasExpanded, true);
                }
            }

            @Override
            public void onDragCancel() {
                if (!stickersTabOpen) {
                    return;
                }
                stickersDragging = false;
                setStickersExpanded(wasExpanded, true);
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

            private boolean allowDragging(){
                return stickersTabOpen && !(!stickersExpanded && messageEditText.length()>0) && emojiView.areThereAnyStickers();
            }
        });
        emojiView.setVisibility(GONE);
        sizeNotifierLayout.addView(emojiView);
        checkChannelRights();
    }

    @Override
    public void onStickerSelected(TLRPC.Document sticker) {
        SendMessagesHelper.getInstance().sendSticker(sticker, dialog_id, replyingMessageObject);
        if (delegate != null) {
            delegate.onMessageSend(null);
        }
    }

    public void addStickerToRecent(TLRPC.Document sticker) {
        createEmojiView();
        emojiView.addRecentSticker(sticker);
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
                emojiView.setVisibility(VISIBLE);
                if (botKeyboardView != null && botKeyboardView.getVisibility() != GONE) {
                    botKeyboardView.setVisibility(GONE);
                }
                currentView = emojiView;
            } else if (contentType == 1) {
                if (emojiView != null && emojiView.getVisibility() != GONE) {
                    emojiView.setVisibility(GONE);
                }
                botKeyboardView.setVisibility(VISIBLE);
                currentView = botKeyboardView;
            }
            currentPopupContentType = contentType;

            if (keyboardHeight <= 0) {
                keyboardHeight = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE).getInt("kbd_height", AndroidUtilities.dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE).getInt("kbd_height_land3", AndroidUtilities.dp(200));
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
                if (contentType == 0) {
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_kb);
                } else if (contentType == 1) {
                    setEmojiButtonImage();
                }
                updateBotButton();
                onWindowSizeChanged();
            }
        } else {
            if (emojiButton != null) {
                setEmojiButtonImage();
            }
            currentPopupContentType = -1;
            if (emojiView != null) {
                emojiView.setVisibility(GONE);
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

        if (stickersTabOpen) {
            checkSendButton(true);
        }
        if (stickersExpanded && show != 1) {
            setStickersExpanded(false, false);
        }
    }

    private void setEmojiButtonImage() {
        int currentPage;
        if (emojiView == null) {
            currentPage = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE).getInt("selected_page", 0);
        } else {
            currentPage = emojiView.getCurrentPage();
        }
        if (currentPage == 0 || !allowStickers && !allowGifs) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        } else if (currentPage == 1) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_stickers);
        } else if (currentPage == 2) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_gif);
        }
    }

    public void hidePopup(boolean byBackButton) {
        if (isPopupShowing()) {
            if (currentPopupContentType == 1 && byBackButton && botButtonsMessageObject != null) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                preferences.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
            }
            showPopup(0, 0);
            removeGifFromInputField();
        }
    }

    private void removeGifFromInputField() {
        if (!AndroidUtilities.usingHardwareInput) {
            if (messageEditText.getText().toString().equals("@gif ")) {
                messageEditText.setText("");
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
        return emojiView != null && emojiView.getVisibility() == VISIBLE || botKeyboardView != null && botKeyboardView.getVisibility() == VISIBLE;
    }

    public boolean isKeyboardVisible() {
        return keyboardVisible;
    }

    public void addRecentGif(TLRPC.Document searchImage) {
        StickersQuery.addRecentGif(searchImage, (int) (System.currentTimeMillis() / 1000));
        if (emojiView != null) {
            emojiView.addRecentGif(searchImage);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw && stickersExpanded)
            setStickersExpanded(false, false);
        videoTimelineView.clearFrames();
    }

    @Override
    public void onSizeChanged(int height, boolean isWidthGreater) {
        if (height > AndroidUtilities.dp(50) && keyboardVisible && !AndroidUtilities.isInMultiwindow) {
            if (isWidthGreater) {
                keyboardHeightLand = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height", keyboardHeight).commit();
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
            if ((layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) && !stickersExpanded) {
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
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
            if (botKeyboardView != null) {
                botKeyboardView.invalidateViews();
            }
        } else if (id == NotificationCenter.recordProgressChanged) {
            long t = (Long) args[0];
            long time = t / 1000;
            int ms = (int) (t % 1000L) / 10;
            String str = String.format("%02d:%02d.%02d", time / 60, time % 60, ms);
            if (lastTimeString == null || !lastTimeString.equals(str)) {
                if (lastTypingSendTime != time && time % 5 == 0) {
                    lastTypingSendTime = time;
                    MessagesController.getInstance().sendTyping(dialog_id, videoSendButton != null && videoSendButton.getTag() != null ? 7 : 1, 0);
                }
                if (recordTimeText != null) {
                    recordTimeText.setText(str);
                }
            }
            if (recordCircle != null) {
                recordCircle.setAmplitude((Double) args[1]);
            }
            if (videoSendButton != null && videoSendButton.getTag() != null && t >= 59500) {
                startedDraggingX = -1;
                delegate.needStartRecordVideo(3);
            }
        } else if (id == NotificationCenter.closeChats) {
            if (messageEditText != null && messageEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(messageEditText);
            }
        } else if (id == NotificationCenter.recordStartError || id == NotificationCenter.recordStopped) {
            if (recordingAudioVideo) {
                MessagesController.getInstance().sendTyping(dialog_id, 2, 0);
                recordingAudioVideo = false;
                updateRecordIntefrace();
            }
            if (id == NotificationCenter.recordStopped) {
                Integer reason = (Integer) args[0];
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
            if (!recordingAudioVideo) {
                recordingAudioVideo = true;
                updateRecordIntefrace();
            }
        } else if (id == NotificationCenter.audioDidSent) {
            Object audio = args[0];
            if (audio instanceof VideoEditedInfo) {
                videoToSendMessageObject = (VideoEditedInfo) audio;

                audioToSendPath = (String) args[1];

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
                audioToSend = (TLRPC.TL_document) args[0];
                audioToSendPath = (String) args[1];
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
                    message.to_id.user_id = message.from_id = UserConfig.getClientUserId();
                    message.date = (int) (System.currentTimeMillis() / 1000);
                    message.message = "-1";
                    message.attachPath = audioToSendPath;
                    message.media = new TLRPC.TL_messageMediaDocument();
                    message.media.flags |= 3;
                    message.media.document = audioToSend;
                    message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    audioToSendMessageObject = new MessageObject(message, null, false);

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
                        delegate.onMessageSend(null);
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
        } else if (id == NotificationCenter.featuredStickersDidLoaded) {
            if (emojiButton != null) {
                emojiButton.invalidate();
            }
        }
    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 2) {
            if (pendingLocationButton != null) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    SendMessagesHelper.getInstance().sendCurrentLocation(pendingMessageObject, pendingLocationButton);
                }
                pendingLocationButton = null;
                pendingMessageObject = null;
            }
        }
    }

    private void setStickersExpanded(boolean expanded, boolean animated) {
        if (emojiView == null || expanded && !emojiView.areThereAnyStickers()) {
            return;
        }
        stickersExpanded = expanded;
        final int origHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
        if (stickersExpansionAnim != null) {
            stickersExpansionAnim.cancel();
            stickersExpansionAnim = null;
        }
        if (stickersExpanded) {
            stickersExpandedHeight = sizeNotifierLayout.getHeight() - (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0) - ActionBar.getCurrentActionBarHeight() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
            emojiView.getLayoutParams().height = stickersExpandedHeight;
            sizeNotifierLayout.requestLayout();
            ((FrameLayout) sizeNotifierLayout).setForeground(new ScrimDrawable());
            messageEditText.setText(messageEditText.getText()); // dismiss action mode, if any
            if (animated) {
                AnimatorSet anims = new AnimatorSet();
                anims.playTogether(
                        ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                        ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                        ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 1)
                );
                anims.setDuration(400);
                anims.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        stickersExpansionProgress = getTranslationY() / (-(stickersExpandedHeight - origHeight));
                        sizeNotifierLayout.invalidate();
                    }
                });
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
            } else {
                stickersExpansionProgress = 1;
                setTranslationY(-(stickersExpandedHeight - origHeight));
                emojiView.setTranslationY(-(stickersExpandedHeight - origHeight));
                stickersArrow.setAnimationProgress(1);
            }
        } else {
            if (animated) {
                AnimatorSet anims = new AnimatorSet();
                anims.playTogether(
                        ObjectAnimator.ofInt(this, roundedTranslationYProperty, 0),
                        ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, 0),
                        ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 0)
                );
                anims.setDuration(400);
                anims.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        stickersExpansionProgress = getTranslationY() / (-(stickersExpandedHeight - origHeight));
                        sizeNotifierLayout.invalidate();
                    }
                });
                anims.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        stickersExpansionAnim = null;
                        emojiView.getLayoutParams().height = origHeight;
                        sizeNotifierLayout.requestLayout();
                        emojiView.setLayerType(LAYER_TYPE_NONE, null);
                        ((FrameLayout) sizeNotifierLayout).setForeground(null);
                        sizeNotifierLayout.setWillNotDraw(false);
                    }
                });
                stickersExpansionAnim = anims;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                anims.start();
            } else {
                stickersExpansionProgress = 0;
                setTranslationY(0);
                emojiView.setTranslationY(0);
                emojiView.getLayoutParams().height = origHeight;
                sizeNotifierLayout.requestLayout();
                ((FrameLayout) sizeNotifierLayout).setForeground(null);
                sizeNotifierLayout.setWillNotDraw(false);
                stickersArrow.setAnimationProgress(0);
            }
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
            return 0;
        }
    }

    private class AnimatedArrowDrawable extends Drawable {

        private Paint paint;
        private Path path = new Path();
        private float animProgress = 0;

        public AnimatedArrowDrawable(int color) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setColor(color);

            updatePath();
        }

        @Override
        public void draw(Canvas c) {
            c.drawPath(path, paint);
        }

        private void updatePath() {
            path.reset();
            float p = animProgress * 2 - 1;
            path.moveTo(AndroidUtilities.dp(3), AndroidUtilities.dp(12) - AndroidUtilities.dp(4) * p);
            path.lineTo(AndroidUtilities.dp(13), AndroidUtilities.dp(12) + AndroidUtilities.dp(4) * p);
            path.lineTo(AndroidUtilities.dp(23), AndroidUtilities.dp(12) - AndroidUtilities.dp(4) * p);
        }

        public void setAnimationProgress(float progress) {
            animProgress = progress;
            updatePath();
            invalidateSelf();
        }

        public float getAnimationProgress() {
            return animProgress;
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return 0;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(26);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(26);
        }
    }
}
