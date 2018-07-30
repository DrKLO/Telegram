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

package org.telegram.messenger.support.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

/**
 * A Sorted list implementation that can keep items in order and also notify for changes in the
 * list
 * such that it can be bound to a {@link android.support.v7.widget.RecyclerView.Adapter
 * RecyclerView.Adapter}.
 * <p>
 * It keeps items ordered using the {@link Callback#compare(Object, Object)} method and uses
 * binary search to retrieve items. If the sorting criteria of your items may change, make sure you
 * call appropriate methods while editing them to avoid data inconsistencies.
 * <p>
 * You can control the order of items and change notifications via the {@link Callback} parameter.
 */
@SuppressWarnings("unchecked")
public class SortedList<T> {

    /**
     * Used by {@link #indexOf(Object)} when he item cannot be found in the list.
     */
    public static final int INVALID_POSITION = -1;

    private static final int MIN_CAPACITY = 10;
    private static final int CAPACITY_GROWTH = MIN_CAPACITY;
    private static final int INSERTION = 1;
    private static final int DELETION = 1 << 1;
    private static final int LOOKUP = 1 << 2;
    T[] mData;

    /**
     * A reference to the previous set of data that is kept during a mutation operation (addAll or
     * replaceAll).
     */
    private T[] mOldData;

    /**
     * The current index into mOldData that has not yet been processed during a mutation operation
     * (addAll or replaceAll).
     */
    private int mOldDataStart;
    private int mOldDataSize;

    /**
     * The current index into the new data that has not yet been processed during a mutation
     * operation (addAll or replaceAll).
     */
    private int mNewDataStart;

    /**
     * The callback instance that controls the behavior of the SortedList and get notified when
     * changes happen.
     */
    private Callback mCallback;

    private BatchedCallback mBatchedCallback;

    private int mSize;
    private final Class<T> mTClass;

    /**
     * Creates a new SortedList of type T.
     *
     * @param klass    The class of the contents of the SortedList.
     * @param callback The callback that controls the behavior of SortedList.
     */
    public SortedList(Class<T> klass, Callback<T> callback) {
        this(klass, callback, MIN_CAPACITY);
    }

    /**
     * Creates a new SortedList of type T.
     *
     * @param klass           The class of the contents of the SortedList.
     * @param callback        The callback that controls the behavior of SortedList.
     * @param initialCapacity The initial capacity to hold items.
     */
    public SortedList(Class<T> klass, Callback<T> callback, int initialCapacity) {
        mTClass = klass;
        mData = (T[]) Array.newInstance(klass, initialCapacity);
        mCallback = callback;
        mSize = 0;
    }

    /**
     * The number of items in the list.
     *
     * @return The number of items in the list.
     */
    public int size() {
        return mSize;
    }

    /**
     * Adds the given item to the list. If this is a new item, SortedList calls
     * {@link Callback#onInserted(int, int)}.
     * <p>
     * If the item already exists in the list and its sorting criteria is not changed, it is
     * replaced with the existing Item. SortedList uses
     * {@link Callback#areItemsTheSame(Object, Object)} to check if two items are the same item
     * and uses {@link Callback#areContentsTheSame(Object, Object)} to decide whether it should
     * call {@link Callback#onChanged(int, int)} or not. In both cases, it always removes the
     * reference to the old item and puts the new item into the backing array even if
     * {@link Callback#areContentsTheSame(Object, Object)} returns false.
     * <p>
     * If the sorting criteria of the item is changed, SortedList won't be able to find
     * its duplicate in the list which will result in having a duplicate of the Item in the list.
     * If you need to update sorting criteria of an item that already exists in the list,
     * use {@link #updateItemAt(int, Object)}. You can find the index of the item using
     * {@link #indexOf(Object)} before you update the object.
     *
     * @param item The item to be added into the list.
     *
     * @return The index of the newly added item.
     * @see Callback#compare(Object, Object)
     * @see Callback#areItemsTheSame(Object, Object)
     * @see Callback#areContentsTheSame(Object, Object)}
     */
    public int add(T item) {
        throwIfInMutationOperation();
        return add(item, true);
    }

