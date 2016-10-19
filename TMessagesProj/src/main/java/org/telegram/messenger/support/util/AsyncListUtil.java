/*
 * Copyright (C) 2015 The Android Open Source Project
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

package org.telegram.messenger.support.util;

import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

/**
 * A utility class that supports asynchronous content loading.
 * <p>
 * It can be used to load Cursor data in chunks without querying the Cursor on the UI Thread while
 * keeping UI and cache synchronous for better user experience.
 * <p>
 * It loads the data on a background thread and keeps only a limited number of fixed sized
 * chunks in memory at all times.
 * <p>
 * {@link AsyncListUtil} queries the currently visible range through {@link ViewCallback},
 * loads the required data items in the background through {@link DataCallback}, and notifies a
 * {@link ViewCallback} when the data is loaded. It may load some extra items for smoother
 * scrolling.
 * <p>
 * Note that this class uses a single thread to load the data, so it suitable to load data from
 * secondary storage such as disk, but not from network.
 * <p>
 * This class is designed to work with {@link android.support.v7.widget.RecyclerView}, but it does
 * not depend on it and can be used with other list views.
 *
 */
public class AsyncListUtil<T> {
    private static final String TAG = "AsyncListUtil";

    private static final boolean DEBUG = false;

    final Class<T> mTClass;
    final int mTileSize;
    final DataCallback<T> mDataCallback;
    final ViewCallback mViewCallback;

    final TileList<T> mTileList;

    final ThreadUtil.MainThreadCallback<T> mMainThreadProxy;
    final ThreadUtil.BackgroundCallback<T> mBackgroundProxy;

    final int[] mTmpRange = new int[2];
    final int[] mPrevRange = new int[2];
    final int[] mTmpRangeExtended = new int[2];

    private boolean mAllowScrollHints;
    private int mScrollHint = ViewCallback.HINT_SCROLL_NONE;

    private int mItemCount = 0;

    int mDisplayedGeneration = 0;
    int mRequestedGeneration = mDisplayedGeneration;

    final private SparseIntArray mMissingPositions = new SparseIntArray();

    private void log(String s, Object... args) {
        Log.d(TAG, "[MAIN] " + String.format(s, args));
    }

    /**
     * Creates an AsyncListUtil.
     *
     * @param klass Class of the data item.
     * @param tileSize Number of item per chunk loaded at once.
     * @param dataCallback Data access callback.
     * @param viewCallback Callback for querying visible item range and update notifications.
     */
    public AsyncListUtil(Class<T> klass, int tileSize, DataCallback<T> dataCallback,
                         ViewCallback viewCallback) {
        mTClass = klass;
        mTileSize = tileSize;
        mDataCallback = dataCallback;
        mViewCallback = viewCallback;

        mTileList = new TileList<T>(mTileSize);

        ThreadUtil<T> threadUtil = new MessageThreadUtil<T>();
        mMainThreadProxy = threadUtil.getMainThreadProxy(mMainThreadCallback);
        mBackgroundProxy = threadUtil.getBackgroundProxy(mBackgroundCallback);

        refresh();
    }

    private boolean isRefreshPending() {
        return mRequestedGeneration != mDisplayedGeneration;
    }

    /**
     * Updates the currently visible item range.
     *
     * <p>
     * Identifies the data items that have not been loaded yet and initiates loading them in the
     * background. Should be called from the view's scroll listener (such as
     * {@link android.support.v7.widget.RecyclerView.OnScrollListener#onScrolled}).
     */
    public void onRangeChanged() {
        if (isRefreshPending()) {
            return;  // Will update range will the refresh result arrives.
        }
        updateRange();
        mAllowScrollHints = true;
    }

    /**
     * Forces reloading the data.
     * <p>
     * Discards all the cached data and reloads all required data items for the currently visible
     * range. To be called when the data item count and/or contents has changed.
     */
    public void refresh() {
        mMissingPositions.clear();
        mBackgroundProxy.refresh(++mRequestedGeneration);
    }

