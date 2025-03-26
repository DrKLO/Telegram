/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
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
import org.telegram.ui.Cells.TopicSearchCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.FilteredSearchView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class DialogsSearchAdapter extends RecyclerListView.SelectionAdapter {

    public final static int VIEW_TYPE_PROFILE_CELL = 0;
    public final static int VIEW_TYPE_GRAY_SECTION = 1;
    public final static int VIEW_TYPE_DIALOG_CELL = 2;
    public final static int VIEW_TYPE_TOPIC_CELL = 3;
    public final static int VIEW_TYPE_LOADING = 4;
    public final static int VIEW_TYPE_HASHTAG_CELL = 5;
    public final static int VIEW_TYPE_CATEGORY_LIST = 6;
    public final static int VIEW_TYPE_ADD_BY_PHONE = 7;
    public final static int VIEW_TYPE_INVITE_CONTACT_CELL = 8;
    public final static int VIEW_TYPE_PUBLIC_POST = 9;
    public final static int VIEW_TYPE_EMPTY_RESULT = 10;

    public static enum Filter {
        All(0, R.string.SearchMessagesFilterAll, R.string.SearchMessagesFilterAllFrom),
        Private(8, R.string.SearchMessagesFilterPrivate, R.string.SearchMessagesFilterPrivateFrom),
        Groups(4, R.string.SearchMessagesFilterGroup, R.string.SearchMessagesFilterGroupFrom),
        Channels(2, R.string.SearchMessagesFilterChannels, R.string.SearchMessagesFilterChannelsFrom);

        public final int flags;
        public final int strResId;
        public final int strFromResId;

        Filter(int flags, int strResId, int strFromResId) {
            this.flags = flags;
            this.strResId = strResId;
            this.strFromResId = strFromResId;
        }
    }

    private Filter currentMessagesFilter = Filter.All;
    private boolean forceLoadingMessages;
    public void resetFilter() {
        currentMessagesFilter = Filter.All;
    }

    private final Context mContext;
    private Runnable searchRunnable;
    private Runnable searchRunnable2;
    private int searchHashtagRequest = -1;
    private Runnable searchHashtagRunnable;
    private ArrayList<Object> searchResult = new ArrayList<>();
    public int publicPostsTotalCount;
    public int publicPostsLastRate;
    public ArrayList<MessageObject> publicPosts = new ArrayList<>();
    public String publicPostsHashtag;
    private final ArrayList<ContactsController.Contact> searchContacts = new ArrayList<>();
    private final ArrayList<TLRPC.TL_forumTopic> searchTopics = new ArrayList<>();
    private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
    private final ArrayList<MessageObject> searchForumResultMessages = new ArrayList<>();
    private final ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private final ArrayList<String> searchResultHashtags = new ArrayList<>();
    public final ArrayList<TLRPC.TL_sponsoredPeer> sponsoredPeers = new ArrayList<>();
    private final HashSet<byte[]> seenSponsoredPeers = new HashSet<>();
    private String lastSearchText;
    private boolean searchWas;
    private int reqId = 0;
    private int lastReqId;
    private int reqForumId = 0;
    private String sponsoredQuery;
    private int sponsoredReqId;
    private int lastForumReqId;
    public DialogsSearchAdapterDelegate delegate;
    private int needMessagesSearch;
    private boolean messagesSearchEndReached;
    private boolean localMessagesSearchEndReached;
    public int localMessagesLoadingRow = -1;
    private String lastMessagesSearchString;
    private int lastMessagesSearchFilterFlags;
    private String currentMessagesQuery;
    private int nextSearchRate;
    private int lastSearchId;
    private int lastGlobalSearchId;
    private int lastLocalSearchId;
    private int lastMessagesSearchId;
    private int dialogsType;
    private DefaultItemAnimator itemAnimator;
    private SearchAdapterHelper searchAdapterHelper;
    private RecyclerListView innerListView;
    private long selfUserId;
    public int showMoreLastItem;
    public boolean showMoreAnimation = false;
    private long lastShowMoreUpdate;
    public View showMoreHeader;
    private Runnable cancelShowMoreAnimation;
    private ArrayList<Long> filterDialogIds;
    private final DialogsActivity dialogsActivity;
    private final Theme.ResourcesProvider resourcesProvider;

    private int currentAccount = UserConfig.selectedAccount;

    private ArrayList<RecentSearchObject> recentSearchObjects = new ArrayList<>();
    private final ArrayList<RecentSearchObject> filteredRecentSearchObjects = new ArrayList<>();
    private final ArrayList<RecentSearchObject> filtered2RecentSearchObjects = new ArrayList<>();
    private String filteredRecentQuery = null;
    private LongSparseArray<RecentSearchObject> recentSearchObjectsById = new LongSparseArray<>();
    private ArrayList<FiltersView.DateData> localTipDates = new ArrayList<>();
    private boolean localTipArchive;
    private FilteredSearchView.Delegate filtersDelegate;
    private int currentItemCount;
    private int folderId;
    private ArrayList<ContactEntry> allContacts;

    public void setFilterDialogIds(ArrayList<Long> filterDialogIds) {
        this.filterDialogIds = filterDialogIds;
    }

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
        void didPressedBlockedDialog(View view, long did);
        void needRemoveHint(long did);
        void needClearList();
        void runResultsEnterAnimation();
        boolean isSelected(long dialogId);
        long getSearchForumDialogId();
    }

    public static class CategoryAdapterRecycler extends RecyclerListView.SelectionAdapter {

        private final Context mContext;
        private final int currentAccount;
        private boolean drawChecked;
        private boolean forceDarkTheme;
        private boolean showPremiumBlock;
        private Theme.ResourcesProvider resourcesProvider;

        public CategoryAdapterRecycler(Context context, int account, boolean drawChecked, boolean showPremiumBlock, Theme.ResourcesProvider resourcesProvider) {
            this.drawChecked = drawChecked;
            mContext = context;
            currentAccount = account;
            this.showPremiumBlock = showPremiumBlock;
            this.resourcesProvider = resourcesProvider;
        }

        public void setIndex(int value) {
            notifyDataSetChanged();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            HintDialogCell cell = new HintDialogCell(mContext, drawChecked, resourcesProvider);
            if (showPremiumBlock) {
                cell.showPremiumBlocked();
            }
            cell.setLayoutParams(new RecyclerView.LayoutParams(dp(80), dp(86)));
            return new RecyclerListView.Holder(cell);
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

    private boolean filter(Object obj) {
        if (dialogsType != DialogsActivity.DIALOGS_TYPE_START_ATTACH_BOT) {
            return true;
        }
        // add more filters if needed
        if (obj instanceof TLRPC.User) {
            if (((TLRPC.User) obj).bot) {
                return dialogsActivity.allowBots;
            }
            return dialogsActivity.allowUsers;
        } else if (obj instanceof TLRPC.Chat) {
            TLRPC.Chat chat = (TLRPC.Chat) obj;
            if (ChatObject.isChannel(chat)) {
                return dialogsActivity.allowChannels;
            } else if (ChatObject.isMegagroup(chat)) {
                return dialogsActivity.allowGroups || dialogsActivity.allowMegagroups;
            } else {
                return dialogsActivity.allowGroups || dialogsActivity.allowLegacyGroups;
            }
        }
        return false;
    }

    public DialogsSearchAdapter(Context context, DialogsActivity dialogsActivity, int messagesSearch, int type, DefaultItemAnimator itemAnimator, boolean allowGlobalSearch, Theme.ResourcesProvider resourcesProvider) {
        this.itemAnimator = itemAnimator;
        this.dialogsActivity = dialogsActivity;
        this.resourcesProvider = resourcesProvider;
        searchAdapterHelper = new SearchAdapterHelper(false) {
            @Override
            protected boolean filter(TLObject obj) {
                return DialogsSearchAdapter.this.filter(obj);
            }
        };
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
        searchAdapterHelper.setAllowGlobalResults(allowGlobalSearch);
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
        return (delegate.getSearchForumDialogId() == 0 || localMessagesSearchEndReached) && messagesSearchEndReached;
    }

    public void loadMoreSearchMessages() {
        if (reqForumId != 0 && reqId != 0) {
            return;
        }
        if (lastMessagesSearchId != lastSearchId) {
            return;
        }
        if (delegate != null && delegate.getSearchForumDialogId() != 0 && !localMessagesSearchEndReached) {
            searchForumMessagesInternal(lastMessagesSearchString, lastMessagesSearchId);
        } else {
            searchMessagesInternal(lastMessagesSearchString, lastMessagesSearchId);
        }
    }

    public String getLastSearchString() {
        return lastMessagesSearchString;
    }

    private void searchForumMessagesInternal(final String query, int searchId) {
        if (delegate == null || delegate.getSearchForumDialogId() == 0) {
            return;
        }
        if (needMessagesSearch == 0 || TextUtils.isEmpty(lastMessagesSearchString) && TextUtils.isEmpty(query)) {
            return;
        }
        if (reqForumId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqForumId, true);
            reqForumId = 0;
        }
        if (TextUtils.isEmpty(query)) {
            filteredRecentQuery = null;
            searchResultMessages.clear();
            searchForumResultMessages.clear();
            lastForumReqId = 0;
            lastMessagesSearchString = null;
            searchWas = false;
            notifyDataSetChanged();
            return;
        }

        if (dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
            return;
        }

        final long dialogId = delegate.getSearchForumDialogId();

        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.limit = 20;
        req.q = query;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        if (query.equals(lastMessagesSearchString) && !searchForumResultMessages.isEmpty()) {
            req.add_offset = searchForumResultMessages.size();
        }
        lastMessagesSearchString = query;
        final int currentReqId = ++lastForumReqId;
        reqForumId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
                if (currentReqId == lastForumReqId && (searchId <= 0 || searchId == lastSearchId)) {
                    waitingResponseCount--;
                    if (error == null) {
                        currentMessagesQuery = query;
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                        if (req.add_offset == 0) {
                            searchForumResultMessages.clear();
                        }
                        nextSearchRate = res.next_rate;
                        for (int a = 0; a < res.messages.size(); a++) {
                            TLRPC.Message message = res.messages.get(a);
                            long did = MessageObject.getDialogId(message);
                            int maxId = MessagesController.getInstance(currentAccount).deletedHistory.get(did);
                            if (maxId != 0 && message.id <= maxId) {
                                continue;
                            }
                            searchForumResultMessages.add(messageObjects.get(a));
                        }
                        searchWas = true;
                        localMessagesSearchEndReached = res.messages.size() != 20;
                        if (searchId > 0) {
                            lastMessagesSearchId = searchId;
                            if (lastLocalSearchId != searchId) {
                                searchResult.clear();
                            }
                            if (lastGlobalSearchId != searchId) {
                                searchAdapterHelper.clear();
                            }
                        }
                        searchAdapterHelper.mergeResults(searchResult, filtered2RecentSearchObjects);
                        if (delegate != null) {
                            delegate.searchStateChanged(waitingResponseCount > 0, true);
                            delegate.runResultsEnterAnimation();
                        }
                        notifyDataSetChanged();
                    }
                }
                reqForumId = 0;
            });
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private void searchTopics(final String query) {
        searchTopics.clear();
        if (delegate == null || delegate.getSearchForumDialogId() == 0) {
            return;
        }
        if (!TextUtils.isEmpty(query)) {
            long dialogId = delegate.getSearchForumDialogId();
            ArrayList<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialogId);
            String searchTrimmed = query.trim();
            for (int i = 0; i < topics.size(); i++) {
                if (topics.get(i) != null && topics.get(i).title.toLowerCase().contains(searchTrimmed)) {
                    searchTopics.add(topics.get(i));
                    topics.get(i).searchQuery = searchTrimmed;
                }
            }
        }
        notifyDataSetChanged();
    }

    private void searchMessagesInternal(final String query, int searchId) {
        if (needMessagesSearch == 0 || TextUtils.isEmpty(lastMessagesSearchString) && TextUtils.isEmpty(query)) {
            return;
        }
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        if (TextUtils.isEmpty(query) || delegate.getSearchForumDialogId() != 0) {
            filteredRecentQuery = null;
            searchResultMessages.clear();
            searchForumResultMessages.clear();
            lastReqId = 0;
            lastMessagesSearchString = null;
            lastMessagesSearchFilterFlags = 0;
            searchWas = false;
            notifyDataSetChanged();
            return;
        } else {
            filterRecent(query);
            searchAdapterHelper.mergeResults(searchResult, filtered2RecentSearchObjects);
        }

        if (dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
            waitingResponseCount--;
            if (delegate != null) {
                delegate.searchStateChanged(waitingResponseCount > 0, true);
                delegate.runResultsEnterAnimation();
            }
            return;
        }

        final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.broadcasts_only = (currentMessagesFilter.flags & 2) != 0;
        req.groups_only = (currentMessagesFilter.flags & 4) != 0;
        req.users_only = (currentMessagesFilter.flags & 8) != 0;
        req.limit = 20;
        req.q = query;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.flags |= 1;
        req.folder_id = folderId;
        if (!query.equals(lastMessagesSearchString)) {
            forceLoadingMessages = false;
        }
        if (query.equals(lastMessagesSearchString) && lastMessagesSearchFilterFlags == currentMessagesFilter.flags && !searchResultMessages.isEmpty() && lastMessagesSearchId == lastSearchId) {
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
        lastMessagesSearchFilterFlags = currentMessagesFilter.flags;
        lastReqId++;
        final int currentReqId = lastReqId;
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
            HashSet<Pair<Boolean, Long>> dialogIdsToGetMaxRead = new HashSet<>();
            if (error == null) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                for (int a = 0; a < res.messages.size(); a++) {
                    TLRPC.Message message = res.messages.get(a);
                    long dialog_id = MessageObject.getDialogId(message);
                    ConcurrentHashMap<Long, Integer> read_max = message.out ? MessagesController.getInstance(currentAccount).dialogs_read_outbox_max : MessagesController.getInstance(currentAccount).dialogs_read_inbox_max;
                    Integer value = read_max.get(dialog_id);
                    if (value == null) {
                        dialogIdsToGetMaxRead.add(new Pair<>(message.out, dialog_id));
                    }
                }
            }
            Runnable done = () -> {
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
                            MessageObject msg = messageObjects.get(a);
                            if (!searchForumResultMessages.isEmpty()) {
                                boolean foundDuplicate = false;
                                for (int i = 0; i < searchForumResultMessages.size(); ++i) {
                                    MessageObject msg2 = searchForumResultMessages.get(i);
                                    if (msg2 != null && msg != null && msg.getId() == msg2.getId() && msg.getDialogId() == msg2.getDialogId()) {
                                        foundDuplicate = true;
                                        break;
                                    }
                                }
                                if (foundDuplicate) {
                                    continue;
                                }
                            }
                            searchResultMessages.add(msg);
                            long dialog_id = MessageObject.getDialogId(message);
                            ConcurrentHashMap<Long, Integer> read_max = message.out ? MessagesController.getInstance(currentAccount).dialogs_read_outbox_max : MessagesController.getInstance(currentAccount).dialogs_read_inbox_max;
                            Integer value = read_max.get(dialog_id);
                            if (value != null) {
                                message.unread = value < message.id;
                            }
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
                        searchAdapterHelper.mergeResults(searchResult, filtered2RecentSearchObjects);
                        if (delegate != null) {
                            delegate.searchStateChanged(waitingResponseCount > 0, true);
                            delegate.runResultsEnterAnimation();
                        }
                        globalSearchCollapsed = true;
                        phoneCollapsed = true;
                        forceLoadingMessages = false;
                        if (messagesEmptyLayout != null) {
                            messagesEmptyLayout.setQuery(lastMessagesSearchString);
                        }
                        notifyDataSetChanged();
                    }
                }
                reqId = 0;
            };
            if (dialogIdsToGetMaxRead.isEmpty()) {
                AndroidUtilities.runOnUIThread(done);
            } else {
                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                    MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    for (Pair<Boolean, Long> p : dialogIdsToGetMaxRead) {
                        final boolean out = p.first;
                        final long did = p.second;
                        ConcurrentHashMap<Long, Integer> read_max = out ? messagesController.dialogs_read_outbox_max : messagesController.dialogs_read_inbox_max;
                        final int dialog_read_max = MessagesStorage.getInstance(currentAccount).getDialogReadMaxSync(out, did);
                        read_max.put(did, dialog_read_max);
                    }
                    AndroidUtilities.runOnUIThread(done);
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public boolean hasRecentSearch() {
        return recentSearchAvailable() && getRecentItemsCount() > 0;
    }

    private boolean recentSearchAvailable() {
        return (
            dialogsType != DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO &&
            dialogsType != DialogsActivity.DIALOGS_TYPE_USERS_ONLY &&
            dialogsType != DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY &&
            dialogsType != DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY &&
            dialogsType != DialogsActivity.DIALOGS_TYPE_BOT_SHARE &&
            dialogsType != DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS &&
            dialogsType != DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER
        );
    }

    public boolean isSearchWas() {
        return searchWas;
    }

    public boolean isRecentSearchDisplayed() {
        return needMessagesSearch != 2 && hasRecentSearch();
    }

    public void loadRecentSearch() {
        if (dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
            return;
        }
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
                        if (dialogsType == DialogsActivity.DIALOGS_TYPE_DEFAULT || dialogsType == DialogsActivity.DIALOGS_TYPE_FORWARD) {
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
                    MessagesStorage.getInstance(currentAccount).getUsersInternal(usersToLoad, users);
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
        filterRecent(lastSearchText != null ? lastSearchText.trim() : null);
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
        StringBuilder queryFilter = null;
        if (searchWas) {
            while (filtered2RecentSearchObjects.size() > 0) {
                RecentSearchObject obj = filtered2RecentSearchObjects.remove(0);
                recentSearchObjects.remove(obj);
                filteredRecentSearchObjects.remove(obj);
                recentSearchObjectsById.remove(obj.did);
                if (queryFilter == null) {
                    queryFilter = new StringBuilder("did IN (");
                    queryFilter.append(obj.did);
                } else {
                    queryFilter.append(", ").append(obj.did);
                }
            }
            if (queryFilter == null) {
                queryFilter = new StringBuilder("1");
            } else {
                queryFilter.append(")");
            }
        } else {
            filtered2RecentSearchObjects.clear();
            filteredRecentSearchObjects.clear();
            recentSearchObjects.clear();
            recentSearchObjectsById.clear();
            queryFilter = new StringBuilder("1");
        }
        final StringBuilder finalQueryFilter = queryFilter;
        filterRecent(lastSearchText != null ? lastSearchText.trim() : null);
        notifyDataSetChanged();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            try {
                finalQueryFilter.insert(0, "DELETE FROM search_recent WHERE ");
                MessagesStorage.getInstance(currentAccount).getDatabase().executeFast(finalQueryFilter.toString()).stepThis().dispose();
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
        filtered2RecentSearchObjects.remove(object);
        filteredRecentSearchObjects.remove(object);
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
        filterRecent(null);
        notifyDataSetChanged();
    }

    private void searchDialogsInternal(final String query, final int searchId) {
        if (needMessagesSearch == 2) {
            return;
        }
        String q = query.trim().toLowerCase();
        if (q.length() == 0) {
            lastSearchId = 0;
            updateSearchResults(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), lastSearchId);
            return;
        }
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            ArrayList<Object> resultArray = new ArrayList<>();
            ArrayList<CharSequence> resultArrayNames = new ArrayList<>();
            ArrayList<TLRPC.User> encUsers = new ArrayList<>();
            ArrayList<ContactsController.Contact> contacts = new ArrayList<>();

            MessagesStorage.getInstance(currentAccount).localSearch(dialogsType, q, resultArray, resultArrayNames, encUsers, filterDialogIds, -1);
//            if (allContacts == null) {
//                allContacts = new ArrayList<>();
//                for (ContactsController.Contact contact : ContactsController.getInstance(currentAccount).phoneBookContacts) {
//                    ContactEntry contactEntry = new ContactEntry();
//                    contactEntry.contact = contact;
//                    contactEntry.q1 = (contact.first_name + " " + contact.last_name).toLowerCase();
//                    contactEntry.q2 = (contact.last_name + " " + contact.first_name).toLowerCase();
//                    allContacts.add(contactEntry);
//                }
//            }
//            for (int i = 0; i < allContacts.size(); i++) {
//                if (allContacts.get(i).q1.toLowerCase().contains(q) || allContacts.get(i).q1.toLowerCase().contains(q)) {
//                    contacts.add(allContacts.get(i).contact);
//                }
//            }
            updateSearchResults(resultArray, resultArrayNames, encUsers, contacts, searchId);
            FiltersView.fillTipDates(q, localTipDates);
            localTipArchive = false;
            if (q.length() >= 3 && (LocaleController.getString(R.string.ArchiveSearchFilter).toLowerCase().startsWith(q) || "archive".startsWith(query))) {
                localTipArchive = true;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (filtersDelegate != null) {
                    filtersDelegate.updateFiltersView(false, null, localTipDates, localTipArchive);
                }
            });
        });
    }


    private void updateSearchResults(final ArrayList<Object> result, final ArrayList<CharSequence> names, final ArrayList<TLRPC.User> encUsers,  final ArrayList<ContactsController.Contact> contacts, final int searchId) {
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
            for (int i = 0; i < result.size(); ++i) {
                if (!filter(result.get(i))) {
                    result.remove(i);
                    i--;
                }
            }
            final int recentCount = filtered2RecentSearchObjects.size();
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

                if (recentSearchAvailable() && !(obj instanceof TLRPC.EncryptedChat)) {
                    boolean foundInRecent = false;
                    if (delegate != null && delegate.getSearchForumDialogId() == dialogId) {
                        foundInRecent = true;
                    }
                    for (int j = 0; !foundInRecent && j < recentCount; ++j) {
                        RecentSearchObject o = filtered2RecentSearchObjects.get(j);
                        if (o != null && o.did == dialogId) {
                            foundInRecent = true;
                        }
                    }
                    if (foundInRecent) {
                        result.remove(a);
                        names.remove(a);
                        a--;
                    }
                }
            }
            MessagesController.getInstance(currentAccount).putUsers(encUsers, true);
            searchResult = result;
            searchResultNames = names;
         //   searchContacts = contacts;
            searchAdapterHelper.mergeResults(searchResult, filtered2RecentSearchObjects);
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

    public void searchDialogs(String text, int folderId, boolean allowPublicPosts) {
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
        if (searchHashtagRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchHashtagRunnable);
            searchHashtagRunnable = null;
        }
        if (searchHashtagRequest >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(searchHashtagRequest, true);
            searchHashtagRequest = -1;
        }
        String query;
        if (text != null) {
            query = text.trim();
        } else {
            query = null;
        }
        filterRecent(query);
        if (!TextUtils.equals(sponsoredQuery, query)) {
            sponsoredQuery = query;
            sponsoredPeers.clear();
            if (sponsoredReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(sponsoredReqId, true);
                sponsoredReqId = 0;
            }
            if (query == null || query.length() < 4 || UserConfig.getInstance(currentAccount).isPremium() && MessagesController.getInstance(currentAccount).isSponsoredDisabled()) {
                sponsoredQuery = null;
            } else {
                final TLRPC.TL_contacts_getSponsoredPeers req = new TLRPC.TL_contacts_getSponsoredPeers();
                req.q = sponsoredQuery = query;
                sponsoredReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    sponsoredReqId = 0;
                    if (res instanceof TLRPC.TL_contacts_sponsoredPeersEmpty) {
                        if (!sponsoredPeers.isEmpty()) {
                            sponsoredPeers.clear();
                            notifyDataSetChanged();
                        }
                    } else if (res instanceof TLRPC.TL_contacts_sponsoredPeers) {
                        final TLRPC.TL_contacts_sponsoredPeers r = (TLRPC.TL_contacts_sponsoredPeers) res;
                        MessagesController.getInstance(currentAccount).putUsers(r.users, true);
                        MessagesController.getInstance(currentAccount).putChats(r.chats, true);
                        sponsoredPeers.addAll(r.peers);
                        notifyDataSetChanged();
                    }
                }));
            }
        }
        if (TextUtils.isEmpty(query)) {
            filteredRecentQuery = null;
            searchAdapterHelper.unloadRecentHashtags();
            searchResult.clear();
            searchResultNames.clear();
            searchResultHashtags.clear();
            publicPostsTotalCount = 0;
            publicPostsLastRate = 0;
            publicPostsHashtag = null;
            publicPosts.clear();
            searchAdapterHelper.mergeResults(null, null);
            if (dialogsType != DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
                searchAdapterHelper.queryServerSearch(
                    null,
                    true,
                    true,
                    dialogsType != DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS,
                    dialogsType != DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS,
                    dialogsType == DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO || dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS,
                    0,
                    dialogsType == DialogsActivity.DIALOGS_TYPE_DEFAULT,
                    0,
                    0,
                    delegate != null ? delegate.getSearchForumDialogId() : 0
                );
            }
            searchWas = false;
            lastSearchId = 0;
            waitingResponseCount = 0;
            globalSearchCollapsed = true;
            phoneCollapsed = true;
            if (delegate != null) {
                delegate.searchStateChanged(false, true);
            }
            if (dialogsType != DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
                searchTopics(null);
                searchMessagesInternal(null, 0);
                searchForumMessagesInternal(null, 0);
            }
            notifyDataSetChanged();
            localTipDates.clear();
            localTipArchive = false;
            if (filtersDelegate != null) {
                filtersDelegate.updateFiltersView(false, null, localTipDates, localTipArchive);
            }
        } else {
            searchAdapterHelper.mergeResults(searchResult, filtered2RecentSearchObjects);
            publicPostsTotalCount = 0;
            publicPostsLastRate = 0;
            publicPostsHashtag = null;
            publicPosts.clear();
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

            lastSearchId++;
            final int searchId = lastSearchId;
            waitingResponseCount = 3;
            globalSearchCollapsed = true;
            phoneCollapsed = true;
            notifyDataSetChanged();
            if (delegate != null) {
                delegate.searchStateChanged(true, false);
            }

            String hashtag = null, hashtagUsername = null;
            if (allowPublicPosts && query != null) {
                String tquery = query.trim();
                if (tquery.length() > 1 && (tquery.charAt(0) == '#' || tquery.charAt(0) == '$')) {
                    int atIndex = tquery.indexOf('@');
                    hashtag = tquery.substring(1);
                    if (atIndex >= 0) {
                        hashtagUsername = tquery.substring(atIndex + 1);
                    }
                }
            }

//            if (hashtagUsername != null) {
//                TLObject chat = MessagesController.getInstance(currentAccount).getUserOrChat(hashtagUsername);
//                if (chat == null) {
//
//                }
//                TLRPC.TL_messages_search
//                return;
//            }

            Utilities.searchQueue.postRunnable(searchRunnable = () -> {
                searchRunnable = null;
                searchDialogsInternal(query, searchId);
                if (dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
                    waitingResponseCount -= 2;
                    return;
                }
                AndroidUtilities.runOnUIThread(searchRunnable2 = () -> {
                    searchRunnable2 = null;
                    if (searchId != lastSearchId) {
                        return;
                    }
                    if (needMessagesSearch != 2 && dialogsType != DialogsActivity.DIALOGS_TYPE_GROUPS_ONLY && dialogsType != DialogsActivity.DIALOGS_TYPE_CHANNELS_ONLY && delegate.getSearchForumDialogId() == 0) {
                        searchAdapterHelper.queryServerSearch(
                            query,
                            true,
                            dialogsType != DialogsActivity.DIALOGS_TYPE_USERS_ONLY,
                            true,
                            dialogsType != DialogsActivity.DIALOGS_TYPE_USERS_ONLY && dialogsType != DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS,
                            dialogsType == DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO || dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_SHARE,
                            0,
                            dialogsType == DialogsActivity.DIALOGS_TYPE_DEFAULT,
                            0,
                            searchId,
                            delegate != null ? delegate.getSearchForumDialogId() : 0
                        );
                    } else {
                        waitingResponseCount -= 2;
                    }
                    if (needMessagesSearch == 0 || dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
                        waitingResponseCount--;
                    } else {
                        searchTopics(text);
                        searchMessagesInternal(text, searchId);
                        searchForumMessagesInternal(text, searchId);
                    }
                });
            }, 300);

            final String finalHashtag = hashtag;

            if (finalHashtag != null) {
                waitingResponseCount++;
                AndroidUtilities.runOnUIThread(searchHashtagRunnable = () -> {
                    searchHashtagRunnable = null;
                    if (searchId != lastSearchId) {
                        return;
                    }
                    if (searchHashtagRequest >= 0) {
                        ConnectionsManager.getInstance(currentAccount).cancelRequest(searchHashtagRequest, true);
                    }
                    TLRPC.TL_channels_searchPosts req = new TLRPC.TL_channels_searchPosts();
                    req.hashtag = finalHashtag;
                    req.limit = 3;
                    req.offset_peer = new TLRPC.TL_inputPeerEmpty();
                    searchHashtagRequest = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (searchId != lastSearchId) {
                            return;
                        }
                        if (res instanceof TLRPC.messages_Messages) {
                            TLRPC.messages_Messages msgs = (TLRPC.messages_Messages) res;
                            int totalCount = 0;
                            if (msgs instanceof TLRPC.TL_messages_messages) {
                                totalCount = ((TLRPC.TL_messages_messages) msgs).messages.size();
                            } else if (msgs instanceof TLRPC.TL_messages_messagesSlice) {
                                totalCount = ((TLRPC.TL_messages_messagesSlice) msgs).count;
                            }
                            publicPostsTotalCount = totalCount;
                            publicPostsLastRate = msgs.next_rate;
                            publicPostsHashtag = finalHashtag;
                            MessagesController.getInstance(currentAccount).putUsers(msgs.users, false);
                            MessagesController.getInstance(currentAccount).putChats(msgs.chats, false);
                            for (int i = 0; i < msgs.messages.size(); ++i) {
                                TLRPC.Message msg = msgs.messages.get(i);
                                publicPosts.add(new MessageObject(currentAccount, msg, false, true));
                            }
                            if (delegate != null) {
                                delegate.searchStateChanged(waitingResponseCount > 0, true);
                            }
                            notifyDataSetChanged();
                        }
                    }));
                }, 300);
            }
        }
    }

    public int getRecentItemsCount() {
        ArrayList<RecentSearchObject> recent = searchWas ? filtered2RecentSearchObjects : filteredRecentSearchObjects;
        return (!recent.isEmpty() ? recent.size() + 1 : 0) + (hasHints() ? 1 : 0);
    }

    public int getRecentResultsCount() {
        ArrayList<RecentSearchObject> recent = searchWas ? filtered2RecentSearchObjects : filteredRecentSearchObjects;
        return recent != null ? recent.size() : 0;
    }

    @Override
    public int getItemCount() {
        if (waitingResponseCount == 3) {
            return 0;
        }
        int count = 0;
        if (!publicPosts.isEmpty()) {
            count += publicPosts.size() + 1;
        }
        if (!searchResultHashtags.isEmpty()) {
            count += searchResultHashtags.size() + 1;
            return count;
        }
        if (isRecentSearchDisplayed()) {
            count += getRecentItemsCount();
            if (!searchWas) {
                return count;
            }
        }
        if (!searchTopics.isEmpty()) {
            count++;
            count += searchTopics.size();
        }
        if (!searchContacts.isEmpty()) {
            int contactsCount = searchContacts.size();
            count += contactsCount + 1;
        }

        int resultsCount = searchResult.size();
        count += resultsCount;
        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
        count += localServerCount;
        int globalCount = searchAdapterHelper.getGlobalSearch().size() + sponsoredPeers.size();
        if (globalCount > 3 && globalSearchCollapsed) {
            globalCount = 3;
        }
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        if (phoneCount > 3 && phoneCollapsed) {
            phoneCount = 3;
        }
        if (resultsCount + localServerCount > 0 && (getRecentItemsCount() > 0 || !searchTopics.isEmpty() || !publicPosts.isEmpty())) {
            count++;
        }
        if (globalCount != 0) {
            count += globalCount + 1;
        }
        if (phoneCount != 0) {
            count += phoneCount;
        }
        int localMessagesCount = searchForumResultMessages.size();
        if (localMessagesCount != 0) {
            count += 1 + localMessagesCount + (localMessagesSearchEndReached ? 0 : 1);
        }
        if (!localMessagesSearchEndReached) {
            localMessagesLoadingRow = count;
        }
        int messagesCount = searchResultMessages.size();
        if ((currentMessagesFilter != Filter.All || forceLoadingMessages) && searchResultMessages.isEmpty()) {
            messagesCount = forceLoadingMessages ? 3 : 1;
        }
        if (!searchForumResultMessages.isEmpty() && !localMessagesSearchEndReached) {
            messagesCount = 0;
        }
        if (messagesCount != 0) {
            count += 1 + messagesCount + (messagesSearchEndReached ? 0 : 1);
        }
        if (localMessagesSearchEndReached) {
            localMessagesLoadingRow = count;
        }
        return currentItemCount = count;
    }

    public Object getItem(int i) {
        if (!publicPosts.isEmpty()) {
            if (i > 0 && i - 1 < publicPosts.size()) {
                return publicPosts.get(i - 1);
            }
            i -= (publicPosts.size() + 1);
        }
        if (!searchResultHashtags.isEmpty()) {
            if (i > 0) {
                return searchResultHashtags.get(i - 1);
            } else {
                return null;
            }
        }
        if (isRecentSearchDisplayed()) {
            int offset = (hasHints() ? 1 : 0);
            ArrayList<RecentSearchObject> recent = searchWas ? filtered2RecentSearchObjects : filteredRecentSearchObjects;
            if (i > offset && i - 1 - offset < recent.size()) {
                TLObject object = recent.get(i - 1 - offset).object;
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
                i -= getRecentItemsCount();
            }
        }
        if (!searchTopics.isEmpty()) {
            if (i > 0 && i <= searchTopics.size()) {
                return searchTopics.get(i - 1);
            }
            i -= 1 + searchTopics.size();
        }
        if (!searchContacts.isEmpty()) {
            if (i > 0 && i <= searchContacts.size()) {
                return searchContacts.get(i - 1);
            }
            i -= 1 + searchContacts.size();
        }
        ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
        ArrayList<TLObject> localServerSearch = searchAdapterHelper.getLocalServerSearch();
        ArrayList<Object> phoneSearch = searchAdapterHelper.getPhoneSearch();
        int localCount = searchResult.size();
        int localServerCount = localServerSearch.size();
        if (localCount + localServerCount > 0 && (getRecentItemsCount() > 0 || !searchTopics.isEmpty() || !publicPosts.isEmpty())) {
            if (i == 0) {
                return null;
            }
            i--;
        }
        int phoneCount = phoneSearch.size();
        if (phoneCount > 3 && phoneCollapsed) {
            phoneCount = 3;
        }
        int globalCount = globalSearch.isEmpty() && sponsoredPeers.isEmpty() ? 0 : globalSearch.size() + sponsoredPeers.size() + 1;
        if (globalCount > 4 && globalSearchCollapsed) {
            globalCount = 4;
        }
        if (i >= 0 && i < localCount) {
            return searchResult.get(i);
        }
        i -= localCount;
        if (i >= 0 && i < localServerCount) {
            return localServerSearch.get(i);
        }
        i -= localServerCount;
        if (i >= 0 && i < phoneCount) {
            return phoneSearch.get(i);
        }
        i -= phoneCount;
        if (i > 0 && i < globalCount) {
            i--;
            if (i >= 0 && i < sponsoredPeers.size()) {
                return sponsoredPeers.get(i);
            }
            i -= sponsoredPeers.size();
            if (i >= 0 && i < globalSearch.size()) {
                return globalSearch.get(i);
            }
        } else {
            i -= globalCount;
        }
        int localMessagesCount = searchForumResultMessages.isEmpty() ? 0 : searchForumResultMessages.size() + 1;
        if (i > 0 && i <= searchForumResultMessages.size()) {
            return searchForumResultMessages.get(i - 1);
        }
        i -= localMessagesCount + (!localMessagesSearchEndReached && !searchForumResultMessages.isEmpty() ? 1 : 0);
        int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
        if (i > 0 && i <= searchResultMessages.size()) {
            return searchResultMessages.get(i - 1);
        }
        // i -= messagesCount;
        return null;
    }

    public boolean isGlobalSearch(int i) {
        if (!searchWas) {
            return false;
        }
        if (!searchResultHashtags.isEmpty()) {
            return false;
        }
        if (!publicPosts.isEmpty()) {
            i -= 1 + publicPosts.size();
        }
        if (isRecentSearchDisplayed()) {
            int offset = (hasHints() ? 1 : 0);
            ArrayList<RecentSearchObject> recent = searchWas ? filtered2RecentSearchObjects : filteredRecentSearchObjects;
            if (i > offset && i - 1 - offset < recent.size()) {
                return false;
            } else {
                i -= getRecentItemsCount();
            }
        }
        ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
        ArrayList<TLObject> localServerSearch = searchAdapterHelper.getLocalServerSearch();
        int localCount = searchResult.size();
        int localServerCount = localServerSearch.size();
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        if (phoneCount > 3 && phoneCollapsed) {
            phoneCount = 3;
        }
        int globalCount = globalSearch.isEmpty() && sponsoredPeers.isEmpty() ? 0 : globalSearch.size() + sponsoredPeers.size() + 1;
        if (globalCount > 4 && globalSearchCollapsed) {
            globalCount = 4;
        }
        int contactsCount = searchContacts.size();
        if (contactsCount > 0) {
            if (i >= 0 && i < contactsCount) {
                return false;
            }
            i -= contactsCount + 1;
        }
        if (localCount + localServerCount > 0 && (getRecentItemsCount() > 0 || !searchTopics.isEmpty() || !publicPosts.isEmpty())) {
            if (i == 0) {
                return false;
            }
            i--;
        }
        if (i >= 0 && i < localCount) {
            return false;
        }
        i -= localCount;
        if (i >= 0 && i < localServerCount) {
            return false;
        }
        i -= localServerCount;
        if (i > 0 && i < phoneCount) {
            return false;
        }
        i -= phoneCount;
        if (i > 0 && i < globalCount) {
            return true;
        }
        i -= globalCount;
        int localMessagesCount = searchForumResultMessages.isEmpty() ? 0 : searchForumResultMessages.size() + 1;
        if (i > 0 && i < localMessagesCount) {
            return false;
        }
        i -= localMessagesCount;
        int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
        if ((currentMessagesFilter != Filter.All || forceLoadingMessages) && searchResultMessages.isEmpty()) {
            messagesCount = forceLoadingMessages ? 4 : 2;
        }
        if (i > 0 && i < messagesCount) {
            return false;
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
        return type != 1 && type != 4 && type != VIEW_TYPE_EMPTY_RESULT;
    }

    private EmptyLayout messagesEmptyLayout;

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_PROFILE_CELL:
                view = new ProfileSearchCell(mContext).showPremiumBlock(dialogsType == DialogsActivity.DIALOGS_TYPE_FORWARD);
                break;
            case VIEW_TYPE_GRAY_SECTION:
                view = new GraySectionCell(mContext);
                break;
            case VIEW_TYPE_DIALOG_CELL:
            case VIEW_TYPE_PUBLIC_POST:
                view = new DialogCell(null, mContext, false, true) {
                    @Override
                    public boolean isForumCell() {
                        return false;
                    }
                };
                break;
            case VIEW_TYPE_TOPIC_CELL:
                view = new TopicSearchCell(mContext);
                break;
            case VIEW_TYPE_LOADING:
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_TYPE);
                flickerLoadingView.setIsSingleCell(true);
                view = flickerLoadingView;
                break;
            case VIEW_TYPE_HASHTAG_CELL:
                view = new HashtagSearchCell(mContext);
                break;
            case VIEW_TYPE_CATEGORY_LIST:
                RecyclerListView horizontalListView = new RecyclerListView(mContext) {
                    @Override
                    public boolean onInterceptTouchEvent(MotionEvent e) {
                        if (getParent() != null && getParent().getParent() != null) {
                            getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1) || canScrollHorizontally(1));
                        }
                        return super.onInterceptTouchEvent(e);
                    }
                };
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
                horizontalListView.setAdapter(new CategoryAdapterRecycler(mContext, currentAccount, false, dialogsType == DialogsActivity.DIALOGS_TYPE_FORWARD, resourcesProvider));
                horizontalListView.setOnItemClickListener((view1, position) -> {
                    if (view1 instanceof HintDialogCell && ((HintDialogCell) view1).isBlocked()) {
                        if (delegate != null) {
                            delegate.didPressedBlockedDialog(view1, ((HintDialogCell) view1).getDialogId());
                        }
                        return;
                    }
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
            case VIEW_TYPE_INVITE_CONTACT_CELL:
                view = new ProfileSearchCell(mContext);
                break;
            case VIEW_TYPE_EMPTY_RESULT:
                view = messagesEmptyLayout = new EmptyLayout(mContext, resourcesProvider, () -> {
                    currentMessagesFilter = Filter.All;
                    searchResultMessages.clear();
                    if (messagesSectionPosition >= 0 && messagesSectionPosition < getItemCount()) {
                        notifyItemChanged(messagesSectionPosition);
                    }
                    loadMoreSearchMessages();
                });
                messagesEmptyLayout.setQuery(lastMessagesSearchString);
                break;
            case VIEW_TYPE_ADD_BY_PHONE:
            default:
                view = new TextCell(mContext, 16, false);
                break;
        }
        if (viewType == 5) {
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp(86)));
        } else {
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        }
        return new RecyclerListView.Holder(view);
    }

    private boolean hasHints() {
        return !searchWas && !MediaDataController.getInstance(currentAccount).hints.isEmpty() && (dialogsType != DialogsActivity.DIALOGS_TYPE_START_ATTACH_BOT || dialogsActivity.allowUsers);
    }

    private int messagesSectionPosition = -1;

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_PROFILE_CELL: {
                ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                long oldDialogId = cell.getDialogId();

                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                TLRPC.EncryptedChat encryptedChat = null;
                CharSequence username = null;
                CharSequence name = null;
                boolean isRecent = false;
                boolean isGlobal = isGlobalSearch(position);
                String un = null;
                ArrayList<TLRPC.TL_username> usernames = null;
                Object obj = getItem(position);

                if (obj instanceof TLRPC.TL_sponsoredPeer) {
                    final TLRPC.TL_sponsoredPeer sponsoredPeer = (TLRPC.TL_sponsoredPeer) obj;
                    seenSponsoredPeer(sponsoredPeer);
                    final long dialogId = DialogObject.getPeerDialogId(sponsoredPeer.peer);
                    if (dialogId >= 0) {
                        user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                        if (user != null) {
                            usernames = user.usernames;
                            un = DialogObject.getPublicUsername(user, currentMessagesQuery);
                        }
                    } else {
                        chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                        if (chat != null) {
                            usernames = chat.usernames;
                            un = DialogObject.getPublicUsername(chat, currentMessagesQuery);
                        }
                    }
                } else if (obj instanceof TLRPC.User) {
                    user = (TLRPC.User) obj;
                    usernames = user.usernames;
                    un = DialogObject.getPublicUsername(user, currentMessagesQuery);
                } else if (obj instanceof TLRPC.Chat) {
                    chat = MessagesController.getInstance(currentAccount).getChat(((TLRPC.Chat) obj).id);
                    if (chat == null) {
                        chat = (TLRPC.Chat) obj;
                    }
                    usernames = chat.usernames;
                    un = DialogObject.getPublicUsername(chat, currentMessagesQuery);
                } else if (obj instanceof TLRPC.EncryptedChat) {
                    encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(((TLRPC.EncryptedChat) obj).id);
                    user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                }

                if (!publicPosts.isEmpty()) {
                    position -= publicPosts.size() + 1;
                }
                if (isRecentSearchDisplayed()) {
                    if (position < getRecentItemsCount()) {
                        cell.useSeparator = position != getRecentItemsCount() - 1;
                        isRecent = true;
                    }
                    position -= getRecentItemsCount();
                }
                if (!searchTopics.isEmpty()) {
                    position -= 1 + searchTopics.size();
                }
                ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
                ArrayList<Object> phoneSearch = searchAdapterHelper.getPhoneSearch();
                int localCount = searchResult.size();
                int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
                if (localCount + localServerCount > 0 && (getRecentItemsCount() > 0 || !searchTopics.isEmpty() || !publicPosts.isEmpty())) {
                    position--;
                }
                int phoneCount = phoneSearch.size();
                if (phoneCount > 3 && phoneCollapsed) {
                    phoneCount = 3;
                }
                int phoneCount2 = phoneCount;
                if (phoneCount > 0 && phoneSearch.get(phoneCount - 1) instanceof String) {
                    phoneCount2 -= 2;
                }
                int globalCount = globalSearch.isEmpty() && sponsoredPeers.isEmpty() ? 0 : globalSearch.size() + sponsoredPeers.size() + 1;
                if (globalCount > 4 && globalSearchCollapsed) {
                    globalCount = 4;
                }
                if (!isRecent) {
                    cell.useSeparator = (
                        position != getItemCount() - getRecentItemsCount() - 1 &&
                        position != localCount + phoneCount2 + localServerCount - 1 &&
                        position != localCount + globalCount + phoneCount + localServerCount - 1
                    );
                }
                if (position >= 0 && position < searchResult.size() && user == null) {
                    name = searchResultNames.get(position);
                    String username1 = UserObject.getPublicUsername(user);
                    if (name != null && user != null && username1 != null) {
                        if (name.toString().startsWith("@" + username1)) {
                            username = name;
                            name = null;
                        }
                    }
                }
                if (username == null) {
                    String foundUserName = isRecent ? filteredRecentQuery : searchAdapterHelper.getLastFoundUsername();
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
                        }
                        if (usernames != null && usernames.size() > 1) {
                            String query = foundUserName;
                            if (query.startsWith("@"))
                                query = query.substring(1);
                            String matchedUsername = null;
                            for (TLRPC.TL_username u : usernames) {
                                if (!u.active) continue;
                                if (u.username.startsWith(query)) {
                                    matchedUsername = u.username;
                                    break;
                                }
                            }
                            if (matchedUsername == null) {
                                for (TLRPC.TL_username u : usernames) {
                                    if (!u.active) continue;
                                    if (u.username.contains(query)) {
                                        matchedUsername = u.username;
                                        break;
                                    }
                                }
                            }
                            if (matchedUsername != null) {
                                un = matchedUsername;
                            }
                        }
                        if (un != null && (user == null || isGlobal)) {
                            if (foundUserName.startsWith("@")) {
                                foundUserName = foundUserName.substring(1);
                            }
                            try {
                                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                                spannableStringBuilder.append("@");
                                spannableStringBuilder.append(un);
                                boolean hasMatch = (index = AndroidUtilities.indexOfIgnoreCase(un, foundUserName)) != -1;
                                if (hasMatch) {
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
                boolean savedMessages = false;
                if (user != null && user.id == selfUserId && dialogsType != DialogsActivity.DIALOGS_TYPE_BOT_SELECT_VERIFY) {
                    name = LocaleController.getString(R.string.SavedMessages);
                    username = null;
                    savedMessages = true;
                }
                if (chat != null && chat.participants_count != 0) {
                    String membersString;
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        membersString = LocaleController.formatPluralStringSpaced("Subscribers", chat.participants_count);
                    } else {
                        membersString = LocaleController.formatPluralStringSpaced("Members", chat.participants_count);
                    }
                    if (username instanceof SpannableStringBuilder) {
                        ((SpannableStringBuilder) username).append(", ").append(membersString);
                    } else if (!TextUtils.isEmpty(username)) {
                        username = TextUtils.concat(username, ", ", membersString);
                    } else {
                        username = membersString;
                    }
                } else if (user != null && user.bot && user.bot_active_users != 0) {
                    String membersString = LocaleController.formatPluralStringSpaced("BotUsersShort", user.bot_active_users);
                    if (username instanceof SpannableStringBuilder) {
                        ((SpannableStringBuilder) username).append(", ").append(membersString);
                    } else if (!TextUtils.isEmpty(username)) {
                        username = TextUtils.concat(username, ", ", membersString);
                    } else {
                        username = membersString;
                    }
                }
                cell.allowBotOpenButton(isRecent, this::openBotApp);
                cell.setOnSponsoredOptionsClick(this::openSponsoredOptions);
                cell.setAd(obj instanceof TLRPC.TL_sponsoredPeer ? (TLRPC.TL_sponsoredPeer) obj : null);
                cell.setData(user != null ? user : chat, encryptedChat, name, username, true, savedMessages);
                cell.setChecked(delegate.isSelected(cell.getDialogId()), oldDialogId == cell.getDialogId());
                break;
            }
            case VIEW_TYPE_GRAY_SECTION: {
                final GraySectionCell cell = (GraySectionCell) holder.itemView;
                if (!searchResultHashtags.isEmpty()) {
                    cell.setText(LocaleController.getString(R.string.Hashtags), LocaleController.getString(R.string.ClearButton), v -> {
                        if (delegate != null) {
                            delegate.needClearList();
                        }
                    });
                } else {
                    int rawPosition = position;
                    if (!publicPosts.isEmpty()) {
                        if (position == 0) {
                            cell.setText(LocaleController.getString(R.string.PublicPostsTabs), AndroidUtilities.replaceArrows(LocaleController.getString(R.string.PublicPostsMore), false, dp(-2), dp(1)), v -> {
                                openPublicPosts();
                            });
                            return;
                        }
                        position -= 1 + publicPosts.size();
                    }
                    ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
                    if (isRecentSearchDisplayed() || !searchTopics.isEmpty() || !searchContacts.isEmpty() || !publicPosts.isEmpty()) {
                        int offset = hasHints() ? 1 : 0;
                        if (position < offset) {
                            cell.setText(LocaleController.getString(R.string.ChatHints));
                            return;
                        } else if (position == offset && isRecentSearchDisplayed()) {
                            if (!searchWas) {
                                cell.setText(LocaleController.getString(R.string.Recent), LocaleController.getString(R.string.ClearButton), v -> {
                                    if (delegate != null) {
                                        delegate.needClearList();
                                    }
                                });
                            } else {
                                cell.setText(LocaleController.getString(R.string.Recent), LocaleController.getString(R.string.Clear), v -> {
                                    if (delegate != null) {
                                        delegate.needClearList();
                                    }
                                });
                            }
                            return;
                        } else if (position == getRecentItemsCount() + (searchTopics.isEmpty() ? 0 : searchTopics.size() + 1) + (searchContacts.isEmpty() ? 0 : searchContacts.size() + 1) && !searchResult.isEmpty()) {
                            cell.setText(LocaleController.getString(R.string.SearchAllChatsShort));
                            return;
                        } else {
                            position -= getRecentItemsCount();
                        }
                    }
                    int localCount = searchResult.size();
                    int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
                    int phoneCount = searchAdapterHelper.getPhoneSearch().size();
                    if (phoneCount > 3 && phoneCollapsed) {
                        phoneCount = 3;
                    }
                    int globalCount = globalSearch.isEmpty() && sponsoredPeers.isEmpty() ? 0 : globalSearch.size() + sponsoredPeers.size() + 1;
                    if (globalCount > 4 && globalSearchCollapsed) {
                        globalCount = 4;
                    }
                    int localMessagesCount = searchForumResultMessages.isEmpty() ? 0 : searchForumResultMessages.size() + 1;
                    int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
                    if ((currentMessagesFilter != Filter.All || forceLoadingMessages) && searchResultMessages.isEmpty()) {
                        messagesCount = forceLoadingMessages ? 4 : 2;
                    }
                    String title = null;
                    boolean showMore = false;
                    Runnable onClick = null;
                    CharSequence customRightText = null;
                    if (!searchTopics.isEmpty()) {
                        if (position == 0) {
                            title = LocaleController.getString(R.string.Topics);
                        }
                        position -= 1 + searchTopics.size();
                    }
                    if (!searchContacts.isEmpty()) {
                        if (position == 0) {
                            title = LocaleController.getString(R.string.InviteToTelegramShort);
                        }
                        position -= 1 + searchContacts.size();
                    }
                    if (title == null) {
                        position -= localCount + localServerCount;
                        if (position >= 0 && position < phoneCount) {
                            title = LocaleController.getString(R.string.PhoneNumberSearch);
                            if (searchAdapterHelper.getPhoneSearch().size() > 3) {
                                showMore = phoneCollapsed;
                                onClick = () -> {
                                    phoneCollapsed = !phoneCollapsed;
                                    cell.setRightText(phoneCollapsed ? LocaleController.getString(R.string.ShowMore) : LocaleController.getString(R.string.ShowLess));
                                    notifyDataSetChanged();
                                };
                            }
                        } else {
                            position -= phoneCount;
                            if (position >= 0 && position < globalCount) {
                                title = LocaleController.getString(R.string.GlobalSearch);
                                if (searchAdapterHelper.getGlobalSearch().size() + sponsoredPeers.size() > 3) {
                                    showMore = globalSearchCollapsed;
                                    onClick = () -> {
                                        final long now = SystemClock.elapsedRealtime();
                                        if (now - lastShowMoreUpdate < 300) {
                                            return;
                                        }
                                        lastShowMoreUpdate = now;

                                        int totalGlobalCount = globalSearch.isEmpty() && sponsoredPeers.isEmpty() ? 0 : globalSearch.size() + sponsoredPeers.size();
                                        boolean disableRemoveAnimation = getItemCount() > rawPosition + Math.min(totalGlobalCount, globalSearchCollapsed ? 4 : Integer.MAX_VALUE) + 1;
                                        if (itemAnimator != null) {
                                            itemAnimator.setAddDuration(disableRemoveAnimation ? 45 : 200);
                                            itemAnimator.setRemoveDuration(disableRemoveAnimation ? 80 : 200);
                                            itemAnimator.setRemoveDelay(disableRemoveAnimation ? 270 : 0);
                                        }
                                        globalSearchCollapsed = !globalSearchCollapsed;
                                        cell.setRightTextMargin(16);
                                        cell.setRightText(globalSearchCollapsed ? LocaleController.getString(R.string.ShowMore) : LocaleController.getString(R.string.ShowLess), globalSearchCollapsed);
                                        showMoreHeader = null;
                                        View parent = (View) cell.getParent();
                                        if (parent instanceof RecyclerView) {
                                            RecyclerView listView = (RecyclerView) parent;
                                            final int nextGraySectionPosition = !globalSearchCollapsed ? rawPosition + 4 : rawPosition + totalGlobalCount + 1;
                                            for (int i = 0; i < listView.getChildCount(); ++i) {
                                                View child = listView.getChildAt(i);
                                                if (listView.getChildAdapterPosition(child) == nextGraySectionPosition) {
                                                    showMoreHeader = child;
                                                    break;
                                                }
                                            }
                                        }
                                        if (!globalSearchCollapsed) {
                                            notifyItemChanged(rawPosition + 3);
                                            notifyItemRangeInserted(rawPosition + 4, (totalGlobalCount - 3));
                                        } else {
                                            notifyItemRangeRemoved(rawPosition + 4, (totalGlobalCount - 3));
                                            if (disableRemoveAnimation) {
                                                AndroidUtilities.runOnUIThread(() -> notifyItemChanged(rawPosition + 3), 350);
                                            } else {
                                                notifyItemChanged(rawPosition + 3);
                                            }
                                        }

                                        if (cancelShowMoreAnimation != null) {
                                            AndroidUtilities.cancelRunOnUIThread(cancelShowMoreAnimation);
                                        }
                                        if (disableRemoveAnimation) {
                                            showMoreAnimation = true;
                                            AndroidUtilities.runOnUIThread(cancelShowMoreAnimation = () -> {
                                                showMoreAnimation = false;
                                                showMoreHeader = null;
                                                if (parent != null) {
                                                    parent.invalidate();
                                                }
                                            }, 400);
                                        } else {
                                            showMoreAnimation = false;
                                        }
                                    };
                                }
                            } else if (delegate != null && localMessagesCount > 0 && position - globalCount <= 1) {
                                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-delegate.getSearchForumDialogId());
                                title = LocaleController.formatString(R.string.SearchMessagesIn, (chat == null ? "null" : chat.title));
                            } else {
                                messagesSectionPosition = position;
                                customRightText = getFilterFromString(currentMessagesFilter);
                                onClick = () -> {
                                    ItemOptions o = ItemOptions.makeOptions(dialogsActivity, cell);
                                    for (Filter f : Filter.values()) {
                                        final boolean isCurrent = f.flags == currentMessagesFilter.flags;
                                        o.addChecked(isCurrent, LocaleController.getString(f.strResId), () -> {
                                            if (isCurrent) return;
                                            cell.setRightText(getFilterFromString(currentMessagesFilter = f));
                                            cell.setRightTextMargin(6);
                                            searchResultMessages.clear();
                                            forceLoadingMessages = true;
                                            notifyDataSetChanged();
                                            loadMoreSearchMessages();
                                        });
                                    }
                                    o
                                        .setGravity(Gravity.RIGHT)
                                        .setOnTopOfScrim()
                                        .setDrawScrim(false)
                                        .setDimAlpha(0)
                                        .show();
                                };
                                title = LocaleController.getString(R.string.SearchMessages);
                            }
                        }
                    }

                    if (onClick == null) {
                        cell.setText(title);
                    } else if (customRightText != null) {
                        final Runnable finalOnClick = onClick;
                        cell.setText(title, customRightText, e -> finalOnClick.run());
                        cell.setRightTextMargin(6);
                    } else {
                        final Runnable finalOnClick = onClick;
                        cell.setText(title, showMore ? LocaleController.getString(R.string.ShowMore) : LocaleController.getString(R.string.ShowLess), e -> finalOnClick.run());
                        cell.setRightTextMargin(16);
                    }
                }
                break;
            }
            case VIEW_TYPE_DIALOG_CELL:
            case VIEW_TYPE_PUBLIC_POST: {
                DialogCell cell = (DialogCell) holder.itemView;
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                cell.useSeparator = (position != getItemCount() - 1);
                MessageObject messageObject = (MessageObject) getItem(position);
                boolean isLocalForum = searchForumResultMessages.contains(messageObject);
                cell.useFromUserAsAvatar = isLocalForum;
                if (messageObject == null) {
                    cell.setDialog(0, null, 0, false, false);
                } else {
                    cell.setDialog(messageObject.getDialogId(), messageObject, messageObject.messageOwner.date, false, false);
                }
                break;
            }
            case VIEW_TYPE_TOPIC_CELL: {
                TopicSearchCell topicSearchCell = (TopicSearchCell) holder.itemView;
                topicSearchCell.setTopic((TLRPC.TL_forumTopic) getItem(position));
                break;
            }
            case VIEW_TYPE_HASHTAG_CELL: {
                HashtagSearchCell cell = (HashtagSearchCell) holder.itemView;
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                cell.setText(searchResultHashtags.get(position - 1));
                cell.setNeedDivider(position != searchResultHashtags.size());
                break;
            }
            case VIEW_TYPE_CATEGORY_LIST: {
                RecyclerListView recyclerListView = (RecyclerListView) holder.itemView;
                ((CategoryAdapterRecycler) recyclerListView.getAdapter()).setIndex(position / 2);
                break;
            }
            case VIEW_TYPE_ADD_BY_PHONE: {
                String str = (String) getItem(position);
                TextCell cell = (TextCell) holder.itemView;
                cell.setColors(-1, Theme.key_windowBackgroundWhiteBlueText2);
                cell.setText(LocaleController.formatString("AddContactByPhone", R.string.AddContactByPhone, PhoneFormat.getInstance().format("+" + str)), false);
                break;
            }
            case VIEW_TYPE_INVITE_CONTACT_CELL: {
                ProfileSearchCell profileSearchCell = (ProfileSearchCell) holder.itemView;
                ContactsController.Contact contact = (ContactsController.Contact) getItem(position);
                profileSearchCell.setData(contact, null, ContactsController.formatName(contact.first_name, contact.last_name), PhoneFormat.getInstance().format("+" + contact.shortPhones.get(0)), false, false);
                break;
            }
        }
    }

    private ColoredImageSpan filterArrowsIcon;
    private CharSequence getFilterFromString(Filter filter) {
        SpannableStringBuilder sb = new SpannableStringBuilder(LocaleController.getString(filter.strFromResId));
        sb.append("v");
//        if (filterArrowsIcon == null) {
            filterArrowsIcon = new ColoredImageSpan(R.drawable.arrows_select);
//        }
        sb.setSpan(filterArrowsIcon, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    boolean globalSearchCollapsed = true;
    boolean phoneCollapsed = true;

    @Override
    public int getItemViewType(int i) {
        if (!searchResultHashtags.isEmpty()) {
            return i == 0 ? VIEW_TYPE_GRAY_SECTION : VIEW_TYPE_HASHTAG_CELL;
        }
        if (!publicPosts.isEmpty()) {
            if (i == 0) return VIEW_TYPE_GRAY_SECTION;
            i--;
            if (i < publicPosts.size()) return VIEW_TYPE_PUBLIC_POST;
            i -= publicPosts.size();
        }
        if (isRecentSearchDisplayed()) {
            int offset = hasHints() ? 1 : 0;
            if (i < offset) {
                return VIEW_TYPE_CATEGORY_LIST;
            }
            if (i == offset) {
                return VIEW_TYPE_GRAY_SECTION;
            }
            if (i < getRecentItemsCount()) {
                return VIEW_TYPE_PROFILE_CELL;
            }
            i -= getRecentItemsCount();
        }
        if (!searchTopics.isEmpty()) {
            if (i == 0) {
                return VIEW_TYPE_GRAY_SECTION;
            } else if (i <= searchTopics.size()) {
                return VIEW_TYPE_TOPIC_CELL;
            }
            i -= 1 + searchTopics.size();
        }

        if (!searchContacts.isEmpty()) {
            if (i == 0) {
                return VIEW_TYPE_GRAY_SECTION;
            } else if (i <= searchContacts.size()) {
                return VIEW_TYPE_INVITE_CONTACT_CELL;
            }
            i -= 1 + searchContacts.size();
        }
        ArrayList<TLObject> globalSearch = searchAdapterHelper.getGlobalSearch();
        int localCount = searchResult.size();
        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
        if (localCount + localServerCount > 0 && (getRecentItemsCount() > 0 || !searchTopics.isEmpty() || !publicPosts.isEmpty())) {
            if (i == 0) {
                return VIEW_TYPE_GRAY_SECTION;
            }
            i--;
        }
        int phoneCount = searchAdapterHelper.getPhoneSearch().size();
        if (phoneCount > 3 && phoneCollapsed) {
            phoneCount = 3;
        }
        int globalCount = sponsoredPeers.isEmpty() && globalSearch.isEmpty() ? 0 : globalSearch.size() + sponsoredPeers.size() + 1;
        if (globalCount > 4 && globalSearchCollapsed) {
            globalCount = 4;
        }
        int messagesCount = searchResultMessages.isEmpty() ? 0 : searchResultMessages.size() + 1;
        if ((currentMessagesFilter != Filter.All || forceLoadingMessages) && searchResultMessages.isEmpty()) {
            messagesCount = forceLoadingMessages ? 4 : 2;
        }
        if (!searchForumResultMessages.isEmpty() && !localMessagesSearchEndReached) {
            messagesCount = 0;
        }
        int localMessagesCount = (searchForumResultMessages.isEmpty() ? 0 : searchForumResultMessages.size() + 1);

        if (i >= 0 && i < localCount) {
            return VIEW_TYPE_PROFILE_CELL;
        }
        i -= localCount;
        if (i >= 0 && i < localServerCount) {
            return VIEW_TYPE_PROFILE_CELL;
        }
        i -= localServerCount;
        if (i >= 0 && i < phoneCount) {
            Object object = getItem(i);
            if (object instanceof String) {
                String str = (String) object;
                if ("section".equals(str)) {
                    return VIEW_TYPE_GRAY_SECTION;
                } else {
                    return VIEW_TYPE_ADD_BY_PHONE;
                }
            }
            return VIEW_TYPE_PROFILE_CELL;
        }
        i -= phoneCount;
        if (i >= 0 && i < globalCount) {
            if (i == 0) {
                return VIEW_TYPE_GRAY_SECTION;
            } else {
                return VIEW_TYPE_PROFILE_CELL;
            }
        }
        i -= globalCount;
        if (localMessagesCount > 0) {
            if (i >= 0 && (localMessagesSearchEndReached ? i < localMessagesCount : i <= localMessagesCount)) {
                if (i == 0) {
                    return VIEW_TYPE_GRAY_SECTION;
                } else if (i == localMessagesCount) {
                    return VIEW_TYPE_LOADING;
                } else {
                    return VIEW_TYPE_DIALOG_CELL;
                }
            }
            i -= localMessagesCount + (!localMessagesSearchEndReached ? 1 : 0);
        }
        if (i >= 0 && i < messagesCount) {
            if (i == 0) {
                return VIEW_TYPE_GRAY_SECTION;
            } else if (forceLoadingMessages && searchResultMessages.isEmpty()) {
                return VIEW_TYPE_LOADING;
            } else if (currentMessagesFilter != Filter.All && searchResultMessages.isEmpty()) {
                return VIEW_TYPE_EMPTY_RESULT;
            } else {
                return VIEW_TYPE_DIALOG_CELL;
            }
        }
        return VIEW_TYPE_LOADING;
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

    public void filterRecent(String query) {
        filteredRecentQuery = query;
        filtered2RecentSearchObjects.clear();
        if (TextUtils.isEmpty(query)) {
            filteredRecentSearchObjects.clear();
            final int count = recentSearchObjects.size();
            for (int i = 0; i < count; ++i) {
                if (delegate != null && delegate.getSearchForumDialogId() == recentSearchObjects.get(i).did || !filter(recentSearchObjects.get(i).object)) {
                    continue;
                }
                filteredRecentSearchObjects.add(recentSearchObjects.get(i));
            }
            return;
        }
        String lowerCasedQuery = query.toLowerCase();
        final int count = recentSearchObjects.size();
        for (int i = 0; i < count; ++i) {
            RecentSearchObject obj = recentSearchObjects.get(i);
            if (obj == null || obj.object == null) {
                continue;
            }
            if (delegate != null && delegate.getSearchForumDialogId() == obj.did || !filter(recentSearchObjects.get(i).object)) {
                continue;
            }
            String title = null, username = null;
            if (obj.object instanceof TLRPC.Chat) {
                title = ((TLRPC.Chat) obj.object).title;
                username = ((TLRPC.Chat) obj.object).username;
            } else if (obj.object instanceof TLRPC.User) {
                title = UserObject.getUserName((TLRPC.User) obj.object);
                username = ((TLRPC.User) obj.object).username;
            } else if (obj.object instanceof TLRPC.ChatInvite) {
                title = ((TLRPC.ChatInvite) obj.object).title;
            }
            if (title != null && wordStartsWith(title.toLowerCase(), lowerCasedQuery) ||
                username != null && wordStartsWith(username.toLowerCase(), lowerCasedQuery)) {
                filtered2RecentSearchObjects.add(obj);
            }
            if (filtered2RecentSearchObjects.size() >= 5) {
                break;
            }
        }
    }

    private boolean wordStartsWith(String loweredTitle, String loweredQuery) {
        if (loweredQuery == null || loweredTitle == null) {
            return false;
        }
        String[] words = loweredTitle.toLowerCase().split(" ");
        boolean found = false;
        for (int j = 0; j < words.length; ++j) {
            if (words[j] != null && (words[j].startsWith(loweredQuery) || loweredQuery.startsWith(words[j]))) {
                found = true;
                break;
            }
        }
        return found;
    }

    public interface OnRecentSearchLoaded {
        void setRecentSearch(ArrayList<RecentSearchObject> arrayList, LongSparseArray<RecentSearchObject> hashMap);
    }

    private static class ContactEntry {
        String q1;
        String q2;
        ContactsController.Contact contact;
    }

    protected void openPublicPosts() {

    }

    protected void openBotApp(TLRPC.User bot) {

    }

    protected void openSponsoredOptions(ProfileSearchCell cell, TLRPC.TL_sponsoredPeer sponsoredPeer) {

    }

    private static class EmptyLayout extends LinearLayout {

        private TextView textView;
        public EmptyLayout(Context context, Theme.ResourcesProvider resourcesProvider, Runnable onClickAll) {
            super(context);

            setOrientation(LinearLayout.VERTICAL);

            BackupImageView imageView = new BackupImageView(context);
            imageView.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(120), dp(120)));
            addView(imageView, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 27, 0, 0));

            TextView titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(LocaleController.getString(R.string.SearchMessagesFilterEmptyTitle));
            titleView.setGravity(Gravity.CENTER);
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 9));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView.setText(LocaleController.formatString(R.string.SearchMessagesFilterEmptyText, ""));
            textView.setGravity(Gravity.CENTER);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

            TextView button = new TextView(context);
            button.setPadding(dp(12), dp(4), dp(12), dp(4));
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            button.setText(LocaleController.getString(R.string.SearchMessagesFilterEmptySearchAll));
            button.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(6, 0, Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .15f)));
            button.setOnClickListener(v -> onClickAll.run());
            addView(button, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 38));
        }

        public void setQuery(String query) {
            textView.setText(LocaleController.formatString(R.string.SearchMessagesFilterEmptyText, query));

        }

    }

    private int globalSearchPosition() {
        if (waitingResponseCount == 3) {
            return 0;
        }
        int count = 0;
        if (!publicPosts.isEmpty()) {
            count += publicPosts.size() + 1;
        }
        if (!searchResultHashtags.isEmpty()) {
            count += searchResultHashtags.size() + 1;
            return count;
        }
        if (isRecentSearchDisplayed()) {
            count += getRecentItemsCount();
            if (!searchWas) {
                return count;
            }
        }
        if (!searchTopics.isEmpty()) {
            count++;
            count += searchTopics.size();
        }
        if (!searchContacts.isEmpty()) {
            int contactsCount = searchContacts.size();
            count += contactsCount + 1;
        }

        int resultsCount = searchResult.size();
        count += resultsCount;
        int localServerCount = searchAdapterHelper.getLocalServerSearch().size();
        count += localServerCount;
        if (resultsCount + localServerCount > 0 && (getRecentItemsCount() > 0 || !searchTopics.isEmpty() || !publicPosts.isEmpty())) {
            count++;
        }
        return count;
    }

    public void removeAd(TLRPC.TL_sponsoredPeer peer) {
        if (sponsoredPeers.isEmpty()) return;
        int index = sponsoredPeers.indexOf(peer);
        if (index < 0) return;

        int globalSearchPosition = globalSearchPosition();
        if (globalSearchPosition >= getItemCount()) return;

        int wasGlobalCountUncollapsed = searchAdapterHelper.getGlobalSearch().size() + sponsoredPeers.size();
        int wasGlobalCount = wasGlobalCountUncollapsed;
        if (wasGlobalCount > 3 && globalSearchCollapsed) {
            wasGlobalCount = 3;
        }

        sponsoredPeers.remove(index);
        notifyItemRemoved(globalSearchPosition + 1 + index);

        int globalCountUncollapsed = searchAdapterHelper.getGlobalSearch().size() + sponsoredPeers.size();
        int globalCount = globalCountUncollapsed;
        if (globalCount > 3 && globalSearchCollapsed) {
            globalCount = 3;
        }

        if (globalCount > 0 && (wasGlobalCountUncollapsed > 3) != (globalCountUncollapsed > 3)) {
            notifyItemChanged(globalSearchPosition);
        }

        if (globalCount <= 0) {
            notifyItemRemoved(globalSearchPosition);
        } else if (globalSearchCollapsed) {
            notifyItemChanged(globalSearchPosition + 2);notifyItemRangeInserted(globalSearchPosition + 1 + (3 - 1), Math.min(Math.max(0, wasGlobalCountUncollapsed - 3), 1));
        }
    }

    public void removeAllAds() {
        if (sponsoredPeers.isEmpty()) return;

        int globalSearchPosition = globalSearchPosition();
        if (globalSearchPosition >= getItemCount()) return;

        int wasGlobalCountUncollapsed = searchAdapterHelper.getGlobalSearch().size() + sponsoredPeers.size();
        int wasGlobalCount = wasGlobalCountUncollapsed;
        if (wasGlobalCount > 3 && globalSearchCollapsed) {
            wasGlobalCount = 3;
        }

        int sponsoredCount = sponsoredPeers.size();
        if (globalSearchCollapsed) {
            sponsoredCount = Math.min(3, sponsoredCount);
        }
        sponsoredPeers.clear();
        notifyItemRangeRemoved(globalSearchPosition + 1, sponsoredCount);

        int globalCountUncollapsed = searchAdapterHelper.getGlobalSearch().size() + sponsoredPeers.size();
        int globalCount = globalCountUncollapsed;
        if (globalCount > 3 && globalSearchCollapsed) {
            globalCount = 3;
        }

        if (globalCount > 0 && (wasGlobalCountUncollapsed > 3) != (globalCountUncollapsed > 3)) {
            notifyItemChanged(globalSearchPosition);
        }

        if (globalCount <= 0) {
            notifyItemRemoved(globalSearchPosition);
        } else if (globalSearchCollapsed) {
            notifyItemChanged(globalSearchPosition + (3 - sponsoredCount));
            notifyItemRangeInserted(globalSearchPosition + 1 + (3 - sponsoredCount), Math.min(Math.max(0, wasGlobalCountUncollapsed - 3), sponsoredCount));
        }
    }

    public void seenSponsoredPeer(TLRPC.TL_sponsoredPeer sponsoredPeer) {
        if (sponsoredPeer == null) return;
        boolean sent = false;
        for (byte[] r : seenSponsoredPeers) {
            if (Arrays.equals(r, sponsoredPeer.random_id)) {
                sent = true;
                break;
            }
        }
        if (sent) return;

        seenSponsoredPeers.add(sponsoredPeer.random_id);
        TLRPC.TL_messages_viewSponsoredMessage req = new TLRPC.TL_messages_viewSponsoredMessage();
        req.random_id = sponsoredPeer.random_id;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
    }

    public void clickedSponsoredPeer(TLRPC.TL_sponsoredPeer sponsoredPeer) {
        if (sponsoredPeer == null) return;
        TLRPC.TL_messages_clickSponsoredMessage req = new TLRPC.TL_messages_clickSponsoredMessage();
        req.random_id = sponsoredPeer.random_id;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
    }
}
