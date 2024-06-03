/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class TextPriceCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;

    public TextPriceCell(Context context) {
        super(context);

        setWillNotDraw(false);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 0, 21, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueTextView.setTypeface(AndroidUtilities.bold());
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 0, 21, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(40));

        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
        int width = availableWidth / 2;

        valueTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        width = availableWidth - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(8);

        textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setTextValueColor(int color) {
        valueTextView.setTextColor(color);
    }

    public void setTextAndValue(String text, String value, boolean bold) {
        textView.setText(text);
        if (value != null) {
            valueTextView.setText(value);
            valueTextView.setVisibility(VISIBLE);
        } else {
            valueTextView.setVisibility(INVISIBLE);
        }
        if (bold) {
            setTag(Theme.key_windowBackgroundWhiteBlackText);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTypeface(AndroidUtilities.bold());
            valueTextView.setTypeface(AndroidUtilities.bold());
        } else {
            setTag(Theme.key_windowBackgroundWhiteGrayText2);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            textView.setTypeface(Typeface.DEFAULT);
            valueTextView.setTypeface(Typeface.DEFAULT);
        }
        requestLayout();
    }
}