    /**
     * Adds the given items to the list. Equivalent to calling {@link SortedList#add} in a loop,
     * except the callback events may be in a different order/granularity since addAll can batch
     * them for better performance.
     * <p>
     * If allowed, will reference the input array during, and possibly after, the operation to avoid
     * extra memory allocation, in which case you should not continue to reference or modify the
     * array yourself.
     * <p>
     * @param items Array of items to be added into the list.
     * @param mayModifyInput If true, SortedList is allowed to modify and permanently reference the
     *                       input array.
     * @see SortedList#addAll(T[] items)
     */
    public void addAll(T[] items, boolean mayModifyInput) {
        throwIfInMutationOperation();
        if (items.length == 0) {
            return;
        }

        if (mayModifyInput) {
            addAllInternal(items);
        } else {
            addAllInternal(copyArray(items));
        }
    }

    /**
     * Adds the given items to the list. Does not modify or retain the input.
     *
     * @see SortedList#addAll(T[] items, boolean mayModifyInput)
     *
     * @param items Array of items to be added into the list.
     */
    public void addAll(T... items) {
        addAll(items, false);
    }

    /**
     * Adds the given items to the list. Does not modify or retain the input.
     *
     * @see SortedList#addAll(T[] items, boolean mayModifyInput)
     *
     * @param items Collection of items to be added into the list.
     */
    public void addAll(Collection<T> items) {
        T[] copy = (T[]) Array.newInstance(mTClass, items.size());
        addAll(items.toArray(copy), true);
    }

    /**
     * Replaces the current items with the new items, dispatching {@link ListUpdateCallback} events
     * for each change detected as appropriate.
     * <p>
     * If allowed, will reference the input array during, and possibly after, the operation to avoid
     * extra memory allocation, in which case you should not continue to reference or modify the
     * array yourself.
     * <p>
     * Note: this method does not detect moves or dispatch
     * {@link ListUpdateCallback#onMoved(int, int)} events. It instead treats moves as a remove
     * followed by an add and therefore dispatches {@link ListUpdateCallback#onRemoved(int, int)}
     * and {@link ListUpdateCallback#onRemoved(int, int)} events.  See {@link DiffUtil} if you want
     * your implementation to dispatch move events.
     * <p>
     * @param items Array of items to replace current items.
     * @param mayModifyInput If true, SortedList is allowed to modify and permanently reference the
     *                       input array.
     * @see #replaceAll(T[])
     */
    public void replaceAll(@NonNull T[] items, boolean mayModifyInput) {
        throwIfInMutationOperation();

        if (mayModifyInput) {
            replaceAllInternal(items);
        } else {
            replaceAllInternal(copyArray(items));
        }
    }

    /**
     * Replaces the current items with the new items, dispatching {@link ListUpdateCallback} events
     * for each change detected as appropriate.  Does not modify or retain the input.
     *
     * @see #replaceAll(T[], boolean)
     *
     * @param items Array of items to replace current items.
     */
    public void replaceAll(@NonNull T... items) {
        replaceAll(items, false);
    }

    /**
     * Replaces the current items with the new items, dispatching {@link ListUpdateCallback} events
     * for each change detected as appropriate. Does not modify or retain the input.
     *
     * @see #replaceAll(T[], boolean)
     *
     * @param items Array of items to replace current items.
     */
    public void replaceAll(@NonNull Collection<T> items) {
        T[] copy = (T[]) Array.newInstance(mTClass, items.size());
        replaceAll(items.toArray(copy), true);
    }

    private void addAllInternal(T[] newItems) {
        if (newItems.length < 1) {
            return;
        }

        final int newSize = sortAndDedup(newItems);

        if (mSize == 0) {
            mData = newItems;
            mSize = newSize;
            mCallback.onInserted(0, newSize);
        } else {
            merge(newItems, newSize);
        }
    }

