/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.GreySectionCell;
import org.telegram.ui.Cells.HashtagSearchCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ProfileSearchCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class DialogsSearchAdapter extends BaseSearchAdapterRecycler {

    private Context mContext;
    private Timer searchTimer;
    private ArrayList<TLObject> searchResult = new ArrayList<>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
    private ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private ArrayList<String> searchResultHashtags = new ArrayList<>();
    private String lastSearchText;
    private int reqId = 0;
    private int lastReqId;
    private MessagesActivitySearchAdapterDelegate delegate;
    private int needMessagesSearch;
    private boolean messagesSearchEndReached;
    private String lastMessagesSearchString;
    private int lastSearchId = 0;
    private int dialogsType;

    private ArrayList<RecentSearchObject> recentSearchObjects = new ArrayList<>();
    private HashMap<Long, RecentSearchObject> recentSearchObjectsById = new HashMap<>();

    private class Holder extends RecyclerView.ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }
    }

    private class DialogSearchResult {
        public TLObject object;
        public int date;
        public CharSequence name;
    }

    protected static class RecentSearchObject {
        TLObject object;
        int date;
        long did;
    }

    public interface MessagesActivitySearchAdapterDelegate {
        void searchStateChanged(boolean searching);
    }

    public DialogsSearchAdapter(Context context, int messagesSearch, int type) {
        mContext = context;
        needMessagesSearch = messagesSearch;
        dialogsType = type;
        loadRecentSearch();
    }

    public void setDelegate(MessagesActivitySearchAdapterDelegate delegate) {
        this.delegate = delegate;
    }

    public boolean isMessagesSearchEndReached() {
        return messagesSearchEndReached;
    }

    public void loadMoreSearchMessages() {
        searchMessagesInternal(lastMessagesSearchString);
    }

    public String getLastSearchString() {
        return lastMessagesSearchString;
    }

    private void searchMessagesInternal(final String query) {
        if (needMessagesSearch == 0 || (lastMessagesSearchString == null || lastMessagesSearchString.length() == 0) && (query == null || query.length() == 0)) {
            return;
        }
        if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(reqId, true);
            reqId = 0;
        }
        if (query == null || query.length() == 0) {
            searchResultMessages.clear();
            lastReqId = 0;
            lastMessagesSearchString = null;
            notifyDataSetChanged();
            if (delegate != null) {
                delegate.searchStateChanged(false);
            }
            return;
        }

        final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.limit = 20;
        req.q = query;
        if (lastMessagesSearchString != null && query.equals(lastMessagesSearchString) && !searchResultMessages.isEmpty()) {
            MessageObject lastMessage = searchResultMessages.get(searchResultMessages.size() - 1);
            req.offset_id = lastMessage.getId();
            req.offset_date = lastMessage.messageOwner.date;
            int id;
            if (lastMessage.messageOwner.to_id.channel_id != 0) {
                id = -lastMessage.messageOwner.to_id.channel_id;
            } else if (lastMessage.messageOwner.to_id.chat_id != 0) {
                id = -lastMessage.messageOwner.to_id.chat_id;
            } else {
                id = lastMessage.messageOwner.to_id.user_id;
            }
            req.offset_peer = MessagesController.getInputPeer(id);
        } else {
            req.offset_date = 0;
            req.offset_id = 0;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        lastMessagesSearchString = query;
        final int currentReqId = ++lastReqId;
        if (delegate != null) {
            delegate.searchStateChanged(true);
        }
        reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentReqId == lastReqId) {
                            if (error == null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                                if (req.offset_id == 0) {
                                    searchResultMessages.clear();
                                }
                                for (TLRPC.Message message : res.messages) {
                                    searchResultMessages.add(new MessageObject(message, null, false));
                                }
                                messagesSearchEndReached = res.messages.size() != 20;
                                notifyDataSetChanged();
                            }
                        }
                        if (delegate != null) {
                            delegate.searchStateChanged(false);
                        }
                        reqId = 0;
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public boolean hasRecentRearch() {
        return !recentSearchObjects.isEmpty();
    }

    public boolean isRecentSearchDisplayed() {
        return needMessagesSearch != 2 && (lastSearchText == null || lastSearchText.length() == 0) && !recentSearchObjects.isEmpty();
    }

    public void loadRecentSearch() {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT did, date FROM search_recent WHERE 1");

                    ArrayList<Integer> usersToLoad = new ArrayList<>();
                    ArrayList<Integer> chatsToLoad = new ArrayList<>();
                    ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                    ArrayList<TLRPC.User> encUsers = new ArrayList<>();

                    final ArrayList<RecentSearchObject> arrayList = new ArrayList<>();
                    final HashMap<Long, RecentSearchObject> hashMap = new HashMap<>();
                    while (cursor.next()) {
                        long did = cursor.longValue(0);

                        boolean add = false;
                        int lower_id = (int) did;
                        int high_id = (int) (did >> 32);
                        if (lower_id != 0) {
                            if (high_id == 1) {
                                if (dialogsType == 0 && !chatsToLoad.contains(lower_id)) {
                                    chatsToLoad.add(lower_id);
                                    add = true;
                                }
                            } else {
                                if (lower_id > 0) {
                                    if (dialogsType != 2 && !usersToLoad.contains(lower_id)) {
                                        usersToLoad.add(lower_id);
                                        add = true;
                                    }
                                } else {
                                    if (!chatsToLoad.contains(-lower_id)) {
                                        chatsToLoad.add(-lower_id);
                                        add = true;
                                    }
                                }
                            }
                        } else if (dialogsType == 0) {
                            if (!encryptedToLoad.contains(high_id)) {
                                encryptedToLoad.add(high_id);
                                add = true;
                            }
                        }
                        if (add) {
                            RecentSearchObject recentSearchObject = new RecentSearchObject();
                            recentSearchObject.did = did;
                            recentSearchObject.date = cursor.intValue(1);
                            arrayList.add(recentSearchObject);
                            hashMap.put(recentSearchObject.did, recentSearchObject);
                        }
                    }
                    cursor.dispose();


                    ArrayList<TLRPC.User> users = new ArrayList<>();

                    if (!encryptedToLoad.isEmpty()) {
                        ArrayList<TLRPC.EncryptedChat> encryptedChats = new ArrayList<>();
                        MessagesStorage.getInstance().getEncryptedChatsInternal(TextUtils.join(",", encryptedToLoad), encryptedChats, usersToLoad);
                        for (int a = 0; a < encryptedChats.size(); a++) {
                            hashMap.get((long) encryptedChats.get(a).id << 32).object = encryptedChats.get(a);
                        }
                    }

                    if (!chatsToLoad.isEmpty()) {
                        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                        MessagesStorage.getInstance().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                        for (int a = 0; a < chats.size(); a++) {
                            TLRPC.Chat chat = chats.get(a);
                            long did;
                            if (chat.id > 0) {
                                did = -chat.id;
                            } else {
                                did = AndroidUtilities.makeBroadcastId(chat.id);
                            }
                            if (chat.migrated_to != null) {
                                RecentSearchObject recentSearchObject = hashMap.remove(did);
                                if (recentSearchObject != null) {
                                    arrayList.remove(recentSearchObject);
                                }
                            } else {
                                hashMap.get(did).object = chat;
                            }
                        }
                    }

                    if (!usersToLoad.isEmpty()) {
                        MessagesStorage.getInstance().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                        for (int a = 0; a < users.size(); a++) {
                            TLRPC.User user = users.get(a);
                            RecentSearchObject recentSearchObject = hashMap.get((long) user.id);
                            if (recentSearchObject != null) {
                                recentSearchObject.object = user;
                            }
                        }
                    }

                    Collections.sort(arrayList, new Comparator<RecentSearchObject>() {
                        @Override
                        public int compare(RecentSearchObject lhs, RecentSearchObject rhs) {
                            if (lhs.date < rhs.date) {
                                return 1;
                            } else if (lhs.date > rhs.date) {
                                return -1;
                            } else {
                                return 0;
                            }
                        }
                    });
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            setRecentSearch(arrayList, hashMap);
                        }
                    });
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    public void putRecentSearch(final long did, TLObject object) {
        RecentSearchObject recentSearchObject = recentSearchObjectsById.get(did);
        if (recentSearchObject == null) {
            recentSearchObject = new RecentSearchObject();
            recentSearchObjectsById.put(did, recentSearchObject);
        } else {
            recentSearchObjects.remove(recentSearchObject);
        }
        recentSearchObjects.add(0, recentSearchObject);
        recentSearchObject.did = did;
        recentSearchObject.object = object;
        recentSearchObject.date = (int) (System.currentTimeMillis() / 1000);
        notifyDataSetChanged();
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO search_recent VALUES(?, ?)");
                    state.requery();
                    state.bindLong(1, did);
                    state.bindInteger(2, (int) (System.currentTimeMillis() / 1000));
                    state.step();
                    state.dispose();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    public void clearRecentSearch() {
        recentSearchObjectsById = new HashMap<>();
        recentSearchObjects = new ArrayList<>();
        notifyDataSetChanged();
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM search_recent WHERE 1").stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    private void setRecentSearch(ArrayList<RecentSearchObject> arrayList, HashMap<Long, RecentSearchObject> hashMap) {
        recentSearchObjects = arrayList;
        recentSearchObjectsById = hashMap;
        for (int a = 0; a < recentSearchObjects.size(); a++) {
            RecentSearchObject recentSearchObject = recentSearchObjects.get(a);
            if (recentSearchObject.object instanceof TLRPC.User) {
                MessagesController.getInstance().putUser((TLRPC.User) recentSearchObject.object, true);
            } else if (recentSearchObject.object instanceof TLRPC.Chat) {
                MessagesController.getInstance().putChat((TLRPC.Chat) recentSearchObject.object, true);
            } else if (recentSearchObject.object instanceof TLRPC.EncryptedChat) {
                MessagesController.getInstance().putEncryptedChat((TLRPC.EncryptedChat) recentSearchObject.object, true);
            }
        }
        notifyDataSetChanged();
    }

    private void searchDialogsInternal(final String query, final int searchId) {
        if (needMessagesSearch == 2) {
            return;
        }
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    String search1 = query.trim().toLowerCase();
                    if (search1.length() == 0) {
                        lastSearchId = -1;
                        updateSearchResults(new ArrayList<TLObject>(), new ArrayList<CharSequence>(), new ArrayList<TLRPC.User>(), lastSearchId);
                        return;
                    }
                    String search2 = LocaleController.getInstance().getTranslitString(search1);
                    if (search1.equals(search2) || search2.length() == 0) {
                        search2 = null;
                    }
                    String search[] = new String[1 + (search2 != null ? 1 : 0)];
                    search[0] = search1;
                    if (search2 != null) {
                        search[1] = search2;
                    }

                    ArrayList<Integer> usersToLoad = new ArrayList<>();
                    ArrayList<Integer> chatsToLoad = new ArrayList<>();
                    ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                    ArrayList<TLRPC.User> encUsers = new ArrayList<>();
                    int resultCount = 0;

                    HashMap<Long, DialogSearchResult> dialogsResult = new HashMap<>();
                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 400");
                    while (cursor.next()) {
                        long id = cursor.longValue(0);
                        DialogSearchResult dialogSearchResult = new DialogSearchResult();
                        dialogSearchResult.date = cursor.intValue(1);
                        dialogsResult.put(id, dialogSearchResult);

                        int lower_id = (int) id;
                        int high_id = (int) (id >> 32);
                        if (lower_id != 0) {
                            if (high_id == 1) {
                                if (dialogsType == 0 && !chatsToLoad.contains(lower_id)) {
                                    chatsToLoad.add(lower_id);
                                }
                            } else {
                                if (lower_id > 0) {
                                    if (dialogsType != 2 && !usersToLoad.contains(lower_id)) {
                                        usersToLoad.add(lower_id);
                                    }
                                } else {
                                    if (!chatsToLoad.contains(-lower_id)) {
                                        chatsToLoad.add(-lower_id);
                                    }
                                }
                            }
                        } else if (dialogsType == 0) {
                            if (!encryptedToLoad.contains(high_id)) {
                                encryptedToLoad.add(high_id);
                            }
                        }
                    }
                    cursor.dispose();

                    if (!usersToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, status, name FROM users WHERE uid IN(%s)", TextUtils.join(",", usersToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(2);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            String username = null;
                            int usernamePos = name.lastIndexOf(";;;");
                            if (usernamePos != -1) {
                                username = name.substring(usernamePos + 3);
                            }
                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }
                                if (found != 0) {
                                    NativeByteBuffer data = new NativeByteBuffer(cursor.byteArrayLength(0));
                                    if (data != null && cursor.byteBufferValue(0, data) != 0) {
                                        TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                        DialogSearchResult dialogSearchResult = dialogsResult.get((long) user.id);
                                        if (user.status != null) {
                                            user.status.expires = cursor.intValue(1);
                                        }
                                        if (found == 1) {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                        } else {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q);
                                        }
                                        dialogSearchResult.object = user;
                                        resultCount++;
                                    }
                                    data.reuse();
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    if (!chatsToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, name FROM chats WHERE uid IN(%s)", TextUtils.join(",", chatsToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(1);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    NativeByteBuffer data = new NativeByteBuffer(cursor.byteArrayLength(0));
                                    if (data != null && cursor.byteBufferValue(0, data) != 0) {
                                        TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                                        if (!(chat == null || chat.deactivated || ChatObject.isChannel(chat) && ChatObject.isNotInChat(chat))) {
                                            long dialog_id;
                                            if (chat.id > 0) {
                                                dialog_id = -chat.id;
                                            } else {
                                                dialog_id = AndroidUtilities.makeBroadcastId(chat.id);
                                            }
                                            DialogSearchResult dialogSearchResult = dialogsResult.get(dialog_id);
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName(chat.title, null, q);
                                            dialogSearchResult.object = chat;
                                            resultCount++;
                                        }
                                    }
                                    data.reuse();
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    if (!encryptedToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT q.data, u.name, q.user, q.g, q.authkey, q.ttl, u.data, u.status, q.layer, q.seq_in, q.seq_out, q.use_count, q.exchange_id, q.key_date, q.fprint, q.fauthkey, q.khash FROM enc_chats as q INNER JOIN users as u ON q.user = u.uid WHERE q.uid IN(%s)", TextUtils.join(",", encryptedToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(1);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }

                            String username = null;
                            int usernamePos = name.lastIndexOf(";;;");
                            if (usernamePos != -1) {
                                username = name.substring(usernamePos + 2);
                            }
                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    NativeByteBuffer data = new NativeByteBuffer(cursor.byteArrayLength(0));
                                    NativeByteBuffer data2 = new NativeByteBuffer(cursor.byteArrayLength(6));
                                    if (data != null && cursor.byteBufferValue(0, data) != 0 && cursor.byteBufferValue(6, data2) != 0) {
                                        TLRPC.EncryptedChat chat = TLRPC.EncryptedChat.TLdeserialize(data, data.readInt32(false), false);
                                        DialogSearchResult dialogSearchResult = dialogsResult.get((long) chat.id << 32);

                                        chat.user_id = cursor.intValue(2);
                                        chat.a_or_b = cursor.byteArrayValue(3);
                                        chat.auth_key = cursor.byteArrayValue(4);
                                        chat.ttl = cursor.intValue(5);
                                        chat.layer = cursor.intValue(8);
                                        chat.seq_in = cursor.intValue(9);
                                        chat.seq_out = cursor.intValue(10);
                                        int use_count = cursor.intValue(11);
                                        chat.key_use_count_in = (short) (use_count >> 16);
                                        chat.key_use_count_out = (short) (use_count);
                                        chat.exchange_id = cursor.longValue(12);
                                        chat.key_create_date = cursor.intValue(13);
                                        chat.future_key_fingerprint = cursor.longValue(14);
                                        chat.future_auth_key = cursor.byteArrayValue(15);
                                        chat.key_hash = cursor.byteArrayValue(16);

                                        TLRPC.User user = TLRPC.User.TLdeserialize(data2, data2.readInt32(false), false);
                                        if (user.status != null) {
                                            user.status.expires = cursor.intValue(7);
                                        }
                                        if (found == 1) {
                                            dialogSearchResult.name = AndroidUtilities.replaceTags("<c#ff00a60e>" + ContactsController.formatName(user.first_name, user.last_name) + "</c>");
                                        } else {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q);
                                        }
                                        dialogSearchResult.object = chat;
                                        encUsers.add(user);
                                        resultCount++;
                                    }
                                    data.reuse();
                                    data2.reuse();
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    ArrayList<DialogSearchResult> searchResults = new ArrayList<>(resultCount);
                    for (DialogSearchResult dialogSearchResult : dialogsResult.values()) {
                        if (dialogSearchResult.object != null && dialogSearchResult.name != null) {
                            searchResults.add(dialogSearchResult);
                        }
                    }

                    Collections.sort(searchResults, new Comparator<DialogSearchResult>() {
                        @Override
                        public int compare(DialogSearchResult lhs, DialogSearchResult rhs) {
                            if (lhs.date < rhs.date) {
                                return 1;
                            } else if (lhs.date > rhs.date) {
                                return -1;
                            }
                            return 0;
                        }
                    });

                    ArrayList<TLObject> resultArray = new ArrayList<>();
                    ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                    for (int a = 0; a < searchResults.size(); a++) {
                        DialogSearchResult dialogSearchResult = searchResults.get(a);
                        resultArray.add(dialogSearchResult.object);
                        resultArrayNames.add(dialogSearchResult.name);
                    }

                    if (dialogsType != 2) {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid");
                        while (cursor.next()) {
                            int uid = cursor.intValue(3);
                            if (dialogsResult.containsKey((long) uid)) {
                                continue;
                            }
                            String name = cursor.stringValue(2);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            String username = null;
                            int usernamePos = name.lastIndexOf(";;;");
                            if (usernamePos != -1) {
                                username = name.substring(usernamePos + 3);
                            }
                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }
                                if (found != 0) {
                                    NativeByteBuffer data = new NativeByteBuffer(cursor.byteArrayLength(0));
                                    if (data != null && cursor.byteBufferValue(0, data) != 0) {
                                        TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                        if (user.status != null) {
                                            user.status.expires = cursor.intValue(1);
                                        }
                                        if (found == 1) {
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                        } else {
                                            resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q));
                                        }
                                        resultArray.add(user);
                                    }
                                    data.reuse();
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    updateSearchResults(resultArray, resultArrayNames, encUsers, searchId);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    private void updateSearchResults(final ArrayList<TLObject> result, final ArrayList<CharSequence> names, final ArrayList<TLRPC.User> encUsers, final int searchId) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (searchId != lastSearchId) {
                    return;
                }
                for (TLObject obj : result) {
                    if (obj instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) obj;
                        MessagesController.getInstance().putUser(user, true);
                    } else if (obj instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) obj;
                        MessagesController.getInstance().putChat(chat, true);
                    } else if (obj instanceof TLRPC.EncryptedChat) {
                        TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) obj;
                        MessagesController.getInstance().putEncryptedChat(chat, true);
                    }
                }
                for (TLRPC.User user : encUsers) {
                    MessagesController.getInstance().putUser(user, true);
                }
                searchResult = result;
                searchResultNames = names;
                notifyDataSetChanged();
            }
        });
    }

    public boolean isGlobalSearch(int i) {
        return i > searchResult.size() && i <= globalSearch.size() + searchResult.size();
    }

    @Override
    public void clearRecentHashtags() {
        super.clearRecentHashtags();
        searchResultHashtags.clear();
        notifyDataSetChanged();
    }

    @Override
    protected void setHashtags(ArrayList<HashtagObject> arrayList, HashMap<String, HashtagObject> hashMap) {
        super.setHashtags(arrayList, hashMap);
        for (HashtagObject hashtagObject : arrayList) {
            searchResultHashtags.add(hashtagObject.hashtag);
        }
        if (delegate != null) {
            delegate.searchStateChanged(false);
        }
        notifyDataSetChanged();
    }

    public void searchDialogs(final String query) {
        if (query != null && lastSearchText != null && query.equals(lastSearchText)) {
            return;
        }
        lastSearchText = query;
        try {
            if (searchTimer != null) {
                searchTimer.cancel();
                searchTimer = null;
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        if (query == null || query.length() == 0) {
            hashtagsLoadedFromDb = false;
            searchResult.clear();
            searchResultNames.clear();
            searchResultHashtags.clear();
            if (needMessagesSearch != 2) {
                queryServerSearch(null, true);
            }
            searchMessagesInternal(null);
            notifyDataSetChanged();
        } else {
            if (needMessagesSearch != 2 && (query.startsWith("#") && query.length() == 1)) {
                messagesSearchEndReached = true;
                if (!hashtagsLoadedFromDb) {
                    loadRecentHashtags();
                    if (delegate != null) {
                        delegate.searchStateChanged(true);
                    }
                    notifyDataSetChanged();
                    return;
                }
                searchResultMessages.clear();
                searchResultHashtags.clear();
                for (HashtagObject hashtagObject : hashtags) {
                    searchResultHashtags.add(hashtagObject.hashtag);
                }
                if (delegate != null) {
                    delegate.searchStateChanged(false);
                }
                notifyDataSetChanged();
                return;
            } else {
                searchResultHashtags.clear();
            }
            final int searchId = ++lastSearchId;
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        cancel();
                        searchTimer.cancel();
                        searchTimer = null;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    searchDialogsInternal(query, searchId);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (needMessagesSearch != 2) {
                                queryServerSearch(query, true);
                            }
                            searchMessagesInternal(query);
                        }
                    });
                }
            }, 200, 300);
        }
    }

    @Override
    public int getItemCount() {
        if (needMessagesSearch != 2 && (lastSearchText == null || lastSearchText.length() == 0) && !recentSearchObjects.isEmpty()) {
            return recentSearchObjects.size() + 1;
        }
        if (!searchResultHashtags.isEmpty()) {
            return searchResultHashtags.size() + 1;
        }
        int count = searchResult.size();
        int globalCount = globalSearch.size();
        int messagesCount = searchResultMessages.size();
        if (globalCount != 0) {
            count += globalCount + 1;
        }
        if (messagesCount != 0) {
            count += messagesCount + 1 + (messagesSearchEndReached ? 0 : 1);
        }
        return count;
    }

    public Object getItem(int i) {
        if (needMessagesSearch != 2 && (lastSearchText == null || lastSearchText.length() == 0) && !recentSearchObjects.isEmpty()) {
            if (i > 0 && i - 1 < recentSearchObjects.size()) {
                TLObject object = recentSearchObjects.get(i - 1).object;
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = MessagesController.getInstance().getUser(((TLRPC.User) object).id);
                    if (user != null) {
                        object = user;
                    }
                } else if (object instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = MessagesController.getInstance().getChat(((TLRPC.Chat) object).id);
                    if (chat != null) {
                        object = chat;
                    }
                }
                return object;
            } else {
                return null;
            }
        }
        if (!searchResultHashtags.isEmpty()) {
            if (i > 0) {
                return searchResultHashtags.get(i - 1);
            } else {
                return null;
            }
        }
        int localCount = searchResult.size();
        int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
        int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
        if (i >= 0 && i < localCount) {
            return searchResult.get(i);
        } else if (i > localCount && i < globalCount + localCount) {
            return globalSearch.get(i - localCount - 1);
        } else if (i > globalCount + localCount && i < globalCount + localCount + messagesCount) {
            return searchResultMessages.get(i - localCount - globalCount - 1);
        }
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        switch (viewType) {
            case 0:
                view = new ProfileSearchCell(mContext);
                view.setBackgroundResource(R.drawable.list_selector);
                break;
            case 1:
                view = new GreySectionCell(mContext);
                break;
            case 2:
                view = new DialogCell(mContext);
                break;
            case 3:
                view = new LoadingCell(mContext);
                break;
            case 4:
                view = new HashtagSearchCell(mContext);
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0: {
                ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;

                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                TLRPC.EncryptedChat encryptedChat = null;
                CharSequence username = null;
                CharSequence name = null;
                boolean isRecent = false;
                String un = null;
                Object obj = getItem(position);

                if (obj instanceof TLRPC.User) {
                    user = (TLRPC.User) obj;
                    un = user.username;
                } else if (obj instanceof TLRPC.Chat) {
                    chat = MessagesController.getInstance().getChat(((TLRPC.Chat) obj).id);
                    if (chat == null) {
                        chat = (TLRPC.Chat) obj;
                    }
                    un = chat.username;
                } else if (obj instanceof TLRPC.EncryptedChat) {
                    encryptedChat = MessagesController.getInstance().getEncryptedChat(((TLRPC.EncryptedChat) obj).id);
                    user = MessagesController.getInstance().getUser(encryptedChat.user_id);
                }

                if (needMessagesSearch != 2 && (lastSearchText == null || lastSearchText.length() == 0) && !recentSearchObjects.isEmpty()) {
                    isRecent = true;
                    cell.useSeparator = position != getItemCount() - 1;
                } else {
                    int localCount = searchResult.size();
                    int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
                    cell.useSeparator = (position != getItemCount() - 1 && position != localCount - 1 && position != localCount + globalCount - 1);

                    if (position < searchResult.size()) {
                        name = searchResultNames.get(position);
                        if (name != null && user != null && user.username != null && user.username.length() > 0) {
                            if (name.toString().startsWith("@" + user.username)) {
                                username = name;
                                name = null;
                            }
                        }
                    } else if (position > searchResult.size() && un != null) {
                        String foundUserName = lastFoundUsername;
                        if (foundUserName.startsWith("@")) {
                            foundUserName = foundUserName.substring(1);
                        }
                        try {
                            username = AndroidUtilities.replaceTags(String.format("<c#ff4d83b3>@%s</c>%s", un.substring(0, foundUserName.length()), un.substring(foundUserName.length())));
                        } catch (Exception e) {
                            username = un;
                            FileLog.e("tmessages", e);
                        }
                    }
                }
                cell.setData(user != null ? user : chat, encryptedChat, name, username, isRecent);
                break;
            }
            case 1: {
                GreySectionCell cell = (GreySectionCell) holder.itemView;
                if (needMessagesSearch != 2 && (lastSearchText == null || lastSearchText.length() == 0) && !recentSearchObjects.isEmpty()) {
                    cell.setText(LocaleController.getString("Recent", R.string.Recent).toUpperCase());
                } else if (!searchResultHashtags.isEmpty()) {
                    cell.setText(LocaleController.getString("Hashtags", R.string.Hashtags).toUpperCase());
                } else if (!globalSearch.isEmpty() && position == searchResult.size()) {
                    cell.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                } else {
                    cell.setText(LocaleController.getString("SearchMessages", R.string.SearchMessages));
                }
                break;
            }
            case 2: {
                DialogCell cell = (DialogCell) holder.itemView;
                cell.useSeparator = (position != getItemCount() - 1);
                MessageObject messageObject = (MessageObject)getItem(position);
                cell.setDialog(messageObject.getDialogId(), messageObject, messageObject.messageOwner.date);
                break;
            }
            case 3: {
                break;
            }
            case 4: {
                HashtagSearchCell cell = (HashtagSearchCell) holder.itemView;
                cell.setText(searchResultHashtags.get(position - 1));
                cell.setNeedDivider(position != searchResultHashtags.size());
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (needMessagesSearch != 2 && (lastSearchText == null || lastSearchText.length() == 0) && !recentSearchObjects.isEmpty()) {
            return i == 0 ? 1 : 0;
        }
        if (!searchResultHashtags.isEmpty()) {
            return i == 0 ? 1 : 4;
        }
        int localCount = searchResult.size();
        int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
        int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
        if (i >= 0 && i < localCount || i > localCount && i < globalCount + localCount) {
            return 0;
        } else if (i > globalCount + localCount && i < globalCount + localCount + messagesCount) {
            return 2;
        } else if (messagesCount != 0 && i == globalCount + localCount + messagesCount) {
            return 3;
        }
        return 1;
    }
}
