/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;

public class SettingsSectionLayout extends LinearLayout {

    private TextView textView;

    private void init() {
        setOrientation(LinearLayout.VERTICAL);

        textView = new TextView(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        textView.setTextColor(0xff3b84c0);
        addView(textView);
        LayoutParams layoutParams = (LayoutParams)textView.getLayoutParams();
        layoutParams.width = LayoutParams.WRAP_CONTENT;
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.leftMargin = AndroidUtilities.dp(8);
        layoutParams.rightMargin = AndroidUtilities.dp(8);
        layoutParams.topMargin = AndroidUtilities.dp(6);
        layoutParams.bottomMargin = AndroidUtilities.dp(4);
        if (LocaleController.isRTL) {
            textView.setGravity(Gravity.RIGHT);
            layoutParams.gravity = Gravity.RIGHT;
        }
        textView.setLayoutParams(layoutParams);

        View view = new View(getContext());
        view.setBackgroundColor(0xff6caae4);
        addView(view);
        layoutParams = (LayoutParams)view.getLayoutParams();
        layoutParams.weight = LayoutParams.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(1);
        view.setLayoutParams(layoutParams);
    }

    public SettingsSectionLayout(Context context) {
        super(context);
        init();
    }

    public SettingsSectionLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SettingsSectionLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public SettingsSectionLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED));
    }

    public void setText(String text) {
        textView.setText(text);
    }
}