    private void replaceAllInternal(@NonNull T[] newData) {
        final boolean forceBatchedUpdates = !(mCallback instanceof BatchedCallback);
        if (forceBatchedUpdates) {
            beginBatchedUpdates();
        }

        mOldDataStart = 0;
        mOldDataSize = mSize;
        mOldData = mData;

        mNewDataStart = 0;
        int newSize = sortAndDedup(newData);
        mData = (T[]) Array.newInstance(mTClass, newSize);

        while (mNewDataStart < newSize || mOldDataStart < mOldDataSize) {
            if (mOldDataStart >= mOldDataSize) {
                int insertIndex = mNewDataStart;
                int itemCount = newSize - mNewDataStart;
                System.arraycopy(newData, insertIndex, mData, insertIndex, itemCount);
                mNewDataStart += itemCount;
                mSize += itemCount;
                mCallback.onInserted(insertIndex, itemCount);
                break;
            }
            if (mNewDataStart >= newSize) {
                int itemCount = mOldDataSize - mOldDataStart;
                mSize -= itemCount;
                mCallback.onRemoved(mNewDataStart, itemCount);
                break;
            }

            T oldItem = mOldData[mOldDataStart];
            T newItem = newData[mNewDataStart];

            int result = mCallback.compare(oldItem, newItem);
            if (result < 0) {
                replaceAllRemove();
            } else if (result > 0) {
                replaceAllInsert(newItem);
            } else {
                if (!mCallback.areItemsTheSame(oldItem, newItem)) {
                    // The items aren't the same even though they were supposed to occupy the same
                    // place, so both notify to remove and add an item in the current location.
                    replaceAllRemove();
                    replaceAllInsert(newItem);
                } else {
                    mData[mNewDataStart] = newItem;
                    mOldDataStart++;
                    mNewDataStart++;
                    if (!mCallback.areContentsTheSame(oldItem, newItem)) {
                        // The item is the same but the contents have changed, so notify that an
                        // onChanged event has occurred.
                        mCallback.onChanged(mNewDataStart - 1, 1,
                                mCallback.getChangePayload(oldItem, newItem));
                    }
                }
            }
        }

        mOldData = null;

        if (forceBatchedUpdates) {
            endBatchedUpdates();
        }
    }

    private void replaceAllInsert(T newItem) {
        mData[mNewDataStart] = newItem;
        mNewDataStart++;
        mSize++;
        mCallback.onInserted(mNewDataStart - 1, 1);
    }

    private void replaceAllRemove() {
        mSize--;
        mOldDataStart++;
        mCallback.onRemoved(mNewDataStart, 1);
    }

    /**
     * Sorts and removes duplicate items, leaving only the last item from each group of "same"
     * items. Move the remaining items to the beginning of the array.
     *
     * @return Number of deduplicated items at the beginning of the array.
     */
    private int sortAndDedup(@NonNull T[] items) {
        if (items.length == 0) {
            return 0;
        }

        // Arrays.sort is stable.
        Arrays.sort(items, mCallback);

        // Keep track of the range of equal items at the end of the output.
        // Start with the range containing just the first item.
        int rangeStart = 0;
        int rangeEnd = 1;

        for (int i = 1; i < items.length; ++i) {
            T currentItem = items[i];

            int compare = mCallback.compare(items[rangeStart], currentItem);

            if (compare == 0) {
                // The range of equal items continues, update it.
                final int sameItemPos = findSameItem(currentItem, items, rangeStart, rangeEnd);
                if (sameItemPos != INVALID_POSITION) {
                    // Replace the duplicate item.
                    items[sameItemPos] = currentItem;
                } else {
                    // Expand the range.
                    if (rangeEnd != i) {  // Avoid redundant copy.
                        items[rangeEnd] = currentItem;
                    }
                    rangeEnd++;
                }
            } else {
                // The range has ended. Reset it to contain just the current item.
                if (rangeEnd != i) {  // Avoid redundant copy.
                    items[rangeEnd] = currentItem;
                }
                rangeStart = rangeEnd++;
            }
        }
        return rangeEnd;
    }


    private int findSameItem(T item, T[] items, int from, int to) {
        for (int pos = from; pos < to; pos++) {
            if (mCallback.areItemsTheSame(items[pos], item)) {
                return pos;
            }
        }
        return INVALID_POSITION;
    }

