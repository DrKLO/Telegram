/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import org.telegram.ui.ApplicationLoader;

public class ChatActivityEnterView implements NotificationCenter.NotificationCenterDelegate, SizeNotifierRelativeLayout.SizeNotifierRelativeLayoutDelegate {

    public static interface ChatActivityEnterViewDelegate {
        public abstract void onMessageSend();
        public abstract void needSendTyping();
    }

    private EditText messsageEditText;
    private ImageButton sendButton;
    private PopupWindow emojiPopup;
    private ImageView emojiButton;
    private EmojiView emojiView;
    private TextView recordTimeText;
    private ImageButton audioSendButton;
    private View recordPanel;
    private View slideText;
    private PowerManager.WakeLock mWakeLock = null;
    private SizeNotifierRelativeLayout sizeNotifierRelativeLayout;
    private Object runningAnimation = null;
    private int runningAnimationType = 0;

    private int keyboardHeight = 0;
    private int keyboardHeightLand = 0;
    private boolean keyboardVisible;
    private boolean sendByEnter = false;
    private long lastTypingTimeSend = 0;
    private String lastTimeString = null;
    private float startedDraggingX = -1;
    private float distCanMove = AndroidUtilities.dp(80);
    private boolean recordingAudio = false;

    private Activity parentActivity;
    private long dialog_id;
    private boolean ignoreTextChange = false;
    private ChatActivityEnterViewDelegate delegate;

