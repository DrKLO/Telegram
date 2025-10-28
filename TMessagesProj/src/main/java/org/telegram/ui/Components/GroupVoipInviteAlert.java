/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.ChatUsersActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class GroupVoipInviteAlert extends UsersAlertBase {

    private final SearchAdapter searchAdapter;

    private int delayResults;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;

    private ArrayList<TLObject> participants = new ArrayList<>();
    private ArrayList<TLObject> contacts = new ArrayList<>();
    private boolean contactsEndReached;
    private LongSparseArray<TLObject> participantsMap = new LongSparseArray<>();
    private LongSparseArray<TLObject> contactsMap = new LongSparseArray<>();
    private boolean loadingUsers;
    private boolean firstLoaded;

    private LongSparseArray<TLRPC.GroupCallParticipant> ignoredUsers;
    private HashSet<Long> invitedUsers;

    private GroupVoipInviteAlertDelegate delegate;

    private boolean showContacts;

    private int emptyRow;
    private int addNewRow;
    private int lastRow;
    private int participantsStartRow;
    private int participantsEndRow;
    private int contactsHeaderRow;
    private int contactsStartRow;
    private int contactsEndRow;
    private int membersHeaderRow;
    private int flickerProgressRow;
    private int rowCount;

    public interface GroupVoipInviteAlertDelegate {
        void copyInviteLink();
        void inviteUser(long id);
        void needOpenSearch(MotionEvent ev, EditTextBoldCursor editText);
    }

    @Override
    protected void updateColorKeys() {
        keyScrollUp = Theme.key_voipgroup_scrollUp;
        keyListSelector = Theme.key_voipgroup_listSelector;
        keySearchBackground = Theme.key_voipgroup_searchBackground;
        keyInviteMembersBackground = Theme.key_voipgroup_inviteMembersBackground;
        keyListViewBackground = Theme.key_voipgroup_listViewBackground;
        keyActionBarUnscrolled = Theme.key_voipgroup_actionBarUnscrolled;
        keyNameText = Theme.key_voipgroup_nameText;
        keyLastSeenText = Theme.key_voipgroup_lastSeenText;
        keyLastSeenTextUnscrolled = Theme.key_voipgroup_lastSeenTextUnscrolled;
        keySearchPlaceholder = Theme.key_voipgroup_searchPlaceholder;
        keySearchText = Theme.key_voipgroup_searchText;
        keySearchIcon = Theme.key_voipgroup_mutedIcon;
        keySearchIconUnscrolled = Theme.key_voipgroup_mutedIconUnscrolled;
    }

    public GroupVoipInviteAlert(final Context context, int account, TLRPC.Chat chat, TLRPC.ChatFull chatFull, LongSparseArray<TLRPC.GroupCallParticipant> participants, HashSet<Long> invited) {
        super(context, false, account, null);

        setDimBehindAlpha(75);

        currentChat = chat;
        info = chatFull;
        ignoredUsers = participants;
        invitedUsers = invited;

        listView.setOnItemClickListener((view, position) -> {
            if (position == addNewRow) {
                delegate.copyInviteLink();
                dismiss();
            } else if (view instanceof ManageChatUserCell) {
                ManageChatUserCell cell = (ManageChatUserCell) view;
                if (invitedUsers.contains(cell.getUserId())) {
                    return;
                }
                delegate.inviteUser(cell.getUserId());
            }
        });
        searchListViewAdapter = searchAdapter = new SearchAdapter(context);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        loadChatParticipants(0, 200);
        updateRows();

        setColorProgress(0.0f);
    }

    public void setDelegate(GroupVoipInviteAlertDelegate groupVoipInviteAlertDelegate) {
        delegate = groupVoipInviteAlertDelegate;
    }

    private void updateRows() {
        addNewRow = -1;
        emptyRow = -1;
        participantsStartRow = -1;
        participantsEndRow = -1;
        contactsHeaderRow = -1;
        contactsStartRow = -1;
        contactsEndRow = -1;
        membersHeaderRow = -1;
        lastRow = -1;

        rowCount = 0;
        emptyRow = rowCount++;
        if (ChatObject.isPublic(currentChat) || ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE)) {
            addNewRow = rowCount++;
        }
        if (!loadingUsers || firstLoaded) {
            boolean hasAnyOther = false;
            if (!contacts.isEmpty()) {
                contactsHeaderRow = rowCount++;
                contactsStartRow = rowCount;
                rowCount += contacts.size();
                contactsEndRow = rowCount;
                hasAnyOther = true;
            }
            if (!participants.isEmpty()) {
                if (hasAnyOther) {
                    membersHeaderRow = rowCount++;
                }
                participantsStartRow = rowCount;
                rowCount += participants.size();
                participantsEndRow = rowCount;
            }
        }
        if (loadingUsers) {
            flickerProgressRow = rowCount++;
        }
        lastRow = rowCount++;
    }

    private void loadChatParticipants(int offset, int count) {
        if (loadingUsers) {
            return;
        }
        contactsEndReached = false;
        loadChatParticipants(offset, count, true);
    }

    private void fillContacts() {
        if (!showContacts) {
            return;
        }
        contacts.addAll(ContactsController.getInstance(currentAccount).contacts);
        long selfId = UserConfig.getInstance(currentAccount).clientUserId;
        for (int a = 0, N = contacts.size(); a < N; a++) {
            TLObject object = contacts.get(a);
            if (!(object instanceof TLRPC.TL_contact)) {
                continue;
            }
            long userId = ((TLRPC.TL_contact) object).user_id;
            if (userId == selfId || ignoredUsers.indexOfKey(userId) >= 0 || invitedUsers.contains(userId)) {
                contacts.remove(a);
                a--;
                N--;
            }
        }
        int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        Collections.sort(contacts, (o1, o2) -> {
            TLRPC.User user1 = o2 instanceof TLRPC.TL_contact ? messagesController.getUser(((TLRPC.TL_contact) o2).user_id) : null;
            TLRPC.User user2 = o1 instanceof TLRPC.TL_contact ? messagesController.getUser(((TLRPC.TL_contact) o1).user_id) : null;
            int status1 = 0;
            int status2 = 0;
            if (user1 != null) {
                if (user1.self) {
                    status1 = currentTime + 50000;
                } else if (user1.status != null) {
                    status1 = user1.status.expires;
                }
            }
            if (user2 != null) {
                if (user2.self) {
                    status2 = currentTime + 50000;
                } else if (user2.status != null) {
                    status2 = user2.status.expires;
                }
            }
            if (status1 > 0 && status2 > 0) {
                if (status1 > status2) {
                    return 1;
                } else if (status1 < status2) {
                    return -1;
                }
                return 0;
            } else if (status1 < 0 && status2 < 0) {
                if (status1 > status2) {
                    return 1;
                } else if (status1 < status2) {
                    return -1;
                }
                return 0;
            } else if (status1 < 0 && status2 > 0 || status1 == 0 && status2 != 0) {
                return -1;
            } else if (status2 < 0 || status1 != 0) {
                return 1;
            }
            return 0;
        });
    }

    protected void loadChatParticipants(int offset, int count, boolean reset) {
        if (!ChatObject.isChannel(currentChat)) {
            loadingUsers = false;
            participants.clear();
            contacts.clear();
            participantsMap.clear();
            contactsMap.clear();
            if (info != null) {
                long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
                for (int a = 0, size = info.participants.participants.size(); a < size; a++) {
                    TLRPC.ChatParticipant participant = info.participants.participants.get(a);
                    if (participant.user_id == selfUserId) {
                        continue;
                    }
                    if (ignoredUsers != null && ignoredUsers.indexOfKey(participant.user_id) >= 0) {
                        continue;
                    }
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                    if (UserObject.isDeleted(user) || user.bot) {
                        continue;
                    }
                    participants.add(participant);
                    participantsMap.put(participant.user_id, participant);
                }
                if (participants.isEmpty()) {
                    showContacts = true;
                    fillContacts();
                }
            }
            updateRows();
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        } else {
            loadingUsers = true;
            if (emptyView != null) {
                emptyView.showProgress(true, false);
            }
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
            TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
            req.channel = MessagesController.getInputChannel(currentChat);
            if (info != null && info.participants_count <= 200) {
                req.filter = new TLRPC.TL_channelParticipantsRecent();
            } else {
                if (!contactsEndReached) {
                    delayResults = 2;
                    req.filter = new TLRPC.TL_channelParticipantsContacts();
                    contactsEndReached = true;
                    loadChatParticipants(0, 200, false);
                } else {
                    req.filter = new TLRPC.TL_channelParticipantsRecent();
                }
            }
            req.filter.q = "";
            req.offset = offset;
            req.limit = count;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                    for (int a = 0; a < res.participants.size(); a++) {
                        if (MessageObject.getPeerId(res.participants.get(a).peer) == selfId) {
                            res.participants.remove(a);
                            break;
                        }
                    }
                    ArrayList<TLObject> objects;
                    LongSparseArray<TLObject> map;
                    delayResults--;
                    if (req.filter instanceof TLRPC.TL_channelParticipantsContacts) {
                        objects = contacts;
                        map = contactsMap;
                    } else {
                        objects = participants;
                        map = participantsMap;
                    }
                    objects.clear();
                    objects.addAll(res.participants);
                    for (int a = 0, size = res.participants.size(); a < size; a++) {
                        TLRPC.ChannelParticipant participant = res.participants.get(a);
                        map.put(MessageObject.getPeerId(participant.peer), participant);
                    }
                    for (int a = 0, N = participants.size(); a < N; a++) {
                        TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) participants.get(a);
                        long peerId = MessageObject.getPeerId(participant.peer);
                        boolean remove = false;
                        if (contactsMap.get(peerId) != null) {
                            remove = true;
                        } else if (ignoredUsers != null && ignoredUsers.indexOfKey(peerId) >= 0) {
                            remove = true;
                        }
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                        if (user != null && user.bot || UserObject.isDeleted(user)) {
                            remove = true;
                        }
                        if (remove) {
                            participants.remove(a);
                            participantsMap.remove(peerId);
                            a--;
                            N--;
                        }
                    }
                    try {
                        if (info.participants_count <= 200) {
                            int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                            Collections.sort(objects, (lhs, rhs) -> {
                                TLRPC.ChannelParticipant p1 = (TLRPC.ChannelParticipant) lhs;
                                TLRPC.ChannelParticipant p2 = (TLRPC.ChannelParticipant) rhs;
                                TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(p1.peer));
                                TLRPC.User user2 = MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(p2.peer));
                                int status1 = 0;
                                int status2 = 0;
                                if (user1 != null && user1.status != null) {
                                    if (user1.self) {
                                        status1 = currentTime + 50000;
                                    } else {
                                        status1 = user1.status.expires;
                                    }
                                }
                                if (user2 != null && user2.status != null) {
                                    if (user2.self) {
                                        status2 = currentTime + 50000;
                                    } else {
                                        status2 = user2.status.expires;
                                    }
                                }
                                if (status1 > 0 && status2 > 0) {
                                    if (status1 > status2) {
                                        return 1;
                                    } else if (status1 < status2) {
                                        return -1;
                                    }
                                    return 0;
                                } else if (status1 < 0 && status2 < 0) {
                                    if (status1 > status2) {
                                        return 1;
                                    } else if (status1 < status2) {
                                        return -1;
                                    }
                                    return 0;
                                } else if (status1 < 0 && status2 > 0 || status1 == 0 && status2 != 0) {
                                    return -1;
                                } else if (status2 < 0 && status1 > 0 || status2 == 0 && status1 != 0) {
                                    return 1;
                                }
                                return 0;
                            });
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (delayResults <= 0) {
                    loadingUsers = false;
                    firstLoaded = true;
                    int num;
                    if (flickerProgressRow == 1) {
                        num = 1;
                    } else {
                        num = listViewAdapter != null ? listViewAdapter.getItemCount() - 1 : 0;
                    }
                    showItemsAnimated(num);
                    if (participants.isEmpty()) {
                        showContacts = true;
                        fillContacts();
                    }
                }
                updateRows();
                if (listViewAdapter != null) {
                    listViewAdapter.notifyDataSetChanged();
                    if (emptyView != null && listViewAdapter.getItemCount() == 0 && firstLoaded) {
                        emptyView.showProgress(false, true);
                    }
                }
            }));
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private SearchAdapterHelper searchAdapterHelper;
        private Runnable searchRunnable;
        private int totalCount;

        private boolean searchInProgress;

        private int lastSearchId;

        private int emptyRow;
        private int lastRow;
        private int groupStartRow;
        private int globalStartRow;

        public SearchAdapter(Context context) {
            mContext = context;
            searchAdapterHelper = new SearchAdapterHelper(true);
            searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
                @Override
                public void onDataSetChanged(int searchId) {
                    if (searchId < 0 || searchId != lastSearchId || searchInProgress) {
                        return;
                    }
                    int oldItemCount = getItemCount() - 1;
                    boolean emptyViewWasVisible = emptyView.getVisibility() == View.VISIBLE;
                    notifyDataSetChanged();
                    if (getItemCount() > oldItemCount) {
                        showItemsAnimated(oldItemCount);
                    }
                    if (!searchAdapterHelper.isSearchInProgress()) {
                        if (listView.emptyViewIsVisible()) {
                            emptyView.showProgress(false, emptyViewWasVisible);
                        }
                    }
                }

                @Override
                public LongSparseArray<TLRPC.GroupCallParticipant> getExcludeCallParticipants() {
                    return ignoredUsers;
                }
            });
        }

        public void searchUsers(final String query) {
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                searchRunnable = null;
            }
            searchAdapterHelper.mergeResults(null);
            searchAdapterHelper.queryServerSearch(null, true, false, true, false, false, currentChat.id, false, ChatUsersActivity.TYPE_USERS, -1);

            if (!TextUtils.isEmpty(query)) {
                emptyView.showProgress(true, true);
                listView.setAnimateEmptyView(false, 0);
                notifyDataSetChanged();
                listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
                searchInProgress = true;
                int searchId = ++lastSearchId;
                AndroidUtilities.runOnUIThread(searchRunnable = () -> {
                    if (searchRunnable == null) {
                        return;
                    }
                    searchRunnable = null;
                    processSearch(query, searchId);
                }, 300);

                if (listView.getAdapter() != searchListViewAdapter) {
                    listView.setAdapter(searchListViewAdapter);
                }
            } else {
                lastSearchId = -1;
            }
        }

        private void processSearch(final String query, int searchId) {
            AndroidUtilities.runOnUIThread(() -> {
                searchRunnable = null;

                final ArrayList<TLObject> participantsCopy = !ChatObject.isChannel(currentChat) && info != null ? new ArrayList<>(info.participants.participants) : null;

                if (participantsCopy != null) {
                    Utilities.searchQueue.postRunnable(() -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), searchId);
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
                        ArrayList<TLObject> resultArray2 = new ArrayList<>();

                        for (int a = 0, N = participantsCopy.size(); a < N; a++) {
                            long userId;
                            TLObject o = participantsCopy.get(a);
                            if (o instanceof TLRPC.ChatParticipant) {
                                userId = ((TLRPC.ChatParticipant) o).user_id;
                            } else if (o instanceof TLRPC.ChannelParticipant) {
                                userId = MessageObject.getPeerId(((TLRPC.ChannelParticipant) o).peer);
                            } else {
                                continue;
                            }
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                            if (UserObject.isUserSelf(user)) {
                                continue;
                            }

                            String name = UserObject.getUserName(user).toLowerCase();
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }

                            int found = 0;
                            String username;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if ((username = UserObject.getPublicUsername(user)) != null && username.startsWith(q)) {
                                    found = 2;
                                }

                                if (found != 0) {
                                    resultArray2.add(o);
                                    break;
                                }
                            }
                        }
                        updateSearchResults(resultArray2, searchId);
                    });
                } else {
                    searchInProgress = false;
                }
                searchAdapterHelper.queryServerSearch(query, ChatObject.canAddUsers(currentChat), false, true, false, false, ChatObject.isChannel(currentChat) ? currentChat.id : 0, false, ChatUsersActivity.TYPE_USERS, searchId);
            });
        }

        private void updateSearchResults(final ArrayList<TLObject> participants, int searchId) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searchId != lastSearchId) {
                    return;
                }
                searchInProgress = false;
                if (!ChatObject.isChannel(currentChat)) {
                    searchAdapterHelper.addGroupMembers(participants);
                }
                int oldItemCount = getItemCount() - 1;
                boolean emptyViewWasVisible = emptyView.getVisibility() == View.VISIBLE;
                notifyDataSetChanged();
                if (getItemCount() > oldItemCount) {
                    showItemsAnimated(oldItemCount);
                }
                if (!searchInProgress && !searchAdapterHelper.isSearchInProgress()) {
                    if (listView.emptyViewIsVisible()) {
                        emptyView.showProgress(false, emptyViewWasVisible);
                    }
                }
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ManageChatUserCell cell = (ManageChatUserCell) holder.itemView;
                if (invitedUsers.contains(cell.getUserId())) {
                    return false;
                }
            }
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            return totalCount;
        }

        @Override
        public void notifyDataSetChanged() {
            totalCount = 0;
            emptyRow = totalCount++;
            int count = searchAdapterHelper.getGroupSearch().size();
            if (count != 0) {
                groupStartRow = totalCount;
                totalCount += count + 1;
            } else {
                groupStartRow = -1;
            }
            count = searchAdapterHelper.getGlobalSearch().size();
            if (count != 0) {
                globalStartRow = totalCount;
                totalCount += count + 1;
            } else {
                globalStartRow = -1;
            }
            lastRow = totalCount++;
            super.notifyDataSetChanged();
        }

        public TLObject getItem(int i) {
            if (groupStartRow >= 0 && i > groupStartRow && i < groupStartRow + 1 + searchAdapterHelper.getGroupSearch().size()) {
                return searchAdapterHelper.getGroupSearch().get(i - groupStartRow - 1);
            }
            if (globalStartRow >= 0 && i > globalStartRow && i < globalStartRow + 1 + searchAdapterHelper.getGlobalSearch().size()) {
                return searchAdapterHelper.getGlobalSearch().get(i - globalStartRow - 1);
            }
            return null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    ManageChatUserCell manageChatUserCell = new ManageChatUserCell(mContext, 2, 2, false);
                    manageChatUserCell.setCustomRightImage(R.drawable.msg_invited);
                    manageChatUserCell.setNameColor(Theme.getColor(Theme.key_voipgroup_nameText));
                    manageChatUserCell.setStatusColors(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled), Theme.getColor(Theme.key_voipgroup_listeningText));
                    manageChatUserCell.setDividerColor(Theme.key_voipgroup_listViewBackground);
                    view = manageChatUserCell;
                    break;
                case 1:
                    GraySectionCell cell = new GraySectionCell(mContext);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_actionBarUnscrolled));
                    cell.setTextColor(Theme.key_voipgroup_searchPlaceholder);
                    view = cell;
                    break;
                case 2:
                    view = new View(mContext);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    break;
                case 3:
                default:
                    view = new View(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TLObject object = getItem(position);
                    TLRPC.User user;
                    if (object instanceof TLRPC.User) {
                        user = (TLRPC.User) object;
                    } else if (object instanceof TLRPC.ChannelParticipant) {
                        user = MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(((TLRPC.ChannelParticipant) object).peer));
                    } else if (object instanceof TLRPC.ChatParticipant) {
                        user = MessagesController.getInstance(currentAccount).getUser(((TLRPC.ChatParticipant) object).user_id);
                    } else {
                        return;
                    }

                    String un = UserObject.getPublicUsername(user);
                    CharSequence username = null;
                    SpannableStringBuilder name = null;

                    int count = searchAdapterHelper.getGroupSearch().size();
                    boolean ok = false;
                    String nameSearch = null;
                    if (count != 0) {
                        if (count + 1 > position) {
                            nameSearch = searchAdapterHelper.getLastFoundChannel();
                            ok = true;
                        } else {
                            position -= count + 1;
                        }
                    }
                    if (!ok && un != null) {
                        count = searchAdapterHelper.getGlobalSearch().size();
                        if (count != 0) {
                            if (count + 1 > position) {
                                String foundUserName = searchAdapterHelper.getLastFoundUsername();
                                if (foundUserName.startsWith("@")) {
                                    foundUserName = foundUserName.substring(1);
                                }
                                try {
                                    int index;
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
                                        spannableStringBuilder.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_voipgroup_listeningText)), index, index + len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                    }
                                    username = spannableStringBuilder;
                                } catch (Exception e) {
                                    username = un;
                                    FileLog.e(e);
                                }
                            }
                        }
                    }

                    if (nameSearch != null) {
                        String u = UserObject.getUserName(user);
                        name = new SpannableStringBuilder(u);
                        int idx = AndroidUtilities.indexOfIgnoreCase(u, nameSearch);
                        if (idx != -1) {
                            name.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_voipgroup_listeningText)), idx, idx + nameSearch.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }

                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    userCell.setTag(position);
                    userCell.setCustomImageVisible(invitedUsers.contains(user.id));
                    userCell.setData(user, name, username, false);

                    break;
                }
                case 1: {
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    if (position == groupStartRow) {
                        sectionCell.setText(LocaleController.getString(R.string.ChannelMembers));
                    } else if (position == globalStartRow) {
                        sectionCell.setText(LocaleController.getString(R.string.GlobalSearch));
                    }
                    break;
                }
            }
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == emptyRow) {
                return 2;
            } else if (i == lastRow) {
                return 3;
            }
            if (i == globalStartRow || i == groupStartRow) {
                return 1;
            }
            return 0;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ManageChatUserCell cell = (ManageChatUserCell) holder.itemView;
                if (invitedUsers.contains(cell.getUserId())) {
                    return false;
                }
            }
            int viewType = holder.getItemViewType();
            return viewType == 0 || viewType == 1;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    ManageChatUserCell manageChatUserCell = new ManageChatUserCell(mContext, 6, 2, false);
                    manageChatUserCell.setCustomRightImage(R.drawable.msg_invited);
                    manageChatUserCell.setNameColor(Theme.getColor(Theme.key_voipgroup_nameText));
                    manageChatUserCell.setStatusColors(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled), Theme.getColor(Theme.key_voipgroup_listeningText));
                    manageChatUserCell.setDividerColor(Theme.key_voipgroup_actionBar);
                    view = manageChatUserCell;
                    break;
                case 1:
                    ManageChatTextCell manageChatTextCell = new ManageChatTextCell(mContext);
                    manageChatTextCell.setColors(Theme.key_voipgroup_listeningText, Theme.key_voipgroup_listeningText);
                    manageChatTextCell.setDividerColor(Theme.key_voipgroup_actionBar);
                    view = manageChatTextCell;
                    break;
                case 2:
                    GraySectionCell cell = new GraySectionCell(mContext);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_actionBarUnscrolled));
                    cell.setTextColor(Theme.key_voipgroup_searchPlaceholder);
                    view = cell;
                    break;
                case 3:
                    view = new View(mContext);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    break;
                case 5:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                    flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setColors(Theme.key_voipgroup_inviteMembersBackground, Theme.key_voipgroup_searchBackground, Theme.key_voipgroup_actionBarUnscrolled);
                    view = flickerLoadingView;
                    break;
                case 4:
                default:
                    view = new View(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    userCell.setTag(position);
                    TLObject item = getItem(position);
                    int lastRow;

                    if (position >= participantsStartRow && position < participantsEndRow) {
                        lastRow = participantsEndRow;
                    } else {
                        lastRow = contactsEndRow;
                    }

                    long userId;
                    if (item instanceof TLRPC.TL_contact) {
                        TLRPC.TL_contact contact = (TLRPC.TL_contact) item;
                        userId = contact.user_id;
                    } else if (item instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) item;
                        userId = user.id;
                    } else if (item instanceof TLRPC.ChannelParticipant) {
                        TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) item;
                        userId = MessageObject.getPeerId(participant.peer);
                    } else {
                        TLRPC.ChatParticipant participant = (TLRPC.ChatParticipant) item;
                        userId = participant.user_id;
                    }
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userId);
                    if (user != null) {
                        userCell.setCustomImageVisible(invitedUsers.contains(user.id));
                        userCell.setData(user, null, null, position != lastRow - 1);
                    }
                    break;
                case 1:
                    ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                    if (position == addNewRow) {
                        boolean showDivider = !(loadingUsers && !firstLoaded) && membersHeaderRow == -1 && !participants.isEmpty();
                        actionCell.setText(LocaleController.getString(R.string.VoipGroupCopyInviteLink), null, R.drawable.msg_link, 7, showDivider);
                    }
                    break;
                case 2:
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    if (position == membersHeaderRow) {
                        sectionCell.setText(LocaleController.getString(R.string.ChannelOtherMembers));
                    } else if (position == contactsHeaderRow) {
                        if (showContacts) {
                            sectionCell.setText(LocaleController.getString(R.string.YourContactsToInvite));
                        } else {
                            sectionCell.setText(LocaleController.getString(R.string.GroupContacts));
                        }
                    }
                    break;
            }
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= participantsStartRow && position < participantsEndRow ||
                    position >= contactsStartRow && position < contactsEndRow) {
                return 0;
            } else if (position == addNewRow) {
                return 1;
            } else if (position == membersHeaderRow || position == contactsHeaderRow) {
                return 2;
            } else if (position == emptyRow) {
                return 3;
            } else if (position == lastRow) {
                return 4;
            } else if (position == flickerProgressRow) {
                return 5;
            }
            return 0;
        }

        public TLObject getItem(int position) {
            if (position >= participantsStartRow && position < participantsEndRow) {
                return participants.get(position - participantsStartRow);
            } else if (position >= contactsStartRow && position < contactsEndRow) {
                return contacts.get(position - contactsStartRow);
            }
            return null;
        }
    }

    @Override
    protected void search(String text) {
        searchAdapter.searchUsers(text);
    }

    @Override
    protected void onSearchViewTouched(MotionEvent ev, EditTextBoldCursor searchEditText) {
        delegate.needOpenSearch(ev, searchEditText);
    }
}
