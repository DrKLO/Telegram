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

import android.view.View;

/**
 * Helper class that keeps temporary state while {LayoutManager} is filling out the empty
 * space.
 */
class LayoutState {

    final static String TAG = "LayoutState";

    final static int LAYOUT_START = -1;

    final static int LAYOUT_END = 1;

    final static int INVALID_LAYOUT = Integer.MIN_VALUE;

    final static int ITEM_DIRECTION_HEAD = -1;

    final static int ITEM_DIRECTION_TAIL = 1;

    /**
     * We may not want to recycle children in some cases (e.g. layout)
     */
    boolean mRecycle = true;
    
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
     * This is the target pixel closest to the start of the layout that we are trying to fill
     */
    int mStartLine = 0;

    /**
     * This is the target pixel closest to the end of the layout that we are trying to fill
     */
    int mEndLine = 0;

    /**
     * If true, layout should stop if a focusable view is added
     */
    boolean mStopInFocusable;

    /**
     * If the content is not wrapped with any value
     */
    boolean mInfinite;

    /**
     * @return true if there are more items in the data adapter
     */
    boolean hasMore(RecyclerView.State state) {
        return mCurrentPosition >= 0 && mCurrentPosition < state.getItemCount();
    }

    /**
     * Gets the view for the next element that we should render.
     * Also updates current item index to the next item, based on {@link #mItemDirection}
     *
     * @return The next element that we should render.
     */
    View next(RecyclerView.Recycler recycler) {
        final View view = recycler.getViewForPosition(mCurrentPosition);
        mCurrentPosition += mItemDirection;
        return view;
    }

    @Override
    public String toString() {
        return "LayoutState{" +
                "mAvailable=" + mAvailable +
                ", mCurrentPosition=" + mCurrentPosition +
                ", mItemDirection=" + mItemDirection +
                ", mLayoutDirection=" + mLayoutDirection +
                ", mStartLine=" + mStartLine +
                ", mEndLine=" + mEndLine +
                '}';
    }
}
