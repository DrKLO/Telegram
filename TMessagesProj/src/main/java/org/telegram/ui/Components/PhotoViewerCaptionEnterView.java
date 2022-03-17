/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Vibrator;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;

public class PhotoViewerCaptionEnterView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayoutPhoto.SizeNotifierFrameLayoutPhotoDelegate {

    private final ImageView doneButton;

    public int getCaptionLimitOffset() {
        return captionMaxLength - codePointCount;
    }

    public interface PhotoViewerCaptionEnterViewDelegate {
        void onCaptionEnter();
        void onTextChanged(CharSequence text);
        void onWindowSizeChanged(int size);
        void onEmojiViewCloseStart();
        void onEmojiViewCloseEnd();
    }

    private EditTextCaption messageEditText;
    private ImageView emojiButton;
    private ReplaceableIconDrawable emojiIconDrawable;
    private EmojiView emojiView;
    private SizeNotifierFrameLayoutPhoto sizeNotifierLayout;
    private Drawable doneDrawable;
    private Drawable checkDrawable;
    private NumberTextView captionLimitView;
    private int lineCount;
    private boolean isInitLineCount;
    private boolean shouldAnimateEditTextWithBounds;
    private int messageEditTextPredrawHeigth;
    private int messageEditTextPredrawScrollY;
    private float chatActivityEnterViewAnimateFromTop;

    private int lastSizeChangeValue1;
    private boolean lastSizeChangeValue2;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private int emojiPadding;

    private boolean forceFloatingEmoji;

    private boolean innerTextChange;
    private boolean popupAnimating;

    private int captionMaxLength = 1024;
    private int codePointCount;

    private PhotoViewerCaptionEnterViewDelegate delegate;

    boolean sendButtonEnabled = true;
    private float sendButtonEnabledProgress = 1f;
    private ValueAnimator sendButtonColorAnimator;

    private View windowView;

    private TextPaint lengthTextPaint;
    private String lengthText;
    private final Theme.ResourcesProvider resourcesProvider;