    /**
     * Returns the data item at the given position or <code>null</code> if it has not been loaded
     * yet.
     *
     * <p>
     * If this method has been called for a specific position and returned <code>null</code>, then
     * {@link ViewCallback#onItemLoaded(int)} will be called when it finally loads. Note that if
     * this position stays outside of the cached item range (as defined by
     * {@link ViewCallback#extendRangeInto} method), then the callback will never be called for
     * this position.
     *
     * @param position Item position.
     *
     * @return The data item at the given position or <code>null</code> if it has not been loaded
     *         yet.
     */
    public T getItem(int position) {
        if (position < 0 || position >= mItemCount) {
            throw new IndexOutOfBoundsException(position + " is not within 0 and " + mItemCount);
        }
        T item = mTileList.getItemAt(position);
        if (item == null && !isRefreshPending()) {
            mMissingPositions.put(position, 0);
        }
        return item;
    }

    /**
     * Returns the number of items in the data set.
     *
     * <p>
     * This is the number returned by a recent call to
     * {@link DataCallback#refreshData()}.
     *
     * @return Number of items.
     */
    public int getItemCount() {
        return mItemCount;
    }

    private void updateRange() {
        mViewCallback.getItemRangeInto(mTmpRange);
        if (mTmpRange[0] > mTmpRange[1] || mTmpRange[0] < 0) {
            return;
        }
        if (mTmpRange[1] >= mItemCount) {
            // Invalid range may arrive soon after the refresh.
            return;
        }

        if (!mAllowScrollHints) {
            mScrollHint = ViewCallback.HINT_SCROLL_NONE;
        } else if (mTmpRange[0] > mPrevRange[1] || mPrevRange[0] > mTmpRange[1]) {
            // Ranges do not intersect, long leap not a scroll.
            mScrollHint = ViewCallback.HINT_SCROLL_NONE;
        } else if (mTmpRange[0] < mPrevRange[0]) {
            mScrollHint = ViewCallback.HINT_SCROLL_DESC;
        } else if (mTmpRange[0] > mPrevRange[0]) {
            mScrollHint = ViewCallback.HINT_SCROLL_ASC;
        }

        mPrevRange[0] = mTmpRange[0];
        mPrevRange[1] = mTmpRange[1];

        mViewCallback.extendRangeInto(mTmpRange, mTmpRangeExtended, mScrollHint);
        mTmpRangeExtended[0] = Math.min(mTmpRange[0], Math.max(mTmpRangeExtended[0], 0));
        mTmpRangeExtended[1] =
                Math.max(mTmpRange[1], Math.min(mTmpRangeExtended[1], mItemCount - 1));

        mBackgroundProxy.updateRange(mTmpRange[0], mTmpRange[1],
                mTmpRangeExtended[0], mTmpRangeExtended[1], mScrollHint);
    }

