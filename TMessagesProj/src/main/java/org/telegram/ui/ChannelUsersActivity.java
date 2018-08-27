/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
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
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class ChannelUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private EmptyTextProgressView emptyView;
    private RecyclerListView listView;
    private SearchAdapter searchListViewAdapter;
    private ActionBarMenuItem searchItem;

    private TLRPC.Chat currentChat;

    private ArrayList<TLRPC.ChannelParticipant> participants = new ArrayList<>();
    private ArrayList<TLRPC.ChannelParticipant> participants2 = new ArrayList<>();
    private SparseArray<TLRPC.ChannelParticipant> participantsMap = new SparseArray<>();
    private int chatId;
    private int type;
    private boolean loadingUsers;
    private boolean firstLoaded;

    private int changeAddHeaderRow;
    private int changeAddRadio1Row;
    private int changeAddRadio2Row;
    private int changeAddSectionRow;
    private int addNewRow;
    private int addNew2Row;
    private int addNewSectionRow;
    private int restricted1SectionRow;
    private int participantsStartRow;
    private int participantsEndRow;
    private int participantsDividerRow;
    private int restricted2SectionRow;
    private int participants2StartRow;
    private int participants2EndRow;
    private int participantsInfoRow;
    private int blockedEmptyRow;
    private int rowCount;
    private int selectType;

    private boolean firstEndReached;

    private boolean needOpenSearch;

    private boolean searchWas;
    private boolean searching;

    private final static int search_button = 0;

    public ChannelUsersActivity(Bundle args) {
        super(args);
        chatId = arguments.getInt("chat_id");
        type = arguments.getInt("type");
        needOpenSearch = arguments.getBoolean("open_search");
        selectType = arguments.getInt("selectType");
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
    }

    private void updateRows() {
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        if (currentChat == null) {
            return;
        }
        changeAddHeaderRow = -1;
        changeAddRadio1Row = -1;
        changeAddRadio2Row = -1;
        changeAddSectionRow = -1;
        addNewRow = -1;
        addNew2Row = -1;
        addNewSectionRow = -1;
        restricted1SectionRow = -1;
        participantsStartRow = -1;
        participantsDividerRow = -1;
        participantsEndRow = -1;
        restricted2SectionRow = -1;
        participants2StartRow = -1;
        participants2EndRow = -1;
        participantsInfoRow = -1;
        blockedEmptyRow = -1;

        rowCount = 0;
        if (type == 0) {
            if (ChatObject.canBlockUsers(currentChat)) {
                addNewRow = rowCount++;
                if (!participants.isEmpty() || !participants2.isEmpty()) {
                    addNewSectionRow = rowCount++;
                }
            } else {
                addNewRow = -1;
                addNewSectionRow = -1;
            }
            if (!participants.isEmpty()) {
                restricted1SectionRow = rowCount++;
                participantsStartRow = rowCount;
                rowCount += participants.size();
                participantsEndRow = rowCount;
            }
            if (!participants2.isEmpty()) {
                if (restricted1SectionRow != -1) {
                    participantsDividerRow = rowCount++;
                }
                restricted2SectionRow = rowCount++;
                participants2StartRow = rowCount;
                rowCount += participants2.size();
                participants2EndRow = rowCount;
            }
            if (participantsStartRow != -1 || participants2StartRow != -1) {
                if (searchItem != null) {
                    searchItem.setVisibility(View.VISIBLE);
                }
                participantsInfoRow = rowCount++;
            } else {
                if (searchItem != null) {
                    searchItem.setVisibility(View.INVISIBLE);
                }
                blockedEmptyRow = rowCount++;
            }
        } else if (type == 1) {
            if ((currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.change_info) && currentChat.megagroup) {
                changeAddHeaderRow = rowCount++;
                changeAddRadio1Row = rowCount++;
                changeAddRadio2Row = rowCount++;
                changeAddSectionRow = rowCount++;
            }
            if (ChatObject.canAddAdmins(currentChat)) {
                addNewRow = rowCount++;
                addNewSectionRow = rowCount++;
            } else {
                addNewRow = -1;
                addNewSectionRow = -1;
            }
            if (!participants.isEmpty()) {
                participantsStartRow = rowCount;
                rowCount += participants.size();
                participantsEndRow = rowCount;
            } else {
                participantsStartRow = -1;
                participantsEndRow = -1;
            }
            participantsInfoRow = rowCount++;
        } else if (type == 2) {
            if (selectType == 0 && !currentChat.megagroup && ChatObject.canAddUsers(currentChat)) {
                addNewRow = rowCount++;
                if ((currentChat.flags & TLRPC.CHAT_FLAG_IS_PUBLIC) == 0 && ChatObject.canAddViaLink(currentChat)) {
                    addNew2Row = rowCount++;
                }
                addNewSectionRow = rowCount++;
            }
            if (!participants.isEmpty()) {
                participantsStartRow = rowCount;
                rowCount += participants.size();
                participantsEndRow = rowCount;
            } else {
                participantsStartRow = -1;
                participantsEndRow = -1;
            }
            if (rowCount != 0) {
                participantsInfoRow = rowCount++;
            }
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoaded);
        getChannelParticipants(0, 200);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoaded);
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (type == 0) {
            actionBar.setTitle(LocaleController.getString("ChannelBlacklist", R.string.ChannelBlacklist));
        } else if (type == 1) {
            actionBar.setTitle(LocaleController.getString("ChannelAdministrators", R.string.ChannelAdministrators));
        } else if (type == 2) {
            if (selectType == 0) {
                if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                    actionBar.setTitle(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers));
                } else {
                    actionBar.setTitle(LocaleController.getString("ChannelMembers", R.string.ChannelMembers));
                }
            } else {
                if (selectType == 1) {
                    actionBar.setTitle(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin));
                } else if (selectType == 2) {
                    actionBar.setTitle(LocaleController.getString("ChannelBlockUser", R.string.ChannelBlockUser));
                }
            }
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        if (selectType != 0 || type == 2 || type == 0) {
            searchListViewAdapter = new SearchAdapter(context);
            ActionBarMenu menu = actionBar.createMenu();
            searchItem = menu.addItem(search_button, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                    emptyView.setShowAtCenter(true);
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
                    emptyView.setShowAtCenter(false);
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
        }

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        if (type == 0 || type == 2) {
            emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        }
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position == addNewRow) {
                if (type == 0) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("chat_id", chatId);
                    bundle.putInt("type", 2);
                    bundle.putInt("selectType", 2);
                    presentFragment(new ChannelUsersActivity(bundle));
                } else if (type == 1) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("chat_id", chatId);
                    bundle.putInt("type", 2);
                    bundle.putInt("selectType", 1);
                    presentFragment(new ChannelUsersActivity(bundle));
                } else if (type == 2) {
                    Bundle args = new Bundle();
                    args.putBoolean("onlyUsers", true);
                    args.putBoolean("destroyAfterSelect", true);
                    args.putBoolean("returnAsResult", true);
                    args.putBoolean("needForwardCount", false);
                    args.putString("selectAlertString", LocaleController.getString("ChannelAddTo", R.string.ChannelAddTo));
                    ContactsActivity fragment = new ContactsActivity(args);
                    fragment.setDelegate((user, param, activity) -> MessagesController.getInstance(currentAccount).addUserToChat(chatId, user, null, param != null ? Utilities.parseInt(param) : 0, null, ChannelUsersActivity.this));
                    presentFragment(fragment);
                }
            } else if (position == addNew2Row) {
                presentFragment(new GroupInviteActivity(chatId));
            } else if (position == changeAddRadio1Row || position == changeAddRadio2Row) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
                if (chat == null) {
                    return;
                }
                boolean changed = false;
                if (position == 1 && !chat.democracy) {
                    chat.democracy = true;
                    changed = true;
                } else if (position == 2 && chat.democracy) {
                    chat.democracy = false;
                    changed = true;
                }
                if (changed) {
                    MessagesController.getInstance(currentAccount).toogleChannelInvites(chatId, chat.democracy);
                    int count = listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof RadioCell) {
                            int num = (Integer) child.getTag();
                            ((RadioCell) child).setChecked(num == 0 && chat.democracy || num == 1 && !chat.democracy, true);
                        }
                    }
                }
            } else {
                TLRPC.TL_channelBannedRights banned_rights = null;
                TLRPC.TL_channelAdminRights admin_rights = null;
                final TLRPC.ChannelParticipant participant;
                int user_id = 0;
                int promoted_by = 0;
                boolean canEditAdmin = false;
                if (listView.getAdapter() == listViewAdapter) {
                    participant = listViewAdapter.getItem(position);
                    if (participant != null) {
                        user_id = participant.user_id;
                        banned_rights = participant.banned_rights;
                        admin_rights = participant.admin_rights;
                        canEditAdmin = !(participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_channelParticipantCreator) || participant.can_edit;
                        if (participant instanceof TLRPC.TL_channelParticipantCreator) {
                            admin_rights = new TLRPC.TL_channelAdminRights();
                            admin_rights.change_info = admin_rights.post_messages = admin_rights.edit_messages =
                                    admin_rights.delete_messages = admin_rights.ban_users = admin_rights.invite_users =
                                            admin_rights.invite_link = admin_rights.pin_messages = admin_rights.add_admins = true;
                        }
                    }
                } else {
                    TLObject object = searchListViewAdapter.getItem(position);
                    if (object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) object;
                        MessagesController.getInstance(currentAccount).putUser(user, false);
                        participant = participantsMap.get(user_id = user.id);
                    } else if (object instanceof TLRPC.ChannelParticipant) {
                        participant = (TLRPC.ChannelParticipant) object;
                    } else {
                        participant = null;
                    }
                    if (participant != null) {
                        user_id = participant.user_id;
                        canEditAdmin = !(participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_channelParticipantCreator) || participant.can_edit;
                        banned_rights = participant.banned_rights;
                        admin_rights = participant.admin_rights;
                    } else {
                        canEditAdmin = true;
                    }
                }
                if (user_id != 0) {
                    if (selectType != 0) {
                        if (currentChat.megagroup || selectType == 1) {
                            ChannelRightsEditActivity fragment = new ChannelRightsEditActivity(user_id, chatId, admin_rights, banned_rights, selectType == 1 ? 0 : 1, canEditAdmin);
                            fragment.setDelegate((rights, rightsAdmin, rightsBanned) -> {
                                if (participant != null) {
                                    participant.admin_rights = rightsAdmin;
                                    participant.banned_rights = rightsBanned;
                                    TLRPC.ChannelParticipant p = participantsMap.get(participant.user_id);
                                    if (p != null) {
                                        p.admin_rights = rightsAdmin;
                                        p.banned_rights = rightsBanned;
                                    }
                                }
                                removeSelfFromStack();
                            });
                            presentFragment(fragment);
                        } else {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(user_id);
                            MessagesController.getInstance(currentAccount).deleteUserFromChat(chatId, user, null);
                            finishFragment();
                        }
                    } else {
                        boolean canEdit = false;
                        if (type == 1) {
                            canEdit = user_id != UserConfig.getInstance(currentAccount).getClientUserId() && (currentChat.creator || canEditAdmin);
                        } else if (type == 0) {
                            canEdit = ChatObject.canBlockUsers(currentChat);
                        }
                        if (type != 1 && !currentChat.megagroup || type == 2 && selectType == 0) {
                            Bundle args = new Bundle();
                            args.putInt("user_id", user_id);
                            presentFragment(new ProfileActivity(args));
                        } else {
                            if (banned_rights == null) {
                                banned_rights = new TLRPC.TL_channelBannedRights();
                                banned_rights.view_messages = true;
                                banned_rights.send_stickers = true;
                                banned_rights.send_media = true;
                                banned_rights.embed_links = true;
                                banned_rights.send_messages = true;
                                banned_rights.send_games = true;
                                banned_rights.send_inline = true;
                                banned_rights.send_gifs = true;
                            }
                            ChannelRightsEditActivity fragment = new ChannelRightsEditActivity(user_id, chatId, admin_rights, banned_rights, type == 1 ? 0 : 1, canEdit);
                            fragment.setDelegate((rights, rightsAdmin, rightsBanned) -> {
                                if (participant != null) {
                                    participant.admin_rights = rightsAdmin;
                                    participant.banned_rights = rightsBanned;
                                    TLRPC.ChannelParticipant p = participantsMap.get(participant.user_id);
                                    if (p != null) {
                                        p.admin_rights = rightsAdmin;
                                        p.banned_rights = rightsBanned;
                                    }
                                }
                            });
                            presentFragment(fragment);
                        }
                    }
                }
            }
        });

        listView.setOnItemLongClickListener((view, position) -> !(getParentActivity() == null || listView.getAdapter() != listViewAdapter) && createMenuForParticipant(listViewAdapter.getItem(position), false));
        if (searchItem != null) {
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && searchWas) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {

                }
            });
        }

        if (loadingUsers) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }
        updateRows();
        return fragmentView;
    }

    private boolean createMenuForParticipant(final TLRPC.ChannelParticipant participant, boolean resultOnly) {
        if (participant == null || selectType != 0) {
            return false;
        }
        if (participant.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return false;
        }
        if (type == 2) {
            final TLRPC.ChannelParticipant channelParticipant;

            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
            boolean allowSetAdmin = participant instanceof TLRPC.TL_channelParticipant || participant instanceof TLRPC.TL_channelParticipantBanned;
            boolean canEditAdmin = !(participant instanceof TLRPC.TL_channelParticipantAdmin || participant instanceof TLRPC.TL_channelParticipantCreator) || participant.can_edit;

            final ArrayList<String> items;
            final ArrayList<Integer> actions;
            if (!resultOnly) {
                items = new ArrayList<>();
                actions = new ArrayList<>();
            } else {
                items = null;
                actions = null;
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
            if (actions == null || actions.isEmpty()) {
                return false;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(items.toArray(new CharSequence[actions.size()]), (dialogInterface, i) -> {
                if (actions.get(i) == 2) {
                    MessagesController.getInstance(currentAccount).deleteUserFromChat(chatId, user, null);
                    for (int a = 0; a < participants.size(); a++) {
                        TLRPC.ChannelParticipant p = participants.get(a);
                        if (p.user_id == participant.user_id) {
                            participants.remove(a);
                            updateRows();
                            listViewAdapter.notifyDataSetChanged();
                            break;
                        }
                    }
                } else {
                    ChannelRightsEditActivity fragment = new ChannelRightsEditActivity(user.id, chatId, participant.admin_rights, participant.banned_rights, actions.get(i), true);
                    fragment.setDelegate((rights, rightsAdmin, rightsBanned) -> {
                        if (actions.get(i) == 0) {
                            for (int a = 0; a < participants.size(); a++) {
                                TLRPC.ChannelParticipant p = participants.get(a);
                                if (p.user_id == participant.user_id) {
                                    TLRPC.ChannelParticipant newPart;
                                    if (rights == 1) {
                                        newPart = new TLRPC.TL_channelParticipantAdmin();
                                    } else {
                                        newPart = new TLRPC.TL_channelParticipant();
                                    }
                                    newPart.admin_rights = rightsAdmin;
                                    newPart.banned_rights = rightsBanned;
                                    newPart.inviter_id = UserConfig.getInstance(currentAccount).getClientUserId();
                                    newPart.user_id = participant.user_id;
                                    newPart.date = participant.date;
                                    participants.set(a, newPart);
                                    break;
                                }
                            }
                        } else if (actions.get(i) == 1) {
                            if (rights == 0) {
                                for (int a = 0; a < participants.size(); a++) {
                                    TLRPC.ChannelParticipant p = participants.get(a);
                                    if (p.user_id == participant.user_id) {
                                        participants.remove(a);
                                        updateRows();
                                        listViewAdapter.notifyDataSetChanged();
                                        break;
                                    }
                                }
                            }
                        }
                    });
                    presentFragment(fragment);
                }
            });
            showDialog(builder.create());
        } else {
            CharSequence[] items = null;
            if (type == 0 && ChatObject.canBlockUsers(currentChat)) {
                if (resultOnly) {
                    return true;
                }
                items = new CharSequence[]{LocaleController.getString("Unban", R.string.Unban)};
            } else if (type == 1 && ChatObject.canAddAdmins(currentChat) && participant.can_edit) {
                if (resultOnly) {
                    return true;
                }
                items = new CharSequence[]{LocaleController.getString("ChannelRemoveUserAdmin", R.string.ChannelRemoveUserAdmin)};
            }
            if (items == null) {
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setItems(items, (dialogInterface, i) -> {
                if (i == 0) {
                    if (type == 0) {
                        participants.remove(participant);
                        updateRows();
                        listViewAdapter.notifyDataSetChanged();
                        TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
                        req.user_id = MessagesController.getInstance(currentAccount).getInputUser(participant.user_id);
                        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
                        req.banned_rights = new TLRPC.TL_channelBannedRights();
                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                            if (response != null) {
                                final TLRPC.Updates updates = (TLRPC.Updates) response;
                                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                                if (!updates.chats.isEmpty()) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        TLRPC.Chat chat = updates.chats.get(0);
                                        MessagesController.getInstance(currentAccount).loadFullChat(chat.id, 0, true);
                                    }, 1000);
                                }
                            }
                        });
                    } else if (type == 1) {
                        MessagesController.getInstance(currentAccount).setUserAdminRole(chatId, MessagesController.getInstance(currentAccount).getUser(participant.user_id), new TLRPC.TL_channelAdminRights(), currentChat.megagroup, ChannelUsersActivity.this);
                    } else if (type == 2) {
                        MessagesController.getInstance(currentAccount).deleteUserFromChat(chatId, MessagesController.getInstance(currentAccount).getUser(participant.user_id), null);
                    }
                }
            });
            showDialog(builder.create());
        }
        return true;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoaded) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            boolean byChannelUsers = (Boolean) args[2];
            if (chatFull.id == chatId && !byChannelUsers) {
                AndroidUtilities.runOnUIThread(() -> {
                    firstEndReached = false;
                    getChannelParticipants(0, 200);
                });
            }
        }
    }

    private int getChannelAdminParticipantType(TLRPC.ChannelParticipant participant) {
        if (participant instanceof TLRPC.TL_channelParticipantCreator || participant instanceof TLRPC.TL_channelParticipantSelf) {
            return 0;
        } else if (participant instanceof TLRPC.TL_channelParticipantAdmin) {
            return 1;
        }  else {
            return 2;
        }
    }

    private void getChannelParticipants(int offset, int count) {
        if (loadingUsers) {
            return;
        }
        loadingUsers = true;
        if (emptyView != null && !firstLoaded) {
            emptyView.showProgress();
        }
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
        final boolean byEndReached = firstEndReached;
        if (type == 0) {
            if (byEndReached) {
                req.filter = new TLRPC.TL_channelParticipantsKicked();
            } else {
                req.filter = new TLRPC.TL_channelParticipantsBanned();
            }
        } else if (type == 1) {
            req.filter = new TLRPC.TL_channelParticipantsAdmins();
        } else if (type == 2) {
            req.filter = new TLRPC.TL_channelParticipantsRecent();
        }
        req.filter.q = "";
        req.offset = offset;
        req.limit = count;
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            boolean changeFirst = !firstLoaded;
            loadingUsers = false;
            firstLoaded = true;
            if (emptyView != null) {
                emptyView.showTextView();
            }
            if (error == null) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                int selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                if (selectType != 0) {
                    for (int a = 0; a < res.participants.size(); a++) {
                        if (res.participants.get(a).user_id == selfId) {
                            res.participants.remove(a);
                            break;
                        }
                    }
                }
                if (type == 0) {
                    if (byEndReached) {
                        participants2 = res.participants;
                    } else {
                        participants2 = new ArrayList<>();
                        participantsMap.clear();
                        participants = res.participants;
                        if (changeFirst) {
                            firstLoaded = false;
                        }
                        firstEndReached = true;
                        getChannelParticipants(0, 200);
                    }
                } else {
                    participantsMap.clear();
                    participants = res.participants;
                }
                for (int a = 0; a < res.participants.size(); a++) {
                    TLRPC.ChannelParticipant participant = res.participants.get(a);
                    participantsMap.put(participant.user_id, participant);
                }
                try {
                    if (type == 0 || type == 2) {
                        Collections.sort(res.participants, (lhs, rhs) -> {
                            TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(rhs.user_id);
                            TLRPC.User user2 = MessagesController.getInstance(currentAccount).getUser(lhs.user_id);
                            int status1 = 0;
                            int status2 = 0;
                            if (user1 != null && user1.status != null) {
                                if (user1.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                    status1 = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 50000;
                                } else {
                                    status1 = user1.status.expires;
                                }
                            }
                            if (user2 != null && user2.status != null) {
                                if (user2.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                                    status2 = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 50000;
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
                    } else if (type == 1) {
                        Collections.sort(res.participants, (lhs, rhs) -> {
                            int type1 = getChannelAdminParticipantType(lhs);
                            int type2 = getChannelAdminParticipantType(rhs);
                            if (type1 > type2) {
                                return 1;
                            } else if (type1 < type2) {
                                return -1;
                            }
                            return 0;
                        });
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            updateRows();
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        }));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && !backward && needOpenSearch) {
            searchItem.openSearch(true);
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<TLRPC.User> searchResult = new ArrayList<>();
        private ArrayList<CharSequence> searchResultNames = new ArrayList<>();
        private SearchAdapterHelper searchAdapterHelper;
        private Timer searchTimer;

        private int groupStartRow;
        private int group2StartRow;
        private int contactsStartRow;
        private int globalStartRow;
        private int totalCount;

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
                searchResult.clear();
                searchResultNames.clear();
                searchAdapterHelper.queryServerSearch(null, type != 0, false, true, true, chatId, type == 0);
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
            AndroidUtilities.runOnUIThread(() -> {
                searchAdapterHelper.queryServerSearch(query, selectType != 0, false, true, true, chatId, type == 0);
                if (selectType == 1) {
                    final ArrayList<TLRPC.TL_contact> contactsCopy = new ArrayList<>(ContactsController.getInstance(currentAccount).contacts);
                    Utilities.searchQueue.postRunnable(() -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), new ArrayList<>());
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

                        ArrayList<TLRPC.User> resultArray = new ArrayList<>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();

                        for (int a = 0; a < contactsCopy.size(); a++) {
                            TLRPC.TL_contact contact = contactsCopy.get(a);
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contact.user_id);
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
                                    resultArray.add(user);
                                    break;
                                }
                            }
                        }

                        updateSearchResults(resultArray, resultArrayNames);
                    });
                }
            });
        }

        private void updateSearchResults(final ArrayList<TLRPC.User> users, final ArrayList<CharSequence> names) {
            AndroidUtilities.runOnUIThread(() -> {
                searchResult = users;
                searchResultNames = names;
                notifyDataSetChanged();
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @Override
        public int getItemCount() {
            int contactsCount = searchResult.size();
            int globalCount = searchAdapterHelper.getGlobalSearch().size();
            int groupsCount = searchAdapterHelper.getGroupSearch().size();
            int groupsCount2 = searchAdapterHelper.getGroupSearch2().size();
            int count = 0;
            if (contactsCount != 0) {
                count += contactsCount + 1;
            }
            if (globalCount != 0) {
                count += globalCount + 1;
            }
            if (groupsCount != 0) {
                count += groupsCount + 1;
            }
            if (groupsCount2 != 0) {
                count += groupsCount2 + 1;
            }
            return count;
        }

        @Override
        public void notifyDataSetChanged() {
            totalCount = 0;
            int count = searchAdapterHelper.getGroupSearch().size();
            if (count != 0) {
                groupStartRow = 0;
                totalCount += count + 1;
            } else {
                groupStartRow = -1;
            }
            count = searchAdapterHelper.getGroupSearch2().size();
            if (count != 0) {
                group2StartRow = totalCount;
                totalCount += count + 1;
            } else {
                group2StartRow = -1;
            }
            count = searchResult.size();
            if (count != 0) {
                contactsStartRow = totalCount;
                totalCount += count + 1;
            } else {
                contactsStartRow = -1;
            }
            count = searchAdapterHelper.getGlobalSearch().size();
            if (count != 0) {
                globalStartRow = totalCount;
                totalCount += count + 1;
            } else {
                globalStartRow = -1;
            }
            super.notifyDataSetChanged();
        }

        public TLObject getItem(int i) {
            int count = searchAdapterHelper.getGroupSearch().size();
            if (count != 0) {
                if (count + 1 > i) {
                    if (i == 0) {
                        return null;
                    } else {
                        return searchAdapterHelper.getGroupSearch().get(i - 1);
                    }
                } else {
                    i -= count + 1;
                }
            }
            count = searchAdapterHelper.getGroupSearch2().size();
            if (count != 0) {
                if (count + 1 > i) {
                    if (i == 0) {
                        return null;
                    } else {
                        return searchAdapterHelper.getGroupSearch2().get(i - 1);
                    }
                } else {
                    i -= count + 1;
                }
            }
            count = searchResult.size();
            if (count != 0) {
                if (count + 1 > i) {
                    if (i == 0) {
                        return null;
                    } else {
                        return searchResult.get(i - 1);
                    }
                } else {
                    i -= count + 1;
                }
            }
            count = searchAdapterHelper.getGlobalSearch().size();
            if (count != 0) {
                if (count + 1 > i) {
                    if (i == 0) {
                        return null;
                    } else {
                        return searchAdapterHelper.getGlobalSearch().get(i - 1);
                    }
                }
            }
            return null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ManageChatUserCell(mContext, 2, selectType == 0);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((ManageChatUserCell) view).setDelegate((cell, click) -> {
                        TLObject object = getItem((Integer) cell.getTag());
                        if (object instanceof TLRPC.ChannelParticipant) {
                            TLRPC.ChannelParticipant participant = (TLRPC.ChannelParticipant) getItem((Integer) cell.getTag());
                            return createMenuForParticipant(participant, !click);
                        } else {
                            return false;
                        }
                    });
                    break;
                case 1:
                default:
                    view = new GraySectionCell(mContext);
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
                    } else {
                        user = MessagesController.getInstance(currentAccount).getUser(((TLRPC.ChannelParticipant) object).user_id);
                    }

                    String un = user.username;
                    CharSequence username = null;
                    CharSequence name = null;

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
                    if (!ok) {
                        count = searchAdapterHelper.getGroupSearch2().size();
                        if (count != 0) {
                            if (count + 1 > position) {
                                nameSearch = searchAdapterHelper.getLastFoundChannel2();
                            } else {
                                position -= count + 1;
                            }
                        }
                    }
                    if (!ok) {
                        count = searchResult.size();
                        if (count != 0) {
                            if (count + 1 > position) {
                                ok = true;
                                name = searchResultNames.get(position - 1);
                                if (name != null && un != null && un.length() > 0) {
                                    if (name.toString().startsWith("@" + un)) {
                                        username = name;
                                        name = null;
                                    }
                                }
                            } else {
                                position -= count + 1;
                            }
                        }
                    }
                    if (!ok) {
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
                                    if ((index = un.toLowerCase().indexOf(foundUserName)) != -1) {
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
                    userCell.setData(user, name, username);

                    break;
                }
                case 1: {
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    if (position == groupStartRow) {
                        if (type == 0) {
                            sectionCell.setText(LocaleController.getString("ChannelRestrictedUsers", R.string.ChannelRestrictedUsers).toUpperCase());
                        } else {
                            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                                actionBar.setTitle(LocaleController.getString("ChannelSubscribers", R.string.ChannelSubscribers));
                            } else {
                                sectionCell.setText(LocaleController.getString("ChannelMembers", R.string.ChannelMembers).toUpperCase());
                            }
                        }
                    } else if (position == group2StartRow) {
                        sectionCell.setText(LocaleController.getString("ChannelBlockedUsers", R.string.ChannelBlockedUsers).toUpperCase());
                    } else if (position == globalStartRow) {
                        sectionCell.setText(LocaleController.getString("GlobalSearch", R.string.GlobalSearch).toUpperCase());
                    } else if (position == contactsStartRow) {
                        sectionCell.setText(LocaleController.getString("Contacts", R.string.Contacts).toUpperCase());
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
            if (i == globalStartRow || i == groupStartRow || i == contactsStartRow || i == group2StartRow) {
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
            int type = holder.getItemViewType();
            return type == 0 || type == 2 || type == 6;
        }

        @Override
        public int getItemCount() {
            if (loadingUsers && !firstLoaded) {
                return 0;
            }
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ManageChatUserCell(mContext, type == 0 ? 8 : 1, selectType == 0);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((ManageChatUserCell) view).setDelegate((cell, click) -> {
                        TLRPC.ChannelParticipant participant = listViewAdapter.getItem((Integer) cell.getTag());
                        return createMenuForParticipant(participant, !click);
                    });
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new ManageChatTextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 4:
                    view = new FrameLayout(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
                        }
                    };
                    FrameLayout frameLayout = (FrameLayout) view;
                    frameLayout.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));

                    LinearLayout linearLayout = new LinearLayout(mContext);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 0));

                    ImageView imageView = new ImageView(mContext);
                    imageView.setImageResource(R.drawable.group_ban_empty);
                    imageView.setScaleType(ImageView.ScaleType.CENTER);
                    imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_emptyListPlaceholder), PorterDuff.Mode.MULTIPLY));
                    linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

                    TextView textView = new TextView(mContext);
                    textView.setText(LocaleController.getString("NoBlockedUsers", R.string.NoBlockedUsers));
                    textView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    textView.setGravity(Gravity.CENTER_HORIZONTAL);
                    textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

                    textView = new TextView(mContext);
                    if (currentChat.megagroup) {
                        textView.setText(LocaleController.getString("NoBlockedGroup", R.string.NoBlockedGroup));
                    } else {
                        textView.setText(LocaleController.getString("NoBlockedChannel", R.string.NoBlockedChannel));
                    }
                    textView.setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder));
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    textView.setGravity(Gravity.CENTER_HORIZONTAL);
                    linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    break;
                case 5:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                default:
                    view = new RadioCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                    TLRPC.ChannelParticipant participant = getItem(position);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(participant.user_id);
                    if (user != null) {
                        if (type == 0) {
                            String role = null;
                            if (participant instanceof TLRPC.TL_channelParticipantBanned) {
                                TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(participant.kicked_by);
                                if (user1 != null) {
                                    role = LocaleController.formatString("UserRestrictionsBy", R.string.UserRestrictionsBy, ContactsController.formatName(user1.first_name, user1.last_name));
                                }
                            }
                            userCell.setData(user, null, role);
                        } else if (type == 1) {
                            String role = null;
                            if (participant instanceof TLRPC.TL_channelParticipantCreator || participant instanceof TLRPC.TL_channelParticipantSelf) {
                                role = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                            } else if (participant instanceof TLRPC.TL_channelParticipantAdmin) {
                                TLRPC.User user1 = MessagesController.getInstance(currentAccount).getUser(participant.promoted_by);
                                if (user1 != null) {
                                    role = LocaleController.formatString("EditAdminPromotedBy", R.string.EditAdminPromotedBy, ContactsController.formatName(user1.first_name, user1.last_name));
                                }
                            }
                            userCell.setData(user, null, role);
                        } else if (type == 2) {
                            userCell.setData(user, null, null);
                        }
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == participantsInfoRow) {
                        if (type == 0) {
                            if (ChatObject.canBlockUsers(currentChat)) {
                                if (currentChat.megagroup) {
                                    privacyCell.setText(String.format("%1$s\n\n%2$s", LocaleController.getString("NoBlockedGroup", R.string.NoBlockedGroup), LocaleController.getString("UnbanText", R.string.UnbanText)));
                                } else {
                                    privacyCell.setText(String.format("%1$s\n\n%2$s", LocaleController.getString("NoBlockedChannel", R.string.NoBlockedChannel), LocaleController.getString("UnbanText", R.string.UnbanText)));
                                }
                            } else {
                                if (currentChat.megagroup) {
                                    privacyCell.setText(LocaleController.getString("NoBlockedGroup", R.string.NoBlockedGroup));
                                } else {
                                    privacyCell.setText(LocaleController.getString("NoBlockedChannel", R.string.NoBlockedChannel));
                                }
                            }
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        } else if (type == 1) {
                            if (addNewRow != -1) {
                                if (currentChat.megagroup) {
                                    privacyCell.setText(LocaleController.getString("MegaAdminsInfo", R.string.MegaAdminsInfo));
                                } else {
                                    privacyCell.setText(LocaleController.getString("ChannelAdminsInfo", R.string.ChannelAdminsInfo));
                                }
                                privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            } else {
                                privacyCell.setText("");
                                privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                            }
                        } else if (type == 2) {
                            if (currentChat.megagroup || selectType != 0) {
                                privacyCell.setText("");
                            } else {
                                privacyCell.setText(LocaleController.getString("ChannelMembersInfo", R.string.ChannelMembersInfo));
                            }
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        }
                    }
                    break;
                case 2:
                    ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                    if (position == addNewRow) {
                        if (type == 0) {
                            actionCell.setText(LocaleController.getString("ChannelBlockUser", R.string.ChannelBlockUser), null, R.drawable.group_ban_new, false);
                        } else if (type == 1) {
                            actionCell.setText(LocaleController.getString("ChannelAddAdmin", R.string.ChannelAddAdmin), null, R.drawable.group_admin_new, false);
                        } else if (type == 2) {
                            if (ChatObject.isChannel(currentChat) && !currentChat.megagroup) {
                                actionCell.setText(LocaleController.getString("AddSubscriber", R.string.AddSubscriber), null, R.drawable.menu_invite, true);
                            } else {
                                actionCell.setText(LocaleController.getString("AddMember", R.string.AddMember), null, R.drawable.menu_invite, true);
                            }
                        }
                    } else if (position == addNew2Row) {
                        actionCell.setText(LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink), null, R.drawable.msg_panel_link, false);
                    }
                    break;
                case 5:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == restricted1SectionRow) {
                        headerCell.setText(LocaleController.getString("ChannelRestrictedUsers", R.string.ChannelRestrictedUsers));
                    } else if (position == restricted2SectionRow) {
                        headerCell.setText(LocaleController.getString("ChannelBlockedUsers", R.string.ChannelBlockedUsers));
                    } else if (position == changeAddHeaderRow) {
                        headerCell.setText(LocaleController.getString("WhoCanAddMembers", R.string.WhoCanAddMembers));
                    }
                    break;
                case 6:
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
                    if (position == changeAddRadio1Row) {
                        radioCell.setTag(0);
                        radioCell.setText(LocaleController.getString("WhoCanAddMembersAllMembers", R.string.WhoCanAddMembersAllMembers), chat != null && chat.democracy, true);
                    } else if (position == changeAddRadio2Row) {
                        radioCell.setTag(1);
                        radioCell.setText(LocaleController.getString("WhoCanAddMembersAdmins", R.string.WhoCanAddMembersAdmins), chat != null && !chat.democracy, false);
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
            if (position == addNewRow || position == addNew2Row) {
                return 2;
            } else if (position >= participantsStartRow && position < participantsEndRow || position >= participants2StartRow && position < participants2EndRow) {
                return 0;
            } else if (position == addNewSectionRow || position == changeAddSectionRow || position == participantsDividerRow) {
                return 3;
            } else if (position == participantsInfoRow) {
                return 1;
            } else if (position == changeAddHeaderRow || position == restricted1SectionRow || position == restricted2SectionRow) {
                return 5;
            } else if (position == changeAddRadio1Row || position == changeAddRadio2Row) {
                return 6;
            } else if (position == blockedEmptyRow) {
                return 4;
            }
            return 0;
        }

        public TLRPC.ChannelParticipant getItem(int position) {
            if (participantsStartRow != -1 && position >= participantsStartRow && position < participantsEndRow) {
                return participants.get(position - participantsStartRow);
            } else if (participants2StartRow != -1 && position >= participants2StartRow && position < participants2EndRow) {
                return participants2.get(position - participants2StartRow);
            }
            return null;
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
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ManageChatUserCell.class, TextSettingsCell.class, ManageChatTextCell.class, RadioCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{RadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackground),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{RadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_radioBackgroundChecked),

                new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_graySectionText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection),

                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(listView, 0, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
        };
    }
}