    /**
     * This method assumes that newItems are sorted and deduplicated.
     */
    private void merge(T[] newData, int newDataSize) {
        final boolean forceBatchedUpdates = !(mCallback instanceof BatchedCallback);
        if (forceBatchedUpdates) {
            beginBatchedUpdates();
        }

        mOldData = mData;
        mOldDataStart = 0;
        mOldDataSize = mSize;

        final int mergedCapacity = mSize + newDataSize + CAPACITY_GROWTH;
        mData = (T[]) Array.newInstance(mTClass, mergedCapacity);
        mNewDataStart = 0;

        int newDataStart = 0;
        while (mOldDataStart < mOldDataSize || newDataStart < newDataSize) {
            if (mOldDataStart == mOldDataSize) {
                // No more old items, copy the remaining new items.
                int itemCount = newDataSize - newDataStart;
                System.arraycopy(newData, newDataStart, mData, mNewDataStart, itemCount);
                mNewDataStart += itemCount;
                mSize += itemCount;
                mCallback.onInserted(mNewDataStart - itemCount, itemCount);
                break;
            }

            if (newDataStart == newDataSize) {
                // No more new items, copy the remaining old items.
                int itemCount = mOldDataSize - mOldDataStart;
                System.arraycopy(mOldData, mOldDataStart, mData, mNewDataStart, itemCount);
                mNewDataStart += itemCount;
                break;
            }

            T oldItem = mOldData[mOldDataStart];
            T newItem = newData[newDataStart];
            int compare = mCallback.compare(oldItem, newItem);
            if (compare > 0) {
                // New item is lower, output it.
                mData[mNewDataStart++] = newItem;
                mSize++;
                newDataStart++;
                mCallback.onInserted(mNewDataStart - 1, 1);
            } else if (compare == 0 && mCallback.areItemsTheSame(oldItem, newItem)) {
                // Items are the same. Output the new item, but consume both.
                mData[mNewDataStart++] = newItem;
                newDataStart++;
                mOldDataStart++;
                if (!mCallback.areContentsTheSame(oldItem, newItem)) {
                    mCallback.onChanged(mNewDataStart - 1, 1,
                            mCallback.getChangePayload(oldItem, newItem));
                }
            } else {
                // Old item is lower than or equal to (but not the same as the new). Output it.
                // New item with the same sort order will be inserted later.
                mData[mNewDataStart++] = oldItem;
                mOldDataStart++;
            }
        }

        mOldData = null;

        if (forceBatchedUpdates) {
            endBatchedUpdates();
        }
    }

    /**
     * Throws an exception if called while we are in the middle of a mutation operation (addAll or
     * replaceAll).
     */
    private void throwIfInMutationOperation() {
        if (mOldData != null) {
            throw new IllegalStateException("Data cannot be mutated in the middle of a batch "
                    + "update operation such as addAll or replaceAll.");
        }
    }

    /**
     * Batches adapter updates that happen after calling this method and before calling
     * {@link #endBatchedUpdates()}. For example, if you add multiple items in a loop
     * and they are placed into consecutive indices, SortedList calls
     * {@link Callback#onInserted(int, int)} only once with the proper item count. If an event
     * cannot be merged with the previous event, the previous event is dispatched
     * to the callback instantly.
     * <p>
     * After running your data updates, you <b>must</b> call {@link #endBatchedUpdates()}
     * which will dispatch any deferred data change event to the current callback.
     * <p>
     * A sample implementation may look like this:
     * <pre>
     *     mSortedList.beginBatchedUpdates();
     *     try {
     *         mSortedList.add(item1)
     *         mSortedList.add(item2)
     *         mSortedList.remove(item3)
     *         ...
     *     } finally {
     *         mSortedList.endBatchedUpdates();
     *     }
     * </pre>
     * <p>
     * Instead of using this method to batch calls, you can use a Callback that extends
     * {@link BatchedCallback}. In that case, you must make sure that you are manually calling
     * {@link BatchedCallback#dispatchLastEvent()} right after you complete your data changes.
     * Failing to do so may create data inconsistencies with the Callback.
     * <p>
     * If the current Callback is an instance of {@link BatchedCallback}, calling this method
     * has no effect.
     */
    public void beginBatchedUpdates() {
        throwIfInMutationOperation();
        if (mCallback instanceof BatchedCallback) {
            return;
        }
        if (mBatchedCallback == null) {
            mBatchedCallback = new BatchedCallback(mCallback);
        }
        mCallback = mBatchedCallback;
    }

