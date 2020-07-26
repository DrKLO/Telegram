/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class EmptyTextProgressView extends FrameLayout {

    private TextView textView;
    private RadialProgressView progressBar;
    private boolean inLayout;
    private int showAtPos;

    public EmptyTextProgressView(Context context) {
        super(context);

        progressBar = new RadialProgressView(context);
        addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        textView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        progressBar.setAlpha(0f);
        textView.setAlpha(0f);

        setOnTouchListener((v, event) -> true);
    }

    public void showProgress() {
        textView.animate().alpha(0f).setDuration(150).start();
        progressBar.animate().alpha(1f).setDuration(150).start();
    }

    public void showTextView() {
        textView.animate().alpha(1f).setDuration(150).start();
        progressBar.animate().alpha(0f).setDuration(150).start();
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public void setTextColor(int color) {
        textView.setTextColor(color);
    }

    public void setProgressBarColor(int color) {
        progressBar.setProgressColor(color);
    }

    public void setTopImage(int resId) {
        if (resId == 0) {
            textView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        } else {
            Drawable drawable = getContext().getResources().getDrawable(resId).mutate();
            if (drawable != null) {
                drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_emptyListPlaceholder), PorterDuff.Mode.MULTIPLY));
            }
            textView.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            textView.setCompoundDrawablePadding(AndroidUtilities.dp(1));
        }
    }

    public void setTextSize(int size) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
    }

    public void setShowAtCenter(boolean value) {
        showAtPos = value ? 1 : 0;
    }

    public void setShowAtTop(boolean value) {
        showAtPos = value ? 2 : 0;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        inLayout = true;
        int width = r - l;
        int height = b - t;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            int x = (width - child.getMeasuredWidth()) / 2;
            int y;
            if (showAtPos == 2) {
                y = (AndroidUtilities.dp(100) - child.getMeasuredHeight()) / 2 + getPaddingTop();
            } else if (showAtPos == 1) {
                y = (height / 2 - child.getMeasuredHeight()) / 2 + getPaddingTop();
            } else {
                y = (height - child.getMeasuredHeight()) / 2 + getPaddingTop();
            }
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
        }
        inLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!inLayout) {
            super.requestLayout();
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
