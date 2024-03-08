package org.telegram.ui.Components.ListView;

import android.util.Log;

import androidx.recyclerview.widget.DiffUtil;

import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public abstract class AdapterWithDiffUtils extends RecyclerListView.SelectionAdapter {

    DiffUtilsCallback callback = new DiffUtilsCallback();

    public void setItems(ArrayList<? extends Item> oldItems, ArrayList<? extends Item> newItems) {
        if (newItems == null) {
            newItems = new ArrayList<>();
        }
        callback.setItems(oldItems, newItems);
        DiffUtil.calculateDiff(callback).dispatchUpdatesTo(this);
    }

    public static abstract class Item {
        public final int viewType;
        public boolean selectable;

        public Item(int viewType, boolean selectable) {
            this.viewType = viewType;
            this.selectable = selectable;
        }

        boolean compare(Item item) {
            if (viewType != item.viewType) {
                return false;
            }
            if (this.equals(item)) {
                return true;
            }
            return false;
        }
    }

    private class DiffUtilsCallback extends DiffUtil.Callback {

        ArrayList<? extends Item> oldItems;
        ArrayList<? extends Item> newItems;

        public void setItems(ArrayList<? extends Item> oldItems, ArrayList<? extends Item> newItems) {
            this.oldItems = oldItems;
            this.newItems = newItems;
        }

        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return newItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldItems.get(oldItemPosition).compare(newItems.get(newItemPosition));
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return false;
        }
    }
}
