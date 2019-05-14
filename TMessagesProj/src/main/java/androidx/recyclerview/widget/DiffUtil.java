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

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * DiffUtil is a utility class that calculates the difference between two lists and outputs a
 * list of update operations that converts the first list into the second one.
 * <p>
 * It can be used to calculate updates for a RecyclerView Adapter. See {@link ListAdapter} and
 * {@link AsyncListDiffer} which can simplify the use of DiffUtil on a background thread.
 * <p>
 * DiffUtil uses Eugene W. Myers's difference algorithm to calculate the minimal number of updates
 * to convert one list into another. Myers's algorithm does not handle items that are moved so
 * DiffUtil runs a second pass on the result to detect items that were moved.
 * <p>
 * Note that DiffUtil, ListAdapter, and AsyncListDiffer require the list to not mutate while in use.
 * This generally means that both the lists themselves and their elements (or at least, the
 * properties of elements used in diffing) should not be modified directly. Instead, new lists
 * should be provided any time content changes. It's common for lists passed to DiffUtil to share
 * elements that have not mutated, so it is not strictly required to reload all data to use
 * DiffUtil.
 * <p>
 * If the lists are large, this operation may take significant time so you are advised to run this
 * on a background thread, get the {@link DiffResult} then apply it on the RecyclerView on the main
 * thread.
 * <p>
 * This algorithm is optimized for space and uses O(N) space to find the minimal
 * number of addition and removal operations between the two lists. It has O(N + D^2) expected time
 * performance where D is the length of the edit script.
 * <p>
 * If move detection is enabled, it takes an additional O(N^2) time where N is the total number of
 * added and removed items. If your lists are already sorted by the same constraint (e.g. a created
 * timestamp for a list of posts), you can disable move detection to improve performance.
 * <p>
 * The actual runtime of the algorithm significantly depends on the number of changes in the list
 * and the cost of your comparison methods. Below are some average run times for reference:
 * (The test list is composed of random UUID Strings and the tests are run on Nexus 5X with M)
 * <ul>
 *     <li>100 items and 10 modifications: avg: 0.39 ms, median: 0.35 ms
 *     <li>100 items and 100 modifications: 3.82 ms, median: 3.75 ms
 *     <li>100 items and 100 modifications without moves: 2.09 ms, median: 2.06 ms
 *     <li>1000 items and 50 modifications: avg: 4.67 ms, median: 4.59 ms
 *     <li>1000 items and 50 modifications without moves: avg: 3.59 ms, median: 3.50 ms
 *     <li>1000 items and 200 modifications: 27.07 ms, median: 26.92 ms
 *     <li>1000 items and 200 modifications without moves: 13.54 ms, median: 13.36 ms
 * </ul>
 * <p>
 * Due to implementation constraints, the max size of the list can be 2^26.
 *
 * @see ListAdapter
 * @see AsyncListDiffer
 */
public class DiffUtil {

    private DiffUtil() {
        // utility class, no instance.
    }

    private static final Comparator<Snake> SNAKE_COMPARATOR = new Comparator<Snake>() {
        @Override
        public int compare(Snake o1, Snake o2) {
            int cmpX = o1.x - o2.x;
            return cmpX == 0 ? o1.y - o2.y : cmpX;
        }
    };

    // Myers' algorithm uses two lists as axis labels. In DiffUtil's implementation, `x` axis is
    // used for old list and `y` axis is used for new list.

    /**
     * Calculates the list of update operations that can covert one list into the other one.
     *
     * @param cb The callback that acts as a gateway to the backing list data
     *
     * @return A DiffResult that contains the information about the edit sequence to convert the
     * old list into the new list.
     */
    @NonNull
    public static DiffResult calculateDiff(@NonNull Callback cb) {
        return calculateDiff(cb, true);
    }

