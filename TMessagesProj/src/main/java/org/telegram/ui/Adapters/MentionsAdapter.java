/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.Cells.MentionCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class MentionsAdapter extends BaseFragmentAdapter {

    public interface MentionsAdapterDelegate {
        void needChangePanelVisibility(boolean show);
    }

    private Context mContext;
    private TLRPC.ChatParticipants info;
    private ArrayList<TLRPC.User> searchResult = new ArrayList<>();
    private MentionsAdapterDelegate delegate;
    private int usernameStartPosition;
    private int usernameLength;
    private String lastText;
    private int lastPosition;
    private ArrayList<MessageObject> messages;

    public MentionsAdapter(Context context, MentionsAdapterDelegate delegate) {
        mContext = context;
        this.delegate = delegate;
    }

    public void setChatInfo(TLRPC.ChatParticipants chatParticipants) {
        info = chatParticipants;
        if (lastText != null) {
            searchUsername(lastText, lastPosition, messages);
        }
    }

    public void searchUsername(String text, int position, ArrayList<MessageObject> messageObjects) {
        if (text == null || text.length() == 0 || position < text.length()) {
            delegate.needChangePanelVisibility(false);
            lastText = null;
            return;
        }
        if (info == null) {
            lastText = text;
            lastPosition = position;
            messages = messageObjects;
            delegate.needChangePanelVisibility(false);
            return;
        }
        lastText = null;
        StringBuilder username = new StringBuilder();
        boolean found = false;
        for (int a = position; a >= 0; a--) {
            if (a >= text.length()) {
                continue;
            }
            char ch = text.charAt(a);
            if (ch == '@' && (a == 0 || text.charAt(a - 1) == ' ' || text.charAt(a - 1) == '\n')) {
                found = true;
                usernameStartPosition = a;
                usernameLength = username.length() + 1;
                break;
            }
            if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                delegate.needChangePanelVisibility(false);
                return;
            }
            username.insert(0, ch);
        }
        if (!found) {
            delegate.needChangePanelVisibility(false);
            return;
        }
        final ArrayList<Integer> users = new ArrayList<>();
        for (int a = 0; a < Math.min(100, messageObjects.size()); a++) {
            int from_id = messageObjects.get(a).messageOwner.from_id;
            if (!users.contains(from_id)) {
                users.add(from_id);
            }
        }
        String usernameString = username.toString().toLowerCase();
        ArrayList<TLRPC.User> newResult = new ArrayList<>();
        for (TLRPC.TL_chatParticipant chatParticipant : info.participants) {
            TLRPC.User user = MessagesController.getInstance().getUser(chatParticipant.user_id);
            if (user == null || user instanceof TLRPC.TL_userSelf) {
                continue;
            }
            if (user.username != null && user.username.length() > 0 && (usernameString.length() > 0 && user.username.toLowerCase().startsWith(usernameString) || usernameString.length() == 0)) {
                newResult.add(user);
            }
        }
        searchResult = newResult;
        Collections.sort(searchResult, new Comparator<TLRPC.User>() {
            @Override
            public int compare(TLRPC.User lhs, TLRPC.User rhs) {
                int lhsNum = users.indexOf(lhs.id);
                int rhsNum = users.indexOf(rhs.id);
                if (lhsNum != -1 && rhsNum != -1) {
                    return lhsNum < rhsNum ? -1 : (lhsNum == rhsNum ? 0 : 1);
                } else if (lhsNum != -1 && rhsNum == -1) {
                    return -1;
                } else if (lhsNum == -1 && rhsNum != -1) {
                    return 1;
                }
                return 0;
            }
        });
        notifyDataSetChanged();
        delegate.needChangePanelVisibility(!newResult.isEmpty());
    }

    public int getUsernameStartPosition() {
        return usernameStartPosition;
    }

    public int getUsernameLength() {
        return usernameLength;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public int getCount() {
        return searchResult.size();
    }

    @Override
    public boolean isEmpty() {
        return searchResult.isEmpty();
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public TLRPC.User getItem(int i) {
        if (i < 0 || i >= searchResult.size()) {
            return null;
        }
        return searchResult.get(i);
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = new MentionCell(mContext);
        }
        ((MentionCell) view).setUser(searchResult.get(i));
        return view;
    }
}
