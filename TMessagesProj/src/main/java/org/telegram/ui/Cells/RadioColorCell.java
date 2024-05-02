/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;

public class RadioColorCell extends FrameLayout {

    private TextView textView;
    private TextView text2View;
    private RadioButton radioButton;
    private final Theme.ResourcesProvider resourcesProvider;

    public int heightDp = 50;

    public RadioColorCell(Context context) {
        this(context, null);
    }

    public RadioColorCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        radioButton = new RadioButton(context);
        radioButton.setSize(AndroidUtilities.dp(20));
        radioButton.setColor(getThemedColor(Theme.key_dialogRadioBackground), getThemedColor(Theme.key_dialogRadioBackgroundChecked));
        addView(radioButton, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 18), 14, (LocaleController.isRTL ? 18 : 0), 0));

        textView = new TextView(context);
        textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 51), 13, (LocaleController.isRTL ? 51 : 21), 0));

        text2View = new TextView(context);
        text2View.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        text2View.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        text2View.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        text2View.setVisibility(View.GONE);
        addView(text2View, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 51), 13 + 16 + 8, (LocaleController.isRTL ? 51 : 21), 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (text2View.getVisibility() == View.VISIBLE) {
            text2View.measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(21 + 51), MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(heightDp) + (text2View.getVisibility() == View.VISIBLE ? AndroidUtilities.dp(4) + text2View.getMeasuredHeight() : 0), MeasureSpec.EXACTLY)
        );
    }

    public void setCheckColor(int color1, int color2) {
        radioButton.setColor(color1, color2);
    }

    public void setTextAndValue(CharSequence text, boolean checked) {
        textView.setText(text);
        text2View.setVisibility(View.GONE);
        radioButton.setChecked(checked, false);
    }

    public void setTextAndText2AndValue(CharSequence text, CharSequence text2, boolean checked) {
        textView.setText(text);
        text2View.setVisibility(View.VISIBLE);
        text2View.setText(text2);
        radioButton.setChecked(checked, false);
    }

    public void setChecked(boolean checked, boolean animated) {
        radioButton.setChecked(checked, animated);
    }

    public boolean isChecked() {
        return radioButton.isChecked();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.RadioButton");
        info.setCheckable(true);
        info.setChecked(radioButton.isChecked());
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
