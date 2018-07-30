/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.MaxFileSizeCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class DataAutoDownloadActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    private int currentType;

    private int mobileDataDownloadMask;
    private int wifiDownloadMask;
    private int roamingDownloadMask;
    private int mobileDataPrivateDownloadMask;
    private int wifiPrivateDownloadMask;
    private int roamingPrivateDownloadMask;
    private int mobileDataGroupDownloadMask;
    private int wifiGroupDownloadMask;
    private int roamingGroupDownloadMask;
    private int mobileDataChannelDownloadMask;
    private int wifiChannelDownloadMask;
    private int roamingChannelDownloadMask;
    private int mobileMaxSize;
    private int wifiMaxSize;
    private int roamingMaxSize;

    private int mobileSectionRow;
    private int mContactsRow;
    private int mPrivateRow;
    private int mGroupRow;
    private int mChannelsRow;
    private int mSizeRow;
    private int mobileSection2Row;
    private int wifiSectionRow;
    private int wContactsRow;
    private int wPrivateRow;
    private int wGroupRow;
    private int wChannelsRow;
    private int wSizeRow;
    private int wifiSection2Row;
    private int roamingSectionRow;
    private int rContactsRow;
    private int rPrivateRow;
    private int rGroupRow;
    private int rChannelsRow;
    private int rSizeRow;
    private int roamingSection2Row;
    private int rowCount;

    private long maxSize;

    private final static int done_button = 1;

    public DataAutoDownloadActivity(int type) {
        super();
        currentType = type;

        if (currentType == DownloadController.AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
            maxSize = 8 * 1024 * 1024;
        } else if (currentType == DownloadController.AUTODOWNLOAD_MASK_GIF) {
            maxSize = 10 * 1024 * 1024;
        } else {
            maxSize = 1536 * 1024 * 1024;
        }

        mobileDataDownloadMask = DownloadController.getInstance(currentAccount).mobileDataDownloadMask[0];
        mobileDataPrivateDownloadMask = DownloadController.getInstance(currentAccount).mobileDataDownloadMask[1];
        mobileDataGroupDownloadMask = DownloadController.getInstance(currentAccount).mobileDataDownloadMask[2];
        mobileDataChannelDownloadMask = DownloadController.getInstance(currentAccount).mobileDataDownloadMask[3];
        wifiDownloadMask = DownloadController.getInstance(currentAccount).wifiDownloadMask[0];
        wifiPrivateDownloadMask = DownloadController.getInstance(currentAccount).wifiDownloadMask[1];
        wifiGroupDownloadMask = DownloadController.getInstance(currentAccount).wifiDownloadMask[2];
        wifiChannelDownloadMask = DownloadController.getInstance(currentAccount).wifiDownloadMask[3];
        roamingDownloadMask = DownloadController.getInstance(currentAccount).roamingDownloadMask[0];
        roamingPrivateDownloadMask = DownloadController.getInstance(currentAccount).roamingDownloadMask[1];
        roamingGroupDownloadMask = DownloadController.getInstance(currentAccount).roamingDownloadMask[2];
        roamingChannelDownloadMask = DownloadController.getInstance(currentAccount).roamingDownloadMask[2];

        mobileMaxSize = DownloadController.getInstance(currentAccount).mobileMaxFileSize[DownloadController.maskToIndex(currentType)];
        wifiMaxSize = DownloadController.getInstance(currentAccount).wifiMaxFileSize[DownloadController.maskToIndex(currentType)];
        roamingMaxSize = DownloadController.getInstance(currentAccount).roamingMaxFileSize[DownloadController.maskToIndex(currentType)];
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        mobileSectionRow = rowCount++;
        mContactsRow = rowCount++;
        mPrivateRow = rowCount++;
        mGroupRow = rowCount++;
        mChannelsRow = rowCount++;
        if (currentType != DownloadController.AUTODOWNLOAD_MASK_PHOTO) {
            mSizeRow = rowCount++;
        } else {
            mSizeRow = -1;
        }
        mobileSection2Row = rowCount++;
        wifiSectionRow = rowCount++;
        wContactsRow = rowCount++;
        wPrivateRow = rowCount++;
        wGroupRow = rowCount++;
        wChannelsRow = rowCount++;
        if (currentType != DownloadController.AUTODOWNLOAD_MASK_PHOTO) {
            wSizeRow = rowCount++;
        } else {
            wSizeRow = -1;
        }
        wifiSection2Row = rowCount++;
        roamingSectionRow = rowCount++;
        rContactsRow = rowCount++;
        rPrivateRow = rowCount++;
        rGroupRow = rowCount++;
        rChannelsRow = rowCount++;
        if (currentType != DownloadController.AUTODOWNLOAD_MASK_PHOTO) {
            rSizeRow = rowCount++;
        } else {
            rSizeRow = -1;
        }
        roamingSection2Row = rowCount++;

        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (currentType == DownloadController.AUTODOWNLOAD_MASK_PHOTO) {
            actionBar.setTitle(LocaleController.getString("LocalPhotoCache", R.string.LocalPhotoCache));
        } else if (currentType == DownloadController.AUTODOWNLOAD_MASK_AUDIO) {
            actionBar.setTitle(LocaleController.getString("AudioAutodownload", R.string.AudioAutodownload));
        } else if (currentType == DownloadController.AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
            actionBar.setTitle(LocaleController.getString("VideoMessagesAutodownload", R.string.VideoMessagesAutodownload));
        } else if (currentType == DownloadController.AUTODOWNLOAD_MASK_VIDEO) {
            actionBar.setTitle(LocaleController.getString("LocalVideoCache", R.string.LocalVideoCache));
        } else if (currentType == DownloadController.AUTODOWNLOAD_MASK_DOCUMENT) {
            actionBar.setTitle(LocaleController.getString("FilesDataUsage", R.string.FilesDataUsage));
        } else if (currentType == DownloadController.AUTODOWNLOAD_MASK_MUSIC) {
            actionBar.setTitle(LocaleController.getString("AttachMusic", R.string.AttachMusic));
        } else if (currentType == DownloadController.AUTODOWNLOAD_MASK_GIF) {
            actionBar.setTitle(LocaleController.getString("LocalGifCache", R.string.LocalGifCache));
        }
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    DownloadController.getInstance(currentAccount).mobileDataDownloadMask[0] = mobileDataDownloadMask;
                    DownloadController.getInstance(currentAccount).mobileDataDownloadMask[1] = mobileDataPrivateDownloadMask;
                    DownloadController.getInstance(currentAccount).mobileDataDownloadMask[2] = mobileDataGroupDownloadMask;
                    DownloadController.getInstance(currentAccount).mobileDataDownloadMask[3] = mobileDataChannelDownloadMask;
                    DownloadController.getInstance(currentAccount).wifiDownloadMask[0] = wifiDownloadMask;
                    DownloadController.getInstance(currentAccount).wifiDownloadMask[1] = wifiPrivateDownloadMask;
                    DownloadController.getInstance(currentAccount).wifiDownloadMask[2] = wifiGroupDownloadMask;
                    DownloadController.getInstance(currentAccount).wifiDownloadMask[3] = wifiChannelDownloadMask;
                    DownloadController.getInstance(currentAccount).roamingDownloadMask[0] = roamingDownloadMask;
                    DownloadController.getInstance(currentAccount).roamingDownloadMask[1] = roamingPrivateDownloadMask;
                    DownloadController.getInstance(currentAccount).roamingDownloadMask[2] = roamingGroupDownloadMask;
                    DownloadController.getInstance(currentAccount).roamingDownloadMask[3] = roamingChannelDownloadMask;
                    DownloadController.getInstance(currentAccount).mobileMaxFileSize[DownloadController.maskToIndex(currentType)] = mobileMaxSize;
                    DownloadController.getInstance(currentAccount).wifiMaxFileSize[DownloadController.maskToIndex(currentType)] = wifiMaxSize;
                    DownloadController.getInstance(currentAccount).roamingMaxFileSize[DownloadController.maskToIndex(currentType)] = roamingMaxSize;
                    SharedPreferences.Editor editor = MessagesController.getMainSettings(currentAccount).edit();
                    for (int a = 0; a < 4; a++) {
                        editor.putInt("mobileDataDownloadMask" + (a != 0 ? a : ""), DownloadController.getInstance(currentAccount).mobileDataDownloadMask[a]);
                        editor.putInt("wifiDownloadMask" + (a != 0 ? a : ""), DownloadController.getInstance(currentAccount).wifiDownloadMask[a]);
                        editor.putInt("roamingDownloadMask" + (a != 0 ? a : ""), DownloadController.getInstance(currentAccount).roamingDownloadMask[a]);
                    }
                    editor.putInt("mobileMaxDownloadSize" + DownloadController.maskToIndex(currentType), mobileMaxSize);
                    editor.putInt("wifiMaxDownloadSize" + DownloadController.maskToIndex(currentType), wifiMaxSize);
                    editor.putInt("roamingMaxDownloadSize" + DownloadController.maskToIndex(currentType), roamingMaxSize);
                    editor.commit();

                    DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

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
                if (!(view instanceof TextCheckBoxCell)) {
                    return;
                }
                int mask = getMaskForRow(position);
                TextCheckBoxCell textCell = (TextCheckBoxCell) view;
                boolean isChecked = !textCell.isChecked();
                if (isChecked) {
                    mask |= currentType;
                } else {
                    mask &=~ currentType;
                }
                setMaskForRow(position, mask);
                textCell.setChecked(isChecked);
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

    private int getMaskForRow(int position) {
        if (position == mContactsRow) {
            return mobileDataDownloadMask;
        } else if (position == mPrivateRow) {
            return mobileDataPrivateDownloadMask;
        } else if (position == mGroupRow) {
            return mobileDataGroupDownloadMask;
        } else if (position == mChannelsRow) {
            return mobileDataChannelDownloadMask;
        } else if (position == wContactsRow) {
            return wifiDownloadMask;
        } else if (position == wPrivateRow) {
            return wifiPrivateDownloadMask;
        } else if (position == wGroupRow) {
            return wifiGroupDownloadMask;
        } else if (position == wChannelsRow) {
            return wifiChannelDownloadMask;
        } else if (position == rContactsRow) {
            return roamingDownloadMask;
        } else if (position == rPrivateRow) {
            return roamingPrivateDownloadMask;
        } else if (position == rGroupRow) {
            return roamingGroupDownloadMask;
        } else if (position == rChannelsRow) {
            return roamingChannelDownloadMask;
        }
        return 0;
    }

    private void setMaskForRow(int position, int mask) {
        if (position == mContactsRow) {
            mobileDataDownloadMask = mask;
        } else if (position == mPrivateRow) {
            mobileDataPrivateDownloadMask = mask;
        } else if (position == mGroupRow) {
            mobileDataGroupDownloadMask = mask;
        } else if (position == mChannelsRow) {
            mobileDataChannelDownloadMask = mask;
        } else if (position == wContactsRow) {
            wifiDownloadMask = mask;
        } else if (position == wPrivateRow) {
            wifiPrivateDownloadMask = mask;
        } else if (position == wGroupRow) {
            wifiGroupDownloadMask = mask;
        } else if (position == wChannelsRow) {
            wifiChannelDownloadMask = mask;
        } else if (position == rContactsRow) {
            roamingDownloadMask = mask;
        } else if (position == rPrivateRow) {
            roamingPrivateDownloadMask = mask;
        } else if (position == rGroupRow) {
            roamingGroupDownloadMask = mask;
        } else if (position == rChannelsRow) {
            roamingChannelDownloadMask = mask;
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
                    if (position == mobileSection2Row || position == wifiSection2Row) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 1: {
                    TextCheckBoxCell textCell = (TextCheckBoxCell) holder.itemView;
                    if (position == mContactsRow || position == wContactsRow || position == rContactsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("AutodownloadContacts", R.string.AutodownloadContacts), (getMaskForRow(position) & currentType) != 0, true);
                    } else if (position == mPrivateRow || position == wPrivateRow || position == rPrivateRow) {
                        textCell.setTextAndCheck(LocaleController.getString("AutodownloadPrivateChats", R.string.AutodownloadPrivateChats), (getMaskForRow(position) & currentType) != 0, true);
                    } else if (position == mChannelsRow || position == wChannelsRow || position == rChannelsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("AutodownloadChannels", R.string.AutodownloadChannels), (getMaskForRow(position) & currentType) != 0, mSizeRow != -1);
                    } else if (position == mGroupRow || position == wGroupRow || position == rGroupRow) {
                        textCell.setTextAndCheck(LocaleController.getString("AutodownloadGroupChats", R.string.AutodownloadGroupChats), (getMaskForRow(position) & currentType) != 0, true);
                    }
                    break;
                }
                case 2: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == mobileSectionRow) {
                        headerCell.setText(LocaleController.getString("WhenUsingMobileData", R.string.WhenUsingMobileData));
                    } else if (position == wifiSectionRow) {
                        headerCell.setText(LocaleController.getString("WhenConnectedOnWiFi", R.string.WhenConnectedOnWiFi));
                    } else if (position == roamingSectionRow) {
                        headerCell.setText(LocaleController.getString("WhenRoaming", R.string.WhenRoaming));
                    }
                    break;
                }
                case 3: {
                    MaxFileSizeCell cell = (MaxFileSizeCell) holder.itemView;
                    if (position == mSizeRow) {
                        cell.setSize(mobileMaxSize, maxSize);
                        cell.setTag(0);
                    } else if (position == wSizeRow) {
                        cell.setSize(wifiMaxSize, maxSize);
                        cell.setTag(1);
                    } else if (position == rSizeRow) {
                        cell.setSize(roamingMaxSize, maxSize);
                        cell.setTag(2);
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position != mSizeRow && position != rSizeRow && position != wSizeRow && position != mobileSectionRow && position != wifiSectionRow && position != roamingSectionRow && position != mobileSection2Row && position != wifiSection2Row && position != roamingSection2Row;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 1:
                    view = new TextCheckBoxCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new MaxFileSizeCell(mContext) {
                        @Override
                        protected void didChangedSizeValue(int value) {
                            Integer tag = (Integer) getTag();
                            if (tag == 0) {
                                mobileMaxSize = value;
                            } else if (tag == 1) {
                                wifiMaxSize = value;
                            } else if (tag == 2) {
                                roamingMaxSize = value;
                            }
                        }
                    };
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mobileSection2Row || position == wifiSection2Row || position == roamingSection2Row) {
                return 0;
            } else if (position == mobileSectionRow || position == wifiSectionRow || position == roamingSectionRow) {
                return 2;
            } else if (position == wSizeRow || position == mSizeRow || position == rSizeRow) {
                return 3;
            } else {
                return 1;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckBoxCell.class, MaxFileSizeCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{MaxFileSizeCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{MaxFileSizeCell.class}, new String[]{"sizeTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),

                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareUnchecked),
                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareDisabled),
                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareBackground),
                new ThemeDescription(listView, 0, new Class[]{TextCheckBoxCell.class}, null, null, null, Theme.key_checkboxSquareCheck),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),
        };
    }
}
