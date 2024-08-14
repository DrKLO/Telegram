package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.decelerateInterpolator;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.Components.AnimatedColor;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class EditTextCell extends FrameLayout {

    private boolean ignoreEditText;
    public final EditTextBoldCursor editText;
    private int maxLength;

    private boolean showLimitWhenEmpty;
    private boolean showLimitWhenFocused;

    public boolean autofocused;
    private boolean focused;

    AnimatedColor limitColor = new AnimatedColor(this);
    private int limitCount;
    AnimatedTextView.AnimatedTextDrawable limit = new AnimatedTextView.AnimatedTextDrawable(false, true, true); {
        limit.setAnimationProperties(.2f, 0, 160, CubicBezierInterpolator.EASE_OUT_QUINT);
        limit.setTextSize(dp(15.33f));
        limit.setGravity(Gravity.RIGHT);
    }

    public void setShowLimitWhenEmpty(boolean show) {
        showLimitWhenEmpty = show;
        if (showLimitWhenEmpty) {
            updateLimitText();
        }
    }

    private void updateLimitText() {
        if (editText == null) return;
        limitCount = maxLength - getText().length();
        limit.setText(TextUtils.isEmpty(getText()) && !showLimitWhenEmpty || showLimitWhenFocused && (!focused || autofocused) ? "" : "" + limitCount);
    }

    public void whenHitEnter(Runnable whenEnter) {
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    whenEnter.run();
                    return true;
                }
                return false;
            }
        });
    }

    public void hideKeyboardOnEnter() {
        whenHitEnter(() -> AndroidUtilities.hideKeyboard(editText));
    }


    public void setShowLimitOnFocus(boolean show) {
        showLimitWhenFocused = show;
    }

    public EditTextCell(Context context, String hint, boolean multiline) {
        this(context, hint, multiline, -1, null);
    }

    public EditTextCell(
        Context context,
        String hint,
        boolean multiline,
        int maxLength,
        Theme.ResourcesProvider resourceProvider
    ) {
        super(context);
        this.maxLength = maxLength;

        editText = new EditTextBoldCursor(context) {
            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == limit || super.verifyDrawable(who);
            }
            @Override
            protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
                super.onTextChanged(text, start, lengthBefore, lengthAfter);

                if (limit != null && maxLength > 0) {
                    limit.cancelAnimation();
                    updateLimitText();
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                limit.setTextColor(limitColor.set(Theme.getColor(limitCount <= 0 ? Theme.key_text_RedRegular : Theme.key_dialogSearchHint, resourceProvider)));
                limit.setBounds(getScrollX(), 0, getScrollX() + getWidth() - getPaddingRight() + dp(42), getHeight());
                limit.draw(canvas);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.clipRect(getScrollX() + getPaddingLeft(), 0, getScrollX() + getWidth() - getPaddingRight(), getHeight());
                super.onDraw(canvas);
                canvas.restore();
            }
        };
        limit.setCallback(editText);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        editText.setBackground(null);
        if (multiline) {
            editText.setMaxLines(5);
            editText.setSingleLine(false);
        } else {
            editText.setMaxLines(1);
            editText.setSingleLine(true);
        }
        editText.setPadding(dp(21), dp(15), dp((maxLength > 0 ? 42 : 0) + 21), dp(15));
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0) | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setRawInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setHint(hint);
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        editText.setCursorSize(dp(19));
        editText.setCursorWidth(1.5f);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                if (!ignoreEditText) {
                    autofocused = false;
                }
            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {}
            @Override
            public void afterTextChanged(Editable editable) {
                if (!ignoreEditText) {
                    if (maxLength > 0 && editable != null && editable.length() > maxLength) {
                        ignoreEditText = true;
                        editText.setText(editable.subSequence(0, maxLength));
                        editText.setSelection(editText.length());
                        ignoreEditText = false;
                    }
                    EditTextCell.this.onTextChanged(editable);
                }

                if (multiline) {
                    int pos;
                    while ((pos = editable.toString().indexOf("\n")) >= 0) {
                        editable.delete(pos, pos + 1);
                    }
                }
            }
        });
        editText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                focused = hasFocus;
                if (showLimitWhenFocused) {
                    updateLimitText();
                }
                onFocusChanged(hasFocus);
            }
        });
        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP));

        updateLimitText();
    }

    public ImageView setLeftDrawable(Drawable drawable) {
        ImageView imageView = new ImageView(getContext());
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageDrawable(drawable);
        addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, 0, 0, 0));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) editText.getLayoutParams();
        lp.leftMargin = dp(24);
        editText.setLayoutParams(lp);

        return imageView;
    }

    public void setText(CharSequence text) {
        ignoreEditText = true;
        editText.setText(text);
        editText.setSelection(editText.getText().length());
        ignoreEditText = false;
    }

    public CharSequence getText() {
        return editText.getText();
    }

    public boolean validate() {
        return maxLength < 0 || editText.getText().length() <= maxLength;
    }

    protected void onTextChanged(CharSequence newText) {

    }

    protected void onFocusChanged(boolean focused) {

    }

    private boolean needDivider;
    public void setDivider(boolean divider) {
        setWillNotDraw(!(needDivider = divider));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawLine(
                    LocaleController.isRTL ? 0 : dp(22),
                    getMeasuredHeight() - 1,
                    getMeasuredWidth() - (LocaleController.isRTL ? dp(22) : 0),
                    getMeasuredHeight() - 1,
                    Theme.dividerPaint
            );
        }
    }
}