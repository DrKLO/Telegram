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
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

public class ManageChatTextCell extends FrameLayout {

    private SimpleTextView textView;
    private SimpleTextView valueTextView;
    private ImageView imageView;
    private boolean divider;
    private int dividerColor = 0;

    public ManageChatTextCell(Context context) {
        super(context);

        textView = new SimpleTextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(textView);

        valueTextView = new SimpleTextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        valueTextView.setTextSize(16);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        addView(valueTextView);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        addView(imageView);
    }

    public SimpleTextView getTextView() {
        return textView;
    }

    public SimpleTextView getValueTextView() {
        return valueTextView;
    }

    public void setDividerColor(int key) {
        dividerColor = key;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(48);

        valueTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(24), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + 24), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));

        setMeasuredDimension(width, AndroidUtilities.dp(56) + (divider ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;

        int viewTop = (height - valueTextView.getTextHeight()) / 2;
        int viewLeft = LocaleController.isRTL ? AndroidUtilities.dp(24) : 0;
        valueTextView.layout(viewLeft, viewTop, viewLeft + valueTextView.getMeasuredWidth(), viewTop + valueTextView.getMeasuredHeight());

        viewTop = (height - textView.getTextHeight()) / 2;
        viewLeft = !LocaleController.isRTL ? AndroidUtilities.dp(71) : AndroidUtilities.dp(24);
        textView.layout(viewLeft, viewTop, viewLeft + textView.getMeasuredWidth(), viewTop + textView.getMeasuredHeight());

        viewTop = AndroidUtilities.dp(9);
        viewLeft = !LocaleController.isRTL ? AndroidUtilities.dp(21) : width - imageView.getMeasuredWidth() - AndroidUtilities.dp(21);
        imageView.layout(viewLeft, viewTop, viewLeft + imageView.getMeasuredWidth(), viewTop + imageView.getMeasuredHeight());
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setColors(int iconColorKey, int text) {
        textView.setTextColor(Theme.getColor(text));
        textView.setTag(text);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(iconColorKey), PorterDuff.Mode.MULTIPLY));
        imageView.setTag(iconColorKey);
    }

    public void setText(String text, String value, int resId, boolean needDivider) {
        setText(text, value, resId, 5, needDivider);
    }

    public void setText(String text, String value, int resId, int paddingTop, boolean needDivider) {
        textView.setText(text);
        if (value != null) {
            valueTextView.setText(value);
            valueTextView.setVisibility(VISIBLE);
        } else {
            valueTextView.setVisibility(INVISIBLE);
        }
        imageView.setPadding(0, AndroidUtilities.dp(paddingTop), 0, 0);
        imageView.setImageResource(resId);
        divider = needDivider;
        setWillNotDraw(!divider);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (divider) {
            if (dividerColor != 0) {
                Theme.dividerExtraPaint.setColor(Theme.getColor(dividerColor));
            }
            canvas.drawLine(AndroidUtilities.dp(71), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, dividerColor != 0 ? Theme.dividerExtraPaint : Theme.dividerPaint);
        }
    }
}
