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
import java.util.HashMap;

public class MentionsAdapter extends BaseSearchAdapter {

    public interface MentionsAdapterDelegate {
        void needChangePanelVisibility(boolean show);
    }

    private Context mContext;
    private TLRPC.ChatParticipants info;
    private ArrayList<TLRPC.User> searchResultUsernames;
    private ArrayList<String> searchResultHashtags;
    private MentionsAdapterDelegate delegate;
    private int resultStartPosition;
    private int resultLength;
    private String lastText;
    private int lastPosition;
    private ArrayList<MessageObject> messages;
    private boolean needUsernames = true;

    public MentionsAdapter(Context context, MentionsAdapterDelegate delegate) {
        mContext = context;
        this.delegate = delegate;
    }

    public void setChatInfo(TLRPC.ChatParticipants chatParticipants) {
        info = chatParticipants;
        if (lastText != null) {
            searchUsernameOrHashtag(lastText, lastPosition, messages);
        }
    }

    public void setNeedUsernames(boolean value) {
        needUsernames = value;
    }

    @Override
    public void clearRecentHashtags() {
        super.clearRecentHashtags();
        searchResultHashtags.clear();
        notifyDataSetChanged();
        if (delegate != null) {
            delegate.needChangePanelVisibility(false);
        }
    }

    @Override
    protected void setHashtags(ArrayList<HashtagObject> arrayList, HashMap<String, HashtagObject> hashMap) {
        super.setHashtags(arrayList, hashMap);
        if (lastText != null) {
            searchUsernameOrHashtag(lastText, lastPosition, messages);
        }
    }

    public void searchUsernameOrHashtag(String text, int position, ArrayList<MessageObject> messageObjects) {
        if (text == null || text.length() == 0) {
            delegate.needChangePanelVisibility(false);
            lastText = null;
            return;
        }
        int searchPostion = position;
        if (text.length() > 0) {
            searchPostion--;
        }
        lastText = null;
        StringBuilder result = new StringBuilder();
        int foundType = -1;
        boolean hasIllegalUsernameCharacters = false;
        for (int a = searchPostion; a >= 0; a--) {
            if (a >= text.length()) {
                continue;
            }
            char ch = text.charAt(a);
            if (a == 0 || text.charAt(a - 1) == ' ' || text.charAt(a - 1) == '\n') {
                if (needUsernames && ch == '@') {
                    if (hasIllegalUsernameCharacters) {
                        delegate.needChangePanelVisibility(false);
                        return;
                    }
                    if (info == null) {
                        lastText = text;
                        lastPosition = position;
                        messages = messageObjects;
                        delegate.needChangePanelVisibility(false);
                        return;
                    }
                    foundType = 0;
                    resultStartPosition = a;
                    resultLength = result.length() + 1;
                    break;
                } else if (ch == '#') {
                    if (!hashtagsLoadedFromDb) {
                        loadRecentHashtags();
                        lastText = text;
                        lastPosition = position;
                        messages = messageObjects;
                        delegate.needChangePanelVisibility(false);
                        return;
                    }
                    foundType = 1;
                    resultStartPosition = a;
                    resultLength = result.length() + 1;
                    result.insert(0, ch);
                    break;
                }
            }
            if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                hasIllegalUsernameCharacters = true;
            }
            result.insert(0, ch);
        }
        if (foundType == -1) {
            delegate.needChangePanelVisibility(false);
            return;
        }
        if (foundType == 0) {
            final ArrayList<Integer> users = new ArrayList<>();
            for (int a = 0; a < Math.min(100, messageObjects.size()); a++) {
                int from_id = messageObjects.get(a).messageOwner.from_id;
                if (!users.contains(from_id)) {
                    users.add(from_id);
                }
            }
            String usernameString = result.toString().toLowerCase();
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
            searchResultHashtags = null;
            searchResultUsernames = newResult;
            Collections.sort(searchResultUsernames, new Comparator<TLRPC.User>() {
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
        } else {
            ArrayList<String> newResult = new ArrayList<>();
            String hashtagString = result.toString().toLowerCase();
            for (HashtagObject hashtagObject : hashtags) {
                if (hashtagString != null && hashtagObject.hashtag != null && hashtagObject.hashtag.startsWith(hashtagString)) {
                    newResult.add(hashtagObject.hashtag);
                }
            }
            searchResultHashtags = newResult;
            searchResultUsernames = null;
            notifyDataSetChanged();
            delegate.needChangePanelVisibility(!newResult.isEmpty());
        }
    }

    public int getResultStartPosition() {
        return resultStartPosition;
    }

    public int getResultLength() {
        return resultLength;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public int getCount() {
        if (searchResultUsernames != null) {
            return searchResultUsernames.size();
        } else if (searchResultHashtags != null) {
            return searchResultHashtags.size();
        }
        return 0;
    }

    @Override
    public boolean isEmpty() {
        if (searchResultUsernames != null) {
            return searchResultUsernames.isEmpty();
        } else if (searchResultHashtags != null) {
            return searchResultHashtags.isEmpty();
        }
        return true;
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
    public Object getItem(int i) {
        if (searchResultUsernames != null) {
            if (i < 0 || i >= searchResultUsernames.size()) {
                return null;
            }
            return searchResultUsernames.get(i);
        } else if (searchResultHashtags != null) {
            if (i < 0 || i >= searchResultHashtags.size()) {
                return null;
            }
            return searchResultHashtags.get(i);
        }
        return null;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = new MentionCell(mContext);
        }
        if (searchResultUsernames != null) {
            ((MentionCell) view).setUser(searchResultUsernames.get(i));
        } else if (searchResultHashtags != null) {
            ((MentionCell) view).setText(searchResultHashtags.get(i));
        }
        return view;
    }
}
