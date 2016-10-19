/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * See the License for the specific languag`e governing permissions and
 * limitations under the License.
 */

package org.telegram.messenger.support.widget;

import static org.telegram.messenger.support.widget.RecyclerView.NO_POSITION;

import android.content.Context;
import android.graphics.PointF;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import org.telegram.messenger.support.widget.RecyclerView.LayoutParams;
import org.telegram.messenger.support.widget.helper.ItemTouchHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

/**
 * A {@link android.support.v7.widget.RecyclerView.LayoutManager} implementation which provides
 * similar functionality to {@link android.widget.ListView}.
 */
public class LinearLayoutManager extends RecyclerView.LayoutManager implements
        ItemTouchHelper.ViewDropHandler {

    private static final String TAG = "LinearLayoutManager";

    private static final boolean DEBUG = false;

    public static final int HORIZONTAL = OrientationHelper.HORIZONTAL;

    public static final int VERTICAL = OrientationHelper.VERTICAL;

    public static final int INVALID_OFFSET = Integer.MIN_VALUE;


    /**
     * While trying to find next view to focus, LayoutManager will not try to scroll more
     * than this factor times the total space of the list. If layout is vertical, total space is the
     * height minus padding, if layout is horizontal, total space is the width minus padding.
     */
    private static final float MAX_SCROLL_FACTOR = 1 / 3f;


    /**
     * Current orientation. Either {@link #HORIZONTAL} or {@link #VERTICAL}
     */
    int mOrientation;

    /**
     * Helper class that keeps temporary layout state.
     * It does not keep state after layout is complete but we still keep a reference to re-use
     * the same object.
     */
    private LayoutState mLayoutState;

    /**
     * Many calculations are made depending on orientation. To keep it clean, this interface
     * helps {@link LinearLayoutManager} make those decisions.
     * Based on {@link #mOrientation}, an implementation is lazily created in
     * {@link #ensureLayoutState} method.
     */
    OrientationHelper mOrientationHelper;

    /**
     * We need to track this so that we can ignore current position when it changes.
     */
    private boolean mLastStackFromEnd;


    /**
     * Defines if layout should be calculated from end to start.
     *
     * @see #mShouldReverseLayout
     */
    private boolean mReverseLayout = false;

    /**
     * This keeps the final value for how LayoutManager should start laying out views.
     * It is calculated by checking {@link #getReverseLayout()} and View's layout direction.
     * {@link #onLayoutChildren(RecyclerView.Recycler, RecyclerView.State)} is run.
     */
    boolean mShouldReverseLayout = false;

    /**
     * Works the same way as {@link android.widget.AbsListView#setStackFromBottom(boolean)} and
     * it supports both orientations.
     * see {@link android.widget.AbsListView#setStackFromBottom(boolean)}
     */
    private boolean mStackFromEnd = false;

    /**
     * Works the same way as {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}.
     * see {@link android.widget.AbsListView#setSmoothScrollbarEnabled(boolean)}
     */
    private boolean mSmoothScrollbarEnabled = true;

    /**
     * When LayoutManager needs to scroll to a position, it sets this variable and requests a
     * layout which will check this variable and re-layout accordingly.
     */
    int mPendingScrollPosition = NO_POSITION;

    /**
     * Used to keep the offset value when {@link #scrollToPositionWithOffset(int, int)} is
     * called.
     */
    int mPendingScrollPositionOffset = INVALID_OFFSET;

    private boolean mRecycleChildrenOnDetach;

    SavedState mPendingSavedState = null;

    /**
    *  Re-used variable to keep anchor information on re-layout.
    *  Anchor position and coordinate defines the reference point for LLM while doing a layout.
    * */
    final AnchorInfo mAnchorInfo = new AnchorInfo();

    /**
     * Creates a vertical LinearLayoutManager
     *
     * @param context Current context, will be used to access resources.
     */
    public LinearLayoutManager(Context context) {
        this(context, VERTICAL, false);
    }

    /**
     * @param context       Current context, will be used to access resources.
     * @param orientation   Layout orientation. Should be {@link #HORIZONTAL} or {@link
     *                      #VERTICAL}.
     * @param reverseLayout When set to true, layouts from end to start.
     */
    public LinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
        setOrientation(orientation);
        setReverseLayout(reverseLayout);
        setAutoMeasureEnabled(true);
    }

    /**
     * Constructor used when layout manager is set in XML by RecyclerView attribute
     * "layoutManager". Defaults to vertical orientation.
     *
     * @attr ref android.support.v7.recyclerview.R.styleable#RecyclerView_android_orientation
     * @attr ref android.support.v7.recyclerview.R.styleable#RecyclerView_reverseLayout
     * @attr ref android.support.v7.recyclerview.R.styleable#RecyclerView_stackFromEnd
     */
    public LinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr,
                               int defStyleRes) {
        Properties properties = getProperties(context, attrs, defStyleAttr, defStyleRes);
        setOrientation(properties.orientation);
        setReverseLayout(properties.reverseLayout);
        setStackFromEnd(properties.stackFromEnd);
        setAutoMeasureEnabled(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Returns whether LayoutManager will recycle its children when it is detached from
     * RecyclerView.
     *
     * @return true if LayoutManager will recycle its children when it is detached from
     * RecyclerView.
     */
    public boolean getRecycleChildrenOnDetach() {
        return mRecycleChildrenOnDetach;
    }

    /**
     * Set whether LayoutManager will recycle its children when it is detached from
     * RecyclerView.
     * <p>
     * If you are using a {@link RecyclerView.RecycledViewPool}, it might be a good idea to set
     * this flag to <code>true</code> so that views will be avilable to other RecyclerViews
     * immediately.
     * <p>
     * Note that, setting this flag will result in a performance drop if RecyclerView
     * is restored.
     *
     * @param recycleChildrenOnDetach Whether children should be recycled in detach or not.
     */
    public void setRecycleChildrenOnDetach(boolean recycleChildrenOnDetach) {
        mRecycleChildrenOnDetach = recycleChildrenOnDetach;
    }

    @Override
    public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);
        if (mRecycleChildrenOnDetach) {
            removeAndRecycleAllViews(recycler);
            recycler.clear();
        }
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        if (getChildCount() > 0) {
            final AccessibilityRecordCompat record = AccessibilityEventCompat
                    .asRecord(event);
            record.setFromIndex(findFirstVisibleItemPosition());
            record.setToIndex(findLastVisibleItemPosition());
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        if (mPendingSavedState != null) {
            return new SavedState(mPendingSavedState);
        }
        SavedState state = new SavedState();
        if (getChildCount() > 0) {
            ensureLayoutState();
            boolean didLayoutFromEnd = mLastStackFromEnd ^ mShouldReverseLayout;
            state.mAnchorLayoutFromEnd = didLayoutFromEnd;
            if (didLayoutFromEnd) {
                final View refChild = getChildClosestToEnd();
                state.mAnchorOffset = mOrientationHelper.getEndAfterPadding() -
                        mOrientationHelper.getDecoratedEnd(refChild);
                state.mAnchorPosition = getPosition(refChild);
            } else {
                final View refChild = getChildClosestToStart();
                state.mAnchorPosition = getPosition(refChild);
                state.mAnchorOffset = mOrientationHelper.getDecoratedStart(refChild) -
                        mOrientationHelper.getStartAfterPadding();
            }
        } else {
            state.invalidateAnchor();
        }
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            mPendingSavedState = (SavedState) state;
            requestLayout();
            if (DEBUG) {
                Log.d(TAG, "loaded saved state");
            }
        } else if (DEBUG) {
            Log.d(TAG, "invalid saved state class");
        }
    }

    /**
     * @return true if {@link #getOrientation()} is {@link #HORIZONTAL}
     */
    @Override
    public boolean canScrollHorizontally() {
        return mOrientation == HORIZONTAL;
    }

    /**
     * @return true if {@link #getOrientation()} is {@link #VERTICAL}
     */
    @Override
    public boolean canScrollVertically() {
        return mOrientation == VERTICAL;
    }

    /**
     * Compatibility support for {@link android.widget.AbsListView#setStackFromBottom(boolean)}
     */
    public void setStackFromEnd(boolean stackFromEnd) {
        assertNotInLayoutOrScroll(null);
        if (mStackFromEnd == stackFromEnd) {
            return;
        }
        mStackFromEnd = stackFromEnd;
        requestLayout();
    }

    public boolean getStackFromEnd() {
        return mStackFromEnd;
    }

    /**
     * Returns the current orientaion of the layout.
     *
     * @return Current orientation,  either {@link #HORIZONTAL} or {@link #VERTICAL}
     * @see #setOrientation(int)
     */
    public int getOrientation() {
        return mOrientation;
    }

    /**
     * Sets the orientation of the layout. {@link android.support.v7.widget.LinearLayoutManager}
     * will do its best to keep scroll position.
     *
     * @param orientation {@link #HORIZONTAL} or {@link #VERTICAL}
     */
    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException("invalid orientation:" + orientation);
        }
        assertNotInLayoutOrScroll(null);
        if (orientation == mOrientation) {
            return;
        }
        mOrientation = orientation;
        mOrientationHelper = null;
        requestLayout();
    }

    /**
     * Calculates the view layout order. (e.g. from end to start or start to end)
     * RTL layout support is applied automatically. So if layout is RTL and
     * {@link #getReverseLayout()} is {@code true}, elements will be laid out starting from left.
     */
    private void resolveShouldLayoutReverse() {
        // A == B is the same result, but we rather keep it readable
        if (mOrientation == VERTICAL || !isLayoutRTL()) {
            mShouldReverseLayout = mReverseLayout;
        } else {
            mShouldReverseLayout = !mReverseLayout;
        }
    }

    /**
     * Returns if views are laid out from the opposite direction of the layout.
     *
     * @return If layout is reversed or not.
     * @see #setReverseLayout(boolean)
     */
    public boolean getReverseLayout() {
        return mReverseLayout;
    }

    /**
     * Used to reverse item traversal and layout order.
     * This behaves similar to the layout change for RTL views. When set to true, first item is
     * laid out at the end of the UI, second item is laid out before it etc.
     *
     * For horizontal layouts, it depends on the layout direction.
     * When set to true, If {@link android.support.v7.widget.RecyclerView} is LTR, than it will
     * layout from RTL, if {@link android.support.v7.widget.RecyclerView}} is RTL, it will layout
     * from LTR.
     *
     * If you are looking for the exact same behavior of
     * {@link android.widget.AbsListView#setStackFromBottom(boolean)}, use
     * {@link #setStackFromEnd(boolean)}
     */
    public void setReverseLayout(boolean reverseLayout) {
        assertNotInLayoutOrScroll(null);
        if (reverseLayout == mReverseLayout) {
            return;
        }
        mReverseLayout = reverseLayout;
        requestLayout();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View findViewByPosition(int position) {
        final int childCount = getChildCount();
        if (childCount == 0) {
            return null;
        }
        final int firstChild = getPosition(getChildAt(0));
        final int viewPosition = position - firstChild;
        if (viewPosition >= 0 && viewPosition < childCount) {
            final View child = getChildAt(viewPosition);
            if (getPosition(child) == position) {
                return child; // in pre-layout, this may not match
            }
        }
        // fallback to traversal. This might be necessary in pre-layout.
        return super.findViewByPosition(position);
    }

    /**
     * <p>Returns the amount of extra space that should be laid out by LayoutManager.
     * By default, {@link android.support.v7.widget.LinearLayoutManager} lays out 1 extra page of
     * items while smooth scrolling and 0 otherwise. You can override this method to implement your
     * custom layout pre-cache logic.</p>
     * <p>Laying out invisible elements will eventually come with performance cost. On the other
     * hand, in places like smooth scrolling to an unknown location, this extra content helps
     * LayoutManager to calculate a much smoother scrolling; which improves user experience.</p>
     * <p>You can also use this if you are trying to pre-layout your upcoming views.</p>
     *
     * @return The extra space that should be laid out (in pixels).
     */
    protected int getExtraLayoutSpace(RecyclerView.State state) {
        if (state.hasTargetScrollPosition()) {
            return mOrientationHelper.getTotalSpace();
        } else {
            return 0;
        }
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state,
            int position) {
        LinearSmoothScroller linearSmoothScroller =
                new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public PointF computeScrollVectorForPosition(int targetPosition) {
                        return LinearLayoutManager.this
                                .computeScrollVectorForPosition(targetPosition);
                    }
                };
        linearSmoothScroller.setTargetPosition(position);
        startSmoothScroll(linearSmoothScroller);
    }

    public PointF computeScrollVectorForPosition(int targetPosition) {
        if (getChildCount() == 0) {
            return null;
        }
        final int firstChildPos = getPosition(getChildAt(0));
        final int direction = targetPosition < firstChildPos != mShouldReverseLayout ? -1 : 1;
        if (mOrientation == HORIZONTAL) {
            return new PointF(direction, 0);
        } else {
            return new PointF(0, direction);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        // layout algorithm:
        // 1) by checking children and other variables, find an anchor coordinate and an anchor
        //  item position.
        // 2) fill towards start, stacking from bottom
        // 3) fill towards end, stacking from top
        // 4) scroll to fulfill requirements like stack from bottom.
        // create layout state
        if (DEBUG) {
            Log.d(TAG, "is pre layout:" + state.isPreLayout());
        }
        if (mPendingSavedState != null || mPendingScrollPosition != NO_POSITION) {
            if (state.getItemCount() == 0) {
                removeAndRecycleAllViews(recycler);
                return;
            }
        }
        if (mPendingSavedState != null && mPendingSavedState.hasValidAnchor()) {
            mPendingScrollPosition = mPendingSavedState.mAnchorPosition;
        }

        ensureLayoutState();
        mLayoutState.mRecycle = false;
        // resolve layout direction
        resolveShouldLayoutReverse();

        mAnchorInfo.reset();
        mAnchorInfo.mLayoutFromEnd = mShouldReverseLayout ^ mStackFromEnd;
        // calculate anchor position and coordinate
        updateAnchorInfoForLayout(recycler, state, mAnchorInfo);
        if (DEBUG) {
            Log.d(TAG, "Anchor info:" + mAnchorInfo);
        }

        // LLM may decide to layout items for "extra" pixels to account for scrolling target,
        // caching or predictive animations.
        int extraForStart;
        int extraForEnd;
        final int extra = getExtraLayoutSpace(state);
        // If the previous scroll delta was less than zero, the extra space should be laid out
        // at the start. Otherwise, it should be at the end.
        if (mLayoutState.mLastScrollDelta >= 0) {
            extraForEnd = extra;
            extraForStart = 0;
        } else {
            extraForStart = extra;
            extraForEnd = 0;
        }
        extraForStart += mOrientationHelper.getStartAfterPadding();
        extraForEnd += mOrientationHelper.getEndPadding();
        if (state.isPreLayout() && mPendingScrollPosition != NO_POSITION &&
                mPendingScrollPositionOffset != INVALID_OFFSET) {
            // if the child is visible and we are going to move it around, we should layout
            // extra items in the opposite direction to make sure new items animate nicely
            // instead of just fading in
            final View existing = findViewByPosition(mPendingScrollPosition);
            if (existing != null) {
                final int current;
                final int upcomingOffset;
                if (mShouldReverseLayout) {
                    current = mOrientationHelper.getEndAfterPadding() -
                            mOrientationHelper.getDecoratedEnd(existing);
                    upcomingOffset = current - mPendingScrollPositionOffset;
                } else {
                    current = mOrientationHelper.getDecoratedStart(existing)
                            - mOrientationHelper.getStartAfterPadding();
                    upcomingOffset = mPendingScrollPositionOffset - current;
                }
                if (upcomingOffset > 0) {
                    extraForStart += upcomingOffset;
                } else {
                    extraForEnd -= upcomingOffset;
                }
            }
        }
        int startOffset;
        int endOffset;
        final int firstLayoutDirection;
        if (mAnchorInfo.mLayoutFromEnd) {
            firstLayoutDirection = mShouldReverseLayout ? LayoutState.ITEM_DIRECTION_TAIL :
                    LayoutState.ITEM_DIRECTION_HEAD;
        } else {
            firstLayoutDirection = mShouldReverseLayout ? LayoutState.ITEM_DIRECTION_HEAD :
                    LayoutState.ITEM_DIRECTION_TAIL;
        }

        onAnchorReady(recycler, state, mAnchorInfo, firstLayoutDirection);
        detachAndScrapAttachedViews(recycler);
        mLayoutState.mInfinite = resolveIsInfinite();
        mLayoutState.mIsPreLayout = state.isPreLayout();
        if (mAnchorInfo.mLayoutFromEnd) {
            // fill towards start
            updateLayoutStateToFillStart(mAnchorInfo);
            mLayoutState.mExtra = extraForStart;
            fill(recycler, mLayoutState, state, false);
            startOffset = mLayoutState.mOffset;
            final int firstElement = mLayoutState.mCurrentPosition;
            if (mLayoutState.mAvailable > 0) {
                extraForEnd += mLayoutState.mAvailable;
            }
            // fill towards end
            updateLayoutStateToFillEnd(mAnchorInfo);
            mLayoutState.mExtra = extraForEnd;
            mLayoutState.mCurrentPosition += mLayoutState.mItemDirection;
            fill(recycler, mLayoutState, state, false);
            endOffset = mLayoutState.mOffset;

            if (mLayoutState.mAvailable > 0) {
                // end could not consume all. add more items towards start
                extraForStart = mLayoutState.mAvailable;
                updateLayoutStateToFillStart(firstElement, startOffset);
                mLayoutState.mExtra = extraForStart;
                fill(recycler, mLayoutState, state, false);
                startOffset = mLayoutState.mOffset;
            }
        } else {
            // fill towards end
            updateLayoutStateToFillEnd(mAnchorInfo);
            mLayoutState.mExtra = extraForEnd;
            fill(recycler, mLayoutState, state, false);
            endOffset = mLayoutState.mOffset;
            final int lastElement = mLayoutState.mCurrentPosition;
            if (mLayoutState.mAvailable > 0) {
                extraForStart += mLayoutState.mAvailable;
            }
            // fill towards start
            updateLayoutStateToFillStart(mAnchorInfo);
            mLayoutState.mExtra = extraForStart;
            mLayoutState.mCurrentPosition += mLayoutState.mItemDirection;
            fill(recycler, mLayoutState, state, false);
            startOffset = mLayoutState.mOffset;

            if (mLayoutState.mAvailable > 0) {
                extraForEnd = mLayoutState.mAvailable;
                // start could not consume all it should. add more items towards end
                updateLayoutStateToFillEnd(lastElement, endOffset);
                mLayoutState.mExtra = extraForEnd;
                fill(recycler, mLayoutState, state, false);
                endOffset = mLayoutState.mOffset;
            }
        }

        // changes may cause gaps on the UI, try to fix them.
        // TODO we can probably avoid this if neither stackFromEnd/reverseLayout/RTL values have
        // changed
        if (getChildCount() > 0) {
            // because layout from end may be changed by scroll to position
            // we re-calculate it.
            // find which side we should check for gaps.
            if (mShouldReverseLayout ^ mStackFromEnd) {
                int fixOffset = fixLayoutEndGap(endOffset, recycler, state, true);
                startOffset += fixOffset;
                endOffset += fixOffset;
                fixOffset = fixLayoutStartGap(startOffset, recycler, state, false);
                startOffset += fixOffset;
                endOffset += fixOffset;
            } else {
                int fixOffset = fixLayoutStartGap(startOffset, recycler, state, true);
                startOffset += fixOffset;
                endOffset += fixOffset;
                fixOffset = fixLayoutEndGap(endOffset, recycler, state, false);
                startOffset += fixOffset;
                endOffset += fixOffset;
            }
        }
        layoutForPredictiveAnimations(recycler, state, startOffset, endOffset);
        if (!state.isPreLayout()) {
            mPendingScrollPosition = NO_POSITION;
            mPendingScrollPositionOffset = INVALID_OFFSET;
            mOrientationHelper.onLayoutComplete();
        }
        mLastStackFromEnd = mStackFromEnd;
        mPendingSavedState = null; // we don't need this anymore
        if (DEBUG) {
            validateChildOrder();
        }
    }

    /**
     * Method called when Anchor position is decided. Extending class can setup accordingly or
     * even update anchor info if necessary.
     * @param recycler The recycler for the layout
     * @param state The layout state
     * @param anchorInfo The mutable POJO that keeps the position and offset.
     * @param firstLayoutItemDirection The direction of the first layout filling in terms of adapter
     *                                 indices.
     */
    void onAnchorReady(RecyclerView.Recycler recycler, RecyclerView.State state,
                       AnchorInfo anchorInfo, int firstLayoutItemDirection) {
    }

    /**
     * If necessary, layouts new items for predictive animations
     */
    private void layoutForPredictiveAnimations(RecyclerView.Recycler recycler,
            RecyclerView.State state, int startOffset,  int endOffset) {
        // If there are scrap children that we did not layout, we need to find where they did go
        // and layout them accordingly so that animations can work as expected.
        // This case may happen if new views are added or an existing view expands and pushes
        // another view out of bounds.
        if (!state.willRunPredictiveAnimations() ||  getChildCount() == 0 || state.isPreLayout()
                || !supportsPredictiveItemAnimations()) {
            return;
        }
        // to make the logic simpler, we calculate the size of children and call fill.
        int scrapExtraStart = 0, scrapExtraEnd = 0;
        final List<RecyclerView.ViewHolder> scrapList = recycler.getScrapList();
        final int scrapSize = scrapList.size();
        final int firstChildPos = getPosition(getChildAt(0));
        for (int i = 0; i < scrapSize; i++) {
            RecyclerView.ViewHolder scrap = scrapList.get(i);
            if (scrap.isRemoved()) {
                continue;
            }
            final int position = scrap.getLayoutPosition();
            final int direction = position < firstChildPos != mShouldReverseLayout
                    ? LayoutState.LAYOUT_START : LayoutState.LAYOUT_END;
            if (direction == LayoutState.LAYOUT_START) {
                scrapExtraStart += mOrientationHelper.getDecoratedMeasurement(scrap.itemView);
            } else {
                scrapExtraEnd += mOrientationHelper.getDecoratedMeasurement(scrap.itemView);
            }
        }

        if (DEBUG) {
            Log.d(TAG, "for unused scrap, decided to add " + scrapExtraStart
                    + " towards start and " + scrapExtraEnd + " towards end");
        }
        mLayoutState.mScrapList = scrapList;
        if (scrapExtraStart > 0) {
            View anchor = getChildClosestToStart();
            updateLayoutStateToFillStart(getPosition(anchor), startOffset);
            mLayoutState.mExtra = scrapExtraStart;
            mLayoutState.mAvailable = 0;
            mLayoutState.assignPositionFromScrapList();
            fill(recycler, mLayoutState, state, false);
        }

        if (scrapExtraEnd > 0) {
            View anchor = getChildClosestToEnd();
            updateLayoutStateToFillEnd(getPosition(anchor), endOffset);
            mLayoutState.mExtra = scrapExtraEnd;
            mLayoutState.mAvailable = 0;
            mLayoutState.assignPositionFromScrapList();
            fill(recycler, mLayoutState, state, false);
        }
        mLayoutState.mScrapList = null;
    }

    private void updateAnchorInfoForLayout(RecyclerView.Recycler recycler, RecyclerView.State state,
                                           AnchorInfo anchorInfo) {
        if (updateAnchorFromPendingData(state, anchorInfo)) {
            if (DEBUG) {
                Log.d(TAG, "updated anchor info from pending information");
            }
            return;
        }

        if (updateAnchorFromChildren(recycler, state, anchorInfo)) {
            if (DEBUG) {
                Log.d(TAG, "updated anchor info from existing children");
            }
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "deciding anchor info for fresh state");
        }
        anchorInfo.assignCoordinateFromPadding();
        anchorInfo.mPosition = mStackFromEnd ? state.getItemCount() - 1 : 0;
    }

    /**
     * Finds an anchor child from existing Views. Most of the time, this is the view closest to
     * start or end that has a valid position (e.g. not removed).
     * <p>
     * If a child has focus, it is given priority.
     */
    private boolean updateAnchorFromChildren(RecyclerView.Recycler recycler,
                                             RecyclerView.State state, AnchorInfo anchorInfo) {
        if (getChildCount() == 0) {
            return false;
        }
        final View focused = getFocusedChild();
        if (focused != null && anchorInfo.isViewValidAsAnchor(focused, state)) {
            anchorInfo.assignFromViewAndKeepVisibleRect(focused);
            return true;
        }
        if (mLastStackFromEnd != mStackFromEnd) {
            return false;
        }
        View referenceChild = anchorInfo.mLayoutFromEnd
                ? findReferenceChildClosestToEnd(recycler, state)
                : findReferenceChildClosestToStart(recycler, state);
        if (referenceChild != null) {
            anchorInfo.assignFromView(referenceChild);
            // If all visible views are removed in 1 pass, reference child might be out of bounds.
            // If that is the case, offset it back to 0 so that we use these pre-layout children.
            if (!state.isPreLayout() && supportsPredictiveItemAnimations()) {
                // validate this child is at least partially visible. if not, offset it to start
                final boolean notVisible =
                        mOrientationHelper.getDecoratedStart(referenceChild) >= mOrientationHelper
                                .getEndAfterPadding()
                                || mOrientationHelper.getDecoratedEnd(referenceChild)
                                < mOrientationHelper.getStartAfterPadding();
                if (notVisible) {
                    anchorInfo.mCoordinate = anchorInfo.mLayoutFromEnd
                            ? mOrientationHelper.getEndAfterPadding()
                            : mOrientationHelper.getStartAfterPadding();
                }
            }
            return true;
        }
        return false;
    }

    /**
     * If there is a pending scroll position or saved states, updates the anchor info from that
     * data and returns true
     */
    private boolean updateAnchorFromPendingData(RecyclerView.State state, AnchorInfo anchorInfo) {
        if (state.isPreLayout() || mPendingScrollPosition == NO_POSITION) {
            return false;
        }
        // validate scroll position
        if (mPendingScrollPosition < 0 || mPendingScrollPosition >= state.getItemCount()) {
            mPendingScrollPosition = NO_POSITION;
            mPendingScrollPositionOffset = INVALID_OFFSET;
            if (DEBUG) {
                Log.e(TAG, "ignoring invalid scroll position " + mPendingScrollPosition);
            }
            return false;
        }

        // if child is visible, try to make it a reference child and ensure it is fully visible.
        // if child is not visible, align it depending on its virtual position.
        anchorInfo.mPosition = mPendingScrollPosition;
        if (mPendingSavedState != null && mPendingSavedState.hasValidAnchor()) {
            // Anchor offset depends on how that child was laid out. Here, we update it
            // according to our current view bounds
            anchorInfo.mLayoutFromEnd = mPendingSavedState.mAnchorLayoutFromEnd;
            if (anchorInfo.mLayoutFromEnd) {
                anchorInfo.mCoordinate = mOrientationHelper.getEndAfterPadding() -
                        mPendingSavedState.mAnchorOffset;
            } else {
                anchorInfo.mCoordinate = mOrientationHelper.getStartAfterPadding() +
                        mPendingSavedState.mAnchorOffset;
            }
            return true;
        }

        if (mPendingScrollPositionOffset == INVALID_OFFSET) {
            View child = findViewByPosition(mPendingScrollPosition);
            if (child != null) {
                final int childSize = mOrientationHelper.getDecoratedMeasurement(child);
                if (childSize > mOrientationHelper.getTotalSpace()) {
                    // item does not fit. fix depending on layout direction
                    anchorInfo.assignCoordinateFromPadding();
                    return true;
                }
                final int startGap = mOrientationHelper.getDecoratedStart(child)
                        - mOrientationHelper.getStartAfterPadding();
                if (startGap < 0) {
                    anchorInfo.mCoordinate = mOrientationHelper.getStartAfterPadding();
                    anchorInfo.mLayoutFromEnd = false;
                    return true;
                }
                final int endGap = mOrientationHelper.getEndAfterPadding() -
                        mOrientationHelper.getDecoratedEnd(child);
                if (endGap < 0) {
                    anchorInfo.mCoordinate = mOrientationHelper.getEndAfterPadding();
                    anchorInfo.mLayoutFromEnd = true;
                    return true;
                }
                anchorInfo.mCoordinate = anchorInfo.mLayoutFromEnd
                        ? (mOrientationHelper.getDecoratedEnd(child) + mOrientationHelper
                                .getTotalSpaceChange())
                        : mOrientationHelper.getDecoratedStart(child);
            } else { // item is not visible.
                if (getChildCount() > 0) {
                    // get position of any child, does not matter
                    int pos = getPosition(getChildAt(0));
                    anchorInfo.mLayoutFromEnd = mPendingScrollPosition < pos
                            == mShouldReverseLayout;
                }
                anchorInfo.assignCoordinateFromPadding();
            }
            return true;
        }
        // override layout from end values for consistency
        anchorInfo.mLayoutFromEnd = mShouldReverseLayout;
        // if this changes, we should update prepareForDrop as well
        if (mShouldReverseLayout) {
            anchorInfo.mCoordinate = mOrientationHelper.getEndAfterPadding() -
                    mPendingScrollPositionOffset;
        } else {
            anchorInfo.mCoordinate = mOrientationHelper.getStartAfterPadding() +
                    mPendingScrollPositionOffset;
        }
        return true;
    }

    /**
     * @return The final offset amount for children
     */
    private int fixLayoutEndGap(int endOffset, RecyclerView.Recycler recycler,
            RecyclerView.State state, boolean canOffsetChildren) {
        int gap = mOrientationHelper.getEndAfterPadding() - endOffset;
        int fixOffset = 0;
        if (gap > 0) {
            fixOffset = -scrollBy(-gap, recycler, state);
        } else {
            return 0; // nothing to fix
        }
        // move offset according to scroll amount
        endOffset += fixOffset;
        if (canOffsetChildren) {
            // re-calculate gap, see if we could fix it
            gap = mOrientationHelper.getEndAfterPadding() - endOffset;
            if (gap > 0) {
                mOrientationHelper.offsetChildren(gap);
                return gap + fixOffset;
            }
        }
        return fixOffset;
    }

    /**
     * @return The final offset amount for children
     */
    private int fixLayoutStartGap(int startOffset, RecyclerView.Recycler recycler,
            RecyclerView.State state, boolean canOffsetChildren) {
        int gap = startOffset - mOrientationHelper.getStartAfterPadding();
        int fixOffset = 0;
        if (gap > 0) {
            // check if we should fix this gap.
            fixOffset = -scrollBy(gap, recycler, state);
        } else {
            return 0; // nothing to fix
        }
        startOffset += fixOffset;
        if (canOffsetChildren) {
            // re-calculate gap, see if we could fix it
            gap = startOffset - mOrientationHelper.getStartAfterPadding();
            if (gap > 0) {
                mOrientationHelper.offsetChildren(-gap);
                return fixOffset - gap;
            }
        }
        return fixOffset;
    }

    private void updateLayoutStateToFillEnd(AnchorInfo anchorInfo) {
        updateLayoutStateToFillEnd(anchorInfo.mPosition, anchorInfo.mCoordinate);
    }

    private void updateLayoutStateToFillEnd(int itemPosition, int offset) {
        mLayoutState.mAvailable = mOrientationHelper.getEndAfterPadding() - offset;
        mLayoutState.mItemDirection = mShouldReverseLayout ? LayoutState.ITEM_DIRECTION_HEAD :
                LayoutState.ITEM_DIRECTION_TAIL;
        mLayoutState.mCurrentPosition = itemPosition;
        mLayoutState.mLayoutDirection = LayoutState.LAYOUT_END;
        mLayoutState.mOffset = offset;
        mLayoutState.mScrollingOffset = LayoutState.SCOLLING_OFFSET_NaN;
    }

    private void updateLayoutStateToFillStart(AnchorInfo anchorInfo) {
        updateLayoutStateToFillStart(anchorInfo.mPosition, anchorInfo.mCoordinate);
    }

    private void updateLayoutStateToFillStart(int itemPosition, int offset) {
        mLayoutState.mAvailable = offset - mOrientationHelper.getStartAfterPadding();
        mLayoutState.mCurrentPosition = itemPosition;
        mLayoutState.mItemDirection = mShouldReverseLayout ? LayoutState.ITEM_DIRECTION_TAIL :
                LayoutState.ITEM_DIRECTION_HEAD;
        mLayoutState.mLayoutDirection = LayoutState.LAYOUT_START;
        mLayoutState.mOffset = offset;
        mLayoutState.mScrollingOffset = LayoutState.SCOLLING_OFFSET_NaN;

    }

    protected boolean isLayoutRTL() {
        return getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL;
    }

    void ensureLayoutState() {
        if (mLayoutState == null) {
            mLayoutState = createLayoutState();
        }
        if (mOrientationHelper == null) {
            mOrientationHelper = OrientationHelper.createOrientationHelper(this, mOrientation);
        }
    }

    /**
     * Test overrides this to plug some tracking and verification.
     *
     * @return A new LayoutState
     */
    LayoutState createLayoutState() {
        return new LayoutState();
    }

    /**
     * <p>Scroll the RecyclerView to make the position visible.</p>
     *
     * <p>RecyclerView will scroll the minimum amount that is necessary to make the
     * target position visible. If you are looking for a similar behavior to
     * {@link android.widget.ListView#setSelection(int)} or
     * {@link android.widget.ListView#setSelectionFromTop(int, int)}, use
     * {@link #scrollToPositionWithOffset(int, int)}.</p>
     *
     * <p>Note that scroll position change will not be reflected until the next layout call.</p>
     *
     * @param position Scroll to this adapter position
     * @see #scrollToPositionWithOffset(int, int)
     */
    @Override
    public void scrollToPosition(int position) {
        mPendingScrollPosition = position;
        mPendingScrollPositionOffset = INVALID_OFFSET;
        if (mPendingSavedState != null) {
            mPendingSavedState.invalidateAnchor();
        }
        requestLayout();
    }

    /**
     * Scroll to the specified adapter position with the given offset from resolved layout
     * start. Resolved layout start depends on {@link #getReverseLayout()},
     * {@link ViewCompat#getLayoutDirection(android.view.View)} and {@link #getStackFromEnd()}.
     * <p>
     * For example, if layout is {@link #VERTICAL} and {@link #getStackFromEnd()} is true, calling
     * <code>scrollToPositionWithOffset(10, 20)</code> will layout such that
     * <code>item[10]</code>'s bottom is 20 pixels above the RecyclerView's bottom.
     * <p>
     * Note that scroll position change will not be reflected until the next layout call.
     * <p>
     * If you are just trying to make a position visible, use {@link #scrollToPosition(int)}.
     *
     * @param position Index (starting at 0) of the reference item.
     * @param offset   The distance (in pixels) between the start edge of the item view and
     *                 start edge of the RecyclerView.
     * @see #setReverseLayout(boolean)
     * @see #scrollToPosition(int)
     */
    public void scrollToPositionWithOffset(int position, int offset) {
        mPendingScrollPosition = position;
        mPendingScrollPositionOffset = offset;
        if (mPendingSavedState != null) {
            mPendingSavedState.invalidateAnchor();
        }
        requestLayout();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler,
            RecyclerView.State state) {
        if (mOrientation == VERTICAL) {
            return 0;
        }
        return scrollBy(dx, recycler, state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
            RecyclerView.State state) {
        if (mOrientation == HORIZONTAL) {
            return 0;
        }
        return scrollBy(dy, recycler, state);
    }

    @Override
    public int computeHorizontalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset(state);
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset(state);
    }

    @Override
    public int computeHorizontalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent(state);
    }

    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent(state);
    }

    @Override
    public int computeHorizontalScrollRange(RecyclerView.State state) {
        return computeScrollRange(state);
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return computeScrollRange(state);
    }

    private int computeScrollOffset(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        ensureLayoutState();
        return ScrollbarHelper.computeScrollOffset(state, mOrientationHelper,
                findFirstVisibleChildClosestToStart(!mSmoothScrollbarEnabled, true),
                findFirstVisibleChildClosestToEnd(!mSmoothScrollbarEnabled, true),
                this, mSmoothScrollbarEnabled, mShouldReverseLayout);
    }

    private int computeScrollExtent(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        ensureLayoutState();
        return ScrollbarHelper.computeScrollExtent(state, mOrientationHelper,
                findFirstVisibleChildClosestToStart(!mSmoothScrollbarEnabled, true),
                findFirstVisibleChildClosestToEnd(!mSmoothScrollbarEnabled, true),
                this,  mSmoothScrollbarEnabled);
    }

    private int computeScrollRange(RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }
        ensureLayoutState();
        return ScrollbarHelper.computeScrollRange(state, mOrientationHelper,
                findFirstVisibleChildClosestToStart(!mSmoothScrollbarEnabled, true),
                findFirstVisibleChildClosestToEnd(!mSmoothScrollbarEnabled, true),
                this, mSmoothScrollbarEnabled);
    }

    /**
     * When smooth scrollbar is enabled, the position and size of the scrollbar thumb is computed
     * based on the number of visible pixels in the visible items. This however assumes that all
     * list items have similar or equal widths or heights (depending on list orientation).
     * If you use a list in which items have different dimensions, the scrollbar will change
     * appearance as the user scrolls through the list. To avoid this issue,  you need to disable
     * this property.
     *
     * When smooth scrollbar is disabled, the position and size of the scrollbar thumb is based
     * solely on the number of items in the adapter and the position of the visible items inside
     * the adapter. This provides a stable scrollbar as the user navigates through a list of items
     * with varying widths / heights.
     *
     * @param enabled Whether or not to enable smooth scrollbar.
     *
     * @see #setSmoothScrollbarEnabled(boolean)
     */
    public void setSmoothScrollbarEnabled(boolean enabled) {
        mSmoothScrollbarEnabled = enabled;
    }

    /**
     * Returns the current state of the smooth scrollbar feature. It is enabled by default.
     *
     * @return True if smooth scrollbar is enabled, false otherwise.
     *
     * @see #setSmoothScrollbarEnabled(boolean)
     */
    public boolean isSmoothScrollbarEnabled() {
        return mSmoothScrollbarEnabled;
    }

    private void updateLayoutState(int layoutDirection, int requiredSpace,
            boolean canUseExistingSpace, RecyclerView.State state) {
        // If parent provides a hint, don't measure unlimited.
        mLayoutState.mInfinite = resolveIsInfinite();
        mLayoutState.mExtra = getExtraLayoutSpace(state);
        mLayoutState.mLayoutDirection = layoutDirection;
        int scrollingOffset;
        if (layoutDirection == LayoutState.LAYOUT_END) {
            mLayoutState.mExtra += mOrientationHelper.getEndPadding();
            // get the first child in the direction we are going
            final View child = getChildClosestToEnd();
            // the direction in which we are traversing children
            mLayoutState.mItemDirection = mShouldReverseLayout ? LayoutState.ITEM_DIRECTION_HEAD
                    : LayoutState.ITEM_DIRECTION_TAIL;
            mLayoutState.mCurrentPosition = getPosition(child) + mLayoutState.mItemDirection;
            mLayoutState.mOffset = mOrientationHelper.getDecoratedEnd(child);
            // calculate how much we can scroll without adding new children (independent of layout)
            scrollingOffset = mOrientationHelper.getDecoratedEnd(child)
                    - mOrientationHelper.getEndAfterPadding();

        } else {
            final View child = getChildClosestToStart();
            mLayoutState.mExtra += mOrientationHelper.getStartAfterPadding();
            mLayoutState.mItemDirection = mShouldReverseLayout ? LayoutState.ITEM_DIRECTION_TAIL
                    : LayoutState.ITEM_DIRECTION_HEAD;
            mLayoutState.mCurrentPosition = getPosition(child) + mLayoutState.mItemDirection;
            mLayoutState.mOffset = mOrientationHelper.getDecoratedStart(child);
            scrollingOffset = -mOrientationHelper.getDecoratedStart(child)
                    + mOrientationHelper.getStartAfterPadding();
        }
        mLayoutState.mAvailable = requiredSpace;
        if (canUseExistingSpace) {
            mLayoutState.mAvailable -= scrollingOffset;
        }
        mLayoutState.mScrollingOffset = scrollingOffset;
    }

    boolean resolveIsInfinite() {
        return mOrientationHelper.getMode() == View.MeasureSpec.UNSPECIFIED
                && mOrientationHelper.getEnd() == 0;
    }

    int scrollBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0 || dy == 0) {
            return 0;
        }
        mLayoutState.mRecycle = true;
        ensureLayoutState();
        final int layoutDirection = dy > 0 ? LayoutState.LAYOUT_END : LayoutState.LAYOUT_START;
        final int absDy = Math.abs(dy);
        updateLayoutState(layoutDirection, absDy, true, state);
        final int consumed = mLayoutState.mScrollingOffset
                + fill(recycler, mLayoutState, state, false);
        if (consumed < 0) {
            if (DEBUG) {
                Log.d(TAG, "Don't have any more elements to scroll");
            }
            return 0;
        }
        final int scrolled = absDy > consumed ? layoutDirection * consumed : dy;
        mOrientationHelper.offsetChildren(-scrolled);
        if (DEBUG) {
            Log.d(TAG, "scroll req: " + dy + " scrolled: " + scrolled);
        }
        mLayoutState.mLastScrollDelta = scrolled;
        return scrolled;
    }

    @Override
    public void assertNotInLayoutOrScroll(String message) {
        if (mPendingSavedState == null) {
            super.assertNotInLayoutOrScroll(message);
        }
    }

    /**
     * Recycles children between given indices.
     *
     * @param startIndex inclusive
     * @param endIndex   exclusive
     */
    private void recycleChildren(RecyclerView.Recycler recycler, int startIndex, int endIndex) {
        if (startIndex == endIndex) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Recycling " + Math.abs(startIndex - endIndex) + " items");
        }
        if (endIndex > startIndex) {
            for (int i = endIndex - 1; i >= startIndex; i--) {
                removeAndRecycleViewAt(i, recycler);
            }
        } else {
            for (int i = startIndex; i > endIndex; i--) {
                removeAndRecycleViewAt(i, recycler);
            }
        }
    }

    /**
     * Recycles views that went out of bounds after scrolling towards the end of the layout.
     *
     * @param recycler Recycler instance of {@link android.support.v7.widget.RecyclerView}
     * @param dt       This can be used to add additional padding to the visible area. This is used
     *                 to detect children that will go out of bounds after scrolling, without
     *                 actually moving them.
     */
    private void recycleViewsFromStart(RecyclerView.Recycler recycler, int dt) {
        if (dt < 0) {
            if (DEBUG) {
                Log.d(TAG, "Called recycle from start with a negative value. This might happen"
                        + " during layout changes but may be sign of a bug");
            }
            return;
        }
        // ignore padding, ViewGroup may not clip children.
        final int limit = dt;
        final int childCount = getChildCount();
        if (mShouldReverseLayout) {
            for (int i = childCount - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (mOrientationHelper.getDecoratedEnd(child) > limit) {// stop here
                    recycleChildren(recycler, childCount - 1, i);
                    return;
                }
            }
        } else {
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (mOrientationHelper.getDecoratedEnd(child) > limit) {// stop here
                    recycleChildren(recycler, 0, i);
                    return;
                }
            }
        }
    }


    /**
     * Recycles views that went out of bounds after scrolling towards the start of the layout.
     *
     * @param recycler Recycler instance of {@link android.support.v7.widget.RecyclerView}
     * @param dt       This can be used to add additional padding to the visible area. This is used
     *                 to detect children that will go out of bounds after scrolling, without
     *                 actually moving them.
     */
    private void recycleViewsFromEnd(RecyclerView.Recycler recycler, int dt) {
        final int childCount = getChildCount();
        if (dt < 0) {
            if (DEBUG) {
                Log.d(TAG, "Called recycle from end with a negative value. This might happen"
                        + " during layout changes but may be sign of a bug");
            }
            return;
        }
        final int limit = mOrientationHelper.getEnd() - dt;
        if (mShouldReverseLayout) {
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (mOrientationHelper.getDecoratedStart(child) < limit) {// stop here
                    recycleChildren(recycler, 0, i);
                    return;
                }
            }
        } else {
            for (int i = childCount - 1; i >= 0; i--) {
                View child = getChildAt(i);
                if (mOrientationHelper.getDecoratedStart(child) < limit) {// stop here
                    recycleChildren(recycler, childCount - 1, i);
                    return;
                }
            }
        }

    }

    /**
     * Helper method to call appropriate recycle method depending on current layout direction
     *
     * @param recycler    Current recycler that is attached to RecyclerView
     * @param layoutState Current layout state. Right now, this object does not change but
     *                    we may consider moving it out of this view so passing around as a
     *                    parameter for now, rather than accessing {@link #mLayoutState}
     * @see #recycleViewsFromStart(android.support.v7.widget.RecyclerView.Recycler, int)
     * @see #recycleViewsFromEnd(android.support.v7.widget.RecyclerView.Recycler, int)
     * @see android.support.v7.widget.LinearLayoutManager.LayoutState#mLayoutDirection
     */
    private void recycleByLayoutState(RecyclerView.Recycler recycler, LayoutState layoutState) {
        if (!layoutState.mRecycle || layoutState.mInfinite) {
            return;
        }
        if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
            recycleViewsFromEnd(recycler, layoutState.mScrollingOffset);
        } else {
            recycleViewsFromStart(recycler, layoutState.mScrollingOffset);
        }
    }

    /**
     * The magic functions :). Fills the given layout, defined by the layoutState. This is fairly
     * independent from the rest of the {@link android.support.v7.widget.LinearLayoutManager}
     * and with little change, can be made publicly available as a helper class.
     *
     * @param recycler        Current recycler that is attached to RecyclerView
     * @param layoutState     Configuration on how we should fill out the available space.
     * @param state           Context passed by the RecyclerView to control scroll steps.
     * @param stopOnFocusable If true, filling stops in the first focusable new child
     * @return Number of pixels that it added. Useful for scoll functions.
     */
    int fill(RecyclerView.Recycler recycler, LayoutState layoutState,
            RecyclerView.State state, boolean stopOnFocusable) {
        // max offset we should set is mFastScroll + available
        final int start = layoutState.mAvailable;
        if (layoutState.mScrollingOffset != LayoutState.SCOLLING_OFFSET_NaN) {
            // TODO ugly bug fix. should not happen
            if (layoutState.mAvailable < 0) {
                layoutState.mScrollingOffset += layoutState.mAvailable;
            }
            recycleByLayoutState(recycler, layoutState);
        }
        int remainingSpace = layoutState.mAvailable + layoutState.mExtra;
        LayoutChunkResult layoutChunkResult = new LayoutChunkResult();
        while ((layoutState.mInfinite || remainingSpace > 0) && layoutState.hasMore(state)) {
            layoutChunkResult.resetInternal();
            layoutChunk(recycler, state, layoutState, layoutChunkResult);
            if (layoutChunkResult.mFinished) {
                break;
            }
            layoutState.mOffset += layoutChunkResult.mConsumed * layoutState.mLayoutDirection;
            /**
             * Consume the available space if:
             * * layoutChunk did not request to be ignored
             * * OR we are laying out scrap children
             * * OR we are not doing pre-layout
             */
            if (!layoutChunkResult.mIgnoreConsumed || mLayoutState.mScrapList != null
                    || !state.isPreLayout()) {
                layoutState.mAvailable -= layoutChunkResult.mConsumed;
                // we keep a separate remaining space because mAvailable is important for recycling
                remainingSpace -= layoutChunkResult.mConsumed;
            }

            if (layoutState.mScrollingOffset != LayoutState.SCOLLING_OFFSET_NaN) {
                layoutState.mScrollingOffset += layoutChunkResult.mConsumed;
                if (layoutState.mAvailable < 0) {
                    layoutState.mScrollingOffset += layoutState.mAvailable;
                }
                recycleByLayoutState(recycler, layoutState);
            }
            if (stopOnFocusable && layoutChunkResult.mFocusable) {
                break;
            }
        }
        if (DEBUG) {
            validateChildOrder();
        }
        return start - layoutState.mAvailable;
    }

    void layoutChunk(RecyclerView.Recycler recycler, RecyclerView.State state,
            LayoutState layoutState, LayoutChunkResult result) {
        View view = layoutState.next(recycler);
        if (view == null) {
            if (DEBUG && layoutState.mScrapList == null) {
                throw new RuntimeException("received null view when unexpected");
            }
            // if we are laying out views in scrap, this may return null which means there is
            // no more items to layout.
            result.mFinished = true;
            return;
        }
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        if (layoutState.mScrapList == null) {
            if (mShouldReverseLayout == (layoutState.mLayoutDirection
                    == LayoutState.LAYOUT_START)) {
                addView(view);
            } else {
                addView(view, 0);
            }
        } else {
            if (mShouldReverseLayout == (layoutState.mLayoutDirection
                    == LayoutState.LAYOUT_START)) {
                addDisappearingView(view);
            } else {
                addDisappearingView(view, 0);
            }
        }
        measureChildWithMargins(view, 0, 0);
        result.mConsumed = mOrientationHelper.getDecoratedMeasurement(view);
        int left, top, right, bottom;
        if (mOrientation == VERTICAL) {
            if (isLayoutRTL()) {
                right = getWidth() - getPaddingRight();
                left = right - mOrientationHelper.getDecoratedMeasurementInOther(view);
            } else {
                left = getPaddingLeft();
                right = left + mOrientationHelper.getDecoratedMeasurementInOther(view);
            }
            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                bottom = layoutState.mOffset;
                top = layoutState.mOffset - result.mConsumed;
            } else {
                top = layoutState.mOffset;
                bottom = layoutState.mOffset + result.mConsumed;
            }
        } else {
            top = getPaddingTop();
            bottom = top + mOrientationHelper.getDecoratedMeasurementInOther(view);

            if (layoutState.mLayoutDirection == LayoutState.LAYOUT_START) {
                right = layoutState.mOffset;
                left = layoutState.mOffset - result.mConsumed;
            } else {
                left = layoutState.mOffset;
                right = layoutState.mOffset + result.mConsumed;
            }
        }
        // We calculate everything with View's bounding box (which includes decor and margins)
        // To calculate correct layout position, we subtract margins.
        layoutDecorated(view, left + params.leftMargin, top + params.topMargin,
                right - params.rightMargin, bottom - params.bottomMargin);
        if (DEBUG) {
            Log.d(TAG, "laid out child at position " + getPosition(view) + ", with l:"
                    + (left + params.leftMargin) + ", t:" + (top + params.topMargin) + ", r:"
                    + (right - params.rightMargin) + ", b:" + (bottom - params.bottomMargin));
        }
        // Consume the available space if the view is not removed OR changed
        if (params.isItemRemoved() || params.isItemChanged()) {
            result.mIgnoreConsumed = true;
        }
        result.mFocusable = view.isFocusable();
    }

    @Override
    boolean shouldMeasureTwice() {
        return getHeightMode() != View.MeasureSpec.EXACTLY
                && getWidthMode() != View.MeasureSpec.EXACTLY
                && hasFlexibleChildInBothOrientations();
    }

    /**
     * Converts a focusDirection to orientation.
     *
     * @param focusDirection One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
     *                       {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT},
     *                       {@link View#FOCUS_BACKWARD}, {@link View#FOCUS_FORWARD}
     *                       or 0 for not applicable
     * @return {@link LayoutState#LAYOUT_START} or {@link LayoutState#LAYOUT_END} if focus direction
     * is applicable to current state, {@link LayoutState#INVALID_LAYOUT} otherwise.
     */
    int convertFocusDirectionToLayoutDirection(int focusDirection) {
        switch (focusDirection) {
            case View.FOCUS_BACKWARD:
                return LayoutState.LAYOUT_START;
            case View.FOCUS_FORWARD:
                return LayoutState.LAYOUT_END;
            case View.FOCUS_UP:
                return mOrientation == VERTICAL ? LayoutState.LAYOUT_START
                        : LayoutState.INVALID_LAYOUT;
            case View.FOCUS_DOWN:
                return mOrientation == VERTICAL ? LayoutState.LAYOUT_END
                        : LayoutState.INVALID_LAYOUT;
            case View.FOCUS_LEFT:
                return mOrientation == HORIZONTAL ? LayoutState.LAYOUT_START
                        : LayoutState.INVALID_LAYOUT;
            case View.FOCUS_RIGHT:
                return mOrientation == HORIZONTAL ? LayoutState.LAYOUT_END
                        : LayoutState.INVALID_LAYOUT;
            default:
                if (DEBUG) {
                    Log.d(TAG, "Unknown focus request:" + focusDirection);
                }
                return LayoutState.INVALID_LAYOUT;
        }

    }

    /**
     * Convenience method to find the child closes to start. Caller should check it has enough
     * children.
     *
     * @return The child closes to start of the layout from user's perspective.
     */
    private View getChildClosestToStart() {
        return getChildAt(mShouldReverseLayout ? getChildCount() - 1 : 0);
    }

    /**
     * Convenience method to find the child closes to end. Caller should check it has enough
     * children.
     *
     * @return The child closes to end of the layout from user's perspective.
     */
    private View getChildClosestToEnd() {
        return getChildAt(mShouldReverseLayout ? 0 : getChildCount() - 1);
    }

    /**
     * Convenience method to find the visible child closes to start. Caller should check if it has
     * enough children.
     *
     * @param completelyVisible Whether child should be completely visible or not
     * @return The first visible child closest to start of the layout from user's perspective.
     */
    private View findFirstVisibleChildClosestToStart(boolean completelyVisible,
            boolean acceptPartiallyVisible) {
        if (mShouldReverseLayout) {
            return findOneVisibleChild(getChildCount() - 1, -1, completelyVisible,
                    acceptPartiallyVisible);
        } else {
            return findOneVisibleChild(0, getChildCount(), completelyVisible,
                    acceptPartiallyVisible);
        }
    }

    /**
     * Convenience method to find the visible child closes to end. Caller should check if it has
     * enough children.
     *
     * @param completelyVisible Whether child should be completely visible or not
     * @return The first visible child closest to end of the layout from user's perspective.
     */
    private View findFirstVisibleChildClosestToEnd(boolean completelyVisible,
            boolean acceptPartiallyVisible) {
        if (mShouldReverseLayout) {
            return findOneVisibleChild(0, getChildCount(), completelyVisible,
                    acceptPartiallyVisible);
        } else {
            return findOneVisibleChild(getChildCount() - 1, -1, completelyVisible,
                    acceptPartiallyVisible);
        }
    }


    /**
     * Among the children that are suitable to be considered as an anchor child, returns the one
     * closest to the end of the layout.
     * <p>
     * Due to ambiguous adapter updates or children being removed, some children's positions may be
     * invalid. This method is a best effort to find a position within adapter bounds if possible.
     * <p>
     * It also prioritizes children that are within the visible bounds.
     * @return A View that can be used an an anchor View.
     */
    private View findReferenceChildClosestToEnd(RecyclerView.Recycler recycler,
                                                RecyclerView.State state) {
        return mShouldReverseLayout ? findFirstReferenceChild(recycler, state) :
                findLastReferenceChild(recycler, state);
    }

    /**
     * Among the children that are suitable to be considered as an anchor child, returns the one
     * closest to the start of the layout.
     * <p>
     * Due to ambiguous adapter updates or children being removed, some children's positions may be
     * invalid. This method is a best effort to find a position within adapter bounds if possible.
     * <p>
     * It also prioritizes children that are within the visible bounds.
     *
     * @return A View that can be used an an anchor View.
     */
    private View findReferenceChildClosestToStart(RecyclerView.Recycler recycler,
                                                  RecyclerView.State state) {
        return mShouldReverseLayout ? findLastReferenceChild(recycler, state) :
                findFirstReferenceChild(recycler, state);
    }

    private View findFirstReferenceChild(RecyclerView.Recycler recycler, RecyclerView.State state) {
        return findReferenceChild(recycler, state, 0, getChildCount(), state.getItemCount());
    }

    private View findLastReferenceChild(RecyclerView.Recycler recycler, RecyclerView.State state) {
        return findReferenceChild(recycler, state, getChildCount() - 1, -1, state.getItemCount());
    }

    // overridden by GridLayoutManager
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
                if (((LayoutParams) view.getLayoutParams()).isItemRemoved()) {
                    if (invalidMatch == null) {
                        invalidMatch = view; // removed item, least preferred
                    }
                } else if (mOrientationHelper.getDecoratedStart(view) >= boundsEnd ||
                        mOrientationHelper.getDecoratedEnd(view) < boundsStart) {
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

    /**
     * Returns the adapter position of the first visible view. This position does not include
     * adapter changes that were dispatched after the last layout pass.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * LayoutManager may pre-cache some views that are not necessarily visible. Those views
     * are ignored in this method.
     *
     * @return The adapter position of the first visible item or {@link RecyclerView#NO_POSITION} if
     * there aren't any visible items.
     * @see #findFirstCompletelyVisibleItemPosition()
     * @see #findLastVisibleItemPosition()
     */
    public int findFirstVisibleItemPosition() {
        final View child = findOneVisibleChild(0, getChildCount(), false, true);
        return child == null ? NO_POSITION : getPosition(child);
    }

    /**
     * Returns the adapter position of the first fully visible view. This position does not include
     * adapter changes that were dispatched after the last layout pass.
     * <p>
     * Note that bounds check is only performed in the current orientation. That means, if
     * LayoutManager is horizontal, it will only check the view's left and right edges.
     *
     * @return The adapter position of the first fully visible item or
     * {@link RecyclerView#NO_POSITION} if there aren't any visible items.
     * @see #findFirstVisibleItemPosition()
     * @see #findLastCompletelyVisibleItemPosition()
     */
    public int findFirstCompletelyVisibleItemPosition() {
        final View child = findOneVisibleChild(0, getChildCount(), true, false);
        return child == null ? NO_POSITION : getPosition(child);
    }

    /**
     * Returns the adapter position of the last visible view. This position does not include
     * adapter changes that were dispatched after the last layout pass.
     * <p>
     * Note that, this value is not affected by layout orientation or item order traversal.
     * ({@link #setReverseLayout(boolean)}). Views are sorted by their positions in the adapter,
     * not in the layout.
     * <p>
     * If RecyclerView has item decorators, they will be considered in calculations as well.
     * <p>
     * LayoutManager may pre-cache some views that are not necessarily visible. Those views
     * are ignored in this method.
     *
     * @return The adapter position of the last visible view or {@link RecyclerView#NO_POSITION} if
     * there aren't any visible items.
     * @see #findLastCompletelyVisibleItemPosition()
     * @see #findFirstVisibleItemPosition()
     */
    public int findLastVisibleItemPosition() {
        final View child = findOneVisibleChild(getChildCount() - 1, -1, false, true);
        return child == null ? NO_POSITION : getPosition(child);
    }

    /**
     * Returns the adapter position of the last fully visible view. This position does not include
     * adapter changes that were dispatched after the last layout pass.
     * <p>
     * Note that bounds check is only performed in the current orientation. That means, if
     * LayoutManager is horizontal, it will only check the view's left and right edges.
     *
     * @return The adapter position of the last fully visible view or
     * {@link RecyclerView#NO_POSITION} if there aren't any visible items.
     * @see #findLastVisibleItemPosition()
     * @see #findFirstCompletelyVisibleItemPosition()
     */
    public int findLastCompletelyVisibleItemPosition() {
        final View child = findOneVisibleChild(getChildCount() - 1, -1, true, false);
        return child == null ? NO_POSITION : getPosition(child);
    }

    View findOneVisibleChild(int fromIndex, int toIndex, boolean completelyVisible,
            boolean acceptPartiallyVisible) {
        ensureLayoutState();
        final int start = mOrientationHelper.getStartAfterPadding();
        final int end = mOrientationHelper.getEndAfterPadding();
        final int next = toIndex > fromIndex ? 1 : -1;
        View partiallyVisible = null;
        for (int i = fromIndex; i != toIndex; i+=next) {
            final View child = getChildAt(i);
            final int childStart = mOrientationHelper.getDecoratedStart(child);
            final int childEnd = mOrientationHelper.getDecoratedEnd(child);
            if (childStart < end && childEnd > start) {
                if (completelyVisible) {
                    if (childStart >= start && childEnd <= end) {
                        return child;
                    } else if (acceptPartiallyVisible && partiallyVisible == null) {
                        partiallyVisible = child;
                    }
                } else {
                    return child;
                }
            }
        }
        return partiallyVisible;
    }

    @Override
    public View onFocusSearchFailed(View focused, int focusDirection,
            RecyclerView.Recycler recycler, RecyclerView.State state) {
        resolveShouldLayoutReverse();
        if (getChildCount() == 0) {
            return null;
        }

        final int layoutDir = convertFocusDirectionToLayoutDirection(focusDirection);
        if (layoutDir == LayoutState.INVALID_LAYOUT) {
            return null;
        }
        ensureLayoutState();
        final View referenceChild;
        if (layoutDir == LayoutState.LAYOUT_START) {
            referenceChild = findReferenceChildClosestToStart(recycler, state);
        } else {
            referenceChild = findReferenceChildClosestToEnd(recycler, state);
        }
        if (referenceChild == null) {
            if (DEBUG) {
                Log.d(TAG,
                        "Cannot find a child with a valid position to be used for focus search.");
            }
            return null;
        }
        ensureLayoutState();
        final int maxScroll = (int) (MAX_SCROLL_FACTOR * mOrientationHelper.getTotalSpace());
        updateLayoutState(layoutDir, maxScroll, false, state);
        mLayoutState.mScrollingOffset = LayoutState.SCOLLING_OFFSET_NaN;
        mLayoutState.mRecycle = false;
        fill(recycler, mLayoutState, state, true);
        final View nextFocus;
        if (layoutDir == LayoutState.LAYOUT_START) {
            nextFocus = getChildClosestToStart();
        } else {
            nextFocus = getChildClosestToEnd();
        }
        if (nextFocus == referenceChild || !nextFocus.isFocusable()) {
            return null;
        }
        return nextFocus;
    }

    /**
     * Used for debugging.
     * Logs the internal representation of children to default logger.
     */
    private void logChildren() {
        Log.d(TAG, "internal representation of views on the screen");
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            Log.d(TAG, "item " + getPosition(child) + ", coord:"
                    + mOrientationHelper.getDecoratedStart(child));
        }
        Log.d(TAG, "==============");
    }

    /**
     * Used for debugging.
     * Validates that child views are laid out in correct order. This is important because rest of
     * the algorithm relies on this constraint.
     *
     * In default layout, child 0 should be closest to screen position 0 and last child should be
     * closest to position WIDTH or HEIGHT.
     * In reverse layout, last child should be closes to screen position 0 and first child should
     * be closest to position WIDTH  or HEIGHT
     */
    void validateChildOrder() {
        Log.d(TAG, "validating child count " + getChildCount());
        if (getChildCount() < 1) {
            return;
        }
        int lastPos = getPosition(getChildAt(0));
        int lastScreenLoc = mOrientationHelper.getDecoratedStart(getChildAt(0));
        if (mShouldReverseLayout) {
            for (int i = 1; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int pos = getPosition(child);
                int screenLoc = mOrientationHelper.getDecoratedStart(child);
                if (pos < lastPos) {
                    logChildren();
                    throw new RuntimeException("detected invalid position. loc invalid? " +
                            (screenLoc < lastScreenLoc));
                }
                if (screenLoc > lastScreenLoc) {
                    logChildren();
                    throw new RuntimeException("detected invalid location");
                }
            }
        } else {
            for (int i = 1; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int pos = getPosition(child);
                int screenLoc = mOrientationHelper.getDecoratedStart(child);
                if (pos < lastPos) {
                    logChildren();
                    throw new RuntimeException("detected invalid position. loc invalid? " +
                            (screenLoc < lastScreenLoc));
                }
                if (screenLoc < lastScreenLoc) {
                    logChildren();
                    throw new RuntimeException("detected invalid location");
                }
            }
        }
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return mPendingSavedState == null && mLastStackFromEnd == mStackFromEnd;
    }

    /**
     * @hide This method should be called by ItemTouchHelper only.
     */
    @Override
    public void prepareForDrop(View view, View target, int x, int y) {
        assertNotInLayoutOrScroll("Cannot drop a view during a scroll or layout calculation");
        ensureLayoutState();
        resolveShouldLayoutReverse();
        final int myPos = getPosition(view);
        final int targetPos = getPosition(target);
        final int dropDirection = myPos < targetPos ? LayoutState.ITEM_DIRECTION_TAIL :
                LayoutState.ITEM_DIRECTION_HEAD;
        if (mShouldReverseLayout) {
            if (dropDirection == LayoutState.ITEM_DIRECTION_TAIL) {
                scrollToPositionWithOffset(targetPos,
                        mOrientationHelper.getEndAfterPadding() -
                                (mOrientationHelper.getDecoratedStart(target) +
                                mOrientationHelper.getDecoratedMeasurement(view)));
            } else {
                scrollToPositionWithOffset(targetPos,
                        mOrientationHelper.getEndAfterPadding() -
                                mOrientationHelper.getDecoratedEnd(target));
            }
        } else {
            if (dropDirection == LayoutState.ITEM_DIRECTION_HEAD) {
                scrollToPositionWithOffset(targetPos, mOrientationHelper.getDecoratedStart(target));
            } else {
                scrollToPositionWithOffset(targetPos,
                        mOrientationHelper.getDecoratedEnd(target) -
                                mOrientationHelper.getDecoratedMeasurement(view));
            }
        }
    }

    /**
     * Helper class that keeps temporary state while {LayoutManager} is filling out the empty
     * space.
     */
    static class LayoutState {

        final static String TAG = "LinearLayoutManager#LayoutState";

        final static int LAYOUT_START = -1;

        final static int LAYOUT_END = 1;

        final static int INVALID_LAYOUT = Integer.MIN_VALUE;

        final static int ITEM_DIRECTION_HEAD = -1;

        final static int ITEM_DIRECTION_TAIL = 1;

        final static int SCOLLING_OFFSET_NaN = Integer.MIN_VALUE;

        /**
         * We may not want to recycle children in some cases (e.g. layout)
         */
        boolean mRecycle = true;

        /**
         * Pixel offset where layout should start
         */
        int mOffset;

        /**
         * Number of pixels that we should fill, in the layout direction.
         */
        int mAvailable;

        /**
         * Current position on the adapter to get the next item.
         */
        int mCurrentPosition;

        /**
         * Defines the direction in which the data adapter is traversed.
         * Should be {@link #ITEM_DIRECTION_HEAD} or {@link #ITEM_DIRECTION_TAIL}
         */
        int mItemDirection;

        /**
         * Defines the direction in which the layout is filled.
         * Should be {@link #LAYOUT_START} or {@link #LAYOUT_END}
         */
        int mLayoutDirection;

        /**
         * Used when LayoutState is constructed in a scrolling state.
         * It should be set the amount of scrolling we can make without creating a new view.
         * Settings this is required for efficient view recycling.
         */
        int mScrollingOffset;

        /**
         * Used if you want to pre-layout items that are not yet visible.
         * The difference with {@link #mAvailable} is that, when recycling, distance laid out for
         * {@link #mExtra} is not considered to avoid recycling visible children.
         */
        int mExtra = 0;

        /**
         * Equal to {@link RecyclerView.State#isPreLayout()}. When consuming scrap, if this value
         * is set to true, we skip removed views since they should not be laid out in post layout
         * step.
         */
        boolean mIsPreLayout = false;

        /**
         * The most recent {@link #scrollBy(int, RecyclerView.Recycler, RecyclerView.State)}
         * amount.
         */
        int mLastScrollDelta;

        /**
         * When LLM needs to layout particular views, it sets this list in which case, LayoutState
         * will only return views from this list and return null if it cannot find an item.
         */
        List<RecyclerView.ViewHolder> mScrapList = null;

        /**
         * Used when there is no limit in how many views can be laid out.
         */
        boolean mInfinite;

        /**
         * @return true if there are more items in the data adapter
         */
        boolean hasMore(RecyclerView.State state) {
            return mCurrentPosition >= 0 && mCurrentPosition < state.getItemCount();
        }

        /**
         * Gets the view for the next element that we should layout.
         * Also updates current item index to the next item, based on {@link #mItemDirection}
         *
         * @return The next element that we should layout.
         */
        View next(RecyclerView.Recycler recycler) {
            if (mScrapList != null) {
                return nextViewFromScrapList();
            }
            final View view = recycler.getViewForPosition(mCurrentPosition);
            mCurrentPosition += mItemDirection;
            return view;
        }

        /**
         * Returns the next item from the scrap list.
         * <p>
         * Upon finding a valid VH, sets current item position to VH.itemPosition + mItemDirection
         *
         * @return View if an item in the current position or direction exists if not null.
         */
        private View nextViewFromScrapList() {
            final int size = mScrapList.size();
            for (int i = 0; i < size; i++) {
                final View view = mScrapList.get(i).itemView;
                final LayoutParams lp = (LayoutParams) view.getLayoutParams();
                if (lp.isItemRemoved()) {
                    continue;
                }
                if (mCurrentPosition == lp.getViewLayoutPosition()) {
                    assignPositionFromScrapList(view);
                    return view;
                }
            }
            return null;
        }

        public void assignPositionFromScrapList() {
            assignPositionFromScrapList(null);
        }

        public void assignPositionFromScrapList(View ignore) {
            final View closest = nextViewInLimitedList(ignore);
            if (closest == null) {
                mCurrentPosition = NO_POSITION;
            } else {
                mCurrentPosition = ((LayoutParams) closest.getLayoutParams())
                        .getViewLayoutPosition();
            }
        }

        public View nextViewInLimitedList(View ignore) {
            int size = mScrapList.size();
            View closest = null;
            int closestDistance = Integer.MAX_VALUE;
            if (DEBUG && mIsPreLayout) {
                throw new IllegalStateException("Scrap list cannot be used in pre layout");
            }
            for (int i = 0; i < size; i++) {
                View view = mScrapList.get(i).itemView;
                final LayoutParams lp = (LayoutParams) view.getLayoutParams();
                if (view == ignore || lp.isItemRemoved()) {
                    continue;
                }
                final int distance = (lp.getViewLayoutPosition() - mCurrentPosition) *
                        mItemDirection;
                if (distance < 0) {
                    continue; // item is not in current direction
                }
                if (distance < closestDistance) {
                    closest = view;
                    closestDistance = distance;
                    if (distance == 0) {
                        break;
                    }
                }
            }
            return closest;
        }

        void log() {
            Log.d(TAG, "avail:" + mAvailable + ", ind:" + mCurrentPosition + ", dir:" +
                    mItemDirection + ", offset:" + mOffset + ", layoutDir:" + mLayoutDirection);
        }
    }

    /**
     * @hide
     */
    public static class SavedState implements Parcelable {

        int mAnchorPosition;

        int mAnchorOffset;

        boolean mAnchorLayoutFromEnd;

        public SavedState() {

        }

        SavedState(Parcel in) {
            mAnchorPosition = in.readInt();
            mAnchorOffset = in.readInt();
            mAnchorLayoutFromEnd = in.readInt() == 1;
        }

        public SavedState(SavedState other) {
            mAnchorPosition = other.mAnchorPosition;
            mAnchorOffset = other.mAnchorOffset;
            mAnchorLayoutFromEnd = other.mAnchorLayoutFromEnd;
        }

        boolean hasValidAnchor() {
            return mAnchorPosition >= 0;
        }

        void invalidateAnchor() {
            mAnchorPosition = NO_POSITION;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mAnchorPosition);
            dest.writeInt(mAnchorOffset);
            dest.writeInt(mAnchorLayoutFromEnd ? 1 : 0);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    /**
     * Simple data class to keep Anchor information
     */
    class AnchorInfo {
        int mPosition;
        int mCoordinate;
        boolean mLayoutFromEnd;
        void reset() {
            mPosition = NO_POSITION;
            mCoordinate = INVALID_OFFSET;
            mLayoutFromEnd = false;
        }

        /**
         * assigns anchor coordinate from the RecyclerView's padding depending on current
         * layoutFromEnd value
         */
        void assignCoordinateFromPadding() {
            mCoordinate = mLayoutFromEnd
                    ? mOrientationHelper.getEndAfterPadding()
                    : mOrientationHelper.getStartAfterPadding();
        }

        @Override
        public String toString() {
            return "AnchorInfo{" +
                    "mPosition=" + mPosition +
                    ", mCoordinate=" + mCoordinate +
                    ", mLayoutFromEnd=" + mLayoutFromEnd +
                    '}';
        }

        private boolean isViewValidAsAnchor(View child, RecyclerView.State state) {
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            return !lp.isItemRemoved() && lp.getViewLayoutPosition() >= 0
                    && lp.getViewLayoutPosition() < state.getItemCount();
        }

        public void assignFromViewAndKeepVisibleRect(View child) {
            final int spaceChange = mOrientationHelper.getTotalSpaceChange();
            if (spaceChange >= 0) {
                assignFromView(child);
                return;
            }
            mPosition = getPosition(child);
            if (mLayoutFromEnd) {
                final int prevLayoutEnd = mOrientationHelper.getEndAfterPadding() - spaceChange;
                final int childEnd = mOrientationHelper.getDecoratedEnd(child);
                final int previousEndMargin = prevLayoutEnd - childEnd;
                mCoordinate = mOrientationHelper.getEndAfterPadding() - previousEndMargin;
                // ensure we did not push child's top out of bounds because of this
                if (previousEndMargin > 0) {// we have room to shift bottom if necessary
                    final int childSize = mOrientationHelper.getDecoratedMeasurement(child);
                    final int estimatedChildStart = mCoordinate - childSize;
                    final int layoutStart = mOrientationHelper.getStartAfterPadding();
                    final int previousStartMargin = mOrientationHelper.getDecoratedStart(child) -
                            layoutStart;
                    final int startReference = layoutStart + Math.min(previousStartMargin, 0);
                    final int startMargin = estimatedChildStart - startReference;
                    if (startMargin < 0) {
                        // offset to make top visible but not too much
                        mCoordinate += Math.min(previousEndMargin, -startMargin);
                    }
                }
            } else {
                final int childStart = mOrientationHelper.getDecoratedStart(child);
                final int startMargin = childStart - mOrientationHelper.getStartAfterPadding();
                mCoordinate = childStart;
                if (startMargin > 0) { // we have room to fix end as well
                    final int estimatedEnd = childStart +
                            mOrientationHelper.getDecoratedMeasurement(child);
                    final int previousLayoutEnd = mOrientationHelper.getEndAfterPadding() -
                            spaceChange;
                    final int previousEndMargin = previousLayoutEnd -
                            mOrientationHelper.getDecoratedEnd(child);
                    final int endReference = mOrientationHelper.getEndAfterPadding() -
                            Math.min(0, previousEndMargin);
                    final int endMargin = endReference - estimatedEnd;
                    if (endMargin < 0) {
                        mCoordinate -= Math.min(startMargin, -endMargin);
                    }
                }
            }
        }

        public void assignFromView(View child) {
            if (mLayoutFromEnd) {
                mCoordinate = mOrientationHelper.getDecoratedEnd(child) +
                        mOrientationHelper.getTotalSpaceChange();
            } else {
                mCoordinate = mOrientationHelper.getDecoratedStart(child);
            }

            mPosition = getPosition(child);
        }
    }

    protected static class LayoutChunkResult {
        public int mConsumed;
        public boolean mFinished;
        public boolean mIgnoreConsumed;
        public boolean mFocusable;

        void resetInternal() {
            mConsumed = 0;
            mFinished = false;
            mIgnoreConsumed = false;
            mFocusable = false;
        }
    }
}