    /**
     * Calculates the list of update operations that can covert one list into the other one.
     * <p>
     * If your old and new lists are sorted by the same constraint and items never move (swap
     * positions), you can disable move detection which takes <code>O(N^2)</code> time where
     * N is the number of added, moved, removed items.
     *
     * @param cb The callback that acts as a gateway to the backing list data
     * @param detectMoves True if DiffUtil should try to detect moved items, false otherwise.
     *
     * @return A DiffResult that contains the information about the edit sequence to convert the
     * old list into the new list.
     */
    @NonNull
    public static DiffResult calculateDiff(@NonNull Callback cb, boolean detectMoves) {
        final int oldSize = cb.getOldListSize();
        final int newSize = cb.getNewListSize();

        final List<Snake> snakes = new ArrayList<>();

        // instead of a recursive implementation, we keep our own stack to avoid potential stack
        // overflow exceptions
        final List<Range> stack = new ArrayList<>();

        stack.add(new Range(0, oldSize, 0, newSize));

        final int max = oldSize + newSize + Math.abs(oldSize - newSize);
        // allocate forward and backward k-lines. K lines are diagonal lines in the matrix. (see the
        // paper for details)
        // These arrays lines keep the max reachable position for each k-line.
        final int[] forward = new int[max * 2];
        final int[] backward = new int[max * 2];

        // We pool the ranges to avoid allocations for each recursive call.
        final List<Range> rangePool = new ArrayList<>();
        while (!stack.isEmpty()) {
            final Range range = stack.remove(stack.size() - 1);
            final Snake snake = diffPartial(cb, range.oldListStart, range.oldListEnd,
                    range.newListStart, range.newListEnd, forward, backward, max);
            if (snake != null) {
                if (snake.size > 0) {
                    snakes.add(snake);
                }
                // offset the snake to convert its coordinates from the Range's area to global
                snake.x += range.oldListStart;
                snake.y += range.newListStart;

                // add new ranges for left and right
                final Range left = rangePool.isEmpty() ? new Range() : rangePool.remove(
                        rangePool.size() - 1);
                left.oldListStart = range.oldListStart;
                left.newListStart = range.newListStart;
                if (snake.reverse) {
                    left.oldListEnd = snake.x;
                    left.newListEnd = snake.y;
                } else {
                    if (snake.removal) {
                        left.oldListEnd = snake.x - 1;
                        left.newListEnd = snake.y;
                    } else {
                        left.oldListEnd = snake.x;
                        left.newListEnd = snake.y - 1;
                    }
                }
                stack.add(left);

                // re-use range for right
                //noinspection UnnecessaryLocalVariable
                final Range right = range;
                if (snake.reverse) {
                    if (snake.removal) {
                        right.oldListStart = snake.x + snake.size + 1;
                        right.newListStart = snake.y + snake.size;
                    } else {
                        right.oldListStart = snake.x + snake.size;
                        right.newListStart = snake.y + snake.size + 1;
                    }
                } else {
                    right.oldListStart = snake.x + snake.size;
                    right.newListStart = snake.y + snake.size;
                }
                stack.add(right);
            } else {
                rangePool.add(range);
            }

        }
        // sort snakes
        Collections.sort(snakes, SNAKE_COMPARATOR);

        return new DiffResult(cb, snakes, forward, backward, detectMoves);

    }

    private static Snake diffPartial(Callback cb, int startOld, int endOld,
            int startNew, int endNew, int[] forward, int[] backward, int kOffset) {
        final int oldSize = endOld - startOld;
        final int newSize = endNew - startNew;

        if (endOld - startOld < 1 || endNew - startNew < 1) {
            return null;
        }

        final int delta = oldSize - newSize;
        final int dLimit = (oldSize + newSize + 1) / 2;
        Arrays.fill(forward, kOffset - dLimit - 1, kOffset + dLimit + 1, 0);
        Arrays.fill(backward, kOffset - dLimit - 1 + delta, kOffset + dLimit + 1 + delta, oldSize);
        final boolean checkInFwd = delta % 2 != 0;
        for (int d = 0; d <= dLimit; d++) {
            for (int k = -d; k <= d; k += 2) {
                // find forward path
                // we can reach k from k - 1 or k + 1. Check which one is further in the graph
                int x;
                final boolean removal;
                if (k == -d || (k != d && forward[kOffset + k - 1] < forward[kOffset + k + 1])) {
                    x = forward[kOffset + k + 1];
                    removal = false;
                } else {
                    x = forward[kOffset + k - 1] + 1;
                    removal = true;
                }
                // set y based on x
                int y = x - k;
                // move diagonal as long as items match
                while (x < oldSize && y < newSize
                        && cb.areItemsTheSame(startOld + x, startNew + y)) {
                    x++;
                    y++;
                }
                forward[kOffset + k] = x;
                if (checkInFwd && k >= delta - d + 1 && k <= delta + d - 1) {
                    if (forward[kOffset + k] >= backward[kOffset + k]) {
                        Snake outSnake = new Snake();
                        outSnake.x = backward[kOffset + k];
                        outSnake.y = outSnake.x - k;
                        outSnake.size = forward[kOffset + k] - backward[kOffset + k];
                        outSnake.removal = removal;
                        outSnake.reverse = false;
                        return outSnake;
                    }
                }
            }
            for (int k = -d; k <= d; k += 2) {
                // find reverse path at k + delta, in reverse
                final int backwardK = k + delta;
                int x;
                final boolean removal;
                if (backwardK == d + delta || (backwardK != -d + delta
                        && backward[kOffset + backwardK - 1] < backward[kOffset + backwardK + 1])) {
                    x = backward[kOffset + backwardK - 1];
                    removal = false;
                } else {
                    x = backward[kOffset + backwardK + 1] - 1;
                    removal = true;
                }

                // set y based on x
                int y = x - backwardK;
                // move diagonal as long as items match
                while (x > 0 && y > 0
                        && cb.areItemsTheSame(startOld + x - 1, startNew + y - 1)) {
                    x--;
                    y--;
                }
                backward[kOffset + backwardK] = x;
                if (!checkInFwd && k + delta >= -d && k + delta <= d) {
                    if (forward[kOffset + backwardK] >= backward[kOffset + backwardK]) {
                        Snake outSnake = new Snake();
                        outSnake.x = backward[kOffset + backwardK];
                        outSnake.y = outSnake.x - backwardK;
                        outSnake.size =
                                forward[kOffset + backwardK] - backward[kOffset + backwardK];
                        outSnake.removal = removal;
                        outSnake.reverse = true;
                        return outSnake;
                    }
                }
            }
        }
        throw new IllegalStateException("DiffUtil hit an unexpected case while trying to calculate"
                + " the optimal path. Please make sure your data is not changing during the"
                + " diff calculation.");
    }

