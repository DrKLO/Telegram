/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.ViewGroup;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.RecyclerListView;

import androidx.recyclerview.widget.RecyclerView;

public class LocationActivitySearchAdapter extends BaseLocationAdapter {

    private Context mContext;

    private FlickerLoadingView globalGradientView;
    public LocationActivitySearchAdapter(Context context) {
        super();
        mContext = context;

        globalGradientView = new FlickerLoadingView(context);
        globalGradientView.setIsSingleCell(true);
    }

    @Override
    public int getItemCount() {
        return (isSearching() ? 3 : places.size());
    }

    public boolean isEmpty() { return places.size() == 0; }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LocationCell locationCell = new LocationCell(mContext, false, null);
        return new RecyclerListView.Holder(locationCell);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        TLRPC.TL_messageMediaVenue place = getItem(position);
        String iconUrl = !isSearching() && position >= 0 && position < iconUrls.size() ? iconUrls.get(position) : null;

        LocationCell locationCell = (LocationCell) holder.itemView;
        locationCell.setLocation(place, iconUrl, position, position != getItemCount() - 1);
    }

    public TLRPC.TL_messageMediaVenue getItem(int i) {
        if (isSearching())
            return null;
        if (i >= 0 && i < places.size()) {
            return places.get(i);
        }
        return null;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return true;
    }

    @Override
    protected void notifyStartSearch(boolean wasSearching, int oldItemCount, boolean animated) {
        if (wasSearching)
            return;
        notifyDataSetChanged();
    }
}
