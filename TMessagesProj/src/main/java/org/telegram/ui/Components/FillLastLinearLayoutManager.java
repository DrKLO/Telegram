package org.telegram.ui.Components;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FillLastLinearLayoutManager extends LinearLayoutManager {

    private SparseArray<RecyclerView.ViewHolder> heights = new SparseArray<>();
    private int lastItemHeight = -1;
    private int listHeight;
    private int listWidth;
    private int additionalHeight;
    private RecyclerView listView;
    private boolean skipFirstItem;
    private boolean bind = true;
    private boolean canScrollVertically = true;

    public FillLastLinearLayoutManager(Context context, int h, RecyclerView recyclerView) {
        super(context);
        additionalHeight = h;
    }

    public FillLastLinearLayoutManager(Context context, int orientation, boolean reverseLayout, int h, RecyclerView recyclerView) {
        super(context, orientation, reverseLayout);
        listView = recyclerView;
        additionalHeight = h;
    }

    public void setAdditionalHeight(int value) {
        additionalHeight = value;
        calcLastItemHeight();
    }

    public void setSkipFirstItem() {
        skipFirstItem = true;
    }

    public void setBind(boolean value) {
        bind = value;
    }

    public void setCanScrollVertically(boolean value) {
        canScrollVertically = value;
    }

    @Override
    public boolean canScrollVertically() {
        return canScrollVertically;
    }

    @SuppressWarnings("unchecked")
    private void calcLastItemHeight() {
        if (listHeight <= 0) {
            return;
        }
        RecyclerView.Adapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        int count = adapter.getItemCount() - 1;
        int allHeight = 0;
        for (int a = skipFirstItem ? 1 : 0; a < count; a++) {
            int type = adapter.getItemViewType(a);
            RecyclerView.ViewHolder holder = heights.get(type, null);
            if (holder == null) {
                holder = adapter.createViewHolder(listView, type);
                heights.put(type, holder);
                if (holder.itemView.getLayoutParams() == null) {
                    holder.itemView.setLayoutParams(generateDefaultLayoutParams());
                }
            }
            if (bind) {
                adapter.onBindViewHolder(holder, a);
            }

            final RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
            final int widthSpec = getChildMeasureSpec(listWidth, getWidthMode(), getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin, lp.width, canScrollHorizontally());
            final int heightSpec = getChildMeasureSpec(listHeight, getHeightMode(), getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin, lp.height, canScrollVertically());
            holder.itemView.measure(widthSpec, heightSpec);
            allHeight += holder.itemView.getMeasuredHeight();
            if (allHeight >= listHeight) {
                break;
            }
        }
        lastItemHeight = Math.max(0, listHeight - allHeight - additionalHeight - listView.getPaddingBottom());
    }

    @Override
    public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec, int heightSpec) {
        int lastHeight = listHeight;
        listWidth = View.MeasureSpec.getSize(widthSpec);
        listHeight = View.MeasureSpec.getSize(heightSpec);
        if (lastHeight != listHeight) {
            calcLastItemHeight();
        }
        super.onMeasure(recycler, state, widthSpec, heightSpec);
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        heights.clear();
        calcLastItemHeight();
        super.onAdapterChanged(oldAdapter, newAdapter);
    }

    @Override
    public void onItemsChanged(RecyclerView recyclerView) {
        heights.clear();
        calcLastItemHeight();
        super.onItemsChanged(recyclerView);
    }

    @Override
    public void onItemsAdded(RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsAdded(recyclerView, positionStart, itemCount);
        calcLastItemHeight();
    }

    @Override
    public void onItemsRemoved(RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsRemoved(recyclerView, positionStart, itemCount);
        calcLastItemHeight();
    }

    @Override
    public void onItemsMoved(RecyclerView recyclerView, int from, int to, int itemCount) {
        super.onItemsMoved(recyclerView, from, to, itemCount);
        calcLastItemHeight();
    }

    @Override
    public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount);
        calcLastItemHeight();
    }

    @Override
    public void onItemsUpdated(RecyclerView recyclerView, int positionStart, int itemCount, Object payload) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount, payload);
        calcLastItemHeight();
    }

    @Override
    public void measureChildWithMargins(View child, int widthUsed, int heightUsed) {
        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
        int pos = holder.getAdapterPosition();
        if (pos == getItemCount() - 1) {
            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) child.getLayoutParams();
            layoutParams.height = Math.max(lastItemHeight, 0);
        }
        super.measureChildWithMargins(child, 0, 0);
    }
}
