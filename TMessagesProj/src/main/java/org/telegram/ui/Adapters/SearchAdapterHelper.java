/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.util.Pair;

import androidx.collection.LongSparseArray;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatUsersActivity;
import org.telegram.ui.Components.ShareAlert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchAdapterHelper {

    public static class HashtagObject {
        String hashtag;
        int date;
    }

    public interface SearchAdapterHelperDelegate {
        void onDataSetChanged(int searchId);

        default void onSetHashtags(ArrayList<HashtagObject> arrayList, HashMap<String, HashtagObject> hashMap) {

        }

        default LongSparseArray<TLRPC.User> getExcludeUsers() {
            return null;
        }

        default LongSparseArray<TLRPC.TL_groupCallParticipant> getExcludeCallParticipants() {
            return null;
        }

        default boolean canApplySearchResults(int searchId) {
            return true;
        }
    }

    private SearchAdapterHelperDelegate delegate;

    private final ArrayList<Integer> pendingRequestIds = new ArrayList<>();
    private String lastFoundUsername = null;
    private final ArrayList<TLObject> localServerSearch = new ArrayList<>();
    private final ArrayList<TLObject> globalSearch = new ArrayList<>();
    private final LongSparseArray<TLObject> globalSearchMap = new LongSparseArray<>();
    private final ArrayList<TLObject> groupSearch = new ArrayList<>();
    private final LongSparseArray<TLObject> groupSearchMap = new LongSparseArray<>();
    private final LongSparseArray<TLObject> phoneSearchMap = new LongSparseArray<>();
    private final ArrayList<Object> phonesSearch = new ArrayList<>();
    private ArrayList<Object> localSearchResults;
    private ArrayList<DialogsSearchAdapter.RecentSearchObject> localRecentResults;

    private int currentAccount = UserConfig.selectedAccount;

    private String lastFoundChannel;

    private boolean allResultsAreGlobal;
    private boolean allowGlobalResults = true;

    private ArrayList<HashtagObject> hashtags;
    private HashMap<String, HashtagObject> hashtagsByText;
    private boolean hashtagsLoadedFromDb = false;

    protected static final class DialogSearchResult {
        public TLObject object;
        public int date;
        public CharSequence name;
    }

    public SearchAdapterHelper(boolean allAsGlobal) {
        allResultsAreGlobal = allAsGlobal;
    }

    public void setAllowGlobalResults(boolean value) {
        allowGlobalResults = value;
    }

    public boolean isSearchInProgress() {
        return pendingRequestIds.size() > 0;
    }
    public void queryServerSearch(String query, boolean allowUsername, boolean allowChats, boolean allowBots, boolean allowSelf, boolean canAddGroupsOnly, long channelId, boolean phoneNumbers, int type, int searchId) {
        queryServerSearch(query, allowUsername, allowChats, allowBots, allowSelf, canAddGroupsOnly, channelId, phoneNumbers, type, searchId, 0, null);
    }
    public void queryServerSearch(String query, boolean allowUsername, boolean allowChats, boolean allowBots, boolean allowSelf, boolean canAddGroupsOnly, long channelId, boolean phoneNumbers, int type, int searchId, long exceptDialogId) {
        queryServerSearch(query, allowUsername, allowChats, allowBots, allowSelf, canAddGroupsOnly, channelId, phoneNumbers, type, searchId, exceptDialogId, null);
    }
    public void queryServerSearch(String query, boolean allowUsername, boolean allowChats, boolean allowBots, boolean allowSelf, boolean canAddGroupsOnly, long channelId, boolean phoneNumbers, int type, int searchId, long exceptDialogId, Runnable onEnd) {
        for (int reqId : pendingRequestIds) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
        }
        pendingRequestIds.clear();
        if (query == null) {
            groupSearch.clear();
            groupSearchMap.clear();
            globalSearch.clear();
            globalSearchMap.clear();
            localServerSearch.clear();
            phonesSearch.clear();
            phoneSearchMap.clear();
            delegate.onDataSetChanged(searchId);
            return;
        }

        boolean hasChanged = false;
        ArrayList<Pair<TLObject, RequestDelegate>> requests = new ArrayList<>();
        if (query.length() > 0) {
            if (channelId != 0) {
                TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
                if (type == ChatUsersActivity.TYPE_ADMIN) {
                    req.filter = new TLRPC.TL_channelParticipantsAdmins();
                } else if (type == ChatUsersActivity.TYPE_KICKED) {
                    req.filter = new TLRPC.TL_channelParticipantsBanned();
                } else if (type == ChatUsersActivity.TYPE_BANNED) {
                    req.filter = new TLRPC.TL_channelParticipantsKicked();
                } else {
                    req.filter = new TLRPC.TL_channelParticipantsSearch();
                }
                req.filter.q = query;
                req.limit = 50;
                req.offset = 0;
                req.channel = MessagesController.getInstance(currentAccount).getInputChannel(channelId);
                requests.add(new Pair<>(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                        lastFoundChannel = query.toLowerCase();
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                        groupSearch.clear();
                        groupSearchMap.clear();
                        groupSearch.addAll(res.participants);
                        long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                        for (int a = 0, N = res.participants.size(); a < N; a++) {
                            TLRPC.ChannelParticipant participant = res.participants.get(a);
                            long peerId = MessageObject.getPeerId(participant.peer);
                            if (!allowSelf && peerId == currentUserId) {
                                groupSearch.remove(participant);
                                continue;
                            }
                            groupSearchMap.put(peerId, participant);
                        }
                    }
                }));
            } else {
                lastFoundChannel = query.toLowerCase();
            }
        } else {
            groupSearch.clear();
            groupSearchMap.clear();
            hasChanged = true;
        }
        if (allowUsername) {
            if (query.length() > 0) {
                TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
                req.q = query;
                req.limit = 20;
                requests.add(new Pair<>(req, (response, error) -> {
                    if (delegate.canApplySearchResults(searchId)) {
                        if (error == null) {
                            TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
                            globalSearch.clear();
                            globalSearchMap.clear();
                            localServerSearch.clear();
                            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                            LongSparseArray<TLRPC.Chat> chatsMap = new LongSparseArray<>();
                            LongSparseArray<TLRPC.User> usersMap = new LongSparseArray<>();
                            for (int a = 0; a < res.chats.size(); a++) {
                                TLRPC.Chat chat = res.chats.get(a);
                                chatsMap.put(chat.id, chat);
                            }
                            for (int a = 0; a < res.users.size(); a++) {
                                TLRPC.User user = res.users.get(a);
                                usersMap.put(user.id, user);
                            }
                            for (int b = 0; b < 2; b++) {
                                ArrayList<TLRPC.Peer> arrayList;
                                if (b == 0) {
                                    if (!allResultsAreGlobal) {
                                        continue;
                                    }
                                    arrayList = res.my_results;
                                } else {
                                    arrayList = res.results;
                                }
                                for (int a = 0; a < arrayList.size(); a++) {
                                    TLRPC.Peer peer = arrayList.get(a);
                                    TLRPC.User user = null;
                                    TLRPC.Chat chat = null;
                                    if (peer.user_id != 0) {
                                        user = usersMap.get(peer.user_id);
                                    } else if (peer.chat_id != 0) {
                                        chat = chatsMap.get(peer.chat_id);
                                    } else if (peer.channel_id != 0) {
                                        chat = chatsMap.get(peer.channel_id);
                                    }
                                    if (chat != null) {
                                        if (!allowChats || canAddGroupsOnly && !ChatObject.canAddBotsToChat(chat) || !allowGlobalResults && ChatObject.isNotInChat(chat) || !filter(chat)) {
                                            continue;
                                        }
                                        globalSearch.add(chat);
                                        globalSearchMap.put(-chat.id, chat);
                                    } else if (user != null) {
                                        if (canAddGroupsOnly || !allowBots && user.bot || !allowSelf && user.self || !allowGlobalResults && b == 1 && !user.contact || !filter(user)) {
                                            continue;
                                        }
                                        globalSearch.add(user);
                                        globalSearchMap.put(user.id, user);
                                    }
                                }
                            }
                            if (!allResultsAreGlobal) {
                                for (int a = 0; a < res.my_results.size(); a++) {
                                    TLRPC.Peer peer = res.my_results.get(a);
                                    TLRPC.User user = null;
                                    TLRPC.Chat chat = null;
                                    if (peer.user_id != 0) {
                                        user = usersMap.get(peer.user_id);
                                    } else if (peer.chat_id != 0) {
                                        chat = chatsMap.get(peer.chat_id);
                                    } else if (peer.channel_id != 0) {
                                        chat = chatsMap.get(peer.channel_id);
                                    }
                                    if (chat != null) {
                                        if (!allowChats || canAddGroupsOnly && !ChatObject.canAddBotsToChat(chat) || -chat.id == exceptDialogId || !filter(chat)) {
                                            continue;
                                        }
                                        localServerSearch.add(chat);
                                        globalSearchMap.put(-chat.id, chat);
                                    } else if (user != null) {
                                        if (canAddGroupsOnly || !allowBots && user.bot || !allowSelf && user.self || user.id == exceptDialogId || !filter(user)) {
                                            continue;
                                        }
                                        localServerSearch.add(user);
                                        globalSearchMap.put(user.id, user);
                                    }
                                }
                            }
                            lastFoundUsername = query.toLowerCase();
                        }
                    }
                }));
            } else {
                globalSearch.clear();
                globalSearchMap.clear();
                localServerSearch.clear();
                hasChanged = false;
            }
        }
        if (!canAddGroupsOnly && phoneNumbers && query.startsWith("+") && query.length() > 3) {
            phonesSearch.clear();
            phoneSearchMap.clear();
            String phone = PhoneFormat.stripExceptNumbers(query);
            ArrayList<TLRPC.TL_contact> arrayList = ContactsController.getInstance(currentAccount).contacts;
            boolean hasFullMatch = false;
            for (int a = 0, N = arrayList.size(); a < N; a++) {
                TLRPC.TL_contact contact = arrayList.get(a);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
                if (user == null) {
                    continue;
                }
                if (user.phone != null && user.phone.startsWith(phone)) {
                    if (!hasFullMatch) {
                        hasFullMatch = user.phone.length() == phone.length();
                    }
                    phonesSearch.add(user);
                    phoneSearchMap.put(user.id, user);
                }
            }
            if (!hasFullMatch) {
                phonesSearch.add("section");
                phonesSearch.add(phone);
            }
            hasChanged = false;
        }

        if (hasChanged) {
            delegate.onDataSetChanged(searchId);
        }

        final AtomicInteger gotResponses = new AtomicInteger(0);
        final ArrayList<Pair<TLObject, TLRPC.TL_error>> responses = new ArrayList<>();
        for (int i = 0; i < requests.size(); ++i) {
            final int index = i;
            Pair<TLObject, RequestDelegate> r = requests.get(i);
            TLObject req = r.first;
            responses.add(null);
            AtomicInteger reqId = new AtomicInteger();
            reqId.set(ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                responses.set(index, new Pair<>(response, error));
                Integer reqIdValue = reqId.get();
                if (!pendingRequestIds.contains(reqIdValue)) {
                    return;
                }
                pendingRequestIds.remove(reqIdValue);
                if (gotResponses.incrementAndGet() == requests.size()) {
                    for (int j = 0; j < requests.size(); ++j) {
                        RequestDelegate callback = requests.get(j).second;
                        Pair<TLObject, TLRPC.TL_error> res = responses.get(j);
                        if (res == null)
                            continue;
                        callback.run(res.first, res.second);
                    }
                    removeGroupSearchFromGlobal();
                    if (localSearchResults != null) {
                        mergeResults(localSearchResults, localRecentResults);
                    }
                    mergeExcludeResults();
                    delegate.onDataSetChanged(searchId);
                    if (onEnd != null) {
                        onEnd.run();
                    }
                }
            })));
            pendingRequestIds.add(reqId.get());
        }
    }

    private void removeGroupSearchFromGlobal() {
        if (globalSearchMap.size() == 0) {
            return;
        }
        for (int a = 0, N = groupSearchMap.size(); a < N; a++) {
            long uid = groupSearchMap.keyAt(a);
            TLRPC.User u = (TLRPC.User) globalSearchMap.get(uid);
            if (u != null) {
                globalSearch.remove(u);
                localServerSearch.remove(u);
                globalSearchMap.remove(u.id);
            }
        }
    }

    public void clear() {
        globalSearch.clear();
        globalSearchMap.clear();
        localServerSearch.clear();
    }

    public void unloadRecentHashtags() {
        hashtagsLoadedFromDb = false;
    }

    public boolean loadRecentHashtags() {
        if (hashtagsLoadedFromDb) {
            return true;
        }
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT id, date FROM hashtag_recent_v2 WHERE 1");
                final ArrayList<HashtagObject> arrayList = new ArrayList<>();
                final HashMap<String, HashtagObject> hashMap = new HashMap<>();
                while (cursor.next()) {
                    HashtagObject hashtagObject = new HashtagObject();
                    hashtagObject.hashtag = cursor.stringValue(0);
                    hashtagObject.date = cursor.intValue(1);
                    arrayList.add(hashtagObject);
                    hashMap.put(hashtagObject.hashtag, hashtagObject);
                }
                cursor.dispose();
                Collections.sort(arrayList, (lhs, rhs) -> {
                    if (lhs.date < rhs.date) {
                        return 1;
                    } else if (lhs.date > rhs.date) {
                        return -1;
                    } else {
                        return 0;
                    }
                });
                AndroidUtilities.runOnUIThread(() -> setHashtags(arrayList, hashMap));
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        return false;
    }

    public void addGroupMembers(ArrayList<TLObject> participants) {
        groupSearch.clear();
        groupSearch.addAll(participants);
        for (int a = 0, N = participants.size(); a < N; a++) {
            TLObject object = participants.get(a);
            if (object instanceof TLRPC.ChatParticipant) {
                groupSearchMap.put(((TLRPC.ChatParticipant) object).user_id, object);
            } else if (object instanceof TLRPC.ChannelParticipant) {
                groupSearchMap.put(MessageObject.getPeerId(((TLRPC.ChannelParticipant) object).peer), object);
            }
        }
        removeGroupSearchFromGlobal();
    }

    public void mergeResults(ArrayList<Object> localResults) {
        mergeResults(localResults, null);
    }

    public void mergeResults(ArrayList<Object> localResults, ArrayList<DialogsSearchAdapter.RecentSearchObject> recentResults) {
        localSearchResults = localResults;
        localRecentResults = recentResults;
        if (globalSearchMap.size() == 0 || localResults == null && recentResults == null) {
            return;
        }
        final int localResultsCount = localResults == null ? 0 : localResults.size();
        final int recentResultsCount = recentResults == null ? 0 : recentResults.size();
        int count = localResultsCount + recentResultsCount;
        for (int a = 0; a < count; a++) {
            Object obj = a < localResultsCount ? localResults.get(a) : recentResults.get(a - localResultsCount);
            if (obj instanceof DialogsSearchAdapter.RecentSearchObject) {
                obj = ((DialogsSearchAdapter.RecentSearchObject) obj).object;
            }
            if (obj instanceof ShareAlert.DialogSearchResult) {
                ShareAlert.DialogSearchResult searchResult = (ShareAlert.DialogSearchResult) obj;
                obj = searchResult.object;
            }
            if (obj instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) obj;
                TLRPC.User u = (TLRPC.User) globalSearchMap.get(user.id);
                if (u != null) {
                    globalSearch.remove(u);
                    localServerSearch.remove(u);
                    globalSearchMap.remove(u.id);
                }
                TLObject participant = groupSearchMap.get(user.id);
                if (participant != null) {
                    groupSearch.remove(participant);
                    groupSearchMap.remove(user.id);
                }
                Object object = phoneSearchMap.get(user.id);
                if (object != null) {
                    phonesSearch.remove(object);
                    phoneSearchMap.remove(user.id);
                }
            } else if (obj instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) obj;
                TLRPC.Chat c = (TLRPC.Chat) globalSearchMap.get(-chat.id);
                if (c != null) {
                    globalSearch.remove(c);
                    localServerSearch.remove(c);
                    globalSearchMap.remove(-c.id);
                }
            }
        }
    }

    public void mergeExcludeResults() {
        if (delegate == null) {
            return;
        }
        LongSparseArray<TLRPC.User> ignoreUsers = delegate.getExcludeUsers();
        if (ignoreUsers != null) {
            for (int a = 0, size = ignoreUsers.size(); a < size; a++) {
                TLRPC.User u = (TLRPC.User) globalSearchMap.get(ignoreUsers.keyAt(a));
                if (u != null) {
                    globalSearch.remove(u);
                    localServerSearch.remove(u);
                    globalSearchMap.remove(u.id);
                }
            }
        }
        LongSparseArray<TLRPC.TL_groupCallParticipant> ignoreParticipants = delegate.getExcludeCallParticipants();
        if (ignoreParticipants != null) {
            for (int a = 0, size = ignoreParticipants.size(); a < size; a++) {
                TLRPC.User u = (TLRPC.User) globalSearchMap.get(ignoreParticipants.keyAt(a));
                if (u != null) {
                    globalSearch.remove(u);
                    localServerSearch.remove(u);
                    globalSearchMap.remove(u.id);
                }
            }
        }
    }

    public void setDelegate(SearchAdapterHelperDelegate searchAdapterHelperDelegate) {
        delegate = searchAdapterHelperDelegate;
    }

    public void addHashtagsFromMessage(CharSequence message) {
        if (message == null) {
            return;
        }
        boolean changed = false;
        Pattern pattern = Pattern.compile("(^|\\s)#[^0-9][\\w@.]+");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (message.charAt(start) != '@' && message.charAt(start) != '#') {
                start++;
            }
            String hashtag = message.subSequence(start, end).toString();
            if (hashtagsByText == null) {
                hashtagsByText = new HashMap<>();
                hashtags = new ArrayList<>();
            }
            HashtagObject hashtagObject = hashtagsByText.get(hashtag);
            if (hashtagObject == null) {
                hashtagObject = new HashtagObject();
                hashtagObject.hashtag = hashtag;
                hashtagsByText.put(hashtagObject.hashtag, hashtagObject);
            } else {
                hashtags.remove(hashtagObject);
            }
            hashtagObject.date = (int) (System.currentTimeMillis() / 1000);
            hashtags.add(0, hashtagObject);
            changed = true;
        }
        if (changed) {
            putRecentHashtags(hashtags);
        }
    }

    private void putRecentHashtags(final ArrayList<HashtagObject> arrayList) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                MessagesStorage.getInstance(currentAccount).getDatabase().beginTransaction();
                SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO hashtag_recent_v2 VALUES(?, ?)");
                for (int a = 0; a < arrayList.size(); a++) {
                    if (a == 100) {
                        break;
                    }
                    HashtagObject hashtagObject = arrayList.get(a);
                    state.requery();
                    state.bindString(1, hashtagObject.hashtag);
                    state.bindInteger(2, hashtagObject.date);
                    state.step();
                }
                state.dispose();
                if (arrayList.size() > 100) {
                    state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM hashtag_recent_v2 WHERE id = ?");
                    for (int a = 100; a < arrayList.size(); a++) {
                        state.requery();
                        state.bindString(1, arrayList.get(a).hashtag);
                        state.step();
                    }
                    state.dispose();
                }
                MessagesStorage.getInstance(currentAccount).getDatabase().commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void removeUserId(long userId) {
        Object object = globalSearchMap.get(userId);
        if (object != null) {
            globalSearch.remove(object);
        }
        object = groupSearchMap.get(userId);
        if (object != null) {
            groupSearch.remove(object);
        }
    }

    public ArrayList<TLObject> getGlobalSearch() {
        if (MessagesController.getGlobalMainSettings().getBoolean("disableGlobalSearch", false)) {
            return new ArrayList<TLObject>();
        }
        return globalSearch;
    }

    public ArrayList<Object> getPhoneSearch() {
        return phonesSearch;
    }

    public ArrayList<TLObject> getLocalServerSearch() {
        return localServerSearch;
    }

    public ArrayList<TLObject> getGroupSearch() {
        return groupSearch;
    }

    public ArrayList<HashtagObject> getHashtags() {
        return hashtags;
    }

    public String getLastFoundUsername() {
        return lastFoundUsername;
    }

    public String getLastFoundChannel() {
        return lastFoundChannel;
    }

    public void clearRecentHashtags() {
        hashtags = new ArrayList<>();
        hashtagsByText = new HashMap<>();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM hashtag_recent_v2 WHERE 1").stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void setHashtags(ArrayList<HashtagObject> arrayList, HashMap<String, HashtagObject> hashMap) {
        hashtags = arrayList;
        hashtagsByText = hashMap;
        hashtagsLoadedFromDb = true;
        delegate.onSetHashtags(arrayList, hashMap);
    }

    protected boolean filter(TLObject obj) {
        return true;
    }
}
