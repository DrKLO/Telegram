package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class HashtagSearchController {
    private static volatile HashtagSearchController[] Instance = new HashtagSearchController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    public static HashtagSearchController getInstance(int num) {
        HashtagSearchController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new HashtagSearchController(num);
                }
            }
        }
        return localInstance;
    }

    public final int currentAccount;

    private final SearchResult myMessagesSearch = new SearchResult();
    private final SearchResult channelPostsSearch = new SearchResult();
    private final SharedPreferences historyPreferences;

    public final ArrayList<String> history = new ArrayList<>();
    public final static int HISTORY_LIMIT = 100;

    private HashtagSearchController(int currentAccount) {
        this.currentAccount = currentAccount;

        historyPreferences = ApplicationLoader.applicationContext.getSharedPreferences("hashtag_search_history" + currentAccount, Activity.MODE_PRIVATE);
        loadHistoryFromPref();
    }

    private void loadHistoryFromPref() {
        int count = historyPreferences.getInt("count", 0);
        history.clear();
        history.ensureCapacity(count);
        for (int i = 0; i < count; ++i) {
            String value = historyPreferences.getString("e_" + i, "");
            if (!value.startsWith("#") && !value.startsWith("$")) {
                value = "#" + value;
            }
            history.add(value);
        }
    }

    private void saveHistoryToPref() {
        SharedPreferences.Editor editor = historyPreferences.edit();
        editor.clear();
        editor.putInt("count", history.size());
        for (int i = 0; i < history.size(); ++i) {
            editor.putString("e_" + i, history.get(i));
        }
        editor.apply();
    }

    public void putToHistory(String hashtag) {
        if (!hashtag.startsWith("#") && !hashtag.startsWith("$")) {
            return;
        }
        int index = history.indexOf(hashtag);
        if (index != -1) {
            if (index == 0) {
                return;
            }
            history.remove(index);
        }
        history.add(0, hashtag);

        if (history.size() >= HISTORY_LIMIT) {
            history.subList(HISTORY_LIMIT - 1, history.size()).clear();
        }
        saveHistoryToPref();
    }

    public void clearHistory() {
        history.clear();
        saveHistoryToPref();
    }

    public void removeHashtagFromHistory(String hashtag) {
        int index = history.indexOf(hashtag);
        if (index != -1) {
            history.remove(index);
            saveHistoryToPref();
        }
    }

    @NonNull
    private SearchResult getSearchResult(int searchType) {
        if (searchType == ChatActivity.SEARCH_MY_MESSAGES) {
            return myMessagesSearch;
        } else if (searchType == ChatActivity.SEARCH_PUBLIC_POSTS) {
            return channelPostsSearch;
        }
        throw new RuntimeException("Unknown search type");
    }

    public ArrayList<MessageObject> getMessages(int searchType) {
        return getSearchResult(searchType).messages;
    }

    public int getCount(int searchType) {
        return getSearchResult(searchType).count;
    }

    public boolean isEndReached(int searchType) {
        return getSearchResult(searchType).endReached;
    }

    public void searchHashtag(String hashtag, int guid, int searchType, int loadIndex) {
        SearchResult search = getSearchResult(searchType);
        if (search.lastHashtag == null && hashtag == null) {
            return;
        }

        if (hashtag != null && hashtag.isEmpty()) {
            return;
        }

        if (hashtag == null) {
            hashtag = search.lastHashtag;
        } else if (!TextUtils.equals(hashtag, search.lastHashtag)) {
            search.clear();
        }
        search.lastHashtag = hashtag;

        final String query = hashtag;
        int limit = 30;
        TLObject request;
        if (searchType == ChatActivity.SEARCH_MY_MESSAGES) {
            TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
            req.limit = limit;
            req.q = query;
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
            if (search.lastOffsetPeer != null) {
                req.offset_rate = search.lastOffsetRate;
                req.offset_id = search.lastOffsetId;
                req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(search.lastOffsetPeer);
            }
            request = req;
        } else {
            TLRPC.TL_channels_searchPosts req = new TLRPC.TL_channels_searchPosts();
            req.limit = limit;
            req.hashtag = query;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
            if (search.lastOffsetPeer != null) {
                req.offset_rate = search.lastOffsetRate;
                req.offset_id = search.lastOffsetId;
                req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(search.lastOffsetPeer);
            }
            request = req;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (res, err) -> {
            if (res instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages messages = (TLRPC.messages_Messages) res;
                ArrayList<MessageObject> messageObjects = new ArrayList<>();
                for (TLRPC.Message msg : messages.messages) {
                    MessageObject obj = new MessageObject(currentAccount, msg, null, null, null, null, null, true, true, 0, false, false, false, searchType);
                    if (obj.hasValidGroupId()) {
                        obj.isPrimaryGroupMessage = true;
                    }
                    obj.setQuery(query);
                    messageObjects.add(obj);
                }

                AndroidUtilities.runOnUIThread(() -> {
                    search.lastOffsetRate = messages.next_rate;

                    for (MessageObject msg : messageObjects) {
                        MessageCompositeID compositeId = new MessageCompositeID(msg.messageOwner);
                        Integer id = search.generatedIds.get(compositeId);
                        if (id == null) {
                            id = search.lastGeneratedId--;
                            search.generatedIds.put(compositeId, id);
                            search.messages.add(msg);
                        }
                        msg.messageOwner.realId = msg.messageOwner.id;
                        msg.messageOwner.id = id;
                    }

                    if (!messages.messages.isEmpty()) {
                        TLRPC.Message lastMsg = messages.messages.get(messages.messages.size() - 1);
                        search.lastOffsetId = lastMsg.id;
                        search.lastOffsetPeer = lastMsg.peer_id;
                    }

                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(messages.users, messages.chats, true, true);
                    MessagesController.getInstance(currentAccount).putUsers(messages.users, false);
                    MessagesController.getInstance(currentAccount).putChats(messages.chats, false);

                    search.endReached = messages.messages.size() < limit;
                    search.count = Math.max(messages.count, messages.messages.size());

                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDidLoad, 0L, messageObjects.size(), messageObjects, false, 0, 0, 0, 0, 2, true, guid, loadIndex, 0, 0, ChatActivity.MODE_SEARCH);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.hashtagSearchUpdated, guid, search.count, search.endReached, search.getMask(), search.selectedIndex, 0);
                });
            }
        });
    }

    public void jumpToMessage(int guid, int index, int searchType) {
        SearchResult search = getSearchResult(searchType);
        if (index < 0 || index >= search.messages.size()) {
            return;
        }
        search.selectedIndex = index;
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.hashtagSearchUpdated, guid, search.count, search.endReached, search.getMask(), search.selectedIndex, search.messages.get(index).messageOwner.id);
    }

    public void clearSearchResults() {
        myMessagesSearch.clear();
        channelPostsSearch.clear();
    }

    public void clearSearchResults(int searchType) {
        getSearchResult(searchType).clear();
    }

    private final static class MessageCompositeID {
        final long dialog_id;
        final int id;

        MessageCompositeID(TLRPC.Message msg) {
            this(MessageObject.getDialogId(msg), msg.id);
        }

        MessageCompositeID(long dialogId, int id) {
            dialog_id = dialogId;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageCompositeID that = (MessageCompositeID) o;
            return dialog_id == that.dialog_id && id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dialog_id, id);
        }
    }

    private static class SearchResult {
        ArrayList<MessageObject> messages = new ArrayList<>();
        HashMap<MessageCompositeID, Integer> generatedIds = new HashMap<>();

        int lastOffsetRate;
        int lastOffsetId;
        TLRPC.Peer lastOffsetPeer;
        int lastGeneratedId = Integer.MAX_VALUE;
        String lastHashtag;
        int selectedIndex;
        int count;
        boolean endReached;

        int getMask() {
            int mask = 0;
            if (selectedIndex < messages.size() - 1) {
                mask |= 1;
            }
            if (selectedIndex > 0) {
                mask |= 2;
            }
            return mask;
        }

        void clear() {
            messages.clear();
            generatedIds.clear();
            lastOffsetRate = 0;
            lastOffsetId = 0;
            lastOffsetPeer = null;
            lastGeneratedId = Integer.MAX_VALUE - 10;
            lastHashtag = null;
            selectedIndex = 0;
            count = 0;
            endReached = false;
        }
    }
}
