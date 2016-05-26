/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.LoadingCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class DialogsAdapter extends RecyclerView.Adapter {

    private Context mContext;
    private int dialogsType;
    private long openedDialogId;
    private int currentCount;

    private class Holder extends RecyclerView.ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }
    }

    public DialogsAdapter(Context context, int type) {
        mContext = context;
        dialogsType = type;
    }

    public void setOpenedDialogId(long id) {
        openedDialogId = id;
    }

    public boolean isDataSetChanged() {
        int current = currentCount;
        return current != getItemCount() || current == 1;
    }

    private ArrayList<TLRPC.Dialog> getDialogsArray() {
        SharedPreferences plusPreferences = ApplicationLoader.applicationContext.getSharedPreferences("plusconfig", Activity.MODE_PRIVATE);
        if (dialogsType == 0) {
            boolean hideTabs = plusPreferences.getBoolean("hideTabs", false);
            int sort = plusPreferences.getInt("sortAll", 0);
            if(sort == 0 || hideTabs){
                sortDefault(MessagesController.getInstance().dialogs);
            }else{
                sortUnread(MessagesController.getInstance().dialogs);
            }
            return MessagesController.getInstance().dialogs;
        } else if (dialogsType == 1) {
            return MessagesController.getInstance().dialogsServerOnly;
        } else if (dialogsType == 2) {
            return MessagesController.getInstance().dialogsGroupsOnly;
        }
        //plus
        else if (dialogsType == 3) {
            int sort = plusPreferences.getInt("sortUsers", 0);
            if(sort == 0){
                sortUsersDefault();
            }else{
                sortUsersByStatus();
            }
            return MessagesController.getInstance().dialogsUsers;
        } else if (dialogsType == 4) {
            int sort = plusPreferences.getInt("sortGroups", 0);
            if(sort == 0){
                sortDefault(MessagesController.getInstance().dialogsGroups);
            }else{
                sortUnread(MessagesController.getInstance().dialogsGroups);
            }
            return MessagesController.getInstance().dialogsGroups;
        } else if (dialogsType == 5) {
            int sort = plusPreferences.getInt("sortChannels", 0);
            if(sort == 0){
                sortDefault(MessagesController.getInstance().dialogsChannels);
            }else{
                sortUnread(MessagesController.getInstance().dialogsChannels);
            }
            return MessagesController.getInstance().dialogsChannels;
        } else if (dialogsType == 6) {
            int sort = plusPreferences.getInt("sortBots", 0);
            if(sort == 0){
                sortDefault(MessagesController.getInstance().dialogsBots);
            }else{
                sortUnread(MessagesController.getInstance().dialogsBots);
            }
            return MessagesController.getInstance().dialogsBots;
        } else if (dialogsType == 7) {
            int sort = plusPreferences.getInt("sortSGroups", 0);
            if(sort == 0){
                sortDefault(MessagesController.getInstance().dialogsMegaGroups);
            }else{
                sortUnread(MessagesController.getInstance().dialogsMegaGroups);
            }
            return MessagesController.getInstance().dialogsMegaGroups;
        } else if (dialogsType == 8) {
            int sort = plusPreferences.getInt("sortFavs", 0);
            if(sort == 0){
                sortDefault(MessagesController.getInstance().dialogsFavs);
            }else{
                sortUnread(MessagesController.getInstance().dialogsFavs);
            }
            return MessagesController.getInstance().dialogsFavs;
        } else if (dialogsType == 9) {
            int sort = plusPreferences.getInt("sortGroups", 0);
            if(sort == 0){
                sortDefault(MessagesController.getInstance().dialogsGroupsAll);
            }else{
                sortUnread(MessagesController.getInstance().dialogsGroupsAll);
            }
            return MessagesController.getInstance().dialogsGroupsAll;
        }
        //
        return null;
    }
    //plus
    public void sort(){
        getDialogsArray();
        notifyDataSetChanged();
    }

    private void sortUsersByStatus(){
        Collections.sort(MessagesController.getInstance().dialogsUsers, new Comparator<TLRPC.Dialog>() {
            @Override
            public int compare(TLRPC.Dialog tl_dialog, TLRPC.Dialog tl_dialog2) {
                TLRPC.User user1 = MessagesController.getInstance().getUser((int) tl_dialog2.id);
                TLRPC.User user2 = MessagesController.getInstance().getUser((int) tl_dialog.id);
                int status1 = 0;
                int status2 = 0;
                if (user1 != null && user1.status != null) {
                    if (user1.id == UserConfig.getClientUserId()) {
                        status1 = ConnectionsManager.getInstance().getCurrentTime() + 50000;
                    } else {
                        status1 = user1.status.expires;
                    }
                }
                if (user2 != null && user2.status != null) {
                    if (user2.id == UserConfig.getClientUserId()) {
                        status2 = ConnectionsManager.getInstance().getCurrentTime() + 50000;
                    } else {
                        status2 = user2.status.expires;
                    }
                }
                if (status1 > 0 && status2 > 0) {
                    if (status1 > status2) {
                        return 1;
                    } else if (status1 < status2) {
                        return -1;
                    }
                    return 0;
                } else if (status1 < 0 && status2 < 0) {
                    if (status1 > status2) {
                        return 1;
                    } else if (status1 < status2) {
                        return -1;
                    }
                    return 0;
                } else if (status1 < 0 && status2 > 0 || status1 == 0 && status2 != 0) {
                    return -1;
                } else if (status2 < 0 && status1 > 0 || status2 == 0 && status1 != 0) {
                    return 1;
                }
                return 0;
            }
        });
    }

    private void sortDefault(ArrayList<TLRPC.Dialog> dialogs){
        Collections.sort(dialogs, new Comparator<TLRPC.Dialog>() {
            @Override
            public int compare(TLRPC.Dialog dialog, TLRPC.Dialog dialog2) {
                if (dialog.last_message_date == dialog2.last_message_date) {
                    return 0;
                } else if (dialog.last_message_date < dialog2.last_message_date) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
    }

    private void sortUnread(ArrayList<TLRPC.Dialog> dialogs){
        Collections.sort(dialogs, new Comparator<TLRPC.Dialog>() {
            @Override
            public int compare(TLRPC.Dialog dialog, TLRPC.Dialog dialog2) {
                if (dialog.unread_count == dialog2.unread_count) {
                    return 0;
                } else if (dialog.unread_count < dialog2.unread_count) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
    }

    private void sortUsersDefault(){
        Collections.sort(MessagesController.getInstance().dialogsUsers, new Comparator<TLRPC.Dialog>() {
            @Override
            public int compare(TLRPC.Dialog dialog, TLRPC.Dialog dialog2) {
                if (dialog.last_message_date == dialog2.last_message_date) {
                    return 0;
                } else if (dialog.last_message_date < dialog2.last_message_date) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
    }

    //
    @Override
    public int getItemCount() {
        int count = getDialogsArray().size();
        if (count == 0 && MessagesController.getInstance().loadingDialogs) {
            return 0;
        }
        if (!MessagesController.getInstance().dialogsEndReached) {
            count++;
        }
        currentCount = count;
        return count;
    }

    public TLRPC.Dialog getItem(int i) {
        ArrayList<TLRPC.Dialog> arrayList = getDialogsArray();
        if (i < 0 || i >= arrayList.size()) {
                return null;
            }
        return arrayList.get(i);
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        if (holder.itemView instanceof DialogCell) {
            ((DialogCell) holder.itemView).checkCurrentDialogIndex();
        }
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = null;
        if (viewType == 0) {
            view = new DialogCell(mContext);
        } else if (viewType == 1) {
                view = new LoadingCell(mContext);
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        if(dialogsType > 2 && viewType == 1)view.setVisibility(View.GONE);
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        int mainColor = themePrefs.getInt("chatsRowColor", 0xffffffff);
        int value = themePrefs.getInt("chatsRowGradient", 0);
        boolean b = true;//themePrefs.getBoolean("chatsRowGradientListCheck", false);
        if(value > 0 && b) {
            GradientDrawable.Orientation go;
            switch(value) {
                case 2:
                    go = GradientDrawable.Orientation.LEFT_RIGHT;
                    break;
                case 3:
                    go = GradientDrawable.Orientation.TL_BR;
                    break;
                case 4:
                    go = GradientDrawable.Orientation.BL_TR;
                    break;
                default:
                    go = GradientDrawable.Orientation.TOP_BOTTOM;
            }

            int gradColor = themePrefs.getInt("chatsRowGradientColor", 0xffffffff);
            int[] colors = new int[]{mainColor, gradColor};
            GradientDrawable gd = new GradientDrawable(go, colors);
            viewGroup.setBackgroundDrawable(gd);
        }else{
            viewGroup.setBackgroundColor(mainColor);
        }
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        if (viewHolder.getItemViewType() == 0) {
            DialogCell cell = (DialogCell) viewHolder.itemView;
            cell.useSeparator = (i != getItemCount() - 1);
            TLRPC.Dialog dialog = getItem(i);
            if (dialogsType == 0) {
                if (AndroidUtilities.isTablet()) {
                    cell.setDialogSelected(dialog.id == openedDialogId);
                }
            }
            //Plus
            SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
            int mainColor = themePrefs.getInt("chatsRowColor", 0xffffffff);
            //cell.setBackgroundColor(mainColor);
            int value = themePrefs.getInt("chatsRowGradient", 0);
            boolean b = true;//themePrefs.getBoolean("chatsRowGradientListCheck", false);
            if(value > 0 && !b) {
                GradientDrawable.Orientation go;
                switch(value) {
                    case 2:
                        go = GradientDrawable.Orientation.LEFT_RIGHT;
                        break;
                    case 3:
                        go = GradientDrawable.Orientation.TL_BR;
                        break;
                    case 4:
                        go = GradientDrawable.Orientation.BL_TR;
                        break;
                    default:
                        go = GradientDrawable.Orientation.TOP_BOTTOM;
                }

                int gradColor = themePrefs.getInt("chatsRowGradientColor", 0xffffffff);
                int[] colors = new int[]{mainColor, gradColor};
                GradientDrawable gd = new GradientDrawable(go, colors);
                cell.setBackgroundDrawable(gd);
            }
            //
            cell.setDialog(dialog, i, dialogsType);
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (i == getDialogsArray().size()) {
            return 1;
        }
        return 0;
    }
}
