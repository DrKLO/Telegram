/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.location.Location;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.GreySectionCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.LocationLoadingCell;
import org.telegram.ui.Cells.LocationPoweredCell;
import org.telegram.ui.Cells.SendLocationCell;

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
    public int getCount() {
        if (searching || !searching && places.isEmpty()) {
            return 4;
        }
        return 3 + places.size() + (places.isEmpty() ? 0 : 1);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (i == 0) {
            if (view == null) {
                view = new EmptyCell(mContext);
            }
            ((EmptyCell) view).setHeight(overScrollHeight);
        } else if (i == 1) {
            if (view == null) {
                view = new SendLocationCell(mContext);
            }
            sendLocationCell = (SendLocationCell) view;
            updateCell();
            return view;
        } else if (i == 2) {
            if (view == null) {
                view = new GreySectionCell(mContext);
            }
            ((GreySectionCell) view).setText(LocaleController.getString("NearbyPlaces", R.string.NearbyPlaces));
        } else if (searching || !searching && places.isEmpty()) {
            if (view == null) {
                view = new LocationLoadingCell(mContext);
            }
            ((LocationLoadingCell) view).setLoading(searching);
        } else if (i == places.size() + 3) {
            if (view == null) {
                view = new LocationPoweredCell(mContext);
            }
        } else {
            if (view == null) {
                view = new LocationCell(mContext);
            }
            ((LocationCell) view).setLocation(places.get(i - 3), iconUrls.get(i - 3), true);
        }
        return view;
    }

    @Override
    public TLRPC.TL_messageMediaVenue getItem(int i) {
        if (i > 2 && i < places.size() + 3) {
            return places.get(i - 3);
        }
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
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
    public int getViewTypeCount() {
        return 6;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return !(position == 2 || position == 0 || position == 3 && (searching || !searching && places.isEmpty()) || position == places.size() + 3);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}
