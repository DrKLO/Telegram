package org.telegram.ui.Components;

import org.telegram.messenger.FileLog;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;

public class OutlineEditText extends OutlineTextContainerView implements Theme.Colorable {

    EditTextBoldCursor editText;

    public OutlineEditText(Context context) {
        super(context);

        editText = new EditTextBoldCursor(context) {
            @Override
            protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
                super.onFocusChanged(focused, direction, previouslyFocusedRect);
                animateSelection(focused || isFocused() ? 1f : 0f);
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        ThemeBind.textColor(editText, Theme.key_windowBackgroundWhiteBlackText);
        ThemeBind.hintTextColor(editText, Theme.key_windowBackgroundWhiteHintText);
        editText.setBackground(null);
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setTypeface(Typeface.DEFAULT);
        ThemeBind.cursorColor(editText, Theme.key_windowBackgroundWhiteInputFieldActivated);
        editText.setCursorWidth(1.5f);
        editText.setPadding(
                AndroidUtilities.dp(15), 0, AndroidUtilities.dp(15), 0
        );
        attachEditText(editText);

        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

    }

    public void setHint(String hint) {
        setText(hint);
    }

    public EditTextBoldCursor getEditText() {
        return editText;
    }

    @Override
    public void updateColors() {
        super.updateColors();
        try {
            invalidate();
    
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