    /**
     * A Callback class used by DiffUtil while calculating the diff between two lists.
     */
    public abstract static class Callback {
        /**
         * Returns the size of the old list.
         *
         * @return The size of the old list.
         */
        public abstract int getOldListSize();

        /**
         * Returns the size of the new list.
         *
         * @return The size of the new list.
         */
        public abstract int getNewListSize();

        /**
         * Called by the DiffUtil to decide whether two object represent the same Item.
         * <p>
         * For example, if your items have unique ids, this method should check their id equality.
         *
         * @param oldItemPosition The position of the item in the old list
         * @param newItemPosition The position of the item in the new list
         * @return True if the two items represent the same object or false if they are different.
         */
        public abstract boolean areItemsTheSame(int oldItemPosition, int newItemPosition);

        /**
         * Called by the DiffUtil when it wants to check whether two items have the same data.
         * DiffUtil uses this information to detect if the contents of an item has changed.
         * <p>
         * DiffUtil uses this method to check equality instead of {@link Object#equals(Object)}
         * so that you can change its behavior depending on your UI.
         * For example, if you are using DiffUtil with a
         * {@link RecyclerView.Adapter RecyclerView.Adapter}, you should
         * return whether the items' visual representations are the same.
         * <p>
         * This method is called only if {@link #areItemsTheSame(int, int)} returns
         * {@code true} for these items.
         *
         * @param oldItemPosition The position of the item in the old list
         * @param newItemPosition The position of the item in the new list which replaces the
         *                        oldItem
         * @return True if the contents of the items are the same or false if they are different.
         */
        public abstract boolean areContentsTheSame(int oldItemPosition, int newItemPosition);

        /**
         * When {@link #areItemsTheSame(int, int)} returns {@code true} for two items and
         * {@link #areContentsTheSame(int, int)} returns false for them, DiffUtil
         * calls this method to get a payload about the change.
         * <p>
         * For example, if you are using DiffUtil with {@link RecyclerView}, you can return the
         * particular field that changed in the item and your
         * {@link RecyclerView.ItemAnimator ItemAnimator} can use that
         * information to run the correct animation.
         * <p>
         * Default implementation returns {@code null}.
         *
         * @param oldItemPosition The position of the item in the old list
         * @param newItemPosition The position of the item in the new list
         *
         * @return A payload object that represents the change between the two items.
         */
        @Nullable
        public Object getChangePayload(int oldItemPosition, int newItemPosition) {
            return null;
        }
    }

