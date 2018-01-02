/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

public class InviteTextCell extends FrameLayout {

    private SimpleTextView textView;
    private ImageView imageView;

    public InviteTextCell(Context context) {
        super(context);

        textView = new SimpleTextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(17);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(textView);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        addView(imageView);
    }

    public SimpleTextView getTextView() {
        return textView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(72);

        textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + 24), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));

        setMeasuredDimension(width, AndroidUtilities.dp(72));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;

        int viewTop = (height - textView.getTextHeight()) / 2;
        int viewLeft = !LocaleController.isRTL ? AndroidUtilities.dp(71) : AndroidUtilities.dp(24);
        textView.layout(viewLeft, viewTop, viewLeft + textView.getMeasuredWidth(), viewTop + textView.getMeasuredHeight());

        viewTop = (height - imageView.getMeasuredHeight()) / 2;
        viewLeft = !LocaleController.isRTL ? AndroidUtilities.dp(20) : width - imageView.getMeasuredWidth() - AndroidUtilities.dp(20);
        imageView.layout(viewLeft, viewTop, viewLeft + imageView.getMeasuredWidth(), viewTop + imageView.getMeasuredHeight());
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setTextAndIcon(String text, int resId) {
        textView.setText(text);
        imageView.setImageResource(resId);
    }
}
