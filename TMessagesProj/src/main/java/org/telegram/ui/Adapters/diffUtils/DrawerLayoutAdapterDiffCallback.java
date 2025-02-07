package org.telegram.ui.Adapters.diffUtils;
import androidx.recyclerview.widget.DiffUtil;
import org.telegram.ui.Adapters.DrawerLayoutAdapter;
import java.util.ArrayList;

/**
 * A DiffUtil.Callback implementation for calculating the differences between two lists of
 * {@link DrawerLayoutAdapter.Item} objects, specifically for updating the DrawerLayoutAdapter
 * efficiently.
 */
public class DrawerLayoutAdapterDiffCallback extends DiffUtil.Callback {
    private final ArrayList<DrawerLayoutAdapter.Item> oldItems;
    private final ArrayList<DrawerLayoutAdapter.Item> newItems;

    public DrawerLayoutAdapterDiffCallback(ArrayList<DrawerLayoutAdapter.Item> _oldItems, ArrayList<DrawerLayoutAdapter.Item> _newItems) {
        oldItems = _oldItems;
        newItems = _newItems;
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
        DrawerLayoutAdapter.Item oldItem = oldItems.get(oldItemPosition);
        DrawerLayoutAdapter.Item newItem = newItems.get(newItemPosition);

        if (oldItem == null || newItem == null) {
            return false;
        }

        return oldItem.id == newItem.id;
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        DrawerLayoutAdapter.Item oldItem = oldItems.get(oldItemPosition);
        DrawerLayoutAdapter.Item newItem = newItems.get(newItemPosition);

        if (oldItem == null || newItem == null) {
            return false;
        }

        return oldItem.areContentsTheSame(newItem);
    }
}