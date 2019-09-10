/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;

import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.LocationActivityAdapter;
import org.telegram.ui.Adapters.LocationActivitySearchAdapter;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.LocationLoadingCell;
import org.telegram.ui.Cells.LocationPoweredCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MapPlaceholderDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class LocationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public class LiveLocation {
        public int id;
        public TLRPC.Message object;
        public TLRPC.User user;
        public TLRPC.Chat chat;
        public Marker marker;
    }

    private GoogleMap googleMap;
    private MapView mapView;
    private EmptyTextProgressView emptyView;
    private FrameLayout mapViewClip;
    private LocationActivityAdapter adapter;
    private RecyclerListView listView;
    private RecyclerListView searchListView;
    private LocationActivitySearchAdapter searchAdapter;
    private View markerImageView;
    private ImageView locationButton;
    private ImageView routeButton;
    private LinearLayoutManager layoutManager;
    private AvatarDrawable avatarDrawable;
    private ActionBarMenuItem otherItem;
    private ChatActivity parentFragment;

    private boolean checkGpsEnabled = true;

    private boolean isFirstLocation = true;
    private long dialogId;

    private boolean firstFocus = true;

    private Runnable updateRunnable;

    private ArrayList<LiveLocation> markers = new ArrayList<>();
    private SparseArray<LiveLocation> markersMap = new SparseArray<>();

    private AnimatorSet animatorSet;

    private boolean checkPermission = true;

    private boolean searching;
    private boolean searchWas;

    private boolean wasResults;
    private boolean mapsInitialized;
    private boolean onResumeCalled;

    private Location myLocation;
    private Location userLocation;
    private int markerTop;

    private TLRPC.TL_channelLocation chatLocation;
    private TLRPC.TL_channelLocation initialLocation;
    private MessageObject messageObject;
    private boolean userLocationMoved;
    private boolean firstWas;
    private CircleOptions circleOptions;
    private LocationActivityDelegate delegate;

    private int locationType;

    private int overScrollHeight = AndroidUtilities.displaySize.x - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(66);

    private final static int share = 1;
    private final static int map_list_menu_map = 2;
    private final static int map_list_menu_satellite = 3;
    private final static int map_list_menu_hybrid = 4;

    public final static int LOCATION_TYPE_SEND = 0;
    public final static int LOCATION_TYPE_GROUP = 4;
    public final static int LOCATION_TYPE_GROUP_VIEW = 5;

    public interface LocationActivityDelegate {
        void didSelectLocation(TLRPC.MessageMedia location, int live, boolean notify, int scheduleDate);
    }

    public LocationActivity(int type) {
        super();
        locationType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        swipeBackEnabled = false;
        getNotificationCenter().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.locationPermissionGranted);
        if (messageObject != null && messageObject.isLiveLocation()) {
            getNotificationCenter().addObserver(this, NotificationCenter.didReceiveNewMessages);
            getNotificationCenter().addObserver(this, NotificationCenter.replaceMessagesObjects);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.locationPermissionGranted);
        getNotificationCenter().removeObserver(this, NotificationCenter.closeChats);
        getNotificationCenter().removeObserver(this, NotificationCenter.didReceiveNewMessages);
        getNotificationCenter().removeObserver(this, NotificationCenter.replaceMessagesObjects);
        try {
            if (googleMap != null) {
                googleMap.setMyLocationEnabled(false);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (mapView != null) {
                mapView.onDestroy();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (adapter != null) {
            adapter.destroy();
        }
        if (searchAdapter != null) {
            searchAdapter.destroy();
        }
        if (updateRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            updateRunnable = null;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAddToContainer(false);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == map_list_menu_map) {
                    if (googleMap != null) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }
                } else if (id == map_list_menu_satellite) {
                    if (googleMap != null) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                    }
                } else if (id == map_list_menu_hybrid) {
                    if (googleMap != null) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    }
                } else if (id == share) {
                    try {
                        double lat = messageObject.messageOwner.media.geo.lat;
                        double lon = messageObject.messageOwner.media.geo._long;
                        getParentActivity().startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        if (chatLocation != null) {
            actionBar.setTitle(LocaleController.getString("ChatLocation", R.string.ChatLocation));
        } else if (messageObject != null) {
            if (messageObject.isLiveLocation()) {
                actionBar.setTitle(LocaleController.getString("AttachLiveLocation", R.string.AttachLiveLocation));
            } else {
                if (messageObject.messageOwner.media.title != null && messageObject.messageOwner.media.title.length() > 0) {
                    actionBar.setTitle(LocaleController.getString("SharedPlace", R.string.SharedPlace));
                } else {
                    actionBar.setTitle(LocaleController.getString("ChatLocation", R.string.ChatLocation));
                }
                menu.addItem(share, R.drawable.share).setContentDescription(LocaleController.getString("ShareFile", R.string.ShareFile));
            }
        } else {
            actionBar.setTitle(LocaleController.getString("ShareLocation", R.string.ShareLocation));

            if (locationType != LOCATION_TYPE_GROUP) {
                ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        searching = true;
                        otherItem.setVisibility(View.GONE);
                        listView.setVisibility(View.GONE);
                        mapViewClip.setVisibility(View.GONE);
                        searchListView.setVisibility(View.VISIBLE);
                        searchListView.setEmptyView(emptyView);
                        emptyView.showTextView();
                    }

                    @Override
                    public void onSearchCollapse() {
                        searching = false;
                        searchWas = false;
                        otherItem.setVisibility(View.VISIBLE);
                        searchListView.setEmptyView(null);
                        listView.setVisibility(View.VISIBLE);
                        mapViewClip.setVisibility(View.VISIBLE);
                        searchListView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.GONE);
                        searchAdapter.searchDelayed(null, null);
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        if (searchAdapter == null) {
                            return;
                        }
                        String text = editText.getText().toString();
                        if (text.length() != 0) {
                            searchWas = true;
                        }
                        emptyView.showProgress();
                        searchAdapter.searchDelayed(text, userLocation);
                    }
                });
                item.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
            }
        }

        otherItem = menu.addItem(0, R.drawable.ic_ab_other);
        otherItem.addSubItem(map_list_menu_map, R.drawable.msg_map, LocaleController.getString("Map", R.string.Map));
        otherItem.addSubItem(map_list_menu_satellite, R.drawable.msg_satellite, LocaleController.getString("Satellite", R.string.Satellite));
        otherItem.addSubItem(map_list_menu_hybrid, R.drawable.msg_hybrid, LocaleController.getString("Hybrid", R.string.Hybrid));
        otherItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));

        fragmentView = new FrameLayout(context) {
            private boolean first = true;

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                if (changed) {
                    fixLayoutInternal(first);
                    first = false;
                }
            }
        };
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        locationButton = new ImageView(context);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_profile_actionBackground), Theme.getColor(Theme.key_profile_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        locationButton.setBackgroundDrawable(drawable);
        locationButton.setImageResource(R.drawable.myloc_on);
        locationButton.setScaleType(ImageView.ScaleType.CENTER);
        locationButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_actionIcon), PorterDuff.Mode.MULTIPLY));
        locationButton.setContentDescription(LocaleController.getString("AccDescrMyLocation", R.string.AccDescrMyLocation));
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(locationButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(locationButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            locationButton.setStateListAnimator(animator);
            locationButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }

        if (chatLocation != null) {
            userLocation = new Location("network");
            userLocation.setLatitude(chatLocation.geo_point.lat);
            userLocation.setLongitude(chatLocation.geo_point._long);
        } else if (messageObject != null) {
            userLocation = new Location("network");
            userLocation.setLatitude(messageObject.messageOwner.media.geo.lat);
            userLocation.setLongitude(messageObject.messageOwner.media.geo._long);
        }

        searchWas = false;
        searching = false;
        mapViewClip = new FrameLayout(context);
        mapViewClip.setBackgroundDrawable(new MapPlaceholderDrawable());
        if (adapter != null) {
            adapter.destroy();
        }
        if (searchAdapter != null) {
            searchAdapter.destroy();
        }

        listView = new RecyclerListView(context);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setAdapter(adapter = new LocationActivityAdapter(context, locationType, dialogId));
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (adapter.getItemCount() == 0) {
                    return;
                }
                int position = layoutManager.findFirstVisibleItemPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                updateClipView(position);
                if (locationType != LOCATION_TYPE_GROUP) {
                    if (dy > 0) {
                        if (!adapter.isPulledUp()) {
                            adapter.setPulledUp();
                            if (myLocation != null) {
                                AndroidUtilities.runOnUIThread(() -> adapter.searchPlacesWithQuery(null, myLocation, true));
                            }
                        }
                    }
                }
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (locationType == LOCATION_TYPE_GROUP) {
                if (position == 1) {
                    TLRPC.TL_messageMediaVenue venue = (TLRPC.TL_messageMediaVenue) adapter.getItem(position);
                    if (venue == null) {
                        return;
                    }
                    if (dialogId == 0) {
                        delegate.didSelectLocation(venue, LOCATION_TYPE_GROUP, true, 0);
                        finishFragment();
                    } else {
                        final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getParentActivity(), 3)};
                        TLRPC.TL_channels_editLocation req = new TLRPC.TL_channels_editLocation();
                        req.address = venue.address;
                        req.channel = getMessagesController().getInputChannel(-(int) dialogId);
                        req.geo_point = new TLRPC.TL_inputGeoPoint();
                        req.geo_point.lat = venue.geo.lat;
                        req.geo_point._long = venue.geo._long;
                        int requestId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog[0].dismiss();
                            } catch (Throwable ignore) {

                            }
                            progressDialog[0] = null;
                            delegate.didSelectLocation(venue, LOCATION_TYPE_GROUP, true, 0);
                            finishFragment();
                        }));
                        progressDialog[0].setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(requestId, true));
                        showDialog(progressDialog[0]);
                    }
                }
            } else if (locationType == LOCATION_TYPE_GROUP_VIEW) {
                if (googleMap != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(chatLocation.geo_point.lat, chatLocation.geo_point._long), googleMap.getMaxZoomLevel() - 4));
                }
            } else if (position == 1 && messageObject != null && !messageObject.isLiveLocation()) {
                if (googleMap != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long), googleMap.getMaxZoomLevel() - 4));
                }
            } else if (position == 1 && locationType != 2) {
                if (delegate != null && userLocation != null) {
                    TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                    location.geo = new TLRPC.TL_geoPoint();
                    location.geo.lat = AndroidUtilities.fixLocationCoord(userLocation.getLatitude());
                    location.geo._long = AndroidUtilities.fixLocationCoord(userLocation.getLongitude());
                    if (parentFragment != null && parentFragment.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(parentFragment.getCurrentUser()), (notify, scheduleDate) -> {
                            delegate.didSelectLocation(location, locationType, notify, scheduleDate);
                            finishFragment();
                        });
                    } else {
                        delegate.didSelectLocation(location, locationType, true, 0);
                        finishFragment();
                    }
                }
            } else if (position == 2 && locationType == 1 || position == 1 && locationType == 2 || position == 3 && locationType == 3) {
                if (getLocationController().isSharingLocation(dialogId)) {
                    getLocationController().removeSharingLocation(dialogId);
                    finishFragment();
                } else {
                    if (delegate == null || getParentActivity() == null) {
                        return;
                    }
                    if (myLocation != null) {
                        TLRPC.User user = null;
                        if ((int) dialogId > 0) {
                            user = getMessagesController().getUser((int) dialogId);
                        }
                        showDialog(AlertsCreator.createLocationUpdateDialog(getParentActivity(), user, param -> {
                            TLRPC.TL_messageMediaGeoLive location = new TLRPC.TL_messageMediaGeoLive();
                            location.geo = new TLRPC.TL_geoPoint();
                            location.geo.lat = AndroidUtilities.fixLocationCoord(myLocation.getLatitude());
                            location.geo._long = AndroidUtilities.fixLocationCoord(myLocation.getLongitude());
                            location.period = param;
                            delegate.didSelectLocation(location, locationType, true, 0);
                            finishFragment();
                        }));
                    }
                }
            } else {
                Object object = adapter.getItem(position);
                if (object instanceof TLRPC.TL_messageMediaVenue) {
                    if (parentFragment != null && parentFragment.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(parentFragment.getCurrentUser()), (notify, scheduleDate) -> {
                            delegate.didSelectLocation((TLRPC.TL_messageMediaVenue) object, locationType, notify, scheduleDate);
                            finishFragment();
                        });
                    } else {
                        delegate.didSelectLocation((TLRPC.TL_messageMediaVenue) object, locationType, true, 0);
                        finishFragment();
                    }
                } else if (object instanceof LiveLocation) {
                    LiveLocation liveLocation = (LiveLocation) object;
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(liveLocation.marker.getPosition(), googleMap.getMaxZoomLevel() - 4));
                }
            }
        });
        adapter.setDelegate(dialogId, places -> {
            if (!wasResults && !places.isEmpty()) {
                wasResults = true;
            }
            emptyView.showTextView();
        });
        adapter.setOverScrollHeight(overScrollHeight);

        frameLayout.addView(mapViewClip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        mapView = new MapView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (messageObject == null && chatLocation == null) {
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        if (animatorSet != null) {
                            animatorSet.cancel();
                        }
                        animatorSet = new AnimatorSet();
                        animatorSet.setDuration(200);
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(markerImageView, View.TRANSLATION_Y, markerTop - AndroidUtilities.dp(10)));
                        animatorSet.start();
                    } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                        if (animatorSet != null) {
                            animatorSet.cancel();
                        }
                        animatorSet = new AnimatorSet();
                        animatorSet.setDuration(200);
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(markerImageView, View.TRANSLATION_Y, markerTop));
                        animatorSet.start();
                        adapter.fetchLocationAddress();
                    }
                    if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                        if (!userLocationMoved) {
                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.setDuration(200);
                            animatorSet.play(ObjectAnimator.ofFloat(locationButton, View.ALPHA, 1.0f));
                            animatorSet.start();
                            userLocationMoved = true;
                        }
                        if (googleMap != null && userLocation != null) {
                            userLocation.setLatitude(googleMap.getCameraPosition().target.latitude);
                            userLocation.setLongitude(googleMap.getCameraPosition().target.longitude);
                        }
                        adapter.setCustomLocation(userLocation);
                    }
                }
                return super.onInterceptTouchEvent(ev);
            }
        };
        final MapView map = mapView;
        new Thread(() -> {
            try {
                map.onCreate(null);
            } catch (Exception e) {
                //this will cause exception, but will preload google maps?
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (mapView != null && getParentActivity() != null) {
                    try {
                        map.onCreate(null);
                        MapsInitializer.initialize(ApplicationLoader.applicationContext);
                        mapView.getMapAsync(map1 -> {
                            googleMap = map1;
                            googleMap.setPadding(AndroidUtilities.dp(70), 0, AndroidUtilities.dp(70), AndroidUtilities.dp(10));
                            onMapInit();
                        });
                        mapsInitialized = true;
                        if (onResumeCalled) {
                            mapView.onResume();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        }).start();

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        mapViewClip.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.BOTTOM));

        if (messageObject == null && chatLocation == null) {
            if (locationType == LOCATION_TYPE_GROUP && dialogId != 0) {
                TLRPC.Chat chat = getMessagesController().getChat(-(int) dialogId);
                if (chat != null) {
                    FrameLayout frameLayout1 = new FrameLayout(context);
                    frameLayout1.setBackgroundResource(R.drawable.livepin);
                    mapViewClip.addView(frameLayout1, LayoutHelper.createFrame(62, 76, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

                    BackupImageView backupImageView = new BackupImageView(context);
                    backupImageView.setRoundRadius(AndroidUtilities.dp(26));
                    backupImageView.setImage(ImageLocation.getForChat(chat, false), "50_50", new AvatarDrawable(chat), chat);
                    frameLayout1.addView(backupImageView, LayoutHelper.createFrame(52, 52, Gravity.LEFT | Gravity.TOP, 5, 5, 0, 0));

                    markerImageView = frameLayout1;
                    markerImageView.setTag(1);
                }
            }

            if (markerImageView == null) {
                ImageView imageView = new ImageView(context);
                imageView.setImageResource(R.drawable.map_pin2);
                mapViewClip.addView(imageView, LayoutHelper.createFrame(28, 48, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
                markerImageView = imageView;
            }

            emptyView = new EmptyTextProgressView(context);
            emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
            emptyView.setShowAtCenter(true);
            emptyView.setVisibility(View.GONE);
            frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            searchListView = new RecyclerListView(context);
            searchListView.setVisibility(View.GONE);
            searchListView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
            searchListView.setAdapter(searchAdapter = new LocationActivitySearchAdapter(context));
            frameLayout.addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
            searchListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && searchWas) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }
            });
            searchListView.setOnItemClickListener((view, position) -> {
                TLRPC.TL_messageMediaVenue object = searchAdapter.getItem(position);
                if (object != null && delegate != null) {
                    if (parentFragment != null && parentFragment.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), UserObject.isUserSelf(parentFragment.getCurrentUser()), (notify, scheduleDate) -> {
                            delegate.didSelectLocation(object, locationType, notify, scheduleDate);
                            finishFragment();
                        });
                    } else {
                        delegate.didSelectLocation(object, locationType, true, 0);
                        finishFragment();
                    }
                }
            });
        } else if (messageObject != null && !messageObject.isLiveLocation() || chatLocation != null) {
            routeButton = new ImageView(context);
            drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
            if (Build.VERSION.SDK_INT < 21) {
                Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
                shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
                combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                drawable = combinedDrawable;
            }
            routeButton.setBackgroundDrawable(drawable);
            routeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
            routeButton.setImageResource(R.drawable.navigate);
            routeButton.setScaleType(ImageView.ScaleType.CENTER);
            if (Build.VERSION.SDK_INT >= 21) {
                StateListAnimator animator = new StateListAnimator();
                animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(routeButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                animator.addState(new int[]{}, ObjectAnimator.ofFloat(routeButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                routeButton.setStateListAnimator(animator);
                routeButton.setOutlineProvider(new ViewOutlineProvider() {
                    @SuppressLint("NewApi")
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    }
                });
            }
            frameLayout.addView(routeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 37));
            routeButton.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= 23) {
                    Activity activity = getParentActivity();
                    if (activity != null) {
                        if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            showPermissionAlert(true);
                            return;
                        }
                    }
                }
                if (myLocation != null) {
                    try {
                        Intent intent;
                        if (messageObject != null) {
                            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "http://maps.google.com/maps?saddr=%f,%f&daddr=%f,%f", myLocation.getLatitude(), myLocation.getLongitude(), messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long)));
                        } else {
                            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "http://maps.google.com/maps?saddr=%f,%f&daddr=%f,%f", myLocation.getLatitude(), myLocation.getLongitude(), chatLocation.geo_point.lat, chatLocation.geo_point._long)));
                        }
                        getParentActivity().startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });

            if (chatLocation != null) {
                adapter.setChatLocation(chatLocation);
            } else if (messageObject != null) {
                adapter.setMessageObject(messageObject);
            }
        }

        if (messageObject != null && !messageObject.isLiveLocation() || chatLocation != null) {
            mapViewClip.addView(locationButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 43));
        } else {
            mapViewClip.addView(locationButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));
        }
        locationButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                Activity activity = getParentActivity();
                if (activity != null) {
                    if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        showPermissionAlert(false);
                        return;
                    }
                }
            }
            if (messageObject != null || chatLocation != null) {
                if (myLocation != null && googleMap != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()), googleMap.getMaxZoomLevel() - 4));
                }
            } else {
                if (myLocation != null && googleMap != null) {
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.setDuration(200);
                    animatorSet.play(ObjectAnimator.ofFloat(locationButton, View.ALPHA, 0.0f));
                    animatorSet.start();
                    adapter.setCustomLocation(null);
                    userLocationMoved = false;
                    googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(myLocation.getLatitude(), myLocation.getLongitude())));
                }
            }
        });
        if (messageObject == null && chatLocation == null) {
            if (initialLocation == null) {
                locationButton.setAlpha(0.0f);
            } else {
                userLocationMoved = true;
            }
        }

        frameLayout.addView(actionBar);

        return fragmentView;
    }

    private Bitmap createUserBitmap(LiveLocation liveLocation) {
        Bitmap result = null;
        try {
            TLRPC.FileLocation photo = null;
            if (liveLocation.user != null && liveLocation.user.photo != null) {
                photo = liveLocation.user.photo.photo_small;
            } else if (liveLocation.chat != null && liveLocation.chat.photo != null) {
                photo = liveLocation.chat.photo.photo_small;
            }
            result = Bitmap.createBitmap(AndroidUtilities.dp(62), AndroidUtilities.dp(76), Bitmap.Config.ARGB_8888);
            result.eraseColor(Color.TRANSPARENT);
            Canvas canvas = new Canvas(result);
            Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.livepin);
            drawable.setBounds(0, 0, AndroidUtilities.dp(62), AndroidUtilities.dp(76));
            drawable.draw(canvas);

            Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            RectF bitmapRect = new RectF();
            canvas.save();
            if (photo != null) {
                File path = FileLoader.getPathToAttach(photo, true);
                Bitmap bitmap = BitmapFactory.decodeFile(path.toString());
                if (bitmap != null) {
                    BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    Matrix matrix = new Matrix();
                    float scale = AndroidUtilities.dp(52) / (float) bitmap.getWidth();
                    matrix.postTranslate(AndroidUtilities.dp(5), AndroidUtilities.dp(5));
                    matrix.postScale(scale, scale);
                    roundPaint.setShader(shader);
                    shader.setLocalMatrix(matrix);
                    bitmapRect.set(AndroidUtilities.dp(5), AndroidUtilities.dp(5), AndroidUtilities.dp(52 + 5), AndroidUtilities.dp(52 + 5));
                    canvas.drawRoundRect(bitmapRect, AndroidUtilities.dp(26), AndroidUtilities.dp(26), roundPaint);
                }
            } else {
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                if (liveLocation.user != null) {
                    avatarDrawable.setInfo(liveLocation.user);
                } else if (liveLocation.chat != null) {
                    avatarDrawable.setInfo(liveLocation.chat);
                }
                canvas.translate(AndroidUtilities.dp(5), AndroidUtilities.dp(5));
                avatarDrawable.setBounds(0, 0, AndroidUtilities.dp(52.2f), AndroidUtilities.dp(52.2f));
                avatarDrawable.draw(canvas);
            }
            canvas.restore();
            try {
                canvas.setBitmap(null);
            } catch (Exception e) {
                //don't promt, this will crash on 2.x
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return result;
    }

    private int getMessageId(TLRPC.Message message) {
        if (message.from_id != 0) {
            return message.from_id;
        } else {
            return (int) MessageObject.getDialogId(message);
        }
    }

    private LiveLocation addUserMarker(TLRPC.Message message) {
        LiveLocation liveLocation;
        LatLng latLng = new LatLng(message.media.geo.lat, message.media.geo._long);
        if ((liveLocation = markersMap.get(message.from_id)) == null) {
            liveLocation = new LiveLocation();
            liveLocation.object = message;
            if (liveLocation.object.from_id != 0) {
                liveLocation.user = getMessagesController().getUser(liveLocation.object.from_id);
                liveLocation.id = liveLocation.object.from_id;
            } else {
                int did = (int) MessageObject.getDialogId(message);
                if (did > 0) {
                    liveLocation.user = getMessagesController().getUser(did);
                    liveLocation.id = did;
                } else {
                    liveLocation.chat = getMessagesController().getChat(-did);
                    liveLocation.id = did;
                }
            }

            try {
                MarkerOptions options = new MarkerOptions().position(latLng);
                Bitmap bitmap = createUserBitmap(liveLocation);
                if (bitmap != null) {
                    options.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
                    options.anchor(0.5f, 0.907f);
                    liveLocation.marker = googleMap.addMarker(options);
                    markers.add(liveLocation);
                    markersMap.put(liveLocation.id, liveLocation);
                    LocationController.SharingLocationInfo myInfo = getLocationController().getSharingLocationInfo(dialogId);
                    if (liveLocation.id == getUserConfig().getClientUserId() && myInfo != null && liveLocation.object.id == myInfo.mid && myLocation != null) {
                        liveLocation.marker.setPosition(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            liveLocation.object = message;
            liveLocation.marker.setPosition(latLng);
        }
        return liveLocation;
    }

    private LiveLocation addUserMarker(TLRPC.TL_channelLocation location) {
        LatLng latLng = new LatLng(location.geo_point.lat, location.geo_point._long);
        LiveLocation liveLocation = new LiveLocation();
        int did = (int) dialogId;
        if (did > 0) {
            liveLocation.user = getMessagesController().getUser(did);
            liveLocation.id = did;
        } else {
            liveLocation.chat = getMessagesController().getChat(-did);
            liveLocation.id = did;
        }

        try {
            MarkerOptions options = new MarkerOptions().position(latLng);
            Bitmap bitmap = createUserBitmap(liveLocation);
            if (bitmap != null) {
                options.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
                options.anchor(0.5f, 0.907f);
                liveLocation.marker = googleMap.addMarker(options);
                markers.add(liveLocation);
                markersMap.put(liveLocation.id, liveLocation);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        return liveLocation;
    }

    private void onMapInit() {
        if (googleMap == null) {
            return;
        }

        if (chatLocation != null) {
            LiveLocation liveLocation = addUserMarker(chatLocation);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(liveLocation.marker.getPosition(), googleMap.getMaxZoomLevel() - 4));
        } else if (messageObject != null) {
            if (messageObject.isLiveLocation()) {
                LiveLocation liveLocation = addUserMarker(messageObject.messageOwner);
                if (!getRecentLocations()) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(liveLocation.marker.getPosition(), googleMap.getMaxZoomLevel() - 4));
                }
            } else {
                LatLng latLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
                try {
                    googleMap.addMarker(new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromResource(R.drawable.map_pin2)));
                } catch (Exception e) {
                    FileLog.e(e);
                }
                CameraUpdate position = CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getMaxZoomLevel() - 4);
                googleMap.moveCamera(position);
                firstFocus = false;
                getRecentLocations();
            }
        } else {
            userLocation = new Location("network");
            if (initialLocation != null) {
                LatLng latLng = new LatLng(initialLocation.geo_point.lat, initialLocation.geo_point._long);
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getMaxZoomLevel() - 4));
                userLocation.setLatitude(initialLocation.geo_point.lat);
                userLocation.setLongitude(initialLocation.geo_point._long);
                adapter.setCustomLocation(userLocation);
            } else {
                userLocation.setLatitude(20.659322);
                userLocation.setLongitude(-11.406250);
            }
        }

        try {
            googleMap.setMyLocationEnabled(true);
        } catch (Exception e) {
            FileLog.e(e);
        }
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(false);
        //googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.setOnMyLocationChangeListener(location -> {
            positionMarker(location);
            getLocationController().setGoogleMapLocation(location, isFirstLocation);
            isFirstLocation = false;
        });
        positionMarker(myLocation = getLastLocation());

        if (checkGpsEnabled && getParentActivity() != null) {
            checkGpsEnabled = false;
            if (!getParentActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
                return;
            }
            try {
                LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("GpsDisabledAlert", R.string.GpsDisabledAlert));
                    builder.setPositiveButton(LocaleController.getString("ConnectingToProxyEnable", R.string.ConnectingToProxyEnable), (dialog, id) -> {
                        if (getParentActivity() == null) {
                            return;
                        }
                        try {
                            getParentActivity().startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (Exception ignore) {

                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void showPermissionAlert(boolean byButton) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        if (byButton) {
            builder.setMessage(LocaleController.getString("PermissionNoLocationPosition", R.string.PermissionNoLocationPosition));
        } else {
            builder.setMessage(LocaleController.getString("PermissionNoLocation", R.string.PermissionNoLocation));
        }
        builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), (dialog, which) -> {
            if (getParentActivity() == null) {
                return;
            }
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                getParentActivity().startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        showDialog(builder.create());
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            try {
                if (mapView.getParent() instanceof ViewGroup) {
                    ViewGroup viewGroup = (ViewGroup) mapView.getParent();
                    viewGroup.removeView(mapView);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (mapViewClip != null) {
                mapViewClip.addView(mapView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10), Gravity.TOP | Gravity.LEFT));
                updateClipView(layoutManager.findFirstVisibleItemPosition());
            } else if (fragmentView != null) {
                ((FrameLayout) fragmentView).addView(mapView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            }
        }
    }

    private void updateClipView(int firstVisibleItem) {
        if (firstVisibleItem == RecyclerView.NO_POSITION) {
            return;
        }
        int height = 0;
        int top = 0;
        View child = listView.getChildAt(0);
        if (child != null) {
            if (firstVisibleItem == 0) {
                top = child.getTop();
                height = overScrollHeight + (top < 0 ? top : 0);
            }
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
            if (layoutParams != null) {
                if (height <= 0) {
                    if (mapView.getVisibility() == View.VISIBLE) {
                        mapView.setVisibility(View.INVISIBLE);
                        mapViewClip.setVisibility(View.INVISIBLE);
                    }
                } else {
                    if (mapView.getVisibility() == View.INVISIBLE) {
                        mapView.setVisibility(View.VISIBLE);
                        mapViewClip.setVisibility(View.VISIBLE);
                    }
                }

                mapViewClip.setTranslationY(Math.min(0, top));
                mapView.setTranslationY(Math.max(0, -top / 2));
                if (markerImageView != null) {
                    markerImageView.setTranslationY(markerTop = -top - AndroidUtilities.dp(markerImageView.getTag() == null ? 48 : 69) + height / 2);
                }
                if (routeButton != null) {
                    routeButton.setTranslationY(top);
                }
                layoutParams = (FrameLayout.LayoutParams) mapView.getLayoutParams();
                if (layoutParams != null && layoutParams.height != overScrollHeight + AndroidUtilities.dp(10)) {
                    layoutParams.height = overScrollHeight + AndroidUtilities.dp(10);
                    if (googleMap != null) {
                        googleMap.setPadding(AndroidUtilities.dp(70), 0, AndroidUtilities.dp(70), AndroidUtilities.dp(10));
                    }
                    mapView.setLayoutParams(layoutParams);
                }
            }
        }
    }

    private void fixLayoutInternal(final boolean resume) {
        if (listView != null) {
            int height = (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
            int viewHeight = fragmentView.getMeasuredHeight();
            if (viewHeight == 0) {
                return;
            }
            overScrollHeight = viewHeight - AndroidUtilities.dp(66) - height;

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.topMargin = height;
            listView.setLayoutParams(layoutParams);
            layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
            layoutParams.topMargin = height;
            layoutParams.height = overScrollHeight;
            mapViewClip.setLayoutParams(layoutParams);
            if (searchListView != null) {
                layoutParams = (FrameLayout.LayoutParams) searchListView.getLayoutParams();
                layoutParams.topMargin = height;
                searchListView.setLayoutParams(layoutParams);
            }

            adapter.setOverScrollHeight(overScrollHeight);
            layoutParams = (FrameLayout.LayoutParams) mapView.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = overScrollHeight + AndroidUtilities.dp(10);
                if (googleMap != null) {
                    googleMap.setPadding(AndroidUtilities.dp(70), 0, AndroidUtilities.dp(70), AndroidUtilities.dp(10));
                }
                mapView.setLayoutParams(layoutParams);
            }
            adapter.notifyDataSetChanged();

            if (resume) {
                layoutManager.scrollToPositionWithOffset(0, -AndroidUtilities.dp(32 + (locationType == 1 || locationType == 2 ? 66 : 0)));
                updateClipView(layoutManager.findFirstVisibleItemPosition());
                listView.post(() -> {
                    layoutManager.scrollToPositionWithOffset(0, -AndroidUtilities.dp(32 + (locationType == 1 || locationType == 2 ? 66 : 0)));
                    updateClipView(layoutManager.findFirstVisibleItemPosition());
                });
            } else {
                updateClipView(layoutManager.findFirstVisibleItemPosition());
            }
        }
    }

    private Location getLastLocation() {
        LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = lm.getProviders(true);
        Location l = null;
        for (int i = providers.size() - 1; i >= 0; i--) {
            l = lm.getLastKnownLocation(providers.get(i));
            if (l != null) {
                break;
            }
        }
        return l;
    }

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        myLocation = new Location(location);
        LiveLocation liveLocation = markersMap.get(getUserConfig().getClientUserId());
        LocationController.SharingLocationInfo myInfo = getLocationController().getSharingLocationInfo(dialogId);
        if (liveLocation != null && myInfo != null && liveLocation.object.id == myInfo.mid) {
            liveLocation.marker.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
        }
        if (messageObject == null && chatLocation == null && googleMap != null) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            if (adapter != null) {
                if (adapter.isPulledUp()) {
                    adapter.searchPlacesWithQuery(null, myLocation, true);
                }
                adapter.setGpsLocation(myLocation);
            }
            if (!userLocationMoved) {
                userLocation = new Location(location);
                if (firstWas) {
                    CameraUpdate position = CameraUpdateFactory.newLatLng(latLng);
                    googleMap.animateCamera(position);
                } else {
                    firstWas = true;
                    CameraUpdate position = CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getMaxZoomLevel() - 4);
                    googleMap.moveCamera(position);
                }
            }
        } else {
            adapter.setGpsLocation(myLocation);
        }
    }

    public void setMessageObject(MessageObject message) {
        messageObject = message;
        dialogId = messageObject.getDialogId();
    }

    public void setChatLocation(int chatId, TLRPC.TL_channelLocation location) {
        dialogId = -chatId;
        chatLocation = location;
    }

    public void setDialogId(long did) {
        dialogId = did;
    }

    public void setInitialLocation(TLRPC.TL_channelLocation location) {
        initialLocation = location;
    }

    private void fetchRecentLocations(ArrayList<TLRPC.Message> messages) {
        LatLngBounds.Builder builder = null;
        if (firstFocus) {
            builder = new LatLngBounds.Builder();
        }
        int date = getConnectionsManager().getCurrentTime();
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message.date + message.media.period > date) {
                if (builder != null) {
                    LatLng latLng = new LatLng(message.media.geo.lat, message.media.geo._long);
                    builder.include(latLng);
                }
                addUserMarker(message);
            }
        }
        if (builder != null) {
            firstFocus = false;
            adapter.setLiveLocations(markers);
            if (messageObject.isLiveLocation()) {
                try {
                    final LatLngBounds bounds = builder.build();
                    if (messages.size() > 1) {
                        try {
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, AndroidUtilities.dp(60)));
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                } catch (Exception ignore) {

                }
            }
        }
    }

    private boolean getRecentLocations() {
        ArrayList<TLRPC.Message> messages = getLocationController().locationsCache.get(messageObject.getDialogId());
        if (messages != null && messages.isEmpty()) {
            fetchRecentLocations(messages);
        } else {
            messages = null;
        }
        int lower_id = (int) dialogId;
        if (lower_id < 0) {
            TLRPC.Chat chat = getMessagesController().getChat(-lower_id);
            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                return false;
            }
        }
        TLRPC.TL_messages_getRecentLocations req = new TLRPC.TL_messages_getRecentLocations();
        final long dialog_id = messageObject.getDialogId();
        req.peer = getMessagesController().getInputPeer((int) dialog_id);
        req.limit = 100;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (googleMap == null) {
                        return;
                    }
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    for (int a = 0; a < res.messages.size(); a++) {
                        if (!(res.messages.get(a).media instanceof TLRPC.TL_messageMediaGeoLive)) {
                            res.messages.remove(a);
                            a--;
                        }
                    }
                    getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                    getMessagesController().putUsers(res.users, false);
                    getMessagesController().putChats(res.chats, false);
                    getLocationController().locationsCache.put(dialog_id, res.messages);
                    getNotificationCenter().postNotificationName(NotificationCenter.liveLocationsCacheChanged, dialog_id);
                    fetchRecentLocations(res.messages);
                });
            }
        });
        return messages != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.locationPermissionGranted) {
            if (googleMap != null) {
                try {
                    googleMap.setMyLocationEnabled(true);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.didReceiveNewMessages) {
            boolean scheduled = (Boolean) args[2];
            if (scheduled) {
                return;
            }
            long did = (Long) args[0];
            if (did != dialogId || messageObject == null) {
                return;
            }
            ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
            boolean added = false;
            for (int a = 0; a < arr.size(); a++) {
                MessageObject messageObject = arr.get(a);
                if (messageObject.isLiveLocation()) {
                    addUserMarker(messageObject.messageOwner);
                    added = true;
                }
            }
            if (added && adapter != null) {
                adapter.setLiveLocations(markers);
            }
        } else if (id == NotificationCenter.replaceMessagesObjects) {
            long did = (long) args[0];
            if (did != dialogId || messageObject == null) {
                return;
            }
            boolean updated = false;
            ArrayList<MessageObject> messageObjects = (ArrayList<MessageObject>) args[1];
            for (int a = 0; a < messageObjects.size(); a++) {
                MessageObject messageObject = messageObjects.get(a);
                if (!messageObject.isLiveLocation()) {
                    continue;
                }
                LiveLocation liveLocation = markersMap.get(getMessageId(messageObject.messageOwner));
                if (liveLocation != null) {
                    LocationController.SharingLocationInfo myInfo = getLocationController().getSharingLocationInfo(did);
                    if (myInfo == null || myInfo.mid != messageObject.getId()) {
                        liveLocation.marker.setPosition(new LatLng(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long));
                    }
                    updated = true;
                }
            }
            if (updated && adapter != null) {
                adapter.updateLiveLocations();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null && mapsInitialized) {
            try {
                mapView.onPause();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        onResumeCalled = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        if (mapView != null && mapsInitialized) {
            try {
                mapView.onResume();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        onResumeCalled = true;
        if (googleMap != null) {
            try {
                googleMap.setMyLocationEnabled(true);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        fixLayoutInternal(true);
        if (checkPermission && Build.VERSION.SDK_INT >= 23) {
            Activity activity = getParentActivity();
            if (activity != null) {
                checkPermission = false;
                if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                }
            }
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null && mapsInitialized) {
            mapView.onLowMemory();
        }
    }

    public void setDelegate(LocationActivityDelegate delegate) {
        this.delegate = delegate;
    }

    public void setChatActivity(ChatActivity chatActivity) {
        parentFragment = chatActivity;
    }

    private void updateSearchInterface() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {

        };
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder),
                new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(locationButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_profile_actionIcon),
                new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_profile_actionBackground),
                new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_profile_actionPressedBackground),

                new ThemeDescription(routeButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon),
                new ThemeDescription(routeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground),
                new ThemeDescription(routeButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground),

                new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection),

                new ThemeDescription(null, 0, null, null, new Drawable[]{Theme.avatar_savedDrawable}, cellDelegate, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(null, 0, null, null, null, null, Theme.key_location_liveLocationProgress),
                new ThemeDescription(null, 0, null, null, null, null, Theme.key_location_placeLocationBackground),
                new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialog_liveLocationProgress),

                new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLocationIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLiveLocationIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLocationBackground),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLiveLocationBackground),
                new ThemeDescription(listView, 0, new Class[]{SendLocationCell.class}, new String[]{"accurateTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText7),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{LocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, 0, new Class[]{LocationCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{LocationCell.class}, new String[]{"addressTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(searchListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{LocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(searchListView, 0, new Class[]{LocationCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(searchListView, 0, new Class[]{LocationCell.class}, new String[]{"addressTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(listView, 0, new Class[]{LocationLoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle),
                new ThemeDescription(listView, 0, new Class[]{LocationLoadingCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(listView, 0, new Class[]{LocationPoweredCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, 0, new Class[]{LocationPoweredCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, 0, new Class[]{LocationPoweredCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
        };
    }
}
