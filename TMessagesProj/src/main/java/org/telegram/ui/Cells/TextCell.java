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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

public class TextCell extends FrameLayout {

    private SimpleTextView textView;
    private SimpleTextView valueTextView;
    private ImageView imageView;
    private ImageView valueImageView;
    private int leftPadding;
    private boolean needDivider;
    private int offsetFromImage = 71;
    private int imageLeft = 21;

    public TextCell(Context context) {
        this(context, 23, false);
    }

    public TextCell(Context context, int left, boolean dialog) {
        super(context);

        leftPadding = left;

        textView = new SimpleTextView(context);
        textView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextBlack : Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(textView);

        valueTextView = new SimpleTextView(context);
        valueTextView.setTextColor(Theme.getColor(dialog ? Theme.key_dialogTextBlue2 : Theme.key_windowBackgroundWhiteValueText));
        valueTextView.setTextSize(16);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        valueTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(valueTextView);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(dialog ? Theme.key_dialogIcon : Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        addView(imageView);

        valueImageView = new ImageView(context);
        valueImageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(valueImageView);

        setFocusable(true);
    }

    public SimpleTextView getTextView() {
        return textView;
    }

    public SimpleTextView getValueTextView() {
        return valueTextView;
    }

    public ImageView getValueImageView() {
        return valueImageView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(48);

        valueTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(leftPadding), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + leftPadding) - valueTextView.getTextWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        if (imageView.getVisibility() == VISIBLE) {
            imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        }
        if (valueImageView.getVisibility() == VISIBLE) {
            valueImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        }
        setMeasuredDimension(width, AndroidUtilities.dp(50) + (needDivider ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;

        int viewTop = (height - valueTextView.getTextHeight()) / 2;
        int viewLeft = LocaleController.isRTL ? AndroidUtilities.dp(leftPadding) : 0;
        valueTextView.layout(viewLeft, viewTop, viewLeft + valueTextView.getMeasuredWidth(), viewTop + valueTextView.getMeasuredHeight());

        viewTop = (height - textView.getTextHeight()) / 2;
        if (LocaleController.isRTL) {
            viewLeft = getMeasuredWidth() - textView.getMeasuredWidth() - AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? offsetFromImage : leftPadding);
        } else {
            viewLeft = AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? offsetFromImage : leftPadding);
        }
        textView.layout(viewLeft, viewTop, viewLeft + textView.getMeasuredWidth(), viewTop + textView.getMeasuredHeight());

        if (imageView.getVisibility() == VISIBLE) {
            viewTop = AndroidUtilities.dp(5);
            viewLeft = !LocaleController.isRTL ? AndroidUtilities.dp(imageLeft) : width - imageView.getMeasuredWidth() - AndroidUtilities.dp(imageLeft);
            imageView.layout(viewLeft, viewTop, viewLeft + imageView.getMeasuredWidth(), viewTop + imageView.getMeasuredHeight());
        }

        if (valueImageView.getVisibility() == VISIBLE) {
            viewTop = (height - valueImageView.getMeasuredHeight()) / 2;
            viewLeft = LocaleController.isRTL ? AndroidUtilities.dp(23) : width - valueImageView.getMeasuredWidth() - AndroidUtilities.dp(23);
            valueImageView.layout(viewLeft, viewTop, viewLeft + valueImageView.getMeasuredWidth(), viewTop + valueImageView.getMeasuredHeight());
        }
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setColors(String icon, String text) {
        textView.setTextColor(Theme.getColor(text));
        textView.setTag(text);
        if (icon != null) {
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(icon), PorterDuff.Mode.MULTIPLY));
            imageView.setTag(icon);
        }
    }

    public void setText(String text, boolean divider) {
        textView.setText(text);
        valueTextView.setText(null);
        imageView.setVisibility(GONE);
        valueTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndIcon(String text, int resId, boolean divider) {
        textView.setText(text);
        valueTextView.setText(null);
        imageView.setImageResource(resId);
        imageView.setVisibility(VISIBLE);
        valueTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndIcon(String text, Drawable drawable, boolean divider) {
        offsetFromImage = 68;
        imageLeft = 18;
        textView.setText(text);
        valueTextView.setText(null);
        imageView.setColorFilter(null);
        imageView.setImageDrawable(drawable);
        imageView.setVisibility(VISIBLE);
        valueTextView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        imageView.setPadding(0, AndroidUtilities.dp(6), 0, 0);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setOffsetFromImage(int value) {
        offsetFromImage = value;
    }

    public void setTextAndValue(String text, String value, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        valueTextView.setVisibility(VISIBLE);
        imageView.setVisibility(GONE);
        valueImageView.setVisibility(GONE);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndValueAndIcon(String text, String value, int resId, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        valueTextView.setVisibility(VISIBLE);
        valueImageView.setVisibility(GONE);
        imageView.setVisibility(VISIBLE);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        imageView.setImageResource(resId);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    public void setTextAndValueDrawable(String text, Drawable drawable, boolean divider) {
        textView.setText(text);
        valueTextView.setText(null);
        valueImageView.setVisibility(VISIBLE);
        valueImageView.setImageDrawable(drawable);
        valueTextView.setVisibility(GONE);
        imageView.setVisibility(GONE);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
        needDivider = divider;
        setWillNotDraw(!needDivider);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 68 : 20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 68 : 20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        final CharSequence text = textView.getText();
        if (!TextUtils.isEmpty(text)) {
            final CharSequence valueText = valueTextView.getText();
            if (!TextUtils.isEmpty(valueText)) {
                info.setText(text + ": " + valueText);
            } else {
                info.setText(text);
            }
        }
    }
}
