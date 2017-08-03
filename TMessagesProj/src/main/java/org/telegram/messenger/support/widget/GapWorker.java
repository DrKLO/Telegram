/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.support.annotation.Nullable;
import android.support.v4.os.TraceCompat;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

final class GapWorker implements Runnable {

    static final ThreadLocal<GapWorker> sGapWorker = new ThreadLocal<>();

    ArrayList<RecyclerView> mRecyclerViews = new ArrayList<>();
    long mPostTimeNs;
    long mFrameIntervalNs;

    static class Task {
        public boolean immediate;
        public int viewVelocity;
        public int distanceToItem;
        public RecyclerView view;
        public int position;

        public void clear() {
            immediate = false;
            viewVelocity = 0;
            distanceToItem = 0;
            view = null;
            position = 0;
        }
    }

    /**
     * Temporary storage for prefetch Tasks that execute in {@link #prefetch(long)}. Task objects
     * are pooled in the ArrayList, and never removed to avoid allocations, but always cleared
     * in between calls.
     */
    private ArrayList<Task> mTasks = new ArrayList<>();

    /**
     * Prefetch information associated with a specific RecyclerView.
     */
    static class LayoutPrefetchRegistryImpl
            implements RecyclerView.LayoutManager.LayoutPrefetchRegistry {
        int mPrefetchDx;
        int mPrefetchDy;
        int[] mPrefetchArray;

        int mCount;

        void setPrefetchVector(int dx, int dy) {
            mPrefetchDx = dx;
            mPrefetchDy = dy;
        }

        void collectPrefetchPositionsFromView(RecyclerView view, boolean nested) {
            mCount = 0;
            if (mPrefetchArray != null) {
                Arrays.fill(mPrefetchArray, -1);
            }

            final RecyclerView.LayoutManager layout = view.mLayout;
            if (view.mAdapter != null
                    && layout != null
                    && layout.isItemPrefetchEnabled()) {
                if (nested) {
                    // nested prefetch, only if no adapter updates pending. Note: we don't query
                    // view.hasPendingAdapterUpdates(), as first layout may not have occurred
                    if (!view.mAdapterHelper.hasPendingUpdates()) {
                        layout.collectInitialPrefetchPositions(view.mAdapter.getItemCount(), this);
                    }
                } else {
                    // momentum based prefetch, only if we trust current child/adapter state
                    if (!view.hasPendingAdapterUpdates()) {
                        layout.collectAdjacentPrefetchPositions(mPrefetchDx, mPrefetchDy,
                                view.mState, this);
                    }
                }

                if (mCount > layout.mPrefetchMaxCountObserved) {
                    layout.mPrefetchMaxCountObserved = mCount;
                    layout.mPrefetchMaxObservedInInitialPrefetch = nested;
                    view.mRecycler.updateViewCacheSize();
                }
            }
        }

        @Override
        public void addPosition(int layoutPosition, int pixelDistance) {
            if (layoutPosition < 0) {
                throw new IllegalArgumentException("Layout positions must be non-negative");
            }

            if (pixelDistance < 0) {
                throw new IllegalArgumentException("Pixel distance must be non-negative");
            }

            // allocate or expand array as needed, doubling when needed
            final int storagePosition = mCount * 2;
            if (mPrefetchArray == null) {
                mPrefetchArray = new int[4];
                Arrays.fill(mPrefetchArray, -1);
            } else if (storagePosition >= mPrefetchArray.length) {
                final int[] oldArray = mPrefetchArray;
                mPrefetchArray = new int[storagePosition * 2];
                System.arraycopy(oldArray, 0, mPrefetchArray, 0, oldArray.length);
            }

            // add position
            mPrefetchArray[storagePosition] = layoutPosition;
            mPrefetchArray[storagePosition + 1] = pixelDistance;

            mCount++;
        }

        boolean lastPrefetchIncludedPosition(int position) {
            if (mPrefetchArray != null) {
                final int count = mCount * 2;
                for (int i = 0; i < count; i += 2) {
                    if (mPrefetchArray[i] == position) return true;
                }
            }
            return false;
        }

        /**
         * Called when prefetch indices are no longer valid for cache prioritization.
         */
        void clearPrefetchPositions() {
            if (mPrefetchArray != null) {
                Arrays.fill(mPrefetchArray, -1);
            }
            mCount = 0;
        }
    }

    public void add(RecyclerView recyclerView) {
        if (RecyclerView.DEBUG && mRecyclerViews.contains(recyclerView)) {
            throw new IllegalStateException("RecyclerView already present in worker list!");
        }
        mRecyclerViews.add(recyclerView);
    }

