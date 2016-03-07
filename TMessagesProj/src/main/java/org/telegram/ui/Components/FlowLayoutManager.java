/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.support.widget.RecyclerView;

import java.util.ArrayList;

public class FlowLayoutManager extends RecyclerView.LayoutManager {

    private SparseArray<Rect> framesPos;
    private SparseArray<Integer> itemsToRow;
    private SparseArray<Integer> rowToItems;
    private ArrayList<ArrayList<Integer>> rows;
    private Size actualSize = new Size();
    private int lastCalculatedWidth;
    private int lastCalculatedHeight;
    private float minimumInteritemSpacing = AndroidUtilities.dp(2);
    private float preferredRowSize;

    private static final int DIRECTION_NONE = -1;
    private static final int DIRECTION_UP = 0;
    private static final int DIRECTION_DOWN = 1;

    private int firstVisiblePosition;
    private boolean forceClearOffsets;

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getItemCount() == 0) {
            detachAndScrapAttachedViews(recycler);
            return;
        }

        int childTop;
        if (framesPos == null || framesPos.size() != getItemCount() || lastCalculatedHeight != getHeight() || lastCalculatedWidth != getWidth()) {
            prepareLayout();
            firstVisiblePosition = 0;
            childTop = 0;
        } else if (getChildCount() == 0) {
            firstVisiblePosition = 0;
            childTop = 0;
        } else if (getVisibleChildCount(0) >= state.getItemCount()) {
            firstVisiblePosition = 0;
            childTop = 0;
        } else {
            final View topChild = getChildAt(0);
            if (forceClearOffsets) {
                childTop = 0;
                forceClearOffsets = false;
            } else {
                childTop = getDecoratedTop(topChild);
            }

            Rect lastFrame = framesPos.get(getIndexOfRow(rows.size() - 1));
            if (getHeight() > lastFrame.y + lastFrame.height) {
                firstVisiblePosition = 0;
                childTop = 0;
            }

            int maxFirstRow = rows.size() - (getVisibleRowCount(0) - 1);
            boolean isOutOfRowBounds = getFirstVisibleRow() > maxFirstRow;
            if (isOutOfRowBounds) {
                int firstRow;
                if (isOutOfRowBounds) {
                    firstRow = maxFirstRow;
                } else {
                    firstRow = getFirstVisibleRow();
                }
                firstVisiblePosition = getIndexOfRow(firstRow);

                childTop = (int) framesPos.get(getIndexOfRow(rows.size() - 1)).y;

                if (getFirstVisibleRow() == 0) {
                    childTop = Math.min(childTop, 0);
                }
            }
        }
        detachAndScrapAttachedViews(recycler);
        fillGrid(DIRECTION_NONE, childTop, 0, recycler, state);
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        removeAllViews();
    }

    private void fillGrid(int direction, int emptyTop, int directionRowCount, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (firstVisiblePosition < 0) {
            firstVisiblePosition = 0;
        }
        if (firstVisiblePosition >= getItemCount()) {
            firstVisiblePosition = (getItemCount() - 1);
        }

        SparseArray<View> viewCache = null;
        int topOffset = emptyTop;
        int childCount = getChildCount();
        if (childCount != 0) {
            final View topView = getChildAt(0);
            topOffset = getDecoratedTop(topView);
            switch (direction) {
                case DIRECTION_UP: {
                    //FileLog.d("tmessages", "up topOffset from " + topOffset);
                    for (int a = 0; a < directionRowCount; a++) {
                        topOffset -= preferredRowSize;
                    }
                    //FileLog.d("tmessages", "up topOffset to " + topOffset);
                    /*framesPos.get(firstVisiblePosition - 1).height*/ //TODO support various row height
                    break;
                }
                case DIRECTION_DOWN: {
                    //FileLog.d("tmessages", "down topOffset from " + topOffset);
                    for (int a = 0; a < directionRowCount; a++) {
                        topOffset += preferredRowSize;
                    }
                    //FileLog.d("tmessages", "down topOffset to " + topOffset);
                    /*framesPos.get(firstVisiblePosition).height*/ //TODO support various row height
                    break;
                }
            }

            viewCache = new SparseArray<>(getChildCount());
            for (int i = 0; i < childCount; i++) {
                viewCache.put(firstVisiblePosition + i, getChildAt(i));
            }
            for (int i = 0; i < childCount; i++) {
                detachView(viewCache.valueAt(i));
            }
        }

        int firstRow = getRowOfIndex(firstVisiblePosition);
        switch (direction) {
            case DIRECTION_UP:
                //FileLog.d("tmessages", "up first position from " + firstVisiblePosition + " row from " + getRowOfIndex(firstVisiblePosition));
                for (int a = 0; a < directionRowCount; a++) {
                    firstVisiblePosition -= getCountForRow(firstRow - (a + 1));
                }
                //FileLog.d("tmessages", "up first position to " + firstVisiblePosition + " row to " + getRowOfIndex(firstVisiblePosition));
                break;
            case DIRECTION_DOWN:
                //FileLog.d("tmessages", "down first position from " + firstVisiblePosition + " row from " + getRowOfIndex(firstVisiblePosition));
                for (int a = 0; a < directionRowCount; a++) {
                    firstVisiblePosition += getCountForRow(firstRow + a);
                }
                //FileLog.d("tmessages", "down first position to " + firstVisiblePosition + " row to " + getRowOfIndex(firstVisiblePosition));
                break;
        }

        int lastHeight = 0;
        int lastRow = -1;
        //FileLog.d("tmessages", "fill from " + firstVisiblePosition + " to " + getVisibleChildCount(topOffset));
        for (int i = firstVisiblePosition, v = firstVisiblePosition + getVisibleChildCount(topOffset); i < v; i++) {
            if (i < 0 || i >= state.getItemCount()) {
                continue;
            }
            int newRow = getRowOfIndex(i);
            if (lastRow != -1 && newRow != lastRow) {
                topOffset = lastHeight;
            }

            Rect rect = framesPos.get(i);
            View view = viewCache != null ? viewCache.get(i) : null;
            if (view == null) {
                view = recycler.getViewForPosition(i);
                addView(view);
                RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) view.getLayoutParams();
                layoutParams.width = (int) rect.width;
                layoutParams.height = (int) rect.height;
                view.setLayoutParams(layoutParams);
                measureChildWithMargins(view, 0, 0);
                layoutDecorated(view, (int) rect.x, topOffset, (int) (rect.x + rect.width), (int) (topOffset + rect.height));
            } else {
                attachView(view);
                viewCache.remove(i);
            }
            lastHeight = (int) (topOffset + rect.height);
            lastRow = newRow;
        }
        if (viewCache != null) {
            for (int i = 0; i < viewCache.size(); i++) {
                final View removingView = viewCache.valueAt(i);
                recycler.recycleView(removingView);
            }
        }
    }

    @Override
    public void scrollToPosition(int position) {
        if (position >= getItemCount()) {
            return;
        }
        forceClearOffsets = true;
        firstVisiblePosition = position;
        requestLayout();
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0) {
            return 0;
        }

        final View topView = getChildAt(0);
        final View bottomView = getChildAt(getChildCount() - 1);

        int viewSpan = getDecoratedBottom(bottomView) - getDecoratedTop(topView);
        if (viewSpan < getHeight()) {
            return 0;
        }

        int delta;
        int rowsCount;
        int firstVisibleRow = getFirstVisibleRow();
        int lastVisibleRow = firstVisibleRow + getVisibleRowCount(0) - 1;
        boolean topBoundReached = firstVisibleRow == 0;
        boolean bottomBoundReached = lastVisibleRow >= rows.size() - 1;
        //FileLog.d("tmessages", "first = " + firstVisibleRow + " last = " + lastVisibleRow + " rows = " + rows.size() + " height = " + getHeight() + " preferredRowSize = " + preferredRowSize);
        if (dy > 0) {
            int bottom = getDecoratedBottom(bottomView);
            int amount = getHeight() - bottom + dy;
            rowsCount = (int) Math.ceil(Math.abs(amount) / preferredRowSize);
            //FileLog.d("tmessages", "scroll bottom = " + dy + " decorated bottom = " + bottom + " amount = " + amount + " rowsCount = " + rowsCount);
            if (amount < 0 || lastVisibleRow + rowsCount <= rows.size() - 1) {
                delta = -dy;
                //FileLog.d("tmessages", "delta1 = " + delta);
            } else {
                rowsCount = rows.size() - 1 - lastVisibleRow;
                delta = getHeight() - bottom - (int) (rowsCount * preferredRowSize);
                //FileLog.d("tmessages", "delta2 = " + delta + " rowsCount = " + rowsCount);
            }
        } else {
            int top = getDecoratedTop(topView);
            int amount = Math.abs(dy) + top;
            rowsCount = (int) Math.ceil(Math.abs(amount) / preferredRowSize);
            //FileLog.d("tmessages", "scroll top = " + dy + " decorated top = " + top + " amount = " + amount + " rowsCount = " + rowsCount);
            if (amount < 0 || firstVisibleRow - rowsCount >= 0) {
                delta = -dy;
                //FileLog.d("tmessages", "delta1 = " + delta);
            } else {
                rowsCount = firstVisibleRow;
                delta = -top + (int) (firstVisibleRow * preferredRowSize);
                //FileLog.d("tmessages", "delta2 = " + delta + " rowsCount = " + rowsCount);
            }
        }

        offsetChildrenVertical(delta);

        if (dy > 0) {
            if (getDecoratedBottom(topView) < 0 && !bottomBoundReached) {
                fillGrid(DIRECTION_DOWN, 0, rowsCount, recycler, state);
            } else if (!bottomBoundReached) {
                fillGrid(DIRECTION_NONE, 0, 0, recycler, state);
            }
        } else {
            if (getDecoratedTop(topView) > 0 && !topBoundReached) {
                fillGrid(DIRECTION_UP, 0, rowsCount, recycler, state);
            } else if (!topBoundReached) {
                fillGrid(DIRECTION_NONE, 0, 0, recycler, state);
            }
        }

        return -delta;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int getCountForRow(int row) {
        if (row < 0 || row >= rows.size()) {
            return 0;
        }
        return rows.get(row).size();
    }

    private int getIndexOfRow(int row) {
        if (rows == null) {
            return 0;
        }
        Integer value = rowToItems.get(row);
        return value == null ? 0 : value;
    }

    public int getRowCount() {
        return rows != null ? rows.size() : 0;
    }

    public int getRowOfIndex(int index) {
        if (rows == null) {
            return 0;
        }
        Integer value = itemsToRow.get(index);
        return value == null ? 0 : value;
    }

    private int getFirstVisibleRow() {
        return getRowOfIndex(firstVisiblePosition);
    }

    private int getVisibleChildCount(int topOffset) {
        int startRow = getFirstVisibleRow();
        int count = 0;
        for (int a = startRow, e = startRow + getVisibleRowCount(topOffset); a < e; a++) {
            if (a < rows.size()) {
                count += rows.get(a).size();
            } else {
                break;
            }
        }
        return count;
    }

    private int getVisibleRowCount(int topOffset) {
        int height = getHeight();
        View topChild = getChildAt(0);
        int top = topChild != null ? getDecoratedTop(topChild) : topOffset;
        int count = 0;
        int startRow = getFirstVisibleRow();
        for (int a = startRow; a < rows.size(); a++) {
            if (top > height) {
                break;
            }
            top += framesPos.get(getIndexOfRow(a)).height;
            count++;
        }
        return count;
    }

    private void prepareLayout() {
        if (framesPos == null) {
            framesPos = new SparseArray<>();
            itemsToRow = new SparseArray<>();
            rowToItems = new SparseArray<>();
        } else {
            framesPos.clear();
            itemsToRow.clear();
            rowToItems.clear();
        }
        preferredRowSize = getHeight() / 2.0f;
        float viewPortAvailableSize = getWidth();
        if (BuildVars.DEBUG_VERSION) {
            FileLog.d("tmessages", "preferredRowSize = " + preferredRowSize + " width = " + viewPortAvailableSize);
        }

        float totalItemSize = 0;
        int[] weights = new int[getItemCount()];
        for (int a = 0; a < getItemCount(); a++) {
            Size size = sizeForItem(a);
            totalItemSize += (size.width / size.height) * preferredRowSize;
            weights[a] = Math.round(size.width / size.height * 100);
        }

        int numberOfRows = Math.max(Math.round(totalItemSize / viewPortAvailableSize), 1);

        rows = getLinearPartitionForSequence(weights, numberOfRows);

        int i = 0, a;
        Point offset = new Point(0, 0);
        float previousItemSize = 0;
        for (a = 0; a < rows.size(); a++) {
            ArrayList<Integer> row = rows.get(a);

            float summedRatios = 0;
            for (int j = i, n = i + row.size(); j < n; j++) {
                Size preferredSize = sizeForItem(j);
                summedRatios += preferredSize.width / preferredSize.height;
            }

            float rowSize = viewPortAvailableSize - ((row.size() - 1) * minimumInteritemSpacing);

            if (rows.size() == 1 && a == rows.size() - 1) {
                if (row.size() < 2) {
                    rowSize = (float) Math.floor(viewPortAvailableSize / 3.0f) - ((row.size() - 1) * minimumInteritemSpacing);
                } else if (row.size() < 3) {
                    rowSize = (float) Math.floor(viewPortAvailableSize * 2.0f / 3.0f) - ((row.size() - 1) * minimumInteritemSpacing);
                }
            }

            for (int j = i, n = i + row.size(); j < n; j++) {
                Size preferredSize = sizeForItem(j);

                actualSize.width = Math.round(rowSize / summedRatios * (preferredSize.width / preferredSize.height));
                actualSize.height = preferredRowSize;//Math.round(rowSize / summedRatios);
                if (a == row.size() - 1) {
                    actualSize.height = preferredRowSize;
                }
                Rect rect = new Rect(offset.x, offset.y, actualSize.width, actualSize.height);
                if (rect.x + rect.width >= viewPortAvailableSize - 2.0f) {
                    rect.width = Math.max(1.0f, viewPortAvailableSize - rect.x);
                }
                framesPos.put(j, rect);
                itemsToRow.put(j, a);
                if (j == i) {
                    rowToItems.put(a, j);
                }
                offset.x += actualSize.width + minimumInteritemSpacing;
                previousItemSize = actualSize.height;
            }

            if (row.size() > 0) {
                offset.x = 0;
                offset.y += previousItemSize;
            }

            i += row.size();
        }
    }

    private int[] getLinearPartitionTable(int[] sequence, int numPartitions) {
        int n = sequence.length;
        int i, j, x;

        int tmpTable[] = new int[n * numPartitions];
        int solution[] = new int[(n - 1) * (numPartitions - 1)];

        for (i = 0; i < n; i++) {
            tmpTable[i * numPartitions] = sequence[i] + (i != 0 ? tmpTable[(i - 1) * numPartitions] : 0);
        }

        for (j = 0; j < numPartitions; j++) {
            tmpTable[j] = sequence[0];
        }

        for (i = 1; i < n; i++) {
            for (j = 1; j < numPartitions; j++) {
                int currentMin = 0;
                int minX = Integer.MAX_VALUE;

                for (x = 0; x < i; x++) {
                    int cost = Math.max(tmpTable[x * numPartitions + (j - 1)], tmpTable[i * numPartitions] - tmpTable[x * numPartitions]);
                    if (x == 0 || cost < currentMin) {
                        currentMin = cost;
                        minX = x;
                    }
                }
                tmpTable[i * numPartitions + j] = currentMin;
                solution[(i - 1) * (numPartitions - 1) + (j - 1)] = minX;
            }
        }

        return solution;
    }

    private ArrayList<ArrayList<Integer>> getLinearPartitionForSequence(int[] sequence, int numberOfPartitions) {
        int n = sequence.length;
        int k = numberOfPartitions;

        if (k <= 0) {
            return new ArrayList<>();
        }

        if (k >= n || n == 1) {
            ArrayList<ArrayList<Integer>> partition = new ArrayList<>(sequence.length);
            for (int i = 0; i < sequence.length; i++) {
                ArrayList<Integer> arrayList = new ArrayList<>(1);
                arrayList.add(sequence[i]);
                partition.add(arrayList);
            }
            return partition;
        }

        int[] solution = getLinearPartitionTable(sequence, numberOfPartitions);
        int solutionRowSize = numberOfPartitions - 1;

        k = k - 2;
        n = n - 1;
        ArrayList<ArrayList<Integer>> answer = new ArrayList<>();

        while (k >= 0) {
            if (n < 1) {
                answer.add(0, new ArrayList<Integer>());
            } else {
                ArrayList<Integer> currentAnswer = new ArrayList<>();
                for (int i = solution[(n - 1) * solutionRowSize + k] + 1, range = n + 1; i < range; i++) {
                    currentAnswer.add(sequence[i]);
                }
                answer.add(0, currentAnswer);
                n = solution[(n - 1) * solutionRowSize + k];
            }
            k = k - 1;
        }

        ArrayList<Integer> currentAnswer = new ArrayList<>();
        for (int i = 0, range = n + 1; i < range; i++) {
            currentAnswer.add(sequence[i]);
        }
        answer.add(0, currentAnswer);
        return answer;
    }

    private Size sizeForItem(int i) {
        Size size = getSizeForItem(i);
        if (size.width == 0) {
            size.width = 100;
        }
        if (size.height == 0) {
            size.height = 100;
        }
        float aspect = size.width / size.height;
        if (aspect > 4.0f || aspect < 0.2f) {
            size.height = size.width = Math.max(size.width, size.height);
        }
        return size;
    }

    protected Size getSizeForItem(int i) {
        return new Size(100, 100);
    }
}
