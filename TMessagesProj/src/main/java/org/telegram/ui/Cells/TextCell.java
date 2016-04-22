/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;

public class TextCell extends FrameLayout {

    private SimpleTextView textView;
    private SimpleTextView valueTextView;
    private ImageView imageView;
    private ImageView valueImageView;

    public TextCell(Context context) {
        super(context);

        textView = new SimpleTextView(context);
        textView.setTextColor(0xff212121);
        textView.setTextSize(16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(textView);

        valueTextView = new SimpleTextView(context);
        valueTextView.setTextColor(0xff2f8cc9);
        valueTextView.setTextSize(16);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        addView(valueTextView);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView);

        valueImageView = new ImageView(context);
        valueImageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(valueImageView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(48);

        valueTextView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(24), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + 24), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        valueImageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));

        setMeasuredDimension(width, AndroidUtilities.dp(48));
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

        viewTop = AndroidUtilities.dp(5);
        viewLeft = !LocaleController.isRTL ? AndroidUtilities.dp(16) : width - imageView.getMeasuredWidth() - AndroidUtilities.dp(16);
        imageView.layout(viewLeft, viewTop, viewLeft + imageView.getMeasuredWidth(), viewTop + imageView.getMeasuredHeight());

        viewTop = (height - valueImageView.getMeasuredHeight()) / 2;
        viewLeft = LocaleController.isRTL ? AndroidUtilities.dp(24) : width - valueImageView.getMeasuredWidth() - AndroidUtilities.dp(24);
        valueImageView.layout(viewLeft, viewTop, viewLeft + valueImageView.getMeasuredWidth(), viewTop + valueImageView.getMeasuredHeight());
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setText(String text) {
        textView.setText(text);
        valueTextView.setText(null);
        imageView.setVisibility(INVISIBLE);
        valueTextView.setVisibility(INVISIBLE);
        valueImageView.setVisibility(INVISIBLE);
    }

    public void setTextAndIcon(String text, int resId) {
        textView.setText(text);
        valueTextView.setText(null);
        imageView.setImageResource(resId);
        imageView.setVisibility(VISIBLE);
        valueTextView.setVisibility(INVISIBLE);
        valueImageView.setVisibility(INVISIBLE);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
    }

    public void setTextAndValue(String text, String value) {
        textView.setText(text);
        valueTextView.setText(value);
        valueTextView.setVisibility(VISIBLE);
        imageView.setVisibility(INVISIBLE);
        valueImageView.setVisibility(INVISIBLE);
    }

    public void setTextAndValueDrawable(String text, Drawable drawable) {
        textView.setText(text);
        valueTextView.setText(null);
        valueImageView.setVisibility(VISIBLE);
        valueImageView.setImageDrawable(drawable);
        valueTextView.setVisibility(INVISIBLE);
        imageView.setVisibility(INVISIBLE);
        imageView.setPadding(0, AndroidUtilities.dp(7), 0, 0);
    }
}
