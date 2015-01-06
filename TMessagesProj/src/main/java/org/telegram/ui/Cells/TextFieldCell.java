/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;

public class TextFieldCell extends LinearLayout {

    private TextView textView;
    private EditText editText;

    public TextFieldCell(Context context) {
        super(context);
        setOrientation(VERTICAL);

        textView = new TextView(context);
        textView.setTextColor(0xff505050);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        addView(textView);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(17);
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.leftMargin = AndroidUtilities.dp(17);
        layoutParams.rightMargin = AndroidUtilities.dp(17);
        layoutParams.width = LayoutParams.MATCH_PARENT;
        textView.setLayoutParams(layoutParams);

        editText = new EditText(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHintTextColor(0xffbebebe);
        editText.setTextColor(0xff212121);
        editText.setMaxLines(1);
        editText.setLines(1);
        editText.setSingleLine(true);
        editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        AndroidUtilities.clearCursorDrawable(editText);
        addView(editText);
        layoutParams = (LayoutParams) editText.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(10);
        layoutParams.bottomMargin = AndroidUtilities.dp(17);
        layoutParams.height = AndroidUtilities.dp(30);
        layoutParams.leftMargin = AndroidUtilities.dp(17);
        layoutParams.rightMargin = AndroidUtilities.dp(17);
        layoutParams.width = LayoutParams.MATCH_PARENT;
        editText.setLayoutParams(layoutParams);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_DONE) {
                    textView.clearFocus();
                    AndroidUtilities.hideKeyboard(textView);
                    return true;
                }
                return false;
            }
        });
    }

    public void setFieldText(String text) {
        editText.setText(text);
    }

    public String getFieldText() {
        return editText.getText().toString();
    }

    public void setFieldTitleAndHint(String title, String hint, int bottom, boolean password) {
        editText.setHint(hint);
        LayoutParams layoutParams = (LayoutParams) editText.getLayoutParams();
        layoutParams.bottomMargin = bottom;
        editText.setLayoutParams(layoutParams);
        if (password) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.setTypeface(Typeface.DEFAULT);
        } else {
            editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        }
        if (title != null) {
            textView.setText(title);
            textView.setVisibility(VISIBLE);
        } else {
            textView.setVisibility(GONE);
        }
    }
}