    /**
     * Callback for calculating the diff between two non-null items in a list.
     * <p>
     * {@link Callback} serves two roles - list indexing, and item diffing. ItemCallback handles
     * just the second of these, which allows separation of code that indexes into an array or List
     * from the presentation-layer and content specific diffing code.
     *
     * @param <T> Type of items to compare.
     */
    public abstract static class ItemCallback<T> {
        /**
         * Called to check whether two objects represent the same item.
         * <p>
         * For example, if your items have unique ids, this method should check their id equality.
         * <p>
         * Note: {@code null} items in the list are assumed to be the same as another {@code null}
         * item and are assumed to not be the same as a non-{@code null} item. This callback will
         * not be invoked for either of those cases.
         *
         * @param oldItem The item in the old list.
         * @param newItem The item in the new list.
         * @return True if the two items represent the same object or false if they are different.
         *
         * @see Callback#areItemsTheSame(int, int)
         */
        public abstract boolean areItemsTheSame(@NonNull T oldItem, @NonNull T newItem);

        /**
         * Called to check whether two items have the same data.
         * <p>
         * This information is used to detect if the contents of an item have changed.
         * <p>
         * This method to check equality instead of {@link Object#equals(Object)} so that you can
         * change its behavior depending on your UI.
         * <p>
         * For example, if you are using DiffUtil with a
         * {@link RecyclerView.Adapter RecyclerView.Adapter}, you should
         * return whether the items' visual representations are the same.
         * <p>
         * This method is called only if {@link #areItemsTheSame(T, T)} returns {@code true} for
         * these items.
         * <p>
         * Note: Two {@code null} items are assumed to represent the same contents. This callback
         * will not be invoked for this case.
         *
         * @param oldItem The item in the old list.
         * @param newItem The item in the new list.
         * @return True if the contents of the items are the same or false if they are different.
         *
         * @see Callback#areContentsTheSame(int, int)
         */
        public abstract boolean areContentsTheSame(@NonNull T oldItem, @NonNull T newItem);

        /**
         * When {@link #areItemsTheSame(T, T)} returns {@code true} for two items and
         * {@link #areContentsTheSame(T, T)} returns false for them, this method is called to
         * get a payload about the change.
         * <p>
         * For example, if you are using DiffUtil with {@link RecyclerView}, you can return the
         * particular field that changed in the item and your
         * {@link RecyclerView.ItemAnimator ItemAnimator} can use that
         * information to run the correct animation.
         * <p>
         * Default implementation returns {@code null}.
         *
         * @see Callback#getChangePayload(int, int)
         */
        @SuppressWarnings({"WeakerAccess", "unused"})
        @Nullable
        public Object getChangePayload(@NonNull T oldItem, @NonNull T newItem) {
            return null;
        }
    }

    /**
     * Snakes represent a match between two lists. It is optionally prefixed or postfixed with an
     * add or remove operation. See the Myers' paper for details.
     */
    static class Snake {
        /**
         * Position in the old list
         */
        int x;

        /**
         * Position in the new list
         */
        int y;

        /**
         * Number of matches. Might be 0.
         */
        int size;

        /**
         * If true, this is a removal from the original list followed by {@code size} matches.
         * If false, this is an addition from the new list followed by {@code size} matches.
         */
        boolean removal;

        /**
         * If true, the addition or removal is at the end of the snake.
         * If false, the addition or removal is at the beginning of the snake.
         */
        boolean reverse;
    }

    /**
     * Represents a range in two lists that needs to be solved.
     * <p>
     * This internal class is used when running Myers' algorithm without recursion.
     */
    static class Range {

        int oldListStart, oldListEnd;

        int newListStart, newListEnd;

        public Range() {
        }

        public Range(int oldListStart, int oldListEnd, int newListStart, int newListEnd) {
            this.oldListStart = oldListStart;
            this.oldListEnd = oldListEnd;
            this.newListStart = newListStart;
            this.newListEnd = newListEnd;
        }
    }

    /**
     * This class holds the information about the result of a
     * {@link DiffUtil#calculateDiff(Callback, boolean)} call.
     * <p>
     * You can consume the updates in a DiffResult via
     * {@link #dispatchUpdatesTo(ListUpdateCallback)} or directly stream the results into a
     * {@link RecyclerView.Adapter} via {@link #dispatchUpdatesTo(RecyclerView.Adapter)}.
     */
    public static class DiffResult {
        /**
         * Signifies an item not present in the list.
         */
        public static final int NO_POSITION = -1;


