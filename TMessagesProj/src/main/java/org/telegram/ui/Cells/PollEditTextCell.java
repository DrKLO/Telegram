/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class PollEditTextCell extends FrameLayout {

    private EditTextBoldCursor textView;
    private ImageView deleteImageView;
    private ImageView moveImageView;
    private SimpleTextView textView2;
    private CheckBox2 checkBox;
    private boolean showNextButton;
    private boolean needDivider;
    private AnimatorSet checkBoxAnimation;
    private boolean alwaysShowText2;

    public PollEditTextCell(Context context, OnClickListener onDelete) {
        this(context, false, onDelete);
    }

    public PollEditTextCell(Context context, boolean caption, OnClickListener onDelete) {
        super(context);

        if (caption) {
            textView = new EditTextCaption(context, null) {
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
        } else {
            textView = new EditTextBoldCursor(context) {
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
            };
        }
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setBackgroundDrawable(null);
        textView.setImeOptions(textView.getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        textView.setInputType(textView.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textView.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(11));

        if (onDelete != null) {
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 58 : 64, 0, !LocaleController.isRTL ? 58 : 64, 0));

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
            deleteImageView.setContentDescription(LocaleController.getString("Delete", R.string.Delete));
            addView(deleteImageView, LayoutHelper.createFrame(48, 50, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 3 : 0, 0, LocaleController.isRTL ? 0 : 3, 0));

            textView2 = new SimpleTextView(context);
            textView2.setTextSize(13);
            textView2.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
            addView(textView2, LayoutHelper.createFrame(48, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 20 : 0, 43, LocaleController.isRTL ? 0 : 20, 0));

            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(null, Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_checkboxCheck);
            checkBox.setContentDescription(LocaleController.getString("AccDescrQuizCorrectAnswer", R.string.AccDescrQuizCorrectAnswer));
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
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 19, 0, 19, 0));
        }
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
            right = 122;
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

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        setEnabled(value);
    }

    protected void onFieldTouchUp(EditTextBoldCursor editText) {

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

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider && drawDivider()) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(moveImageView != null ? 63 : 20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(moveImageView != null ? 63 : 20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
