/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.DrawerProfileCell;

public class DrawerLayoutAdapter extends BaseAdapter {

    private Context mContext;

    // View Types for Drawer
    public static final int PROFILE_CELL_TYPE = 0;
    public static final int EMPTY_CELL_TYPE = 1;
    public static final int DIVIDER_CELL_TYPE = 2;
    public static final int ACTION_CELL_TYPE = 3;
    private static final int VIEW_TYPE_COUNT = 4;

    // Number of list items for the view when client is activated
    private static final int LIST_COUNT = 10;

    public DrawerLayoutAdapter(Context context) {
        mContext = context;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int i) {
        int type = getItemViewType(i);
        return !(type == PROFILE_CELL_TYPE || type == EMPTY_CELL_TYPE || type == DIVIDER_CELL_TYPE);
    }

    @Override
    public int getCount() {
        return UserConfig.isClientActivated() ? LIST_COUNT : 0;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int row, View view, ViewGroup viewGroup) {
        int type = getItemViewType(row);
        if (type == PROFILE_CELL_TYPE) {
            if (view == null) {
                view = new DrawerProfileCell(mContext);
            }
            ((DrawerProfileCell) view).setUser(MessagesController.getInstance().getUser(UserConfig.getClientUserId()));
        } else if (type == EMPTY_CELL_TYPE) {
            if (view == null) {
                view = new EmptyCell(mContext, AndroidUtilities.dp(8));
            }
        } else if (type == DIVIDER_CELL_TYPE) {
            if (view == null) {
                view = new DividerCell(mContext);
            }
        } else if (type == ACTION_CELL_TYPE) {
            if (view == null) {
                view = new DrawerActionCell(mContext);
            }
            DrawerActionCell actionCell = (DrawerActionCell) view;
            switch (row) {
                case 2:
                    actionCell.setTextAndIcon(LocaleController.getString("NewGroup", R.string.NewGroup), R.drawable.menu_newgroup);
                    break;
                case 3:
                    actionCell.setTextAndIcon(LocaleController.getString("NewSecretChat", R.string.NewSecretChat), R.drawable.menu_secret);
                    break;
                case 4:
                    actionCell.setTextAndIcon(LocaleController.getString("NewChannel", R.string.NewChannel), R.drawable.menu_broadcast);
                    break;
                case 6:
                    actionCell.setTextAndIcon(LocaleController.getString("Contacts", R.string.Contacts), R.drawable.menu_contacts);
                    break;
                case 7:
                    actionCell.setTextAndIcon(LocaleController.getString("InviteFriends", R.string.InviteFriends), R.drawable.menu_invite);
                    break;
                case 8:
                    actionCell.setTextAndIcon(LocaleController.getString("Settings", R.string.Settings), R.drawable.menu_settings);
                    break;
                case 9:
                    actionCell.setTextAndIcon(LocaleController.getString("TelegramFaq", R.string.TelegramFaq), R.drawable.menu_help);
                    break;
                default:
                    break;
            }
        }

        return view;
    }

    @Override
    public int getItemViewType(int i) {
        if (i == 0) {
            return PROFILE_CELL_TYPE;
        } else if (i == 1) {
            return EMPTY_CELL_TYPE;
        } else if (i == 5) {
            return DIVIDER_CELL_TYPE;
        }
        return ACTION_CELL_TYPE;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return !UserConfig.isClientActivated();
    }
}
