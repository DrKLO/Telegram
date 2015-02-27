/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.Emoji;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessagesController;
import org.telegram.android.SendMessagesHelper;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.ui.AnimationCompat.AnimatorSetProxy;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.AnimationCompat.ViewProxy;
import org.telegram.messenger.ApplicationLoader;

import java.lang.reflect.Field;

public class ChatActivityEnterView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate, SizeNotifierRelativeLayout.SizeNotifierRelativeLayoutDelegate {

    public static interface ChatActivityEnterViewDelegate {
        public abstract void onMessageSend();
        public abstract void needSendTyping();
        public abstract void onTextChanged(CharSequence text);
        public abstract void onAttachButtonHidden();
        public abstract void onAttachButtonShow();
        public abstract void onWindowSizeChanged(int size);
    }

    private EditText messsageEditText;
    private ImageView sendButton;
    private PopupWindow emojiPopup;
    private ImageView emojiButton;
    private EmojiView emojiView;
    private TextView recordTimeText;
    private ImageView audioSendButton;
    private FrameLayout recordPanel;
    private LinearLayout slideText;
    private SizeNotifierRelativeLayout sizeNotifierRelativeLayout;
    private FrameLayout attachButton;

    private PowerManager.WakeLock mWakeLock;
    private AnimatorSetProxy runningAnimation;
    private AnimatorSetProxy runningAnimation2;
    private ObjectAnimatorProxy runningAnimationAudio;
    private int runningAnimationType;
    private int audioInterfaceState;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private boolean sendByEnter;
    private long lastTypingTimeSend;
    private String lastTimeString;
    private float startedDraggingX = -1;
    private float distCanMove = AndroidUtilities.dp(80);
    private boolean recordingAudio;

    private Activity parentActivity;
    private BaseFragment parentFragment;
    private long dialog_id;
    private boolean ignoreTextChange;
    private ChatActivityEnterViewDelegate delegate;

