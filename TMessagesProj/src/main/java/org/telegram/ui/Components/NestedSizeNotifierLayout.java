package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;

import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BottomSheet;

public class NestedSizeNotifierLayout extends SizeNotifierFrameLayout implements NestedScrollingParent3, View.OnLayoutChangeListener {
    public NestedSizeNotifierLayout(Context context) {
        super(context);
        nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
    }

    private NestedScrollingParentHelper nestedScrollingParentHelper;
    View targetListView;
    ChildLayout childLayout;
    BottomSheet.ContainerView bottomSheetContainerView;

    int maxTop;
    boolean attached;

    private boolean childAttached() {
        return childLayout != null && childLayout.isAttached() && childLayout.getListView() != null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateMaxTop();
    }

    private void updateMaxTop() {
        if (targetListView != null && childLayout != null) {
            maxTop = targetListView.getMeasuredHeight() - targetListView.getPaddingBottom() - childLayout.getMeasuredHeight();
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, int[] consumed) {
        if (target == targetListView && childAttached()) {
            RecyclerListView innerListView = childLayout.getListView();
            int top = childLayout.getTop();
            if (top == maxTop) {
                consumed[1] = dyUnconsumed;
                innerListView.scrollBy(0, dyUnconsumed);
            }
        }
    }

    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {

    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return super.onNestedPreFling(target, velocityX, velocityY);
    }

    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed, int type) {
        if (target == targetListView && childAttached()) {
            int t = childLayout.getTop();
            if (dy < 0) {
                if (t <= maxTop) {
                    RecyclerListView innerListView = childLayout.getListView();
                    LinearLayoutManager linearLayoutManager = (LinearLayoutManager) innerListView.getLayoutManager();
                    int pos = linearLayoutManager.findFirstVisibleItemPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        RecyclerView.ViewHolder holder = innerListView.findViewHolderForAdapterPosition(pos);
                        int top = holder != null ? holder.itemView.getTop() : -1;
                        int paddingTop = innerListView.getPaddingTop();
                        if (top != paddingTop || pos != 0) {
                            consumed[1] = pos != 0 ? dy : Math.max(dy, (top - paddingTop));
                            innerListView.scrollBy(0, dy);
                        }
                    }
                } else if (bottomSheetContainerView != null && !targetListView.canScrollVertically(dy)) {
                    bottomSheetContainerView.onNestedScroll(target, 0, 0, dx, dy);
                }
            } else {
                if (bottomSheetContainerView != null) {
                    bottomSheetContainerView.onNestedPreScroll(target, dx, dy, consumed);
                }
            }
        }
    }

    @Override
    public boolean onStartNestedScroll(View child, View target, int axes, int type) {
        return child != null && child.isAttachedToWindow() && axes == ViewCompat.SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedScrollAccepted(View child, View target, int axes, int type) {
        nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
    }

    @Override
    public void onStopNestedScroll(View target, int type) {
        nestedScrollingParentHelper.onStopNestedScroll(target);
        if (bottomSheetContainerView != null) {
            bottomSheetContainerView.onStopNestedScroll(target);
        }
    }

    @Override
    public void onStopNestedScroll(View child) {

    }

    public void setTargetListView(View target) {
        this.targetListView = target;
        updateMaxTop();
    }

    public void setChildLayout(ChildLayout childLayout) {
        if (this.childLayout != childLayout) {
            this.childLayout = childLayout;
            if (attached && childLayout != null && childLayout.getListView() != null) {
                childLayout.getListView().addOnLayoutChangeListener(this);
            }
            updateMaxTop();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (childLayout != null) {
            childLayout.addOnLayoutChangeListener(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        if (childLayout != null) {
            childLayout.removeOnLayoutChangeListener(this);
        }
    }

    public boolean isPinnedToTop() {
        return childLayout != null && childLayout.getTop() == maxTop;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        updateMaxTop();
    }

    public interface ChildLayout {
        RecyclerListView getListView();

        int getTop();

        boolean isAttached();

        int getMeasuredHeight();

        void addOnLayoutChangeListener(View.OnLayoutChangeListener listener);

        void removeOnLayoutChangeListener(OnLayoutChangeListener li);
    }

    public void setBottomSheetContainerView(BottomSheet.ContainerView bottomSheetContainerView) {
        this.bottomSheetContainerView = bottomSheetContainerView;
    }
}
