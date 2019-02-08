/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
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
import android.support.v4.content.FileProvider;
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
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.widget.DefaultItemAnimator;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.time.SunDate;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.BrightnessControlCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.ThemeCell;
import org.telegram.ui.Cells.ThemeTypeCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.ThemeEditorView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ThemeActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public final static int THEME_TYPE_BASIC = 0;
    public final static int THEME_TYPE_NIGHT = 1;

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int backgroundRow;
    private int textSizeHeaderRow;
    private int textSizeRow;
    private int settingsRow;
    private int customTabsRow;
    private int directShareRow;
    private int raiseToSpeakRow;
    private int sendByEnterRow;
    private int autoplayGifsRow;
    private int saveToGalleryRow;
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
    private int themeStartRow;
    private int themeEndRow;
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

        private LinearLayout messagesContainer;
        private ChatMessageCell[] cells = new ChatMessageCell[2];
        private SeekBarView sizeBar;
        private Drawable shadowDrawable;
        private int startFontSize = 12;
        private int endFontSize = 30;
        private int lastWidth;

        private TextPaint textPaint;

        public TextSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);

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
                    for (int a = 0; a < cells.length; a++) {
                        cells[a].getMessageObject().resetLayout();
                        cells[a].requestLayout();
                    }
                }
            });
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 0));

            messagesContainer = new LinearLayout(context) {

                private Drawable backgroundDrawable;

                @Override
                protected void onDraw(Canvas canvas) {
                    Drawable newDrawable = Theme.getCachedWallpaperNonBlocking();
                    if (newDrawable != null) {
                        backgroundDrawable = newDrawable;
                    }
                    if (backgroundDrawable instanceof ColorDrawable) {
                        backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        backgroundDrawable.draw(canvas);
                    } else if (backgroundDrawable instanceof BitmapDrawable) {
                        BitmapDrawable bitmapDrawable = (BitmapDrawable) backgroundDrawable;
                        if (bitmapDrawable.getTileModeX() == Shader.TileMode.REPEAT) {
                            canvas.save();
                            float scale = 2.0f / AndroidUtilities.density;
                            canvas.scale(scale, scale);
                            backgroundDrawable.setBounds(0, 0, (int) Math.ceil(getMeasuredWidth() / scale), (int) Math.ceil(getMeasuredHeight() / scale));
                            backgroundDrawable.draw(canvas);
                            canvas.restore();
                        } else {
                            int viewHeight = getMeasuredHeight();
                            float scaleX = (float) getMeasuredWidth() / (float) backgroundDrawable.getIntrinsicWidth();
                            float scaleY = (float) (viewHeight) / (float) backgroundDrawable.getIntrinsicHeight();
                            float scale = scaleX < scaleY ? scaleY : scaleX;
                            int width = (int) Math.ceil(backgroundDrawable.getIntrinsicWidth() * scale);
                            int height = (int) Math.ceil(backgroundDrawable.getIntrinsicHeight() * scale);
                            int x = (getMeasuredWidth() - width) / 2;
                            int y = (viewHeight - height) / 2;
                            canvas.save();
                            canvas.clipRect(0, 0, width, getMeasuredHeight());
                            backgroundDrawable.setBounds(x, y, x + width, y + height);
                            backgroundDrawable.draw(canvas);
                            canvas.restore();
                        }
                    } else {
                        super.onDraw(canvas);
                    }

                    shadowDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    shadowDrawable.draw(canvas);
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    return false;
                }

                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    return false;
                }

                @Override
                protected void dispatchSetPressed(boolean pressed) {

                }

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    return false;
                }
            };
            messagesContainer.setOrientation(LinearLayout.VERTICAL);
            messagesContainer.setWillNotDraw(false);
            messagesContainer.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(11));
            addView(messagesContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 53, 0, 0));

            int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;
            TLRPC.Message message = new TLRPC.TL_message();
            message.message = LocaleController.getString("FontSizePreviewReply", R.string.FontSizePreviewReply);
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = 0;
            MessageObject replyMessageObject = new MessageObject(currentAccount, message, true);

            message = new TLRPC.TL_message();
            message.message = LocaleController.getString("FontSizePreviewLine2", R.string.FontSizePreviewLine2);
            message.date = date + 960;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = 0;
            MessageObject message1 = new MessageObject(currentAccount, message, true);
            message1.resetLayout();
            message1.eventId = 1;

            message = new TLRPC.TL_message();
            message.message = LocaleController.getString("FontSizePreviewLine1", R.string.FontSizePreviewLine1);
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 257 + 8;
            message.from_id = 0;
            message.id = 1;
            message.reply_to_msg_id = 5;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = false;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
            MessageObject message2 = new MessageObject(currentAccount, message, true);
            message2.customReplyName = "Lucio";
            message2.eventId = 1;
            message2.resetLayout();
            message2.replyMessageObject = replyMessageObject;

            for (int a = 0; a < cells.length; a++) {
                cells[a] = new ChatMessageCell(context);
                cells[a].setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

                });
                cells[a].isChat = false;
                cells[a].setFullyDraw(true);
                cells[a].setMessageObject(a == 0 ? message2 : message1, null, false, false);
                messagesContainer.addView(cells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }
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
            messagesContainer.invalidate();
            sizeBar.invalidate();
            for (int a = 0; a < cells.length; a++) {
                cells[a].invalidate();
            }
        }
    }

    private int currentType;

    public ThemeActivity(int type) {
        super();
        currentType = type;
        updateRows();
    }

    private void updateRows() {
        int oldRowCount = rowCount;

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
        themeStartRow = -1;
        themeEndRow = -1;
        themeInfoRow = -1;
        preferedHeaderRow = -1;
        automaticHeaderRow = -1;
        automaticBrightnessRow = -1;
        automaticBrightnessInfoRow = -1;
        textSizeHeaderRow = -1;
        themeHeaderRow = -1;

        textSizeRow = -1;
        backgroundRow = -1;
        settingsRow = -1;
        customTabsRow = -1;
        directShareRow = -1;
        enableAnimationsRow = -1;
        raiseToSpeakRow = -1;
        sendByEnterRow = -1;
        autoplayGifsRow = -1;
        saveToGalleryRow = -1;
        settings2Row = -1;
        stickersRow = -1;
        stickersSection2Row = -1;

        if (currentType == THEME_TYPE_BASIC) {
            textSizeHeaderRow = rowCount++;
            textSizeRow = rowCount++;
            backgroundRow = rowCount++;
            nightThemeRow = rowCount++;
            newThemeInfoRow = rowCount++;
            themeHeaderRow = rowCount++;
            themeStartRow = rowCount;
            rowCount += Theme.themes.size();
            themeEndRow = rowCount;
            themeInfoRow = rowCount++;

            settingsRow = rowCount++;
            customTabsRow = rowCount++;
            directShareRow = rowCount++;
            enableAnimationsRow = rowCount++;
            raiseToSpeakRow = rowCount++;
            sendByEnterRow = rowCount++;
            autoplayGifsRow = rowCount++;
            saveToGalleryRow = rowCount++;
            settings2Row = rowCount++;
            stickersRow = rowCount++;
            stickersSection2Row = rowCount++;
        } else {
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
                themeStartRow = rowCount;
                rowCount += Theme.themes.size();
                themeEndRow = rowCount;
                themeInfoRow = rowCount++;
            }
        }

        if (listAdapter != null) {
            if (currentType == THEME_TYPE_BASIC || previousUpdatedType == -1) {
                listAdapter.notifyDataSetChanged();
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
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        stopLocationUpdate();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.locationPermissionGranted);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
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
            item.addSubItem(create_theme, LocaleController.getString("CreateNewThemeMenu", R.string.CreateNewThemeMenu));
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
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
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
            } else if (position == autoplayGifsRow) {
                SharedConfig.toggleAutoplayGifs();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.autoplayGifs);
                }
            } else if (position == saveToGalleryRow) {
                SharedConfig.toggleSaveToGallery();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.saveToGallery);
                }
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
                presentFragment(new StickersActivity(DataQuery.TYPE_IMAGE));
            } else if (position == emojiRow) {
                if (getParentActivity() == null) {
                    return;
                }
                final boolean maskValues[] = new boolean[2];
                BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());

                builder.setApplyTopPadding(false);
                builder.setApplyBottomPadding(false);
                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                for (int a = 0; a < (Build.VERSION.SDK_INT >= 19 ? 2 : 1); a++) {
                    String name = null;
                    if (a == 0) {
                        maskValues[a] = SharedConfig.allowBigEmoji;
                        name = LocaleController.getString("EmojiBigSize", R.string.EmojiBigSize);
                    } else if (a == 1) {
                        maskValues[a] = SharedConfig.useSystemEmoji;
                        name = LocaleController.getString("EmojiUseDefault", R.string.EmojiUseDefault);
                    }
                    CheckBoxCell checkBoxCell = new CheckBoxCell(getParentActivity(), 1, 21);
                    checkBoxCell.setTag(a);
                    checkBoxCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                    checkBoxCell.setText(name, "", maskValues[a], true);
                    checkBoxCell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    checkBoxCell.setOnClickListener(v -> {
                        CheckBoxCell cell = (CheckBoxCell) v;
                        int num = (Integer) cell.getTag();
                        maskValues[num] = !maskValues[num];
                        cell.setChecked(maskValues[num], true);
                    });
                }
                BottomSheet.BottomSheetCell cell = new BottomSheet.BottomSheetCell(getParentActivity(), 1);
                cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                cell.setTextAndIcon(LocaleController.getString("Save", R.string.Save).toUpperCase(), 0);
                cell.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
                cell.setOnClickListener(v -> {
                    try {
                        if (visibleDialog != null) {
                            visibleDialog.dismiss();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                    editor.putBoolean("allowBigEmoji", SharedConfig.allowBigEmoji = maskValues[0]);
                    editor.putBoolean("useSystemEmoji", SharedConfig.useSystemEmoji = maskValues[1]);
                    editor.commit();
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(position);
                    }
                });
                linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                builder.setCustomView(linearLayout);
                showDialog(builder.create());
            } else if (position >= themeStartRow && position < themeEndRow) {
                int p = position - themeStartRow;
                if (p >= 0 && p < Theme.themes.size()) {
                    Theme.ThemeInfo themeInfo = Theme.themes.get(p);
                    if (currentType == THEME_TYPE_BASIC) {
                        if (themeInfo == Theme.getCurrentTheme()) {
                            return;
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, themeInfo, false);
                    } else {
                        Theme.setCurrentNightTheme(themeInfo);
                    }
                    int count = listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof ThemeCell) {
                            ((ThemeCell) child).updateCurrentThemeCheck();
                        }
                    }
                }
            } else if (position == nightThemeRow) {
                presentFragment(new ThemeActivity(THEME_TYPE_NIGHT));
            } else if (position == nightDisabledRow) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_NONE;
                updateRows();
                Theme.checkAutoNightThemeConditions();
            } else if (position == nightScheduledRow) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_SCHEDULED;
                if (Theme.autoNightScheduleByLocation) {
                    updateSunTime(null, true);
                }
                updateRows();
                Theme.checkAutoNightThemeConditions();
            } else if (position == nightAutomaticRow) {
                Theme.selectedAutoNightType = Theme.AUTO_NIGHT_TYPE_AUTOMATIC;
                updateRows();
                Theme.checkAutoNightThemeConditions();
            } else if (position == scheduleLocationRow) {
                Theme.autoNightScheduleByLocation = !Theme.autoNightScheduleByLocation;
                TextCheckCell checkCell = (TextCheckCell) view;
                checkCell.setChecked(Theme.autoNightScheduleByLocation);
                updateRows();
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
            listAdapter.notifyDataSetChanged();
        }
    }

    private void openThemeCreate() {
        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("NewTheme", R.string.NewTheme));
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {

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
            String name = editText.getText().toString() + ".attheme";
            themeEditorView.show(getParentActivity(), name);
            Theme.saveCurrentTheme(name, true);
            updateRows();
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
            } else if (lastKnownLocation == null) {
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
        int time[] = SunDate.calculateSunriseSunset(Theme.autoNightLocationLatitude, Theme.autoNightLocationLongitude);
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

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

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
            return type == 0 || type == 1 || type == 4 || type == 7;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ThemeCell(mContext, currentType == THEME_TYPE_NIGHT);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    if (currentType == THEME_TYPE_BASIC) {
                        ((ThemeCell) view).setOnOptionsClick(v -> {
                            final Theme.ThemeInfo themeInfo = ((ThemeCell) v.getParent()).getCurrentThemeInfo();
                            if (getParentActivity() == null) {
                                return;
                            }

                            BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                            CharSequence[] items;
                            if (themeInfo.pathToFile == null) {
                                items = new CharSequence[]{
                                        LocaleController.getString("ShareFile", R.string.ShareFile)
                                };
                            } else {
                                items = new CharSequence[]{
                                        LocaleController.getString("ShareFile", R.string.ShareFile),
                                        LocaleController.getString("Edit", R.string.Edit),
                                        LocaleController.getString("Delete", R.string.Delete)};
                            }
                            builder.setItems(items, (dialog, which) -> {
                                if (which == 0) {
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
                                    File finalFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), currentFile.getName());
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
                                } else if (which == 1) {
                                    if (parentLayout != null) {
                                        Theme.applyTheme(themeInfo);
                                        parentLayout.rebuildAllFragmentViews(true, true);
                                        new ThemeEditorView().show(getParentActivity(), themeInfo.name);
                                    }
                                } else {
                                    if (getParentActivity() == null) {
                                        return;
                                    }
                                    AlertDialog.Builder builder1 = new AlertDialog.Builder(getParentActivity());
                                    builder1.setMessage(LocaleController.getString("DeleteThemeAlert", R.string.DeleteThemeAlert));
                                    builder1.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                    builder1.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                                        if (Theme.deleteTheme(themeInfo)) {
                                            parentLayout.rebuildAllFragmentViews(true, true);
                                        }
                                        updateRows();
                                    });
                                    builder1.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                    showDialog(builder1.create());
                                }
                            });
                            showDialog(builder.create());
                        });
                    }
                    break;
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
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
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
                default:
                    view = new TextSizeCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;

            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    position -= themeStartRow;
                    Theme.ThemeInfo themeInfo = Theme.themes.get(position);
                    ((ThemeCell) holder.itemView).setTheme(themeInfo, position != Theme.themes.size() - 1);
                    break;
                }
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
                        cell.setText(LocaleController.getString("ChatBackground", R.string.ChatBackground), true);
                    } else if (position == contactsReimportRow) {
                        cell.setText(LocaleController.getString("ImportContacts", R.string.ImportContacts), true);
                    } else if (position == stickersRow) {
                        cell.setText(LocaleController.getString("StickersAndMasks", R.string.StickersAndMasks), false);
                    } else if (position == emojiRow) {
                        cell.setText(LocaleController.getString("Emoji", R.string.Emoji), true);
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
                case 4: {
                    ThemeTypeCell typeCell = (ThemeTypeCell) holder.itemView;
                    if (position == nightDisabledRow) {
                        typeCell.setValue(LocaleController.getString("AutoNightDisabled", R.string.AutoNightDisabled), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_NONE, true);
                    } else if (position == nightScheduledRow) {
                        typeCell.setValue(LocaleController.getString("AutoNightScheduled", R.string.AutoNightScheduled), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED, true);
                    } else if (position == nightAutomaticRow) {
                        typeCell.setValue(LocaleController.getString("AutoNightAutomatic", R.string.AutoNightAutomatic), Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_AUTOMATIC, false);
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
                        textCheckCell.setTextAndCheck(LocaleController.getString("SaveToGallerySettings", R.string.SaveToGallerySettings), SharedConfig.saveToGallery, false);
                    } else if (position == autoplayGifsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("AutoplayGifs", R.string.AutoplayGifs), SharedConfig.autoplayGifs, true);
                    } else if (position == raiseToSpeakRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("RaiseToSpeak", R.string.RaiseToSpeak), SharedConfig.raiseToSpeak, true);
                    } else if (position == customTabsRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("ChromeCustomTabs", R.string.ChromeCustomTabs), LocaleController.getString("ChromeCustomTabsInfo", R.string.ChromeCustomTabsInfo), SharedConfig.customTabs, false, true);
                    } else if (position == directShareRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("DirectShare", R.string.DirectShare), LocaleController.getString("DirectShareInfo", R.string.DirectShareInfo), SharedConfig.directShare, false, true);
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
            } else if (type == 0) {
                ((ThemeCell) holder.itemView).updateCurrentThemeCheck();
            }
            if (type != 2 && type != 3) {
                holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == nightThemeRow || position == scheduleFromRow || position == emojiRow ||
                    position == scheduleToRow || position == scheduleUpdateLocationRow || position == backgroundRow ||
                    position == contactsReimportRow || position == contactsSortRow || position == stickersRow) {
                return 1;
            } else if (position == automaticBrightnessInfoRow || position == scheduleLocationInfoRow) {
                return 2;
            } else if (position == themeInfoRow || position == nightTypeInfoRow || position == scheduleFromToInfoRow ||
                    position == stickersSection2Row || position == settings2Row || position == newThemeInfoRow) {
                return 3;
            } else if (position == nightDisabledRow || position == nightScheduledRow || position == nightAutomaticRow) {
                return 4;
            } else if (position == scheduleHeaderRow || position == automaticHeaderRow || position == preferedHeaderRow ||
                    position == settingsRow || position == themeHeaderRow || position == textSizeHeaderRow) {
                return 5;
            } else if (position == automaticBrightnessRow) {
                return 6;
            } else if (position == scheduleLocationRow || position == enableAnimationsRow || position == sendByEnterRow ||
                    position == saveToGalleryRow || position == autoplayGifsRow || position == raiseToSpeakRow ||
                    position == customTabsRow || position == directShareRow) {
                return 7;
            } else if (position == textSizeRow) {
                return 8;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, BrightnessControlCell.class, ThemeTypeCell.class, ThemeCell.class, TextSizeCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{ThemeCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ThemeCell.class}, new String[]{"checkImage"}, null, null, null, Theme.key_featuredStickers_addedIcon),
                new ThemeDescription(listView, 0, new Class[]{ThemeCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menu),

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

                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgInShadowDrawable, Theme.chat_msgInMediaShadowDrawable}, null, Theme.key_chat_inBubbleShadow),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutShadowDrawable, Theme.chat_msgOutMediaShadowDrawable}, null, Theme.key_chat_outBubbleShadow),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_messageTextIn),
                new ThemeDescription(listView, 0, null, null, null, null, Theme.key_chat_messageTextOut),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheck),
                new ThemeDescription(listView, 0, null, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected),
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
