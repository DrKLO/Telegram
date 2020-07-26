/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.LongSparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HashtagSearchCell;
import org.telegram.ui.Cells.HintDialogCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class DialogsSearchAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private Runnable searchRunnable;
    private Runnable searchRunnable2;
    private ArrayList<TLObject> searchResult = new ArrayList<>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
    private ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private ArrayList<String> searchResultHashtags = new ArrayList<>();
    private String lastSearchText;
    private boolean searchWas;
    private int reqId = 0;
    private int lastReqId;
    private DialogsSearchAdapterDelegate delegate;
    private int needMessagesSearch;
    private boolean messagesSearchEndReached;
    private String lastMessagesSearchString;
    private int nextSearchRate;
    private int lastSearchId;
    private int lastGlobalSearchId;
    private int lastLocalSearchId;
    private int lastMessagesSearchId;
    private int dialogsType;
    private SearchAdapterHelper searchAdapterHelper;
    private RecyclerListView innerListView;
    private int selfUserId;

    private int currentAccount = UserConfig.selectedAccount;

    private ArrayList<RecentSearchObject> recentSearchObjects = new ArrayList<>();
    private LongSparseArray<RecentSearchObject> recentSearchObjectsById = new LongSparseArray<>();

    private static class DialogSearchResult {
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

        void didPressedOnSubDialog(long did);

        void needRemoveHint(int did);

        void needClearList();
    }

    private class CategoryAdapterRecycler extends RecyclerListView.SelectionAdapter {

        public void setIndex(int value) {
            notifyDataSetChanged();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new HintDialogCell(mContext);
            view.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(80), AndroidUtilities.dp(86)));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            HintDialogCell cell = (HintDialogCell) holder.itemView;

            TLRPC.TL_topPeer peer = MediaDataController.getInstance(currentAccount).hints.get(position);
            TLRPC.Dialog dialog = new TLRPC.TL_dialog();
            TLRPC.Chat chat = null;
            TLRPC.User user = null;
            int did = 0;
            if (peer.peer.user_id != 0) {
                did = peer.peer.user_id;
                user = MessagesController.getInstance(currentAccount).getUser(peer.peer.user_id);
            } else if (peer.peer.channel_id != 0) {
                did = -peer.peer.channel_id;
                chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.channel_id);
            } else if (peer.peer.chat_id != 0) {
                did = -peer.peer.chat_id;
                chat = MessagesController.getInstance(currentAccount).getChat(peer.peer.chat_id);
            }
            cell.setTag(did);
            String name = "";
            if (user != null) {
                name = UserObject.getFirstName(user);
            } else if (chat != null) {
                name = chat.title;
            }
            cell.setDialog(did, true, name);
        }

        @Override
        public int getItemCount() {
            return MediaDataController.getInstance(currentAccount).hints.size();
        }
    }

    public DialogsSearchAdapter(Context context, int messagesSearch, int type) {
        searchAdapterHelper = new SearchAdapterHelper(false);
        searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
            @Override
            public void onDataSetChanged(int searchId) {
                lastGlobalSearchId = searchId;
                if (lastLocalSearchId != searchId) {
                    searchResult.clear();
                }
                if (lastMessagesSearchId != searchId) {
                    searchResultMessages.clear();
                }
                searchWas = true;
                if (!searchAdapterHelper.isSearchInProgress() && delegate != null && reqId == 0) {
                    delegate.searchStateChanged(false);
                }
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

            @Override
            public boolean canApplySearchResults(int searchId) {
                return searchId == lastSearchId;
            }
        });
        mContext = context;
        needMessagesSearch = messagesSearch;
        dialogsType = type;
        selfUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        loadRecentSearch();
        MediaDataController.getInstance(currentAccount).loadHints(true);
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
        if (reqId != 0) {
            return;
        }
        searchMessagesInternal(lastMessagesSearchString, lastMessagesSearchId);
    }

    public String getLastSearchString() {
        return lastMessagesSearchString;
    }

    private void searchMessagesInternal(final String query, int searchId) {
        if (needMessagesSearch == 0 || TextUtils.isEmpty(lastMessagesSearchString) && TextUtils.isEmpty(query)) {
            return;
        }
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        if (TextUtils.isEmpty(query)) {
            searchResultMessages.clear();
            lastReqId = 0;
            lastMessagesSearchString = null;
            searchWas = false;
            notifyDataSetChanged();
            if (delegate != null) {
                delegate.searchStateChanged(false);
            }
            return;
        }

        final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.limit = 20;
        req.q = query;
        if (query.equals(lastMessagesSearchString) && !searchResultMessages.isEmpty()) {
            MessageObject lastMessage = searchResultMessages.get(searchResultMessages.size() - 1);
            req.offset_id = lastMessage.getId();
            req.offset_rate = nextSearchRate;
            int id;
            if (lastMessage.messageOwner.to_id.channel_id != 0) {
                id = -lastMessage.messageOwner.to_id.channel_id;
            } else if (lastMessage.messageOwner.to_id.chat_id != 0) {
                id = -lastMessage.messageOwner.to_id.chat_id;
            } else {
                id = lastMessage.messageOwner.to_id.user_id;
            }
            req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(id);
        } else {
            req.offset_rate = 0;
            req.offset_id = 0;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        lastMessagesSearchString = query;
        final int currentReqId = ++lastReqId;
        /*if (delegate != null) {
            delegate.searchStateChanged(true);
        }*/
        reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (currentReqId == lastReqId && (searchId <= 0 || searchId == lastSearchId)) {
                if (error == null) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    if (req.offset_id == 0) {
                        searchResultMessages.clear();
                    }
                    nextSearchRate = res.next_rate;
                    for (int a = 0; a < res.messages.size(); a++) {
                        TLRPC.Message message = res.messages.get(a);
                        long did = MessageObject.getDialogId(message);
                        Integer maxId = MessagesController.getInstance(currentAccount).deletedHistory.get(did);
                        if (maxId != null && message.id <= maxId) {
                            continue;
                        }
                        searchResultMessages.add(new MessageObject(currentAccount, message, false));
                        long dialog_id = MessageObject.getDialogId(message);
                        ConcurrentHashMap<Long, Integer> read_max = message.out ? MessagesController.getInstance(currentAccount).dialogs_read_outbox_max : MessagesController.getInstance(currentAccount).dialogs_read_inbox_max;
                        Integer value = read_max.get(dialog_id);
                        if (value == null) {
                            value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, dialog_id);
                            read_max.put(dialog_id, value);
                        }
                        message.unread = value < message.id;
                    }
                    searchWas = true;
                    messagesSearchEndReached = res.messages.size() != 20;
                    if (searchId > 0) {
                        lastMessagesSearchId = searchId;
                        if (lastLocalSearchId != searchId) {
                            searchResult.clear();
                        }
                        if (lastGlobalSearchId != searchId) {
                            searchAdapterHelper.clear();
                        }
                    }
                    notifyDataSetChanged();
                }
            }
            reqId = 0;
            if (!searchAdapterHelper.isSearchInProgress() && delegate != null) {
                delegate.searchStateChanged(false);
            }
        }), ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public boolean hasRecentRearch() {
        return dialogsType != 2 && dialogsType != 4 && dialogsType != 5 && dialogsType != 6 && (!recentSearchObjects.isEmpty() || !MediaDataController.getInstance(currentAccount).hints.isEmpty());
    }

    public boolean isRecentSearchDisplayed() {
        return needMessagesSearch != 2 && !searchWas && (!recentSearchObjects.isEmpty() || !MediaDataController.getInstance(currentAccount).hints.isEmpty()) && dialogsType != 2 && dialogsType != 4 && dialogsType != 5 && dialogsType != 6;
    }

    public void loadRecentSearch() {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT did, date FROM search_recent WHERE 1");

                ArrayList<Integer> usersToLoad = new ArrayList<>();
                ArrayList<Integer> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                ArrayList<TLRPC.User> encUsers = new ArrayList<>();

                final ArrayList<RecentSearchObject> arrayList = new ArrayList<>();
                final LongSparseArray<RecentSearchObject> hashMap = new LongSparseArray<>();
                while (cursor.next()) {
                    long did = cursor.longValue(0);

                    boolean add = false;
                    int lower_id = (int) did;
                    int high_id = (int) (did >> 32);
                    if (lower_id != 0) {
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
                    } else if (dialogsType == 0 || dialogsType == 3) {
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
                    MessagesStorage.getInstance(currentAccount).getEncryptedChatsInternal(TextUtils.join(",", encryptedToLoad), encryptedChats, usersToLoad);
                    for (int a = 0; a < encryptedChats.size(); a++) {
                        hashMap.get((long) encryptedChats.get(a).id << 32).object = encryptedChats.get(a);
                    }
                }

                if (!chatsToLoad.isEmpty()) {
                    ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                    MessagesStorage.getInstance(currentAccount).getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    for (int a = 0; a < chats.size(); a++) {
                        TLRPC.Chat chat = chats.get(a);
                        long did = -chat.id;
                        if (chat.migrated_to != null) {
                            RecentSearchObject recentSearchObject = hashMap.get(did);
                            hashMap.remove(did);
                            if (recentSearchObject != null) {
                                arrayList.remove(recentSearchObject);
                            }
                        } else {
                            hashMap.get(did).object = chat;
                        }
                    }
                }

                if (!usersToLoad.isEmpty()) {
                    MessagesStorage.getInstance(currentAccount).getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    for (int a = 0; a < users.size(); a++) {
                        TLRPC.User user = users.get(a);
                        RecentSearchObject recentSearchObject = hashMap.get(user.id);
                        if (recentSearchObject != null) {
                            recentSearchObject.object = user;
                        }
                    }
                }

                Collections.sort(arrayList, (lhs, rhs) -> {
                    if (lhs.date < rhs.date) {
                        return 1;
                    } else if (lhs.date > rhs.date) {
                        return -1;
                    } else {
                        return 0;
                    }
                });
                AndroidUtilities.runOnUIThread(() -> setRecentSearch(arrayList, hashMap));
            } catch (Exception e) {
                FileLog.e(e);
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
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO search_recent VALUES(?, ?)");
                state.requery();
                state.bindLong(1, did);
                state.bindInteger(2, (int) (System.currentTimeMillis() / 1000));
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void clearRecentSearch() {
        recentSearchObjectsById = new LongSparseArray<>();
        recentSearchObjects = new ArrayList<>();
        notifyDataSetChanged();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM search_recent WHERE 1").stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void removeRecentSearch(long did) {
        RecentSearchObject object = recentSearchObjectsById.get(did);
        if (object == null) {
            return;
        }
        recentSearchObjectsById.remove(did);
        recentSearchObjects.remove(object);
        notifyDataSetChanged();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM search_recent WHERE did = " + did).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void addHashtagsFromMessage(CharSequence message) {
        searchAdapterHelper.addHashtagsFromMessage(message);
    }

    private void setRecentSearch(ArrayList<RecentSearchObject> arrayList, LongSparseArray<RecentSearchObject> hashMap) {
        recentSearchObjects = arrayList;
        recentSearchObjectsById = hashMap;
        for (int a = 0; a < recentSearchObjects.size(); a++) {
            RecentSearchObject recentSearchObject = recentSearchObjects.get(a);
            if (recentSearchObject.object instanceof TLRPC.User) {
                MessagesController.getInstance(currentAccount).putUser((TLRPC.User) recentSearchObject.object, true);
            } else if (recentSearchObject.object instanceof TLRPC.Chat) {
                MessagesController.getInstance(currentAccount).putChat((TLRPC.Chat) recentSearchObject.object, true);
            } else if (recentSearchObject.object instanceof TLRPC.EncryptedChat) {
                MessagesController.getInstance(currentAccount).putEncryptedChat((TLRPC.EncryptedChat) recentSearchObject.object, true);
            }
        }
        notifyDataSetChanged();
    }

    private void searchDialogsInternal(final String query, final int searchId) {
        if (needMessagesSearch == 2) {
            return;
        }
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                String savedMessages = LocaleController.getString("SavedMessages", R.string.SavedMessages).toLowerCase();
                String search1 = query.trim().toLowerCase();
                if (search1.length() == 0) {
                    lastSearchId = 0;
                    updateSearchResults(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), lastSearchId);
                    return;
                }
                String search2 = LocaleController.getInstance().getTranslitString(search1);
                if (search1.equals(search2) || search2.length() == 0) {
                    search2 = null;
                }
                String[] search = new String[1 + (search2 != null ? 1 : 0)];
                search[0] = search1;
                if (search2 != null) {
                    search[1] = search2;
                }

                ArrayList<Integer> usersToLoad = new ArrayList<>();
                ArrayList<Integer> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                ArrayList<TLRPC.User> encUsers = new ArrayList<>();
                int resultCount = 0;

                LongSparseArray<DialogSearchResult> dialogsResult = new LongSparseArray<>();
                SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 600");
                while (cursor.next()) {
                    long id = cursor.longValue(0);
                    DialogSearchResult dialogSearchResult = new DialogSearchResult();
                    dialogSearchResult.date = cursor.intValue(1);
                    dialogsResult.put(id, dialogSearchResult);

                    int lower_id = (int) id;
                    int high_id = (int) (id >> 32);
                    if (lower_id != 0) {
                        if (lower_id > 0) {
                            if (dialogsType == 4 && lower_id == selfUserId) {
                                continue;
                            }
                            if (dialogsType != 2 && !usersToLoad.contains(lower_id)) {
                                usersToLoad.add(lower_id);
                            }
                        } else {
                            if (dialogsType == 4) {
                                continue;
                            }
                            if (!chatsToLoad.contains(-lower_id)) {
                                chatsToLoad.add(-lower_id);
                            }
                        }
                    } else if (dialogsType == 0 || dialogsType == 3) {
                        if (!encryptedToLoad.contains(high_id)) {
                            encryptedToLoad.add(high_id);
                        }
                    }
                }
                cursor.dispose();

                if (dialogsType != 4 && savedMessages.startsWith(search1)) {
                    TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
                    DialogSearchResult dialogSearchResult = new DialogSearchResult();
                    dialogSearchResult.date = Integer.MAX_VALUE;
                    dialogSearchResult.name = savedMessages;
                    dialogSearchResult.object = user;
                    dialogsResult.put(user.id, dialogSearchResult);
                    resultCount++;
                }

                if (!usersToLoad.isEmpty()) {
                    cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, status, name FROM users WHERE uid IN(%s)", TextUtils.join(",", usersToLoad)));
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
                                    DialogSearchResult dialogSearchResult = dialogsResult.get(user.id);
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
                    cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, name FROM chats WHERE uid IN(%s)", TextUtils.join(",", chatsToLoad)));
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
                                        long dialog_id = -chat.id;
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
                    cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT q.data, u.name, q.user, q.g, q.authkey, q.ttl, u.data, u.status, q.layer, q.seq_in, q.seq_out, q.use_count, q.exchange_id, q.key_date, q.fprint, q.fauthkey, q.khash, q.in_seq_no, q.admin_id, q.mtproto_seq FROM enc_chats as q INNER JOIN users as u ON q.user = u.uid WHERE q.uid IN(%s)", TextUtils.join(",", encryptedToLoad)));
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
                                    int admin_id = cursor.intValue(18);
                                    if (admin_id != 0) {
                                        chat.admin_id = admin_id;
                                    }
                                    chat.mtproto_seq = cursor.intValue(19);

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
                for (int a = 0; a < dialogsResult.size(); a++) {
                    DialogSearchResult dialogSearchResult = dialogsResult.valueAt(a);
                    if (dialogSearchResult.object != null && dialogSearchResult.name != null) {
                        searchResults.add(dialogSearchResult);
                    }
                }

                Collections.sort(searchResults, (lhs, rhs) -> {
                    if (lhs.date < rhs.date) {
                        return 1;
                    } else if (lhs.date > rhs.date) {
                        return -1;
                    }
                    return 0;
                });

                ArrayList<TLObject> resultArray = new ArrayList<>();
                ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                for (int a = 0; a < searchResults.size(); a++) {
                    DialogSearchResult dialogSearchResult = searchResults.get(a);
                    resultArray.add(dialogSearchResult.object);
                    resultArrayNames.add(dialogSearchResult.name);
                }

                if (dialogsType != 2) {
                    cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid");
                    while (cursor.next()) {
                        int uid = cursor.intValue(3);
                        if (dialogsResult.indexOfKey(uid) >= 0) {
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
        });
    }

    private void updateSearchResults(final ArrayList<TLObject> result, final ArrayList<CharSequence> names, final ArrayList<TLRPC.User> encUsers, final int searchId) {
        AndroidUtilities.runOnUIThread(() -> {
            if (searchId != lastSearchId) {
                return;
            }
            lastLocalSearchId = searchId;
            if (lastGlobalSearchId != searchId) {
                searchAdapterHelper.clear();
            }
            if (lastMessagesSearchId != searchId) {
                searchResultMessages.clear();
            }
            searchWas = true;
            for (int a = 0; a < result.size(); a++) {
                TLObject obj = result.get(a);
                if (obj instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) obj;
                    MessagesController.getInstance(currentAccount).putUser(user, true);
                } else if (obj instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) obj;
                    MessagesController.getInstance(currentAccount).putChat(chat, true);
                } else if (obj instanceof TLRPC.EncryptedChat) {
                    TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) obj;
                    MessagesController.getInstance(currentAccount).putEncryptedChat(chat, true);
                }
            }
            MessagesController.getInstance(currentAccount).putUsers(encUsers, true);
            searchResult = result;
            searchResultNames = names;
            searchAdapterHelper.mergeResults(searchResult);
            notifyDataSetChanged();
            if (delegate != null) {
                if (getItemCount() == 0 && (searchRunnable2 != null || searchAdapterHelper.isSearchInProgress())) {
                    delegate.searchStateChanged(true);
                } else {
                    delegate.searchStateChanged(false);
                }
            }
        });
    }

    public boolean isHashtagSearch() {
        return !searchResultHashtags.isEmpty();
    }

    public void clearRecentHashtags() {
        searchAdapterHelper.clearRecentHashtags();
        searchResultHashtags.clear();
        notifyDataSetChanged();
    }

    public void searchDialogs(String text) {
        if (text != null && text.equals(lastSearchText)) {
            return;
        }
        lastSearchText = text;
        if (searchRunnable != null) {
            Utilities.searchQueue.cancelRunnable(searchRunnable);
            searchRunnable = null;
        }
        if (searchRunnable2 != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable2);
            searchRunnable2 = null;
        }
        String query;
        if (text != null) {
            query = text.trim();
        } else {
            query = null;
        }
        if (TextUtils.isEmpty(query)) {
            searchAdapterHelper.unloadRecentHashtags();
            searchResult.clear();
            searchResultNames.clear();
            searchResultHashtags.clear();
            searchAdapterHelper.mergeResults(null);
            if (needMessagesSearch != 2) {
                searchAdapterHelper.queryServerSearch(null, true, true, true, true, dialogsType == 2, 0, dialogsType == 0, 0, 0);
            }
            searchWas = false;
            lastSearchId = 0;
            searchMessagesInternal(null, 0);
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
                    /*if (delegate != null) {
                        delegate.searchStateChanged(true);
                    }*/
                }
                notifyDataSetChanged();
            } else {
                searchResultHashtags.clear();
                notifyDataSetChanged();
            }
            final int searchId = ++lastSearchId;
            if (needMessagesSearch != 2 && delegate != null) {
                delegate.searchStateChanged(true);
            }
            Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                searchRunnable = null;
                searchDialogsInternal(query, searchId);
                AndroidUtilities.runOnUIThread(searchRunnable2 = () -> {
                    searchRunnable2 = null;
                    if (searchId != lastSearchId) {
                        return;
                    }
                    if (needMessagesSearch != 2) {
                        searchAdapterHelper.queryServerSearch(query, true, dialogsType != 4, true, dialogsType != 4, dialogsType == 2, 0, dialogsType == 0, 0, searchId);
                    }
                    searchMessagesInternal(text, searchId);
                });
            }, 300);
        }
    }

    @Override
    public int getItemCount() {
        if (isRecentSearchDisplayed()) {
            return (!recentSearchObjects.isEmpty() ? recentSearchObjects.size() + 1 : 0) + (!MediaDataController.getInstance(currentAccount).hints.isEmpty() ? 2 : 0);
        }
        if (!searchResultHashtags.isEmpty()) {
            return searchResultHashtags.size() + 1;
        }
        int count = searchResult.size();
        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
        int globalCount = searchAdapterHelper.getGlobalSearch().size();
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        int messagesCount = searchResultMessages.size();
        count += localServerCount;
        if (globalCount != 0) {
            count += globalCount + 1;
        }
        if (phoneCount != 0) {
            count += phoneCount;
        }
        if (messagesCount != 0) {
            count += messagesCount + 1 + (messagesSearchEndReached ? 0 : 1);
        }
        return count;
    }

    public Object getItem(int i) {
        if (isRecentSearchDisplayed()) {
            int offset = (!MediaDataController.getInstance(currentAccount).hints.isEmpty() ? 2 : 0);
            if (i > offset && i - 1 - offset < recentSearchObjects.size()) {
                TLObject object = recentSearchObjects.get(i - 1 - offset).object;
                if (object instanceof TLRPC.User) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(((TLRPC.User) object).id);
                    if (user != null) {
                        object = user;
                    }
                } else if (object instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(((TLRPC.Chat) object).id);
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
        ArrayList<TLObject> localServerSearch = searchAdapterHelper.getLocalServerSearch();
        ArrayList<Object> phoneSearch = searchAdapterHelper.getPhoneSearch();
        int localCount = searchResult.size();
        int localServerCount = localServerSearch.size();
        int phoneCount = phoneSearch.size();
        int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
        int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
        if (i >= 0 && i < localCount) {
            return searchResult.get(i);
        } else {
            i -= localCount;
            if (i >= 0 && i < localServerCount) {
                return localServerSearch.get(i);
            } else {
                i -= localServerCount;
                if (i >= 0 && i < phoneCount) {
                    return phoneSearch.get(i);
                } else {
                    i -= phoneCount;
                    if (i > 0 && i < globalCount) {
                        return globalSearch.get(i - 1);
                    } else {
                        i -= globalCount;
                        if (i > 0 && i < messagesCount) {
                            return searchResultMessages.get(i - 1);
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean isGlobalSearch(int i) {
        if (isRecentSearchDisplayed()) {
            return false;
        }
        if (!searchResultHashtags.isEmpty()) {
            return false;
        }
        ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
        ArrayList<TLObject> localServerSearch = searchAdapterHelper.getLocalServerSearch();
        int localCount = searchResult.size();
        int localServerCount = localServerSearch.size();
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
        int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;

        if (i >= 0 && i < localCount) {
            return false;
        } else {
            i -= localCount;
            if (i >= 0 && i < localServerCount) {
                return false;
            } else {
                i -= localServerCount;
                if (i > 0 && i < phoneCount) {
                    return false;
                } else {
                    i -= phoneCount;
                    if (i > 0 && i < globalCount) {
                        return true;
                    } else {
                        i -= globalCount;
                        if (i > 0 && i < messagesCount) {
                            return false;
                        }
                    }
                }
            }
        }
        return false;
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
                view = new DialogCell(mContext, false, true);
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
                            getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
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
                horizontalListView.setOnItemClickListener((view1, position) -> {
                    if (delegate != null) {
                        delegate.didPressedOnSubDialog((Integer) view1.getTag());
                    }
                });
                horizontalListView.setOnItemLongClickListener((view12, position) -> {
                    if (delegate != null) {
                        delegate.needRemoveHint((Integer) view12.getTag());
                    }
                    return true;
                });
                view = horizontalListView;
                innerListView = horizontalListView;
                break;
            case 6:
                view = new TextCell(mContext, 16, false);
                break;
        }
        if (viewType == 5) {
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(86)));
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
                    chat = MessagesController.getInstance(currentAccount).getChat(((TLRPC.Chat) obj).id);
                    if (chat == null) {
                        chat = (TLRPC.Chat) obj;
                    }
                    un = chat.username;
                } else if (obj instanceof TLRPC.EncryptedChat) {
                    encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(((TLRPC.EncryptedChat) obj).id);
                    user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                }

                if (isRecentSearchDisplayed()) {
                    isRecent = true;
                    cell.useSeparator = position != getItemCount() - 1;
                } else {
                    ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
                    ArrayList<Object> phoneSearch = searchAdapterHelper.getPhoneSearch();
                    int localCount = searchResult.size();
                    int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
                    int phoneCount = phoneSearch.size();
                    int phoneCount2 = phoneCount;
                    if (phoneCount > 0 && phoneSearch.get(phoneCount - 1) instanceof String) {
                        phoneCount2 -= 2;
                    }
                    int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
                    cell.useSeparator = (position != getItemCount() - 1 && position != localCount + phoneCount2 + localServerCount - 1 && position != localCount + globalCount + phoneCount + localServerCount - 1);

                    if (position < searchResult.size()) {
                        name = searchResultNames.get(position);
                        if (name != null && user != null && user.username != null && user.username.length() > 0) {
                            if (name.toString().startsWith("@" + user.username)) {
                                username = name;
                                name = null;
                            }
                        }
                    } else {
                        String foundUserName = searchAdapterHelper.getLastFoundUsername();
                        if (!TextUtils.isEmpty(foundUserName)) {
                            String nameSearch = null;
                            String nameSearchLower = null;
                            int index;
                            if (user != null) {
                                nameSearch = ContactsController.formatName(user.first_name, user.last_name);
                            } else if (chat != null) {
                                nameSearch = chat.title;
                            }
                            if (nameSearch != null && (index = AndroidUtilities.indexOfIgnoreCase(nameSearch, foundUserName)) != -1) {
                                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(nameSearch);
                                spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + foundUserName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                name = spannableStringBuilder;
                            } else if (un != null) {
                                if (foundUserName.startsWith("@")) {
                                    foundUserName = foundUserName.substring(1);
                                }
                                try {
                                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                                    spannableStringBuilder.append("@");
                                    spannableStringBuilder.append(un);
                                    if ((index = AndroidUtilities.indexOfIgnoreCase(un, foundUserName)) != -1) {
                                        int len = foundUserName.length();
                                        if (index == 0) {
                                            len++;
                                        } else {
                                            index++;
                                        }
                                        spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    username = spannableStringBuilder;
                                } catch (Exception e) {
                                    username = un;
                                    FileLog.e(e);
                                }
                            }
                        }
                    }
                }
                boolean savedMessages = false;
                if (user != null && user.id == selfUserId) {
                    name = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                    username = null;
                    savedMessages = true;
                }
                if (chat != null && chat.participants_count != 0) {
                    String membersString;
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        membersString = LocaleController.formatPluralString("Subscribers", chat.participants_count);
                    } else {
                        membersString = LocaleController.formatPluralString("Members", chat.participants_count);
                    }
                    if (username instanceof SpannableStringBuilder) {
                        ((SpannableStringBuilder) username).append(", ").append(membersString);
                    } else if (!TextUtils.isEmpty(username)) {
                        username = TextUtils.concat(username, ", ", membersString);
                    } else {
                        username = membersString;
                    }
                }
                cell.setData(user != null ? user : chat, encryptedChat, name, username, isRecent, savedMessages);
                break;
            }
            case 1: {
                GraySectionCell cell = (GraySectionCell) holder.itemView;
                if (isRecentSearchDisplayed()) {
                    int offset = (!MediaDataController.getInstance(currentAccount).hints.isEmpty() ? 2 : 0);
                    if (position < offset) {
                        cell.setText(LocaleController.getString("ChatHints", R.string.ChatHints));
                    } else {
                        cell.setText(LocaleController.getString("Recent", R.string.Recent), LocaleController.getString("ClearButton", R.string.ClearButton), v -> {
                            if (delegate != null) {
                                delegate.needClearList();
                            }
                        });
                    }
                } else if (!searchResultHashtags.isEmpty()) {
                    cell.setText(LocaleController.getString("Hashtags", R.string.Hashtags), LocaleController.getString("ClearButton", R.string.ClearButton), v -> {
                        if (delegate != null) {
                            delegate.needClearList();
                        }
                    });
                } else {
                    ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
                    int localCount = searchResult.size();
                    int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
                    int phoneCount = searchAdapterHelper.getPhoneSearch().size();
                    int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
                    int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;

                    position -= localCount + localServerCount;
                    if (position >= 0 && position < phoneCount) {
                        cell.setText(LocaleController.getString("PhoneNumberSearch", R.string.PhoneNumberSearch));
                    } else {
                        position -= phoneCount;
                        if (position >= 0 && position < globalCount) {
                            cell.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
                        } else {
                            cell.setText(LocaleController.getString("SearchMessages", R.string.SearchMessages));
                        }
                    }
                }
                break;
            }
            case 2: {
                DialogCell cell = (DialogCell) holder.itemView;
                cell.useSeparator = (position != getItemCount() - 1);
                MessageObject messageObject = (MessageObject) getItem(position);
                cell.setDialog(messageObject.getDialogId(), messageObject, messageObject.messageOwner.date, false);
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
            case 6: {
                String str = (String) getItem(position);
                TextCell cell = (TextCell) holder.itemView;
                cell.setColors(null, Theme.key_windowBackgroundWhiteBlueText2);
                cell.setText(LocaleController.formatString("AddContactByPhone", R.string.AddContactByPhone, PhoneFormat.getInstance().format("+" + str)), false);
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (isRecentSearchDisplayed()) {
            int offset = (!MediaDataController.getInstance(currentAccount).hints.isEmpty() ? 2 : 0);
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
        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
        int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;

        if (i >= 0 && i < localCount) {
            return 0;
        } else {
            i -= localCount;
            if (i >= 0 && i < localServerCount) {
                return 0;
            } else {
                i -= localServerCount;
                if (i >= 0 && i < phoneCount) {
                    Object object = getItem(i);
                    if (object instanceof String) {
                        String str = (String) object;
                        if ("section".equals(str)) {
                            return 1;
                        } else {
                            return 6;
                        }
                    }
                    return 0;
                } else {
                    i -= phoneCount;
                    if (i >= 0 && i < globalCount) {
                        if (i == 0) {
                            return 1;
                        } else {
                            return 0;
                        }
                    } else {
                        i -= globalCount;
                        if (i >= 0 && i < messagesCount) {
                            if (i == 0) {
                                return 1;
                            } else {
                                return 2;
                            }
                        }
                    }
                }
            }
        }
        return 3;
    }
}
