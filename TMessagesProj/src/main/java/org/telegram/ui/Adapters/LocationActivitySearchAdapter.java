/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.RecyclerListView;

import androidx.recyclerview.widget.RecyclerView;

public class LocationActivitySearchAdapter extends BaseLocationAdapter {

    private static final int VIEW_TYPE_LOCATION = 0;
    private static final int VIEW_TYPE_SECTION = 1;

    private Context mContext;
    private Theme.ResourcesProvider resourcesProvider;

    private boolean myLocationDenied = false;
    public void setMyLocationDenied(boolean myLocationDenied) {
        if (this.myLocationDenied == myLocationDenied)
            return;
        this.myLocationDenied = myLocationDenied;
    }

    private FlickerLoadingView globalGradientView;
    public LocationActivitySearchAdapter(Context context, Theme.ResourcesProvider resourcesProvider, boolean stories, boolean biz) {
        super(stories, biz);

        mContext = context;
        this.resourcesProvider = resourcesProvider;

        globalGradientView = new FlickerLoadingView(context);
        globalGradientView.setIsSingleCell(true);
    }

    @Override
    public int getItemCount() {
        if (locations.isEmpty()) {
            return isSearching() ? 3 : 0;
        }
        return 1 + locations.size();
    }

    public boolean isEmpty() { return locations.isEmpty(); }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_LOCATION) {
            view = new LocationCell(mContext, false, resourcesProvider);
        } else {
            view = new GraySectionCell(mContext, resourcesProvider);
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == VIEW_TYPE_LOCATION) {
            final TLRPC.TL_messageMediaVenue place = getItem(position);
            LocationCell locationCell = (LocationCell) holder.itemView;
            locationCell.setLocation(place, place != null ? 2 : position, position != getItemCount() - 1);
        } else if (holder.getItemViewType() == VIEW_TYPE_SECTION) {
            ((GraySectionCell) holder.itemView).setText(LocaleController.getString(R.string.LocationOnMap));
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 && !locations.isEmpty()) {
            return VIEW_TYPE_SECTION;
        }
        return VIEW_TYPE_LOCATION;
    }

    public TLRPC.TL_messageMediaVenue getItem(int position) {
        if (locations.isEmpty()) {
            return null;
        }
        position--;
        if (position >= 0 && position < locations.size()) {
            return locations.get(position);
        }
        return null;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return true;
    }
}
