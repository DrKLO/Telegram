/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.LocationDirectionCell;
import org.telegram.ui.Cells.LocationLoadingCell;
import org.telegram.ui.Cells.LocationPoweredCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SharingLiveLocationCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LocationActivity;

import java.util.ArrayList;
import java.util.Locale;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.vision.Frame;

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
    private int shareLiveLocationPotistion = -1;
    private MessageObject currentMessageObject;
    private TLRPC.TL_channelLocation chatLocation;
    private ArrayList<LocationActivity.LiveLocation> currentLiveLocations = new ArrayList<>();
    private boolean fetchingLocation;
    private boolean needEmptyView;

    private Runnable updateRunnable;
    private final Theme.ResourcesProvider resourcesProvider;

    private FlickerLoadingView globalGradientView;

    public LocationActivityAdapter(Context context, int type, long did, boolean emptyView, Theme.ResourcesProvider resourcesProvider) {
        super();
        mContext = context;
        locationType = type;
        dialogId = did;
        needEmptyView = emptyView;
        this.resourcesProvider = resourcesProvider;

        globalGradientView = new FlickerLoadingView(context);
        globalGradientView.setIsSingleCell(true);
    }

    private boolean myLocationDenied = false;
    public void setMyLocationDenied(boolean myLocationDenied) {
        if (this.myLocationDenied == myLocationDenied)
            return;
        this.myLocationDenied = myLocationDenied;
        notifyDataSetChanged();
    }

    public void setOverScrollHeight(int value) {
        overScrollHeight = value;
        if (emptyCell != null) {
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) emptyCell.getLayoutParams();
            if (lp == null) {
                lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overScrollHeight);
            } else {
                lp.height = overScrollHeight;
            }
            emptyCell.setLayoutParams(lp);
            emptyCell.forceLayout();
        }
    }

    public void setUpdateRunnable(Runnable runnable) {
        updateRunnable = runnable;
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
            notifyItemChanged(1, new Object());
            updateLiveLocations();
        } else if (locationType != 2) {
            updateCell();
        } else {
            updateLiveLocations();
        }
    }

    public void updateLiveLocationCell() {
        if (shareLiveLocationPotistion > 0) {
            notifyItemChanged(shareLiveLocationPotistion);
        }
    }

    public void updateLiveLocations() {
        if (!currentLiveLocations.isEmpty()) {
            notifyItemRangeChanged(2, currentLiveLocations.size(), new Object());
        }
    }

    public void setCustomLocation(Location location) {
        customLocation = location;
        fetchLocationAddress();
        updateCell();
    }

    public void setLiveLocations(ArrayList<LocationActivity.LiveLocation> liveLocations) {
        currentLiveLocations = new ArrayList<>(liveLocations);
        long uid = UserConfig.getInstance(currentAccount).getClientUserId();
        for (int a = 0; a < currentLiveLocations.size(); a++) {
            if (currentLiveLocations.get(a).id == uid || currentLiveLocations.get(a).object.out) {
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
            if (locationType == LocationActivity.LOCATION_TYPE_GROUP || customLocation != null) {
                String address = "";
                if (!TextUtils.isEmpty(addressName)) {
                    address = addressName;
                } else if (customLocation == null && gpsLocation == null || fetchingLocation) {
                    address = LocaleController.getString("Loading", R.string.Loading);
                } else if (customLocation != null) {
                    address = String.format(Locale.US, "(%f,%f)", customLocation.getLatitude(), customLocation.getLongitude());
                } else if (gpsLocation != null) {
                    address = String.format(Locale.US, "(%f,%f)", gpsLocation.getLatitude(), gpsLocation.getLongitude());
                } else if (!myLocationDenied) {
                    address = LocaleController.getString("Loading", R.string.Loading);
                }
                if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
                    sendLocationCell.setText(LocaleController.getString("ChatSetThisLocation", R.string.ChatSetThisLocation), address);
                } else {
                    sendLocationCell.setText(LocaleController.getString("SendSelectedLocation", R.string.SendSelectedLocation), address);
                }
                sendLocationCell.setHasLocation(true);
            } else {
                if (gpsLocation != null) {
                    sendLocationCell.setText(LocaleController.getString("SendLocation", R.string.SendLocation), LocaleController.formatString("AccurateTo", R.string.AccurateTo, LocaleController.formatPluralString("Meters", (int) gpsLocation.getAccuracy())));
                    sendLocationCell.setHasLocation(true);
                } else {
                    sendLocationCell.setText(LocaleController.getString("SendLocation", R.string.SendLocation), myLocationDenied ? "" : LocaleController.getString("Loading", R.string.Loading));
                    sendLocationCell.setHasLocation(!myLocationDenied);
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

    protected void onDirectionClick() {

    }

    public void fetchLocationAddress() {
        if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
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
            fetchingLocation = true;
            updateCell();
            LocationController.fetchLocationAddress(location, this);
        } else {
            Location location;
            if (customLocation != null) {
                location = customLocation;
            } else {
                return;
            }
            if (previousFetchedLocation == null || previousFetchedLocation.distanceTo(location) > 20) {
                addressName = null;
            }
            fetchingLocation = true;
            updateCell();
            LocationController.fetchLocationAddress(location, this);
        }
    }

    @Override
    public int getItemCount() {
        if (locationType == LocationActivity.LOCATION_TYPE_LIVE_VIEW) {
            return 2;
        } else if (locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) {
            return 2;
        } else if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
            return 2;
        } else if (currentMessageObject != null) {
            return 2 + (currentLiveLocations.isEmpty() ? 1 : currentLiveLocations.size() + 3);
        } else if (locationType == 2) {
            return 2 + currentLiveLocations.size();
        } else {
            if (searching || !searched || places.isEmpty()) {
                return (locationType != LocationActivity.LOCATION_TYPE_SEND ? 6 : 5) + (!myLocationDenied && (searching || !searched) ? 2 : 0) + (needEmptyView ? 1 : 0) - (myLocationDenied ? 2 : 0);
            }
            return (locationType == LocationActivity.LOCATION_TYPE_SEND_WITH_LIVE ? 6 : 5) + places.size() + (needEmptyView ? 1 : 0);
        }
    }

    private FrameLayout emptyCell;

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
//                view = emptyCell = new EmptyCell(mContext) {
//                    @Override
//                    public ViewPropertyAnimator animate() {
//                        ViewPropertyAnimator animator = super.animate();
//                        if (Build.VERSION.SDK_INT >= 19) {
//                            animator.setUpdateListener(animation -> {
//                                if (updateRunnable != null) {
//                                    updateRunnable.run();
//                                }
//                            });
//                        }
//                        return animator;
//                    }
//                };
                view = emptyCell = new FrameLayout(mContext);
                emptyCell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overScrollHeight));
                break;
            case 1:
                view = new SendLocationCell(mContext, false, resourcesProvider);
                break;
            case 2:
                view = new HeaderCell(mContext, resourcesProvider);
                break;
            case 3:
                LocationCell locationCell = new LocationCell(mContext, false, resourcesProvider);
                view = locationCell;
                break;
            case 4:
                view = new LocationLoadingCell(mContext, resourcesProvider);
                break;
            case 5:
                view = new LocationPoweredCell(mContext, resourcesProvider);
                break;
            case 6: {
                SendLocationCell cell = new SendLocationCell(mContext, true, resourcesProvider);
                cell.setDialogId(dialogId);
                view = cell;
                break;
            }
            case 7:
                view = new SharingLiveLocationCell(mContext, true, locationType == LocationActivity.LOCATION_TYPE_GROUP || locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW ? 16 : 54, resourcesProvider);
                break;
            case 8: {
                LocationDirectionCell cell = new LocationDirectionCell(mContext, resourcesProvider);
                cell.setOnButtonClick(v -> onDirectionClick());
                view = cell;
                break;
            }
            case 9: {
                view = new ShadowSectionCell(mContext);
                Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)), drawable);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
                break;
            }
            case 10:
            default: {
                view = new View(mContext);
                break;
            }
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0:
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
                if (lp == null) {
                    lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overScrollHeight);
                } else {
                    lp.height = overScrollHeight;
                }
                holder.itemView.setLayoutParams(lp);
                break;
            case 1:
                sendLocationCell = (SendLocationCell) holder.itemView;
                updateCell();
                break;
            case 2: {
                HeaderCell cell = (HeaderCell) holder.itemView;
                if (currentMessageObject != null) {
                    cell.setText(LocaleController.getString("LiveLocations", R.string.LiveLocations));
                } else {
                    cell.setText(LocaleController.getString("NearbyVenue", R.string.NearbyVenue));
                }
                break;
            }
            case 3: {
                LocationCell cell = (LocationCell) holder.itemView;
                if (locationType == 0) {
                    position -= 4;
                } else {
                    position -= 5;
                }
                TLRPC.TL_messageMediaVenue place = position < 0 || position >= places.size() || !searched ? null : places.get(position);
                String iconUrl = position < 0 || position >= iconUrls.size() || !searched ? null : iconUrls.get(position);
                cell.setLocation(place, iconUrl, position, true);
                break;
            }
            case 4:
                ((LocationLoadingCell) holder.itemView).setLoading(searching);
                break;
            case 6:
                ((SendLocationCell) holder.itemView).setHasLocation(gpsLocation != null);
                break;
            case 7:
                SharingLiveLocationCell locationCell = (SharingLiveLocationCell) holder.itemView;
                if (locationType == LocationActivity.LOCATION_TYPE_LIVE_VIEW) {
                    locationCell.setDialog(currentMessageObject, gpsLocation, myLocationDenied);
                } else if (chatLocation != null) {
                    locationCell.setDialog(dialogId, chatLocation);
                } else if (currentMessageObject != null && position == 1) {
                    locationCell.setDialog(currentMessageObject, gpsLocation, myLocationDenied);
                } else {
                    locationCell.setDialog(currentLiveLocations.get(position - (currentMessageObject != null ? 5 : 2)), gpsLocation);
                }
                break;
            case 10:
                View emptyView = holder.itemView;
                emptyView.setBackgroundColor(Theme.getColor(myLocationDenied ? Theme.key_dialogBackgroundGray : Theme.key_dialogBackground));
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
            } else if (i > 4 && i < places.size() + 4) {
                return currentLiveLocations.get(i - 5);
            }
        } else if (locationType == 2) {
            if (i >= 2) {
                return currentLiveLocations.get(i - 2);
            }
            return null;
        } else if (locationType == 1) {
            if (i > 4 && i < places.size() + 5) {
                return places.get(i - 5);
            }
        } else {
            if (i > 3 && i < places.size() + 4) {
                return places.get(i - 4);
            }
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return 0;
        }
        if (locationType == LocationActivity.LOCATION_TYPE_LIVE_VIEW) {
            return 7;
        }
        if (needEmptyView && position == getItemCount() - 1) {
            return 10;
        }
        if (locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) {
            return 7;
        }
        if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
            return 1;
        }
        if (currentMessageObject != null) {
            if (currentLiveLocations.isEmpty()) {
                if (position == 2) {
                    return 8;
                }
            } else {
                if (position == 2) {
                    return 9;
                } else if (position == 3) {
                    return 2;
                } else if (position == 4) {
                    shareLiveLocationPotistion = position;
                    return 6;
                }
            }
            return 7;
        }
        if (locationType == 2) {
            if (position == 1) {
                shareLiveLocationPotistion = position;
                return 6;
            } else {
                return 7;
            }
        }
        if (locationType == LocationActivity.LOCATION_TYPE_SEND_WITH_LIVE) {
            if (position == 1) {
                return 1;
            } else if (position == 2) {
                shareLiveLocationPotistion = position;
                return 6;
            } else if (position == 3) {
                return 9;
            } else if (position == 4) {
                return 2;
            } else if (searching || places.isEmpty() || !searched) {
                if (position <= 4 + 3 && (searching || !searched) && !myLocationDenied)
                    return 3;
                return 4;
            } else if (position == places.size() + 5) {
                return 5;
            }
        } else {
            if (position == 1) {
                return 1;
            } else if (position == 2) {
                return 9;
            } else if (position == 3) {
                return 2;
            } else if (searching || places.isEmpty()) {
                if (position <= 3 + 3 && (searching || !searched) && !myLocationDenied)
                    return 3;
                return 4;
            } else if (position == places.size() + 4) {
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

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
