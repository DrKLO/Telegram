/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.LoadingCell;

public class DialogsAdapter extends BaseFragmentAdapter {

    private Context mContext;
    private boolean serverOnly;
    private long openedDialogId;

    public DialogsAdapter(Context context, boolean onlyFromServer) {
        mContext = context;
        serverOnly = onlyFromServer;
    }

    public void setOpenedDialogId(long id) {
        openedDialogId = id;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int i) {
        return true;
    }

    @Override
    public int getCount() {
        int count;
        if (serverOnly) {
            count = MessagesController.getInstance().dialogsServerOnly.size();
        } else {
            count = MessagesController.getInstance().dialogs.size();
        }
        if (count == 0 && MessagesController.getInstance().loadingDialogs) {
            return 0;
        }
        if (!MessagesController.getInstance().dialogsEndReached) {
            count++;
        }
        return count;
    }

    @Override
    public TLRPC.TL_dialog getItem(int i) {
        if (serverOnly) {
            if (i < 0 || i >= MessagesController.getInstance().dialogsServerOnly.size()) {
                return null;
            }
            return MessagesController.getInstance().dialogsServerOnly.get(i);
        } else {
            if (i < 0 || i >= MessagesController.getInstance().dialogs.size()) {
                return null;
            }
            return MessagesController.getInstance().dialogs.get(i);
        }
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
    public View getView(int i, View view, ViewGroup viewGroup) {
        int type = getItemViewType(i);
        if (type == 1) {
            if (view == null) {
                view = new LoadingCell(mContext);
            }
        } else if (type == 0) {
            if (view == null) {
                view = new DialogCell(mContext);
            }
            ((DialogCell) view).useSeparator = (i != getCount() - 1);
            TLRPC.TL_dialog dialog = null;
            if (serverOnly) {
                dialog = MessagesController.getInstance().dialogsServerOnly.get(i);
            } else {
                dialog = MessagesController.getInstance().dialogs.get(i);
                if (AndroidUtilities.isTablet()) {
                    if (dialog.id == openedDialogId) {
                        view.setBackgroundColor(0x0f000000);
                    } else {
                        view.setBackgroundColor(0);
                    }
                }
            }
            MessageObject message = MessagesController.getInstance().dialogMessage.get(dialog.top_message);
            ((DialogCell) view).setDialog(dialog.id, message, true, dialog.last_message_date, dialog.unread_count);
        }

        return view;
    }

    @Override
    public int getItemViewType(int i) {
        if (serverOnly && i == MessagesController.getInstance().dialogsServerOnly.size() || !serverOnly && i == MessagesController.getInstance().dialogs.size()) {
            return 1;
        }
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public boolean isEmpty() {
        if (MessagesController.getInstance().loadingDialogs && MessagesController.getInstance().dialogs.isEmpty()) {
            return true;
        }
        int count;
        if (serverOnly) {
            count = MessagesController.getInstance().dialogsServerOnly.size();
        } else {
            count = MessagesController.getInstance().dialogs.size();
        }
        if (count == 0 && MessagesController.getInstance().loadingDialogs) {
            return true;
        }
        if (!MessagesController.getInstance().dialogsEndReached) {
            count++;
        }
        return count == 0;
    }
}
