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
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

public class SetAdminsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private SearchAdapter searchAdapter;
    private EmptyTextProgressView emptyView;
    private RecyclerListView listView;
    private TLRPC.ChatFull info;
    private ArrayList<TLRPC.ChatParticipant> participants = new ArrayList<>();
    private int chat_id;
    private TLRPC.Chat chat;
    private ActionBarMenuItem searchItem;
    private boolean searching;
    private boolean searchWas;

    private int allAdminsRow;
    private int allAdminsInfoRow;
    private int usersStartRow;
    private int usersEndRow;
    private int rowCount;

    public SetAdminsActivity(Bundle args) {
        super(args);
        chat_id = args.getInt("chat_id");
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("SetAdminsTitle", R.string.SetAdminsTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                listView.setEmptyView(emptyView);
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                searchWas = false;
                if (listView != null) {
                    listView.setEmptyView(null);
                    emptyView.setVisibility(View.GONE);
                    if (listView.getAdapter() != listAdapter) {
                        listView.setAdapter(listAdapter);
                    }
                }
                if (searchAdapter != null) {
                    searchAdapter.search(null);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                if (text.length() != 0) {
                    searchWas = true;
                    if (searchAdapter != null && listView.getAdapter() != searchAdapter) {
                        listView.setAdapter(searchAdapter);
                        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    }
                    if (emptyView != null && listView.getEmptyView() != emptyView) {
                        emptyView.showTextView();
                        listView.setEmptyView(emptyView);
                    }
                }
                if (searchAdapter != null) {
                    searchAdapter.search(text);
                }
            }
        });
        searchItem.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));

        listAdapter = new ListAdapter(context);
        searchAdapter = new SearchAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (listView.getAdapter() == searchAdapter || position >= usersStartRow && position < usersEndRow) {
                    UserCell userCell = (UserCell) view;
                    chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                    TLRPC.ChatParticipant participant;
                    int index = -1;
                    if (listView.getAdapter() == searchAdapter) {
                        participant = searchAdapter.getItem(position);
                        for (int a = 0; a < participants.size(); a++) {
                            TLRPC.ChatParticipant p = participants.get(a);
                            if (p.user_id == participant.user_id) {
                                index = a;
                                break;
                            }
                        }
                    } else {
                        participant = participants.get(index = position - usersStartRow);
                    }
                    if (index != -1 && !(participant instanceof TLRPC.TL_chatParticipantCreator)) {
                        TLRPC.ChatParticipant newParticipant;
                        if (participant instanceof TLRPC.TL_chatParticipant) {
                            newParticipant = new TLRPC.TL_chatParticipantAdmin();
                            newParticipant.user_id = participant.user_id;
                            newParticipant.date = participant.date;
                            newParticipant.inviter_id = participant.inviter_id;
                        } else {
                            newParticipant = new TLRPC.TL_chatParticipant();
                            newParticipant.user_id = participant.user_id;
                            newParticipant.date = participant.date;
                            newParticipant.inviter_id = participant.inviter_id;
                        }
                        participants.set(index, newParticipant);
                        index = info.participants.participants.indexOf(participant);
                        if (index != -1) {
                            info.participants.participants.set(index, newParticipant);
                        }
                        if (listView.getAdapter() == searchAdapter) {
                            searchAdapter.searchResult.set(position, newParticipant);
                        }
                        participant = newParticipant;
                        userCell.setChecked(!(participant instanceof TLRPC.TL_chatParticipant) || chat != null && !chat.admins_enabled, true);
                        if (chat != null && chat.admins_enabled) {
                            MessagesController.getInstance(currentAccount).toggleUserAdmin(chat_id, participant.user_id, !(participant instanceof TLRPC.TL_chatParticipant));
                        }
                    }
                } else {
                    if (position == allAdminsRow) {
                        chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                        if (chat != null) {
                            chat.admins_enabled = !chat.admins_enabled;
                            ((TextCheckCell) view).setChecked(!chat.admins_enabled);
                            MessagesController.getInstance(currentAccount).toggleAdminMode(chat_id, chat.admins_enabled);
                        }
                    }
                }
            }
        });

        emptyView = new EmptyTextProgressView(context);
        emptyView.setVisibility(View.GONE);
        emptyView.setShowAtCenter(true);
        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.showTextView();

        updateRowsIds();

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chat_id) {
                info = chatFull;
                updateChatParticipants();
                updateRowsIds();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                if (listView != null) {
                    int count = listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof UserCell) {
                            ((UserCell) child).update(mask);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    public void setChatInfo(TLRPC.ChatFull chatParticipants) {
        info = chatParticipants;
        updateChatParticipants();
    }

    private int getChatAdminParticipantType(TLRPC.ChatParticipant participant) {
        if (participant instanceof TLRPC.TL_chatParticipantCreator) {
            return 0;
        } else if (participant instanceof TLRPC.TL_chatParticipantAdmin) {
            return 1;
        }  else {
            return 2;
        }
    }

    private void updateChatParticipants() {
        if (info == null) {
            return;
        }
        if (participants.size() != info.participants.participants.size()) {
            participants.clear();
            participants.addAll(info.participants.participants);
            try {
                Collections.sort(participants, new Comparator<TLRPC.ChatParticipant>() {
                    @Override
                    public int compare(TLRPC.ChatParticipant lhs, TLRPC.ChatParticipant rhs) {
                        int type1 = getChatAdminParticipantType(lhs);
                        int type2 = getChatAdminParticipantType(rhs);
                        if (type1 > type2) {
                            return 1;
                        } else if (type1 < type2) {
                            return -1;
                        } else if (type1 == type2) {
                            TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(rhs.user_id);
                            TLRPC.User user2 = MessagesController.getInstance(currentAccount).getUser(lhs.user_id);
                            int status1 = 0;
                            int status2 = 0;
                            if (user1 != null && user1.status != null) {
                                status1 = user1.status.expires;
                            }
                            if (user2 != null && user2.status != null) {
                                status2 = user2.status.expires;
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
                        }
                        return 0;
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void updateRowsIds() {
        rowCount = 0;
        allAdminsRow = rowCount++;
        allAdminsInfoRow = rowCount++;
        if (info != null) {
            usersStartRow = rowCount;
            rowCount += participants.size();
            usersEndRow = rowCount++;
            if (searchItem != null && !searchWas) {
                searchItem.setVisibility(View.VISIBLE);
            }
        } else {
            usersStartRow = -1;
            usersEndRow = -1;
            if (searchItem != null) {
                searchItem.setVisibility(View.GONE);
            }
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == allAdminsRow) {
                return true;
            } else if (position >= usersStartRow && position < usersEndRow) {
                TLRPC.ChatParticipant participant = participants.get(position - usersStartRow);
                if (!(participant instanceof TLRPC.TL_chatParticipantCreator)) {
                    return true;
                }
            }
            return false;
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
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                default:
                    view = new UserCell(mContext, 1, 2, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                    checkCell.setTextAndCheck(LocaleController.getString("SetAdminsAll", R.string.SetAdminsAll), chat != null && !chat.admins_enabled, false);
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == allAdminsInfoRow) {
                        if (chat.admins_enabled) {
                            privacyCell.setText(LocaleController.getString("SetAdminsNotAllInfo", R.string.SetAdminsNotAllInfo));
                        } else {
                            privacyCell.setText(LocaleController.getString("SetAdminsAllInfo", R.string.SetAdminsAllInfo));
                        }
                        if (usersStartRow != -1) {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        } else {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        }
                    } else if (position == usersEndRow) {
                        privacyCell.setText("");
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 2:
                    UserCell userCell = (UserCell) holder.itemView;
                    TLRPC.ChatParticipant part = participants.get(position - usersStartRow);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(part.user_id);
                    userCell.setData(user, null, null, 0);
                    chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                    userCell.setChecked(!(part instanceof TLRPC.TL_chatParticipant) || chat != null && !chat.admins_enabled, false);
                    userCell.setCheckDisabled(chat == null || !chat.admins_enabled || part.user_id == UserConfig.getInstance(currentAccount).getClientUserId());
                    break;
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == allAdminsRow) {
                return 0;
            } else if (i == allAdminsInfoRow || i == usersEndRow) {
                return 1;
            } else {
                return 2;
            }
        }
    }

    public class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<TLRPC.ChatParticipant> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private Timer searchTimer;

        public SearchAdapter(Context context) {
            mContext = context;
        }

        public void search(final String query) {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (query == null) {
                searchResult.clear();
                searchResultNames.clear();
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
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    final ArrayList<TLRPC.ChatParticipant> contactsCopy = new ArrayList<>();
                    contactsCopy.addAll(participants);
                    Utilities.searchQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            String search1 = query.trim().toLowerCase();
                            if (search1.length() == 0) {
                                updateSearchResults(new ArrayList<TLRPC.ChatParticipant>(), new ArrayList<CharSequence>());
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

                            ArrayList<TLRPC.ChatParticipant> resultArray = new ArrayList<>();
                            ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                            for (int a = 0; a < contactsCopy.size(); a++) {
                                TLRPC.ChatParticipant participant = contactsCopy.get(a);
                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                                if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                    continue;
                                }

                                String name = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                                String tName = LocaleController.getInstance().getTranslitString(name);
                                if (name.equals(tName)) {
                                    tName = null;
                                }

                                int found = 0;
                                for (String q : search) {
                                    if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                        found = 1;
                                    } else if (user.username != null && user.username.startsWith(q)) {
                                        found = 2;
                                    }

                                    if (found != 0) {
                                        if (found == 1) {
                                            resultArrayNames.add(AndroidUtilities.generateSearchName(user.first_name, user.last_name, q));
                                        } else {
                                            resultArrayNames.add(AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q));
                                        }
                                        resultArray.add(participant);
                                        break;
                                    }
                                }
                            }

                            updateSearchResults(resultArray, resultArrayNames);
                        }
                    });
                }
            });
        }

        private void updateSearchResults(final ArrayList<TLRPC.ChatParticipant> users, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    searchResult = users;
                    searchResultNames = names;
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return searchResult.size();
        }

        public TLRPC.ChatParticipant getItem(int i) {
            return searchResult.get(i);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new UserCell(mContext, 1, 2, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TLRPC.ChatParticipant participant = getItem(position);
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
            String un = user.username;

            CharSequence username = null;
            CharSequence name = null;
            if (position < searchResult.size()) {
                name = searchResultNames.get(position);
                if (name != null && un != null && un.length() > 0) {
                    if (name.toString().startsWith("@" + un)) {
                        username = name;
                        name = null;
                    }
                }
            }
            UserCell userCell = (UserCell) holder.itemView;
            userCell.setData(user, name, username, 0);
            chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
            userCell.setChecked(!(participant instanceof TLRPC.TL_chatParticipant) || chat != null && !chat.admins_enabled, false);
            userCell.setCheckDisabled(chat == null || !chat.admins_enabled || participant.user_id == UserConfig.getInstance(currentAccount).getClientUserId());
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                if (listView != null) {
                    int count = listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof UserCell) {
                            ((UserCell) child).update(0);
                        }
                    }
                }
            }
        };

        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckCell.class, UserCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumb),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumbChecked),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, null, null, Theme.key_checkboxSquareUnchecked),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, null, null, Theme.key_checkboxSquareDisabled),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, null, null, Theme.key_checkboxSquareBackground),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, null, null, Theme.key_checkboxSquareCheck),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),
        };
    }
}
