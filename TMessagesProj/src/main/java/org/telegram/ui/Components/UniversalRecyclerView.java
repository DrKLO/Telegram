package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.ui.ActionBar.Theme.multAlpha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.iv.RichEditor;

import java.util.ArrayList;

public class UniversalRecyclerView extends RecyclerListView {

    public LinearLayoutManager layoutManager;
    public final UniversalAdapter adapter;
    public ItemTouchHelper itemTouchHelper;

    private boolean doNotDetachViews;
    public void doNotDetachViews() {
        doNotDetachViews = true;
    }
    public void doNotDetachViews(boolean value) {
        doNotDetachViews = value;
    }

    public UniversalRecyclerView(
        BaseFragment fragment,
        Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems,
        Utilities.Callback5<UItem, View, Integer, Float, Float> onClick,
        Utilities.Callback5Return<UItem, View, Integer, Float, Float, Boolean> onLongClick
    ) {
        this(
            fragment.getContext(),
            fragment.getCurrentAccount(),
            fragment.getClassGuid(),
            fillItems,
            onClick,
            onLongClick,
            fragment.getResourceProvider()
        );
    }

    public UniversalRecyclerView(
        Context context,
        int currentAccount,
        int classGuid,
        Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems,
        Utilities.Callback5<UItem, View, Integer, Float, Float> onClick,
        Utilities.Callback5Return<UItem, View, Integer, Float, Float, Boolean> onLongClick,
        Theme.ResourcesProvider resourcesProvider
    ) {
        this(context, currentAccount, classGuid, false, fillItems, onClick, onLongClick, resourcesProvider);
    }

    public UniversalRecyclerView(
        Context context,
        int currentAccount,
        int classGuid,
        boolean dialog,
        Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems,
        Utilities.Callback5<UItem, View, Integer, Float, Float> onClick,
        Utilities.Callback5Return<UItem, View, Integer, Float, Float, Boolean> onLongClick,
        Theme.ResourcesProvider resourcesProvider
    ) {
        this(context, currentAccount, classGuid, dialog, fillItems, onClick, onLongClick, resourcesProvider, UItem.MAX_SPAN_COUNT, LinearLayoutManager.VERTICAL);
    }