        /**
         * While reading the flags below, keep in mind that when multiple items move in a list,
         * Myers's may pick any of them as the anchor item and consider that one NOT_CHANGED while
         * picking others as additions and removals. This is completely fine as we later detect
         * all moves.
         * <p>
         * Below, when an item is mentioned to stay in the same "location", it means we won't
         * dispatch a move/add/remove for it, it DOES NOT mean the item is still in the same
         * position.
         */
        // item stayed the same.
        private static final int FLAG_NOT_CHANGED = 1;
        // item stayed in the same location but changed.
        private static final int FLAG_CHANGED = FLAG_NOT_CHANGED << 1;
        // Item has moved and also changed.
        private static final int FLAG_MOVED_CHANGED = FLAG_CHANGED << 1;
        // Item has moved but did not change.
        private static final int FLAG_MOVED_NOT_CHANGED = FLAG_MOVED_CHANGED << 1;
        // Ignore this update.
        // If this is an addition from the new list, it means the item is actually removed from an
        // earlier position and its move will be dispatched when we process the matching removal
        // from the old list.
        // If this is a removal from the old list, it means the item is actually added back to an
        // earlier index in the new list and we'll dispatch its move when we are processing that
        // addition.
        private static final int FLAG_IGNORE = FLAG_MOVED_NOT_CHANGED << 1;

        // since we are re-using the int arrays that were created in the Myers' step, we mask
        // change flags
        private static final int FLAG_OFFSET = 5;

        private static final int FLAG_MASK = (1 << FLAG_OFFSET) - 1;

        // The Myers' snakes. At this point, we only care about their diagonal sections.
        private final List<Snake> mSnakes;

        // The list to keep oldItemStatuses. As we traverse old items, we assign flags to them
        // which also includes whether they were a real removal or a move (and its new index).
        private final int[] mOldItemStatuses;
        // The list to keep newItemStatuses. As we traverse new items, we assign flags to them
        // which also includes whether they were a real addition or a move(and its old index).
        private final int[] mNewItemStatuses;
        // The callback that was given to calcualte diff method.
        private final Callback mCallback;

        private final int mOldListSize;

        private final int mNewListSize;

        private final boolean mDetectMoves;

        /**
         * @param callback The callback that was used to calculate the diff
         * @param snakes The list of Myers' snakes
         * @param oldItemStatuses An int[] that can be re-purposed to keep metadata
         * @param newItemStatuses An int[] that can be re-purposed to keep metadata
         * @param detectMoves True if this DiffResult will try to detect moved items
         */
        DiffResult(Callback callback, List<Snake> snakes, int[] oldItemStatuses,
                int[] newItemStatuses, boolean detectMoves) {
            mSnakes = snakes;
            mOldItemStatuses = oldItemStatuses;
            mNewItemStatuses = newItemStatuses;
            Arrays.fill(mOldItemStatuses, 0);
            Arrays.fill(mNewItemStatuses, 0);
            mCallback = callback;
            mOldListSize = callback.getOldListSize();
            mNewListSize = callback.getNewListSize();
            mDetectMoves = detectMoves;
            addRootSnake();
            findMatchingItems();
        }

        /**
         * We always add a Snake to 0/0 so that we can run loops from end to beginning and be done
         * when we run out of snakes.
         */
        private void addRootSnake() {
            Snake firstSnake = mSnakes.isEmpty() ? null : mSnakes.get(0);
            if (firstSnake == null || firstSnake.x != 0 || firstSnake.y != 0) {
                Snake root = new Snake();
                root.x = 0;
                root.y = 0;
                root.removal = false;
                root.size = 0;
                root.reverse = false;
                mSnakes.add(0, root);
            }
        }

        /**
         * This method traverses each addition / removal and tries to match it to a previous
         * removal / addition. This is how we detect move operations.
         * <p>
         * This class also flags whether an item has been changed or not.
         * <p>
         * DiffUtil does this pre-processing so that if it is running on a big list, it can be moved
         * to background thread where most of the expensive stuff will be calculated and kept in
         * the statuses maps. DiffResult uses this pre-calculated information while dispatching
         * the updates (which is probably being called on the main thread).
         */
        private void findMatchingItems() {
            int posOld = mOldListSize;
            int posNew = mNewListSize;
            // traverse the matrix from right bottom to 0,0.
            for (int i = mSnakes.size() - 1; i >= 0; i--) {
                final Snake snake = mSnakes.get(i);
                final int endX = snake.x + snake.size;
                final int endY = snake.y + snake.size;
                if (mDetectMoves) {
                    while (posOld > endX) {
                        // this is a removal. Check remaining snakes to see if this was added before
                        findAddition(posOld, posNew, i);
                        posOld--;
                    }
                    while (posNew > endY) {
                        // this is an addition. Check remaining snakes to see if this was removed
                        // before
                        findRemoval(posOld, posNew, i);
                        posNew--;
                    }
                }
                for (int j = 0; j < snake.size; j++) {
                    // matching items. Check if it is changed or not
                    final int oldItemPos = snake.x + j;
                    final int newItemPos = snake.y + j;
                    final boolean theSame = mCallback
                            .areContentsTheSame(oldItemPos, newItemPos);
                    final int changeFlag = theSame ? FLAG_NOT_CHANGED : FLAG_CHANGED;
                    mOldItemStatuses[oldItemPos] = (newItemPos << FLAG_OFFSET) | changeFlag;
                    mNewItemStatuses[newItemPos] = (oldItemPos << FLAG_OFFSET) | changeFlag;
                }
                posOld = snake.x;
                posNew = snake.y;
            }
        }

