/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

public class TextCheckCell2 extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private Switch checkBox;
    private boolean needDivider;
    private boolean isMultiline;

    public TextCheckCell2(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 64 : 21, 0, LocaleController.isRTL ? 21 : 64, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setPadding(0, 0, 0, 0);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 64 : 21, 35, LocaleController.isRTL ? 21 : 64, 0));

        checkBox = new Switch(context);
        checkBox.setDrawIconType(1);
        addView(checkBox, LayoutHelper.createFrame(37, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isMultiline) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(valueTextView.getVisibility() == VISIBLE ? 64 : 50) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }
    }

    public void setTextAndCheck(String text, boolean checked, boolean divider) {
        textView.setText(text);
        isMultiline = false;
        checkBox.setChecked(checked, false);
        needDivider = divider;
        valueTextView.setVisibility(GONE);
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.topMargin = 0;
        textView.setLayoutParams(layoutParams);
        setWillNotDraw(!divider);
    }

    public void setTextAndValueAndCheck(String text, String value, boolean checked, boolean multiline, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        checkBox.setChecked(checked, false);
        needDivider = divider;
        valueTextView.setVisibility(VISIBLE);
        isMultiline = multiline;
        if (multiline) {
            valueTextView.setLines(0);
            valueTextView.setMaxLines(0);
            valueTextView.setSingleLine(false);
            valueTextView.setEllipsize(null);
            valueTextView.setPadding(0, 0, 0, AndroidUtilities.dp(11));
        } else {
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            valueTextView.setPadding(0, 0, 0, 0);
        }
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        layoutParams.height = LayoutParams.WRAP_CONTENT;
        layoutParams.topMargin = AndroidUtilities.dp(10);
        textView.setLayoutParams(layoutParams);
        setWillNotDraw(!divider);
    }

    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        if (value) {
            textView.setAlpha(1.0f);
            valueTextView.setAlpha(1.0f);
            checkBox.setAlpha(1.0f);
        } else {
            checkBox.setAlpha(0.5f);
            textView.setAlpha(0.5f);
            valueTextView.setAlpha(0.5f);
        }
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked, true);
    }

    public void setIcon(int icon) {
        checkBox.setIcon(icon);
    }

    public boolean hasIcon() {
        return checkBox.hasIcon();
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checkBox.isChecked());
        info.setContentDescription(checkBox.isChecked() ? LocaleController.getString("NotificationsOn", R.string.NotificationsOn) : LocaleController.getString("NotificationsOff", R.string.NotificationsOff));
    }
}
