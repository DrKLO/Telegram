/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;

import androidx.annotation.Keep;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.InputType;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.ArrayUtils;
import org.telegram.messenger.time.SunDate;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.BrightnessControlCell;
import org.telegram.ui.Cells.ChatListCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Cells.ThemeTypeCell;
import org.telegram.ui.Cells.ThemesHorizontalListCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.ThemeEditorView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ThemeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public final static int THEME_TYPE_BASIC = 0;
    public final static int THEME_TYPE_NIGHT = 1;
    public final static int THEME_TYPE_OTHER = 2;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;
    private ThemesHorizontalListCell themesHorizontalListCell;

    private ArrayList<Theme.ThemeInfo> darkThemes = new ArrayList<>();
    private ArrayList<Theme.ThemeInfo> defaultThemes = new ArrayList<>();
    private int currentType;

    boolean hasThemeAccents;

    private int backgroundRow;
    private int textSizeHeaderRow;
    private int textSizeRow;
    private int settingsRow;
    private int customTabsRow;
    private int directShareRow;
    private int raiseToSpeakRow;
    private int sendByEnterRow;
    private int saveToGalleryRow;
    private int distanceRow;
    private int enableAnimationsRow;
    private int settings2Row;
    private int stickersRow;
    private int stickersSection2Row;

    private int emojiRow;
    private int contactsReimportRow;
    private int contactsSortRow;

    private int nightThemeRow;
    private int nightDisabledRow;
    private int nightScheduledRow;
    private int nightAutomaticRow;
    private int nightTypeInfoRow;
    private int scheduleHeaderRow;
    private int scheduleLocationRow;
    private int scheduleUpdateLocationRow;
    private int scheduleLocationInfoRow;
    private int scheduleFromRow;
    private int scheduleToRow;
    private int scheduleFromToInfoRow;
    private int automaticHeaderRow;
    private int automaticBrightnessRow;
    private int automaticBrightnessInfoRow;
    private int preferedHeaderRow;
    private int newThemeInfoRow;
    private int themeHeaderRow;
    private int chatListHeaderRow;
    private int chatListRow;
    private int chatListInfoRow;
    private int themeListRow;
    private int themeAccentListRow;
    private int themeInfoRow;

    private int rowCount;

    private boolean updatingLocation;

    private int previousUpdatedType;
    private boolean previousByLocation;

    private GpsLocationListener gpsLocationListener = new GpsLocationListener();
    private GpsLocationListener networkLocationListener = new GpsLocationListener();

    private final static int create_theme = 1;

    private class GpsLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            if (location == null) {
                return;
            }
            stopLocationUpdate();
            updateSunTime(location, false);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    }

    public interface SizeChooseViewDelegate {
        void onSizeChanged();
    }

    private class TextSizeCell extends FrameLayout {

        private ThemePreviewMessagesCell messagesCell;
        private SeekBarView sizeBar;
        private int startFontSize = 12;
        private int endFontSize = 30;
        private int lastWidth;

        private TextPaint textPaint;

        public TextSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setDelegate(progress -> {
                int fontSize = Math.round(startFontSize + (endFontSize - startFontSize) * progress);
                if (fontSize != SharedConfig.fontSize) {
                    SharedConfig.fontSize = fontSize;
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("fons_size", SharedConfig.fontSize);
                    editor.commit();
                    Theme.chat_msgTextPaint.setTextSize(AndroidUtilities.dp(SharedConfig.fontSize));
                    int firstVisPos = layoutManager.findFirstVisibleItemPosition();
                    View firstVisView = firstVisPos != RecyclerView.NO_POSITION ? layoutManager.findViewByPosition(firstVisPos) : null;
                    int top = firstVisView != null ? firstVisView.getTop() : 0;
                    ChatMessageCell[] cells = messagesCell.getCells();
                    for (int a = 0; a < cells.length; a++) {
                        cells[a].getMessageObject().resetLayout();
                        cells[a].requestLayout();
                    }
                    if (firstVisView != null) {
                        layoutManager.scrollToPositionWithOffset(firstVisPos, top);
                    }
                }
            });
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 0));

            messagesCell = new ThemePreviewMessagesCell(context, parentLayout, 0);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 53, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText("" + SharedConfig.fontSize, getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int w = MeasureSpec.getSize(widthMeasureSpec);
            if (lastWidth != w) {
                sizeBar.setProgress((SharedConfig.fontSize - startFontSize) / (float) (endFontSize - startFontSize));
                lastWidth = w;
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            messagesCell.invalidate();
            sizeBar.invalidate();
        }
    }

    public ThemeActivity(int type) {
        super();
        currentType = type;
        updateRows(true);
    }

    private void updateRows(boolean notify) {
        int oldRowCount = rowCount;

        int prevThemeAccentListRow = themeAccentListRow;

        rowCount = 0;
        emojiRow = -1;
        contactsReimportRow = -1;
        contactsSortRow = -1;
        scheduleLocationRow = -1;
        scheduleUpdateLocationRow = -1;
        scheduleLocationInfoRow = -1;
        nightDisabledRow = -1;
        nightScheduledRow = -1;
        nightAutomaticRow = -1;
        nightTypeInfoRow = -1;
        scheduleHeaderRow = -1;
        nightThemeRow = -1;
        newThemeInfoRow = -1;
        scheduleFromRow = -1;
        scheduleToRow = -1;
        scheduleFromToInfoRow = -1;
        themeListRow = -1;
        themeAccentListRow = -1;
        themeInfoRow = -1;
        preferedHeaderRow = -1;
        automaticHeaderRow = -1;
        automaticBrightnessRow = -1;
        automaticBrightnessInfoRow = -1;
        textSizeHeaderRow = -1;
        themeHeaderRow = -1;
        chatListHeaderRow = -1;
        chatListRow = -1;
        chatListInfoRow = -1;

        textSizeRow = -1;
        backgroundRow = -1;
        settingsRow = -1;
        customTabsRow = -1;
        directShareRow = -1;
        enableAnimationsRow = -1;
        raiseToSpeakRow = -1;
        sendByEnterRow = -1;
        saveToGalleryRow = -1;
        distanceRow = -1;
        settings2Row = -1;
        stickersRow = -1;
        stickersSection2Row = -1;

        if (currentType == THEME_TYPE_BASIC) {
            defaultThemes.clear();
            darkThemes.clear();
            for (int a = 0, N = Theme.themes.size(); a < N; a++) {
                Theme.ThemeInfo themeInfo = Theme.themes.get(a);
                if (themeInfo.pathToFile != null) {
                    darkThemes.add(themeInfo);
                } else {
                    defaultThemes.add(themeInfo);
                }
            }
            Collections.sort(defaultThemes, (o1, o2) -> Integer.compare(o1.sortIndex, o2.sortIndex));

            textSizeHeaderRow = rowCount++;
            textSizeRow = rowCount++;
            backgroundRow = rowCount++;
            newThemeInfoRow = rowCount++;
            themeHeaderRow = rowCount++;
            themeListRow = rowCount++;
            hasThemeAccents = Theme.getCurrentTheme().accentColorOptions != null;
            if (themesHorizontalListCell != null) {
                themesHorizontalListCell.setDrawDivider(hasThemeAccents);
            }
            if (hasThemeAccents) {
                themeAccentListRow = rowCount++;
            }
            themeInfoRow = rowCount++;

            chatListHeaderRow = rowCount++;
            chatListRow = rowCount++;
            chatListInfoRow = rowCount++;

            settingsRow = rowCount++;
            nightThemeRow = rowCount++;
            customTabsRow = rowCount++;
            directShareRow = rowCount++;
            enableAnimationsRow = rowCount++;
            emojiRow = rowCount++;
            raiseToSpeakRow = rowCount++;
            sendByEnterRow = rowCount++;
            saveToGalleryRow = rowCount++;
            distanceRow = rowCount++;
            settings2Row = rowCount++;
            stickersRow = rowCount++;
            stickersSection2Row = rowCount++;
        } else {
            darkThemes.clear();
            for (int a = 0, N = Theme.themes.size(); a < N; a++) {
                Theme.ThemeInfo themeInfo = Theme.themes.get(a);
                if (themeInfo.isLight() || themeInfo.info != null && themeInfo.info.document == null) {
                    continue;
                }
                darkThemes.add(themeInfo);
            }

            nightDisabledRow = rowCount++;
            nightScheduledRow = rowCount++;
            nightAutomaticRow = rowCount++;
            nightTypeInfoRow = rowCount++;
            if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
                scheduleHeaderRow = rowCount++;
                scheduleLocationRow = rowCount++;
                if (Theme.autoNightScheduleByLocation) {
                    scheduleUpdateLocationRow = rowCount++;
                    scheduleLocationInfoRow = rowCount++;
                } else {
                    scheduleFromRow = rowCount++;
                    scheduleToRow = rowCount++;
                    scheduleFromToInfoRow = rowCount++;
                }
            } else if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC) {
                automaticHeaderRow = rowCount++;
                automaticBrightnessRow = rowCount++;
                automaticBrightnessInfoRow = rowCount++;
            }
            if (Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE) {
                preferedHeaderRow = rowCount++;
                themeListRow = rowCount++;
                hasThemeAccents = Theme.getCurrentNightTheme().accentColorOptions != null;
                if (themesHorizontalListCell != null) {
                    themesHorizontalListCell.setDrawDivider(hasThemeAccents);
                }
                if (hasThemeAccents) {
                    themeAccentListRow = rowCount++;
                }
                themeInfoRow = rowCount++;
            }
        }

        if (listAdapter != null) {
            if (currentType != THEME_TYPE_NIGHT || previousUpdatedType == Theme.selectedAutoNightType || previousUpdatedType == -1) {
                if (notify || previousUpdatedType == -1) {
                    if (themesHorizontalListCell != null) {
                        themesHorizontalListCell.notifyDataSetChanged(listView.getWidth());
                    }
                    listAdapter.notifyDataSetChanged();
                } else {
                    if (prevThemeAccentListRow == -1 && themeAccentListRow != -1) {
                        listAdapter.notifyItemInserted(themeAccentListRow);
                    } else if (prevThemeAccentListRow != -1 && themeAccentListRow == -1) {
                        listAdapter.notifyItemRemoved(prevThemeAccentListRow);
                    } else if (themeAccentListRow != -1) {
                        listAdapter.notifyItemChanged(themeAccentListRow);
                    }
                }
            } else {
                int start = nightTypeInfoRow + 1;
                if (previousUpdatedType != Theme.selectedAutoNightType) {
                    for (int a = 0; a < 3; a++) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(a);
                        if (holder == null) {
                            continue;
                        }
                        ((ThemeTypeCell) holder.itemView).setTypeChecked(a == Theme.selectedAutoNightType);
                    }

                    if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE) {
                        listAdapter.notifyItemRangeRemoved(start, oldRowCount - start);
                    } else if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
                        if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_NONE) {
                            listAdapter.notifyItemRangeInserted(start, rowCount - start);
                        } else if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC) {
                            listAdapter.notifyItemRangeRemoved(start, 3);
                            listAdapter.notifyItemRangeInserted(start, Theme.autoNightScheduleByLocation ? 4 : 5);
                        }
                    } else if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC) {
                        if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_NONE) {
                            listAdapter.notifyItemRangeInserted(start, rowCount - start);
                        } else if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
                            listAdapter.notifyItemRangeRemoved(start, Theme.autoNightScheduleByLocation ? 4 : 5);
                            listAdapter.notifyItemRangeInserted(start, 3);
                        }
                    }
                } else {
                    if (previousByLocation != Theme.autoNightScheduleByLocation) {
                        listAdapter.notifyItemRangeRemoved(start + 2, Theme.autoNightScheduleByLocation ? 3 : 2);
                        listAdapter.notifyItemRangeInserted(start + 2, Theme.autoNightScheduleByLocation ? 2 : 3);
                    }
                }
            }
        }
        if (currentType == THEME_TYPE_NIGHT) {
            previousByLocation = Theme.autoNightScheduleByLocation;
            previousUpdatedType = Theme.selectedAutoNightType;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.themeListUpdated);
        if (currentType == THEME_TYPE_BASIC) {
            Theme.loadRemoteThemes(currentAccount, true);
            Theme.checkCurrentRemoteTheme(true);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        stopLocationUpdate();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.themeListUpdated);
        Theme.saveAutoNightThemeConfig();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.locationPermissionGranted) {
            updateSunTime(null, true);
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (listView != null) {
                listView.invalidateViews();
            }
        } else if (id == NotificationCenter.themeListUpdated) {
            updateRows(true);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        if (currentType == THEME_TYPE_BASIC) {
            actionBar.setTitle(LocaleController.getString("ChatSettings", R.string.ChatSettings));
            ActionBarMenu menu = actionBar.createMenu();
            ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
            item.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
            item.addSubItem(create_theme, R.drawable.menu_palette, LocaleController.getString("CreateNewThemeMenu", R.string.CreateNewThemeMenu));
        } else {
            actionBar.setTitle(LocaleController.getString("AutoNightTheme", R.string.AutoNightTheme));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == create_theme) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("NewTheme", R.string.NewTheme));
                    builder.setMessage(LocaleController.getString("CreateNewThemeAlert", R.string.CreateNewThemeAlert));
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.setPositiveButton(LocaleController.getString("CreateTheme", R.string.CreateTheme), (dialog, which) -> openThemeCreate());
                    showDialog(builder.create());
                }
            }
        });

        listAdapter = new ListAdapter(context);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == enableAnimationsRow) {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                boolean animations = preferences.getBoolean("view_animations", true);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("view_animations", !animations);
                editor.commit();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(!animations);
                }
            } else if (position == backgroundRow) {
                presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL));
            } else if (position == sendByEnterRow) {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                boolean send = preferences.getBoolean("send_by_enter", false);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("send_by_enter", !send);
                editor.commit();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(!send);
                }
            } else if (position == raiseToSpeakRow) {
                SharedConfig.toogleRaiseToSpeak();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.raiseToSpeak);
                }
            } else if (position == saveToGalleryRow) {
                SharedConfig.toggleSaveToGallery();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.saveToGallery);
                }
            } else if (position == distanceRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("DistanceUnitsTitle", R.string.DistanceUnitsTitle));
                builder.setItems(new CharSequence[]{
                        LocaleController.getString("DistanceUnitsAutomatic", R.string.DistanceUnitsAutomatic),
                        LocaleController.getString("DistanceUnitsKilometers", R.string.DistanceUnitsKilometers),
                        LocaleController.getString("DistanceUnitsMiles", R.string.DistanceUnitsMiles)
                }, (dialog, which) -> {
                    SharedConfig.setDistanceSystemType(which);
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(distanceRow);
                    if (holder != null) {
                        listAdapter.onBindViewHolder(holder, distanceRow);
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == customTabsRow) {
                SharedConfig.toggleCustomTabs();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.customTabs);
                }
            } else if (position == directShareRow) {
                SharedConfig.toggleDirectShare();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.directShare);
                }
            } else if (position == contactsReimportRow) {
                //not implemented
            } else if (position == contactsSortRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("SortBy", R.string.SortBy));
                builder.setItems(new CharSequence[]{
                        LocaleController.getString("Default", R.string.Default),
                        LocaleController.getString("SortFirstName", R.string.SortFirstName),
                        LocaleController.getString("SortLastName", R.string.SortLastName)
                }, (dialog, which) -> {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("sortContactsBy", which);
                    editor.commit();
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(position);
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == stickersRow) {
                presentFragment(new StickersActivity(MediaDataController.TYPE_IMAGE));
            } else if (position == emojiRow) {
                SharedConfig.toggleBigEmoji();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.allowBigEmoji);
                }
            } else if (position == nightThemeRow) {
                if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) view;
                    if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE) {
                        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_AUTOMATIC;
                        checkCell.setChecked(true);
                    } else {
                        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                        checkCell.setChecked(false);
                    }
                    Theme.saveAutoNightThemeConfig();
                    Theme.checkAutoNightThemeConditions();
                    boolean enabled = Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE;
                    String value = enabled ? Theme.getCurrentNightThemeName() : LocaleController.getString("AutoNightThemeOff", R.string.AutoNightThemeOff);
                    if (enabled) {
                        String type = Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED ? LocaleController.getString("AutoNightScheduled", R.string.AutoNightScheduled) : LocaleController.getString("AutoNightAdaptive", R.string.AutoNightAdaptive);
                        value = type + " " + value;
                    }
                    checkCell.setTextAndValueAndCheck(LocaleController.getString("AutoNightTheme", R.string.AutoNightTheme), value, enabled, true);
                } else {
                    presentFragment(new ThemeActivity(THEME_TYPE_NIGHT));
                }
            } else if (position == nightDisabledRow) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                updateRows(true);
                Theme.checkAutoNightThemeConditions();
            } else if (position == nightScheduledRow) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SCHEDULED;
                if (Theme.autoNightScheduleByLocation) {
                    updateSunTime(null, true);
                }
                updateRows(true);
                Theme.checkAutoNightThemeConditions();
            } else if (position == nightAutomaticRow) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_AUTOMATIC;
                updateRows(true);
                Theme.checkAutoNightThemeConditions();
            } else if (position == scheduleLocationRow) {
                Theme.autoNightScheduleByLocation = !Theme.autoNightScheduleByLocation;
                TextCheckCell checkCell = (TextCheckCell) view;
                checkCell.setChecked(Theme.autoNightScheduleByLocation);
                updateRows(true);
                if (Theme.autoNightScheduleByLocation) {
                    updateSunTime(null, true);
                }
                Theme.checkAutoNightThemeConditions();
            } else if (position == scheduleFromRow || position == scheduleToRow) {
                if (getParentActivity() == null) {
                    return;
                }
                int currentHour;
                int currentMinute;
                if (position == scheduleFromRow) {
                    currentHour = Theme.autoNightDayStartTime / 60;
                    currentMinute = (Theme.autoNightDayStartTime - currentHour * 60);
                } else {
                    currentHour = Theme.autoNightDayEndTime / 60;
                    currentMinute = (Theme.autoNightDayEndTime - currentHour * 60);
                }
                final TextSettingsCell cell = (TextSettingsCell) view;
                TimePickerDialog dialog = new TimePickerDialog(getParentActivity(), (view1, hourOfDay, minute) -> {
                    int time = hourOfDay * 60 + minute;
                    if (position == scheduleFromRow) {
                        Theme.autoNightDayStartTime = time;
                        cell.setTextAndValue(LocaleController.getString("AutoNightFrom", R.string.AutoNightFrom), String.format("%02d:%02d", hourOfDay, minute), true);
                    } else {
                        Theme.autoNightDayEndTime = time;
                        cell.setTextAndValue(LocaleController.getString("AutoNightTo", R.string.AutoNightTo), String.format("%02d:%02d", hourOfDay, minute), true);
                    }
                }, currentHour, currentMinute, true);
                showDialog(dialog);
            } else if (position == scheduleUpdateLocationRow) {
                updateSunTime(null, true);
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            updateRows(true);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    private void openThemeCreate() {
        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("NewTheme", R.string.NewTheme));
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString("Create", R.string.Create), (dialog, which) -> {

        });

        LinearLayout linearLayout = new LinearLayout(getParentActivity());
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        builder.setView(linearLayout);

        final TextView message = new TextView(getParentActivity());
        message.setText(LocaleController.formatString("EnterThemeName", R.string.EnterThemeName));
        message.setTextSize(16);
        message.setPadding(AndroidUtilities.dp(23), AndroidUtilities.dp(12), AndroidUtilities.dp(23), AndroidUtilities.dp(6));
        message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        linearLayout.addView(message, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setMaxLines(1);
        editText.setLines(1);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setGravity(Gravity.LEFT | Gravity.TOP);
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 24, 6, 24, 0));
        editText.setOnEditorActionListener((textView, i, keyEvent) -> {
            AndroidUtilities.hideKeyboard(textView);
            return false;
        });
        editText.setText(generateThemeName());
        editText.setSelection(editText.length());

        final AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(dialog -> AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }));
        showDialog(alertDialog);
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (editText.length() == 0) {
                Vibrator vibrator = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    vibrator.vibrate(200);
                }
                AndroidUtilities.shakeView(editText, 2, 0);
                return;
            }
            ThemeEditorView themeEditorView = new ThemeEditorView();
            themeEditorView.show(getParentActivity(), Theme.createNewTheme(editText.getText().toString()));
            updateRows(true);
            alertDialog.dismiss();

            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences.getBoolean("themehint", false)) {
                return;
            }
            preferences.edit().putBoolean("themehint", true).commit();
            try {
                Toast.makeText(getParentActivity(), LocaleController.getString("CreateNewThemeHelp", R.string.CreateNewThemeHelp), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void updateSunTime(Location lastKnownLocation, boolean forceUpdate) {
        LocationManager locationManager = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) {
            Activity activity = getParentActivity();
            if (activity != null) {
                if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                    return;
                }
            }
        }
        if (getParentActivity() != null) {
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
                    return;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        try {
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation == null) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastKnownLocation == null) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (lastKnownLocation == null || forceUpdate) {
            startLocationUpdate();
            if (lastKnownLocation == null) {
                return;
            }
        }
        Theme.autoNightLocationLatitude = lastKnownLocation.getLatitude();
        Theme.autoNightLocationLongitude = lastKnownLocation.getLongitude();
        int[] time = SunDate.calculateSunriseSunset(Theme.autoNightLocationLatitude, Theme.autoNightLocationLongitude);
        Theme.autoNightSunriseTime = time[0];
        Theme.autoNightSunsetTime = time[1];
        Theme.autoNightCityName = null;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        Theme.autoNightLastSunCheckDay = calendar.get(Calendar.DAY_OF_MONTH);
        Utilities.globalQueue.postRunnable(() -> {
            String name;
            try {
                Geocoder gcd = new Geocoder(ApplicationLoader.applicationContext, Locale.getDefault());
                List<Address> addresses = gcd.getFromLocation(Theme.autoNightLocationLatitude, Theme.autoNightLocationLongitude, 1);
                if (addresses.size() > 0) {
                    name = addresses.get(0).getLocality();
                } else {
                    name = null;
                }
            } catch (Exception ignore) {
                name = null;
            }
            final String nameFinal = name;
            AndroidUtilities.runOnUIThread(() -> {
                Theme.autoNightCityName = nameFinal;
                if (Theme.autoNightCityName == null) {
                    Theme.autoNightCityName = String.format("(%.06f, %.06f)", Theme.autoNightLocationLatitude, Theme.autoNightLocationLongitude);
                }
                Theme.saveAutoNightThemeConfig();
                if (listView != null) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(scheduleUpdateLocationRow);
                    if (holder != null && holder.itemView instanceof TextSettingsCell) {
                        ((TextSettingsCell) holder.itemView).setTextAndValue(LocaleController.getString("AutoNightUpdateLocation", R.string.AutoNightUpdateLocation), Theme.autoNightCityName, false);
                    }
                }
            });
        });
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(scheduleLocationInfoRow);
        if (holder != null && holder.itemView instanceof TextInfoPrivacyCell) {
            ((TextInfoPrivacyCell) holder.itemView).setText(getLocationSunString());
        }
        if (Theme.autoNightScheduleByLocation && Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
            Theme.checkAutoNightThemeConditions();
        }
    }

    private void startLocationUpdate() {
        if (updatingLocation) {
            return;
        }
        updatingLocation = true;
        LocationManager locationManager = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 0, gpsLocationListener);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 0, networkLocationListener);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void stopLocationUpdate() {
        updatingLocation = false;
        LocationManager locationManager = (LocationManager) ApplicationLoader.applicationContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(gpsLocationListener);
        locationManager.removeUpdates(networkLocationListener);
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

    private String getLocationSunString() {
        int currentHour = Theme.autoNightSunriseTime / 60;
        int currentMinute = (Theme.autoNightSunriseTime - currentHour * 60);
        String sunriseTimeStr = String.format("%02d:%02d", currentHour, currentMinute);
        currentHour = Theme.autoNightSunsetTime / 60;
        currentMinute = (Theme.autoNightSunsetTime - currentHour * 60);
        String sunsetTimeStr = String.format("%02d:%02d", currentHour, currentMinute);
        return LocaleController.formatString("AutoNightUpdateLocationInfo", R.string.AutoNightUpdateLocationInfo, sunsetTimeStr, sunriseTimeStr);
    }

    private static class InnerAccentView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ObjectAnimator checkAnimator;
        private float checkedState;
        private Theme.ThemeInfo currentTheme;
        private int currentColor;

        InnerAccentView(Context context) {
            super(context);
        }

        void setThemeAndColor(Theme.ThemeInfo themeInfo, int color) {
            currentTheme = themeInfo;
            currentColor = color;
            updateCheckedState(false);
        }

        void updateCheckedState(boolean animate) {
            boolean checked = currentTheme.accentColor == currentColor;

            if (checkAnimator != null) {
                checkAnimator.cancel();
            }

            if (animate) {
                checkAnimator = ObjectAnimator.ofFloat(this, "checkedState", checked ? 1f : 0f);
                checkAnimator.setDuration(200);
                checkAnimator.start();
            } else {
                setCheckedState(checked ? 1f : 0f);
            }
        }

        @Keep
        public void setCheckedState(float state) {
            checkedState = state;
            invalidate();
        }

        @Keep
        public float getCheckedState() {
            return checkedState;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateCheckedState(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(62), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(62), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float radius = AndroidUtilities.dp(20);

            paint.setColor(currentColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(3));
            paint.setAlpha(Math.round(255f * checkedState));
            canvas.drawCircle(0.5f * getMeasuredWidth(), 0.5f * getMeasuredHeight(), radius - 0.5f * paint.getStrokeWidth(), paint);

            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(0.5f * getMeasuredWidth(), 0.5f * getMeasuredHeight(), radius - AndroidUtilities.dp(5) * checkedState, paint);
        }
    }


    private static class InnerCustomAccentView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[] colors = new int[7];

        InnerCustomAccentView(Context context) {
            super(context);
        }

        private void setTheme(Theme.ThemeInfo themeInfo) {
            int[] options = themeInfo == null ? null : themeInfo.accentColorOptions;
            if (options != null && options.length >= 8) {
                colors = new int[] { options[6], options[4], options[7], options[2], options[0], options[5], options[3] };
            } else {
                colors = new int[7];
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(62), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(62), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float centerX = 0.5f * getMeasuredWidth();
            float centerY = 0.5f * getMeasuredHeight();

            float radSmall = AndroidUtilities.dp(5);
            float radRing = AndroidUtilities.dp(20) - radSmall;

            paint.setStyle(Paint.Style.FILL);

            paint.setColor(colors[0]);
            canvas.drawCircle(centerX, centerY, radSmall, paint);

            double angle = 0.0;
            for (int a = 0; a < 6; a++) {
                float cx = centerX + radRing * (float) Math.sin(angle);
                float cy = centerY - radRing * (float) Math.cos(angle);

                paint.setColor(colors[a + 1]);
                canvas.drawCircle(cx, cy, radSmall, paint);

                angle += Math.PI / 3;
            }
        }
    }

    private class ThemeAccentsListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private Theme.ThemeInfo currentTheme;
        private int[] options;
        private boolean hasExtraColor;
        private int extraColor;

        ThemeAccentsListAdapter(Context context) {
            mContext = context;
            setHasStableIds(true);
            notifyDataSetChanged();
        }

        @Override
        public void notifyDataSetChanged() {
            currentTheme = currentType == THEME_TYPE_NIGHT ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
            options = currentTheme.accentColorOptions;

            if (options != null && ArrayUtils.indexOf(options, currentTheme.accentColor) == -1) {
                extraColor = currentTheme.accentColor;
                hasExtraColor = true;
            }

            super.notifyDataSetChanged();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public long getItemId(int position) {
            return getAccentColor(position);
        }

        @Override
        public int getItemViewType(int position) {
            return position == getItemCount() - 1 ? 1 : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            switch (viewType) {
                case 0:
                    return new RecyclerListView.Holder(new InnerAccentView(mContext));
                case 1:
                default:
                    return new RecyclerListView.Holder(new InnerCustomAccentView(mContext));
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (getItemViewType(position)) {
                case 0: {
                    InnerAccentView view = (InnerAccentView) holder.itemView;
                    view.setThemeAndColor(currentTheme, getAccentColor(position));
                    break;
                }
                case 1: {
                    InnerCustomAccentView view = (InnerCustomAccentView) holder.itemView;
                    view.setTheme(currentTheme);
                    break;
                }
            }
        }

        @Override
        public int getItemCount() {
            return options == null ? 0 : options.length + (hasExtraColor ? 1 : 0) + 1;
        }

        int getAccentColor(int pos) {
            if (options == null) {
                return 0;
            }
            if (hasExtraColor && pos == options.length) {
                return extraColor;
            } else if (pos < options.length) {
                return options[pos];
            } else {
                return 0;
            }
        }

        int findCurrentAccent() {
            if (hasExtraColor && extraColor == currentTheme.accentColor) {
                return options.length;
            } else {
                return ArrayUtils.indexOf(options, currentTheme.accentColor);
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private boolean first = true;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 1 || type == 4 || type == 7 || type == 10 || type == 11 || type == 12;
        }

        private void showOptionsForTheme(Theme.ThemeInfo themeInfo) {
            if (getParentActivity() == null || themeInfo.info != null && !themeInfo.themeLoaded || currentType == THEME_TYPE_NIGHT) {
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            CharSequence[] items;
            int[] icons;
            boolean hasDelete;
            if (themeInfo.pathToFile == null) {
                hasDelete = false;
                items = new CharSequence[]{
                        null,
                        LocaleController.getString("ExportTheme", R.string.ExportTheme)
                };
                icons = new int[]{
                        0,
                        R.drawable.msg_shareout
                };
            } else {
                hasDelete = themeInfo.info == null || !themeInfo.info.isDefault;
                items = new CharSequence[]{
                        themeInfo.info != null ? LocaleController.getString("ShareFile", R.string.ShareFile) : null,
                        LocaleController.getString("ExportTheme", R.string.ExportTheme),
                        themeInfo.info == null || !themeInfo.info.isDefault && themeInfo.info.creator ? LocaleController.getString("Edit", R.string.Edit) : null,
                        themeInfo.info != null && themeInfo.info.creator ? LocaleController.getString("ThemeSetUrl", R.string.ThemeSetUrl) : null,
                        hasDelete ? LocaleController.getString("Delete", R.string.Delete) : null};
                icons = new int[]{
                        R.drawable.msg_share,
                        R.drawable.msg_shareout,
                        R.drawable.msg_edit,
                        R.drawable.msg_link,
                        R.drawable.msg_delete
                };
            }
            builder.setItems(items, icons, (dialog, which) -> {
                if (getParentActivity() == null) {
                    return;
                }
                if (which == 0) {
                    String link = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addtheme/" + themeInfo.info.slug;
                    showDialog(new ShareAlert(getParentActivity(), null, link, false, link, false));
                } else if (which == 1) {
                    File currentFile;
                    if (themeInfo.pathToFile == null && themeInfo.assetName == null) {
                        StringBuilder result = new StringBuilder();
                        for (HashMap.Entry<String, Integer> entry : Theme.getDefaultColors().entrySet()) {
                            result.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
                        }
                        currentFile = new File(ApplicationLoader.getFilesDirFixed(), "default_theme.attheme");
                        FileOutputStream stream = null;
                        try {
                            stream = new FileOutputStream(currentFile);
                            stream.write(AndroidUtilities.getStringBytes(result.toString()));
                        } catch (Exception e) {
                            FileLog.e(e);
                        } finally {
                            try {
                                if (stream != null) {
                                    stream.close();
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    } else if (themeInfo.assetName != null) {
                        currentFile = Theme.getAssetFile(themeInfo.assetName);
                    } else {
                        currentFile = new File(themeInfo.pathToFile);
                    }
                    File finalFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), FileLoader.fixFileName(currentFile.getName()));
                    try {
                        if (!AndroidUtilities.copyFile(currentFile, finalFile)) {
                            return;
                        }
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/xml");
                        if (Build.VERSION.SDK_INT >= 24) {
                            try {
                                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", finalFile));
                                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignore) {
                                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(finalFile));
                            }
                        } else {
                            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(finalFile));
                        }
                        startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (which == 2) {
                    if (parentLayout != null) {
                        Theme.applyTheme(themeInfo);
                        parentLayout.rebuildAllFragmentViews(true, true);
                        new ThemeEditorView().show(getParentActivity(), themeInfo);
                    }
                } else if (which == 3) {
                    presentFragment(new ThemeSetUrlActivity(themeInfo, false));
                } else {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                    builder1.setMessage(LocaleController.getString("DeleteThemeAlert", R.string.DeleteThemeAlert));
                    builder1.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder1.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                        getMessagesController().saveTheme(themeInfo, themeInfo == Theme.getCurrentNightTheme(), true);
                        if (Theme.deleteTheme(themeInfo)) {
                            parentLayout.rebuildAllFragmentViews(true, true);
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeListUpdated);
                    });
                    builder1.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder1.create());
                }
            });
            AlertDialog alertDialog = builder.create();
            showDialog(alertDialog);
            if (hasDelete) {
                alertDialog.setItemColor(alertDialog.getItemsCount() - 1, Theme.getColor(Theme.key_dialogTextRed2), Theme.getColor(Theme.key_dialogRedIcon));
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 3:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 4:
                    view = new ThemeTypeCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new BrightnessControlCell(mContext) {
                        @Override
                        protected void didChangedValue(float value) {
                            int oldValue = (int) (Theme.autoNightBrighnessThreshold * 100);
                            int newValue = (int) (value * 100);
                            Theme.autoNightBrighnessThreshold = value;
                            if (oldValue != newValue) {
                                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(automaticBrightnessInfoRow);
                                if (holder != null) {
                                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                                    cell.setText(LocaleController.formatString("AutoNightBrightnessInfo", R.string.AutoNightBrightnessInfo, (int) (100 * Theme.autoNightBrighnessThreshold)));
                                }
                                Theme.checkAutoNightThemeConditions(true);
                            }
                        }
                    };
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 7:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 8:
                    view = new TextSizeCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 9:
                    view = new ChatListCell(mContext) {
                        @Override
                        protected void didSelectChatType(boolean threeLines) {
                            SharedConfig.setUseThreeLinesLayout(threeLines);
                        }
                    };
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 10:
                    view = new NotificationsCheckCell(mContext, 21, 64);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 11:
                    first = true;
                    themesHorizontalListCell = new ThemesHorizontalListCell(mContext, currentType, defaultThemes, darkThemes) {
                        @Override
                        protected void showOptionsForTheme(Theme.ThemeInfo themeInfo) {
                            listAdapter.showOptionsForTheme(themeInfo);
                        }

                        @Override
                        protected void presentFragment(BaseFragment fragment) {
                            ThemeActivity.this.presentFragment(fragment);
                        }

                        @Override
                        protected void updateRows() {
                            ThemeActivity.this.updateRows(false);
                        }
                    };
                    themesHorizontalListCell.setDrawDivider(hasThemeAccents);
                    view = themesHorizontalListCell;
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(148)));
                    break;
                case 12:
                default: {
                    RecyclerListView accentsListView = new TintRecyclerListView(mContext) {
                        @Override
                        public boolean onInterceptTouchEvent(MotionEvent e) {
                            if (getParent() != null && getParent().getParent() != null) {
                                getParent().getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            return super.onInterceptTouchEvent(e);
                        }
                    };
                    accentsListView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    accentsListView.setItemAnimator(null);
                    accentsListView.setLayoutAnimation(null);
                    accentsListView.setPadding(AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11), 0);
                    accentsListView.setClipToPadding(false);
                    LinearLayoutManager accentsLayoutManager = new LinearLayoutManager(mContext);
                    accentsLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    accentsListView.setLayoutManager(accentsLayoutManager);
                    ThemeAccentsListAdapter accentsAdapter = new ThemeAccentsListAdapter(mContext);
                    accentsListView.setAdapter(accentsAdapter);
                    accentsListView.setOnItemClickListener((view1, position) -> {
                        Theme.ThemeInfo currentTheme = currentType == THEME_TYPE_NIGHT ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();

                        if (position == accentsAdapter.getItemCount() - 1) {
                            presentFragment(new ThemePreviewActivity(currentTheme, false, ThemePreviewActivity.SCREEN_TYPE_ACCENT_COLOR, currentType == THEME_TYPE_NIGHT));
                        } else {
                            int newAccent = accentsAdapter.getAccentColor(position);
                            if (currentTheme.accentColor != newAccent) {
                                Theme.saveThemeAccent(currentTheme, newAccent);
                                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentTheme, currentType == THEME_TYPE_NIGHT);
                            }
                        }

                        int left = view1.getLeft();
                        int right = view1.getRight();
                        int extra = AndroidUtilities.dp(52);
                        if (left - extra < 0) {
                            accentsListView.smoothScrollBy(left - extra, 0);
                        } else if (right + extra > accentsListView.getMeasuredWidth()) {
                            accentsListView.smoothScrollBy(right + extra - accentsListView.getMeasuredWidth(), 0);
                        }

                        int count = accentsListView.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = accentsListView.getChildAt(a);
                            if (child instanceof InnerAccentView) {
                                ((InnerAccentView) child).updateCheckedState(true);
                            }
                        }
                    });

                    view = accentsListView;
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(62)));
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == nightThemeRow) {
                        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE || Theme.getCurrentNightTheme() == null) {
                            cell.setTextAndValue(LocaleController.getString("AutoNightTheme", R.string.AutoNightTheme), LocaleController.getString("AutoNightThemeOff", R.string.AutoNightThemeOff), false);
                        } else {
                            cell.setTextAndValue(LocaleController.getString("AutoNightTheme", R.string.AutoNightTheme), Theme.getCurrentNightThemeName(), false);
                        }
                    } else if (position == scheduleFromRow) {
                        int currentHour = Theme.autoNightDayStartTime / 60;
                        int currentMinute = (Theme.autoNightDayStartTime - currentHour * 60);
                        cell.setTextAndValue(LocaleController.getString("AutoNightFrom", R.string.AutoNightFrom), String.format("%02d:%02d", currentHour, currentMinute), true);
                    } else if (position == scheduleToRow) {
                        int currentHour = Theme.autoNightDayEndTime / 60;
                        int currentMinute = (Theme.autoNightDayEndTime - currentHour * 60);
                        cell.setTextAndValue(LocaleController.getString("AutoNightTo", R.string.AutoNightTo), String.format("%02d:%02d", currentHour, currentMinute), false);
                    } else if (position == scheduleUpdateLocationRow) {
                        cell.setTextAndValue(LocaleController.getString("AutoNightUpdateLocation", R.string.AutoNightUpdateLocation), Theme.autoNightCityName, false);
                    } else if (position == contactsSortRow) {
                        String value;
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        int sort = preferences.getInt("sortContactsBy", 0);
                        if (sort == 0) {
                            value = LocaleController.getString("Default", R.string.Default);
                        } else if (sort == 1) {
                            value = LocaleController.getString("FirstName", R.string.SortFirstName);
                        } else {
                            value = LocaleController.getString("LastName", R.string.SortLastName);
                        }
                        cell.setTextAndValue(LocaleController.getString("SortBy", R.string.SortBy), value, true);
                    } else if (position == backgroundRow) {
                        cell.setText(LocaleController.getString("ChangeChatBackground", R.string.ChangeChatBackground), false);
                    } else if (position == contactsReimportRow) {
                        cell.setText(LocaleController.getString("ImportContacts", R.string.ImportContacts), true);
                    } else if (position == stickersRow) {
                        cell.setText(LocaleController.getString("StickersAndMasks", R.string.StickersAndMasks), false);
                    } else if (position == distanceRow) {
                        String value;
                        if (SharedConfig.distanceSystemType == 0) {
                            value = LocaleController.getString("DistanceUnitsAutomatic", R.string.DistanceUnitsAutomatic);
                        } else if (SharedConfig.distanceSystemType == 1) {
                            value = LocaleController.getString("DistanceUnitsKilometers", R.string.DistanceUnitsKilometers);
                        } else {
                            value = LocaleController.getString("DistanceUnitsMiles", R.string.DistanceUnitsMiles);
                        }
                        cell.setTextAndValue(LocaleController.getString("DistanceUnits", R.string.DistanceUnits), value, false);
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == automaticBrightnessInfoRow) {
                        cell.setText(LocaleController.formatString("AutoNightBrightnessInfo", R.string.AutoNightBrightnessInfo, (int) (100 * Theme.autoNightBrighnessThreshold)));
                    } else if (position == scheduleLocationInfoRow) {
                        cell.setText(getLocationSunString());
                    }
                    break;
                }
                case 3: {
                    if (position == stickersSection2Row || position == nightTypeInfoRow && themeInfoRow == -1 || position == themeInfoRow && nightTypeInfoRow != -1) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 4: {
                    ThemeTypeCell typeCell = (ThemeTypeCell) holder.itemView;
                    if (position == nightDisabledRow) {
                        typeCell.setValue(LocaleController.getString("AutoNightDisabled", R.string.AutoNightDisabled), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE, true);
                    } else if (position == nightScheduledRow) {
                        typeCell.setValue(LocaleController.getString("AutoNightScheduled", R.string.AutoNightScheduled), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED, true);
                    } else if (position == nightAutomaticRow) {
                        typeCell.setValue(LocaleController.getString("AutoNightAdaptive", R.string.AutoNightAdaptive), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC, false);
                    }
                    break;
                }
                case 5: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == scheduleHeaderRow) {
                        headerCell.setText(LocaleController.getString("AutoNightSchedule", R.string.AutoNightSchedule));
                    } else if (position == automaticHeaderRow) {
                        headerCell.setText(LocaleController.getString("AutoNightBrightness", R.string.AutoNightBrightness));
                    } else if (position == preferedHeaderRow) {
                        headerCell.setText(LocaleController.getString("AutoNightPreferred", R.string.AutoNightPreferred));
                    } else if (position == settingsRow) {
                        headerCell.setText(LocaleController.getString("SETTINGS", R.string.SETTINGS));
                    } else if (position == themeHeaderRow) {
                        headerCell.setText(LocaleController.getString("ColorTheme", R.string.ColorTheme));
                    } else if (position == textSizeHeaderRow) {
                        headerCell.setText(LocaleController.getString("TextSizeHeader", R.string.TextSizeHeader));
                    } else if (position == chatListHeaderRow) {
                        headerCell.setText(LocaleController.getString("ChatList", R.string.ChatList));
                    }
                    break;
                }
                case 6: {
                    BrightnessControlCell cell = (BrightnessControlCell) holder.itemView;
                    cell.setProgress(Theme.autoNightBrighnessThreshold);
                    break;
                }
                case 7: {
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == scheduleLocationRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("AutoNightLocation", R.string.AutoNightLocation), Theme.autoNightScheduleByLocation, true);
                    } else if (position == enableAnimationsRow) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        textCheckCell.setTextAndCheck(LocaleController.getString("EnableAnimations", R.string.EnableAnimations), preferences.getBoolean("view_animations", true), true);
                    } else if (position == sendByEnterRow) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        textCheckCell.setTextAndCheck(LocaleController.getString("SendByEnter", R.string.SendByEnter), preferences.getBoolean("send_by_enter", false), true);
                    } else if (position == saveToGalleryRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("SaveToGallerySettings", R.string.SaveToGallerySettings), SharedConfig.saveToGallery, true);
                    } else if (position == raiseToSpeakRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("RaiseToSpeak", R.string.RaiseToSpeak), SharedConfig.raiseToSpeak, true);
                    } else if (position == customTabsRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("ChromeCustomTabs", R.string.ChromeCustomTabs), LocaleController.getString("ChromeCustomTabsInfo", R.string.ChromeCustomTabsInfo), SharedConfig.customTabs, false, true);
                    } else if (position == directShareRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("DirectShare", R.string.DirectShare), LocaleController.getString("DirectShareInfo", R.string.DirectShareInfo), SharedConfig.directShare, false, true);
                    } else if (position == emojiRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("LargeEmoji", R.string.LargeEmoji), SharedConfig.allowBigEmoji, true);
                    }
                    break;
                }
                case 10: {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                    if (position == nightThemeRow) {
                        boolean enabled = Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE;
                        String value = enabled ? Theme.getCurrentNightThemeName() : LocaleController.getString("AutoNightThemeOff", R.string.AutoNightThemeOff);
                        if (enabled) {
                            String type = Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED ? LocaleController.getString("AutoNightScheduled", R.string.AutoNightScheduled) : LocaleController.getString("AutoNightAdaptive", R.string.AutoNightAdaptive);
                            value = type + " " + value;
                        }
                        checkCell.setTextAndValueAndCheck(LocaleController.getString("AutoNightTheme", R.string.AutoNightTheme), value, enabled, true);
                    }
                    break;
                }
                case 11: {
                    if (first) {
                        themesHorizontalListCell.scrollToCurrentTheme(listView.getMeasuredWidth(), false);
                        first = false;
                    }
                    break;
                }
                case 12: {
                    RecyclerListView accentsList = (RecyclerListView) holder.itemView;
                    ThemeAccentsListAdapter adapter = (ThemeAccentsListAdapter) accentsList.getAdapter();
                    adapter.notifyDataSetChanged();
                    int pos = adapter.findCurrentAccent();
                    if (pos == -1) {
                        pos = adapter.getItemCount() - 1;
                    }
                    if (pos != -1) {
                        ((LinearLayoutManager) accentsList.getLayoutManager()).scrollToPositionWithOffset(pos, listView.getMeasuredWidth() / 2 - AndroidUtilities.dp(42));
                    }
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == 4) {
                ((ThemeTypeCell) holder.itemView).setTypeChecked(holder.getAdapterPosition() == Theme.selectedAutoNightType);
            }
            if (type != 2 && type != 3) {
                holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == scheduleFromRow || position == distanceRow ||
                    position == scheduleToRow || position == scheduleUpdateLocationRow || position == backgroundRow ||
                    position == contactsReimportRow || position == contactsSortRow || position == stickersRow) {
                return 1;
            } else if (position == automaticBrightnessInfoRow || position == scheduleLocationInfoRow) {
                return 2;
            } else if (position == themeInfoRow || position == nightTypeInfoRow || position == scheduleFromToInfoRow ||
                    position == stickersSection2Row || position == settings2Row || position == newThemeInfoRow ||
                    position == chatListInfoRow) {
                return 3;
            } else if (position == nightDisabledRow || position == nightScheduledRow || position == nightAutomaticRow) {
                return 4;
            } else if (position == scheduleHeaderRow || position == automaticHeaderRow || position == preferedHeaderRow ||
                    position == settingsRow || position == themeHeaderRow || position == textSizeHeaderRow ||
                    position == chatListHeaderRow) {
                return 5;
            } else if (position == automaticBrightnessRow) {
                return 6;
            } else if (position == scheduleLocationRow || position == enableAnimationsRow || position == sendByEnterRow ||
                    position == saveToGalleryRow || position == raiseToSpeakRow || position == customTabsRow ||
                    position == directShareRow || position == emojiRow) {
                return 7;
            } else if (position == textSizeRow) {
                return 8;
            } else if (position == chatListRow) {
                return 9;
            } else if (position == nightThemeRow) {
                return 10;
            } else if (position == themeListRow) {
                return 11;
            } else if (position == themeAccentListRow) {
                return 12;
            }
            return 1;
        }
    }

    private static abstract class TintRecyclerListView extends RecyclerListView {
        TintRecyclerListView(Context context) {
            super(context);
        }
    }

    private String generateThemeName() {
        List<String> adjectives = Arrays.asList(
                "Ancient",
                "Antique",
                "Autumn",
                "Baby",
                "Barely",
                "Baroque",
                "Blazing",
                "Blushing",
                "Bohemian",
                "Bubbly",
                "Burning",
                "Buttered",
                "Classic",
                "Clear",
                "Cool",
                "Cosmic",
                "Cotton",
                "Cozy",
                "Crystal",
                "Dark",
                "Daring",
                "Darling",
                "Dawn",
                "Dazzling",
                "Deep",
                "Deepest",
                "Delicate",
                "Delightful",
                "Divine",
                "Double",
                "Downtown",
                "Dreamy",
                "Dusky",
                "Dusty",
                "Electric",
                "Enchanted",
                "Endless",
                "Evening",
                "Fantastic",
                "Flirty",
                "Forever",
                "Frigid",
                "Frosty",
                "Frozen",
                "Gentle",
                "Heavenly",
                "Hyper",
                "Icy",
                "Infinite",
                "Innocent",
                "Instant",
                "Luscious",
                "Lunar",
                "Lustrous",
                "Magic",
                "Majestic",
                "Mambo",
                "Midnight",
                "Millenium",
                "Morning",
                "Mystic",
                "Natural",
                "Neon",
                "Night",
                "Opaque",
                "Paradise",
                "Perfect",
                "Perky",
                "Polished",
                "Powerful",
                "Rich",
                "Royal",
                "Sheer",
                "Simply",
                "Sizzling",
                "Solar",
                "Sparkling",
                "Splendid",
                "Spicy",
                "Spring",
                "Stellar",
                "Sugared",
                "Summer",
                "Sunny",
                "Super",
                "Sweet",
                "Tender",
                "Tenacious",
                "Tidal",
                "Toasted",
                "Totally",
                "Tranquil",
                "Tropical",
                "True",
                "Twilight",
                "Twinkling",
                "Ultimate",
                "Ultra",
                "Velvety",
                "Vibrant",
                "Vintage",
                "Virtual",
                "Warm",
                "Warmest",
                "Whipped",
                "Wild",
                "Winsome"
        );

        List<String> subjectives = Arrays.asList(
                "Ambrosia",
                "Attack",
                "Avalanche",
                "Blast",
                "Bliss",
                "Blossom",
                "Blush",
                "Burst",
                "Butter",
                "Candy",
                "Carnival",
                "Charm",
                "Chiffon",
                "Cloud",
                "Comet",
                "Delight",
                "Dream",
                "Dust",
                "Fantasy",
                "Flame",
                "Flash",
                "Fire",
                "Freeze",
                "Frost",
                "Glade",
                "Glaze",
                "Gleam",
                "Glimmer",
                "Glitter",
                "Glow",
                "Grande",
                "Haze",
                "Highlight",
                "Ice",
                "Illusion",
                "Intrigue",
                "Jewel",
                "Jubilee",
                "Kiss",
                "Lights",
                "Lollypop",
                "Love",
                "Luster",
                "Madness",
                "Matte",
                "Mirage",
                "Mist",
                "Moon",
                "Muse",
                "Myth",
                "Nectar",
                "Nova",
                "Parfait",
                "Passion",
                "Pop",
                "Rain",
                "Reflection",
                "Rhapsody",
                "Romance",
                "Satin",
                "Sensation",
                "Silk",
                "Shine",
                "Shadow",
                "Shimmer",
                "Sky",
                "Spice",
                "Star",
                "Sugar",
                "Sunrise",
                "Sunset",
                "Sun",
                "Twist",
                "Unbound",
                "Velvet",
                "Vibrant",
                "Waters",
                "Wine",
                "Wink",
                "Wonder",
                "Zone"
        );

        HashMap<Integer, String> colors = new HashMap<>();
        colors.put(0x8e0000, "Berry");
        colors.put(0xdec196, "Brandy");
        colors.put(0x800b47, "Cherry");
        colors.put(0xff7f50, "Coral");
        colors.put(0xdb5079, "Cranberry");
        colors.put(0xdc143c, "Crimson");
        colors.put(0xe0b0ff, "Mauve");
        colors.put(0xffc0cb, "Pink");
        colors.put(0xff0000, "Red");
        colors.put(0xff007f, "Rose");
        colors.put(0x80461b, "Russet");
        colors.put(0xff2400, "Scarlet");
        colors.put(0xf1f1f1, "Seashell");
        colors.put(0xff3399, "Strawberry");
        colors.put(0xffbf00, "Amber");
        colors.put(0xeb9373, "Apricot");
        colors.put(0xfbe7b2, "Banana");
        colors.put(0xa1c50a, "Citrus");
        colors.put(0xb06500, "Ginger");
        colors.put(0xffd700, "Gold");
        colors.put(0xfde910, "Lemon");
        colors.put(0xffa500, "Orange");
        colors.put(0xffe5b4, "Peach");
        colors.put(0xff6b53, "Persimmon");
        colors.put(0xe4d422, "Sunflower");
        colors.put(0xf28500, "Tangerine");
        colors.put(0xffc87c, "Topaz");
        colors.put(0xffff00, "Yellow");
        colors.put(0x384910, "Clover");
        colors.put(0x83aa5d, "Cucumber");
        colors.put(0x50c878, "Emerald");
        colors.put(0xb5b35c, "Olive");
        colors.put(0x00ff00, "Green");
        colors.put(0x00a86b, "Jade");
        colors.put(0x29ab87, "Jungle");
        colors.put(0xbfff00, "Lime");
        colors.put(0x0bda51, "Malachite");
        colors.put(0x98ff98, "Mint");
        colors.put(0xaddfad, "Moss");
        colors.put(0x315ba1, "Azure");
        colors.put(0x0000ff, "Blue");
        colors.put(0x0047ab, "Cobalt");
        colors.put(0x4f69c6, "Indigo");
        colors.put(0x017987, "Lagoon");
        colors.put(0x71d9e2, "Aquamarine");
        colors.put(0x120a8f, "Ultramarine");
        colors.put(0x000080, "Navy");
        colors.put(0x2f519e, "Sapphire");
        colors.put(0x76d7ea, "Sky");
        colors.put(0x008080, "Teal");
        colors.put(0x40e0d0, "Turquoise");
        colors.put(0x9966cc, "Amethyst");
        colors.put(0x4d0135, "Blackberry");
        colors.put(0x614051, "Eggplant");
        colors.put(0xc8a2c8, "Lilac");
        colors.put(0xb57edc, "Lavender");
        colors.put(0xccccff, "Periwinkle");
        colors.put(0x843179, "Plum");
        colors.put(0x660099, "Purple");
        colors.put(0xd8bfd8, "Thistle");
        colors.put(0xda70d6, "Orchid");
        colors.put(0x240a40, "Violet");
        colors.put(0x3f2109, "Bronze");
        colors.put(0x370202, "Chocolate");
        colors.put(0x7b3f00, "Cinnamon");
        colors.put(0x301f1e, "Cocoa");
        colors.put(0x706555, "Coffee");
        colors.put(0x796989, "Rum");
        colors.put(0x4e0606, "Mahogany");
        colors.put(0x782d19, "Mocha");
        colors.put(0xc2b280, "Sand");
        colors.put(0x882d17, "Sienna");
        colors.put(0x780109, "Maple");
        colors.put(0xf0e68c, "Khaki");
        colors.put(0xb87333, "Copper");
        colors.put(0xb94e48, "Chestnut");
        colors.put(0xeed9c4, "Almond");
        colors.put(0xfffdd0, "Cream");
        colors.put(0xb9f2ff, "Diamond");
        colors.put(0xa98307, "Honey");
        colors.put(0xfffff0, "Ivory");
        colors.put(0xeae0c8, "Pearl");
        colors.put(0xeff2f3, "Porcelain");
        colors.put(0xd1bea8, "Vanilla");
        colors.put(0xffffff, "White");
        colors.put(0x808080, "Gray");
        colors.put(0x000000, "Black");
        colors.put(0xe8f1d4, "Chrome");
        colors.put(0x36454f, "Charcoal");
        colors.put(0x0c0b1d, "Ebony");
        colors.put(0xc0c0c0, "Silver");
        colors.put(0xf5f5f5, "Smoke");
        colors.put(0x262335, "Steel");
        colors.put(0x4fa83d, "Apple");
        colors.put(0x80b3c4, "Glacier");
        colors.put(0xfebaad, "Melon");
        colors.put(0xc54b8c, "Mulberry");
        colors.put(0xa9c6c2, "Opal");
        colors.put(0x54a5f8, "Blue");

        int color;
        Theme.ThemeInfo themeInfo = Theme.getCurrentTheme();
        if (themeInfo.accentColor != 0) {
            color = themeInfo.accentColor;
        } else {
            color = AndroidUtilities.calcDrawableColor(Theme.getCachedWallpaper())[0];
        }

        String minKey = null;
        int minValue = Integer.MAX_VALUE;
        int r1 = Color.red(color);
        int g1 = Color.green(color);
        int b1 = Color.blue(color);

        for (HashMap.Entry<Integer, String> entry : colors.entrySet()) {
            Integer value = entry.getKey();
            int r2 = Color.red(value);
            int g2 = Color.green(value);
            int b2 = Color.blue(value);

            int rMean = (r1 + r2) / 2;
            int r = r1 - r2;
            int g = g1 - g2;
            int b = b1 - b2;
            int d = (((512 + rMean) * r * r) >> 8) + (4 * g * g) + (((767 - rMean) * b * b) >> 8);

            if (d < minValue) {
                minKey = entry.getValue();
                minValue = d;
            }
        }
        String result;
        if (Utilities.random.nextInt() % 2 == 0) {
            result = adjectives.get(Utilities.random.nextInt(adjectives.size())) + " " + minKey;
        } else {
            result = minKey + " " + subjectives.get(Utilities.random.nextInt(subjectives.size()));
        }
        return result;
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, BrightnessControlCell.class, ThemeTypeCell.class, TextSizeCell.class, ChatListCell.class, NotificationsCheckCell.class, ThemesHorizontalListCell.class, TintRecyclerListView.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{BrightnessControlCell.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_profile_actionIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{BrightnessControlCell.class}, new String[]{"rightImageView"}, null, null, null, Theme.key_profile_actionIcon),
                new ThemeDescription(listView, 0, new Class[]{BrightnessControlCell.class}, new String[]{"seekBarView"}, null, null, null, Theme.key_player_progressBackground),
                new ThemeDescription(listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{BrightnessControlCell.class}, new String[]{"seekBarView"}, null, null, null, Theme.key_player_progress),

                new ThemeDescription(listView, 0, new Class[]{ThemeTypeCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ThemeTypeCell.class}, new String[]{"checkImage"}, null, null, null, Theme.key_featuredStickers_addedIcon),

                new ThemeDescription(listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{TextSizeCell.class}, new String[]{"sizeBar"}, null, null, null, Theme.key_player_progress),
                new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, new String[]{"sizeBar"}, null, null, null, Theme.key_player_progressBackground),

                new ThemeDescription(listView, 0, new Class[]{ChatListCell.class}, null, null, null, Theme.key_radioBackground),
                new ThemeDescription(listView, 0, new Class[]{ChatListCell.class}, null, null, null, Theme.key_radioBackgroundChecked),

                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInShadowDrawable, Theme.chat_msgInMediaShadowDrawable}, null, Theme.key_chat_inBubbleShadow),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutShadowDrawable, Theme.chat_msgOutMediaShadowDrawable}, null, Theme.key_chat_outBubbleShadow),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_messageTextIn),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_messageTextOut),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckDrawable}, null, Theme.key_chat_outSentCheck),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckReadDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheckRead),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckReadSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckReadSelected),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgMediaCheckDrawable, Theme.chat_msgMediaHalfCheckDrawable}, null, Theme.key_chat_mediaSentCheck),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyLine),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyLine),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyNameText),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyNameText),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyMessageText),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyMessageText),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inReplyMediaMessageSelectedText),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outReplyMediaMessageSelectedText),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inTimeText),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outTimeText),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_inTimeSelectedText),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_outTimeSelectedText),
        };
    }
}
