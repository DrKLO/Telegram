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

    private final SearchResult myMessagesSearch;
    private final SearchResult channelPostsSearch;
    private final SearchResult localPostsSearch;
    private final SharedPreferences historyPreferences;

    public final ArrayList<String> history = new ArrayList<>();
    public final static int HISTORY_LIMIT = 100;

    private HashtagSearchController(int currentAccount) {
        this.currentAccount = currentAccount;
        myMessagesSearch = new SearchResult(currentAccount);
        channelPostsSearch = new SearchResult(currentAccount);
        localPostsSearch = new SearchResult(currentAccount);

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
    public SearchResult getSearchResult(int searchType) {
        if (searchType == ChatActivity.SEARCH_MY_MESSAGES) {
            return myMessagesSearch;
        } else if (searchType == ChatActivity.SEARCH_PUBLIC_POSTS) {
            return channelPostsSearch;
        } else if (searchType == ChatActivity.SEARCH_CHANNEL_POSTS) {
            return localPostsSearch;
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

    public void searchHashtag(String _query, int guid, int searchType, int loadIndex) {
        SearchResult search = getSearchResult(searchType);
        if (search.lastHashtag == null && _query == null) {
            return;
        }

        if (_query != null && _query.isEmpty()) {
            return;
        }

        if (_query == null) {
            _query = search.lastHashtag;
        } else if (!TextUtils.equals(_query, search.lastHashtag)) {
            search.clear();
        } else if (search.loading) {
            return;
        }
        search.lastHashtag = _query;
        final String query = _query;

        String _username = null;
        int atIndex = _query.indexOf('@');
        if (atIndex >= 0) {
            _username = _query.substring(atIndex + 1);
            _query = _query.substring(0, atIndex);
        }
        final String hashtag = _query;
        final String username = _username;
        search.loading = true;

        TLObject chat = null;
        if (!TextUtils.isEmpty(username)) {
            chat = MessagesController.getInstance(currentAccount).getUserOrChat(username);
            if (chat == null) {
                Runnable[] cancel = new Runnable[1];
                cancel[0] = search.cancel = MessagesController.getInstance(currentAccount).getUserNameResolver().resolve(username, resolvedChatId -> {
                    if (!TextUtils.equals(search.lastHashtag, query)) return;
                    final TLObject resolvedChat = MessagesController.getInstance(currentAccount).getUserOrChat(username);
                    if (resolvedChat == null) {
                        if (cancel[0] == search.cancel) {
                            search.cancel = null;
                        } else {
                            return;
                        }
                        search.loading = false;
                        search.endReached = true;
                        search.count = 0;
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.hashtagSearchUpdated, guid, search.count, search.endReached, search.getMask(), search.selectedIndex, 0);
                        return;
                    }
                    searchHashtag(query, guid, searchType, loadIndex);
                });
                return;
            }
        }

        int limit = 21;
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
            if (chat != null) {
                TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
                req.peer = MessagesController.getInputPeer(chat);
                req.q = hashtag;
                req.limit = limit;
                if (search.lastOffsetId != 0) {
                    req.offset_id = search.lastOffsetId;
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
        }
        final int[] reqId = new int[1];
        reqId[0] = search.reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(request, (res, err) -> {
            if (res instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages messages = (TLRPC.messages_Messages) res;
                ArrayList<MessageObject> messageObjects = new ArrayList<>();
                for (TLRPC.Message msg : messages.messages) {
                    MessageObject obj = new MessageObject(currentAccount, msg, null, null, null, null, null, true, true, 0, false, false, false, searchType);
                    if (obj.hasValidGroupId()) {
                        obj.isPrimaryGroupMessage = true;
                    }
                    obj.setQuery(query, false);
                    messageObjects.add(obj);
                }

                AndroidUtilities.runOnUIThread(() -> {
                    if (reqId[0] == search.reqId) {
                        search.reqId = -1;
                    } else {
                        return;
                    }
                    search.loading = false;
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
                        search.lastOffsetId = lastMsg.realId;
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

    public static class SearchResult {
        public final ArrayList<MessageObject> messages = new ArrayList<>();
        public final HashMap<MessageCompositeID, Integer> generatedIds = new HashMap<>();

        private final int currentAccount;
        public SearchResult(int account) {
            this.currentAccount = account;
        }

        public int reqId = -1;
        public Runnable cancel;
        public boolean loading;
        public int lastOffsetRate;
        public int lastOffsetId;
        public TLRPC.Peer lastOffsetPeer;
        public int lastGeneratedId = Integer.MAX_VALUE;
        public String lastHashtag;
        public int selectedIndex;
        public int count;
        public boolean endReached;

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
            if (reqId >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                reqId = -1;
            }
            if (cancel != null) {
                cancel.run();
                cancel = null;
            }
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
