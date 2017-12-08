/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.StatsController;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class DataUsageActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int currentType;

    private int messagesSectionRow;
    private int messagesSentRow = -1;
    private int messagesReceivedRow = -1;
    private int messagesBytesSentRow;
    private int messagesBytesReceivedRow;
    private int messagesSection2Row;

    private int photosSectionRow;
    private int photosSentRow;
    private int photosReceivedRow;
    private int photosBytesSentRow;
    private int photosBytesReceivedRow;
    private int photosSection2Row;

    private int videosSectionRow;
    private int videosSentRow;
    private int videosReceivedRow;
    private int videosBytesSentRow;
    private int videosBytesReceivedRow;
    private int videosSection2Row;

    private int audiosSectionRow;
    private int audiosSentRow;
    private int audiosReceivedRow;
    private int audiosBytesSentRow;
    private int audiosBytesReceivedRow;
    private int audiosSection2Row;

    private int filesSectionRow;
    private int filesSentRow;
    private int filesReceivedRow;
    private int filesBytesSentRow;
    private int filesBytesReceivedRow;
    private int filesSection2Row;

    private int callsSectionRow;
    private int callsSentRow;
    private int callsReceivedRow;
    private int callsBytesSentRow;
    private int callsBytesReceivedRow;
    private int callsTotalTimeRow;
    private int callsSection2Row;

    private int totalSectionRow;
    private int totalBytesSentRow;
    private int totalBytesReceivedRow;
    private int totalSection2Row;

    private int resetRow;
    private int resetSection2Row;

    private int rowCount;

    public DataUsageActivity(int type) {
        super();
        currentType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;

        photosSectionRow = rowCount++;
        photosSentRow = rowCount++;
        photosReceivedRow = rowCount++;
        photosBytesSentRow = rowCount++;
        photosBytesReceivedRow = rowCount++;
        photosSection2Row = rowCount++;

        videosSectionRow = rowCount++;
        videosSentRow = rowCount++;
        videosReceivedRow = rowCount++;
        videosBytesSentRow = rowCount++;
        videosBytesReceivedRow = rowCount++;
        videosSection2Row = rowCount++;

        audiosSectionRow = rowCount++;
        audiosSentRow = rowCount++;
        audiosReceivedRow = rowCount++;
        audiosBytesSentRow = rowCount++;
        audiosBytesReceivedRow = rowCount++;
        audiosSection2Row = rowCount++;

        filesSectionRow = rowCount++;
        filesSentRow = rowCount++;
        filesReceivedRow = rowCount++;
        filesBytesSentRow = rowCount++;
        filesBytesReceivedRow = rowCount++;
        filesSection2Row = rowCount++;

        callsSectionRow = rowCount++;
        callsSentRow = rowCount++;
        callsReceivedRow = rowCount++;
        callsBytesSentRow = rowCount++;
        callsBytesReceivedRow = rowCount++;
        callsTotalTimeRow = rowCount++;
        callsSection2Row = rowCount++;

        messagesSectionRow = rowCount++;
        /*if (BuildVars.DEBUG_VERSION) {
            messagesSentRow = rowCount++;
            messagesReceivedRow = rowCount++;
        }*/
        messagesBytesSentRow = rowCount++;
        messagesBytesReceivedRow = rowCount++;
        messagesSection2Row = rowCount++;

        totalSectionRow = rowCount++;
        totalBytesSentRow = rowCount++;
        totalBytesReceivedRow = rowCount++;
        totalSection2Row = rowCount++;

        resetRow = rowCount++;
        resetSection2Row = rowCount++;

        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (currentType == 0) {
            actionBar.setTitle(LocaleController.getString("MobileUsage", R.string.MobileUsage));
        } else if (currentType == 1) {
            actionBar.setTitle(LocaleController.getString("WiFiUsage", R.string.WiFiUsage));
        } else if (currentType == 2) {
            actionBar.setTitle(LocaleController.getString("RoamingUsage", R.string.RoamingUsage));
        }
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
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
                if (getParentActivity() == null) {
                    return;
                }
                if (position == resetRow) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("ResetStatisticsAlert", R.string.ResetStatisticsAlert));
                    builder.setPositiveButton(LocaleController.getString("Reset", R.string.Reset), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            StatsController.getInstance().resetStats(currentType);
                            listAdapter.notifyDataSetChanged();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                }
            }
        });

        frameLayout.addView(actionBar);

        return fragmentView;
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
                    if (position == resetSection2Row) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 1: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == resetRow) {
                        textCell.setTag(Theme.key_windowBackgroundWhiteRedText2);
                        textCell.setText(LocaleController.getString("ResetStatistics", R.string.ResetStatistics), false);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText2));
                    } else {
                        int type;
                        textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        if (position == callsSentRow || position == callsReceivedRow || position == callsBytesSentRow || position == callsBytesReceivedRow) {
                            type = StatsController.TYPE_CALLS;
                        } else if (position == messagesSentRow || position == messagesReceivedRow || position == messagesBytesSentRow || position == messagesBytesReceivedRow) {
                            type = StatsController.TYPE_MESSAGES;
                        } else if (position == photosSentRow || position == photosReceivedRow || position == photosBytesSentRow || position == photosBytesReceivedRow) {
                            type = StatsController.TYPE_PHOTOS;
                        } else if (position == audiosSentRow || position == audiosReceivedRow || position == audiosBytesSentRow || position == audiosBytesReceivedRow) {
                            type = StatsController.TYPE_AUDIOS;
                        } else if (position == videosSentRow || position == videosReceivedRow || position == videosBytesSentRow || position == videosBytesReceivedRow) {
                            type = StatsController.TYPE_VIDEOS;
                        } else if (position == filesSentRow || position == filesReceivedRow || position == filesBytesSentRow || position == filesBytesReceivedRow) {
                            type = StatsController.TYPE_FILES;
                        } else {
                            type = StatsController.TYPE_TOTAL;
                        }
                        if (position == callsSentRow) {
                            textCell.setTextAndValue(LocaleController.getString("OutgoingCalls", R.string.OutgoingCalls), String.format("%d", StatsController.getInstance().getSentItemsCount(currentType, type)), true);
                        } else if (position == callsReceivedRow) {
                            textCell.setTextAndValue(LocaleController.getString("IncomingCalls", R.string.IncomingCalls), String.format("%d", StatsController.getInstance().getRecivedItemsCount(currentType, type)), true);
                        } else if (position == callsTotalTimeRow) {
                            int total = StatsController.getInstance().getCallsTotalTime(currentType);
                            int hours = total / 3600;
                            total -= hours * 3600;
                            int minutes = total / 60;
                            total -= minutes * 60;
                            String time;
                            if (hours != 0) {
                                time = String.format("%d:%02d:%02d", hours, minutes, total);
                            } else {
                                time = String.format("%d:%02d", minutes, total);
                            }
                            textCell.setTextAndValue(LocaleController.getString("CallsTotalTime", R.string.CallsTotalTime), time, false);
                        } else if (position == messagesSentRow || position == photosSentRow || position == videosSentRow || position == audiosSentRow || position == filesSentRow) {
                            textCell.setTextAndValue(LocaleController.getString("CountSent", R.string.CountSent), String.format("%d", StatsController.getInstance().getSentItemsCount(currentType, type)), true);
                        } else if (position == messagesReceivedRow || position == photosReceivedRow || position == videosReceivedRow || position == audiosReceivedRow || position == filesReceivedRow) {
                            textCell.setTextAndValue(LocaleController.getString("CountReceived", R.string.CountReceived), String.format("%d", StatsController.getInstance().getRecivedItemsCount(currentType, type)), true);
                        } else if (position == messagesBytesSentRow || position == photosBytesSentRow || position == videosBytesSentRow || position == audiosBytesSentRow || position == filesBytesSentRow || position == callsBytesSentRow || position == totalBytesSentRow) {
                            textCell.setTextAndValue(LocaleController.getString("BytesSent", R.string.BytesSent), AndroidUtilities.formatFileSize(StatsController.getInstance().getSentBytesCount(currentType, type)), true);
                        } else if (position == messagesBytesReceivedRow || position == photosBytesReceivedRow || position == videosBytesReceivedRow || position == audiosBytesReceivedRow || position == filesBytesReceivedRow || position == callsBytesReceivedRow || position == totalBytesReceivedRow) {
                            textCell.setTextAndValue(LocaleController.getString("BytesReceived", R.string.BytesReceived), AndroidUtilities.formatFileSize(StatsController.getInstance().getReceivedBytesCount(currentType, type)), position != totalBytesReceivedRow);
                        }
                    }
                    break;
                }
                case 2: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == totalSectionRow) {
                        headerCell.setText(LocaleController.getString("TotalDataUsage", R.string.TotalDataUsage));
                    } else if (position == callsSectionRow) {
                        headerCell.setText(LocaleController.getString("CallsDataUsage", R.string.CallsDataUsage));
                    } else if (position == filesSectionRow) {
                        headerCell.setText(LocaleController.getString("FilesDataUsage", R.string.FilesDataUsage));
                    } else if (position == audiosSectionRow) {
                        headerCell.setText(LocaleController.getString("LocalAudioCache", R.string.LocalAudioCache));
                    } else if (position == videosSectionRow) {
                        headerCell.setText(LocaleController.getString("LocalVideoCache", R.string.LocalVideoCache));
                    } else if (position == photosSectionRow) {
                        headerCell.setText(LocaleController.getString("LocalPhotoCache", R.string.LocalPhotoCache));
                    } else if (position == messagesSectionRow) {
                        headerCell.setText(LocaleController.getString("MessagesDataUsage", R.string.MessagesDataUsage));
                    }
                    break;
                }
                case 3: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    cell.setText(LocaleController.formatString("NetworkUsageSince", R.string.NetworkUsageSince, LocaleController.getInstance().formatterStats.format(StatsController.getInstance().getResetStatsDate(currentType))));
                    break;
                }
                default:
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getAdapterPosition() == resetRow;
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
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == resetSection2Row) {
                return 3;
            } else if (position == resetSection2Row || position == callsSection2Row || position == filesSection2Row || position == audiosSection2Row || position == videosSection2Row || position == photosSection2Row || position == messagesSection2Row || position == totalSection2Row) {
                return 0;
            } else if (position == totalSectionRow || position == callsSectionRow || position == filesSectionRow || position == audiosSectionRow || position == videosSectionRow || position == photosSectionRow || position == messagesSectionRow) {
                return 2;
            } else {
                return 1;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText2),
        };
    }
}
