/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.recyclerview.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import java.util.Arrays;

/**
 * A {@link RecyclerView.LayoutManager} implementations that lays out items in a grid.
 * <p>
 * By default, each item occupies 1 span. You can change it by providing a custom
 * {@link SpanSizeLookup} instance via {@link #setSpanSizeLookup(SpanSizeLookup)}.
 */
public class GridLayoutManager extends LinearLayoutManager {

    private static final boolean DEBUG = false;
    private static final String TAG = "GridLayoutManager";
    public static final int DEFAULT_SPAN_COUNT = -1;
    /**
     * Span size have been changed but we've not done a new layout calculation.
     */
    boolean mPendingSpanCountChange = false;
    int mSpanCount = DEFAULT_SPAN_COUNT;
    /**
     * Right borders for each span.
     * <p>For <b>i-th</b> item start is {@link #mCachedBorders}[i-1] + 1
     * and end is {@link #mCachedBorders}[i].
     */
    protected int [] mCachedBorders;
    /**
     * Temporary array to keep views in layoutChunk method
     */
    View[] mSet;
    final SparseIntArray mPreLayoutSpanSizeCache = new SparseIntArray();
    final SparseIntArray mPreLayoutSpanIndexCache = new SparseIntArray();
    SpanSizeLookup mSpanSizeLookup = new DefaultSpanSizeLookup();
    // re-used variable to acquire decor insets from RecyclerView
    final Rect mDecorInsets = new Rect();

    private boolean mUsingSpansToEstimateScrollBarDimensions;

    /**
     * Creates a vertical GridLayoutManager
     *
     * @param context Current context, will be used to access resources.
     * @param spanCount The number of columns in the grid
     */
    public GridLayoutManager(Context context, int spanCount) {
        super(context);
        setSpanCount(spanCount);
    }

    /**
     * @param context Current context, will be used to access resources.
     * @param spanCount The number of columns or rows in the grid
     * @param orientation Layout orientation. Should be {@link #HORIZONTAL} or {@link
     *                      #VERTICAL}.
     * @param reverseLayout When set to true, layouts from end to start.
     */
    public GridLayoutManager(Context context, int spanCount,
            @RecyclerView.Orientation int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
        setSpanCount(spanCount);
    }

    /**
     * stackFromEnd is not supported by GridLayoutManager. Consider using
     * {@link #setReverseLayout(boolean)}.
     */
    @Override
    public void setStackFromEnd(boolean stackFromEnd) {
        if (stackFromEnd) {
            throw new UnsupportedOperationException(
                    "GridLayoutManager does not support stack from end."
                            + " Consider using reverse layout");
        }
        super.setStackFromEnd(false);
    }

    @Override
    public int getRowCountForAccessibility(RecyclerView.Recycler recycler,
            RecyclerView.State state) {
        if (mOrientation == HORIZONTAL) {
            return mSpanCount;
        }
        if (state.getItemCount() < 1) {
            return 0;
        }

        // Row count is one more than the last item's row index.
        return getSpanGroupIndex(recycler, state, state.getItemCount() - 1) + 1;
    }

    @Override
    public int getColumnCountForAccessibility(RecyclerView.Recycler recycler,
            RecyclerView.State state) {
        if (mOrientation == VERTICAL) {
            return mSpanCount;
        }
        if (state.getItemCount() < 1) {
            return 0;
        }

        // Column count is one more than the last item's column index.
        return getSpanGroupIndex(recycler, state, state.getItemCount() - 1) + 1;
    }

