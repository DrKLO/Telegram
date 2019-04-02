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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.telegram.messenger.support.widget;

import android.view.View;

/**
 * A helper class to do scroll offset calculations.
 */
class ScrollbarHelper {

    /**
     * @param startChild View closest to start of the list. (top or left)
     * @param endChild   View closest to end of the list (bottom or right)
     */
    static int computeScrollOffset(RecyclerView.State state, OrientationHelper orientation,
            View startChild, View endChild, RecyclerView.LayoutManager lm,
            boolean smoothScrollbarEnabled, boolean reverseLayout) {
        if (lm.getChildCount() == 0 || state.getItemCount() == 0 || startChild == null
                || endChild == null) {
            return 0;
        }
        final int minPosition = Math.min(lm.getPosition(startChild),
                lm.getPosition(endChild));
        final int maxPosition = Math.max(lm.getPosition(startChild),
                lm.getPosition(endChild));
        final int itemsBefore = reverseLayout
                ? Math.max(0, state.getItemCount() - maxPosition - 1)
                : Math.max(0, minPosition);
        if (!smoothScrollbarEnabled) {
            return itemsBefore;
        }
        final int laidOutArea = Math.abs(orientation.getDecoratedEnd(endChild)
                - orientation.getDecoratedStart(startChild));
        final int itemRange = Math.abs(lm.getPosition(startChild)
                - lm.getPosition(endChild)) + 1;
        final float avgSizePerRow = (float) laidOutArea / itemRange;

        return Math.round(itemsBefore * avgSizePerRow + (orientation.getStartAfterPadding()
                - orientation.getDecoratedStart(startChild)));
    }

    /**
     * @param startChild View closest to start of the list. (top or left)
     * @param endChild   View closest to end of the list (bottom or right)
     */
    static int computeScrollExtent(RecyclerView.State state, OrientationHelper orientation,
            View startChild, View endChild, RecyclerView.LayoutManager lm,
            boolean smoothScrollbarEnabled) {
        if (lm.getChildCount() == 0 || state.getItemCount() == 0 || startChild == null
                || endChild == null) {
            return 0;
        }
        if (!smoothScrollbarEnabled) {
            return Math.abs(lm.getPosition(startChild) - lm.getPosition(endChild)) + 1;
        }
        final int extend = orientation.getDecoratedEnd(endChild)
                - orientation.getDecoratedStart(startChild);
        return Math.min(orientation.getTotalSpace(), extend);
    }

    /**
     * @param startChild View closest to start of the list. (top or left)
     * @param endChild   View closest to end of the list (bottom or right)
     */
    static int computeScrollRange(RecyclerView.State state, OrientationHelper orientation,
            View startChild, View endChild, RecyclerView.LayoutManager lm,
            boolean smoothScrollbarEnabled) {
        if (lm.getChildCount() == 0 || state.getItemCount() == 0 || startChild == null
                || endChild == null) {
            return 0;
        }
        if (!smoothScrollbarEnabled) {
            return state.getItemCount();
        }
        // smooth scrollbar enabled. try to estimate better.
        final int laidOutArea = orientation.getDecoratedEnd(endChild)
                - orientation.getDecoratedStart(startChild);
        final int laidOutRange = Math.abs(lm.getPosition(startChild)
                - lm.getPosition(endChild))
                + 1;
        // estimate a size for full list.
        return (int) ((float) laidOutArea / laidOutRange * state.getItemCount());
    }
}