    public ChatActivityEnterView() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStarted);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStartError);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordStopped);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recordProgressChanged);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.audioDidSent);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.hideEmojiKeyboard);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        sendByEnter = preferences.getBoolean("send_by_enter", false);
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
        if (mWakeLock != null) {
            try {
                mWakeLock.release();
                mWakeLock = null;
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.delegate = null;
        }
    }

    public void setContainerView(Activity activity, View containerView) {
        parentActivity = activity;

        sizeNotifierRelativeLayout = (SizeNotifierRelativeLayout)containerView.findViewById(R.id.chat_layout);
        sizeNotifierRelativeLayout.delegate = this;

        messsageEditText = (EditText)containerView.findViewById(R.id.chat_text_edit);
        messsageEditText.setHint(LocaleController.getString("TypeMessage", R.string.TypeMessage));

        sendButton = (ImageButton)containerView.findViewById(R.id.chat_send_button);
        sendButton.setVisibility(View.INVISIBLE);
        emojiButton = (ImageView)containerView.findViewById(R.id.chat_smile_button);
        audioSendButton = (ImageButton)containerView.findViewById(R.id.chat_audio_send_button);
        recordPanel = containerView.findViewById(R.id.record_panel);
        recordTimeText = (TextView)containerView.findViewById(R.id.recording_time_text);
        slideText = containerView.findViewById(R.id.slideText);
        TextView textView = (TextView)containerView.findViewById(R.id.slideToCancelTextView);
        textView.setText(LocaleController.getString("SlideToCancel", R.string.SlideToCancel));

        emojiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (emojiPopup == null) {
                    showEmojiPopup(true);
                } else {
                    showEmojiPopup(!emojiPopup.isShowing());
                }
            }
        });

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

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        audioSendButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
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
                    if(android.os.Build.VERSION.SDK_INT > 13) {
                        x = x + audioSendButton.getX();
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)slideText.getLayoutParams();
                        if (startedDraggingX != -1) {
                            float dist = (x - startedDraggingX);
                            params.leftMargin = AndroidUtilities.dp(30) + (int)dist;
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
                }
                view.onTouchEvent(motionEvent);
                return true;
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

                if (message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
                    int currentTime = ConnectionsManager.getInstance().getCurrentTime();
                    TLRPC.User currentUser = null;
                    if ((int)dialog_id > 0) {
                        currentUser = MessagesController.getInstance().getUser((int)dialog_id);
                    }
                    if (currentUser != null && currentUser.status != null && currentUser.status.expires < currentTime) {
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

        checkSendButton(false);
    }

    private void sendMessage() {
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
            int count = (int)Math.ceil(text.length() / 2048.0f);
            for (int a = 0; a < count; a++) {
                String mess = text.substring(a * 2048, Math.min((a + 1) * 2048, text.length()));
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

    private void checkSendButton(boolean animated) {
        String message = getTrimmedString(messsageEditText.getText().toString());
        if (message.length() > 0) {
            if (audioSendButton.getVisibility() == View.VISIBLE) {
                if (Build.VERSION.SDK_INT >= 11 && animated) {
                    if (runningAnimationType == 1) {
                        return;
                    }
                    if (runningAnimation != null) {
                        ((AnimatorSet)runningAnimation).cancel();
                        runningAnimation = null;
                    }

                    sendButton.setVisibility(View.VISIBLE);
                    AnimatorSet animatorSet = new AnimatorSet();
                    runningAnimation = animatorSet;
                    runningAnimationType = 1;
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(audioSendButton, "scaleX", 0.1f),
                            ObjectAnimator.ofFloat(audioSendButton, "scaleY", 0.1f),
                            ObjectAnimator.ofFloat(audioSendButton, "alpha", 0.0f),
                            ObjectAnimator.ofFloat(sendButton, "scaleX", 1.0f),
                            ObjectAnimator.ofFloat(sendButton, "scaleY", 1.0f),
                            ObjectAnimator.ofFloat(sendButton, "alpha", 1.0f)
                    );

                    animatorSet.setDuration(200);
                    animatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (animation == runningAnimation) {
                                sendButton.setVisibility(View.VISIBLE);
                                audioSendButton.setVisibility(View.INVISIBLE);
                                runningAnimation = null;
                                runningAnimationType = 0;
                            }
                        }
                    });
                    animatorSet.start();
                } else {
                    if (Build.VERSION.SDK_INT >= 11) {
                        audioSendButton.setScaleX(0.1f);
                        audioSendButton.setScaleY(0.1f);
                        audioSendButton.setAlpha(0.0f);
                        sendButton.setScaleX(1.0f);
                        sendButton.setScaleY(1.0f);
                        sendButton.setAlpha(1.0f);
                    }
                    sendButton.setVisibility(View.VISIBLE);
                    audioSendButton.setVisibility(View.INVISIBLE);
                }
            }
        } else if (sendButton.getVisibility() == View.VISIBLE) {
            if (Build.VERSION.SDK_INT >= 11 && animated) {
                if (runningAnimationType == 2) {
                    return;
                }

                if (runningAnimation != null) {
                    ((AnimatorSet)runningAnimation).cancel();
                    runningAnimation = null;
                }

                audioSendButton.setVisibility(View.VISIBLE);
                AnimatorSet animatorSet = new AnimatorSet();
                runningAnimation = animatorSet;
                runningAnimationType = 2;
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(sendButton, "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(sendButton, "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(sendButton, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(audioSendButton, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(audioSendButton, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(audioSendButton, "alpha", 1.0f)
                );

                animatorSet.setDuration(200);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation == runningAnimation) {
                            sendButton.setVisibility(View.INVISIBLE);
                            audioSendButton.setVisibility(View.VISIBLE);
                            runningAnimation = null;
                            runningAnimationType = 0;
                        }
                    }
                });
                animatorSet.start();
            } else {
                if (Build.VERSION.SDK_INT >= 11) {
                    sendButton.setScaleX(0.1f);
                    sendButton.setScaleY(0.1f);
                    sendButton.setAlpha(0.0f);
                    audioSendButton.setScaleX(1.0f);
                    audioSendButton.setScaleY(1.0f);
                    audioSendButton.setAlpha(1.0f);
                }
                sendButton.setVisibility(View.INVISIBLE);
                audioSendButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateAudioRecordIntefrace() {
        if (recordingAudio) {
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
            if(android.os.Build.VERSION.SDK_INT > 13) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)slideText.getLayoutParams();
                params.leftMargin = AndroidUtilities.dp(30);
                slideText.setLayoutParams(params);
                slideText.setAlpha(1);
                recordPanel.setX(AndroidUtilities.displaySize.x);
                recordPanel.animate().setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        recordPanel.setX(0);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animator) {
                    }
                }).setDuration(300).translationX(0).start();
            }
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
            if(android.os.Build.VERSION.SDK_INT > 13) {
                recordPanel.animate().setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)slideText.getLayoutParams();
                        params.leftMargin = AndroidUtilities.dp(30);
                        slideText.setLayoutParams(params);
                        slideText.setAlpha(1);
                        recordPanel.setVisibility(View.GONE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animator) {
                    }
                }).setDuration(300).translationX(AndroidUtilities.displaySize.x).start();
            } else {
                recordPanel.setVisibility(View.GONE);
            }
        }
    }

    private void showEmojiPopup(boolean show) {
        InputMethodManager localInputMethodManager = (InputMethodManager)ApplicationLoader.applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) {
            if (emojiPopup == null) {
                createEmojiPopup();
            }
            int currentHeight;
            WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
            int rotation = manager.getDefaultDisplay().getRotation();
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

            emojiPopup.showAtLocation(parentActivity.getWindow().getDecorView(), 83, 0, 0);
            if (!keyboardVisible) {
                if (sizeNotifierRelativeLayout != null) {
                    sizeNotifierRelativeLayout.setPadding(0, 0, 0, currentHeight);
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_hide);
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
            emojiPopup.dismiss();
        }
        if (sizeNotifierRelativeLayout != null) {
            sizeNotifierRelativeLayout.post(new Runnable() {
                public void run() {
                    if (sizeNotifierRelativeLayout != null) {
                        sizeNotifierRelativeLayout.setPadding(0, 0, 0, 0);
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

    private void createEmojiPopup() {
        if (parentActivity == null) {
            return;
        }
        emojiView = new EmojiView(parentActivity);
        emojiView.setListener(new EmojiView.Listener() {
            public void onBackspace() {
                messsageEditText.dispatchKeyEvent(new KeyEvent(0, 67));
            }

            public void onEmojiSelected(String paramAnonymousString) {
                int i = messsageEditText.getSelectionEnd();
                CharSequence localCharSequence = Emoji.replaceEmoji(paramAnonymousString, messsageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20));
                messsageEditText.setText(messsageEditText.getText().insert(i, localCharSequence));
                int j = i + localCharSequence.length();
                messsageEditText.setSelection(j, j);
            }
        });
        emojiPopup = new PopupWindow(emojiView);

        /*try {
            Method method = emojiPopup.getClass().getMethod("setWindowLayoutType", int.class);
            if (method != null) {
                method.invoke(emojiPopup, WindowManager.LayoutParams.LAST_SUB_WINDOW);
            }
        } catch (Exception e) {
            //don't promt
        }*/
    }

    public void setDelegate(ChatActivityEnterViewDelegate delegate) {
        this.delegate = delegate;
    }

    public void setDialogId(long id) {
        dialog_id = id;
    }

    public void setFieldText(String text) {
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

    @Override
    public void onSizeChanged(int height) {
        Rect localRect = new Rect();
        parentActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(localRect);

        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        if (manager == null || manager.getDefaultDisplay() == null) {
            return;
        }
        int rotation = manager.getDefaultDisplay().getRotation();

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
            final WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams)emojiPopup.getContentView().getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
                WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
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
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
        } else if (id == NotificationCenter.recordProgressChanged) {
            Long time = (Long)args[0] / 1000;
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
        }
    }
}