    /**
     * Ends the update transaction and dispatches any remaining event to the callback.
     */
    public void endBatchedUpdates() {
        throwIfInMutationOperation();
        if (mCallback instanceof BatchedCallback) {
            ((BatchedCallback) mCallback).dispatchLastEvent();
        }
        if (mCallback == mBatchedCallback) {
            mCallback = mBatchedCallback.mWrappedCallback;
        }
    }

    private int add(T item, boolean notify) {
        int index = findIndexOf(item, mData, 0, mSize, INSERTION);
        if (index == INVALID_POSITION) {
            index = 0;
        } else if (index < mSize) {
            T existing = mData[index];
            if (mCallback.areItemsTheSame(existing, item)) {
                if (mCallback.areContentsTheSame(existing, item)) {
                    //no change but still replace the item
                    mData[index] = item;
                    return index;
                } else {
                    mData[index] = item;
                    mCallback.onChanged(index, 1, mCallback.getChangePayload(existing, item));
                    return index;
                }
            }
        }
        addToData(index, item);
        if (notify) {
            mCallback.onInserted(index, 1);
        }
        return index;
    }

    /**
     * Removes the provided item from the list and calls {@link Callback#onRemoved(int, int)}.
     *
     * @param item The item to be removed from the list.
     *
     * @return True if item is removed, false if item cannot be found in the list.
     */
    public boolean remove(T item) {
        throwIfInMutationOperation();
        return remove(item, true);
    }

    /**
     * Removes the item at the given index and calls {@link Callback#onRemoved(int, int)}.
     *
     * @param index The index of the item to be removed.
     *
     * @return The removed item.
     */
    public T removeItemAt(int index) {
        throwIfInMutationOperation();
        T item = get(index);
        removeItemAtIndex(index, true);
        return item;
    }

    private boolean remove(T item, boolean notify) {
        int index = findIndexOf(item, mData, 0, mSize, DELETION);
        if (index == INVALID_POSITION) {
            return false;
        }
        removeItemAtIndex(index, notify);
        return true;
    }

    private void removeItemAtIndex(int index, boolean notify) {
        System.arraycopy(mData, index + 1, mData, index, mSize - index - 1);
        mSize--;
        mData[mSize] = null;
        if (notify) {
            mCallback.onRemoved(index, 1);
        }
    }

    /**
     * Updates the item at the given index and calls {@link Callback#onChanged(int, int)} and/or
     * {@link Callback#onMoved(int, int)} if necessary.
     * <p>
     * You can use this method if you need to change an existing Item such that its position in the
     * list may change.
     * <p>
     * If the new object is a different object (<code>get(index) != item</code>) and
     * {@link Callback#areContentsTheSame(Object, Object)} returns <code>true</code>, SortedList
     * avoids calling {@link Callback#onChanged(int, int)} otherwise it calls
     * {@link Callback#onChanged(int, int)}.
     * <p>
     * If the new position of the item is different than the provided <code>index</code>,
     * SortedList
     * calls {@link Callback#onMoved(int, int)}.
     *
     * @param index The index of the item to replace
     * @param item  The item to replace the item at the given Index.
     * @see #add(Object)
     */
    public void updateItemAt(int index, T item) {
        throwIfInMutationOperation();
        final T existing = get(index);
        // assume changed if the same object is given back
        boolean contentsChanged = existing == item || !mCallback.areContentsTheSame(existing, item);
        if (existing != item) {
            // different items, we can use comparison and may avoid lookup
            final int cmp = mCallback.compare(existing, item);
            if (cmp == 0) {
                mData[index] = item;
                if (contentsChanged) {
                    mCallback.onChanged(index, 1, mCallback.getChangePayload(existing, item));
                }
                return;
            }
        }
        if (contentsChanged) {
            mCallback.onChanged(index, 1, mCallback.getChangePayload(existing, item));
        }
        // TODO this done in 1 pass to avoid shifting twice.
        removeItemAtIndex(index, false);
        int newIndex = add(item, false);
        if (index != newIndex) {
            mCallback.onMoved(index, newIndex);
        }
    }

