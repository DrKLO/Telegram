/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.voip.VoIPController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class DataSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private AnimatorSet animatorSet;

    private int mediaDownloadSectionRow;
    private int autoDownloadMediaRow;
    private int photosRow;
    private int voiceMessagesRow;
    private int videoMessagesRow;
    private int videosRow;
    private int filesRow;
    private int musicRow;
    private int gifsRow;
    private int resetDownloadRow;
    private int mediaDownloadSection2Row;
    private int usageSectionRow;
    private int storageUsageRow;
    private int mobileUsageRow;
    private int wifiUsageRow;
    private int roamingUsageRow;
    private int usageSection2Row;
    private int streamSectionRow;
    private int enableStreamRow;
    private int enableCacheStreamRow;
    private int enableAllStreamRow;
    private int enableAllStreamInfoRow;
    private int callsSectionRow;
    private int useLessDataForCallsRow;
    private int quickRepliesRow;
    private int callsSection2Row;
    private int proxySectionRow;
    private int proxyRow;
    private int proxySection2Row;
    private int rowCount;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        usageSectionRow = rowCount++;
        storageUsageRow = rowCount++;
        mobileUsageRow = rowCount++;
        wifiUsageRow = rowCount++;
        roamingUsageRow = rowCount++;
        usageSection2Row = rowCount++;
        mediaDownloadSectionRow = rowCount++;
        autoDownloadMediaRow = rowCount++;
        photosRow = rowCount++;
        voiceMessagesRow = rowCount++;
        videoMessagesRow = rowCount++;
        videosRow = rowCount++;
        filesRow = rowCount++;
        musicRow = rowCount++;
        gifsRow = rowCount++;
        resetDownloadRow = rowCount++;
        mediaDownloadSection2Row = rowCount++;
        streamSectionRow = rowCount++;
        enableStreamRow = rowCount++;
        if (BuildVars.DEBUG_VERSION) {
            enableAllStreamRow = rowCount++;
        } else {
            enableAllStreamRow = -1;
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

        return true;
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

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, final int position) {
                if (position == photosRow || position == voiceMessagesRow || position == videoMessagesRow || position == videosRow || position == filesRow || position == musicRow || position == gifsRow) {
                    if (!DownloadController.getInstance(currentAccount).globalAutodownloadEnabled) {
                        return;
                    }
                    if (position == photosRow) {
                        presentFragment(new DataAutoDownloadActivity(DownloadController.AUTODOWNLOAD_MASK_PHOTO));
                    } else if (position == voiceMessagesRow) {
                        presentFragment(new DataAutoDownloadActivity(DownloadController.AUTODOWNLOAD_MASK_AUDIO));
                    } else if (position == videoMessagesRow) {
                        presentFragment(new DataAutoDownloadActivity(DownloadController.AUTODOWNLOAD_MASK_VIDEOMESSAGE));
                    } else if (position == videosRow) {
                        presentFragment(new DataAutoDownloadActivity(DownloadController.AUTODOWNLOAD_MASK_VIDEO));
                    } else if (position == filesRow) {
                        presentFragment(new DataAutoDownloadActivity(DownloadController.AUTODOWNLOAD_MASK_DOCUMENT));
                    } else if (position == musicRow) {
                        presentFragment(new DataAutoDownloadActivity(DownloadController.AUTODOWNLOAD_MASK_MUSIC));
                    } else if (position == gifsRow) {
                        presentFragment(new DataAutoDownloadActivity(DownloadController.AUTODOWNLOAD_MASK_GIF));
                    }
                } else if (position == resetDownloadRow) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("ResetAutomaticMediaDownloadAlert", R.string.ResetAutomaticMediaDownloadAlert));
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            SharedPreferences.Editor editor = MessagesController.getMainSettings(currentAccount).edit();
                            DownloadController downloadController = DownloadController.getInstance(currentAccount);
                            for (int a = 0; a < 4; a++) {
                                downloadController.mobileDataDownloadMask[a] = DownloadController.AUTODOWNLOAD_MASK_PHOTO | DownloadController.AUTODOWNLOAD_MASK_AUDIO | DownloadController.AUTODOWNLOAD_MASK_MUSIC | DownloadController.AUTODOWNLOAD_MASK_GIF | DownloadController.AUTODOWNLOAD_MASK_VIDEOMESSAGE;
                                downloadController.wifiDownloadMask[a] = DownloadController.AUTODOWNLOAD_MASK_PHOTO | DownloadController.AUTODOWNLOAD_MASK_AUDIO | DownloadController.AUTODOWNLOAD_MASK_MUSIC | DownloadController.AUTODOWNLOAD_MASK_GIF | DownloadController.AUTODOWNLOAD_MASK_VIDEOMESSAGE;
                                downloadController.roamingDownloadMask[a] = 0;
                                editor.putInt("mobileDataDownloadMask" + (a != 0 ? a : ""), downloadController.mobileDataDownloadMask[a]);
                                editor.putInt("wifiDownloadMask" + (a != 0 ? a : ""), downloadController.wifiDownloadMask[a]);
                                editor.putInt("roamingDownloadMask" + (a != 0 ? a : ""), downloadController.roamingDownloadMask[a]);
                            }
                            for (int a = 0; a < 7; a++) {
                                int sdefault;
                                if (a == 1) {
                                    sdefault = 2 * 1024 * 1024;
                                } else if (a == 6) {
                                    sdefault = 5 * 1024 * 1024;
                                } else {
                                    sdefault = 10 * 1024 * 1024;
                                }
                                downloadController.mobileMaxFileSize[a] = sdefault;
                                downloadController.wifiMaxFileSize[a] = sdefault;
                                downloadController.roamingMaxFileSize[a] = sdefault;
                                editor.putInt("mobileMaxDownloadSize" + a, sdefault);
                                editor.putInt("wifiMaxDownloadSize" + a, sdefault);
                                editor.putInt("roamingMaxDownloadSize" + a, sdefault);
                            }
                            if (!DownloadController.getInstance(currentAccount).globalAutodownloadEnabled) {
                                DownloadController.getInstance(currentAccount).globalAutodownloadEnabled = true;
                                editor.putBoolean("globalAutodownloadEnabled", DownloadController.getInstance(currentAccount).globalAutodownloadEnabled);
                                updateAutodownloadRows(true);
                            }
                            editor.commit();
                            DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.show();
                } else if (position == autoDownloadMediaRow) {
                    DownloadController.getInstance(currentAccount).globalAutodownloadEnabled = !DownloadController.getInstance(currentAccount).globalAutodownloadEnabled;
                    final SharedPreferences preferences = MessagesController.getMainSettings(currentAccount);
                    preferences.edit().putBoolean("globalAutodownloadEnabled", DownloadController.getInstance(currentAccount).globalAutodownloadEnabled).commit();
                    TextCheckCell textCheckCell = (TextCheckCell) view;
                    textCheckCell.setChecked(DownloadController.getInstance(currentAccount).globalAutodownloadEnabled);
                    updateAutodownloadRows(false);
                } else if (position == storageUsageRow) {
                    presentFragment(new CacheControlActivity());
                } else if (position == useLessDataForCallsRow) {
                    final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    Dialog dlg = AlertsCreator.createSingleChoiceDialog(getParentActivity(), DataSettingsActivity.this, new String[]{
                                    LocaleController.getString("UseLessDataNever", R.string.UseLessDataNever),
                                    LocaleController.getString("UseLessDataOnMobile", R.string.UseLessDataOnMobile),
                                    LocaleController.getString("UseLessDataAlways", R.string.UseLessDataAlways)},
                            LocaleController.getString("VoipUseLessData", R.string.VoipUseLessData), preferences.getInt("VoipDataSaving", VoIPController.DATA_SAVING_NEVER), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    int val = -1;
                                    switch (which) {
                                        case 0:
                                            val = VoIPController.DATA_SAVING_NEVER;
                                            break;
                                        case 1:
                                            val = VoIPController.DATA_SAVING_MOBILE;
                                            break;
                                        case 2:
                                            val = VoIPController.DATA_SAVING_ALWAYS;
                                            break;
                                    }
                                    if (val != -1) {
                                        preferences.edit().putInt("VoipDataSaving", val).commit();
                                    }
                                    if (listAdapter != null) {
                                        listAdapter.notifyItemChanged(position);
                                    }
                                }
                            });
                    setVisibleDialog(dlg);
                    dlg.show();
                } else if (position == mobileUsageRow) {
                    presentFragment(new DataUsageActivity(0));
                } else if (position == roamingUsageRow) {
                    presentFragment(new DataUsageActivity(2));
                } else if (position == wifiUsageRow) {
                    presentFragment(new DataUsageActivity(1));
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
                } else if (position == enableCacheStreamRow) {
                    SharedConfig.toggleSaveStreamMedia();
                    TextCheckCell textCheckCell = (TextCheckCell) view;
                    textCheckCell.setChecked(SharedConfig.saveStreamMedia);
                } else if (position == quickRepliesRow) {
                    presentFragment(new QuickRepliesSettingsActivity());
                }
            }
        });

        frameLayout.addView(actionBar);

        return fragmentView;
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void updateAutodownloadRows(boolean check) {
        int count = listView.getChildCount();
        ArrayList<Animator> animators = new ArrayList<>();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.getChildViewHolder(child);
            int type = holder.getItemViewType();
            int p = holder.getAdapterPosition();
            if (p >= photosRow && p <= gifsRow) {
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                textCell.setEnabled(DownloadController.getInstance(currentAccount).globalAutodownloadEnabled, animators);
            } else if (check && p == autoDownloadMediaRow) {
                TextCheckCell textCell = (TextCheckCell) holder.itemView;
                textCell.setChecked(true);
            }
        }
        if (!animators.isEmpty()) {
            if (animatorSet != null) {
                animatorSet.cancel();
            }
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    if (animator.equals(animatorSet)) {
                        animatorSet = null;
                    }
                }
            });
            animatorSet.setDuration(150);
            animatorSet.start();
        }
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
                    if (position == proxySection2Row) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == storageUsageRow) {
                        textCell.setText(LocaleController.getString("StorageUsage", R.string.StorageUsage), true);
                    } else if (position == useLessDataForCallsRow) {
                        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                        String value = null;
                        switch (preferences.getInt("VoipDataSaving", VoIPController.DATA_SAVING_NEVER)) {
                            case VoIPController.DATA_SAVING_NEVER:
                                value = LocaleController.getString("UseLessDataNever", R.string.UseLessDataNever);
                                break;
                            case VoIPController.DATA_SAVING_MOBILE:
                                value = LocaleController.getString("UseLessDataOnMobile", R.string.UseLessDataOnMobile);
                                break;
                            case VoIPController.DATA_SAVING_ALWAYS:
                                value = LocaleController.getString("UseLessDataAlways", R.string.UseLessDataAlways);
                                break;
                        }
                        textCell.setTextAndValue(LocaleController.getString("VoipUseLessData", R.string.VoipUseLessData), value, true);
                    } else if (position == mobileUsageRow) {
                        textCell.setText(LocaleController.getString("MobileUsage", R.string.MobileUsage), true);
                    } else if (position == roamingUsageRow) {
                        textCell.setText(LocaleController.getString("RoamingUsage", R.string.RoamingUsage), false);
                    } else if (position == wifiUsageRow) {
                        textCell.setText(LocaleController.getString("WiFiUsage", R.string.WiFiUsage), true);
                    } else if (position == proxyRow) {
                        textCell.setText(LocaleController.getString("ProxySettings", R.string.ProxySettings), true);
                    } else if (position == resetDownloadRow) {
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText));
                        textCell.setText(LocaleController.getString("ResetAutomaticMediaDownload", R.string.ResetAutomaticMediaDownload), false);
                    } else if (position == photosRow) {
                        textCell.setText(LocaleController.getString("LocalPhotoCache", R.string.LocalPhotoCache), true);
                    } else if (position == voiceMessagesRow) {
                        textCell.setText(LocaleController.getString("AudioAutodownload", R.string.AudioAutodownload), true);
                    } else if (position == videoMessagesRow) {
                        textCell.setText(LocaleController.getString("VideoMessagesAutodownload", R.string.VideoMessagesAutodownload), true);
                    } else if (position == videosRow) {
                        textCell.setText(LocaleController.getString("LocalVideoCache", R.string.LocalVideoCache), true);
                    } else if (position == filesRow) {
                        textCell.setText(LocaleController.getString("FilesDataUsage", R.string.FilesDataUsage), true);
                    } else if (position == musicRow) {
                        textCell.setText(LocaleController.getString("AttachMusic", R.string.AttachMusic), true);
                    } else if (position == gifsRow) {
                        textCell.setText(LocaleController.getString("LocalGifCache", R.string.LocalGifCache), true);
                    } else if (position == quickRepliesRow){
                        textCell.setText(LocaleController.getString("VoipQuickReplies", R.string.VoipQuickReplies), false);
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
                    }
                    break;
                }
                case 3: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == autoDownloadMediaRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("AutoDownloadMedia", R.string.AutoDownloadMedia), DownloadController.getInstance(currentAccount).globalAutodownloadEnabled, true);
                    } else if (position == enableStreamRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EnableStreaming", R.string.EnableStreaming), SharedConfig.streamMedia, enableAllStreamRow != -1);
                    } else if (position == enableCacheStreamRow) {
                        //checkCell.setTextAndCheck(LocaleController.getString("CacheStreamFile", R.string.CacheStreamFile), SharedConfig.saveStreamMedia, true);
                    } else if (position == enableAllStreamRow) {
                        checkCell.setTextAndCheck("Try to Stream All Videos", SharedConfig.streamAllVideo, false);
                    }
                    break;
                }
                case 4: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == enableAllStreamInfoRow) {
                        cell.setText(LocaleController.getString("EnableAllStreamingInfo", R.string.EnableAllStreamingInfo));
                    }
                }
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == 1) {
                int position = holder.getAdapterPosition();
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                if (position >= photosRow && position <= gifsRow) {
                    textCell.setEnabled(DownloadController.getInstance(currentAccount).globalAutodownloadEnabled, null);
                } else {
                    textCell.setEnabled(true, null);
                }
            } else if (viewType == 3) {
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                int position = holder.getAdapterPosition();
                if (position == enableCacheStreamRow) {
                    checkCell.setChecked(SharedConfig.saveStreamMedia);
                } else if (position == enableStreamRow) {
                    checkCell.setChecked(SharedConfig.streamMedia);
                } else if (position == autoDownloadMediaRow) {
                    checkCell.setChecked(DownloadController.getInstance(currentAccount).globalAutodownloadEnabled);
                } else if (position == enableAllStreamRow) {
                    checkCell.setChecked(SharedConfig.streamAllVideo);
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == photosRow || position == voiceMessagesRow || position == videoMessagesRow || position == videosRow || position == filesRow || position == musicRow || position == gifsRow) {
                return DownloadController.getInstance(currentAccount).globalAutodownloadEnabled;
            }
            return position == storageUsageRow || position == useLessDataForCallsRow || position == mobileUsageRow || position == roamingUsageRow || position == wifiUsageRow || position == proxyRow ||
                    position == resetDownloadRow || position == autoDownloadMediaRow || position == enableCacheStreamRow || position == enableStreamRow || position == enableAllStreamRow || position == quickRepliesRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(mContext);
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
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mediaDownloadSection2Row || position == usageSection2Row || position == callsSection2Row || position == proxySection2Row) {
                return 0;
            } else if (position == mediaDownloadSectionRow || position == streamSectionRow || position == callsSectionRow || position == usageSectionRow || position == proxySectionRow) {
                return 2;
            } else if (position == autoDownloadMediaRow || position == enableCacheStreamRow || position == enableStreamRow || position == enableAllStreamRow) {
                return 3;
            } else if (position == enableAllStreamInfoRow) {
                return 4;
            } else {
                return 1;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumb),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumbChecked),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),
        };
    }
}
