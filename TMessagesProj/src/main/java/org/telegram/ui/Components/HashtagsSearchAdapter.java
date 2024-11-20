package org.telegram.ui.Components;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MessagesSearchAdapter;
import org.telegram.ui.Stories.StoriesController;

import java.util.ArrayList;

public class HashtagsSearchAdapter extends UniversalAdapter {

    private final int currentAccount;
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    public boolean hasList;
    public StoriesController.SearchStoriesList list;

    public HashtagsSearchAdapter(RecyclerListView listView, Context context, int currentAccount, int folderId, Theme.ResourcesProvider resourcesProvider) {
        super(listView, context, currentAccount, 0, null, resourcesProvider);
        super.fillItems = this::fillItems;
        this.currentAccount = currentAccount;
    }

    private boolean hadStories;
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean hasStories = hasList && list != null && list.getLoadedCount() > 0;
        if (hasStories) {
            items.add(MessagesSearchAdapter.StoriesView.Factory.asStoriesList(list));
        }
        hadStories = hasStories;
        for (int i = 0; i < messages.size(); ++i) {
            items.add(UItem.asSearchMessage(1 + i, messages.get(i)));
        }
        if (loading || !endReached) {
            items.add(UItem.asFlicker(-2, FlickerLoadingView.DIALOG_TYPE));
            items.add(UItem.asFlicker(-3, FlickerLoadingView.DIALOG_TYPE));
            items.add(UItem.asFlicker(-4, FlickerLoadingView.DIALOG_TYPE));
        }
        if (!hadStories && hasStories) {
            AndroidUtilities.runOnUIThread(() -> {
                scrollToTop(true);
            });
        }
    }

    protected boolean loading;
    private int searchId = 0;
    private int reqId = -1;
    private boolean endReached;
    private int totalCount;
    private String lastQuery;
    private String hashtagQuery;
    private int lastRate;
    private Runnable searchRunnable;

    public void setInitialData(String hashtag, ArrayList<MessageObject> messages, int messagesLastRate, int totalCount) {
        if (TextUtils.equals(hashtag, hashtagQuery)) return;
        cancel();
        this.messages.clear();
        this.messages.addAll(messages);
        this.totalCount = totalCount;
        endReached = totalCount > messages.size();
        this.lastRate = messagesLastRate;
        hashtagQuery = hashtag;
        update(true);
    }

    private final boolean[] cashtag = new boolean[1];

    public void search(String query) {
        lastQuery = query;
        final String hashtag = getHashtag(query, cashtag);
        if (!TextUtils.equals(this.hashtagQuery, hashtag)) {
            messages.clear();
            endReached = false;
            totalCount = 0;
            cancel();
        } else if (loading) {
            return;
        }
        final int id = ++searchId;
        if (hashtag == null) return;
        loading = true;
        update(true);
        AndroidUtilities.runOnUIThread(searchRunnable = () -> {
            if (id != searchId) {
                return;
            }
            final String finalQuery = (cashtag[0] ? "$" : "#") + hashtagQuery;
            if (list == null || !TextUtils.equals(list.query, finalQuery)) {
                list = new StoriesController.SearchStoriesList(currentAccount, null, finalQuery);
            }
            if (list.getLoadedCount() <= 0) {
                list.load(true, 4);
            }
            hasList = true;
            TLRPC.TL_channels_searchPosts req = new TLRPC.TL_channels_searchPosts();
            req.hashtag = this.hashtagQuery = hashtag;
            req.limit = 10;
            if (!messages.isEmpty()) {
                MessageObject lastMessage = messages.get(messages.size() - 1);
                req.offset_rate = lastRate;
                req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(lastMessage.messageOwner.peer_id);
            } else {
                req.offset_peer = new TLRPC.TL_inputPeerEmpty();
            }
            reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (id != searchId) {
                    return;
                }
                final boolean wasEmpty = messages.isEmpty();
                loading = false;
                if (res instanceof TLRPC.messages_Messages) {
                    TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) res;
                    if (msgs instanceof TLRPC.TL_messages_messages) {
                        totalCount = ((TLRPC.TL_messages_messages) msgs).messages.size();
                    } else if (msgs instanceof TLRPC.TL_messages_messagesSlice) {
                        totalCount = ((TLRPC.TL_messages_messagesSlice) msgs).count;
                    }
                    lastRate = msgs.next_rate;
                    MessagesController.getInstance(currentAccount).putUsers(msgs.users, false);
                    MessagesController.getInstance(currentAccount).putChats(msgs.chats, false);
                    for (int i = 0; i < msgs.messages.size(); ++i) {
                        final TLRPC.Message msg = msgs.messages.get(i);
                        final MessageObject messageObject = new MessageObject(currentAccount, msg, false, true);
                        messageObject.setQuery(finalQuery);
                        messages.add(messageObject);
                    }
                    endReached = messages.size() >= totalCount;
                    checkBottom();
                } else {
                    endReached = true;
                    totalCount = messages.size();
                }
                update(true);
                if (wasEmpty) {
                    scrollToTop(false);
                }
            }));
        }, 300);
    }

    public String getHashtag(String query) {
        return getHashtag(query, null);
    }

    public String getHashtag(String query, final boolean[] cashtag) {
        if (cashtag != null) cashtag[0] = false;
        if (query == null || query.isEmpty()) return null;
        String tquery = query.trim();
        if (tquery.length() <= 1) return null;
        if (tquery.charAt(0) != '#' && tquery.charAt(0) != '$') return null;
        if (tquery.indexOf('@') >= 0) return null;
        if (cashtag != null) cashtag[0] = tquery.charAt(0) == '$';
        return tquery.substring(1);
    }

    public void cancel() {
        if (list != null) {
            list.cancel();
        }
        hasList = false;
        if (reqId >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = -1;
        }
        AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        searchId++;
        loading = false;
    }


    public void checkBottom() {
        if (!TextUtils.isEmpty(lastQuery)) {
            if (!endReached && !loading && seesLoading()) {
                search(lastQuery);
            }
        }
    }

    public boolean seesLoading() {
        if (listView == null) return false;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof FlickerLoadingView) {
                return true;
            }
        }
        return false;
    }

    protected void scrollToTop(boolean ifAtTop) {

    }

}
