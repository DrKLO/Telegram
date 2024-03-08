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
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.LocationDirectionCell;
import org.telegram.ui.Cells.LocationLoadingCell;
import org.telegram.ui.Cells.LocationPoweredCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SharingLiveLocationCell;
import org.telegram.ui.Components.ChatAttachAlertLocationLayout;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
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
    private String overrideAddressName;
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

    public boolean animated = true;
    public TLRPC.TL_messageMediaVenue city, street;

    public void setAddressNameOverride(String address) {
        overrideAddressName = address;
        updateCell();
    }

    public LocationActivityAdapter(Context context, int type, long did, boolean emptyView, Theme.ResourcesProvider resourcesProvider, boolean stories, boolean biz) {
        super(stories, biz);

        mContext = context;
        locationType = type;
        dialogId = did;
        needEmptyView = emptyView;
        this.resourcesProvider = resourcesProvider;
    }

    private boolean myLocationDenied = false;
    private boolean askingForMyLocation = false;
    public void setMyLocationDenied(boolean myLocationDenied, boolean askingForLocation) {
        if (this.myLocationDenied == myLocationDenied && this.askingForMyLocation == askingForLocation)
            return;
        this.myLocationDenied = myLocationDenied;
        this.askingForMyLocation = askingForLocation;
        if (askingForMyLocation) {
            city = null;
            street = null;
        }
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
            if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ) {
                String address = "";
                if (!TextUtils.isEmpty(overrideAddressName)) {
                    address = overrideAddressName;
                } else if (!TextUtils.isEmpty(addressName)) {
                    address = addressName;
                } else if (fetchingLocation) {
                    address = LocaleController.getString("Loading", R.string.Loading);
                } else {
                    address = LocaleController.getString(R.string.UnknownLocation);
                }
                sendLocationCell.setText(LocaleController.getString(R.string.SetThisLocation), address);
                sendLocationCell.setHasLocation(true);
            } else if (locationType == LocationActivity.LOCATION_TYPE_GROUP || customLocation != null) {
                String address = "";
                if (!TextUtils.isEmpty(overrideAddressName)) {
                    address = overrideAddressName;
                } else if (!TextUtils.isEmpty(addressName)) {
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
                    sendLocationCell.setText(LocaleController.getString(R.string.SendLocation), LocaleController.formatString(R.string.AccurateTo, LocaleController.formatPluralString("Meters", (int) gpsLocation.getAccuracy())));
                    sendLocationCell.setHasLocation(true);
                } else {
                    sendLocationCell.setText(LocaleController.getString("SendLocation", R.string.SendLocation), myLocationDenied ? "" : LocaleController.getString("Loading", R.string.Loading));
                    sendLocationCell.setHasLocation(!myLocationDenied);
                }
            }
        }
    }

    public String getAddressName() {
        return addressName;
    }

    @Override
    public void onLocationAddressAvailable(String address, String displayAddress, TLRPC.TL_messageMediaVenue city, TLRPC.TL_messageMediaVenue street, Location location) {
        fetchingLocation = false;
        previousFetchedLocation = location;
        if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ) {
            addressName = displayAddress;
        } else {
            addressName = address;
        }

        if ((locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_STORY) && askingForMyLocation) {
            this.city = null;
            this.street = null;
        }

        boolean wasStreet = this.street != null;
        if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_STORY) {
            this.city = city;
            this.street = street;
            if (wasStreet == (this.street == null)) {
                notifyItemChanged(1);
                if (this.street == null) {
                    notifyItemRemoved(2);
                } else {
                    notifyItemInserted(2);
                }
            } else {
                notifyItemRangeChanged(1, 2);
            }
        } else {
            updateCell();
        }
    }

    protected void onDirectionClick() {

    }

    public void fetchLocationAddress() {
        if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ) {
            Location location;
            if (customLocation != null) {
                location = customLocation;
            } else if (gpsLocation != null) {
                location = gpsLocation;
            } else {
                return;
            }
            fetchingLocation = true;
            updateCell();
            LocationController.fetchLocationAddress(location, biz ? LocationController.TYPE_BIZ : 0, this);
        } else if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
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
        } else if (biz) {
            return 2;
        } else if (currentMessageObject != null) {
            return 2 + (currentLiveLocations.isEmpty() ? 1 : currentLiveLocations.size() + 3);
        } else if (locationType == 2) {
            return 2 + currentLiveLocations.size();
        } else {
            if (searching || !searched || places.isEmpty()) {
                int count = 6;
                if (locationType == LocationActivity.LOCATION_TYPE_SEND) {
                    count = 5;
                } else if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_STORY) {
                    count = 5 + (this.street != null ? 1 : 0);
                }
                return count + (!myLocationDenied && (searching || !searched) ? 2 : 0) + (needEmptyView ? 1 : 0) - (myLocationDenied ? 2 : 0);
            }
            int count = 5;
            if (locationType == LocationActivity.LOCATION_TYPE_SEND_WITH_LIVE) {
                count = 6;
            } else if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_STORY) {
                count = 5;// + (this.street != null ? 1 : 0);
            }
            return count + locations.size() + places.size() + (needEmptyView ? 1 : 0);
        }
    }

    private FrameLayout emptyCell;

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_PADDING:
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
            case VIEW_TYPE_SEND_LOCATION:
                view = new SendLocationCell(mContext, false, resourcesProvider);
                break;
            case VIEW_TYPE_HEADER:
                view = new HeaderCell(mContext, resourcesProvider);
                break;
            case VIEW_TYPE_LOCATION:
                LocationCell locationCell = new LocationCell(mContext, false, resourcesProvider);
                view = locationCell;
                break;
            case VIEW_TYPE_LOADING:
                view = new LocationLoadingCell(mContext, resourcesProvider);
                break;
            case VIEW_TYPE_FOOTER:
                view = new LocationPoweredCell(mContext, resourcesProvider);
                break;
            case VIEW_TYPE_LIVE_LOCATION: {
                SendLocationCell cell = new SendLocationCell(mContext, true, resourcesProvider);
                cell.setDialogId(dialogId);
                view = cell;
                break;
            }
            case VIEW_TYPE_SHARING:
                view = new SharingLiveLocationCell(mContext, true, locationType == LocationActivity.LOCATION_TYPE_GROUP || locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW || locationType == 3 ? 16 : 54, resourcesProvider);
                break;
            case VIEW_TYPE_DIRECTION: {
                LocationDirectionCell cell = new LocationDirectionCell(mContext, resourcesProvider);
                cell.setOnButtonClick(v -> onDirectionClick());
                view = cell;
                break;
            }
            case VIEW_TYPE_SHADOW: {
                view = new ShadowSectionCell(mContext);
                Drawable drawable = Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
                CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)), drawable);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
                break;
            }
            case VIEW_TYPE_STORY_LOCATION: {
                LocationCell locationCell2 = new LocationCell(mContext, false, resourcesProvider);
                locationCell2.setAllowTextAnimation(true);
                view = locationCell2;
                break;
            }
            case VIEW_TYPE_EMPTY:
            default: {
                view = new View(mContext);
                break;
            }
        }
        return new RecyclerListView.Holder(view);
    }

    public static final int VIEW_TYPE_PADDING = 0;
    public static final int VIEW_TYPE_SEND_LOCATION = 1;
    public static final int VIEW_TYPE_HEADER = 2;
    public static final int VIEW_TYPE_LOCATION = 3;
    public static final int VIEW_TYPE_LOADING = 4;
    public static final int VIEW_TYPE_FOOTER = 5;
    public static final int VIEW_TYPE_LIVE_LOCATION = 6;
    public static final int VIEW_TYPE_SHARING = 7;
    public static final int VIEW_TYPE_DIRECTION = 8;
    public static final int VIEW_TYPE_SHADOW = 9;
    public static final int VIEW_TYPE_EMPTY = 10;
    public static final int VIEW_TYPE_STORY_LOCATION = 11;

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_PADDING:
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
                if (lp == null) {
                    lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, overScrollHeight);
                } else {
                    lp.height = overScrollHeight;
                }
                holder.itemView.setLayoutParams(lp);
                break;
            case VIEW_TYPE_SEND_LOCATION:
                sendLocationCell = (SendLocationCell) holder.itemView;
                updateCell();
                break;
            case VIEW_TYPE_HEADER: {
                HeaderCell cell = (HeaderCell) holder.itemView;
                if (currentMessageObject != null) {
                    cell.setText(LocaleController.getString("LiveLocations", R.string.LiveLocations));
                } else {
                    cell.setText(LocaleController.getString("NearbyVenue", R.string.NearbyVenue));
                }
                break;
            }
            case VIEW_TYPE_LOCATION: {
                LocationCell cell = (LocationCell) holder.itemView;

                if (locationType == 0) {
                    position -= 4;
                } else if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_STORY || locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ) {
                    position -= 4;
                    if (this.street != null) {
                        position--;
                    }
                } else {
                    position -= 5;
                }
                boolean shouldHave = searched && (locationType != ChatAttachAlertLocationLayout.LOCATION_TYPE_STORY || !searching);
                TLRPC.TL_messageMediaVenue place = null;
                int p = position;
                if (shouldHave) {
                    if (position >= 0 && position < locations.size()) {
                        place = locations.get(position);
                        p = 2;
                    } else {
                        position -= locations.size();
                        if (position >= 0 && position < places.size()) {
                            place = places.get(position);
                        }
                    }
                }
                cell.setLocation(place, p, true);
                break;
            }
            case VIEW_TYPE_LOADING:
                ((LocationLoadingCell) holder.itemView).setLoading(searching);
                break;
            case VIEW_TYPE_LIVE_LOCATION:
                ((SendLocationCell) holder.itemView).setHasLocation(gpsLocation != null);
                break;
            case VIEW_TYPE_SHARING:
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
            case VIEW_TYPE_EMPTY:
                View emptyView = holder.itemView;
                emptyView.setBackgroundColor(Theme.getColor(myLocationDenied ? Theme.key_dialogBackgroundGray : Theme.key_dialogBackground, resourcesProvider));
                break;
            case VIEW_TYPE_STORY_LOCATION:
                LocationCell cell = (LocationCell) holder.itemView;
                if (askingForMyLocation) {
                    cell.setLocation(null, 2, position == 1 && this.street != null);
                } else {
                    if (position == 1) {
                        cell.setLocation(city, null, 2, this.street != null, animated);
                    } else {
                        cell.setLocation(street, null, 2, false, animated);
                    }
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
            } else if (i > 4 && i < places.size() + 4) {
                return currentLiveLocations.get(i - 5);
            }
        } else if (locationType == 2) {
            if (i >= 2) {
                return currentLiveLocations.get(i - 2);
            }
            return null;
        } else if (locationType == LocationActivity.LOCATION_TYPE_SEND_WITH_LIVE) {
            if (i > 4 && i < places.size() + 5) {
                return places.get(i - 5);
            }
        } else if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_STORY) {
            int x = this.street == null ? 3 : 4;
            if (i > x && i < locations.size() + (x + 1)) {
                return locations.get(i - (x + 1));
            }
            x += locations.size();
            if (i > x && i < places.size() + (x + 1)) {
                return places.get(i - (x + 1));
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
            return VIEW_TYPE_PADDING;
        }
        if (locationType == LocationActivity.LOCATION_TYPE_LIVE_VIEW) {
            return VIEW_TYPE_SHARING;
        }
        if (needEmptyView && position == getItemCount() - 1) {
            return VIEW_TYPE_EMPTY;
        }
        if (locationType == LocationActivity.LOCATION_TYPE_GROUP_VIEW) {
            return VIEW_TYPE_SHARING;
        }
        if (locationType == LocationActivity.LOCATION_TYPE_GROUP) {
            return VIEW_TYPE_SEND_LOCATION;
        }
        if (currentMessageObject != null) {
            if (currentLiveLocations.isEmpty()) {
                if (position == 2) {
                    return VIEW_TYPE_DIRECTION;
                }
            } else {
                if (position == 2) {
                    return VIEW_TYPE_SHADOW;
                } else if (position == 3) {
                    return VIEW_TYPE_HEADER;
                } else if (position == 4) {
                    shareLiveLocationPotistion = position;
                    return VIEW_TYPE_LIVE_LOCATION;
                }
            }
            return VIEW_TYPE_SHARING;
        }
        if (locationType == 2) {
            if (position == 1) {
                shareLiveLocationPotistion = position;
                return VIEW_TYPE_LIVE_LOCATION;
            } else {
                return VIEW_TYPE_SHARING;
            }
        }
        if (locationType == LocationActivity.LOCATION_TYPE_SEND_WITH_LIVE) {
            if (position == 1) {
                return VIEW_TYPE_SEND_LOCATION;
            } else if (position == 2) {
                shareLiveLocationPotistion = position;
                return VIEW_TYPE_LIVE_LOCATION;
            } else if (position == 3) {
                return VIEW_TYPE_SHADOW;
            } else if (position == 4) {
                return VIEW_TYPE_HEADER;
            } else if (searching || places.isEmpty() || !searched) {
                if (position <= 4 + 3 && (searching || !searched) && !myLocationDenied)
                    return VIEW_TYPE_LOCATION;
                return VIEW_TYPE_LOADING;
            } else if (position == places.size() + 5) {
                return VIEW_TYPE_FOOTER;
            }
        } else {
            int i = 4;
            int placesCount = places.size() + locations.size();
            if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_STORY) {
                if (position == 1) {
                    return VIEW_TYPE_STORY_LOCATION;
                }
                if (this.street != null) {
                    if (position == 2) {
                        return VIEW_TYPE_STORY_LOCATION;
                    }
                    position--;
                    i--;
                }
            }
            if (position == 1) {
                return VIEW_TYPE_SEND_LOCATION;
            } else if (position == 2) {
                return VIEW_TYPE_SHADOW;
            } else if (position == 3) {
                return VIEW_TYPE_HEADER;
            } else if (searching || places.isEmpty() && locations.isEmpty()) {
                if (position <= 3 + 3 && (searching || !searched) && !myLocationDenied)
                    return VIEW_TYPE_LOCATION;
                return VIEW_TYPE_LOADING;
            } else if (position == placesCount + i) {
                return VIEW_TYPE_FOOTER;
            }
        }
        return VIEW_TYPE_LOCATION;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int viewType = holder.getItemViewType();
        if (viewType == VIEW_TYPE_LIVE_LOCATION) {
            return !(LocationController.getInstance(currentAccount).getSharingLocationInfo(dialogId) == null && gpsLocation == null);
        }
        return viewType == VIEW_TYPE_SEND_LOCATION || viewType == VIEW_TYPE_LOCATION || viewType == VIEW_TYPE_SHARING || viewType == VIEW_TYPE_STORY_LOCATION;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
