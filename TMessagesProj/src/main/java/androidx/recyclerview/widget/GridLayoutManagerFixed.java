/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package androidx.recyclerview.widget;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import com.google.android.exoplayer2.util.Log;

import org.telegram.ui.Cells.ChatMessageCell;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A {@link RecyclerView.LayoutManager} implementations that lays out items in a grid.
 * <p>
 * By default, each item occupies 1 span. You can change it by providing a custom
 * {@link SpanSizeLookup} instance via {@link #setSpanSizeLookup(SpanSizeLookup)}.
 */
public class GridLayoutManagerFixed extends GridLayoutManager {

    private ArrayList<View> additionalViews = new ArrayList<>(4);
    private boolean canScrollVertically = true;

    public GridLayoutManagerFixed(Context context, int spanCount) {
        super(context, spanCount);
    }

    public GridLayoutManagerFixed(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, spanCount, orientation, reverseLayout);
    }

    protected boolean hasSiblingChild(int position) {
        return false;
    }

    public void setCanScrollVertically(boolean value) {
        canScrollVertically = value;
    }

    @Override
    public boolean canScrollVertically() {
        return canScrollVertically;
    }

    @Override
    protected void recycleViewsFromStart(RecyclerView.Recycler recycler, int scrollingOffset,
                                         int noRecycleSpace) {
        if (scrollingOffset < 0) {
            return;
        }
        // ignore padding, ViewGroup may not clip children.
        final int childCount = getChildCount();
        if (mShouldReverseLayout) {
            for (int i = childCount - 1; i >= 0; i--) {
                View child = getChildAt(i);
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
                if (child.getBottom() + params.bottomMargin > scrollingOffset
                        || child.getTop() + child.getHeight() > scrollingOffset) {
                    // stop here
                    recycleChildren(recycler, childCount - 1, i);
                    return;
                }
            }
        } else {
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
                if (child.getBottom() + params.bottomMargin > scrollingOffset
                        || child.getTop() + child.getHeight() > scrollingOffset) {
                    // stop here
                    recycleChildren(recycler, 0, i);
                    return;
                }
            }
        }
    }

    @Override
    protected int[] calculateItemBorders(int[] cachedBorders, int spanCount, int totalSpace) {
        if (cachedBorders == null || cachedBorders.length != spanCount + 1 || cachedBorders[cachedBorders.length - 1] != totalSpace) {
            cachedBorders = new int[spanCount + 1];
        }
        cachedBorders[0] = 0;
        for (int i = 1; i <= spanCount; i++) {
            cachedBorders[i] = (int) Math.ceil(i / (float) spanCount * totalSpace);
        }
        return cachedBorders;
    }

    public boolean shouldLayoutChildFromOpositeSide(View child) {
        return false;
    }

    @Override
    protected void measureChild(View view, int otherDirParentSpecMode, boolean alreadyMeasured) {
        final LayoutParams lp = (LayoutParams) view.getLayoutParams();
        final Rect decorInsets = lp.mDecorInsets;
        final int verticalInsets = decorInsets.top + decorInsets.bottom
                + lp.topMargin + lp.bottomMargin;
        final int horizontalInsets = decorInsets.left + decorInsets.right
                + lp.leftMargin + lp.rightMargin;
        final int availableSpaceInOther = mCachedBorders[lp.mSpanSize];
        final int wSpec = getChildMeasureSpec(availableSpaceInOther, otherDirParentSpecMode,
                    horizontalInsets, lp.width, false);
        final int hSpec = getChildMeasureSpec(mOrientationHelper.getTotalSpace(), getHeightMode(),
                    verticalInsets, lp.height, true);
        measureChildWithDecorationsAndMargin(view, wSpec, hSpec, alreadyMeasured);
    }

    @Override
    void layoutChunk(RecyclerView.Recycler recycler, RecyclerView.State state, LayoutState layoutState, LayoutChunkResult result) {
        final int otherDirSpecMode = mOrientationHelper.getModeInOther();

        final boolean layingOutInPrimaryDirection = layoutState.mItemDirection == LayoutState.ITEM_DIRECTION_TAIL;
        result.mConsumed = 0;

        int startPosition = layoutState.mCurrentPosition;
        if (
            mShouldReverseLayout &&
            layoutState.mLayoutDirection != LayoutState.LAYOUT_START &&
            hasSiblingChild(layoutState.mCurrentPosition) &&
            findViewByPosition(layoutState.mCurrentPosition + 1) == null
        ) {
            if (hasSiblingChild(layoutState.mCurrentPosition + 1)) {
                layoutState.mCurrentPosition += 3;
            } else {
                layoutState.mCurrentPosition += 2;
            }
            int backupPosition = layoutState.mCurrentPosition;
            for (int a = layoutState.mCurrentPosition; a > startPosition; a--) {
                View view = layoutState.next(recycler);
                if (view == null) {
                    continue;
                }
                additionalViews.add(view);
                if (a != backupPosition) {
                    calculateItemDecorationsForChild(view, mDecorInsets);
                    measureChild(view, otherDirSpecMode, false);
                    int size = mOrientationHelper.getDecoratedMeasurement(view);
                    layoutState.mOffset -= size;
                    layoutState.mAvailable += size;
                }
            }
            layoutState.mCurrentPosition = backupPosition;
        }

        boolean working = true;
        while (working) {
            int count = 0;
            int remainingSpan = mSpanCount;
            working = !additionalViews.isEmpty();
            while (count < mSpanCount && layoutState.hasMore(state) && remainingSpan > 0) {
                int pos = layoutState.mCurrentPosition;
                final int spanSize = getSpanSize(recycler, state, pos);
                remainingSpan -= spanSize;
                if (remainingSpan < 0) {
                    break;
                }
                View view;
                if (!additionalViews.isEmpty()) {
                    view = additionalViews.get(0);
                    additionalViews.remove(0);
                    layoutState.mCurrentPosition--;
                } else {
                    view = layoutState.next(recycler);
                }
                if (view == null) {
                    break;
                }
                mSet[count] = view;
                count++;
                if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START && remainingSpan <= 0 && hasSiblingChild(pos)) {
                    working = true;
                }
            }

            if (count == 0) {
                result.mFinished = true;
                return;
            }

            int maxSize = 0;
            float maxSizeInOther = 0;

            assignSpans(recycler, state, count, layingOutInPrimaryDirection);
            for (int i = 0; i < count; i++) {
                View view = mSet[i];
                if (layoutState.mScrapList == null) {
                    if (layingOutInPrimaryDirection) {
                        addView(view);
                    } else {
                        addView(view, 0);
                    }
                } else {
                    if (layingOutInPrimaryDirection) {
                        addDisappearingView(view);
                    } else {
                        addDisappearingView(view, 0);
                    }
                }
                calculateItemDecorationsForChild(view, mDecorInsets);

                measureChild(view, otherDirSpecMode, false);
                final int size = mOrientationHelper.getDecoratedMeasurement(view);
                if (size > maxSize) {
                    maxSize = size;
                }
                final LayoutParams lp = (LayoutParams) view.getLayoutParams();
                final float otherSize = 1f * mOrientationHelper.getDecoratedMeasurementInOther(view) / lp.mSpanSize;
                if (otherSize > maxSizeInOther) {
                    maxSizeInOther = otherSize;
                }
            }

            // Views that did not measure the maxSize has to be re-measured
            // We will stop doing this once we introduce Gravity in the GLM layout params
            for (int i = 0; i < count; i++) {
                final View view = mSet[i];
                if (mOrientationHelper.getDecoratedMeasurement(view) != maxSize) {
                    final LayoutParams lp = (LayoutParams) view.getLayoutParams();
                    final Rect decorInsets = lp.mDecorInsets;
                    final int verticalInsets = decorInsets.top + decorInsets.bottom + lp.topMargin + lp.bottomMargin;
                    final int horizontalInsets = decorInsets.left + decorInsets.right + lp.leftMargin + lp.rightMargin;
                    final int totalSpaceInOther = mCachedBorders[lp.mSpanSize];

                    final int wSpec = getChildMeasureSpec(totalSpaceInOther, View.MeasureSpec.EXACTLY, horizontalInsets, lp.width, false);
                    final int hSpec = View.MeasureSpec.makeMeasureSpec(maxSize - verticalInsets, View.MeasureSpec.EXACTLY);

                    measureChildWithDecorationsAndMargin(view, wSpec, hSpec, true);
                }
            }

            int left, right, top, bottom;
            boolean fromOppositeSide = shouldLayoutChildFromOpositeSide(mSet[0]);
            if (
                fromOppositeSide && layoutState.mLayoutDirection == LayoutState.LAYOUT_START ||
                !fromOppositeSide && layoutState.mLayoutDirection == LayoutState.LAYOUT_END
            ) {
                if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                    bottom = layoutState.mOffset - result.mConsumed;
                    top = bottom - maxSize;
                    left = 0;
                } else {
                    top = layoutState.mOffset + result.mConsumed;
                    bottom = top + maxSize;
                    left = getWidth();
                }
                for (int i = count - 1; i >= 0; i--) {
                    View view = mSet[i];
                    LayoutParams params = (LayoutParams) view.getLayoutParams();

                    right = mOrientationHelper.getDecoratedMeasurementInOther(view);
                    if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                        left -= right;
                    }
                    layoutDecoratedWithMargins(view, left, top, left + right, bottom);
                    if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                        left += right;
                    }
                    if (params.isItemRemoved() || params.isItemChanged()) {
                        result.mIgnoreConsumed = true;
                    }
                    result.mFocusable |= view.hasFocusable();
                }
            } else {
                if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                    bottom = layoutState.mOffset - result.mConsumed;
                    top = bottom - maxSize;
                    left = getWidth();
                } else {
                    top = layoutState.mOffset + result.mConsumed;
                    bottom = top + maxSize;
                    left = 0;
                }
                for (int i = 0; i < count; i++) {
                    View view = mSet[i];
                    LayoutParams params = (LayoutParams) view.getLayoutParams();

                    right = mOrientationHelper.getDecoratedMeasurementInOther(view);
                    if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                        left -= right;
                    }
                    layoutDecoratedWithMargins(view, left, top, left + right, bottom);
                    if (layoutState.mLayoutDirection == LayoutState.LAYOUT_END) {
                        left += right;
                    }
                    if (params.isItemRemoved() || params.isItemChanged()) {
                        result.mIgnoreConsumed = true;
                    }
                    result.mFocusable |= view.hasFocusable();
                }
            }
            result.mConsumed += maxSize;
            Arrays.fill(mSet, null);
        }
    }
}
