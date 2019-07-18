/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.location.Location;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.LocationLoadingCell;
import org.telegram.ui.Cells.LocationPoweredCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Cells.SharingLiveLocationCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LocationActivity;

import java.util.ArrayList;
import java.util.Locale;

import androidx.recyclerview.widget.RecyclerView;

public class LocationActivityAdapter extends BaseLocationAdapter implements LocationController.LocationFetchCallback {

    private int currentAccount = UserConfig.selectedAccount;
    private Context mContext;
    private int overScrollHeight;
    private SendLocationCell sendLocationCell;
    private Location gpsLocation;
    private Location customLocation;
    private String addressName;
    private Location previousFetchedLocation;
    private int locationType;
    private long dialogId;
    private boolean pulledUp;
    private int shareLiveLocationPotistion = -1;
    private MessageObject currentMessageObject;
    private TLRPC.TL_channelLocation chatLocation;
    private ArrayList<LocationActivity.LiveLocation> currentLiveLocations = new ArrayList<>();
    private boolean fetchingLocation;

    public LocationActivityAdapter(Context context, int type, long did) {
        super();
        mContext = context;
        locationType = type;
        dialogId = did;
    }

    public void setOverScrollHeight(int value) {
        overScrollHeight = value;
    }

    public void setGpsLocation(Location location) {
        boolean notSet = gpsLocation == null;
        gpsLocation = location;
        if (customLocation == null) {
            fetchLocationAddress();
        }
        if (notSet && shareLiveLocationPotistion > 0) {
            notifyItemChanged(shareLiveLocationPotistion);
        }
        if (currentMessageObject != null) {
            notifyItemChanged(1);
            updateLiveLocations();
        } else if (locationType != 2) {
            updateCell();
        } else {
            updateLiveLocations();
        }
    }

    public void updateLiveLocations() {
        if (!currentLiveLocations.isEmpty()) {
            notifyItemRangeChanged(2, currentLiveLocations.size());
        }
    }

    public void setCustomLocation(Location location) {
        customLocation = location;
        fetchLocationAddress();
        updateCell();
    }

    public void setLiveLocations(ArrayList<LocationActivity.LiveLocation> liveLocations) {
        currentLiveLocations = new ArrayList<>(liveLocations);
        int uid = UserConfig.getInstance(currentAccount).getClientUserId();
        for (int a = 0; a < currentLiveLocations.size(); a++) {
            if (currentLiveLocations.get(a).id == uid) {
                currentLiveLocations.remove(a);
                break;
            }
        }
        notifyDataSetChanged();
    }

    public void setMessageObject(MessageObject messageObject) {
        currentMessageObject = messageObject;
        notifyDataSetChanged();
    }

    public void setChatLocation(TLRPC.TL_channelLocation location) {
        chatLocation = location;
    }

