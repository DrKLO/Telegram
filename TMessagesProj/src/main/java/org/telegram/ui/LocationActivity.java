/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Outline;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;

import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Adapters.BaseLocationAdapter;
import org.telegram.ui.Adapters.LocationActivityAdapter;
import org.telegram.ui.Adapters.LocationActivitySearchAdapter;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MapPlaceholderDrawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private GoogleMap googleMap;
    private TextView distanceTextView;
    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private MapView mapView;
    private FrameLayout mapViewClip;
    private LocationActivityAdapter adapter;
    private ListView listView;
    private ListView searchListView;
    private LocationActivitySearchAdapter searchAdapter;
    private LinearLayout emptyTextLayout;
    private ImageView markerImageView;
    private ImageView markerXImageView;
    private ImageView locationButton;

    private AnimatorSet animatorSet;

    private boolean checkPermission = true;

    private boolean searching;
    private boolean searchWas;

    private boolean wasResults;

    private Location myLocation;
    private Location userLocation;
    private int markerTop;

    private MessageObject messageObject;
    private boolean userLocationMoved = false;
    private boolean firstWas = false;
    private CircleOptions circleOptions;
    private LocationActivityDelegate delegate;

    private int overScrollHeight = AndroidUtilities.displaySize.x - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(66);
    private int halfHeight;

    private final static int share = 1;
    private final static int map_list_menu_map = 2;
    private final static int map_list_menu_satellite = 3;
    private final static int map_list_menu_hybrid = 4;

    public interface LocationActivityDelegate {
        void didSelectLocation(TLRPC.MessageMedia location);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        swipeBackEnabled = false;
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.locationPermissionGranted);
        if (messageObject != null) {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        if (mapView != null) {
            mapView.onDestroy();
        }
        if (adapter != null) {
            adapter.destroy();
        }
        if (searchAdapter != null) {
            searchAdapter.destroy();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAddToContainer(messageObject != null);

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
                        FileLog.e("tmessages", e);
                    }
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        if (messageObject != null) {
            if (messageObject.messageOwner.media.title != null && messageObject.messageOwner.media.title.length() > 0) {
                actionBar.setTitle(messageObject.messageOwner.media.title);
                if (messageObject.messageOwner.media.address != null && messageObject.messageOwner.media.address.length() > 0) {
                    actionBar.setSubtitle(messageObject.messageOwner.media.address);
                }
            } else {
                actionBar.setTitle(LocaleController.getString("ChatLocation", R.string.ChatLocation));
            }
            menu.addItem(share, R.drawable.share);
        } else {
            actionBar.setTitle(LocaleController.getString("ShareLocation", R.string.ShareLocation));

            ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    listView.setVisibility(View.GONE);
                    mapViewClip.setVisibility(View.GONE);
                    searchListView.setVisibility(View.VISIBLE);
                    searchListView.setEmptyView(emptyTextLayout);
                }

                @Override
                public void onSearchCollapse() {
                    searching = false;
                    searchWas = false;
                    searchListView.setEmptyView(null);
                    listView.setVisibility(View.VISIBLE);
                    mapViewClip.setVisibility(View.VISIBLE);
                    searchListView.setVisibility(View.GONE);
                    emptyTextLayout.setVisibility(View.GONE);
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
                    searchAdapter.searchDelayed(text, userLocation);
                }
            });
            item.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));
        }

        ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
        item.addSubItem(map_list_menu_map, LocaleController.getString("Map", R.string.Map), 0);
        item.addSubItem(map_list_menu_satellite, LocaleController.getString("Satellite", R.string.Satellite), 0);
        item.addSubItem(map_list_menu_hybrid, LocaleController.getString("Hybrid", R.string.Hybrid), 0);
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
        locationButton.setBackgroundResource(R.drawable.floating_user_states);
        locationButton.setImageResource(R.drawable.myloc_on);
        locationButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(locationButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(locationButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            locationButton.setStateListAnimator(animator);
            locationButton.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }

        if (messageObject != null) {
            mapView = new MapView(context);
            frameLayout.setBackgroundDrawable(new MapPlaceholderDrawable());
            mapView.onCreate(null);
            try {
                MapsInitializer.initialize(context);
                googleMap = mapView.getMap();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            FrameLayout bottomView = new FrameLayout(context);
            bottomView.setBackgroundResource(R.drawable.location_panel);
            frameLayout.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.LEFT | Gravity.BOTTOM));
            bottomView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (userLocation != null) {
                        LatLng latLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
                        if (googleMap != null) {
                            CameraUpdate position = CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getMaxZoomLevel() - 4);
                            googleMap.animateCamera(position);
                        }
                    }
                }
            });

            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(20));
            bottomView.addView(avatarImageView, LayoutHelper.createFrame(40, 40, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 12, 12, LocaleController.isRTL ? 12 : 0, 0));

            nameTextView = new TextView(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameTextView.setTextColor(0xff212121);
            nameTextView.setMaxLines(1);
            nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setSingleLine(true);
            nameTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            bottomView.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 12 : 72, 10, LocaleController.isRTL ? 72 : 12, 0));

            distanceTextView = new TextView(context);
            distanceTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            distanceTextView.setTextColor(0xff2f8cc9);
            distanceTextView.setMaxLines(1);
            distanceTextView.setEllipsize(TextUtils.TruncateAt.END);
            distanceTextView.setSingleLine(true);
            distanceTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            bottomView.addView(distanceTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 12 : 72, 33, LocaleController.isRTL ? 72 : 12, 0));

            userLocation = new Location("network");
            userLocation.setLatitude(messageObject.messageOwner.media.geo.lat);
            userLocation.setLongitude(messageObject.messageOwner.media.geo._long);
            if (googleMap != null) {
                LatLng latLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
                try {
                    googleMap.addMarker(new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.fromResource(R.drawable.map_pin)));
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                CameraUpdate position = CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getMaxZoomLevel() - 4);
                googleMap.moveCamera(position);
            }

            ImageView routeButton = new ImageView(context);
            routeButton.setBackgroundResource(R.drawable.floating_states);
            routeButton.setImageResource(R.drawable.navigate);
            routeButton.setScaleType(ImageView.ScaleType.CENTER);
            if (Build.VERSION.SDK_INT >= 21) {
                StateListAnimator animator = new StateListAnimator();
                animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(routeButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                animator.addState(new int[]{}, ObjectAnimator.ofFloat(routeButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                routeButton.setStateListAnimator(animator);
                routeButton.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    }
                });
            }
            frameLayout.addView(routeButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 28));
            routeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
                            Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, "http://maps.google.com/maps?saddr=%f,%f&daddr=%f,%f", myLocation.getLatitude(), myLocation.getLongitude(), messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long)));
                            getParentActivity().startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
            });

            frameLayout.addView(locationButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 100));
            locationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        Activity activity = getParentActivity();
                        if (activity != null) {
                            if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                showPermissionAlert(true);
                                return;
                            }
                        }
                    }
                    if (myLocation != null && googleMap != null) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(myLocation.getLatitude(), myLocation.getLongitude()), googleMap.getMaxZoomLevel() - 4));
                    }
                }
            });
        } else {
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

            listView = new ListView(context);
            listView.setAdapter(adapter = new LocationActivityAdapter(context));
            listView.setVerticalScrollBarEnabled(false);
            listView.setDividerHeight(0);
            listView.setDivider(null);
            frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {

                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (totalItemCount == 0) {
                        return;
                    }
                    updateClipView(firstVisibleItem);
                }
            });
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (position == 1) {
                        if (delegate != null && userLocation != null) {
                            TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                            location.geo = new TLRPC.TL_geoPoint();
                            location.geo.lat = userLocation.getLatitude();
                            location.geo._long = userLocation.getLongitude();
                            delegate.didSelectLocation(location);
                        }
                        finishFragment();
                    } else {
                        TLRPC.TL_messageMediaVenue object = adapter.getItem(position);
                        if (object != null && delegate != null) {
                            delegate.didSelectLocation(object);
                        }
                        finishFragment();
                    }
                }
            });
            adapter.setDelegate(new BaseLocationAdapter.BaseLocationAdapterDelegate() {
                @Override
                public void didLoadedSearchResult(ArrayList<TLRPC.TL_messageMediaVenue> places) {
                    if (!wasResults && !places.isEmpty()) {
                        wasResults = true;
                    }
                }
            });
            adapter.setOverScrollHeight(overScrollHeight);

            frameLayout.addView(mapViewClip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

            mapView = new MapView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (Build.VERSION.SDK_INT >= 11) {
                        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                            if (animatorSet != null) {
                                animatorSet.cancel();
                            }
                            animatorSet = new AnimatorSet();
                            animatorSet.setDuration(200);
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(markerImageView, "translationY", markerTop + -AndroidUtilities.dp(10)),
                                    ObjectAnimator.ofFloat(markerXImageView, "alpha", 1.0f));
                            animatorSet.start();
                        } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                            if (animatorSet != null) {
                                animatorSet.cancel();
                            }
                            animatorSet = new AnimatorSet();
                            animatorSet.setDuration(200);
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(markerImageView, "translationY", markerTop),
                                    ObjectAnimator.ofFloat(markerXImageView, "alpha", 0.0f));
                            animatorSet.start();
                        }
                    }
                    if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                        if (!userLocationMoved) {
                            if (Build.VERSION.SDK_INT >= 11) {
                                AnimatorSet animatorSet = new AnimatorSet();
                                animatorSet.setDuration(200);
                                animatorSet.play(ObjectAnimator.ofFloat(locationButton, "alpha", 1.0f));
                                animatorSet.start();
                            } else {
                                locationButton.setVisibility(VISIBLE);
                            }
                            userLocationMoved = true;
                        }
                        if (googleMap != null && userLocation != null) {
                            userLocation.setLatitude(googleMap.getCameraPosition().target.latitude);
                            userLocation.setLongitude(googleMap.getCameraPosition().target.longitude);
                        }
                        adapter.setCustomLocation(userLocation);
                    }
                    return super.onInterceptTouchEvent(ev);
                }
            };
            try {
                mapView.onCreate(null);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            try {
                MapsInitializer.initialize(context);
                googleMap = mapView.getMap();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            View shadow = new View(context);
            shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
            mapViewClip.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.LEFT | Gravity.BOTTOM));

            markerImageView = new ImageView(context);
            markerImageView.setImageResource(R.drawable.map_pin);
            mapViewClip.addView(markerImageView, LayoutHelper.createFrame(24, 42, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            if (Build.VERSION.SDK_INT >= 11) {
                markerXImageView = new ImageView(context);
                markerXImageView.setAlpha(0.0f);
                markerXImageView.setImageResource(R.drawable.place_x);
                mapViewClip.addView(markerXImageView, LayoutHelper.createFrame(14, 14, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            }

            mapViewClip.addView(locationButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));
            locationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        Activity activity = getParentActivity();
                        if (activity != null) {
                            if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                showPermissionAlert(false);
                                return;
                            }
                        }
                    }
                    if (myLocation != null && googleMap != null) {
                        if (Build.VERSION.SDK_INT >= 11) {
                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.setDuration(200);
                            animatorSet.play(ObjectAnimator.ofFloat(locationButton, "alpha", 0.0f));
                            animatorSet.start();
                        } else {
                            locationButton.setVisibility(View.INVISIBLE);
                        }
                        adapter.setCustomLocation(null);
                        userLocationMoved = false;
                        googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(myLocation.getLatitude(), myLocation.getLongitude())));
                    }
                }
            });
            if (Build.VERSION.SDK_INT >= 11) {
                locationButton.setAlpha(0.0f);
            } else {
                locationButton.setVisibility(View.INVISIBLE);
            }

            emptyTextLayout = new LinearLayout(context);
            emptyTextLayout.setVisibility(View.GONE);
            emptyTextLayout.setOrientation(LinearLayout.VERTICAL);
            frameLayout.addView(emptyTextLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 100, 0, 0));
            emptyTextLayout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            TextView emptyTextView = new TextView(context);
            emptyTextView.setTextColor(0xff808080);
            emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            emptyTextView.setGravity(Gravity.CENTER);
            emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
            emptyTextLayout.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f));

            FrameLayout frameLayoutEmpty = new FrameLayout(context);
            emptyTextLayout.addView(frameLayoutEmpty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f));

            searchListView = new ListView(context);
            searchListView.setVisibility(View.GONE);
            searchListView.setDividerHeight(0);
            searchListView.setDivider(null);
            searchListView.setAdapter(searchAdapter = new LocationActivitySearchAdapter(context));
            frameLayout.addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
            searchListView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (scrollState == SCROLL_STATE_TOUCH_SCROLL && searching && searchWas) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

                }
            });
            searchListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    TLRPC.TL_messageMediaVenue object = searchAdapter.getItem(position);
                    if (object != null && delegate != null) {
                        delegate.didSelectLocation(object);
                    }
                    finishFragment();
                }
            });

            if (googleMap != null) {
                userLocation = new Location("network");
                userLocation.setLatitude(20.659322);
                userLocation.setLongitude(-11.406250);
            }

            frameLayout.addView(actionBar);
        }

        if (googleMap != null) {
            try {
                googleMap.setMyLocationEnabled(true);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            googleMap.getUiSettings().setZoomControlsEnabled(false);
            googleMap.getUiSettings().setCompassEnabled(false);
            googleMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                @Override
                public void onMyLocationChange(Location location) {
                    positionMarker(location);
                }
            });
            positionMarker(myLocation = getLastLocation());
        }

        return fragmentView;
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
        builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), new DialogInterface.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.GINGERBREAD)
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (getParentActivity() == null) {
                    return;
                }
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                    getParentActivity().startActivity(intent);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
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
                FileLog.e("tmessages", e);
            }
            if (mapViewClip != null) {
                mapViewClip.addView(mapView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10), Gravity.TOP | Gravity.LEFT));
                updateClipView(listView.getFirstVisiblePosition());
            } else {
                ((FrameLayout) fragmentView).addView(mapView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            }
        }
    }

    private void updateClipView(int firstVisibleItem) {
        int height = 0;
        int top = 0;
        View child = listView.getChildAt(0);
        if (child != null) {
            if (firstVisibleItem == 0) {
                top = child.getTop();
                height = overScrollHeight + (top < 0 ? top : 0);
                halfHeight = (top < 0 ? top : 0) / 2;
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
                if (Build.VERSION.SDK_INT >= 11) {
                    mapViewClip.setTranslationY(Math.min(0, top));
                    mapView.setTranslationY(Math.max(0, -top / 2));
                    markerImageView.setTranslationY(markerTop = -top - AndroidUtilities.dp(42) + height / 2);
                    markerXImageView.setTranslationY(-top - AndroidUtilities.dp(7) + height / 2);

                    if (googleMap != null) {
                        layoutParams = (FrameLayout.LayoutParams) mapView.getLayoutParams();
                        if (layoutParams != null && layoutParams.height != overScrollHeight + AndroidUtilities.dp(10)) {
                            layoutParams.height = overScrollHeight + AndroidUtilities.dp(10);
                            googleMap.setPadding(0, 0, 0, AndroidUtilities.dp(10));
                            mapView.setLayoutParams(layoutParams);
                        }
                    }
                } else {
                    markerTop = 0;
                    layoutParams.height = height;
                    mapViewClip.setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams) markerImageView.getLayoutParams();
                    layoutParams.topMargin = height / 2 - AndroidUtilities.dp(42);
                    markerImageView.setLayoutParams(layoutParams);

                    if (googleMap != null) {
                        layoutParams = (FrameLayout.LayoutParams) mapView.getLayoutParams();
                        if (layoutParams != null) {
                            layoutParams.topMargin = halfHeight;
                            layoutParams.height = overScrollHeight + AndroidUtilities.dp(10);
                            googleMap.setPadding(0, 0, 0, AndroidUtilities.dp(10));
                            mapView.setLayoutParams(layoutParams);
                        }
                    }
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
            layoutParams = (FrameLayout.LayoutParams) searchListView.getLayoutParams();
            layoutParams.topMargin = height;
            searchListView.setLayoutParams(layoutParams);

            adapter.setOverScrollHeight(overScrollHeight);
            layoutParams = (FrameLayout.LayoutParams) mapView.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = overScrollHeight + AndroidUtilities.dp(10);
                if (googleMap != null) {
                    googleMap.setPadding(0, 0, 0, AndroidUtilities.dp(10));
                }
                mapView.setLayoutParams(layoutParams);
            }
            adapter.notifyDataSetChanged();

            if (resume) {
                listView.setSelectionFromTop(0, -(int) (AndroidUtilities.dp(56) * 2.5f + AndroidUtilities.dp(36 + 66)));
                updateClipView(listView.getFirstVisiblePosition());
                listView.post(new Runnable() {
                    @Override
                    public void run() {
                        listView.setSelectionFromTop(0, -(int) (AndroidUtilities.dp(56) * 2.5f + AndroidUtilities.dp(36 + 66)));
                        updateClipView(listView.getFirstVisiblePosition());
                    }
                });
            } else {
                updateClipView(listView.getFirstVisiblePosition());
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

    private void updateUserData() {
        if (messageObject != null && avatarImageView != null) {
            int fromId = messageObject.messageOwner.from_id;
            if (messageObject.isForwarded()) {
                if (messageObject.messageOwner.fwd_from.channel_id != 0) {
                    fromId = -messageObject.messageOwner.fwd_from.channel_id;
                } else {
                    fromId = messageObject.messageOwner.fwd_from.from_id;
                }
            }
            String name = "";
            TLRPC.FileLocation photo = null;
            AvatarDrawable avatarDrawable = null;
            if (fromId > 0) {
                TLRPC.User user = MessagesController.getInstance().getUser(fromId);
                if (user != null) {
                    if (user.photo != null) {
                        photo = user.photo.photo_small;
                    }
                    avatarDrawable = new AvatarDrawable(user);
                    name = UserObject.getUserName(user);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance().getChat(-fromId);
                if (chat != null) {
                    if (chat.photo != null) {
                        photo = chat.photo.photo_small;
                    }
                    avatarDrawable = new AvatarDrawable(chat);
                    name = chat.title;
                }
            }
            if (avatarDrawable != null) {
                avatarImageView.setImage(photo, null, avatarDrawable);
                nameTextView.setText(name);
            } else {
                avatarImageView.setImageDrawable(null);
            }
        }
    }

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        myLocation = new Location(location);
        if (messageObject != null) {
            if (userLocation != null && distanceTextView != null) {
                float distance = location.distanceTo(userLocation);
                if (distance < 1000) {
                    distanceTextView.setText(String.format("%d %s", (int) (distance), LocaleController.getString("MetersAway", R.string.MetersAway)));
                } else {
                    distanceTextView.setText(String.format("%.2f %s", distance / 1000.0f, LocaleController.getString("KMetersAway", R.string.KMetersAway)));
                }
            }
        } else if (googleMap != null) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            if (adapter != null) {
                adapter.searchGooglePlacesWithQuery(null, myLocation);
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
        }
    }

    public void setMessageObject(MessageObject message) {
        messageObject = message;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                updateUserData();
            }
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.locationPermissionGranted) {
            if (googleMap != null) {
                try {
                    googleMap.setMyLocationEnabled(true);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            try {
                mapView.onPause();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
        if (mapView != null) {
            try {
                mapView.onResume();
            } catch (Throwable e) {
                FileLog.e("tmessages", e);
            }
        }
        if (googleMap != null) {
            try {
                googleMap.setMyLocationEnabled(true);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
        updateUserData();
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
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    public void setDelegate(LocationActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private void updateSearchInterface() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}