    @Override
    public void onInitializeAccessibilityNodeInfoForItem(RecyclerView.Recycler recycler,
            RecyclerView.State state, View host, AccessibilityNodeInfoCompat info) {
        ViewGroup.LayoutParams lp = host.getLayoutParams();
        if (!(lp instanceof LayoutParams)) {
            super.onInitializeAccessibilityNodeInfoForItem(host, info);
            return;
        }
        LayoutParams glp = (LayoutParams) lp;
        int spanGroupIndex = getSpanGroupIndex(recycler, state, glp.getViewLayoutPosition());
        if (mOrientation == HORIZONTAL) {
            info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    glp.getSpanIndex(), glp.getSpanSize(),
                    spanGroupIndex, 1,
                    mSpanCount > 1 && glp.getSpanSize() == mSpanCount, false));
        } else { // VERTICAL
            info.setCollectionItemInfo(AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                    spanGroupIndex , 1,
                    glp.getSpanIndex(), glp.getSpanSize(),
                    mSpanCount > 1 && glp.getSpanSize() == mSpanCount, false));
        }
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (state.isPreLayout()) {
            cachePreLayoutSpanMapping();
        }
        super.onLayoutChildren(recycler, state);
        if (DEBUG) {
            validateChildOrder();
        }
        clearPreLayoutSpanMappingCache();
    }

    @Override
    public void onLayoutCompleted(RecyclerView.State state) {
        super.onLayoutCompleted(state);
        mPendingSpanCountChange = false;
    }

    private void clearPreLayoutSpanMappingCache() {
        mPreLayoutSpanSizeCache.clear();
        mPreLayoutSpanIndexCache.clear();
    }

    private void cachePreLayoutSpanMapping() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
            final int viewPosition = lp.getViewLayoutPosition();
            mPreLayoutSpanSizeCache.put(viewPosition, lp.getSpanSize());
            mPreLayoutSpanIndexCache.put(viewPosition, lp.getSpanIndex());
        }
    }

    @Override
    public void onItemsAdded(RecyclerView recyclerView, int positionStart, int itemCount) {
        mSpanSizeLookup.invalidateSpanIndexCache();
        mSpanSizeLookup.invalidateSpanGroupIndexCache();
    }

    @Override
    public void onItemsChanged(RecyclerView recyclerView) {
        mSpanSizeLookup.invalidateSpanIndexCache();
        mSpanSizeLookup.invalidateSpanGroupIndexCache();
    }

    @Override
    public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
        mSpanSizeLookup.invalidateSpanIndexCache();
        mSpanSizeLookup.invalidateSpanGroupIndexCache();
    }

    @Override
    public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount,
            Object payload) {
        mSpanSizeLookup.invalidateSpanIndexCache();
        mSpanSizeLookup.invalidateSpanGroupIndexCache();
    }

    @Override
    public void onItemsMoved(RecyclerView recyclerView, int from, int to, int itemCount) {
        mSpanSizeLookup.invalidateSpanIndexCache();
        mSpanSizeLookup.invalidateSpanGroupIndexCache();
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        if (mOrientation == HORIZONTAL) {
            return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs);
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            return new LayoutParams((ViewGroup.MarginLayoutParams) lp);
        } else {
            return new LayoutParams(lp);
        }
    }

    @Override
    public boolean checkLayoutParams(RecyclerView.LayoutParams lp) {
        return lp instanceof LayoutParams;
    }

    /**
     * Sets the source to get the number of spans occupied by each item in the adapter.
     *
     * @param spanSizeLookup {@link SpanSizeLookup} instance to be used to query number of spans
     *                       occupied by each item
     */
    public void setSpanSizeLookup(SpanSizeLookup spanSizeLookup) {
        mSpanSizeLookup = spanSizeLookup;
    }

    /**
     * Returns the current {@link SpanSizeLookup} used by the GridLayoutManager.
     *
     * @return The current {@link SpanSizeLookup} used by the GridLayoutManager.
     */
    public SpanSizeLookup getSpanSizeLookup() {
        return mSpanSizeLookup;
    }

    private void updateMeasurements() {
        int totalSpace;
        if (getOrientation() == VERTICAL) {
            totalSpace = getWidth() - getPaddingRight() - getPaddingLeft();
        } else {
            totalSpace = getHeight() - getPaddingBottom() - getPaddingTop();
        }
        calculateItemBorders(totalSpace);
    }

    @Override
    public void setMeasuredDimension(Rect childrenBounds, int wSpec, int hSpec) {
        if (mCachedBorders == null) {
            super.setMeasuredDimension(childrenBounds, wSpec, hSpec);
        }
        final int width, height;
        final int horizontalPadding = getPaddingLeft() + getPaddingRight();
        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        if (mOrientation == VERTICAL) {
            final int usedHeight = childrenBounds.height() + verticalPadding;
            height = chooseSize(hSpec, usedHeight, getMinimumHeight());
            width = chooseSize(wSpec, mCachedBorders[mCachedBorders.length - 1] + horizontalPadding,
                    getMinimumWidth());
        } else {
            final int usedWidth = childrenBounds.width() + horizontalPadding;
            width = chooseSize(wSpec, usedWidth, getMinimumWidth());
            height = chooseSize(hSpec, mCachedBorders[mCachedBorders.length - 1] + verticalPadding,
                    getMinimumHeight());
        }
        setMeasuredDimension(width, height);
    }

    /**
     * @param totalSpace Total available space after padding is removed
     */
    private void calculateItemBorders(int totalSpace) {
        mCachedBorders = calculateItemBorders(mCachedBorders, mSpanCount, totalSpace);
    }

    /**
     * @param cachedBorders The out array
     * @param spanCount number of spans
     * @param totalSpace total available space after padding is removed
     * @return The updated array. Might be the same instance as the provided array if its size
     * has not changed.
     */
    protected int[] calculateItemBorders(int[] cachedBorders, int spanCount, int totalSpace) {
        if (cachedBorders == null || cachedBorders.length != spanCount + 1
                || cachedBorders[cachedBorders.length - 1] != totalSpace) {
            cachedBorders = new int[spanCount + 1];
        }
        cachedBorders[0] = 0;
        int sizePerSpan = totalSpace / spanCount;
        int sizePerSpanRemainder = totalSpace % spanCount;
        int consumedPixels = 0;
        int additionalSize = 0;
        for (int i = 1; i <= spanCount; i++) {
            int itemSize = sizePerSpan;
            additionalSize += sizePerSpanRemainder;
            if (additionalSize > 0 && (spanCount - additionalSize) < sizePerSpanRemainder) {
                itemSize += 1;
                additionalSize -= spanCount;
            }
            consumedPixels += itemSize;
            cachedBorders[i] = consumedPixels;
        }
        return cachedBorders;
    }

    int getSpaceForSpanRange(int startSpan, int spanSize) {
        if (mOrientation == VERTICAL && isLayoutRTL()) {
            return mCachedBorders[mSpanCount - startSpan]
                    - mCachedBorders[mSpanCount - startSpan - spanSize];
        } else {
            return mCachedBorders[startSpan + spanSize] - mCachedBorders[startSpan];
        }
    }

    @Override
    void onAnchorReady(RecyclerView.Recycler recycler, RecyclerView.State state,
                       AnchorInfo anchorInfo, int itemDirection) {
        super.onAnchorReady(recycler, state, anchorInfo, itemDirection);
        updateMeasurements();
        if (state.getItemCount() > 0 && !state.isPreLayout()) {
            ensureAnchorIsInCorrectSpan(recycler, state, anchorInfo, itemDirection);
        }
        ensureViewSet();
    }

    private void ensureViewSet() {
        if (mSet == null || mSet.length != mSpanCount) {
            mSet = new View[mSpanCount];
        }
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler,
            RecyclerView.State state) {
        updateMeasurements();
        ensureViewSet();
        return super.scrollHorizontallyBy(dx, recycler, state);
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
            RecyclerView.State state) {
        updateMeasurements();
        ensureViewSet();
        return super.scrollVerticallyBy(dy, recycler, state);
    }

    private void ensureAnchorIsInCorrectSpan(RecyclerView.Recycler recycler,
            RecyclerView.State state, AnchorInfo anchorInfo, int itemDirection) {
        final boolean layingOutInPrimaryDirection =
                itemDirection == LayoutState.ITEM_DIRECTION_TAIL;
        int span = getSpanIndex(recycler, state, anchorInfo.mPosition);
        if (layingOutInPrimaryDirection) {
            // choose span 0
            while (span > 0 && anchorInfo.mPosition > 0) {
                anchorInfo.mPosition--;
                span = getSpanIndex(recycler, state, anchorInfo.mPosition);
            }
        } else {
            // choose the max span we can get. hopefully last one
            final int indexLimit = state.getItemCount() - 1;
            int pos = anchorInfo.mPosition;
            int bestSpan = span;
            while (pos < indexLimit) {
                int next = getSpanIndex(recycler, state, pos + 1);
                if (next > bestSpan) {
                    pos += 1;
                    bestSpan = next;
                } else {
                    break;
                }
            }
            anchorInfo.mPosition = pos;
        }
    }

    @Override
    View findReferenceChild(RecyclerView.Recycler recycler, RecyclerView.State state,
                            int start, int end, int itemCount) {
        ensureLayoutState();
        View invalidMatch = null;
        View outOfBoundsMatch = null;
        final int boundsStart = mOrientationHelper.getStartAfterPadding();
        final int boundsEnd = mOrientationHelper.getEndAfterPadding();
        final int diff = end > start ? 1 : -1;

        for (int i = start; i != end; i += diff) {
            final View view = getChildAt(i);
            final int position = getPosition(view);
            if (position >= 0 && position < itemCount) {
                final int span = getSpanIndex(recycler, state, position);
                if (span != 0) {
                    continue;
                }
                if (((RecyclerView.LayoutParams) view.getLayoutParams()).isItemRemoved()) {
                    if (invalidMatch == null) {
                        invalidMatch = view; // removed item, least preferred
                    }
                } else if (mOrientationHelper.getDecoratedStart(view) >= boundsEnd
                        || mOrientationHelper.getDecoratedEnd(view) < boundsStart) {
                    if (outOfBoundsMatch == null) {
                        outOfBoundsMatch = view; // item is not visible, less preferred
                    }
                } else {
                    return view;
                }
            }
        }
        return outOfBoundsMatch != null ? outOfBoundsMatch : invalidMatch;
    }

    private int getSpanGroupIndex(RecyclerView.Recycler recycler, RecyclerView.State state,
            int viewPosition) {
        if (!state.isPreLayout()) {
            return mSpanSizeLookup.getCachedSpanGroupIndex(viewPosition, mSpanCount);
        }
        final int adapterPosition = recycler.convertPreLayoutPositionToPostLayout(viewPosition);
        if (adapterPosition == -1) {
            if (DEBUG) {
                throw new RuntimeException("Cannot find span group index for position "
                        + viewPosition);
            }
            Log.w(TAG, "Cannot find span size for pre layout position. " + viewPosition);
            return 0;
        }
        return mSpanSizeLookup.getCachedSpanGroupIndex(adapterPosition, mSpanCount);
    }

    private int getSpanIndex(RecyclerView.Recycler recycler, RecyclerView.State state, int pos) {
        if (!state.isPreLayout()) {
            return mSpanSizeLookup.getCachedSpanIndex(pos, mSpanCount);
        }
        final int cached = mPreLayoutSpanIndexCache.get(pos, -1);
        if (cached != -1) {
            return cached;
        }
        final int adapterPosition = recycler.convertPreLayoutPositionToPostLayout(pos);
        if (adapterPosition == -1) {
            if (DEBUG) {
                throw new RuntimeException("Cannot find span index for pre layout position. It is"
                        + " not cached, not in the adapter. Pos:" + pos);
            }
            Log.w(TAG, "Cannot find span size for pre layout position. It is"
                    + " not cached, not in the adapter. Pos:" + pos);
            return 0;
        }
        return mSpanSizeLookup.getCachedSpanIndex(adapterPosition, mSpanCount);
    }

    protected int getSpanSize(RecyclerView.Recycler recycler, RecyclerView.State state, int pos) {
        if (!state.isPreLayout()) {
            return mSpanSizeLookup.getSpanSize(pos);
        }
        final int cached = mPreLayoutSpanSizeCache.get(pos, -1);
        if (cached != -1) {
            return cached;
        }
        final int adapterPosition = recycler.convertPreLayoutPositionToPostLayout(pos);
        if (adapterPosition == -1) {
            if (DEBUG) {
                throw new RuntimeException("Cannot find span size for pre layout position. It is"
                        + " not cached, not in the adapter. Pos:" + pos);
            }
            Log.w(TAG, "Cannot find span size for pre layout position. It is"
                    + " not cached, not in the adapter. Pos:" + pos);
            return 1;
        }
        return mSpanSizeLookup.getSpanSize(adapterPosition);
    }

    @Override
    void collectPrefetchPositionsForLayoutState(RecyclerView.State state, LayoutState layoutState,
            LayoutPrefetchRegistry layoutPrefetchRegistry) {
        int remainingSpan = mSpanCount;
        int count = 0;
        while (count < mSpanCount && layoutState.hasMore(state) && remainingSpan > 0) {
            final int pos = layoutState.mCurrentPosition;
            layoutPrefetchRegistry.addPosition(pos, Math.max(0, layoutState.mScrollingOffset));
            final int spanSize = mSpanSizeLookup.getSpanSize(pos);
            remainingSpan -= spanSize;
            layoutState.mCurrentPosition += layoutState.mItemDirection;
            count++;
        }
    }

    @Override
    void layoutChunk(RecyclerView.Recycler recycler, RecyclerView.State state,
            LayoutState layoutState, LayoutChunkResult result) {
        final int otherDirSpecMode = mOrientationHelper.getModeInOther();
        final boolean flexibleInOtherDir = otherDirSpecMode != View.MeasureSpec.EXACTLY;
        final int currentOtherDirSize = getChildCount() > 0 ? mCachedBorders[mSpanCount] : 0;
        // if grid layout's dimensions are not specified, let the new row change the measurements
        // This is not perfect since we not covering all rows but still solves an important case
        // where they may have a header row which should be laid out according to children.
        if (flexibleInOtherDir) {
            updateMeasurements(); //  reset measurements
        }
        final boolean layingOutInPrimaryDirection =
                layoutState.mItemDirection == LayoutState.ITEM_DIRECTION_TAIL;
        int count = 0;
        int consumedSpanCount = 0;
        int remainingSpan = mSpanCount;
        if (!layingOutInPrimaryDirection) {
            int itemSpanIndex = getSpanIndex(recycler, state, layoutState.mCurrentPosition);
            int itemSpanSize = getSpanSize(recycler, state, layoutState.mCurrentPosition);
            remainingSpan = itemSpanIndex + itemSpanSize;
        }
        while (count < mSpanCount && layoutState.hasMore(state) && remainingSpan > 0) {
            int pos = layoutState.mCurrentPosition;
            final int spanSize = getSpanSize(recycler, state, pos);
            if (spanSize > mSpanCount) {
                throw new IllegalArgumentException("Item at position " + pos + " requires "
                        + spanSize + " spans but GridLayoutManager has only " + mSpanCount
                        + " spans.");
            }
            remainingSpan -= spanSize;
            if (remainingSpan < 0) {
                break; // item did not fit into this row or column
            }
            View view = layoutState.next(recycler);
            if (view == null) {
                break;
            }
            consumedSpanCount += spanSize;
            mSet[count] = view;
            count++;
        }

        if (count == 0) {
            result.mFinished = true;
            return;
        }

        int maxSize = 0;
        float maxSizeInOther = 0; // use a float to get size per span

        // we should assign spans before item decor offsets are calculated
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
            final float otherSize = 1f * mOrientationHelper.getDecoratedMeasurementInOther(view)
                    / lp.mSpanSize;
            if (otherSize > maxSizeInOther) {
                maxSizeInOther = otherSize;
            }
        }
        if (flexibleInOtherDir) {
            // re-distribute columns
            guessMeasurement(maxSizeInOther, currentOtherDirSize);
            // now we should re-measure any item that was match parent.
            maxSize = 0;
            for (int i = 0; i < count; i++) {
                View view = mSet[i];
                measureChild(view, View.MeasureSpec.EXACTLY, true);
                final int size = mOrientationHelper.getDecoratedMeasurement(view);
                if (size > maxSize) {
                    maxSize = size;
                }
            }
        }

        // Views that did not measure the maxSize has to be re-measured
        // We will stop doing this once we introduce Gravity in the GLM layout params
        for (int i = 0; i < count; i++) {
            final View view = mSet[i];
            if (mOrientationHelper.getDecoratedMeasurement(view) != maxSize) {
                final LayoutParams lp = (LayoutParams) view.getLayoutParams();
                final Rect decorInsets = lp.mDecorInsets;
                final int verticalInsets = decorInsets.top + decorInsets.bottom
                        + lp.topMargin + lp.bottomMargin;
                final int horizontalInsets = decorInsets.left + decorInsets.right
                        + lp.leftMargin + lp.rightMargin;
                final int totalSpaceInOther = getSpaceForSpanRange(lp.mSpanIndex, lp.mSpanSize);
                final int wSpec;
                final int hSpec;
                if (mOrientation == VERTICAL) {
                    wSpec = getChildMeasureSpec(totalSpaceInOther, View.MeasureSpec.EXACTLY,
                            horizontalInsets, lp.width, false);
                    hSpec = View.MeasureSpec.makeMeasureSpec(maxSize - verticalInsets,
                            View.MeasureSpec.EXACTLY);
                } else {
                    wSpec = View.MeasureSpec.makeMeasureSpec(maxSize - horizontalInsets,
                            View.MeasureSpec.EXACTLY);
                    hSpec = getChildMeasureSpec(totalSpaceInOther, View.MeasureSpec.EXACTLY,
                            verticalInsets, lp.height, false);
                }
                measureChildWithDecorationsAndMargin(view, wSpec, hSpec, true);
            }
        }

        result.mConsumed = maxSize;

        int left = 0, right = 0, top = 0, bottom = 0;
        if (mOrientation == VERTICAL) {
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                bottom = layoutState.mOffset;
                top = bottom - maxSize;
            } else {
                top = layoutState.mOffset;
                bottom = top + maxSize;
            }
        } else {
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                right = layoutState.mOffset;
                left = right - maxSize;
            } else {
                left = layoutState.mOffset;
                right = left + maxSize;
            }
        }
        for (int i = 0; i < count; i++) {
            View view = mSet[i];
            LayoutParams params = (LayoutParams) view.getLayoutParams();
            if (mOrientation == VERTICAL) {
                if (isLayoutRTL()) {
                    right = getPaddingLeft() + mCachedBorders[mSpanCount - params.mSpanIndex];
                    left = right - mOrientationHelper.getDecoratedMeasurementInOther(view);
                } else {
                    left = getPaddingLeft() + mCachedBorders[params.mSpanIndex];
                    right = left + mOrientationHelper.getDecoratedMeasurementInOther(view);
                }
            } else {
                top = getPaddingTop() + mCachedBorders[params.mSpanIndex];
                bottom = top + mOrientationHelper.getDecoratedMeasurementInOther(view);
            }
            // We calculate everything with View's bounding box (which includes decor and margins)
            // To calculate correct layout position, we subtract margins.
            layoutDecoratedWithMargins(view, left, top, right, bottom);
            if (DEBUG) {
                Log.d(TAG, "laid out child at position " + getPosition(view) + ", with l:"
                        + (left + params.leftMargin) + ", t:" + (top + params.topMargin) + ", r:"
                        + (right - params.rightMargin) + ", b:" + (bottom - params.bottomMargin)
                        + ", span:" + params.mSpanIndex + ", spanSize:" + params.mSpanSize);
            }
            // Consume the available space if the view is not removed OR changed
            if (params.isItemRemoved() || params.isItemChanged()) {
                result.mIgnoreConsumed = true;
            }
            result.mFocusable |= view.hasFocusable();
        }
        Arrays.fill(mSet, null);
    }

    /**
     * Measures a child with currently known information. This is not necessarily the child's final
     * measurement. (see fillChunk for details).
     *
     * @param view The child view to be measured
     * @param otherDirParentSpecMode The RV measure spec that should be used in the secondary
     *                               orientation
     * @param alreadyMeasured True if we've already measured this view once
     */
    protected void measureChild(View view, int otherDirParentSpecMode, boolean alreadyMeasured) {
        final LayoutParams lp = (LayoutParams) view.getLayoutParams();
        final Rect decorInsets = lp.mDecorInsets;
        final int verticalInsets = decorInsets.top + decorInsets.bottom
                + lp.topMargin + lp.bottomMargin;
        final int horizontalInsets = decorInsets.left + decorInsets.right
                + lp.leftMargin + lp.rightMargin;
        final int availableSpaceInOther = getSpaceForSpanRange(lp.mSpanIndex, lp.mSpanSize);
        final int wSpec;
        final int hSpec;
        if (mOrientation == VERTICAL) {
            wSpec = getChildMeasureSpec(availableSpaceInOther, otherDirParentSpecMode,
                    horizontalInsets, lp.width, false);
            hSpec = getChildMeasureSpec(mOrientationHelper.getTotalSpace(), getHeightMode(),
                    verticalInsets, lp.height, true);
        } else {
            hSpec = getChildMeasureSpec(availableSpaceInOther, otherDirParentSpecMode,
                    verticalInsets, lp.height, false);
            wSpec = getChildMeasureSpec(mOrientationHelper.getTotalSpace(), getWidthMode(),
                    horizontalInsets, lp.width, true);
        }
        measureChildWithDecorationsAndMargin(view, wSpec, hSpec, alreadyMeasured);
    }

    /**
     * This is called after laying out a row (if vertical) or a column (if horizontal) when the
     * RecyclerView does not have exact measurement specs.
     * <p>
     * Here we try to assign a best guess width or height and re-do the layout to update other
     * views that wanted to MATCH_PARENT in the non-scroll orientation.
     *
     * @param maxSizeInOther The maximum size per span ratio from the measurement of the children.
     * @param currentOtherDirSize The size before this layout chunk. There is no reason to go below.
     */
    private void guessMeasurement(float maxSizeInOther, int currentOtherDirSize) {
        final int contentSize = Math.round(maxSizeInOther * mSpanCount);
        // always re-calculate because borders were stretched during the fill
        calculateItemBorders(Math.max(contentSize, currentOtherDirSize));
    }

    protected void measureChildWithDecorationsAndMargin(View child, int widthSpec, int heightSpec,
            boolean alreadyMeasured) {
        RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
        final boolean measure;
        if (alreadyMeasured) {
            measure = shouldReMeasureChild(child, widthSpec, heightSpec, lp);
        } else {
            measure = shouldMeasureChild(child, widthSpec, heightSpec, lp);
        }
        if (measure) {
            child.measure(widthSpec, heightSpec);
        }
    }

    protected void assignSpans(RecyclerView.Recycler recycler, RecyclerView.State state, int count,
            boolean layingOutInPrimaryDirection) {
        // spans are always assigned from 0 to N no matter if it is RTL or not.
        // RTL is used only when positioning the view.
        int span, start, end, diff;
        // make sure we traverse from min position to max position
        if (layingOutInPrimaryDirection) {
            start = 0;
            end = count;
            diff = 1;
        } else {
            start = count - 1;
            end = -1;
            diff = -1;
        }
        span = 0;
        for (int i = start; i != end; i += diff) {
            View view = mSet[i];
            LayoutParams params = (LayoutParams) view.getLayoutParams();
            params.mSpanSize = getSpanSize(recycler, state, getPosition(view));
            params.mSpanIndex = span;
            span += params.mSpanSize;
        }
    }

    /**
     * Returns the number of spans laid out by this grid.
     *
     * @return The number of spans
     * @see #setSpanCount(int)
     */
    public int getSpanCount() {
        return mSpanCount;
    }

    /**
     * Sets the number of spans to be laid out.
     * <p>
     * If {@link #getOrientation()} is {@link #VERTICAL}, this is the number of columns.
     * If {@link #getOrientation()} is {@link #HORIZONTAL}, this is the number of rows.
     *
     * @param spanCount The total number of spans in the grid
     * @see #getSpanCount()
     */
    public void setSpanCount(int spanCount) {
        if (spanCount == mSpanCount) {
            return;
        }
        mPendingSpanCountChange = true;
        if (spanCount < 1) {
            throw new IllegalArgumentException("Span count should be at least 1. Provided "
                    + spanCount);
        }
        mSpanCount = spanCount;
        mSpanSizeLookup.invalidateSpanIndexCache();
        requestLayout();
    }

    /**
     * A helper class to provide the number of spans each item occupies.
     * <p>
     * Default implementation sets each item to occupy exactly 1 span.
     *
     * @see GridLayoutManager#setSpanSizeLookup(SpanSizeLookup)
     */
    public abstract static class SpanSizeLookup {

        final SparseIntArray mSpanIndexCache = new SparseIntArray();
        final SparseIntArray mSpanGroupIndexCache = new SparseIntArray();

        private boolean mCacheSpanIndices = false;
        private boolean mCacheSpanGroupIndices = false;

        /**
         * Returns the number of span occupied by the item at <code>position</code>.
         *
         * @param position The adapter position of the item
         * @return The number of spans occupied by the item at the provided position
         */
        public abstract int getSpanSize(int position);

        /**
         * Sets whether the results of {@link #getSpanIndex(int, int)} method should be cached or
         * not. By default these values are not cached. If you are not overriding
         * {@link #getSpanIndex(int, int)} with something highly performant, you should set this
         * to true for better performance.
         *
         * @param cacheSpanIndices Whether results of getSpanIndex should be cached or not.
         */
        public void setSpanIndexCacheEnabled(boolean cacheSpanIndices) {
            if (!cacheSpanIndices) {
                mSpanGroupIndexCache.clear();
            }
            mCacheSpanIndices = cacheSpanIndices;
        }

        /**
         * Sets whether the results of {@link #getSpanGroupIndex(int, int)} method should be cached
         * or not. By default these values are not cached. If you are not overriding
         * {@link #getSpanGroupIndex(int, int)} with something highly performant, and you are using
         * spans to calculate scrollbar offset and range, you should set this to true for better
         * performance.
         *
         * @param cacheSpanGroupIndices Whether results of getGroupSpanIndex should be cached or
         *                              not.
         */
        public void setSpanGroupIndexCacheEnabled(boolean cacheSpanGroupIndices)  {
            if (!cacheSpanGroupIndices) {
                mSpanGroupIndexCache.clear();
            }
            mCacheSpanGroupIndices = cacheSpanGroupIndices;
        }

        /**
         * Clears the span index cache. GridLayoutManager automatically calls this method when
         * adapter changes occur.
         */
        public void invalidateSpanIndexCache() {
            mSpanIndexCache.clear();
        }

        /**
         * Clears the span group index cache. GridLayoutManager automatically calls this method
         * when adapter changes occur.
         */
        public void invalidateSpanGroupIndexCache() {
            mSpanGroupIndexCache.clear();
        }

        /**
         * Returns whether results of {@link #getSpanIndex(int, int)} method are cached or not.
         *
         * @return True if results of {@link #getSpanIndex(int, int)} are cached.
         */
        public boolean isSpanIndexCacheEnabled() {
            return mCacheSpanIndices;
        }

        /**
         * Returns whether results of {@link #getSpanGroupIndex(int, int)} method are cached or not.
         *
         * @return True if results of {@link #getSpanGroupIndex(int, int)} are cached.
         */
        public boolean isSpanGroupIndexCacheEnabled() {
            return mCacheSpanGroupIndices;
        }

        int getCachedSpanIndex(int position, int spanCount) {
            if (!mCacheSpanIndices) {
                return getSpanIndex(position, spanCount);
            }
            final int existing = mSpanIndexCache.get(position, -1);
            if (existing != -1) {
                return existing;
            }
            final int value = getSpanIndex(position, spanCount);
            mSpanIndexCache.put(position, value);
            return value;
        }

        int getCachedSpanGroupIndex(int position, int spanCount) {
            if (!mCacheSpanGroupIndices) {
                return getSpanGroupIndex(position, spanCount);
            }
            final int existing = mSpanGroupIndexCache.get(position, -1);
            if (existing != -1) {
                return existing;
            }
            final int value = getSpanGroupIndex(position, spanCount);
            mSpanGroupIndexCache.put(position, value);
            return value;
        }

        /**
         * Returns the final span index of the provided position.
         * <p>
         * If you have a faster way to calculate span index for your items, you should override
         * this method. Otherwise, you should enable span index cache
         * ({@link #setSpanIndexCacheEnabled(boolean)}) for better performance. When caching is
         * disabled, default implementation traverses all items from 0 to
         * <code>position</code>. When caching is enabled, it calculates from the closest cached
         * value before the <code>position</code>.
         * <p>
         * If you override this method, you need to make sure it is consistent with
         * {@link #getSpanSize(int)}. GridLayoutManager does not call this method for
         * each item. It is called only for the reference item and rest of the items
         * are assigned to spans based on the reference item. For example, you cannot assign a
         * position to span 2 while span 1 is empty.
         * <p>
         * Note that span offsets always start with 0 and are not affected by RTL.
         *
         * @param position  The position of the item
         * @param spanCount The total number of spans in the grid
         * @return The final span position of the item. Should be between 0 (inclusive) and
         * <code>spanCount</code>(exclusive)
         */
        public int getSpanIndex(int position, int spanCount) {
            int positionSpanSize = getSpanSize(position);
            if (positionSpanSize == spanCount) {
                return 0; // quick return for full-span items
            }
            int span = 0;
            int startPos = 0;
            // If caching is enabled, try to jump
            if (mCacheSpanIndices) {
                int prevKey = findFirstKeyLessThan(mSpanIndexCache, position);
                if (prevKey >= 0) {
                    span = mSpanIndexCache.get(prevKey) + getSpanSize(prevKey);
                    startPos = prevKey + 1;
                }
            }
            for (int i = startPos; i < position; i++) {
                int size = getSpanSize(i);
                span += size;
                if (span == spanCount) {
                    span = 0;
                } else if (span > spanCount) {
                    // did not fit, moving to next row / column
                    span = size;
                }
            }
            if (span + positionSpanSize <= spanCount) {
                return span;
            }
            return 0;
        }

        static int findFirstKeyLessThan(SparseIntArray cache, int position) {
            int lo = 0;
            int hi = cache.size() - 1;

            while (lo <= hi) {
                // Using unsigned shift here to divide by two because it is guaranteed to not
                // overflow.
                final int mid = (lo + hi) >>> 1;
                final int midVal = cache.keyAt(mid);
                if (midVal < position) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            int index = lo - 1;
            if (index >= 0 && index < cache.size()) {
                return cache.keyAt(index);
            }
            return -1;
        }

        /**
         * Returns the index of the group this position belongs.
         * <p>
         * For example, if grid has 3 columns and each item occupies 1 span, span group index
         * for item 1 will be 0, item 5 will be 1.
         *
         * @param adapterPosition The position in adapter
         * @param spanCount The total number of spans in the grid
         * @return The index of the span group including the item at the given adapter position
         */
        public int getSpanGroupIndex(int adapterPosition, int spanCount) {
            int span = 0;
            int group = 0;
            int start = 0;
            if (mCacheSpanGroupIndices) {
                // This finds the first non empty cached group cache key.
                int prevKey = findFirstKeyLessThan(mSpanGroupIndexCache, adapterPosition);
                if (prevKey != -1) {
                    group = mSpanGroupIndexCache.get(prevKey);
                    start = prevKey + 1;
                    span = getCachedSpanIndex(prevKey, spanCount) + getSpanSize(prevKey);
                    if (span == spanCount) {
                        span = 0;
                        group++;
                    }
                }
            }
            int positionSpanSize = getSpanSize(adapterPosition);
            for (int i = start; i < adapterPosition; i++) {
                int size = getSpanSize(i);
                span += size;
                if (span == spanCount) {
                    span = 0;
                    group++;
                } else if (span > spanCount) {
                    // did not fit, moving to next row / column
                    span = size;
                    group++;
                }
            }
            if (span + positionSpanSize > spanCount) {
                group++;
            }
            return group;
        }
    }

    @Override
    public View onFocusSearchFailed(View focused, int focusDirection,
            RecyclerView.Recycler recycler, RecyclerView.State state) {
        View prevFocusedChild = findContainingItemView(focused);
        if (prevFocusedChild == null) {
            return null;
        }
        LayoutParams lp = (LayoutParams) prevFocusedChild.getLayoutParams();
        final int prevSpanStart = lp.mSpanIndex;
        final int prevSpanEnd = lp.mSpanIndex + lp.mSpanSize;
        View view = super.onFocusSearchFailed(focused, focusDirection, recycler, state);
        if (view == null) {
            return null;
        }
        // LinearLayoutManager finds the last child. What we want is the child which has the same
        // spanIndex.
        final int layoutDir = convertFocusDirectionToLayoutDirection(focusDirection);
        final boolean ascend = (layoutDir == LayoutState.LAYOUT_END) != mShouldReverseLayout;
        final int start, inc, limit;
        if (ascend) {
            start = getChildCount() - 1;
            inc = -1;
            limit = -1;
        } else {
            start = 0;
            inc = 1;
            limit = getChildCount();
        }
        final boolean preferLastSpan = mOrientation == VERTICAL && isLayoutRTL();

        // The focusable candidate to be picked if no perfect focusable candidate is found.
        // The best focusable candidate is the one with the highest amount of span overlap with
        // the currently focused view.
        View focusableWeakCandidate = null; // somewhat matches but not strong
        int focusableWeakCandidateSpanIndex = -1;
        int focusableWeakCandidateOverlap = 0; // how many spans overlap

        // The unfocusable candidate to become visible on the screen next, if no perfect or
        // weak focusable candidates are found to receive focus next.
        // We are only interested in partially visible unfocusable views. These are views that are
        // not fully visible, that is either partially overlapping, or out-of-bounds and right below
        // or above RV's padded bounded area. The best unfocusable candidate is the one with the
        // highest amount of span overlap with the currently focused view.
        View unfocusableWeakCandidate = null; // somewhat matches but not strong
        int unfocusableWeakCandidateSpanIndex = -1;
        int unfocusableWeakCandidateOverlap = 0; // how many spans overlap

        // The span group index of the start child. This indicates the span group index of the
        // next focusable item to receive focus, if a focusable item within the same span group
        // exists. Any focusable item beyond this group index are not relevant since they
        // were already stored in the layout before onFocusSearchFailed call and were not picked
        // by the focusSearch algorithm.
        int focusableSpanGroupIndex = getSpanGroupIndex(recycler, state, start);
        for (int i = start; i != limit; i += inc) {
            int spanGroupIndex = getSpanGroupIndex(recycler, state, i);
            View candidate = getChildAt(i);
            if (candidate == prevFocusedChild) {
                break;
            }

            if (candidate.hasFocusable() && spanGroupIndex != focusableSpanGroupIndex) {
                // We are past the allowable span group index for the next focusable item.
                // The search only continues if no focusable weak candidates have been found up
                // until this point, in order to find the best unfocusable candidate to become
                // visible on the screen next.
                if (focusableWeakCandidate != null) {
                    break;
                }
                continue;
            }

            final LayoutParams candidateLp = (LayoutParams) candidate.getLayoutParams();
            final int candidateStart = candidateLp.mSpanIndex;
            final int candidateEnd = candidateLp.mSpanIndex + candidateLp.mSpanSize;
            if (candidate.hasFocusable() && candidateStart == prevSpanStart
                    && candidateEnd == prevSpanEnd) {
                return candidate; // perfect match
            }
            boolean assignAsWeek = false;
            if ((candidate.hasFocusable() && focusableWeakCandidate == null)
                    || (!candidate.hasFocusable() && unfocusableWeakCandidate == null)) {
                assignAsWeek = true;
            } else {
                int maxStart = Math.max(candidateStart, prevSpanStart);
                int minEnd = Math.min(candidateEnd, prevSpanEnd);
                int overlap = minEnd - maxStart;
                if (candidate.hasFocusable()) {
                    if (overlap > focusableWeakCandidateOverlap) {
                        assignAsWeek = true;
                    } else if (overlap == focusableWeakCandidateOverlap
                            && preferLastSpan == (candidateStart
                            > focusableWeakCandidateSpanIndex)) {
                        assignAsWeek = true;
                    }
                } else if (focusableWeakCandidate == null
                        && isViewPartiallyVisible(candidate, false, true)) {
                    if (overlap > unfocusableWeakCandidateOverlap) {
                        assignAsWeek = true;
                    } else if (overlap == unfocusableWeakCandidateOverlap
                            && preferLastSpan == (candidateStart
                                    > unfocusableWeakCandidateSpanIndex)) {
                        assignAsWeek = true;
                    }
                }
            }

            if (assignAsWeek) {
                if (candidate.hasFocusable()) {
                    focusableWeakCandidate = candidate;
                    focusableWeakCandidateSpanIndex = candidateLp.mSpanIndex;
                    focusableWeakCandidateOverlap = Math.min(candidateEnd, prevSpanEnd)
                            - Math.max(candidateStart, prevSpanStart);
                } else {
                    unfocusableWeakCandidate = candidate;
                    unfocusableWeakCandidateSpanIndex = candidateLp.mSpanIndex;
                    unfocusableWeakCandidateOverlap = Math.min(candidateEnd, prevSpanEnd)
                            - Math.max(candidateStart, prevSpanStart);
                }
            }
        }
        return (focusableWeakCandidate != null) ? focusableWeakCandidate : unfocusableWeakCandidate;
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return mPendingSavedState == null && !mPendingSpanCountChange;
    }

    @Override
    public int computeHorizontalScrollRange(RecyclerView.State state) {
        if (mUsingSpansToEstimateScrollBarDimensions) {
            return computeScrollRangeWithSpanInfo(state);
        } else {
            return super.computeHorizontalScrollRange(state);
        }
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        if (mUsingSpansToEstimateScrollBarDimensions) {
            return computeScrollRangeWithSpanInfo(state);
        } else {
            return super.computeVerticalScrollRange(state);
        }
    }

    @Override
    public int computeHorizontalScrollOffset(RecyclerView.State state) {
        if (mUsingSpansToEstimateScrollBarDimensions) {
            return computeScrollOffsetWithSpanInfo(state);
        } else {
            return super.computeHorizontalScrollOffset(state);
        }
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        if (mUsingSpansToEstimateScrollBarDimensions) {
            return computeScrollOffsetWithSpanInfo(state);
        } else {
            return super.computeVerticalScrollOffset(state);
        }
    }

    /**
     * When this flag is set, the scroll offset and scroll range calculations will take account
     * of span information.
     *
     * <p>This is will increase the accuracy of the scroll bar's size and offset but will require
     * more calls to {@link SpanSizeLookup#getSpanGroupIndex(int, int)}".
     *
     * <p>This additional accuracy may or may not be needed, depending on the characteristics of
     * your layout.  You will likely benefit from this accuracy when:
     *
     * <ul>
     *   <li>The variation in item span sizes is large.
     *   <li>The size of your data set is small (if your data set is large, the scrollbar will
     *   likely be very small anyway, and thus the increased accuracy has less impact).
     *   <li>Calls to {@link SpanSizeLookup#getSpanGroupIndex(int, int)} are fast.
     * </ul>
     *
     * <p>If you decide to enable this feature, you should be sure that calls to
     * {@link SpanSizeLookup#getSpanGroupIndex(int, int)} are fast, that set span group index
     * caching is set to true via a call to
     * {@link SpanSizeLookup#setSpanGroupIndexCacheEnabled(boolean),
     * and span index caching is also enabled via a call to
     * {@link SpanSizeLookup#setSpanIndexCacheEnabled(boolean)}}.
     */
    public void setUsingSpansToEstimateScrollbarDimensions(
            boolean useSpansToEstimateScrollBarDimensions) {
        mUsingSpansToEstimateScrollBarDimensions = useSpansToEstimateScrollBarDimensions;
    }

    /**
     * Returns true if the scroll offset and scroll range calculations take account of span
     * information. See {@link #setUsingSpansToEstimateScrollbarDimensions(boolean)} for more
     * information on this topic. Defaults to {@code false}.
     *
     * @return true if the scroll offset and scroll range calculations take account of span
     * information.
     */
    public boolean isUsingSpansToEstimateScrollbarDimensions() {
        return mUsingSpansToEstimateScrollBarDimensions;
    }

    private int computeScrollRangeWithSpanInfo(RecyclerView.State state) {
        if (getChildCount() == 0 || state.getItemCount() == 0) {
            return 0;
        }
        ensureLayoutState();

        View startChild = findFirstVisibleChildClosestToStart(!isSmoothScrollbarEnabled(), true);
        View endChild = findFirstVisibleChildClosestToEnd(!isSmoothScrollbarEnabled(), true);

        if (startChild == null || endChild == null) {
            return 0;
        }
        if (!isSmoothScrollbarEnabled()) {
            return mSpanSizeLookup.getCachedSpanGroupIndex(
                    state.getItemCount() - 1, mSpanCount) + 1;
        }

        // smooth scrollbar enabled. try to estimate better.
        final int laidOutArea = mOrientationHelper.getDecoratedEnd(endChild)
                - mOrientationHelper.getDecoratedStart(startChild);

        final int firstVisibleSpan =
                mSpanSizeLookup.getCachedSpanGroupIndex(getPosition(startChild), mSpanCount);
        final int lastVisibleSpan = mSpanSizeLookup.getCachedSpanGroupIndex(getPosition(endChild),
                mSpanCount);
        final int totalSpans = mSpanSizeLookup.getCachedSpanGroupIndex(state.getItemCount() - 1,
                mSpanCount) + 1;
        final int laidOutSpans = lastVisibleSpan - firstVisibleSpan + 1;

        // estimate a size for full list.
        return (int) (((float) laidOutArea / laidOutSpans) * totalSpans);
    }

    private int computeScrollOffsetWithSpanInfo(RecyclerView.State state) {
        if (getChildCount() == 0 || state.getItemCount() == 0) {
            return 0;
        }
        ensureLayoutState();

        boolean smoothScrollEnabled = isSmoothScrollbarEnabled();
        View startChild = findFirstVisibleChildClosestToStart(!smoothScrollEnabled, true);
        View endChild = findFirstVisibleChildClosestToEnd(!smoothScrollEnabled, true);
        if (startChild == null || endChild == null) {
            return 0;
        }
        int startChildSpan = mSpanSizeLookup.getCachedSpanGroupIndex(getPosition(startChild),
                mSpanCount);
        int endChildSpan = mSpanSizeLookup.getCachedSpanGroupIndex(getPosition(endChild),
                mSpanCount);

        final int minSpan = Math.min(startChildSpan, endChildSpan);
        final int maxSpan = Math.max(startChildSpan, endChildSpan);
        final int totalSpans = mSpanSizeLookup.getCachedSpanGroupIndex(state.getItemCount() - 1,
                mSpanCount) + 1;

        final int spansBefore = mShouldReverseLayout
                ? Math.max(0, totalSpans - maxSpan - 1)
                : Math.max(0, minSpan);
        if (!smoothScrollEnabled) {
            return spansBefore;
        }
        final int laidOutArea = Math.abs(mOrientationHelper.getDecoratedEnd(endChild)
                - mOrientationHelper.getDecoratedStart(startChild));

        final int firstVisibleSpan =
                mSpanSizeLookup.getCachedSpanGroupIndex(getPosition(startChild), mSpanCount);
        final int lastVisibleSpan = mSpanSizeLookup.getCachedSpanGroupIndex(getPosition(endChild),
                mSpanCount);
        final int laidOutSpans = lastVisibleSpan - firstVisibleSpan + 1;
        final float avgSizePerSpan = (float) laidOutArea / laidOutSpans;

        return Math.round(spansBefore * avgSizePerSpan + (mOrientationHelper.getStartAfterPadding()
            - mOrientationHelper.getDecoratedStart(startChild)));
    }

    /**
     * Default implementation for {@link SpanSizeLookup}. Each item occupies 1 span.
     */
    public static final class DefaultSpanSizeLookup extends SpanSizeLookup {

        @Override
        public int getSpanSize(int position) {
            return 1;
        }

        @Override
        public int getSpanIndex(int position, int spanCount) {
            return position % spanCount;
        }
    }

    /**
     * LayoutParams used by GridLayoutManager.
     * <p>
     * Note that if the orientation is {@link #VERTICAL}, the width parameter is ignored and if the
     * orientation is {@link #HORIZONTAL} the height parameter is ignored because child view is
     * expected to fill all of the space given to it.
     */
    public static class LayoutParams extends RecyclerView.LayoutParams {

        /**
         * Span Id for Views that are not laid out yet.
         */
        public static final int INVALID_SPAN_ID = -1;

        int mSpanIndex = INVALID_SPAN_ID;

        public int mSpanSize = 0;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(RecyclerView.LayoutParams source) {
            super(source);
        }

        /**
         * Returns the current span index of this View. If the View is not laid out yet, the return
         * value is <code>undefined</code>.
         * <p>
         * Starting with RecyclerView <b>24.2.0</b>, span indices are always indexed from position 0
         * even if the layout is RTL. In a vertical GridLayoutManager, <b>leftmost</b> span is span
         * 0 if the layout is <b>LTR</b> and <b>rightmost</b> span is span 0 if the layout is
         * <b>RTL</b>. Prior to 24.2.0, it was the opposite which was conflicting with
         * {@link SpanSizeLookup#getSpanIndex(int, int)}.
         * <p>
         * If the View occupies multiple spans, span with the minimum index is returned.
         *
         * @return The span index of the View.
         */
        public int getSpanIndex() {
            return mSpanIndex;
        }

        /**
         * Returns the number of spans occupied by this View. If the View not laid out yet, the
         * return value is <code>undefined</code>.
         *
         * @return The number of spans occupied by this View.
         */
        public int getSpanSize() {
            return mSpanSize;
        }
    }

}
