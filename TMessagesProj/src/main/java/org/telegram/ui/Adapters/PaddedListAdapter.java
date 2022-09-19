package org.telegram.ui.Adapters;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.WrapperListAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Components.RecyclerListView;

/**
 * PaddedListAdapter wraps list adapter and adds transparent padding view at the start
 */
public class PaddedListAdapter extends RecyclerListView.SelectionAdapter {

    private final int PADDING_VIEW_TYPE = -983904;

    private RecyclerListView.SelectionAdapter wrappedAdapter;
    private GetPaddingRunnable getPaddingRunnable;
    private Integer padding = null;
    public View paddingView;

    public boolean paddingViewAttached = false;

    public PaddedListAdapter(RecyclerListView.SelectionAdapter adapter) {
        wrappedAdapter = adapter;
        wrappedAdapter.registerAdapterDataObserver(mDataObserver);
    }
    public PaddedListAdapter(RecyclerListView.SelectionAdapter adapter, GetPaddingRunnable getPaddingRunnable) {
        wrappedAdapter = adapter;
        wrappedAdapter.registerAdapterDataObserver(mDataObserver);
        this.getPaddingRunnable = getPaddingRunnable;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        if (holder.getAdapterPosition() == 0) {
            return false;
        }
        return wrappedAdapter.isEnabled(holder);
    }

    public interface GetPaddingRunnable {
        public int run(int parentHeight);
    }

    public void setPadding(int padding) {
        this.padding = padding;
        if (paddingView != null) {
            paddingView.requestLayout();
        }
    }

    public void setPadding(GetPaddingRunnable getPaddingRunnable) {
        this.getPaddingRunnable = getPaddingRunnable;
        if (paddingView != null) {
            paddingView.requestLayout();
        }
    }

    private int getPadding(int parentHeight) {
        if (padding != null) {
            return lastPadding = padding;
        } else if (getPaddingRunnable != null) {
            return lastPadding = getPaddingRunnable.run(parentHeight);
        } else {
            return lastPadding = 0;
        }
    }

    private int lastPadding;
    public int getPadding() {
        return lastPadding;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == PADDING_VIEW_TYPE) {
            return new RecyclerListView.Holder(paddingView = new View(parent.getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int parentHeight = ((View) getParent()).getMeasuredHeight();
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getPadding(parentHeight), MeasureSpec.EXACTLY));
                }

                @Override
                protected void onAttachedToWindow() {
                    super.onAttachedToWindow();
                    paddingViewAttached = true;
                }

                @Override
                protected void onDetachedFromWindow() {
                    super.onDetachedFromWindow();
                    paddingViewAttached = false;
                }
            });
        }
        return wrappedAdapter.onCreateViewHolder(parent, viewType);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return PADDING_VIEW_TYPE;
        }
        return wrappedAdapter.getItemViewType(position - 1);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position > 0) {
            wrappedAdapter.onBindViewHolder(holder, position - 1);
        }
    }

    @Override
    public int getItemCount() {
        return 1 + wrappedAdapter.getItemCount();
    }

    private RecyclerView.AdapterDataObserver mDataObserver = new RecyclerView.AdapterDataObserver() {
        @Override
        public void onChanged() {
            super.onChanged();
            notifyDataSetChanged();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            super.onItemRangeChanged(positionStart, itemCount);
            notifyItemRangeChanged(1 + positionStart, itemCount);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            super.onItemRangeInserted(positionStart, itemCount);
            notifyItemRangeInserted(1 + positionStart, itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            super.onItemRangeRemoved(positionStart, itemCount);
            notifyItemRangeRemoved(1 + positionStart, itemCount);
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            super.onItemRangeMoved(fromPosition, toPosition, itemCount);
            notifyItemRangeChanged(1 + fromPosition, 1 + toPosition + itemCount);
        }
    };


}