    private final ThreadUtil.MainThreadCallback<T>
            mMainThreadCallback = new ThreadUtil.MainThreadCallback<T>() {
        @Override
        public void updateItemCount(int generation, int itemCount) {
            if (DEBUG) {
                log("updateItemCount: size=%d, gen #%d", itemCount, generation);
            }
            if (!isRequestedGeneration(generation)) {
                return;
            }
            mItemCount = itemCount;
            mViewCallback.onDataRefresh();
            mDisplayedGeneration = mRequestedGeneration;
            recycleAllTiles();

            mAllowScrollHints = false;  // Will be set to true after a first real scroll.
            // There will be no scroll event if the size change does not affect the current range.
            updateRange();
        }

        @Override
        public void addTile(int generation, TileList.Tile<T> tile) {
            if (!isRequestedGeneration(generation)) {
                if (DEBUG) {
                    log("recycling an older generation tile @%d", tile.mStartPosition);
                }
                mBackgroundProxy.recycleTile(tile);
                return;
            }
            TileList.Tile<T> duplicate = mTileList.addOrReplace(tile);
            if (duplicate != null) {
                Log.e(TAG, "duplicate tile @" + duplicate.mStartPosition);
                mBackgroundProxy.recycleTile(duplicate);
            }
            if (DEBUG) {
                log("gen #%d, added tile @%d, total tiles: %d",
                        generation, tile.mStartPosition, mTileList.size());
            }
            int endPosition = tile.mStartPosition + tile.mItemCount;
            int index = 0;
            while (index < mMissingPositions.size()) {
                final int position = mMissingPositions.keyAt(index);
                if (tile.mStartPosition <= position && position < endPosition) {
                    mMissingPositions.removeAt(index);
                    mViewCallback.onItemLoaded(position);
                } else {
                    index++;
                }
            }
        }

        @Override
        public void removeTile(int generation, int position) {
            if (!isRequestedGeneration(generation)) {
                return;
            }
            TileList.Tile<T> tile = mTileList.removeAtPos(position);
            if (tile == null) {
                Log.e(TAG, "tile not found @" + position);
                return;
            }
            if (DEBUG) {
                log("recycling tile @%d, total tiles: %d", tile.mStartPosition, mTileList.size());
            }
            mBackgroundProxy.recycleTile(tile);
        }

        private void recycleAllTiles() {
            if (DEBUG) {
                log("recycling all %d tiles", mTileList.size());
            }
            for (int i = 0; i < mTileList.size(); i++) {
                mBackgroundProxy.recycleTile(mTileList.getAtIndex(i));
            }
            mTileList.clear();
        }

        private boolean isRequestedGeneration(int generation) {
            return generation == mRequestedGeneration;
        }
    };

