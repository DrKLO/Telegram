/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PhotoViewerCaptionEnterView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayoutPhoto.SizeNotifierFrameLayoutPhotoDelegate {

    public interface PhotoViewerCaptionEnterViewDelegate {
        void onCaptionEnter();
        void onTextChanged(CharSequence text);
        void onWindowSizeChanged(int size);
    }

    private EditTextCaption messageEditText;
    private ImageView emojiButton;
    private EmojiView emojiView;
    private SizeNotifierFrameLayoutPhoto sizeNotifierLayout;

    private ActionMode currentActionMode;

    private AnimatorSet runningAnimation;
    private AnimatorSet runningAnimation2;
    private ObjectAnimator runningAnimationAudio;
    private int runningAnimationType;
    private int audioInterfaceState;

    private int lastSizeChangeValue1;
    private boolean lastSizeChangeValue2;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private int emojiPadding;

    private boolean forceFloatingEmoji;

    private boolean innerTextChange;

    private int captionMaxLength = 1024;

    private PhotoViewerCaptionEnterViewDelegate delegate;

    private View windowView;

    private TextPaint lengthTextPaint;
    private String lengthText;

    public PhotoViewerCaptionEnterView(Context context, SizeNotifierFrameLayoutPhoto parent, final View window) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(0x7f000000);
        setFocusable(true);
        setFocusableInTouchMode(true);
        windowView = window;

        sizeNotifierLayout = parent;

        LinearLayout textFieldContainer = new LinearLayout(context);
        textFieldContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 2, 0, 0, 0));

        FrameLayout frameLayout = new FrameLayout(context);
        textFieldContainer.addView(frameLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        emojiButton = new ImageView(context);
        emojiButton.setImageResource(R.drawable.input_smile);
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(1), 0, 0);
        frameLayout.addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT));
        emojiButton.setOnClickListener(view -> {
            if (!isPopupShowing()) {
                showPopup(1);
            } else {
                openKeyboardInternal();
            }
        });
        emojiButton.setContentDescription(LocaleController.getString("Emoji", R.string.Emoji));

        lengthTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        lengthTextPaint.setTextSize(AndroidUtilities.dp(13));
        lengthTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        lengthTextPaint.setColor(0xffd9d9d9);

        messageEditText = new EditTextCaption(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                try {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                } catch (Exception e) {
                    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(51));
                    FileLog.e(e);
                }
            }

            @Override
            protected void onSelectionChanged(int selStart, int selEnd) {
                super.onSelectionChanged(selStart, selEnd);
                if (selStart != selEnd) {
                    fixHandleView(false);
                } else {
                    fixHandleView(true);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 23 && windowView != null) {
            messageEditText.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    currentActionMode = mode;
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        fixActionMode(mode);
                    }
                    return true;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    if (currentActionMode == mode) {
                        currentActionMode = null;
                    }
                }
            });

            messageEditText.setCustomInsertionActionModeCallback(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    currentActionMode = mode;
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        fixActionMode(mode);
                    }
                    return true;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    if (currentActionMode == mode) {
                        currentActionMode = null;
                    }
                }
            });
        }
        messageEditText.setHint(LocaleController.getString("AddCaption", R.string.AddCaption));
        messageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messageEditText.setInputType(messageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageEditText.setMaxLines(4);
        messageEditText.setHorizontallyScrolling(false);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messageEditText.setGravity(Gravity.BOTTOM);
        messageEditText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        messageEditText.setBackgroundDrawable(null);
        messageEditText.setCursorColor(0xffffffff);
        messageEditText.setCursorSize(AndroidUtilities.dp(20));
        messageEditText.setTextColor(0xffffffff);
        messageEditText.setHintTextColor(0xb2ffffff);
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(captionMaxLength);
        messageEditText.setFilters(inputFilters);
        frameLayout.addView(messageEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 52, 0, 6, 0));
        messageEditText.setOnKeyListener((view, i, keyEvent) -> {
            if (i == KeyEvent.KEYCODE_BACK) {
                if (windowView != null && hideActionMode()) {
                    return true;
                } else if (!keyboardVisible && isPopupShowing()) {
                    if (keyEvent.getAction() == 1) {
                        showPopup(0);
                    }
                    return true;
                }
            }
            return false;
        });
        messageEditText.setOnClickListener(view -> {
            if (isPopupShowing()) {
                showPopup(AndroidUtilities.usingHardwareInput ? 0 : 2);
            }
        });
        messageEditText.addTextChangedListener(new TextWatcher() {
            boolean processChange = false;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (innerTextChange) {
                    return;
                }

                if (delegate != null) {
                    delegate.onTextChanged(charSequence);
                }

                if (before != count && (count - before) > 1) {
                    processChange = true;
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                int charactersLeft = captionMaxLength - messageEditText.length();
                if (charactersLeft <= 128) {
                    lengthText = String.format("%d", charactersLeft);
                } else {
                    lengthText = null;
                }
                PhotoViewerCaptionEnterView.this.invalidate();
                if (innerTextChange) {
                    return;
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

        Drawable drawable = Theme.createCircleDrawable(AndroidUtilities.dp(16), 0xff66bffa);
        Drawable checkDrawable = context.getResources().getDrawable(R.drawable.input_done).mutate();
        CombinedDrawable combinedDrawable = new CombinedDrawable(drawable, checkDrawable, 0, AndroidUtilities.dp(1));
        combinedDrawable.setCustomSize(AndroidUtilities.dp(32), AndroidUtilities.dp(32));

        ImageView doneButton = new ImageView(context);
        doneButton.setScaleType(ImageView.ScaleType.CENTER);
        doneButton.setImageDrawable(combinedDrawable);
        textFieldContainer.addView(doneButton, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));
        doneButton.setOnClickListener(view -> delegate.onCaptionEnter());
        doneButton.setContentDescription(LocaleController.getString("Done", R.string.Done));
    }

    float animationProgress = 0.0f;

    @Override
    protected void onDraw(Canvas canvas) {
        if (lengthText != null && getMeasuredHeight() > AndroidUtilities.dp(48)) {
            int width = (int) Math.ceil(lengthTextPaint.measureText(lengthText));
            int x = (AndroidUtilities.dp(56) - width) / 2;
            canvas.drawText(lengthText, x, getMeasuredHeight() - AndroidUtilities.dp(48), lengthTextPaint);
            if (animationProgress < 1.0f) {
                animationProgress += 17.0f / 120.0f;
                invalidate();
                if (animationProgress >= 1.0f) {
                    animationProgress = 1.0f;
                }
                lengthTextPaint.setAlpha((int) (255 * animationProgress));
            }
        } else {
            lengthTextPaint.setAlpha(0);
            animationProgress = 0.0f;
        }
    }

    public void setForceFloatingEmoji(boolean value) {
        forceFloatingEmoji = value;
    }

    public boolean hideActionMode() {
        if (Build.VERSION.SDK_INT >= 23 && currentActionMode != null) {
            try {
                currentActionMode.finish();
            } catch (Exception e) {
                FileLog.e(e);
            }
            currentActionMode = null;
            return true;
        }
        return false;
    }

    @SuppressLint("PrivateApi")
    @SuppressWarnings("unchecked")
    private void fixActionMode(ActionMode mode) {
        try {
            Class classActionMode = Class.forName("com.android.internal.view.FloatingActionMode");
            Field fieldToolbar = classActionMode.getDeclaredField("mFloatingToolbar");
            fieldToolbar.setAccessible(true);
            Object toolbar = fieldToolbar.get(mode);

            Class classToolbar = Class.forName("com.android.internal.widget.FloatingToolbar");
            Field fieldToolbarPopup = classToolbar.getDeclaredField("mPopup");
            Field fieldToolbarWidth = classToolbar.getDeclaredField("mWidthChanged");
            fieldToolbarPopup.setAccessible(true);
            fieldToolbarWidth.setAccessible(true);
            Object popup = fieldToolbarPopup.get(toolbar);

            Class classToolbarPopup = Class.forName("com.android.internal.widget.FloatingToolbar$FloatingToolbarPopup");
            Field fieldToolbarPopupParent = classToolbarPopup.getDeclaredField("mParent");
            fieldToolbarPopupParent.setAccessible(true);

            View currentView = (View) fieldToolbarPopupParent.get(popup);
            if (currentView != windowView) {
                fieldToolbarPopupParent.set(popup, windowView);

                Method method = classActionMode.getDeclaredMethod("updateViewLocationInWindow");
                method.setAccessible(true);
                method.invoke(mode);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void onWindowSizeChanged() {
        int size = sizeNotifierLayout.getHeight();
        if (!keyboardVisible) {
            size -= emojiPadding;
        }
        if (delegate != null) {
            delegate.onWindowSizeChanged(size);
        }
    }

    public void onCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoad);
        sizeNotifierLayout.setDelegate(this);
    }

    public void onDestroy() {
        hidePopup();
        if (isKeyboardVisible()) {
            closeKeyboard();
        }
        keyboardVisible = false;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoad);
        if (sizeNotifierLayout != null) {
            sizeNotifierLayout.setDelegate(null);
        }
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
            delegate.onTextChanged(messageEditText.getText());
        }
        int old = captionMaxLength;
        captionMaxLength = MessagesController.getInstance(UserConfig.selectedAccount).maxCaptionLength;
        if (old != captionMaxLength) {
            InputFilter[] inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(captionMaxLength);
            messageEditText.setFilters(inputFilters);
        }
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

    public int getCursorPosition() {
        if (messageEditText == null) {
            return 0;
        }
        return messageEditText.getSelectionStart();
    }

    private void createEmojiView() {
        if (emojiView != null) {
            return;
        }
        emojiView = new EmojiView(false, false, getContext(), false, null);
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
                if (messageEditText.length() + symbol.length() > captionMaxLength) {
                    return;
                }
                int i = messageEditText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    innerTextChange = true;
                    CharSequence localCharSequence = Emoji.replaceEmoji(symbol, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    messageEditText.setText(messageEditText.getText().insert(i, localCharSequence));
                    int j = i + localCharSequence.length();
                    messageEditText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    innerTextChange = false;
                }
            }
        });
        sizeNotifierLayout.addView(emojiView);
    }

    public void addEmojiToRecent(String code) {
        createEmojiView();
        emojiView.addEmojiToRecent(code);
    }

    public void replaceWithText(int start, int len, CharSequence text, boolean parseEmoji) {
        try {
            SpannableStringBuilder builder = new SpannableStringBuilder(messageEditText.getText());
            builder.replace(start, start + len, text);
            if (parseEmoji) {
                Emoji.replaceEmoji(builder, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            }
            messageEditText.setText(builder);
            if (start + text.length() <= messageEditText.length()) {
                messageEditText.setSelection(start + text.length());
            } else {
                messageEditText.setSelection(messageEditText.length());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setFieldFocused(boolean focus) {
        if (messageEditText == null) {
            return;
        }
        if (focus) {
            if (!messageEditText.isFocused()) {
                messageEditText.postDelayed(() -> {
                    if (messageEditText != null) {
                        try {
                            messageEditText.requestFocus();
                        } catch (Exception e) {
                            FileLog.e(e);
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

    public CharSequence getFieldCharSequence() {
        return AndroidUtilities.getTrimmedString(messageEditText.getText());
    }

    public int getEmojiPadding() {
        return emojiPadding;
    }

    public boolean isPopupView(View view) {
        return view == emojiView;
    }

    private void showPopup(int show) {
        if (show == 1) {
            if (emojiView == null) {
                createEmojiView();
            }

            emojiView.setVisibility(VISIBLE);

            if (keyboardHeight <= 0) {
                keyboardHeight = MessagesController.getGlobalEmojiSettings().getInt("kbd_height", AndroidUtilities.dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = MessagesController.getGlobalEmojiSettings().getInt("kbd_height_land3", AndroidUtilities.dp(200));
            }
            int currentHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
            layoutParams.width = AndroidUtilities.displaySize.x;
            layoutParams.height = currentHeight;
            emojiView.setLayoutParams(layoutParams);
            if (!AndroidUtilities.isInMultiwindow && !forceFloatingEmoji) {
                AndroidUtilities.hideKeyboard(messageEditText);
            }
            if (sizeNotifierLayout != null) {
                emojiPadding = currentHeight;
                sizeNotifierLayout.requestLayout();
                emojiButton.setImageResource(R.drawable.input_keyboard);
                onWindowSizeChanged();
            }
        } else {
            if (emojiButton != null) {
                emojiButton.setImageResource(R.drawable.input_smile);
            }
            if (emojiView != null) {
                emojiView.setVisibility(GONE);
            }
            if (sizeNotifierLayout != null) {
                if (show == 0) {
                    emojiPadding = 0;
                }
                sizeNotifierLayout.requestLayout();
                onWindowSizeChanged();
            }
        }
    }

    public void hidePopup() {
        if (isPopupShowing()) {
            showPopup(0);
        }
    }

    private void openKeyboardInternal() {
        showPopup(AndroidUtilities.usingHardwareInput ? 0 : 2);
        openKeyboard();
    }

    public void openKeyboard() {
        int currentSelection;
        try {
            currentSelection = messageEditText.getSelectionStart();
        } catch (Exception e) {
            currentSelection = messageEditText.length();
            FileLog.e(e);
        }
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        messageEditText.onTouchEvent(event);
        event.recycle();
        event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 0, 0);
        messageEditText.onTouchEvent(event);
        event.recycle();
        AndroidUtilities.showKeyboard(messageEditText);
        try {
            messageEditText.setSelection(currentSelection);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean isPopupShowing() {
        return emojiView != null && emojiView.getVisibility() == VISIBLE;
    }

    public void closeKeyboard() {
        AndroidUtilities.hideKeyboard(messageEditText);
    }

    public boolean isKeyboardVisible() {
        return AndroidUtilities.usingHardwareInput && getTag() != null || keyboardVisible;
    }

    @Override
    public void onSizeChanged(int height, boolean isWidthGreater) {
        if (height > AndroidUtilities.dp(50) && keyboardVisible && !AndroidUtilities.isInMultiwindow && !forceFloatingEmoji) {
            if (isWidthGreater) {
                keyboardHeightLand = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }

        if (isPopupShowing()) {
            int newHeight;
            if (isWidthGreater) {
                newHeight = keyboardHeightLand;
            } else {
                newHeight = keyboardHeight;
            }

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                emojiView.setLayoutParams(layoutParams);
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
            showPopup(0);
        }
        if (emojiPadding != 0 && !keyboardVisible && keyboardVisible != oldValue && !isPopupShowing()) {
            emojiPadding = 0;
            sizeNotifierLayout.requestLayout();
        }
        onWindowSizeChanged();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiDidLoad) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
        }
    }

    public void setAllowTextEntitiesIntersection(boolean value) {
        messageEditText.setAllowTextEntitiesIntersection(value);
    }
}
