/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.LocationCell;

public class LocationActivitySearchAdapter extends BaseLocationAdapter {

    private Context mContext;

    public LocationActivitySearchAdapter(Context context) {
        super();
        mContext = context;
    }

    @Override
    public int getCount() {
        return places.size();
    }

    @Override
    public boolean isEmpty() {
        return places.isEmpty();
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = new LocationCell(mContext);
        }
        ((LocationCell) view).setLocation(places.get(i), iconUrls.get(i), i != places.size() - 1);
        return view;
    }

    @Override
    public TLRPC.TL_messageMediaVenue getItem(int i) {
        if (i >= 0 && i < places.size()) {
            return places.get(i);
        }
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