    public PhotoViewerCaptionEnterView(Context context, SizeNotifierFrameLayoutPhoto parent, final View window, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        paint.setColor(0x7f000000);
        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClipChildren(false);
        windowView = window;

        sizeNotifierLayout = parent;

        LinearLayout textFieldContainer = new LinearLayout(context);
        textFieldContainer.setClipChildren(false);
        textFieldContainer.setOrientation(LinearLayout.HORIZONTAL);
        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 2, 0, 0, 0));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setClipChildren(false);
        textFieldContainer.addView(frameLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        emojiButton = new ImageView(context);
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(1), 0, 0);
        emojiButton.setAlpha(0.58f);
        frameLayout.addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT));
        emojiButton.setOnClickListener(view -> {
            if (keyboardVisible || (AndroidUtilities.isInMultiwindow || AndroidUtilities.usingHardwareInput) && !isPopupShowing()) {
                showPopup(1, false);
            } else {
                openKeyboardInternal();
            }
        });
        emojiButton.setContentDescription(LocaleController.getString("Emoji", R.string.Emoji));
        emojiButton.setImageDrawable(emojiIconDrawable = new ReplaceableIconDrawable(context));
        emojiIconDrawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        emojiIconDrawable.setIcon(R.drawable.input_smile, false);

        lengthTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        lengthTextPaint.setTextSize(AndroidUtilities.dp(13));
        lengthTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        lengthTextPaint.setColor(0xffd9d9d9);

        messageEditText = new EditTextCaption(context, null) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                try {
                    isInitLineCount = getMeasuredWidth() == 0 && getMeasuredHeight() == 0;
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    if (isInitLineCount) {
                        lineCount = getLineCount();
                    }
                    isInitLineCount = false;
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

            @Override
            protected void extendActionMode(ActionMode actionMode, Menu menu) {
                PhotoViewerCaptionEnterView.this.extendActionMode(actionMode, menu);
            }

            @Override
            protected int getActionModeStyle() {
                return FloatingToolbar.STYLE_BLACK;
            }

            @Override
            public boolean requestRectangleOnScreen(Rect rectangle) {
                rectangle.bottom += AndroidUtilities.dp(1000);
                return super.requestRectangleOnScreen(rectangle);
            }
        };
        messageEditText.setOnFocusChangeListener((view, focused) -> {
            if (focused) {
                try {
                    messageEditText.setSelection(messageEditText.length(), messageEditText.length());
                } catch (Exception ignore) {}
            }
        });
        messageEditText.setSelectAllOnFocus(false);

        messageEditText.setDelegate(() -> messageEditText.invalidateEffects());
        messageEditText.setWindowView(windowView);
        messageEditText.setHint(LocaleController.getString("AddCaption", R.string.AddCaption));
        messageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messageEditText.setLinkTextColor(0xff76c2f1);
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
        messageEditText.setHighlightColor(0x4fffffff);
        messageEditText.setHintTextColor(0xb2ffffff);
        frameLayout.addView(messageEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 52, 0, 6, 0));
        messageEditText.setOnKeyListener((view, i, keyEvent) -> {
            if (i == KeyEvent.KEYCODE_BACK) {
                if (windowView != null && hideActionMode()) {
                    return true;
                } else if (!keyboardVisible && isPopupShowing()) {
                    if (keyEvent.getAction() == 1) {
                        showPopup(0, true);
                    }
                    return true;
                }
            }
            return false;
        });
        messageEditText.setOnClickListener(view -> {
            if (isPopupShowing()) {
                showPopup(AndroidUtilities.isInMultiwindow || AndroidUtilities.usingHardwareInput ? 0 : 2, false);
            }
        });
        messageEditText.addTextChangedListener(new TextWatcher() {
            boolean processChange = false;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (lineCount != messageEditText.getLineCount()) {
                    if (!isInitLineCount && messageEditText.getMeasuredWidth() > 0) {
                        onLineCountChanged(lineCount, messageEditText.getLineCount());
                    }
                    lineCount = messageEditText.getLineCount();
                }

                if (innerTextChange) {
                    return;
                }

                if (delegate != null) {
                    delegate.onTextChanged(charSequence);
                }
                if (count - before > 1) {
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
                if (!innerTextChange) {
                    if (processChange) {
                        ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);
                        for (int i = 0; i < spans.length; i++) {
                            editable.removeSpan(spans[i]);
                        }
                        Emoji.replaceEmoji(editable, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                        processChange = false;
                    }
                }

                int beforeLimit;
                codePointCount = Character.codePointCount(editable, 0, editable.length());
                boolean sendButtonEnabledLocal = true;
                if (captionMaxLength > 0 && (beforeLimit = captionMaxLength - codePointCount) <= 100) {
                    if (beforeLimit < -9999) {
                        beforeLimit = -9999;
                    }
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
                        sendButtonEnabledLocal = false;
                        captionLimitView.setTextColor(0xffEC7777);
                    } else {
                        captionLimitView.setTextColor(0xffffffff);
                    }
                } else {
                    captionLimitView.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setDuration(100).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            captionLimitView.setVisibility(View.GONE);
                        }
                    });
                }
                if (sendButtonEnabled != sendButtonEnabledLocal) {
                    sendButtonEnabled = sendButtonEnabledLocal;
                    if (sendButtonColorAnimator != null) {
                        sendButtonColorAnimator.cancel();
                    }
                    sendButtonColorAnimator = ValueAnimator.ofFloat(sendButtonEnabled ? 0 : 1f, sendButtonEnabled ? 1f : 0);
                    sendButtonColorAnimator.addUpdateListener(valueAnimator -> {
                        sendButtonEnabledProgress = (float) valueAnimator.getAnimatedValue();
                        int color = getThemedColor(Theme.key_dialogFloatingIcon);
                        int alpha = Color.alpha(color);
                        Theme.setDrawableColor(checkDrawable, ColorUtils.setAlphaComponent(color, (int) (alpha * (0.58f + 0.42f * sendButtonEnabledProgress))));
                        doneButton.invalidate();
                    });
                    sendButtonColorAnimator.setDuration(150).start();
                }
            }
        });

        doneDrawable = Theme.createCircleDrawable(AndroidUtilities.dp(16), 0xff66bffa);
        checkDrawable = context.getResources().getDrawable(R.drawable.input_done).mutate();
        CombinedDrawable combinedDrawable = new CombinedDrawable(doneDrawable, checkDrawable, 0, AndroidUtilities.dp(1));
        combinedDrawable.setCustomSize(AndroidUtilities.dp(32), AndroidUtilities.dp(32));

        doneButton = new ImageView(context);
        doneButton.setScaleType(ImageView.ScaleType.CENTER);
        doneButton.setImageDrawable(combinedDrawable);
        textFieldContainer.addView(doneButton, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));
        doneButton.setOnClickListener(view -> {
            if (captionMaxLength - codePointCount < 0) {
                AndroidUtilities.shakeView(captionLimitView, 2, 0);
                Vibrator v = (Vibrator) captionLimitView.getContext().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(200);
                }
                return;
            }
            delegate.onCaptionEnter();
        });
        doneButton.setContentDescription(LocaleController.getString("Done", R.string.Done));

        captionLimitView = new NumberTextView(context);
        captionLimitView.setVisibility(View.GONE);
        captionLimitView.setTextSize(15);
        captionLimitView.setTextColor(0xffffffff);
        captionLimitView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        captionLimitView.setCenterAlign(true);
        addView(captionLimitView, LayoutHelper.createFrame(48, 20, Gravity.BOTTOM | Gravity.RIGHT, 3, 0, 3, 48));
    }

    private void onLineCountChanged(int lineCountOld, int lineCountNew) {
        if (!TextUtils.isEmpty(messageEditText.getText())) {
            shouldAnimateEditTextWithBounds = true;
            messageEditTextPredrawHeigth = messageEditText.getMeasuredHeight();
            messageEditTextPredrawScrollY = messageEditText.getScrollY();
            invalidate();
        } else {
            messageEditText.animate().cancel();
            messageEditText.setOffsetY(0);
            shouldAnimateEditTextWithBounds = false;
        }
        chatActivityEnterViewAnimateFromTop = getTop() + offset;
    }

    float animationProgress = 0.0f;

    Paint paint = new Paint();
    float offset = 0;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.drawRect(0, offset, getMeasuredWidth(), getMeasuredHeight(), paint);
        canvas.clipRect(0, offset, getMeasuredWidth(), getMeasuredHeight());
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    ValueAnimator messageEditTextAnimator;
    ValueAnimator topBackgroundAnimator;
    @Override
    protected void onDraw(Canvas canvas) {
        if (shouldAnimateEditTextWithBounds) {
            float dy = (messageEditTextPredrawHeigth - messageEditText.getMeasuredHeight()) + (messageEditTextPredrawScrollY - messageEditText.getScrollY());
            messageEditText.setOffsetY(messageEditText.getOffsetY() - dy);
            ValueAnimator a = ValueAnimator.ofFloat(messageEditText.getOffsetY(), 0);
            a.addUpdateListener(animation -> messageEditText.setOffsetY((float) animation.getAnimatedValue()));
            if (messageEditTextAnimator != null) {
                messageEditTextAnimator.cancel();
            }
            messageEditTextAnimator = a;
            a.setDuration(200);
            a.setInterpolator(CubicBezierInterpolator.DEFAULT);
            a.start();
            shouldAnimateEditTextWithBounds = false;
        }

        if (chatActivityEnterViewAnimateFromTop != 0 && chatActivityEnterViewAnimateFromTop != getTop() + offset) {
            if (topBackgroundAnimator != null) {
                topBackgroundAnimator.cancel();
            }
            offset = chatActivityEnterViewAnimateFromTop - (getTop() + offset);
            topBackgroundAnimator = ValueAnimator.ofFloat(offset, 0);
            topBackgroundAnimator.addUpdateListener(valueAnimator -> {
                offset = (float) valueAnimator.getAnimatedValue();
                invalidate();
            });
            topBackgroundAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            topBackgroundAnimator.setDuration(200);
            topBackgroundAnimator.start();
            chatActivityEnterViewAnimateFromTop = 0;
        }

//        if (lengthText != null && getMeasuredHeight() > AndroidUtilities.dp(48)) {
//            int width = (int) Math.ceil(lengthTextPaint.measureText(lengthText));
//            int x = (AndroidUtilities.dp(56) - width) / 2;
//            canvas.drawText(lengthText, x, getMeasuredHeight() - AndroidUtilities.dp(48), lengthTextPaint);
//            if (animationProgress < 1.0f) {
//                animationProgress += 17.0f / 120.0f;
//                invalidate();
//                if (animationProgress >= 1.0f) {
//                    animationProgress = 1.0f;
//                }
//                lengthTextPaint.setAlpha((int) (255 * animationProgress));
//            }
//        } else {
//            lengthTextPaint.setAlpha(0);
//            animationProgress = 0.0f;
//        }
    }

    public void setForceFloatingEmoji(boolean value) {
        forceFloatingEmoji = value;
    }

    public void updateColors() {
        Theme.setDrawableColor(doneDrawable, getThemedColor(Theme.key_dialogFloatingButton));
        int color = getThemedColor(Theme.key_dialogFloatingIcon);
        int alpha = Color.alpha(color);
        Theme.setDrawableColor(checkDrawable, ColorUtils.setAlphaComponent(color, (int) (alpha * (0.58f + 0.42f * sendButtonEnabledProgress))));
        if (emojiView != null) {
            emojiView.updateColors();
        }
    }

    public boolean hideActionMode() {
        /*if (Build.VERSION.SDK_INT >= 23 && currentActionMode != null) {
            try {
                currentActionMode.finish();
            } catch (Exception e) {
                FileLog.e(e);
            }
            currentActionMode = null;
            return true;
        }*/
        return false;
    }

    protected void extendActionMode(ActionMode actionMode, Menu menu) {

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
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        sizeNotifierLayout.setDelegate(this);
    }

    public void onDestroy() {
        hidePopup();
        if (isKeyboardVisible()) {
            closeKeyboard();
        }
        keyboardVisible = false;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
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
        captionMaxLength = MessagesController.getInstance(UserConfig.selectedAccount).maxCaptionLength;
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
        emojiView = new EmojiView(false, false, getContext(), false, null, null, null);
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
            messageEditText.setSelection(Math.min(start + text.length(), messageEditText.length()));
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

    int lastShow;
    private void showPopup(int show, boolean animated) {
        lastShow = show;
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
                emojiIconDrawable.setIcon(R.drawable.input_keyboard, true);
                onWindowSizeChanged();
            }
        } else {
            if (emojiButton != null) {
                emojiIconDrawable.setIcon(R.drawable.input_smile, true);
            }
            if (sizeNotifierLayout != null) {
                if (animated && SharedConfig.smoothKeyboard && show == 0 && emojiView != null) {
                    ValueAnimator animator = ValueAnimator.ofFloat(emojiPadding, 0);
                    float animateFrom = emojiPadding;
                    popupAnimating = true;
                    delegate.onEmojiViewCloseStart();
                    animator.addUpdateListener(animation -> {
                        float v = (float) animation.getAnimatedValue();
                        emojiPadding = (int) v;
                        emojiView.setTranslationY(animateFrom - v);
                        setTranslationY(animateFrom - v);
                        setAlpha(v / animateFrom);
                        emojiView.setAlpha(v / animateFrom);
                    });
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            emojiPadding = 0;
                            setTranslationY(0);
                            setAlpha(1f);
                            emojiView.setTranslationY(0);
                            popupAnimating = false;
                            delegate.onEmojiViewCloseEnd();
                            emojiView.setVisibility(GONE);
                            emojiView.setAlpha(1f);
                        }
                    });
                    animator.setDuration(210);
                    animator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                    animator.start();
                } else if (show == 0) {
                    if (emojiView != null) {
                        emojiView.setVisibility(GONE);
                    }
                    emojiPadding = 0;
                } else {
                    if (!SharedConfig.smoothKeyboard && emojiView != null) {
                        emojiView.setVisibility(GONE);
                    }
                }
                sizeNotifierLayout.requestLayout();
                onWindowSizeChanged();
            }
        }
    }

    public void hidePopup() {
        if (isPopupShowing()) {
            showPopup(0, true);
        }
    }

    private void openKeyboardInternal() {
        showPopup(AndroidUtilities.isInMultiwindow || AndroidUtilities.usingHardwareInput ? 0 : 2, false);
        openKeyboard();
    }

    public void openKeyboard() {
        messageEditText.requestFocus();
        AndroidUtilities.showKeyboard(messageEditText);
        try {
            messageEditText.setSelection(messageEditText.length(), messageEditText.length());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean isPopupShowing() {
        return emojiView != null && emojiView.getVisibility() == VISIBLE;
    }

    public boolean isPopupAnimatig() {
        return popupAnimating;
    }

    public void closeKeyboard() {
        AndroidUtilities.hideKeyboard(messageEditText);
        messageEditText.clearFocus();
    }

    public boolean isKeyboardVisible() {
        return (AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) && getTag() != null || keyboardVisible;
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
            showPopup(0, false);
        }
        if (emojiPadding != 0 && !keyboardVisible && keyboardVisible != oldValue && !isPopupShowing()) {
            emojiPadding = 0;
            sizeNotifierLayout.requestLayout();
        }
        onWindowSizeChanged();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
        }
    }

    public void setAllowTextEntitiesIntersection(boolean value) {
        messageEditText.setAllowTextEntitiesIntersection(value);
    }

    public EditTextCaption getMessageEditText() {
        return messageEditText;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
