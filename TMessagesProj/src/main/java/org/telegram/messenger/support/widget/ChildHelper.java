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

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to manage children.
 * <p>
 * It wraps a RecyclerView and adds ability to hide some children. There are two sets of methods
 * provided by this class. <b>Regular</b> methods are the ones that replicate ViewGroup methods
 * like getChildAt, getChildCount etc. These methods ignore hidden children.
 * <p>
 * When RecyclerView needs direct access to the view group children, it can call unfiltered
 * methods like get getUnfilteredChildCount or getUnfilteredChildAt.
 */
class ChildHelper {

    private static final boolean DEBUG = false;

    private static final String TAG = "ChildrenHelper";

    final Callback mCallback;

    final Bucket mBucket;

    final List<View> mHiddenViews;

    ChildHelper(Callback callback) {
        mCallback = callback;
        mBucket = new Bucket();
        mHiddenViews = new ArrayList<View>();
    }

    /**
     * Marks a child view as hidden
     *
     * @param child  View to hide.
     */
    private void hideViewInternal(View child) {
        mHiddenViews.add(child);
        mCallback.onEnteredHiddenState(child);
    }

    /**
     * Unmarks a child view as hidden.
     *
     * @param child  View to hide.
     */
    private boolean unhideViewInternal(View child) {
        if (mHiddenViews.remove(child)) {
            mCallback.onLeftHiddenState(child);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a view to the ViewGroup
     *
     * @param child  View to add.
     * @param hidden If set to true, this item will be invisible from regular methods.
     */
    void addView(View child, boolean hidden) {
        addView(child, -1, hidden);
    }

    /**
     * Add a view to the ViewGroup at an index
     *
     * @param child  View to add.
     * @param index  Index of the child from the regular perspective (excluding hidden views).
     *               ChildHelper offsets this index to actual ViewGroup index.
     * @param hidden If set to true, this item will be invisible from regular methods.
     */
    void addView(View child, int index, boolean hidden) {
        final int offset;
        if (index < 0) {
            offset = mCallback.getChildCount();
        } else {
            offset = getOffset(index);
        }
        mBucket.insert(offset, hidden);
        if (hidden) {
            hideViewInternal(child);
        }
        mCallback.addView(child, offset);
        if (DEBUG) {
            Log.d(TAG, "addViewAt " + index + ",h:" + hidden + ", " + this);
        }
    }

    private int getOffset(int index) {
        if (index < 0) {
            return -1; //anything below 0 won't work as diff will be undefined.
        }
        final int limit = mCallback.getChildCount();
        int offset = index;
        while (offset < limit) {
            final int removedBefore = mBucket.countOnesBefore(offset);
            final int diff = index - (offset - removedBefore);
            if (diff == 0) {
                while (mBucket.get(offset)) { // ensure this offset is not hidden
                    offset ++;
                }
                return offset;
            } else {
                offset += diff;
            }
        }
        return -1;
    }

    /**
     * Removes the provided View from underlying RecyclerView.
     *
     * @param view The view to remove.
     */
    void removeView(View view) {
        int index = mCallback.indexOfChild(view);
        if (index < 0) {
            return;
        }
        if (mBucket.remove(index)) {
            unhideViewInternal(view);
        }
        mCallback.removeViewAt(index);
        if (DEBUG) {
            Log.d(TAG, "remove View off:" + index + "," + this);
        }
    }

    /**
     * Removes the view at the provided index from RecyclerView.
     *
     * @param index Index of the child from the regular perspective (excluding hidden views).
     *              ChildHelper offsets this index to actual ViewGroup index.
     */
    void removeViewAt(int index) {
        final int offset = getOffset(index);
        final View view = mCallback.getChildAt(offset);
        if (view == null) {
            return;
        }
        if (mBucket.remove(offset)) {
            unhideViewInternal(view);
        }
        mCallback.removeViewAt(offset);
        if (DEBUG) {
            Log.d(TAG, "removeViewAt " + index + ", off:" + offset + ", " + this);
        }
    }

    /**
     * Returns the child at provided index.
     *
     * @param index Index of the child to return in regular perspective.
     */
    View getChildAt(int index) {
        final int offset = getOffset(index);
        return mCallback.getChildAt(offset);
    }

    /**
     * Removes all views from the ViewGroup including the hidden ones.
     */
    void removeAllViewsUnfiltered() {
        mBucket.reset();
        for (int i = mHiddenViews.size() - 1; i >= 0; i--) {
            mCallback.onLeftHiddenState(mHiddenViews.get(i));
            mHiddenViews.remove(i);
        }
        mCallback.removeAllViews();
        if (DEBUG) {
            Log.d(TAG, "removeAllViewsUnfiltered");
        }
    }

    /**
     * This can be used to find a disappearing view by position.
     *
     * @param position The adapter position of the item.
     * @return         A hidden view with a valid ViewHolder that matches the position.
     */
    View findHiddenNonRemovedView(int position) {
        final int count = mHiddenViews.size();
        for (int i = 0; i < count; i++) {
            final View view = mHiddenViews.get(i);
            RecyclerView.ViewHolder holder = mCallback.getChildViewHolder(view);
            if (holder.getLayoutPosition() == position
                    && !holder.isInvalid()
                    && !holder.isRemoved()) {
                return view;
            }
        }
        return null;
    }

    /**
     * Attaches the provided view to the underlying ViewGroup.
     *
     * @param child        Child to attach.
     * @param index        Index of the child to attach in regular perspective.
     * @param layoutParams LayoutParams for the child.
     * @param hidden       If set to true, this item will be invisible to the regular methods.
     */
    void attachViewToParent(View child, int index, ViewGroup.LayoutParams layoutParams,
            boolean hidden) {
        final int offset;
        if (index < 0) {
            offset = mCallback.getChildCount();
        } else {
            offset = getOffset(index);
        }
        mBucket.insert(offset, hidden);
        if (hidden) {
            hideViewInternal(child);
        }
        mCallback.attachViewToParent(child, offset, layoutParams);
        if (DEBUG) {
            Log.d(TAG, "attach view to parent index:" + index + ",off:" + offset + "," +
                    "h:" + hidden + ", " + this);
        }
    }

    /**
     * Returns the number of children that are not hidden.
     *
     * @return Number of children that are not hidden.
     * @see #getChildAt(int)
     */
    int getChildCount() {
        return mCallback.getChildCount() - mHiddenViews.size();
    }

    /**
     * Returns the total number of children.
     *
     * @return The total number of children including the hidden views.
     * @see #getUnfilteredChildAt(int)
     */
    int getUnfilteredChildCount() {
        return mCallback.getChildCount();
    }

    /**
     * Returns a child by ViewGroup offset. ChildHelper won't offset this index.
     *
     * @param index ViewGroup index of the child to return.
     * @return The view in the provided index.
     */
    View getUnfilteredChildAt(int index) {
        return mCallback.getChildAt(index);
    }

    /**
     * Detaches the view at the provided index.
     *
     * @param index Index of the child to return in regular perspective.
     */
    void detachViewFromParent(int index) {
        final int offset = getOffset(index);
        mBucket.remove(offset);
        mCallback.detachViewFromParent(offset);
        if (DEBUG) {
            Log.d(TAG, "detach view from parent " + index + ", off:" + offset);
        }
    }

    /**
     * Returns the index of the child in regular perspective.
     *
     * @param child The child whose index will be returned.
     * @return The regular perspective index of the child or -1 if it does not exists.
     */
    int indexOfChild(View child) {
        final int index = mCallback.indexOfChild(child);
        if (index == -1) {
            return -1;
        }
        if (mBucket.get(index)) {
            if (DEBUG) {
                throw new IllegalArgumentException("cannot get index of a hidden child");
            } else {
                return -1;
            }
        }
        // reverse the index
        return index - mBucket.countOnesBefore(index);
    }

    /**
     * Returns whether a View is visible to LayoutManager or not.
     *
     * @param view The child view to check. Should be a child of the Callback.
     * @return True if the View is not visible to LayoutManager
     */
    boolean isHidden(View view) {
        return mHiddenViews.contains(view);
    }

    /**
     * Marks a child view as hidden.
     *
     * @param view The view to hide.
     */
    void hide(View view) {
        final int offset = mCallback.indexOfChild(view);
        if (offset < 0) {
            throw new IllegalArgumentException("view is not a child, cannot hide " + view);
        }
        if (DEBUG && mBucket.get(offset)) {
            throw new RuntimeException("trying to hide same view twice, how come ? " + view);
        }
        mBucket.set(offset);
        hideViewInternal(view);
        if (DEBUG) {
            Log.d(TAG, "hiding child " + view + " at offset " + offset+ ", " + this);
        }
    }

    /**
     * Moves a child view from hidden list to regular list.
     * Calling this method should probably be followed by a detach, otherwise, it will suddenly
     * show up in LayoutManager's children list.
     *
     * @param view The hidden View to unhide
     */
    void unhide(View view) {
        final int offset = mCallback.indexOfChild(view);
        if (offset < 0) {
            throw new IllegalArgumentException("view is not a child, cannot hide " + view);
        }
        if (!mBucket.get(offset)) {
            throw new RuntimeException("trying to unhide a view that was not hidden" + view);
        }
        mBucket.clear(offset);
        unhideViewInternal(view);
    }

    @Override
    public String toString() {
        return mBucket.toString() + ", hidden list:" + mHiddenViews.size();
    }

    /**
     * Removes a view from the ViewGroup if it is hidden.
     *
     * @param view The view to remove.
     * @return True if the View is found and it is hidden. False otherwise.
     */
    boolean removeViewIfHidden(View view) {
        final int index = mCallback.indexOfChild(view);
        if (index == -1) {
            if (unhideViewInternal(view) && DEBUG) {
                throw new IllegalStateException("view is in hidden list but not in view group");
            }
            return true;
        }
        if (mBucket.get(index)) {
            mBucket.remove(index);
            if (!unhideViewInternal(view) && DEBUG) {
                throw new IllegalStateException(
                        "removed a hidden view but it is not in hidden views list");
            }
            mCallback.removeViewAt(index);
            return true;
        }
        return false;
    }

    /**
     * Bitset implementation that provides methods to offset indices.
     */
    static class Bucket {

        final static int BITS_PER_WORD = Long.SIZE;

        final static long LAST_BIT = 1L << (Long.SIZE - 1);

        long mData = 0;

        Bucket next;

        void set(int index) {
            if (index >= BITS_PER_WORD) {
                ensureNext();
                next.set(index - BITS_PER_WORD);
            } else {
                mData |= 1L << index;
            }
        }

        private void ensureNext() {
            if (next == null) {
                next = new Bucket();
            }
        }

        void clear(int index) {
            if (index >= BITS_PER_WORD) {
                if (next != null) {
                    next.clear(index - BITS_PER_WORD);
                }
            } else {
                mData &= ~(1L << index);
            }

        }

        boolean get(int index) {
            if (index >= BITS_PER_WORD) {
                ensureNext();
                return next.get(index - BITS_PER_WORD);
            } else {
                return (mData & (1L << index)) != 0;
            }
        }

        void reset() {
            mData = 0;
            if (next != null) {
                next.reset();
            }
        }

        void insert(int index, boolean value) {
            if (index >= BITS_PER_WORD) {
                ensureNext();
                next.insert(index - BITS_PER_WORD, value);
            } else {
                final boolean lastBit = (mData & LAST_BIT) != 0;
                long mask = (1L << index) - 1;
                final long before = mData & mask;
                final long after = ((mData & ~mask)) << 1;
                mData = before | after;
                if (value) {
                    set(index);
                } else {
                    clear(index);
                }
                if (lastBit || next != null) {
                    ensureNext();
                    next.insert(0, lastBit);
                }
            }
        }

        boolean remove(int index) {
            if (index >= BITS_PER_WORD) {
                ensureNext();
                return next.remove(index - BITS_PER_WORD);
            } else {
                long mask = (1L << index);
                final boolean value = (mData & mask) != 0;
                mData &= ~mask;
                mask = mask - 1;
                final long before = mData & mask;
                // cannot use >> because it adds one.
                final long after = Long.rotateRight(mData & ~mask, 1);
                mData = before | after;
                if (next != null) {
                    if (next.get(0)) {
                        set(BITS_PER_WORD - 1);
                    }
                    next.remove(0);
                }
                return value;
            }
        }

        int countOnesBefore(int index) {
            if (next == null) {
                if (index >= BITS_PER_WORD) {
                    return Long.bitCount(mData);
                }
                return Long.bitCount(mData & ((1L << index) - 1));
            }
            if (index < BITS_PER_WORD) {
                return Long.bitCount(mData & ((1L << index) - 1));
            } else {
                return next.countOnesBefore(index - BITS_PER_WORD) + Long.bitCount(mData);
            }
        }

        @Override
        public String toString() {
            return next == null ? Long.toBinaryString(mData)
                    : next.toString() + "xx" + Long.toBinaryString(mData);
        }
    }

    static interface Callback {

        int getChildCount();

        void addView(View child, int index);

        int indexOfChild(View view);

        void removeViewAt(int index);

        View getChildAt(int offset);

        void removeAllViews();

        RecyclerView.ViewHolder getChildViewHolder(View view);

        void attachViewToParent(View child, int index, ViewGroup.LayoutParams layoutParams);

        void detachViewFromParent(int offset);

        void onEnteredHiddenState(View child);

        void onLeftHiddenState(View child);
    }
}
