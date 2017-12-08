/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
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
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class DrawerLayoutAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private ArrayList<Item> items = new ArrayList<>(11);

    public DrawerLayoutAdapter(Context context) {
        mContext = context;
        Theme.createDialogsResources(context);
        resetItems();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void notifyDataSetChanged() {
        resetItems();
        super.notifyDataSetChanged();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return holder.getItemViewType() == 3;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new DrawerProfileCell(mContext);
                break;
            case 1:
            default:
                view = new EmptyCell(mContext, AndroidUtilities.dp(8));
                break;
            case 2:
                view = new DividerCell(mContext);
                break;
            case 3:
                view = new DrawerActionCell(mContext);
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0:
                ((DrawerProfileCell) holder.itemView).setUser(MessagesController.getInstance().getUser(UserConfig.getClientUserId()));
                holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
                break;
            case 3:
                items.get(position).bind((DrawerActionCell) holder.itemView);
                break;
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (i == 0) {
            return 0;
        } else if (i == 1) {
            return 1;
        } else if (i == 5) {
            return 2;
        }
        return 3;
    }

    private void resetItems() {
        items.clear();
        if (!UserConfig.isClientActivated()) {
            return;
        }
        items.add(null); // profile
        items.add(null); // padding
        items.add(new Item(2, LocaleController.getString("NewGroup", R.string.NewGroup), R.drawable.menu_newgroup));
        items.add(new Item(3, LocaleController.getString("NewSecretChat", R.string.NewSecretChat), R.drawable.menu_secret));
        items.add(new Item(4, LocaleController.getString("NewChannel", R.string.NewChannel), R.drawable.menu_broadcast));
        items.add(null); // divider
        items.add(new Item(6, LocaleController.getString("Contacts", R.string.Contacts), R.drawable.menu_contacts));
        items.add(new Item(11, LocaleController.getString("SavedMessages", R.string.SavedMessages), R.drawable.menu_saved));
        items.add(new Item(10, LocaleController.getString("Calls", R.string.Calls), R.drawable.menu_calls));
        items.add(new Item(7, LocaleController.getString("InviteFriends", R.string.InviteFriends), R.drawable.menu_invite));
        items.add(new Item(8, LocaleController.getString("Settings", R.string.Settings), R.drawable.menu_settings));
        items.add(new Item(9, LocaleController.getString("TelegramFAQ", R.string.TelegramFAQ), R.drawable.menu_help));
    }

    public int getId(int position) {
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
