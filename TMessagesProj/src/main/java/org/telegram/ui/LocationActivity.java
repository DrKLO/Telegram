/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.objects.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;

public class LocationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private GoogleMap googleMap;
    private TextView distanceTextView;
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
            googleMap = getMap();
            if (googleMap == null) {
                return;
            }

            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            googleMap.getUiSettings().setZoomControlsEnabled(false);
            googleMap.getUiSettings().setCompassEnabled(false);
            googleMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                @Override
                public void onMyLocationChange(Location location) {
                    positionMarker(location);
                }
            });
            myLocation = googleMap.getMyLocation();

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
                        NotificationCenter.getInstance().postNotificationName(997, userLocation.getLatitude(), userLocation.getLongitude());
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
                TLRPC.User user = MessagesController.getInstance().users.get(fromId);
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
        messageObject = (MessageObject)NotificationCenter.getInstance().getFromMemCache(0);
        NotificationCenter.getInstance().addObserver(this, MessagesController.closeChats);
        if (messageObject != null) {
            NotificationCenter.getInstance().addObserver(this, MessagesController.updateInterfaces);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, MessagesController.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, MessagesController.closeChats);
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
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setSubtitle(null);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        if (messageObject != null) {
            actionBar.setTitle(LocaleController.getString("ChatLocation", R.string.ChatLocation));
        } else {
            actionBar.setTitle(LocaleController.getString("ShareLocation", R.string.ShareLocation));
        }

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
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
        if (getActivity() == null) {
            return;
        }
        ((LaunchActivity)parentActivity).showActionBar();
        ((LaunchActivity)parentActivity).updateActionBar();
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
            if (sendButton != null) {
                sendButton.setText(LocaleController.getString("SendLocation", R.string.SendLocation));
            }

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
            TLRPC.User user = MessagesController.getInstance().users.get(fromId);
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

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        myLocation = location;
        if (messageObject != null) {
            if (userLocation != null && distanceTextView != null) {
                float distance = location.distanceTo(userLocation);
                if (distance < 1000) {
                    distanceTextView.setText(String.format("%d %s", (int)(distance), LocaleController.getString("MetersAway", R.string.MetersAway)));
                } else {
                    distanceTextView.setText(String.format("%.2f %s", distance / 1000.0f, LocaleController.getString("KMetersAway", R.string.KMetersAway)));
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
    public void didReceivedNotification(int id, Object... args) {
        if (id == MessagesController.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                updateUserData();
            }
        } else if (id == MessagesController.closeChats) {
            removeSelfFromStack();
        }
    }
}
