/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.voip.VoIPController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class DataSettingsActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int mediaDownloadSectionRow;
    private int mobileDownloadRow;
    private int wifiDownloadRow;
    private int roamingDownloadRow;
    private int mediaDownloadSection2Row;
    private int usageSectionRow;
    private int storageUsageRow;
    private int mobileUsageRow;
    private int wifiUsageRow;
    private int roamingUsageRow;
    private int usageSection2Row;
    private int callsSectionRow;
    private int useLessDataForCallsRow;
    private int callsSection2Row;
    private int proxySectionRow;
    private int proxyRow;
    private int proxySection2Row;
    private int rowCount;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        mediaDownloadSectionRow = rowCount++;
        mobileDownloadRow = rowCount++;
        wifiDownloadRow = rowCount++;
        roamingDownloadRow = rowCount++;
        mediaDownloadSection2Row = rowCount++;
        usageSectionRow = rowCount++;
        storageUsageRow = rowCount++;
        mobileUsageRow = rowCount++;
        wifiUsageRow = rowCount++;
        roamingUsageRow = rowCount++;
        usageSection2Row = rowCount++;
        if (MessagesController.getInstance().callsEnabled) {
            callsSectionRow = rowCount++;
            useLessDataForCallsRow = rowCount++;
            callsSection2Row = rowCount++;
        } else {
            callsSection2Row = -1;
            callsSectionRow = -1;
            useLessDataForCallsRow = -1;
        }
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
                if (position == wifiDownloadRow || position == mobileDownloadRow || position == roamingDownloadRow) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    final boolean maskValues[] = new boolean[7];
                    BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());

                    int mask = 0;
                    if (position == mobileDownloadRow) {
                        mask = MediaController.getInstance().mobileDataDownloadMask;
                    } else if (position == wifiDownloadRow) {
                        mask = MediaController.getInstance().wifiDownloadMask;
                    } else if (position == roamingDownloadRow) {
                        mask = MediaController.getInstance().roamingDownloadMask;
                    }

                    builder.setApplyTopPadding(false);
                    builder.setApplyBottomPadding(false);
                    LinearLayout linearLayout = new LinearLayout(getParentActivity());
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    for (int a = 0; a < 7; a++) {
                        String name = null;
                        if (a == 0) {
                            maskValues[a] = (mask & MediaController.AUTODOWNLOAD_MASK_PHOTO) != 0;
                            name = LocaleController.getString("LocalPhotoCache", R.string.LocalPhotoCache);
                        } else if (a == 1) {
                            maskValues[a] = (mask & MediaController.AUTODOWNLOAD_MASK_AUDIO) != 0;
                            name = LocaleController.getString("AudioAutodownload", R.string.AudioAutodownload);
                        } else if (a == 2) {
                            maskValues[a] = (mask & MediaController.AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0;
                            name = LocaleController.getString("VideoMessagesAutodownload", R.string.VideoMessagesAutodownload);
                        } else if (a == 3) {
                            maskValues[a] = (mask & MediaController.AUTODOWNLOAD_MASK_VIDEO) != 0;
                            name = LocaleController.getString("LocalVideoCache", R.string.LocalVideoCache);
                        } else if (a == 4) {
                            maskValues[a] = (mask & MediaController.AUTODOWNLOAD_MASK_DOCUMENT) != 0;
                            name = LocaleController.getString("FilesDataUsage", R.string.FilesDataUsage);
                        } else if (a == 5) {
                            maskValues[a] = (mask & MediaController.AUTODOWNLOAD_MASK_MUSIC) != 0;
                            name = LocaleController.getString("AttachMusic", R.string.AttachMusic);
                        } else if (a == 6) {
                            maskValues[a] = (mask & MediaController.AUTODOWNLOAD_MASK_GIF) != 0;
                            name = LocaleController.getString("LocalGifCache", R.string.LocalGifCache);
                        }
                        CheckBoxCell checkBoxCell = new CheckBoxCell(getParentActivity(), true);
                        checkBoxCell.setTag(a);
                        checkBoxCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                        linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                        checkBoxCell.setText(name, "", maskValues[a], true);
                        checkBoxCell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        checkBoxCell.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                CheckBoxCell cell = (CheckBoxCell) v;
                                int num = (Integer) cell.getTag();
                                maskValues[num] = !maskValues[num];
                                cell.setChecked(maskValues[num], true);
                            }
                        });
                    }
                    BottomSheet.BottomSheetCell cell = new BottomSheet.BottomSheetCell(getParentActivity(), 1);
                    cell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    cell.setTextAndIcon(LocaleController.getString("Save", R.string.Save).toUpperCase(), 0);
                    cell.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                if (visibleDialog != null) {
                                    visibleDialog.dismiss();
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            int newMask = 0;
                            for (int a = 0; a < 7; a++) {
                                if (maskValues[a]) {
                                    if (a == 0) {
                                        newMask |= MediaController.AUTODOWNLOAD_MASK_PHOTO;
                                    } else if (a == 1) {
                                        newMask |= MediaController.AUTODOWNLOAD_MASK_AUDIO;
                                    } else if (a == 2) {
                                        newMask |= MediaController.AUTODOWNLOAD_MASK_VIDEOMESSAGE;
                                    } else if (a == 3) {
                                        newMask |= MediaController.AUTODOWNLOAD_MASK_VIDEO;
                                    } else if (a == 4) {
                                        newMask |= MediaController.AUTODOWNLOAD_MASK_DOCUMENT;
                                    } else if (a == 5) {
                                        newMask |= MediaController.AUTODOWNLOAD_MASK_MUSIC;
                                    } else if (a == 6) {
                                        newMask |= MediaController.AUTODOWNLOAD_MASK_GIF;
                                    }
                                }
                            }
                            SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit();
                            if (position == mobileDownloadRow) {
                                editor.putInt("mobileDataDownloadMask", newMask);
                                MediaController.getInstance().mobileDataDownloadMask = newMask;
                            } else if (position == wifiDownloadRow) {
                                editor.putInt("wifiDownloadMask", newMask);
                                MediaController.getInstance().wifiDownloadMask = newMask;
                            } else if (position == roamingDownloadRow) {
                                editor.putInt("roamingDownloadMask", newMask);
                                MediaController.getInstance().roamingDownloadMask = newMask;
                            }
                            editor.commit();
                            if (listAdapter != null) {
                                listAdapter.notifyItemChanged(position);
                            }
                        }
                    });
                    linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                    builder.setCustomView(linearLayout);
                    showDialog(builder.create());
                } else if (position == storageUsageRow) {
                    presentFragment(new CacheControlActivity());
                } else if (position == useLessDataForCallsRow) {
                    final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
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
                    presentFragment(new ProxySettingsActivity());
                }
            }
        });

        frameLayout.addView(actionBar);

        return fragmentView;
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        MediaController.getInstance().checkAutodownloadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
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
                    if (position == storageUsageRow) {
                        textCell.setText(LocaleController.getString("StorageUsage", R.string.StorageUsage), true);
                    } else if (position == useLessDataForCallsRow) {
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
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
                        textCell.setTextAndValue(LocaleController.getString("VoipUseLessData", R.string.VoipUseLessData), value, false);
                    } else if (position == mobileUsageRow) {
                        textCell.setText(LocaleController.getString("MobileUsage", R.string.MobileUsage), true);
                    } else if (position == roamingUsageRow) {
                        textCell.setText(LocaleController.getString("RoamingUsage", R.string.RoamingUsage), false);
                    } else if (position == wifiUsageRow) {
                        textCell.setText(LocaleController.getString("WiFiUsage", R.string.WiFiUsage), true);
                    } else if (position == proxyRow) {
                        textCell.setText(LocaleController.getString("ProxySettings", R.string.ProxySettings), true);
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
                    }
                    break;
                }
                case 3: {
                    TextDetailSettingsCell textCell = (TextDetailSettingsCell) holder.itemView;

                    if (position == mobileDownloadRow || position == wifiDownloadRow || position == roamingDownloadRow) {
                        int mask;
                        String value;
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        if (position == mobileDownloadRow) {
                            value = LocaleController.getString("WhenUsingMobileData", R.string.WhenUsingMobileData);
                            mask = MediaController.getInstance().mobileDataDownloadMask;
                        } else if (position == wifiDownloadRow) {
                            value = LocaleController.getString("WhenConnectedOnWiFi", R.string.WhenConnectedOnWiFi);
                            mask = MediaController.getInstance().wifiDownloadMask;
                        } else {
                            value = LocaleController.getString("WhenRoaming", R.string.WhenRoaming);
                            mask = MediaController.getInstance().roamingDownloadMask;
                        }
                        String text = "";
                        if ((mask & MediaController.AUTODOWNLOAD_MASK_PHOTO) != 0) {
                            text += LocaleController.getString("LocalPhotoCache", R.string.LocalPhotoCache);
                        }
                        if ((mask & MediaController.AUTODOWNLOAD_MASK_AUDIO) != 0) {
                            if (text.length() != 0) {
                                text += ", ";
                            }
                            text += LocaleController.getString("AudioAutodownload", R.string.AudioAutodownload);
                        }
                        if ((mask & MediaController.AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
                            if (text.length() != 0) {
                                text += ", ";
                            }
                            text += LocaleController.getString("VideoMessagesAutodownload", R.string.VideoMessagesAutodownload);
                        }
                        if ((mask & MediaController.AUTODOWNLOAD_MASK_VIDEO) != 0) {
                            if (text.length() != 0) {
                                text += ", ";
                            }
                            text += LocaleController.getString("LocalVideoCache", R.string.LocalVideoCache);
                        }
                        if ((mask & MediaController.AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
                            if (text.length() != 0) {
                                text += ", ";
                            }
                            text += LocaleController.getString("FilesDataUsage", R.string.FilesDataUsage);
                        }
                        if ((mask & MediaController.AUTODOWNLOAD_MASK_MUSIC) != 0) {
                            if (text.length() != 0) {
                                text += ", ";
                            }
                            text += LocaleController.getString("AttachMusic", R.string.AttachMusic);
                        }
                        if ((mask & MediaController.AUTODOWNLOAD_MASK_GIF) != 0) {
                            if (text.length() != 0) {
                                text += ", ";
                            }
                            text += LocaleController.getString("LocalGifCache", R.string.LocalGifCache);
                        }
                        if (text.length() == 0) {
                            text = LocaleController.getString("NoMediaAutoDownload", R.string.NoMediaAutoDownload);
                        }
                        textCell.setTextAndValue(value, text, true);
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == wifiDownloadRow || position == mobileDownloadRow || position == roamingDownloadRow || position == storageUsageRow ||
                    position == useLessDataForCallsRow || position == mobileUsageRow || position == roamingUsageRow || position == wifiUsageRow || position == proxyRow;
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
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mediaDownloadSection2Row || position == usageSection2Row || position == callsSection2Row || position == proxySection2Row) {
                return 0;
            } else if (position == storageUsageRow || position == useLessDataForCallsRow || position == roamingUsageRow || position == wifiUsageRow || position == mobileUsageRow || position == proxyRow) {
                return 1;
            } else if (position == wifiDownloadRow || position == mobileDownloadRow || position == roamingDownloadRow) {
                return 3;
            } else if (position == mediaDownloadSectionRow || position == callsSectionRow || position == usageSectionRow || position == proxySectionRow) {
                return 2;
            } else {
                return 1;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextSettingsCell.class, TextDetailSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
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

                new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
        };
    }
}
