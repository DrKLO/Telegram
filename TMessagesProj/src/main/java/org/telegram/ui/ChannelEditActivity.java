/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.SearchAdapterHelper;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

public class ChannelEditActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter listViewAdapter;
    private SearchAdapter searchListViewAdapter;
    private LinearLayoutManager layoutManager;
    private int chat_id;

    private boolean loadingUsers;
    private SparseArray<TLRPC.ChatParticipant> participantsMap = new SparseArray<>();
    private boolean usersEndReached;

    private TLRPC.ChatFull info;
    private ArrayList<Integer> sortedUsers;

    private TLRPC.Chat currentChat;

    private final static int search_button = 1;

    private boolean searchWas;
    private boolean searching;

    private int infoRow;
    private int eventLogRow;
    private int blockedUsersRow;
    private int managementRow;
    private int permissionsRow;
    private int membersSectionRow;
    private int membersStartRow;
    private int membersEndRow;
    private int membersSection2Row;
    private int loadMoreMembersRow;
    private int rowCount = 0;

    public ChannelEditActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        chat_id = getArguments().getInt("chat_id", 0);
        currentChat = MessagesController.getInstance(currentAccount).getChat(chat_id);
        if (currentChat == null) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                currentChat = MessagesStorage.getInstance(currentAccount).getChat(chat_id);
                countDownLatch.countDown();
            });
            try {
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (currentChat != null) {
                MessagesController.getInstance(currentAccount).putChat(currentChat, true);
            } else {
                return false;
            }
        }

        getChannelParticipants(true);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);

        sortedUsers = new ArrayList<>();
        updateRowsIds();

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
    }

    @Override
    public View createView(Context context) {
        Theme.createProfileResources(context);

        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentChat.megagroup) {
            actionBar.setTitle(LocaleController.getString("ManageGroup", R.string.ManageGroup));
        } else {
            actionBar.setTitle(LocaleController.getString("ManageChannel", R.string.ManageChannel));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(final int id) {
                if (getParentActivity() == null) {
                    return;
                }
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        searchListViewAdapter = new SearchAdapter(context);
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem searchItem = menu.addItem(search_button, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
            }

            @Override
            public void onSearchCollapse() {
                searchListViewAdapter.searchDialogs(null);
                searching = false;
                searchWas = false;
                listView.setAdapter(listViewAdapter);
                listViewAdapter.notifyDataSetChanged();
                listView.setFastScrollVisible(true);
                listView.setVerticalScrollBarEnabled(false);
            }

            @Override
            public void onTextChanged(EditText editText) {
                if (searchListViewAdapter == null) {
                    return;
                }
                String text = editText.getText().toString();
                if (text.length() != 0) {
                    searchWas = true;
                    if (listView != null) {
                        listView.setAdapter(searchListViewAdapter);
                        searchListViewAdapter.notifyDataSetChanged();
                        listView.setFastScrollVisible(false);
                        listView.setVerticalScrollBarEnabled(true);
                    }
                }
                searchListViewAdapter.searchDialogs(text);
            }
        });
        searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));

        listViewAdapter = new ListAdapter(context);
        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        EmptyTextProgressView emptyView = new EmptyTextProgressView(context);
        emptyView.setShowAtCenter(true);
        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.showTextView();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        listView.setVerticalScrollBarEnabled(false);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        listView.setAdapter(listViewAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (listView.getAdapter() == searchListViewAdapter) {
                Bundle args = new Bundle();
                args.putInt("user_id", searchListViewAdapter.getItem(position).user_id);
                presentFragment(new ProfileActivity(args));
            } else {
                if (position >= membersStartRow && position < membersEndRow) {
                    int user_id;
                    if (!sortedUsers.isEmpty()) {
                        user_id = info.participants.participants.get(sortedUsers.get(position - membersStartRow)).user_id;
                    } else {
                        user_id = info.participants.participants.get(position - membersStartRow).user_id;
                    }
                    Bundle args = new Bundle();
                    args.putInt("user_id", user_id);
                    presentFragment(new ProfileActivity(args));
                } else if (position == blockedUsersRow || position == managementRow) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat_id);
                    if (position == blockedUsersRow) {
                        args.putInt("type", 0);
                    } else if (position == managementRow) {
                        args.putInt("type", 1);
                    }
                    presentFragment(new ChannelUsersActivity(args));
                } else if (position == permissionsRow) {
                    ChannelPermissionsActivity permissions = new ChannelPermissionsActivity(chat_id);
                    permissions.setInfo(info);
                    presentFragment(permissions);
                } else if (position == eventLogRow) {
                    presentFragment(new ChannelAdminLogActivity(currentChat));
                } else if (position == infoRow) {
                    Bundle args = new Bundle();
                    args.putInt("chat_id", chat_id);
                    ChannelEditInfoActivity fragment = new ChannelEditInfoActivity(args);
                    fragment.setInfo(info);
                    presentFragment(fragment);
                }
            }
        });

        listView.setOnItemLongClickListener((view, position) -> {
            if (position >= membersStartRow && position < membersEndRow) {
                if (getParentActivity() == null) {
                    return false;
                }
                final TLRPC.TL_chatChannelParticipant user;
                if (!sortedUsers.isEmpty()) {
                    user = (TLRPC.TL_chatChannelParticipant) info.participants.participants.get(sortedUsers.get(position - membersStartRow));
                } else {
                    user = (TLRPC.TL_chatChannelParticipant) info.participants.participants.get(position - membersStartRow);
                }
                return createMenuForParticipant(user, null, false);
            }
            return false;
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (participantsMap != null && loadMoreMembersRow != -1 && layoutManager.findLastVisibleItemPosition() > loadMoreMembersRow - 8) {
                    getChannelParticipants(false);
                }
            }
        });

        return fragmentView;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chat_id) {
                boolean byChannelUsers = (Boolean) args[2];
                if (info instanceof TLRPC.TL_channelFull) {
                    if (chatFull.participants == null && info != null) {
                        chatFull.participants = info.participants;
                    }
                }
                boolean loadChannelParticipants = info == null && chatFull instanceof TLRPC.TL_channelFull;
                info = chatFull;
                fetchUsersFromChannelInfo();
                updateRowsIds();
                if (listViewAdapter != null) {
                    listViewAdapter.notifyDataSetChanged();
                }
                TLRPC.Chat newChat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                if (newChat != null) {
                    currentChat = newChat;
                }
                if (loadChannelParticipants || !byChannelUsers) {
                    getChannelParticipants(true);
                }
            }
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    private void getChannelParticipants(boolean reload) {
        if (loadingUsers || participantsMap == null || info == null) {
            return;
        }
        loadingUsers = true;
        final int delay = participantsMap.size() != 0 && reload ? 300 : 0;

        final TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat_id);
        req.filter = new TLRPC.TL_channelParticipantsRecent();
        req.offset = reload ? 0 : participantsMap.size();
        req.limit = 200;
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                if (res.users.size() < 200) {
                    usersEndReached = true;
                }
                if (req.offset == 0) {
                    participantsMap.clear();
                    info.participants = new TLRPC.TL_chatParticipants();
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, null, true, true);
                    MessagesStorage.getInstance(currentAccount).updateChannelUsers(chat_id, res.participants);
                }
                for (int a = 0; a < res.participants.size(); a++) {
                    TLRPC.TL_chatChannelParticipant participant = new TLRPC.TL_chatChannelParticipant();
                    participant.channelParticipant = res.participants.get(a);
                    participant.inviter_id = participant.channelParticipant.inviter_id;
                    participant.user_id = participant.channelParticipant.user_id;
                    participant.date = participant.channelParticipant.date;
                    if (participantsMap.indexOfKey(participant.user_id) < 0) {
                        info.participants.participants.add(participant);
                        participantsMap.put(participant.user_id, participant);
                    }
                }
            }
            loadingUsers = false;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, true, null);
        }, delay));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    public void setInfo(TLRPC.ChatFull chatInfo) {
        info = chatInfo;
        fetchUsersFromChannelInfo();
    }

    private void fetchUsersFromChannelInfo() {
        if (info instanceof TLRPC.TL_channelFull && info.participants != null) {
            for (int a = 0; a < info.participants.participants.size(); a++) {
                TLRPC.ChatParticipant chatParticipant = info.participants.participants.get(a);
                participantsMap.put(chatParticipant.user_id, chatParticipant);
            }
        }
    }

    private void updateRowsIds() {
        rowCount = 0;
        if (ChatObject.canEditInfo(currentChat)) {
            infoRow = rowCount++;
        } else {
            infoRow = -1;
        }
        permissionsRow = -1;
        /*if (currentChat.creator) {
            permissionsRow = rowCount++;
        }*/
        eventLogRow = rowCount++;
        managementRow = rowCount++;
        blockedUsersRow = rowCount++;
        membersSectionRow = rowCount++;
        if (info != null && info.participants != null && !info.participants.participants.isEmpty()) {
            membersStartRow = rowCount;
            rowCount += info.participants.participants.size();
            membersEndRow = rowCount;
            membersSection2Row = rowCount++;
            if (!usersEndReached) {
                loadMoreMembersRow = rowCount++;
            } else {
                loadMoreMembersRow = -1;
            }
        } else {
            membersStartRow = -1;
            membersEndRow = -1;
            loadMoreMembersRow = -1;
            membersSection2Row = -1;
        }
    }

    private boolean createMenuForParticipant(TLRPC.TL_chatChannelParticipant user, TLRPC.ChannelParticipant channelParticipant, boolean resultOnly) {
        if (user == null && channelParticipant == null) {
            return false;
        }
        int currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        final int uid;
        if (channelParticipant != null) {
            if (currentUserId == channelParticipant.user_id) {
                return false;
            }
            uid = channelParticipant.user_id;
            user = (TLRPC.TL_chatChannelParticipant) participantsMap.get(channelParticipant.user_id);
            if (user != null) {
                channelParticipant = user.channelParticipant;
            }
        } else {
            if (user.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                return false;
            }
            uid = user.user_id;
            channelParticipant = user.channelParticipant;
        }


        TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(uid);
        boolean allowSetAdmin = channelParticipant instanceof TLRPC.TL_channelParticipant || channelParticipant instanceof TLRPC.TL_channelParticipantBanned;
        boolean canEditAdmin = !(channelParticipant instanceof TLRPC.TL_channelParticipantAdmin || channelParticipant instanceof TLRPC.TL_channelParticipantCreator) || channelParticipant.can_edit;

        ArrayList<String> items;
        final ArrayList<Integer> actions;
        if (resultOnly) {
            items = null;
            actions = null;
        } else {
            items = new ArrayList<>();
            actions = new ArrayList<>();
        }
        if (allowSetAdmin && ChatObject.canAddAdmins(currentChat)) {
            if (resultOnly) {
                return true;
            }
            items.add(LocaleController.getString("SetAsAdmin", R.string.SetAsAdmin));
            actions.add(0);
        }
        if (ChatObject.canBlockUsers(currentChat) && canEditAdmin) {
            if (resultOnly) {
                return true;
            }
            if (currentChat.megagroup) {
                items.add(LocaleController.getString("KickFromSupergroup", R.string.KickFromSupergroup));
                actions.add(1);
                items.add(LocaleController.getString("KickFromGroup", R.string.KickFromGroup));
                actions.add(2);
            } else {
                items.add(LocaleController.getString("ChannelRemoveUser", R.string.ChannelRemoveUser));
                actions.add(2);
            }
        }
        if (items == null || items.isEmpty()) {
            return false;
        }
        final TLRPC.ChannelParticipant channelParticipantFinal = channelParticipant;
        final TLRPC.TL_chatChannelParticipant userFinal = user;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setItems(items.toArray(new CharSequence[items.size()]), (dialogInterface, i) -> {
            if (actions.get(i) == 2) {
                MessagesController.getInstance(currentAccount).deleteUserFromChat(chat_id, MessagesController.getInstance(currentAccount).getUser(uid), info);
            } else {
                ChannelRightsEditActivity fragment = new ChannelRightsEditActivity(channelParticipantFinal.user_id, chat_id, channelParticipantFinal.admin_rights, channelParticipantFinal.banned_rights, actions.get(i), true);
                fragment.setDelegate((rights, rightsAdmin, rightsBanned) -> {
                    channelParticipantFinal.admin_rights = rightsAdmin;
                    channelParticipantFinal.banned_rights = rightsBanned;
                    if (actions.get(i) == 0) {
                        if (userFinal != null) {
                            if (rights == 1) {
                                userFinal.channelParticipant = new TLRPC.TL_channelParticipantAdmin();
                            } else {
                                userFinal.channelParticipant = new TLRPC.TL_channelParticipant();
                            }
                            userFinal.channelParticipant.inviter_id = UserConfig.getInstance(currentAccount).getClientUserId();
                            userFinal.channelParticipant.user_id = userFinal.user_id;
                            userFinal.channelParticipant.date = userFinal.date;
                        }
                    } else if (actions.get(i) == 1) {
                        if (rights == 0) {
                            if (currentChat.megagroup && info != null && info.participants != null) {
                                boolean changed = false;
                                for (int a = 0; a < info.participants.participants.size(); a++) {
                                    TLRPC.ChannelParticipant p = ((TLRPC.TL_chatChannelParticipant) info.participants.participants.get(a)).channelParticipant;
                                    if (p.user_id == uid) {
                                        if (info != null) {
                                            info.participants_count--;
                                        }
                                        info.participants.participants.remove(a);
                                        changed = true;
                                        break;
                                    }
                                }
                                if (info != null && info.participants != null) {
                                    for (int a = 0; a < info.participants.participants.size(); a++) {
                                        TLRPC.ChatParticipant p = info.participants.participants.get(a);
                                        if (p.user_id == uid) {
                                            info.participants.participants.remove(a);
                                            changed = true;
                                            break;
                                        }
                                    }
                                }
                                if (changed) {
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, true, null);
                                }
                            }
                        }
                    }
                });
                presentFragment(fragment);
            }
        });
        showDialog(builder.create());
        return true;
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private SearchAdapterHelper searchAdapterHelper;
        private Timer searchTimer;

        public SearchAdapter(Context context) {
            mContext = context;
            searchAdapterHelper = new SearchAdapterHelper(true);
            searchAdapterHelper.setDelegate(new SearchAdapterHelper.SearchAdapterHelperDelegate() {
                @Override
                public void onDataSetChanged() {
                    notifyDataSetChanged();
                }

                @Override
                public void onSetHashtags(ArrayList<SearchAdapterHelper.HashtagObject> arrayList, HashMap<String, SearchAdapterHelper.HashtagObject> hashMap) {

                }
            });
        }

        public void searchDialogs(final String query) {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (query == null) {
                searchAdapterHelper.queryServerSearch(null, false, false, true, true, chat_id, false);
                notifyDataSetChanged();
            } else {
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        processSearch(query);
                    }
                }, 200, 300);
            }
        }

        private void processSearch(final String query) {
            AndroidUtilities.runOnUIThread(() -> searchAdapterHelper.queryServerSearch(query, false, false, true, true, chat_id, false));
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @Override
        public int getItemCount() {
            return searchAdapterHelper.getGroupSearch().size();
        }

        public TLRPC.ChannelParticipant getItem(int i) {
            return searchAdapterHelper.getGroupSearch().get(i);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new ManageChatUserCell(mContext, 8, true);
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            ((ManageChatUserCell) view).setDelegate((cell, click) -> createMenuForParticipant(null, getItem((Integer) cell.getTag()), !click));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    TLObject object = getItem(position);
                    TLRPC.User user;
                    boolean isAdmin;
                    if (object instanceof TLRPC.User) {
                        user = (TLRPC.User) object;
                        TLRPC.ChatParticipant part = participantsMap.get(user.id);
                        if (part instanceof TLRPC.TL_chatChannelParticipant) {
                            TLRPC.ChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) part).channelParticipant;
                            isAdmin = channelParticipant instanceof TLRPC.TL_channelParticipantCreator || channelParticipant instanceof TLRPC.TL_channelParticipantAdmin;
                        } else {
                            isAdmin = part instanceof TLRPC.TL_chatParticipantAdmin;
                        }
                    } else {
                        isAdmin = object instanceof TLRPC.TL_channelParticipantAdmin || object instanceof TLRPC.TL_channelParticipantCreator;
                        user = MessagesController.getInstance(currentAccount).getUser(((TLRPC.ChannelParticipant) object).user_id);
                    }
                    CharSequence name = null;
                    String nameSearch = searchAdapterHelper.getLastFoundChannel();

                    if (nameSearch != null) {
                        String u = UserObject.getUserName(user);
                        name = new SpannableStringBuilder(u);
                        int idx = u.toLowerCase().indexOf(nameSearch);
                        if (idx != -1) {
                            ((SpannableStringBuilder) name).setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4)), idx, idx + nameSearch.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }

                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    userCell.setTag(position);
                    userCell.setIsAdmin(isAdmin);
                    userCell.setData(user, name, null);

                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new ManageChatTextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new ManageChatUserCell(mContext, 8, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((ManageChatUserCell) view).setDelegate((cell, click) -> {
                        int i = (Integer) cell.getTag();
                        TLRPC.ChatParticipant part;
                        if (!sortedUsers.isEmpty()) {
                            part = info.participants.participants.get(sortedUsers.get(i - membersStartRow));
                        } else {
                            part = info.participants.participants.get(i - membersStartRow);
                        }
                        return createMenuForParticipant((TLRPC.TL_chatChannelParticipant) part, null, !click);
                    });
                    break;
                case 2:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 3:
                    view = new LoadingCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
            boolean checkBackground = true;
            switch (holder.getItemViewType()) {
                case 0:
                    ManageChatTextCell textCell = (ManageChatTextCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    textCell.setTag(Theme.key_windowBackgroundWhiteBlackText);

                    if (i == managementRow) {
                        textCell.setText(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators), info != null ? String.format("%d", info.admins_count) : null, R.drawable.group_admin, blockedUsersRow != -1);
                    } else if (i == blockedUsersRow) {
                        textCell.setText(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist), info != null ? String.format("%d", info.kicked_count + info.banned_count) : null, R.drawable.group_banned, false);
                    } else if (i == eventLogRow) {
                        textCell.setText(LocaleController.getString("EventLog", R.string.EventLog), null, R.drawable.group_log, true);
                    } else if (i == infoRow) {
                        textCell.setText(currentChat.megagroup ? LocaleController.getString("EventLogFilterGroupInfo", R.string.EventLogFilterGroupInfo) : LocaleController.getString("EventLogFilterChannelInfo", R.string.EventLogFilterChannelInfo), null, R.drawable.group_edit, true);
                    } else if (i == permissionsRow) {
                        //textCell.setText(LocaleController.getString("ChatPermissions", R.string.ChatPermissions), null, R.drawable.group_log, true); //TODO icon
                    }
                    break;
                case 1:
                    ManageChatUserCell userCell = ((ManageChatUserCell) holder.itemView);
                    userCell.setTag(i);
                    TLRPC.ChatParticipant part;
                    if (!sortedUsers.isEmpty()) {
                        part = info.participants.participants.get(sortedUsers.get(i - membersStartRow));
                    } else {
                        part = info.participants.participants.get(i - membersStartRow);
                    }
                    if (part != null) {
                        if (part instanceof TLRPC.TL_chatChannelParticipant) {
                            TLRPC.ChannelParticipant channelParticipant = ((TLRPC.TL_chatChannelParticipant) part).channelParticipant;
                            userCell.setIsAdmin(channelParticipant instanceof TLRPC.TL_channelParticipantCreator || channelParticipant instanceof TLRPC.TL_channelParticipantAdmin);
                        } else {
                            userCell.setIsAdmin(part instanceof TLRPC.TL_chatParticipantAdmin);
                        }
                        userCell.setData(MessagesController.getInstance(currentAccount).getUser(part.user_id), null, null);
                    }
                    break;
                case 2:
                    if (i == membersSectionRow && membersStartRow != -1) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 1;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public int getItemViewType(int i) {
            if (i == managementRow || i == blockedUsersRow || i == infoRow || i == eventLogRow || i == permissionsRow) {
                return 0;
            } else if (i >= membersStartRow && i < membersEndRow) {
                return 1;
            } else if (i == membersSectionRow || i == membersSection2Row) {
                return 2;
            } else if (i == loadMoreMembersRow) {
                return 3;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof ManageChatUserCell) {
                        ((ManageChatUserCell) child).update(0);
                    }
                }
            }
        };
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ManageChatTextCell.class, ManageChatUserCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_avatar_backgroundActionBarBlue),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_avatar_actionBarSelectorBlue),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGreenText2),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{ManageChatTextCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),

                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menu),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGray),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),
        };
    }
}
