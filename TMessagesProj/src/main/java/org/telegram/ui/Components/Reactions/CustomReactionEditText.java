package org.telegram.ui.Components.Reactions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;
import android.os.Build;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.Menu;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextCaption;

@SuppressLint("ViewConstructor")
public class CustomReactionEditText extends EditTextCaption {

    private final Theme.ResourcesProvider resourcesProvider;
    private final GestureDetectorCompat gestureDetector;
    private Runnable onFocused;

    public CustomReactionEditText(Context context, Theme.ResourcesProvider resourcesProvider, int maxLength) {
        super(context, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        this.gestureDetector = new GestureDetectorCompat(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                return true;
            }
        });
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setIncludeFontPadding(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setShowSoftInputOnFocus(false);
        }
        setSingleLine(false);
        setMaxLines(50);
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(maxLength);
        setFilters(inputFilters);
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        setGravity(Gravity.BOTTOM);
        setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(4), AndroidUtilities.dp(18), AndroidUtilities.dp(12));
        setTextColor(getThemedColor(Theme.key_chat_messagePanelText));
        setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkOut));
        setHighlightColor(getThemedColor(Theme.key_chat_inTextSelectionHighlight));
        setHintColor(getThemedColor(Theme.key_chat_messagePanelHint));
        setHintTextColor(getThemedColor(Theme.key_chat_messagePanelHint));
        setCursorColor(getThemedColor(Theme.key_chat_messagePanelCursor));
        setHandlesColor(getThemedColor(Theme.key_chat_TextSelectionCursor));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setFallbackLineSpacing(false);
        }
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                removeReactionsSpan(true);
                if (onFocused != null) {
                    onFocused.run();
                }
            } else {
                addReactionsSpan();
            }
        });
        setTextIsSelectable(true);
        setLongClickable(false);
        setFocusableInTouchMode(false);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            if(!isLongClickable()) {
                return false;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (hasSelection()) {
            AddReactionsSpan[] spans = getText().getSpans(selStart, selEnd, AddReactionsSpan.class);
            if (spans.length != 0) {
                setSelection(selStart, selEnd - 1);
            }
        }
    }

    @Override
    protected void extendActionMode(ActionMode actionMode, Menu menu) {
        menu.clear();
        menu.add(R.id.menu_delete, R.id.menu_delete, 0, LocaleController.getString("Delete", R.string.Delete));
    }

    public void setOnFocused(Runnable onFocused) {
        this.onFocused = onFocused;
    }

    public void addReactionsSpan() {
        setLongClickable(false);
        SpannableStringBuilder spannableText = new SpannableStringBuilder(getText());
        AddReactionsSpan[] spans = spannableText.getSpans(0, spannableText.length(), AddReactionsSpan.class);
        if (spans.length == 0) {
            SpannableStringBuilder builder = new SpannableStringBuilder("x");
            AddReactionsSpan span = new AddReactionsSpan(15, resourcesProvider);
            span.show(this);
            builder.setSpan(span, 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setText(getText().append(builder));
        }
    }

    public void removeReactionsSpan(boolean animate) {
        SpannableStringBuilder spannableText = new SpannableStringBuilder(getText());
        AddReactionsSpan[] spans = spannableText.getSpans(0, spannableText.length(), AddReactionsSpan.class);
        for (AddReactionsSpan span : spans) {
            Runnable action = () -> {
                getText().delete(getText().getSpanStart(span), getText().getSpanEnd(span));
                setCursorVisible(true);
                setLongClickable(true);
            };
            if (animate) {
                setCursorVisible(false);
                span.hide(this, action);
            } else {
                action.run();
            }
        }
    }

    public int getEditTextSelectionEnd() {
        int selectionEnd = getSelectionEnd();
        if (selectionEnd < 0) {
            selectionEnd = 0;
        }
        return selectionEnd;
    }

    public int getEditTextSelectionStart() {
        int selectionStart = getSelectionStart();
        if (selectionStart < 0) {
            selectionStart = 0;
        }
        return selectionStart;
    }

    public int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public Paint.FontMetricsInt getFontMetricsInt() {
        return getPaint().getFontMetricsInt();
    }
}