    /**
     * This method can be used to recalculate the position of the item at the given index, without
     * triggering an {@link Callback#onChanged(int, int)} callback.
     * <p>
     * If you are editing objects in the list such that their position in the list may change but
     * you don't want to trigger an onChange animation, you can use this method to re-position it.
     * If the item changes position, SortedList will call {@link Callback#onMoved(int, int)}
     * without
     * calling {@link Callback#onChanged(int, int)}.
     * <p>
     * A sample usage may look like:
     *
     * <pre>
     *     final int position = mSortedList.indexOf(item);
     *     item.incrementPriority(); // assume items are sorted by priority
     *     mSortedList.recalculatePositionOfItemAt(position);
     * </pre>
     * In the example above, because the sorting criteria of the item has been changed,
     * mSortedList.indexOf(item) will not be able to find the item. This is why the code above
     * first
     * gets the position before editing the item, edits it and informs the SortedList that item
     * should be repositioned.
     *
     * @param index The current index of the Item whose position should be re-calculated.
     * @see #updateItemAt(int, Object)
     * @see #add(Object)
     */
    public void recalculatePositionOfItemAt(int index) {
        throwIfInMutationOperation();
        // TODO can be improved
        final T item = get(index);
        removeItemAtIndex(index, false);
        int newIndex = add(item, false);
        if (index != newIndex) {
            mCallback.onMoved(index, newIndex);
        }
    }

    /**
     * Returns the item at the given index.
     *
     * @param index The index of the item to retrieve.
     *
     * @return The item at the given index.
     * @throws java.lang.IndexOutOfBoundsException if provided index is negative or larger than the
     *                                             size of the list.
     */
    public T get(int index) throws IndexOutOfBoundsException {
        if (index >= mSize || index < 0) {
            throw new IndexOutOfBoundsException("Asked to get item at " + index + " but size is "
                    + mSize);
        }
        if (mOldData != null) {
            // The call is made from a callback during addAll execution. The data is split
            // between mData and mOldData.
            if (index >= mNewDataStart) {
                return mOldData[index - mNewDataStart + mOldDataStart];
            }
        }
        return mData[index];
    }

    /**
     * Returns the position of the provided item.
     *
     * @param item The item to query for position.
     *
     * @return The position of the provided item or {@link #INVALID_POSITION} if item is not in the
     * list.
     */
    public int indexOf(T item) {
        if (mOldData != null) {
            int index = findIndexOf(item, mData, 0, mNewDataStart, LOOKUP);
            if (index != INVALID_POSITION) {
                return index;
            }
            index = findIndexOf(item, mOldData, mOldDataStart, mOldDataSize, LOOKUP);
            if (index != INVALID_POSITION) {
                return index - mOldDataStart + mNewDataStart;
            }
            return INVALID_POSITION;
        }
        return findIndexOf(item, mData, 0, mSize, LOOKUP);
    }

    private int findIndexOf(T item, T[] mData, int left, int right, int reason) {
        while (left < right) {
            final int middle = (left + right) / 2;
            T myItem = mData[middle];
            final int cmp = mCallback.compare(myItem, item);
            if (cmp < 0) {
                left = middle + 1;
            } else if (cmp == 0) {
                if (mCallback.areItemsTheSame(myItem, item)) {
                    return middle;
                } else {
                    int exact = linearEqualitySearch(item, middle, left, right);
                    if (reason == INSERTION) {
                        return exact == INVALID_POSITION ? middle : exact;
                    } else {
                        return exact;
                    }
                }
            } else {
                right = middle;
            }
        }
        return reason == INSERTION ? left : INVALID_POSITION;
    }

