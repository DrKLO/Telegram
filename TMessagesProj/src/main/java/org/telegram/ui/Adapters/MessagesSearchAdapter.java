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

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashSet;

public class MessagesSearchAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private HashSet<Integer> messageIds = new HashSet<>();
    private ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    public int loadedCount;

    private int currentAccount = UserConfig.selectedAccount;
    private final Theme.ResourcesProvider resourcesProvider;

    public MessagesSearchAdapter(Context context, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        mContext = context;
    }

    @Override
    public void notifyDataSetChanged() {
        searchResultMessages.clear();
        messageIds.clear();
        ArrayList<MessageObject> searchResults = MediaDataController.getInstance(currentAccount).getFoundMessageObjects();
        for (int i = 0; i < searchResults.size(); ++i) {
            MessageObject m = searchResults.get(i);
            if ((!m.hasValidGroupId() || m.isPrimaryGroupMessage) && !messageIds.contains(m.getId())) {
                searchResultMessages.add(m);
                messageIds.add(m.getId());
            }
        }
        loadedCount = searchResultMessages.size();
        super.notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return searchResultMessages.size();
    }

    public Object getItem(int i) {
        if (i < 0 || i >= searchResultMessages.size()) {
            return null;
        }
        return searchResultMessages.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return holder.getItemViewType() == 0;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        switch (viewType) {
            case 0:
                view = new DialogCell(null, mContext, false, true, currentAccount, resourcesProvider);
                break;
            case 1:
                view = new LoadingCell(mContext);
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == 0) {
            DialogCell cell = (DialogCell) holder.itemView;
            cell.useSeparator = true;
            cell.isSavedDialog = true;
            MessageObject messageObject = (MessageObject) getItem(position);
            int date;
            boolean useMe = false;
            long did;
            if (messageObject.getDialogId() == UserConfig.getInstance(currentAccount).getClientUserId()) {
                did = messageObject.getSavedDialogId();
                if (messageObject.messageOwner.fwd_from != null && (messageObject.messageOwner.fwd_from.date != 0 || messageObject.messageOwner.fwd_from.saved_date != 0)) {
                    date = messageObject.messageOwner.fwd_from.date;
                    if (date == 0) {
                        date = messageObject.messageOwner.fwd_from.saved_date;
                    }
                } else {
                    date = messageObject.messageOwner.date;
                }
            } else {
                did = messageObject.getDialogId();
                date = messageObject.messageOwner.date;
            }
            cell.setDialog(did, messageObject, date, useMe, false);
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (i < searchResultMessages.size()) {
            return 0;
        }
        return 1;
    }
}
