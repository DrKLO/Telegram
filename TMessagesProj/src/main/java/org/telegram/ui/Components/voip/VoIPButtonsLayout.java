package org.telegram.ui.Components.voip;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

public class VoIPButtonsLayout extends FrameLayout {

    public VoIPButtonsLayout(@NonNull Context context) {
        super(context);
    }

    int visibleChildCount;
    int childWidth;
    int childPadding;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heigth = MeasureSpec.getSize(heightMeasureSpec);
        visibleChildCount = 0;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() != View.GONE) {
                visibleChildCount++;
            }
        }
        childWidth = AndroidUtilities.dp(68);
        int maxChildHeigth = 0;
        childPadding = (width / getChildCount() - childWidth) / 2;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() != View.GONE) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
                if (getChildAt(i).getMeasuredHeight() > maxChildHeigth) {
                    maxChildHeigth = getChildAt(i).getMeasuredHeight();
                }
            }
        }

        int h = Math.max(maxChildHeigth, AndroidUtilities.dp(80));
        setMeasuredDimension(width, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int startFrom = (int) ((getChildCount() - visibleChildCount) / 2f * (childWidth + childPadding * 2));
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                child.layout(startFrom + childPadding, 0, startFrom + childPadding + child.getMeasuredWidth(), child.getMeasuredHeight());
                startFrom += childPadding * 2 + child.getMeasuredWidth();
            }
        }
    }
}
