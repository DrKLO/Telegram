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
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
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
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.time.SunDate;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AppIconsSelectorCell;
import org.telegram.ui.Cells.BrightnessControlCell;
import org.telegram.ui.Cells.ChatListCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Cells.ThemeTypeCell;
import org.telegram.ui.Cells.ThemesHorizontalListCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PermissionRequest;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SimpleThemeDescription;
import org.telegram.ui.Components.SwipeGestureSettingsView;
import org.telegram.ui.Components.ThemeEditorView;
import org.telegram.ui.web.SearchEngine;
import org.telegram.ui.web.WebBrowserSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class ThemeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public final static int THEME_TYPE_BASIC = 0;
    public final static int THEME_TYPE_NIGHT = 1;
    public final static int THEME_TYPE_OTHER = 2;
    public final static int THEME_TYPE_THEMES_BROWSER = 3;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;
    private ThemesHorizontalListCell themesHorizontalListCell;

    private ArrayList<Theme.ThemeInfo> darkThemes = new ArrayList<>();
    private ArrayList<Theme.ThemeInfo> defaultThemes = new ArrayList<>();
    private int currentType;

    private Theme.ThemeInfo sharingTheme;
    private Theme.ThemeAccent sharingAccent;
    private AlertDialog sharingProgressDialog;
    private ActionBarMenuItem menuItem;

    boolean hasThemeAccents;

    private int backgroundRow;
    private int textSizeHeaderRow;
    private int textSizeRow;
    private int settingsRow;
    private int directShareRow;
    private int sensitiveContentRow;
    private int raiseToSpeakRow;
    private int raiseToListenRow;
    private int nextMediaTapRow;
    private int sendByEnterRow;
    private int saveToGalleryOption1Row;
    private int saveToGalleryOption2Row;
    private int saveToGallerySectionRow;
    private int distanceRow;
    private int searchEngineRow;
    private int bluetoothScoRow;
    private int enableAnimationsRow;
    private int settings2Row;
    private int changeUserColor;

    private int contactsReimportRow;
    private int contactsSortRow;

    private int nightThemeRow;
    private int browserRow;
    private int nightDisabledRow;
    private int nightScheduledRow;
    private int nightAutomaticRow;
    private int nightSystemDefaultRow;
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
    private int bubbleRadiusHeaderRow;
    private int bubbleRadiusRow;
    private int bubbleRadiusInfoRow;
    private int chatListHeaderRow;
    private int chatListRow;
    private int chatListInfoRow;
    private int themeListRow;
    private int themeListRow2;
    private int themeAccentListRow;
    private int themeInfoRow;
    private int chatBlurRow;
    private int pauseOnRecordRow;
    private int pauseOnMediaRow;

    private int swipeGestureHeaderRow;
    private int swipeGestureRow;
    private int swipeGestureInfoRow;

    private int selectThemeHeaderRow;
    private int themePreviewRow;
    private int editThemeRow;
    private int createNewThemeRow;
    private int lastShadowRow;
    private int stickersRow;
    private int stickersInfoRow;
    private int stickersSectionRow, mediaSoundSectionRow, otherSectionRow;
    private int mediaSoundHeaderRow, otherHeaderRow;
    private int liteModeRow;
    private int liteModeInfoRow;

    private int appIconHeaderRow;
    private int appIconSelectorRow;
    private int appIconShadowRow;

    private int rowCount;

    private boolean updatingLocation;

    private int previousUpdatedType;
    private boolean previousByLocation;

    private boolean updateRecordViaSco;
    private boolean updateDistance;
    private boolean updateSearchEngine;

    private GpsLocationListener gpsLocationListener = new GpsLocationListener();
    private GpsLocationListener networkLocationListener = new GpsLocationListener();

    private final static int create_theme = 1;
    private final static int share_theme = 2;
    private final static int edit_theme = 3;
    private final static int reset_settings = 4;
    private final static int day_night_switch = 5;

    private RLottieDrawable sunDrawable;

    private boolean highlightSensitiveRow;
    public ThemeActivity highlightSensitiveRow() {
        this.highlightSensitiveRow = true;
        return this;
    }

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

        private TextPaint textPaint;
        private int lastWidth;

        public TextSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(endFontSize - startFontSize + 1);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    setFontSize(Math.round(startFontSize + (endFontSize - startFontSize) * progress));
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }

                @Override
                public CharSequence getContentDescription() {
                    return String.valueOf(Math.round(startFontSize + (endFontSize - startFontSize) * sizeBar.getProgress()));
                }

                @Override
                public int getStepsCount() {
                    return endFontSize - startFontSize;
                }
            });
            sizeBar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 5, 5, 39, 0));

            messagesCell = new ThemePreviewMessagesCell(context, parentLayout, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                messagesCell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 53, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText("" + SharedConfig.fontSize, getMeasuredWidth() - dp(39), dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (lastWidth != width) {
                sizeBar.setProgress((SharedConfig.fontSize - startFontSize) / (float) (endFontSize - startFontSize));
                lastWidth = width;
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            messagesCell.invalidate();
            sizeBar.invalidate();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            sizeBar.getSeekBarAccessibilityDelegate().onInitializeAccessibilityNodeInfoInternal(this, info);
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle arguments) {
            return super.performAccessibilityAction(action, arguments) || sizeBar.getSeekBarAccessibilityDelegate().performAccessibilityActionInternal(this, action, arguments);
        }
    }

    private class BubbleRadiusCell extends FrameLayout {

        private SeekBarView sizeBar;
        private int startRadius = 0;
        private int endRadius = 17;

        private TextPaint textPaint;

        public BubbleRadiusCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setSeparatorsCount(endRadius - startRadius + 1);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    setBubbleRadius(Math.round(startRadius + (endRadius - startRadius) * progress), false);
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {
                }

                @Override
                public CharSequence getContentDescription() {
                    return String.valueOf(Math.round(startRadius + (endRadius - startRadius) * sizeBar.getProgress()));
                }

                @Override
                public int getStepsCount() {
                    return endRadius - startRadius;
                }
            });
            sizeBar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 5, 5, 39, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText("" + SharedConfig.bubbleRadius, getMeasuredWidth() - dp(39), dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
            sizeBar.setProgress((SharedConfig.bubbleRadius - startRadius) / (float) (endRadius - startRadius));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            sizeBar.invalidate();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            sizeBar.getSeekBarAccessibilityDelegate().onInitializeAccessibilityNodeInfoInternal(this, info);
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle arguments) {
            return super.performAccessibilityAction(action, arguments) || sizeBar.getSeekBarAccessibilityDelegate().performAccessibilityActionInternal(this, action, arguments);
        }
    }

    public ThemeActivity(int type) {
        super();
        currentType = type;
        updateRows(true);
    }

    private boolean setBubbleRadius(int size, boolean layout) {
        if (size != SharedConfig.bubbleRadius) {
            SharedConfig.bubbleRadius = size;
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("bubbleRadius", SharedConfig.bubbleRadius);
            editor.commit();

            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(textSizeRow);
            if (holder != null && holder.itemView instanceof TextSizeCell) {
                TextSizeCell cell = (TextSizeCell) holder.itemView;
                ChatMessageCell[] cells = cell.messagesCell.getCells();
                for (int a = 0; a < cells.length; a++) {
                    cells[a].getMessageObject().resetLayout();
                    cells[a].requestLayout();
                }
                cell.invalidate();
            }

            holder = listView.findViewHolderForAdapterPosition(bubbleRadiusRow);
            if (holder != null && holder.itemView instanceof BubbleRadiusCell) {
                BubbleRadiusCell cell = (BubbleRadiusCell) holder.itemView;
                if (layout) {
                    cell.requestLayout();
                } else {
                    cell.invalidate();
                }
            }

            updateMenuItem();
            return true;
        }
        return false;
    }

    private boolean setFontSize(int size) {
        if (size != SharedConfig.fontSize) {
            SharedConfig.fontSize = size;
            SharedConfig.fontSizeIsDefault = false;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            if (preferences == null) {
                return false;
            }
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("fons_size", SharedConfig.fontSize);
            editor.commit();

            Theme.createCommonMessageResources();

            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(textSizeRow);
            if (holder != null && holder.itemView instanceof TextSizeCell) {
                TextSizeCell cell = (TextSizeCell) holder.itemView;
                ChatMessageCell[] cells = cell.messagesCell.getCells();
                for (int a = 0; a < cells.length; a++) {
                    cells[a].getMessageObject().resetLayout();
                    cells[a].requestLayout();
                }
            }
            updateMenuItem();
            return true;
        }
        return false;
    }

    private void updateRows(boolean notify) {
        int oldRowCount = rowCount;

        int prevThemeAccentListRow = themeAccentListRow;
        int prevEditThemeRow = editThemeRow;
        int prevRaiseToSpeakRow = raiseToSpeakRow;

        rowCount = 0;
        contactsReimportRow = -1;
        contactsSortRow = -1;
        scheduleLocationRow = -1;
        scheduleUpdateLocationRow = -1;
        scheduleLocationInfoRow = -1;
        nightDisabledRow = -1;
        nightScheduledRow = -1;
        nightAutomaticRow = -1;
        nightSystemDefaultRow = -1;
        nightTypeInfoRow = -1;
        scheduleHeaderRow = -1;
        nightThemeRow = -1;
        browserRow = -1;
        newThemeInfoRow = -1;
        scheduleFromRow = -1;
        scheduleToRow = -1;
        scheduleFromToInfoRow = -1;
        themeListRow = -1;
        themeListRow2 = -1;
        themeAccentListRow = -1;
        themeInfoRow = -1;
        preferedHeaderRow = -1;
        automaticHeaderRow = -1;
        automaticBrightnessRow = -1;
        automaticBrightnessInfoRow = -1;
        textSizeHeaderRow = -1;
        themeHeaderRow = -1;
        bubbleRadiusHeaderRow = -1;
        bubbleRadiusRow = -1;
        bubbleRadiusInfoRow = -1;
        chatListHeaderRow = -1;
        chatListRow = -1;
        chatListInfoRow = -1;
        chatBlurRow = -1;
        pauseOnRecordRow = -1;
        pauseOnMediaRow = -1;
        stickersRow = -1;
        stickersInfoRow = -1;
        stickersSectionRow = -1;
        mediaSoundHeaderRow = -1;
        otherHeaderRow = -1;
        mediaSoundSectionRow = -1;
        otherSectionRow = -1;
        liteModeRow = -1;
        liteModeInfoRow = -1;

        textSizeRow = -1;
        backgroundRow = -1;
        changeUserColor = -1;
        settingsRow = -1;
        directShareRow = -1;
        sensitiveContentRow = -1;
        enableAnimationsRow = -1;
        raiseToSpeakRow = -1;
        raiseToListenRow = -1;
        nextMediaTapRow = -1;
        sendByEnterRow = -1;
        saveToGalleryOption1Row = -1;
        saveToGalleryOption2Row = -1;
        saveToGallerySectionRow = -1;
        distanceRow = -1;
        searchEngineRow = -1;
        bluetoothScoRow = -1;
        settings2Row = -1;

        swipeGestureHeaderRow = -1;
        swipeGestureRow = -1;
        swipeGestureInfoRow = -1;

        selectThemeHeaderRow = -1;
        themePreviewRow = -1;
        editThemeRow = -1;
        createNewThemeRow = -1;

        appIconHeaderRow = -1;
        appIconSelectorRow = -1;
        appIconShadowRow = -1;
        lastShadowRow = -1;

        defaultThemes.clear();
        darkThemes.clear();
        for (int a = 0, N = Theme.themes.size(); a < N; a++) {
            Theme.ThemeInfo themeInfo = Theme.themes.get(a);
            if (currentType != THEME_TYPE_BASIC && currentType != THEME_TYPE_THEMES_BROWSER) {
                if (themeInfo.isLight() || themeInfo.info != null && themeInfo.info.document == null) {
                    continue;
                }
            }
            if (themeInfo.pathToFile != null) {
                darkThemes.add(themeInfo);
            } else {
                defaultThemes.add(themeInfo);
            }
        }
        Collections.sort(defaultThemes, (o1, o2) -> Integer.compare(o1.sortIndex, o2.sortIndex));

        if (currentType == THEME_TYPE_THEMES_BROWSER) {
            selectThemeHeaderRow = rowCount++;
            themeListRow2 = rowCount++;
            chatListInfoRow = rowCount++;

            themePreviewRow = rowCount++;
            themeHeaderRow = rowCount++;
            themeListRow = rowCount++;
            hasThemeAccents = Theme.getCurrentTheme().hasAccentColors();
            if (themesHorizontalListCell != null) {
                themesHorizontalListCell.setDrawDivider(hasThemeAccents);
            }
            if (hasThemeAccents) {
                themeAccentListRow = rowCount++;
            }
            bubbleRadiusInfoRow = rowCount++;

            Theme.ThemeInfo themeInfo = Theme.getCurrentTheme();
            Theme.ThemeAccent accent = themeInfo.getAccent(false);
            if (themeInfo.themeAccents != null && !themeInfo.themeAccents.isEmpty() && accent != null && accent.id >= 100) {
                editThemeRow = rowCount++;
            }
            createNewThemeRow = rowCount++;
            lastShadowRow = rowCount++;
        } else if (currentType == THEME_TYPE_BASIC) {
            textSizeHeaderRow = rowCount++;
            textSizeRow = rowCount++;
            backgroundRow = rowCount++;
            changeUserColor = rowCount++;
            newThemeInfoRow = rowCount++;
            themeHeaderRow = rowCount++;

            themeListRow2 = rowCount++;
            themeInfoRow = rowCount++;

            bubbleRadiusHeaderRow = rowCount++;
            bubbleRadiusRow = rowCount++;
            bubbleRadiusInfoRow = rowCount++;

            chatListHeaderRow = rowCount++;
            chatListRow = rowCount++;
            chatListInfoRow = rowCount++;

            appIconHeaderRow = rowCount++;
            appIconSelectorRow = rowCount++;
            appIconShadowRow = rowCount++;

            swipeGestureHeaderRow = rowCount++;
            swipeGestureRow = rowCount++;
            swipeGestureInfoRow = rowCount++;

            nightThemeRow = rowCount++;
            browserRow = rowCount++;
            liteModeRow = rowCount++;
            stickersRow = rowCount++;
            stickersSectionRow = rowCount++;

            mediaSoundHeaderRow = rowCount++;
            nextMediaTapRow = rowCount++;
            raiseToListenRow = rowCount++;
            if (SharedConfig.raiseToListen) {
                raiseToSpeakRow = rowCount++;
            }
            pauseOnRecordRow = rowCount++;
            pauseOnMediaRow = rowCount++;
            bluetoothScoRow = rowCount++;
            mediaSoundSectionRow = rowCount++;

            otherHeaderRow = rowCount++;
            directShareRow = rowCount++;
            TL_account.contentSettings contentSettings = getMessagesController().getContentSettings();
            if (contentSettings != null && contentSettings.sensitive_can_change) {
                sensitiveContentRow = rowCount++;
            }
            sendByEnterRow = rowCount++;
            distanceRow = rowCount++;
            otherSectionRow = rowCount++;
        } else {
            nightDisabledRow = rowCount++;
            nightScheduledRow = rowCount++;
            nightAutomaticRow = rowCount++;
            if (Build.VERSION.SDK_INT >= 29) {
                nightSystemDefaultRow = rowCount++;
            }
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
                hasThemeAccents = Theme.getCurrentNightTheme().hasAccentColors();
                if (themesHorizontalListCell != null) {
                    themesHorizontalListCell.setDrawDivider(hasThemeAccents);
                }
                if (hasThemeAccents) {
                    themeAccentListRow = rowCount++;
                }
                themeInfoRow = rowCount++;
            }
        }

        if (themesHorizontalListCell != null) {
            themesHorizontalListCell.notifyDataSetChanged(listView.getWidth());
        }

        if (listAdapter != null) {
            if (currentType != THEME_TYPE_NIGHT || previousUpdatedType == Theme.selectedAutoNightType || previousUpdatedType == -1) {
                if (notify || previousUpdatedType == -1) {
                    listAdapter.notifyDataSetChanged();
                } else {
                    if (prevThemeAccentListRow == -1 && themeAccentListRow != -1) {
                        listAdapter.notifyItemInserted(themeAccentListRow);
                    } else if (prevThemeAccentListRow != -1 && themeAccentListRow == -1) {
                        listAdapter.notifyItemRemoved(prevThemeAccentListRow);
                        if (prevEditThemeRow != -1) {
                            prevEditThemeRow--;
                        }
                    } else if (themeAccentListRow != -1) {
                        listAdapter.notifyItemChanged(themeAccentListRow);
                    }

                    if (prevEditThemeRow == -1 && editThemeRow != -1) {
                        listAdapter.notifyItemInserted(editThemeRow);
                    } else if (prevEditThemeRow != -1 && editThemeRow == -1) {
                        listAdapter.notifyItemRemoved(prevEditThemeRow);
                    }

                    if (prevRaiseToSpeakRow == -1 && raiseToSpeakRow != -1) {
                        listAdapter.notifyItemInserted(raiseToSpeakRow);
                    } else if (prevRaiseToSpeakRow != -1 && raiseToSpeakRow == -1) {
                        listAdapter.notifyItemRemoved(prevRaiseToSpeakRow);
                    }
                }
            } else {
                int start = nightTypeInfoRow + 1;
                if (previousUpdatedType != Theme.selectedAutoNightType) {
                    for (int a = 0; a < 4; a++) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(a);
                        if (holder == null || !(holder.itemView instanceof ThemeTypeCell)) {
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
                        } else if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
                            listAdapter.notifyItemRangeInserted(start, Theme.autoNightScheduleByLocation ? 4 : 5);
                        }
                    } else if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC) {
                        if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_NONE) {
                            listAdapter.notifyItemRangeInserted(start, rowCount - start);
                        } else if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
                            listAdapter.notifyItemRangeRemoved(start, Theme.autoNightScheduleByLocation ? 4 : 5);
                            listAdapter.notifyItemRangeInserted(start, 3);
                        } else if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
                            listAdapter.notifyItemRangeInserted(start, 3);
                        }
                    } else if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
                        if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_NONE) {
                            listAdapter.notifyItemRangeInserted(start, rowCount - start);
                        } else if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC) {
                            listAdapter.notifyItemRangeRemoved(start, 3);
                        } else if (previousUpdatedType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
                            listAdapter.notifyItemRangeRemoved(start, Theme.autoNightScheduleByLocation ? 4 : 5);
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
        updateMenuItem();
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.themeListUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.themeAccentListUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needShareTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiPreviewThemesChanged);
        getNotificationCenter().addObserver(this, NotificationCenter.themeUploadedToServer);
        getNotificationCenter().addObserver(this, NotificationCenter.themeUploadError);
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
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.themeAccentListUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needShareTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needSetDayNightTheme);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiPreviewThemesChanged);
        getNotificationCenter().removeObserver(this, NotificationCenter.themeUploadedToServer);
        getNotificationCenter().removeObserver(this, NotificationCenter.themeUploadError);
        Theme.saveAutoNightThemeConfig();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.locationPermissionGranted) {
            updateSunTime(null, true);
        } else if (id == NotificationCenter.didSetNewWallpapper || id == NotificationCenter.emojiLoaded) {
            if (listView != null) {
                listView.invalidateViews();
            }
            updateMenuItem();
        } else if (id == NotificationCenter.themeAccentListUpdated) {
            if (listAdapter != null && themeAccentListRow != -1) {
                listAdapter.notifyItemChanged(themeAccentListRow, new Object());
            }
        } else if (id == NotificationCenter.themeListUpdated) {
            updateRows(true);
        } else if (id == NotificationCenter.themeUploadedToServer) {
            Theme.ThemeInfo themeInfo = (Theme.ThemeInfo) args[0];
            Theme.ThemeAccent accent = (Theme.ThemeAccent) args[1];
            if (themeInfo == sharingTheme && accent == sharingAccent) {
                String link = "https://" + getMessagesController().linkPrefix + "/addtheme/" + (accent != null ? accent.info.slug : themeInfo.info.slug);
                showDialog(new ShareAlert(getParentActivity(), null, link, false, link, false));
                if (sharingProgressDialog != null) {
                    sharingProgressDialog.dismiss();
                }
            }
        } else if (id == NotificationCenter.themeUploadError) {
            Theme.ThemeInfo themeInfo = (Theme.ThemeInfo) args[0];
            Theme.ThemeAccent accent = (Theme.ThemeAccent) args[1];
            if (themeInfo == sharingTheme && accent == sharingAccent && sharingProgressDialog == null) {
                sharingProgressDialog.dismiss();
            }
        } else if (id == NotificationCenter.needShareTheme) {
            if (getParentActivity() == null || isPaused) {
                return;
            }
            sharingTheme = (Theme.ThemeInfo) args[0];
            sharingAccent = (Theme.ThemeAccent) args[1];
            sharingProgressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            sharingProgressDialog.setCanCancel(true);
            showDialog(sharingProgressDialog, dialog -> {
                sharingProgressDialog = null;
                sharingTheme = null;
                sharingAccent = null;
            });
        } else if (id == NotificationCenter.needSetDayNightTheme) {
            updateMenuItem();
            checkCurrentDayNight();
        } else if (id == NotificationCenter.emojiPreviewThemesChanged) {
            if (themeListRow2 >= 0) {
                listAdapter.notifyItemChanged(themeListRow2);
            }
        }
    }

    @Override
    public View createView(Context context) {
        lastIsDarkTheme = !Theme.isCurrentThemeDay();

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        if (currentType == THEME_TYPE_THEMES_BROWSER) {
            actionBar.setTitle(getString("BrowseThemes", R.string.BrowseThemes));
            ActionBarMenu menu = actionBar.createMenu();
            sunDrawable = new RLottieDrawable(R.raw.sun, "" + R.raw.sun, dp(28), dp(28), true, null);
            if (lastIsDarkTheme) {
                sunDrawable.setCurrentFrame(sunDrawable.getFramesCount() - 1);
            } else {
                sunDrawable.setCurrentFrame(0);
            }
            sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
            menuItem = menu.addItem(day_night_switch, sunDrawable);
        } else if (currentType == THEME_TYPE_BASIC) {
            actionBar.setTitle(getString("ChatSettings", R.string.ChatSettings));
            ActionBarMenu menu = actionBar.createMenu();
            menuItem = menu.addItem(0, R.drawable.ic_ab_other);
            menuItem.setContentDescription(getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
            menuItem.addSubItem(share_theme, R.drawable.msg_share, getString("ShareTheme", R.string.ShareTheme));
            menuItem.addSubItem(edit_theme, R.drawable.msg_edit, getString("EditThemeColors", R.string.EditThemeColors));
            menuItem.addSubItem(create_theme, R.drawable.msg_palette, getString("CreateNewThemeMenu", R.string.CreateNewThemeMenu));
            menuItem.addSubItem(reset_settings, R.drawable.msg_reset, getString("ThemeResetToDefaults", R.string.ThemeResetToDefaults));

            if (getMessagesController().getContentSettings() == null) {
                getMessagesController().getContentSettings(settings -> {
                    if (listView != null && listView.isAttachedToWindow() && listAdapter != null) {
                        if ((sensitiveContentRow >= 0) == (settings != null && settings.sensitive_can_change)) {
                            listAdapter.notifyItemChanged(sensitiveContentRow);
                        } else {
                            updateRows(true);
                        }
                    }
                });
            }
        } else {
            actionBar.setTitle(getString("AutoNightTheme", R.string.AutoNightTheme));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == create_theme) {
                    createNewTheme();
                } else if (id == share_theme) {
                    Theme.ThemeInfo currentTheme = Theme.getCurrentTheme();
                    Theme.ThemeAccent accent = currentTheme.getAccent(false);
                    if (accent.info == null) {
                        getMessagesController().saveThemeToServer(accent.parentTheme, accent);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needShareTheme, accent.parentTheme, accent);
                    } else {
                        String link = "https://" + getMessagesController().linkPrefix + "/addtheme/" + accent.info.slug;
                        showDialog(new ShareAlert(getParentActivity(), null, link, false, link, false));
                    }
                } else if (id == edit_theme) {
                    editTheme();
                } else if (id == reset_settings) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                    builder1.setTitle(getString("ThemeResetToDefaultsTitle", R.string.ThemeResetToDefaultsTitle));
                    builder1.setMessage(getString("ThemeResetToDefaultsText", R.string.ThemeResetToDefaultsText));
                    builder1.setPositiveButton(getString("Reset", R.string.Reset), (dialogInterface, i) -> {
                        boolean changed = false;
                        if (setFontSize(AndroidUtilities.isTablet() ? 18 : 16)) {
                            changed = true;
                        }
                        if (setBubbleRadius(17, true)) {
                            changed = true;
                        }
                        if (changed) {
                            listAdapter.notifyItemChanged(textSizeRow, new Object());
                            listAdapter.notifyItemChanged(bubbleRadiusRow, new Object());
                        }
                        if (themesHorizontalListCell != null) {
                            Theme.ThemeInfo themeInfo = Theme.getTheme("Blue");
                            Theme.ThemeInfo currentTheme = Theme.getCurrentTheme();
                            Theme.ThemeAccent accent = themeInfo.themeAccentsMap.get(Theme.DEFALT_THEME_ACCENT_ID);
                            if (accent != null) {
                                Theme.OverrideWallpaperInfo info = new Theme.OverrideWallpaperInfo();
                                info.slug = Theme.DEFAULT_BACKGROUND_SLUG;
                                info.fileName = "Blue_99_wp.jpg";
                                info.originalFileName = "Blue_99_wp.jpg";
                                accent.overrideWallpaper = info;
                                themeInfo.setOverrideWallpaper(info);
                            }
                            if (themeInfo != currentTheme) {
                                themeInfo.setCurrentAccentId(Theme.DEFALT_THEME_ACCENT_ID);
                                Theme.saveThemeAccents(themeInfo, true, false, true, false);
                                themesHorizontalListCell.selectTheme(themeInfo);
                                themesHorizontalListCell.smoothScrollToPosition(0);
                            } else if (themeInfo.currentAccentId != Theme.DEFALT_THEME_ACCENT_ID) {
                                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentTheme, currentType == THEME_TYPE_NIGHT, null, Theme.DEFALT_THEME_ACCENT_ID);
                                listAdapter.notifyItemChanged(themeAccentListRow);
                            } else {
                                Theme.reloadWallpaper(true);
                            }
                        }
                    });
                    builder1.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                    AlertDialog alertDialog = builder1.create();
                    showDialog(alertDialog);
                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                } else if (id == day_night_switch) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
                    String dayThemeName = preferences.getString("lastDayTheme", "Blue");
                    if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                        dayThemeName = "Blue";
                    }
                    String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
                    if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                        nightThemeName = "Dark Blue";
                    }
                    Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
                    if (dayThemeName.equals(nightThemeName)) {
                        if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                            dayThemeName = "Blue";
                        } else {
                            nightThemeName = "Dark Blue";
                        }
                    }

                    boolean toDark;
                    if (toDark = dayThemeName.equals(themeInfo.getKey())) {
                        themeInfo = Theme.getTheme(nightThemeName);
                    } else {
                        themeInfo = Theme.getTheme(dayThemeName);
                    }

                    int[] pos = new int[2];
                    menuItem.getIconView().getLocationInWindow(pos);
                    pos[0] += menuItem.getIconView().getMeasuredWidth() / 2;
                    pos[1] += menuItem.getIconView().getMeasuredHeight() / 2;
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false, pos, -1, toDark, menuItem.getIconView());
                    updateRows(true);
                    //AndroidUtilities.updateVisibleRows(listView);

                    Theme.turnOffAutoNight(ThemeActivity.this);
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
                SharedConfig.setAnimationsEnabled(!animations);
                editor.commit();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(!animations);
                }
            } else if (position == backgroundRow) {
                presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL));
            } else if (position == changeUserColor) {
                presentFragment(new PeerColorActivity(0).setOnApplied(this));
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
                SharedConfig.toggleRaiseToSpeak();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.raiseToSpeak);
                }
            } else if (position == nextMediaTapRow) {
                SharedConfig.toggleNextMediaTap();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.nextMediaTap);
                }
            } else if (position == raiseToListenRow) {
                SharedConfig.toggleRaiseToListen();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.raiseToListen);
                }
                if (!SharedConfig.raiseToListen && raiseToSpeakRow != -1) {
                    for (int i = 0; i < listView.getChildCount(); ++i) {
                        View child = listView.getChildAt(i);
                        if (child instanceof TextCheckCell && listView.getChildAdapterPosition(child) == raiseToSpeakRow) {
                            ((TextCheckCell) child).setChecked(false);
                        }
                    }
                }
                updateRows(false);
            } else if (position == pauseOnRecordRow) {
                SharedConfig.togglePauseMusicOnRecord();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.pauseMusicOnRecord);
                }
            } else if (position == pauseOnMediaRow) {
                SharedConfig.togglePauseMusicOnMedia();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.pauseMusicOnMedia);
                }
            } else if (position == distanceRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AtomicReference<Dialog> dialogRef = new AtomicReference<>();

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.VERTICAL);

                CharSequence[] items = new CharSequence[]{
                    getString("DistanceUnitsAutomatic", R.string.DistanceUnitsAutomatic),
                    getString("DistanceUnitsKilometers", R.string.DistanceUnitsKilometers),
                    getString("DistanceUnitsMiles", R.string.DistanceUnitsMiles)
                };

                for (int i = 0; i < items.length; ++i) {
                    final int index = i;
                    RadioColorCell cell = new RadioColorCell(getParentActivity());
                    cell.setPadding(dp(4), 0, dp(4), 0);
                    cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                    cell.setTextAndValue(items[index], index == SharedConfig.distanceSystemType);
                    cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                    linearLayout.addView(cell);
                    cell.setOnClickListener(v -> {
                        SharedConfig.setDistanceSystemType(index);
                        updateDistance = true;
                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(distanceRow);
                        if (holder != null) {
                            listAdapter.onBindViewHolder(holder, distanceRow);
                        }
                        dialogRef.get().dismiss();
                    });
                }

                Dialog dialog = new AlertDialog.Builder(getParentActivity())
                        .setTitle(getString("DistanceUnitsTitle", R.string.DistanceUnitsTitle))
                        .setView(linearLayout)
                        .setNegativeButton(getString("Cancel", R.string.Cancel), null)
                        .create();
                dialogRef.set(dialog);
                showDialog(dialog);
            } else if (position == searchEngineRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AtomicReference<Dialog> dialogRef = new AtomicReference<>();

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.VERTICAL);

                ArrayList<SearchEngine> searchEngines = SearchEngine.getSearchEngines();
                CharSequence[] items = new CharSequence[ searchEngines.size() ];

                for (int i = 0; i < items.length; ++i) {
                    final SearchEngine engine = searchEngines.get(i);
                    items[i] = engine.name;

                    final int index = i;
                    RadioColorCell cell = new RadioColorCell(getParentActivity());
                    cell.setPadding(dp(4), 0, dp(4), 0);
                    cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                    cell.setTextAndValue(items[index], index == SharedConfig.searchEngineType);
                    cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                    linearLayout.addView(cell);
                    cell.setOnClickListener(v -> {
                        SharedConfig.setSearchEngineType(index);
                        updateSearchEngine = true;
                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(searchEngineRow);
                        if (holder != null) {
                            listAdapter.onBindViewHolder(holder, searchEngineRow);
                        }
                        dialogRef.get().dismiss();
                    });
                }

                Dialog dialog = new AlertDialog.Builder(getParentActivity())
                        .setTitle(getString(R.string.SearchEngine))
                        .setView(linearLayout)
                        .setNegativeButton(getString("Cancel", R.string.Cancel), null)
                        .create();
                dialogRef.set(dialog);
                showDialog(dialog);
            } else if (position == bluetoothScoRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AtomicReference<Dialog> dialogRef = new AtomicReference<>();

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.VERTICAL);

                RadioColorCell cell = new RadioColorCell(getParentActivity());
                cell.setPadding(dp(4), 0, dp(4), 0);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                cell.setTextAndValue(getString(R.string.MicrophoneForVoiceMessagesBuiltIn), !SharedConfig.recordViaSco);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    SharedConfig.recordViaSco = false;
                    SharedConfig.saveConfig();
                    updateRecordViaSco = true;
                    dialogRef.get().dismiss();

                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(bluetoothScoRow);
                    if (holder != null) {
                        listAdapter.onBindViewHolder(holder, bluetoothScoRow);
                    }
                });

                cell = new RadioColorCell(getParentActivity());
                cell.setPadding(dp(4), 0, dp(4), 0);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                cell.setTextAndText2AndValue(getString(R.string.MicrophoneForVoiceMessagesScoIfConnected), getString(R.string.MicrophoneForVoiceMessagesScoHint), SharedConfig.recordViaSco);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    PermissionRequest.ensurePermission(R.raw.permission_request_microphone, R.string.PermissionNoBluetoothWithHint, Manifest.permission.BLUETOOTH_CONNECT, granted -> {
                        if (!granted) {
                            SharedConfig.recordViaSco = false;
                            SharedConfig.saveConfig();
                            updateRecordViaSco = true;
                            dialogRef.get().dismiss();

                            if (listView != null && listView.isAttachedToWindow()) {
                                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(bluetoothScoRow);
                                if (holder != null) {
                                    listAdapter.onBindViewHolder(holder, bluetoothScoRow);
                                }
                            }
                        }
                    });

                    SharedConfig.recordViaSco = true;
                    SharedConfig.saveConfig();
                    updateRecordViaSco = true;
                    dialogRef.get().dismiss();

                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(bluetoothScoRow);
                    if (holder != null) {
                        listAdapter.onBindViewHolder(holder, bluetoothScoRow);
                    }
                });

                Dialog dialog = new AlertDialog.Builder(getParentActivity())
                        .setTitle(getString(R.string.MicrophoneForVoiceMessages))
                        .setView(linearLayout)
                        .setNegativeButton(getString("Cancel", R.string.Cancel), null)
                        .create();
                dialogRef.set(dialog);
                showDialog(dialog);
            } else if (position == directShareRow) {
                SharedConfig.toggleDirectShare();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.directShare);
                }
            } else if (position == sensitiveContentRow) {
                if (!getMessagesController().showSensitiveContent()) {
                    showDialog(
                        new AlertDialog.Builder(context, resourceProvider)
                            .setTitle(getString(R.string.ConfirmSensitiveContentTitle))
                            .setMessage(getString(R.string.ConfirmSensitiveContentText))
                            .setPositiveButton(getString(R.string.Confirm), (di, w) -> {
                                getMessagesController().setContentSettings(true);
                                if (view instanceof TextCheckCell) {
                                    ((TextCheckCell) view).setChecked(getMessagesController().showSensitiveContent());
                                }
                            })
                            .setNegativeButton(getString(R.string.Cancel), null)
                            .create()
                    );
                } else {
                    getMessagesController().setContentSettings(false);
                    if (view instanceof TextCheckCell) {
                        ((TextCheckCell) view).setChecked(getMessagesController().showSensitiveContent());
                    }
                }
            } else if (position == contactsReimportRow) {
                //not implemented
            } else if (position == contactsSortRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(getString("SortBy", R.string.SortBy));
                builder.setItems(new CharSequence[]{
                        getString("Default", R.string.Default),
                        getString("SortFirstName", R.string.SortFirstName),
                        getString("SortLastName", R.string.SortLastName)
                }, (dialog, which) -> {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putInt("sortContactsBy", which);
                    editor.commit();
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(position);
                    }
                });
                builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == chatBlurRow) {
                SharedConfig.toggleChatBlur();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.chatBlurEnabled());
                }
            } else if (position == nightThemeRow) {
                if (LocaleController.isRTL && x <= dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - dp(76)) {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) view;
                    if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE) {
                        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_AUTOMATIC;
                        checkCell.setChecked(true);
                    } else {
                        Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                        checkCell.setChecked(false);
                    }
                    Theme.saveAutoNightThemeConfig();
                    Theme.checkAutoNightThemeConditions(true);
                    boolean enabled = Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE;
                    String value = enabled ? Theme.getCurrentNightThemeName() : getString("AutoNightThemeOff", R.string.AutoNightThemeOff);
                    if (enabled) {
                        String type;
                        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
                            type = getString("AutoNightScheduled", R.string.AutoNightScheduled);
                        } else if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
                            type = getString("AutoNightSystemDefault", R.string.AutoNightSystemDefault);
                        } else {
                            type = getString("AutoNightAdaptive", R.string.AutoNightAdaptive);
                        }
                        value = type + " " + value;
                    }
                    checkCell.setTextAndValueAndIconAndCheck(getString("AutoNightTheme", R.string.AutoNightTheme), value, R.drawable.msg2_night_auto, enabled, 0, false, true);
                } else {
                    presentFragment(new ThemeActivity(THEME_TYPE_NIGHT));
                }
            } else if (position == browserRow) {
                if (LocaleController.isRTL && x <= dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - dp(76)) {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) view;
                    SharedConfig.toggleInappBrowser();
                    checkCell.setChecked(SharedConfig.inappBrowser);
                } else {
                    presentFragment(new WebBrowserSettings(null));
                }
            } else if (position == nightDisabledRow) {
                if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE) {
                    return;
                }
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                updateRows(true);
                Theme.checkAutoNightThemeConditions();
            } else if (position == nightScheduledRow) {
                if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
                    return;
                }
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SCHEDULED;
                if (Theme.autoNightScheduleByLocation) {
                    updateSunTime(null, true);
                }
                updateRows(true);
                Theme.checkAutoNightThemeConditions();
            } else if (position == nightAutomaticRow) {
                if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC) {
                    return;
                }
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_AUTOMATIC;
                updateRows(true);
                Theme.checkAutoNightThemeConditions();
            } else if (position == nightSystemDefaultRow) {
                if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
                    return;
                }
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SYSTEM;
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
                        cell.setTextAndValue(getString("AutoNightFrom", R.string.AutoNightFrom), String.format("%02d:%02d", hourOfDay, minute), true);
                    } else {
                        Theme.autoNightDayEndTime = time;
                        cell.setTextAndValue(getString("AutoNightTo", R.string.AutoNightTo), String.format("%02d:%02d", hourOfDay, minute), true);
                    }
                }, currentHour, currentMinute, true);
                showDialog(dialog);
            } else if (position == scheduleUpdateLocationRow) {
                updateSunTime(null, true);
            } else if (position == createNewThemeRow) {
                createNewTheme();
            } else if (position == editThemeRow) {
                editTheme();
            } else if (position == stickersRow) {
                presentFragment(new StickersActivity(MediaDataController.TYPE_IMAGE, null));
            } else if (position == liteModeRow) {
                presentFragment(new LiteModeSettingsActivity());
            }
        });
        if (currentType == THEME_TYPE_BASIC) {
            DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setDurations(350);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setSupportsChangeAnimations(false);
            listView.setItemAnimator(itemAnimator);
        }

        if (highlightSensitiveRow) {
            updateRows(false);
            highlightSensitiveRow = false;
            listView.scrollToPosition(listAdapter.getItemCount() - 1);
            AndroidUtilities.runOnUIThread(() -> {
                listView.highlightRow(() -> sensitiveContentRow);
            }, 200);
        }

        return fragmentView;
    }

    private void editTheme() {
        Theme.ThemeInfo currentTheme = Theme.getCurrentTheme();
        Theme.ThemeAccent accent = currentTheme.getAccent(false);
        presentFragment(new ThemePreviewActivity(currentTheme, false, ThemePreviewActivity.SCREEN_TYPE_ACCENT_COLOR, accent.id >= 100, currentType == THEME_TYPE_NIGHT));
    }

    private void createNewTheme() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString("NewTheme", R.string.NewTheme));
        builder.setMessage(getString("CreateNewThemeAlert", R.string.CreateNewThemeAlert));
        builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
        builder.setPositiveButton(getString("CreateTheme", R.string.CreateTheme), (dialog, which) -> AlertsCreator.createThemeCreateDialog(ThemeActivity.this, 0, null, null));
        showDialog(builder.create());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            updateRows(true);
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
            AndroidUtilities.setAdjustResizeToNothing(getParentActivity(), classGuid);
        }
    }

    private void updateMenuItem() {
        if (menuItem == null) {
            return;
        }
        Theme.ThemeInfo themeInfo = Theme.getCurrentTheme();
        Theme.ThemeAccent accent = themeInfo.getAccent(false);
        if (themeInfo.themeAccents != null && !themeInfo.themeAccents.isEmpty() && accent != null && accent.id >= 100) {
            menuItem.showSubItem(share_theme);
            menuItem.showSubItem(edit_theme);
        } else {
            menuItem.hideSubItem(share_theme);
            menuItem.hideSubItem(edit_theme);
        }
        int fontSize = AndroidUtilities.isTablet() ? 18 : 16;
        Theme.ThemeInfo currentTheme = Theme.getCurrentTheme();
        if (SharedConfig.fontSize != fontSize || SharedConfig.bubbleRadius != 17 || !currentTheme.firstAccentIsDefault || currentTheme.currentAccentId != Theme.DEFALT_THEME_ACCENT_ID || accent != null && accent.overrideWallpaper != null && !Theme.DEFAULT_BACKGROUND_SLUG.equals(accent.overrideWallpaper.slug)) {
            menuItem.showSubItem(reset_settings);
        } else {
            menuItem.hideSubItem(reset_settings);
        }
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
                    builder.setTopAnimation(R.raw.permission_request_location, AlertsCreator.PERMISSIONS_REQUEST_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground));
                    builder.setMessage(getString("GpsDisabledAlertText", R.string.GpsDisabledAlertText));
                    builder.setPositiveButton(getString("ConnectingToProxyEnable", R.string.ConnectingToProxyEnable), (dialog, id) -> {
                        if (getParentActivity() == null) {
                            return;
                        }
                        try {
                            getParentActivity().startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (Exception ignore) {

                        }
                    });
                    builder.setNegativeButton(getString("Cancel", R.string.Cancel), null);
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
                        ((TextSettingsCell) holder.itemView).setTextAndValue(getString("AutoNightUpdateLocation", R.string.AutoNightUpdateLocation), Theme.autoNightCityName, false);
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
        private Theme.ThemeAccent currentAccent;
        private boolean checked;

        InnerAccentView(Context context) {
            super(context);
        }

        void setThemeAndColor(Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent) {
            currentTheme = themeInfo;
            currentAccent = accent;
            updateCheckedState(false);
        }

        void updateCheckedState(boolean animate) {
            checked = currentTheme.currentAccentId == currentAccent.id;

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
            super.onMeasure(MeasureSpec.makeMeasureSpec(dp(62), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(62), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float radius = dp(20);

            float cx = 0.5f * getMeasuredWidth();
            float cy = 0.5f * getMeasuredHeight();

            paint.setColor(currentAccent.accentColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setAlpha(Math.round(255f * checkedState));
            canvas.drawCircle(cx, cy, radius - 0.5f * paint.getStrokeWidth(), paint);

            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, radius - dp(5) * checkedState, paint);

            if (checkedState != 0) {
                paint.setColor(0xffffffff);
                paint.setAlpha(Math.round(255f * checkedState));
                canvas.drawCircle(cx, cy, dp(2), paint);
                canvas.drawCircle(cx - dp(7) * checkedState, cy, dp(2), paint);
                canvas.drawCircle(cx + dp(7) * checkedState, cy, dp(2), paint);
            }

            if (currentAccent.myMessagesAccentColor != 0 && checkedState != 1) {
                paint.setColor(currentAccent.myMessagesAccentColor);
                canvas.drawCircle(cx, cy, dp(8) * (1.0f - checkedState), paint);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setText(getString("ColorPickerMainColor", R.string.ColorPickerMainColor));
            info.setClassName(Button.class.getName());
            info.setChecked(checked);
            info.setCheckable(true);
            info.setEnabled(true);
        }
    }

    private static class InnerCustomAccentView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[] colors = new int[7];

        InnerCustomAccentView(Context context) {
            super(context);
        }

        private void setTheme(Theme.ThemeInfo themeInfo) {
            if (themeInfo.defaultAccentCount >= 8) {
                colors = new int[]{themeInfo.getAccentColor(6), themeInfo.getAccentColor(4), themeInfo.getAccentColor(7), themeInfo.getAccentColor(2), themeInfo.getAccentColor(0), themeInfo.getAccentColor(5), themeInfo.getAccentColor(3)};
            } else {
                colors = new int[7];
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(dp(62), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(62), MeasureSpec.EXACTLY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float centerX = 0.5f * getMeasuredWidth();
            float centerY = 0.5f * getMeasuredHeight();

            float radSmall = dp(5);
            float radRing = dp(20) - radSmall;

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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setText(getString("ColorPickerMainColor", R.string.ColorPickerMainColor));
            info.setClassName(Button.class.getName());
            info.setEnabled(true);
        }
    }

    private class ThemeAccentsListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private Theme.ThemeInfo currentTheme;
        private ArrayList<Theme.ThemeAccent> themeAccents;

        ThemeAccentsListAdapter(Context context) {
            mContext = context;
            notifyDataSetChanged();
        }

        @Override
        public void notifyDataSetChanged() {
            currentTheme = currentType == THEME_TYPE_NIGHT ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
            themeAccents = new ArrayList<>(currentTheme.themeAccents);
            super.notifyDataSetChanged();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemViewType(int position) {
            return position == getItemCount() - 1 ? 1 : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            switch (viewType) {
                case 0: {
                    return new RecyclerListView.Holder(new InnerAccentView(mContext));
                }
                case 1:
                default: {
                    return new RecyclerListView.Holder(new InnerCustomAccentView(mContext));
                }
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (getItemViewType(position)) {
                case 0: {
                    InnerAccentView view = (InnerAccentView) holder.itemView;
                    view.setThemeAndColor(currentTheme, themeAccents.get(position));
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
            return themeAccents.isEmpty() ? 0 : themeAccents.size() + 1;
        }

        private int findCurrentAccent() {
            return themeAccents.indexOf(currentTheme.getAccent(false));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int TYPE_TEXT_SETTING = 1;
        private final static int TYPE_TEXT_INFO_PRIVACY = 2;
        private final static int TYPE_SHADOW = 3;
        private final static int TYPE_THEME_TYPE = 4;
        private final static int TYPE_HEADER = 5;
        private final static int TYPE_BRIGHTNESS = 6;
        private final static int TYPE_TEXT_CHECK = 7;
        private final static int TYPE_TEXT_SIZE = 8;
        private final static int TYPE_CHAT_LIST = 9;
        private final static int TYPE_NIGHT_THEME = 10;
        private final static int TYPE_THEME_LIST = 11;
        private final static int TYPE_THEME_ACCENT_LIST = 12;
        private final static int TYPE_BUBBLE_RADIUS = 13;
        private final static int TYPE_TEXT_PREFERENCE = 14;
        private final static int TYPE_SWIPE_GESTURE = 15;
        private final static int TYPE_THEME_PREVIEW = 16;
        private final static int TYPE_DEFAULT_THEMES_PREVIEW = 17;
        private final static int TYPE_SAVE_TO_GALLERY = 19;
        private final static int TYPE_APP_ICON = 20;
        private final static int TYPE_CHOOSE_COLOR = 21;

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
            return type == 0 || type == TYPE_TEXT_SETTING || type == TYPE_THEME_TYPE || type == TYPE_TEXT_CHECK ||
                    type == TYPE_NIGHT_THEME || type == TYPE_THEME_LIST || type == TYPE_THEME_ACCENT_LIST ||
                    type == TYPE_TEXT_PREFERENCE || type == 18 || type == TYPE_APP_ICON || type == TYPE_CHOOSE_COLOR;
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
                        getString("ExportTheme", R.string.ExportTheme)
                };
                icons = new int[]{
                        0,
                        R.drawable.msg_shareout
                };
            } else {
                hasDelete = themeInfo.info == null || !themeInfo.info.isDefault;
                items = new CharSequence[]{
                        getString("ShareFile", R.string.ShareFile),
                        getString("ExportTheme", R.string.ExportTheme),
                        themeInfo.info == null || !themeInfo.info.isDefault && themeInfo.info.creator ? getString("Edit", R.string.Edit) : null,
                        themeInfo.info != null && themeInfo.info.creator ? getString("ThemeSetUrl", R.string.ThemeSetUrl) : null,
                        hasDelete ? getString("Delete", R.string.Delete) : null};
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
                    if (themeInfo.info == null) {
                        getMessagesController().saveThemeToServer(themeInfo, null);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needShareTheme, themeInfo, null);
                    } else {
                        String link = "https://" + getMessagesController().linkPrefix + "/addtheme/" + themeInfo.info.slug;
                        showDialog(new ShareAlert(getParentActivity(), null, link, false, link, false));
                    }
                } else if (which == 1) {
                    File currentFile;
                    if (themeInfo.pathToFile == null && themeInfo.assetName == null) {
                        StringBuilder result = new StringBuilder();
                        int[] defaultColors = Theme.getDefaultColors();
                        for (int i = 0; i < defaultColors.length; i++) {
                            result.append(ThemeColors.getStringName(i)).append("=").append(defaultColors[i]).append("\n");
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
                    String name = themeInfo.name;
                    if (!name.endsWith(".attheme")) {
                        name += ".attheme";
                    }
                    File finalFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), FileLoader.fixFileName(name));
                    try {
                        if (!AndroidUtilities.copyFile(currentFile, finalFile)) {
                            return;
                        }
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/xml");
                        if (Build.VERSION.SDK_INT >= 24) {
                            try {
                                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", finalFile));
                                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignore) {
                                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(finalFile));
                            }
                        } else {
                            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(finalFile));
                        }
                        startActivityForResult(Intent.createChooser(intent, getString("ShareFile", R.string.ShareFile)), 500);
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
                    presentFragment(new ThemeSetUrlActivity(themeInfo, null, false));
                } else {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                    builder1.setTitle(getString("DeleteThemeTitle", R.string.DeleteThemeTitle));
                    builder1.setMessage(getString("DeleteThemeAlert", R.string.DeleteThemeAlert));
                    builder1.setPositiveButton(getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                        MessagesController.getInstance(themeInfo.account).saveTheme(themeInfo, null, themeInfo == Theme.getCurrentNightTheme(), true);
                        if (Theme.deleteTheme(themeInfo)) {
                            parentLayout.rebuildAllFragmentViews(true, true);
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeListUpdated);
                    });
                    builder1.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                    AlertDialog alertDialog = builder1.create();
                    showDialog(alertDialog);
                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                }
            });
            AlertDialog alertDialog = builder.create();
            showDialog(alertDialog);
            if (hasDelete) {
                alertDialog.setItemColor(alertDialog.getItemsCount() - 1, Theme.getColor(Theme.key_text_RedBold), Theme.getColor(Theme.key_text_RedRegular));
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_TEXT_SETTING:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_TEXT_INFO_PRIVACY:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext);
                    break;
                case TYPE_THEME_TYPE:
                    view = new ThemeTypeCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_BRIGHTNESS:
                    view = new BrightnessControlCell(mContext, BrightnessControlCell.TYPE_DEFAULT) {
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
                case TYPE_TEXT_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_TEXT_SIZE:
                    view = new TextSizeCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHAT_LIST:
                    view = new ChatListCell(mContext) {
                        @Override
                        protected void didSelectChatType(boolean threeLines) {
                            SharedConfig.setUseThreeLinesLayout(threeLines);
                        }
                    };
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_NIGHT_THEME:
                    view = new NotificationsCheckCell(mContext, 21, 60, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_THEME_LIST:
                    first = true;
                    themesHorizontalListCell = new ThemesHorizontalListCell(mContext, ThemeActivity.this, currentType, defaultThemes, darkThemes) {
                        @Override
                        protected void showOptionsForTheme(Theme.ThemeInfo themeInfo) {
                            listAdapter.showOptionsForTheme(themeInfo);
                        }

                        @Override
                        protected void updateRows() {
                            ThemeActivity.this.updateRows(false);
                        }
                    };
                    themesHorizontalListCell.setDrawDivider(hasThemeAccents);
                    themesHorizontalListCell.setFocusable(false);
                    view = themesHorizontalListCell;
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(148)));
                    break;
                case TYPE_THEME_ACCENT_LIST: {
                    RecyclerListView accentsListView = new TintRecyclerListView(mContext) {
                        @Override
                        public boolean onInterceptTouchEvent(MotionEvent e) {
                            if (getParent() != null && getParent().getParent() != null) {
                                getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
                            }
                            return super.onInterceptTouchEvent(e);
                        }
                    };
                    accentsListView.setFocusable(false);
                    accentsListView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    accentsListView.setItemAnimator(null);
                    accentsListView.setLayoutAnimation(null);
                    accentsListView.setPadding(dp(11), 0, dp(11), 0);
                    accentsListView.setClipToPadding(false);
                    LinearLayoutManager accentsLayoutManager = new LinearLayoutManager(mContext);
                    accentsLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                    accentsListView.setLayoutManager(accentsLayoutManager);
                    ThemeAccentsListAdapter accentsAdapter = new ThemeAccentsListAdapter(mContext);
                    accentsListView.setAdapter(accentsAdapter);
                    accentsListView.setOnItemClickListener((view1, position) -> {
                        Theme.ThemeInfo currentTheme = currentType == THEME_TYPE_NIGHT ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
                        if (position == accentsAdapter.getItemCount() - 1) {
                            presentFragment(new ThemePreviewActivity(currentTheme, false, ThemePreviewActivity.SCREEN_TYPE_ACCENT_COLOR, false, currentType == THEME_TYPE_NIGHT));
                        } else {
                            Theme.ThemeAccent accent = accentsAdapter.themeAccents.get(position);

                            if (!TextUtils.isEmpty(accent.patternSlug) && accent.id != Theme.DEFALT_THEME_ACCENT_ID) {
                                Theme.PatternsLoader.createLoader(false);
                            }

                            if (currentTheme.currentAccentId != accent.id) {
                                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentTheme, currentType == THEME_TYPE_NIGHT, null, accent.id);
                                EmojiThemes.saveCustomTheme(currentTheme, accent.id);
                                Theme.turnOffAutoNight(ThemeActivity.this);
                            } else {
                                presentFragment(new ThemePreviewActivity(currentTheme, false, ThemePreviewActivity.SCREEN_TYPE_ACCENT_COLOR, accent.id >= 100, currentType == THEME_TYPE_NIGHT));
                            }
                        }

                        int left = view1.getLeft();
                        int right = view1.getRight();
                        int extra = dp(52);
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
                    accentsListView.setOnItemLongClickListener((view12, position) -> {
                        if (position < 0 || position >= accentsAdapter.themeAccents.size()) {
                            return false;
                        }
                        Theme.ThemeAccent accent = accentsAdapter.themeAccents.get(position);
                        if (accent.id >= 100 && !accent.isDefault) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            CharSequence[] items = new CharSequence[]{
                                    getString("OpenInEditor", R.string.OpenInEditor),
                                    getString("ShareTheme", R.string.ShareTheme),
                                    accent.info != null && accent.info.creator ? getString("ThemeSetUrl", R.string.ThemeSetUrl) : null,
                                    getString("DeleteTheme", R.string.DeleteTheme)
                            };
                            int[] icons = new int[]{
                                    R.drawable.msg_edit,
                                    R.drawable.msg_share,
                                    R.drawable.msg_link,
                                    R.drawable.msg_delete
                            };
                            builder.setItems(items, icons, (dialog, which) -> {
                                if (getParentActivity() == null) {
                                    return;
                                }
                                if (which == 0) {
                                    AlertsCreator.createThemeCreateDialog(ThemeActivity.this, which == 1 ? 2 : 1, accent.parentTheme, accent);
                                } else if (which == 1) {
                                    if (accent.info == null) {
                                        getMessagesController().saveThemeToServer(accent.parentTheme, accent);
                                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needShareTheme, accent.parentTheme, accent);
                                    } else {
                                        String link = "https://" + getMessagesController().linkPrefix + "/addtheme/" + accent.info.slug;
                                        showDialog(new ShareAlert(getParentActivity(), null, link, false, link, false));
                                    }
                                } else if (which == 2) {
                                    presentFragment(new ThemeSetUrlActivity(accent.parentTheme, accent, false));
                                } else if (which == 3) {
                                    if (getParentActivity() == null) {
                                        return;
                                    }
                                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                                    builder1.setTitle(getString("DeleteThemeTitle", R.string.DeleteThemeTitle));
                                    builder1.setMessage(getString("DeleteThemeAlert", R.string.DeleteThemeAlert));
                                    builder1.setPositiveButton(getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                                        if (Theme.deleteThemeAccent(accentsAdapter.currentTheme, accent, true)) {
                                            Theme.refreshThemeColors();
                                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, Theme.getActiveTheme(), currentType == THEME_TYPE_NIGHT, null, -1);
                                        }
                                    });
                                    builder1.setNegativeButton(getString("Cancel", R.string.Cancel), null);
                                    AlertDialog alertDialog = builder1.create();
                                    showDialog(alertDialog);
                                    TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                                    if (button != null) {
                                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                                    }
                                }
                            });
                            AlertDialog alertDialog = builder.create();
                            showDialog(alertDialog);
                            alertDialog.setItemColor(alertDialog.getItemsCount() - 1, Theme.getColor(Theme.key_text_RedBold), Theme.getColor(Theme.key_text_RedRegular));
                            return true;
                        }
                        return false;
                    });

                    view = accentsListView;
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
                    break;
                }
                case TYPE_BUBBLE_RADIUS:
                    view = new BubbleRadiusCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_TEXT_PREFERENCE:
                default:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_SWIPE_GESTURE:
                    view = new SwipeGestureSettingsView(mContext, currentAccount);
                    break;
                case TYPE_THEME_PREVIEW:
                    ThemePreviewMessagesCell messagesCell = new ThemePreviewMessagesCell(mContext, parentLayout, 0) {
                        @Override
                        public boolean onInterceptTouchEvent(MotionEvent e) {
                            if (getParent() != null && getParent().getParent() != null) {
                                getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
                            }
                            return super.onInterceptTouchEvent(e);
                        }
                    };
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        messagesCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    }
                    view = messagesCell;
                    break;
                case TYPE_DEFAULT_THEMES_PREVIEW:
                    DefaultThemesPreviewCell cell = new DefaultThemesPreviewCell(mContext, ThemeActivity.this, currentType);
                    view = cell;
                    cell.setFocusable(false);
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    break;
                case TYPE_SAVE_TO_GALLERY:
                    view = new RadioButtonCell(mContext);
                    break;
                case TYPE_APP_ICON:
                    view = new AppIconsSelectorCell(mContext, ThemeActivity.this, currentAccount);
                    break;
                case TYPE_CHOOSE_COLOR:
                    view = new PeerColorActivity.ChangeNameColorCell(currentAccount, 0, mContext, getResourceProvider());
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_TEXT_SETTING: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == nightThemeRow) {
                        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE || Theme.getCurrentNightTheme() == null) {
                            cell.setTextAndValue(getString(R.string.AutoNightTheme), getString(R.string.AutoNightThemeOff), false);
                        } else {
                            cell.setTextAndValue(getString(R.string.AutoNightTheme), Theme.getCurrentNightThemeName(), false);
                        }
                    } else if (position == scheduleFromRow) {
                        int currentHour = Theme.autoNightDayStartTime / 60;
                        int currentMinute = (Theme.autoNightDayStartTime - currentHour * 60);
                        cell.setTextAndValue(getString("AutoNightFrom", R.string.AutoNightFrom), String.format("%02d:%02d", currentHour, currentMinute), true);
                    } else if (position == scheduleToRow) {
                        int currentHour = Theme.autoNightDayEndTime / 60;
                        int currentMinute = (Theme.autoNightDayEndTime - currentHour * 60);
                        cell.setTextAndValue(getString("AutoNightTo", R.string.AutoNightTo), String.format("%02d:%02d", currentHour, currentMinute), false);
                    } else if (position == scheduleUpdateLocationRow) {
                        cell.setTextAndValue(getString("AutoNightUpdateLocation", R.string.AutoNightUpdateLocation), Theme.autoNightCityName, false);
                    } else if (position == contactsSortRow) {
                        String value;
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        int sort = preferences.getInt("sortContactsBy", 0);
                        if (sort == 0) {
                            value = getString("Default", R.string.Default);
                        } else if (sort == 1) {
                            value = getString("FirstName", R.string.SortFirstName);
                        } else {
                            value = getString("LastName", R.string.SortLastName);
                        }
                        cell.setTextAndValue(getString("SortBy", R.string.SortBy), value, true);
                    } else if (position == contactsReimportRow) {
                        cell.setText(getString("ImportContacts", R.string.ImportContacts), true);
                    } else if (position == distanceRow) {
                        String value;
                        if (SharedConfig.distanceSystemType == 0) {
                            value = getString("DistanceUnitsAutomatic", R.string.DistanceUnitsAutomatic);
                        } else if (SharedConfig.distanceSystemType == 1) {
                            value = getString("DistanceUnitsKilometers", R.string.DistanceUnitsKilometers);
                        } else {
                            value = getString("DistanceUnitsMiles", R.string.DistanceUnitsMiles);
                        }
                        cell.setTextAndValue(getString("DistanceUnits", R.string.DistanceUnits), value, updateDistance, true);
                        updateDistance = false;
                    } else if (position == searchEngineRow) {
                        cell.setTextAndValue(getString(R.string.SearchEngine), SearchEngine.getCurrent().name, updateSearchEngine, false);
                    } else if (position == bluetoothScoRow) {
                        cell.setTextAndValue(getString(R.string.MicrophoneForVoiceMessages), getString(SharedConfig.recordViaSco ? R.string.MicrophoneForVoiceMessagesSco : R.string.MicrophoneForVoiceMessagesBuiltIn), updateRecordViaSco, false);
                        updateRecordViaSco = false;
                    }
                    break;
                }
                case TYPE_TEXT_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setFixedSize(0);
                    if (position == automaticBrightnessInfoRow) {
                        cell.setText(LocaleController.formatString("AutoNightBrightnessInfo", R.string.AutoNightBrightnessInfo, (int) (100 * Theme.autoNightBrighnessThreshold)));
                    } else if (position == scheduleLocationInfoRow) {
                        cell.setText(getLocationSunString());
                    } else if (position == swipeGestureInfoRow) {
                        cell.setText(getString("ChatListSwipeGestureInfo", R.string.ChatListSwipeGestureInfo));
                    } else if (position == liteModeInfoRow) {
                        cell.setText(getString("LiteModeInfo", R.string.LiteModeInfo));
                    } else {
                        cell.setFixedSize(12);
                        cell.setText("");
                    }
                    break;
                }
                case TYPE_SHADOW: {
                    if (position == nightTypeInfoRow && themeInfoRow == -1 || position == lastShadowRow || position == themeInfoRow && nightTypeInfoRow != -1 || position == saveToGallerySectionRow || position == settings2Row) {
                        holder.itemView.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case TYPE_THEME_TYPE: {
                    ThemeTypeCell typeCell = (ThemeTypeCell) holder.itemView;
                    if (position == nightDisabledRow) {
                        typeCell.setValue(getString("AutoNightDisabled", R.string.AutoNightDisabled), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE, true);
                    } else if (position == nightScheduledRow) {
                        typeCell.setValue(getString("AutoNightScheduled", R.string.AutoNightScheduled), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED, true);
                    } else if (position == nightAutomaticRow) {
                        typeCell.setValue(getString("AutoNightAdaptive", R.string.AutoNightAdaptive), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC, nightSystemDefaultRow != -1);
                    } else if (position == nightSystemDefaultRow) {
                        typeCell.setValue(getString("AutoNightSystemDefault", R.string.AutoNightSystemDefault), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM, false);
                    }
                    break;
                }
                case TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == scheduleHeaderRow) {
                        headerCell.setText(getString("AutoNightSchedule", R.string.AutoNightSchedule));
                    } else if (position == automaticHeaderRow) {
                        headerCell.setText(getString("AutoNightBrightness", R.string.AutoNightBrightness));
                    } else if (position == preferedHeaderRow) {
                        headerCell.setText(getString("AutoNightPreferred", R.string.AutoNightPreferred));
                    } else if (position == settingsRow) {
                        headerCell.setText(getString("SETTINGS", R.string.SETTINGS));
                    } else if (position == themeHeaderRow) {
                        if (currentType == THEME_TYPE_THEMES_BROWSER) {
                            headerCell.setText(getString("BuildMyOwnTheme", R.string.BuildMyOwnTheme));
                        } else {
                            headerCell.setText(getString("ColorTheme", R.string.ColorTheme));
                        }
                    } else if (position == textSizeHeaderRow) {
                        headerCell.setText(getString("TextSizeHeader", R.string.TextSizeHeader));
                    } else if (position == chatListHeaderRow) {
                        headerCell.setText(getString("ChatList", R.string.ChatList));
                    } else if (position == bubbleRadiusHeaderRow) {
                        headerCell.setText(getString("BubbleRadius", R.string.BubbleRadius));
                    } else if (position == swipeGestureHeaderRow) {
                        headerCell.setText(getString("ChatListSwipeGesture", R.string.ChatListSwipeGesture));
                    } else if (position == selectThemeHeaderRow) {
                        headerCell.setText(getString("SelectTheme", R.string.SelectTheme));
                    } else if (position == appIconHeaderRow) {
                        headerCell.setText(getString(R.string.AppIcon));
                    } else if (position == otherHeaderRow) {
                        headerCell.setText(getString("OtherSettings", R.string.OtherSettings));
                    } else if (position == mediaSoundHeaderRow) {
                        headerCell.setText(getString("MediaAndSoundSettings", R.string.MediaAndSoundSettings));
                    }
                    break;
                }
                case TYPE_BRIGHTNESS: {
                    BrightnessControlCell cell = (BrightnessControlCell) holder.itemView;
                    cell.setProgress(Theme.autoNightBrighnessThreshold);
                    break;
                }
                case TYPE_TEXT_CHECK: {
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    if (position == scheduleLocationRow) {
                        textCheckCell.setTextAndCheck(getString("AutoNightLocation", R.string.AutoNightLocation), Theme.autoNightScheduleByLocation, true);
                    } else if (position == enableAnimationsRow) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        textCheckCell.setTextAndCheck(getString("EnableAnimations", R.string.EnableAnimations), preferences.getBoolean("view_animations", true), true);
                    } else if (position == sendByEnterRow) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        textCheckCell.setTextAndCheck(getString("SendByEnter", R.string.SendByEnter), preferences.getBoolean("send_by_enter", false), true);
                    } else if (position == raiseToSpeakRow) {
                        textCheckCell.setTextAndValueAndCheck(getString("RaiseToSpeak", R.string.RaiseToSpeak), getString("RaiseToSpeakInfo", R.string.RaiseToSpeakInfo), SharedConfig.raiseToSpeak, true, true);
                    } else if (position == raiseToListenRow) {
                        textCheckCell.setTextAndValueAndCheck(getString("RaiseToListen", R.string.RaiseToListen), getString("RaiseToListenInfo", R.string.RaiseToListenInfo), SharedConfig.raiseToListen, true, true);
                    } else if (position == nextMediaTapRow) {
                        textCheckCell.setTextAndValueAndCheck(getString("NextMediaTap", R.string.NextMediaTap), getString("NextMediaTapInfo", R.string.NextMediaTapInfo), SharedConfig.nextMediaTap, true, true);
                    } else if (position == pauseOnRecordRow) {
                        textCheckCell.setTextAndValueAndCheck(getString(R.string.PauseMusicOnRecord), getString("PauseMusicOnRecordInfo", R.string.PauseMusicOnRecordInfo), SharedConfig.pauseMusicOnRecord, true, true);
                    } else if (position == pauseOnMediaRow) {
                        textCheckCell.setTextAndCheck(getString(R.string.PauseMusicOnMedia), SharedConfig.pauseMusicOnMedia, true);
                    } else if (position == directShareRow) {
                        textCheckCell.setTextAndValueAndCheck(getString("DirectShare", R.string.DirectShare), getString("DirectShareInfo", R.string.DirectShareInfo), SharedConfig.directShare, false, true);
                    } else if (position == sensitiveContentRow) {
                        textCheckCell.setTextAndValueAndCheck(getString(R.string.ShowSensitiveContent), getString(R.string.ShowSensitiveContentInfo), getMessagesController().showSensitiveContent(), true, true);
                    } else if (position == chatBlurRow) {
                        textCheckCell.setTextAndCheck(getString("BlurInChat", R.string.BlurInChat), SharedConfig.chatBlurEnabled(), true);
                    }
                    break;
                }
                case TYPE_NIGHT_THEME: {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;
                    if (position == nightThemeRow) {
                        boolean enabled = Theme.selectedAutoNightType != Theme.AUTO_NIGHT_TYPE_NONE;
                        String value = enabled ? Theme.getCurrentNightThemeName() : getString("AutoNightThemeOff", R.string.AutoNightThemeOff);
                        if (enabled) {
                            String type;
                            if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED) {
                                type = getString("AutoNightScheduled", R.string.AutoNightScheduled);
                            } else if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SYSTEM) {
                                type = getString("AutoNightSystemDefault", R.string.AutoNightSystemDefault);
                            } else {
                                type = getString("AutoNightAdaptive", R.string.AutoNightAdaptive);
                            }
                            value = type + " " + value;
                        }
                        checkCell.setTextAndValueAndIconAndCheck(getString("AutoNightTheme", R.string.AutoNightTheme), value, R.drawable.msg2_night_auto, enabled, 0, false, true);
                    } else if (position == browserRow) {
                        checkCell.setTextAndValueAndIconAndCheck(getString(R.string.InappBrowser), getString(R.string.InappBrowserInfo), R.drawable.msg2_language, SharedConfig.inappBrowser, 0, false, true);
                    }
                    break;
                }
                case TYPE_THEME_LIST: {
                    if (first) {
                        themesHorizontalListCell.scrollToCurrentTheme(listView.getMeasuredWidth(), false);
                        first = false;
                    }
                    break;
                }
                case TYPE_THEME_ACCENT_LIST: {
                    RecyclerListView accentsList = (RecyclerListView) holder.itemView;
                    ThemeAccentsListAdapter adapter = (ThemeAccentsListAdapter) accentsList.getAdapter();
                    adapter.notifyDataSetChanged();
                    int pos = adapter.findCurrentAccent();
                    if (pos == -1) {
                        pos = adapter.getItemCount() - 1;
                    }
                    if (pos != -1) {
                        ((LinearLayoutManager) accentsList.getLayoutManager()).scrollToPositionWithOffset(pos, listView.getMeasuredWidth() / 2 - dp(42));
                    }
                    break;
                }
                case TYPE_TEXT_PREFERENCE: {
                    TextCell cell = (TextCell) holder.itemView;
                    cell.heightDp = 48;
                    if (position == backgroundRow) {
                        cell.setSubtitle(null);
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                        cell.setTextAndIcon(getString(R.string.ChangeChatBackground), R.drawable.msg_background, changeUserColor >= 0);
                    } else if (position == editThemeRow) {
                        cell.setSubtitle(null);
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                        cell.setTextAndIcon(getString("EditCurrentTheme", R.string.EditCurrentTheme), R.drawable.msg_theme, true);
                    } else if (position == createNewThemeRow) {
                        cell.setSubtitle(null);
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                        cell.setTextAndIcon(getString("CreateNewTheme", R.string.CreateNewTheme), R.drawable.msg_colors, false);
                    } else if (position == liteModeRow) {
                        cell.setColors(Theme.key_dialogIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString("LiteMode", R.string.LiteMode), R.drawable.msg2_animations, true);
                        cell.setSubtitle(getString("LiteModeInfo", R.string.LiteModeInfo));
                        cell.heightDp = 60;
                        cell.offsetFromImage = 64;
                        cell.imageLeft = 20;
                    } else if (position == stickersRow) {
                        cell.setColors(Theme.key_dialogIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString("StickersName", R.string.StickersName), R.drawable.msg2_sticker, false);
                        cell.setSubtitle(getString("StickersNameInfo2", R.string.StickersNameInfo2));
                        cell.offsetFromImage = 64;
                        cell.heightDp = 60;
                        cell.imageLeft = 20;
                    }
                    break;
                }
                case TYPE_DEFAULT_THEMES_PREVIEW: {
                    DefaultThemesPreviewCell cell = (DefaultThemesPreviewCell) holder.itemView;
                    cell.updateDayNightMode();
                    break;
                }
                case TYPE_SAVE_TO_GALLERY: {
                    RadioButtonCell radioCell = (RadioButtonCell) holder.itemView;
                    if (position == saveToGalleryOption1Row) {
                        radioCell.setTextAndValue("save media only from peer chats", "",true, false);
                    } else {
                        radioCell.setTextAndValue("save media from all chats", "",true, false);
                    }

                    break;
                }
                case TYPE_CHOOSE_COLOR: {
                    PeerColorActivity.ChangeNameColorCell cell = (PeerColorActivity.ChangeNameColorCell) holder.itemView;
                    cell.set(getUserConfig().getCurrentUser());
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == TYPE_THEME_TYPE) {
                ((ThemeTypeCell) holder.itemView).setTypeChecked(holder.getAdapterPosition() == Theme.selectedAutoNightType);
            }
            if (type != TYPE_TEXT_INFO_PRIVACY && type != TYPE_SHADOW) {
                holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == scheduleFromRow || position == distanceRow ||
                    position == scheduleToRow || position == scheduleUpdateLocationRow ||
                    position == contactsReimportRow || position == contactsSortRow ||
                    position == bluetoothScoRow || position == searchEngineRow) {
                return TYPE_TEXT_SETTING;
            } else if (position == automaticBrightnessInfoRow || position == scheduleLocationInfoRow || position == swipeGestureInfoRow || position == stickersInfoRow || position == liteModeInfoRow) {
                return TYPE_TEXT_INFO_PRIVACY;
            } else if (position == themeInfoRow || position == nightTypeInfoRow || position == scheduleFromToInfoRow ||
                    position == settings2Row || position == newThemeInfoRow || position == chatListInfoRow || position == bubbleRadiusInfoRow ||
                    position == saveToGallerySectionRow || position == appIconShadowRow || position == lastShadowRow || position == stickersSectionRow ||
                    position == mediaSoundSectionRow || position == otherSectionRow) {
                return TYPE_SHADOW;
            } else if (position == nightDisabledRow || position == nightScheduledRow || position == nightAutomaticRow || position == nightSystemDefaultRow) {
                return TYPE_THEME_TYPE;
            } else if (position == scheduleHeaderRow || position == automaticHeaderRow || position == preferedHeaderRow ||
                    position == settingsRow || position == themeHeaderRow || position == textSizeHeaderRow ||
                    position == chatListHeaderRow || position == bubbleRadiusHeaderRow || position == swipeGestureHeaderRow ||
                    position == selectThemeHeaderRow || position == appIconHeaderRow || position == mediaSoundHeaderRow ||
                    position == otherHeaderRow) {
                return TYPE_HEADER;
            } else if (position == automaticBrightnessRow) {
                return TYPE_BRIGHTNESS;
            } else if (position == scheduleLocationRow || position == sendByEnterRow ||
                    position == raiseToSpeakRow || position == raiseToListenRow || position == pauseOnRecordRow ||
                    position == directShareRow || position == chatBlurRow || position == pauseOnMediaRow || position == nextMediaTapRow || position == sensitiveContentRow) {
                return TYPE_TEXT_CHECK;
            } else if (position == textSizeRow) {
                return TYPE_TEXT_SIZE;
            } else if (position == chatListRow) {
                return TYPE_CHAT_LIST;
            } else if (position == nightThemeRow || position == browserRow) {
                return TYPE_NIGHT_THEME;
            } else if (position == themeListRow) {
                return TYPE_THEME_LIST;
            } else if (position == themeAccentListRow) {
                return TYPE_THEME_ACCENT_LIST;
            } else if (position == bubbleRadiusRow) {
                return TYPE_BUBBLE_RADIUS;
            } else if (position == backgroundRow || position == editThemeRow || position == createNewThemeRow ||
                        position == liteModeRow || position == stickersRow) {
                return TYPE_TEXT_PREFERENCE;
            } else if (position == swipeGestureRow) {
                return TYPE_SWIPE_GESTURE;
            } else if (position == themePreviewRow) {
                return TYPE_THEME_PREVIEW;
            } else if (position == themeListRow2) {
                return TYPE_DEFAULT_THEMES_PREVIEW;
            } else if (position == saveToGalleryOption1Row || position == saveToGalleryOption2Row) {
                return TYPE_SAVE_TO_GALLERY;
            } else if (position == appIconSelectorRow) {
                return TYPE_APP_ICON;
            } else if (position == changeUserColor) {
                return TYPE_CHOOSE_COLOR;
            }
            return TYPE_TEXT_SETTING;
        }
    }

    private static abstract class TintRecyclerListView extends RecyclerListView {
        TintRecyclerListView(Context context) {
            super(context);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, BrightnessControlCell.class, ThemeTypeCell.class, TextSizeCell.class, BubbleRadiusCell.class, ChatListCell.class, NotificationsCheckCell.class, ThemesHorizontalListCell.class, TintRecyclerListView.class, TextCell.class, PeerColorActivity.ChangeNameColorCell.class, SwipeGestureSettingsView.class, DefaultThemesPreviewCell.class, AppIconsSelectorCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{BrightnessControlCell.class}, new String[]{"leftImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{BrightnessControlCell.class}, new String[]{"rightImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{BrightnessControlCell.class}, new String[]{"seekBarView"}, null, null, null, Theme.key_player_progressBackground));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{BrightnessControlCell.class}, new String[]{"seekBarView"}, null, null, null, Theme.key_player_progress));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ThemeTypeCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ThemeTypeCell.class}, new String[]{"checkImage"}, null, null, null, Theme.key_featuredStickers_addedIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{TextSizeCell.class}, new String[]{"sizeBar"}, null, null, null, Theme.key_player_progress));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, new String[]{"sizeBar"}, null, null, null, Theme.key_player_progressBackground));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{BubbleRadiusCell.class}, new String[]{"sizeBar"}, null, null, null, Theme.key_player_progress));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{BubbleRadiusCell.class}, new String[]{"sizeBar"}, null, null, null, Theme.key_player_progressBackground));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ChatListCell.class}, null, null, null, Theme.key_radioBackground));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ChatListCell.class}, null, null, null, Theme.key_radioBackgroundChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, Theme.chat_msgInDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, Theme.chat_msgInMediaDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient1));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_messageTextIn));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_messageTextOut));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckDrawable}, null, Theme.key_chat_outSentCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheckRead));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckReadSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, new Drawable[]{Theme.chat_msgMediaCheckDrawable, Theme.chat_msgMediaHalfCheckDrawable}, null, Theme.key_chat_mediaSentCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_inReplyLine));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_outReplyLine));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_inReplyNameText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_outReplyNameText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_inReplyMessageText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_outReplyMessageText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_inReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_outReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_inTimeText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_outTimeText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_inTimeSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSizeCell.class}, null, null, null, Theme.key_chat_outTimeSelectedText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AppIconsSelectorCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AppIconsSelectorCell.class}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AppIconsSelectorCell.class}, null, null, null, Theme.key_windowBackgroundWhiteHintText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{AppIconsSelectorCell.class}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        themeDescriptions.addAll(SimpleThemeDescription.createThemeDescriptions(() -> {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View ch = listView.getChildAt(i);
                if (ch instanceof AppIconsSelectorCell) {
                    ((AppIconsSelectorCell) ch).getAdapter().notifyDataSetChanged();
                } else if (ch instanceof PeerColorActivity.ChangeNameColorCell) {
                    ((PeerColorActivity.ChangeNameColorCell) ch).updateColors();
                }
            }
            for (int i = 0; i < listView.getCachedChildCount(); i++) {
                View ch = listView.getCachedChildAt(i);
                if (ch instanceof AppIconsSelectorCell) {
                    ((AppIconsSelectorCell) ch).getAdapter().notifyDataSetChanged();
                } else if (ch instanceof PeerColorActivity.ChangeNameColorCell) {
                    ((PeerColorActivity.ChangeNameColorCell) ch).updateColors();
                }
            }
            for (int i = 0; i < listView.getHiddenChildCount(); i++) {
                View ch = listView.getHiddenChildAt(i);
                if (ch instanceof AppIconsSelectorCell) {
                    ((AppIconsSelectorCell) ch).getAdapter().notifyDataSetChanged();
                } else if (ch instanceof PeerColorActivity.ChangeNameColorCell) {
                    ((PeerColorActivity.ChangeNameColorCell) ch).updateColors();
                }
            }
            for (int i = 0; i < listView.getAttachedScrapChildCount(); i++) {
                View ch = listView.getAttachedScrapChildAt(i);
                if (ch instanceof AppIconsSelectorCell) {
                    ((AppIconsSelectorCell) ch).getAdapter().notifyDataSetChanged();
                } else if (ch instanceof PeerColorActivity.ChangeNameColorCell) {
                    ((PeerColorActivity.ChangeNameColorCell) ch).updateColors();
                }
            }
        }, Theme.key_windowBackgroundWhiteHintText, Theme.key_windowBackgroundWhiteBlackText, Theme.key_windowBackgroundWhiteValueText));

        return themeDescriptions;
    }

    boolean lastIsDarkTheme;

    public void checkCurrentDayNight() {
        if (currentType != THEME_TYPE_THEMES_BROWSER) {
            return;
        }
        boolean toDark = !Theme.isCurrentThemeDay();
        if (lastIsDarkTheme != toDark) {
            lastIsDarkTheme = toDark;
            sunDrawable.setCustomEndFrame(toDark ? sunDrawable.getFramesCount() - 1 : 0);
            menuItem.getIconView().playAnimation();
        }
        if (themeListRow2 >= 0) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                if (listView.getChildAt(i) instanceof DefaultThemesPreviewCell) {
                    DefaultThemesPreviewCell cell = (DefaultThemesPreviewCell) listView.getChildAt(i);
                    cell.updateDayNightMode();
                }
            }
        }
    }
}
