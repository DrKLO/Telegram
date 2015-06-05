/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.AnimationCompat.AnimatorSetProxy;
import org.telegram.android.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.android.Emoji;
import org.telegram.android.LocaleController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;

public class PhotoViewerCaptionEnterView extends FrameLayoutFixed implements NotificationCenter.NotificationCenterDelegate, SizeNotifierRelativeLayoutPhoto.SizeNotifierRelativeLayoutPhotoDelegate {

    public interface PhotoViewerCaptionEnterViewDelegate {
        void onCaptionEnter();
        void onTextChanged(CharSequence text, boolean bigChange);
        void onWindowSizeChanged(int size);
    }

    private EditText messageEditText;
    private PopupWindow emojiPopup;
    private ImageView emojiButton;
    private EmojiView emojiView;
    private SizeNotifierRelativeLayoutPhoto sizeNotifierFrameLayout;

    private int framesDroped;

    private int keyboardTransitionState;
    private boolean showKeyboardOnEmojiButton;
    private ViewTreeObserver.OnPreDrawListener onPreDrawListener;

    private AnimatorSetProxy runningAnimation;
    private AnimatorSetProxy runningAnimation2;
    private ObjectAnimatorProxy runningAnimationAudio;
    private int runningAnimationType;
    private int audioInterfaceState;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;

    private View window;
    private PhotoViewerCaptionEnterViewDelegate delegate;
    private boolean wasFocus;