        private void findAddition(int x, int y, int snakeIndex) {
            if (mOldItemStatuses[x - 1] != 0) {
                return; // already set by a latter item
            }
            findMatchingItem(x, y, snakeIndex, false);
        }

        private void findRemoval(int x, int y, int snakeIndex) {
            if (mNewItemStatuses[y - 1] != 0) {
                return; // already set by a latter item
            }
            findMatchingItem(x, y, snakeIndex, true);
        }

        /**
         * Given a position in the old list, returns the position in the new list, or
         * {@code NO_POSITION} if it was removed.
         *
         * @param oldListPosition Position of item in old list
         *
         * @return Position of item in new list, or {@code NO_POSITION} if not present.
         *
         * @see #NO_POSITION
         * @see #convertNewPositionToOld(int)
         */
        public int convertOldPositionToNew(@IntRange(from = 0) int oldListPosition) {
            if (oldListPosition < 0 || oldListPosition >= mOldListSize) {
                throw new IndexOutOfBoundsException("Index out of bounds - passed position = "
                        + oldListPosition + ", old list size = " + mOldListSize);
            }
            final int status = mOldItemStatuses[oldListPosition];
            if ((status & FLAG_MASK) == 0) {
                return NO_POSITION;
            } else {
                return status >> FLAG_OFFSET;
            }
        }

        /**
         * Given a position in the new list, returns the position in the old list, or
         * {@code NO_POSITION} if it was removed.
         *
         * @param newListPosition Position of item in new list
         *
         * @return Position of item in old list, or {@code NO_POSITION} if not present.
         *
         * @see #NO_POSITION
         * @see #convertOldPositionToNew(int)
         */
        public int convertNewPositionToOld(@IntRange(from = 0) int newListPosition) {
            if (newListPosition < 0 || newListPosition >= mNewListSize) {
                throw new IndexOutOfBoundsException("Index out of bounds - passed position = "
                        + newListPosition + ", new list size = " + mNewListSize);
            }
            final int status = mNewItemStatuses[newListPosition];
            if ((status & FLAG_MASK) == 0) {
                return NO_POSITION;
            } else {
                return status >> FLAG_OFFSET;
            }
        }

        /**
         * Finds a matching item that is before the given coordinates in the matrix
         * (before : left and above).
         *
         * @param x The x position in the matrix (position in the old list)
         * @param y The y position in the matrix (position in the new list)
         * @param snakeIndex The current snake index
         * @param removal True if we are looking for a removal, false otherwise
         *
         * @return True if such item is found.
         */
        private boolean findMatchingItem(final int x, final int y, final int snakeIndex,
                final boolean removal) {
            final int myItemPos;
            int curX;
            int curY;
            if (removal) {
                myItemPos = y - 1;
                curX = x;
                curY = y - 1;
            } else {
                myItemPos = x - 1;
                curX = x - 1;
                curY = y;
            }
            for (int i = snakeIndex; i >= 0; i--) {
                final Snake snake = mSnakes.get(i);
                final int endX = snake.x + snake.size;
                final int endY = snake.y + snake.size;
                if (removal) {
                    // check removals for a match
                    for (int pos = curX - 1; pos >= endX; pos--) {
                        if (mCallback.areItemsTheSame(pos, myItemPos)) {
                            // found!
                            final boolean theSame = mCallback.areContentsTheSame(pos, myItemPos);
                            final int changeFlag = theSame ? FLAG_MOVED_NOT_CHANGED
                                    : FLAG_MOVED_CHANGED;
                            mNewItemStatuses[myItemPos] = (pos << FLAG_OFFSET) | FLAG_IGNORE;
                            mOldItemStatuses[pos] = (myItemPos << FLAG_OFFSET) | changeFlag;
                            return true;
                        }
                    }
                } else {
                    // check for additions for a match
                    for (int pos = curY - 1; pos >= endY; pos--) {
                        if (mCallback.areItemsTheSame(myItemPos, pos)) {
                            // found
                            final boolean theSame = mCallback.areContentsTheSame(myItemPos, pos);
                            final int changeFlag = theSame ? FLAG_MOVED_NOT_CHANGED
                                    : FLAG_MOVED_CHANGED;
                            mOldItemStatuses[x - 1] = (pos << FLAG_OFFSET) | FLAG_IGNORE;
                            mNewItemStatuses[pos] = ((x - 1) << FLAG_OFFSET) | changeFlag;
                            return true;
                        }
                    }
                }
                curX = snake.x;
                curY = snake.y;
            }
            return false;
        }

