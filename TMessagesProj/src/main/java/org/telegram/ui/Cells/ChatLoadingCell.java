/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.Theme;

public class ChatLoadingCell extends FrameLayout {

    private FrameLayout frameLayout;

    public ChatLoadingCell(Context context) {
        super(context);

        frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundResource(R.drawable.system_loader);
        frameLayout.getBackground().setColorFilter(Theme.colorFilter);
        addView(frameLayout, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

        ProgressBar progressBar = new ProgressBar(context);
        try {
            progressBar.setIndeterminateDrawable(getResources().getDrawable(R.drawable.loading_animation));
        } catch (Exception e) {
            //don't promt
        }
        progressBar.setIndeterminate(true);
        AndroidUtilities.setProgressBarAnimationDuration(progressBar, 1500);
        frameLayout.addView(progressBar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44), MeasureSpec.EXACTLY));
    }

    public void setProgressVisible(boolean value) {
        frameLayout.setVisibility(value ? VISIBLE : INVISIBLE);
    }
}