    public UniversalRecyclerView(
        Context context,
        int currentAccount,
        int classGuid,
        boolean dialog,
        Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems,
        Utilities.Callback5<UItem, View, Integer, Float, Float> onClick,
        Utilities.Callback5Return<UItem, View, Integer, Float, Float, Boolean> onLongClick,
        Theme.ResourcesProvider resourcesProvider,
        int spansCount,
        int orientation
    ) {
        super(context, resourcesProvider);

        if (spansCount == UItem.MAX_SPAN_COUNT) {
            setLayoutManager(layoutManager = new LinearLayoutManager(context, orientation, false) {
                @Override
                protected int getExtraLayoutSpace(State state) {
                    if (doNotDetachViews) return AndroidUtilities.displaySize.y;
                    return super.getExtraLayoutSpace(state);
                }
            });
        } else {
            ExtendedGridLayoutManager layoutManager1 = new ExtendedGridLayoutManager(context, spansCount) {
                @Override
                protected int getExtraLayoutSpace(State state) {
                    if (doNotDetachViews) return AndroidUtilities.displaySize.y;
                    return super.getExtraLayoutSpace(state);
                }
            };
            layoutManager1.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (adapter == null)
                        return layoutManager1.getSpanCount();
                    final UItem item = adapter.getItem(position);
                    if (item == null || item.spanCount == UItem.MAX_SPAN_COUNT)
                        return layoutManager1.getSpanCount();
                    return item.spanCount;
                }
            });
            setLayoutManager(layoutManager = layoutManager1);
        }
        setAdapter(adapter = new UniversalAdapter(this, context, currentAccount, classGuid, dialog, fillItems, resourcesProvider));

        if (onClick != null) {
            setOnItemClickListener((view, position, x, y) -> {
                UItem item = adapter.getItem(position);
                if (item == null) return;
                onClick.run(item, view, position, x, y);
            });
        }

        if (onLongClick != null) {
            setOnItemLongClickListener((view, position, x, y) -> {
                UItem item = adapter.getItem(position);
                if (item == null) return false;
                return onLongClick.run(item, view, position, x, y);
            });
        }

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                invalidate();
                onLayoutUpdate();
            }
            @Override
            protected void onRemoveAnimationUpdate(ViewHolder holder) {
                super.onRemoveAnimationUpdate(holder);
                if (hasSections()) invalidate();
                onLayoutUpdate();
            }
            @Override
            protected void onAddAnimationUpdate(ViewHolder holder) {
                super.onAddAnimationUpdate(holder);
                if (hasSections()) invalidate();
                onLayoutUpdate();
            }
            @Override
            protected void onChangeAnimationUpdate(ViewHolder holder) {
                super.onChangeAnimationUpdate(holder);
                if (hasSections()) invalidate();
                onLayoutUpdate();
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        setItemAnimator(itemAnimator);
    }

    protected void onLayoutUpdate() {

    }

    public void makeHorizontal() {
        setLayoutManager(layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false) {
            @Override
            protected int getExtraLayoutSpace(State state) {
                if (doNotDetachViews) return AndroidUtilities.displaySize.y;
                return super.getExtraLayoutSpace(state);
            }
        });
    }

    public void setSpanCount(int spanCount) {
        if (layoutManager instanceof ExtendedGridLayoutManager) {
            ((ExtendedGridLayoutManager) layoutManager).setSpanCount(spanCount);
        } else if (layoutManager instanceof LinearLayoutManager && spanCount != UItem.MAX_SPAN_COUNT) {
            ExtendedGridLayoutManager layoutManager1 = new ExtendedGridLayoutManager(getContext(), spanCount) {
                @Override
                protected int getExtraLayoutSpace(State state) {
                    if (doNotDetachViews) return AndroidUtilities.displaySize.y;
                    return super.getExtraLayoutSpace(state);
                }
            };
            layoutManager1.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (adapter == null)
                        return layoutManager1.getSpanCount();
                    final UItem item = adapter.getItem(position);
                    if (item == null || item.spanCount == UItem.MAX_SPAN_COUNT)
                        return layoutManager1.getSpanCount();
                    return item.spanCount;
                }
            });
            setLayoutManager(layoutManager = layoutManager1);
        }
    }

    public int getSpanCount() {
        if (layoutManager instanceof ExtendedGridLayoutManager) {
            return ((ExtendedGridLayoutManager) layoutManager).getSpanCount();
        }
        return UItem.MAX_SPAN_COUNT;
    }

    public void listenReorder(Utilities.Callback2<Integer, ArrayList<UItem>> onReordered) {
        listenReorder(onReordered, false);
    }

    private boolean reorderingOnOtherAxis;
    private boolean reorderingAllowed;
    public void listenReorder(
        Utilities.Callback2<Integer, ArrayList<UItem>> onReordered,
        boolean otherAxis
    ) {
        reorderingOnOtherAxis = otherAxis;
        itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(this);
        adapter.listenReorder(onReordered);
    }

    protected void swappedElements() {

    }

    protected void onReorderStart(ViewHolder viewHolder) {}
    protected void onReorderMoved(ViewHolder viewHolder) {}
    protected void onReorderEnd(ViewHolder viewHolder) {}
    protected boolean isReorderRemoving() { return false; }
    protected void onReorderRemove(ViewHolder viewHolder) {}
    private ViewHolder reorderingViewHolder;
    public boolean isReordering() { return reorderingViewHolder != null; }

    private boolean reorderingLongPressEnabled = true;
    public void setReorderLongPressEnabled(boolean enabled) {
        reorderingLongPressEnabled = enabled;
    }

    public boolean isReorderAllowed() {
        return reorderingAllowed;
    }

    public void allowReorder(boolean allow) {
        if (reorderingAllowed == allow) return;
        adapter.updateReorder(reorderingAllowed = allow);
        AndroidUtilities.forEachViews(this, view -> {
            adapter.updateReorder(getChildViewHolder(view), reorderingAllowed);
        });
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!hasSections()) adapter.drawWhiteSections(canvas, this);
        super.dispatchDraw(canvas);
    }

    public UItem findItemByItemId(int itemId) {
        for (int i = 0; i < adapter.getItemCount(); ++i) {
            UItem item = adapter.getItem(i);
            if (item != null && item.id == itemId) {
                return item;
            }
        }
        return null;
    }

    public View findViewByItemId(int itemId) {
        int position = -1;
        for (int i = 0; i < adapter.getItemCount(); ++i) {
            UItem item = adapter.getItem(i);
            if (item != null && item.id == itemId) {
                position = i;
                break;
            }
        }
        return findViewByPosition(position);
    }

    public View findViewByItemObject(Object object) {
        int position = -1;
        for (int i = 0; i < adapter.getItemCount(); ++i) {
            UItem item = adapter.getItem(i);
            if (item != null && item.object == object) {
                position = i;
                break;
            }
        }
        return findViewByPosition(position);
    }

    public int findPositionByItemId(int itemId) {
        int position = -1;
        for (int i = 0; i < adapter.getItemCount(); ++i) {
            UItem item = adapter.getItem(i);
            if (item != null && item.id == itemId) {
                return i;
            }
        }
        return -1;
    }

    private class TouchHelperCallback extends ItemTouchHelper.Callback {
        @Override
        public boolean isLongPressDragEnabled() {
            return reorderingAllowed && reorderingLongPressEnabled;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder) {
            if (reorderingAllowed && adapter.isReorderItem(viewHolder.getAdapterPosition())) {
                int flags = 0;
                if (layoutManager.getOrientation() == LinearLayoutManager.HORIZONTAL) {
                    flags |= ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
                    if (reorderingOnOtherAxis) {
                        flags |= ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                    }
                } else {
                    flags |= ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                    if (reorderingOnOtherAxis) {
                        flags |= ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
                    }
                }
                return makeMovementFlags(flags, 0);
            } else {
                return makeMovementFlags(0, 0);
            }
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder, @NonNull ViewHolder target) {
            if (!adapter.isReorderItem(viewHolder.getAdapterPosition()) || adapter.getReorderSectionId(viewHolder.getAdapterPosition()) != adapter.getReorderSectionId(target.getAdapterPosition())) {
                return false;
            }
            adapter.swapElements(viewHolder.getAdapterPosition(), target.getAdapterPosition());
            swappedElements();
            return true;
        }

        @Override
        public void onMoved(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder, int fromPos,
                            @NonNull ViewHolder target, int toPos, int x, int y) {}

        @Override
        public void onSwiped(@NonNull ViewHolder viewHolder, int direction) {

        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (viewHolder != null) {
                hideSelector(false);
            }
            if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                adapter.reorderDone();
                if (reorderingViewHolder != null) {
                    onReorderEnd(reorderingViewHolder);
                    reorderingViewHolder = null;
                }
            } else {
                cancelClickRunnables(false);
                if (viewHolder != null) {
                    viewHolder.itemView.setPressed(true);
                    if (viewHolder.itemView.getBackground() instanceof RichEditor.DraggingDrawable) {
                        ((RichEditor.DraggingDrawable) viewHolder.itemView.getBackground()).setDragging(true);
                    }
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        reorderingViewHolder = viewHolder;
                        onReorderStart(viewHolder);
                    }
                }
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && !isCurrentlyActive && isReorderRemoving()) {
                return;
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                onReorderMoved(viewHolder);
            }
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
            if (viewHolder.itemView.getBackground() instanceof RichEditor.DraggingDrawable) {
                ((RichEditor.DraggingDrawable) viewHolder.itemView.getBackground()).setDragging(false);
            }
            if (isReorderRemoving()) {
                onReorderRemove(viewHolder);
                viewHolder.itemView.animate()
                    .scaleX(0.5f).scaleY(0.5f)
                    .setDuration(200)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
            }
        }
    }

    public void setSections() {
        setSections(dp(12), dp(16), false);
    }
    public void setSections(boolean topPadding) {
        setSections(dp(12), dp(16), topPadding);
    }
    public void setSections(int padding, float roundRadius, boolean topPadding) {
        super.setSections(
            view -> {
                if (view.getParent() != this) return false;
                final ViewHolder viewHolder = getChildViewHolder(view);
                return !UniversalAdapter.isShadow(viewHolder.getItemViewType());
            },
            UniversalAdapter::isShadow,
            padding, roundRadius,
            super::drawBackgroundRect,
            topPadding
        );
    }
}
