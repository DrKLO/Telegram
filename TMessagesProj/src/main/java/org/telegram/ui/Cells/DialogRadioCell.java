/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;

import java.util.ArrayList;

public class DialogRadioCell extends FrameLayout {

    private TextView textView;
    private RadioButton radioButton;
    private boolean needDivider;

    public DialogRadioCell(Context context) {
        this(context, false);
    }

    public DialogRadioCell(Context context, boolean dialog) {
        super(context);

        textView = new TextView(context);
        if (dialog) {
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        } else {
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 23 : 61, 0, LocaleController.isRTL ? 61 : 23, 0));

        radioButton = new RadioButton(context);
        radioButton.setSize(AndroidUtilities.dp(20));
        if (dialog) {
            radioButton.setColor(Theme.getColor(Theme.key_dialogRadioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
        } else {
            radioButton.setColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_radioBackgroundChecked));
        }
        addView(radioButton, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 20, 15, 20, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50) + (needDivider ? 1 : 0));

        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
        radioButton.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY));
        textView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setText(String text, boolean checked, boolean divider) {
        textView.setText(text);
        radioButton.setChecked(checked, false);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public boolean isChecked() {
        return radioButton.isChecked();
    }

    public void setChecked(boolean checked, boolean animated) {
        radioButton.setChecked(checked, animated);
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(textView, "alpha", value ? 1.0f : 0.5f));
            animators.add(ObjectAnimator.ofFloat(radioButton, "alpha", value ? 1.0f : 0.5f));
        } else {
            textView.setAlpha(value ? 1.0f : 0.5f);
            radioButton.setAlpha(value ? 1.0f : 0.5f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(AndroidUtilities.dp(LocaleController.isRTL ? 0 : 60), getHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(LocaleController.isRTL ? 60 : 0), getHeight() - 1, Theme.dividerPaint);
        }
    }
}
