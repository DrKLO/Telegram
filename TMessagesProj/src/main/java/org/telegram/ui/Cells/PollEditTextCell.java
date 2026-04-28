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
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SuggestEmojiView;
import org.telegram.ui.Components.poll.PollAttachButton;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class PollEditTextCell extends FrameLayout implements SuggestEmojiView.AnchorViewDelegate, FactorAnimator.Target {
    private static final int ANIMATOR_ID_CHECKBOX_MULTISELECT = 0;
    private static final int ANIMATOR_ID_EMOJI_BUTTON_VISIBLE = 1;

    private final BoolAnimator animatorCheckboxMultiselect = new BoolAnimator(ANIMATOR_ID_CHECKBOX_MULTISELECT,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 380L);

    private final BoolAnimator animatorEmojiButtonVisible = new BoolAnimator(ANIMATOR_ID_EMOJI_BUTTON_VISIBLE,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 380L);


    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_EMOJI = 1;
    private final Theme.ResourcesProvider resourcesProvider;

    public EditTextBoldCursor textView;
    public PollAttachButton attachView;
    public ImageView deleteImageView;
    public ImageView moveImageView;
    private SimpleTextView textView2;
    private CheckBox2 checkBox;
    private boolean showNextButton;
    private boolean needDivider;
    private AnimatorSet checkBoxAnimation;
    private boolean alwaysShowText2;
    private ChatActivityEnterViewAnimatedIconView emojiButton;

    public PollEditTextCell(Context context, OnClickListener onDelete) {
        this(context, false, TYPE_DEFAULT, onDelete);
    }

    public PollEditTextCell(Context context, boolean caption, int type, OnClickListener onDelete) {
        this(context, caption, type, onDelete, null);
    }

    public PollEditTextCell(Context context, boolean caption, int type, OnClickListener onDelete, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.resourcesProvider = resourcesProvider;
        textView = new EditTextCaption(context, resourcesProvider) {
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

            @Override
            public boolean onTextContextMenuItem(int id) {
                if (id == android.R.id.paste) {
                    ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clipData = clipboard.getPrimaryClip();
                    if (clipData != null && clipData.getItemCount() == 1 && AndroidUtilities.charSequenceIndexOf(clipData.getItemAt(0).getText(), "\n") > 0) {
                        CharSequence text = clipData.getItemAt(0).getText();
                        ArrayList<CharSequence> parts = new ArrayList<>();
                        StringBuilder current = new StringBuilder();
                        for (int i = 0; i < text.length(); i++) {
                            char c = text.charAt(i);
                            if (c == '\n') {
                                parts.add(current.toString());
                                current.setLength(0);
                            } else {
                                current.append(c);
                            }
                        }
                        if (!TextUtils.isEmpty(current)) {
                            parts.add(current);
                        }
                        if (onPastedMultipleLines(parts)) {
                            return true;
                        }
                    }
                }
                return super.onTextContextMenuItem(id);
            }
        };
        ((EditTextCaption) textView).setAllowTextEntitiesIntersection(true);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        textView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setMaxLines(Integer.MAX_VALUE);
        textView.setBackground(null);
        textView.setImeOptions(textView.getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        textView.setInputType(textView.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textView.setPadding(dp(4), dp(10), dp(4), dp(11));

        if (onDelete != null) {
            int endMargin = type == TYPE_EMOJI ? 92 : 58;
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? endMargin : 54, 0, !LocaleController.isRTL ? endMargin : 54, 0));

            moveImageView = new ImageView(context);
            moveImageView.setFocusable(false);
            moveImageView.setScaleType(ImageView.ScaleType.CENTER);
            moveImageView.setImageResource(R.drawable.menu_poll_order_24);
            moveImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            addView(moveImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 6, 2, 6, 0));

            deleteImageView = new ImageView(context);
            deleteImageView.setFocusable(false);
            deleteImageView.setScaleType(ImageView.ScaleType.CENTER);
            deleteImageView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector, resourcesProvider)));
            deleteImageView.setImageResource(R.drawable.poll_remove);
            deleteImageView.setOnClickListener(onDelete);
            deleteImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            deleteImageView.setContentDescription(LocaleController.getString(R.string.Delete));
            addView(deleteImageView, LayoutHelper.createFrame(48, 50, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 3 : 0, 0, LocaleController.isRTL ? 0 : 3, 0));

            textView2 = new SimpleTextView(context);
            textView2.setTextSize(13);
            textView2.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
            addView(textView2, LayoutHelper.createFrame(48, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 20 : 0, 43, LocaleController.isRTL ? 0 : 20, 0));

            checkBox = new CheckBox2(context, 21, resourcesProvider);
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
            int endMargin = type == TYPE_EMOJI ? 70 : 19;
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,  LocaleController.isRTL ? endMargin : 19, 0, LocaleController.isRTL ? 19 : endMargin, 0));
        }

        if (type == TYPE_EMOJI) {
            emojiButton = new ChatActivityEnterViewAnimatedIconView(context);
            emojiButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.SRC_IN));
            emojiButton.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
            int padding = dp(9.5f);
            emojiButton.setPadding(padding, padding, padding, padding);
            emojiButton.setVisibility(View.GONE);
            int endMargin = deleteImageView == null ? 3 : 38;
            addView(emojiButton, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? endMargin : 0, 0, LocaleController.isRTL ? 0 : endMargin, 0));
            emojiButton.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector, resourcesProvider)));
            emojiButton.setOnClickListener(view -> onEmojiButtonClicked(this));
            emojiButton.setContentDescription(LocaleController.getString(R.string.Emoji));
        }
    }

    public View addAttachView() {
        if (deleteImageView != null) {
            deleteImageView.setVisibility(GONE);
        }

        attachView = new PollAttachButton(getContext(), resourcesProvider);
        attachView.setFocusable(false);
        attachView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector, resourcesProvider)));
        ScaleStateListAnimator.apply(attachView);
        addView(attachView, LayoutHelper.createFrame(48, 50, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 4 : 0, 0, LocaleController.isRTL ? 0 : 4, 0));

        if (emojiButton != null) {
            int endMargin = 44;
            emojiButton.setLayoutParams(LayoutHelper.createFrame(48, 48, (Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)), LocaleController.isRTL ? endMargin : 0, 1, LocaleController.isRTL ? 0 : endMargin, 0));
        }

        if (textView != null) {
            float startMargin = (LocaleController.isRTL ?
                ((MarginLayoutParams) textView.getLayoutParams()).rightMargin :
                ((MarginLayoutParams) textView.getLayoutParams()).leftMargin
            ) / AndroidUtilities.density;
            int endMargin = (emojiButton != null ? 70 : 19) + 24;
            textView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? endMargin : startMargin, 0, !LocaleController.isRTL ? endMargin : startMargin, 0));
        }

        return attachView;
    }

    public void setIconsColor(int key) {
        if (moveImageView != null) {
            moveImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(key, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        }
        if (deleteImageView != null) {
            deleteImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(key, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        }
        if (emojiButton != null) {
            emojiButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(key, resourcesProvider), PorterDuff.Mode.SRC_IN));
        }
    }

    public boolean onPastedMultipleLines(ArrayList<CharSequence> parts) {
        return false;
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

    private Integer right;
    public void setTextRight(int r) {
        this.right = r;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        for (int i = 0; i < getChildCount(); ++i) {
            final View child = getChildAt(i);
            if (child == textView) continue;
            if (child == deleteImageView) {
                deleteImageView.measure(MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
            } else if (child == emojiButton) {
                emojiButton.measure(MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
            } else if (child == moveImageView) {
                moveImageView.measure(MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
            } else if (child == textView2) {
                textView2.measure(MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY));
            } else if (child == checkBox) {
                checkBox.measure(MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
            } else {
                final ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp != null) {
                    child.measure(MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY));
                } else {
                    child.measure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        }
        int right;
        if (this.right != null) {
            right = this.right;
        } else if (textView2 == null) {
            right = 42;
        } else if (deleteImageView == null) {
            right = 70;
        } else if (emojiButton != null) {
            right = 144;
        } else {
            right = 122;
        }
        textView.measure(MeasureSpec.makeMeasureSpec(width - getPaddingLeft() - getPaddingRight() - dp(right), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int h = textView.getMeasuredHeight();
        setMeasuredDimension(width, Math.max(dp(50), textView.getMeasuredHeight()) + (needDivider ? 1 : 0));
        if (textView2 != null && !alwaysShowText2) {
            textView2.setAlpha(h >= dp(52) ? 1.0f : 0.0f);
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
        animatorEmojiButtonVisible.setValue(visible, true);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (needDivider && drawDivider()) {
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(moveImageView != null ? 58 : 20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(moveImageView != null ? 58 : 20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
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



    public void supportMultiselect() {
        if (checkBox != null) {
            checkBox.getCheckBoxBase().setCustomRadius(dp(6));
            checkBox.getCheckBoxBase().setCustomRadiusFactor(animatorCheckboxMultiselect.getFloatValue());
        }
    }

    public void setCheckboxMultiselect(boolean multiselect, boolean animated) {
        animatorCheckboxMultiselect.setValue(multiselect, animated);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_CHECKBOX_MULTISELECT) {
            if (checkBox != null) {
                checkBox.getCheckBoxBase().setCustomRadiusFactor(animatorCheckboxMultiselect.getFloatValue());
                checkBox.invalidate();
            }
        } else if (id == ANIMATOR_ID_EMOJI_BUTTON_VISIBLE) {
            if (emojiButton != null) {
                final float value = animatorEmojiButtonVisible.getFloatValue();
                emojiButton.setScaleX(value * 0.85f);
                emojiButton.setScaleY(value * 0.85f);
                emojiButton.setAlpha(value);
                emojiButton.setVisibility(value > 0 ? VISIBLE : GONE);
                if (textView2 != null && deleteImageView == null && textView2.getVisibility() == View.VISIBLE) {
                    if (attachView != null) {
                        textView2.setTranslationY(dp(36));
                    } else {
                        textView2.setTranslationY(dp(26) * value);
                    }
                }
            }
        }
    }
}