    private void updateCell() {
        if (sendLocationCell != null) {
            if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
                String address;
                if (addressName != null) {
                    address = addressName;
                } else if (customLocation == null && gpsLocation == null || fetchingLocation) {
                    address = LocaleController.getString("Loading", R.string.Loading);
                } else if (customLocation != null) {
                    address = String.format(Locale.US, "(%f,%f)", customLocation.getLatitude(), customLocation.getLongitude());
                } else if (gpsLocation != null) {
                    address = String.format(Locale.US, "(%f,%f)", gpsLocation.getLatitude(), gpsLocation.getLongitude());
                } else {
                    address = LocaleController.getString("Loading", R.string.Loading);
                }
                sendLocationCell.setText(LocaleController.getString("ChatSetThisLocation", R.string.ChatSetThisLocation), address);
            } else if (customLocation != null) {
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

    private String getAddressName() {
        return addressName;
    }

    @Override
    public void onLocationAddressAvailable(String address, String displayAddress, Location location) {
        fetchingLocation = false;
        previousFetchedLocation = location;
        addressName = address;
        updateCell();
    }

    public void fetchLocationAddress() {
        if (locationType != LocationActivity.LOCATION_TYPE_GROUP) {
            return;
        }
        Location location;
        if (customLocation != null) {
            location = customLocation;
        } else if (gpsLocation != null) {
            location = gpsLocation;
        } else {
            return;
        }
        if (previousFetchedLocation == null || previousFetchedLocation.distanceTo(location) > 100) {
            addressName = null;
        }
        updateCell();
        fetchingLocation = true;
        LocationController.fetchLocationAddress(location, this);
    }

    @Override
    public int getItemCount() {
        if (locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) {
            return 2;
        } else if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
            return 2;
        } else if (currentMessageObject != null) {
            return 2 + (currentLiveLocations.isEmpty() ? 0 : currentLiveLocations.size() + 2);
        } else if (locationType == 2) {
            return 2 + currentLiveLocations.size();
        } else {
            if (searching || !searching && places.isEmpty()) {
                return locationType != 0 ? 5 : 4;
            }
            if (locationType == 1) {
                return 4 + places.size() + (places.isEmpty() ? 0 : 1);
            } else {
                return 3 + places.size() + (places.isEmpty() ? 0 : 1);
            }
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new EmptyCell(mContext);
                break;
            case 1:
                view = new SendLocationCell(mContext, false);
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
                view = new LocationPoweredCell(mContext);
                break;
            case 6:
                SendLocationCell cell = new SendLocationCell(mContext, true);
                cell.setDialogId(dialogId);
                view = cell;
                break;
            case 7:
            default:
                view = new SharingLiveLocationCell(mContext, true, locationType == LocationActivity.LOCATION_TYPE_GROUP || locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW ? 16 : 54);
                break;
        }
        return new RecyclerListView.Holder(view);
    }

    public void setPulledUp() {
        if (pulledUp) {
            return;
        }
        pulledUp = true;
        AndroidUtilities.runOnUIThread(() -> notifyItemChanged(locationType == 0 ? 2 : 3));
    }

    public boolean isPulledUp() {
        return pulledUp;
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
                if (currentMessageObject != null) {
                    ((GraySectionCell) holder.itemView).setText(LocaleController.getString("LiveLocations", R.string.LiveLocations));
                } else if (pulledUp) {
                    ((GraySectionCell) holder.itemView).setText(LocaleController.getString("NearbyPlaces", R.string.NearbyPlaces));
                } else {
                    ((GraySectionCell) holder.itemView).setText(LocaleController.getString("ShowNearbyPlaces", R.string.ShowNearbyPlaces));
                }
                break;
            case 3:
                if (locationType == 0) {
                    ((LocationCell) holder.itemView).setLocation(places.get(position - 3), iconUrls.get(position - 3), true);
                } else {
                    ((LocationCell) holder.itemView).setLocation(places.get(position - 4), iconUrls.get(position - 4), true);
                }
                break;
            case 4:
                ((LocationLoadingCell) holder.itemView).setLoading(searching);
                break;
            case 6:
                ((SendLocationCell) holder.itemView).setHasLocation(gpsLocation != null);
                break;
            case 7:
                SharingLiveLocationCell locationCell = (SharingLiveLocationCell) holder.itemView;
                if (chatLocation != null) {
                    locationCell.setDialog(dialogId, chatLocation);
                } else if (currentMessageObject != null && position == 1) {
                    locationCell.setDialog(currentMessageObject, gpsLocation);
                } else {
                    locationCell.setDialog(currentLiveLocations.get(position - (currentMessageObject != null ? 4 : 2)), gpsLocation);
                }
                break;
        }
    }

    public Object getItem(int i) {
        if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
            if (addressName == null) {
                return null;
            } else {
                TLRPC.TL_messageMediaVenue venue = new TLRPC.TL_messageMediaVenue();
                venue.address = addressName;
                venue.geo = new TLRPC.TL_geoPoint();
                if (customLocation != null) {
                    venue.geo.lat = customLocation.getLatitude();
                    venue.geo._long = customLocation.getLongitude();
                } else if (gpsLocation != null) {
                    venue.geo.lat = gpsLocation.getLatitude();
                    venue.geo._long = gpsLocation.getLongitude();
                }
                return venue;
            }
        } else if (currentMessageObject != null) {
            if (i == 1) {
                return currentMessageObject;
            } else if (i > 3 && i < places.size() + 3) {
                return currentLiveLocations.get(i - 4);
            }
        } else if (locationType == 2) {
            if (i >= 2) {
                return currentLiveLocations.get(i - 2);
            }
            return null;
        } else if (locationType == 1) {
            if (i > 3 && i < places.size() + 4) {
                return places.get(i - 4);
            }
        } else {
            if (i > 2 && i < places.size() + 3) {
                return places.get(i - 3);
            }
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return 0;
        }
        if (locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) {
            return 7;
        } else if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
            return 1;
        } else if (currentMessageObject != null) {
            if (position == 2) {
                return 2;
            } else if (position == 3) {
                shareLiveLocationPotistion = position;
                return 6;
            } else {
                return 7;
            }
        } else if (locationType == 2) {
            if (position == 1) {
                shareLiveLocationPotistion = position;
                return 6;
            } else {
                return 7;
            }
        } else if (locationType == 1) {
            if (position == 1) {
                return 1;
            } else if (position == 2) {
                shareLiveLocationPotistion = position;
                return 6;
            } else if (position == 3) {
                return 2;
            } else if (searching || !searching && places.isEmpty()) {
                return 4;
            } else if (position == places.size() + 4) {
                return 5;
            }
        } else {
            if (position == 1) {
                return 1;
            } else if (position == 2) {
                return 2;
            } else if (searching || !searching && places.isEmpty()) {
                return 4;
            } else if (position == places.size() + 3) {
                return 5;
            }
        }
        return 3;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int viewType = holder.getItemViewType();
        if (viewType == 6) {
            return !(LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId) == null && gpsLocation == null);
        }
        return viewType == 1 || viewType == 3 || viewType == 7;
    }
}
