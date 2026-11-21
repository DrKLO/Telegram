/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.IMapsProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.LocationActivityAdapter;
import org.telegram.ui.Adapters.LocationActivitySearchAdapter;
import org.telegram.ui.BasePermissionsActivity;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LocationDirectionCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SharingLiveLocationCell;
import org.telegram.ui.ChatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAttachAlertLocationLayout extends ChatAttachAlert.AttachAlertLayout implements Theme.Colorable, NotificationCenter.NotificationCenterDelegate {

    private ImageView locationButton;
    private ActionBarMenuItem mapTypeButton;
    private LinearLayout emptyView;
    private ImageView emptyImageView;
    private TextView emptyTitleTextView;
    private TextView emptySubtitleTextView;
    private ActionBarMenuItem searchItem;

    private boolean doNotDrawMap;
    private IMapsProvider.IMap map;
    private IMapsProvider.IMapView mapView;
    private IMapsProvider.ICameraUpdate forceUpdate;
    private float yOffset;

    private boolean scrolling;

    private View loadingMapView;
    private FrameLayout mapViewClip;
    private LocationActivityAdapter adapter;
    private RecyclerListView listView;
    private RecyclerListView searchListView;
    private LocationActivitySearchAdapter searchAdapter;
    private ImageView markerImageView;
    private FillLastLinearLayoutManager layoutManager;
    private AvatarDrawable avatarDrawable;
    private ActionBarMenuItem otherItem;

    private boolean currentMapStyleDark;

    private boolean checkGpsEnabled = true;
    private boolean askedForLocation = false;
    private boolean locationDenied = false;

    private boolean isFirstLocation = true;
    private long dialogId;

    private boolean firstFocus = true;

    private Paint backgroundPaint = new Paint();


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

    private boolean ignoreIdleCamera;
    private boolean userLocationMoved;
    private boolean firstWas;
    private LocationActivityDelegate delegate;

    private int locationType;

    // the row of the map in the list as the sheet opens; the map itself goes from the top of the sheet down to the
    // bottom of the screen, under the cards and the tabs, and up to the top of the screen as the sheet gets there
    // (see positionMap)
    private int overScrollHeight = AndroidUtilities.displaySize.x - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(66);
    private int mapHeight = overScrollHeight;
    // the cards under the map as laid out, the rows after the map's but the scroll room
    private int cardsHeight = -1;
    // where the cards are in the list now, the map gets the touches above them
    private int cardsTop = Integer.MAX_VALUE;
    // the sheet's background under the layout, see onSheetBackground
    private float sheetTop = Float.NaN, sheetRound = 1f;
    // the strip at the top of the map that pulls the sheet, not the map
    private static final int MAP_GRAB_HEIGHT = 32;
    private final int[] windowLocation = new int[2];
    // the map's part between the top of the sheet's content and the cards, in its coordinates
    private int mapFreeTop, mapFreeBottom = Integer.MAX_VALUE;
    private int clipSize;
    private int nonClipSize;

    private final static int map_list_menu_map = 2;
    private final static int map_list_menu_satellite = 3;
    private final static int map_list_menu_hybrid = 4;

    public final static int LOCATION_TYPE_SEND = 0;
    public final static int LOCATION_TYPE_SEND_WITH_LIVE = 1;
    public final static int LOCATION_TYPE_STORY = 7;
    public final static int LOCATION_TYPE_BIZ = 8;

    public static class LiveLocation {
        public int id;
        public TLRPC.Message object;
        public TLRPC.User user;
        public TLRPC.Chat chat;
        public IMapsProvider.IMarker marker;
    }

    public interface LocationActivityDelegate {
        void didSelectLocation(TLRPC.MessageMedia location, int live, boolean notify, int scheduleDate, long payStars);
    }

    public ChatAttachAlertLocationLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider, boolean allowLiveLocation) {
        super(alert, context, resourcesProvider);
        AndroidUtilities.fixGoogleMapsBug();
        ChatActivity chatActivity = parentAlert.baseFragment instanceof ChatActivity ? (ChatActivity) parentAlert.baseFragment : null;
        dialogId = parentAlert.getDialogId();
        if (parentAlert.isStoryLocationPicker) {
            locationType = LOCATION_TYPE_STORY;
        } else if (parentAlert.isBizLocationPicker) {
            locationType = LOCATION_TYPE_BIZ;
        } else if (allowLiveLocation && chatActivity != null && chatActivity.getCurrentEncryptedChat() == null && !chatActivity.isInScheduleMode() && !UserObject.isUserSelf(chatActivity.getCurrentUser())) {
            locationType = LOCATION_TYPE_SEND_WITH_LIVE;
        } else {
            locationType = LOCATION_TYPE_SEND;
        }
        NotificationCenter.getGlobalInstance().addWeakObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().addWeakObserver(this, NotificationCenter.locationPermissionDenied);

        searchWas = false;
        searching = false;
        searchInProgress = false;
        if (adapter != null) {
            adapter.destroy();
        }
        if (searchAdapter != null) {
            searchAdapter.destroy();
        }

        locationDenied = Build.VERSION.SDK_INT >= 23 && getParentActivity() != null && getParentActivity().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        ActionBarMenu menu = parentAlert.actionBar.createMenu();

        searchItem = menu.addItem(0, R.drawable.outline_header_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                parentAlert.makeFocusable(searchItem.getSearchField(), true);
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                searchWas = false;
                searchAdapter.searchDelayed(null);
                updateEmptyView();

                if (otherItem != null) {
                    otherItem.setVisibility(VISIBLE);
                }
                listView.setVisibility(VISIBLE);
                mapViewClip.setVisibility(VISIBLE);
                searchListView.setVisibility(GONE);
                emptyView.setVisibility(GONE);
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
                        otherItem.setVisibility(GONE);
                    }
                    listView.setVisibility(GONE);
                    mapViewClip.setVisibility(GONE);
                    if (searchListView.getAdapter() != searchAdapter) {
                        searchListView.setAdapter(searchAdapter);
                    }
                    searchListView.setVisibility(VISIBLE);
                    searchInProgress = searchAdapter.isEmpty();
                    updateEmptyView();
                } else {
                    if (otherItem != null) {
                        otherItem.setVisibility(VISIBLE);
                    }
                    listView.setVisibility(VISIBLE);
                    mapViewClip.setVisibility(VISIBLE);
                    searchListView.setAdapter(null);
                    searchListView.setVisibility(GONE);
                    emptyView.setVisibility(GONE);
                }
                searchAdapter.searchDelayed(text);
            }
        });
        searchItem.setVisibility(parentAlert.isBizLocationPicker ? View.GONE : View.VISIBLE);
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));
        searchItem.setContentDescription(LocaleController.getString(R.string.Search));
        EditTextBoldCursor editText = searchItem.getSearchField();
        ThemeBind.textColor(editText, Theme.key_dialogTextBlack, resourcesProvider);
        ThemeBind.cursorColor(editText, Theme.key_dialogTextBlack, resourcesProvider);
        ThemeBind.hintTextColor(editText, Theme.key_chat_messagePanelHint, resourcesProvider);

        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(21));
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;

        mapViewClip = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                canvas.save();
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - clipSize);
                boolean result = doNotDrawMap ? false : super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return result;
            }

            @Override
            protected void onDraw(Canvas canvas) {
                backgroundPaint.setColor(getThemedColor(Theme.key_dialogBackground));
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - clipSize, backgroundPaint);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && (ev.getY() < mapFreeTop || ev.getY() > mapFreeBottom)) {
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                // the map under the header, the sheet's edge and the cards is to be seen, not touched
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && (ev.getY() < mapFreeTop || ev.getY() > mapFreeBottom)) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        mapViewClip.setWillNotDraw(false);

        loadingMapView = new View(context);
        loadingMapView.setBackgroundDrawable(new MapPlaceholderDrawable());

        mapTypeButton = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_location_actionIcon), resourcesProvider);
        mapTypeButton.setClickable(true);
        mapTypeButton.setSubMenuOpenSide(2);
        mapTypeButton.setAdditionalXOffset(AndroidUtilities.dp(10));
        mapTypeButton.setAdditionalYOffset(-AndroidUtilities.dp(10));
        mapTypeButton.addSubItem(map_list_menu_map, R.drawable.msg_map, LocaleController.getString(R.string.Map), resourcesProvider);
        mapTypeButton.addSubItem(map_list_menu_satellite, R.drawable.msg_satellite, LocaleController.getString(R.string.Satellite), resourcesProvider);
        mapTypeButton.addSubItem(map_list_menu_hybrid, R.drawable.msg_hybrid, LocaleController.getString(R.string.Hybrid), resourcesProvider);
        mapTypeButton.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40), getThemedColor(Theme.key_location_actionBackground), getThemedColor(Theme.key_location_actionPressedBackground));
        ScaleStateListAnimator.apply(mapTypeButton);
        mapTypeButton.setTranslationZ(dp(2));
        mapTypeButton.setOutlineProvider(ViewOutlineProviderImpl.BOUNDS_OVAL);
        ThemeBind.selectorBackground(mapTypeButton, drawable, ThemeBind.of(Theme.key_location_actionBackground), ThemeBind.of(Theme.key_location_actionPressedBackground), resourcesProvider);
        mapTypeButton.setIcon(R.drawable.msg_map_type);
        mapViewClip.addView(mapTypeButton, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.TOP, 0, 12, 12, 0));
        mapTypeButton.setOnClickListener(v -> mapTypeButton.toggleSubMenu());
        mapTypeButton.setDelegate(id -> {
            if (map == null) {
                return;
            }
            if (id == map_list_menu_map) {
                map.setMapType(IMapsProvider.MAP_TYPE_NORMAL);
            } else if (id == map_list_menu_satellite) {
                map.setMapType(IMapsProvider.MAP_TYPE_SATELLITE);
            } else if (id == map_list_menu_hybrid) {
                map.setMapType(IMapsProvider.MAP_TYPE_HYBRID);
            }
        });

        locationButton = new ImageView(context);
        drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40), getThemedColor(Theme.key_location_actionBackground), getThemedColor(Theme.key_location_actionPressedBackground));
        ScaleStateListAnimator.apply(locationButton);
        locationButton.setTranslationZ(dp(2));
        locationButton.setOutlineProvider(ViewOutlineProviderImpl.BOUNDS_OVAL);
        ThemeBind.selectorBackground(locationButton, drawable, ThemeBind.of(Theme.key_location_actionBackground), ThemeBind.of(Theme.key_location_actionPressedBackground), resourcesProvider);
        locationButton.setImageResource(R.drawable.msg_current_location);
        locationButton.setScaleType(ImageView.ScaleType.CENTER);
        ThemeBind.colorFilter(locationButton, Theme.key_location_actionActiveIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
        locationButton.setTag(Theme.key_location_actionActiveIcon);
        locationButton.setContentDescription(LocaleController.getString(R.string.AccDescrMyLocation));
        LayoutParams layoutParams1 = LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 12, 12);
        mapViewClip.addView(locationButton, layoutParams1);
        locationButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23) {
                Activity activity = getParentActivity();
                if (activity != null) {
                    if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        AlertsCreator.createLocationRequiredDialog(getParentActivity(), true).show();
                        return;
                    }
                }
            }
            if (myLocation != null && map != null) {
                ThemeBind.colorFilter(locationButton, Theme.key_location_actionActiveIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
                locationButton.setTag(Theme.key_location_actionActiveIcon);
                adapter.setCustomLocation(null);
                userLocationMoved = false;
                map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLng(new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude())));
            }
        });

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyView.setPadding(0, AndroidUtilities.dp(60 + 100), 0, 0);
        emptyView.setVisibility(GONE);
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setOnTouchListener((v, event) -> true);

        emptyImageView = new ImageView(context);
        emptyImageView.setImageResource(R.drawable.location_empty);
        ThemeBind.colorFilter(emptyImageView, Theme.key_dialogEmptyImage, resourcesProvider, PorterDuff.Mode.MULTIPLY);
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTitleTextView = new TextView(context);
        ThemeBind.textColor(emptyTitleTextView, Theme.key_dialogEmptyText, resourcesProvider);
        emptyTitleTextView.setGravity(Gravity.CENTER);
        emptyTitleTextView.setTypeface(AndroidUtilities.bold());
        emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleTextView.setText(LocaleController.getString(R.string.NoPlacesFound));
        emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

        emptySubtitleTextView = new TextView(context);
        ThemeBind.textColor(emptySubtitleTextView, Theme.key_dialogEmptyText, resourcesProvider);
        emptySubtitleTextView.setGravity(Gravity.CENTER);
        emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptySubtitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                updateMapSpace();
                updateClipView();
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                // the map is under the list: above the cards the touches are its own, but for a strip along the top of
                // the sheet that pulls it, see MAP_GRAB_HEIGHT
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && ev.getY() < cardsTop && mapViewClip != null && mapViewClip.getVisibility() == VISIBLE) {
                    final RecyclerView.ViewHolder first = findViewHolderForAdapterPosition(0);
                    if (first != null && ev.getY() >= Math.max(0, first.itemView.getTop()) + AndroidUtilities.dp(MAP_GRAB_HEIGHT)) {
                        return false;
                    }
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        setBlur3Capture(listView);
        iBlur3CaptureView = listView;
        occupyNavigationBar = true;
        listView.setClipToPadding(false);
        listView.setAdapter(adapter = new LocationActivityAdapter(context, locationType, dialogId, true, resourcesProvider, parentAlert.isStoryLocationPicker, false, parentAlert.isBizLocationPicker));
        adapter.isPollAttach = parentAlert != null && (parentAlert.isPollAttach || parentAlert.isLocationPicker);
        // the cards are placed by translation at the bottom of the sheet (updateClipView), the animator would move them too
        listView.setItemAnimator(null);
        adapter.setUpdateRunnable(this::updateClipView);
        adapter.setMyLocationDenied(locationDenied, askedForLocation);
        listView.setVerticalScrollBarEnabled(false);
        listView.setSections();
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(context, LinearLayoutManager.VERTICAL, false, 0, listView) {
            @Override
            protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace) {
                super.calculateExtraLayoutSpace(state, extraLayoutSpace);
                // the cards are moved up to the bottom of the sheet from where the list lays them, maybe below it
                extraLayoutSpace[1] = Math.max(extraLayoutSpace[1], listView.getHeight());
            }

            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, snapPreference);
                        dy -= (listView.getPaddingTop() - (mapHeight - overScrollHeight));
                        return dy;
                    }

                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return super.calculateTimeForDeceleration(dx) * 4;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                if (!scrolling && forceUpdate != null) {
                    forceUpdate = null;
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = AndroidUtilities.dp(13);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        // close to the top: pulled up all the way
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > 0) {
                            listView.smoothScrollBy(0, holder.itemView.getTop());
                        }
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateClipView();
                if (forceUpdate != null) {
                    yOffset += dy;
                }
                parentAlert.updateLayout(ChatAttachAlertLocationLayout.this, true, dy);
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (locationType == LOCATION_TYPE_STORY) {
                if (position == 1 && adapter.city != null) {
                    delegate.didSelectLocation(adapter.city, locationType, true, 0, 0);
                    parentAlert.dismiss(true);
                    return;
                } else if (position == 2 && adapter.street != null) {
                    delegate.didSelectLocation(adapter.street, locationType, true, 0, 0);
                    parentAlert.dismiss(true);
                    return;
                }
            } else if (position == 1) {
                if (delegate != null && userLocation != null) {
                    TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                    location.geo = new TLRPC.TL_geoPoint();
                    location.geo.lat = AndroidUtilities.fixLocationCoord(userLocation.getLatitude());
                    location.geo._long = AndroidUtilities.fixLocationCoord(userLocation.getLongitude());
                    AlertsCreator.ensurePaidMessageConfirmation(parentAlert.currentAccount, parentAlert.getDialogId(), 1 + parentAlert.getAdditionalMessagesCount(), payStars -> {
                        if (chatActivity != null && chatActivity.isInScheduleMode()) {
                            AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> {
                                delegate.didSelectLocation(location, locationType, notify, scheduleDate, payStars);
                                parentAlert.dismiss(true);
                            }, resourcesProvider);
                        } else {
                            delegate.didSelectLocation(location, locationType, true, 0, payStars);
                            parentAlert.dismiss(true);
                        }
                    });
                } else if (locationDenied) {
                    AlertsCreator.createLocationRequiredDialog(getParentActivity(), true).show();
                }
                return;
            } else if (position == 2 && locationType == LOCATION_TYPE_SEND_WITH_LIVE) {
                if (getLocationController().isSharingLocation(dialogId)) {
                    getLocationController().removeSharingLocation(dialogId);
                    parentAlert.dismiss(true);
                } else {
                    if (myLocation == null && locationDenied) {
                        AlertsCreator.createLocationRequiredDialog(getParentActivity(), true).show();
                    } else {
                        openShareLiveLocation();
                    }
                }
                return;
            }

            Object object = adapter.getItem(position);
            if (object instanceof TLRPC.TL_messageMediaVenue) {
                AlertsCreator.ensurePaidMessageConfirmation(parentAlert.currentAccount, parentAlert.getDialogId(), 1 + parentAlert.getAdditionalMessagesCount(), payStars -> {
                    if (chatActivity != null && chatActivity.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> {
                            delegate.didSelectLocation((TLRPC.TL_messageMediaVenue) object, locationType, notify, scheduleDate, 0);
                            parentAlert.dismiss(true);
                        }, resourcesProvider);
                    } else {
                        delegate.didSelectLocation((TLRPC.TL_messageMediaVenue) object, locationType, true, 0, 0);
                        parentAlert.dismiss(true);
                    }
                });
            } else if (object instanceof LiveLocation) {
                LiveLocation liveLocation = (LiveLocation) object;
                map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(new IMapsProvider.LatLng(liveLocation.marker.getPosition().latitude, liveLocation.marker.getPosition().longitude), map.getMaxZoomLevel() - 4));
            }
        });
        adapter.setOverScrollHeight(overScrollHeight);

        // under the list: the cards are over it
        addView(mapViewClip, indexOfChild(listView), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        mapView = ApplicationLoader.getMapsProvider().onCreateMapView(context);
        mapView.setOnDispatchTouchEventInterceptor((ev, origMethod) -> {
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
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                if (animatorSet != null) {
                    animatorSet.cancel();
                }
                animatorSet = new AnimatorSet();
                animatorSet.setDuration(200);
                animatorSet.playTogether(ObjectAnimator.ofFloat(markerImageView, View.TRANSLATION_Y, markerTop - AndroidUtilities.dp(10)));
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
//                adapter.fetchLocationAddress();
            }
            if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                if (!userLocationMoved) {
                    ThemeBind.colorFilter(locationButton, Theme.key_location_actionIcon, resourcesProvider, PorterDuff.Mode.MULTIPLY);
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
            return origMethod.call(ev);
        });
        final IMapsProvider.IMapView map = mapView;
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
                            this.map.setOnMapLoadedCallback(() -> AndroidUtilities.runOnUIThread(() -> {
                                loadingMapView.setTag(1);
                                loadingMapView.animate().alpha(0.0f).setDuration(180).start();
                            }));
                            if (isActiveThemeDark()) {
                                currentMapStyleDark = true;
                                IMapsProvider.IMapStyleOptions style = ApplicationLoader.getMapsProvider().loadRawResourceStyle(ApplicationLoader.applicationContext, R.raw.mapstyle_night);
                                this.map.setMapStyle(style);
                            }
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

        markerImageView = new ImageView(context);
        markerImageView.setImageResource(R.drawable.map_pin2);
        mapViewClip.addView(markerImageView, LayoutHelper.createFrame(28, 48, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        searchListView = new RecyclerListView(context, resourcesProvider);
        searchListView.setSections(true);
        searchListView.setClipToPadding(false);
        searchListView.setVisibility(GONE);
        searchListView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        searchAdapter = new LocationActivitySearchAdapter(context, resourcesProvider, parentAlert.isStoryLocationPicker, parentAlert.isBizLocationPicker) {
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
        searchListView.setItemAnimator(null);
        addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        searchListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && searchWas) {
                    AndroidUtilities.hideKeyboard(parentAlert.getCurrentFocus());
                }
            }
        });
        searchListView.setOnItemClickListener((view, position) -> {
            TLRPC.TL_messageMediaVenue object = searchAdapter.getItem(position);
            if (object != null && locationType != LOCATION_TYPE_STORY) {
                // a found address only moves the pin, it is sent as the selected location
                parentAlert.actionBar.closeSearchField();
                resetMapPosition(object.geo.lat, object.geo._long);
                return;
            }
            if (object != null && delegate != null) {
                if (chatActivity != null && chatActivity.isInScheduleMode()) {
                    AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> {
                        delegate.didSelectLocation(object, locationType, notify, scheduleDate, 0);
                        parentAlert.dismiss(true);
                    }, resourcesProvider);
                } else {
                    delegate.didSelectLocation(object, locationType, true, 0, 0);
                    parentAlert.dismiss(true);
                }
            }
        });

        updateEmptyView();
    }

    /** The map goes up under the header and the status bar. */
    @Override
    public boolean disableTopFade() {
        return true;
    }

    /** The cards are at the bottom of the sheet, over the map: the fade is only for the tabs, when there are tabs. */
    @Override
    public boolean disableBottomFade() {
        return !hasTabs();
    }

    /** The tabs stay with the map pulled up: the cards are over them. */
    @Override
    public boolean shouldHideBottomButtons() {
        return false;
    }

    @Override
    public void onPause() {
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
    public void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.locationPermissionDenied);
        doNotDrawMap = true;
        if (mapViewClip != null) {
            mapViewClip.invalidate();
        }
        try {
            if (map != null) {
                map.setMyLocationEnabled(false);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (mapView != null) {
            mapView.getView().setTranslationY(-AndroidUtilities.displaySize.y * 3);
        }
        try {
            if (mapView != null) {
                mapView.onPause();
            }
        } catch (Exception ignore) {

        }
        try {
            if (mapView != null) {
                mapView.onDestroy();
                mapView = null;
            }
        } catch (Exception ignore) {

        }
        if (adapter != null) {
            adapter.destroy();
        }
        if (searchAdapter != null) {
            searchAdapter.destroy();
        }
        parentAlert.actionBar.closeSearchField();
        ActionBarMenu menu = parentAlert.actionBar.createMenu();
        menu.removeView(searchItem);
    }

    @Override
    public void onHide() {
        searchItem.setVisibility(GONE);
    }

    @Override
    public int needsActionBar() {
        return 1;
    }

    @Override
    public boolean onDismiss() {
        onDestroy();
        return false;
    }

    @Override
    public int getCurrentItemTop() {
        if (listView.getChildCount() <= 0) {
            return Integer.MAX_VALUE;
        }
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(0);
        int newOffset = 0;
        if (holder != null) {
            int top = (int) holder.itemView.getY() - nonClipSize;
            newOffset = Math.max(top, 0);
        }
        return newOffset + AndroidUtilities.dp(56);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
        updateClipView();
    }

    @Override
    public int getListTopPadding() {
        return listView.getPaddingTop();
    }

    @Override
    public int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(56);
    }

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (parentAlert.actionBar.isSearchFieldVisible() || parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
            padding = 0;
            parentAlert.setAllowNestedScroll(false);
        } else {
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                padding = (int) (availableHeight / 3.5f);
            } else {
                padding = (availableHeight / 5 * 2);
            }
            padding -= AndroidUtilities.dp(52);
            if (padding < 0) {
                padding = 0;
            }
            parentAlert.setAllowNestedScroll(true);
        }
        // the list ends over the navigation bar, the cards keep over the tabs themselves (getBottomInset); the room to
        // pull the sheet up is as high as its top padding (the last row fills the rest, FillLastLinearLayoutManager)
        listView.setPaddingWithoutRequestLayout(0, padding, 0, getMinBottomInset());
        searchListView.setPaddingWithoutRequestLayout(0, 0, 0, listPaddingBottom);
    }

    private boolean first = true;

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            fixLayoutInternal(first);
            first = false;
        }
    }

    @Override
    public int getButtonsHideOffset() {
        return AndroidUtilities.dp(56);
    }

    @Override
    public void scrollToTop() {
        listView.smoothScrollToPosition(0);
    }

    private boolean isActiveThemeDark() {
        Theme.ThemeInfo info = Theme.getActiveTheme();
        if (info.isDark()) {
            return true;
        }
        int color = getThemedColor(Theme.key_windowBackgroundWhite);
        return AndroidUtilities.computePerceivedBrightness(color) < 0.721f;
    }

    private void updateEmptyView() {
        if (searching) {
            if (searchInProgress) {
                searchListView.setEmptyView(null);
                emptyView.setVisibility(GONE);
            } else {
                searchListView.setEmptyView(emptyView);
            }
        } else {
            emptyView.setVisibility(GONE);
        }
    }

    public void openShareLiveLocation() {
        if (delegate == null || getParentActivity() == null || myLocation == null) {
            return;
        }
        TLRPC.User user = null;
        if (DialogObject.isUserDialog(dialogId)) {
            user = parentAlert.baseFragment.getMessagesController().getUser(dialogId);
        }
        AlertsCreator.createLocationUpdateDialog(getParentActivity(), false, user, param -> {
            AlertsCreator.ensurePaidMessageConfirmation(parentAlert.currentAccount, parentAlert.getDialogId(), 1 + parentAlert.getAdditionalMessagesCount(), payStars -> {
                final TLRPC.TL_messageMediaGeoLive location = new TLRPC.TL_messageMediaGeoLive();
                location.geo = new TLRPC.TL_geoPoint();
                location.geo.lat = AndroidUtilities.fixLocationCoord(myLocation.getLatitude());
                location.geo._long = AndroidUtilities.fixLocationCoord(myLocation.getLongitude());
                location.period = param;
                delegate.didSelectLocation(location, locationType, true, 0, payStars);
                parentAlert.dismiss(true);
            });
        }, resourcesProvider).show();
    }

    private MessagesController getMessagesController() {
        return parentAlert.baseFragment.getMessagesController();
    }

    private LocationController getLocationController() {
        return parentAlert.baseFragment.getLocationController();
    }

    private UserConfig getUserConfig() {
        return parentAlert.baseFragment.getUserConfig();
    }

    private Activity getParentActivity() {
        return parentAlert != null && parentAlert.baseFragment != null ? parentAlert.baseFragment.getParentActivity() : null;
    }

    private void onMapInit() {
        if (map == null) {
            return;
        }

        userLocation = new Location("network");
        userLocation.setLatitude(20.659322);
        userLocation.setLongitude(-11.406250);

        try {
            map.setMyLocationEnabled(true);
        } catch (Exception e) {
            FileLog.e(e);
        }
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
        map.setOnCameraMoveStartedListener(reason -> {
            if (reason == IMapsProvider.OnCameraMoveStartedListener.REASON_GESTURE) {
                if (!scrolling && listView.getChildCount() > 0) {
                    View view = listView.getChildAt(0);
                    if (view != null) {
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(view);
                        if (holder != null && holder.getAdapterPosition() == 0) {
                            int min = locationType == LOCATION_TYPE_SEND ? 0 : AndroidUtilities.dp(66);
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
        map.setOnCameraIdleListener(() -> {
            if (ignoreIdleCamera) {
                ignoreIdleCamera = false;
                return;
            }
            if (map != null) {
                if (userLocation != null) {
                    userLocation.setLatitude(map.getCameraPosition().target.latitude);
                    userLocation.setLongitude(map.getCameraPosition().target.longitude);
                }
            }
            adapter.setCustomLocation(userLocation);
            adapter.fetchLocationAddress();
        });
        map.setOnMyLocationChangeListener(location -> {
            if (parentAlert == null || parentAlert.baseFragment == null) {
                return;
            }
            positionMarker(location);
            if (adapter != null && (locationType == LOCATION_TYPE_STORY || locationType == LOCATION_TYPE_BIZ) && !userLocationMoved) {
                adapter.setCustomLocation(userLocation);
            }
            getLocationController().setMapLocation(location, isFirstLocation);
            isFirstLocation = false;
        });
        map.setOnMarkerClickListener(marker -> true);
        positionMarker();
        AndroidUtilities.runOnUIThread(() -> {
            if (loadingMapView.getTag() == null) {
                loadingMapView.animate().alpha(0.0f).setDuration(180).start();
            }
        }, 200);

        if (checkGpsEnabled && getParentActivity() != null) {
            checkGpsEnabled = false;
            Activity parentActivity;
            PackageManager packageManager;
            if ((parentActivity = getParentActivity()) != null && (packageManager = parentActivity.getPackageManager()) != null && !packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
                return;
            }
            try {
                LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
                    builder.setTopAnimation(R.raw.permission_request_location, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground, resourcesProvider)); builder.setTopBackgroundColorKey(Theme.key_dialogTopBackground, resourcesProvider);
                    builder.setMessage(LocaleController.getString(R.string.GpsDisabledAlertText));
                    builder.setPositiveButton(LocaleController.getString(R.string.ConnectingToProxyEnable), (dialog, id) -> {
                        if (getParentActivity() == null) {
                            return;
                        }
                        try {
                            getParentActivity().startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (Exception ignore) {

                        }
                    });
                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    builder.show();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        updateClipView();
    }

    private void resetMapPosition(double lat, double _long) {
        if (map == null) {
            return;
        }
        if (lat != 0 && _long != 0) {
            userLocation = new Location("");
            userLocation.reset();
            userLocation.setLatitude(lat);
            userLocation.setLongitude(_long);
        } else {
            myLocation = new Location("");
            myLocation.reset();
            myLocation.setLatitude(lat);
            myLocation.setLongitude(_long);
        }
        IMapsProvider.LatLng latLng = new IMapsProvider.LatLng(lat, _long);
        IMapsProvider.ICameraUpdate position;
        if (lat != 0 && _long != 0) {
            position = ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(latLng, map.getMaxZoomLevel() - 4);
        } else {
            position = ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(latLng, map.getMinZoomLevel());
        }
        forceUpdate = position;
        map.moveCamera(position);
        if (lat != 0 && _long != 0) {
            adapter.setCustomLocation(userLocation);
        } else {
            adapter.setGpsLocation(myLocation);
        }
        adapter.fetchLocationAddress();
        listView.smoothScrollBy(0, 1);
        ignoreIdleCamera = true;

        if (lat != 0 && _long != 0) {
            userLocationMoved = true;
        }
    }

    /**
     * The map takes the room the cards leave. As the sheet opens, the cards are at its bottom under the map's row; as
     * it is pulled up (the list scrolls by up to its top padding into the room below the cards), the cards stay at the
     * bottom and the map grows to the top of the sheet.
     */
    private void updateMapSpace() {
        if (mapView == null || mapViewClip == null || getMeasuredHeight() == 0) {
            return;
        }
        final RecyclerView.ViewHolder first = listView.findViewHolderForAdapterPosition(0);
        if (first != null) {
            int bottom = first.itemView.getBottom();
            boolean all = true;
            for (int i = 1, count = adapter.getItemCount(); i < count; i++) {
                if (adapter.getItemViewType(i) == LocationActivityAdapter.VIEW_TYPE_EMPTY) {
                    continue;
                }
                final RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(i);
                if (holder == null) {
                    all = false;
                    break;
                }
                bottom = Math.max(bottom, holder.itemView.getBottom());
            }
            if (all) {
                cardsHeight = bottom - first.itemView.getBottom();
            }
        }
        final int cards = cardsHeight >= 0 ? cardsHeight : buttonsHeight();
        // as it opens the cards are over the tabs, the map's row above them; pulled up, the map gets the whole sheet
        final int room = listView.getMeasuredHeight() - getDefaultBottomInset() - cards;
        final int newMapHeight = getMeasuredHeight() + getMapExtraTop();
        final int newOverScrollHeight = Math.max(AndroidUtilities.dp(120), room - listView.getPaddingTop());
        if (newMapHeight != mapHeight || newOverScrollHeight != overScrollHeight) {
            mapHeight = newMapHeight;
            overScrollHeight = newOverScrollHeight;
            AndroidUtilities.runOnUIThread(this::applyMapSpace);
        }
    }

    /** Under the cards: the tabs with a gap over them, as long as they are shown (see shouldHideBottomButtons). */
    private int getBottomInset() {
        final float hidden = hasTabs() && shouldHideBottomButtons() && parentAlert.actionBar != null ? parentAlert.actionBar.getAlpha() : 0;
        return (int) AndroidUtilities.lerp(getDefaultBottomInset(), getMinBottomInset(), hidden);
    }

    /** The picker of a story's or a business' location has no tabs. */
    private boolean hasTabs() {
        return parentAlert.buttonsRecyclerViewWrapper != null && parentAlert.buttonsRecyclerViewWrapper.getVisibility() != GONE;
    }

    private int getDefaultBottomInset() {
        return hasTabs() ? listPaddingBottom + AndroidUtilities.dp(8) : getMinBottomInset();
    }

    private int getMinBottomInset() {
        return hasTabs() && !shouldHideBottomButtons() ? getDefaultBottomInset() : Math.min(listPaddingBottom, AndroidUtilities.navigationBarHeight);
    }

    private float headerAlpha = -1;
    private final ViewTreeObserver.OnPreDrawListener headerAlphaListener = () -> {
        final float alpha = parentAlert.actionBar != null ? parentAlert.actionBar.getAlpha() : 0;
        if (alpha != headerAlpha) {
            headerAlpha = alpha;
            updateClipView();
        }
        return true;
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(headerAlphaListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnPreDrawListener(headerAlphaListener);
    }

    private void applyMapSpace() {
        if (mapView == null || mapViewClip == null) {
            return;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
        if (layoutParams != null && layoutParams.height != mapHeight) {
            layoutParams.height = mapHeight;
            mapViewClip.setLayoutParams(layoutParams);
        }
        layoutParams = (FrameLayout.LayoutParams) mapView.getView().getLayoutParams();
        if (layoutParams != null && layoutParams.height != getMapContentHeight()) {
            layoutParams.height = getMapContentHeight();
            mapView.getView().setLayoutParams(layoutParams);
        }
        adapter.setOverScrollHeight(overScrollHeight);
        // the row bound now, the adapter keeps only the last one it made
        final RecyclerView.ViewHolder first = listView.findViewHolderForAdapterPosition(0);
        if (first != null && first.itemView.getLayoutParams() != null && first.itemView.getLayoutParams().height != overScrollHeight) {
            first.itemView.getLayoutParams().height = overScrollHeight;
            first.itemView.requestLayout();
        }
        // and the last row, it fills the list after the others: the room to pull the sheet up stays as high as before
        layoutManager.setAdditionalHeight(0);
        listView.requestLayout();
        updateClipView();
    }

    private void updateClipView() {
        if (mapView == null || mapViewClip == null) {
            return;
        }
        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
        final int top = holder != null ? holder.itemView.getTop() : -mapViewClip.getMeasuredHeight();
        // the cards stay at the bottom of the sheet, over the tabs while they are there
        final int cards = cardsHeight >= 0 ? cardsHeight : buttonsHeight();
        cardsTop = listView.getMeasuredHeight() - getBottomInset() - cards;
        if (holder != null) {
            final int shift = cardsTop - holder.itemView.getBottom();
            for (int i = 0, count = listView.getChildCount(); i < count; i++) {
                final View child = listView.getChildAt(i);
                if (listView.getChildAdapterPosition(child) > 0) {
                    child.setTranslationY(shift);
                }
            }
        }
        positionMap();
    }

    /** How far above the layout the map may go: up to the top of the screen, under the status bar. */
    private int getMapExtraTop() {
        return AndroidUtilities.statusBarHeight + AndroidUtilities.dp(8);
    }

    @Override
    public void onSheetBackground(float top, float round) {
        if (sheetTop != top || sheetRound != round) {
            sheetTop = top;
            sheetRound = round;
            positionMap();
        }
    }

    /**
     * The map from the top of the sheet (to the top of the screen as it is pulled up there) down to the bottom of the
     * screen, under the cards and the tabs; its camera centered between the top of the sheet's content and the cards.
     */
    private void positionMap() {
        if (mapView == null || mapViewClip == null || listView == null) {
            return;
        }
        final RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
        final int rowTop = holder != null ? listView.getTop() + holder.itemView.getTop() : Integer.MAX_VALUE;
        if (holder == null || rowTop >= getMeasuredHeight()) {
            if (mapView.getView().getVisibility() == View.VISIBLE) {
                mapView.getView().setVisibility(INVISIBLE);
                mapViewClip.setVisibility(INVISIBLE);
            }
            return;
        }
        if (mapView.getView().getVisibility() == View.INVISIBLE) {
            mapView.getView().setVisibility(VISIBLE);
            mapViewClip.setVisibility(VISIBLE);
        }
        // as the sheet is round, the map starts under its edge; as the sheet squares at the top of the screen, the map
        // goes up to it, under the status bar
        float top = rowTop;
        if (sheetRound < 1f) {
            getLocationInWindow(windowLocation);
            top = Math.min(rowTop, AndroidUtilities.lerp(-windowLocation[1], rowTop, sheetRound));
        }
        top = Math.max(top, -getMapExtraTop());
        final int mapTop = (int) top;

        final int freeTop = listView.getTop() + Math.max(0, holder.itemView.getTop()) - mapTop;
        final int freeBottom = listView.getTop() + Math.min(cardsTop, listView.getMeasuredHeight()) - mapTop;
        mapFreeTop = freeTop + AndroidUtilities.dp(MAP_GRAB_HEIGHT);
        mapFreeBottom = freeBottom;

        clipSize = Math.max(0, mapHeight - (getMeasuredHeight() - mapTop));
        nonClipSize = 0;
        mapViewClip.setTranslationY(mapTop);
        mapViewClip.invalidate();

        mapTypeButton.setTranslationY(freeTop);
        locationButton.setTranslationY(-Math.max(0, mapHeight - freeBottom));

        // the marker is in the middle of the free part, from the top of the sheet's content to the cards; the map is
        // moved with it as a view, the camera is never moved for the sheet: it would lag behind the view
        final int listTop = listView.getTop();
        final int bottom = mapTop + freeBottom;
        final float centerMin = (listTop + bottom) / 2f;
        final float centerMax = Math.max(centerMin, (listTop + listView.getPaddingTop() + bottom) / 2f);
        final int travel = (int) Math.ceil(centerMax - centerMin);
        if (travel != mapTravel) {
            mapTravel = travel;
            AndroidUtilities.runOnUIThread(this::applyMapSpace);
        }
        final float center = Math.max(centerMin, Math.min(centerMax, (mapTop + freeTop + bottom) / 2f));
        // the camera's center in the map view, the map view up to the top of the screen at any center
        final int mapCenter = (int) Math.ceil(centerMax) + getMapExtraTop();
        final float mapViewTop = center - mapCenter;
        mapView.getView().setTranslationY(mapViewTop - mapTop);

        final int newMarkerTop = (int) (center - mapTop) - AndroidUtilities.dp(48);
        if (newMarkerTop != markerTop) {
            markerImageView.setTranslationY(markerTop = newMarkerTop);
        }

        // the padding keeps the camera at mapCenter, its bottom at the cards for the logo: the map doesn't move with it
        final int viewHeight = mapView.getView().getMeasuredHeight() > 0 ? mapView.getView().getMeasuredHeight() : getMapContentHeight();
        final int padBottom = Math.max(0, (int) (viewHeight - (bottom - mapViewTop)));
        final int padTop = 2 * mapCenter - viewHeight + padBottom;
        if (map != null && (padTop != mapPaddingTop || padBottom != mapPaddingBottom)) {
            final boolean first = mapPaddingTop < 0;
            // the center moved in the map view (the cards measured, the list's padding changed), and the view the other
            // way: the point at the center of the screen stays there, the camera is kept on it
            IMapsProvider.LatLng target = null;
            if (first) {
                target = getMapTarget();
            } else if (mapCenter != appliedMapCenter && map.getCameraPosition() != null) {
                target = map.getCameraPosition().target;
            }
            appliedMapCenter = mapCenter;
            mapPaddingTop = padTop;
            mapPaddingBottom = padBottom;
            map.setPadding(0, padTop + AndroidUtilities.dp(6), 0, padBottom + AndroidUtilities.dp(6));
            if (target != null) {
                map.moveCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLng(target));
            }
        }
    }

    private int appliedMapCenter;

    /** The map view: over the whole layout, and as much more as the marker travels with the sheet. */
    private int getMapContentHeight() {
        return mapHeight + mapTravel + AndroidUtilities.dp(10);
    }

    private int mapTravel;
    private int mapPaddingTop = -1, mapPaddingBottom = -1;

    /** The marker's location: picked by the user, or the user's own. */
    private IMapsProvider.LatLng getMapTarget() {
        if (userLocationMoved && userLocation != null) {
            return new IMapsProvider.LatLng(userLocation.getLatitude(), userLocation.getLongitude());
        } else if (myLocation != null) {
            return new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude());
        }
        return null;
    }

    private boolean isTypeSend() {
        return locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE;
    }

    private int buttonsHeight() {
        int buttonsHeight = AndroidUtilities.dp(66);
        if (locationType == LOCATION_TYPE_SEND_WITH_LIVE || locationType == LOCATION_TYPE_STORY || locationType == LOCATION_TYPE_BIZ)
            buttonsHeight += AndroidUtilities.dp(66);
        return buttonsHeight;
    }

    private void fixLayoutInternal(final boolean resume) {
        int viewHeight = getMeasuredHeight();
        if (viewHeight == 0 || mapView == null) {
            return;
        }
        int height = ActionBar.getCurrentActionBarHeight();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        layoutParams.topMargin = height;
        listView.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
        layoutParams.topMargin = 0;
        layoutParams.height = mapHeight;
        mapViewClip.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) searchListView.getLayoutParams();
        layoutParams.topMargin = height;
        searchListView.setLayoutParams(layoutParams);

        adapter.setOverScrollHeight(overScrollHeight);
        layoutParams = (FrameLayout.LayoutParams) mapView.getView().getLayoutParams();
        if (layoutParams != null) {
            layoutParams.height = mapHeight + AndroidUtilities.dp(10);
            mapView.getView().setLayoutParams(layoutParams);
        }

        adapter.notifyDataSetChanged();
        updateClipView();
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

    private void positionMarker() {
        if (parentAlert.isStoryLocationPicker) {
            if (parentAlert.storyLocationPickerLatLong != null) {
                AndroidUtilities.runOnUIThread(() -> resetMapPosition(parentAlert.storyLocationPickerLatLong[0], parentAlert.storyLocationPickerLatLong[1]));
            } else if (!locationDenied) {
                boolean reset = true;
                final File file = parentAlert.storyLocationPickerPhotoFile;
                final boolean isVideo = parentAlert.storyLocationPickerFileIsVideo;
                if (file != null) {
                    try {
                        if (isVideo) {
                            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                            mediaMetadataRetriever.setDataSource(file.getAbsolutePath());
                            String location = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
                            if (location != null) {
                                Matcher m = Pattern.compile("([+\\-][0-9.]+)([+\\-][0-9.]+)").matcher(location);
                                if (m.find() && m.groupCount() == 2) {
                                    String latstr = m.group(1);
                                    String lonstr = m.group(2);
                                    try {
                                        double lat = Double.parseDouble(latstr);
                                        double lon = Double.parseDouble(lonstr);
                                        AndroidUtilities.runOnUIThread(() -> resetMapPosition(lat, lon));
                                        reset = false;
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                            }
                        } else {
                            ExifInterface ei = new ExifInterface(file.getAbsolutePath());
                            float[] latlong = new float[2];
                            if (ei.getLatLong(latlong)) {
                                AndroidUtilities.runOnUIThread(() -> resetMapPosition(latlong[0], latlong[1]));
                                reset = false;
                            }
                        }
                    } catch (Exception e) {}
                }
                if (reset) {
                    positionMarker(myLocation = getLastLocation());
                }
            } else {
                AndroidUtilities.runOnUIThread(() -> resetMapPosition(0, 0));
            }
        } else {
            positionMarker(myLocation = getLastLocation());
        }
    }

    private void positionMarker(Location location) {
        if (location == null) {
            return;
        }
        myLocation = new Location(location);

        if (map != null) {
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
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.locationPermissionGranted) {
            locationDenied = false;
            askedForLocation = false;
            positionMarker();
            if (adapter != null) {
                adapter.setMyLocationDenied(locationDenied, askedForLocation);
            }
            if (searchAdapter != null) {
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
            askedForLocation = false;
            if (adapter != null) {
                adapter.setMyLocationDenied(locationDenied, askedForLocation);
            }
            if (searchAdapter != null) {
            }
        }
        fixLayoutInternal(true);
        searchItem.setVisibility(parentAlert.isBizLocationPicker ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        if (mapView != null && mapsInitialized) {
            try {
                mapView.onResume();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        onResumeCalled = true;
    }

    @Override
    public void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        parentAlert.actionBar.setTitle(LocaleController.getString(R.string.ShareLocation));
        if (mapView.getView().getParent() == null) {
            mapViewClip.addView(mapView.getView(), 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10), Gravity.TOP | Gravity.LEFT));
            mapViewClip.addView(loadingMapView, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }
        searchItem.setVisibility(VISIBLE);

        if (mapView != null && mapsInitialized) {
            try {
                mapView.onResume();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        onResumeCalled = true;
        if (map != null) {
            try {
                map.setMyLocationEnabled(true);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        fixLayoutInternal(true);
        boolean keyboardVisible = parentAlert.delegate.needEnterComment();
        AndroidUtilities.runOnUIThread(() -> {
            if (checkPermission && Build.VERSION.SDK_INT >= 23) {
                Activity activity = getParentActivity();
                if (activity != null) {
                    checkPermission = false;
                    if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        String[] permissions = parentAlert.isStoryLocationPicker && parentAlert.storyLocationPickerPhotoFile != null && Build.VERSION.SDK_INT >= 29 ?
                            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_MEDIA_LOCATION} :
                            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
                        askedForLocation = true;
                        if (adapter != null) {
                            adapter.setMyLocationDenied(locationDenied, askedForLocation);
                        }
                        activity.requestPermissions(permissions, BasePermissionsActivity.REQUEST_CODE_GEOLOCATION);
                    } else if (Build.VERSION.SDK_INT >= 29 && parentAlert.isStoryLocationPicker && parentAlert.storyLocationPickerPhotoFile != null && activity.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        askedForLocation = true;
                        if (adapter != null) {
                            adapter.setMyLocationDenied(locationDenied, askedForLocation);
                        }
                        activity.requestPermissions(new String[]{Manifest.permission.ACCESS_MEDIA_LOCATION}, BasePermissionsActivity.REQUEST_CODE_MEDIA_GEO);
                    }
                }
            }
        }, keyboardVisible ? 200 : 0);

        layoutManager.scrollToPositionWithOffset(0, 0);

        updateClipView();
    }

    public void setDelegate(LocationActivityDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::cellDelegateThemeColors;

        themeDescriptions.add(new ThemeDescription(mapViewClip, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));
        themeDescriptions.add(new ThemeDescription(searchItem != null ? searchItem.getSearchField() : null, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_dialogTextBlack));




        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_location_actionBackground));
        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_location_actionPressedBackground));

        themeDescriptions.add(new ThemeDescription(mapTypeButton, 0, null, null, null, cellDelegate, Theme.key_location_actionIcon));
        themeDescriptions.add(new ThemeDescription(mapTypeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_location_actionBackground));
        themeDescriptions.add(new ThemeDescription(mapTypeButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_location_actionPressedBackground));



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

    @Override
    public void onPanTransitionStart(boolean keyboardVisible, int contentHeight) {
        if (keyboardVisible) {
            adapter.animated = false;
        }
    }

    @Override
    public void onPanTransitionEnd() {
        adapter.animated = parentAlert != null && !parentAlert.isKeyboardVisible();
    }

    @Override
    public void updateColors() {
        try {
            cellDelegateThemeColors();
    
        } catch (Exception e) {
            FileLog.e(e);
        }
        updateSearchCursorColor();
    }

    private void updateSearchCursorColor() {
        try {
            if (searchItem != null && searchItem.getSearchField() != null) {
                searchItem.getSearchField().setCursorColor(getThemedColor(Theme.key_dialogTextBlack));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void cellDelegateThemeColors() {
        if (mapTypeButton != null) {
            mapTypeButton.setIconColor(getThemedColor(Theme.key_location_actionIcon));
            mapTypeButton.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
            mapTypeButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), true);
            mapTypeButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), false);
        }

        if (map != null) {
            if (isActiveThemeDark()) {
                if (!currentMapStyleDark) {
                    currentMapStyleDark = true;
                    IMapsProvider.IMapStyleOptions style = ApplicationLoader.getMapsProvider().loadRawResourceStyle(ApplicationLoader.applicationContext, R.raw.mapstyle_night);
                    map.setMapStyle(style);
                }
            } else {
                if (currentMapStyleDark) {
                    currentMapStyleDark = false;
                    map.setMapStyle(null);
                }
            }
        }
    }

    /** theme editor: colors this view shows (former ThemeDescription keys) */
    @Override
    public void addColorKeys(HashSet<Integer> colors) {
        colors.add(Theme.key_windowBackgroundWhiteGrayText3);
        colors.add(Theme.key_windowBackgroundWhiteBlackText);
        colors.add(Theme.key_dialogTextBlue2);
        colors.add(Theme.key_windowBackgroundGray);
        colors.add(Theme.key_windowBackgroundGrayShadow);
        colors.add(Theme.key_featuredStickers_addButton);
        colors.add(Theme.key_featuredStickers_buttonText);
        colors.add(Theme.key_location_sendLocationText);
        colors.add(Theme.key_location_sendLiveLocationText);
        colors.add(Theme.key_avatar_backgroundPink);
        colors.add(Theme.key_avatar_backgroundBlue);
        colors.add(Theme.key_avatar_backgroundCyan);
        colors.add(Theme.key_avatar_backgroundGreen);
        colors.add(Theme.key_avatar_backgroundViolet);
        colors.add(Theme.key_avatar_backgroundOrange);
        colors.add(Theme.key_avatar_backgroundRed);
        colors.add(Theme.key_avatar_text);
        colors.add(Theme.key_divider);
        colors.add(Theme.key_listSelector);
    }
}