    private int linearEqualitySearch(T item, int middle, int left, int right) {
        // go left
        for (int next = middle - 1; next >= left; next--) {
            T nextItem = mData[next];
            int cmp = mCallback.compare(nextItem, item);
            if (cmp != 0) {
                break;
            }
            if (mCallback.areItemsTheSame(nextItem, item)) {
                return next;
            }
        }
        for (int next = middle + 1; next < right; next++) {
            T nextItem = mData[next];
            int cmp = mCallback.compare(nextItem, item);
            if (cmp != 0) {
                break;
            }
            if (mCallback.areItemsTheSame(nextItem, item)) {
                return next;
            }
        }
        return INVALID_POSITION;
    }

    private void addToData(int index, T item) {
        if (index > mSize) {
            throw new IndexOutOfBoundsException(
                    "cannot add item to " + index + " because size is " + mSize);
        }
        if (mSize == mData.length) {
            // we are at the limit enlarge
            T[] newData = (T[]) Array.newInstance(mTClass, mData.length + CAPACITY_GROWTH);
            System.arraycopy(mData, 0, newData, 0, index);
            newData[index] = item;
            System.arraycopy(mData, index, newData, index + 1, mSize - index);
            mData = newData;
        } else {
            // just shift, we fit
            System.arraycopy(mData, index, mData, index + 1, mSize - index);
            mData[index] = item;
        }
        mSize++;
    }

    private T[] copyArray(T[] items) {
        T[] copy = (T[]) Array.newInstance(mTClass, items.length);
        System.arraycopy(items, 0, copy, 0, items.length);
        return copy;
    }

    /**
     * Removes all items from the SortedList.
     */
    public void clear() {
        throwIfInMutationOperation();
        if (mSize == 0) {
            return;
        }
        final int prevSize = mSize;
        Arrays.fill(mData, 0, prevSize, null);
        mSize = 0;
        mCallback.onRemoved(0, prevSize);
    }

    /**
     * The class that controls the behavior of the {@link SortedList}.
     * <p>
     * It defines how items should be sorted and how duplicates should be handled.
     * <p>
     * SortedList calls the callback methods on this class to notify changes about the underlying
     * data.
     */
    public static abstract class Callback<T2> implements Comparator<T2>, ListUpdateCallback {

        /**
         * Similar to {@link java.util.Comparator#compare(Object, Object)}, should compare two and
         * return how they should be ordered.
         *
         * @param o1 The first object to compare.
         * @param o2 The second object to compare.
         *
         * @return a negative integer, zero, or a positive integer as the
         * first argument is less than, equal to, or greater than the
         * second.
         */
        @Override
        abstract public int compare(T2 o1, T2 o2);

        /**
         * Called by the SortedList when the item at the given position is updated.
         *
         * @param position The position of the item which has been updated.
         * @param count    The number of items which has changed.
         */
        abstract public void onChanged(int position, int count);

        @Override
        public void onChanged(int position, int count, Object payload) {
            onChanged(position, count);
        }

        /**
         * Called by the SortedList when it wants to check whether two items have the same data
         * or not. SortedList uses this information to decide whether it should call
         * {@link #onChanged(int, int)} or not.
         * <p>
         * SortedList uses this method to check equality instead of {@link Object#equals(Object)}
         * so
         * that you can change its behavior depending on your UI.
         * <p>
         * For example, if you are using SortedList with a
         * {@link android.support.v7.widget.RecyclerView.Adapter RecyclerView.Adapter}, you should
         * return whether the items' visual representations are the same or not.
         *
         * @param oldItem The previous representation of the object.
         * @param newItem The new object that replaces the previous one.
         *
         * @return True if the contents of the items are the same or false if they are different.
         */
        abstract public boolean areContentsTheSame(T2 oldItem, T2 newItem);