    private final ThreadUtil.BackgroundCallback<T>
            mBackgroundCallback = new ThreadUtil.BackgroundCallback<T>() {

        private TileList.Tile<T> mRecycledRoot;

        final SparseBooleanArray mLoadedTiles = new SparseBooleanArray();

        private int mGeneration;
        private int mItemCount;

        private int mFirstRequiredTileStart;
        private int mLastRequiredTileStart;

        @Override
        public void refresh(int generation) {
            mGeneration = generation;
            mLoadedTiles.clear();
            mItemCount = mDataCallback.refreshData();
            mMainThreadProxy.updateItemCount(mGeneration, mItemCount);
        }

        @Override
        public void updateRange(int rangeStart, int rangeEnd, int extRangeStart, int extRangeEnd,
                int scrollHint) {
            if (DEBUG) {
                log("updateRange: %d..%d extended to %d..%d, scroll hint: %d",
                        rangeStart, rangeEnd, extRangeStart, extRangeEnd, scrollHint);
            }

            if (rangeStart > rangeEnd) {
                return;
            }

            final int firstVisibleTileStart = getTileStart(rangeStart);
            final int lastVisibleTileStart = getTileStart(rangeEnd);

            mFirstRequiredTileStart = getTileStart(extRangeStart);
            mLastRequiredTileStart = getTileStart(extRangeEnd);
            if (DEBUG) {
                log("requesting tile range: %d..%d",
                        mFirstRequiredTileStart, mLastRequiredTileStart);
            }

            // All pending tile requests are removed by ThreadUtil at this point.
            // Re-request all required tiles in the most optimal order.
            if (scrollHint == ViewCallback.HINT_SCROLL_DESC) {
                requestTiles(mFirstRequiredTileStart, lastVisibleTileStart, scrollHint, true);
                requestTiles(lastVisibleTileStart + mTileSize, mLastRequiredTileStart, scrollHint,
                        false);
            } else {
                requestTiles(firstVisibleTileStart, mLastRequiredTileStart, scrollHint, false);
                requestTiles(mFirstRequiredTileStart, firstVisibleTileStart - mTileSize, scrollHint,
                        true);
            }
        }

        private int getTileStart(int position) {
            return position - position % mTileSize;
        }

        private void requestTiles(int firstTileStart, int lastTileStart, int scrollHint,
                                  boolean backwards) {
            for (int i = firstTileStart; i <= lastTileStart; i += mTileSize) {
                int tileStart = backwards ? (lastTileStart + firstTileStart - i) : i;
                if (DEBUG) {
                    log("requesting tile @%d", tileStart);
                }
                mBackgroundProxy.loadTile(tileStart, scrollHint);
            }
        }

        @Override
        public void loadTile(int position, int scrollHint) {
            if (isTileLoaded(position)) {
                if (DEBUG) {
                    log("already loaded tile @%d", position);
                }
                return;
            }
            TileList.Tile<T> tile = acquireTile();
            tile.mStartPosition = position;
            tile.mItemCount = Math.min(mTileSize, mItemCount - tile.mStartPosition);
            mDataCallback.fillData(tile.mItems, tile.mStartPosition, tile.mItemCount);
            flushTileCache(scrollHint);
            addTile(tile);
        }

        @Override
        public void recycleTile(TileList.Tile<T> tile) {
            if (DEBUG) {
                log("recycling tile @%d", tile.mStartPosition);
            }
            mDataCallback.recycleData(tile.mItems, tile.mItemCount);

            tile.mNext = mRecycledRoot;
            mRecycledRoot = tile;
        }

        private TileList.Tile<T> acquireTile() {
            if (mRecycledRoot != null) {
                TileList.Tile<T> result = mRecycledRoot;
                mRecycledRoot = mRecycledRoot.mNext;
                return result;
            }
            return new TileList.Tile<T>(mTClass, mTileSize);
        }

        private boolean isTileLoaded(int position) {
            return mLoadedTiles.get(position);
        }

        private void addTile(TileList.Tile<T> tile) {
            mLoadedTiles.put(tile.mStartPosition, true);
            mMainThreadProxy.addTile(mGeneration, tile);
            if (DEBUG) {
                log("loaded tile @%d, total tiles: %d", tile.mStartPosition, mLoadedTiles.size());
            }
        }

        private void removeTile(int position) {
            mLoadedTiles.delete(position);
            mMainThreadProxy.removeTile(mGeneration, position);
            if (DEBUG) {
                log("flushed tile @%d, total tiles: %s", position, mLoadedTiles.size());
            }
        }

        private void flushTileCache(int scrollHint) {
            final int cacheSizeLimit = mDataCallback.getMaxCachedTiles();
            while (mLoadedTiles.size() >= cacheSizeLimit) {
                int firstLoadedTileStart = mLoadedTiles.keyAt(0);
                int lastLoadedTileStart = mLoadedTiles.keyAt(mLoadedTiles.size() - 1);
                int startMargin = mFirstRequiredTileStart - firstLoadedTileStart;
                int endMargin = lastLoadedTileStart - mLastRequiredTileStart;
                if (startMargin > 0 && (startMargin >= endMargin ||
                        (scrollHint == ViewCallback.HINT_SCROLL_ASC))) {
                    removeTile(firstLoadedTileStart);
                } else if (endMargin > 0 && (startMargin < endMargin ||
                        (scrollHint == ViewCallback.HINT_SCROLL_DESC))){
                    removeTile(lastLoadedTileStart);
                } else {
                    // Could not flush on either side, bail out.
                    return;
                }
            }
        }

        private void log(String s, Object... args) {
            Log.d(TAG, "[BKGR] " + String.format(s, args));
        }
    };

    /**
     * The callback that provides data access for {@link AsyncListUtil}.
     *
     * <p>
     * All methods are called on the background thread.
     */
    public static abstract class DataCallback<T> {

        /**
         * Refresh the data set and return the new data item count.
         *
         * <p>
         * If the data is being accessed through {@link android.database.Cursor} this is where
         * the new cursor should be created.
         *
         * @return Data item count.
         */
        @WorkerThread
        public abstract int refreshData();

        /**
         * Fill the given tile.
         *
         * <p>
         * The provided tile might be a recycled tile, in which case it will already have objects.
         * It is suggested to re-use these objects if possible in your use case.
         *
         * @param startPosition The start position in the list.
         * @param itemCount The data item count.
         * @param data The data item array to fill into. Should not be accessed beyond
         *             <code>itemCount</code>.
         */
        @WorkerThread
        public abstract void fillData(T[] data, int startPosition, int itemCount);

