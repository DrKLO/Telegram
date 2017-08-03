/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.location.Location;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.LocationLoadingCell;
import org.telegram.ui.Cells.LocationPoweredCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Locale;

public class LocationActivityAdapter extends BaseLocationAdapter {

    private Context mContext;
    private int overScrollHeight;
    private SendLocationCell sendLocationCell;
    private Location gpsLocation;
    private Location customLocation;

    public LocationActivityAdapter(Context context) {
        super();
        mContext = context;
    }

    public void setOverScrollHeight(int value) {
        overScrollHeight = value;
    }

    public void setGpsLocation(Location location) {
        gpsLocation = location;
        updateCell();
    }

    public void setCustomLocation(Location location) {
        customLocation = location;
        updateCell();
    }

    private void updateCell() {
        if (sendLocationCell != null) {
            if (customLocation != null) {
                sendLocationCell.setText(LocaleController.getString("SendSelectedLocation", R.string.SendSelectedLocation), String.format(Locale.US, "(%f,%f)", customLocation.getLatitude(), customLocation.getLongitude()));
            } else {
                if (gpsLocation != null) {
                    sendLocationCell.setText(LocaleController.getString("SendLocation", R.string.SendLocation), LocaleController.formatString("AccurateTo", R.string.AccurateTo, LocaleController.formatPluralString("Meters", (int) gpsLocation.getAccuracy())));
                } else {
                    sendLocationCell.setText(LocaleController.getString("SendLocation", R.string.SendLocation), LocaleController.getString("Loading", R.string.Loading));
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        if (searching || !searching && places.isEmpty()) {
            return 4;
        }
        return 3 + places.size() + (places.isEmpty() ? 0 : 1);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new EmptyCell(mContext);
                break;
            case 1:
                view = new SendLocationCell(mContext);
                break;
            case 2:
                view = new GraySectionCell(mContext);
                break;
            case 3:
                view = new LocationCell(mContext);
                break;
            case 4:
                view = new LocationLoadingCell(mContext);
                break;
            case 5:
            default:
                view = new LocationPoweredCell(mContext);
                break;
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0:
                ((EmptyCell) holder.itemView).setHeight(overScrollHeight);
                break;
            case 1:
                sendLocationCell = (SendLocationCell) holder.itemView;
                updateCell();
                break;
            case 2:
                ((GraySectionCell) holder.itemView).setText(LocaleController.getString("NearbyPlaces", R.string.NearbyPlaces));
                break;
            case 3:
                ((LocationCell) holder.itemView).setLocation(places.get(position - 3), iconUrls.get(position - 3), true);
                break;
            case 4:
                ((LocationLoadingCell) holder.itemView).setLoading(searching);
                break;
        }
    }

    public TLRPC.TL_messageMediaVenue getItem(int i) {
        if (i > 2 && i < places.size() + 3) {
            return places.get(i - 3);
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return 0;
        } else if (position == 1) {
            return 1;
        } else if (position == 2) {
            return 2;
        } else if (searching || !searching && places.isEmpty()) {
            return 4;
        } else if (position == places.size() + 3) {
            return 5;
        }
        return 3;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int position = holder.getAdapterPosition();
        return !(position == 2 || position == 0 || position == 3 && (searching || !searching && places.isEmpty()) || position == places.size() + 3);
    }
}
