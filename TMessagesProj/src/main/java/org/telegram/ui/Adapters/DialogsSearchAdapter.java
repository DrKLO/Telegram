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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
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
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HashtagSearchCell;
import org.telegram.ui.Cells.HintDialogCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.FilteredSearchView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class DialogsSearchAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private Runnable searchRunnable;
    private Runnable searchRunnable2;
    private ArrayList<Object> searchResult = new ArrayList<>();
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
    private String currentMessagesQuery;
    private int nextSearchRate;
    private int lastSearchId;
    private int lastGlobalSearchId;
    private int lastLocalSearchId;
    private int lastMessagesSearchId;
    private int dialogsType;
    private SearchAdapterHelper searchAdapterHelper;
    private RecyclerListView innerListView;
    private long selfUserId;

    private int currentAccount = UserConfig.selectedAccount;

    private ArrayList<RecentSearchObject> recentSearchObjects = new ArrayList<>();
    private LongSparseArray<RecentSearchObject> recentSearchObjectsById = new LongSparseArray<>();
    private ArrayList<FiltersView.DateData> localTipDates = new ArrayList<>();
    private boolean localTipArchive;
    private FilteredSearchView.Delegate filtersDelegate;
    private int currentItemCount;
    private int folderId;

    public boolean isSearching() {
        return waitingResponseCount > 0;
    }

    public static class DialogSearchResult {
        public TLObject object;
        public int date;
        public CharSequence name;
    }

    public static class RecentSearchObject {
        public TLObject object;
        public int date;
        public long did;
    }

    public interface DialogsSearchAdapterDelegate {
        void searchStateChanged(boolean searching, boolean animated);
        void didPressedOnSubDialog(long did);
        void needRemoveHint(long did);
        void needClearList();
        void runResultsEnterAnimation();
        boolean isSelected(long dialogId);
    }

    public static class CategoryAdapterRecycler extends RecyclerListView.SelectionAdapter {

        private final Context mContext;
        private final int currentAccount;
        private boolean drawChecked;

        public CategoryAdapterRecycler(Context context, int account, boolean drawChecked) {
            this.drawChecked = drawChecked;
            mContext = context;
            currentAccount = account;
        }

        public void setIndex(int value) {
            notifyDataSetChanged();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new HintDialogCell(mContext, drawChecked);
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
            long did = 0;
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
                waitingResponseCount--;
                lastGlobalSearchId = searchId;
                if (lastLocalSearchId != searchId) {
                    searchResult.clear();
                }
                if (lastMessagesSearchId != searchId) {
                    searchResultMessages.clear();
                }
                searchWas = true;
                if (delegate != null) {
                    delegate.searchStateChanged(waitingResponseCount > 0, true);
                }
                notifyDataSetChanged();
                if (delegate != null) {
                    delegate.runResultsEnterAnimation();
                }
            }

            @Override
            public void onSetHashtags(ArrayList<SearchAdapterHelper.HashtagObject> arrayList, HashMap<String, SearchAdapterHelper.HashtagObject> hashMap) {
                for (int a = 0; a < arrayList.size(); a++) {
                    searchResultHashtags.add(arrayList.get(a).hashtag);
                }
                if (delegate != null) {
                    delegate.searchStateChanged(waitingResponseCount > 0, false);
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
            return;
        }

        final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.limit = 20;
        req.q = query;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.flags |= 1;
        req.folder_id = folderId;
        if (query.equals(lastMessagesSearchString) && !searchResultMessages.isEmpty()) {
            MessageObject lastMessage = searchResultMessages.get(searchResultMessages.size() - 1);
            req.offset_id = lastMessage.getId();
            req.offset_rate = nextSearchRate;
            long id = MessageObject.getPeerId(lastMessage.messageOwner.peer_id);
            req.offset_peer = MessagesController.getInstance(currentAccount).getInputPeer(id);
        } else {
            req.offset_rate = 0;
            req.offset_id = 0;
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        lastMessagesSearchString = query;
        final int currentReqId = ++lastReqId;
        reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            final ArrayList<MessageObject> messageObjects = new ArrayList<>();
            if (error == null) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
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
                for (int a = 0; a < res.messages.size(); a++) {
                    TLRPC.Message message = res.messages.get(a);
                    MessageObject messageObject = new MessageObject(currentAccount, message, usersMap, chatsMap, false, true);
                    messageObjects.add(messageObject);
                    messageObject.setQuery(query);
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (currentReqId == lastReqId && (searchId <= 0 || searchId == lastSearchId)) {
                    waitingResponseCount--;
                    if (error == null) {
                        currentMessagesQuery = query;
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
                            int maxId = MessagesController.getInstance(currentAccount).deletedHistory.get(did);
                            if (maxId != 0 && message.id <= maxId) {
                                continue;
                            }
                            searchResultMessages.add(messageObjects.get(a));
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
                        if (delegate != null) {
                            delegate.searchStateChanged(waitingResponseCount > 0, true);
                            delegate.runResultsEnterAnimation();
                        }
                        globalSearchCollapsed = true;
                        phoneCollapsed = true;
                        notifyDataSetChanged();
                    }
                }
                reqId = 0;
            });
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public boolean hasRecentSearch() {
        return dialogsType != 2 && dialogsType != 4 && dialogsType != 5 && dialogsType != 6 && dialogsType != 11 && (!recentSearchObjects.isEmpty() || !MediaDataController.getInstance(currentAccount).hints.isEmpty());
    }

    public boolean isRecentSearchDisplayed() {
        return needMessagesSearch != 2 && !searchWas && (!recentSearchObjects.isEmpty() || !MediaDataController.getInstance(currentAccount).hints.isEmpty()) && dialogsType != 2 && dialogsType != 4 && dialogsType != 5 && dialogsType != 6 && dialogsType != 11;
    }

    public void loadRecentSearch() {
        loadRecentSearch(currentAccount, dialogsType, (arrayList, hashMap) -> {
            DialogsSearchAdapter.this.setRecentSearch(arrayList, hashMap);
        });
    }

    public static void loadRecentSearch(int currentAccount, int dialogsType, OnRecentSearchLoaded callback) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT did, date FROM search_recent WHERE 1");

                ArrayList<Long> usersToLoad = new ArrayList<>();
                ArrayList<Long> chatsToLoad = new ArrayList<>();
                ArrayList<Integer> encryptedToLoad = new ArrayList<>();
                ArrayList<TLRPC.User> encUsers = new ArrayList<>();

                final ArrayList<RecentSearchObject> arrayList = new ArrayList<>();
                final LongSparseArray<RecentSearchObject> hashMap = new LongSparseArray<>();
                while (cursor.next()) {
                    long did = cursor.longValue(0);

                    boolean add = false;
                    if (DialogObject.isEncryptedDialog(did)) {
                        if (dialogsType == 0 || dialogsType == 3) {
                            int encryptedChatId = DialogObject.getEncryptedChatId(did);
                            if (!encryptedToLoad.contains(encryptedChatId)) {
                                encryptedToLoad.add(encryptedChatId);
                                add = true;
                            }
                        }
                    } else if (DialogObject.isUserDialog(did)) {
                        if (dialogsType != 2 && !usersToLoad.contains(did)) {
                            usersToLoad.add(did);
                            add = true;
                        }
                    } else {
                        if (!chatsToLoad.contains(-did)) {
                            chatsToLoad.add(-did);
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
                        RecentSearchObject recentSearchObject = hashMap.get(DialogObject.makeEncryptedDialogId(encryptedChats.get(a).id));
                        if (recentSearchObject != null) {
                            recentSearchObject.object = encryptedChats.get(a);
                        }
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
                            RecentSearchObject recentSearchObject = hashMap.get(did);
                            if (recentSearchObject != null) {
                                recentSearchObject.object = chat;
                            }
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
                AndroidUtilities.runOnUIThread(() -> callback.setRecentSearch(arrayList, hashMap));
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
        String q = query.trim().toLowerCase();
        if (q.length() == 0) {
            lastSearchId = 0;
            updateSearchResults(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), lastSearchId);
            return;
        }
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            ArrayList<Object> resultArray = new ArrayList<>();
            ArrayList<CharSequence> resultArrayNames = new ArrayList<>();
            ArrayList<TLRPC.User> encUsers = new ArrayList<>();
            MessagesStorage.getInstance(currentAccount).localSearch(dialogsType, q, resultArray, resultArrayNames, encUsers, -1);
            updateSearchResults(resultArray, resultArrayNames, encUsers, searchId);
            FiltersView.fillTipDates(q, localTipDates);
            localTipArchive = false;
            if (q.length() >= 3 && (LocaleController.getString("ArchiveSearchFilter", R.string.ArchiveSearchFilter).toLowerCase().startsWith(q) || "archive".startsWith(query))) {
                localTipArchive = true;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (filtersDelegate != null) {
                    filtersDelegate.updateFiltersView(false, null, localTipDates, localTipArchive);
                }
            });
        });
    }


    private void updateSearchResults(final ArrayList<Object> result, final ArrayList<CharSequence> names, final ArrayList<TLRPC.User> encUsers, final int searchId) {
        AndroidUtilities.runOnUIThread(() -> {
            waitingResponseCount--;
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
                Object obj = result.get(a);
                long dialogId = 0;
                if (obj instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) obj;
                    MessagesController.getInstance(currentAccount).putUser(user, true);
                    dialogId = user.id;
                } else if (obj instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) obj;
                    MessagesController.getInstance(currentAccount).putChat(chat, true);
                    dialogId = -chat.id;
                } else if (obj instanceof TLRPC.EncryptedChat) {
                    TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat) obj;
                    MessagesController.getInstance(currentAccount).putEncryptedChat(chat, true);
                }

                if (dialogId != 0) {
                    TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogs_dict.get(dialogId);
                    if (dialog == null) {
                        long finalDialogId = dialogId;
                        MessagesStorage.getInstance(currentAccount).getDialogFolderId(dialogId, param -> {
                            if (param != -1) {
                                TLRPC.Dialog newDialog = new TLRPC.TL_dialog();
                                newDialog.id = finalDialogId;
                                if (param != 0) {
                                    newDialog.folder_id = param;
                                }
                                if (obj instanceof TLRPC.Chat) {
                                    newDialog.flags = ChatObject.isChannel((TLRPC.Chat) obj) ? 1 : 0;
                                }
                                MessagesController.getInstance(currentAccount).dialogs_dict.put(finalDialogId, newDialog);
                                MessagesController.getInstance(currentAccount).getAllDialogs().add(newDialog);
                                MessagesController.getInstance(currentAccount).sortDialogs(null);
                            }
                        });
                    }
                }
            }
            MessagesController.getInstance(currentAccount).putUsers(encUsers, true);
            searchResult = result;
            searchResultNames = names;
            searchAdapterHelper.mergeResults(searchResult);
            notifyDataSetChanged();
            if (delegate != null) {
                delegate.searchStateChanged(waitingResponseCount > 0, true);
                delegate.runResultsEnterAnimation();
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

    int waitingResponseCount;

    public void searchDialogs(String text, int folderId) {
        if (text != null && text.equals(lastSearchText) && (folderId == this.folderId || TextUtils.isEmpty(text))) {
            return;
        }
        lastSearchText = text;
        this.folderId = folderId;
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
            searchAdapterHelper.queryServerSearch(null, true, true, dialogsType != 11, dialogsType != 11, dialogsType == 2 || dialogsType == 11, 0, dialogsType == 0, 0, 0);
            searchWas = false;
            lastSearchId = 0;
            waitingResponseCount = 0;
            globalSearchCollapsed = true;
            phoneCollapsed = true;
            if (delegate != null) {
                delegate.searchStateChanged(false, true);
            }
            searchMessagesInternal(null, 0);
            notifyDataSetChanged();
            localTipDates.clear();
            localTipArchive = false;
            if (filtersDelegate != null) {
                filtersDelegate.updateFiltersView(false, null, localTipDates, localTipArchive);
            }
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
                    globalSearchCollapsed = true;
                    phoneCollapsed = true;
                    waitingResponseCount = 0;
                    notifyDataSetChanged();
                    if (delegate != null) {
                        delegate.searchStateChanged(false, false);
                    }
                }
            } else {
                searchResultHashtags.clear();
            }

            final int searchId = ++lastSearchId;
            waitingResponseCount = 3;
            globalSearchCollapsed = true;
            phoneCollapsed = true;
            notifyDataSetChanged();
            if (delegate != null) {
                delegate.searchStateChanged(true, false);
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
                        searchAdapterHelper.queryServerSearch(query, true, dialogsType != 4, true, dialogsType != 4 && dialogsType != 11, dialogsType == 2 || dialogsType == 1, 0, dialogsType == 0, 0, searchId);
                    } else {
                        waitingResponseCount -= 2;
                    }
                    if (needMessagesSearch == 0) {
                        waitingResponseCount--;
                    } else {
                        searchMessagesInternal(text, searchId);
                    }
                });
            }, 300);
        }
    }

    @Override
    public int getItemCount() {
        if (waitingResponseCount == 3) {
            return 0;
        }
        if (isRecentSearchDisplayed()) {
            return (!recentSearchObjects.isEmpty() ? recentSearchObjects.size() + 1 : 0) + (!MediaDataController.getInstance(currentAccount).hints.isEmpty() ? 1 : 0);
        }
        int count = 0;
        if (!searchResultHashtags.isEmpty()) {
            count += searchResultHashtags.size() + 1;
            return count;
        }
        count += searchResult.size();
        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
        int globalCount = searchAdapterHelper.getGlobalSearch().size();
        if (globalCount > 3 && globalSearchCollapsed) {
            globalCount = 3;
        }
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        if (phoneCount > 3 && phoneCollapsed) {
            phoneCount = 3;
        }
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
        return currentItemCount = count;
    }

    public Object getItem(int i) {
        if (isRecentSearchDisplayed()) {
            int offset = (!MediaDataController.getInstance(currentAccount).hints.isEmpty() ? 1 : 0);
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
        if (phoneCount > 3 && phoneCollapsed) {
            phoneCount = 3;
        }
        int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
        if (globalCount > 4 && globalSearchCollapsed) {
            globalCount = 4;
        }
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
        if (phoneCount > 3 && phoneCollapsed) {
            phoneCount = 3;
        }
        int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
        if (globalCount > 4 && globalSearchCollapsed) {
            globalCount = 4;
        }
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
        View view;
        switch (viewType) {
            case 0:
                view = new ProfileSearchCell(mContext);
                break;
            case 1:
                view = new GraySectionCell(mContext);
                break;
            case 2:
                view = new DialogCell(null, mContext, false, true);
                break;
            case 3:
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_TYPE);
                flickerLoadingView.setIsSingleCell(true);
                view = flickerLoadingView;
                break;
            case 4:
                view = new HashtagSearchCell(mContext);
                break;
            case 5:
                RecyclerListView horizontalListView = new RecyclerListView(mContext) {
                    @Override
                    public boolean onInterceptTouchEvent(MotionEvent e) {
                        if (getParent() != null && getParent().getParent() != null) {
                            getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1));
                        }
                        return super.onInterceptTouchEvent(e);
                    }
                };
                horizontalListView.setSelectorRadius(AndroidUtilities.dp(4));
                horizontalListView.setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector));
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
                horizontalListView.setAdapter(new CategoryAdapterRecycler(mContext, currentAccount, false));
                horizontalListView.setOnItemClickListener((view1, position) -> {
                    if (delegate != null) {
                        delegate.didPressedOnSubDialog((Long) view1.getTag());
                    }
                });
                horizontalListView.setOnItemLongClickListener((view12, position) -> {
                    if (delegate != null) {
                        delegate.needRemoveHint((Long) view12.getTag());
                    }
                    return true;
                });
                view = horizontalListView;
                innerListView = horizontalListView;
                break;
            case 6:
            default:
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
                long oldDialogId = cell.getDialogId();

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
                    if (phoneCount > 3 && phoneCollapsed) {
                        phoneCount = 3;
                    }
                    int phoneCount2 = phoneCount;
                    if (phoneCount > 0 && phoneSearch.get(phoneCount - 1) instanceof String) {
                        phoneCount2 -= 2;
                    }
                    int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
                    if (globalCount > 4 && globalSearchCollapsed) {
                        globalCount = 4;
                    }
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
                            int index;
                            if (user != null) {
                                nameSearch = ContactsController.formatName(user.first_name, user.last_name);
                            } else if (chat != null) {
                                nameSearch = chat.title;
                            }
                            if (nameSearch != null && (index = AndroidUtilities.indexOfIgnoreCase(nameSearch, foundUserName)) != -1) {
                                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(nameSearch);
                                spannableStringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4), index, index + foundUserName.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
                                        spannableStringBuilder.setSpan(new ForegroundColorSpanThemable(Theme.key_windowBackgroundWhiteBlueText4), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    username = spannableStringBuilder;
                                } catch (Exception e) {
                                    username = un;
                                    FileLog.e(e);
                                }
                            }
                        }
                    }
                    cell.setChecked(false, false);
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
                cell.setChecked(delegate.isSelected(cell.getDialogId()), oldDialogId == cell.getDialogId());
                break;
            }
            case 1: {
                GraySectionCell cell = (GraySectionCell) holder.itemView;
                if (isRecentSearchDisplayed()) {
                    int offset = (!MediaDataController.getInstance(currentAccount).hints.isEmpty() ? 1 : 0);
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
                    if (phoneCount > 3 && phoneCollapsed) {
                        phoneCount = 3;
                    }
                    int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
                    if (globalCount > 4 && globalSearchCollapsed) {
                        globalCount = 4;
                    }
                    int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
                    position -= localCount + localServerCount;
                    String title;
                    boolean showMore = false;
                    Runnable onClick = null;
                    if (position >= 0 && position < phoneCount) {
                        title = LocaleController.getString("PhoneNumberSearch", R.string.PhoneNumberSearch);
                        if (searchAdapterHelper.getPhoneSearch().size() > 3) {
                            showMore = phoneCollapsed;
                            onClick = () -> {
                                phoneCollapsed = !phoneCollapsed;
                                notifyDataSetChanged();
                            };
                        }
                    } else {
                        position -= phoneCount;
                        if (position >= 0 && position < globalCount) {
                            title = LocaleController.getString("GlobalSearch", R.string.GlobalSearch);
                            if (searchAdapterHelper.getGlobalSearch().size() > 3) {
                                showMore = globalSearchCollapsed;
                                onClick = () -> {
                                    globalSearchCollapsed = !globalSearchCollapsed;
                                    notifyDataSetChanged();
                                };
                            }
                        } else {
                            title = LocaleController.getString("SearchMessages", R.string.SearchMessages);
                        }
                    }

                    if (onClick == null) {
                        cell.setText(title);
                    } else {
                        final Runnable finalOnClick = onClick;
                        cell.setText(title, showMore ? LocaleController.getString("ShowMore", R.string.ShowMore) : LocaleController.getString("ShowLess", R.string.ShowLess), e -> finalOnClick.run());
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

    boolean globalSearchCollapsed = true;
    boolean phoneCollapsed = true;

    @Override
    public int getItemViewType(int i) {
        if (isRecentSearchDisplayed()) {
            int offset = (!MediaDataController.getInstance(currentAccount).hints.isEmpty() ? 1 : 0);
            if (i < offset) {
                return 5;
            }
            if (i == offset) {
                return 1;
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
        if (phoneCount > 3 && phoneCollapsed) {
            phoneCount = 3;
        }
        int globalCount = globalSearch.isEmpty() ? 0 : globalSearch.size() + 1;
        if (globalCount > 4 && globalSearchCollapsed) {
            globalCount = 4;
        }
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

    public void setFiltersDelegate(FilteredSearchView.Delegate filtersDelegate, boolean update) {
        this.filtersDelegate = filtersDelegate;
        if (filtersDelegate != null && update) {
            filtersDelegate.updateFiltersView(false, null, localTipDates, localTipArchive);
        }
    }

    public int getCurrentItemCount() {
        return currentItemCount;
    }

    public interface OnRecentSearchLoaded {
        void setRecentSearch(ArrayList<RecentSearchObject> arrayList, LongSparseArray<RecentSearchObject> hashMap);
    }
}
