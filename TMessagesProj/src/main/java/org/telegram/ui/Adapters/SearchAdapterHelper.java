/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Adapters;

import android.util.SparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

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
        void onSetHashtags(ArrayList<HashtagObject> arrayList, HashMap<String, HashtagObject> hashMap);
    }

    private SearchAdapterHelperDelegate delegate;

    private int reqId = 0;
    private int lastReqId;
    private String lastFoundUsername = null;
    private ArrayList<TLObject> globalSearch = new ArrayList<>();
    private ArrayList<TLObject> localServerSearch = new ArrayList<>();
    private SparseArray<TLObject> globalSearchMap = new SparseArray<>();
    private ArrayList<TLRPC.ChannelParticipant> groupSearch = new ArrayList<>();
    private ArrayList<TLRPC.ChannelParticipant> groupSearch2 = new ArrayList<>();
    private ArrayList<TLObject> localSearchResults;

    private int currentAccount = UserConfig.selectedAccount;

    private int channelReqId = 0;
    private int channelLastReqId;
    private String lastFoundChannel;

    private int channelReqId2 = 0;
    private int channelLastReqId2;
    private String lastFoundChannel2;

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

    public void queryServerSearch(final String query, final boolean allowUsername, final boolean allowChats, final boolean allowBots, final boolean allowSelf, final int channelId, final boolean kicked) {
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        if (channelReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(channelReqId, true);
            channelReqId = 0;
        }
        if (channelReqId2 != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(channelReqId2, true);
            channelReqId2 = 0;
        }
        if (query == null) {
            groupSearch.clear();
            groupSearch2.clear();
            globalSearch.clear();
            globalSearchMap.clear();
            localServerSearch.clear();
            lastReqId = 0;
            channelLastReqId = 0;
            channelLastReqId2 = 0;
            delegate.onDataSetChanged();
            return;
        }
        if (query.length() > 0 && channelId != 0) {
            TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
            if (kicked) {
                req.filter = new TLRPC.TL_channelParticipantsBanned();
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
                        groupSearch = res.participants;
                        delegate.onDataSetChanged();
                    }
                }
                channelReqId = 0;
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
            if (kicked) {
                req = new TLRPC.TL_channels_getParticipants();
                req.filter = new TLRPC.TL_channelParticipantsKicked();
                req.filter.q = query;
                req.limit = 50;
                req.offset = 0;
                req.channel = MessagesController.getInstance(currentAccount).getInputChannel(channelId);
                final int currentReqId2 = ++channelLastReqId2;
                channelReqId2 = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (currentReqId2 == channelLastReqId2) {
                        if (error == null) {
                            TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                            lastFoundChannel2 = query.toLowerCase();
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            groupSearch2 = res.participants;
                            delegate.onDataSetChanged();
                        }
                    }
                    channelReqId2 = 0;
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
            }
        } else {
            groupSearch.clear();
            groupSearch2.clear();
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
                            delegate.onDataSetChanged();
                        }
                    }
                    reqId = 0;
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
            } else {
                globalSearch.clear();
                globalSearchMap.clear();
                localServerSearch.clear();
                lastReqId = 0;
                delegate.onDataSetChanged();
            }
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

    public ArrayList<TLObject> getLocalServerSearch() {
        return localServerSearch;
    }

    public ArrayList<TLRPC.ChannelParticipant> getGroupSearch() {
        return groupSearch;
    }

    public ArrayList<TLRPC.ChannelParticipant> getGroupSearch2() {
        return groupSearch2;
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

    public String getLastFoundChannel2() {
        return lastFoundChannel2;
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
