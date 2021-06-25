package org.telegram.ui.Components;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FillLastGridLayoutManager extends GridLayoutManager {

    private SparseArray<RecyclerView.ViewHolder> heights = new SparseArray<>();
    protected int lastItemHeight = -1;
    private int listHeight;
    private int listWidth;
    private int additionalHeight;
    private RecyclerView listView;
    private boolean bind = true;
    private boolean canScrollVertically = true;

    public void setBind(boolean bind) {
        this.bind = bind;
    }

    public FillLastGridLayoutManager(Context context, int spanCount, int h, RecyclerView recyclerView) {
        super(context, spanCount);
        listView = recyclerView;
        additionalHeight = h;
    }

    public FillLastGridLayoutManager(Context context, int spanCount, int orientation, boolean reverseLayout, int h, RecyclerView recyclerView) {
        super(context, spanCount, orientation, reverseLayout);
        listView = recyclerView;
        additionalHeight = h;
    }

    public void setAdditionalHeight(int value) {
        additionalHeight = value;
        calcLastItemHeight();
    }

    @SuppressWarnings("unchecked")
    protected void calcLastItemHeight() {
        if (listHeight <= 0 || !shouldCalcLastItemHeight()) {
            return;
        }
        RecyclerView.Adapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        int spanCount = getSpanCount();
        int spanCounter = 0;
        int count = adapter.getItemCount() - 1;
        int allHeight = 0;
        final SpanSizeLookup spanSizeLookup = getSpanSizeLookup();
        boolean add = true;
        for (int a = 0; a < count; a++) {
            final int spanSize = spanSizeLookup.getSpanSize(a);
            spanCounter += spanSize;
            if (spanSize == spanCount || spanCounter > spanCount) {
                spanCounter = spanSize;
                add = true;
            }
            if (!add) {
                continue;
            }
            add = false;

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
            if (allHeight >= (listHeight - additionalHeight - listView.getPaddingBottom())) {
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
    protected void measureChild(View view, int otherDirParentSpecMode, boolean alreadyMeasured) {
        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(view);
        int pos = holder.getAdapterPosition();
        if (pos == getItemCount() - 1) {
            RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) view.getLayoutParams();
            layoutParams.height = Math.max(lastItemHeight, 0);
        }
        super.measureChild(view, otherDirParentSpecMode, alreadyMeasured);
    }

    protected boolean shouldCalcLastItemHeight() {
        return true;
    }

    public void setCanScrollVertically(boolean value) {
        canScrollVertically = value;
    }

    @Override
    public boolean canScrollVertically() {
        return canScrollVertically;
    }
}