        /**
         * Dispatches the update events to the given adapter.
         * <p>
         * For example, if you have an {@link RecyclerView.Adapter Adapter}
         * that is backed by a {@link List}, you can swap the list with the new one then call this
         * method to dispatch all updates to the RecyclerView.
         * <pre>
         *     List oldList = mAdapter.getData();
         *     DiffResult result = DiffUtil.calculateDiff(new MyCallback(oldList, newList));
         *     mAdapter.setData(newList);
         *     result.dispatchUpdatesTo(mAdapter);
         * </pre>
         * <p>
         * Note that the RecyclerView requires you to dispatch adapter updates immediately when you
         * change the data (you cannot defer {@code notify*} calls). The usage above adheres to this
         * rule because updates are sent to the adapter right after the backing data is changed,
         * before RecyclerView tries to read it.
         * <p>
         * On the other hand, if you have another
         * {@link RecyclerView.AdapterDataObserver AdapterDataObserver}
         * that tries to process events synchronously, this may confuse that observer because the
         * list is instantly moved to its final state while the adapter updates are dispatched later
         * on, one by one. If you have such an
         * {@link RecyclerView.AdapterDataObserver AdapterDataObserver},
         * you can use
         * {@link #dispatchUpdatesTo(ListUpdateCallback)} to handle each modification
         * manually.
         *
         * @param adapter A RecyclerView adapter which was displaying the old list and will start
         *                displaying the new list.
         * @see AdapterListUpdateCallback
         */
        public void dispatchUpdatesTo(@NonNull final RecyclerView.Adapter adapter) {
            dispatchUpdatesTo(new AdapterListUpdateCallback(adapter));
        }

        /**
         * Dispatches update operations to the given Callback.
         * <p>
         * These updates are atomic such that the first update call affects every update call that
         * comes after it (the same as RecyclerView).
         *
         * @param updateCallback The callback to receive the update operations.
         * @see #dispatchUpdatesTo(RecyclerView.Adapter)
         */
        public void dispatchUpdatesTo(@NonNull ListUpdateCallback updateCallback) {
            final BatchingListUpdateCallback batchingCallback;
            if (updateCallback instanceof BatchingListUpdateCallback) {
                batchingCallback = (BatchingListUpdateCallback) updateCallback;
            } else {
                batchingCallback = new BatchingListUpdateCallback(updateCallback);
                // replace updateCallback with a batching callback and override references to
                // updateCallback so that we don't call it directly by mistake
                //noinspection UnusedAssignment
                updateCallback = batchingCallback;
            }
            // These are add/remove ops that are converted to moves. We track their positions until
            // their respective update operations are processed.
            final List<PostponedUpdate> postponedUpdates = new ArrayList<>();
            int posOld = mOldListSize;
            int posNew = mNewListSize;
            for (int snakeIndex = mSnakes.size() - 1; snakeIndex >= 0; snakeIndex--) {
                final Snake snake = mSnakes.get(snakeIndex);
                final int snakeSize = snake.size;
                final int endX = snake.x + snakeSize;
                final int endY = snake.y + snakeSize;
                if (endX < posOld) {
                    dispatchRemovals(postponedUpdates, batchingCallback, endX, posOld - endX, endX);
                }

                if (endY < posNew) {
                    dispatchAdditions(postponedUpdates, batchingCallback, endX, posNew - endY,
                            endY);
                }
                for (int i = snakeSize - 1; i >= 0; i--) {
                    if ((mOldItemStatuses[snake.x + i] & FLAG_MASK) == FLAG_CHANGED) {
                        batchingCallback.onChanged(snake.x + i, 1,
                                mCallback.getChangePayload(snake.x + i, snake.y + i));
                    }
                }
                posOld = snake.x;
                posNew = snake.y;
            }
            batchingCallback.dispatchLastEvent();
        }

