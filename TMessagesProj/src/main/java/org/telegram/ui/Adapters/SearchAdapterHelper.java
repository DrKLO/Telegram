/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.util.SparseArray;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatUsersActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchAdapterHelper {

    public static class HashtagObject {
        String hashtag;
        int date;
    }

    public interface SearchAdapterHelperDelegate {
        void onDataSetChanged();

        default void onSetHashtags(ArrayList<HashtagObject> arrayList, HashMap<String, HashtagObject> hashMap) {

        }

        default SparseArray<TLRPC.User> getExcludeUsers() {
            return null;
        }
    }

    private SearchAdapterHelperDelegate delegate;

    private int reqId = 0;
    private int lastReqId;
    private String lastFoundUsername = null;
    private ArrayList<TLObject> localServerSearch = new ArrayList<>();
    private ArrayList<TLObject> globalSearch = new ArrayList<>();
    private SparseArray<TLObject> globalSearchMap = new SparseArray<>();
    private ArrayList<TLObject> groupSearch = new ArrayList<>();
    private SparseArray<TLObject> groupSearchMap = new SparseArray<>();
    private SparseArray<TLObject> phoneSearchMap = new SparseArray<>();
    private ArrayList<Object> phonesSearch = new ArrayList<>();
    private ArrayList<TLObject> localSearchResults;

    private int currentAccount = UserConfig.selectedAccount;

    private int channelReqId = 0;
    private int channelLastReqId;
    private String lastFoundChannel;

    private boolean allResultsAreGlobal;

    private ArrayList<HashtagObject> hashtags;
    private HashMap<String, HashtagObject> hashtagsByText;
    private boolean hashtagsLoadedFromDb = false;

    protected static final class DialogSearchResult {
        public TLObject object;
        public int date;
        public CharSequence name;
    }

    public SearchAdapterHelper(boolean global) {
        allResultsAreGlobal = global;
    }

    public boolean isSearchInProgress() {
        return reqId != 0 || channelReqId != 0;
    }

    public void queryServerSearch(final String query, final boolean allowUsername, final boolean allowChats, final boolean allowBots, final boolean allowSelf, final int channelId, final boolean phoneNumbers, final int type) {
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        if (channelReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(channelReqId, true);
            channelReqId = 0;
        }
        if (query == null) {
            groupSearch.clear();
            groupSearchMap.clear();
            globalSearch.clear();
            globalSearchMap.clear();
            localServerSearch.clear();
            phonesSearch.clear();
            phoneSearchMap.clear();
            lastReqId = 0;
            channelLastReqId = 0;
            delegate.onDataSetChanged();
            return;
        }
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
                final int currentReqId = ++channelLastReqId;
                channelReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (currentReqId == channelLastReqId) {
                        if (error == null) {
                            TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                            lastFoundChannel = query.toLowerCase();
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            groupSearch.clear();
                            groupSearchMap.clear();
                            groupSearch.addAll(res.participants);
                            int currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                            for (int a = 0, N = res.participants.size(); a < N; a++) {
                                TLRPC.ChannelParticipant participant = res.participants.get(a);
                                if (!allowSelf && participant.user_id == currentUserId) {
                                    groupSearch.remove(participant);
                                    continue;
                                }
                                groupSearchMap.put(participant.user_id, participant);
                            }
                            if (localSearchResults != null) {
                                mergeResults(localSearchResults);
                            }
                            delegate.onDataSetChanged();
                        }
                    }
                    channelReqId = 0;
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
            } else {
                lastFoundChannel = query.toLowerCase();
            }
        } else {
            groupSearch.clear();
            groupSearchMap.clear();
            channelLastReqId = 0;
            delegate.onDataSetChanged();
        }
        if (allowUsername) {
            if (query.length() > 0) {
                TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
                req.q = query;
                req.limit = 50;
                final int currentReqId = ++lastReqId;
                reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (currentReqId == lastReqId) {
                        reqId = 0;
                        if (error == null) {
                            TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
                            globalSearch.clear();
                            globalSearchMap.clear();
                            localServerSearch.clear();
                            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                            SparseArray<TLRPC.Chat> chatsMap = new SparseArray<>();
                            SparseArray<TLRPC.User> usersMap = new SparseArray<>();
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
                                        if (!allowChats) {
                                            continue;
                                        }
                                        globalSearch.add(chat);
                                        globalSearchMap.put(-chat.id, chat);
                                    } else if (user != null) {
                                        if (!allowBots && user.bot || !allowSelf && user.self) {
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
                                        if (!allowChats) {
                                            continue;
                                        }
                                        localServerSearch.add(chat);
                                        globalSearchMap.put(-chat.id, chat);
                                    } else if (user != null) {
                                        localServerSearch.add(user);
                                        globalSearchMap.put(user.id, user);
                                    }
                                }
                            }
                            lastFoundUsername = query.toLowerCase();
                            if (localSearchResults != null) {
                                mergeResults(localSearchResults);
                            }
                            mergeExcludeResults();
                            delegate.onDataSetChanged();
                        }
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
            } else {
                globalSearch.clear();
                globalSearchMap.clear();
                localServerSearch.clear();
                lastReqId = 0;
                delegate.onDataSetChanged();
            }
        }
        if (phoneNumbers && query.startsWith("+") && query.length() > 3) {
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
            delegate.onDataSetChanged();
        }
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

    public void mergeResults(ArrayList<TLObject> localResults) {
        localSearchResults = localResults;
        if (globalSearchMap.size() == 0 || localResults == null) {
            return;
        }
        int count = localResults.size();
        for (int a = 0; a < count; a++) {
            TLObject obj = localResults.get(a);
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
        SparseArray<TLRPC.User> ignoreUsers = delegate.getExcludeUsers();
        if (ignoreUsers == null) {
            return;
        }
        for (int a = 0, size = ignoreUsers.size(); a < size; a++) {
            TLRPC.User u = (TLRPC.User) globalSearchMap.get(ignoreUsers.keyAt(a));
            if (u != null) {
                globalSearch.remove(u);
                localServerSearch.remove(u);
                globalSearchMap.remove(u.id);
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
        Pattern pattern = Pattern.compile("(^|\\s)#[\\w@.]+");
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
                MessagesStorage.getInstance(currentAccount).getDatabase().commitTransaction();
                if (arrayList.size() >= 100) {
                    MessagesStorage.getInstance(currentAccount).getDatabase().beginTransaction();
                    for (int a = 100; a < arrayList.size(); a++) {
                        MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM hashtag_recent_v2 WHERE id = '" + arrayList.get(a).hashtag + "'").stepThis().dispose();
                    }
                    MessagesStorage.getInstance(currentAccount).getDatabase().commitTransaction();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public ArrayList<TLObject> getGlobalSearch() {
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
}
