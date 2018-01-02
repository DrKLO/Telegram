/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class ChannelPermissionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private RecyclerListView listView;

    private int chatId;

    private TLRPC.TL_channelAdminRights adminRights;
    private TLRPC.ChatFull info;
    private boolean historyHidden;

    private HeaderCell headerCell2;
    private LinearLayout linearLayout;
    private RadioButtonCell radioButtonCell3;
    private RadioButtonCell radioButtonCell4;

    private int rowCount;
    private int permissionsHeaderRow;
    private int sendMediaRow;
    private int sendStickersRow;
    private int embedLinksRow;
    private int addUsersRow;
    private int changeInfoRow;
    private int rightsShadowRow;
    private int forwardRow;
    private int forwardShadowRow;

    private final static int done_button = 1;

    public ChannelPermissionsActivity(int channelId) {
        super();
        chatId = channelId;
        adminRights = new TLRPC.TL_channelAdminRights();
        rowCount = 0;
        permissionsHeaderRow = rowCount++;
        sendMediaRow = rowCount++;
        sendStickersRow = rowCount++;
        embedLinksRow = rowCount++;
        addUsersRow = rowCount++;
        changeInfoRow = rowCount++;
        rightsShadowRow = rowCount++;
        TLRPC.Chat chat = MessagesController.getInstance().getChat(chatId);
        if (chat != null && TextUtils.isEmpty(chat.username)) {
            forwardRow = rowCount++;
            forwardShadowRow = rowCount++;
        } else {
            forwardRow = -1;
            forwardShadowRow = -1;
        }
    }

    /*
        <string name="ChatPermissions">Permissions</string>
    <string name="ChatPermissionsTitle">Group Permissions</string>
    <string name="ChatPermissionsHeader">What can group members do?</string>
    <string name="ChatPermissionsInviteMembers">Invite New Members</string>
    <string name="ChatPermissionsEditInfo">Edit Group Info &amp; Photo</string>
         */

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.chatInfoDidLoaded);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.chatInfoDidLoaded);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        //actionBar.setTitle(LocaleController.getString("ChatPermissionsTitle", R.string.ChatPermissionsTitle));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (headerCell2 != null && headerCell2.getVisibility() == View.VISIBLE && info != null && info.hidden_prehistory != historyHidden) {
                        info.hidden_prehistory = historyHidden;
                        MessagesController.getInstance().toogleChannelInvitesHistory(chatId, historyHidden);
                    }
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(linearLayoutManager);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (view instanceof TextCheckCell2) {
                    TextCheckCell2 checkCell = (TextCheckCell2) view;
                    if (!checkCell.isEnabled()) {
                        return;
                    }
                    checkCell.setChecked(!checkCell.isChecked());
                    if (position == changeInfoRow) {
                        adminRights.change_info = !adminRights.change_info;
                    } else if (position == addUsersRow) {
                        adminRights.invite_users = !adminRights.invite_users;
                    } else if (position == sendMediaRow) {
                        adminRights.ban_users = !adminRights.ban_users;
                    } else if (position == sendStickersRow) {
                        adminRights.add_admins = !adminRights.add_admins;
                    } else if (position == embedLinksRow) {
                        adminRights.pin_messages = !adminRights.pin_messages;
                    }
                }
            }
        });

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        headerCell2 = new HeaderCell(context);
        headerCell2.setText(LocaleController.getString("ChatHistory", R.string.ChatHistory));
        headerCell2.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(headerCell2);

        radioButtonCell3 = new RadioButtonCell(context);
        radioButtonCell3.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        radioButtonCell3.setTextAndValue(LocaleController.getString("ChatHistoryVisible", R.string.ChatHistoryVisible), LocaleController.getString("ChatHistoryVisibleInfo", R.string.ChatHistoryVisibleInfo), !historyHidden);
        linearLayout.addView(radioButtonCell3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        radioButtonCell3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioButtonCell3.setChecked(true, true);
                radioButtonCell4.setChecked(false, true);
                historyHidden = false;
            }
        });

        radioButtonCell4 = new RadioButtonCell(context);
        radioButtonCell4.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        radioButtonCell4.setTextAndValue(LocaleController.getString("ChatHistoryHidden", R.string.ChatHistoryHidden), LocaleController.getString("ChatHistoryHiddenInfo", R.string.ChatHistoryHiddenInfo), historyHidden);
        linearLayout.addView(radioButtonCell4, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        radioButtonCell4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                radioButtonCell3.setChecked(false, true);
                radioButtonCell4.setChecked(true, true);
                historyHidden = true;
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                if (info == null) {
                    historyHidden = chatFull.hidden_prehistory;
                    if (radioButtonCell3 != null) {
                        radioButtonCell3.setChecked(!historyHidden, false);
                        radioButtonCell4.setChecked(historyHidden, false);
                    }
                }
                info = chatFull;
            }
        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        if (info == null && chatFull != null) {
            historyHidden = chatFull.hidden_prehistory;
        }
        info = chatFull;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 1;
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
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 3:
                default:
                    view = linearLayout;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                /*case 0:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText(LocaleController.getString("ChatPermissionsHeader", R.string.ChatPermissionsHeader));
                    break;
                case 1:
                    TextCheckCell2 checkCell = (TextCheckCell2) holder.itemView;
                    if (position == changeInfoRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("ChatPermissionsEditInfo", R.string.ChatPermissionsEditInfo), adminRights.change_info, true);
                    } else if (position == addUsersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("ChatPermissionsInviteMembers", R.string.ChatPermissionsInviteMembers), adminRights.invite_users, true);
                    } else if (position == sendMediaRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendMedia", R.string.UserRestrictionsSendMedia), adminRights.ban_users, true);
                    } else if (position == sendStickersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendStickers", R.string.UserRestrictionsSendStickers), adminRights.add_admins, true);
                    } else if (position == embedLinksRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsEmbedLinks", R.string.UserRestrictionsEmbedLinks), adminRights.pin_messages, false);
                    }
                    //if (position == sendMediaRow || position == sendStickersRow || position == embedLinksRow) {
                    //    checkCell.setEnabled(!bannedRights.send_messages && !bannedRights.view_messages);
                    //} else if (position == sendMessagesRow) {
                    //    checkCell.setEnabled(!bannedRights.view_messages);
                    //}
                    break;*/
                case 2:
                    ShadowSectionCell shadowCell = (ShadowSectionCell) holder.itemView;
                    if (position == rightsShadowRow) {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, forwardShadowRow == -1 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == rightsShadowRow || position == forwardShadowRow) {
                return 2;
            } else if (position == changeInfoRow || position == addUsersRow || position == sendMediaRow || position == sendStickersRow || position == embedLinksRow) {
                return 1;
            } else if (position == forwardRow) {
                return 3;
            } else if (position == permissionsHeaderRow) {
                return 0;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckCell2.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumb),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumbChecked),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(linearLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(radioButtonCell3, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(radioButtonCell3, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground),
                new ThemeDescription(radioButtonCell3, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked),
                new ThemeDescription(radioButtonCell3, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(radioButtonCell3, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(radioButtonCell4, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(radioButtonCell4, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground),
                new ThemeDescription(radioButtonCell4, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioButtonCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked),
                new ThemeDescription(radioButtonCell4, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(radioButtonCell4, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{RadioButtonCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
        };
    }
}