    public void remove(RecyclerView recyclerView) {
        boolean removeSuccess = mRecyclerViews.remove(recyclerView);
        if (RecyclerView.DEBUG && !removeSuccess) {
            throw new IllegalStateException("RecyclerView removal failed!");
        }
    }

    /**
     * Schedule a prefetch immediately after the current traversal.
     */
    void postFromTraversal(RecyclerView recyclerView, int prefetchDx, int prefetchDy) {
        if (recyclerView.isAttachedToWindow()) {
            if (RecyclerView.DEBUG && !mRecyclerViews.contains(recyclerView)) {
                throw new IllegalStateException("attempting to post unregistered view!");
            }
            if (mPostTimeNs == 0) {
                mPostTimeNs = recyclerView.getNanoTime();
                recyclerView.post(this);
            }
        }

        recyclerView.mPrefetchRegistry.setPrefetchVector(prefetchDx, prefetchDy);
    }

    static Comparator<Task> sTaskComparator = new Comparator<Task>() {
        @Override
        public int compare(Task lhs, Task rhs) {
            // first, prioritize non-cleared tasks
            if ((lhs.view == null) != (rhs.view == null)) {
                return lhs.view == null ? 1 : -1;
            }

            // then prioritize immediate
            if (lhs.immediate != rhs.immediate) {
                return lhs.immediate ? -1 : 1;
            }

            // then prioritize _highest_ view velocity
            int deltaViewVelocity = rhs.viewVelocity - lhs.viewVelocity;
            if (deltaViewVelocity != 0) return deltaViewVelocity;

            // then prioritize _lowest_ distance to item
            int deltaDistanceToItem = lhs.distanceToItem - rhs.distanceToItem;
            if (deltaDistanceToItem != 0) return deltaDistanceToItem;

            return 0;
        }
    };

    private void buildTaskList() {
        // Update PrefetchRegistry in each view
        final int viewCount = mRecyclerViews.size();
        int totalTaskCount = 0;
        for (int i = 0; i < viewCount; i++) {
            RecyclerView view = mRecyclerViews.get(i);
            if (view.getWindowVisibility() == View.VISIBLE) {
                view.mPrefetchRegistry.collectPrefetchPositionsFromView(view, false);
                totalTaskCount += view.mPrefetchRegistry.mCount;
            }
        }

        // Populate task list from prefetch data...
        mTasks.ensureCapacity(totalTaskCount);
        int totalTaskIndex = 0;
        for (int i = 0; i < viewCount; i++) {
            RecyclerView view = mRecyclerViews.get(i);
            if (view.getWindowVisibility() != View.VISIBLE) {
                // Invisible view, don't bother prefetching
                continue;
            }

            LayoutPrefetchRegistryImpl prefetchRegistry = view.mPrefetchRegistry;
            final int viewVelocity = Math.abs(prefetchRegistry.mPrefetchDx)
                    + Math.abs(prefetchRegistry.mPrefetchDy);
            for (int j = 0; j < prefetchRegistry.mCount * 2; j += 2) {
                final Task task;
                if (totalTaskIndex >= mTasks.size()) {
                    task = new Task();
                    mTasks.add(task);
                } else {
                    task = mTasks.get(totalTaskIndex);
                }
                final int distanceToItem = prefetchRegistry.mPrefetchArray[j + 1];

                task.immediate = distanceToItem <= viewVelocity;
                task.viewVelocity = viewVelocity;
                task.distanceToItem = distanceToItem;
                task.view = view;
                task.position = prefetchRegistry.mPrefetchArray[j];

                totalTaskIndex++;
            }
        }

        // ... and priority sort
        Collections.sort(mTasks, sTaskComparator);
    }

    static boolean isPrefetchPositionAttached(RecyclerView view, int position) {
        final int childCount = view.mChildHelper.getUnfilteredChildCount();
        for (int i = 0; i < childCount; i++) {
            View attachedView = view.mChildHelper.getUnfilteredChildAt(i);
            RecyclerView.ViewHolder holder = RecyclerView.getChildViewHolderInt(attachedView);
            // Note: can use mPosition here because adapter doesn't have pending updates
            if (holder.mPosition == position && !holder.isInvalid()) {
                return true;
            }
        }
        return false;
    }

