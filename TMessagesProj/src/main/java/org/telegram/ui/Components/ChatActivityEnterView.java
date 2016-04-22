/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
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
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.messenger.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.StickersActivity;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Locale;

public class ChatActivityEnterView extends FrameLayoutFixed implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate, StickersAlert.StickersAlertDelegate {

    public interface ChatActivityEnterViewDelegate {
        void onMessageSend(String message);
        void needSendTyping();
        void onTextChanged(CharSequence text, boolean bigChange);
        void onAttachButtonHidden();
        void onAttachButtonShow();
        void onWindowSizeChanged(int size);
        void onStickersTab(boolean opened);
        void onMessageEditEnd();
    }

    private class SeekBarWaveformView extends View {

        private SeekBarWaveform seekBarWaveform;

        public SeekBarWaveformView(Context context) {
            super(context);
            seekBarWaveform = new SeekBarWaveform(context);
            seekBarWaveform.setColors(0xffa2cef8, 0xffffffff, 0xffa2cef8);
            seekBarWaveform.setDelegate(new SeekBar.SeekBarDelegate() {
                @Override
                public void onSeekBarDrag(float progress) {
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
            seekBarWaveform.draw(canvas);
        }
    }

    private class EditTextCaption extends EditText {

        private String caption;
        private StaticLayout captionLayout;
        private int userNameLength;
        private int xOffset;
        private int yOffset;
        private Object editor;
        private Field editorField;
        private Drawable[] mCursorDrawable;
        private Field mCursorDrawableField;
        private int triesCount = 0;

        public EditTextCaption(Context context) {
            super(context);

            try {
                Field field = TextView.class.getDeclaredField("mEditor");
                field.setAccessible(true);
                editor = field.get(this);
                Class editorClass = Class.forName("android.widget.Editor");
                editorField = editorClass.getDeclaredField("mShowCursor");
                editorField.setAccessible(true);
                mCursorDrawableField = editorClass.getDeclaredField("mCursorDrawable");
                mCursorDrawableField.setAccessible(true);
                mCursorDrawable = (Drawable[]) mCursorDrawableField.get(editor);
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }
        }

        public void setCaption(String value) {
            if ((caption == null || caption.length() == 0) && (value == null || value.length() == 0) || caption != null && value != null && caption.equals(value)) {
                return;
            }
            caption = value;
            if (caption != null) {
                caption = caption.replace('\n', ' ');
            }
            requestLayout();
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            captionLayout = null;

            if (caption != null && caption.length() > 0) {
                CharSequence text = getText();
                if (text.length() > 1 && text.charAt(0) == '@') {
                    int index = TextUtils.indexOf(text, ' ');
                    if (index != -1) {
                        TextPaint paint = getPaint();
                        CharSequence str = text.subSequence(0, index + 1);
                        int size = (int) Math.ceil(paint.measureText(text, 0, index + 1));
                        int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                        userNameLength = str.length();
                        CharSequence captionFinal = TextUtils.ellipsize(caption, paint, width - size, TextUtils.TruncateAt.END);
                        xOffset = size;
                        try {
                            captionLayout = new StaticLayout(captionFinal, getPaint(), width - size, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            if (captionLayout.getLineCount() > 0) {
                                xOffset += -captionLayout.getLineLeft(0);
                            }
                            yOffset = (getMeasuredHeight() - captionLayout.getLineBottom(0)) / 2 + AndroidUtilities.dp(0.5f);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            try {
                super.onDraw(canvas);
                if (captionLayout != null && userNameLength == length()) {
                    Paint paint = getPaint();
                    int oldColor = getPaint().getColor();
                    paint.setColor(0xffb2b2b2);
                    canvas.save();
                    canvas.translate(xOffset, yOffset);
                    captionLayout.draw(canvas);
                    canvas.restore();
                    paint.setColor(oldColor);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            try {
                if (editorField != null && mCursorDrawable != null && mCursorDrawable[0] != null) {
                    long mShowCursor = editorField.getLong(editor);
                    boolean showCursor = (SystemClock.uptimeMillis() - mShowCursor) % (2 * 500) < 500;
                    if (showCursor) {
                        canvas.save();
                        canvas.translate(0, getPaddingTop());
                        mCursorDrawable[0].draw(canvas);
                        canvas.restore();
                    }
                }
            } catch (Throwable e) {
                //ignore
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (isPopupShowing() && event.getAction() == MotionEvent.ACTION_DOWN) {
                showPopup(AndroidUtilities.usingHardwareInput ? 0 : 2, 0);
                openKeyboardInternal();
            }
            return super.onTouchEvent(event);
        }
    }

    private EditTextCaption messageEditText;
    private ImageView sendButton;
    private ImageView cancelBotButton;
    private ImageView emojiButton;
    private EmojiView emojiView;
    private TextView recordTimeText;
    private ImageView audioSendButton;
    private FrameLayout recordPanel;
    private FrameLayout recordedAudioPanel;
    private SeekBarWaveformView recordedAudioSeekBar;
    private ImageView recordedAudioPlayButton;
    private TextView recordedAudioTimeTextView;
    private LinearLayout slideText;
    private RecordDot recordDot;
    private SizeNotifierFrameLayout sizeNotifierLayout;
    private LinearLayout attachButton;
    private ImageView botButton;
    private LinearLayout textFieldContainer;
    private FrameLayout sendButtonContainer;
    private View topView;
    private PopupWindow botKeyboardPopup;
    private BotKeyboardView botKeyboardView;
    private ImageView asAdminButton;
    private ImageView notifyButton;
    private RecordCircle recordCircle;
    private ContextProgressView contextProgressView;
    private CloseProgressDrawable2 progressDrawable;

    private MessageObject editingMessageObject;
    private boolean editingCaption;

    private int currentPopupContentType = -1;

    private boolean silent;
    private boolean canWriteToChannel;

    private boolean isAsAdmin;
    private boolean adminModeAvailable;

    private boolean isPaused;
    private boolean showKeyboardOnResume;

    private MessageObject botButtonsMessageObject;
    private TLRPC.TL_replyKeyboardMarkup botReplyMarkup;
    private int botCount;
    private boolean hasBotCommands;

    private PowerManager.WakeLock mWakeLock;
    private AnimatorSetProxy runningAnimation;
    private AnimatorSetProxy runningAnimation2;
    private AnimatorSetProxy runningAnimationAudio;
    private int runningAnimationType;
    private int audioInterfaceState;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private int emojiPadding;
    private boolean sendByEnter;
    private long lastTypingTimeSend;
    private String lastTimeString;
    private float startedDraggingX = -1;
    private float distCanMove = AndroidUtilities.dp(80);
    private boolean recordingAudio;
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

    private float topViewAnimation;
    private boolean topViewShowed;
    private boolean needShowTopView;
    private boolean allowShowTopView;
    private AnimatorSetProxy currentTopViewAnimation;

    private MessageObject pendingMessageObject;
    private TLRPC.KeyboardButton pendingLocationButton;

    private boolean waitingForKeyboardOpen;
    private Runnable openKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            if (messageEditText != null && waitingForKeyboardOpen && !keyboardVisible && !AndroidUtilities.usingHardwareInput) {
                messageEditText.requestFocus();
                AndroidUtilities.showKeyboard(messageEditText);
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    };

    private class RecordDot extends View {

        private Drawable dotDrawable;
        private float alpha;
        private long lastUpdateTime;
        private boolean isIncr;

        public RecordDot(Context context) {
            super(context);

            dotDrawable = getResources().getDrawable(R.drawable.rec);
        }

        public void resetAlpha() {
            alpha = 1.0f;
            lastUpdateTime = System.currentTimeMillis();
            isIncr = false;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            dotDrawable.setBounds(0, 0, AndroidUtilities.dp(11), AndroidUtilities.dp(11));
            dotDrawable.setAlpha((int) (255 * alpha));
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
            dotDrawable.draw(canvas);
            invalidate();
        }
    }

    private class RecordCircle extends View {

        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint paintRecord = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Drawable micDrawable;
        private float scale;
        private float amplitude;
        private float animateToAmplitude;
        private float animateAmplitudeDiff;
        private long lastUpdateTime;

        public RecordCircle(Context context) {
            super(context);
            paint.setColor(0xff5795cc);
            paintRecord.setColor(0x0d000000);
            micDrawable = getResources().getDrawable(R.drawable.mic_pressed);
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

        @Override
        protected void onDraw(Canvas canvas) {
            int cx = getMeasuredWidth() / 2;
            int cy = getMeasuredHeight() / 2;
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
                canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, (AndroidUtilities.dp(42) + AndroidUtilities.dp(20) * amplitude) * scale, paintRecord);
            }
            canvas.drawCircle(getMeasuredWidth() / 2.0f, getMeasuredHeight() / 2.0f, AndroidUtilities.dp(42) * sc, paint);
            micDrawable.setBounds(cx - micDrawable.getIntrinsicWidth() / 2, cy - micDrawable.getIntrinsicHeight() / 2, cx + micDrawable.getIntrinsicWidth() / 2, cy + micDrawable.getIntrinsicHeight() / 2);
            micDrawable.setAlpha((int) (255 * alpha));
            micDrawable.draw(canvas);
        }
    }

    public ChatActivityEnterView(Activity context, SizeNotifierFrameLayout parent, ChatActivity fragment, boolean isChat) {
        super(context);
        setBackgroundResource(R.drawable.compose_panel);
        setFocusable(true);
        setFocusableInTouchMode(true);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioRouteChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioProgressDidChanged);
        parentActivity = context;
        parentFragment = fragment;
        sizeNotifierLayout = parent;
        sizeNotifierLayout.setDelegate(this);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        sendByEnter = preferences.getBoolean("send_by_enter", false);

        textFieldContainer = new LinearLayout(context);
        textFieldContainer.setBackgroundColor(0xffffffff);
        textFieldContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 2, 0, 0));

        FrameLayoutFixed frameLayout = new FrameLayoutFixed(context);
        textFieldContainer.addView(frameLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        emojiButton = new ImageView(context);
        emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setPadding(0, AndroidUtilities.dp(1), 0, 0);
        if (Build.VERSION.SDK_INT >= 21) {
            emojiButton.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
        }
        frameLayout.addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT, 3, 0, 0, 0));
        emojiButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isPopupShowing() || currentPopupContentType != 0) {
                    showPopup(1, 0);
                } else {
                    openKeyboardInternal();
                    removeGifFromInputField();
                }
            }
        });

        messageEditText = new EditTextCaption(context);
        updateFieldHint();
        messageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messageEditText.setInputType(messageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messageEditText.setSingleLine(false);
        messageEditText.setMaxLines(4);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messageEditText.setGravity(Gravity.BOTTOM);
        messageEditText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        messageEditText.setBackgroundDrawable(null);
        messageEditText.setTextColor(0xff000000);
        messageEditText.setHintTextColor(0xffb2b2b2);
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
                } else if (i == KeyEvent.KEYCODE_ENTER && (ctrlPressed || sendByEnter) && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
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
                    if ((ctrlPressed || sendByEnter) && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
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
                String message = getTrimmedString(charSequence.toString());
                if (delegate != null) {
                    if (count > 2 || charSequence == null || charSequence.length() == 0) {
                        messageWebPageSearch = true;
                    }
                    if (!ignoreTextChange) {
                        delegate.onTextChanged(charSequence, before > count + 1 || (count - before) > 2);
                    }
                }
                if (innerTextChange != 2 && before != count && (count - before) > 1) {
                    processChange = true;
                }
                if (editingMessageObject == null && !isAsAdmin && message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
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
                if (sendByEnter && editable.length() > 0 && editable.charAt(editable.length() - 1) == '\n') {
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
        try {
            Field mCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
            mCursorDrawableRes.setAccessible(true);
            mCursorDrawableRes.set(messageEditText, R.drawable.field_carret);
        } catch (Exception e) {
            //nothing to do
        }

        if (isChat) {
            contextProgressView = new ContextProgressView(context);
            contextProgressView.setVisibility(INVISIBLE);
            frameLayout.addView(contextProgressView, LayoutHelper.createFrame(38, 48, Gravity.BOTTOM | Gravity.RIGHT));

            attachButton = new LinearLayout(context);
            attachButton.setOrientation(LinearLayout.HORIZONTAL);
            attachButton.setEnabled(false);
            ViewProxy.setPivotX(attachButton, AndroidUtilities.dp(48));
            frameLayout.addView(attachButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.BOTTOM | Gravity.RIGHT));

            botButton = new ImageView(context);
            botButton.setImageResource(R.drawable.bot_keyboard2);
            botButton.setScaleType(ImageView.ScaleType.CENTER);
            botButton.setVisibility(GONE);
            if (Build.VERSION.SDK_INT >= 21) {
                botButton.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
            }
            attachButton.addView(botButton, LayoutHelper.createLinear(48, 48));
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
                        openKeyboard();
                    }
                }
            });

            asAdminButton = new ImageView(context);
            asAdminButton.setImageResource(isAsAdmin ? R.drawable.publish_active : R.drawable.publish);
            asAdminButton.setScaleType(ImageView.ScaleType.CENTER);
            asAdminButton.setVisibility(adminModeAvailable ? VISIBLE : GONE);
            if (Build.VERSION.SDK_INT >= 21) {
                asAdminButton.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
            }
            attachButton.addView(asAdminButton, LayoutHelper.createLinear(48, 48));
            asAdminButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    isAsAdmin = !isAsAdmin;
                    asAdminButton.setImageResource(isAsAdmin ? R.drawable.publish_active : R.drawable.publish);
                    updateFieldHint();
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    preferences.edit().putBoolean("asadmin_" + dialog_id, isAsAdmin).commit();
                }
            });

