/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.util.SparseIntArray;

import org.telegram.messenger.AndroidUtilities;

import androidx.recyclerview.widget.GridLayoutManager;

public class ExtendedGridLayoutManager extends GridLayoutManager {

    private SparseIntArray itemSpans = new SparseIntArray();
    private SparseIntArray itemsToRow = new SparseIntArray();
    private int firstRowMax;
    private int rowsCount;
    private int calculatedWidth;

    public ExtendedGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    @Override
    public boolean supportsPredictiveItemAnimations() {
        return false;
    }

    private void prepareLayout(float viewPortAvailableSize) {
        if (viewPortAvailableSize == 0) {
            viewPortAvailableSize = 100;
        }
        itemSpans.clear();
        itemsToRow.clear();
        rowsCount = 0;
        firstRowMax = 0;
        
        int preferredRowSize = AndroidUtilities.dp(100);
        int itemsCount = getFlowItemCount();
        int spanCount = getSpanCount();
        int spanLeft = spanCount;
        int currentItemsInRow = 0;
        int currentItemsSpanAmount = 0;
        for (int a = 0; a < itemsCount; a++) {
            Size size = sizeForItem(a);
            int requiredSpan = Math.min(spanCount, (int) Math.floor(spanCount * (size.width / size.height * preferredRowSize / viewPortAvailableSize)));
            boolean moveToNewRow = spanLeft < requiredSpan || requiredSpan > 33 && spanLeft < requiredSpan - 15;
            if (moveToNewRow) {
                if (spanLeft != 0) {
                    int spanPerItem = spanLeft / currentItemsInRow;
                    for (int start = a - currentItemsInRow, b = start; b < start + currentItemsInRow; b++) {
                        if (b == start + currentItemsInRow - 1) {
                            itemSpans.put(b, itemSpans.get(b) + spanLeft);
                        } else {
                            itemSpans.put(b, itemSpans.get(b) + spanPerItem);
                        }
                        spanLeft -= spanPerItem;
                    }
                    itemsToRow.put(a - 1, rowsCount);
                }
                rowsCount++;
                currentItemsSpanAmount = 0;
                currentItemsInRow = 0;
                spanLeft = spanCount;
            } else {
                if (spanLeft < requiredSpan) {
                    requiredSpan = spanLeft;
                }
            }
            if (rowsCount == 0) {
                firstRowMax = Math.max(firstRowMax, a);
            }
            if (a == itemsCount - 1) {
                itemsToRow.put(a, rowsCount);
            }
            currentItemsSpanAmount += requiredSpan;
            currentItemsInRow++;
            spanLeft -= requiredSpan;

            itemSpans.put(a, requiredSpan);
        }
        if (itemsCount != 0) {
            rowsCount++;
        }
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

    private void checkLayout() {
        if (itemSpans.size() != getFlowItemCount() || calculatedWidth != getWidth()) {
            calculatedWidth = getWidth();
            prepareLayout(getWidth());
        }
    }

    public int getSpanSizeForItem(int i) {
        checkLayout();
        return itemSpans.get(i);
    }

    public int getRowsCount(int width) {
        if (rowsCount == 0) {
            prepareLayout(width);
        }
        return rowsCount;
    }

    public boolean isLastInRow(int i) {
        checkLayout();
        return itemsToRow.get(i, Integer.MAX_VALUE) != Integer.MAX_VALUE;
    }

    public boolean isFirstRow(int i) {
        checkLayout();
        return i <= firstRowMax;
    }

    protected int getFlowItemCount() {
        return getItemCount();
    }
}