        /**
         * Recycle the objects created in {@link #fillData} if necessary.
         *
         *
         * @param data Array of data items. Should not be accessed beyond <code>itemCount</code>.
         * @param itemCount The data item count.
         */
        @WorkerThread
        public void recycleData(T[] data, int itemCount) {
        }

        /**
         * Returns tile cache size limit (in tiles).
         *
         * <p>
         * The actual number of cached tiles will be the maximum of this value and the number of
         * tiles that is required to cover the range returned by
         * {@link ViewCallback#extendRangeInto(int[], int[], int)}.
         * <p>
         * For example, if this method returns 10, and the most
         * recent call to {@link ViewCallback#extendRangeInto(int[], int[], int)} returned
         * {100, 179}, and the tile size is 5, then the maximum number of cached tiles will be 16.
         * <p>
         * However, if the tile size is 20, then the maximum number of cached tiles will be 10.
         * <p>
         * The default implementation returns 10.
         *
         * @return Maximum cache size.
         */
        @WorkerThread
        public int getMaxCachedTiles() {
            return 10;
        }
    }

    /**
     * The callback that links {@link AsyncListUtil} with the list view.
     *
     * <p>
     * All methods are called on the main thread.
          */
    public static abstract class ViewCallback {

        /**
         * No scroll direction hint available.
         */
        public static final int HINT_SCROLL_NONE = 0;

        /**
         * Scrolling in descending order (from higher to lower positions in the order of the backing
         * storage).
         */
        public static final int HINT_SCROLL_DESC = 1;

        /**
         * Scrolling in ascending order (from lower to higher positions in the order of the backing
         * storage).
         */
        public static final int HINT_SCROLL_ASC = 2;

        /**
         * Compute the range of visible item positions.
         * <p>
         * outRange[0] is the position of the first visible item (in the order of the backing
         * storage).
         * <p>
         * outRange[1] is the position of the last visible item (in the order of the backing
         * storage).
         * <p>
         * Negative positions and positions greater or equal to {@link #getItemCount} are invalid.
         * If the returned range contains invalid positions it is ignored (no item will be loaded).
         *
         * @param outRange The visible item range.
         */
        @UiThread
        public abstract void getItemRangeInto(int[] outRange);

        /**
         * Compute a wider range of items that will be loaded for smoother scrolling.
         *
         * <p>
         * If there is no scroll hint, the default implementation extends the visible range by half
         * its length in both directions. If there is a scroll hint, the range is extended by
         * its full length in the scroll direction, and by half in the other direction.
         * <p>
         * For example, if <code>range</code> is <code>{100, 200}</code> and <code>scrollHint</code>
         * is {@link #HINT_SCROLL_ASC}, then <code>outRange</code> will be <code>{50, 300}</code>.
         * <p>
         * However, if <code>scrollHint</code> is {@link #HINT_SCROLL_NONE}, then
         * <code>outRange</code> will be <code>{50, 250}</code>
         *
         * @param range Visible item range.
         * @param outRange Extended range.
         * @param scrollHint The scroll direction hint.
         */
        @UiThread
        public void extendRangeInto(int[] range, int[] outRange, int scrollHint) {
            final int fullRange = range[1] - range[0] + 1;
            final int halfRange = fullRange / 2;
            outRange[0] = range[0] - (scrollHint == HINT_SCROLL_DESC ? fullRange : halfRange);
            outRange[1] = range[1] + (scrollHint == HINT_SCROLL_ASC ? fullRange : halfRange);
        }

        /**
         * Called when the entire data set has changed.
         */
        @UiThread
        public abstract void onDataRefresh();

        /**
         * Called when an item at the given position is loaded.
         * @param position Item position.
         */
        @UiThread
        public abstract void onItemLoaded(int position);
    }
}
