/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.Manifest;
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
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
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
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.LocationActivityAdapter;
import org.telegram.ui.Adapters.LocationActivitySearchAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LocationCell;
import org.telegram.ui.Cells.LocationDirectionCell;
import org.telegram.ui.Cells.LocationLoadingCell;
import org.telegram.ui.Cells.LocationPoweredCell;
import org.telegram.ui.Cells.SendLocationCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SharingLiveLocationCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

public class ChatAttachAlertLocationLayout extends ChatAttachAlert.AttachAlertLayout implements NotificationCenter.NotificationCenterDelegate {

    private ImageView locationButton;
    private ActionBarMenuItem mapTypeButton;
    private SearchButton searchAreaButton;
    private LinearLayout emptyView;
    private ImageView emptyImageView;
    private TextView emptyTitleTextView;
    private TextView emptySubtitleTextView;
    private ActionBarMenuItem searchItem;
    private MapOverlayView overlayView;

    private GoogleMap googleMap;
    private MapView mapView;
    private CameraUpdate forceUpdate;
    private float yOffset;

    private boolean ignoreLayout;

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
    private boolean locationDenied = false;

    private boolean isFirstLocation = true;
    private long dialogId;

    private boolean firstFocus = true;

    private Paint backgroundPaint = new Paint();

    private ArrayList<VenueLocation> placeMarkers = new ArrayList<>();

    private AnimatorSet animatorSet;

    private Marker lastPressedMarker;
    private VenueLocation lastPressedVenue;
    private FrameLayout lastPressedMarkerView;

    private boolean checkPermission = true;
    private boolean checkBackgroundPermission = true;

    private boolean searching;
    private boolean searchWas;
    private boolean searchInProgress;

    private boolean wasResults;
    private boolean mapsInitialized;
    private boolean onResumeCalled;

    private Location myLocation;
    private Location userLocation;
    private int markerTop;

    private boolean userLocationMoved;
    private boolean searchedForCustomLocations;
    private boolean firstWas;
    private CircleOptions circleOptions;
    private LocationActivityDelegate delegate;

    private int locationType;

    private int overScrollHeight = AndroidUtilities.displaySize.x - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(66);
    private int mapHeight = overScrollHeight;
    private int clipSize;
    private int nonClipSize;

    private final static int map_list_menu_map = 2;
    private final static int map_list_menu_satellite = 3;
    private final static int map_list_menu_hybrid = 4;

    public final static int LOCATION_TYPE_SEND = 0;
    public final static int LOCATION_TYPE_SEND_WITH_LIVE = 1;

    public static class VenueLocation {
        public int num;
        public Marker marker;
        public TLRPC.TL_messageMediaVenue venue;
    }

    public static class LiveLocation {
        public int id;
        public TLRPC.Message object;
        public TLRPC.User user;
        public TLRPC.Chat chat;
        public Marker marker;
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

        private HashMap<Marker, View> views = new HashMap<>();

        public MapOverlayView(Context context) {
            super(context);
        }

