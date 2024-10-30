/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SuggestEmojiView;

import java.util.ArrayList;

public class PollEditTextCell extends FrameLayout implements SuggestEmojiView.AnchorViewDelegate {

    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_EMOJI = 1;

    private EditTextBoldCursor textView;
    private ImageView deleteImageView;
    private ImageView moveImageView;
    private SimpleTextView textView2;
    private CheckBox2 checkBox;
    private boolean showNextButton;
    private boolean needDivider;
    private AnimatorSet checkBoxAnimation;
    private boolean alwaysShowText2;
    private ChatActivityEnterViewAnimatedIconView emojiButton;
    private ValueAnimator valueAnimator;

    public PollEditTextCell(Context context, OnClickListener onDelete) {
        this(context, false, TYPE_DEFAULT, onDelete);
    }

    public PollEditTextCell(Context context, boolean caption, int type, OnClickListener onDelete) {
        super(context);

        textView = new EditTextCaption(context, null) {
            @Override
            protected int emojiCacheType() {
                return AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW;
            }

            @Override
            public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                InputConnection conn = super.onCreateInputConnection(outAttrs);
                if (showNextButton) {
                    outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
                }
                return conn;
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                onEditTextDraw(this, canvas);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (!isEnabled()) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onFieldTouchUp(this);
                }
                return super.onTouchEvent(event);
            }

            @Override
            protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
                super.onFocusChanged(focused, direction, previouslyFocusedRect);
                onEditTextFocusChanged(focused);
            }

            @Override
            public ActionMode startActionMode(ActionMode.Callback callback, int type) {
                ActionMode actionMode = super.startActionMode(callback, type);
                onActionModeStart(this, actionMode);
                return actionMode;
            }

            @Override
            public ActionMode startActionMode(ActionMode.Callback callback) {
                ActionMode actionMode = super.startActionMode(callback);
                onActionModeStart(this, actionMode);
                return actionMode;
            }
        };
        ((EditTextCaption) textView).setAllowTextEntitiesIntersection(true);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setMaxLines(type == TYPE_EMOJI ? 4 : Integer.MAX_VALUE);