            notifyButton = new ImageView(context);
            notifyButton.setImageResource(silent ? R.drawable.notify_members_off : R.drawable.notify_members_on);
            notifyButton.setScaleType(ImageView.ScaleType.CENTER);
            notifyButton.setVisibility(canWriteToChannel ? VISIBLE : GONE);
            if (Build.VERSION.SDK_INT >= 21) {
                notifyButton.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
            }
            attachButton.addView(notifyButton, LayoutHelper.createLinear(48, 48));
            notifyButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    silent = !silent;
                    notifyButton.setImageResource(silent ? R.drawable.notify_members_off : R.drawable.notify_members_on);
                    ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).edit().putBoolean("silent_" + dialog_id, silent).commit();
                    NotificationsController.updateServerNotificationsSettings(dialog_id);
                    if (silent) {
                        Toast.makeText(parentActivity, LocaleController.getString("ChannelNotifyMembersInfoOff", R.string.ChannelNotifyMembersInfoOff), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(parentActivity, LocaleController.getString("ChannelNotifyMembersInfoOn", R.string.ChannelNotifyMembersInfoOn), Toast.LENGTH_SHORT).show();
                    }
                    updateFieldHint();
                }
            });
        }

        recordedAudioPanel = new FrameLayoutFixed(context);
        recordedAudioPanel.setVisibility(audioToSend == null ? GONE : VISIBLE);
        recordedAudioPanel.setBackgroundColor(0xffffffff);
        recordedAudioPanel.setFocusable(true);
        recordedAudioPanel.setFocusableInTouchMode(true);
        recordedAudioPanel.setClickable(true);
        frameLayout.addView(recordedAudioPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.ic_ab_fwd_delete);
        recordedAudioPanel.addView(imageView, LayoutHelper.createFrame(48, 48));
        imageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
                if (playing != null && playing == audioToSendMessageObject) {
                    MediaController.getInstance().cleanupPlayer(true, true);
                }
                if (audioToSendPath != null) {
                    new File(audioToSendPath).delete();
                }
                hideRecordedAudioPanel();
                checkSendButton(true);
            }
        });

        View view = new View(context);
        view.setBackgroundResource(R.drawable.recorded);
        recordedAudioPanel.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 48, 0, 0, 0));

        recordedAudioSeekBar = new SeekBarWaveformView(context);
        recordedAudioPanel.addView(recordedAudioSeekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER_VERTICAL | Gravity.LEFT, 48 + 44, 0, 52, 0));

        recordedAudioPlayButton = new ImageView(context);
        recordedAudioPlayButton.setImageResource(R.drawable.s_player_play_states);
        recordedAudioPlayButton.setScaleType(ImageView.ScaleType.CENTER);
        recordedAudioPanel.addView(recordedAudioPlayButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.BOTTOM, 48, 0, 0, 0));
        recordedAudioPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (audioToSend == null) {
                    return;
                }
                if (MediaController.getInstance().isPlayingAudio(audioToSendMessageObject) && !MediaController.getInstance().isAudioPaused()) {
                    MediaController.getInstance().pauseAudio(audioToSendMessageObject);
                    recordedAudioPlayButton.setImageResource(R.drawable.s_player_play_states);
                } else {
                    recordedAudioPlayButton.setImageResource(R.drawable.s_player_pause_states);
                    MediaController.getInstance().playAudio(audioToSendMessageObject);
                }
            }
        });

        recordedAudioTimeTextView = new TextView(context);
        recordedAudioTimeTextView.setTextColor(0xffffffff);
        recordedAudioTimeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        recordedAudioTimeTextView.setText("0:13");
        recordedAudioPanel.addView(recordedAudioTimeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 13, 0));

        recordPanel = new FrameLayoutFixed(context);
        recordPanel.setVisibility(GONE);
        recordPanel.setBackgroundColor(0xffffffff);
        frameLayout.addView(recordPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));

        slideText = new LinearLayout(context);
        slideText.setOrientation(LinearLayout.HORIZONTAL);
        recordPanel.addView(slideText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 30, 0, 0, 0));

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.slidearrow);
        slideText.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 1, 0, 0));

        TextView textView = new TextView(context);
        textView.setText(LocaleController.getString("SlideToCancel", R.string.SlideToCancel));
        textView.setTextColor(0xff999999);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        slideText.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setPadding(AndroidUtilities.dp(13), 0, 0, 0);
        linearLayout.setBackgroundColor(0xffffffff);
        recordPanel.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        recordDot = new RecordDot(context);
        linearLayout.addView(recordDot, LayoutHelper.createLinear(11, 11, Gravity.CENTER_VERTICAL, 0, 1, 0, 0));

        recordTimeText = new TextView(context);
        recordTimeText.setText("00:00");
        recordTimeText.setTextColor(0xff4d4c4b);
        recordTimeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        linearLayout.addView(recordTimeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

        sendButtonContainer = new FrameLayout(context);
        textFieldContainer.addView(sendButtonContainer, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));

        audioSendButton = new ImageView(context);
        audioSendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        audioSendButton.setImageResource(R.drawable.mic);
        audioSendButton.setBackgroundColor(0xffffffff);
        audioSendButton.setSoundEffectsEnabled(false);
        audioSendButton.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        sendButtonContainer.addView(audioSendButton, LayoutHelper.createFrame(48, 48));
        audioSendButton.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (parentFragment != null) {
                        if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            parentActivity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                            return false;
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
                            return false;
                        }
                    }
                    startedDraggingX = -1;
                    MediaController.getInstance().startRecording(dialog_id, replyingMessageObject, asAdmin());
                    updateAudioRecordIntefrace();
                    audioSendButton.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    startedDraggingX = -1;
                    MediaController.getInstance().stopRecording(1);
                    recordingAudio = false;
                    updateAudioRecordIntefrace();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && recordingAudio) {
                    float x = motionEvent.getX();
                    if (x < -distCanMove) {
                        MediaController.getInstance().stopRecording(0);
                        recordingAudio = false;
                        updateAudioRecordIntefrace();
                    }

                    x = x + ViewProxy.getX(audioSendButton);
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
                    if (startedDraggingX != -1) {
                        float dist = (x - startedDraggingX);
                        ViewProxy.setTranslationX(recordCircle, dist);
                        params.leftMargin = AndroidUtilities.dp(30) + (int) dist;
                        slideText.setLayoutParams(params);
                        float alpha = 1.0f + dist / distCanMove;
                        if (alpha > 1) {
                            alpha = 1;
                        } else if (alpha < 0) {
                            alpha = 0;
                        }
                        ViewProxy.setAlpha(slideText, alpha);
                    }
                    if (x <= ViewProxy.getX(slideText) + slideText.getWidth() + AndroidUtilities.dp(30)) {
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
                        ViewProxy.setTranslationX(recordCircle, 0);
                        slideText.setLayoutParams(params);
                        ViewProxy.setAlpha(slideText, 1);
                        startedDraggingX = -1;
                    }
                }
                view.onTouchEvent(motionEvent);
                return true;
            }
        });

        recordCircle = new RecordCircle(context);
        recordCircle.setVisibility(GONE);
        sizeNotifierLayout.addView(recordCircle, LayoutHelper.createFrame(124, 124, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, -36, -38));

        cancelBotButton = new ImageView(context);
        cancelBotButton.setVisibility(INVISIBLE);
        cancelBotButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        //cancelBotButton.setImageResource(R.drawable.delete_reply);
        cancelBotButton.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
        cancelBotButton.setSoundEffectsEnabled(false);
        ViewProxy.setScaleX(cancelBotButton, 0.1f);
        ViewProxy.setScaleY(cancelBotButton, 0.1f);
        ViewProxy.setAlpha(cancelBotButton, 0.0f);
        cancelBotButton.clearAnimation();
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
        sendButton.setImageResource(R.drawable.ic_send);
        sendButton.setSoundEffectsEnabled(false);
        ViewProxy.setScaleX(sendButton, 0.1f);
        ViewProxy.setScaleY(sendButton, 0.1f);
        ViewProxy.setAlpha(sendButton, 0.0f);
        sendButton.clearAnimation();
        sendButtonContainer.addView(sendButton, LayoutHelper.createFrame(48, 48));
        sendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE);
        keyboardHeight = sharedPreferences.getInt("kbd_height", AndroidUtilities.dp(200));
        keyboardHeightLand = sharedPreferences.getInt("kbd_height_land3", AndroidUtilities.dp(200));

        checkSendButton(false);
    }

    public void showContextProgress(boolean show) {
        /*if (contextProgressView == null) {
            return;
        }
        contextProgressView.setVisibility(show ? VISIBLE : INVISIBLE);
        try {
            messageEditText.setPadding(0, AndroidUtilities.dp(11), show ? AndroidUtilities.dp(38) : 0, AndroidUtilities.dp(12));
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }*/
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
        addView(topView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, height, Gravity.TOP | Gravity.LEFT, 0, 2, 0, 0));
        needShowTopView = false;
    }

    public void setTopViewAnimation(float progress) {
        topViewAnimation = progress;
        LayoutParams layoutParams2 = (LayoutParams) textFieldContainer.getLayoutParams();
        layoutParams2.topMargin = AndroidUtilities.dp(2) + (int) (topView.getLayoutParams().height * progress);
        textFieldContainer.setLayoutParams(layoutParams2);
    }

    public float getTopViewAnimation() {
        return topViewAnimation;
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
    }

    public void setOpenGifsTabFirst() {
        createEmojiView();
        emojiView.loadGifRecent();
        emojiView.switchToGifRecent();
    }

    public boolean asAdmin() {
        return isAsAdmin;
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
            if (animated) {
                if (keyboardVisible || isPopupShowing()) {
                    currentTopViewAnimation = new AnimatorSetProxy();
                    currentTopViewAnimation.playTogether(
                            ObjectAnimatorProxy.ofFloat(ChatActivityEnterView.this, "topViewAnimation", 1.0f)
                    );
                    currentTopViewAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                                setTopViewAnimation(1.0f);
                                if (recordedAudioPanel.getVisibility() != VISIBLE && (!forceShowSendButton || openKeyboard)) {
                                    openKeyboard();
                                }
                                currentTopViewAnimation = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Object animation) {
                            if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                                currentTopViewAnimation = null;
                            }
                        }
                    });
                    currentTopViewAnimation.setDuration(200);
                    currentTopViewAnimation.start();
                } else {
                    setTopViewAnimation(1.0f);
                    if (recordedAudioPanel.getVisibility() != VISIBLE && (!forceShowSendButton || openKeyboard)) {
                        openKeyboard();
                    }
                }
            } else {
                setTopViewAnimation(1.0f);
            }
        }
    }

    public void hideTopView(final boolean animated) {
        if (topView == null || !topViewShowed) {
            return;
        }

        topViewShowed = false;
        needShowTopView = false;
        if (allowShowTopView) {
            float resumeValue = 1.0f;
            if (currentTopViewAnimation != null) {
                resumeValue = topViewAnimation;
                currentTopViewAnimation.cancel();
                currentTopViewAnimation = null;
            }
            if (animated) {
                currentTopViewAnimation = new AnimatorSetProxy();
                currentTopViewAnimation.playTogether(
                        ObjectAnimatorProxy.ofFloat(ChatActivityEnterView.this, "topViewAnimation", resumeValue, 0.0f)
                );
                currentTopViewAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            topView.setVisibility(GONE);
                            setTopViewAnimation(0.0f);
                            currentTopViewAnimation = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Object animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            currentTopViewAnimation = null;
                        }
                    }
                });
                currentTopViewAnimation.setDuration(200);
                currentTopViewAnimation.start();
            } else {
                topView.setVisibility(GONE);
                setTopViewAnimation(0.0f);
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
                        setTopViewAnimation(0.0f);
                    }
                }
            } else {
                if (!allowShowTopView) {
                    allowShowTopView = true;
                    if (needShowTopView) {
                        topView.setVisibility(VISIBLE);
                        setTopViewAnimation(1.0f);
                    }
                }
            }
        }
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
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidReset);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioProgressDidChanged);
        if (emojiView != null) {
            emojiView.onDestroy();
        }
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (sizeNotifierLayout != null) {
            sizeNotifierLayout.setDelegate(null);
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
            if (!AndroidUtilities.usingHardwareInput && !keyboardVisible) {
                waitingForKeyboardOpen = true;
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    }

    public void setDialogId(long id) {
        dialog_id = id;
        if ((int) dialog_id < 0) {
            TLRPC.Chat currentChat = MessagesController.getInstance().getChat(-(int) dialog_id);
            silent = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + dialog_id, false);
            isAsAdmin = ChatObject.isChannel(currentChat) && (currentChat.creator || currentChat.editor) && !currentChat.megagroup;
            adminModeAvailable = isAsAdmin && !currentChat.broadcast;
            canWriteToChannel = isAsAdmin;
            if (adminModeAvailable) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                isAsAdmin = preferences.getBoolean("asadmin_" + dialog_id, true);
            }
            if (asAdminButton != null) {
                asAdminButton.setVisibility(adminModeAvailable ? VISIBLE : GONE);
                asAdminButton.setImageResource(isAsAdmin ? R.drawable.publish_active : R.drawable.publish);
                updateFieldHint();
            }
            if (notifyButton != null) {
                notifyButton.setVisibility(canWriteToChannel ? VISIBLE : GONE);
                notifyButton.setImageResource(silent ? R.drawable.notify_members_off : R.drawable.notify_members_on);
                ViewProxy.setPivotX(attachButton, AndroidUtilities.dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
            }
        }
    }

    private void updateFieldHint() {
        boolean isChannel = false;
        if ((int) dialog_id < 0) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(-(int) dialog_id);
            isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
        }
        if (isChannel) {
            if (editingMessageObject != null) {
                messageEditText.setHint(editingCaption ? LocaleController.getString("Caption", R.string.Caption) : LocaleController.getString("TypeMessage", R.string.TypeMessage));
            } else {
                if (isAsAdmin) {
                    if (silent) {
                        messageEditText.setHint(LocaleController.getString("ChannelSilentBroadcast", R.string.ChannelSilentBroadcast));
                    } else {
                        messageEditText.setHint(LocaleController.getString("ChannelBroadcast", R.string.ChannelBroadcast));
                    }
                } else {
                    messageEditText.setHint(LocaleController.getString("ChannelComment", R.string.ChannelComment));
                }
            }
        } else {
            messageEditText.setHint(LocaleController.getString("TypeMessage", R.string.TypeMessage));
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
        AnimatorSetProxy animatorSetProxy = new AnimatorSetProxy();
        animatorSetProxy.playTogether(
                ObjectAnimatorProxy.ofFloat(recordedAudioPanel, "alpha", 0.0f)
        );
        animatorSetProxy.setDuration(200);
        animatorSetProxy.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Object animation) {
                recordedAudioPanel.clearAnimation();
                recordedAudioPanel.setVisibility(GONE);

            }
        });
        animatorSetProxy.start();
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
        if (audioToSend != null) {
            MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
            if (playing != null && playing == audioToSendMessageObject) {
                MediaController.getInstance().cleanupPlayer(true, true);
            }
            SendMessagesHelper.getInstance().sendMessage(audioToSend, null, audioToSendPath, dialog_id, replyingMessageObject, isAsAdmin, null, null);
            if (delegate != null) {
                delegate.onMessageSend(null);
            }
            hideRecordedAudioPanel();
            checkSendButton(true);
            return;
        }
        String message = messageEditText.getText().toString();
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
            SendMessagesHelper.getInstance().editMessage(editingMessageObject, messageEditText.getText().toString(), messageWebPageSearch, parentFragment);
            setEditinigMessageObject(null, false);
        }
    }

    public boolean processSendingText(String text) {
        text = getTrimmedString(text);
        if (text.length() != 0) {
            int count = (int) Math.ceil(text.length() / 4096.0f);
            for (int a = 0; a < count; a++) {
                String mess = text.substring(a * 4096, Math.min((a + 1) * 4096, text.length()));
                SendMessagesHelper.getInstance().sendMessage(mess, dialog_id, replyingMessageObject, messageWebPage, messageWebPageSearch, asAdmin(), null, null, null);
            }
            return true;
        }
        return false;
    }

    private String getTrimmedString(String src) {
        src = src.trim();
        if (src.length() == 0) {
            return src;
        }
        while (src.startsWith("\n")) {
            src = src.substring(1);
        }
        while (src.endsWith("\n")) {
            src = src.substring(0, src.length() - 1);
        }
        return src;
    }

    private void checkSendButton(final boolean animated) {
        if (editingMessageObject != null) {
            return;
        }
        String message = getTrimmedString(messageEditText.getText().toString());
        if (message.length() > 0 || forceShowSendButton || audioToSend != null) {
            boolean showBotButton = messageEditText.caption != null && sendButton.getVisibility() == VISIBLE;
            boolean showSendButton = messageEditText.caption == null && cancelBotButton.getVisibility() == VISIBLE;
            if (audioSendButton.getVisibility() == VISIBLE || showBotButton || showSendButton) {
                if (animated) {
                    if (runningAnimationType == 1 && messageEditText.caption == null || runningAnimationType == 3 && messageEditText.caption != null) {
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

                    if (attachButton != null) {
                        runningAnimation2 = new AnimatorSetProxy();
                        runningAnimation2.playTogether(
                                ObjectAnimatorProxy.ofFloat(attachButton, "alpha", 0.0f),
                                ObjectAnimatorProxy.ofFloat(attachButton, "scaleX", 0.0f)
                        );
                        runningAnimation2.setDuration(100);
                        runningAnimation2.addListener(new AnimatorListenerAdapterProxy() {
                            @Override
                            public void onAnimationEnd(Object animation) {
                                if (runningAnimation2 != null && runningAnimation2.equals(animation)) {
                                    attachButton.setVisibility(GONE);
                                    attachButton.clearAnimation();
                                }
                            }

                            @Override
                            public void onAnimationCancel(Object animation) {
                                if (runningAnimation2 != null && runningAnimation2.equals(animation)) {
                                    runningAnimation2 = null;
                                }
                            }
                        });
                        runningAnimation2.start();
                        updateFieldRight(0);
                        if (delegate != null) {
                            delegate.onAttachButtonHidden();
                        }
                    }

                    runningAnimation = new AnimatorSetProxy();

                    ArrayList<Object> animators = new ArrayList<>();
                    if (audioSendButton.getVisibility() == VISIBLE) {
                        animators.add(ObjectAnimatorProxy.ofFloat(audioSendButton, "scaleX", 0.1f));
                        animators.add(ObjectAnimatorProxy.ofFloat(audioSendButton, "scaleY", 0.1f));
                        animators.add(ObjectAnimatorProxy.ofFloat(audioSendButton, "alpha", 0.0f));
                    }
                    if (showBotButton) {
                        animators.add(ObjectAnimatorProxy.ofFloat(sendButton, "scaleX", 0.1f));
                        animators.add(ObjectAnimatorProxy.ofFloat(sendButton, "scaleY", 0.1f));
                        animators.add(ObjectAnimatorProxy.ofFloat(sendButton, "alpha", 0.0f));
                    } else if (showSendButton) {
                        animators.add(ObjectAnimatorProxy.ofFloat(cancelBotButton, "scaleX", 0.1f));
                        animators.add(ObjectAnimatorProxy.ofFloat(cancelBotButton, "scaleY", 0.1f));
                        animators.add(ObjectAnimatorProxy.ofFloat(cancelBotButton, "alpha", 0.0f));
                    }
                    if (messageEditText.caption != null) {
                        runningAnimationType = 3;
                        animators.add(ObjectAnimatorProxy.ofFloat(cancelBotButton, "scaleX", 1.0f));
                        animators.add(ObjectAnimatorProxy.ofFloat(cancelBotButton, "scaleY", 1.0f));
                        animators.add(ObjectAnimatorProxy.ofFloat(cancelBotButton, "alpha", 1.0f));
                        cancelBotButton.setVisibility(VISIBLE);
                    } else {
                        runningAnimationType = 1;
                        animators.add(ObjectAnimatorProxy.ofFloat(sendButton, "scaleX", 1.0f));
                        animators.add(ObjectAnimatorProxy.ofFloat(sendButton, "scaleY", 1.0f));
                        animators.add(ObjectAnimatorProxy.ofFloat(sendButton, "alpha", 1.0f));
                        sendButton.setVisibility(VISIBLE);
                    }

                    runningAnimation.playTogether(animators);
                    runningAnimation.setDuration(150);
                    runningAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (runningAnimation != null && runningAnimation.equals(animation)) {
                                if (messageEditText.caption != null) {
                                    cancelBotButton.setVisibility(VISIBLE);
                                    sendButton.setVisibility(GONE);
                                    sendButton.clearAnimation();
                                } else {
                                    sendButton.setVisibility(VISIBLE);
                                    cancelBotButton.setVisibility(GONE);
                                    cancelBotButton.clearAnimation();
                                }
                                audioSendButton.setVisibility(GONE);
                                audioSendButton.clearAnimation();
                                runningAnimation = null;
                                runningAnimationType = 0;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Object animation) {
                            if (runningAnimation != null && runningAnimation.equals(animation)) {
                                runningAnimation = null;
                            }
                        }
                    });
                    runningAnimation.start();
                } else {
                    ViewProxy.setScaleX(audioSendButton, 0.1f);
                    ViewProxy.setScaleY(audioSendButton, 0.1f);
                    ViewProxy.setAlpha(audioSendButton, 0.0f);
                    if (messageEditText.caption != null) {
                        ViewProxy.setScaleX(sendButton, 0.1f);
                        ViewProxy.setScaleY(sendButton, 0.1f);
                        ViewProxy.setAlpha(sendButton, 0.0f);
                        ViewProxy.setScaleX(cancelBotButton, 1.0f);
                        ViewProxy.setScaleY(cancelBotButton, 1.0f);
                        ViewProxy.setAlpha(cancelBotButton, 1.0f);
                        cancelBotButton.setVisibility(VISIBLE);
                        sendButton.setVisibility(GONE);
                        sendButton.clearAnimation();
                    } else {
                        ViewProxy.setScaleX(cancelBotButton, 0.1f);
                        ViewProxy.setScaleY(cancelBotButton, 0.1f);
                        ViewProxy.setAlpha(cancelBotButton, 0.0f);
                        ViewProxy.setScaleX(sendButton, 1.0f);
                        ViewProxy.setScaleY(sendButton, 1.0f);
                        ViewProxy.setAlpha(sendButton, 1.0f);
                        sendButton.setVisibility(VISIBLE);
                        cancelBotButton.setVisibility(GONE);
                        cancelBotButton.clearAnimation();
                    }
                    audioSendButton.setVisibility(GONE);
                    audioSendButton.clearAnimation();
                    if (attachButton != null) {
                        attachButton.setVisibility(GONE);
                        attachButton.clearAnimation();
                        if (delegate != null) {
                            delegate.onAttachButtonHidden();
                        }
                        updateFieldRight(0);
                    }
                }
            }
        } else if (sendButton.getVisibility() == VISIBLE || cancelBotButton.getVisibility() == VISIBLE) {
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

                if (attachButton != null) {
                    attachButton.setVisibility(VISIBLE);
                    runningAnimation2 = new AnimatorSetProxy();
                    runningAnimation2.playTogether(
                            ObjectAnimatorProxy.ofFloat(attachButton, "alpha", 1.0f),
                            ObjectAnimatorProxy.ofFloat(attachButton, "scaleX", 1.0f)
                    );
                    runningAnimation2.setDuration(100);
                    runningAnimation2.start();
                    updateFieldRight(1);
                    delegate.onAttachButtonShow();
                }

                audioSendButton.setVisibility(VISIBLE);
                runningAnimation = new AnimatorSetProxy();
                runningAnimationType = 2;

                ArrayList<Object> animators = new ArrayList<>();
                animators.add(ObjectAnimatorProxy.ofFloat(audioSendButton, "scaleX", 1.0f));
                animators.add(ObjectAnimatorProxy.ofFloat(audioSendButton, "scaleY", 1.0f));
                animators.add(ObjectAnimatorProxy.ofFloat(audioSendButton, "alpha", 1.0f));
                if (cancelBotButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimatorProxy.ofFloat(cancelBotButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimatorProxy.ofFloat(cancelBotButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimatorProxy.ofFloat(cancelBotButton, "alpha", 0.0f));
                } else {
                    animators.add(ObjectAnimatorProxy.ofFloat(sendButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimatorProxy.ofFloat(sendButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimatorProxy.ofFloat(sendButton, "alpha", 0.0f));
                }

                runningAnimation.playTogether(animators);
                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            sendButton.setVisibility(GONE);
                            sendButton.clearAnimation();
                            cancelBotButton.setVisibility(GONE);
                            cancelBotButton.clearAnimation();
                            audioSendButton.setVisibility(VISIBLE);
                            runningAnimation = null;
                            runningAnimationType = 0;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Object animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            runningAnimation = null;
                        }
                    }
                });
                runningAnimation.start();
            } else {
                ViewProxy.setScaleX(sendButton, 0.1f);
                ViewProxy.setScaleY(sendButton, 0.1f);
                ViewProxy.setAlpha(sendButton, 0.0f);
                ViewProxy.setScaleX(cancelBotButton, 0.1f);
                ViewProxy.setScaleY(cancelBotButton, 0.1f);
                ViewProxy.setAlpha(cancelBotButton, 0.0f);
                ViewProxy.setScaleX(audioSendButton, 1.0f);
                ViewProxy.setScaleY(audioSendButton, 1.0f);
                ViewProxy.setAlpha(audioSendButton, 1.0f);
                cancelBotButton.setVisibility(GONE);
                cancelBotButton.clearAnimation();
                sendButton.setVisibility(GONE);
                sendButton.clearAnimation();
                audioSendButton.setVisibility(VISIBLE);
                if (attachButton != null) {
                    delegate.onAttachButtonShow();
                    attachButton.setVisibility(VISIBLE);
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

    private void updateAudioRecordIntefrace() {
        if (recordingAudio) {
            if (audioInterfaceState == 1) {
                return;
            }
            audioInterfaceState = 1;
            try {
                if (mWakeLock == null) {
                    PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
                    mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "audio record lock");
                    mWakeLock.acquire();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            AndroidUtilities.lockOrientation(parentActivity);

            recordPanel.setVisibility(VISIBLE);
            recordCircle.setVisibility(VISIBLE);
            recordCircle.setAmplitude(0);
            recordTimeText.setText("00:00");
            recordDot.resetAlpha();
            lastTimeString = null;

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
            params.leftMargin = AndroidUtilities.dp(30);
            slideText.setLayoutParams(params);
            ViewProxy.setAlpha(slideText, 1);
            ViewProxy.setX(recordPanel, AndroidUtilities.displaySize.x);
            ViewProxy.setTranslationX(recordCircle, 0);
            if (runningAnimationAudio != null) {
                runningAnimationAudio.cancel();
            }
            runningAnimationAudio = new AnimatorSetProxy();
            runningAnimationAudio.playTogether(ObjectAnimatorProxy.ofFloat(recordPanel, "translationX", 0),
                    ObjectAnimatorProxy.ofFloat(recordCircle, "scale", 1),
                    ObjectAnimatorProxy.ofFloat(audioSendButton, "alpha", 0));
            runningAnimationAudio.setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animator) {
                    if (runningAnimationAudio != null && runningAnimationAudio.equals(animator)) {
                        ViewProxy.setX(recordPanel, 0);
                        runningAnimationAudio = null;
                    }
                }
            });
            runningAnimationAudio.setInterpolator(new DecelerateInterpolator());
            runningAnimationAudio.start();
        } else {
            if (mWakeLock != null) {
                try {
                    mWakeLock.release();
                    mWakeLock = null;
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
            AndroidUtilities.unlockOrientation(parentActivity);
            if (audioInterfaceState == 0) {
                return;
            }
            audioInterfaceState = 0;

            if (runningAnimationAudio != null) {
                runningAnimationAudio.cancel();
            }
            runningAnimationAudio = new AnimatorSetProxy();
            runningAnimationAudio.playTogether(ObjectAnimatorProxy.ofFloat(recordPanel, "translationX", AndroidUtilities.displaySize.x),
                    ObjectAnimatorProxy.ofFloat(recordCircle, "scale", 0.0f),
                    ObjectAnimatorProxy.ofFloat(audioSendButton, "alpha", 1.0f));
            runningAnimationAudio.setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animator) {
                    if (runningAnimationAudio != null && runningAnimationAudio.equals(animator)) {
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
                        params.leftMargin = AndroidUtilities.dp(30);
                        slideText.setLayoutParams(params);
                        ViewProxy.setAlpha(slideText, 1);
                        recordPanel.setVisibility(GONE);
                        recordCircle.setVisibility(GONE);
                        runningAnimationAudio = null;
                    }
                }
            });
            runningAnimationAudio.setInterpolator(new AccelerateInterpolator());
            runningAnimationAudio.start();
        }
    }

    public void setDelegate(ChatActivityEnterViewDelegate delegate) {
        this.delegate = delegate;
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
                SendMessagesHelper.getInstance().sendMessage(String.format(Locale.US, "%s@%s", command, user.username), dialog_id, null, null, false, asAdmin(), null, null, null);
            } else {
                SendMessagesHelper.getInstance().sendMessage(command, dialog_id, null, null, false, asAdmin(), null, null, null);
            }
        }
    }

    public void setEditinigMessageObject(MessageObject messageObject, boolean caption) {
        if (audioToSend != null || editingMessageObject == messageObject) {
            return;
        }
        editingMessageObject = messageObject;
        editingCaption = caption;
        if (editingMessageObject != null) {
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
                    setFieldText(Emoji.replaceEmoji(new SpannableStringBuilder(editingMessageObject.messageText.toString()), messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false));
                } else {
                    setFieldText("");
                }
            }
            messageEditText.setFilters(inputFilters);
            openKeyboard();
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messageEditText.getLayoutParams();
            layoutParams.rightMargin = AndroidUtilities.dp(4);
            messageEditText.setLayoutParams(layoutParams);
            sendButton.clearAnimation();
            cancelBotButton.clearAnimation();
            audioSendButton.clearAnimation();
            attachButton.clearAnimation();
            sendButtonContainer.clearAnimation();
            sendButton.setVisibility(GONE);
            cancelBotButton.setVisibility(GONE);
            audioSendButton.setVisibility(GONE);
            attachButton.setVisibility(GONE);
            sendButtonContainer.setVisibility(GONE);
        } else {
            messageEditText.setFilters(new InputFilter[0]);
            delegate.onMessageEditEnd();
            audioSendButton.setVisibility(VISIBLE);
            attachButton.setVisibility(VISIBLE);
            sendButtonContainer.setVisibility(VISIBLE);
            ViewProxy.setScaleX(attachButton, 1.0f);
            ViewProxy.setAlpha(attachButton, 1.0f);
            ViewProxy.setScaleX(sendButton, 0.1f);
            ViewProxy.setScaleY(sendButton, 0.1f);
            ViewProxy.setAlpha(sendButton, 0.0f);
            ViewProxy.setScaleX(cancelBotButton, 0.1f);
            ViewProxy.setScaleY(cancelBotButton, 0.1f);
            ViewProxy.setAlpha(cancelBotButton, 0.0f);
            ViewProxy.setScaleX(audioSendButton, 1.0f);
            ViewProxy.setScaleY(audioSendButton, 1.0f);
            ViewProxy.setAlpha(audioSendButton, 1.0f);
            sendButton.setVisibility(GONE);
            sendButton.clearAnimation();
            cancelBotButton.setVisibility(GONE);
            cancelBotButton.clearAnimation();
            messageEditText.setText("");
            delegate.onAttachButtonShow();
            updateFieldRight(1);
        }
        updateFieldHint();
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

    public void replaceWithText(int start, int len, String text) {
        try {
            StringBuilder builder = new StringBuilder(messageEditText.getText());
            builder.replace(start, start + len, text);
            messageEditText.setText(builder);
            messageEditText.setSelection(start + text.length());
        } catch (Exception e) {
            FileLog.e("tmessages", e);
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
                                FileLog.e("tmessages", e);
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

    public String getFieldText() {
        if (messageEditText != null && messageEditText.length() > 0) {
            return messageEditText.getText().toString();
        }
        return null;
    }

    public void addToAttachLayout(View view) {
        if (attachButton == null) {
            return;
        }
        if (view.getParent() != null) {
            ViewGroup viewGroup = (ViewGroup) view.getParent();
            viewGroup.removeView(view);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            view.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
        }
        attachButton.addView(view, LayoutHelper.createLinear(48, 48));
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
        ViewProxy.setPivotX(attachButton, AndroidUtilities.dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
        attachButton.clearAnimation();
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
        botKeyboardView.setButtons(botReplyMarkup != null ? botReplyMarkup : null);
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
            SendMessagesHelper.getInstance().sendMessage(button.text, dialog_id, replyMessageObject, null, false, asAdmin(), null, null, null);
        } else if (button instanceof TLRPC.TL_keyboardButtonUrl) {
            parentFragment.showOpenUrlAlert(button.url);
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
        } else if (button instanceof TLRPC.TL_keyboardButtonCallback) {
            SendMessagesHelper.getInstance().sendCallback(messageObject, button, parentFragment);
        } else if (button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
            if (parentFragment.processSwitchButton((TLRPC.TL_keyboardButtonSwitchInline) button)) {
                return;
            }
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", 1);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                @Override
                public void didSelectDialog(DialogsActivity fragment, long did, boolean param) {
                    int uid = messageObject.messageOwner.from_id;
                    if (messageObject.messageOwner.via_bot_id != 0) {
                        uid = messageObject.messageOwner.via_bot_id;
                    }
                    TLRPC.User user = MessagesController.getInstance().getUser(uid);
                    if (user == null) {
                        fragment.finishFragment();
                        return;
                    }
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("dialog_" + did, "@" + user.username + " " + button.query);
                    editor.commit();
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
        emojiView = new EmojiView(allowStickers, allowGifs, parentActivity);
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
                    FileLog.e("tmessages", e);
                } finally {
                    innerTextChange = 0;
                }
            }

            public void onStickerSelected(TLRPC.Document sticker) {
                ChatActivityEnterView.this.onStickerSelected(sticker);
            }

            @Override
            public void onStickersSettingsClick() {
                if (parentFragment != null) {
                    parentFragment.presentFragment(new StickersActivity());
                }
            }

            @Override
            public void onGifSelected(TLRPC.Document gif) {
                SendMessagesHelper.getInstance().sendSticker(gif, dialog_id, replyingMessageObject, asAdmin());
                if ((int) dialog_id == 0) {
                    MessagesController.getInstance().saveGif(gif);
                }
                if (delegate != null) {
                    delegate.onMessageSend(null);
                }
            }

            @Override
            public void onGifTab(boolean opened) {
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
        });
        emojiView.setVisibility(GONE);
        sizeNotifierLayout.addView(emojiView);
    }

    @Override
    public void onStickerSelected(TLRPC.Document sticker) {
        SendMessagesHelper.getInstance().sendSticker(sticker, dialog_id, replyingMessageObject, asAdmin());
        if (delegate != null) {
            delegate.onMessageSend(null);
        }
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
            layoutParams.width = AndroidUtilities.displaySize.x;
            layoutParams.height = currentHeight;
            currentView.setLayoutParams(layoutParams);
            AndroidUtilities.hideKeyboard(messageEditText);
            if (sizeNotifierLayout != null) {
                emojiPadding = currentHeight;
                sizeNotifierLayout.requestLayout();
                if (contentType == 0) {
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_kb);
                } else if (contentType == 1) {
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
                }
                updateBotButton();
                onWindowSizeChanged();
            }
        } else {
            if (emojiButton != null) {
                emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
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
        } else if (!AndroidUtilities.usingHardwareInput && !keyboardVisible) {
            waitingForKeyboardOpen = true;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
            AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
        }
    }

    public boolean isEditingMessage() {
        return editingMessageObject != null;
    }

    public boolean isEditingCaption() {
        return editingCaption;
    }

    public boolean hasAudioToSend() {
        return audioToSendMessageObject != null;
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

    public void addRecentGif(MediaController.SearchImage searchImage) {
        if (emojiView == null) {
            return;
        }
        emojiView.addRecentGif(searchImage);
    }

    @Override
    public void onSizeChanged(int height, boolean isWidthGreater) {
        if (height > AndroidUtilities.dp(50) && keyboardVisible) {
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
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
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

    public int getEmojiHeight() {
        if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            return keyboardHeightLand;
        } else {
            return keyboardHeight;
        }
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
            Long time = t / 1000;
            int ms = (int) (t % 1000L) / 10;
            String str = String.format("%02d:%02d.%02d", time / 60, time % 60, ms);
            if (lastTimeString == null || !lastTimeString.equals(str)) {
                if (time % 5 == 0) {
                    MessagesController.getInstance().sendTyping(dialog_id, 1, 0);
                }
                if (recordTimeText != null) {
                    recordTimeText.setText(str);
                }
            }
            if (recordCircle != null) {
                recordCircle.setAmplitude((Double) args[1]);
            }
        } else if (id == NotificationCenter.closeChats) {
            if (messageEditText != null && messageEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(messageEditText);
            }
        } else if (id == NotificationCenter.recordStartError || id == NotificationCenter.recordStopped) {
            if (recordingAudio) {
                MessagesController.getInstance().sendTyping(dialog_id, 2, 0);
                recordingAudio = false;
                updateAudioRecordIntefrace();
            }
        } else if (id == NotificationCenter.recordStarted) {
            if (!recordingAudio) {
                recordingAudio = true;
                updateAudioRecordIntefrace();
            }
        } else if (id == NotificationCenter.audioDidSent) {
            audioToSend = (TLRPC.TL_document) args[0];
            audioToSendPath = (String) args[1];
            if (audioToSend != null) {
                if (recordedAudioPanel == null) {
                    return;
                }

                TLRPC.TL_message message = new TLRPC.TL_message();
                message.out = true;
                message.id = 0;
                message.to_id = new TLRPC.TL_peerUser();
                message.to_id.user_id = message.from_id = UserConfig.getClientUserId();
                message.date = (int) (System.currentTimeMillis() / 1000);
                message.message = "-1";
                message.attachPath = audioToSendPath;
                message.media = new TLRPC.TL_messageMediaDocument();
                message.media.document = audioToSend;
                message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                audioToSendMessageObject = new MessageObject(message, null, false);

                ViewProxy.setAlpha(recordedAudioPanel, 1.0f);
                recordedAudioPanel.clearAnimation();
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
        } else if (id == NotificationCenter.audioRouteChanged) {
            if (parentActivity != null) {
                boolean frontSpeaker = (Boolean) args[0];
                parentActivity.setVolumeControlStream(frontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.USE_DEFAULT_STREAM_TYPE);
            }
        } else if (id == NotificationCenter.audioDidReset) {
            if (audioToSendMessageObject != null && !MediaController.getInstance().isPlayingAudio(audioToSendMessageObject)) {
                recordedAudioPlayButton.setImageResource(R.drawable.s_player_play_states);
                recordedAudioSeekBar.setProgress(0);
            }
        } else if (id == NotificationCenter.audioProgressDidChanged) {
            Integer mid = (Integer) args[0];
            if (audioToSendMessageObject != null && MediaController.getInstance().isPlayingAudio(audioToSendMessageObject)) {
                MessageObject player = MediaController.getInstance().getPlayingMessageObject();
                audioToSendMessageObject.audioProgress = player.audioProgress;
                audioToSendMessageObject.audioProgressSec = player.audioProgressSec;
                if (!recordedAudioSeekBar.isDragging()) {
                    recordedAudioSeekBar.setProgress(audioToSendMessageObject.audioProgress);
                }
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
}
