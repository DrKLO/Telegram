/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLClassStore;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.GreySectionCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ProfileSearchCell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class DialogsSearchAdapter extends BaseContactsSearchAdapter {

    private Context mContext;
    private Timer searchTimer;
    private ArrayList<TLObject> searchResult = new ArrayList<TLObject>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<CharSequence>();
    private ArrayList<MessageObject> searchResultMessages = new ArrayList<MessageObject>();
    private String lastSearchText;
    private long reqId = 0;
    private int lastReqId;
    private MessagesActivitySearchAdapterDelegate delegate;
    private boolean needMessagesSearch;
    private boolean messagesSearchEndReached;
    private String lastMessagesSearchString;
    private int lastSearchId = 0;

    private class DialogSearchResult {
        public TLObject object;
        public int date;
        public CharSequence name;
    }

    public static interface MessagesActivitySearchAdapterDelegate {
        public abstract void searchStateChanged(boolean searching);
    }

    public DialogsSearchAdapter(Context context, boolean messagesSearch) {
        mContext = context;
        needMessagesSearch = messagesSearch;
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

    private void searchMessagesInternal(final String query) {
        if (!needMessagesSearch) {
            return;
        }
        if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRpc(reqId, true);
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
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.limit = 20;
        req.peer = new TLRPC.TL_inputPeerEmpty();
        req.q = query;
        if (lastMessagesSearchString != null && query.equals(lastMessagesSearchString) && !searchResultMessages.isEmpty()) {
            req.max_id = searchResultMessages.get(searchResultMessages.size() - 1).messageOwner.id;
        }
        lastMessagesSearchString = query;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        final int currentReqId = ++lastReqId;
        if (delegate != null) {
            delegate.searchStateChanged(true);
        }
        reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
                                if (req.max_id == 0) {
                                    searchResultMessages.clear();
                                }
                                for (TLRPC.Message message : res.messages) {
                                    searchResultMessages.add(new MessageObject(message, null, 0));
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
        }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
    }

    private void searchDialogsInternal(final String query, final boolean serverOnly, final int searchId) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    String q = query.trim().toLowerCase();
                    if (q.length() == 0) {
                        lastSearchId = -1;
                        updateSearchResults(new ArrayList<TLObject>(), new ArrayList<CharSequence>(), new ArrayList<TLRPC.User>(), lastSearchId);
                        return;
                    }

                    ArrayList<Integer> usersToLoad = new ArrayList<Integer>();
                    ArrayList<Integer> chatsToLoad = new ArrayList<Integer>();
                    ArrayList<Integer> encryptedToLoad = new ArrayList<Integer>();
                    ArrayList<TLRPC.User> encUsers = new ArrayList<TLRPC.User>();
                    int resultCount = 0;

                    HashMap<Long, DialogSearchResult> dialogsResult = new HashMap<Long, DialogSearchResult>();
                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 200"));
                    while (cursor.next()) {
                        long id = cursor.longValue(0);
                        DialogSearchResult dialogSearchResult = new DialogSearchResult();
                        dialogSearchResult.date = cursor.intValue(1);
                        dialogsResult.put(id, dialogSearchResult);

                        int lower_id = (int)id;
                        int high_id = (int)(id >> 32);
                        if (lower_id != 0) {
                            if (high_id == 1) {
                                if (!serverOnly && !chatsToLoad.contains(lower_id)) {
                                    chatsToLoad.add(lower_id);
                                }
                            } else {
                                if (lower_id > 0) {
                                    if (!usersToLoad.contains(lower_id)) {
                                        usersToLoad.add(lower_id);
                                    }
                                } else {
                                    if (!chatsToLoad.contains(-lower_id)) {
                                        chatsToLoad.add(-lower_id);
                                    }
                                }
                            }
                        } else if (!serverOnly) {
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
                            String username = null;
                            int usernamePos = name.lastIndexOf(";;;");
                            if (usernamePos != -1) {
                                username = name.substring(usernamePos + 3);
                            }
                            int found = 0;
                            if (name.startsWith(q) || name.contains(" " + q)) {
                                found = 1;
                            } else if (username != null && username.startsWith(q)) {
                                found = 2;
                            }
                            if (found != 0) {
                                ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(0));
                                if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                                    TLRPC.User user = (TLRPC.User) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                                    if (user.id != UserConfig.getClientUserId()) {
                                        DialogSearchResult dialogSearchResult = dialogsResult.get((long)user.id);
                                        if (user.status != null) {
                                            user.status.expires = cursor.intValue(1);
                                        }
                                        if (found == 1) {
                                            dialogSearchResult.name = Utilities.generateSearchName(user.first_name, user.last_name, q);
                                        } else {
                                            dialogSearchResult.name = Utilities.generateSearchName("@" + user.username, null, "@" + q);
                                        }
                                        dialogSearchResult.object = user;
                                        resultCount++;
                                    }
                                }
                                MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                            }
                        }
                        cursor.dispose();
                    }

                    if (!chatsToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, name FROM chats WHERE uid IN(%s)", TextUtils.join(",", chatsToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(1);
                            String[] args = name.split(" ");
                            if (name.startsWith(q) || name.contains(" " + q)) {
                                ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(0));
                                if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                                    TLRPC.Chat chat = (TLRPC.Chat) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                                    long dialog_id;
                                    if (chat.id > 0) {
                                        dialog_id = -chat.id;
                                    } else {
                                        dialog_id = AndroidUtilities.makeBroadcastId(chat.id);
                                    }
                                    DialogSearchResult dialogSearchResult = dialogsResult.get(dialog_id);
                                    dialogSearchResult.name = Utilities.generateSearchName(chat.title, null, q);
                                    dialogSearchResult.object = chat;
                                    resultCount++;
                                }
                                MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                            }
                        }
                        cursor.dispose();
                    }

                    if (!encryptedToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT q.data, u.name, q.user, q.g, q.authkey, q.ttl, u.data, u.status, q.layer, q.seq_in, q.seq_out, q.use_count, q.exchange_id, q.key_date, q.fprint, q.fauthkey, q.khash FROM enc_chats as q INNER JOIN users as u ON q.user = u.uid WHERE q.uid IN(%s)", TextUtils.join(",", encryptedToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(1);

                            String username = null;
                            int usernamePos = name.lastIndexOf(";;;");
                            if (usernamePos != -1) {
                                username = name.substring(usernamePos + 2);
                            }
                            int found = 0;
                            if (name.startsWith(q) || name.contains(" " + q)) {
                                found = 1;
                            } else if (username != null && username.startsWith(q)) {
                                found = 2;
                            }

                            if (found != 0) {
                                ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(0));
                                ByteBufferDesc data2 = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(6));
                                if (data != null && cursor.byteBufferValue(0, data.buffer) != 0 && cursor.byteBufferValue(6, data2.buffer) != 0) {
                                    TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                                    DialogSearchResult dialogSearchResult = dialogsResult.get((long)chat.id << 32);

                                    chat.user_id = cursor.intValue(2);
                                    chat.a_or_b = cursor.byteArrayValue(3);
                                    chat.auth_key = cursor.byteArrayValue(4);
                                    chat.ttl = cursor.intValue(5);
                                    chat.layer = cursor.intValue(8);
                                    chat.seq_in = cursor.intValue(9);
                                    chat.seq_out = cursor.intValue(10);
                                    int use_count = cursor.intValue(11);
                                    chat.key_use_count_in = (short)(use_count >> 16);
                                    chat.key_use_count_out = (short)(use_count);
                                    chat.exchange_id = cursor.longValue(12);
                                    chat.key_create_date = cursor.intValue(13);
                                    chat.future_key_fingerprint = cursor.longValue(14);
                                    chat.future_auth_key = cursor.byteArrayValue(15);
                                    chat.key_hash = cursor.byteArrayValue(16);

                                    TLRPC.User user = (TLRPC.User)TLClassStore.Instance().TLdeserialize(data2, data2.readInt32());
                                    if (user.status != null) {
                                        user.status.expires = cursor.intValue(7);
                                    }
                                    if (found == 1) {
                                        dialogSearchResult.name = Html.fromHtml("<font color=\"#00a60e\">" + ContactsController.formatName(user.first_name, user.last_name) + "</font>");
                                    } else {
                                        dialogSearchResult.name = Utilities.generateSearchName("@" + user.username, null, "@" + q);
                                    }
                                    dialogSearchResult.object = chat;
                                    encUsers.add(user);
                                    resultCount++;
                                }
                                MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                                MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data2);
                            }
                        }
                        cursor.dispose();
                    }

                    ArrayList<DialogSearchResult> searchResults = new ArrayList<DialogSearchResult>(resultCount);
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

                    ArrayList<TLObject> resultArray = new ArrayList<TLObject>();
                    ArrayList<CharSequence> resultArrayNames = new ArrayList<CharSequence>();

                    for (DialogSearchResult dialogSearchResult : searchResults) {
                        resultArray.add(dialogSearchResult.object);
                        resultArrayNames.add(dialogSearchResult.name);
                    }

                    cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid");
                    while (cursor.next()) {
                        int uid = cursor.intValue(3);
                        if (dialogsResult.containsKey((long)uid)) {
                            continue;
                        }
                        String name = cursor.stringValue(2);
                        String username = null;
                        int usernamePos = name.lastIndexOf(";;;");
                        if (usernamePos != -1) {
                            username = name.substring(usernamePos + 3);
                        }
                        int found = 0;
                        if (name.startsWith(q) || name.contains(" " + q)) {
                            found = 1;
                        } else if (username != null && username.startsWith(q)) {
                            found = 2;
                        }
                        if (found != 0) {
                            ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(0));
                            if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                                TLRPC.User user = (TLRPC.User) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                                if (user.id != UserConfig.getClientUserId()) {
                                    if (user.status != null) {
                                        user.status.expires = cursor.intValue(1);
                                    }
                                    if (found == 1) {
                                        resultArrayNames.add(Utilities.generateSearchName(user.first_name, user.last_name, q));
                                    } else {
                                        resultArrayNames.add(Utilities.generateSearchName("@" + user.username, null, "@" + q));
                                    }
                                    resultArray.add(user);
                                }
                            }
                            MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                        }
                    }
                    cursor.dispose();

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

    public String getLastSearchText() {
        return lastSearchText;
    }

    public boolean isGlobalSearch(int i) {
        return i > searchResult.size() && i <= globalSearch.size() + searchResult.size();
    }

    public void searchDialogs(final String query, final boolean serverOnly) {
        if (query != null && lastSearchText != null && query.equals(lastSearchText)) {
            return;
        }
        try {
            if (searchTimer != null) {
                searchTimer.cancel();
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        if (query == null || query.length() == 0) {
            searchResult.clear();
            searchResultNames.clear();
            searchMessagesInternal(null);
            queryServerSearch(null);
            notifyDataSetChanged();
        } else {
            final int searchId = ++lastSearchId;
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        searchTimer.cancel();
                        searchTimer = null;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    searchDialogsInternal(query, serverOnly, searchId);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            queryServerSearch(query);
                            searchMessagesInternal(query);
                        }
                    });
                }
            }, 200, 300);
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int i) {
        return i != searchResult.size() && i != searchResult.size() + (globalSearch.isEmpty() ? 0 : globalSearch.size() + 1);
    }

    @Override
    public int getCount() {
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

    @Override
    public Object getItem(int i) {
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
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        int type = getItemViewType(i);

        if (type == 1) {
            if (view == null) {
                view = new GreySectionCell(mContext);
            }
            if (!globalSearch.isEmpty() && i == searchResult.size()) {
                ((GreySectionCell) view).setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch));
            } else {
                ((GreySectionCell) view).setText(LocaleController.getString("SearchMessages", R.string.SearchMessages));
            }
        } else if (type == 0) {
            if (view == null) {
                view = new ProfileSearchCell(mContext);
            }

            TLRPC.User user = null;
            TLRPC.Chat chat = null;
            TLRPC.EncryptedChat encryptedChat = null;

            int localCount = searchResult.size();
            int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;

            ((ProfileSearchCell) view).useSeparator = (i != getCount() - 1 && i != localCount - 1 && i != localCount + globalCount - 1);
            Object obj = getItem(i);
            if (obj instanceof TLRPC.User) {
                user = MessagesController.getInstance().getUser(((TLRPC.User) obj).id);
                if (user == null) {
                    user = (TLRPC.User) obj;
                }
            } else if (obj instanceof TLRPC.Chat) {
                chat = MessagesController.getInstance().getChat(((TLRPC.Chat) obj).id);
            } else if (obj instanceof TLRPC.EncryptedChat) {
                encryptedChat = MessagesController.getInstance().getEncryptedChat(((TLRPC.EncryptedChat) obj).id);
                user = MessagesController.getInstance().getUser(encryptedChat.user_id);
            }

            CharSequence username = null;
            CharSequence name = null;
            if (i < searchResult.size()) {
                name = searchResultNames.get(i);
                if (name != null && user != null && user.username != null && user.username.length() > 0) {
                    if (name.toString().startsWith("@" + user.username)) {
                        username = name;
                        name = null;
                    }
                }
            } else if (i > searchResult.size() && user != null && user.username != null) {
                try {
                    username = Html.fromHtml(String.format("<font color=\"#4d83b3\">@%s</font>%s", user.username.substring(0, lastFoundUsername.length()), user.username.substring(lastFoundUsername.length())));
                } catch (Exception e) {
                    username = user.username;
                    FileLog.e("tmessages", e);
                }
            }

            ((ProfileSearchCell) view).setData(user, chat, encryptedChat, name, username);
        } else if (type == 2) {
            if (view == null) {
                view = new DialogCell(mContext);
            }
            ((DialogCell) view).useSeparator = (i != getCount() - 1);
            MessageObject messageObject = (MessageObject)getItem(i);
            ((DialogCell) view).setDialog(messageObject.getDialogId(), messageObject, false, messageObject.messageOwner.date, 0);
        } else if (type == 3) {
            if (view == null) {
                view = new LoadingCell(mContext);
            }
        }

        return view;
    }

    @Override
    public int getItemViewType(int i) {
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

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    @Override
    public boolean isEmpty() {
        return searchResult.isEmpty() && globalSearch.isEmpty() && searchResultMessages.isEmpty();
    }
}