        private static PostponedUpdate removePostponedUpdate(List<PostponedUpdate> updates,
                int pos, boolean removal) {
            for (int i = updates.size() - 1; i >= 0; i--) {
                final PostponedUpdate update = updates.get(i);
                if (update.posInOwnerList == pos && update.removal == removal) {
                    updates.remove(i);
                    for (int j = i; j < updates.size(); j++) {
                        // offset other ops since they swapped positions
                        updates.get(j).currentPos += removal ? 1 : -1;
                    }
                    return update;
                }
            }
            return null;
        }

        private void dispatchAdditions(List<PostponedUpdate> postponedUpdates,
                ListUpdateCallback updateCallback, int start, int count, int globalIndex) {
            if (!mDetectMoves) {
                updateCallback.onInserted(start, count);
                return;
            }
            for (int i = count - 1; i >= 0; i--) {
                int status = mNewItemStatuses[globalIndex + i] & FLAG_MASK;
                switch (status) {
                    case 0: // real addition
                        updateCallback.onInserted(start, 1);
                        for (PostponedUpdate update : postponedUpdates) {
                            update.currentPos += 1;
                        }
                        break;
                    case FLAG_MOVED_CHANGED:
                    case FLAG_MOVED_NOT_CHANGED:
                        final int pos = mNewItemStatuses[globalIndex + i] >> FLAG_OFFSET;
                        final PostponedUpdate update = removePostponedUpdate(postponedUpdates, pos,
                                true);
                        // the item was moved from that position
                        //noinspection ConstantConditions
                        updateCallback.onMoved(update.currentPos, start);
                        if (status == FLAG_MOVED_CHANGED) {
                            // also dispatch a change
                            updateCallback.onChanged(start, 1,
                                    mCallback.getChangePayload(pos, globalIndex + i));
                        }
                        break;
                    case FLAG_IGNORE: // ignoring this
                        postponedUpdates.add(new PostponedUpdate(globalIndex + i, start, false));
                        break;
                    default:
                        throw new IllegalStateException(
                                "unknown flag for pos " + (globalIndex + i) + " " + Long
                                        .toBinaryString(status));
                }
            }
        }

        private void dispatchRemovals(List<PostponedUpdate> postponedUpdates,
                ListUpdateCallback updateCallback, int start, int count, int globalIndex) {
            if (!mDetectMoves) {
                updateCallback.onRemoved(start, count);
                return;
            }
            for (int i = count - 1; i >= 0; i--) {
                final int status = mOldItemStatuses[globalIndex + i] & FLAG_MASK;
                switch (status) {
                    case 0: // real removal
                        updateCallback.onRemoved(start + i, 1);
                        for (PostponedUpdate update : postponedUpdates) {
                            update.currentPos -= 1;
                        }
                        break;
                    case FLAG_MOVED_CHANGED:
                    case FLAG_MOVED_NOT_CHANGED:
                        final int pos = mOldItemStatuses[globalIndex + i] >> FLAG_OFFSET;
                        final PostponedUpdate update = removePostponedUpdate(postponedUpdates, pos,
                                false);
                        // the item was moved to that position. we do -1 because this is a move not
                        // add and removing current item offsets the target move by 1
                        //noinspection ConstantConditions
                        updateCallback.onMoved(start + i, update.currentPos - 1);
                        if (status == FLAG_MOVED_CHANGED) {
                            // also dispatch a change
                            updateCallback.onChanged(update.currentPos - 1, 1,
                                    mCallback.getChangePayload(globalIndex + i, pos));
                        }
                        break;
                    case FLAG_IGNORE: // ignoring this
                        postponedUpdates.add(new PostponedUpdate(globalIndex + i, start + i, true));
                        break;
                    default:
                        throw new IllegalStateException(
                                "unknown flag for pos " + (globalIndex + i) + " " + Long
                                        .toBinaryString(status));
                }
            }
        }

        @VisibleForTesting
        List<Snake> getSnakes() {
            return mSnakes;
        }
    }

    /**
     * Represents an update that we skipped because it was a move.
     * <p>
     * When an update is skipped, it is tracked as other updates are dispatched until the matching
     * add/remove operation is found at which point the tracked position is used to dispatch the
     * update.
     */
    private static class PostponedUpdate {

        int posInOwnerList;

        int currentPos;

        boolean removal;

        public PostponedUpdate(int posInOwnerList, int currentPos, boolean removal) {
            this.posInOwnerList = posInOwnerList;
            this.currentPos = currentPos;
            this.removal = removal;
        }
    }
}
