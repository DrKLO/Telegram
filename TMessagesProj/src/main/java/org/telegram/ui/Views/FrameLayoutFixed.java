/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;

public class FrameLayoutFixed extends FrameLayout {
    private final ArrayList<View> mMatchParentChildren = new ArrayList<View>(1);

    public FrameLayoutFixed(Context context) {
        super(context);
    }

    public FrameLayoutFixed(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FrameLayoutFixed(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public final int getMeasuredStateFixed(View view) {
        return (view.getMeasuredWidth()&0xff000000)
                | ((view.getMeasuredHeight()>>16)
                & (0xff000000>>16));
    }

    public static int resolveSizeAndStateFixed(int size, int measureSpec, int childMeasuredState) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize =  MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case MeasureSpec.AT_MOST:
                if (specSize < size) {
                    result = specSize | 0x01000000;
                } else {
                    result = size;
                }
                break;
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
        }
        return result | (childMeasuredState&0xff000000);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            int count = getChildCount();

            final boolean measureMatchParentChildren =
                    MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY ||
                            MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY;
            mMatchParentChildren.clear();

            int maxHeight = 0;
            int maxWidth = 0;
            int childState = 0;

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    maxWidth = Math.max(maxWidth,
                            child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                    maxHeight = Math.max(maxHeight,
                            child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
                    childState |= getMeasuredStateFixed(child);
                    if (measureMatchParentChildren) {
                        if (lp.width == LayoutParams.MATCH_PARENT ||
                                lp.height == LayoutParams.MATCH_PARENT) {
                            mMatchParentChildren.add(child);
                        }
                    }
                }
            }

            // Account for padding too
            maxWidth += getPaddingLeft() + getPaddingRight();
            maxHeight += getPaddingTop() + getPaddingBottom();

            // Check against our minimum height and width
            maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
            maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

            // Check against our foreground's minimum height and width
            final Drawable drawable = getForeground();
            if (drawable != null) {
                maxHeight = Math.max(maxHeight, drawable.getMinimumHeight());
                maxWidth = Math.max(maxWidth, drawable.getMinimumWidth());
            }

            setMeasuredDimension(resolveSizeAndStateFixed(maxWidth, widthMeasureSpec, childState),
                    resolveSizeAndStateFixed(maxHeight, heightMeasureSpec, childState << MEASURED_HEIGHT_STATE_SHIFT));

            count = mMatchParentChildren.size();
            if (count > 1) {
                for (int i = 0; i < count; i++) {
                    final View child = mMatchParentChildren.get(i);

                    final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                    int childWidthMeasureSpec;
                    int childHeightMeasureSpec;

                    if (lp.width == LayoutParams.MATCH_PARENT) {
                        childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth() -
                                getPaddingLeft() - getPaddingRight() -
                                lp.leftMargin - lp.rightMargin,
                                MeasureSpec.EXACTLY);
                    } else {
                        childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                                getPaddingLeft() + getPaddingRight() +
                                        lp.leftMargin + lp.rightMargin,
                                lp.width);
                    }

                    if (lp.height == LayoutParams.MATCH_PARENT) {
                        childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight() -
                                getPaddingTop() - getPaddingBottom() -
                                lp.topMargin - lp.bottomMargin,
                                MeasureSpec.EXACTLY);
                    } else {
                        childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                                getPaddingTop() + getPaddingBottom() +
                                        lp.topMargin + lp.bottomMargin,
                                lp.height);
                    }

                    child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