        /**
         * Called by the SortedList to decide whether two objects represent the same Item or not.
         * <p>
         * For example, if your items have unique ids, this method should check their equality.
         *
         * @param item1 The first item to check.
         * @param item2 The second item to check.
         *
         * @return True if the two items represent the same object or false if they are different.
         */
        abstract public boolean areItemsTheSame(T2 item1, T2 item2);

        /**
         * When {@link #areItemsTheSame(T2, T2)} returns {@code true} for two items and
         * {@link #areContentsTheSame(T2, T2)} returns false for them, {@link Callback} calls this
         * method to get a payload about the change.
         * <p>
         * For example, if you are using {@link Callback} with
         * {@link android.support.v7.widget.RecyclerView}, you can return the particular field that
         * changed in the item and your
         * {@link android.support.v7.widget.RecyclerView.ItemAnimator ItemAnimator} can use that
         * information to run the correct animation.
         * <p>
         * Default implementation returns {@code null}.
         *
         * @param item1 The first item to check.
         * @param item2 The second item to check.
         * @return A payload object that represents the changes between the two items.
         */
        @Nullable
        public Object getChangePayload(T2 item1, T2 item2) {
            return null;
        }
    }

    /**
     * A callback implementation that can batch notify events dispatched by the SortedList.
     * <p>
     * This class can be useful if you want to do multiple operations on a SortedList but don't
     * want to dispatch each event one by one, which may result in a performance issue.
     * <p>
     * For example, if you are going to add multiple items to a SortedList, BatchedCallback call
     * convert individual <code>onInserted(index, 1)</code> calls into one
     * <code>onInserted(index, N)</code> if items are added into consecutive indices. This change
     * can help RecyclerView resolve changes much more easily.
     * <p>
     * If consecutive changes in the SortedList are not suitable for batching, BatchingCallback
     * dispatches them as soon as such case is detected. After your edits on the SortedList is
     * complete, you <b>must</b> always call {@link BatchedCallback#dispatchLastEvent()} to flush
     * all changes to the Callback.
     */
    public static class BatchedCallback<T2> extends Callback<T2> {

        final Callback<T2> mWrappedCallback;
        private final BatchingListUpdateCallback mBatchingListUpdateCallback;
        /**
         * Creates a new BatchedCallback that wraps the provided Callback.
         *
         * @param wrappedCallback The Callback which should received the data change callbacks.
         *                        Other method calls (e.g. {@link #compare(Object, Object)} from
         *                        the SortedList are directly forwarded to this Callback.
         */
        public BatchedCallback(Callback<T2> wrappedCallback) {
            mWrappedCallback = wrappedCallback;
            mBatchingListUpdateCallback = new BatchingListUpdateCallback(mWrappedCallback);
        }

        @Override
        public int compare(T2 o1, T2 o2) {
            return mWrappedCallback.compare(o1, o2);
        }

        @Override
        public void onInserted(int position, int count) {
            mBatchingListUpdateCallback.onInserted(position, count);
        }

        @Override
        public void onRemoved(int position, int count) {
            mBatchingListUpdateCallback.onRemoved(position, count);
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {
            mBatchingListUpdateCallback.onMoved(fromPosition, toPosition);
        }

        @Override
        public void onChanged(int position, int count) {
            mBatchingListUpdateCallback.onChanged(position, count, null);
        }

        @Override
        public void onChanged(int position, int count, Object payload) {
            mBatchingListUpdateCallback.onChanged(position, count, payload);
        }

        @Override
        public boolean areContentsTheSame(T2 oldItem, T2 newItem) {
            return mWrappedCallback.areContentsTheSame(oldItem, newItem);
        }

        @Override
        public boolean areItemsTheSame(T2 item1, T2 item2) {
            return mWrappedCallback.areItemsTheSame(item1, item2);
        }

        @Nullable
        @Override
        public Object getChangePayload(T2 item1, T2 item2) {
            return mWrappedCallback.getChangePayload(item1, item2);
        }

        /**
         * This method dispatches any pending event notifications to the wrapped Callback.
         * You <b>must</b> always call this method after you are done with editing the SortedList.
         */
        public void dispatchLastEvent() {
            mBatchingListUpdateCallback.dispatchLastEvent();
        }
    }
}