    public ChatActivityEnterView(Activity context, SizeNotifierRelativeLayout parent, BaseFragment fragment, boolean isChat) {
        super(context);
        setOrientation(HORIZONTAL);
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
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.hideEmojiKeyboard);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioRouteChanged);
        parentActivity = context;
        parentFragment = fragment;
        sizeNotifierRelativeLayout = parent;
        sizeNotifierRelativeLayout.setDelegate(this);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        sendByEnter = preferences.getBoolean("send_by_enter", false);

        FrameLayoutFixed frameLayout = new FrameLayoutFixed(context);
        addView(frameLayout);
        LayoutParams layoutParams = (LayoutParams) frameLayout.getLayoutParams();
        layoutParams.width = 0;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.weight = 1;
        frameLayout.setLayoutParams(layoutParams);

        emojiButton = new ImageView(context);
        emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(1), 0, 0);
        frameLayout.addView(emojiButton);
        FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) emojiButton.getLayoutParams();
        layoutParams1.width = AndroidUtilities.dp(48);
        layoutParams1.height = AndroidUtilities.dp(48);
        layoutParams1.gravity = Gravity.BOTTOM;
        layoutParams1.topMargin = AndroidUtilities.dp(2);
        emojiButton.setLayoutParams(layoutParams1);
        emojiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showEmojiPopup(emojiPopup == null || !emojiPopup.isShowing());
            }
        });

        messsageEditText = new EditText(context);
        messsageEditText.setHint(LocaleController.getString("TypeMessage", R.string.TypeMessage));
        messsageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messsageEditText.setInputType(messsageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messsageEditText.setSingleLine(false);
        messsageEditText.setMaxLines(4);
        messsageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messsageEditText.setGravity(Gravity.BOTTOM);
        messsageEditText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        messsageEditText.setBackgroundDrawable(null);
        AndroidUtilities.clearCursorDrawable(messsageEditText);
        messsageEditText.setTextColor(0xff000000);
        messsageEditText.setHintTextColor(0xffb2b2b2);
        frameLayout.addView(messsageEditText);
        layoutParams1 = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
        layoutParams1.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams1.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams1.gravity = Gravity.BOTTOM;
        layoutParams1.leftMargin = AndroidUtilities.dp(52);
        layoutParams1.rightMargin = AndroidUtilities.dp(isChat ? 50 : 2);
        messsageEditText.setLayoutParams(layoutParams1);
        messsageEditText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (i == 4 && !keyboardVisible && emojiPopup != null && emojiPopup.isShowing()) {
                    if (keyEvent.getAction() == 1) {
                        showEmojiPopup(false);
                    }
                    return true;
                } else if (i == KeyEvent.KEYCODE_ENTER && sendByEnter && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    sendMessage();
                    return true;
                }
                return false;
            }
        });
        messsageEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (emojiPopup != null && emojiPopup.isShowing()) {
                    showEmojiPopup(false);
                }
            }
        });
        messsageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                } else if (sendByEnter) {
                    if (keyEvent != null && i == EditorInfo.IME_NULL && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                        sendMessage();
                        return true;
                    }
                }
                return false;
            }
        });
        messsageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                String message = getTrimmedString(charSequence.toString());
                checkSendButton(true);

                if (delegate != null) {
                    delegate.onTextChanged(charSequence);
                }

                if (message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
                    int currentTime = ConnectionsManager.getInstance().getCurrentTime();
                    TLRPC.User currentUser = null;
                    if ((int) dialog_id > 0) {
                        currentUser = MessagesController.getInstance().getUser((int) dialog_id);
                    }
                    if (currentUser != null && (currentUser.id == UserConfig.getClientUserId() || currentUser.status != null && currentUser.status.expires < currentTime)) {
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
                if (sendByEnter && editable.length() > 0 && editable.charAt(editable.length() - 1) == '\n') {
                    sendMessage();
                }
                int i = 0;
                ImageSpan[] arrayOfImageSpan = editable.getSpans(0, editable.length(), ImageSpan.class);
                int j = arrayOfImageSpan.length;
                while (true) {
                    if (i >= j) {
                        Emoji.replaceEmoji(editable, messsageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20));
                        return;
                    }
                    editable.removeSpan(arrayOfImageSpan[i]);
                    i++;
                }
            }
        });

        if (isChat) {
            attachButton = new FrameLayout(context);
            attachButton.setEnabled(false);
            ViewProxy.setPivotX(attachButton, AndroidUtilities.dp(48));
            frameLayout.addView(attachButton);
            layoutParams1 = (FrameLayout.LayoutParams) attachButton.getLayoutParams();
            layoutParams1.width = AndroidUtilities.dp(48);
            layoutParams1.height = AndroidUtilities.dp(48);
            layoutParams1.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            layoutParams1.topMargin = AndroidUtilities.dp(2);
            attachButton.setLayoutParams(layoutParams1);
        }

        recordPanel = new FrameLayoutFixed(context);
        recordPanel.setVisibility(GONE);
        recordPanel.setBackgroundColor(0xffffffff);
        frameLayout.addView(recordPanel);
        layoutParams1 = (FrameLayout.LayoutParams) recordPanel.getLayoutParams();
        layoutParams1.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams1.height = AndroidUtilities.dp(48);
        layoutParams1.gravity = Gravity.BOTTOM;
        layoutParams1.topMargin = AndroidUtilities.dp(2);
        recordPanel.setLayoutParams(layoutParams1);

        slideText = new LinearLayout(context);
        slideText.setOrientation(HORIZONTAL);
        recordPanel.addView(slideText);
        layoutParams1 = (FrameLayout.LayoutParams) slideText.getLayoutParams();
        layoutParams1.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams1.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams1.gravity = Gravity.CENTER;
        layoutParams1.leftMargin = AndroidUtilities.dp(30);
        slideText.setLayoutParams(layoutParams1);

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.slidearrow);
        slideText.addView(imageView);
        layoutParams = (LayoutParams) imageView.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        layoutParams.topMargin = AndroidUtilities.dp(1);
        imageView.setLayoutParams(layoutParams);

        TextView textView = new TextView(context);
        textView.setText(LocaleController.getString("SlideToCancel", R.string.SlideToCancel));
        textView.setTextColor(0xff999999);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        slideText.addView(textView);
        layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        layoutParams.leftMargin = AndroidUtilities.dp(6);
        textView.setLayoutParams(layoutParams);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(HORIZONTAL);
        linearLayout.setPadding(AndroidUtilities.dp(13), 0, 0, 0);
        linearLayout.setBackgroundColor(0xffffffff);
        recordPanel.addView(linearLayout);
        layoutParams1 = (FrameLayout.LayoutParams) linearLayout.getLayoutParams();
        layoutParams1.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams1.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams1.gravity = Gravity.CENTER_VERTICAL;
        linearLayout.setLayoutParams(layoutParams1);

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.rec);
        linearLayout.addView(imageView);
        layoutParams = (LayoutParams) imageView.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        layoutParams.topMargin = AndroidUtilities.dp(1);
        imageView.setLayoutParams(layoutParams);

        recordTimeText = new TextView(context);
        recordTimeText.setText("00:00");
        recordTimeText.setTextColor(0xff4d4c4b);
        recordTimeText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        linearLayout.addView(recordTimeText);
        layoutParams = (LayoutParams) recordTimeText.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER_VERTICAL;
        layoutParams.leftMargin = AndroidUtilities.dp(6);
        recordTimeText.setLayoutParams(layoutParams);

        FrameLayout frameLayout1 = new FrameLayout(context);
        addView(frameLayout1);
        layoutParams = (LayoutParams) frameLayout1.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(48);
        layoutParams.height = AndroidUtilities.dp(48);
        layoutParams.gravity = Gravity.BOTTOM;
        layoutParams.topMargin = AndroidUtilities.dp(2);
        frameLayout1.setLayoutParams(layoutParams);

        audioSendButton = new ImageView(context);
        audioSendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        audioSendButton.setImageResource(R.drawable.mic_button_states);
        audioSendButton.setBackgroundColor(0xffffffff);
        audioSendButton.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        frameLayout1.addView(audioSendButton);
        layoutParams1 = (FrameLayout.LayoutParams) audioSendButton.getLayoutParams();
        layoutParams1.width = AndroidUtilities.dp(48);
        layoutParams1.height = AndroidUtilities.dp(48);
        audioSendButton.setLayoutParams(layoutParams1);
        audioSendButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    if (parentFragment != null) {
                        String action = null;
                        TLRPC.Chat currentChat = null;
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
                    MediaController.getInstance().startRecording(dialog_id);
                    updateAudioRecordIntefrace();
                    audioSendButton.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    startedDraggingX = -1;
                    MediaController.getInstance().stopRecording(true);
                    recordingAudio = false;
                    updateAudioRecordIntefrace();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && recordingAudio) {
                    float x = motionEvent.getX();
                    if (x < -distCanMove) {
                        MediaController.getInstance().stopRecording(false);
                        recordingAudio = false;
                        updateAudioRecordIntefrace();
                    }

                    x = x + ViewProxy.getX(audioSendButton);
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
                        slideText.setLayoutParams(params);
                        ViewProxy.setAlpha(slideText, 1);
                        startedDraggingX = -1;
                    }
                }
                view.onTouchEvent(motionEvent);
                return true;
            }
        });

        sendButton = new ImageView(context);
        sendButton.setVisibility(View.INVISIBLE);
        sendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sendButton.setImageResource(R.drawable.ic_send);
        ViewProxy.setScaleX(sendButton, 0.1f);
        ViewProxy.setScaleY(sendButton, 0.1f);
        ViewProxy.setAlpha(sendButton, 0.0f);
        sendButton.clearAnimation();
        frameLayout1.addView(sendButton);
        layoutParams1 = (FrameLayout.LayoutParams) sendButton.getLayoutParams();
        layoutParams1.width = AndroidUtilities.dp(48);
        layoutParams1.height = AndroidUtilities.dp(48);
        sendButton.setLayoutParams(layoutParams1);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        checkSendButton(false);
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.hideEmojiKeyboard);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.audioRouteChanged);
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.setDelegate(null);
        }
    }

    public void setDialogId(long id) {
        dialog_id = id;
    }

    private void sendMessage() {
        if (parentFragment != null) {
            String action = null;
            TLRPC.Chat currentChat = null;
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
        if (processSendingText(messsageEditText.getText().toString())) {
            messsageEditText.setText("");
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend();
            }
        }
    }

    public boolean processSendingText(String text) {
        text = getTrimmedString(text);
        if (text.length() != 0) {
            int count = (int) Math.ceil(text.length() / 4096.0f);
            for (int a = 0; a < count; a++) {
                String mess = text.substring(a * 4096, Math.min((a + 1) * 4096, text.length()));
                SendMessagesHelper.getInstance().sendMessage(mess, dialog_id);
            }
            return true;
        }
        return false;
    }

    private String getTrimmedString(String src) {
        String result = src.trim();
        if (result.length() == 0) {
            return result;
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
        String message = getTrimmedString(messsageEditText.getText().toString());
        if (message.length() > 0) {
            if (audioSendButton.getVisibility() == View.VISIBLE) {
                if (animated) {
                    if (runningAnimationType == 1) {
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
                                if (runningAnimation2.equals(animation)) {
                                    attachButton.setVisibility(View.GONE);
                                    attachButton.clearAnimation();
                                }
                            }
                        });
                        runningAnimation2.start();

                        if (messsageEditText != null) {
                            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
                            layoutParams.rightMargin = AndroidUtilities.dp(0);
                            messsageEditText.setLayoutParams(layoutParams);
                        }

                        delegate.onAttachButtonHidden();
                    }

                    sendButton.setVisibility(View.VISIBLE);
                    runningAnimation = new AnimatorSetProxy();
                    runningAnimationType = 1;

                    runningAnimation.playTogether(
                            ObjectAnimatorProxy.ofFloat(audioSendButton, "scaleX", 0.1f),
                            ObjectAnimatorProxy.ofFloat(audioSendButton, "scaleY", 0.1f),
                            ObjectAnimatorProxy.ofFloat(audioSendButton, "alpha", 0.0f),
                            ObjectAnimatorProxy.ofFloat(sendButton, "scaleX", 1.0f),
                            ObjectAnimatorProxy.ofFloat(sendButton, "scaleY", 1.0f),
                            ObjectAnimatorProxy.ofFloat(sendButton, "alpha", 1.0f)
                    );

                    runningAnimation.setDuration(150);
                    runningAnimation.addListener(new AnimatorListenerAdapterProxy() {
                        @Override
                        public void onAnimationEnd(Object animation) {
                            if (runningAnimation.equals(animation)) {
                                sendButton.setVisibility(View.VISIBLE);
                                audioSendButton.setVisibility(View.GONE);
                                audioSendButton.clearAnimation();
                                runningAnimation = null;
                                runningAnimationType = 0;
                            }
                        }
                    });
                    runningAnimation.start();
                } else {
                    ViewProxy.setScaleX(audioSendButton, 0.1f);
                    ViewProxy.setScaleY(audioSendButton, 0.1f);
                    ViewProxy.setAlpha(audioSendButton, 0.0f);
                    ViewProxy.setScaleX(sendButton, 1.0f);
                    ViewProxy.setScaleY(sendButton, 1.0f);
                    ViewProxy.setAlpha(sendButton, 1.0f);
                    sendButton.setVisibility(View.VISIBLE);
                    audioSendButton.setVisibility(View.GONE);
                    audioSendButton.clearAnimation();
                    if (attachButton != null) {
                        attachButton.setVisibility(View.GONE);
                        attachButton.clearAnimation();

                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
                        layoutParams.rightMargin = AndroidUtilities.dp(0);
                        messsageEditText.setLayoutParams(layoutParams);
                    }
                }
            }
        } else if (sendButton.getVisibility() == View.VISIBLE) {
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
                    attachButton.setVisibility(View.VISIBLE);
                    runningAnimation2 = new AnimatorSetProxy();
                    runningAnimation2.playTogether(
                            ObjectAnimatorProxy.ofFloat(attachButton, "alpha", 1.0f),
                            ObjectAnimatorProxy.ofFloat(attachButton, "scaleX", 1.0f)
                    );
                    runningAnimation2.setDuration(100);
                    runningAnimation2.start();

                    if (messsageEditText != null) {
                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
                        layoutParams.rightMargin = AndroidUtilities.dp(50);
                        messsageEditText.setLayoutParams(layoutParams);
                    }

                    delegate.onAttachButtonShow();
                }

                audioSendButton.setVisibility(View.VISIBLE);
                runningAnimation = new AnimatorSetProxy();
                runningAnimationType = 2;

                runningAnimation.playTogether(
                        ObjectAnimatorProxy.ofFloat(sendButton, "scaleX", 0.1f),
                        ObjectAnimatorProxy.ofFloat(sendButton, "scaleY", 0.1f),
                        ObjectAnimatorProxy.ofFloat(sendButton, "alpha", 0.0f),
                        ObjectAnimatorProxy.ofFloat(audioSendButton, "scaleX", 1.0f),
                        ObjectAnimatorProxy.ofFloat(audioSendButton, "scaleY", 1.0f),
                        ObjectAnimatorProxy.ofFloat(audioSendButton, "alpha", 1.0f)
                );

                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapterProxy() {
                    @Override
                    public void onAnimationEnd(Object animation) {
                        if (runningAnimation.equals(animation)) {
                            sendButton.setVisibility(View.GONE);
                            sendButton.clearAnimation();
                            audioSendButton.setVisibility(View.VISIBLE);
                            runningAnimation = null;
                            runningAnimationType = 0;
                        }
                    }
                });
                runningAnimation.start();
            } else {
                ViewProxy.setScaleX(sendButton, 0.1f);
                ViewProxy.setScaleY(sendButton, 0.1f);
                ViewProxy.setAlpha(sendButton, 0.0f);
                ViewProxy.setScaleX(audioSendButton, 1.0f);
                ViewProxy.setScaleY(audioSendButton, 1.0f);
                ViewProxy.setAlpha(audioSendButton, 1.0f);
                sendButton.setVisibility(View.GONE);
                sendButton.clearAnimation();
                audioSendButton.setVisibility(View.VISIBLE);
                if (attachButton != null) {
                    attachButton.setVisibility(View.VISIBLE);
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messsageEditText.getLayoutParams();
                    layoutParams.rightMargin = AndroidUtilities.dp(50);
                    messsageEditText.setLayoutParams(layoutParams);
                }
            }
        }
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

            recordPanel.setVisibility(View.VISIBLE);
            recordTimeText.setText("00:00");
            lastTimeString = null;

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
            params.leftMargin = AndroidUtilities.dp(30);
            slideText.setLayoutParams(params);
            ViewProxy.setAlpha(slideText, 1);
            ViewProxy.setX(recordPanel, AndroidUtilities.displaySize.x);
            if (runningAnimationAudio != null) {
                runningAnimationAudio.cancel();
            }
            runningAnimationAudio = ObjectAnimatorProxy.ofFloatProxy(recordPanel, "translationX", 0).setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animator) {
                    if (runningAnimationAudio != null && runningAnimationAudio.equals(animator)) {
                        ViewProxy.setX(recordPanel, 0);
                    }
                }
            });
            runningAnimationAudio.setInterpolator(new AccelerateDecelerateInterpolator());
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
            runningAnimationAudio = ObjectAnimatorProxy.ofFloatProxy(recordPanel, "translationX", AndroidUtilities.displaySize.x).setDuration(300);
            runningAnimationAudio.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animator) {
                    if (runningAnimationAudio != null && runningAnimationAudio.equals(animator)) {
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) slideText.getLayoutParams();
                        params.leftMargin = AndroidUtilities.dp(30);
                        slideText.setLayoutParams(params);
                        ViewProxy.setAlpha(slideText, 1);
                        recordPanel.setVisibility(View.GONE);
                    }
                }
            });
            runningAnimationAudio.setInterpolator(new AccelerateDecelerateInterpolator());
            runningAnimationAudio.start();
        }
    }

    private void showEmojiPopup(boolean show) {
        if (show) {
            if (emojiPopup == null) {
                if (parentActivity == null) {
                    return;
                }
                emojiView = new EmojiView(parentActivity);
                emojiView.setListener(new EmojiView.Listener() {
                    public void onBackspace() {
                        messsageEditText.dispatchKeyEvent(new KeyEvent(0, 67));
                    }

                    public void onEmojiSelected(String symbol) {
                        int i = messsageEditText.getSelectionEnd();
                        if (i < 0) {
                            i = 0;
                        }
                        try {
                            CharSequence localCharSequence = Emoji.replaceEmoji(symbol, messsageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20));
                            messsageEditText.setText(messsageEditText.getText().insert(i, localCharSequence));
                            int j = i + localCharSequence.length();
                            messsageEditText.setSelection(j, j);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                });
                emojiPopup = new PopupWindow(emojiView);

                if (Build.VERSION.SDK_INT >= 21) {
                    try {
                        Field field = PopupWindow.class.getDeclaredField("mWindowLayoutType");
                        field.setAccessible(true);
                        field.set(emojiPopup, WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
                    } catch (Exception e) {
                        /* ignored */
                    }
                }
            }
            int currentHeight;
            WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
            int rotation = wm.getDefaultDisplay().getRotation();
            if (keyboardHeight <= 0) {
                keyboardHeight = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height", AndroidUtilities.dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height_land3", AndroidUtilities.dp(200));
            }
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                currentHeight = keyboardHeightLand;
            } else {
                currentHeight = keyboardHeight;
            }
            emojiPopup.setHeight(View.MeasureSpec.makeMeasureSpec(currentHeight, View.MeasureSpec.EXACTLY));
            if (sizeNotifierRelativeLayout != null) {
                emojiPopup.setWidth(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.EXACTLY));
            }

            try {
                emojiPopup.showAtLocation(parentActivity.getWindow().getDecorView(), Gravity.BOTTOM | Gravity.LEFT, 0, 0);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
                return;
            }

            if (!keyboardVisible) {
                if (sizeNotifierRelativeLayout != null) {
                    sizeNotifierRelativeLayout.setPadding(0, 0, 0, currentHeight);
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_hide);
                    if (delegate != null) {
                        delegate.onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
                    }
                }
                return;
            }
            emojiButton.setImageResource(R.drawable.ic_msg_panel_kb);
            return;
        }
        if (emojiButton != null) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        }
        if (emojiPopup != null) {
            try {
                emojiPopup.dismiss();
            } catch (Exception e) {
                //don't promt
            }
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.post(new Runnable() {
                public void run() {
                    if (sizeNotifierRelativeLayout != null) {
                        sizeNotifierRelativeLayout.setPadding(0, 0, 0, 0);
                        if (delegate != null) {
                            delegate.onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
                        }
                    }
                }
            });
        }
    }

    public void hideEmojiPopup() {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            showEmojiPopup(false);
        }
    }

    public void setDelegate(ChatActivityEnterViewDelegate delegate) {
        this.delegate = delegate;
    }

    public void setFieldText(String text) {
        if (messsageEditText == null) {
            return;
        }
        ignoreTextChange = true;
        messsageEditText.setText(text);
        messsageEditText.setSelection(messsageEditText.getText().length());
        ignoreTextChange = false;
    }

    public void setFieldFocused(boolean focus) {
        if (messsageEditText == null) {
            return;
        }
        if (focus) {
            if (!messsageEditText.isFocused()) {
                messsageEditText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (messsageEditText != null) {
                            try {
                                messsageEditText.requestFocus();
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
                    }
                }, 600);
            }
        } else {
            if (messsageEditText.isFocused() && !keyboardVisible) {
                messsageEditText.clearFocus();
            }
        }
    }

    public boolean hasText() {
        return messsageEditText != null && messsageEditText.length() > 0;
    }

    public String getFieldText() {
        if (messsageEditText != null && messsageEditText.length() > 0) {
            return messsageEditText.getText().toString();
        }
        return null;
    }

    public boolean isEmojiPopupShowing() {
        return emojiPopup != null && emojiPopup.isShowing();
    }

    public void addToAttachLayout(View view) {
        if (attachButton == null) {
            return;
        }
        if (view.getParent() != null) {
            ViewGroup viewGroup = (ViewGroup) view.getParent();
            viewGroup.removeView(view);
        }
        attachButton.addView(view);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.width = AndroidUtilities.dp(48);
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(layoutParams);
    }

    @Override
    public void onSizeChanged(int height) {
        Rect localRect = new Rect();
        parentActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(localRect);

        WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        if (wm == null || wm.getDefaultDisplay() == null) {
            return;
        }
        int rotation = wm.getDefaultDisplay().getRotation();

        if (height > AndroidUtilities.dp(50) && keyboardVisible) {
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                keyboardHeightLand = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }

        if (emojiPopup != null && emojiPopup.isShowing()) {
            int newHeight = 0;
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                newHeight = keyboardHeightLand;
            } else {
                newHeight = keyboardHeight;
            }
            final WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) emojiPopup.getContentView().getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                wm.updateViewLayout(emojiPopup.getContentView(), layoutParams);
                if (!keyboardVisible) {
                    sizeNotifierRelativeLayout.post(new Runnable() {
                        @Override
                        public void run() {
                            if (sizeNotifierRelativeLayout != null) {
                                sizeNotifierRelativeLayout.setPadding(0, 0, 0, layoutParams.height);
                                sizeNotifierRelativeLayout.requestLayout();
                                if (delegate != null) {
                                    delegate.onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
                                }
                            }
                        }
                    });
                }
            }
        }

        boolean oldValue = keyboardVisible;
        keyboardVisible = height > 0;
        if (keyboardVisible && sizeNotifierRelativeLayout.getPaddingBottom() > 0) {
            showEmojiPopup(false);
        } else if (!keyboardVisible && keyboardVisible != oldValue && emojiPopup != null && emojiPopup.isShowing()) {
            showEmojiPopup(false);
        }
        if (delegate != null) {
            delegate.onWindowSizeChanged(sizeNotifierRelativeLayout.getHeight() - sizeNotifierRelativeLayout.getPaddingBottom());
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
        } else if (id == NotificationCenter.recordProgressChanged) {
            Long time = (Long) args[0] / 1000;
            String str = String.format("%02d:%02d", time / 60, time % 60);
            if (lastTimeString == null || !lastTimeString.equals(str)) {
                if (recordTimeText != null) {
                    recordTimeText.setText(str);
                }
            }
        } else if (id == NotificationCenter.closeChats) {
            if (messsageEditText != null && messsageEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(messsageEditText);
            }
        } else if (id == NotificationCenter.recordStartError || id == NotificationCenter.recordStopped) {
            if (recordingAudio) {
                recordingAudio = false;
                updateAudioRecordIntefrace();
            }
        } else if (id == NotificationCenter.recordStarted) {
            if (!recordingAudio) {
                recordingAudio = true;
                updateAudioRecordIntefrace();
            }
        } else if (id == NotificationCenter.audioDidSent) {
            if (delegate != null) {
                delegate.onMessageSend();
            }
        } else if (id == NotificationCenter.hideEmojiKeyboard) {
            hideEmojiPopup();
        } else if (id == NotificationCenter.audioRouteChanged) {
            if (parentActivity != null) {
                boolean frontSpeaker = (Boolean) args[0];
                parentActivity.setVolumeControlStream(frontSpeaker ? AudioManager.STREAM_VOICE_CALL : AudioManager.USE_DEFAULT_STREAM_TYPE);
            }
        }
    }
}
