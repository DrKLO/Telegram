package org.telegram.ui.Components;

import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;

public class AnimatedAvatarContainer extends FrameLayout {

    boolean occupyStatusBar = true;
    private int leftPadding = AndroidUtilities.dp(8);
    AnimatedTextView titleTextView;
    AnimatedTextView subtitleTextView;
    public AnimatedAvatarContainer(@NonNull Context context) {
        super(context);
        titleTextView = new AnimatedTextView(context, true, true, true);
        titleTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        titleTextView.setTextSize(AndroidUtilities.dp(18));
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setTypeface(AndroidUtilities.bold());
        titleTextView.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(12));
        addView(titleTextView);

        subtitleTextView = new AnimatedTextView(context, true, true, true);
        subtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        subtitleTextView.setTextSize(AndroidUtilities.dp(14));
        subtitleTextView.setGravity(Gravity.LEFT);
        subtitleTextView.setPadding(0, 0, AndroidUtilities.dp(10), 0);
        addView(subtitleTextView);

        titleTextView.getDrawable().setAllowCancel(true);
        subtitleTextView.getDrawable().setAllowCancel(true);
        titleTextView.setAnimationProperties(1f, 0, 150, CubicBezierInterpolator.DEFAULT);
        subtitleTextView.setAnimationProperties(1f, 0, 150, CubicBezierInterpolator.DEFAULT);

        setClipChildren(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) + titleTextView.getPaddingRight();
        int availableWidth = width - AndroidUtilities.dp( 16);
        titleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24 + 8) + titleTextView.getPaddingRight(), MeasureSpec.AT_MOST));
        subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.AT_MOST));
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int actionBarHeight = ActionBar.getCurrentActionBarHeight();
        int viewTop = (actionBarHeight - AndroidUtilities.dp(42)) / 2 + (Build.VERSION.SDK_INT >= 21 && occupyStatusBar ? AndroidUtilities.statusBarHeight : 0);
        int l = leftPadding;
        if (subtitleTextView.getVisibility() != GONE) {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(1f) - titleTextView.getPaddingTop(), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(1.3f) - titleTextView.getPaddingTop() + titleTextView.getPaddingBottom());
        } else {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(11) - titleTextView.getPaddingTop(), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(11) - titleTextView.getPaddingTop() + titleTextView.getPaddingBottom());
        }
        subtitleTextView.layout(l, viewTop + AndroidUtilities.dp(20), l + subtitleTextView.getMeasuredWidth(), viewTop + subtitleTextView.getTextHeight() + AndroidUtilities.dp(24));
    }

    public AnimatedTextView getTitle() {
        return titleTextView;
    }

    public AnimatedTextView getSubtitleTextView() {
        return subtitleTextView;
    }
}
