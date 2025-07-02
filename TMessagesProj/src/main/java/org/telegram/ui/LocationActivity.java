/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.LocationActivityAdapter;
import org.telegram.ui.Adapters.LocationActivitySearchAdapter;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.LocationDirectionCell;
import org.telegram.ui.Cells.LocationLoadingCell;
import org.telegram.ui.Cells.LocationPoweredCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SharingLiveLocationCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatAttachAlertLocationLayout;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MapPlaceholderDrawable;
import org.telegram.ui.Components.ProximitySheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class LocationActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ImageView locationButton;
    private ImageView proximityButton;
    private ActionBarMenuItem mapTypeButton;
    private SearchButton searchAreaButton;
    private LinearLayout emptyView;
    private ImageView emptyImageView;
    private TextView emptyTitleTextView;
    private TextView emptySubtitleTextView;
    private Drawable shadowDrawable;
    private View shadow;
    private ActionBarMenuItem searchItem;
    private MapOverlayView overlayView;
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

    private ArrayList<VenueLocation> placeMarkers = new ArrayList<>();

    private AnimatorSet animatorSet;

    private IMapsProvider.IMarker lastPressedMarker;
    private VenueLocation lastPressedVenue;
    private FrameLayout lastPressedMarkerView;

    private boolean checkPermission = true;
    private boolean checkBackgroundPermission = true;
    private int askWithRadius;

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
    private boolean searchedForCustomLocations;
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

    public static class VenueLocation {
        public int num;
        public IMapsProvider.IMarker marker;
        public TLRPC.TL_messageMediaVenue venue;
    }

    public static class LiveLocation {
        public long id;
        public TLRPC.Message object;
        public TLRPC.User user;
        public TLRPC.Chat chat;
        public IMapsProvider.IMarker marker;
        public IMapsProvider.IMarker directionMarker;
        public boolean hasRotation;
    }

    private static class SearchButton extends TextView {

        private float additionanTranslationY;
        private float currentTranslationY;

        public SearchButton(Context context) {
            super(context);
        }

        @Override
        public float getTranslationX() {
            return additionanTranslationY;
        }

        @Override
        public void setTranslationX(float translationX) {
            additionanTranslationY = translationX;
            updateTranslationY();
        }

        public void setTranslation(float value) {
            currentTranslationY = value;
            updateTranslationY();
        }

        private void updateTranslationY() {
            setTranslationY(currentTranslationY + additionanTranslationY);
        }
    }

    public class MapOverlayView extends FrameLayout {

        private HashMap<IMapsProvider.IMarker, View> views = new HashMap<>();

        public MapOverlayView(Context context) {
            super(context);
        }

        public void addInfoView(IMapsProvider.IMarker marker) {
            VenueLocation location = (VenueLocation) marker.getTag();
            if (location == null || lastPressedVenue == location) {
                return;
            }
            showSearchPlacesButton(false);
            if (lastPressedMarker != null) {
                removeInfoView(lastPressedMarker);
                lastPressedMarker = null;
            }
            lastPressedVenue = location;
            lastPressedMarker = marker;

            Context context = getContext();

            FrameLayout frameLayout = new FrameLayout(context);
            addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 114));

            lastPressedMarkerView = new FrameLayout(context);
            lastPressedMarkerView.setBackgroundResource(R.drawable.venue_tooltip);
            lastPressedMarkerView.getBackground().setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
            frameLayout.addView(lastPressedMarkerView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 71));
            lastPressedMarkerView.setAlpha(0.0f);
            lastPressedMarkerView.setOnClickListener(v -> {
                if (parentFragment != null && parentFragment.isInScheduleMode()) {
                    AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), parentFragment.getDialogId(), (notify, scheduleDate) -> {
                        delegate.didSelectLocation(location.venue, locationType, notify, scheduleDate, 0);
                        finishFragment();
                    });
                } else {
                    delegate.didSelectLocation(location.venue, locationType, true, 0, 0);
                    finishFragment();
                }
            });

            TextView nameTextView = new TextView(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameTextView.setMaxLines(1);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setSingleLine(true);
            nameTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setTypeface(AndroidUtilities.bold());
            nameTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            lastPressedMarkerView.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 18, 10, 18, 0));

            TextView addressTextView = new TextView(context);
            addressTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addressTextView.setMaxLines(1);
            addressTextView.setEllipsize(TextUtils.TruncateAt.END);
            addressTextView.setSingleLine(true);
            addressTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
            addressTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            lastPressedMarkerView.addView(addressTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 18, 32, 18, 0));

            nameTextView.setText(location.venue.title);
            addressTextView.setText(LocaleController.getString(R.string.TapToSendLocation));

            FrameLayout iconLayout = new FrameLayout(context);
            iconLayout.setBackground(Theme.createCircleDrawable(dp(36), LocationCell.getColorForIndex(location.num)));
            frameLayout.addView(iconLayout, LayoutHelper.createFrame(36, 36, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 4));

            BackupImageView imageView = new BackupImageView(context);
            imageView.setImage("https://ss3.4sqi.net/img/categories_v2/" + location.venue.venue_type + "_64.png", null, null);
            iconLayout.addView(imageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER));

            ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 1.0f);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                private boolean startedInner;
                private final float[] animatorValues = new float[]{0.0f, 1.0f};

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float value = AndroidUtilities.lerp(animatorValues, animation.getAnimatedFraction());
                    if (value >= 0.7f && !startedInner && lastPressedMarkerView != null) {
                        AnimatorSet animatorSet1 = new AnimatorSet();
                        animatorSet1.playTogether(
                                ObjectAnimator.ofFloat(lastPressedMarkerView, View.SCALE_X, 0.0f, 1.0f),
                                ObjectAnimator.ofFloat(lastPressedMarkerView, View.SCALE_Y, 0.0f, 1.0f),
                                ObjectAnimator.ofFloat(lastPressedMarkerView, View.ALPHA, 0.0f, 1.0f));
                        animatorSet1.setInterpolator(new OvershootInterpolator(1.02f));
                        animatorSet1.setDuration(250);
                        animatorSet1.start();
                        startedInner = true;
                    }
                    float scale;
                    if (value <= 0.5f) {
                        scale = 1.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(value / 0.5f);
                    } else if (value <= 0.75f) {
                        value -= 0.5f;
                        scale = 1.1f - 0.2f * CubicBezierInterpolator.EASE_OUT.getInterpolation(value / 0.25f);
                    } else {
                        value -= 0.75f;
                        scale = 0.9f + 0.1f * CubicBezierInterpolator.EASE_OUT.getInterpolation(value / 0.25f);
                    }
                    iconLayout.setScaleX(scale);
                    iconLayout.setScaleY(scale);
                }
            });
            animator.setDuration(360);
            animator.start();

            views.put(marker, frameLayout);

            map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLng(marker.getPosition()), 300, null);
        }

        public void removeInfoView(IMapsProvider.IMarker marker) {
            View view = views.get(marker);
            if (view != null) {
                removeView(view);
                views.remove(marker);
            }
        }

        public void updatePositions() {
            if (map == null) {
                return;
            }
            IMapsProvider.IProjection projection = map.getProjection();
            for (HashMap.Entry<IMapsProvider.IMarker, View> entry : views.entrySet()) {
                IMapsProvider.IMarker marker = entry.getKey();
                View view = entry.getValue();
                Point point = projection.toScreenLocation(marker.getPosition());
                view.setTranslationX(point.x - view.getMeasuredWidth() / 2);
                view.setTranslationY(point.y - view.getMeasuredHeight() + dp(22));
            }
        }
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
        getNotificationCenter().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.locationPermissionDenied);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.liveLocationsChanged);
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

        actionBar.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        actionBar.setTitleColor(getThemedColor(Theme.key_dialogTextBlack));
        actionBar.setItemsColor(getThemedColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_dialogButtonSelector), false);
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
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        if (chatLocation != null) {
            actionBar.setTitle(LocaleController.getString(R.string.ChatLocation));
        } else if (messageObject != null) {
            if (messageObject.isLiveLocation()) {
                actionBar.setTitle(LocaleController.getString(R.string.AttachLiveLocation));
                otherItem = menu.addItem(0, R.drawable.ic_ab_other, getResourceProvider());
                otherItem.addSubItem(get_directions, R.drawable.filled_directions, LocaleController.getString(R.string.GetDirections));
            } else {
                if (messageObject.messageOwner.media.title != null && messageObject.messageOwner.media.title.length() > 0) {
                    actionBar.setTitle(LocaleController.getString(R.string.SharedPlace));
                } else {
                    actionBar.setTitle(LocaleController.getString(R.string.ChatLocation));
                }
                if (locationType != 3) {
                    otherItem = menu.addItem(0, R.drawable.ic_ab_other, getResourceProvider());
                    otherItem.addSubItem(open_in, R.drawable.msg_openin, LocaleController.getString(R.string.OpenInExternalApp));
                    if (!getLocationController().isSharingLocation(dialogId) && isSharingAllowed) {
                        otherItem.addSubItem(share_live_location, R.drawable.msg_location, LocaleController.getString(R.string.SendLiveLocationMenu));
                    }
                    otherItem.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
                }
            }
        } else {
            actionBar.setTitle(LocaleController.getString(R.string.ShareLocation));

            if (locationType != LOCATION_TYPE_GROUP) {
                overlayView = new MapOverlayView(context);

                searchItem = menu.addItem(0, R.drawable.ic_ab_search, getResourceProvider()).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override
                    public void onSearchExpand() {
                        searching = true;
                    }

                    @Override
                    public void onSearchCollapse() {
                        searching = false;
                        searchWas = false;
                        searchAdapter.searchDelayed(null, null);
                        updateEmptyView();
                        if (locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ) {
                            if (otherItem != null) {
                                otherItem.setVisibility(View.VISIBLE);
                            }
                            listView.setVisibility(View.VISIBLE);
                            mapViewClip.setVisibility(View.VISIBLE);
                            searchListView.setAdapter(null);
                            searchListView.setVisibility(View.GONE);
                        }
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
                        searchAdapter.searchDelayed(text, userLocation);
                    }
                });
                searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));
                searchItem.setContentDescription(LocaleController.getString(R.string.Search));
                EditTextBoldCursor editText = searchItem.getSearchField();
                editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
                editText.setCursorColor(getThemedColor(Theme.key_dialogTextBlack));
                editText.setHintTextColor(getThemedColor(Theme.key_chat_messagePanelHint));
            }
        }

        fragmentView = new NestedFrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        fragmentView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
        Rect padding = new Rect();
        shadowDrawable.getPadding(padding);

        FrameLayout.LayoutParams layoutParams;
        if (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE) {
            layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(21) + padding.top);
        } else {
            layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6) + padding.top);
        }
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;

        mapViewClip = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (overlayView != null) {
                    overlayView.updatePositions();
                }
            }
        };
        mapViewClip.setBackgroundDrawable(new MapPlaceholderDrawable(isActiveThemeDark()));

        if (messageObject == null && (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE) || messageObject != null && locationType == 3) {
            searchAreaButton = new SearchButton(context);
            searchAreaButton.setTranslationX(-dp(80));
            Drawable drawable = Theme.createSimpleSelectorRoundRectDrawable(dp(40), getThemedColor(Theme.key_location_actionBackground), getThemedColor(Theme.key_location_actionPressedBackground));
            if (Build.VERSION.SDK_INT < 21) {
                Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.places_btn).mutate();
                shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, dp(2), dp(2));
                combinedDrawable.setFullsize(true);
                drawable = combinedDrawable;
            } else {
                StateListAnimator animator = new StateListAnimator();
                animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(searchAreaButton, View.TRANSLATION_Z, dp(2), dp(4)).setDuration(200));
                animator.addState(new int[]{}, ObjectAnimator.ofFloat(searchAreaButton, View.TRANSLATION_Z, dp(4), dp(2)).setDuration(200));
                searchAreaButton.setStateListAnimator(animator);
                searchAreaButton.setOutlineProvider(new ViewOutlineProvider() {
                    @SuppressLint("NewApi")
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), view.getMeasuredHeight() / 2);
                    }
                });
            }
            searchAreaButton.setBackgroundDrawable(drawable);
            searchAreaButton.setTextColor(getThemedColor(Theme.key_location_actionActiveIcon));
            searchAreaButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            searchAreaButton.setTypeface(AndroidUtilities.bold());
            searchAreaButton.setGravity(Gravity.CENTER);
            searchAreaButton.setPadding(dp(20), 0, dp(20), 0);
            mapViewClip.addView(searchAreaButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, Build.VERSION.SDK_INT >= 21 ? 40 : 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 80, 12, 80, 0));
            if (locationType == 3) {
                searchAreaButton.setText(LocaleController.getString(R.string.OpenInMaps));
                searchAreaButton.setOnClickListener(v -> {
                    try {
                        double lat = messageObject.messageOwner.media.geo.lat;
                        double lon = messageObject.messageOwner.media.geo._long;
                        getParentActivity().startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon)));
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
                searchAreaButton.setTranslationX(0);
            } else {
                searchAreaButton.setText(LocaleController.getString(R.string.PlacesInThisArea));
                searchAreaButton.setOnClickListener(v -> {
                    showSearchPlacesButton(false);
                    adapter.searchPlacesWithQuery(null, userLocation, true, true);
                    searchedForCustomLocations = true;
                    showResults();
                });
            }
        }

        mapTypeButton = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_location_actionIcon), getResourceProvider());
        mapTypeButton.setClickable(true);
        mapTypeButton.setSubMenuOpenSide(2);
        mapTypeButton.setAdditionalXOffset(dp(10));
        mapTypeButton.setAdditionalYOffset(-dp(10));
        mapTypeButton.addSubItem(map_list_menu_map, R.drawable.msg_map, LocaleController.getString(R.string.Map), getResourceProvider());
        mapTypeButton.addSubItem(map_list_menu_satellite, R.drawable.msg_satellite, LocaleController.getString(R.string.Satellite), getResourceProvider());
        mapTypeButton.addSubItem(map_list_menu_hybrid, R.drawable.msg_hybrid, LocaleController.getString(R.string.Hybrid), getResourceProvider());
        mapTypeButton.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(dp(40), getThemedColor(Theme.key_location_actionBackground), getThemedColor(Theme.key_location_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(dp(40), dp(40));
            drawable = combinedDrawable;
        } else {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(mapTypeButton, View.TRANSLATION_Z, dp(2), dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(mapTypeButton, View.TRANSLATION_Z, dp(4), dp(2)).setDuration(200));
            mapTypeButton.setStateListAnimator(animator);
            mapTypeButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, dp(40), dp(40));
                }
            });
        }
        mapTypeButton.setBackgroundDrawable(drawable);
        mapTypeButton.setIcon(R.drawable.msg_map_type);
        mapViewClip.addView(mapTypeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 40 : 44, Build.VERSION.SDK_INT >= 21 ? 40 : 44, Gravity.RIGHT | Gravity.TOP, 0, 12, 12, 0));
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
        drawable = Theme.createSimpleSelectorCircleDrawable(dp(40), getThemedColor(Theme.key_location_actionBackground), getThemedColor(Theme.key_location_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(dp(40), dp(40));
            drawable = combinedDrawable;
        } else {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(locationButton, View.TRANSLATION_Z, dp(2), dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(locationButton, View.TRANSLATION_Z, dp(4), dp(2)).setDuration(200));
            locationButton.setStateListAnimator(animator);
            locationButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, dp(40), dp(40));
                }
            });
        }
        locationButton.setBackgroundDrawable(drawable);
        locationButton.setImageResource(R.drawable.msg_current_location);
        locationButton.setScaleType(ImageView.ScaleType.CENTER);
        locationButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionActiveIcon), PorterDuff.Mode.MULTIPLY));
        locationButton.setTag(Theme.key_location_actionActiveIcon);
        locationButton.setContentDescription(LocaleController.getString(R.string.AccDescrMyLocation));
        FrameLayout.LayoutParams layoutParams1 = LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 40 : 44, Build.VERSION.SDK_INT >= 21 ? 40 : 44, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 12, 12);
        layoutParams1.bottomMargin += layoutParams.height - padding.top;
        mapViewClip.addView(locationButton, layoutParams1);
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
                    locationButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionActiveIcon), PorterDuff.Mode.MULTIPLY));
                    locationButton.setTag(Theme.key_location_actionActiveIcon);
                    adapter.setCustomLocation(null);
                    userLocationMoved = false;
                    showSearchPlacesButton(false);
                    map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLng(new IMapsProvider.LatLng(myLocation.getLatitude(), myLocation.getLongitude())));
                    if (searchedForCustomLocations && locationType != ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ) {
                        if (myLocation != null) {
                            adapter.searchPlacesWithQuery(null, myLocation, true, true);
                        }
                        searchedForCustomLocations = false;
                        showResults();
                    }
                }
            }
            removeInfoView();
        });

        proximityButton = new ImageView(context);
        drawable = Theme.createSimpleSelectorCircleDrawable(dp(40), getThemedColor(Theme.key_location_actionBackground), getThemedColor(Theme.key_location_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(dp(40), dp(40));
            drawable = combinedDrawable;
        } else {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(proximityButton, View.TRANSLATION_Z, dp(2), dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(proximityButton, View.TRANSLATION_Z, dp(4), dp(2)).setDuration(200));
            proximityButton.setStateListAnimator(animator);
            proximityButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, dp(40), dp(40));
                }
            });
        }
        proximityButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionIcon), PorterDuff.Mode.MULTIPLY));
        proximityButton.setBackgroundDrawable(drawable);
        proximityButton.setScaleType(ImageView.ScaleType.CENTER);
        proximityButton.setContentDescription(LocaleController.getString(R.string.AccDescrLocationNotify));
        mapViewClip.addView(proximityButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 40 : 44, Build.VERSION.SDK_INT >= 21 ? 40 : 44, Gravity.RIGHT | Gravity.TOP, 0, 12 + 50, 12, 0));
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
        hintView.setJoint(1, -(12 + 13));
        hintView.setPadding(0, dp(4), 0, 0);
        mapViewClip.addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 8, 12 + 50 + 44, 8, 0));

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyView.setPadding(0, dp(60 + 100), 0, 0);
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setOnTouchListener((v, event) -> true);

        emptyImageView = new ImageView(context);
        emptyImageView.setImageResource(R.drawable.location_empty);
        emptyImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogEmptyImage), PorterDuff.Mode.MULTIPLY));
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTitleTextView = new TextView(context);
        emptyTitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptyTitleTextView.setGravity(Gravity.CENTER);
        emptyTitleTextView.setTypeface(AndroidUtilities.bold());
        emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleTextView.setText(LocaleController.getString(R.string.NoPlacesFound));
        emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

        emptySubtitleTextView = new TextView(context);
        emptySubtitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptySubtitleTextView.setGravity(Gravity.CENTER);
        emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptySubtitleTextView.setPadding(dp(40), 0, dp(40), 0);
        emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));

        listView = new RecyclerListView(context);
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
            sharedMediaLayout = new SharedMediaLayout(context, 0, new SharedMediaLayout.SharedMediaPreloader(this), 0, null, null, null, SharedMediaLayout.TAB_STORIES, this, new SharedMediaLayout.Delegate() {
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
            sharedMediaLayout.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
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
                    cell.setTextAndIcon(LocaleController.getString(R.string.GetDirections), R.drawable.filled_directions);
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
                    if (lastPressedMarkerView != null) {
                        lastPressedMarkerView.callOnClick();
                    } else {
                        TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                        location.geo = new TLRPC.TL_geoPoint();
                        location.geo.lat = AndroidUtilities.fixLocationCoord(userLocation.getLatitude());
                        location.geo._long = AndroidUtilities.fixLocationCoord(userLocation.getLongitude());
                        if (parentFragment != null && parentFragment.isInScheduleMode()) {
                            AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), parentFragment.getDialogId(), (notify, scheduleDate) -> {
                                delegate.didSelectLocation(location, locationType, notify, scheduleDate, 0);
                                finishFragment();
                            });
                        } else {
                            delegate.didSelectLocation(location, locationType, true, 0, 0);
                            finishFragment();
                        }
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
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), parentFragment.getDialogId(), (notify, scheduleDate) -> {
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
                    map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(liveLocation.marker.getPosition(), map.getMaxZoomLevel() - 4));
                }
            }
        });
        adapter.setDelegate(dialogId, this::updatePlacesMarkers);
        adapter.setOverScrollHeight(overScrollHeight);

        frameLayout.addView(mapViewClip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        mapView = ApplicationLoader.getMapsProvider().onCreateMapView(context);
        mapView.getView().setAlpha(0f);
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
                        locationButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionIcon), PorterDuff.Mode.MULTIPLY));
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
                            this.map.setPadding(dp(70), 0, dp(70), dp(10));
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
                        emptySubtitleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("NoPlacesFoundInfo", R.string.NoPlacesFoundInfo, searchAdapter.getLastSearchString())));
                    }
                    super.notifyDataSetChanged();
                }
            };
            searchAdapter.setDelegate(0, places -> {
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
                if (object != null && object.icon != null && locationType == ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ && this.map != null) {
                    userLocationMoved = true;
                    menu.closeSearchField(true);
                    final float zoom = "pin".equals(object.icon) ? this.map.getMaxZoomLevel() - 4 : this.map.getMaxZoomLevel() - 9;
                    this.map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(new IMapsProvider.LatLng(object.geo.lat, object.geo._long), zoom));
                    if (userLocation != null) {
                        userLocation.setLatitude(object.geo.lat);
                        userLocation.setLongitude(object.geo._long);
                    }
                    adapter.setCustomLocation(userLocation);
                } else if (object != null && delegate != null) {
                    if (parentFragment != null && parentFragment.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), parentFragment.getDialogId(), (notify, scheduleDate) -> {
                            delegate.didSelectLocation(object, locationType, notify, scheduleDate, 0);
                            finishFragment();
                        });
                    } else {
                        delegate.didSelectLocation(object, locationType, true, 0, 0);
                        finishFragment();
                    }
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
            if (Build.VERSION.SDK_INT >= 21) {
                undoView[a].setTranslationZ(dp(5));
            }
            mapViewClip.addView(undoView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        }

        shadow = new View(context) {

            private RectF rect = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                shadowDrawable.setBounds(-padding.left, 0, getMeasuredWidth() + padding.right, getMeasuredHeight());
                shadowDrawable.draw(canvas);

                if (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE) {
                    int w = dp(36);
                    int y = padding.top + dp(10);
                    rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + dp(4));
                    int color = getThemedColor(Theme.key_sheet_scrollUp);
                    int alpha = Color.alpha(color);
                    Theme.dialogs_onlineCirclePaint.setColor(color);
                    canvas.drawRoundRect(rect, dp(2), dp(2), Theme.dialogs_onlineCirclePaint);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 21) {
            shadow.setTranslationZ(dp(6));
        }
        mapViewClip.addView(shadow, layoutParams);

        if (messageObject == null && chatLocation == null && initialLocation != null) {
            userLocationMoved = true;
            locationButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionIcon), PorterDuff.Mode.MULTIPLY));
            locationButton.setTag(Theme.key_location_actionIcon);
        }

        frameLayout.addView(actionBar);
        updateEmptyView();

        return fragmentView;
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

    private void showSearchPlacesButton(boolean show) {
        if (locationType == 3) {
            show = true;
        }
        if (show && searchAreaButton != null && searchAreaButton.getTag() == null) {
            if (myLocation == null || userLocation == null || userLocation.distanceTo(myLocation) < 300) {
                show = false;
            }
        }
        if (searchAreaButton == null || show && searchAreaButton.getTag() != null || !show && searchAreaButton.getTag() == null) {
            return;
        }
        searchAreaButton.setTag(show ? 1 : null);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(searchAreaButton, View.TRANSLATION_X, show ? 0 : -dp(80)));
        animatorSet.setDuration(180);
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animatorSet.start();
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

            if (photo != null) {
                File path = ImageReceiver.getAvatarLocalFile(currentAccount, liveLocation.user != null ? liveLocation.user : liveLocation.chat);
                Bitmap bitmap = BitmapFactory.decodeFile(path.toString());
                if (bitmap != null) {
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
                builder.setTitle(LocaleController.getString(R.string.ShareLocationAlertTitle));
                builder.setMessage(LocaleController.getString(R.string.ShareLocationAlertText));
                builder.setPositiveButton(LocaleController.getString(R.string.ShareLocationAlertButton), (dialog, id) -> shareLiveLocation(user, 900, radius));
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
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
                map.setPadding(dp(70), 0, dp(70), dp(10));
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
        if (checkBackgroundPermission && Build.VERSION.SDK_INT >= 29) {
            Activity activity = getParentActivity();
            if (activity != null) {
                askWithRadius = proximityRadius;
                checkBackgroundPermission = false;
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                int lastTime = preferences.getInt("backgroundloc", 0);
                if (Math.abs(System.currentTimeMillis() / 1000 - lastTime) > 24 * 60 * 60 && activity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    preferences.edit().putInt("backgroundloc", (int) (System.currentTimeMillis() / 1000)).commit();
                    AlertsCreator.createBackgroundLocationPermissionDialog(activity, getMessagesController().getUser(getUserConfig().getClientUserId()), () -> openShareLiveLocation(expand, askWithRadius), null).show();
                    return;
                }
            }
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

    private Bitmap[] bitmapCache = new Bitmap[7];
    private Bitmap createPlaceBitmap(int num) {
        if (bitmapCache[num % 7] != null) {
            return bitmapCache[num % 7];
        }
        try {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xffffffff);
            Bitmap bitmap = Bitmap.createBitmap(dp(12), dp(12), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawCircle(dp(6), dp(6), dp(6), paint);
            paint.setColor(LocationCell.getColorForIndex(num));
            canvas.drawCircle(dp(6), dp(6), dp(5), paint);
            canvas.setBitmap(null);
            return bitmapCache[num % 7] = bitmap;
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    private void updatePlacesMarkers(ArrayList<TLRPC.TL_messageMediaVenue> places) {
        if (places == null) {
            return;
        }
        for (int a = 0, N = placeMarkers.size(); a < N; a++) {
            placeMarkers.get(a).marker.remove();
        }
        placeMarkers.clear();
        for (int a = 0, N = places.size(); a < N; a++) {
            TLRPC.TL_messageMediaVenue venue = places.get(a);
            try {
                IMapsProvider.IMarkerOptions options = ApplicationLoader.getMapsProvider().onCreateMarkerOptions().position(new IMapsProvider.LatLng(venue.geo.lat, venue.geo._long));
                options.icon(createPlaceBitmap(a));
                options.anchor(0.5f, 0.5f);
                options.title(venue.title);
                options.snippet(venue.address);
                VenueLocation venueLocation = new VenueLocation();
                venueLocation.num = a;
                venueLocation.marker = map.addMarker(options);
                venueLocation.venue = venue;
                venueLocation.marker.setTag(venueLocation);
                placeMarkers.add(venueLocation);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
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

        mapView.getView().animate().alpha(1).setStartDelay(200).setDuration(100).start();

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
                showSearchPlacesButton(true);
                removeInfoView();

                selectedMarkerId = -1;

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
        map.setOnMarkerClickListener(marker -> {
            if (!(marker.getTag() instanceof VenueLocation)) {
                return true;
            }
            markerImageView.setVisibility(View.INVISIBLE);
            if (!userLocationMoved) {
                locationButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionIcon), PorterDuff.Mode.MULTIPLY));
                locationButton.setTag(Theme.key_location_actionIcon);
                userLocationMoved = true;
            }
            for (int i = 0; i < markers.size(); ++i) {
                LiveLocation loc = markers.get(i);
                if (loc != null && loc.marker == marker) {
                    selectedMarkerId = loc.id;
                    map.animateCamera(ApplicationLoader.getMapsProvider().newCameraUpdateLatLngZoom(loc.marker.getPosition(), zoom));
                    break;
                }
            }
            overlayView.addInfoView(marker);
            return true;
        });
        map.setOnCameraMoveListener(() -> {
            if (overlayView != null) {
                overlayView.updatePositions();
            }
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

    private void removeInfoView() {
        if (lastPressedMarker != null) {
            markerImageView.setVisibility(View.VISIBLE);
            overlayView.removeInfoView(lastPressedMarker);
            lastPressedMarker = null;
            lastPressedVenue = null;
            lastPressedMarkerView = null;
        }
    }

    private void showPermissionAlert(boolean byButton) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTopAnimation(R.raw.permission_request_location, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, getThemedColor(Theme.key_dialogTopBackground));
        if (byButton) {
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PermissionNoLocationNavigation)));
        } else {
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PermissionNoLocationFriends)));
        }
        builder.setNegativeButton(LocaleController.getString(R.string.PermissionOpenSettings), (dialog, which) -> {
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
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
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
                mapViewClip.addView(mapView.getView(), 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + dp(10), Gravity.TOP | Gravity.LEFT));
                if (overlayView != null) {
                    try {
                        if (overlayView.getParent() instanceof ViewGroup) {
                            ViewGroup viewGroup = (ViewGroup) overlayView.getParent();
                            viewGroup.removeView(overlayView);
                        }
                    } catch (Exception ignore) {

                    }
                    mapViewClip.addView(overlayView, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + dp(10), Gravity.TOP | Gravity.LEFT));
                }
                updateClipView(false);
                maybeShowProximityHint();
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
                hintView.setText(LocaleController.getString(R.string.ProximityTooltioGroup));
            }
            hintView.show();
        }
    }

    private void showResults() {
        if (adapter.getItemCount() == 0) {
            return;
        }
        int position = layoutManager.findFirstVisibleItemPosition();
        if (position != 0) {
            return;
        }
        View child = listView.getChildAt(0);
        int offset = dp(258) + child.getTop();
        if (offset < 0 || offset > dp(258)) {
            return;
        }
        listView.smoothScrollBy(0, offset);
    }

    private void updateClipView(boolean fromLayout) {
        int height = 0;
        int top;
        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
        if (holder != null) {
            top = (int) holder.itemView.getY();
            height = overScrollHeight + (Math.min(top, 0));
        } else {
            top = -mapViewClip.getMeasuredHeight();
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
        if (layoutParams != null) {
            if (height <= 0) {
                if (mapView.getView().getVisibility() == View.VISIBLE) {
                    mapView.getView().setVisibility(View.INVISIBLE);
                    mapViewClip.setVisibility(View.INVISIBLE);
                    if (overlayView != null) {
                        overlayView.setVisibility(View.INVISIBLE);
                    }
                }
            } else {
                if (mapView.getView().getVisibility() == View.INVISIBLE) {
                    mapView.getView().setVisibility(View.VISIBLE);
                    mapViewClip.setVisibility(View.VISIBLE);
                    if (overlayView != null) {
                        overlayView.setVisibility(View.VISIBLE);
                    }
                }
            }

            mapViewClip.setTranslationY(Math.min(0, top));
            mapView.getView().setTranslationY(Math.max(0, -top / 2));
            if (overlayView != null) {
                overlayView.setTranslationY(Math.max(0, -top / 2));
            }
            float translationY = Math.min(overScrollHeight - mapTypeButton.getMeasuredHeight() - dp(64 + (locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE ? 30 : 10)), -top);
            mapTypeButton.setTranslationY(translationY);
            proximityButton.setTranslationY(translationY);
            if (hintView != null) {
                hintView.setTranslationY(translationY);
            }
            if (searchAreaButton != null) {
                searchAreaButton.setTranslation(translationY);
            }
            if (markerImageView != null) {
                markerImageView.setTranslationY(markerTop = -top - dp(markerImageView.getTag() == null ? 48 : 69) + height / 2);
            }
            if (!fromLayout) {
                layoutParams = (FrameLayout.LayoutParams) mapView.getView().getLayoutParams();
                if (layoutParams != null && layoutParams.height != overScrollHeight + dp(10)) {
                    layoutParams.height = overScrollHeight + dp(10);
                    if (map != null) {
                        map.setPadding(dp(70), 0, dp(70), dp(10));
                    }
                    mapView.getView().setLayoutParams(layoutParams);
                }
                if (overlayView != null) {
                    layoutParams = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
                    if (layoutParams != null && layoutParams.height != overScrollHeight + dp(10)) {
                        layoutParams.height = overScrollHeight + dp(10);
                        overlayView.setLayoutParams(layoutParams);
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
            layoutParams = (FrameLayout.LayoutParams) mapView.getView().getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = overScrollHeight + dp(10);
                if (map != null) {
                    map.setPadding(dp(70), 0, dp(70), dp(10));
                }
                mapView.getView().setLayoutParams(layoutParams);
            }
            if (overlayView != null) {
                layoutParams = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.height = overScrollHeight + dp(10);
                    overlayView.setLayoutParams(layoutParams);
                }
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
                if (!searchedForCustomLocations && locationType != LOCATION_TYPE_GROUP && locationType != ChatAttachAlertLocationLayout.LOCATION_TYPE_BIZ) {
                    adapter.searchPlacesWithQuery(null, myLocation, true);
                }
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
                    int height = (int) (proximitySheet.getCustomView().getMeasuredHeight() - dp(40) + mapViewClip.getTranslationY());
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
                    int height = proximitySheet.getCustomView().getMeasuredHeight() - dp(100);
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
    }

    @Override
    public boolean onBackPressed() {
        if (proximitySheet != null) {
            proximitySheet.dismiss();
            return false;
        }
        if (onCheckGlScreenshot()) {
            return false;
        }

        return super.onBackPressed();
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
            GLSurfaceView glSurfaceView = mapView.getGlSurfaceView();
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
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 30) {
            openShareLiveLocation(false, askWithRadius);
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
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            mapTypeButton.setIconColor(getThemedColor(Theme.key_location_actionIcon));
            mapTypeButton.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
            mapTypeButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), true);
            mapTypeButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), false);

            shadowDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
            shadow.invalidate();

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
            }
        };

        for (int a = 0; a < undoView.length; a++) {
            themeDescriptions.add(new ThemeDescription(undoView[a], ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_undo_background));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"undoImageView"}, null, null, null, Theme.key_undo_cancelColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"undoTextView"}, null, null, null, Theme.key_undo_cancelColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"infoTextView"}, null, null, null, Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"subinfoTextView"}, null, null, null, Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"textPaint"}, null, null, null, Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"progressPaint"}, null, null, null, Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "BODY", Theme.key_undo_background));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Wibe Big", Theme.key_undo_background));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Wibe Big 3", Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Wibe Small", Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Body Main.**", Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Body Top.**", Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Line.**", Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Curve Big.**", Theme.key_undo_infoColor));
            themeDescriptions.add(new ThemeDescription(undoView[a], 0, new Class[]{UndoView.class}, new String[]{"leftImageView"}, "Curve Small.**", Theme.key_undo_infoColor));
        }

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, cellDelegate, Theme.key_dialogBackground));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_dialogButtonSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_chat_messagePanelHint));
        themeDescriptions.add(new ThemeDescription(searchItem != null ? searchItem.getSearchField() : null, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, cellDelegate, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, cellDelegate, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, cellDelegate, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(emptyImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyImage));
        themeDescriptions.add(new ThemeDescription(emptyTitleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogEmptyText));
        themeDescriptions.add(new ThemeDescription(emptySubtitleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogEmptyText));
        themeDescriptions.add(new ThemeDescription(shadow, 0, null, null, null, null, Theme.key_sheet_scrollUp));

        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_location_actionIcon));
        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_location_actionActiveIcon));
        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_location_actionBackground));
        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_location_actionPressedBackground));

        themeDescriptions.add(new ThemeDescription(mapTypeButton, 0, null, null, null, cellDelegate, Theme.key_location_actionIcon));
        themeDescriptions.add(new ThemeDescription(mapTypeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_location_actionBackground));
        themeDescriptions.add(new ThemeDescription(mapTypeButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_location_actionPressedBackground));

        themeDescriptions.add(new ThemeDescription(proximityButton, 0, null, null, null, cellDelegate, Theme.key_location_actionIcon));
        themeDescriptions.add(new ThemeDescription(proximityButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_location_actionBackground));
        themeDescriptions.add(new ThemeDescription(proximityButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_location_actionPressedBackground));

        themeDescriptions.add(new ThemeDescription(searchAreaButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_location_actionActiveIcon));
        themeDescriptions.add(new ThemeDescription(searchAreaButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_location_actionBackground));
        themeDescriptions.add(new ThemeDescription(searchAreaButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_location_actionPressedBackground));

        themeDescriptions.add(new ThemeDescription(null, 0, null, null, Theme.avatarDrawables, cellDelegate, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_location_liveLocationProgress));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_location_placeLocationBackground));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, null, Theme.key_dialog_liveLocationProgress));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLocationIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLiveLocationIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLocationBackground));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_location_sendLiveLocationBackground));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SendLocationCell.class}, new String[]{"accurateTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_location_sendLiveLocationText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{SendLocationCell.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_location_sendLocationText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LocationDirectionCell.class}, new String[]{"buttonTextView"}, null, null, null, Theme.key_featuredStickers_buttonText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{LocationDirectionCell.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_featuredStickers_addButton));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{LocationDirectionCell.class}, new String[]{"frameLayout"}, null, null, null, Theme.key_featuredStickers_addButtonPressed));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_dialogTextBlue2));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{LocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LocationCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LocationCell.class}, new String[]{"addressTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(searchListView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{LocationCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(searchListView, 0, new Class[]{LocationCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(searchListView, 0, new Class[]{LocationCell.class}, new String[]{"addressTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SharingLiveLocationCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SharingLiveLocationCell.class}, new String[]{"distanceTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LocationLoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LocationLoadingCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LocationLoadingCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LocationPoweredCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{LocationPoweredCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_dialogEmptyImage));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LocationPoweredCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_dialogEmptyText));

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

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            boolean result = super.drawChild(canvas, child, drawingTime);
            if (child == actionBar && parentLayout != null) {
                parentLayout.drawHeaderShadow(canvas, actionBar.getMeasuredHeight());
            }
            return result;
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
                    if (top == 0) {
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
                boolean searchVisible = actionBar.isSearchFieldVisible();
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

        @Override
        protected void drawList(Canvas blurCanvas, boolean top, ArrayList<IViewWithInvalidateCallback> views) {
            super.drawList(blurCanvas, top, views);
            if (sharedMediaLayout != null) {
                blurCanvas.save();
                blurCanvas.translate(0, listView.getY());
                sharedMediaLayout.drawListForBlur(blurCanvas, views);
                blurCanvas.restore();
            }
        }
    }

}
