/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.MotionEvent;
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
import org.telegram.messenger.query.SearchQuery;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HashtagSearchCell;
import org.telegram.ui.Cells.HintDialogCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class DialogsSearchAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private Timer searchTimer;
    private ArrayList<TLObject> searchResult = new ArrayList<>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
    private ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private ArrayList<String> searchResultHashtags = new ArrayList<>();
    private String lastSearchText;
    private int reqId = 0;
    private int lastReqId;
    private DialogsSearchAdapterDelegate delegate;
    private int needMessagesSearch;
    private boolean messagesSearchEndReached;
    private String lastMessagesSearchString;
    private int lastSearchId = 0;
    private int dialogsType;
    private SearchAdapterHelper searchAdapterHelper;
    private RecyclerListView innerListView;

    private ArrayList<RecentSearchObject> recentSearchObjects = new ArrayList<>();
    private HashMap<Long, RecentSearchObject> recentSearchObjectsById = new HashMap<>();

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

    public interface DialogsSearchAdapterDelegate {
        void searchStateChanged(boolean searching);
        void didPressedOnSubDialog(int did);
        void needRemoveHint(int did);
    }

    private class CategoryAdapterRecycler extends RecyclerListView.SelectionAdapter {

        public void setIndex(int value) {
            notifyDataSetChanged();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new HintDialogCell(mContext);
            view.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(80), AndroidUtilities.dp(100)));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            HintDialogCell cell = (HintDialogCell) holder.itemView;

            TLRPC.TL_topPeer peer = SearchQuery.hints.get(position);
            TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
            TLRPC.Chat chat = null;
            TLRPC.User user = null;
            int did = 0;
            if (peer.peer.user_id != 0) {
                did = peer.peer.user_id;
                user = MessagesController.getInstance().getUser(peer.peer.user_id);
            } else if (peer.peer.channel_id != 0) {
                did = -peer.peer.channel_id;
                chat = MessagesController.getInstance().getChat(peer.peer.channel_id);
            } else if (peer.peer.chat_id != 0) {
                did = -peer.peer.chat_id;
                chat = MessagesController.getInstance().getChat(peer.peer.chat_id);
            }
            cell.setTag(did);
            String name = "";
            if (user != null) {
                name = ContactsController.formatName(user.first_name, user.last_name);
            } else if (chat != null) {
                name = chat.title;
            }
            cell.setDialog(did, true, name);
        }

        @Override
        public int getItemCount() {
            return SearchQuery.hints.size();
        }
    }

    public DialogsSearchAdapter(Context context, int messagesSearch, int type) {
        searchAdapterHelper = new SearchAdapterHelper();
        searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
            @Override
            public void onDataSetChanged() {
                notifyDataSetChanged();
            }

            @Override
            public void onSetHashtags(ArrayList<SearchAdapterHelper.HashtagObject> arrayList, HashMap<String, SearchAdapterHelper.HashtagObject> hashMap) {
                for (int a = 0; a < arrayList.size(); a++) {
                    searchResultHashtags.add(arrayList.get(a).hashtag);
                }
                if (delegate != null) {
                    delegate.searchStateChanged(false);
                }
                notifyDataSetChanged();
            }
        });
        mContext = context;
        needMessagesSearch = messagesSearch;
        dialogsType = type;
        loadRecentSearch();
        SearchQuery.loadHints(true);
    }

    public RecyclerListView getInnerListView() {
        return innerListView;
    }

    public void setDelegate(DialogsSearchAdapterDelegate delegate) {
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
                                for (int a = 0; a < res.messages.size(); a++) {
                                    TLRPC.Message message = res.messages.get(a);
                                    searchResultMessages.add(new MessageObject(message, null, false));
                                    long dialog_id = MessageObject.getDialogId(message);
                                    ConcurrentHashMap<Long, Integer> read_max = message.out ? MessagesController.getInstance().dialogs_read_outbox_max : MessagesController.getInstance().dialogs_read_inbox_max;
                                    Integer value = read_max.get(dialog_id);
                                    if (value == null) {
                                        value = MessagesStorage.getInstance().getDialogReadMax(message.out, dialog_id);
                                        read_max.put(dialog_id, value);
                                    }
                                    message.unread = value < message.id;
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
        return !recentSearchObjects.isEmpty() || !SearchQuery.hints.isEmpty();
    }

    public boolean isRecentSearchDisplayed() {
        return needMessagesSearch != 2 && (lastSearchText == null || lastSearchText.length() == 0) && (!recentSearchObjects.isEmpty() || !SearchQuery.hints.isEmpty());
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
                    FileLog.e(e);
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
                    FileLog.e(e);
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
                    FileLog.e(e);
                }
            }
        });
    }

    public void addHashtagsFromMessage(CharSequence message) {
        searchAdapterHelper.addHashtagsFromMessage(message);
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
                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 600");
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
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
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
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
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
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    if (!encryptedToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT q.data, u.name, q.user, q.g, q.authkey, q.ttl, u.data, u.status, q.layer, q.seq_in, q.seq_out, q.use_count, q.exchange_id, q.key_date, q.fprint, q.fauthkey, q.khash, q.in_seq_no FROM enc_chats as q INNER JOIN users as u ON q.user = u.uid WHERE q.uid IN(%s)", TextUtils.join(",", encryptedToLoad)));
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
                            for (int a = 0; a < search.length; a++) {
                                String q = search[a];
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    TLRPC.EncryptedChat chat = null;
                                    TLRPC.User user = null;
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        chat = TLRPC.EncryptedChat.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
                                    }
                                    data = cursor.byteBufferValue(6);
                                    if (data != null) {
                                        user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
                                    }
                                    if (chat != null && user != null) {
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
                                        chat.in_seq_no = cursor.intValue(17);

                                        if (user.status != null) {
                                            user.status.expires = cursor.intValue(7);
                                        }
                                        if (found == 1) {
                                            dialogSearchResult.name = new SpannableStringBuilder(ContactsController.formatName(user.first_name, user.last_name));
                                            ((SpannableStringBuilder) dialogSearchResult.name).setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_chats_secretName)), 0, dialogSearchResult.name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        } else {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q);
                                        }
                                        dialogSearchResult.object = chat;
                                        encUsers.add(user);
                                        resultCount++;
                                    }
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
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
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
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    updateSearchResults(resultArray, resultArrayNames, encUsers, searchId);
                } catch (Exception e) {
                    FileLog.e(e);
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
                for (int a = 0; a < result.size(); a++) {
                    TLObject obj = result.get(a);
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
                MessagesController.getInstance().putUsers(encUsers, true);
                searchResult = result;
                searchResultNames = names;
                notifyDataSetChanged();
            }
        });
    }

    public boolean isGlobalSearch(int i) {
        return i > searchResult.size() && i <= searchAdapterHelper.getGlobalSearch().size() + searchResult.size();
    }

    public void clearRecentHashtags() {
        searchAdapterHelper.clearRecentHashtags();
        searchResultHashtags.clear();
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
            FileLog.e(e);
        }
        if (query == null || query.length() == 0) {
            searchAdapterHelper.unloadRecentHashtags();
            searchResult.clear();
            searchResultNames.clear();
            searchResultHashtags.clear();
            if (needMessagesSearch != 2) {
                searchAdapterHelper.queryServerSearch(null, true, true, true, true, 0, false);
            }
            searchMessagesInternal(null);
            notifyDataSetChanged();
        } else {
            if (needMessagesSearch != 2 && (query.startsWith("#") && query.length() == 1)) {
                messagesSearchEndReached = true;
                if (searchAdapterHelper.loadRecentHashtags()) {
                    searchResultMessages.clear();
                    searchResultHashtags.clear();
                    ArrayList<SearchAdapterHelper.HashtagObject> hashtags = searchAdapterHelper.getHashtags();
                    for (int a = 0; a < hashtags.size(); a++) {
                        searchResultHashtags.add(hashtags.get(a).hashtag);
                    }
                    if (delegate != null) {
                        delegate.searchStateChanged(false);
                    }
                } else {
                    if (delegate != null) {
                        delegate.searchStateChanged(true);
                    }
                }
                notifyDataSetChanged();
            } else {
                searchResultHashtags.clear();
                notifyDataSetChanged();
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
                        FileLog.e(e);
                    }
                    searchDialogsInternal(query, searchId);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (needMessagesSearch != 2) {
                                searchAdapterHelper.queryServerSearch(query, true, true, true, true, 0, false);
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
        if (isRecentSearchDisplayed()) {
            return (!recentSearchObjects.isEmpty() ? recentSearchObjects.size() + 1 : 0) + (!SearchQuery.hints.isEmpty() ? 2 : 0);
        }
        if (!searchResultHashtags.isEmpty()) {
            return searchResultHashtags.size() + 1;
        }
        int count = searchResult.size();
        int globalCount = searchAdapterHelper.getGlobalSearch().size();
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
        if (isRecentSearchDisplayed()) {
            int offset = (!SearchQuery.hints.isEmpty() ? 2 : 0);
            if (i > offset && i - 1 - offset < recentSearchObjects.size()) {
                TLObject object = recentSearchObjects.get(i - 1 - offset).object;
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
        ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
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
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int type = holder.getItemViewType();
        return type != 1 && type != 3;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        switch (viewType) {
            case 0:
                view = new ProfileSearchCell(mContext);
                break;
            case 1:
                view = new GraySectionCell(mContext);
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
            case 5:
                RecyclerListView horizontalListView = new RecyclerListView(mContext) {
                    @Override
                    public boolean onInterceptTouchEvent(MotionEvent e) {
                        if (getParent() != null && getParent().getParent() != null) {
                            getParent().getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return super.onInterceptTouchEvent(e);
                    }
                };
                horizontalListView.setTag(9);
                horizontalListView.setItemAnimator(null);
                horizontalListView.setLayoutAnimation(null);
                LinearLayoutManager layoutManager = new LinearLayoutManager(mContext) {
                    @Override
                    public boolean supportsPredictiveItemAnimations() {
                        return false;
                    }
                };
                layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
                horizontalListView.setLayoutManager(layoutManager);
                //horizontalListView.setDisallowInterceptTouchEvents(true);
                horizontalListView.setAdapter(new CategoryAdapterRecycler());
                horizontalListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        if (delegate != null) {
                            delegate.didPressedOnSubDialog((Integer) view.getTag());
                        }
                    }
                });
                horizontalListView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position) {
                        if (delegate != null) {
                            delegate.needRemoveHint((Integer) view.getTag());
                        }
                        return true;
                    }
                });
                view = horizontalListView;
                innerListView = horizontalListView;
        }
        if (viewType == 5) {
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(100)));
        } else {
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        }
        return new RecyclerListView.Holder(view);
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

                if (isRecentSearchDisplayed()) {
                    isRecent = true;
                    cell.useSeparator = position != getItemCount() - 1;
                } else {
                    ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
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
                        String foundUserName = searchAdapterHelper.getLastFoundUsername();
                        if (foundUserName.startsWith("@")) {
                            foundUserName = foundUserName.substring(1);
                        }
                        try {
                            username = new SpannableStringBuilder(un);
                            ((SpannableStringBuilder) username).setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), 0, foundUserName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } catch (Exception e) {
                            username = un;
                            FileLog.e(e);
                        }
                    }
                }
                cell.setData(user != null ? user : chat, encryptedChat, name, username, isRecent);
                break;
            }
            case 1: {
                GraySectionCell cell = (GraySectionCell) holder.itemView;
                if (isRecentSearchDisplayed()) {
                    int offset = (!SearchQuery.hints.isEmpty() ? 2 : 0);
                    if (position < offset) {
                        cell.setText(LocaleController.getString("ChatHints", R.string.ChatHints).toUpperCase());
                    } else {
                        cell.setText(LocaleController.getString("Recent", R.string.Recent).toUpperCase());
                    }
                } else if (!searchResultHashtags.isEmpty()) {
                    cell.setText(LocaleController.getString("Hashtags", R.string.Hashtags).toUpperCase());
                } else if (!searchAdapterHelper.getGlobalSearch().isEmpty() && position == searchResult.size()) {
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
            case 5: {
                RecyclerListView recyclerListView = (RecyclerListView) holder.itemView;
                ((CategoryAdapterRecycler) recyclerListView.getAdapter()).setIndex(position / 2);
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (isRecentSearchDisplayed()) {
            int offset = (!SearchQuery.hints.isEmpty() ? 2 : 0);
            if (i <= offset) {
                if (i == offset || i % 2 == 0) {
                    return 1;
                } else {
                    return 5;
                }
            }
            return 0;
        }
        if (!searchResultHashtags.isEmpty()) {
            return i == 0 ? 1 : 4;
        }
        ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
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
