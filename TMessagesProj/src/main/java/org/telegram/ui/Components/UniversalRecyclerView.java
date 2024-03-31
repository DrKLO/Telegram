package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.FiltersSetupActivity;

import java.util.ArrayList;

import kotlinx.coroutines.android.AndroidDispatcherFactory;

public class UniversalRecyclerView extends RecyclerListView {

    public final LinearLayoutManager layoutManager;
    public final UniversalAdapter adapter;
    private ItemTouchHelper itemTouchHelper;

    private boolean doNotDetachViews;
    public void doNotDetachViews() {
        doNotDetachViews = true;
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
        super(context, resourcesProvider);

        setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            protected int getExtraLayoutSpace(State state) {
                if (doNotDetachViews) return AndroidUtilities.displaySize.y;
                return super.getExtraLayoutSpace(state);
            }
        });
        setAdapter(adapter = new UniversalAdapter(this, context, currentAccount, classGuid, fillItems, resourcesProvider));

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
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        setItemAnimator(itemAnimator);
    }

    private boolean reorderingAllowed;
    public void listenReorder(
        Utilities.Callback2<Integer, ArrayList<UItem>> onReordered
    ) {
        itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(this);
        adapter.listenReorder(onReordered);
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
        adapter.drawWhiteSections(canvas, this);
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

    public View findViewByPosition(int position) {
        if (position == NO_POSITION) return null;
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            int childPosition = getChildAdapterPosition(child);
            if (childPosition != NO_POSITION && childPosition == position) {
                return child;
            }
        }
        return null;
    }

    private class TouchHelperCallback extends ItemTouchHelper.Callback {
        @Override
        public boolean isLongPressDragEnabled() {
            return reorderingAllowed;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder) {
            if (reorderingAllowed && adapter.isReorderItem(viewHolder.getAdapterPosition())) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
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
            return true;
        }

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
            } else {
                cancelClickRunnables(false);
                if (viewHolder != null) {
                    viewHolder.itemView.setPressed(true);
                }
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
        }
    }
}