    private RecyclerView.ViewHolder prefetchPositionWithDeadline(RecyclerView view,
            int position, long deadlineNs) {
        if (isPrefetchPositionAttached(view, position)) {
            // don't attempt to prefetch attached views
            return null;
        }

        RecyclerView.Recycler recycler = view.mRecycler;
        RecyclerView.ViewHolder holder = recycler.tryGetViewHolderForPositionByDeadline(
                position, false, deadlineNs);

        if (holder != null) {
            if (holder.isBound()) {
                // Only give the view a chance to go into the cache if binding succeeded
                // Note that we must use public method, since item may need cleanup
                recycler.recycleView(holder.itemView);
            } else {
                // Didn't bind, so we can't cache the view, but it will stay in the pool until
                // next prefetch/traversal. If a View fails to bind, it means we didn't have
                // enough time prior to the deadline (and won't for other instances of this
                // type, during this GapWorker prefetch pass).
                recycler.addViewHolderToRecycledViewPool(holder, false);
            }
        }
        return holder;
    }

    private void prefetchInnerRecyclerViewWithDeadline(@Nullable RecyclerView innerView,
            long deadlineNs) {
        if (innerView == null) {
            return;
        }

        if (innerView.mDataSetHasChangedAfterLayout
                && innerView.mChildHelper.getUnfilteredChildCount() != 0) {
            // RecyclerView has new data, but old attached views. Clear everything, so that
            // we can prefetch without partially stale data.
            innerView.removeAndRecycleViews();
        }

        // do nested prefetch!
        final LayoutPrefetchRegistryImpl innerPrefetchRegistry = innerView.mPrefetchRegistry;
        innerPrefetchRegistry.collectPrefetchPositionsFromView(innerView, true);

        if (innerPrefetchRegistry.mCount != 0) {
            try {
                TraceCompat.beginSection(RecyclerView.TRACE_NESTED_PREFETCH_TAG);
                innerView.mState.prepareForNestedPrefetch(innerView.mAdapter);
                for (int i = 0; i < innerPrefetchRegistry.mCount * 2; i += 2) {
                    // Note that we ignore immediate flag for inner items because
                    // we have lower confidence they're needed next frame.
                    final int innerPosition = innerPrefetchRegistry.mPrefetchArray[i];
                    prefetchPositionWithDeadline(innerView, innerPosition, deadlineNs);
                }
            } finally {
                TraceCompat.endSection();
            }
        }
    }

    private void flushTaskWithDeadline(Task task, long deadlineNs) {
        long taskDeadlineNs = task.immediate ? RecyclerView.FOREVER_NS : deadlineNs;
        RecyclerView.ViewHolder holder = prefetchPositionWithDeadline(task.view,
                task.position, taskDeadlineNs);
        if (holder != null && holder.mNestedRecyclerView != null) {
            prefetchInnerRecyclerViewWithDeadline(holder.mNestedRecyclerView.get(), deadlineNs);
        }
    }

    private void flushTasksWithDeadline(long deadlineNs) {
        for (int i = 0; i < mTasks.size(); i++) {
            final Task task = mTasks.get(i);
            if (task.view == null) {
                break; // done with populated tasks
            }
            flushTaskWithDeadline(task, deadlineNs);
            task.clear();
        }
    }

    void prefetch(long deadlineNs) {
        buildTaskList();
        flushTasksWithDeadline(deadlineNs);
    }

    @Override
    public void run() {
        try {
            TraceCompat.beginSection(RecyclerView.TRACE_PREFETCH_TAG);

            if (mRecyclerViews.isEmpty()) {
                // abort - no work to do
                return;
            }

            // Query most recent vsync so we can predict next one. Note that drawing time not yet
            // valid in animation/input callbacks, so query it here to be safe.
            final int size = mRecyclerViews.size();
            long latestFrameVsyncMs = 0;
            for (int i = 0; i < size; i++) {
                RecyclerView view = mRecyclerViews.get(i);
                if (view.getWindowVisibility() == View.VISIBLE) {
                    latestFrameVsyncMs = Math.max(view.getDrawingTime(), latestFrameVsyncMs);
                }
            }

            if (latestFrameVsyncMs == 0) {
                // abort - either no views visible, or couldn't get last vsync for estimating next
                return;
            }

            long nextFrameNs = TimeUnit.MILLISECONDS.toNanos(latestFrameVsyncMs) + mFrameIntervalNs;

            prefetch(nextFrameNs);

            // TODO: consider rescheduling self, if there's more work to do
        } finally {
            mPostTimeNs = 0;
            TraceCompat.endSection();
        }
    }
}
