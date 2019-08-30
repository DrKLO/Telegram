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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class TextSettingsCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private ImageView valueImageView;
    private boolean needDivider;
    private boolean canDisable;

    public TextSettingsCell(Context context) {
        this(context, 21);
    }

    public TextSettingsCell(Context context, int padding) {
        super(context);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, padding, 0, padding, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, padding, 0, padding, 0));

        valueImageView = new ImageView(context);
        valueImageView.setScaleType(ImageView.ScaleType.CENTER);
        valueImageView.setVisibility(INVISIBLE);
        valueImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        addView(valueImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, padding, 0, padding, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50) + (needDivider ? 1 : 0));

        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
        int width = availableWidth / 2;
        if (valueImageView.getVisibility() == VISIBLE) {
            valueImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        }
        if (valueTextView.getVisibility() == VISIBLE) {
            valueTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
            width = availableWidth - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(8);
        } else {
            width = availableWidth;
        }
        textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    public TextView getTextView() {
        return textView;
    }

    public void setCanDisable(boolean value) {
        canDisable = value;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setTextValueColor(int color) {
        valueTextView.setTextColor(color);
    }

    public void setText(String text, boolean divider) {
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        valueImageView.setVisibility(INVISIBLE);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndValue(String text, String value, boolean divider) {
        textView.setText(text);
        valueImageView.setVisibility(INVISIBLE);
        if (value != null) {
            valueTextView.setText(value);
            valueTextView.setVisibility(VISIBLE);
        } else {
            valueTextView.setVisibility(INVISIBLE);
        }
        needDivider = divider;
        setWillNotDraw(!divider);
        requestLayout();
    }

    public void setTextAndIcon(String text, int resId, boolean divider) {
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        if (resId != 0) {
            valueImageView.setVisibility(VISIBLE);
            valueImageView.setImageResource(resId);
        } else {
            valueImageView.setVisibility(INVISIBLE);
        }
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        setEnabled(value);
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(textView, "alpha", value ? 1.0f : 0.5f));
            if (valueTextView.getVisibility() == VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(valueTextView, "alpha", value ? 1.0f : 0.5f));
            }
            if (valueImageView.getVisibility() == VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(valueImageView, "alpha", value ? 1.0f : 0.5f));
            }
        } else {
            textView.setAlpha(value ? 1.0f : 0.5f);
            if (valueTextView.getVisibility() == VISIBLE) {
                valueTextView.setAlpha(value ? 1.0f : 0.5f);
            }
            if (valueImageView.getVisibility() == VISIBLE) {
                valueImageView.setAlpha(value ? 1.0f : 0.5f);
            }
        }
    }

    @Override
    public void setEnabled(boolean value) {
        super.setEnabled(value);
        textView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
        if (valueTextView.getVisibility() == VISIBLE) {
            valueTextView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
        }
        if (valueImageView.getVisibility() == VISIBLE) {
            valueImageView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
        }
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
        info.setEnabled(isEnabled());
    }
}
