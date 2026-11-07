/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.IMapsProvider;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MenuOverlayHost;
import org.telegram.ui.ActionBar.SimpleActionBarMenu;
import org.telegram.ui.ActionBar.SimpleActionBarMenuOverlay;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.LocationActivityAdapter;
import org.telegram.ui.Adapters.LocationActivitySearchAdapter;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LocationDirectionCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SharingLiveLocationCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatAttachAlertLocationLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MapGlassSnapshot;
import org.telegram.ui.Components.MapPlaceholderDrawable;
import org.telegram.ui.Components.ProximitySheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.chat.ChatInputViewsContainer;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.utils.glass.BaseFragmentSimpleGlass;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LocationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ImageView locationButton;
    private TextView showAllButton;
    private ImageView proximityButton;
    private ActionBarMenuItem mapTypeButton;
    private LinearLayout emptyView;
    private ImageView emptyImageView;
    private TextView emptyTitleTextView;
    private TextView emptySubtitleTextView;
    private ActionBarMenuItem searchItem;
    private HintView2 hintView;
    public boolean fromStories;

    private UndoView[] undoView = new UndoView[2];
    private boolean canUndo;

    private boolean proximityAnimationInProgress;

    private IMapsProvider.IMap map;
    private IMapsProvider.ICameraUpdate moveToBounds;
    private IMapsProvider.IMapView mapView;
    private IMapsProvider.ICameraUpdate forceUpdate;
    private boolean hasScreenshot;
    private float yOffset;

    private IMapsProvider.ICircle proximityCircle;
    private double previousRadius;

    private boolean scrolling;

    private ProximitySheet proximitySheet;

    private FrameLayout mapViewClip;
    private LocationActivityAdapter adapter;
    private RecyclerListView listView;
    private RecyclerListView searchListView;
    private LocationActivitySearchAdapter searchAdapter;
    private View markerImageView;
    private LinearLayoutManager layoutManager;
    private AvatarDrawable avatarDrawable;
    private ActionBarMenuItem otherItem;
    private ChatActivity parentFragment;

    /** The size of the glass of the map's buttons; their views are larger by the glass padding on each side. */
    private static final int MAP_BUTTON_DP = 46;
    private static final int MAP_BUTTON_PADDING_DP = 6;

    /** The glass buttons of the map under the header, and the ones at its bottom: they fade out when the list comes up. */
    private FrameLayout mapTopButtons;
    private FrameLayout mapBottomButtons;
    /** The list's sheet: frosted glass over the map, the map goes on under it. */
    private BlurredBackgroundDrawable sheetGlass;
    private final RectF sheetHandleRect = new RectF();
    /** The top of the sheet in the list and on the screen. */
    private int sheetTop;
    /** How far the map goes on under the sheet, for its frosted glass; the map's centre stays above the sheet. */
    private int mapExtraHeight;
    /** The map's own view when it is a texture: the glass captures it as it is. */
    @Nullable
    private TextureView mapTexture;
    /** A copy of the map's surface when the map is a GL surface, which the glass cannot capture. */
    @Nullable
    private MapGlassSnapshot mapSnapshot;
    private final RectF captureRect = new RectF();
    private final Paint captureFillPaint = new Paint();
    /** From the end of the map's space in the list to the top edge of the list's sheet, which is drawn over the map. */
    private int mapSheetTopOffset;
    /** The rounding of the sheet's top, as the panel under the chat's keyboard. */
    private static final int SHEET_RADIUS_DP = ChatInputViewsContainer.INPUT_KEYBOARD_RADIUS;

    private boolean currentMapStyleDark;

    private boolean checkGpsEnabled = true;
    private boolean locationDenied = false;

    private boolean isFirstLocation = true;
    private long dialogId;

    private boolean firstFocus = true;

    private Runnable updateRunnable;

    private ArrayList<LiveLocation> markers = new ArrayList<>();
    private LongSparseArray<LiveLocation> markersMap = new LongSparseArray<>();
    private long selectedMarkerId = -1;
    private boolean showAllMode;


    private AnimatorSet animatorSet;

    private boolean checkPermission = true;

    private boolean searching;
    private boolean searchWas;
    private boolean searchInProgress;

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
    private LocationActivityDelegate delegate;

    private int locationType;

    private int overScrollHeight = AndroidUtilities.displaySize.x - ActionBar.getCurrentActionBarHeight() - dp(66);

    private final static int open_in = 1;
    private final static int share_live_location = 5;
    private final static int get_directions = 6;
    private final static int map_list_menu_map = 2;
    private final static int map_list_menu_satellite = 3;
    private final static int map_list_menu_hybrid = 4;

    public final static int LOCATION_TYPE_SEND = 0;
    public final static int LOCATION_TYPE_SEND_WITH_LIVE = 1;
    public final static int LOCATION_TYPE_LIVE = 2;
    public final static int LOCATION_TYPE_GROUP = 4;
    public final static int LOCATION_TYPE_GROUP_VIEW = 5;
    public final static int LOCATION_TYPE_LIVE_VIEW = 6;

    private ActionBarPopupWindow popupWindow;

    private Runnable markAsReadRunnable;

    public static class LiveLocation {
        public long id;
        public TLRPC.Message object;
        public TLRPC.User user;
        public TLRPC.Chat chat;
        public IMapsProvider.IMarker marker;
        public IMapsProvider.IMarker directionMarker;
        public boolean hasRotation;
        public ImageReceiver avatarReceiver;
    }

    public interface LocationActivityDelegate {
        void didSelectLocation(TLRPC.MessageMedia location, int live, boolean notify, int scheduleDate, long payStars);
    }

    public LocationActivity(int type) {
        super();
        locationType = type;
        AndroidUtilities.fixGoogleMapsBug();
    }

    private SharedMediaLayout sharedMediaLayout;
    private GraySectionCell sharedMediaHeader;
    private TL_stories.MediaArea searchStoriesArea;

    public LocationActivity searchStories(TL_stories.MediaArea area) {
        searchStoriesArea = area;
        return this;
    }

    private boolean initialMaxZoom;
    public void setInitialMaxZoom(boolean initialMaxZoom) {
        this.initialMaxZoom = initialMaxZoom;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addWeakObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getGlobalInstance().addWeakObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().addWeakObserver(this, NotificationCenter.locationPermissionDenied);
        NotificationCenter.getGlobalInstance().addWeakObserver(this, NotificationCenter.liveLocationsChanged);
        if (messageObject != null && messageObject.isLiveLocation()) {
            getNotificationCenter().addWeakObserver(this, NotificationCenter.didReceiveNewMessages);
            getNotificationCenter().addWeakObserver(this, NotificationCenter.replaceMessagesObjects);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (mapSnapshot != null) {
            mapSnapshot.release();
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.locationPermissionDenied);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.liveLocationsChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.closeChats);
        getNotificationCenter().removeObserver(this, NotificationCenter.didReceiveNewMessages);
        getNotificationCenter().removeObserver(this, NotificationCenter.replaceMessagesObjects);
        try {
            if (map != null) {
                map.setMyLocationEnabled(false);
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
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
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
        if (markAsReadRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(markAsReadRunnable);
            markAsReadRunnable = null;
        }
        for (int a = 0, N = markers.size(); a < N; a++) {
            LiveLocation liveLocation = markers.get(a);
            if (liveLocation.avatarReceiver != null) {
                liveLocation.avatarReceiver.onDetachedFromWindow();
                liveLocation.avatarReceiver = null;
            }
        }
    }

    private UndoView getUndoView() {
        if (undoView[0].getVisibility() == View.VISIBLE) {
            UndoView old = undoView[0];
            undoView[0] = undoView[1];
            undoView[1] = old;
            old.hide(true, 2);
            mapViewClip.removeView(undoView[0]);
            mapViewClip.addView(undoView[0]);
        }
        return undoView[0];
    }

    private boolean isSharingAllowed = true;
    public void setSharingAllowed(boolean allowed) {
        isSharingAllowed = allowed;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public View createView(Context context) {
        searchWas = false;
        searching = false;
        searchInProgress = false;
        if (adapter != null) {
            adapter.destroy();
        }
        if (searchAdapter != null) {
            searchAdapter.destroy();
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
        locationDenied = Build.VERSION.SDK_INT >= 23 && getParentActivity() != null && getParentActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (parentLayout != null && parentLayout.isLayersLayout()) {
            simpleActionBar.setOccupyStatusBar(false);
        }

        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == open_in) {
                    try {
                        double lat = messageObject.messageOwner.media.geo.lat;
                        double lon = messageObject.messageOwner.media.geo._long;
                        getParentActivity().startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (id == share_live_location) {
                    openShareLiveLocation(false, 0);
                } else if (id == get_directions) {
                    openDirections(null);
                } else if ((id == map_list_menu_map || id == map_list_menu_satellite || id == map_list_menu_hybrid) && map != null) {
                    pokeMapSnapshot();
                    map.setMapType(id == map_list_menu_satellite ? IMapsProvider.MAP_TYPE_SATELLITE : id == map_list_menu_hybrid ? IMapsProvider.MAP_TYPE_HYBRID : IMapsProvider.MAP_TYPE_NORMAL);
                }
            }
        });

        SimpleActionBarMenu menu = simpleActionBar.createMenu();
        mapTypeButton = menu.addItem(0, R.drawable.msg_map_type);
        mapTypeButton.addSubItem(map_list_menu_map, R.drawable.msg_map, getString(R.string.Map));
        mapTypeButton.addSubItem(map_list_menu_satellite, R.drawable.msg_satellite, getString(R.string.Satellite));
        mapTypeButton.addSubItem(map_list_menu_hybrid, R.drawable.msg_hybrid, getString(R.string.Hybrid));
        mapTypeButton.setContentDescription(getString(R.string.AccDescrMoreOptions));
        if (chatLocation != null) {
            simpleActionBar.setTitle(getString(R.string.ChatLocation));
        } else if (messageObject != null) {
            if (messageObject.isLiveLocation()) {
                simpleActionBar.setTitle(getString(R.string.AttachLiveLocation));
                otherItem = menu.addItem(0, R.drawable.ic_ab_other);
                otherItem.addSubItem(get_directions, R.drawable.filled_directions, getString(R.string.GetDirections));
            } else {
                if (locationType == 3) {
                    // the header's centre is the Open in Maps pill instead of the title
                } else if (messageObject.messageOwner.media.title != null && messageObject.messageOwner.media.title.length() > 0) {
                    simpleActionBar.setTitle(getString(R.string.SharedPlace));
                } else {
                    simpleActionBar.setTitle(getString(R.string.ChatLocation));
                }
                if (locationType != 3) {
                    otherItem = menu.addItem(0, R.drawable.ic_ab_other);
                    otherItem.addSubItem(open_in, R.drawable.msg_openin, getString(R.string.OpenInExternalApp));
                    if (!getLocationController().isSharingLocation(dialogId) && isSharingAllowed) {
                        otherItem.addSubItem(share_live_location, R.drawable.msg_location, getString(R.string.SendLiveLocationMenu));
                    }
                    otherItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
                }
            }
        } else {
            simpleActionBar.setTitle(getString(R.string.ShareLocation));

            if (locationType != LOCATION_TYPE_GROUP) {
                searchItem = menu.addItem(0, R.drawable.outline_header_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        searching = true;
                    }

                    @Override
                    public void onSearchCollapse() {
                        searching = false;
                        searchWas = false;
                        searchAdapter.searchDelayed(null);
                        updateEmptyView();
                        // the glass header closes its search with the text in it: the map and the list come back here
                        if (otherItem != null) {
                            otherItem.setVisibility(View.VISIBLE);
                        }
                        listView.setVisibility(View.VISIBLE);
                        mapViewClip.setVisibility(View.VISIBLE);
                        searchListView.setAdapter(null);
                        searchListView.setVisibility(View.GONE);
                        updateClipView(false);
                    }

                    @Override
                    public void onTextChanged(EditText editText) {
                        if (searchAdapter == null) {
                            return;
                        }
                        String text = editText.getText().toString();
                        if (text.length() != 0) {
                            searchWas = true;
                            searchItem.setShowSearchProgress(true);

                            if (otherItem != null) {
                                otherItem.setVisibility(View.GONE);
                            }
                            listView.setVisibility(View.GONE);
                            mapViewClip.setVisibility(View.GONE);
                            if (searchListView.getAdapter() != searchAdapter) {
                                searchListView.setAdapter(searchAdapter);
                            }
                            searchListView.setVisibility(View.VISIBLE);
                            searchInProgress = searchAdapter.getItemCount() == 0;
                        } else {
                            if (otherItem != null) {
                                otherItem.setVisibility(View.VISIBLE);
                            }
                            listView.setVisibility(View.VISIBLE);
                            mapViewClip.setVisibility(View.VISIBLE);
                            searchListView.setAdapter(null);
                            searchListView.setVisibility(View.GONE);
                        }
                        updateEmptyView();
                        updateMapSnapshotRunning();
                        searchAdapter.searchDelayed(text);
                    }
                });
                searchItem.setSearchFieldHint(getString(R.string.Search));
                searchItem.setContentDescription(getString(R.string.Search));
            }
        }

        fragmentView = new NestedFrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        ThemeBind.backgroundColor(fragmentView, Theme.key_dialogBackground, getResourceProvider());

        // the sheet's rounded top goes over the map; with its handle when it can be pulled up
        mapSheetTopOffset = dp(locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE ? 26 : 16);

        mapViewClip = new FrameLayout(context) {
        };
        mapViewClip.setBackgroundDrawable(new MapPlaceholderDrawable(isActiveThemeDark()));

        // the glass buttons under the header (their place is set with the insets) and over the bottom of the map
        final int mapButtonSize = MAP_BUTTON_DP + MAP_BUTTON_PADDING_DP * 2;
        mapTopButtons = new FrameLayout(context);
        mapViewClip.addView(mapTopButtons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        mapBottomButtons = new FrameLayout(context);
        final FrameLayout.LayoutParams bottomButtonsParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM);
        // 7dp between the glass and the top of the list's sheet
        bottomButtonsParams.bottomMargin = dp(SHEET_RADIUS_DP) + mapSheetTopOffset + dp(BaseFragmentSimpleGlass.PILLS_GAP_DP - MAP_BUTTON_PADDING_DP);
        mapViewClip.addView(mapBottomButtons, bottomButtonsParams);

        if (messageObject != null && locationType == 3) {
            final TextView openInMapsButton = new TextView(context);
            setMapButtonGlass(openInMapsButton);
            ThemeBind.textColor(openInMapsButton, Theme.key_location_actionActiveIcon, getResourceProvider());
            openInMapsButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            openInMapsButton.setTypeface(AndroidUtilities.bold());
            openInMapsButton.setGravity(Gravity.CENTER);
            openInMapsButton.setPadding(dp(MAP_BUTTON_PADDING_DP + 18), 0, dp(MAP_BUTTON_PADDING_DP + 18), 0);
            // Open in Maps: a glass pill in the header's centre, in the row of its buttons
            simpleActionBar.addView(openInMapsButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, mapButtonSize, Gravity.CENTER_HORIZONTAL));
            openInMapsButton.setText(getString(R.string.OpenInMaps));
            openInMapsButton.setOnClickListener(v -> {
                try {
                    double lat = messageObject.messageOwner.media.geo.lat;
                    double lon = messageObject.messageOwner.media.geo._long;
                    getParentActivity().startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }

        locationButton = new ImageView(context);
        setMapButtonGlass(locationButton);
        locationButton.setImageResource(R.drawable.msg_current_location);
        locationButton.setScaleType(ImageView.ScaleType.CENTER);
        ThemeBind.colorFilter(locationButton, Theme.key_location_actionActiveIcon, getResourceProvider(), PorterDuff.Mode.MULTIPLY);
        locationButton.setTag(Theme.key_location_actionActiveIcon);
        locationButton.setContentDescription(getString(R.string.AccDescrMyLocation));
        mapBottomButtons.addView(locationButton, LayoutHelper.createFrame(mapButtonSize, mapButtonSize, Gravity.RIGHT | Gravity.BOTTOM));
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
            if (!checkGpsEnabled() && locationType != 3) {
                return;
            }
            if (messageObject != null && locationType != 3 || chatLocation != null) {
                if (myLocation != null && map != null) {
                    map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude()), map.getMaxZoomLevel() - 4));
                }
            } else {
                if (myLocation != null && map != null) {
                    ThemeBind.colorFilter(locationButton, Theme.key_location_actionActiveIcon, getResourceProvider(), PorterDuff.Mode.MULTIPLY);
                    locationButton.setTag(Theme.key_location_actionActiveIcon);
                    adapter.setCustomLocation(null);
                    userLocationMoved = false;
                    map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLng(new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude())));
                }
            }
        });

        showAllButton = new TextView(context);
        setMapButtonGlass(showAllButton);
        showAllButton.setGravity(Gravity.CENTER);
        showAllButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        ThemeBind.textColor(showAllButton, Theme.key_featuredStickers_addButton, resourceProvider);
        showAllButton.setTypeface(AndroidUtilities.bold());
        showAllButton.setPadding(dp(MAP_BUTTON_PADDING_DP + 16), 0, dp(MAP_BUTTON_PADDING_DP + 16), 0);
        showAllButton.setText(getString(R.string.LocationsShowAll));
        mapBottomButtons.addView(showAllButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, mapButtonSize, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
        showAllButton.setOnClickListener(v -> {
            selectedMarkerId = -1;
            userLocationMoved = true;
            if (fitAllLiveLocations(true)) {
                showAllMode = true;
                showShowAllButton(false, true);
            }
        });
        showShowAllButton(false, false);

        proximityButton = new ImageView(context);
        setMapButtonGlass(proximityButton);
        ThemeBind.colorFilter(proximityButton, Theme.key_location_actionIcon, getResourceProvider(), PorterDuff.Mode.MULTIPLY);
        proximityButton.setScaleType(ImageView.ScaleType.CENTER);
        proximityButton.setContentDescription(getString(R.string.AccDescrLocationNotify));
        mapTopButtons.addView(proximityButton, LayoutHelper.createFrame(mapButtonSize, mapButtonSize, Gravity.RIGHT | Gravity.TOP));
        proximityButton.setOnClickListener(v -> {
            if (getParentActivity() == null || myLocation == null || !checkGpsEnabled() || map == null) {
                return;
            }
            if (hintView != null) {
                hintView.hide();
            }
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            preferences.edit().putInt("proximityhint", 3).commit();
            LocationController.SharingLocationInfo info = getLocationController().getSharingLocationInfo(dialogId);
            if (canUndo) {
                undoView[0].hide(true, 1);
            }
            if (info != null && info.proximityMeters > 0) {
                proximityButton.setImageResource(R.drawable.msg_location_alert);
                if (proximityCircle != null) {
                    proximityCircle.remove();
                    proximityCircle = null;
                }
                canUndo = true;
                getUndoView().showWithAction(0, UndoView.ACTION_PROXIMITY_REMOVED, 0, null,
                        () -> {
                            getLocationController().setProximityLocation(dialogId, 0, true);
                            canUndo = false;
                        }, () -> {
                            proximityButton.setImageResource(R.drawable.msg_location_alert2);
                            createCircle(info.proximityMeters);
                            canUndo = false;
                        });
                return;
            }
            openProximityAlert();
        });
        TLRPC.Chat chat = null;
        if (DialogObject.isChatDialog(dialogId)) {
            chat = getMessagesController().getChat(-dialogId);
        }
        if (messageObject == null || !messageObject.isLiveLocation() || messageObject.isExpiredLiveLocation(getConnectionsManager().getCurrentTime()) || ChatObject.isChannel(chat) && !chat.megagroup) {
            proximityButton.setVisibility(View.GONE);
            proximityButton.setImageResource(R.drawable.msg_location_alert);
        } else {
            LocationController.SharingLocationInfo myInfo = getLocationController().getSharingLocationInfo(dialogId);
            if (myInfo != null && myInfo.proximityMeters > 0) {
                proximityButton.setImageResource(R.drawable.msg_location_alert2);
            } else {
                if (DialogObject.isUserDialog(dialogId) && messageObject.getFromChatId() == getUserConfig().getClientUserId()) {
                    proximityButton.setVisibility(View.INVISIBLE);
                    proximityButton.setAlpha(0.0f);
                    proximityButton.setScaleX(0.4f);
                    proximityButton.setScaleY(0.4f);
                }
                proximityButton.setImageResource(R.drawable.msg_location_alert);
            }
        }

        hintView = new HintView2(context, HintView2.DIRECTION_TOP);
        hintView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        hintView.setDuration(4000);
        // the arrow at the centre of the proximity button, the hint under its glass
        hintView.setJoint(1, -(MAP_BUTTON_PADDING_DP + MAP_BUTTON_DP / 2 - 8));
        hintView.setPadding(0, dp(4), 0, 0);
        mapTopButtons.addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 8, MAP_BUTTON_DP + MAP_BUTTON_PADDING_DP + 4, 8, 0));

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyView.setPadding(0, dp(60 + 100), 0, 0);
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setOnTouchListener((v, event) -> true);

        emptyImageView = new ImageView(context);
        emptyImageView.setImageResource(R.drawable.location_empty);
        ThemeBind.colorFilter(emptyImageView, Theme.key_dialogEmptyImage, getResourceProvider(), PorterDuff.Mode.MULTIPLY);
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTitleTextView = new TextView(context);
        ThemeBind.textColor(emptyTitleTextView, Theme.key_dialogEmptyText, getResourceProvider());
        emptyTitleTextView.setGravity(Gravity.CENTER);
        emptyTitleTextView.setTypeface(AndroidUtilities.bold());
        emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleTextView.setText(getString(R.string.NoPlacesFound));
        emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

        emptySubtitleTextView = new TextView(context);
        ThemeBind.textColor(emptySubtitleTextView, Theme.key_dialogEmptyText, getResourceProvider());
        emptySubtitleTextView.setGravity(Gravity.CENTER);
        emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptySubtitleTextView.setPadding(dp(40), 0, dp(40), 0);
        emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));

        listView = new RecyclerListView(context) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                // the list is over the map: above its sheet the touches go on to the map and its buttons
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && ev.getY() < sheetTop) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                drawSheet(this, canvas);
                super.dispatchDraw(canvas);
            }
        };
        sheetGlass = getBaseSimpleGlass().frostedFactory.create(listView)
            .setColorProvider(BlurredBackgroundProviderImpl.frostedSheet(getResourceProvider()))
            .setRadius(dp(SHEET_RADIUS_DP), dp(SHEET_RADIUS_DP), 0, 0)
            .setPadding(dp(12));
        listView.setAdapter(adapter = new LocationActivityAdapter(context, locationType, dialogId, false, getResourceProvider(), false, fromStories, locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ) {
            @Override
            protected void onDirectionClick() {
                openDirections(null);
            }

            private boolean firstSet = true;

            @Override
            public void setLiveLocations(ArrayList<LiveLocation> liveLocations) {
                if (messageObject != null && messageObject.isLiveLocation()) {
                    int otherPeopleLocations = 0;
                    if (liveLocations != null) {
                        for (int i = 0; i < liveLocations.size(); ++i) {
                            LiveLocation loc = liveLocations.get(i);
                            if (loc != null && !UserObject.isUserSelf(loc.user)) {
                                otherPeopleLocations++;
                            }
                        }
                    }
                    if (firstSet && otherPeopleLocations == 1) {
                        selectedMarkerId = liveLocations.get(0).id;
                    }
                    firstSet = false;
                    otherItem.setVisibility(otherPeopleLocations == 1 ? View.VISIBLE : View.GONE);
                }
                super.setLiveLocations(liveLocations);
            }
        });
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        if (searchStoriesArea != null) {
            sharedMediaHeader = new GraySectionCell(context, resourceProvider);
            sharedMediaLayout = new SharedMediaLayout(context, 0, new SharedMediaLayout.SharedMediaPreloader(this), 0, null, null, null, SharedMediaLayout.TAB_STORIES, 0, this, new SharedMediaLayout.Delegate() {
                @Override
                public void scrollToSharedMedia() {

                }

                @Override
                public boolean onMemberClick(TLRPC.ChatParticipant participant, boolean b, boolean resultOnly, View view) {
                    return false;
                }

                @Override
                public TLRPC.Chat getCurrentChat() {
                    return null;
                }

                @Override
                public boolean isFragmentOpened() {
                    return true;
                }

                @Override
                public RecyclerListView getListView() {
                    return listView;
                }

                @Override
                public boolean canSearchMembers() {
                    return false;
                }

                @Override
                public void updateSelectedMediaTabText() {
                    final int count = sharedMediaLayout == null ? 0 : sharedMediaLayout.getStoriesCount(SharedMediaLayout.TAB_STORIES);
                    sharedMediaHeader.setText(LocaleController.formatPluralString("LocationStories", count));
                    if (adapter.setSharedMediaLayoutVisible(count > 0)) {
                        listView.smoothScrollBy(0, dp(200));
                    }
                }
            }, SharedMediaLayout.VIEW_TYPE_MEDIA_ACTIVITY, getResourceProvider()) {
                @Override
                public TL_stories.MediaArea getStoriesArea() {
                    return searchStoriesArea;
                }

                @Override
                protected boolean customTabs() {
                    return true;
                }

                @Override
                public int mediaPageTopMargin() {
                    return 32;
                }

                @Override
                public int overrideColumnsCount() {
                    return 3;
                }
            };
            ThemeBind.backgroundColor(sharedMediaLayout, Theme.key_dialogBackground, getResourceProvider());
            sharedMediaLayout.addView(sharedMediaHeader, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.TOP | Gravity.FILL_HORIZONTAL));
            adapter.setSharedMediaLayout(sharedMediaLayout);
            listView.setOverScrollMode(View.OVER_SCROLL_NEVER);

            DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setSupportsChangeAnimations(false);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDurations(350);
            listView.setItemAnimator(itemAnimator);
        }
        adapter.setMyLocationDenied(locationDenied, false);
        adapter.setUpdateRunnable(() -> updateClipView(false));
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        if (messageObject != null && messageObject.messageOwner != null && messageObject.messageOwner.media != null && !TextUtils.isEmpty(messageObject.messageOwner.media.address)) {
            adapter.setAddressNameOverride(messageObject.messageOwner.media.address);
        }

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                if (!scrolling && forceUpdate != null) {
                    forceUpdate = null;
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateClipView(false);
                if (forceUpdate != null) {
                    yOffset += dy;
                }
            }
        });
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        listView.setOnItemLongClickListener((view, position) -> {
            if (locationType == LOCATION_TYPE_LIVE) {
                Object object = adapter.getItem(position);
                if (object instanceof LiveLocation) {

                    final LiveLocation location = (LiveLocation) object;

                    ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);
                    ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), true, true, getResourceProvider());
                    cell.setMinimumWidth(dp(200));
                    cell.setTextAndIcon(getString(R.string.GetDirections), R.drawable.filled_directions);
                    cell.setOnClickListener(e -> {
                        openDirections(location);
                        if (popupWindow != null) {
                            popupWindow.dismiss();
                        }
                    });
                    popupLayout.addView(cell);

                    popupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                        @Override
                        public void dismiss() {
                            super.dismiss();
                            popupWindow = null;
                        }
                    };
                    popupWindow.setOutsideTouchable(true);
                    popupWindow.setClippingEnabled(true);
                    popupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                    popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

                    int[] loc = new int[2];
                    view.getLocationInWindow(loc);
                    popupWindow.showAtLocation(view, Gravity.TOP, 0, loc[1] - dp(48 + 4));
                    popupWindow.dimBehind();

                    return true;
                }
            }
            return false;
        });
        listView.setOnItemClickListener((view, position) -> {
            selectedMarkerId = -1;
            if (locationType == LOCATION_TYPE_GROUP) {
                if (position == 1) {
                    TLRPC.TL_messageMediaVenue venue = (TLRPC.TL_messageMediaVenue) adapter.getItem(position);
                    if (venue == null) {
                        return;
                    }
                    if (dialogId == 0) {
                        delegate.didSelectLocation(venue, LOCATION_TYPE_GROUP, true, 0, 0);
                        finishFragment();
                    } else {
                        final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER)};
                        TLRPC.TL_channels_editLocation req = new TLRPC.TL_channels_editLocation();
                        req.address = venue.address;
                        req.channel = getMessagesController().getInputChannel(-dialogId);
                        req.geo_point = new TLRPC.TL_inputGeoPoint();
                        req.geo_point.lat = venue.geo.lat;
                        req.geo_point._long = venue.geo._long;
                        int requestId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            try {
                                progressDialog[0].dismiss();
                            } catch (Throwable ignore) {

                            }
                            progressDialog[0] = null;
                            delegate.didSelectLocation(venue, LOCATION_TYPE_GROUP, true, 0, 0);
                            finishFragment();
                        }));
                        progressDialog[0].setOnCancelListener(dialog -> getConnectionsManager().cancelRequest(requestId, true));
                        showDialog(progressDialog[0]);
                    }
                }
            } else if (locationType == LOCATION_TYPE_GROUP_VIEW) {
                if (map != null) {
                    map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(new IMapsProvider.LatLng(chatLocation.geo_point.lat, chatLocation.geo_point._long), map.getMaxZoomLevel() - 4));
                }
            } else if (position == 1 && messageObject != null && (!messageObject.isLiveLocation() || locationType == LOCATION_TYPE_LIVE_VIEW)) {
                if (map != null) {
                    map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(new IMapsProvider.LatLng(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long), map.getMaxZoomLevel() - 4));
                }
            } else if (position == 1 && locationType != 2) {
                if (delegate != null && userLocation != null) {
                    TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                    location.geo = new TLRPC.TL_geoPoint();
                    location.geo.lat = AndroidUtilities.fixLocationCoord(userLocation.getLatitude());
                    location.geo._long = AndroidUtilities.fixLocationCoord(userLocation.getLongitude());
                    if (parentFragment != null && parentFragment.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), parentFragment.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> {
                            delegate.didSelectLocation(location, locationType, notify, scheduleDate, 0);
                            finishFragment();
                        });
                    } else {
                        delegate.didSelectLocation(location, locationType, true, 0, 0);
                        finishFragment();
                    }
                }
            } else if (locationType == LOCATION_TYPE_LIVE && getLocationController().isSharingLocation(dialogId) && adapter.getItemViewType(position) == LocationActivityAdapter.VIEW_TYPE_DELETE_LIVE_LOCATION) {
                getLocationController().removeSharingLocation(dialogId);
                adapter.notifyDataSetChanged();
                finishFragment();
            } else if (locationType == LOCATION_TYPE_LIVE && getLocationController().isSharingLocation(dialogId) && adapter.getItemViewType(position) == LocationActivityAdapter.VIEW_TYPE_LIVE_LOCATION) {
                openShareLiveLocation(getLocationController().getSharingLocationInfo(dialogId).period != 0x7FFFFFFF, 0);
            } else if (position == 2 && locationType == 1 || position == 1 && locationType == 2 || position == 3 && locationType == 3) {
                if (getLocationController().isSharingLocation(dialogId)) {
                    getLocationController().removeSharingLocation(dialogId);
                    adapter.notifyDataSetChanged();
                    finishFragment();
                } else {
                    openShareLiveLocation(false, 0);
                }
            } else {
                Object object = adapter.getItem(position);
                if (object instanceof TLRPC.TL_messageMediaVenue) {
                    if (parentFragment != null && parentFragment.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), parentFragment.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> {
                            delegate.didSelectLocation((TLRPC.TL_messageMediaVenue) object, locationType, notify, scheduleDate, 0);
                            finishFragment();
                        });
                    } else {
                        delegate.didSelectLocation((TLRPC.TL_messageMediaVenue) object, locationType, true, 0, 0);
                        finishFragment();
                    }
                } else if (object instanceof LiveLocation) {
                    LiveLocation liveLocation = (LiveLocation) object;
                    selectedMarkerId = liveLocation.id;
                    if (showAllMode) {
                        showAllMode = false;
                        updateShowAllButton();
                    }
                    map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(liveLocation.marker.getPosition(), map.getMaxZoomLevel() - 4));
                }
            }
        });
        adapter.setOverScrollHeight(overScrollHeight);

        // under the list: the list's sheet is frosted glass over the map
        frameLayout.addView(mapViewClip, frameLayout.indexOfChild(listView), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        mapView = ApplicationLoader.getMapsProvider().onCreateMapView(context);
        mapView.getView().setAlpha(0f);
        mapView.setOnDispatchTouchEventInterceptor((ev, origMethod) -> {
            pokeMapSnapshot();
            MotionEvent eventToRecycle = null;
            if (yOffset != 0) {
                ev = eventToRecycle = MotionEvent.obtain(ev);
                eventToRecycle.offsetLocation(0, -yOffset / 2);
            }
            boolean result = origMethod.call(ev);
            if (eventToRecycle != null) {
                eventToRecycle.recycle();
            }
            return result;
        });
        mapView.setOnInterceptTouchEventInterceptor((ev, origMethod) -> {
            if (messageObject == null && chatLocation == null) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                    }
                    animatorSet = new AnimatorSet();
                    animatorSet.setDuration(200);
                    animatorSet.playTogether(ObjectAnimator.ofFloat(markerImageView, View.TRANSLATION_Y, markerTop - dp(10)));
                    animatorSet.start();
                } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                    if (animatorSet != null) {
                        animatorSet.cancel();
                    }
                    yOffset = 0;
                    animatorSet = new AnimatorSet();
                    animatorSet.setDuration(200);
                    animatorSet.playTogether(ObjectAnimator.ofFloat(markerImageView, View.TRANSLATION_Y, markerTop));
                    animatorSet.start();
                    adapter.fetchLocationAddress();
                }
                if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!userLocationMoved) {
                        ThemeBind.colorFilter(locationButton, Theme.key_location_actionIcon, getResourceProvider(), PorterDuff.Mode.MULTIPLY);
                        locationButton.setTag(Theme.key_location_actionIcon);
                        userLocationMoved = true;
                    }
                    if (map != null) {
                        if (userLocation != null) {
                            userLocation.setLatitude(map.getCameraPosition().target.latitude);
                            userLocation.setLongitude(map.getCameraPosition().target.longitude);
                        }
                    }
                    adapter.setCustomLocation(userLocation);
                }
            }
            return origMethod.call(ev);
        });
        mapView.setOnLayoutListener(()-> AndroidUtilities.runOnUIThread(() -> {
            if (moveToBounds != null) {
                map.moveCamera(moveToBounds);
                moveToBounds = null;
            }
        }));
        IMapsProvider.IMapView map = mapView;
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
                        ApplicationLoader.getMapsProvider().initializeMaps(ApplicationLoader.applicationContext);
                        mapView.getMapAsync(map1 -> {
                            this.map = map1;
                            int themeResId = getMapThemeResId();
                            if (themeResId != 0) {
                                currentMapStyleDark = true;
                                IMapsProvider.IMapStyleOptions style = ApplicationLoader.getMapsProvider().loadRawResourceStyle(ApplicationLoader.applicationContext, themeResId);
                                this.map.setMapStyle(style);
                            }
                            this.map.setPadding(dp(70), 0, dp(70), getMapBottomPadding());
                            onMapInit();
                            // the map's GL surface is there once the map is
                            AndroidUtilities.runOnUIThread(this::pokeMapSnapshot);
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

        if (messageObject == null && chatLocation == null) {
            if (chat != null && locationType == LOCATION_TYPE_GROUP && dialogId != 0) {
                FrameLayout frameLayout1 = new FrameLayout(context);
                frameLayout1.setBackgroundResource(R.drawable.livepin);
                mapViewClip.addView(frameLayout1, LayoutHelper.createFrame(62, 76, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

                BackupImageView backupImageView = new BackupImageView(context);
                backupImageView.setRoundRadius(dp(26));
                backupImageView.setForUserOrChat(chat, new AvatarDrawable(chat));
                frameLayout1.addView(backupImageView, LayoutHelper.createFrame(52, 52, Gravity.LEFT | Gravity.TOP, 5, 5, 0, 0));

                markerImageView = frameLayout1;
                markerImageView.setTag(1);
            }

            if (markerImageView == null) {
                ImageView imageView = new ImageView(context);
                imageView.setImageResource(R.drawable.map_pin2);
                mapViewClip.addView(imageView, LayoutHelper.createFrame(28, 48, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
                markerImageView = imageView;
            }

            searchListView = new RecyclerListView(context);
            searchListView.setVisibility(View.GONE);
            searchListView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
            searchAdapter = new LocationActivitySearchAdapter(context, getResourceProvider(), false, locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ) {
                @Override
                public void notifyDataSetChanged() {
                    if (searchItem != null) {
                        searchItem.setShowSearchProgress(searchAdapter.isSearching());
                    }
                    if (emptySubtitleTextView != null) {
                        emptySubtitleTextView.setText(LocaleController.formatString(R.string.NoResultFoundFor2, searchAdapter.getLastSearchString()));
                    }
                    super.notifyDataSetChanged();
                }
            };
            searchAdapter.setDelegate(locations -> {
                searchInProgress = false;
                updateEmptyView();
            });
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
                // a found address only moves the pin, it is sent or saved as the selected location
                if (object != null && object.icon != null && this.map != null) {
                    userLocationMoved = true;
                    simpleActionBar.closeSearchField(true);
                    final float zoom = "pin".equals(object.icon) ? this.map.getMaxZoomLevel() - 4 : this.map.getMaxZoomLevel() - 9;
                    this.map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(new IMapsProvider.LatLng(object.geo.lat, object.geo._long), zoom));
                    if (userLocation != null) {
                        userLocation.setLatitude(object.geo.lat);
                        userLocation.setLongitude(object.geo._long);
                    }
                    adapter.setCustomLocation(userLocation);
                }
            });
        } else if (messageObject != null && !messageObject.isLiveLocation() || chatLocation != null) {
            if (chatLocation != null) {
                adapter.setChatLocation(chatLocation);
            } else if (messageObject != null) {
                adapter.setMessageObject(messageObject);
            }
        }
        if (messageObject != null && locationType == LOCATION_TYPE_LIVE_VIEW) {
            adapter.setMessageObject(messageObject);
        }


        for (int a = 0; a < 2; a++) {
            undoView[a] = new UndoView(context);
            undoView[a].setAdditionalTranslationY(dp(10));
            undoView[a].setTranslationZ(dp(5));
            final FrameLayout.LayoutParams undoParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8);
            undoParams.bottomMargin += dp(SHEET_RADIUS_DP) + mapSheetTopOffset;
            mapViewClip.addView(undoView[a], undoParams);
        }


        if (messageObject == null && chatLocation == null && initialLocation != null) {
            userLocationMoved = true;
            ThemeBind.colorFilter(locationButton, Theme.key_location_actionIcon, getResourceProvider(), PorterDuff.Mode.MULTIPLY);
            locationButton.setTag(Theme.key_location_actionIcon);
        }

        setupGlass(context, frameLayout);
        updateEmptyView();

        return fragmentView;
    }

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        applyInsets();
    }

    /** The header on glass over the map; the glass captures the list and a copy of the map's surface. */
    private void setupGlass(Context context, FrameLayout frameLayout) {
        final BaseFragmentSimpleGlass glass = getBaseSimpleGlass();
        navigationBarFadeView = new View(context);
        navigationBarFadeView.setBackground(glass.createNavigationBarFadeDrawable(navigationBarFadeView));
        frameLayout.addView(navigationBarFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM));

        glass.setViews(frameLayout, simpleActionBar, getResourceProvider());
        glass.setCapture(this::captureGlass);
        // no blur under the header: the map stays clear up to the screen top
        simpleActionBar.setBackground(null);
        glassEngine.addScrolledView(listView);
        if (searchListView != null) {
            glassEngine.addScrolledView(searchListView);
        }
        if (glass.getCaptureSourceCanvas() != null && SharedConfig.chatBlurEnabled()) {
            mapSnapshot = new MapGlassSnapshot(() -> {
                glassEngine.invalidate();
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            });
        }
        applyInsets();
    }

    private void applyInsets() {
        if (fragmentView == null || listView == null) {
            return;
        }
        final int top = mSystemInsets.top, bottom = mSystemInsets.bottom;
        if (mapTopButtons != null) {
            // the glass of the first row 7dp under the header's buttons
            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mapTopButtons.getLayoutParams();
            final int topMargin = BaseFragmentSimpleGlass.getPillGlassTop(top) - dp(MAP_BUTTON_PADDING_DP);
            if (lp.topMargin != topMargin) {
                lp.topMargin = topMargin;
                mapTopButtons.requestLayout();
            }
        }
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, bottom);
        if (searchListView != null) {
            searchListView.setClipToPadding(false);
            BaseFragmentSimpleGlass.setListViewParams(searchListView, top, bottom, ActionBar.getCurrentActionBarHeight(), 0);
        }
        if (fragmentView.getMeasuredHeight() > 0) {
            fixLayoutInternal(false);
        }
    }

    /** A glass button of the map, like the header's buttons: the view is larger than the glass by its padding. */
    private BlurredBackgroundDrawable setMapButtonGlass(View view) {
        final BlurredBackgroundDrawable glass = getBaseSimpleGlass().factory.create(view)
            .setColorProvider(BlurredBackgroundProviderImpl.simpleActionBar(getResourceProvider()))
            .setRadius(dp(MAP_BUTTON_DP / 2f))
            .setPadding(dp(MAP_BUTTON_PADDING_DP));
        view.setBackground(glass);
        ScaleStateListAnimator.apply(view);
        return glass;
    }

    /**
     * The content under the glass: the list's sheet, and over it the map's part of the screen with the copy of the map's
     * surface (the glass captures views, not surfaces), the pins in views over it and the top edge of the sheet.
     */
    private void captureGlass(Canvas canvas, RectF position) {
        captureFillPaint.setColor(getThemedColor(Theme.key_dialogBackground));
        canvas.drawRect(position, captureFillPaint);
        final ViewGroup root = (ViewGroup) fragmentView;
        if (searchListView != null && searchListView.getVisibility() == View.VISIBLE) {
            Blur3Utils.captureRelativeParent(searchListView, canvas, position, searchListView, root);
            return;
        }
        if (mapViewClip.getVisibility() == View.VISIBLE) {
            captureMap(canvas, root);
        }
        if (listView.getVisibility() != View.VISIBLE) {
            return;
        }
        // the rows only under the header, over the sheet's tint: the sheet's frosted glass under them shows just the map
        final int headerBottom = mSystemInsets.top + ActionBar.getCurrentActionBarHeight() + dp(6 + 16);
        if (position.top >= headerBottom) {
            return;
        }
        canvas.save();
        canvas.clipRect(0, Math.min(position.top, headerBottom), root.getWidth(), headerBottom);
        if (sheetTop < headerBottom) {
            captureFillPaint.setColor(getThemedColor(Theme.key_dialogBackground));
            captureFillPaint.setAlpha(205);
            canvas.drawRect(0, Math.max(sheetTop, position.top), root.getWidth(), headerBottom, captureFillPaint);
        }
        Blur3Utils.captureRelativeParent(listView, canvas, position, listView, root);
        canvas.restore();
    }

    /** The map, on under the sheet too (for its frosted glass), with the pins in views over it. */
    private void captureMap(Canvas canvas, ViewGroup root) {
        canvas.save();
        canvas.translate(mapViewClip.getX(), mapViewClip.getY());
        canvas.clipRect(0, 0, mapViewClip.getWidth(), root.getHeight() - mapViewClip.getY());
        final Drawable placeholder = mapViewClip.getBackground();
        if (placeholder != null) {
            placeholder.draw(canvas);
        }
        final View map = mapView != null ? mapView.getView() : null;
        if (map != null && map.getParent() == mapViewClip && map.getVisibility() == View.VISIBLE && map.getAlpha() > 0) {
            if (mapTexture != null && mapTexture.isAttachedToWindow() && ViewPositionWatcher.computeRectInParent(mapTexture, mapViewClip, captureRect)) {
                final boolean alpha = map.getAlpha() < 1f;
                if (alpha) {
                    canvas.saveLayerAlpha(captureRect, (int) (255 * map.getAlpha()));
                } else {
                    canvas.save();
                }
                canvas.translate(captureRect.left, captureRect.top);
                mapTexture.draw(canvas);
                canvas.restore();
            } else if (mapSnapshot != null) {
                captureRect.set(map.getX(), map.getY(), map.getX() + map.getWidth(), map.getY() + map.getHeight());
                mapSnapshot.draw(canvas, captureRect, map.getAlpha());
            }
        }
        drawCapturedChild(canvas, markerImageView);
        canvas.restore();
    }

    /** The map's logo and centre stay above the sheet's top, which goes over the map; the map goes on further under it. */
    private int getMapBottomPadding() {
        return dp(10) + mapSheetTopOffset + mapExtraHeight;
    }

    private int getMapViewHeight() {
        return overScrollHeight + dp(10) + mapExtraHeight;
    }

    /** The list's sheet under its rows: frosted glass with a rounded top over the map, the handle when it can be pulled. */
    private void drawSheet(View list, Canvas canvas) {
        if (sheetGlass == null) {
            return;
        }
        final int pad = dp(12);
        sheetGlass.setBounds(-pad, sheetTop - pad, list.getWidth() + pad, list.getHeight() + pad);
        sheetGlass.draw(canvas);
        if (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE) {
            final int w = dp(36), y = sheetTop + dp(10);
            sheetHandleRect.set((list.getWidth() - w) / 2f, y, (list.getWidth() + w) / 2f, y + dp(4));
            Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_sheet_scrollUp));
            canvas.drawRoundRect(sheetHandleRect, dp(2), dp(2), Theme.dialogs_onlineCirclePaint);
        }
    }

    private static void drawCapturedChild(Canvas canvas, @Nullable View child) {
        if (child == null || child.getParent() == null || child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0) {
            return;
        }
        canvas.save();
        canvas.translate(child.getX(), child.getY());
        child.draw(canvas);
        canvas.restore();
    }

    /**
     * The map changes (a touch, a camera move): a copy of its GL surface for the glass is taken on every frame for a while.
     * A map in a texture needs no copy, the glass captures it with each new frame of the texture.
     */
    private void pokeMapSnapshot() {
        if (mapSnapshot == null || mapView == null) {
            return;
        }
        if ((mapTexture == null || !mapTexture.isAttachedToWindow()) && !mapSnapshot.hasSurface()) {
            attachMapSurface();
        }
        if (mapTexture == null) {
            mapSnapshot.poke();
        } else {
            listenMapTexture();
        }
    }

    /** The map's own listener stays behind this one; the map may set it again later, so it is checked with each change of the map. */
    private void listenMapTexture() {
        final TextureView.SurfaceTextureListener listener = mapTexture.getSurfaceTextureListener();
        if (!(listener instanceof MapTextureListener)) {
            mapTexture.setSurfaceTextureListener(new MapTextureListener(listener));
        }
    }

    /** A new frame of the map's texture is a change under the glass. */
    private class MapTextureListener implements TextureView.SurfaceTextureListener {
        @Nullable
        private final TextureView.SurfaceTextureListener listener;

        MapTextureListener(@Nullable TextureView.SurfaceTextureListener listener) {
            this.listener = listener;
        }

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            if (listener != null) {
                listener.onSurfaceTextureAvailable(surface, width, height);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            if (listener != null) {
                listener.onSurfaceTextureSizeChanged(surface, width, height);
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return listener == null || listener.onSurfaceTextureDestroyed(surface);
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            if (listener != null) {
                listener.onSurfaceTextureUpdated(surface);
            }
            glassEngine.invalidate();
        }
    }

    /** The view the map draws into, once the map has made it: a texture (the current renderer) or a GL surface. */
    private void attachMapSurface() {
        final View map = mapView.getView();
        mapTexture = findChild(map, TextureView.class);
        mapSnapshot.setSurface(mapTexture == null ? findChild(map, SurfaceView.class) : null);
        if (mapTexture != null) {
            listenMapTexture();
        }
        updateMapSnapshotRunning();
    }

    private void updateMapSnapshotRunning() {
        if (mapSnapshot == null || mapView == null) {
            return;
        }
        final View map = mapView.getView();
        mapSnapshot.setRunning(mapTexture == null && onResumeCalled && !isFinishing() && mapViewClip != null && mapViewClip.getVisibility() == View.VISIBLE
            && map.getParent() != null && map.getVisibility() == View.VISIBLE);
    }

    @Nullable
    private static <T extends View> T findChild(View view, Class<T> type) {
        if (type.isInstance(view)) {
            return type.cast(view);
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                final T child = findChild(group.getChildAt(i), type);
                if (child != null) {
                    return child;
                }
            }
        }
        return null;
    }

    private void updateShowAllButton() {
        if (showAllButton == null) {
            return;
        }
        if (showAllMode) {
            showShowAllButton(false, true);
            fitAllLiveLocations(true);
            return;
        }
        int count = 0;
        int date = getConnectionsManager() != null ? getConnectionsManager().getCurrentTime() : 0;
        for (int a = 0, N = markers.size(); a < N; a++) {
            LiveLocation loc = markers.get(a);
            if (loc.object == null || loc.object.media == null) {
                continue;
            }
            if (loc.object.media.period == 0x7FFFFFFF || loc.object.date + loc.object.media.period > date) {
                count++;
            }
        }
        boolean myInMarkers = markersMap.get(getUserConfig().getClientUserId()) != null;
        if (myLocation != null && !myInMarkers) {
            count++;
        }
        showShowAllButton(count >= 2, true);
    }

    private boolean fitAllLiveLocations(boolean animated) {
        if (map == null) {
            return false;
        }
        ArrayList<IMapsProvider.LatLng> points = new ArrayList<>();
        int date = getConnectionsManager() != null ? getConnectionsManager().getCurrentTime() : 0;
        for (int a = 0, N = markers.size(); a < N; a++) {
            LiveLocation loc = markers.get(a);
            if (loc.marker == null || loc.object == null || loc.object.media == null) {
                continue;
            }
            if (loc.object.media.period == 0x7FFFFFFF || loc.object.date + loc.object.media.period > date) {
                points.add(loc.marker.getPosition());
            }
        }
        boolean myInMarkers = markersMap.get(getUserConfig().getClientUserId()) != null;
        if (myLocation != null && !myInMarkers) {
            points.add(new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
        }
        if (points.size() < 2) {
            return false;
        }
        try {
            IMapsProvider.ILatLngBoundsBuilder builder = ApplicationLoader.getMapsProvider().onCreateLatLngBoundsBuilder();
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            for (int i = 0, n = points.size(); i < n; i++) {
                IMapsProvider.LatLng p = points.get(i);
                builder.include(p);
                if (p.latitude < minLat) minLat = p.latitude;
                if (p.latitude > maxLat) maxLat = p.latitude;
                if (p.longitude < minLon) minLon = p.longitude;
                if (p.longitude > maxLon) maxLon = p.longitude;
            }
            IMapsProvider.LatLng center = new IMapsProvider.LatLng((minLat + maxLat) / 2, (minLon + maxLon) / 2);
            double latSpanMeters = Math.toRadians(maxLat - minLat) * EARTHRADIUS;
            double lonSpanMeters = Math.toRadians(maxLon - minLon) * EARTHRADIUS * Math.cos(Math.toRadians(center.latitude));
            double minSpan = 30;
            if (latSpanMeters < minSpan || lonSpanMeters < minSpan) {
                builder.include(move(center, minSpan / 2, minSpan / 2));
                builder.include(move(center, -minSpan / 2, -minSpan / 2));
            }
            IMapsProvider.ILatLngBounds bounds = builder.build();
            IMapsProvider.ICameraUpdate update = ApplicationLoader.getMapsProvider().newCameraUpdateLatLngBounds(bounds, dp(60));
            if (animated) {
                map.animateCamera(update, 500, null);
            } else {
                map.moveCamera(update);
            }
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private Boolean shownShowAllButton;
    private void showShowAllButton(boolean show, boolean animated) {
        if (shownShowAllButton != null && shownShowAllButton == show) return;
        shownShowAllButton = show;
        if (!animated) {
            showAllButton.setVisibility(show ? View.VISIBLE : View.GONE);
            showAllButton.setAlpha(show ? 1.0f : 0.0f);
            showAllButton.setScaleX(show ? 1.0f : 0.7f);
            showAllButton.setScaleY(show ? 1.0f : 0.7f);
        } else {
            showAllButton.setVisibility(View.VISIBLE);
            showAllButton.animate()
                .alpha(show ? 1.0f : 0.0f)
                .scaleX(show ? 1.0f : 0.7f)
                .scaleY(show ? 1.0f : 0.7f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .withEndAction(() -> {
                    if (!show) {
                        showAllButton.setVisibility(View.GONE);
                    }
                })
                .start();
        }
    }

    private boolean isActiveThemeDark() {
        if (getResourceProvider() == null) {
            Theme.ThemeInfo info = Theme.getActiveTheme();
            if (info.isDark()) {
                return true;
            }
        }
        int color = getThemedColor(Theme.key_windowBackgroundWhite);
        return AndroidUtilities.computePerceivedBrightness(color) < 0.721f;
    }

    private int getMapThemeResId() {
        int color = getThemedColor(Theme.key_windowBackgroundWhite);
        if (AndroidUtilities.computePerceivedBrightness(color) < 0.721f) {
//            if (Color.blue(color) - 3 > Color.red(color) && Color.blue(color) - 3 > Color.green(color)) {
//                return R.raw.mapstyle_night;
//            } else {
//                return R.raw.mapstyle_dark;
//            }
            return R.raw.mapstyle_night;
        }
        return 0;
    }

    private void openDirections(LiveLocation location) {
        double daddrLat, daddrLong;
        if (location != null && location.object != null) {
            daddrLat = location.object.media.geo.lat;
            daddrLong = location.object.media.geo._long;
        } else if (messageObject != null) {
            daddrLat = messageObject.messageOwner.media.geo.lat;
            daddrLong = messageObject.messageOwner.media.geo._long;
        } else {
            daddrLat = chatLocation.geo_point.lat;
            daddrLong = chatLocation.geo_point._long;
        }
        String domain;
        if (BuildVars.isHuaweiStoreApp()) {
            domain = "mapapp://navigation";
        } else {
            domain = "http://maps.google.com/maps";
        }
        if (myLocation != null) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, domain + "?saddr=%f,%f&daddr=%f,%f", myLocation.getLatitude(), myLocation.getLongitude(), daddrLat, daddrLong)));
                getParentActivity().startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Locale.US, domain + "?saddr=&daddr=%f,%f", daddrLat, daddrLong)));
                getParentActivity().startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void updateEmptyView() {
        if (searching) {
            if (searchInProgress) {
                searchListView.setEmptyView(null);
                emptyView.setVisibility(View.GONE);
                searchListView.setVisibility(View.GONE);
            } else {
                searchListView.setEmptyView(emptyView);
            }
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    private Bitmap createUserBitmap(LiveLocation liveLocation) {
        Bitmap result = null;
        try {
            result = Bitmap.createBitmap(dp(62), dp(85), Bitmap.Config.ARGB_8888);
            result.eraseColor(Color.TRANSPARENT);
            Canvas canvas = new Canvas(result);
            Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.map_pin_photo);
            drawable.setBounds(0, 0, dp(62), dp(85));
            drawable.draw(canvas);

            Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            RectF bitmapRect = new RectF();
            canvas.save();

            canvas.save();
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            if (liveLocation.user != null) {
                avatarDrawable.setInfo(currentAccount, liveLocation.user);
            } else if (liveLocation.chat != null) {
                avatarDrawable.setInfo(currentAccount, liveLocation.chat);
            }
            canvas.translate(dp(6), dp(6));
            avatarDrawable.setBounds(0, 0, dp(50), dp(50));
            avatarDrawable.draw(canvas);
            canvas.restore();

            Bitmap bitmap = liveLocation.avatarReceiver != null && liveLocation.avatarReceiver.hasImageLoaded() ? liveLocation.avatarReceiver.getBitmap() : null;
            if (bitmap != null && !bitmap.isRecycled()) {
                BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                Matrix matrix = new Matrix();
                float scale = dp(50) / (float) bitmap.getWidth();
                matrix.postTranslate(dp(6), dp(6));
                matrix.postScale(scale, scale);
                roundPaint.setShader(shader);
                shader.setLocalMatrix(matrix);
                bitmapRect.set(dp(6), dp(6), dp(50 + 6), dp(50 + 6));
                canvas.drawRoundRect(bitmapRect, dp(25), dp(25), roundPaint);
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

    private long getMessageId(TLRPC.Message message) {
        if (message.from_id != null) {
            return MessageObject.getFromChatId(message);
        } else {
            return MessageObject.getDialogId(message);
        }
    }

    private void openProximityAlert() {
        if (proximityCircle == null) {
            createCircle(500);
        } else {
            previousRadius = proximityCircle.getRadius();
        }

        TLRPC.User user;
        if (DialogObject.isUserDialog(dialogId)) {
            user = getMessagesController().getUser(dialogId);
        } else {
            user = null;
        }
        proximitySheet = new ProximitySheet(getParentActivity(), user, (move, radius) -> {
            if (proximityCircle != null) {
                proximityCircle.setRadius(radius);
                if (move) {
                    moveToBounds(radius, true, true);
                }
            }
            if (DialogObject.isChatDialog(dialogId)) {
                return true;
            }
            for (int a = 0, N = markers.size(); a < N; a++) {
                LiveLocation location = markers.get(a);
                if (location.object == null || UserObject.isUserSelf(location.user)) {
                    continue;
                }
                TLRPC.GeoPoint point = location.object.media.geo;
                Location loc = new Location("network");
                loc.setLatitude(point.lat);
                loc.setLongitude(point._long);
                if (myLocation.distanceTo(loc) > radius) {
                    return true;
                }
            }
            return false;
        }, (move, radius) -> {
            LocationController.SharingLocationInfo info = getLocationController().getSharingLocationInfo(dialogId);
            if (info == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString(R.string.ShareLocationAlertTitle));
                builder.setMessage(getString(R.string.ShareLocationAlertText));
                builder.setPositiveButton(getString(R.string.ShareLocationAlertButton), (dialog, id) -> shareLiveLocation(user, 900, radius));
                builder.setNegativeButton(getString(R.string.Cancel), null);
                showDialog(builder.create());
                return false;
            }
            proximitySheet.setRadiusSet();
            proximityButton.setImageResource(R.drawable.msg_location_alert2);
            getUndoView().showWithAction(0, UndoView.ACTION_PROXIMITY_SET, radius, user, null, null);
            getLocationController().setProximityLocation(dialogId, radius, true);
            return true;
        }, () -> {
            if (map != null) {
                map.setPadding(dp(70), 0, dp(70), getMapBottomPadding());
            }
            if (!proximitySheet.getRadiusSet()) {
                if (previousRadius > 0) {
                    proximityCircle.setRadius(previousRadius);
                } else if (proximityCircle != null) {
                    proximityCircle.remove();
                    proximityCircle = null;
                }
            }
            proximitySheet = null;
        });
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.addView(proximitySheet, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        proximitySheet.show();
    }

    private void openShareLiveLocation(boolean expand, int proximityRadius) {
        if (delegate == null || disablePermissionCheck() || getParentActivity() == null || myLocation == null || !checkGpsEnabled()) {
            return;
        }
        TLRPC.User user;
        if (DialogObject.isUserDialog(dialogId)) {
            user = getMessagesController().getUser(dialogId);
        } else {
            user = null;
        }
        showDialog(AlertsCreator.createLocationUpdateDialog(getParentActivity(), expand, user, param -> {
            if (expand) {
                LocationController.SharingLocationInfo info = getLocationController().getSharingLocationInfo(dialogId);
                if (info != null) {
                    TLRPC.TL_messages_editMessage req = new TLRPC.TL_messages_editMessage();
                    req.peer = getMessagesController().getInputPeer(info.did);
                    req.id = info.mid;
                    req.flags |= 16384;
                    req.media = new TLRPC.TL_inputMediaGeoLive();
                    req.media.stopped = false;
                    req.media.geo_point = new TLRPC.TL_inputGeoPoint();
                    Location lastKnownLocation = LocationController.getInstance(currentAccount).getLastKnownLocation();
                    req.media.geo_point.lat = AndroidUtilities.fixLocationCoord(lastKnownLocation.getLatitude());
                    req.media.geo_point._long = AndroidUtilities.fixLocationCoord(lastKnownLocation.getLongitude());
                    req.media.geo_point.accuracy_radius = (int) lastKnownLocation.getAccuracy();
                    if (req.media.geo_point.accuracy_radius != 0) {
                        req.media.geo_point.flags |= 1;
                    }
                    if (info.lastSentProximityMeters != info.proximityMeters) {
                        req.media.proximity_notification_radius = info.proximityMeters;
                        req.media.flags |= 8;
                    }
                    req.media.heading = LocationController.getHeading(lastKnownLocation);
                    req.media.flags |= 4;
                    req.media.period = info.period = param == 0x7FFFFFFF ? 0x7FFFFFFF : info.period + param;
                    info.stopTime = param == 0x7FFFFFFF ? Integer.MAX_VALUE : info.stopTime + param;
                    req.media.flags |= 2;
                    if (info.messageObject != null && info.messageObject.messageOwner != null && info.messageObject.messageOwner.media != null) {
                        info.messageObject.messageOwner.media.period = info.period;
//                        ArrayList<TLRPC.Message> messages = new ArrayList<>();
//                        messages.add(info.messageObject.messageOwner);
                        getMessagesStorage().replaceMessageIfExists(info.messageObject.messageOwner, null, null, true);
                    }
                    getConnectionsManager().sendRequest(req, null);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.liveLocationsChanged);
                }
                return;
            }
            shareLiveLocation(user, param, proximityRadius);
        }, null));
    }

    private void shareLiveLocation(TLRPC.User user, int period, int radius) {
        TLRPC.TL_messageMediaGeoLive location = new TLRPC.TL_messageMediaGeoLive();
        location.geo = new TLRPC.TL_geoPoint();
        location.geo.lat = AndroidUtilities.fixLocationCoord(myLocation.getLatitude());
        location.geo._long = AndroidUtilities.fixLocationCoord(myLocation.getLongitude());
        location.heading = LocationController.getHeading(myLocation);
        location.flags |= 1;
        location.period = period;
        location.proximity_notification_radius = radius;
        location.flags |= 8;
        delegate.didSelectLocation(location, locationType, true, 0, 0);
        if (radius > 0) {
            proximitySheet.setRadiusSet();
            proximityButton.setImageResource(R.drawable.msg_location_alert2);
            if (proximitySheet != null) {
                proximitySheet.dismiss();
            }
            getUndoView().showWithAction(0, UndoView.ACTION_PROXIMITY_SET, radius, user, null, null);
        } else {
            finishFragment();
        }
    }

    private void setupAvatarReceiver(LiveLocation liveLocation) {
        if (liveLocation.avatarReceiver != null) return;
        final TLRPC.User user = liveLocation.user;
        final TLRPC.Chat chat = liveLocation.chat;
        if (user == null && chat == null) return;

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (user != null) {
            avatarDrawable.setInfo(currentAccount, user);
        } else {
            avatarDrawable.setInfo(currentAccount, chat);
        }

        final ImageReceiver receiver = new ImageReceiver();
        receiver.setCurrentAccount(currentAccount);
        receiver.setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (!set || thumb) return;
            if (liveLocation.marker == null) return;
            Bitmap bitmap = createUserBitmap(liveLocation);
            if (bitmap != null) {
                liveLocation.marker.setIcon(bitmap);
            }
        });
        receiver.onAttachedToWindow();
        receiver.setForUserOrChat(user != null ? user : chat, avatarDrawable);
        liveLocation.avatarReceiver = receiver;
    }

    private LiveLocation addUserMarker(TLRPC.Message message) {
        LiveLocation liveLocation;
        IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(message.media.geo.lat, message.media.geo._long);
        if ((liveLocation = markersMap.get(MessageObject.getFromChatId(message))) == null) {
            liveLocation = new LiveLocation();
            liveLocation.object = message;
            if (liveLocation.object.from_id instanceof TLRPC.TL_peerUser) {
                liveLocation.user = getMessagesController().getUser(liveLocation.object.from_id.user_id);
                liveLocation.id = liveLocation.object.from_id.user_id;
            } else {
                long did = MessageObject.getDialogId(message);
                if (DialogObject.isUserDialog(did)) {
                    liveLocation.user = getMessagesController().getUser(did);
                } else {
                    liveLocation.chat = getMessagesController().getChat(-did);
                }
                liveLocation.id = did;
            }
            setupAvatarReceiver(liveLocation);

            try {
                IMapsProvider.IMarkerOptions options = ApplicationLoader.getMapsProvider().onCreateMarkerOptions().position(latLng);
                Bitmap bitmap = createUserBitmap(liveLocation);
                if (bitmap != null) {
                    options.icon(bitmap);
                    options.anchor(0.5f, 0.907f);
                    liveLocation.marker = map.addMarker(options);

                    if (!UserObject.isUserSelf(liveLocation.user)) {
                        IMapsProvider.IMarkerOptions dirOptions = ApplicationLoader.getMapsProvider().onCreateMarkerOptions().position(latLng).flat(true);
                        dirOptions.anchor(0.5f, 0.5f);
                        liveLocation.directionMarker = map.addMarker(dirOptions);

                        if (message.media.heading != 0) {
                            liveLocation.directionMarker.setRotation(message.media.heading);
                            liveLocation.directionMarker.setIcon(R.drawable.map_pin_cone2);
                            liveLocation.hasRotation = true;
                        } else {
                            liveLocation.directionMarker.setRotation(0);
                            liveLocation.directionMarker.setIcon(R.drawable.map_pin_circle);
                            liveLocation.hasRotation = false;
                        }
                    }

                    markers.add(liveLocation);
                    markersMap.put(liveLocation.id, liveLocation);
                    LocationController.SharingLocationInfo myInfo = getLocationController().getSharingLocationInfo(dialogId);
                    if (liveLocation.id == getUserConfig().getClientUserId() && myInfo != null && liveLocation.object.id == myInfo.mid && myLocation != null) {
                        IMapsProvider.LatLng latLng1 = new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude());
                        liveLocation.marker.setPosition(latLng1);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            liveLocation.object = message;
            liveLocation.marker.setPosition(latLng);
            if (selectedMarkerId == liveLocation.id) {
                map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLng(liveLocation.marker.getPosition()));
            }
        }
        if (proximitySheet != null) {
            proximitySheet.updateText(true, true);
        }
        updateShowAllButton();
        return liveLocation;
    }

    private LiveLocation addUserMarker(TLRPC.TL_channelLocation location) {
        IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(location.geo_point.lat, location.geo_point._long);
        LiveLocation liveLocation = new LiveLocation();
        if (DialogObject.isUserDialog(dialogId)) {
            liveLocation.user = getMessagesController().getUser(dialogId);
        } else {
            liveLocation.chat = getMessagesController().getChat(-dialogId);
        }
        liveLocation.id = dialogId;
        setupAvatarReceiver(liveLocation);

        try {
            IMapsProvider.IMarkerOptions options = ApplicationLoader.getMapsProvider().onCreateMarkerOptions().position(latLng);
            Bitmap bitmap = createUserBitmap(liveLocation);
            if (bitmap != null) {
                options.icon(bitmap);
                options.anchor(0.5f, 0.907f);
                liveLocation.marker = map.addMarker(options);

                if (!UserObject.isUserSelf(liveLocation.user)) {
                    IMapsProvider.IMarkerOptions dirOptions = ApplicationLoader.getMapsProvider().onCreateMarkerOptions().position(latLng).flat(true);
                    dirOptions.icon(R.drawable.map_pin_circle);
                    dirOptions.anchor(0.5f, 0.5f);
                    liveLocation.directionMarker = map.addMarker(dirOptions);
                }

                markers.add(liveLocation);
                markersMap.put(liveLocation.id, liveLocation);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        return liveLocation;
    }

    private void onMapInit() {
        if (map == null) {
            return;
        }

        mapView.getView().animate().alpha(1).setStartDelay(200).setDuration(100).setUpdateListener(a -> glassEngine.invalidate()).start();

        final float zoom = initialMaxZoom ? map.getMinZoomLevel() + 4 : map.getMaxZoomLevel() - 4;
        if (chatLocation != null) {
            LiveLocation liveLocation = addUserMarker(chatLocation);
            map.moveCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(liveLocation.marker.getPosition(), zoom));
        } else if (messageObject != null) {
            if (messageObject.isLiveLocation()) {
                LiveLocation liveLocation = addUserMarker(messageObject.messageOwner);
                if (!getRecentLocations()) {
                    map.moveCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(liveLocation.marker.getPosition(), zoom));
                }
            } else {
                IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(userLocation.getLatitude(), userLocation.getLongitude());
                try {
                    map.addMarker(ApplicationLoader.getMapsProvider().onCreateMarkerOptions().position(latLng).icon(R.drawable.map_pin2));
                } catch (Exception e) {
                    FileLog.e(e);
                }
                IMapsProvider.ICameraUpdate position = ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(latLng, zoom);
                map.moveCamera(position);
                firstFocus = false;
                getRecentLocations();
            }
        } else {
            userLocation = new Location("network");
            if (initialLocation != null) {
                IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(initialLocation.geo_point.lat, initialLocation.geo_point._long);
                map.moveCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(latLng, zoom));
                userLocation.setLatitude(initialLocation.geo_point.lat);
                userLocation.setLongitude(initialLocation.geo_point._long);
                userLocation.setAccuracy(initialLocation.geo_point.accuracy_radius);
                adapter.setCustomLocation(userLocation);
            } else {
                userLocation.setLatitude(20.659322);
                userLocation.setLongitude(-11.406250);
            }
        }

        try {
            map.setMyLocationEnabled(true);
        } catch (Exception e) {
            FileLog.e(e, false);
        }
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.setOnCameraMoveStartedListener(reason -> {
            if (reason == IMapsProvider.OnCameraMoveStartedListener.REASON_GESTURE) {
                selectedMarkerId = -1;
                if (showAllMode) {
                    showAllMode = false;
                    updateShowAllButton();
                }

                if (!scrolling && (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE) && listView.getChildCount() > 0) {
                    View view = listView.getChildAt(0);
                    if (view != null) {
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(view);
                        if (holder != null && holder.getAdapterPosition() == 0) {
                            int min = locationType == LOCATION_TYPE_SEND ? 0 : dp(66);
                            int top = view.getTop();
                            if (top < -min) {
                                IMapsProvider.CameraPosition cameraPosition = map.getCameraPosition();
                                forceUpdate = ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(cameraPosition.target, cameraPosition.zoom);
                                listView.smoothScrollBy(0, top + min);
                            }
                        }
                    }
                }
            }
        });
        map.setOnMyLocationChangeListener(location -> {
            positionMarker(location);
            getLocationController().setMapLocation(location, isFirstLocation);
            isFirstLocation = false;
        });
        map.setOnMarkerClickListener(marker -> true);
        map.setOnCameraMoveListener(() -> {
            pokeMapSnapshot();
        });
        positionMarker(myLocation = getLastLocation());

        if (checkGpsEnabled && getParentActivity() != null) {
            checkGpsEnabled = false;
            checkGpsEnabled();
        }

        if (proximityButton != null && proximityButton.getVisibility() == View.VISIBLE) {
            LocationController.SharingLocationInfo myInfo = getLocationController().getSharingLocationInfo(dialogId);
            if (myInfo != null && myInfo.proximityMeters > 0) {
                createCircle(myInfo.proximityMeters);
            }
        }
    }

    private boolean checkGpsEnabled() {
        if (disablePermissionCheck()) {
            return false;
        }
        if (!getParentActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
            return true;
        }
        try {
            LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTopAnimation(R.raw.permission_request_location, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, getThemedColor(Theme.key_dialogTopBackground));
                builder.setTopBackgroundColorKey(Theme.key_dialogTopBackground, getResourceProvider());
                builder.setMessage(getString(R.string.GpsDisabledAlertText));
                builder.setPositiveButton(getString(R.string.ConnectingToProxyEnable), (dialog, id) -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    try {
                        getParentActivity().startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    } catch (Exception ignore) {

                    }
                });
                builder.setNegativeButton(getString(R.string.Cancel), null);
                showDialog(builder.create());
                return false;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    private void createCircle(int meters) {
        if (map == null) {
            return;
        }
        List<IMapsProvider.PatternItem> PATTERN_POLYGON_ALPHA = Arrays.asList(new IMapsProvider.PatternItem.Gap(20), new IMapsProvider.PatternItem.Dash(20));

        IMapsProvider.ICircleOptions circleOptions = ApplicationLoader.getMapsProvider().onCreateCircleOptions();
        circleOptions.center(new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
        circleOptions.radius(meters);
        if (isActiveThemeDark()) {
            circleOptions.strokeColor(0x9666A3D7);
            circleOptions.fillColor(0x1c66A3D7);
        } else {
            circleOptions.strokeColor(0x964286F5);
            circleOptions.fillColor(0x1c4286F5);
        }
        circleOptions.strokePattern(PATTERN_POLYGON_ALPHA);
        circleOptions.strokeWidth(2);
        proximityCircle = map.addCircle(circleOptions);
    }

    private void showPermissionAlert(boolean byButton) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTopAnimation(R.raw.permission_request_location, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, getThemedColor(Theme.key_dialogTopBackground));
        builder.setTopBackgroundColorKey(Theme.key_dialogTopBackground, getResourceProvider());
        if (byButton) {
            builder.setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionNoLocationNavigation)));
        } else {
            builder.setMessage(AndroidUtilities.replaceTags(getString(R.string.PermissionNoLocationFriends)));
        }
        builder.setNegativeButton(getString(R.string.PermissionOpenSettings), (dialog, which) -> {
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
        builder.setPositiveButton(getString(R.string.OK), null);
        showDialog(builder.create());
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward) {
            try {
                if (mapView.getView().getParent() instanceof ViewGroup) {
                    ViewGroup viewGroup = (ViewGroup) mapView.getView().getParent();
                    viewGroup.removeView(mapView.getView());
                }
            } catch (Exception ignore) {

            }
            if (mapViewClip != null) {
                mapViewClip.addView(mapView.getView(), 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, getMapViewHeight(), Gravity.TOP | Gravity.LEFT));
                updateClipView(false);
                maybeShowProximityHint();
                pokeMapSnapshot();
            } else if (fragmentView != null) {
                ((FrameLayout) fragmentView).addView(mapView.getView(), 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            }
        }
    }

    private void maybeShowProximityHint() {
        if (proximityButton == null || proximityButton.getVisibility() != View.VISIBLE || proximityAnimationInProgress) {
            return;
        }
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        int val = preferences.getInt("proximityhint", 0);
        if (val < 3) {
            preferences.edit().putInt("proximityhint", ++val).commit();
            if (DialogObject.isUserDialog(dialogId)) {
                TLRPC.User user = getMessagesController().getUser(dialogId);
                hintView.setText(LocaleController.formatString("ProximityTooltioUser", R.string.ProximityTooltioUser, UserObject.getFirstName(user)));
            } else {
                hintView.setText(getString(R.string.ProximityTooltioGroup));
            }
            hintView.show();
        }
    }

    private void updateClipView(boolean fromLayout) {
        int height = 0;
        int top;
        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
        if (holder != null) {
            top = (int) holder.itemView.getY();
            height = overScrollHeight + (Math.min(top, 0));
            sheetTop = top + overScrollHeight - mapSheetTopOffset;
        } else {
            top = -mapViewClip.getMeasuredHeight();
            sheetTop = -dp(SHEET_RADIUS_DP + 12);
        }
        listView.invalidate();
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
        if (layoutParams != null) {
            if (height <= 0) {
                if (mapView.getView().getVisibility() == View.VISIBLE) {
                    mapView.getView().setVisibility(View.INVISIBLE);
                    mapViewClip.setVisibility(View.INVISIBLE);
                }
            } else {
                if (mapView.getView().getVisibility() == View.INVISIBLE) {
                    mapView.getView().setVisibility(View.VISIBLE);
                    mapViewClip.setVisibility(View.VISIBLE);
                }
            }

            mapViewClip.setTranslationY(Math.min(0, top));
            mapView.getView().setTranslationY(Math.max(0, -top / 2));
            updateMapButtons(top, height);
            if (markerImageView != null) {
                // the camera's centre is above the map's bottom padding (under the sheet's top and further down)
                markerImageView.setTranslationY(markerTop = -top - dp(markerImageView.getTag() == null ? 48 : 69) + height / 2 - mapSheetTopOffset / 2);
            }
            if (!fromLayout) {
                layoutParams = (FrameLayout.LayoutParams) mapView.getView().getLayoutParams();
                if (layoutParams != null && layoutParams.height != getMapViewHeight()) {
                    layoutParams.height = getMapViewHeight();
                    if (map != null) {
                        map.setPadding(dp(70), 0, dp(70), getMapBottomPadding());
                    }
                    mapView.getView().setLayoutParams(layoutParams);
                }
            }
        }
    }

    /**
     * The glass buttons under the header stay there while the map is under them (the map rides up with the list),
     * all the buttons fade out when the list's sheet comes up close to them.
     */
    private void updateMapButtons(int top, int mapHeight) {
        if (mapTopButtons == null) {
            return;
        }
        mapTopButtons.setTranslationY(-Math.min(0, top));
        final int topGlassTop = BaseFragmentSimpleGlass.getPillGlassTop(mSystemInsets.top);
        final int topGlassBottom = topGlassTop + dp(MAP_BUTTON_DP);
        final int bottomGlassTop = mapHeight - mapSheetTopOffset - dp(BaseFragmentSimpleGlass.PILLS_GAP_DP + MAP_BUTTON_DP);
        final float space = bottomGlassTop - topGlassBottom - dp(BaseFragmentSimpleGlass.PILLS_GAP_DP);
        final float alpha = Utilities.clamp01(space / dp(48));
        setMapButtonsAlpha(mapTopButtons, alpha);
        setMapButtonsAlpha(mapBottomButtons, alpha);
        updateMapSnapshotRunning();
    }

    private static void setMapButtonsAlpha(View buttons, float alpha) {
        buttons.setAlpha(alpha);
        buttons.setVisibility(alpha > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    private void fixLayoutInternal(final boolean resume) {
        if (listView != null) {
            // the map goes up under the glass header to the top of the screen, the list's sheet ends over the navigation bar
            final int height = mSystemInsets.bottom;
            int viewHeight = fragmentView.getMeasuredHeight();
            if (viewHeight == 0) {
                return;
            }
            if (locationType == LOCATION_TYPE_LIVE_VIEW) {
                overScrollHeight = viewHeight - dp(66) - height;
            } else if (locationType == 2) {
                overScrollHeight = viewHeight - dp(66 + 7) - height;
            } else {
                overScrollHeight = viewHeight - dp(66) - height;
            }
            if (sharedMediaLayout != null && sharedMediaLayout.getStoriesCount(SharedMediaLayout.TAB_STORIES) > 0) {
                overScrollHeight -= dp(200);
            }
            // the map goes on under the sheet down to the bottom (also while the list is pulled up a bit, the map moving
            // half as fast): its frosted glass shows it
            mapExtraHeight = viewHeight - overScrollHeight + dp(100);

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
            // the map is seen on under the sheet's rounded corners: the clip goes past the map's space in the list
            layoutParams.height = overScrollHeight + dp(SHEET_RADIUS_DP);
            mapViewClip.setLayoutParams(layoutParams);

            adapter.setOverScrollHeight(overScrollHeight);
            layoutParams = (FrameLayout.LayoutParams) mapView.getView().getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = getMapViewHeight();
                if (map != null) {
                    map.setPadding(dp(70), 0, dp(70), getMapBottomPadding());
                }
                mapView.getView().setLayoutParams(layoutParams);
            }
            adapter.notifyDataSetChanged();

            if (resume) {
                int top;
                if (locationType == 3) {
                    top = 73;
                } else if (locationType == 1 || locationType == 2) {
                    top = 66;
                } else {
                    top = 0;
                }
                layoutManager.scrollToPositionWithOffset(0, -dp(top));
                updateClipView(false);
                listView.post(() -> {
                    layoutManager.scrollToPositionWithOffset(0, -dp(top));
                    updateClipView(false);
                });
            } else {
                updateClipView(false);
            }
        }
    }

    @SuppressLint("MissingPermission")
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
            IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(location.getLatitude(), location.getLongitude());
            liveLocation.marker.setPosition(latLng);
            if (liveLocation.directionMarker != null) {
                liveLocation.directionMarker.setPosition(latLng);
            }
            if (selectedMarkerId == liveLocation.id) {
                map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLng(liveLocation.marker.getPosition()));
            }
        }
        if (messageObject == null && chatLocation == null && map != null) {
            IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(location.getLatitude(), location.getLongitude());
            if (adapter != null) {
                adapter.setGpsLocation(myLocation);
            }
            if (!userLocationMoved) {
                userLocation = new Location(location);
                if (firstWas) {
                    IMapsProvider.ICameraUpdate position = ApplicationLoader.getMapsProvider().newCameraUpdateLatLng(latLng);
                    map.animateCamera(position);
                } else {
                    firstWas = true;
                    IMapsProvider.ICameraUpdate position = ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(latLng, map.getMaxZoomLevel() - 4);
                    map.moveCamera(position);
                }
            }
        } else {
            adapter.setGpsLocation(myLocation);
        }
        if (proximitySheet != null) {
            proximitySheet.updateText(true, true);
        }
        if (proximityCircle != null) {
            proximityCircle.setCenter(new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
        }
        updateShowAllButton();
    }

    public void setMessageObject(MessageObject message) {
        messageObject = message;
        dialogId = messageObject.getDialogId();
    }

    public void setChatLocation(long chatId, TLRPC.TL_channelLocation location) {
        dialogId = -chatId;
        chatLocation = location;
    }

    public void setDialogId(long did) {
        dialogId = did;
    }

    public void setInitialLocation(TLRPC.TL_channelLocation location) {
        initialLocation = location;
    }

    private static final double EARTHRADIUS = 6366198;

    private static IMapsProvider.LatLng move(IMapsProvider.LatLng startLL, double toNorth, double toEast) {
        double lonDiff = meterToLongitude(toEast, startLL.latitude);
        double latDiff = meterToLatitude(toNorth);
        return new IMapsProvider.LatLng(startLL.latitude + latDiff, startLL.longitude + lonDiff);
    }

    private static double meterToLongitude(double meterToEast, double latitude) {
        double latArc = Math.toRadians(latitude);
        double radius = Math.cos(latArc) * EARTHRADIUS;
        double rad = meterToEast / radius;
        return Math.toDegrees(rad);
    }


    private static double meterToLatitude(double meterToNorth) {
        double rad = meterToNorth / EARTHRADIUS;
        return Math.toDegrees(rad);
    }

    private void fetchRecentLocations(ArrayList<TLRPC.Message> messages) {
        IMapsProvider.ILatLngBoundsBuilder builder = null;
        if (firstFocus) {
            builder = ApplicationLoader.getMapsProvider().onCreateLatLngBoundsBuilder();
        }
        int date = getConnectionsManager().getCurrentTime();
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message.date + message.media.period > date || message.media.period == 0x7FFFFFFF) {
                if (builder != null) {
                    IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(message.media.geo.lat, message.media.geo._long);
                    builder.include(latLng);
                }
                addUserMarker(message);
                if (proximityButton.getVisibility() != View.GONE && MessageObject.getFromChatId(message) != getUserConfig().getClientUserId()) {
                    proximityButton.setVisibility(View.VISIBLE);
                    proximityAnimationInProgress = true;
                    proximityButton.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(180).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            proximityAnimationInProgress = false;
                            maybeShowProximityHint();
                        }
                    }).start();
                }
            }
        }
        if (builder != null) {
            if (firstFocus) {
                listView.smoothScrollBy(0, dp(66 * 1.5f));
            }
            firstFocus = false;
            adapter.setLiveLocations(markers);
            if (messageObject.isLiveLocation()) {
                try {
                    IMapsProvider.ILatLngBounds bounds = builder.build();
                    IMapsProvider.LatLng center = bounds.getCenter();
                    IMapsProvider.LatLng northEast = move(center, 100, 100);
                    IMapsProvider.LatLng southWest = move(center, -100, -100);
                    builder.include(southWest);
                    builder.include(northEast);
                    bounds = builder.build();
                    if (messages.size() > 1) {
                        try {
                            moveToBounds = ApplicationLoader.getMapsProvider().newCameraUpdateLatLngBounds(bounds, dp(80 + 33));
                            map.moveCamera(moveToBounds);
                            moveToBounds = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                } catch (Exception ignore) {

                }
            }
        }
    }

    private void moveToBounds(int radius, boolean self, boolean animated) {
        IMapsProvider.ILatLngBoundsBuilder builder = ApplicationLoader.getMapsProvider().onCreateLatLngBoundsBuilder();
        builder.include(new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude()));
        if (self) {
            try {
                radius = Math.max(radius, 250);
                IMapsProvider.ILatLngBounds bounds = builder.build();
                IMapsProvider.LatLng center = bounds.getCenter();
                IMapsProvider.LatLng northEast = move(center, radius, radius);
                IMapsProvider.LatLng southWest = move(center, -radius, -radius);
                builder.include(southWest);
                builder.include(northEast);
                bounds = builder.build();
                try {
                    int height = (int) (proximitySheet.getCustomView().getMeasuredHeight() - dp(40) + mapViewClip.getTranslationY()) + mapSheetTopOffset + mapExtraHeight;
                    map.setPadding(dp(70), 0, dp(70), height);
                    if (animated) {
                        map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngBounds(bounds, 0), 500, null);
                    } else {
                        map.moveCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngBounds(bounds, 0));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } catch (Exception ignore) {

            }
        } else {
            int date = getConnectionsManager().getCurrentTime();
            for (int a = 0, N = markers.size(); a < N; a++) {
                TLRPC.Message message = markers.get(a).object;
                if (message.date + message.media.period > date) {
                    IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(message.media.geo.lat, message.media.geo._long);
                    builder.include(latLng);
                }
            }
            try {
                IMapsProvider.ILatLngBounds bounds = builder.build();
                IMapsProvider.LatLng center = bounds.getCenter();
                IMapsProvider.LatLng northEast = move(center, 100, 100);
                IMapsProvider.LatLng southWest = move(center, -100, -100);
                builder.include(southWest);
                builder.include(northEast);
                bounds = builder.build();
                try {
                    int height = proximitySheet.getCustomView().getMeasuredHeight() - dp(100) + mapSheetTopOffset + mapExtraHeight;
                    map.setPadding(dp(70), 0, dp(70), height);
                    map.moveCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngBounds(bounds, 0));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } catch (Exception ignore) {

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
        if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                return false;
            }
        }
        TLRPC.TL_messages_getRecentLocations req = new TLRPC.TL_messages_getRecentLocations();
        final long dialog_id = messageObject.getDialogId();
        req.peer = getMessagesController().getInputPeer(dialog_id);
        req.limit = 100;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (map == null) {
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
                    getLocationController().markLiveLoactionsAsRead(dialogId);
                    if (markAsReadRunnable == null) {
                        markAsReadRunnable = () -> {
                            getLocationController().markLiveLoactionsAsRead(dialogId);
                            if (isPaused || markAsReadRunnable == null) {
                                return;
                            }
                            AndroidUtilities.runOnUIThread(markAsReadRunnable, 5000);
                        };
                        AndroidUtilities.runOnUIThread(markAsReadRunnable, 5000);
                    }
                });
            }
        });
        return messages != null;
    }

    private double bearingBetweenLocations(IMapsProvider.LatLng latLng1, IMapsProvider.LatLng latLng2) {
        double lat1 = latLng1.latitude * Math.PI / 180;
        double long1 = latLng1.longitude * Math.PI / 180;
        double lat2 = latLng2.latitude * Math.PI / 180;
        double long2 = latLng2.longitude * Math.PI / 180;
        double dLon = (long2 - long1);

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double brng = Math.atan2(y, x);

        brng = Math.toDegrees(brng);
        brng = (brng + 360) % 360;

        return brng;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.closeChats) {
            removeSelfFromStack(true);
        } else if (id == NotificationCenter.locationPermissionGranted) {
            locationDenied = false;
            if (adapter != null) {
                adapter.setMyLocationDenied(locationDenied, false);
            }
            if (map != null) {
                try {
                    map.setMyLocationEnabled(true);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.locationPermissionDenied) {
            locationDenied = true;
            if (adapter != null) {
                adapter.setMyLocationDenied(locationDenied, false);
            }
        } else if (id == NotificationCenter.liveLocationsChanged) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateShowAllButton();
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
                } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionGeoProximityReached) {
                    if (DialogObject.isUserDialog(messageObject.getDialogId())) {
                        proximityButton.setImageResource(R.drawable.msg_location_alert);
                        if (proximityCircle != null) {
                            proximityCircle.remove();
                            proximityCircle = null;
                        }
                    }
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
                        liveLocation.object = messageObject.messageOwner;
                        IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(messageObject.messageOwner.media.geo.lat, messageObject.messageOwner.media.geo._long);
                        liveLocation.marker.setPosition(latLng);
                        if (selectedMarkerId == liveLocation.id) {
                            map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLng(liveLocation.marker.getPosition()));
                        }
                        if (liveLocation.directionMarker != null) {
                            IMapsProvider.LatLng oldLocation = liveLocation.directionMarker.getPosition();
                            liveLocation.directionMarker.setPosition(latLng);
                            if (messageObject.messageOwner.media.heading != 0) {
                                liveLocation.directionMarker.setRotation(messageObject.messageOwner.media.heading);
                                if (!liveLocation.hasRotation) {
                                    liveLocation.directionMarker.setIcon(R.drawable.map_pin_cone2);
                                    liveLocation.hasRotation = true;
                                }
                            } else {
                                if (liveLocation.hasRotation) {
                                    liveLocation.directionMarker.setRotation(0);
                                    liveLocation.directionMarker.setIcon(R.drawable.map_pin_circle);
                                    liveLocation.hasRotation = false;
                                }
                            }
                        }
                    }
                    updated = true;
                }
            }
            if (updated && adapter != null) {
                adapter.notifyDataSetChanged();
                if (proximitySheet != null) {
                    proximitySheet.updateText(true, true);
                }
            }
            if (updated) {
                updateShowAllButton();
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
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
        onResumeCalled = false;
        updateMapSnapshotRunning();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (proximitySheet != null) {
            if (invoked) proximitySheet.dismiss();
            return false;
        }
        if (mapView != null && mapView.getGlSurfaceView() != null && !hasScreenshot) {
            if (invoked) onCheckGlScreenshot();
            return false;
        }

        return super.onBackPressed(invoked);
    }

    @Override
    public boolean finishFragment(boolean animated) {
        if (onCheckGlScreenshot()) {
            return false;
        }
        return super.finishFragment(animated);
    }

    private boolean onCheckGlScreenshot() {
        if (mapView != null && mapView.getGlSurfaceView() != null && !hasScreenshot) {
            final GLSurfaceView glSurfaceView = mapView.getGlSurfaceView();
            glSurfaceView.queueEvent(() -> {
                if (glSurfaceView.getWidth() == 0 || glSurfaceView.getHeight() == 0) {
                    return;
                }
                ByteBuffer buffer = ByteBuffer.allocateDirect(glSurfaceView.getWidth() * glSurfaceView.getHeight() * 4);
                GLES20.glReadPixels(0, 0, glSurfaceView.getWidth(), glSurfaceView.getHeight(), GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
                Bitmap bitmap = Bitmap.createBitmap(glSurfaceView.getWidth(), glSurfaceView.getHeight(), Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);

                Matrix flipVertically = new Matrix();
                flipVertically.preScale(1, -1);

                Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), flipVertically, false);
                bitmap.recycle();

                AndroidUtilities.runOnUIThread(()->{
                    ImageView snapshotView = new ImageView(getContext());
                    snapshotView.setImageBitmap(flippedBitmap);

                    ViewGroup parent = (ViewGroup) glSurfaceView.getParent();
                    try {
                        parent.addView(snapshotView, parent.indexOfChild(glSurfaceView));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }

                    AndroidUtilities.runOnUIThread(()->{
                        try {
                            parent.removeView(glSurfaceView);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }

                        hasScreenshot = true;

                        finishFragment();
                    }, 100);
                });
            });
            return true;
        }
        return false;
    }

    @Override
    public void onBecomeFullyHidden() {
        if (undoView[0] != null) {
            undoView[0].hide(true, 0);
        }
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
        pokeMapSnapshot();
        if (map != null) {
            try {
                map.setMyLocationEnabled(true);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        fixLayoutInternal(true);
        if (disablePermissionCheck()) {
            checkPermission = false;
        } else if (checkPermission && Build.VERSION.SDK_INT >= 23) {
            Activity activity = getParentActivity();
            if (activity != null) {
                checkPermission = false;
                if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                }
            }
        }
        if (markAsReadRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(markAsReadRunnable);
            AndroidUtilities.runOnUIThread(markAsReadRunnable, 5000);
        }
    }

    protected boolean disablePermissionCheck() {
        return false;
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
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::cellDelegateThemeColors;

        for (int a = 0; a < undoView.length; a++) {
            themeDescriptions.add(new ThemeDescription(undoView[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
        }


        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogBackground));
        themeDescriptions.add(new ThemeDescription(searchItem != null ? searchItem.getSearchField() : null, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_dialogTextBlack));









        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_location_liveLocationProgress));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_location_placeLocationBackground));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialog_liveLocationProgress));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLocationIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLiveLocationIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLocationBackground));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLiveLocationBackground));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{LocationDirectionCell.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_featuredStickers_addButtonPressed));








        return themeDescriptions;
    }

    public String getAddressName() {
        return adapter != null ? adapter.getAddressName() : null;
    }

    @Override
    public boolean isLightStatusBar() {
        return ColorUtils.calculateLuminance(getThemedColor(Theme.key_windowBackgroundWhite)) > 0.7f;
    }

    private class NestedFrameLayout extends SizeNotifierFrameLayout implements NestedScrollingParent3 {

        private NestedScrollingParentHelper nestedScrollingParentHelper;

        private boolean first = true;

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);

            if (changed) {
                fixLayoutInternal(first);
                first = false;
            } else {
                updateClipView(true);
            }
        }

        public NestedFrameLayout(Context context) {
            super(context);
            nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, int[] consumed) {
            try {
                if (target == listView && sharedMediaLayout != null && sharedMediaLayout.isAttachedToWindow()) {
                    RecyclerListView innerListView = sharedMediaLayout.getCurrentListView();
                    int top = sharedMediaLayout.getTop();
                    if (innerListView != null && top == 0) {
                        consumed[1] = dyUnconsumed;
                        innerListView.scrollBy(0, dyUnconsumed);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        RecyclerListView innerListView = sharedMediaLayout.getCurrentListView();
                        if (innerListView != null && innerListView.getAdapter() != null) {
                            innerListView.getAdapter().notifyDataSetChanged();
                        }
                    } catch (Throwable e2) {

                    }
                });
            }
        }

        @Override
        public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {

        }

        @Override
        public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
            return super.onNestedPreFling(target, velocityX, velocityY);
        }

        @Override
        public void onNestedPreScroll(View target, int dx, int dy, int[] consumed, int type) {
            if (target == listView && sharedMediaLayout != null && sharedMediaLayout.isAttachedToWindow()) {
                boolean searchVisible = simpleActionBar != null && simpleActionBar.isSearchFieldVisible();
                int t = sharedMediaLayout.getTop();
                if (dy < 0) {
                    boolean scrolledInner = false;
                    if (t <= 0) {
                        RecyclerListView innerListView = sharedMediaLayout.getCurrentListView();
                        if (innerListView != null) {
                            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) innerListView.getLayoutManager();
                            int pos = linearLayoutManager.findFirstVisibleItemPosition();
                            if (pos != RecyclerView.NO_POSITION) {
                                RecyclerView.ViewHolder holder = innerListView.findViewHolderForAdapterPosition(pos);
                                int top = holder != null ? holder.itemView.getTop() : -1;
                                int paddingTop = innerListView.getPaddingTop();
                                if (top != paddingTop || pos != 0) {
                                    consumed[1] = pos != 0 ? dy : Math.max(dy, (top - paddingTop));
                                    innerListView.scrollBy(0, dy);
                                    scrolledInner = true;
                                }
                            }
                        }
                    }
                    if (searchVisible) {
                        if (!scrolledInner && t < 0) {
                            consumed[1] = dy - Math.max(t, dy);
                        } else {
                            consumed[1] = dy;
                        }
                    }
                } else {
                    if (searchVisible) {
                        RecyclerListView innerListView = sharedMediaLayout.getCurrentListView();
                        consumed[1] = dy;
                        if (t > 0) {
                            consumed[1] -= dy;
                        }
                        if (innerListView != null && consumed[1] > 0) {
                            innerListView.scrollBy(0, consumed[1]);
                        }
                    }
                }
            }
        }

        @Override
        public boolean onStartNestedScroll(View child, View target, int axes, int type) {
            return sharedMediaLayout != null && axes == ViewCompat.SCROLL_AXIS_VERTICAL;
        }

        @Override
        public void onNestedScrollAccepted(View child, View target, int axes, int type) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        }

        @Override
        public void onStopNestedScroll(View target, int type) {
            nestedScrollingParentHelper.onStopNestedScroll(target);
        }

        @Override
        public void onStopNestedScroll(View child) {

        }
    }


    @Override
    public void onThemeColorsChanged() {
        try {
            super.onThemeColorsChanged();
            cellDelegateThemeColors();
    
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            if (searchItem != null && searchItem.getSearchField() != null) {
                searchItem.getSearchField().setCursorColor(getThemedColor(Theme.key_dialogTextBlack));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void cellDelegateThemeColors() {
        if (sheetGlass != null) {
            sheetGlass.updateColors();
        }
        if (listView != null) {
            listView.invalidate();
        }

        if (map != null) {
            int themeResId = getMapThemeResId();
            if (themeResId != 0) {
                if (!currentMapStyleDark) {
                    currentMapStyleDark = true;
                    IMapsProvider.IMapStyleOptions style = ApplicationLoader.getMapsProvider().loadRawResourceStyle(ApplicationLoader.applicationContext, themeResId);
                    map.setMapStyle(style);
                    if (proximityCircle != null) {
                        proximityCircle.setStrokeColor(0xffffffff);
                        proximityCircle.setFillColor(0x20ffffff);
                    }
                }
            } else {
                if (currentMapStyleDark) {
                    currentMapStyleDark = false;
                    map.setMapStyle(null);
                    if (proximityCircle != null) {
                        proximityCircle.setStrokeColor(0xff000000);
                        proximityCircle.setFillColor(0x20000000);
                    }
                }
            }
            pokeMapSnapshot();
        }
    }

    /** theme editor: colors this page shows (former ThemeDescription keys) */
    @Override
    public void addThemeColorKeys(HashSet<Integer> keys) {
        super.addThemeColorKeys(keys);
        keys.add(Theme.key_windowBackgroundWhiteGrayText3);
        keys.add(Theme.key_windowBackgroundWhiteBlackText);
        keys.add(Theme.key_dialogTextBlue2);
        keys.add(Theme.key_windowBackgroundGray);
        keys.add(Theme.key_windowBackgroundGrayShadow);
        keys.add(Theme.key_featuredStickers_buttonText);
        keys.add(Theme.key_location_sendLocationText);
        keys.add(Theme.key_location_sendLiveLocationText);
        keys.add(Theme.key_avatar_backgroundPink);
        keys.add(Theme.key_avatar_backgroundBlue);
        keys.add(Theme.key_avatar_backgroundCyan);
        keys.add(Theme.key_avatar_backgroundGreen);
        keys.add(Theme.key_avatar_backgroundViolet);
        keys.add(Theme.key_avatar_backgroundOrange);
        keys.add(Theme.key_avatar_backgroundRed);
        keys.add(Theme.key_avatar_text);
        keys.add(Theme.key_divider);
        keys.add(Theme.key_listSelector);
        keys.add(Theme.key_undo_infoColor);
        keys.add(Theme.key_undo_background);
        keys.add(Theme.key_undo_cancelColor);
    }
}