    public PhotoViewerCaptionEnterView(Context context, View windowView, SizeNotifierRelativeLayoutPhoto parent) {
        super(context);
        setBackgroundColor(0x7f000000);
        setFocusable(true);
        setFocusableInTouchMode(true);

        window = windowView;
        sizeNotifierFrameLayout = parent;

        LinearLayout textFieldContainer = new LinearLayout(context);
        textFieldContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 2, 0, 0, 0));

        FrameLayoutFixed frameLayout = new FrameLayoutFixed(context);
        textFieldContainer.addView(frameLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        emojiButton = new ImageView(context);
        emojiButton.setImageResource(R.drawable.ic_smile_w);
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(1), 0, 0);
        frameLayout.addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT));
        emojiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (showKeyboardOnEmojiButton) {
                    setKeyboardTransitionState(1);
                    showEmojiPopup(false, false);
                    int selection = messageEditText.getSelectionStart();
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 0, 0);
                    messageEditText.onTouchEvent(event);
                    event.recycle();
                    messageEditText.setSelection(selection);
                } else {
                    boolean show = emojiPopup == null || !emojiPopup.isShowing();
                    if (show) {
                        setKeyboardTransitionState(5);
                        showEmojiPopup(show, true);
                    } else {
                        showEmojiPopup(show, true);
                        setKeyboardTransitionState(1);
                    }
                }
            }
        });

        messageEditText = new EditText(context);
        messageEditText.setHint(LocaleController.getString("AddCaption", R.string.AddCaption));
        //messageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
        //messageEditText.setInputType(EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        //Show suggestions
        messageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messageEditText.setInputType(messageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messageEditText.setSingleLine(false);
        messageEditText.setMaxLines(4);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messageEditText.setGravity(Gravity.BOTTOM);
        messageEditText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        messageEditText.setBackgroundDrawable(null);
        AndroidUtilities.clearCursorDrawable(messageEditText);
        messageEditText.setTextColor(0xffffffff);
        messageEditText.setHintTextColor(0xb2ffffff);
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(140);
        messageEditText.setFilters(inputFilters);
        frameLayout.addView(messageEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 52, 0, 6, 0));
        messageEditText.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (i == 4 && !keyboardVisible && emojiPopup != null && emojiPopup.isShowing()) {
                    if (keyEvent.getAction() == 1) {
                        showEmojiPopup(false, true);
                    }
                    return true;
                } else if (i == KeyEvent.KEYCODE_ENTER && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    delegate.onCaptionEnter();
                    return true;
                }
                return false;
            }
        });
        messageEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!wasFocus) {
                    setKeyboardTransitionState(3);
                }
                wasFocus = hasFocus;
            }
        });
        messageEditText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (emojiPopup != null && emojiPopup.isShowing()) {
                    setKeyboardTransitionState(1);
                    showEmojiPopup(false, false);
                } else {
                    setKeyboardTransitionState(3);
                }
            }
        });
        messageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    delegate.onCaptionEnter();
                    return true;
                } else if (keyEvent != null && i == EditorInfo.IME_NULL && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    delegate.onCaptionEnter();
                    return true;
                }
                return false;
            }
        });
        messageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                String message = getTrimmedString(charSequence.toString());

                if (delegate != null) {
                    delegate.onTextChanged(charSequence, before > count || count > 2);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                int i = 0;
                ImageSpan[] arrayOfImageSpan = editable.getSpans(0, editable.length(), ImageSpan.class);
                int j = arrayOfImageSpan.length;
                while (true) {
                    if (i >= j) {
                        Emoji.replaceEmoji(editable, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20));
                        return;
                    }
                    editable.removeSpan(arrayOfImageSpan[i]);
                    i++;
                }
            }
        });
    }

    private void setKeyboardTransitionState(int state) {
        if (AndroidUtilities.usingHardwareInput) {
            if (state == 1) {
                showEmojiPopup(false, false);
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();
                layoutParams.bottomMargin = 0;//AndroidUtilities.dp(48);
                setLayoutParams(layoutParams);
                keyboardTransitionState = 0;
            } else if (state == 2) {
                int currentHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
                sizeNotifierFrameLayout.setPadding(0, 0, 0, currentHeight);
                keyboardTransitionState = 0;
            } else if (state == 3) {
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();
                layoutParams.bottomMargin = 0;//AndroidUtilities.dp(48);
                setLayoutParams(layoutParams);
                keyboardTransitionState = 0;
            } else if (state == 4) {
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();
                layoutParams.bottomMargin = -AndroidUtilities.dp(400);
                setLayoutParams(layoutParams);
                keyboardTransitionState = 0;
            } else if (state == 5) {
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();
                layoutParams.bottomMargin = 0;
                setLayoutParams(layoutParams);
                keyboardTransitionState = 0;
            }
        } else {
            framesDroped = 0;
            keyboardTransitionState = state;
            if (state == 1) {
                sizeNotifierFrameLayout.setPadding(0, 0, 0, 0);
            }
        }
    }

    public int getKeyboardTransitionState() {
        return keyboardTransitionState;
    }

    private void onWindowSizeChanged(int size) {
        if (delegate != null) {
            delegate.onWindowSizeChanged(size);
        }
    }

    public void onCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.hideEmojiKeyboard);
        sizeNotifierFrameLayout.getViewTreeObserver().addOnPreDrawListener(onPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (keyboardTransitionState == 1) {
                    if (keyboardVisible || framesDroped >= 60) {
                        showEmojiPopup(false, false);
                        keyboardTransitionState = 0;
                    } else {
                        if (messageEditText != null) {
                            messageEditText.requestFocus();
                            AndroidUtilities.showKeyboard(messageEditText);
                        }
                    }
                    framesDroped++;
                    return false;
                } else if (keyboardTransitionState == 2) {
                    if (!keyboardVisible || framesDroped >= 60) {
                        int currentHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
                        sizeNotifierFrameLayout.setPadding(0, 0, 0, currentHeight);
                        keyboardTransitionState = 0;
                    }
                    framesDroped++;
                    return false;
                } else if (keyboardTransitionState == 3) {
                    if (keyboardVisible) {
                        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();
                        layoutParams.bottomMargin = 0;//AndroidUtilities.usingHardwareInput ? AndroidUtilities.dp(48) : 0;
                        setLayoutParams(layoutParams);
                        keyboardTransitionState = 0;
                    }
                } else if (keyboardTransitionState == 4) {
                    if (!keyboardVisible && (emojiPopup == null || !emojiPopup.isShowing())) {
                        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();
                        layoutParams.bottomMargin = -AndroidUtilities.dp(400);
                        setLayoutParams(layoutParams);
                        keyboardTransitionState = 0;
                    }
                } else if (keyboardTransitionState == 5) {
                    if (emojiPopup != null && emojiPopup.isShowing()) {
                        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) getLayoutParams();
                        layoutParams.bottomMargin = 0;
                        setLayoutParams(layoutParams);
                        keyboardTransitionState = 0;
                    }
                }
                return true;
            }
        });
        sizeNotifierFrameLayout.setDelegate(this);
    }

    public void onDestroy() {
        if (isEmojiPopupShowing()) {
            hideEmojiPopup();
        }
        if (isKeyboardVisible()) {
            closeKeyboard();
        }
        keyboardVisible = false;
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.hideEmojiKeyboard);
        if (sizeNotifierFrameLayout != null) {
            sizeNotifierFrameLayout.getViewTreeObserver().removeOnPreDrawListener(onPreDrawListener);
            sizeNotifierFrameLayout.setDelegate(null);
        }
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

    private void showEmojiPopup(boolean show, boolean post) {
        if (show) {
            if (emojiPopup == null) {
                emojiView = new EmojiView(false, getContext());
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
                            CharSequence localCharSequence = Emoji.replaceEmoji(symbol, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20));
                            messageEditText.setText(messageEditText.getText().insert(i, localCharSequence));
                            int j = i + localCharSequence.length();
                            messageEditText.setSelection(j, j);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }

                    public void onStickerSelected(TLRPC.Document sticker) {

                    }
                });
                emojiPopup = new PopupWindow(emojiView);
            }

            if (keyboardHeight <= 0) {
                keyboardHeight = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height", AndroidUtilities.dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).getInt("kbd_height_land3", AndroidUtilities.dp(200));
            }
            int currentHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
            FileLog.e("tmessages", "show emoji with height = " + currentHeight);
            emojiPopup.setHeight(View.MeasureSpec.makeMeasureSpec(currentHeight, View.MeasureSpec.EXACTLY));
            if (sizeNotifierFrameLayout != null) {
                emojiPopup.setWidth(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.EXACTLY));
            }

            emojiPopup.showAtLocation(window, Gravity.BOTTOM | Gravity.LEFT, 0, 0);

            if (!keyboardVisible) {
                if (sizeNotifierFrameLayout != null) {
                    sizeNotifierFrameLayout.setPadding(0, 0, 0, currentHeight);
                    emojiButton.setImageResource(R.drawable.arrow_down_w);
                    showKeyboardOnEmojiButton = false;
                    onWindowSizeChanged(sizeNotifierFrameLayout.getHeight() - sizeNotifierFrameLayout.getPaddingBottom());
                }
                return;
            } else {
                setKeyboardTransitionState(2);
                AndroidUtilities.hideKeyboard(messageEditText);
            }
            emojiButton.setImageResource(R.drawable.ic_keyboard_w);
            showKeyboardOnEmojiButton = true;
            return;
        }
        if (emojiButton != null) {
            showKeyboardOnEmojiButton = false;
            emojiButton.setImageResource(R.drawable.ic_smile_w);
        }
        if (emojiPopup != null) {
            try {
                emojiPopup.dismiss();
            } catch (Exception e) {
                //don't promt
            }
        }
        if (keyboardTransitionState == 0) {
            if (sizeNotifierFrameLayout != null) {
                if (post) {
                    sizeNotifierFrameLayout.post(new Runnable() {
                        public void run() {
                            if (sizeNotifierFrameLayout != null) {
                                sizeNotifierFrameLayout.setPadding(0, 0, 0, 0);
                                onWindowSizeChanged(sizeNotifierFrameLayout.getHeight());
                            }
                        }
                    });
                } else {
                    sizeNotifierFrameLayout.setPadding(0, 0, 0, 0);
                    onWindowSizeChanged(sizeNotifierFrameLayout.getHeight());
                }
            }
        }
    }

    public void hideEmojiPopup() {
        if (emojiPopup != null && emojiPopup.isShowing()) {
            showEmojiPopup(false, true);
        }
        setKeyboardTransitionState(4);
    }

    public void openKeyboard() {
        setKeyboardTransitionState(3);
        messageEditText.requestFocus();
        AndroidUtilities.showKeyboard(messageEditText);
    }

    public void closeKeyboard() {
        setKeyboardTransitionState(4);
        AndroidUtilities.hideKeyboard(messageEditText);
    }

    public void setDelegate(PhotoViewerCaptionEnterViewDelegate delegate) {
        this.delegate = delegate;
    }

    public void setFieldText(CharSequence text) {
        if (messageEditText == null) {
            return;
        }
        messageEditText.setText(text);
        messageEditText.setSelection(messageEditText.getText().length());
        if (delegate != null) {
            delegate.onTextChanged(messageEditText.getText(), true);
        }
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
            if (start + text.length() <= messageEditText.length()) {
                messageEditText.setSelection(start + text.length());
            } else {
                messageEditText.setSelection(messageEditText.length());
            }
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
            return getTrimmedString(messageEditText.getText().toString());
        }
        return null;
    }

    public CharSequence getFieldCharSequence() {
        return messageEditText.getText();
    }

    public boolean isEmojiPopupShowing() {
        return emojiPopup != null && emojiPopup.isShowing();
    }

    public boolean isKeyboardVisible() {
        return AndroidUtilities.usingHardwareInput && getLayoutParams() != null && ((RelativeLayout.LayoutParams) getLayoutParams()).bottomMargin == 0 || keyboardVisible;
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

        if (emojiPopup != null && emojiPopup.isShowing()) {
            int newHeight = 0;
            if (isWidthGreater) {
                newHeight = keyboardHeightLand;
            } else {
                newHeight = keyboardHeight;
            }
            WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) emojiPopup.getContentView().getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
                if (wm != null) {
                    wm.updateViewLayout(emojiPopup.getContentView(), layoutParams);
                    if (!keyboardVisible) {
                        if (sizeNotifierFrameLayout != null) {
                            sizeNotifierFrameLayout.setPadding(0, 0, 0, layoutParams.height);
                            sizeNotifierFrameLayout.requestLayout();
                            onWindowSizeChanged(sizeNotifierFrameLayout.getHeight() - sizeNotifierFrameLayout.getPaddingBottom());
                        }
                    }
                }
            }
        }

        boolean oldValue = keyboardVisible;
        keyboardVisible = height > 0;
        if (keyboardVisible && (sizeNotifierFrameLayout.getPaddingBottom() > 0 || keyboardTransitionState == 1)) {
            setKeyboardTransitionState(1);
        } else if (keyboardTransitionState != 2 && !keyboardVisible && keyboardVisible != oldValue && emojiPopup != null && emojiPopup.isShowing()) {
            showEmojiPopup(false, true);
        }
        if (keyboardTransitionState == 0) {
            onWindowSizeChanged(sizeNotifierFrameLayout.getHeight() - sizeNotifierFrameLayout.getPaddingBottom());
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
        } else if (id == NotificationCenter.hideEmojiKeyboard) {
            hideEmojiPopup();
        }
    }
}
