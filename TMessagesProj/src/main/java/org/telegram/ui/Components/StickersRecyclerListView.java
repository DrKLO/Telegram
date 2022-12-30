package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;

import java.util.ArrayList;

public class StickersRecyclerListView extends RecyclerListView {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_STICKER = 1;

    public static class StickerHeaderItem extends Item {
        private TLRPC.StickerSet set;
        public StickerHeaderItem(TLRPC.StickerSet set) {
            super(VIEW_TYPE_HEADER, false);
            this.set = set;
        }

        public StickerHeaderItem(TLRPC.TL_messages_stickerSet stickerSet) {
            super(VIEW_TYPE_HEADER, false);
            if (stickerSet != null) {
                this.set = stickerSet.set;
            }
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof StickerHeaderItem)) {
                return false;
            }
            TLRPC.StickerSet otherSet = ((StickerHeaderItem) obj).set;
            return (set == null) == (otherSet == null) && (set != null && otherSet.id == set.id);
        }
    }

    public static class StickerItem extends Item {
        private TLRPC.Document document;
        public StickerItem(TLRPC.Document document) {
            super(VIEW_TYPE_STICKER, true);
            this.document = document;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof StickerItem)) {
                return false;
            }
            TLRPC.Document otherDocument = ((StickerItem) obj).document;
            return (document == null) == (otherDocument == null) && (document != null && otherDocument.id == document.id);
        }
    }

    private static abstract class Item extends  AdapterWithDiffUtils.Item {
        public Item(int viewType, boolean selectable) {
            super(viewType, selectable);
        }
    }

    private Theme.ResourcesProvider resourcesProvider;

    private GridLayoutManager layoutManager;
    private AdapterWithDiffUtils adapter;

    public StickersRecyclerListView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setAdapter(adapter = new Adapter());
        setLayoutManager(layoutManager = new GridLayoutManager(context, 5));
    }

    private ArrayList<Item> items = new ArrayList<>();
    private ArrayList<Item> oldItems = new ArrayList<>();

    public void setItems(ArrayList<Item> newItems) {
        oldItems.clear();
        oldItems.addAll(items);

        items.clear();
        items.addAll(newItems);

        adapter.setItems(newItems, oldItems);
    }

    public void setSpanCount(int spanCount) {
        layoutManager.setSpanCount(spanCount);
    }

    private class Adapter extends AdapterWithDiffUtils {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            if (viewType == VIEW_TYPE_HEADER) {

            } else if (viewType == VIEW_TYPE_STICKER) {

            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final int viewType = holder.getItemViewType();
            if (viewType == VIEW_TYPE_HEADER) {
                StickerItem item = (StickerItem) items.get(position);

            } else if (viewType == VIEW_TYPE_STICKER) {

            }
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(ViewHolder holder) {
            return false;
        }
    }
}