//        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setBackgroundDrawable(null);
        textView.setImeOptions(textView.getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        textView.setInputType(textView.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textView.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(11));

        if (onDelete != null) {
            int endMargin = type == TYPE_EMOJI ? 102: 58;
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? endMargin : 64, 0, !LocaleController.isRTL ? endMargin : 64, 0));

            moveImageView = new ImageView(context);
            moveImageView.setFocusable(false);
            moveImageView.setScaleType(ImageView.ScaleType.CENTER);
            moveImageView.setImageResource(R.drawable.poll_reorder);
            moveImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            addView(moveImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 6, 2, 6, 0));

            deleteImageView = new ImageView(context);
            deleteImageView.setFocusable(false);
            deleteImageView.setScaleType(ImageView.ScaleType.CENTER);
            deleteImageView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector)));
            deleteImageView.setImageResource(R.drawable.poll_remove);
            deleteImageView.setOnClickListener(onDelete);
            deleteImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            deleteImageView.setContentDescription(LocaleController.getString(R.string.Delete));
            addView(deleteImageView, LayoutHelper.createFrame(48, 50, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 3 : 0, 0, LocaleController.isRTL ? 0 : 3, 0));

            textView2 = new SimpleTextView(context);
            textView2.setTextSize(13);
            textView2.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
            addView(textView2, LayoutHelper.createFrame(48, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 20 : 0, 43, LocaleController.isRTL ? 0 : 20, 0));

            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(-1, Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_checkboxCheck);
            checkBox.setContentDescription(LocaleController.getString(R.string.AccDescrQuizCorrectAnswer));
            checkBox.setDrawUnchecked(true);
            checkBox.setChecked(true, false);
            checkBox.setAlpha(0.0f);
            checkBox.setDrawBackgroundAsArc(8);
            addView(checkBox, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 6, 2, 6, 0));
            checkBox.setOnClickListener(v -> {
                if (checkBox.getTag() == null) {
                    return;
                }
                onCheckBoxClick(PollEditTextCell.this, !checkBox.isChecked());
            });
        } else {
            int endMargin = type == TYPE_EMOJI ? 80: 19;
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,  LocaleController.isRTL ? endMargin : 19, 0, LocaleController.isRTL ? 19 : endMargin, 0));
        }

        if (type == TYPE_EMOJI) {
            emojiButton = new ChatActivityEnterViewAnimatedIconView(context);
            emojiButton.setAlpha(0.80f);
            emojiButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.SRC_IN));
            emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
            int padding = dp(9.5f);
            emojiButton.setPadding(padding, padding, padding, padding);
            emojiButton.setVisibility(View.GONE);
            int endMargin = deleteImageView == null ? 3 : 48;
            addView(emojiButton, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? endMargin : 0, 0, LocaleController.isRTL ? 0 : endMargin, 0));
            if (Build.VERSION.SDK_INT >= 21) {
                emojiButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector)));
            }
            emojiButton.setOnClickListener(view -> {
                onEmojiButtonClicked(this);
            });
            emojiButton.setContentDescription(LocaleController.getString(R.string.Emoji));
        }
    }

    protected void onEditTextFocusChanged(boolean focused) {

    }

    public void createErrorTextView() {
        alwaysShowText2 = true;
        textView2 = new SimpleTextView(getContext());
        textView2.setTextSize(13);
        textView2.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        addView(textView2, LayoutHelper.createFrame(48, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 20 : 0, 17, LocaleController.isRTL ? 0 : 20, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (deleteImageView != null) {
            deleteImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }
        if (emojiButton != null) {
            emojiButton.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }
        if (moveImageView != null) {
            moveImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }
        if (textView2 != null) {
            textView2.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
        }
        if (checkBox != null) {
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }
        int right;
        if (textView2 == null) {
            right = 42;
        } else if (deleteImageView == null) {
            right = 70;
        } else {
            if (emojiButton != null) {
                right = 174;
            } else {
                right = 122;
            }
        }
        textView.measure(MeasureSpec.makeMeasureSpec(width - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(right), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int h = textView.getMeasuredHeight();
        setMeasuredDimension(width, Math.max(AndroidUtilities.dp(50), textView.getMeasuredHeight()) + (needDivider ? 1 : 0));
        if (textView2 != null && !alwaysShowText2) {
            textView2.setAlpha(h >= AndroidUtilities.dp(52) ? 1.0f : 0.0f);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (checkBox != null) {
            setShowCheckBox(shouldShowCheckBox(), false);
            checkBox.setChecked(isChecked(this), false);
        }
    }

    protected void onCheckBoxClick(PollEditTextCell editText, boolean checked) {
        checkBox.setChecked(checked, true);
    }

    protected boolean isChecked(PollEditTextCell editText) {
        return false;
    }

    protected void onActionModeStart(EditTextBoldCursor editText, ActionMode actionMode) {

    }

    public void callOnDelete() {
        if (deleteImageView == null) {
            return;
        }
        deleteImageView.callOnClick();
    }

    public void setShowNextButton(boolean value) {
        showNextButton = value;
    }

    public EditTextBoldCursor getTextView() {
        return textView;
    }

    public CheckBox2 getCheckBox() {
        return checkBox;
    }

    public void addTextWatcher(TextWatcher watcher) {
        textView.addTextChangedListener(watcher);
    }

    protected boolean drawDivider() {
        return true;
    }

    protected void onEditTextDraw(EditTextBoldCursor editText, Canvas canvas) {

    }

    protected boolean shouldShowCheckBox() {
        return false;
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    public String getText() {
        return textView.getText().toString();
    }

    public int length() {
        return textView.length();
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setShowCheckBox(boolean show, boolean animated) {
        if (show == (checkBox.getTag() != null)) {
            return;
        }
        if (checkBoxAnimation != null) {
            checkBoxAnimation.cancel();
            checkBoxAnimation = null;
        }
        checkBox.setTag(show ? 1 : null);
        if (animated) {
            checkBoxAnimation = new AnimatorSet();
            checkBoxAnimation.playTogether(
                    ObjectAnimator.ofFloat(checkBox, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(moveImageView, View.ALPHA, show ? 0.0f : 1.0f));
            checkBoxAnimation.setDuration(180);
            checkBoxAnimation.start();
        } else {
            checkBox.setAlpha(show ? 1.0f : 0.0f);
            moveImageView.setAlpha(show ? 0.0f : 1.0f);
        }
    }

    public void setText(CharSequence text, boolean divider) {
        textView.setText(text);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndHint(CharSequence text, String hint, boolean divider) {
        if (deleteImageView != null) {
            deleteImageView.setTag(null);
        }
        textView.setText(text);
        if (!TextUtils.isEmpty(text)) {
            textView.setSelection(textView.length());
        }
        textView.setHint(hint);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public ChatActivityEnterViewAnimatedIconView getEmojiButton() {
        return emojiButton;
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        setEnabled(value);
    }

    protected void onFieldTouchUp(EditTextBoldCursor editText) {

    }

    protected void onEmojiButtonClicked(PollEditTextCell cell) {

    }

    public void setText2(String text) {
        if (textView2 == null) {
            return;
        }
        textView2.setText(text);
    }

    public SimpleTextView getTextView2() {
        return textView2;
    }

    public void setEmojiButtonVisibility(boolean visible) {
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        if (visible) {
            emojiButton.setVisibility(View.VISIBLE);
            emojiButton.setScaleX(0f);
            emojiButton.setScaleY(0f);
            emojiButton.setAlpha(0f);
        }
        valueAnimator = ValueAnimator.ofFloat(visible ? 0 : 1, visible ? 1 : 0);
        valueAnimator.addUpdateListener(animation -> {
            float value = (Float) animation.getAnimatedValue();
            emojiButton.setScaleX(value);
            emojiButton.setScaleY(value);
            emojiButton.setAlpha(Math.max(value, 0.80f));
            if (textView2 != null && deleteImageView == null && textView2.getVisibility() == View.VISIBLE) {
                textView2.setTranslationY(AndroidUtilities.dp(26) * value);
            }
        });
        valueAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {

            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (!visible) {
                    emojiButton.setVisibility(View.GONE);
                } else {
                    emojiButton.setScaleX(1f);
                    emojiButton.setScaleY(1f);
                    emojiButton.setAlpha(0.80f);
                }
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {

            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {

            }
        });
        valueAnimator.setDuration(200L);
        valueAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider && drawDivider()) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(moveImageView != null ? 63 : 20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(moveImageView != null ? 63 : 20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public BaseFragment getParentFragment() {
        return null;
    }

    @Override
    public void setFieldText(CharSequence text) {
        textView.setText(text);
    }

    @Override
    public void addTextChangedListener(TextWatcher watcher) {
        textView.addTextChangedListener(watcher);
    }

    @Override
    public EditTextBoldCursor getEditField() {
        return textView;
    }

    @Override
    public CharSequence getFieldText() {
        if (textView.length() > 0) {
            return textView.getText();
        }
        return null;
    }

    @Override
    public Editable getEditText() {
        return textView.getText();
    }
}
