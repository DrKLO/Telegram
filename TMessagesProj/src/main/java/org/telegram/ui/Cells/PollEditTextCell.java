/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class PollEditTextCell extends FrameLayout {

    private EditTextBoldCursor textView;
    private ImageView deleteImageView;
    private SimpleTextView textView2;
    private boolean showNextButton;
    private boolean needDivider;

    public PollEditTextCell(Context context, OnClickListener onDelete) {
        super(context);

        textView = new EditTextBoldCursor(context) {

            @Override
            public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                InputConnection conn = super.onCreateInputConnection(outAttrs);
                if (showNextButton) {
                    outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
                }
                return conn;
            }
        };
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setBackgroundDrawable(null);
        textView.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        textView.setImeOptions(textView.getImeOptions() | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        textView.setInputType(textView.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL && onDelete != null ? 58 : 21, 0, !LocaleController.isRTL && onDelete != null ? 58 : 21, 0));

        if (onDelete != null) {
            deleteImageView = new ImageView(context);
            deleteImageView.setFocusable(false);
            deleteImageView.setScaleType(ImageView.ScaleType.CENTER);
            deleteImageView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_stickers_menuSelector)));
            deleteImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            deleteImageView.setImageResource(R.drawable.msg_panel_clear);
            deleteImageView.setOnClickListener(onDelete);
            deleteImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.MULTIPLY));
            deleteImageView.setContentDescription(LocaleController.getString("Delete", R.string.Delete));
            addView(deleteImageView, LayoutHelper.createFrame(48, 50, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 3 : 0, 0, LocaleController.isRTL ? 0 : 3, 0));

            textView2 = new SimpleTextView(getContext());
            textView2.setTextSize(13);
            textView2.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
            addView(textView2, LayoutHelper.createFrame(48, 24, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 20 : 0, 43, LocaleController.isRTL ? 0 : 20, 0));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (deleteImageView != null) {
            deleteImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
            textView2.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
        }
        textView.measure(MeasureSpec.makeMeasureSpec(width - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(deleteImageView != null ? 79 : 42), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int h = textView.getMeasuredHeight();
        setMeasuredDimension(width, Math.max(AndroidUtilities.dp(50), textView.getMeasuredHeight()) + (needDivider ? 1 : 0));
        if (textView2 != null) {
            textView2.setAlpha(h >= AndroidUtilities.dp(52) ? 1.0f : 0.0f);
        }
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

    public void addTextWatcher(TextWatcher watcher) {
        textView.addTextChangedListener(watcher);
    }

    protected boolean drawDivider() {
        return true;
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

    public void setText(String text, boolean divider) {
        textView.setText(text);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndHint(String text, String hint, boolean divider) {
        if (deleteImageView != null) {
            deleteImageView.setTag(null);
        }
        textView.setText(text);
        if (!TextUtils.isEmpty(text)) {
            textView.setSelection(text.length());
        }
        textView.setHint(hint);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        setEnabled(value);
    }

    public void setText2(String text) {
        textView2.setText(text);
    }

    public SimpleTextView getTextView2() {
        return textView2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider && drawDivider()) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
