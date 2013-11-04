/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import org.telegram.TL.TLRPC;
import org.telegram.objects.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;

public class LocationActivity extends BaseFragment implements LocationListener, NotificationCenter.NotificationCenterDelegate {
    private GoogleMap googleMap;
    private TextView distanceTextView;
    private Marker myLocationMarker;
    private Marker userMarker;
    private Location myLocation;
    private Location userLocation;
    private MessageObject messageObject;
    private BackupImageView avatarImageView;
    private TextView nameTextView;
    private View bottomView;
    private TextView sendButton;
    private boolean userLocationMoved = false;
    private boolean firstWas = false;

    public SupportMapFragment mapFragment = new SupportMapFragment() {
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            setupLocationListener();
            googleMap = getMap();
            if (googleMap == null) {
                return;
            }

            googleMap.setMyLocationEnabled(false);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            googleMap.getUiSettings().setZoomControlsEnabled(false);
            googleMap.getUiSettings().setCompassEnabled(false);

            if (sendButton != null) {
                userLocation = new Location("network");
                userLocation.setLatitude(20.659322);
                userLocation.setLongitude(-11.406250);
                LatLng latLng = new LatLng(20.659322, -11.406250);
                userMarker = googleMap.addMarker(new MarkerOptions().position(latLng).
                        icon(BitmapDescriptorFactory.fromResource(R.drawable.map_pin)).draggable(true));

                sendButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        NotificationCenter.Instance.postNotificationName(997, userLocation.getLatitude(), userLocation.getLongitude());
                        finishFragment();
                    }
                });

                googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
                    @Override
                    public void onMarkerDragStart(Marker marker) {
                    }

                    @Override
                    public void onMarkerDrag(Marker marker) {
                        userLocationMoved = true;
                    }

                    @Override
                    public void onMarkerDragEnd(Marker marker) {
                        LatLng latLng = marker.getPosition();
                        userLocation.setLatitude(latLng.latitude);
                        userLocation.setLongitude(latLng.longitude);
                    }
                });
            }

            if (bottomView != null) {
                bottomView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (userLocation != null) {
                            LatLng latLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
                            CameraUpdate position = CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getMaxZoomLevel() - 8);
                            googleMap.animateCamera(position);
                        }
                    }
                });
            }

            if (messageObject != null) {
                int fromId = messageObject.messageOwner.from_id;
                if (messageObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
                    fromId = messageObject.messageOwner.fwd_from_id;
                }
                TLRPC.User user = MessagesController.Instance.users.get(fromId);
                if (user != null) {
                    avatarImageView.setImage(user.photo.photo_small, "50_50", Utilities.getUserAvatarForId(user.id));
                    nameTextView.setText(Utilities.formatName(user.first_name, user.last_name));
                }
                userLocation = new Location("network");
                userLocation.setLatitude(messageObject.messageOwner.media.geo.lat);
                userLocation.setLongitude(messageObject.messageOwner.media.geo._long);
                LatLng latLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
                userMarker = googleMap.addMarker(new MarkerOptions().position(latLng).
                        icon(BitmapDescriptorFactory.fromResource(R.drawable.map_pin)));
                CameraUpdate position = CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getMaxZoomLevel() - 8);
                googleMap.moveCamera(position);
            }

            positionMarker(myLocation);

            ViewGroup topLayout = (ViewGroup)parentActivity.findViewById(R.id.container);
            topLayout.requestTransparentRegion(topLayout);
        }
    };

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        messageObject = (MessageObject)NotificationCenter.Instance.getFromMemCache(0);
        NotificationCenter.Instance.addObserver(this, MessagesController.closeChats);
        if (messageObject != null) {
            NotificationCenter.Instance.addObserver(this, MessagesController.updateInterfaces);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        LocationManager locationManager = (LocationManager)parentActivity.getSystemService(Activity.LOCATION_SERVICE);
        locationManager.removeUpdates(this);
        NotificationCenter.Instance.removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.Instance.removeObserver(this, MessagesController.closeChats);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        final ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setSubtitle(null);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        if (messageObject != null) {
            actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.ChatLocation) + "</font>"));
        } else {
            actionBar.setTitle(Html.fromHtml("<font color='#006fc8'>" + getStringEntry(R.string.ShareLocation) + "</font>"));
        }

        TextView title = (TextView)parentActivity.findViewById(R.id.abs__action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getSherlockActivity() == null) {
            return;
        }
        ((ApplicationActivity)parentActivity).showActionBar();
        ((ApplicationActivity)parentActivity).updateActionBar();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            if (messageObject != null) {
                fragmentView = inflater.inflate(R.layout.location_view_layout, container, false);
            } else {
                fragmentView = inflater.inflate(R.layout.location_attach_layout, container, false);
            }

            avatarImageView = (BackupImageView)fragmentView.findViewById(R.id.location_avatar_view);
            nameTextView = (TextView)fragmentView.findViewById(R.id.location_name_label);
            distanceTextView = (TextView)fragmentView.findViewById(R.id.location_distance_label);
            bottomView = fragmentView.findViewById(R.id.location_bottom_view);
            sendButton = (TextView)fragmentView.findViewById(R.id.location_send_button);

            getChildFragmentManager().beginTransaction().replace(R.id.map_view, mapFragment).commit();
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void updateUserData() {
        if (messageObject != null && avatarImageView != null) {
            int fromId = messageObject.messageOwner.from_id;
            if (messageObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
                fromId = messageObject.messageOwner.fwd_from_id;
            }
            TLRPC.User user = MessagesController.Instance.users.get(fromId);
            if (user != null) {
                TLRPC.FileLocation photo = null;
                if (user.photo != null) {
                    photo = user.photo.photo_small;
                }
                avatarImageView.setImage(photo, null, Utilities.getUserAvatarForId(user.id));
                nameTextView.setText(Utilities.formatName(user.first_name, user.last_name));
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.location_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.map_list_menu_map:
                if (googleMap != null) {
                    googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                }
                break;
            case R.id.map_list_menu_satellite:
                if (googleMap != null) {
                    googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                }
                break;
            case R.id.map_list_menu_hybrid:
                if (googleMap != null) {
                    googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                }
                break;
            case R.id.map_to_my_location:
                if (myLocation != null) {
                    LatLng latLng = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
                    if (googleMap != null) {
                        CameraUpdate position = CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getMaxZoomLevel() - 8);
                        googleMap.animateCamera(position);
                    }
                }
                break;
            case android.R.id.home:
                finishFragment();
                break;
        }
        return true;
    }

    private void setupLocationListener() {
        try {
            LocationManager locationManager = (LocationManager)parentActivity.getSystemService(Activity.LOCATION_SERVICE);
            boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            Location location = null;

            if (isGPSEnabled || isNetworkEnabled) {
                if (isGPSEnabled) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 3 * 1, 1, this);
                    if (locationManager != null) {
                        location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }
                }
                if (location == null && isNetworkEnabled) {
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000 * 3 * 1, 1, this);
                    if (locationManager != null) {
                        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                }
            }
            positionMarker(location);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        myLocation = location;
        if (myLocationMarker == null) {
            if (googleMap != null) {
                myLocationMarker = googleMap.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())).
                        icon(BitmapDescriptorFactory.fromResource(R.drawable.map_dot)).anchor(0.5f, 0.5f));
            }
        } else {
            myLocationMarker.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
        }
        if (messageObject != null) {
            if (userLocation != null && distanceTextView != null) {
                float distance = location.distanceTo(userLocation);
                if (distance < 1000) {
                    distanceTextView.setText(String.format("%d %s", (int)(distance), ApplicationLoader.applicationContext.getString(R.string.MetersAway)));
                } else {
                    distanceTextView.setText(String.format("%.2f %s", distance / 1000.0f, ApplicationLoader.applicationContext.getString(R.string.KMetersAway)));
                }
            }
        } else {
            if (!userLocationMoved && googleMap != null) {
                userLocation = location;
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                userMarker.setPosition(latLng);
                if (firstWas) {
                    CameraUpdate position = CameraUpdateFactory.newLatLng(latLng);
                    googleMap.animateCamera(position);
                } else {
                    firstWas = true;
                    CameraUpdate position = CameraUpdateFactory.newLatLngZoom(latLng, googleMap.getMaxZoomLevel() - 8);
                    googleMap.moveCamera(position);
                }
            }
        }

    }

    @Override
    public void onLocationChanged(final Location location) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                positionMarker(location);
            }
        });
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            updateUserData();
        } else if (id == MessagesController.closeChats) {
            removeSelfFromStack();
        }
    }
}
