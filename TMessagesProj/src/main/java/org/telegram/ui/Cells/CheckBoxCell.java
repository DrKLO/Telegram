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
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.LayoutHelper;

public class CheckBoxCell extends FrameLayout {

    public final static int TYPE_CHECK_BOX_ROUND = 4;
    
    private final Theme.ResourcesProvider resourcesProvider;
    private TextView textView;
    private TextView valueTextView;
    private View checkBox;
    private CheckBoxSquare checkBoxSquare;
    private CheckBox2 checkBoxRound;
    private boolean needDivider;
    private boolean isMultiline;
    private int currentType;
    private int checkBoxSize = 18;

    public CheckBoxCell(Context context, int type) {
        this(context, type, 17, null);
    }

    public CheckBoxCell(Context context, int type, Theme.ResourcesProvider resourcesProvider) {
        this(context, type, 17, resourcesProvider);
    }

    public CheckBoxCell(Context context, int type, int padding, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        currentType = type;

        textView = new TextView(context);
        textView.setTextColor(getThemedColor(type == 1 || type == 5 ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        textView.setLinkTextColor(getThemedColor(type == 1 || type == 5 ? Theme.key_dialogTextLink : Theme.key_windowBackgroundWhiteLinkText));
        textView.setTag(getThemedColor(type == 1 || type == 5 ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        if (type == 3) {
            textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 29, 0, 0, 0));
            textView.setPadding(0, 0, 0, AndroidUtilities.dp(3));
        } else {
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            if (type == 2) {
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : 29), 0, (LocaleController.isRTL ? 29 : 0), 0));
            } else {
                int offset = type == 4 ? 56 : 46;
                addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? padding : offset + (padding - 17)), 0, (LocaleController.isRTL ? offset + (padding - 17) : padding), 0));
            }
        }

        valueTextView = new TextView(context);
        valueTextView.setTextColor(getThemedColor(type == 1 || type == 5 ? Theme.key_dialogTextBlue : Theme.key_windowBackgroundWhiteValueText));
        valueTextView.setTag(type == 1 || type == 5 ? Theme.key_dialogTextBlue : Theme.key_windowBackgroundWhiteValueText);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, padding, 0, padding, 0));

        if (type == TYPE_CHECK_BOX_ROUND) {
            checkBox = checkBoxRound = new CheckBox2(context, 21, resourcesProvider);
            checkBoxRound.setDrawUnchecked(true);
            checkBoxRound.setChecked(true, false);
            checkBoxRound.setDrawBackgroundAsArc(10);
            checkBoxSize = 21;
            addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : padding), 16, (LocaleController.isRTL ? padding : 0), 0));
        } else {
            checkBox = checkBoxSquare = new CheckBoxSquare(context, type == 1 || type == 5, resourcesProvider);
            checkBoxSize = 18;
            if (type == 5) {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, (LocaleController.isRTL ? 0 : padding), 0, (LocaleController.isRTL ? padding : 0), 0));
            } else if (type == 3) {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, Gravity.LEFT | Gravity.TOP, 0, 15, 0, 0));
            } else if (type == 2) {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 15, 0, 0));
            } else {
                addView(checkBox, LayoutHelper.createFrame(checkBoxSize, checkBoxSize, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 0 : padding), 16, (LocaleController.isRTL ? padding : 0), 0));
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (currentType == 3) {
            int width = MeasureSpec.getSize(widthMeasureSpec);

            valueTextView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(10), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
            textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(34), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize), MeasureSpec.EXACTLY));

            setMeasuredDimension(textView.getMeasuredWidth() + AndroidUtilities.dp(29), AndroidUtilities.dp(50));
        } else if (isMultiline) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50) + (needDivider ? 1 : 0));

            int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);

            valueTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth / 2, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            textView.measure(MeasureSpec.makeMeasureSpec(availableWidth - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(8), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(checkBoxSize), MeasureSpec.EXACTLY));
        }
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setText(CharSequence text, String value, boolean checked, boolean divider) {
        textView.setText(text);
        if (checkBoxRound != null) {
            checkBoxRound.setChecked(checked, false);
        } else {
            checkBoxSquare.setChecked(checked, false);
        }
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setNeedDivider(boolean needDivider){
        this.needDivider = needDivider;
    }

    public void setMultiline(boolean value) {
        isMultiline = value;
        LayoutParams layoutParams = (LayoutParams) textView.getLayoutParams();
        LayoutParams layoutParams1 = (LayoutParams) checkBox.getLayoutParams();
        if (isMultiline) {
            textView.setLines(0);
            textView.setMaxLines(0);
            textView.setSingleLine(false);
            textView.setEllipsize(null);
            if (currentType != 5) {
                textView.setPadding(0, 0, 0, AndroidUtilities.dp(5));
                layoutParams.height = LayoutParams.WRAP_CONTENT;
                layoutParams.topMargin = AndroidUtilities.dp(10);
                layoutParams1.topMargin = AndroidUtilities.dp(12);
            }
        } else {
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setPadding(0, 0, 0, 0);

            layoutParams.height = LayoutParams.MATCH_PARENT;
            layoutParams.topMargin = 0;
            layoutParams1.topMargin = AndroidUtilities.dp(15);
        }
        textView.setLayoutParams(layoutParams);
        checkBox.setLayoutParams(layoutParams1);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        textView.setAlpha(enabled ? 1.0f : 0.5f);
        valueTextView.setAlpha(enabled ? 1.0f : 0.5f);
        checkBox.setAlpha(enabled ? 1.0f : 0.5f);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBoxRound != null) {
            checkBoxRound.setChecked(checked, animated);
        } else {
            checkBoxSquare.setChecked(checked, animated);
        }
    }

    public boolean isChecked() {
        if (checkBoxRound != null) {
            return checkBoxRound.isChecked();
        } else {
            return checkBoxSquare.isChecked();
        }
    }

    public TextView getTextView() {
        return textView;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public View getCheckBoxView() {
        return checkBox;
    }

    public void setCheckBoxColor(String background, String background1, String check) {
        if (checkBoxRound != null) {
            checkBoxRound.setColor(background,background,check);
        }
    }
    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            int offset = currentType == TYPE_CHECK_BOX_ROUND ? 50 : 20;
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(offset), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(offset) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.CheckBox");
        info.setCheckable(true);
        info.setChecked(isChecked());
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
