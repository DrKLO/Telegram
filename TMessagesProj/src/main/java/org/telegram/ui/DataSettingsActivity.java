/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SaveToGallerySettingsHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.voip.Instance;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LanguageCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.io.File;
import java.util.ArrayList;

public class DataSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private ArrayList<File> storageDirs;

    private int mediaDownloadSectionRow;
    private int mobileRow;
    private int roamingRow;
    private int wifiRow;
    private int storageNumRow;
    private int resetDownloadRow = -1;
    private int mediaDownloadSection2Row;
    private int usageSectionRow;
    private int storageUsageRow;
    private int dataUsageRow;
    private int usageSection2Row;
    private int streamSectionRow;
    private int enableStreamRow;
    private int enableCacheStreamRow;
    private int enableAllStreamRow;
    private int enableMkvRow;
    private int enableAllStreamInfoRow;
    private int autoplayHeaderRow = -1;
    private int autoplayGifsRow = -1;
    private int autoplayVideoRow = -1;
    private int autoplaySectionRow = -1;
    private int callsSectionRow;
    private int useLessDataForCallsRow;
    private int quickRepliesRow;
    private int callsSection2Row;
    private int proxySectionRow;
    private int proxyRow;
    private int proxySection2Row;
    private int clearDraftsRow;
    private int clearDraftsSectionRow;
    private int saveToGallerySectionRow;
    private int saveToGalleryPeerRow;
    private int saveToGalleryChannelsRow;
    private int saveToGalleryGroupsRow;
    private int saveToGalleryDividerRow;

    private int rowCount;

    private boolean updateVoipUseLessData;

    private boolean updateStorageUsageAnimated;
    private boolean storageUsageLoading;
    private long storageUsageSize;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        DownloadController.getInstance(currentAccount).loadAutoDownloadConfig(true);
        updateRows(true);

        return true;
    }

    private void updateRows(boolean fullNotify) {

        rowCount = 0;
        usageSectionRow = rowCount++;
        storageUsageRow = rowCount++;
        dataUsageRow = rowCount++;
        storageNumRow = -1;
        if (Build.VERSION.SDK_INT >= 19) {
            storageDirs = AndroidUtilities.getRootDirs();
            if (storageDirs.size() > 1) {
                storageNumRow = rowCount++;
            }
        }
        usageSection2Row = rowCount++;
        mediaDownloadSectionRow = rowCount++;
        mobileRow = rowCount++;
        wifiRow = rowCount++;
        roamingRow = rowCount++;
        DownloadController dc = getDownloadController();
        boolean isDefault = !(
            !dc.lowPreset.equals(dc.getCurrentRoamingPreset()) || dc.lowPreset.isEnabled() != dc.roamingPreset.enabled ||
            !dc.mediumPreset.equals(dc.getCurrentMobilePreset()) || dc.mediumPreset.isEnabled() != dc.mobilePreset.enabled ||
            !dc.highPreset.equals(dc.getCurrentWiFiPreset()) || dc.highPreset.isEnabled() != dc.wifiPreset.enabled
        );
        int wasResetDownloadRow = resetDownloadRow;
        resetDownloadRow = isDefault ? -1 : rowCount++;
        if (listAdapter != null && !fullNotify) {
            if (wasResetDownloadRow < 0 && resetDownloadRow >= 0) {
                listAdapter.notifyItemChanged(roamingRow);
                listAdapter.notifyItemInserted(resetDownloadRow);
            } else if (wasResetDownloadRow >= 0 && resetDownloadRow < 0) {
                listAdapter.notifyItemChanged(roamingRow);
                listAdapter.notifyItemRemoved(wasResetDownloadRow);
            } else {
                fullNotify = true;
            }
        }
        mediaDownloadSection2Row = rowCount++;

        saveToGallerySectionRow = rowCount++;
        saveToGalleryPeerRow = rowCount++;
        saveToGalleryGroupsRow = rowCount++;
        saveToGalleryChannelsRow = rowCount++;
        saveToGalleryDividerRow = rowCount++;

//        autoplayHeaderRow = rowCount++;
//        autoplayGifsRow = rowCount++;
//        autoplayVideoRow = rowCount++;
//        autoplaySectionRow = rowCount++;
        streamSectionRow = rowCount++;
        enableStreamRow = rowCount++;
        if (BuildVars.DEBUG_VERSION) {
            enableMkvRow = rowCount++;
            enableAllStreamRow = rowCount++;
        } else {
            enableAllStreamRow = -1;
            enableMkvRow = -1;
        }
        enableAllStreamInfoRow = rowCount++;

        enableCacheStreamRow = -1;//rowCount++;
        callsSectionRow = rowCount++;
        useLessDataForCallsRow = rowCount++;
        quickRepliesRow = rowCount++;
        callsSection2Row = rowCount++;
        proxySectionRow = rowCount++;
        proxyRow = rowCount++;
        proxySection2Row = rowCount++;
        clearDraftsRow = rowCount++;
        clearDraftsSectionRow = rowCount++;

        if (listAdapter != null && fullNotify) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void loadCacheSize() {
        final Runnable fireLoading = () -> {
            storageUsageLoading = true;
            if (listAdapter != null && storageUsageRow >= 0) {
                rebind(storageUsageRow);
            }
        };
        AndroidUtilities.runOnUIThread(fireLoading, 100);

        final long start = System.currentTimeMillis();
        CacheControlActivity.calculateTotalSize(size -> {
            AndroidUtilities.cancelRunOnUIThread(fireLoading);
            updateStorageUsageAnimated = updateStorageUsageAnimated || (System.currentTimeMillis() - start) > 120;
            storageUsageSize = size;
            storageUsageLoading = false;
            if (listAdapter != null && storageUsageRow >= 0) {
                rebind(storageUsageRow);
            }
        });

    }

    private void rebind(int position) {
        if (listView == null || listAdapter == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            RecyclerView.ViewHolder holder = listView.getChildViewHolder(child);
            if (holder != null && holder.getAdapterPosition() == position) {
                listAdapter.onBindViewHolder(holder, position);
                return;
            }
        }
    }

    private void rebindAll() {
        if (listView == null || listAdapter == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            RecyclerView.ViewHolder holder = listView.getChildViewHolder(child);
            if (holder != null) {
                listAdapter.onBindViewHolder(holder, listView.getChildAdapterPosition(child));
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        CacheControlActivity.canceled = true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("DataSettings", R.string.DataSettings));
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context) {
            @Override
            public Integer getSelectorColor(int position) {
                if (position == resetDownloadRow) {
                    return Theme.multAlpha(getThemedColor(Theme.key_text_RedRegular), .1f);
                }
                return getThemedColor(Theme.key_listSelector);
            }
        };
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == saveToGalleryGroupsRow || position == saveToGalleryChannelsRow || position == saveToGalleryPeerRow) {
                int flag;
                if (position == saveToGalleryGroupsRow) {
                    flag = SharedConfig.SAVE_TO_GALLERY_FLAG_GROUP;
                } else if (position == saveToGalleryChannelsRow) {
                    flag = SharedConfig.SAVE_TO_GALLERY_FLAG_CHANNELS;
                } else {
                    flag = SharedConfig.SAVE_TO_GALLERY_FLAG_PEER;
                }
                if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                    SaveToGallerySettingsHelper.getSettings(flag).toggle();
                    AndroidUtilities.updateVisibleRows(listView);
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putInt("type", flag);
                    presentFragment(new SaveToGallerySettingsActivity(bundle));
                }

            } else if (position == mobileRow || position == roamingRow || position == wifiRow) {
                if (LocaleController.isRTL && x <= AndroidUtilities.dp(76) || !LocaleController.isRTL && x >= view.getMeasuredWidth() - AndroidUtilities.dp(76)) {
                    boolean wasEnabled = listAdapter.isRowEnabled(resetDownloadRow);

                    NotificationsCheckCell cell = (NotificationsCheckCell) view;
                    boolean checked = cell.isChecked();

                    DownloadController.Preset preset;
                    DownloadController.Preset defaultPreset;
                    String key;
                    String key2;
                    int num;
                    if (position == mobileRow) {
                        preset = DownloadController.getInstance(currentAccount).mobilePreset;
                        defaultPreset = DownloadController.getInstance(currentAccount).mediumPreset;
                        key = "mobilePreset";
                        key2 = "currentMobilePreset";
                        num = 0;
                    } else if (position == wifiRow) {
                        preset = DownloadController.getInstance(currentAccount).wifiPreset;
                        defaultPreset = DownloadController.getInstance(currentAccount).highPreset;
                        key = "wifiPreset";
                        key2 = "currentWifiPreset";
                        num = 1;
                    } else {
                        preset = DownloadController.getInstance(currentAccount).roamingPreset;
                        defaultPreset = DownloadController.getInstance(currentAccount).lowPreset;
                        key = "roamingPreset";
                        key2 = "currentRoamingPreset";
                        num = 2;
                    }
                    if (!checked && preset.enabled) {
                        preset.set(defaultPreset);
                    } else {
                        preset.enabled = !preset.enabled;
                    }
                    SharedPreferences.Editor editor = MessagesController.getMainSettings(currentAccount).edit();
                    editor.putString(key, preset.toString());
                    editor.putInt(key2, 3);
                    editor.commit();

                    cell.setChecked(!checked);
                    RecyclerView.ViewHolder holder = listView.findContainingViewHolder(view);
                    if (holder != null) {
                        listAdapter.onBindViewHolder(holder, position);
                    }
                    DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
                    DownloadController.getInstance(currentAccount).savePresetToServer(num);
                    updateRows(false);
                } else {
                    int type;
                    if (position == mobileRow) {
                        type = 0;
                    } else if (position == wifiRow) {
                        type = 1;
                    } else {
                        type = 2;
                    }
                    presentFragment(new DataAutoDownloadActivity(type));
                }
            } else if (position == resetDownloadRow) {
                if (getParentActivity() == null || !view.isEnabled()) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("ResetAutomaticMediaDownloadAlertTitle", R.string.ResetAutomaticMediaDownloadAlertTitle));
                builder.setMessage(LocaleController.getString("ResetAutomaticMediaDownloadAlert", R.string.ResetAutomaticMediaDownloadAlert));
                builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), (dialogInterface, i) -> {
                    DownloadController.Preset preset;
                    DownloadController.Preset defaultPreset;
                    String key;

                    SharedPreferences.Editor editor = MessagesController.getMainSettings(currentAccount).edit();
                    for (int a = 0; a < 3; a++) {
                        if (a == 0) {
                            preset = DownloadController.getInstance(currentAccount).mobilePreset;
                            defaultPreset = DownloadController.getInstance(currentAccount).mediumPreset;
                            key = "mobilePreset";
                        } else if (a == 1) {
                            preset = DownloadController.getInstance(currentAccount).wifiPreset;
                            defaultPreset = DownloadController.getInstance(currentAccount).highPreset;
                            key = "wifiPreset";
                        } else {
                            preset = DownloadController.getInstance(currentAccount).roamingPreset;
                            defaultPreset = DownloadController.getInstance(currentAccount).lowPreset;
                            key = "roamingPreset";
                        }
                        preset.set(defaultPreset);
                        preset.enabled = defaultPreset.isEnabled();
                        editor.putInt("currentMobilePreset", DownloadController.getInstance(currentAccount).currentMobilePreset = 3);
                        editor.putInt("currentWifiPreset", DownloadController.getInstance(currentAccount).currentWifiPreset = 3);
                        editor.putInt("currentRoamingPreset", DownloadController.getInstance(currentAccount).currentRoamingPreset = 3);
                        editor.putString(key, preset.toString());
                    }
                    editor.commit();
                    DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
                    for (int a = 0; a < 3; a++) {
                        DownloadController.getInstance(currentAccount).savePresetToServer(a);
                    }
                    listAdapter.notifyItemRangeChanged(mobileRow, 4);
                    updateRows(false);
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            } else if (position == storageUsageRow) {
                presentFragment(new CacheControlActivity());
            } else if (position == useLessDataForCallsRow) {
                final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                int selected = 0;
                switch (preferences.getInt("VoipDataSaving", VoIPHelper.getDataSavingDefault())) {
                    case Instance.DATA_SAVING_NEVER:
                        selected = 0;
                        break;
                    case Instance.DATA_SAVING_ROAMING:
                        selected = 1;
                        break;
                    case Instance.DATA_SAVING_MOBILE:
                        selected = 2;
                        break;
                    case Instance.DATA_SAVING_ALWAYS:
                        selected = 3;
                        break;
                }
                Dialog dlg = AlertsCreator.createSingleChoiceDialog(getParentActivity(), new String[]{
                                LocaleController.getString("UseLessDataNever", R.string.UseLessDataNever),
                                LocaleController.getString("UseLessDataOnRoaming", R.string.UseLessDataOnRoaming),
                                LocaleController.getString("UseLessDataOnMobile", R.string.UseLessDataOnMobile),
                                LocaleController.getString("UseLessDataAlways", R.string.UseLessDataAlways)},
                        LocaleController.getString("VoipUseLessData", R.string.VoipUseLessData), selected, (dialog, which) -> {
                            int val = -1;
                            switch (which) {
                                case 0:
                                    val = Instance.DATA_SAVING_NEVER;
                                    break;
                                case 1:
                                    val = Instance.DATA_SAVING_ROAMING;
                                    break;
                                case 2:
                                    val = Instance.DATA_SAVING_MOBILE;
                                    break;
                                case 3:
                                    val = Instance.DATA_SAVING_ALWAYS;
                                    break;
                            }
                            if (val != -1) {
                                preferences.edit().putInt("VoipDataSaving", val).commit();
                                updateVoipUseLessData = true;
                            }
                            if (listAdapter != null) {
                                listAdapter.notifyItemChanged(position);
                            }
                        });
                setVisibleDialog(dlg);
                dlg.show();
            } else if (position == dataUsageRow) {
                presentFragment(new DataUsage2Activity());
            } else if (position == storageNumRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("StoragePath", R.string.StoragePath));
                final LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                builder.setView(linearLayout);

                String dir = storageDirs.get(0).getAbsolutePath();
                if (!TextUtils.isEmpty(SharedConfig.storageCacheDir)) {
                    for (int a = 0, N = storageDirs.size(); a < N; a++) {
                        String path = storageDirs.get(a).getAbsolutePath();
                        if (path.startsWith(SharedConfig.storageCacheDir)) {
                            dir = path;
                            break;
                        }
                    }
                }

                boolean fullString = true;
                try {
                    fullString = storageDirs.size() != 2 || storageDirs.get(0).getAbsolutePath().contains("/storage/emulated/") == storageDirs.get(1).getAbsolutePath().contains("/storage/emulated/");
                } catch (Exception ignore) {}

                for (int a = 0, N = storageDirs.size(); a < N; a++) {
                    File file = storageDirs.get(a);
                    String storageDir = file.getAbsolutePath();
                    LanguageCell cell = new LanguageCell(context);
                    cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                    cell.setTag(a);
                    String description;
                    boolean isInternal = storageDir.contains("/storage/emulated/");
                    if (fullString && !isInternal) {
                        description = LocaleController.formatString("StoragePathFreeValueExternal", R.string.StoragePathFreeValueExternal, AndroidUtilities.formatFileSize(file.getFreeSpace()), storageDir);
                    } else {
                        if (isInternal) {
                            description = LocaleController.formatString("StoragePathFreeInternal", R.string.StoragePathFreeInternal, AndroidUtilities.formatFileSize(file.getFreeSpace()));
                        } else {
                            description = LocaleController.formatString("StoragePathFreeExternal", R.string.StoragePathFreeExternal, AndroidUtilities.formatFileSize(file.getFreeSpace()));
                        }
                    }

                    cell.setValue(
                            isInternal ? LocaleController.getString("InternalStorage", R.string.InternalStorage) : LocaleController.getString("SdCard", R.string.SdCard),
                            description
                    );
                    cell.setLanguageSelected(storageDir.startsWith(dir), false);
                    cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 2));
                    linearLayout.addView(cell);
                    cell.setOnClickListener(v -> {
                        if (!TextUtils.equals(SharedConfig.storageCacheDir, storageDir)) {
                            if (!isInternal) {
                                AlertDialog.Builder confirAlert = new AlertDialog.Builder(getContext());
                                confirAlert.setTitle(LocaleController.getString("DecreaseSpeed", R.string.DecreaseSpeed));
                                confirAlert.setMessage(LocaleController.getString("SdCardAlert", R.string.SdCardAlert));
                                confirAlert.setPositiveButton(LocaleController.getString("Proceed", R.string.Proceed), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                       setStorageDirectory(storageDir);
                                       builder.getDismissRunnable().run();
                                    }
                                });
                                confirAlert.setNegativeButton(LocaleController.getString("Back", R.string.Back), null);
                                confirAlert.show();
                            } else {
                                setStorageDirectory(storageDir);
                                builder.getDismissRunnable().run();
                            }

                        }
                    });
                }
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position == proxyRow) {
                presentFragment(new ProxyListActivity());
            } else if (position == enableStreamRow) {
                SharedConfig.toggleStreamMedia();
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(SharedConfig.streamMedia);
            } else if (position == enableAllStreamRow) {
                SharedConfig.toggleStreamAllVideo();
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(SharedConfig.streamAllVideo);
            } else if (position == enableMkvRow) {
                SharedConfig.toggleStreamMkv();
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(SharedConfig.streamMkv);
            } else if (position == enableCacheStreamRow) {
                SharedConfig.toggleSaveStreamMedia();
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(SharedConfig.saveStreamMedia);
            } else if (position == quickRepliesRow) {
                presentFragment(new QuickRepliesSettingsActivity());
            } else if (position == autoplayGifsRow) {
                SharedConfig.toggleAutoplayGifs();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.isAutoplayGifs());
                }
            } else if (position == autoplayVideoRow) {
                SharedConfig.toggleAutoplayVideo();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.isAutoplayVideo());
                }
            } else if (position == clearDraftsRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AreYouSureClearDraftsTitle", R.string.AreYouSureClearDraftsTitle));
                builder.setMessage(LocaleController.getString("AreYouSureClearDrafts", R.string.AreYouSureClearDrafts));
                builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                    TLRPC.TL_messages_clearAllDrafts req = new TLRPC.TL_messages_clearAllDrafts();
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> getMediaDataController().clearAllDrafts(true)));
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            }
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);

        return fragmentView;
    }

    private void setStorageDirectory(String storageDir) {
        SharedConfig.storageCacheDir = storageDir;
        SharedConfig.saveConfig();
        if (storageDir != null) {
            SharedConfig.readOnlyStorageDirAlertShowed = false;
        }
        rebind(storageNumRow);
        ImageLoader.getInstance().checkMediaPaths(() -> {
            CacheControlActivity.resetCalculatedTotalSIze();
            loadCacheSize();
        });
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadCacheSize();
        rebindAll();
        updateRows(false);
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
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    if (position == clearDraftsSectionRow) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 6: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == storageUsageRow) {
                        if (storageUsageLoading) {
                            textCell.setTextAndValueAndColorfulIcon(LocaleController.getString("StorageUsage", R.string.StorageUsage), "", false, R.drawable.msg_filled_storageusage, getThemedColor(Theme.key_color_lightblue), true);
                            textCell.setDrawLoading(true, 45, updateStorageUsageAnimated);
                        } else {
                            textCell.setTextAndValueAndColorfulIcon(LocaleController.getString("StorageUsage", R.string.StorageUsage), storageUsageSize <= 0 ? "" : AndroidUtilities.formatFileSize(storageUsageSize), true, R.drawable.msg_filled_storageusage, getThemedColor(Theme.key_color_lightblue), true);
                            textCell.setDrawLoading(false, 45, updateStorageUsageAnimated);
                        }
                        updateStorageUsageAnimated = false;
                    } else if (position == dataUsageRow) {
                        StatsController statsController = StatsController.getInstance(currentAccount);
                        long size = (
                            statsController.getReceivedBytesCount(0, StatsController.TYPE_TOTAL) +
                            statsController.getReceivedBytesCount(1, StatsController.TYPE_TOTAL) +
                            statsController.getReceivedBytesCount(2, StatsController.TYPE_TOTAL) +
                            statsController.getSentBytesCount(0, StatsController.TYPE_TOTAL) +
                            statsController.getSentBytesCount(1, StatsController.TYPE_TOTAL) +
                            statsController.getSentBytesCount(2, StatsController.TYPE_TOTAL)
                        );
                        textCell.setTextAndValueAndColorfulIcon(LocaleController.getString("NetworkUsage", R.string.NetworkUsage), AndroidUtilities.formatFileSize(size), true, R.drawable.msg_filled_datausage, getThemedColor(Theme.key_color_green), storageNumRow != -1);
                    } else if (position == storageNumRow) {
                        String dir = storageDirs.get(0).getAbsolutePath();
                        if (!TextUtils.isEmpty(SharedConfig.storageCacheDir)) {
                            for (int a = 0, N = storageDirs.size(); a < N; a++) {
                                String path = storageDirs.get(a).getAbsolutePath();
                                if (path.startsWith(SharedConfig.storageCacheDir)) {
                                    dir = path;
                                    break;
                                }
                            }
                        }
                        final String value = dir == null || dir.contains("/storage/emulated/") ? LocaleController.getString("InternalStorage", R.string.InternalStorage) : LocaleController.getString("SdCard", R.string.SdCard);
                        textCell.setTextAndValueAndColorfulIcon(LocaleController.getString("StoragePath", R.string.StoragePath), value, true, R.drawable.msg_filled_sdcard, getThemedColor(Theme.key_color_yellow), false);
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setCanDisable(false);
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == useLessDataForCallsRow) {
                        textCell.setIcon(0);
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        String value = null;
                        switch (preferences.getInt("VoipDataSaving", VoIPHelper.getDataSavingDefault())) {
                            case Instance.DATA_SAVING_NEVER:
                                value = LocaleController.getString("UseLessDataNever", R.string.UseLessDataNever);
                                break;
                            case Instance.DATA_SAVING_MOBILE:
                                value = LocaleController.getString("UseLessDataOnMobile", R.string.UseLessDataOnMobile);
                                break;
                            case Instance.DATA_SAVING_ROAMING:
                                value = LocaleController.getString("UseLessDataOnRoaming", R.string.UseLessDataOnRoaming);
                                break;
                            case Instance.DATA_SAVING_ALWAYS:
                                value = LocaleController.getString("UseLessDataAlways", R.string.UseLessDataAlways);
                                break;
                        }
                        textCell.setTextAndValue(LocaleController.getString("VoipUseLessData", R.string.VoipUseLessData), value, updateVoipUseLessData, true);
                        updateVoipUseLessData = false;
                    } else if (position == proxyRow) {
                        textCell.setIcon(0);
                        textCell.setText(LocaleController.getString("ProxySettings", R.string.ProxySettings), false);
                    } else if (position == resetDownloadRow) {
                        textCell.setIcon(0);
                        textCell.setCanDisable(true);
                        textCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        textCell.setText(LocaleController.getString("ResetAutomaticMediaDownload", R.string.ResetAutomaticMediaDownload), false);
                    } else if (position == quickRepliesRow){
                        textCell.setIcon(0);
                        textCell.setText(LocaleController.getString("VoipQuickReplies", R.string.VoipQuickReplies), false);
                    } else if (position == clearDraftsRow) {
                        textCell.setIcon(0);
                        textCell.setText(LocaleController.getString("PrivacyDeleteCloudDrafts", R.string.PrivacyDeleteCloudDrafts), false);
                    }
                    break;
                }
                case 2: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == mediaDownloadSectionRow) {
                        headerCell.setText(LocaleController.getString("AutomaticMediaDownload", R.string.AutomaticMediaDownload));
                    } else if (position == usageSectionRow) {
                        headerCell.setText(LocaleController.getString("DataUsage", R.string.DataUsage));
                    } else if (position == callsSectionRow) {
                        headerCell.setText(LocaleController.getString("Calls", R.string.Calls));
                    } else if (position == proxySectionRow) {
                        headerCell.setText(LocaleController.getString("Proxy", R.string.Proxy));
                    } else if (position == streamSectionRow) {
                        headerCell.setText(LocaleController.getString("Streaming", R.string.Streaming));
                    } else if (position == autoplayHeaderRow) {
                        headerCell.setText(LocaleController.getString("AutoplayMedia", R.string.AutoplayMedia));
                    } else if (position == saveToGallerySectionRow) {
                        headerCell.setText(LocaleController.getString("SaveToGallerySettings", R.string.SaveToGallerySettings));
                    }
                    break;
                }
                case 3: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == enableStreamRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EnableStreaming", R.string.EnableStreaming), SharedConfig.streamMedia, enableAllStreamRow != -1);
                    } else if (position == enableCacheStreamRow) {
                        //checkCell.setTextAndCheck(LocaleController.getString("CacheStreamFile", R.string.CacheStreamFile), SharedConfig.saveStreamMedia, true);
                    } else if (position == enableMkvRow) {
                        checkCell.setTextAndCheck("(beta only) Show MKV as Video", SharedConfig.streamMkv, true);
                    } else if (position == enableAllStreamRow) {
                        checkCell.setTextAndCheck("(beta only) Stream All Videos", SharedConfig.streamAllVideo, false);
                    } else if (position == autoplayGifsRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("AutoplayGIF", R.string.AutoplayGIF), SharedConfig.isAutoplayGifs(), true);
                    } else if (position == autoplayVideoRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("AutoplayVideo", R.string.AutoplayVideo), SharedConfig.isAutoplayVideo(), false);
                    }
                    break;
                }
                case 4: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == enableAllStreamInfoRow) {
                        cell.setText(LocaleController.getString("EnableAllStreamingInfo", R.string.EnableAllStreamingInfo));
                    }
                    break;
                }
                case 5: {
                    NotificationsCheckCell checkCell = (NotificationsCheckCell) holder.itemView;

                    String text;
                    CharSequence description = null;
                    DownloadController.Preset preset = null;
                    boolean enabled, divider = true;
                    if (position == saveToGalleryPeerRow) {
                        text = LocaleController.getString("SaveToGalleryPrivate", R.string.SaveToGalleryPrivate);
                        description = SaveToGallerySettingsHelper.user.createDescription(currentAccount);
                        enabled = SaveToGallerySettingsHelper.user.enabled();
                    } else if (position == saveToGalleryGroupsRow) {
                        text = LocaleController.getString("SaveToGalleryGroups", R.string.SaveToGalleryGroups);
                        description = SaveToGallerySettingsHelper.groups.createDescription(currentAccount);
                        enabled = SaveToGallerySettingsHelper.groups.enabled();
                    } else if (position == saveToGalleryChannelsRow) {
                        text = LocaleController.getString("SaveToGalleryChannels", R.string.SaveToGalleryChannels);
                        description = SaveToGallerySettingsHelper.channels.createDescription(currentAccount);
                        enabled = SaveToGallerySettingsHelper.channels.enabled();
                        divider = false;
                    } else if (position == mobileRow) {
                        text = LocaleController.getString("WhenUsingMobileData", R.string.WhenUsingMobileData);
                        enabled = DownloadController.getInstance(currentAccount).mobilePreset.enabled;
                        preset = DownloadController.getInstance(currentAccount).getCurrentMobilePreset();
                    } else if (position == wifiRow) {
                        text = LocaleController.getString("WhenConnectedOnWiFi", R.string.WhenConnectedOnWiFi);
                        enabled = DownloadController.getInstance(currentAccount).wifiPreset.enabled;
                        preset = DownloadController.getInstance(currentAccount).getCurrentWiFiPreset();
                    } else {
                        text = LocaleController.getString("WhenRoaming", R.string.WhenRoaming);
                        enabled = DownloadController.getInstance(currentAccount).roamingPreset.enabled;
                        preset = DownloadController.getInstance(currentAccount).getCurrentRoamingPreset();
                        divider = resetDownloadRow >= 0;
                    }
                    boolean checked;
                    if (preset != null) {
                        StringBuilder builder = new StringBuilder();
                        boolean photos = false;
                        boolean videos = false;
                        boolean files = false;
                        int count = 0;
                        for (int a = 0; a < preset.mask.length; a++) {
                            if (!photos && (preset.mask[a] & DownloadController.AUTODOWNLOAD_TYPE_PHOTO) != 0) {
                                photos = true;
                                count++;
                            }
                            if (!videos && (preset.mask[a] & DownloadController.AUTODOWNLOAD_TYPE_VIDEO) != 0) {
                                videos = true;
                                count++;
                            }
                            if (!files && (preset.mask[a] & DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT) != 0) {
                                files = true;
                                count++;
                            }
                        }
                        if (preset.enabled && count != 0) {
                            if (photos) {
                                builder.append(LocaleController.getString("AutoDownloadPhotosOn", R.string.AutoDownloadPhotosOn));
                            }
                            if (videos) {
                                if (builder.length() > 0) {
                                    builder.append(", ");
                                }
                                builder.append(LocaleController.getString("AutoDownloadVideosOn", R.string.AutoDownloadVideosOn));
                                builder.append(String.format(" (%1$s)", AndroidUtilities.formatFileSize(preset.sizes[DownloadController.typeToIndex(DownloadController.AUTODOWNLOAD_TYPE_VIDEO)], true)));
                            }
                            if (files) {
                                if (builder.length() > 0) {
                                    builder.append(", ");
                                }
                                builder.append(LocaleController.getString("AutoDownloadFilesOn", R.string.AutoDownloadFilesOn));
                                builder.append(String.format(" (%1$s)", AndroidUtilities.formatFileSize(preset.sizes[DownloadController.typeToIndex(DownloadController.AUTODOWNLOAD_TYPE_DOCUMENT)], true)));
                            }
                        } else {
                            builder.append(LocaleController.getString("NoMediaAutoDownload", R.string.NoMediaAutoDownload));
                        }
                        checked = (photos || videos || files) && enabled;
                        description = builder;
                    } else {
                        checked = enabled;
                    }
                    checkCell.setAnimationsEnabled(true);
                    checkCell.setTextAndValueAndCheck(text, description, checked, 0, true, divider);
                    break;
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == 3) {
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                int position = holder.getAdapterPosition();
                if (position == enableCacheStreamRow) {
                    checkCell.setChecked(SharedConfig.saveStreamMedia);
                } else if (position == enableStreamRow) {
                    checkCell.setChecked(SharedConfig.streamMedia);
                } else if (position == enableAllStreamRow) {
                    checkCell.setChecked(SharedConfig.streamAllVideo);
                } else if (position == enableMkvRow) {
                    checkCell.setChecked(SharedConfig.streamMkv);
                } else if (position == autoplayGifsRow) {
                    checkCell.setChecked(SharedConfig.isAutoplayGifs());
                } else if (position == autoplayVideoRow) {
                    checkCell.setChecked(SharedConfig.isAutoplayVideo());
                }
            }
        }

        public boolean isRowEnabled(int position) {
            return position == mobileRow || position == roamingRow || position == wifiRow || position == storageUsageRow || position == useLessDataForCallsRow || position == dataUsageRow || position == proxyRow || position == clearDraftsRow ||
                    position == enableCacheStreamRow || position == enableStreamRow || position == enableAllStreamRow || position == enableMkvRow || position == quickRepliesRow || position == autoplayVideoRow || position == autoplayGifsRow ||
                    position == storageNumRow || position == saveToGalleryGroupsRow || position == saveToGalleryPeerRow || position == saveToGalleryChannelsRow || position == resetDownloadRow;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return isRowEnabled(holder.getAdapterPosition());
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(mContext, 22);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 5:
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                default:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mediaDownloadSection2Row || position == usageSection2Row || position == callsSection2Row || position == proxySection2Row || position == autoplaySectionRow || position == clearDraftsSectionRow || position == saveToGalleryDividerRow) {
                return 0;
            } else if (position == mediaDownloadSectionRow || position == streamSectionRow || position == callsSectionRow || position == usageSectionRow || position == proxySectionRow || position == autoplayHeaderRow || position == saveToGallerySectionRow) {
                return 2;
            } else if (position == enableCacheStreamRow || position == enableStreamRow || position == enableAllStreamRow || position == enableMkvRow || position == autoplayGifsRow || position == autoplayVideoRow) {
                return 3;
            } else if (position == enableAllStreamInfoRow) {
                return 4;
            } else if (position == mobileRow || position == wifiRow || position == roamingRow || position == saveToGalleryGroupsRow || position == saveToGalleryPeerRow || position == saveToGalleryChannelsRow) {
                return 5;
            } else if (position == storageUsageRow || position == dataUsageRow || position == storageNumRow) {
                return 6;
            } else {
                return 1;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        return themeDescriptions;
    }
}
