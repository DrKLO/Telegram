/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

public class NotificationsCheckCell extends FrameLayout {

    private TextView textView;
    private AnimatedTextView valueTextView;
    private TextView multilineValueTextView;
    @SuppressWarnings("FieldCanBeLocal")
    private ImageView imageView;
    private Switch checkBox;
    private boolean needDivider;
    private boolean drawLine = true;
    private boolean isMultiline;
    private int currentHeight;
    private boolean animationsEnabled;
    private Theme.ResourcesProvider resourcesProvider;

    public NotificationsCheckCell(Context context) {
        this(context, 21, 70, false, null);
    }

    public NotificationsCheckCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, 21, 70, false, resourcesProvider);
    }

    public NotificationsCheckCell(Context context, int padding, int height, boolean withImage) {
        this(context, padding, height, withImage, null);
    }

    public NotificationsCheckCell(Context context, int padding, int height, boolean withImage, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);
        currentHeight = height;

        if (withImage) {
            imageView = new ImageView(context);
            imageView.setFocusable(false);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));
        }

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 80 : (withImage ? 64 : padding), 13 + (currentHeight - 70) / 2, LocaleController.isRTL ? (withImage ? 64 : padding) : 80, 0));

        valueTextView = new AnimatedTextView(context);
        valueTextView.setAnimationProperties(.55f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        valueTextView.setTextSize(dp(13));
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setPadding(0, 0, 0, 0);
        valueTextView.setEllipsizeByGradient(true);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 80 : (withImage ? 64 : padding), 38 - 9 - (withImage ? 2 : 0) + (currentHeight - 70) / 2, LocaleController.isRTL ? (withImage ? 64 : padding) : 80, 0));

        multilineValueTextView = new TextView(context);
        multilineValueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        multilineValueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        multilineValueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        multilineValueTextView.setLines(0);
        multilineValueTextView.setMaxLines(0);
        multilineValueTextView.setSingleLine(false);
        multilineValueTextView.setEllipsize(null);
        multilineValueTextView.setPadding(0, 0, 0, 0);
        multilineValueTextView.setVisibility(View.GONE);
        addView(multilineValueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 80 : (withImage ? 64 : padding), 38 - (withImage ? 2 : 0) + (currentHeight - 70) / 2, LocaleController.isRTL ? (withImage ? 64 : padding) : 80, 0));

        checkBox = new Switch(context, resourcesProvider) {
            @Override
            protected int processColor(int color) {
                return NotificationsCheckCell.this.processColor(color);
            }
        };
        checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        addView(checkBox, LayoutHelper.createFrame(37, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 21, 0, 21, 0));
        checkBox.setFocusable(false);
    }

    public Switch getCheckBox() {
        return checkBox;
    }

    protected int processColor(int color) {
        return color;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isMultiline) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(currentHeight), MeasureSpec.EXACTLY));
        }
    }

    public void setTextAndValueAndCheck(CharSequence text, CharSequence value, boolean checked, boolean divider) {
        setTextAndValueAndCheck(text, value, checked, 0, false, divider);
    }

    public void setTextAndValueAndCheck(CharSequence text, CharSequence value, boolean checked, int iconType, boolean divider) {
        setTextAndValueAndCheck(text, value, checked, iconType, false, divider);
    }

    public void setTextAndValueAndCheck(CharSequence text, CharSequence value, boolean checked, int iconType, boolean multiline, boolean divider) {
        setTextAndValueAndIconAndCheck(text, value, 0, checked, iconType, multiline, divider);
    }

    public void setTextAndValueAndIconAndCheck(CharSequence text, CharSequence value, int iconResId, boolean checked, int iconType, boolean multiline, boolean divider) {
        setTextAndValueAndIconAndCheck(text, value, iconResId, checked, iconType, multiline, divider, false);
    }

    public void setTextAndValueAndIconAndCheck(CharSequence text, CharSequence value, int iconResId, boolean checked, int iconType, boolean multiline, boolean divider, boolean animated) {
        textView.setText(text);
        if (imageView != null) {
            imageView.setImageResource(iconResId);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogIcon), PorterDuff.Mode.MULTIPLY));
        }
        checkBox.setChecked(checked, iconType, animationsEnabled);
        setMultiline(multiline);
        if (isMultiline) {
            multilineValueTextView.setText(value);
        } else {
            valueTextView.setText(value, animated);
        }
        (isMultiline ? multilineValueTextView : valueTextView).setVisibility(VISIBLE);
        checkBox.setContentDescription(text);
        needDivider = divider;
    }

    public void setMultiline(boolean multiline) {
        isMultiline = multiline;
        if (multiline) {
            multilineValueTextView.setVisibility(View.VISIBLE);
            valueTextView.setVisibility(View.GONE);
            multilineValueTextView.setPadding(0, 0, 0, dp(14));
        } else {
            multilineValueTextView.setVisibility(View.GONE);
            valueTextView.setVisibility(View.VISIBLE);
            valueTextView.setPadding(0, 0, 0, 0);
        }
    }

    public void setValue(CharSequence value) {
        if (isMultiline) {
            multilineValueTextView.setText(value);
        } else {
            valueTextView.setText(value, true);
        }
    }

    public void setDrawLine(boolean value) {
        drawLine = value;
    }

    public void setChecked(boolean checked) {
        checkBox.setChecked(checked, true);
    }

    public void setChecked(boolean checked, int iconType) {
        checkBox.setChecked(checked, iconType, true);
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(
                LocaleController.isRTL ? 0 : dp(imageView != null ? 64 : 20),
                getMeasuredHeight() - 1,
                getMeasuredWidth() - (LocaleController.isRTL ? dp(imageView != null ? 64 : 20) : 0),
                getMeasuredHeight() - 1,
                Theme.dividerPaint
            );
        }
        if (drawLine) {
            int x = LocaleController.isRTL ? dp(76) : getMeasuredWidth() - dp(76) - 1;
            int y = (getMeasuredHeight() - dp(22)) / 2;
            canvas.drawRect(x, y, x + 2, y + dp(22), Theme.dividerPaint);
        }
    }

    public void setAnimationsEnabled(boolean animationsEnabled) {
        this.animationsEnabled = animationsEnabled;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        StringBuilder sb = new StringBuilder();
        sb.append(textView.getText());
        if (isMultiline) {
            if (multilineValueTextView != null && !TextUtils.isEmpty(multilineValueTextView.getText())) {
                sb.append("\n");
                sb.append(multilineValueTextView.getText());
            }
        } else {
            if (valueTextView != null && !TextUtils.isEmpty(valueTextView.getText())) {
                sb.append("\n");
                sb.append(valueTextView.getText());
            }
        }
        info.setContentDescription(sb);
        info.setCheckable(true);
        info.setChecked(checkBox.isChecked());
    }
}
