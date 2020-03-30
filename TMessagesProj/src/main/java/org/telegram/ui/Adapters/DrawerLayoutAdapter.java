/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.DrawerAddCell;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SideMenultItemAnimator;

import java.util.ArrayList;
import java.util.Collections;

import androidx.recyclerview.widget.RecyclerView;

public class DrawerLayoutAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private ArrayList<Item> items = new ArrayList<>(11);
    private ArrayList<Integer> accountNumbers = new ArrayList<>();
    private boolean accountsShown;
    private DrawerProfileCell profileCell;
    private SideMenultItemAnimator itemAnimator;

    public DrawerLayoutAdapter(Context context, SideMenultItemAnimator animator) {
        mContext = context;
        itemAnimator = animator;
        accountsShown = UserConfig.getActivatedAccountsCount() > 1 && MessagesController.getGlobalMainSettings().getBoolean("accountsShown", true);
        Theme.createDialogsResources(context);
        resetItems();
    }

    private int getAccountRowsCount() {
        int count = accountNumbers.size() + 1;
        if (accountNumbers.size() < UserConfig.MAX_ACCOUNT_COUNT) {
            count++;
        }
        return count;
    }

    @Override
    public int getItemCount() {
        int count = items.size() + 2;
        if (accountsShown) {
            count += getAccountRowsCount();
        }
        return count;
    }

    public void setAccountsShown(boolean value, boolean animated) {
        if (accountsShown == value || itemAnimator.isRunning()) {
            return;
        }
        accountsShown = value;
        if (profileCell != null) {
            profileCell.setAccountsShown(accountsShown, animated);
        }
        MessagesController.getGlobalMainSettings().edit().putBoolean("accountsShown", accountsShown).commit();
        if (animated) {
            itemAnimator.setShouldClipChildren(false);
            if (accountsShown) {
                notifyItemRangeInserted(2, getAccountRowsCount());
            } else {
                notifyItemRangeRemoved(2, getAccountRowsCount());
            }
        } else {
            notifyDataSetChanged();
        }
    }

    public boolean isAccountsShown() {
        return accountsShown;
    }

    @Override
    public void notifyDataSetChanged() {
        resetItems();
        super.notifyDataSetChanged();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int itemType = holder.getItemViewType();
        return itemType == 3 || itemType == 4 || itemType == 5 || itemType == 6;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = profileCell = new DrawerProfileCell(mContext);
                break;
            case 2:
                view = new DividerCell(mContext);
                break;
            case 3:
                view = new DrawerActionCell(mContext);
                break;
            case 4:
                view = new DrawerUserCell(mContext);
                break;
            case 5:
                view = new DrawerAddCell(mContext);
                break;
            case 1:
            default:
                view = new EmptyCell(mContext, AndroidUtilities.dp(8));
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0: {
                DrawerProfileCell profileCell = (DrawerProfileCell) holder.itemView;
                profileCell.setUser(MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()), accountsShown);
                break;
            }
            case 3: {
                DrawerActionCell drawerActionCell = (DrawerActionCell) holder.itemView;
                position -= 2;
                if (accountsShown) {
                    position -= getAccountRowsCount();
                }
                items.get(position).bind(drawerActionCell);
                drawerActionCell.setPadding(0, 0, 0, 0);
                break;
            }
            case 4: {
                DrawerUserCell drawerUserCell = (DrawerUserCell) holder.itemView;
                drawerUserCell.setAccount(accountNumbers.get(position - 2));
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (i == 0) {
            return 0;
        } else if (i == 1) {
            return 1;
        }
        i -= 2;
        if (accountsShown) {
            if (i < accountNumbers.size()) {
                return 4;
            } else {
                if (accountNumbers.size() < UserConfig.MAX_ACCOUNT_COUNT) {
                    if (i == accountNumbers.size()){
                        return 5;
                    } else if (i == accountNumbers.size() + 1) {
                        return 2;
                    }
                } else {
                    if (i == accountNumbers.size()) {
                        return 2;
                    }
                }
            }
            i -= getAccountRowsCount();
        }
        if (items.get(i) == null) {
            return 2;
        }
        return 3;
    }

    private void resetItems() {
        accountNumbers.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accountNumbers.add(a);
            }
        }
        Collections.sort(accountNumbers, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });

        items.clear();
        if (!UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) {
            return;
        }
        int eventType = Theme.getEventType();
        int newGroupIcon;
        int newSecretIcon;
        int newChannelIcon;
        int contactsIcon;
        int callsIcon;
        int savedIcon;
        int settingsIcon;
        int inviteIcon;
        int helpIcon;
        if (eventType == 0) {
            newGroupIcon = R.drawable.menu_groups_ny;
            newSecretIcon = R.drawable.menu_secret_ny;
            newChannelIcon = R.drawable.menu_channel_ny;
            contactsIcon = R.drawable.menu_contacts_ny;
            callsIcon = R.drawable.menu_calls_ny;
            savedIcon = R.drawable.menu_bookmarks_ny;
            settingsIcon = R.drawable.menu_settings_ny;
            inviteIcon = R.drawable.menu_invite_ny;
            helpIcon = R.drawable.menu_help_ny;
        } else if (eventType == 1) {
            newGroupIcon = R.drawable.menu_groups_14;
            newSecretIcon = R.drawable.menu_secret_14;
            newChannelIcon = R.drawable.menu_broadcast_14;
            contactsIcon = R.drawable.menu_contacts_14;
            callsIcon = R.drawable.menu_calls_14;
            savedIcon = R.drawable.menu_bookmarks_14;
            settingsIcon = R.drawable.menu_settings_14;
            inviteIcon = R.drawable.menu_secret_ny;
            helpIcon = R.drawable.menu_help;
        } else {
            newGroupIcon = R.drawable.menu_groups;
            newSecretIcon = R.drawable.menu_secret;
            newChannelIcon = R.drawable.menu_broadcast;
            contactsIcon = R.drawable.menu_contacts;
            callsIcon = R.drawable.menu_calls;
            savedIcon = R.drawable.menu_saved;
            settingsIcon = R.drawable.menu_settings;
            inviteIcon = R.drawable.menu_invite;
            helpIcon = R.drawable.menu_help;
        }
        items.add(new Item(2, LocaleController.getString("NewGroup", R.string.NewGroup), newGroupIcon));
        items.add(new Item(3, LocaleController.getString("NewSecretChat", R.string.NewSecretChat), newSecretIcon));
        items.add(new Item(4, LocaleController.getString("NewChannel", R.string.NewChannel), newChannelIcon));
        items.add(new Item(6, LocaleController.getString("Contacts", R.string.Contacts), contactsIcon));
        items.add(new Item(10, LocaleController.getString("Calls", R.string.Calls), callsIcon));
        items.add(new Item(11, LocaleController.getString("SavedMessages", R.string.SavedMessages), savedIcon));
        items.add(new Item(8, LocaleController.getString("Settings", R.string.Settings), settingsIcon));
        items.add(null); // divider
        items.add(new Item(7, LocaleController.getString("InviteFriends", R.string.InviteFriends), inviteIcon));
        items.add(new Item(9, LocaleController.getString("TelegramFAQ", R.string.TelegramFAQ), helpIcon));
    }

    public int getId(int position) {
        position -= 2;
        if (accountsShown) {
            position -= getAccountRowsCount();
        }
        if (position < 0 || position >= items.size()) {
            return -1;
        }
        Item item = items.get(position);
        return item != null ? item.id : -1;
    }

    private class Item {
        public int icon;
        public String text;
        public int id;

        public Item(int id, String text, int icon) {
            this.icon = icon;
            this.id = id;
            this.text = text;
        }

        public void bind(DrawerActionCell actionCell) {
            actionCell.setTextAndIcon(text, icon);
        }
    }
}