        public void addInfoView(Marker marker) {
            VenueLocation location = (VenueLocation) marker.getTag();
            if (lastPressedVenue == location) {
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
                ChatActivity chatActivity = (ChatActivity) parentAlert.baseFragment;
                if (chatActivity.isInScheduleMode()) {
                    AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate) -> {
                        delegate.didSelectLocation(location.venue, locationType, notify, scheduleDate);
                        parentAlert.dismiss();
                    }, resourcesProvider);
                } else {
                    delegate.didSelectLocation(location.venue, locationType, true, 0);
                    parentAlert.dismiss();
                }
            });

            TextView nameTextView = new TextView(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameTextView.setMaxLines(1);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setSingleLine(true);
            nameTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
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
            addressTextView.setText(LocaleController.getString("TapToSendLocation", R.string.TapToSendLocation));

            FrameLayout iconLayout = new FrameLayout(context);
            iconLayout.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(36), LocationCell.getColorForIndex(location.num)));
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

            googleMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()), 300, null);
        }

        public void removeInfoView(Marker marker) {
            View view = views.get(marker);
            if (view != null) {
                removeView(view);
                views.remove(marker);
            }
        }

        public void updatePositions() {
            if (googleMap == null) {
                return;
            }
            Projection projection = googleMap.getProjection();
            for (HashMap.Entry<Marker, View> entry : views.entrySet()) {
                Marker marker = entry.getKey();
                View view = entry.getValue();
                Point point = projection.toScreenLocation(marker.getPosition());
                view.setTranslationX(point.x - view.getMeasuredWidth() / 2);
                view.setTranslationY(point.y - view.getMeasuredHeight() + AndroidUtilities.dp(22));
            }
        }
    }

    public interface LocationActivityDelegate {
        void didSelectLocation(TLRPC.MessageMedia location, int live, boolean notify, int scheduleDate);
    }

    public ChatAttachAlertLocationLayout(ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);
        AndroidUtilities.fixGoogleMapsBug();
        ChatActivity chatActivity = (ChatActivity) parentAlert.baseFragment;
        dialogId = chatActivity.getDialogId();
        if (chatActivity.getCurrentEncryptedChat() == null && !chatActivity.isInScheduleMode() && !UserObject.isUserSelf(chatActivity.getCurrentUser())) {
            locationType = LOCATION_TYPE_SEND_WITH_LIVE;
        } else {
            locationType = LOCATION_TYPE_SEND;
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.locationPermissionDenied);

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

        overlayView = new MapOverlayView(context);

        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                parentAlert.makeFocusable(searchItem.getSearchField(), true);
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                searchWas = false;
                searchAdapter.searchDelayed(null, null);
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
                searchAdapter.searchDelayed(text, userLocation);
            }
        });
        searchItem.setVisibility(locationDenied ? View.GONE : View.VISIBLE);
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        searchItem.setContentDescription(LocaleController.getString("Search", R.string.Search));
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setCursorColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_chat_messagePanelHint));

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(21));
        layoutParams.gravity = Gravity.LEFT | Gravity.BOTTOM;

        mapViewClip = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (overlayView != null) {
                    overlayView.updatePositions();
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                canvas.save();
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight() - clipSize);
                boolean result = super.drawChild(canvas, child, drawingTime);
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
                if (ev.getY() > getMeasuredHeight() - clipSize) {
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getY() > getMeasuredHeight() - clipSize) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        mapViewClip.setWillNotDraw(false);

        loadingMapView = new View(context);
        loadingMapView.setBackgroundDrawable(new MapPlaceholderDrawable());

        searchAreaButton = new SearchButton(context);
        searchAreaButton.setTranslationX(-AndroidUtilities.dp(80));
        searchAreaButton.setVisibility(INVISIBLE);
        Drawable drawable = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(40), getThemedColor(Theme.key_location_actionBackground), getThemedColor(Theme.key_location_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.places_btn).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, AndroidUtilities.dp(2), AndroidUtilities.dp(2));
            combinedDrawable.setFullsize(true);
            drawable = combinedDrawable;
        } else {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(searchAreaButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(searchAreaButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
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
        searchAreaButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        searchAreaButton.setText(LocaleController.getString("PlacesInThisArea", R.string.PlacesInThisArea));
        searchAreaButton.setGravity(Gravity.CENTER);
        searchAreaButton.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        mapViewClip.addView(searchAreaButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, Build.VERSION.SDK_INT >= 21 ? 40 : 44, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 80, 12, 80, 0));
        searchAreaButton.setOnClickListener(v -> {
            showSearchPlacesButton(false);
            adapter.searchPlacesWithQuery(null, userLocation, true, true);
            searchedForCustomLocations = true;
            showResults();
        });

        mapTypeButton = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_location_actionIcon), resourcesProvider);
        mapTypeButton.setClickable(true);
        mapTypeButton.setSubMenuOpenSide(2);
        mapTypeButton.setAdditionalXOffset(AndroidUtilities.dp(10));
        mapTypeButton.setAdditionalYOffset(-AndroidUtilities.dp(10));
        mapTypeButton.addSubItem(map_list_menu_map, R.drawable.msg_map, LocaleController.getString("Map", R.string.Map), resourcesProvider);
        mapTypeButton.addSubItem(map_list_menu_satellite, R.drawable.msg_satellite, LocaleController.getString("Satellite", R.string.Satellite), resourcesProvider);
        mapTypeButton.addSubItem(map_list_menu_hybrid, R.drawable.msg_hybrid, LocaleController.getString("Hybrid", R.string.Hybrid), resourcesProvider);
        mapTypeButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40), getThemedColor(Theme.key_location_actionBackground), getThemedColor(Theme.key_location_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(40), AndroidUtilities.dp(40));
            drawable = combinedDrawable;
        } else {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(mapTypeButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(mapTypeButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            mapTypeButton.setStateListAnimator(animator);
            mapTypeButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(40), AndroidUtilities.dp(40));
                }
            });
        }
        mapTypeButton.setBackgroundDrawable(drawable);
        mapTypeButton.setIcon(R.drawable.location_type);
        mapViewClip.addView(mapTypeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 40 : 44, Build.VERSION.SDK_INT >= 21 ? 40 : 44, Gravity.RIGHT | Gravity.TOP, 0, 12, 12, 0));
        mapTypeButton.setOnClickListener(v -> mapTypeButton.toggleSubMenu());
        mapTypeButton.setDelegate(id -> {
            if (googleMap == null) {
                return;
            }
            if (id == map_list_menu_map) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            } else if (id == map_list_menu_satellite) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            } else if (id == map_list_menu_hybrid) {
                googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            }
        });

        locationButton = new ImageView(context);
        drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(40), getThemedColor(Theme.key_location_actionBackground), getThemedColor(Theme.key_location_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(40), AndroidUtilities.dp(40));
            drawable = combinedDrawable;
        } else {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(locationButton, View.TRANSLATION_Z, AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(locationButton, View.TRANSLATION_Z, AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            locationButton.setStateListAnimator(animator);
            locationButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(40), AndroidUtilities.dp(40));
                }
            });
        }
        locationButton.setBackgroundDrawable(drawable);
        locationButton.setImageResource(R.drawable.location_current);
        locationButton.setScaleType(ImageView.ScaleType.CENTER);
        locationButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionActiveIcon), PorterDuff.Mode.MULTIPLY));
        locationButton.setTag(Theme.key_location_actionActiveIcon);
        locationButton.setContentDescription(LocaleController.getString("AccDescrMyLocation", R.string.AccDescrMyLocation));
        FrameLayout.LayoutParams layoutParams1 = LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 40 : 44, Build.VERSION.SDK_INT >= 21 ? 40 : 44, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 12, 12);
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
            if (myLocation != null && googleMap != null) {
                locationButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionActiveIcon), PorterDuff.Mode.MULTIPLY));
                locationButton.setTag(Theme.key_location_actionActiveIcon);
                adapter.setCustomLocation(null);
                userLocationMoved = false;
                showSearchPlacesButton(false);
                googleMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(myLocation.getLatitude(), myLocation.getLongitude())));
                if (searchedForCustomLocations) {
                    if (myLocation != null) {
                        adapter.searchPlacesWithQuery(null, myLocation, true, true);
                    }
                    searchedForCustomLocations = false;
                    showResults();
                }
            }
            removeInfoView();
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
        emptyImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_dialogEmptyImage), PorterDuff.Mode.MULTIPLY));
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTitleTextView = new TextView(context);
        emptyTitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptyTitleTextView.setGravity(Gravity.CENTER);
        emptyTitleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleTextView.setText(LocaleController.getString("NoPlacesFound", R.string.NoPlacesFound));
        emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

        emptySubtitleTextView = new TextView(context);
        emptySubtitleTextView.setTextColor(getThemedColor(Theme.key_dialogEmptyText));
        emptySubtitleTextView.setGravity(Gravity.CENTER);
        emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptySubtitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                updateClipView();
            }
        };
        listView.setClipToPadding(false);
        listView.setAdapter(adapter = new LocationActivityAdapter(context, locationType, dialogId, true, resourcesProvider));
        adapter.setUpdateRunnable(this::updateClipView);
        adapter.setMyLocationDenied(locationDenied);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(context, LinearLayoutManager.VERTICAL, false, 0, listView) {
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
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > (mapHeight - overScrollHeight)) {
                            listView.smoothScrollBy(0, holder.itemView.getTop() - (mapHeight - overScrollHeight));
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
            if (position == 1) {
                if (delegate != null && userLocation != null) {
                    if (lastPressedMarkerView != null) {
                        lastPressedMarkerView.callOnClick();
                    } else {
                        TLRPC.TL_messageMediaGeo location = new TLRPC.TL_messageMediaGeo();
                        location.geo = new TLRPC.TL_geoPoint();
                        location.geo.lat = AndroidUtilities.fixLocationCoord(userLocation.getLatitude());
                        location.geo._long = AndroidUtilities.fixLocationCoord(userLocation.getLongitude());
                        if (chatActivity.isInScheduleMode()) {
                            AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate) -> {
                                delegate.didSelectLocation(location, locationType, notify, scheduleDate);
                                parentAlert.dismiss();
                            }, resourcesProvider);
                        } else {
                            delegate.didSelectLocation(location, locationType, true, 0);
                            parentAlert.dismiss();
                        }
                    }
                } else if (locationDenied) {
                    showPermissionAlert(true);
                }
            } else if (position == 2 && locationType == LOCATION_TYPE_SEND_WITH_LIVE) {
                if (getLocationController().isSharingLocation(dialogId)) {
                    getLocationController().removeSharingLocation(dialogId);
                    parentAlert.dismiss();
                } else {
                    if (myLocation == null && locationDenied) {
                        showPermissionAlert(true);
                    } else {
                        openShareLiveLocation();
                    }
                }
            } else {
                Object object = adapter.getItem(position);
                if (object instanceof TLRPC.TL_messageMediaVenue) {
                    if (chatActivity.isInScheduleMode()) {
                        AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate) -> {
                            delegate.didSelectLocation((TLRPC.TL_messageMediaVenue) object, locationType, notify, scheduleDate);
                            parentAlert.dismiss();
                        }, resourcesProvider);
                    } else {
                        delegate.didSelectLocation((TLRPC.TL_messageMediaVenue) object, locationType, true, 0);
                        parentAlert.dismiss();
                    }
                } else if (object instanceof LiveLocation) {
                    LiveLocation liveLocation = (LiveLocation) object;
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(liveLocation.marker.getPosition(), googleMap.getMaxZoomLevel() - 4));
                }
            }
        });
        adapter.setDelegate(dialogId, this::updatePlacesMarkers);
        adapter.setOverScrollHeight(overScrollHeight);

        addView(mapViewClip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        mapView = new MapView(context) {

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                MotionEvent eventToRecycle = null;
                if (yOffset != 0) {
                    ev = eventToRecycle = MotionEvent.obtain(ev);
                    eventToRecycle.offsetLocation(0, -yOffset / 2);
                }
                boolean result = super.dispatchTouchEvent(ev);
                if (eventToRecycle != null) {
                    eventToRecycle.recycle();
                }
                return result;
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
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
                    adapter.fetchLocationAddress();
                }
                if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!userLocationMoved) {
                        locationButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionIcon), PorterDuff.Mode.MULTIPLY));
                        locationButton.setTag(Theme.key_location_actionIcon);
                        userLocationMoved = true;
                    }
                    if (googleMap != null) {
                        if (userLocation != null) {
                            userLocation.setLatitude(googleMap.getCameraPosition().target.latitude);
                            userLocation.setLongitude(googleMap.getCameraPosition().target.longitude);
                        }
                    }
                    adapter.setCustomLocation(userLocation);
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
                            googleMap.setOnMapLoadedCallback(() -> AndroidUtilities.runOnUIThread(() -> {
                                loadingMapView.setTag(1);
                                loadingMapView.animate().alpha(0.0f).setDuration(180).start();
                            }));
                            if (isActiveThemeDark()) {
                                currentMapStyleDark = true;
                                MapStyleOptions style = MapStyleOptions.loadRawResourceStyle(ApplicationLoader.applicationContext, R.raw.mapstyle_night);
                                googleMap.setMapStyle(style);
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
        searchListView.setVisibility(GONE);
        searchListView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        searchAdapter = new LocationActivitySearchAdapter(context) {
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
            if (object != null && delegate != null) {
                if (chatActivity.isInScheduleMode()) {
                    AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate) -> {
                        delegate.didSelectLocation(object, locationType, notify, scheduleDate);
                        parentAlert.dismiss();
                    }, resourcesProvider);
                } else {
                    delegate.didSelectLocation(object, locationType, true, 0);
                    parentAlert.dismiss();
                }
            }
        });

        updateEmptyView();
    }

    @Override
    boolean shouldHideBottomButtons() {
        return !locationDenied;
    }

    @Override
    void onPause() {
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
    void onDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.locationPermissionDenied);
        try {
            if (googleMap != null) {
                googleMap.setMyLocationEnabled(false);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (mapView != null) {
            mapView.setTranslationY(-AndroidUtilities.displaySize.y * 3);
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
    void onHide() {
        searchItem.setVisibility(GONE);
    }

    @Override
    int needsActionBar() {
        return 1;
    }

    @Override
    boolean onDismiss() {
        onDestroy();
        return false;
    }

    @Override
    int getCurrentItemTop() {
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
    int getListTopPadding() {
        return listView.getPaddingTop();
    }

    @Override
    int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(56);
    }

    @Override
    void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (parentAlert.actionBar.isSearchFieldVisible() || parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
            padding = mapHeight - overScrollHeight;
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
        if (listView.getPaddingTop() != padding) {
            ignoreLayout = true;
            listView.setPadding(0, padding, 0, 0);
            ignoreLayout = false;
        }
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
    int getButtonsHideOffset() {
        return AndroidUtilities.dp(56);
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    void scrollToTop() {
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

    private void showSearchPlacesButton(boolean show) {
        if (show && searchAreaButton != null && searchAreaButton.getTag() == null) {
            if (myLocation == null || userLocation == null || userLocation.distanceTo(myLocation) < 300) {
                show = false;
            }
        }
        if (searchAreaButton == null || show && searchAreaButton.getTag() != null || !show && searchAreaButton.getTag() == null) {
            return;
        }
        searchAreaButton.setVisibility(show ? VISIBLE : INVISIBLE);
        searchAreaButton.setTag(show ? 1 : null);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(searchAreaButton, View.TRANSLATION_X, show ? 0 : -AndroidUtilities.dp(80)));
        animatorSet.setDuration(180);
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animatorSet.start();
    }

    public void openShareLiveLocation() {
        if (delegate == null || getParentActivity() == null || myLocation == null) {
            return;
        }
        if (checkBackgroundPermission && Build.VERSION.SDK_INT >= 29) {
            Activity activity = getParentActivity();
            if (activity != null) {
                checkBackgroundPermission = false;
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                int lastTime = preferences.getInt("backgroundloc", 0);
                if (Math.abs(System.currentTimeMillis() / 1000 - lastTime) > 24 * 60 * 60 && activity.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    preferences.edit().putInt("backgroundloc", (int) (System.currentTimeMillis() / 1000)).commit();
                    AlertsCreator.createBackgroundLocationPermissionDialog(activity, getMessagesController().getUser(getUserConfig().getClientUserId()), this::openShareLiveLocation, resourcesProvider).show();
                    return;
                }
            }
        }
        TLRPC.User user = null;
        if (DialogObject.isUserDialog(dialogId)) {
            user = parentAlert.baseFragment.getMessagesController().getUser(dialogId);
        }
        AlertsCreator.createLocationUpdateDialog(getParentActivity(), user, param -> {
            TLRPC.TL_messageMediaGeoLive location = new TLRPC.TL_messageMediaGeoLive();
            location.geo = new TLRPC.TL_geoPoint();
            location.geo.lat = AndroidUtilities.fixLocationCoord(myLocation.getLatitude());
            location.geo._long = AndroidUtilities.fixLocationCoord(myLocation.getLongitude());
            location.period = param;
            delegate.didSelectLocation(location, locationType, true, 0);
            parentAlert.dismiss();
        }, resourcesProvider).show();
    }

    private Bitmap[] bitmapCache = new Bitmap[7];
    private Bitmap createPlaceBitmap(int num) {
        if (bitmapCache[num % 7] != null) {
            return bitmapCache[num % 7];
        }
        try {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xffffffff);
            Bitmap bitmap = Bitmap.createBitmap(AndroidUtilities.dp(12), AndroidUtilities.dp(12), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawCircle(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
            paint.setColor(LocationCell.getColorForIndex(num));
            canvas.drawCircle(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(5), paint);
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
                MarkerOptions options = new MarkerOptions().position(new LatLng(venue.geo.lat, venue.geo._long));
                options.icon(BitmapDescriptorFactory.fromBitmap(createPlaceBitmap(a)));
                options.anchor(0.5f, 0.5f);
                options.title(venue.title);
                options.snippet(venue.address);
                VenueLocation venueLocation = new VenueLocation();
                venueLocation.num = a;
                venueLocation.marker = googleMap.addMarker(options);
                venueLocation.venue = venue;
                venueLocation.marker.setTag(venueLocation);
                placeMarkers.add(venueLocation);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
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
        if (googleMap == null) {
            return;
        }

        userLocation = new Location("network");
        userLocation.setLatitude(20.659322);
        userLocation.setLongitude(-11.406250);

        try {
            googleMap.setMyLocationEnabled(true);
        } catch (Exception e) {
            FileLog.e(e);
        }
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                showSearchPlacesButton(true);
                removeInfoView();

                if (!scrolling && listView.getChildCount() > 0) {
                    View view = listView.getChildAt(0);
                    if (view != null) {
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(view);
                        if (holder != null && holder.getAdapterPosition() == 0) {
                            int min = locationType == LOCATION_TYPE_SEND ? 0 : AndroidUtilities.dp(66);
                            int top = view.getTop();
                            if (top < -min) {
                                CameraPosition cameraPosition = googleMap.getCameraPosition();
                                forceUpdate = CameraUpdateFactory.newLatLngZoom(cameraPosition.target, cameraPosition.zoom);
                                listView.smoothScrollBy(0, top + min);
                            }
                        }
                    }
                }
            }
        });
        googleMap.setOnMyLocationChangeListener(location -> {
            if (parentAlert == null || parentAlert.baseFragment == null) {
                return;
            }
            positionMarker(location);
            getLocationController().setGoogleMapLocation(location, isFirstLocation);
            isFirstLocation = false;
        });
        googleMap.setOnMarkerClickListener(marker -> {
            if (!(marker.getTag() instanceof VenueLocation)) {
                return true;
            }
            markerImageView.setVisibility(INVISIBLE);
            if (!userLocationMoved) {
                locationButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_location_actionIcon), PorterDuff.Mode.MULTIPLY));
                locationButton.setTag(Theme.key_location_actionIcon);
                userLocationMoved = true;
            }
            overlayView.addInfoView(marker);
            return true;
        });
        googleMap.setOnCameraMoveListener(() -> {
            if (overlayView != null) {
                overlayView.updatePositions();
            }
        });
        AndroidUtilities.runOnUIThread(() -> {
            if (loadingMapView.getTag() == null) {
                loadingMapView.animate().alpha(0.0f).setDuration(180).start();
            }
        }, 200);
        positionMarker(myLocation = getLastLocation());

        if (checkGpsEnabled && getParentActivity() != null) {
            checkGpsEnabled = false;
            if (!getParentActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
                return;
            }
            try {
                LocationManager lm = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
                if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
                    builder.setTitle(LocaleController.getString("GpsDisabledAlertTitle", R.string.GpsDisabledAlertTitle));
                    builder.setMessage(LocaleController.getString("GpsDisabledAlertText", R.string.GpsDisabledAlertText));
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
                    builder.show();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        updateClipView();
    }

    private void removeInfoView() {
        if (lastPressedMarker != null) {
            markerImageView.setVisibility(VISIBLE);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity(), resourcesProvider);
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
        builder.show();
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
        int offset = AndroidUtilities.dp(258) + child.getTop();
        if (offset < 0 || offset > AndroidUtilities.dp(258)) {
            return;
        }
        listView.smoothScrollBy(0, offset);
    }

    private void updateClipView() {
        if (mapView == null || mapViewClip == null) {
            return;
        }
        int height;
        int top;
        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
        if (holder != null) {
            top = (int) (holder.itemView.getY());
            height = overScrollHeight + Math.min(top, 0);
        } else {
            top = -mapViewClip.getMeasuredHeight();
            height = 0;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
        if (layoutParams != null) {
            if (height <= 0) {
                if (mapView.getVisibility() == View.VISIBLE) {
                    mapView.setVisibility(INVISIBLE);
                    mapViewClip.setVisibility(INVISIBLE);
                    if (overlayView != null) {
                        overlayView.setVisibility(INVISIBLE);
                    }
                }
                mapView.setTranslationY(top);
                return;
            }
            if (mapView.getVisibility() == View.INVISIBLE) {
                mapView.setVisibility(VISIBLE);
                mapViewClip.setVisibility(VISIBLE);
                if (overlayView != null) {
                    overlayView.setVisibility(VISIBLE);
                }
            }
            int trY = Math.max(0, -(top - mapHeight + overScrollHeight) / 2);
            int maxClipSize = mapHeight - overScrollHeight;
            int totalToMove = listView.getPaddingTop() - maxClipSize;
            float moveProgress = 1.0f - (Math.max(0.0f, Math.min(1.0f, (listView.getPaddingTop() - top) / (float) totalToMove)));
            int prevClipSize = clipSize;
            if (locationDenied && isTypeSend()) {
                maxClipSize += Math.min(top, listView.getPaddingTop());
            }
            clipSize = (int) (maxClipSize * moveProgress);

            mapView.setTranslationY(trY);
            nonClipSize = maxClipSize - clipSize;
            mapViewClip.invalidate();

            mapViewClip.setTranslationY(top - nonClipSize);
            if (googleMap != null) {
                googleMap.setPadding(0, AndroidUtilities.dp(6), 0, clipSize + AndroidUtilities.dp(6));
            }
            if (overlayView != null) {
                overlayView.setTranslationY(trY);
            }
            float translationY = Math.min(Math.max(nonClipSize - top, 0), mapHeight - mapTypeButton.getMeasuredHeight() - AndroidUtilities.dp(64 + 16));
            mapTypeButton.setTranslationY(translationY);
            searchAreaButton.setTranslation(translationY);
            locationButton.setTranslationY(-clipSize);
            markerImageView.setTranslationY(markerTop = (mapHeight - clipSize) / 2 - AndroidUtilities.dp(48) + trY);
            if (prevClipSize != clipSize) {
                LatLng location;
                if (lastPressedMarker != null) {
                    location = lastPressedMarker.getPosition();
                } else if (userLocationMoved) {
                    location = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
                } else if (myLocation != null) {
                    location = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
                } else {
                    location = null;
                }
                if (location != null) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLng(location));
                }
            }

            if (locationDenied && isTypeSend()) {
//                adapter.setOverScrollHeight(overScrollHeight + top);
//                // TODO(dkaraush): fix ripple effect on buttons
                final int count = adapter.getItemCount();
                for (int i = 1; i < count; ++i) {
                    holder = listView.findViewHolderForAdapterPosition(i);
                    if (holder != null) {
                        holder.itemView.setTranslationY(listView.getPaddingTop() - top);
                    }
                }
            }
        }
    }

    private boolean isTypeSend() {
        return locationType == LOCATION_TYPE_SEND || locationType == LOCATION_TYPE_SEND_WITH_LIVE;
    }

    private int buttonsHeight() {
        int buttonsHeight = AndroidUtilities.dp(66);
        if (locationType == LOCATION_TYPE_SEND_WITH_LIVE)
            buttonsHeight += AndroidUtilities.dp(66);
        return buttonsHeight;
    }

    private void fixLayoutInternal(final boolean resume) {
        int viewHeight = getMeasuredHeight();
        if (viewHeight == 0 || mapView == null) {
            return;
        }
        int height = ActionBar.getCurrentActionBarHeight();
        int maxMapHeight = AndroidUtilities.displaySize.y - height - buttonsHeight() - AndroidUtilities.dp(90);
        overScrollHeight = AndroidUtilities.dp(189);
        mapHeight = Math.max(overScrollHeight, locationDenied && isTypeSend() ? maxMapHeight : Math.min(AndroidUtilities.dp(310), maxMapHeight));
        if (locationDenied && isTypeSend()) {
            overScrollHeight = mapHeight;
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        layoutParams.topMargin = height;
        listView.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) mapViewClip.getLayoutParams();
        layoutParams.topMargin = height;
        layoutParams.height = mapHeight;
        mapViewClip.setLayoutParams(layoutParams);

        layoutParams = (FrameLayout.LayoutParams) searchListView.getLayoutParams();
        layoutParams.topMargin = height;
        searchListView.setLayoutParams(layoutParams);

        adapter.setOverScrollHeight(locationDenied && isTypeSend() ? overScrollHeight - listView.getPaddingTop() : overScrollHeight);
        layoutParams = (FrameLayout.LayoutParams) mapView.getLayoutParams();
        if (layoutParams != null) {
            layoutParams.height = mapHeight + AndroidUtilities.dp(10);
            mapView.setLayoutParams(layoutParams);
        }
        if (overlayView != null) {
            layoutParams = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.height = mapHeight + AndroidUtilities.dp(10);
                overlayView.setLayoutParams(layoutParams);
            }
        }

        adapter.notifyDataSetChanged();
        updateClipView();
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

        if (googleMap != null) {
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            if (adapter != null) {
                if (!searchedForCustomLocations) {
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

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.locationPermissionGranted) {
            locationDenied = false;
            if (adapter != null) {
                adapter.setMyLocationDenied(locationDenied);
            }
            if (googleMap != null) {
                try {
                    googleMap.setMyLocationEnabled(true);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.locationPermissionDenied) {
            locationDenied = true;
            if (adapter != null) {
                adapter.setMyLocationDenied(locationDenied);
            }
        }
        fixLayoutInternal(true);
        searchItem.setVisibility(locationDenied ? View.GONE : View.VISIBLE);
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
    void onShow() {
        parentAlert.actionBar.setTitle(LocaleController.getString("ShareLocation", R.string.ShareLocation));
        if (mapView.getParent() == null) {
            mapViewClip.addView(mapView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10), Gravity.TOP | Gravity.LEFT));
            mapViewClip.addView(overlayView, 1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, overScrollHeight + AndroidUtilities.dp(10), Gravity.TOP | Gravity.LEFT));
            mapViewClip.addView(loadingMapView, 2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
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
        if (googleMap != null) {
            try {
                googleMap.setMyLocationEnabled(true);
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
                        activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
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

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            mapTypeButton.setIconColor(getThemedColor(Theme.key_location_actionIcon));
            mapTypeButton.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
            mapTypeButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), true);
            mapTypeButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), false);

            if (googleMap != null) {
                if (isActiveThemeDark()) {
                    if (!currentMapStyleDark) {
                        currentMapStyleDark = true;
                        MapStyleOptions style = MapStyleOptions.loadRawResourceStyle(ApplicationLoader.applicationContext, R.raw.mapstyle_night);
                        googleMap.setMapStyle(style);
                    }
                } else {
                    if (currentMapStyleDark) {
                        currentMapStyleDark = false;
                        googleMap.setMapStyle(null);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(mapViewClip, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));
        themeDescriptions.add(new ThemeDescription(searchItem != null ? searchItem.getSearchField() : null, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_dialogTextBlack));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(emptyImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyImage));
        themeDescriptions.add(new ThemeDescription(emptyTitleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogEmptyText));
        themeDescriptions.add(new ThemeDescription(emptySubtitleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogEmptyText));

        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_location_actionIcon));
        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_location_actionActiveIcon));
        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_location_actionBackground));
        themeDescriptions.add(new ThemeDescription(locationButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_location_actionPressedBackground));

        themeDescriptions.add(new ThemeDescription(mapTypeButton, 0, null, null, null, cellDelegate, Theme.key_location_actionIcon));
        themeDescriptions.add(new ThemeDescription(mapTypeButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_location_actionBackground));
        themeDescriptions.add(new ThemeDescription(mapTypeButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_location_actionPressedBackground));

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
}
