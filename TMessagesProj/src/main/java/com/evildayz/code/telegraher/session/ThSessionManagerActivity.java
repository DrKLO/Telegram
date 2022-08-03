/**
 * Copyright 2022  Nikita S. <nikita@saraeff.net>
 * <p>
 * This file is part of Telegraher.
 * <p>
 * Telegraher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Telegraher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with Telegraher.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.evildayz.code.telegraher.session;


import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.evildayz.code.telegraher.ui.ThTextCheckShadowbanCell;
import com.evildayz.code.telegraher.ui.ThTextDetailCell;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.*;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.QrActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ThSessionManagerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private RecyclerListView listView;

    private ThSessionManagerActivity.ListAdapter adapter;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;
    private Map<Integer, Long> map;
    private Map<Integer, Integer> accs;
    private final static String[] EMOJIS = {"\uD83C\uDF1A", "\uD83C\uDF1E"};

    public ThSessionManagerActivity() {
        rowCount = 0;
        accs = new HashMap<>();
        for (int a : SharedConfig.thAccounts.keySet()) {
            accs.put(rowCount++, a);
        }
    }

    private int rowCount;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.telegraherSettingsUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.telegraherSettingsUpdated);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.THAccountSessionManager));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(adapter = new ThSessionManagerActivity.ListAdapter(context));
        listView.setOnItemClickListener((view, position, x, y) -> {
            boolean enabled = false;
            if (getParentActivity() == null) {
                return;
            }
            if (false) {
                //durov relogin!
            } else if (position >= 0 && position < rowCount) {
                if (((ThTextDetailCell) view).isChecked()) {
                    SharedConfig.activeAccounts.remove(accs.get(position));
                } else {
                    SharedConfig.activeAccounts.add(accs.get(position));
                }
                SharedConfig.saveAccounts();
                ((ThTextDetailCell) view).setChecked(!((ThTextDetailCell) view).isChecked());
                ((ThTextDetailCell) view).setText(String.format("%s %s ",
                        ((ThTextDetailCell) view).isChecked() ? EMOJIS[1] : EMOJIS[0],
                        SharedConfig.thAccounts.get(accs.get(position)).get("userPhone").toString()));
            }
        });

        listView.setOnItemLongClickListener((view, position, x, y) -> {
            boolean enabled = false;
            if (getParentActivity() == null) {
                return enabled;
            }
            if (false) {
                //durov relogin!
            } else if (position >= 0 && position < rowCount) {
                presentFragment(new THSessionInfoActivity(accs.get(position)));
            }
            return enabled;
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (false) {
            //durov relogin!
        } else if (id == NotificationCenter.telegraherSettingsUpdated) {
            adapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return true;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new ThTextCheckShadowbanCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new NotificationsCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 5:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new SlideChooseView(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 7:
                    view = new ThTextDetailCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 8:
                    view = new ChatMessageCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (false) {
                        //durov relogin!
                    }
                    break;
                }
                case 1: {
                    ThTextCheckShadowbanCell checkCell = (ThTextCheckShadowbanCell) holder.itemView;
                    if (false) {
                        //durov relogin!
                    }
                    break;
                }
                case 5: {
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    break;
                }
                case 6: {
                    SlideChooseView slideChooseView = (SlideChooseView) holder.itemView;
                    break;
                }
                case 7: {
                    ThTextDetailCell thTextDetailCell = (ThTextDetailCell) holder.itemView;
                    if (false) {
                        //durov relogin!
                    } else if (position >= 0 && position < rowCount) {
                        thTextDetailCell.setChecked(SharedConfig.activeAccounts.contains(accs.get(position)));
                        thTextDetailCell.setTextAndValue(
                                String.format("%s %s ",
                                        thTextDetailCell.isChecked() ? EMOJIS[1] : EMOJIS[0],
                                        SharedConfig.thAccounts.get(accs.get(position)).get("userPhone").toString())
                                ,
                                String.format(Locale.US, "%s %s(@%s) - %s %s, SDK (%s)"
                                        , SharedConfig.thAccounts.get(accs.get(position)).get("userFName")
                                        , SharedConfig.thAccounts.get(accs.get(position)).get("userLName")
                                        , SharedConfig.thAccounts.get(accs.get(position)).get("userName")
                                        , SharedConfig.thDeviceSpoofing.get(accs.get(position)).get("deviceBrand")
                                        , SharedConfig.thDeviceSpoofing.get(accs.get(position)).get("deviceModel")
                                        , SharedConfig.thDeviceSpoofing.get(accs.get(position)).get("deviceSDK")
                                )
                                , false);

                        thTextDetailCell.setId(position);
                    }
                    break;
                }
                case 8: {
                    ChatActionCell chatMessageCell = (ChatActionCell) holder.itemView;
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (false) {
                return 0;
            } else if (false) {
                return 1;
            } else if (false) {
                return 5;
            } else if (false) {
                return 6;
            } else if (position >= 0 && position < rowCount) {
                return 7;
            } else if (false) {
                return 8;
            } else
                return 1337;
        }
    }

    private void onTextDetailCellImageClicked(View view) {
        View parent = (View) view.getParent();
        if (parent.getTag() != null && ((int) parent.getTag()) == 1337) {
            Bundle args = new Bundle();
            presentFragment(new QrActivity(args));
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, ThTextCheckShadowbanCell.class, TextDetailSettingsCell.class, TextSettingsCell.class, NotificationsCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{NotificationsCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ThTextCheckShadowbanCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ThTextCheckShadowbanCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ThTextCheckShadowbanCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ThTextCheckShadowbanCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ThTextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ThTextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ChatActionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ChatActionCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        return themeDescriptions;
    }

    class ThHistoryMessage {
        ThHistoryMessage(Long date, String message) {
            this.date = date;
            this.message = message;
        }

        Long date;
        String message;

    }
}
